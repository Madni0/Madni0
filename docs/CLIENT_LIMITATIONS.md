# Honest limitations & roadmap (share with the client)

Being upfront about platform constraints protects the project. These were
flagged in the proposal and are reflected in the implementation:

## Platform-imposed

1. **Consent dialog per session.** Android requires the screen-capture
   consent every time protection starts (a "session"). The brief's
   "enable once" is honored within a session — the app keeps working while
   the user switches apps — but Android revokes the projection token when
   the service stops or the device reboots. Every serious app (screen
   recorders, parental controls) works this way.
2. **"Screen sharing" indicator.** On Android 12+ the OS shows a cast/recording
   icon while capture is active. Not controllable by apps.
3. **Non-capturable content.** Apps can flag screens `FLAG_SECURE` (DRM,
   banking, some Incognito screens). Those surfaces simply don't reach us;
   we skip silently and never crash.
4. **iOS.** No background screen monitoring exists on iOS; Android-first is
   correct. (iOS would need the Share/Extension-based approach.)

## Detection-imposed

5. **Content Credentials (C2PA).** Credentials live in the original file's
   metadata; a screen capture only contains pixels, so live scanning
   reports "Not detected" (implemented exactly this way, honestly labeled).
   True verification needs a share-to-verify flow — recommended Phase 2.
6. **No detector is 100%.** The shipped heuristic baseline demonstrates the
   full pipeline immediately; production accuracy requires dropping in a
   trained TFLite model (supported already, zero code change) and tuning
   thresholds against real social-media screenshots. Expect iterative
   tuning of precision/recall, especially for AI *text* overlaid on images
   (a separate OCR+classifier phase).
7. **Synthetic audio** analysis works on Android 10+ and only for media
   that allows playback capture (non-DRM).

## Recommended phases

| Phase | Scope |
|---|---|
| 1 (this codebase) | Capture pipeline, overlay, thresholds via backend, history, guest/email auth, heuristic + model-drop-in detection |
| 2 | Trained TFLite models, share-to-verify C2PA, AI-text (OCR) detection |
| 3 | Play Store release hardening: HTTPS-only, Firebase Auth/config swap, crash reporting, per-app exclusions list |
