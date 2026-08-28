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

## Running the backend

```bash
cd backend
npm install
ADMIN_KEY=dev-admin-key npm start   # http://localhost:3000
```

- **Emulator**: default app setting `http://10.0.2.2:3000` works as-is.
- **Physical device**: set the app's Settings → Backend URL to your
  computer's LAN IP, e.g. `http://192.168.1.20:3000` (same Wi-Fi), or
  deploy the backend (Dockerfile included) and use the public URL.

Use Settings → *Refresh from backend* to pull new thresholds instantly.

## Manual test script (what to show the client)

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
