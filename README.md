# Health Connect Exporter

Exports all available Android Health Connect data daily to a self-hosted server or Google Drive. Includes a companion web dashboard for visualization.

## Project Structure

```
health-connect-exporter/
├── android/              Android app (Kotlin + Jetpack Compose)
│   ├── app/src/main/
│   │   ├── java/com/fozzels/healthexporter/
│   │   │   ├── HealthExporterApp.kt        Application class (Hilt + WorkManager)
│   │   │   ├── MainActivity.kt
│   │   │   ├── model/                      Data models (serializable)
│   │   │   ├── data/                       HealthConnectManager, repositories
│   │   │   ├── service/                    HTTP & Drive upload services
│   │   │   ├── worker/                     ExportWorker (daily background job)
│   │   │   ├── di/                         Hilt DI module
│   │   │   └── ui/                         Compose screens & ViewModels
│   │   └── res/
│   └── gradle/libs.versions.toml
└── dashboard/            Web dashboard (static HTML + Node.js server)
    ├── index.html
    ├── dashboard.js
    ├── style.css
    ├── server.js
    └── package.json
```

## Android App Setup

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android device / emulator with Health Connect installed (Android 9+)
- Min SDK: 26 (Android 8), Target SDK: 35

### Build & Run

```bash
cd android
./gradlew assembleDebug
# or open in Android Studio and run
```

### Health Connect permissions

On first launch, navigate to the **Permissions** tab and tap **Grant All Health Connect Permissions**. This opens the Health Connect permission dialog.

> Health Connect must be installed on the device. On Android 13 and below, install it from the Play Store. On Android 14+, it's built in.

### Export targets

#### Option A: Custom HTTP endpoint (recommended for self-hosted)

1. Deploy the Node.js dashboard server (see below)
2. In the app's **Settings** tab:
   - Select **Custom HTTP Endpoint**
   - Enter your server URL, e.g. `https://your-server.com/api/upload`
   - Optionally enter a Bearer token (must match `UPLOAD_TOKEN` env var on server)
3. Tap **Save Settings**

#### Option B: Google Drive

1. Set up Google Cloud credentials — see `google-services.json.template`
2. Place your `google-services.json` at `android/app/google-services.json`
3. Add the Google Services Gradle plugin (instructions in the template file)
4. In Settings: select **Google Drive**, tap **Sign in with Google**, then pick a folder

### Daily export schedule

- Default: **02:00 AM** every day
- Exports **previous day's** data (e.g. at 2am on Tuesday, exports Monday's data)
- WorkManager retries up to 3 times on failure (exponential backoff)
- A notification is shown on completion or failure
- You can also trigger an export manually from the Dashboard screen

### JSON export format

```json
{
  "export_date": "2024-01-15",
  "device": "Google Pixel 8",
  "android_version": 34,
  "exported_at": "2024-01-16T02:00:00Z",
  "data": {
    "steps": [{"start_time": "...", "end_time": "...", "count": 8432, "source": "com.google.android.apps.fitness"}],
    "heart_rate": [{"time": "...", "bpm": 72}],
    "sleep": [{"start": "...", "end": "...", "stage": "DEEP"}],
    "blood_pressure": [{"time": "...", "systolic": 120.0, "diastolic": 80.0}],
    "weight": [{"time": "...", "kg": 75.5}],
    "calories": [{"start_time": "...", "end_time": "...", "kcal": 2100.0}],
    "active_calories": [...],
    "distance": [{"start_time": "...", "end_time": "...", "meters": 5000.0}],
    "spo2": [{"time": "...", "percent": 98.0}],
    "blood_glucose": [{"time": "...", "mmol_l": 5.2}],
    "body_temperature": [{"time": "...", "celsius": 36.6}],
    "hydration": [{"start_time": "...", "end_time": "...", "liters": 0.25}],
    "nutrition": [{"start_time": "...", "end_time": "...", "calories": 400.0, "protein_g": 20.0, "fat_g": 15.0, "carbs_g": 45.0}],
    "exercise_sessions": [{"start": "...", "end": "...", "type": "RUNNING", "title": "Morning run"}]
  }
}
```

---

## Web Dashboard Setup

### Quick start (local)

```bash
cd dashboard
npm install
node server.js
# Open http://localhost:3000
```

### Environment variables

| Variable       | Default       | Description                                      |
|----------------|---------------|--------------------------------------------------|
| `PORT`         | `3000`        | HTTP port                                        |
| `DATA_DIR`     | `./data`      | Directory where JSON export files are stored     |
| `UPLOAD_TOKEN` | (none)        | Bearer token required for `POST /api/upload`     |

### Production deployment (systemd example)

```bash
# /etc/systemd/system/health-dashboard.service
[Unit]
Description=Health Connect Dashboard
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/health-connect-exporter/dashboard
ExecStart=/usr/bin/node server.js
Restart=on-failure
Environment=PORT=3000
Environment=DATA_DIR=/var/data/health-exports
Environment=UPLOAD_TOKEN=your-secret-token-here

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable health-dashboard
sudo systemctl start health-dashboard
```

### With nginx reverse proxy

```nginx
server {
    listen 443 ssl;
    server_name health.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/health.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/health.yourdomain.com/privkey.pem;

    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### API endpoints

| Method | Endpoint              | Auth?    | Description                              |
|--------|-----------------------|----------|------------------------------------------|
| `GET`  | `/api/health`         | No       | Server health check                      |
| `GET`  | `/api/files`          | No       | List all available export files          |
| `GET`  | `/api/data/:date`     | No       | Get export JSON for a specific date      |
| `GET`  | `/api/range?from=&to=`| No       | Get all exports in a date range          |
| `POST` | `/api/upload`         | Optional | Upload a health export from Android app  |

### Dashboard features

- **Date range picker**: Last 7, 14, 30, 90 days or custom range
- **Summary cards**: Avg steps, avg HR, total sleep, avg weight, avg calories, avg SpO2, total distance
- **Charts** (Chart.js): Steps (bar), Heart Rate (line), Sleep Duration (bar), Weight (line), Calories (bar), SpO2 (line)
- **Data table**: All metrics per day, newest first
- **CSV export**: Download the visible range as CSV
- **Auto-refresh**: Every 5 minutes

---

## Architecture

```
Android App
  │
  ├─ MainActivity (Compose)
  │   └─ NavGraph (3 tabs)
  │       ├─ DashboardScreen  ── DashboardViewModel
  │       ├─ SettingsScreen   ── SettingsViewModel
  │       └─ PermissionsScreen── PermissionsViewModel
  │
  ├─ HealthConnectManager      (reads all HC record types)
  ├─ SettingsRepository        (DataStore Preferences)
  ├─ ExportRepository          (export logs in DataStore)
  │
  ├─ ExportWorker (WorkManager)
  │   ├─ Reads health data for previous day
  │   ├─ Serializes to JSON
  │   └─ Uploads via HttpExportService or DriveExportService
  │
  └─ Hilt DI (AppModule → OkHttpClient)

Web Dashboard
  ├─ server.js  (Express: serves static files + REST API)
  ├─ index.html (responsive layout)
  ├─ dashboard.js (fetch + Chart.js rendering)
  └─ style.css  (CSS variables, dark mode support)
```

## Tech Stack

### Android
- Kotlin 1.9 + Jetpack Compose (Material3)
- Hilt (DI)
- WorkManager (background jobs)
- Health Connect SDK 1.1.0-rc01
- OkHttp + Retrofit
- Kotlinx Serialization (JSON)
- DataStore Preferences (settings + logs)
- Google Sign-In (for Drive OAuth)

### Dashboard
- Node.js + Express (no TypeScript, no build step)
- Chart.js 4 (CDN)
- Vanilla JS + CSS variables (dark mode ready)

## Troubleshooting

**Health Connect not available**: Install the Health Connect app from Play Store (Android 9–13). On Android 14+, it's built in but may need to be enabled in Settings.

**Permissions not showing**: Make sure the app is correctly declared in the manifest with the `<queries>` block and the activity-alias for `VIEW_PERMISSION_USAGE`.

**Export fails with "HTTP URL not configured"**: Go to Settings and set the upload URL.

**Google Drive sign-in fails**: Ensure `google-services.json` is in place, the SHA-1 fingerprint matches your signing certificate, and the Drive API is enabled in Google Cloud Console.

**WorkManager not running**: WorkManager requires network connectivity. Check that the device is connected and battery optimization isn't killing the app.
