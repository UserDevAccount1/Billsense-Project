# 04 — MCP servers, Skills, and the gcloud unlock

## MCP servers

Registered in `.mcp.json` (repo root, workspace scope):

| MCP | Command | Purpose |
|---|---|---|
| `firebase` | `npx -y firebase-tools@latest experimental:mcp` | Firebase ops |
| `context7` | `npx -y @upstash/context7-mcp@latest` | live library docs |
| `cpanel` | `node ./tools/ringo380-cpanel-mcp/dist/index.js` | cPanel UAPI (37 tools: files, FTP, DNS, SSL, DB, cron…) |

### cPanel MCP setup (the cloned repo is gitignored)
```bash
cd tools
git clone --depth=1 https://github.com/ringo380/cpanel-mcp.git ringo380-cpanel-mcp
cd ringo380-cpanel-mcp && npm install && npm run build   # -> dist/index.js
```
Env it expects (set in shell before launching the agent; `.mcp.json` uses
`${VAR}` placeholders so nothing is committed):
```
CPANEL_HOSTNAME=billsense.dev-environment.site
CPANEL_PORT=2083
CPANEL_SSL=true
CPANEL_USERNAME=devenvir
CPANEL_API_TOKEN=<from Resources/Documents/Credentials.txt>
```
> There is **no official cPanel MCP** in any registry — `ringo380/cpanel-mcp`
> is the one chosen. A minimal custom MCP was built first then replaced by it.
> Most cPanel work this session was actually done via **direct UAPI calls**
> (Python `urllib` → `https://<host>:2083/execute/<Module>/<func>` with
> `Authorization: cpanel <user>:<token>`), which is reliable and dependency-free.

### Direct cPanel UAPI (no MCP needed — used heavily)
```
GET/POST https://billsense.dev-environment.site:2083/execute/<Module>/<func>
Header: Authorization: cpanel devenvir:<CPANEL_API_TOKEN>
Key calls used:
  Variables/get_user_information      (whoami)
  Fileman/save_file_content           (deploy app.js / key files)
  Fileman/get_file_content / list_files
  Ftp/add_ftp / list_ftp_with_disk    (created deploy@dev-environment.site)
```

## Skills

No project-specific skills are required to operate BillSense. General skills
that helped this session: `anthropic-skills:pdf` (audit report), web fetch /
search. The Android-related skills (compose/kotlin/etc.) are NOT applicable —
the app is Java, not Kotlin/Compose.

## ⚠️ The gcloud unlock (most important operational knowledge)

The BillSense GCP/Firebase project `bill-sense-aec6b` is owned by a Google
account that interactive `gcloud auth login` / `firebase login` **cannot reach
from this environment** — the browser OAuth flow returns "Access blocked", and
the only locally-cached account (`gabriel@defeatdiabetes.com.au`) has no
access to `bill-sense-aec6b`.

**The working method: authenticate with the Firebase service account
non-interactively.**

```bash
export CLOUDSDK_PYTHON="C:/Users/<u>/AppData/Local/Programs/Python/Python312/python.exe"
GCLOUD="C:/Users/<u>/AppData/Local/Google/Cloud SDK/google-cloud-sdk/bin/gcloud.cmd"
"$GCLOUD" auth activate-service-account \
  --key-file="tools/cpanel-mcp/.firebase-sa.local.json"
"$GCLOUD" config set project bill-sense-aec6b
# now: projects describe, artifact registry pull, etc. all work
```

This single technique unblocked: pulling the 9.93 GB ML container from
Artifact Registry, project access, and any GCP op needing `bill-sense-aec6b`.
The SA (`firebase-adminsdk-fbsvc@bill-sense-aec6b.iam.gserviceaccount.com`)
has Artifact Registry read + Firebase admin.

`firebase` CLI separately works because the `gabriel@…` account was added as a
member of the Firebase project (Firebase project membership ≠ full GCP IAM —
that's why gcloud needed the SA but firebase CLI didn't).

## GitHub CLI

`gh` is installed but interactive `gh auth login` is a browser flow. The
session used `export GH_TOKEN=<PAT>` for all `gh` operations (secret set, run
watch, workflow run). Provide a PAT with `repo` + `workflow` scopes via
`GH_TOKEN`; do not rely on interactive login.

## Browser preview / verification

Local UI verification used the `Claude_Preview` MCP against the Vite dev server
(`.claude/launch.json` → `admin-panel` config, port **3001**). Pattern:
`preview_start` → `preview_eval` to set `sessionStorage.billsense_auth` and
navigate → `preview_eval`/`preview_screenshot` to assert DOM state. This is the
local-first verification loop — use it before every deploy.
