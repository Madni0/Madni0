# Setup & Build

## Requirements

- Android Studio (Koala or newer) with SDK 34
- JDK 17 (bundled with recent Android Studio)
- Android device/emulator on API 26+ (audio analysis needs API 29+)

## Build & run

```bash
# open in Android Studio, or:
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

The Gradle wrapper (8.7) is included; AGP 8.5.2 / Kotlin 2.0.20 are pinned
in the build files.

## CI (no local build needed)

A ready GitHub Actions workflow is included at `docs/android-ci.yml`
(it builds a debug APK + runs unit tests on every push and uploads the APK
as an artifact). To activate it, copy it into place — this needs a push
made by an account/token with the `workflows` permission (e.g. upload the
file through the GitHub web UI at "Add file → Upload files" into
`.github/workflows/`):

```
mkdir -p .github/workflows && cp docs/android-ci.yml .github/workflows/android.yml
```

## Running the backend + web app

```bash
cd backend
npm install
ADMIN_KEY=dev-admin-key npm start   # http://localhost:3000
```

- **Web app** (http://localhost:3000): image analyzer + live screen-scan demo
  of the phone experience. `public/engine.js` is the same detector as the
  Android app, ported to JavaScript; images are analyzed entirely in the
  browser. Bundled samples: one synthetic-style render (scores ~90% → red
  "Likely AI") and one photographic sample (scores low → green).
- **Dashboard** (http://localhost:3000/dashboard): live stats, the shared
  detection log (Android + web), and the threshold editor that updates every
  connected device.
- **Emulator**: default app setting `http://10.0.2.2:3000` works as-is.
- **Physical device**: set the app's Settings → Backend URL to your
  computer's LAN IP, e.g. `http://192.168.1.20:3000` (same Wi-Fi), or
  deploy the backend (Dockerfile included) and use the public URL.

Engine sanity check from Node (no browser needed):
`node tools/test-engine.mjs` — prints both sample scores.

## Manual test script (what to show the client)

**Fastest demo — the web app (2 minutes, nothing to install):**
1. Start the backend, open it in Chrome.
2. Click the synthetic sample → **"Likely AI Generated · ~91%"** with the red
   breakdown; click the photographic sample → low score, green verdict.
3. Open the Dashboard in a second tab — both results appear in the log.
4. In the Dashboard, drop the alert threshold to 0.60, rescan the photo
   sample's neighborhood or run **Live screen scan** on any feed — behavior
   changes with no app update (that's the backend-configurable requirement).
5. **Live screen scan**: share a tab with a social feed and scroll; the
   "⚠ Likely AI · NN%" chip appears only on high-confidence content,
   auto-dismisses, and logs to the dashboard.

**Android device/emulator:**
1. Install & open the app → Get Started → Continue as Guest.
2. Toggle **AI Protection** → accept the screen-capture consent
   (Android also asks for "record audio" within the same dialog on 10+).
3. Open Instagram/TikTok/YouTube and scroll.
4. On AI-looking content the chip **"Likely AI · NN%"** appears at the top
   right and auto-dismisses after ~8 s. Tap it → full breakdown.
5. Open **History** → every analyzed item, including 70–89% "silent" ones
   (they never showed UI while scrolling — that's the requirement).
6. Open the backend admin page, drop the alert threshold to e.g. 0.60,
   press *Refresh from backend* in the app settings, and repeat the feed
   scroll — more alerts now fire. Raise it back to 0.90.

## Notes

- `android:usesCleartextTraffic="true"` is set for local development;
  remove it once the backend is served over HTTPS.
- On Android 14+ the OS shows a "screen is being shared/cast" indicator
  while protection is active — unavoidable platform behavior, same as
  every screen-recording app.
