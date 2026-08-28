# AI Shield — Real-Time AI Content Detection (Android)

Mobile app that identifies likely AI-generated content **while the user
browses social media** — no screenshots, no manual uploads. Enable once per
session, scroll normally; if content is highly likely AI-generated, a small
warning chip appears over the current app:

> ⚠️ **Likely AI · 94%**

Detection is silent. There is no "Scanning…" UI — the user is only
interrupted when confidence exceeds the (backend-configured) threshold.

Built to the client brief: Instagram, TikTok, Facebook, X/Twitter, Reddit,
YouTube and any other app, during an authorized scanning session.

## Feature highlights

- **MediaProjection capture service** samples the screen while scrolling
  (720p mirror, ~4 fps change detection, ≤512px analysis crops).
- **Silent detection pipeline**: change detection → perceptual-hash dedup →
  on-device analysis → weighted fusion (visual / deepfake / synthetic audio).
- **Overlay warning** (`SYSTEM_ALERT_WINDOW`) with tap-through detail screen:
  visual %, deepfake %, synthetic audio %, Content Credentials, overall.
- **Threshold bands from the backend** (client spec): quiet < 70%,
  silently recorded 70–89%, visible alert ≥ 90% — tunable from
  [`/backend`](backend/README.md) without app updates.
- **Guest or email sign-in**, local history of every analysis, today-stats.
- **Model drop-in**: ship `ai_image_detector.tflite` +
  `deepfake_detector.tflite` in assets for production accuracy; a documented
  heuristic baseline runs out of the box (see `docs/ARCHITECTURE.md`).

## Repo layout

| Path | What |
|---|---|
| `app/` | Android app (Kotlin, minSdk 26, targetSdk 34) |
| `backend/` | Express config/logging backend + admin UI (Dockerfile) |
| `docs/ARCHITECTURE.md` | Full pipeline design & model roadmap |
| `docs/SETUP_AND_BUILD.md` | Build/run/test instructions |
| `docs/CLIENT_LIMITATIONS.md` | Honest platform/detection constraints & phases |
| `.github/workflows/android.yml` | CI that builds a debug APK on push |

## Quick start

```bash
./gradlew :app:assembleDebug          # or open in Android Studio
cd backend && npm install && npm start  # thresholds + admin UI on :3000
```

Emulator default backend URL is `http://10.0.2.2:3000`.

## Honest notes

Android enforces per-session screen-capture consent and a system
"screen sharing" indicator while active; Content Credentials (C2PA) cannot
be verified from screen captures (reported honestly as "Not detected");
detectors are probabilistic. Details and the phased roadmap:
`docs/CLIENT_LIMITATIONS.md`.
