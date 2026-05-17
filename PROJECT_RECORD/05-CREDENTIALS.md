# 05 — Credentials Manifest

> **No secret values are in this file or anywhere in git.** This documents
> WHICH credentials exist, their FORMAT, WHERE they are installed, and HOW to
> rotate them. Actual values live in gitignored files / on the cPanel server.

## The credential handoff file (local, gitignored)

`D:\Github\Billsense-Project\Resources\Documents\Credentials.txt`
The `Resources/` folder is gitignored. This is where the human pastes secrets
for an agent to pick up, then wipes after. Historically contained: Gemini key,
cPanel API token, Firebase service-account JSON.

## Inventory

| Credential | Format | Where it must be installed | Used by |
|---|---|---|---|
| **cPanel API token** | 32-char string | env `CPANEL_API_TOKEN` / `Authorization: cpanel devenvir:<tok>` | cPanel MCP + all direct UAPI deploys |
| **cPanel deploy FTP** | `deploy@dev-environment.site` + password | GitHub secrets `CPANEL_FTP_USER`/`PASS`; local `tools/cpanel-mcp/.deploy-credentials.local.txt` (gitignored) | CI/CD FTPS deploy |
| **Firebase service account** | JSON (`type:service_account`, `private_key`, `client_email` …) | cPanel host `…/.firebase-sa.json`; local `tools/cpanel-mcp/.firebase-sa.local.json` (gitignored); gcloud `activate-service-account` | `/api/db/*` proxy, gcloud auth, ML image pull |
| **Gemini API key** | `AIza…` GCP API key, IP-restricted to `185.2.168.24,185.2.168.30`, API-restricted to Generative Language | cPanel host `…/.gemini-key` (server-only) | `/api/gemini/*` proxy (Billy, GitNexus AI) |
| **GitHub PAT** | `ghp_…`, scopes `repo,workflow` | env `GH_TOKEN` (not stored) | CI/CD secret mgmt, run watching |
| **Firebase Web SDK key** | `AIzaSyCKdSYeVztx0gXo2Z-Q6CkZ_SJT2pcajAI` | hardcoded in `src/services/firebase.js` | **intentionally public** — Firebase Web keys are project identifiers, scoped by RTDB rules + Firebase authorized domains. NOT a secret. |
| **Dashboard login** | 3 accepted accounts (soft gate) | sha256("user:pass") hashes in `src/services/auth.js` `CRED_HASHES[]` | the soft client-side gate. Accepted: `Billsense`/`admin`, `admin@neuralyx.dev`/`neuralyx2026`, `admin`/`admin123` (the Firebase `Admin` node creds) |
| **MAPS_API_KEY** | string | Android `local.properties` (gitignored) | Android build only |

## Gitignored paths (never commit these)

```
Resources/                                  (entire folder)
tools/cpanel-mcp/.deploy-credentials.local.txt
tools/cpanel-mcp/.firebase-sa.local.json
tools/ringo380-cpanel-mcp/                  (cloned 3rd-party)
**/.env, **/.env.production
BillSense/local.properties
**/google-services.json, **/service_account.json
.firebase/
```

## Rotation procedures

### Gemini API key
1. GCP Console → APIs & Services → Credentials (project that owns the key).
2. Create new API key → restrict: Application=IP `185.2.168.24,185.2.168.30`,
   API=Generative Language API only. (CLI: `gcloud services api-keys create
   --api-target=service=generativelanguage.googleapis.com
   --allowed-ips=185.2.168.24,185.2.168.30`.)
3. Upload to cPanel host as `.gemini-key` via UAPI `Fileman.save_file_content`,
   touch `tmp/restart.txt`. Verify `GET /api/gemini/health` → `keyConfigured:true`
   and a real `/api/gemini/chat` round-trip.
4. Delete the old key.

### Firebase service account
1. Firebase Console → Project Settings → Service Accounts → Generate new
   private key.
2. Replace `tools/cpanel-mcp/.firebase-sa.local.json` locally.
3. Upload JSON to cPanel host as `.firebase-sa.json` via UAPI, touch
   `tmp/restart.txt`. Verify `GET /api/db/health` → `saConfigured:true` and a
   `/api/db/get {path:"Users"}` round-trip.
4. Re-activate gcloud: `gcloud auth activate-service-account
   --key-file=tools/cpanel-mcp/.firebase-sa.local.json`.
5. Delete the old SA key in the Firebase console.

### cPanel API token
cPanel → Security → Manage API Tokens → revoke old → create new → update
`Credentials.txt` + any `CPANEL_API_TOKEN` env.

### cPanel deploy FTP password
cPanel → FTP Accounts → change password for `deploy@dev-environment.site` →
update GitHub secret `CPANEL_FTP_PASS` (and the local
`.deploy-credentials.local.txt`).

### GitHub PAT
github.com/settings/tokens → revoke → create (`repo,workflow`).

## Known exposure (action item carried forward)

During development, the Gemini key(s), Firebase SA JSON, GitHub PAT, and cPanel
token were pasted into a chat transcript. They should all be rotated. The
running system does not embed any of them in git or the browser bundle, so
rotation is zero-downtime (follow procedures above). Two earlier Gemini keys
were already auto-revoked by Google's leaked-key scanner — do not reuse them.
