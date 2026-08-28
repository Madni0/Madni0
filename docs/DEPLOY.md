# Deploying AI Shield Web (permanent public link)

The in-chat preview link only works while the agent sandbox is awake. For a
link you can send to anyone (the client, your phone), deploy the backend —
it hosts the web app, dashboard and config API in one service.

## Option A — Render.com (free, ~5 minutes)

1. Make sure this repo is pushed to GitHub (it is).
2. Go to https://render.com → **New + → Blueprint** → select your
   `Madni0` repo. Render reads `render.yaml` at the repo root.
3. When prompted, set the `ADMIN_KEY` env var to any long random string.
4. Deploy. You get a permanent URL like `https://aishield-xxxx.onrender.com`.

| URL | What |
|---|---|
| `https://<your-app>.onrender.com/` | AI Shield Web |
| `https://<your-app>.onrender.com/dashboard` | Dashboard |
| `https://<your-app>.onrender.com/config` | Config API for the Android app |

Then point the Android app at it: app **Settings → Backend URL** =
`https://<your-app>.onrender.com` (HTTPS, so you can also remove
`android:usesCleartextTraffic` from the manifest for production).

Notes: free tier sleeps after 15 min idle (first request wakes it, ~30 s).
The free plan's disk may not persist — logs/config reset on redeploy is fine
for demos; use a paid plan or external DB for production.

## Option B — Railway / Fly.io

Same idea: create a service from the repo, set **root directory =
`backend`**, add `ADMIN_KEY` env var, deploy. Dockerfile is included, so
either platform auto-detects it.

## Option C — Any VPS (Docker)

```bash
scp -r backend/ user@your-server:~/aishield
ssh user@your-server
cd aishield
docker build -t aishield .
docker run -d -p 80:3000 -e ADMIN_KEY=change-me -v $PWD/data:/app/data --restart unless-stopped aishield
```

Put Caddy or Nginx + Let's Encrypt in front for HTTPS.

## After deploying — 30-second client demo

1. Open `https://<your-app>/` on any device.
2. Click the **synthetic sample** → ~91% 🔴 Likely AI Generated.
3. Click the **photo sample** → ~7% 🟢 Appears Authentic.
4. Open **Live screen scan** (Chrome/Edge desktop) → share a social-feed tab
   → scroll → the "⚠ Likely AI · NN%" chip appears on AI-looking content.
5. Open `/dashboard` in a second tab → results are logged; drop the alert
   threshold to 0.6 and rescan to show thresholds are backend-controlled.
