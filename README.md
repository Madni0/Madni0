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
| `backend/` | Express config/logging backend + **AI Shield Web** + dashboard (Dockerfile) |
| `backend/public/` | Web app: same detection engine ported to JS (`engine.js`), image analyzer + **live screen-scan demo**, samples |
| `docs/ARCHITECTURE.md` | Full pipeline design & model roadmap |
| `docs/SETUP_AND_BUILD.md` | Build/run/test instructions (Android + web) |
| `docs/CLIENT_LIMITATIONS.md` | Honest platform/detection constraints & phases |
| `docs/android-ci.yml` | GitHub Actions workflow (copy into `.github/workflows/` to activate) |

## Web app (no install, demo-ready)

Run the backend and open its URL — the web app is the same detector in the browser:

- **Analyze an image** — drag & drop, paste (Ctrl+V), or pick a bundled sample; identical scoring UI to the phone (band colors, feature details). 100% client-side: images never leave the browser.
- **Live screen scan** — share a tab/window; the page monitors silently with change detection + pHash dedup and only raises the same **"⚠ Likely AI · NN%"** chip when the server-configured threshold is crossed. A faithful desktop mirror of the Android experience.
- **Dashboard** (`/dashboard`) — live stats, detection log fed by both apps, and threshold editing applied to every connected device.

Quick start: `cd backend && npm install && npm start` → open `http://localhost:3000`.


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
