# AI Shield — Architecture

## What the app does (matches the client brief)

1. User onboards → guest or email account → lands on the dashboard.
2. Toggles **AI Protection** → Android shows the one-time screen-capture
   consent dialog.
3. `ScanService` (foreground service, type `mediaProjection`) starts mirroring
   the screen into an off-screen `ImageReader` (720p). A persistent, silent
   notification hosts a **Stop** action.
4. While the user scrolls Instagram/TikTok/Facebook/X/Reddit/YouTube:
   - frames are compared at ~4 fps on a 64×48 grayscale thumbnail;
   - when the screen "moves enough" and the sample interval has elapsed,
     the frame is downscaled and analyzed **on-device**;
   - a perceptual hash (dHash) skips content already analyzed;
   - results below `logThreshold` are discarded; `logThreshold…alertThreshold`
     are **recorded silently** (no UI, per client spec); ≥ `alertThreshold`
     raises the overlay warning chip.
5. The chip (`TYPE_APPLICATION_OVERLAY`) reads **"Likely AI · 94%"**, auto
   dismisses, and opens a detailed breakdown when tapped.

There is deliberately **no scanning indicator anywhere** — detection is
silent; only high-confidence content interrupts the user.

## Module map

```
app/src/main/java/com/aishield/detector/
├── App.kt                  Application: singletons (DB, config, account)
├── core/
│   ├── AppConfig           config model; server JSON parsing + clamping
│   ├── ConfigRepository    defaults ← server cache ← local overrides
│   ├── ThresholdEngine     0-69 ignore · 70-89 log · 90+ alert (server values)
│   ├── ScoreFusion         weighted overall score (tested)
│   ├── PHash               dHash + hamming dedup (tested)
│   ├── DetectionDb         SQLite log of every analysis
│   ├── AccountStore        guest/email session (Firebase-swappable)
│   └── ProtectionState     service ↔ UI state
├── capture/
│   ├── ScanService         MediaProjection FGS: capture → sample → analyze → alert
│   └── FrameChangeDetector 64x48 gray diff "new content" trigger
├── detection/
│   ├── DetectionEngine     orchestrates detectors, builds the breakdown
│   ├── ImageDetector       interface
│   ├── HeuristicImageDetector   noise/spectrum/blockiness baseline (tested features)
│   ├── TfliteImageDetector drop-in trained model (assets/ai_image_detector.tflite)
│   ├── FaceHelper          platform face detection (feeds deepfake analyzer)
│   ├── DeepfakeAnalyzer    model or smoothness baseline on face crops
│   ├── AudioAnalyzer       AudioPlaybackCapture + spectral synthetic-voice heuristic
│   ├── C2paChecker         Content Credentials status (honest "not detected")
│   └── Fft                 radix-2 FFT used by frequency features
├── overlay/OverlayManager  the warning chip window (tap → DetailActivity)
└── ui/                     Onboarding · Auth · Main · Detail · History · Settings
```

## Detection pipeline detail

```
VirtualDisplay (720p RGBA)
   └─ ImageReader ──► downscale 64x48 gray ──► mean-abs-diff ──┐
                        (throttled ~4 fps)                     │ change?
                                                               ▼
                  ┌──────────────── sample interval gate ─────┤
                  ▼
        analysis bitmap (≤512px, config)
                  │
        dHash dedup (hamming < N → skip)
                  │
        ┌─────────┴───────────────────────────┐
        ▼                                     ▼
  visual score                    face found? → deepfake score
  (TFLite model if bundled,       (model or smoothness baseline)
   else heuristic baseline)                 │
        └──────────────┬────────────────────┘
                       ▼
        audio snapshot (optional, 16 kHz playback capture,
        speech-gated synthetic-voice score)
                       ▼
        ScoreFusion → overall ∈ [0,1]
                       ▼
        ThresholdEngine (server thresholds)
          IGNORE │ LOG_SILENT (Room/SQLite row only)
                 └► ALERT → overlay chip (+ fallback notification)
                          → thumbnail + detailed record in History
                          → POST /logs to backend
```

## Thresholds & remote config

- Alert/silent bands, sample rate, dedup distance, overlay timing/position,
  session auto-stop: all in one JSON served by `GET /config` (see `/backend`).
- The app caches the last good config and clamps values defensively, so a
  bad push can't break scanning; defaults match the client's spec
  (quiet < 70%, silent log 70–89%, alert ≥ 90%).

## Model roadmap (heuristic → trained)

The bundled heuristic detector measures well-known synthetic-media
fingerprints (median-filter residual noise, 1/f² spectrum slope, JPEG
blockiness, saturation skew). It gives the client a working demo from day
one and a measurable baseline; production accuracy comes from swapping in a
trained model with **zero app-code changes**:

1. Train/obtain a universal AI-image detector (e.g. UNFD / UniversalFakeDetect
   style EfficientNet backbone).
2. Export to TFLite with input `[1,224,224,3]` float 0..1 and a single
   sigmoid output.
3. Drop the file at `app/src/main/assets/ai_image_detector.tflite`
   (and `deepfake_detector.tflite` for faces). Done — `TfliteImageDetector`
   auto-detects and takes priority.

Improving the shipped baseline later: fine-tune against a labeled set of
social-media screenshots, move the logistic weights in
`HeuristicImageDetector.combine()` into server config.

## Privacy posture (selling point)

- Analysis runs on-device; frames never leave the phone in the default flow.
- Only anonymous detection events (scores, package name, timestamp) are
  posted to `/logs`, and the endpoint can be disabled by not pointing the
  app at the backend.
- The OS-level capture consent + the persistent notification make the
  active session transparent, satisfying Android policy expectations.
