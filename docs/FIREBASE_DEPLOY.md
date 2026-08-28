# Deploy AI Shield Web to Firebase Hosting (from YOUR machine)

The agent sandbox cannot reach Firebase (network-restricted), so run these
once on your own computer. Result: a permanent HTTPS link on Firebase's
global CDN.

## Prerequisites

- Node.js 18+ → https://nodejs.org
- A Google account

## Steps (copy-paste)

```bash
# 1. get the code
git clone https://github.com/Madni0/Madni0.git
cd Madni0

# 2. install the Firebase CLI (once)
npm install -g firebase-tools

# 3. sign in with your Google account (opens your browser)
firebase login

# 4. create a Firebase project (or skip if you already have one)
firebase projects:create aishield-demo-01 --display-name "AI Shield"

# 5. tell the CLI your project id
#    (edit .firebaserc and replace YOUR-PROJECT-ID with aishield-demo-01)

# 6. deploy — that's it
firebase deploy --only hosting
```

Your link appears at the end:

```
Hosting URL: https://aishield-demo-01.web.app
```

Share that link anywhere — it is permanent, HTTPS, and served from
Firebase's CDN. The web app auto-detects there is no backend behind
Firebase Hosting and runs in standalone demo mode (default thresholds,
100% in-browser analysis — the samples work immediately).

## Notes

- `firebase.json` at the repo root is already configured to publish the
  `backend/public` folder (web app + demo samples).
- Firebase Hosting is **static** — the Dashboard / live-threshold editing /
  Android config API need the Express backend. Use `render.yaml`
  (docs/DEPLOY.md) on Render/Railway for the full stack, or add Firebase
  Cloud Functions later.
- Want a custom domain? `firebase hosting:sites:create` + connect it in the
  Firebase console.

## Troubleshooting

- `firebase projects:create` fails with "not authorized": your account may
  need to accept Firebase terms at https://console.firebase.google.com once.
- Already have a project: just edit `.firebaserc`, then step 6.
