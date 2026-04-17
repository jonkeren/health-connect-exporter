/**
 * Health Connect Dashboard — dashboard.js
 * Fetches data from the Node.js server and renders charts/tables.
 */

const API_BASE = window.location.origin;
const REFRESH_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

let charts = {};
let allData = [];
let refreshTimer = null;

// ── Chart.js default config ───────────────────────────────────────────────────
Chart.defaults.font.family = "-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
Chart.defaults.font.size = 12;
const COLORS = {
  steps:    { bg: 'rgba(76,175,80,0.7)',    border: '#4CAF50' },
  hr:       { bg: 'rgba(239,68,68,0.2)',     border: '#ef4444' },
  sleep:    { bg: 'rgba(99,102,241,0.7)',    border: '#6366f1' },
  weight:   { bg: 'rgba(245,158,11,0.2)',    border: '#f59e0b' },
  calories: { bg: 'rgba(239,68,68,0.7)',     border: '#ef4444' },
  spo2:     { bg: 'rgba(6,182,212,0.2)',     border: '#06b6d4' },
};

// ── Date helpers ──────────────────────────────────────────────────────────────
function today() {
  return new Date().toISOString().split('T')[0];
}

function daysAgo(n) {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().split('T')[0];
}

function formatDate(dateStr) {
  const [y, m, d] = dateStr.split('-');
  return `${d}/${m}`;
}

// ── Init ──────────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  setDefaultDates();
  loadData();
  startAutoRefresh();
});

function setDefaultDates() {
  document.getElementById('fromDate').value = daysAgo(30);
  document.getElementById('toDate').value = today();
}

function startAutoRefresh() {
  if (refreshTimer) clearInterval(refreshTimer);
  refreshTimer = setInterval(loadData, REFRESH_INTERVAL_MS);
}

function onPresetChange() {
  const preset = document.getElementById('rangePreset').value;
  const customDates = document.getElementById('customDates');
  if (preset === 'custom') {
    customDates.style.display = 'flex';
  } else {
    customDates.style.display = 'none';
    document.getElementById('fromDate').value = daysAgo(parseInt(preset, 10));
    document.getElementById('toDate').value = today();
    loadData();
  }
}

// ── Data loading ──────────────────────────────────────────────────────────────
async function loadData() {
  const from = document.getElementById('fromDate').value;
  const to = document.getElementById('toDate').value;

  setLoading(true);
  hideError();

  try {
    const res = await fetch(`${API_BASE}/api/range?from=${from}&to=${to}`);
    if (!res.ok) throw new Error(`Server returned ${res.status}`);
    const json = await res.json();
    allData = json.data || [];
    renderAll(allData);
    updateLastRefresh();
  } catch (err) {
    showError(`Failed to load data: ${err.message}. Is the server running?`);
  } finally {
    setLoading(false);
  }
}

// ── Aggregation helpers ───────────────────────────────────────────────────────
function sumSteps(dayData) {
  return dayData.data?.steps?.reduce((s, r) => s + (r.count || 0), 0) || 0;
}

function avgHeartRate(dayData) {
  const samples = dayData.data?.heart_rate || [];
  if (!samples.length) return null;
  return Math.round(samples.reduce((s, r) => s + r.bpm, 0) / samples.length);
}

function totalSleepHours(dayData) {
  const sessions = dayData.data?.sleep || [];
  let ms = 0;
  for (const s of sessions) {
    if (s.start && s.end) {
      ms += new Date(s.end) - new Date(s.start);
    }
  }
  return ms / 3600000;
}

function latestWeight(dayData) {
  const records = dayData.data?.weight || [];
  if (!records.length) return null;
  const sorted = [...records].sort((a, b) => new Date(b.time) - new Date(a.time));
  return sorted[0]?.kg ?? null;
}

function totalCalories(dayData) {
  return dayData.data?.calories?.reduce((s, r) => s + (r.kcal || 0), 0) || 0;
}

function totalDistanceKm(dayData) {
  const meters = dayData.data?.distance?.reduce((s, r) => s + (r.meters || 0), 0) || 0;
  return meters / 1000;
}

function avgSpO2(dayData) {
  const records = dayData.data?.spo2 || [];
  if (!records.length) return null;
  return (records.reduce((s, r) => s + r.percent, 0) / records.length).toFixed(1);
}

// ── Render everything ─────────────────────────────────────────────────────────
function renderAll(data) {
  const labels = data.map(d => d.export_date);
  const fmtLabels = labels.map(formatDate);

  const stepsData   = data.map(sumSteps);
  const hrData      = data.map(avgHeartRate);
  const sleepData   = data.map(d => parseFloat(totalSleepHours(d).toFixed(2)));
  const weightData  = data.map(latestWeight);
  const calData     = data.map(d => Math.round(totalCalories(d)));
  const spo2Data    = data.map(d => parseFloat(avgSpO2(d) || 0));

  // Summary cards
  const validSteps   = stepsData.filter(v => v > 0);
  const validHR      = hrData.filter(v => v !== null);
  const validSleep   = sleepData.filter(v => v > 0);
  const validWeight  = weightData.filter(v => v !== null);
  const validCal     = calData.filter(v => v > 0);
  const validSpo2    = spo2Data.filter(v => v > 0);
  const validDist    = data.map(totalDistanceKm).filter(v => v > 0);

  setText('avgSteps',     validSteps.length  ? Math.round(avg(validSteps)).toLocaleString() : '—');
  setText('avgHR',        validHR.length     ? Math.round(avg(validHR)) : '—');
  setText('totalSleep',   validSleep.length  ? sum(validSleep).toFixed(1) : '—');
  setText('avgWeight',    validWeight.length ? avg(validWeight).toFixed(1) : '—');
  setText('avgCalories',  validCal.length    ? Math.round(avg(validCal)).toLocaleString() : '—');
  setText('avgSpO2',      validSpo2.length   ? avg(validSpo2).toFixed(1) : '—');
  setText('totalDistance',validDist.length   ? sum(validDist).toFixed(1) : '—');
  setText('exportDays',   data.length);

  // Charts
  renderBarChart('stepsChart', fmtLabels, stepsData, 'Daily Steps', COLORS.steps);
  renderLineChart('heartRateChart', fmtLabels, hrData, 'Avg Heart Rate (bpm)', COLORS.hr);
  renderBarChart('sleepChart', fmtLabels, sleepData, 'Sleep Duration (h)', COLORS.sleep);
  renderLineChart('weightChart', fmtLabels, weightData, 'Weight (kg)', COLORS.weight);
  renderBarChart('caloriesChart', fmtLabels, calData, 'Calories (kcal)', COLORS.calories);
  renderLineChart('spo2Chart', fmtLabels, spo2Data, 'SpO2 (%)', COLORS.spo2);

  // Table
  renderTable(data, labels);
}

function renderBarChart(id, labels, data, label, colorSet) {
  const ctx = document.getElementById(id).getContext('2d');
  if (charts[id]) charts[id].destroy();
  charts[id] = new Chart(ctx, {
    type: 'bar',
    data: {
      labels,
      datasets: [{
        label,
        data,
        backgroundColor: colorSet.bg,
        borderColor: colorSet.border,
        borderWidth: 1,
        borderRadius: 4,
      }]
    },
    options: {
      responsive: true,
      plugins: { legend: { display: false } },
      scales: {
        y: { beginAtZero: true, grid: { color: 'rgba(128,128,128,0.1)' } },
        x: { grid: { display: false } }
      }
    }
  });
}

function renderLineChart(id, labels, data, label, colorSet) {
  const ctx = document.getElementById(id).getContext('2d');
  if (charts[id]) charts[id].destroy();

  // Filter out nulls/zeros for display but keep positions
  const cleaned = data.map(v => (v === null || v === 0) ? null : v);

  charts[id] = new Chart(ctx, {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label,
        data: cleaned,
        backgroundColor: colorSet.bg,
        borderColor: colorSet.border,
        borderWidth: 2,
        pointRadius: 3,
        pointHoverRadius: 5,
        fill: true,
        tension: 0.3,
        spanGaps: true,
      }]
    },
    options: {
      responsive: true,
      plugins: { legend: { display: false } },
      scales: {
        y: { beginAtZero: false, grid: { color: 'rgba(128,128,128,0.1)' } },
        x: { grid: { display: false } }
      }
    }
  });
}

function renderTable(data, labels) {
  const tbody = document.getElementById('dataTableBody');

  if (!data.length) {
    tbody.innerHTML = '<tr><td colspan="9" class="no-data">No data available for the selected range.</td></tr>';
    return;
  }

  const rows = data.map((d, i) => {
    const steps    = sumSteps(d);
    const hr       = avgHeartRate(d);
    const sleep    = totalSleepHours(d);
    const weight   = latestWeight(d);
    const cal      = totalCalories(d);
    const dist     = totalDistanceKm(d);
    const spo2     = avgSpO2(d);
    const exercises = (d.data?.exercise_sessions || []).length;

    return `<tr>
      <td><strong>${labels[i]}</strong></td>
      <td>${steps ? steps.toLocaleString() : '<span style="color:var(--text-muted)">—</span>'}</td>
      <td>${hr ? hr : '<span style="color:var(--text-muted)">—</span>'}</td>
      <td>${sleep > 0 ? sleep.toFixed(1) : '<span style="color:var(--text-muted)">—</span>'}</td>
      <td>${weight ? weight.toFixed(1) : '<span style="color:var(--text-muted)">—</span>'}</td>
      <td>${cal ? cal.toLocaleString() : '<span style="color:var(--text-muted)">—</span>'}</td>
      <td>${dist > 0 ? dist.toFixed(2) : '<span style="color:var(--text-muted)">—</span>'}</td>
      <td>${spo2 && spo2 > 0 ? spo2 : '<span style="color:var(--text-muted)">—</span>'}</td>
      <td>${exercises > 0 ? `<span class="badge badge-blue">${exercises}</span>` : '<span style="color:var(--text-muted)">—</span>'}</td>
    </tr>`;
  }).reverse(); // newest first

  tbody.innerHTML = rows.join('');
}

// ── CSV export ────────────────────────────────────────────────────────────────
function exportCsv() {
  if (!allData.length) return;
  const headers = ['Date', 'Steps', 'Avg HR', 'Sleep (h)', 'Weight (kg)', 'Calories', 'Distance (km)', 'SpO2 (%)'];
  const rows = allData.map(d => [
    d.export_date,
    sumSteps(d),
    avgHeartRate(d) || '',
    totalSleepHours(d).toFixed(2),
    latestWeight(d) || '',
    Math.round(totalCalories(d)),
    totalDistanceKm(d).toFixed(2),
    avgSpO2(d) || ''
  ]);

  const csv = [headers, ...rows].map(r => r.join(',')).join('\n');
  const blob = new Blob([csv], { type: 'text/csv' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `health_export_${document.getElementById('fromDate').value}_${document.getElementById('toDate').value}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

// ── UI helpers ────────────────────────────────────────────────────────────────
function setLoading(visible) {
  document.getElementById('loadingOverlay').style.display = visible ? 'flex' : 'none';
}

function showError(msg) {
  const el = document.getElementById('errorBanner');
  el.textContent = msg;
  el.style.display = 'block';
}

function hideError() {
  document.getElementById('errorBanner').style.display = 'none';
}

function updateLastRefresh() {
  const now = new Date().toLocaleTimeString();
  document.getElementById('lastRefresh').textContent = `Last updated: ${now}`;
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

function avg(arr) {
  return arr.reduce((s, v) => s + v, 0) / arr.length;
}

function sum(arr) {
  return arr.reduce((s, v) => s + v, 0);
}
