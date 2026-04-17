/**
 * Health Connect Exporter — Node.js server
 *
 * Environment variables:
 *   PORT          — HTTP port (default: 3000)
 *   DATA_DIR      — directory where JSON exports are stored (default: ./data)
 *   UPLOAD_TOKEN  — Bearer token required for POST /api/upload (optional)
 */

const express = require('express');
const cors = require('cors');
const multer = require('multer');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'data');
const UPLOAD_TOKEN = process.env.UPLOAD_TOKEN || null;

// Ensure data directory exists
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

app.use(cors());
app.use(express.json({ limit: '50mb' }));
app.use(express.static(__dirname));

// ── Middleware: Bearer token auth for upload ──────────────────────────────────
function requireToken(req, res, next) {
  if (!UPLOAD_TOKEN) return next();
  const auth = req.headers['authorization'] || '';
  const token = auth.startsWith('Bearer ') ? auth.slice(7) : null;
  if (token !== UPLOAD_TOKEN) {
    return res.status(401).json({ error: 'Unauthorized — invalid or missing Bearer token' });
  }
  next();
}

// ── GET /api/files ─ list available export dates ─────────────────────────────
app.get('/api/files', (req, res) => {
  try {
    const files = fs.readdirSync(DATA_DIR)
      .filter(f => f.startsWith('health_') && f.endsWith('.json'))
      .map(f => ({
        filename: f,
        date: f.replace('health_', '').replace('.json', ''),
        size: fs.statSync(path.join(DATA_DIR, f)).size,
        modified: fs.statSync(path.join(DATA_DIR, f)).mtime.toISOString()
      }))
      .sort((a, b) => b.date.localeCompare(a.date));
    res.json({ files });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ── HEAD /api/data/:date ─ check if a date's export exists (used by app to skip already-synced days)
app.head('/api/data/:date', (req, res) => {
  const { date } = req.params;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    return res.status(400).end();
  }
  const filePath = path.join(DATA_DIR, `health_${date}.json`);
  res.status(fs.existsSync(filePath) ? 200 : 404).end();
});

// ── GET /api/data/:date ─ return JSON for a specific date ────────────────────
app.get('/api/data/:date', (req, res) => {
  const { date } = req.params;
  // Validate date format YYYY-MM-DD
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    return res.status(400).json({ error: 'Invalid date format. Use YYYY-MM-DD' });
  }
  const filePath = path.join(DATA_DIR, `health_${date}.json`);
  if (!fs.existsSync(filePath)) {
    return res.status(404).json({ error: `No data found for ${date}` });
  }
  try {
    const content = fs.readFileSync(filePath, 'utf8');
    res.setHeader('Content-Type', 'application/json');
    res.send(content);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ── GET /api/range?from=YYYY-MM-DD&to=YYYY-MM-DD ─ fetch range of dates ──────
app.get('/api/range', (req, res) => {
  const { from, to } = req.query;
  if (!from || !to) {
    return res.status(400).json({ error: 'from and to query params required' });
  }

  const results = [];
  const files = fs.readdirSync(DATA_DIR)
    .filter(f => f.startsWith('health_') && f.endsWith('.json'));

  for (const file of files) {
    const date = file.replace('health_', '').replace('.json', '');
    if (date >= from && date <= to) {
      try {
        const content = JSON.parse(fs.readFileSync(path.join(DATA_DIR, file), 'utf8'));
        results.push(content);
      } catch (_) { /* skip corrupt files */ }
    }
  }

  results.sort((a, b) => a.export_date.localeCompare(b.export_date));
  res.json({ data: results });
});

// ── POST /api/upload ─ accept JSON from Android app ──────────────────────────
app.post('/api/upload', requireToken, (req, res) => {
  const body = req.body;

  if (!body || !body.export_date) {
    return res.status(400).json({ error: 'Missing export_date in payload' });
  }

  const date = body.export_date;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    return res.status(400).json({ error: 'Invalid export_date format' });
  }

  const filePath = path.join(DATA_DIR, `health_${date}.json`);
  try {
    fs.writeFileSync(filePath, JSON.stringify(body, null, 2), 'utf8');
    console.log(`[${new Date().toISOString()}] Saved export for ${date} (${JSON.stringify(body).length} bytes)`);
    res.json({ success: true, date, path: `health_${date}.json` });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ── Health check ──────────────────────────────────────────────────────────────
app.get('/api/health', (req, res) => {
  const files = fs.readdirSync(DATA_DIR).filter(f => f.startsWith('health_') && f.endsWith('.json'));
  res.json({
    status: 'ok',
    dataDir: DATA_DIR,
    exportCount: files.length,
    latestExport: files.sort().reverse()[0] || null,
    uploadTokenRequired: !!UPLOAD_TOKEN,
    timestamp: new Date().toISOString()
  });
});

// ── Root → redirect to dashboard ─────────────────────────────────────────────
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'index.html'));
});

app.listen(PORT, () => {
  console.log(`Health Connect Dashboard running at http://localhost:${PORT}`);
  console.log(`Data directory: ${DATA_DIR}`);
  console.log(`Upload token: ${UPLOAD_TOKEN ? 'configured' : 'not required'}`);
});
