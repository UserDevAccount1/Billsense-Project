# BillSense — Development Setup

Step-by-step for getting a working dev environment on Windows. Linux/macOS notes inline where the path differs.

## 1. Prerequisites

| Tool | Version | Where |
|---|---|---|
| JDK | 17 (Microsoft / Adoptium) | `winget install Microsoft.OpenJDK.17` |
| Android SDK | platform 35, build-tools 35.0.1 | Android Studio or `cmdline-tools` |
| Node.js | 20 LTS | `nvm install 20` |
| Docker Desktop | 24+ | docker.com/products/docker-desktop |
| Firebase CLI | 13+ | `npm install -g firebase-tools` |
| Python | 3.12 | python.org (and disable WindowsApps alias) |
| gcloud SDK | latest | cloud.google.com/sdk/docs/install |

### Windows-specific gotcha — Python alias

Windows ships a `python.exe` alias under `WindowsApps` that opens the Microsoft Store. It shadows your real Python install and breaks gcloud. Fix:

```
Settings → Apps → Advanced app settings → App execution aliases
  → turn OFF python.exe and python3.exe
```

Or per-session:

```powershell
$env:CLOUDSDK_PYTHON = "C:\Users\<you>\AppData\Local\Programs\Python\Python312\python.exe"
```

## 2. Clone

```bash
git clone https://github.com/UserDevAccount1/Billsense-Project.git
cd Billsense-Project
```

## 3. Local config files

These are git-ignored; you must create them.

### `BillSense/local.properties`

```properties
sdk.dir=C:\\Android\\Sdk
MAPS_API_KEY=PLACEHOLDER_KEY_OR_REAL_KEY
```

The Gradle build fails without `MAPS_API_KEY` because it's wired into BuildConfig.

### `BillSense/app/google-services.json`

Pull from Firebase:

```bash
firebase login
firebase use bill-sense-aec6b
firebase apps:sdkconfig android 1:340624938055:android:81d528ded5f924a23fcd62 \
  > BillSense/app/google-services.json
```

### `BillSense/app/src/main/res/raw/service_account.json`

Service account from GCP IAM with FCM send permissions. Stub it with `{}` if you don't need FCM during dev — the build needs the file to exist but doesn't parse it at compile time.

### `BillSense Admin/admin-panel/.env`

Optional, only needed for GitNexus auto-clone:

```
VITE_GITNEXUS_REPO=UserDevAccount1/Billsense-Project
VITE_GEMINI_API_KEY=
VITE_OPENAI_API_KEY=
VITE_GITHUB_PAT=
```

### `docker/app/serviceAccountKey.json`

Only needed if you're running the API locally with real Firebase persistence. Get from GCP IAM, **never commit**.

## 4. Bring up the dev stack

```powershell
# admin + gitnexus in Docker
cd docker
docker compose up -d billsense-admin gitnexus-agent

# dev-server on host (separate terminal)
cd "..\BillSense Admin\admin-panel"
npm ci
node dev-server.mjs

# open the dashboard
start http://localhost:3000
```

You should see green tiles for Admin Panel, GitNexus Agent, Dev Server, Cloud Run API, and Firebase RTDB. The "Local Docker API (8080)" tile will be red until you bring up the local backend (see §6).

## 5. Android emulator + app

```powershell
# create an AVD once
avdmanager create avd -n BillSense_Test -k "system-images;android-35;google_apis;x86_64" -d pixel_6

# launch
emulator -avd BillSense_Test -gpu auto

# build + install
cd BillSense
.\gradlew installDebug

# launch from the dashboard's App Testing page, or:
adb shell am start -n com.app.billsense/.activities.MainActivity
```

## 6. Local ML backend (optional)

Requires GCP auth to pull the prod image, **or** the model weights extracted and a local build.

### Option A — pull prod image

```powershell
$env:CLOUDSDK_PYTHON = "C:\Users\<you>\AppData\Local\Programs\Python\Python312\python.exe"
gcloud auth login
gcloud auth configure-docker asia-southeast2-docker.pkg.dev
cd docker
docker compose pull billsense-api
docker compose up -d billsense-api
```

### Option B — local build

```powershell
cd docker
extract-models.bat                      # one-time
# put serviceAccountKey.json into docker/app/
docker compose build billsense-api
docker compose up -d billsense-api
```

Verify:

```powershell
curl http://localhost:8080/api/health
```

## 7. Common issues

| Symptom | Fix |
|---|---|
| `Python was not found; run without arguments...` | Disable WindowsApps alias (§1) or set `CLOUDSDK_PYTHON` |
| Compose tries to pull `billsense-api` even when you only asked for admin/gitnexus | Already fixed — `billsense-api` removed from `depends_on` in `docker-compose.yml` |
| Admin context path errors | Already fixed — context is `"../BillSense Admin/admin-panel"` in `docker-compose.yml` |
| `https://billsense.dev-environment.site/` shows "It works! NodeJS 22.22.2" | The cPanel Node app is shadowing static files. Stop+remove it in cPanel → Setup Node.js App. Then push to `main` to trigger the deploy workflow |
| Dashboard's GitNexus page says "OpenAI API key not configured" | Add `VITE_OPENAI_API_KEY` to `.env` and rebuild (`npm run build`) |
| `/api/dev/status` returns no emulator | ADB not on PATH or no AVD running; check `adb devices` |
| Backend says `models_loaded: false` | Normal on cold health check — models lazy-load on first scan |

## 8. Recommended tooling

- **Android Studio** for the Java app — JDK 17, Gradle JDK 17, ViewBinding-aware
- **VS Code** for the admin panel and dev-server — Vue + Volar extensions
- **Postman / Insomnia / Bruno** for hitting `/api/*` and inspecting WebSocket frames
- **`mitmproxy`** for capturing Android ↔ Cloud Run traffic during debugging

## 9. MCP servers (optional, Claude Code users)

Add to `~/.claude/settings.json`:

```jsonc
{
  "mcpServers": {
    "claude-in-mobile": { "command": "npx", "args": ["-y", "claude-in-mobile"] }
  }
}
```

`claude-in-mobile` lets Claude drive the emulator via ADB (screenshots, taps, text). Restart Claude Code to pick it up.

## 10. Running the test suite

```bash
# Android instrumented tests (needs running emulator)
cd BillSense
.\gradlew connectedDebugAndroidTest
```

There is no JS or Python test suite wired up yet — see [audit report](../pdf-output/BillSense_Deep_Analysis.pdf) §8.
