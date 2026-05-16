# 03 — Deployment Runbook

Three independent deploy surfaces. After ANY frontend change, update all three
to keep parity (this caused repeated "old dashboard" confusion).

## A. Dashboard → cPanel (AUTOMATIC via CI/CD)

- Workflow: `.github/workflows/deploy-admin-cpanel.yml`
- Trigger: any push to `main` (no path filter — always deploys)
- Steps: `npm ci` → inject `.env.production` (VITE_GITNEXUS_REPO,
  VITE_OPENAI_API_KEY, VITE_GITHUB_PAT — **NOT** VITE_GEMINI_API_KEY) →
  `npm run build` → FTPS upload `dist/` with `dangerous-clean-slate: true`
  (full wipe+upload every time — the incremental sync drifted on iFastNet and
  froze cPanel on stale builds).
- Target: `CPANEL_REMOTE_DIR` = `/public/` (FTP user lands in the subdomain root)
- To deploy: `git push origin HEAD:main` then watch:
  ```bash
  export GH_TOKEN=<pat>
  gh run watch --repo UserDevAccount1/Billsense-Project
  ```

### CI/CD secrets/variables (GitHub repo Settings → Actions)
| Kind | Name | Value source |
|---|---|---|
| secret | `CPANEL_FTP_HOST` | `billsense.dev-environment.site` |
| secret | `CPANEL_FTP_USER` | `deploy@dev-environment.site` |
| secret | `CPANEL_FTP_PASS` | in `tools/cpanel-mcp/.deploy-credentials.local.txt` |
| variable | `CPANEL_REMOTE_DIR` | `/public/` |
| variable | `VITE_GITNEXUS_REPO` | `UserDevAccount1/Billsense-Project` |

One-shot setup: `pwsh -File tools/cpanel-mcp/set-github-secrets.ps1`
(needs `gh` authed or `$env:GH_TOKEN`).

## B. Dashboard → Firebase Hosting (MANUAL)

```bash
cd "BillSense Admin/admin-panel"
echo "VITE_GITNEXUS_REPO=UserDevAccount1/Billsense-Project" > .env.production
npm run build
rm .env.production
cd ../..
firebase deploy --only hosting --project bill-sense-aec6b --non-interactive
# -> https://bill-sense-aec6b.web.app
```
Firebase CLI auth: the `gabriel@defeatdiabetes.com.au` account was granted
member access to `bill-sense-aec6b`, so `firebase deploy` works as-is. If it
stops working: `firebase login --reauth` as an account with project access.

## C. The cPanel Node backend (`app.js`) — MANUAL via cPanel UAPI

The CI/CD only deploys `/public/`. The Node app (`app.js`) and the server-only
key files are deployed separately via the cPanel UAPI (the
`tools/cpanel-mcp/ringo380-cpanel-mcp` MCP, or a direct script).

Source of truth: `tools/cpanel-mcp/app.js.production`
On the host: `/home/devenvir/billsense.dev-environment.site/app.js`

To deploy app.js (pattern used all session — Python + UAPI
`Fileman.save_file_content`, then touch `tmp/restart.txt` to restart
Passenger):
```python
# auth: 'cpanel devenvir:<CPANEL_API_TOKEN>'  (token in Credentials.txt)
# 1. PUT app.js.production -> /home/devenvir/billsense.dev-environment.site/app.js
# 2. PUT '' -> /home/devenvir/billsense.dev-environment.site/tmp/restart.txt
# 3. GET https://billsense.dev-environment.site/api/db/health  (verify)
```
Server-only key files on the host (NOT in git, NOT in /public):
- `/home/devenvir/billsense.dev-environment.site/.gemini-key`
- `/home/devenvir/billsense.dev-environment.site/.firebase-sa.json`

The cPanel site structure (Phusion Passenger Node app):
```
/home/devenvir/billsense.dev-environment.site/
├── app.js              ← the Node backend (static + proxies)
├── .htaccess           ← CloudLinux Passenger config (Node 22) — DO NOT edit
├── .gemini-key         ← server-only
├── .firebase-sa.json   ← server-only
├── public/             ← CI/CD deploys the SPA build here
└── tmp/restart.txt     ← touch to restart Passenger
```

## D. ML inference container (Cloud Run is production; local is a mirror)

Production ML is already on Cloud Run — nothing to deploy for normal work.

To run the **local ML mirror** (`billsense-api`, port 8080) — needed only to
make the "Docker Container" health card green locally:
```bash
# gcloud must be authed to bill-sense-aec6b — use the service account:
gcloud auth activate-service-account \
  --key-file=tools/cpanel-mcp/.firebase-sa.local.json
gcloud config set project bill-sense-aec6b
gcloud auth configure-docker asia-southeast2-docker.pkg.dev --quiet
gcloud auth print-access-token | docker login -u oauth2accesstoken \
  --password-stdin https://asia-southeast2-docker.pkg.dev
docker pull asia-southeast2-docker.pkg.dev/bill-sense-aec6b/billsense/billsense-api:latest
cd docker && docker compose up -d billsense-api   # 9.93 GB image
# verify: curl http://localhost:8080/api/health  -> status: healthy
```
To redeploy Cloud Run itself (rarely needed):
```bash
cd docker
gcloud builds submit --tag asia-southeast2-docker.pkg.dev/bill-sense-aec6b/billsense/billsense-api:latest
gcloud run deploy billsense-api --image <same> --region asia-southeast2
```

## E. Local Docker admin container (manual rebuild — IMPORTANT)

The `billsense-admin` container bakes the build at image-build time. It does
NOT pick up source changes, git pushes, or other deploys. After any frontend
change:
```bash
cd "D:/Github/Billsense-Project/.claude/worktrees/<wt>"   # or repo root
docker compose -f docker/docker-compose.yml build --no-cache billsense-admin
docker compose -f docker/docker-compose.yml up -d --force-recreate billsense-admin
# -> http://localhost:3000 now current
```
**For day-to-day local viewing prefer Vite (`localhost:3001`)** — it hot-reloads
instantly. Only rebuild the Docker container when you specifically want to test
the nginx-served prod image.

## F. RTDB security rules

```bash
firebase deploy --only database --project bill-sense-aec6b --non-interactive
# rules source: BillSense Admin/database.rules.json
# firebase.json must contain { "database": { "rules": "BillSense Admin/database.rules.json" } }
```

## Standard "ship a frontend change" sequence

1. Edit code. Verify on Vite `localhost:3001` (browser preview).
2. `git add … && git commit && git push origin HEAD:main` → CI/CD deploys cPanel.
3. `npm run build` + `firebase deploy --only hosting` → Firebase parity.
4. `docker compose build --no-cache billsense-admin && up -d --force-recreate`
   → Docker parity.
5. Verify all three serve the same asset hash + the change is live.
6. Update `PROJECT_RECORD/07-CHANGELOG.md`.
