# AI Shield — Backend

Small Express service that fulfills the "thresholds configurable from the
backend" requirement and provides account + detection-log endpoints.

## Run locally

```bash
cd backend
npm install
ADMIN_KEY=dev-admin-key PORT=3000 npm start
```

Open http://localhost:3000 — a mini admin UI to edit thresholds live.

## Android app connection

- Emulator default backend URL: `http://10.0.2.2:3000` (host machine).
- Real device: use your LAN IP (e.g. `http://192.168.1.20:3000`) or deploy
  and set an HTTPS URL (Settings → Backend URL in the app).

## API

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/config` | — | thresholds + app config |
| PUT | `/config` | `x-admin-key` | update config (clamped server-side) |
| POST | `/auth/guest` | — | guest token |
| POST | `/auth/register` | — | `{email,password}` → token |
| POST | `/auth/login` | — | `{email,password}` → token |
| POST | `/logs` | — | app posts detection events |
| GET | `/logs?limit=100` | `x-admin-key` | recent logs |

Default thresholds implement the client's bands: quiet under 70%,
silent logging 70–89%, visible warning at 90%+.

## Production notes

- Storage here is JSON files under `data/` for zero-dependency demos.
  Swap for Postgres/Firestore as needed.
- Put the service behind HTTPS (e.g. Caddy/Nginx or a PaaS) and remove
  `android:usesCleartextTraffic` from the app manifest.
- Set a strong `ADMIN_KEY` env var.
