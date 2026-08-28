/**
 * AI Shield - config & logging backend
 *
 * Endpoints
 *   GET  /config                 -> thresholds + app config (consumed by the Android app)
 *   PUT  /config                 -> update config  (header: x-admin-key)
 *   POST /auth/guest             -> guest token
 *   POST /auth/register          -> {email,password} -> token
 *   POST /auth/login             -> {email,password} -> token
 *   POST /logs                   -> app posts silent/raised detections here
 *   GET  /logs?limit=100         -> recent logs (header: x-admin-key)
 *   GET  /                       -> tiny admin UI (view + edit thresholds live)
 *
 * Storage: JSON files under ./data (swap for any DB in production).
 * Run:  ADMIN_KEY=dev-admin-key PORT=3000 node server.js
 */
const express = require('express');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 3000;
const ADMIN_KEY = process.env.ADMIN_KEY || 'dev-admin-key';
const DATA_DIR = path.join(__dirname, 'data');
const CONFIG_FILE = path.join(DATA_DIR, 'config.json');
const LOGS_FILE = path.join(DATA_DIR, 'logs.json');
const USERS_FILE = path.join(DATA_DIR, 'users.json');

const DEFAULT_CONFIG = {
  // Client-specified bands: 0-69% quiet, 70-89% record silently, 90%+ alert.
  alertThreshold: 0.90,
  logThreshold: 0.70,
  sampleIntervalMs: 2500,
  changeThreshold: 0.035,
  dedupHamming: 10,
  overlayAutoDismissMs: 8000,
  overlayPosition: 'top',
  sessionTimeoutMin: 0,
  analysisMaxWidth: 512,
  audioEnabled: true
};

fs.mkdirSync(DATA_DIR, { recursive: true });
if (!fs.existsSync(CONFIG_FILE)) fs.writeFileSync(CONFIG_FILE, JSON.stringify(DEFAULT_CONFIG, null, 2));
if (!fs.existsSync(LOGS_FILE)) fs.writeFileSync(LOGS_FILE, '[]');
if (!fs.existsSync(USERS_FILE)) fs.writeFileSync(USERS_FILE, '{}');

const readJson = (file, fallback) => {
  try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch (_) { return fallback; }
};
const writeJson = (file, value) => fs.writeFileSync(file, JSON.stringify(value, null, 2));

const app = express();
app.use(express.json({ limit: '1mb' }));
app.use(express.static(path.join(__dirname, 'public')));

// ---------- helpers ----------
function hashPassword(password, salt) {
  return crypto.scryptSync(password, salt, 32).toString('hex');
}

function requireAdmin(req, res, next) {
  if ((req.headers['x-admin-key'] || '') !== ADMIN_KEY) {
    return res.status(401).json({ error: 'invalid admin key' });
  }
  next();
}

// ---------- config ----------
app.get('/config', (_req, res) => {
  res.json({ ...DEFAULT_CONFIG, ...readJson(CONFIG_FILE, {}) });
});

app.put('/config', requireAdmin, (req, res) => {
  const current = { ...DEFAULT_CONFIG, ...readJson(CONFIG_FILE, {}) };
  const next = { ...current };
  for (const key of Object.keys(current)) {
    if (req.body[key] !== undefined) next[key] = req.body[key];
  }
  // Sanity clamps so a typo can't break scanning for everyone.
  next.alertThreshold = Math.min(1, Math.max(0.5, Number(next.alertThreshold) || 0.9));
  next.logThreshold = Math.min(next.alertThreshold - 0.01, Math.max(0.1, Number(next.logThreshold) || 0.7));
  next.sampleIntervalMs = Math.min(60000, Math.max(300, Number(next.sampleIntervalMs) || 2500));
  writeJson(CONFIG_FILE, next);
  res.json(next);
});

// ---------- auth ----------
app.post('/auth/guest', (_req, res) => {
  const token = 'guest-' + crypto.randomBytes(12).toString('hex');
  res.json({ token, email: 'Guest', guest: true });
});

app.post('/auth/register', (req, res) => {
  const { email, password } = req.body || {};
  if (!email || !email.includes('@') || !password || password.length < 6) {
    return res.status(400).json({ error: 'email and 6+ char password required' });
  }
  const users = readJson(USERS_FILE, {});
  if (users[email]) return res.status(409).json({ error: 'user exists' });
  const salt = crypto.randomBytes(16).toString('hex');
  users[email] = { salt, hash: hashPassword(password, salt), created: Date.now() };
  writeJson(USERS_FILE, users);
  const token = crypto.randomBytes(24).toString('hex');
  res.json({ token, email, guest: false });
});

app.post('/auth/login', (req, res) => {
  const { email, password } = req.body || {};
  const users = readJson(USERS_FILE, {});
  const user = users[email];
  if (!user) return res.status(401).json({ error: 'unknown user' });
  const hash = hashPassword(password || '', user.salt);
  if (hash !== user.hash) return res.status(401).json({ error: 'bad credentials' });
  const token = crypto.randomBytes(24).toString('hex');
  res.json({ token, email, guest: false });
});

// ---------- detection logs ----------
app.post('/logs', (req, res) => {
  const logs = readJson(LOGS_FILE, []);
  logs.push({ ...req.body, receivedAt: Date.now() });
  // Keep the file bounded in the demo deployment.
  writeJson(LOGS_FILE, logs.slice(-5000));
  res.json({ ok: true });
});

app.get('/logs', requireAdmin, (req, res) => {
  const limit = Math.min(5000, Number(req.query.limit) || 100);
  const logs = readJson(LOGS_FILE, []);
  res.json(logs.slice(-limit));
});

app.get('/health', (_req, res) => res.json({ ok: true }));

app.listen(PORT, '0.0.0.0', () => {
  console.log(`AI Shield backend listening on http://0.0.0.0:${PORT}`);
  console.log(`Admin key: ${ADMIN_KEY} (override with ADMIN_KEY env)`);
});
