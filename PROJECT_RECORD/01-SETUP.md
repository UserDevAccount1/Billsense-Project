# 01 — Setup (dev machine from zero)

## Prerequisites / CLIs

| Tool | Version used | Purpose | Notes |
|---|---|---|---|
| Node.js | 20 LTS (24 also works locally) | admin panel, dev-server, gitnexus-agent | |
| npm | 11.x | package mgmt | |
| Docker Desktop | 24+ | admin/gitnexus/ML containers | |
| Firebase CLI | 15.x | Firebase Hosting + RTDB rules deploy | `npm i -g firebase-tools` |
| gcloud SDK | 561.x | pull ML image, GCP ops | **see Python gotcha + SA-auth in `04-MCP-AND-SKILLS.md`** |
| Python | 3.12 | utility/verification scripts | path: `C:\Users\<u>\AppData\Local\Programs\Python\Python312\python.exe` |
| GitHub CLI `gh` | 2.92 | CI/CD secret mgmt, run watching | `gh auth login` OR `GH_TOKEN` env |
| JDK | 17 (Microsoft/Adoptium) | Android build | only for the Android app |
| Android SDK | platform 35, build-tools 35.0.1 | Android build | only for the Android app |

### Windows Python gotcha (recurring)

`python`/`python3` resolve to a WindowsApps shim that opens the Store and breaks
gcloud. Always use the explicit interpreter path
`C:\Users\<user>\AppData\Local\Programs\Python\Python312\python.exe`, or set
`CLOUDSDK_PYTHON` to it for gcloud.

## Repo layout

```
Billsense-Project/
├── BillSense/                       Android app (Gradle, Java 11)
├── BillSense Admin/
│   ├── admin-panel/                 Vue 3 + Vite 6 dashboard (the live site)
│   │   ├── src/views/               Dashboard, Billy, ScanReports, Users,
│   │   │                            Cases, VotingPosts, MLModels, Settings,
│   │   │                            ConnectionHealth, GitNexus, ApkManagement,
│   │   │                            AppTesting, Login
│   │   ├── src/services/            gemini.js, db.js, auth.js, healthCheck.js,
│   │   │                            modelConnectionCheck.js, firebase.js
│   │   ├── dev-server.mjs           local build/ADB/emulator API (port 3003)
│   │   └── dist/                    build output (gitignored)
│   └── database.rules.json          RTDB security rules (deployed via firebase)
├── docker/
│   ├── docker-compose.yml           billsense-api, billsense-admin, gitnexus-agent
│   ├── app/main.py                  FastAPI ML source (Cloud Run image source)
│   └── gitnexus-agent/              Puppeteer agent (port 3002)
├── tools/cpanel-mcp/
│   ├── app.js.production            THE cPanel Node backend (static + proxies)
│   ├── ringo380-cpanel-mcp/         3rd-party cPanel MCP (gitignored, cloned)
│   ├── set-github-secrets.ps1       one-shot CI/CD secret setup
│   ├── .deploy-credentials.local.txt  FTP creds (gitignored)
│   └── .firebase-sa.local.json      Firebase service account (gitignored)
├── .github/workflows/
│   └── deploy-admin-cpanel.yml      CI/CD: build + FTPS clean-slate to cPanel
├── firebase.json / .firebaserc      Firebase Hosting + database config
├── PROJECT_RECORD/                  ← this knowledge folder
├── AGENTS.md / CLAUDE.md            agent entrypoints
└── Resources/Documents/Credentials.txt   local credential handoff (gitignored)
```

## First-time local setup

```bash
# 1. clone
git clone https://github.com/UserDevAccount1/Billsense-Project.git
cd Billsense-Project

# 2. admin panel deps
cd "BillSense Admin/admin-panel"
npm ci

# 3. run the dashboard locally (Vite, hot reload, port 3001)
npm run dev          # http://localhost:3001  ← primary local dev surface

# 4. (optional) local APK/App-Testing backend
node dev-server.mjs  # port 3003 — only needed for App Testing / APK pages

# 5. (optional) Docker stack
cd ../../docker
docker compose up -d billsense-admin gitnexus-agent
# billsense-admin  -> http://localhost:3000 (nginx-served prod image)
# gitnexus-agent   -> http://localhost:3002
```

The dashboard talks to the **live cPanel proxy** for AI + DB even when run
locally (CORS allowlist includes localhost:3000/3001/5173). So Billy and the
data pages work locally without any local backend — they hit
`https://billsense.dev-environment.site/api/*`.

## Local-only tools

App Testing and APK Management need `dev-server.mjs` on `localhost:3003`. They
only function when the dashboard is opened over **http** (localhost:3000/3001) —
an https page cannot call http://localhost (mixed content). On the live site
they show a "local developer tool" banner by design.

## Build commands

```bash
# admin panel production build
cd "BillSense Admin/admin-panel"
echo "VITE_GITNEXUS_REPO=UserDevAccount1/Billsense-Project" > .env.production
npm run build        # -> dist/
rm .env.production

# Android debug APK
cd BillSense
./gradlew assembleDebug
```

> Note: `VITE_GEMINI_API_KEY` is intentionally NOT injected at build time —
> the Gemini key is server-side only (see `02-ARCHITECTURE.md`). Vite would
> bake any `VITE_*` var into the public bundle and Google auto-revokes leaked
> keys within minutes (this happened twice — see `07-CHANGELOG.md`).
