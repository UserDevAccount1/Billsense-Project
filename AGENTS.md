# AGENTS.md — read before doing anything

This file is the entrypoint for any AI agent or LLM (Claude Code, Codex,
Cursor, Gemini, etc.) working on **BillSense**.

## ⛔ Step 0: read the knowledge record FIRST

Everything you need is in [`PROJECT_RECORD/`](PROJECT_RECORD/). Read it in order:

1. [`PROJECT_RECORD/README.md`](PROJECT_RECORD/README.md) — orientation + golden rules
2. [`PROJECT_RECORD/01-SETUP.md`](PROJECT_RECORD/01-SETUP.md) — CLIs, prerequisites, local run, build
3. [`PROJECT_RECORD/02-ARCHITECTURE.md`](PROJECT_RECORD/02-ARCHITECTURE.md) — how everything connects
4. [`PROJECT_RECORD/03-DEPLOYMENT.md`](PROJECT_RECORD/03-DEPLOYMENT.md) — deploy runbook (cPanel/Firebase/Docker/ML)
5. [`PROJECT_RECORD/04-MCP-AND-SKILLS.md`](PROJECT_RECORD/04-MCP-AND-SKILLS.md) — MCP, skills, the gcloud-via-SA unlock
6. [`PROJECT_RECORD/05-CREDENTIALS.md`](PROJECT_RECORD/05-CREDENTIALS.md) — credential map + rotation
7. [`PROJECT_RECORD/06-STATUS.md`](PROJECT_RECORD/06-STATUS.md) — what works / local-only / open items
8. [`PROJECT_RECORD/07-CHANGELOG.md`](PROJECT_RECORD/07-CHANGELOG.md) — history + recurring lessons

Do not start "discovering" the project from scratch — it's all written down.

## Non-negotiable rules

1. **Local-first.** Verify on Vite `localhost:3001` (browser preview) before
   any push. Pushing to `main` auto-deploys to the production cPanel site.
2. **Never commit secrets.** Real keys are gitignored / server-side only.
3. **Docker `billsense-admin` is manual** — rebuild it after frontend changes
   or it serves a stale dashboard. Three deploy surfaces (cPanel CI, Firebase
   manual, Docker manual) — keep them in parity.
4. **Gemini/API keys must NEVER go in `VITE_*`** — they get baked into the
   public bundle and auto-revoked. Use the server-side cPanel proxy.
5. **gcloud/firebase interactive login is blocked here.** Use the Firebase
   service account: `gcloud auth activate-service-account
   --key-file=tools/cpanel-mcp/.firebase-sa.local.json`. Use `GH_TOKEN` for `gh`.
6. **Append to `PROJECT_RECORD/07-CHANGELOG.md`** for anything you change.

## Fast facts

- Live: https://billsense.dev-environment.site (cPanel, has the proxy) and
  https://bill-sense-aec6b.web.app (Firebase mirror).
- Dashboard login: `Billsense` / `admin`.
- cPanel `app.js` (source: `tools/cpanel-mcp/app.js.production`) is the
  backend-for-frontend: serves the SPA + `/api/gemini/*` + `/api/db/*` with
  server-only keys.
- RTDB: read public, write `auth != null`; dashboard writes via the SA proxy.
- Repo: `https://github.com/UserDevAccount1/Billsense-Project`, branch `main`.
