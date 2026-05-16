# BillSense — Project Knowledge Record

> **Read this first.** This folder is the single source of truth for setting up,
> running, deploying, and maintaining BillSense. It exists so that any engineer
> or AI agent (Claude Code, Codex, Cursor, etc.) picking this project up cold has
> full context without re-discovering everything.
>
> **If you are an AI agent: read every file in this folder before acting.**

Last updated: 2026-05-16 · HEAD at time of writing: `dae818a`

---

## What BillSense is

Philippine-peso counterfeit-detection platform. Three surfaces:

| Surface | Tech | Where it runs |
|---|---|---|
| Android app | Java 11, CameraX, ViewBinding | User devices |
| ML inference API | Python, FastAPI, 6× YOLOv8 OBB models | Google Cloud Run (`bill-sense-aec6b`, asia-southeast2) |
| Admin dashboard | Vue 3 + Vite 6 SPA | cPanel (primary) + Firebase Hosting (mirror) |

Data plane: Firebase RTDB + Storage + FCM, project `bill-sense-aec6b`.

---

## The files in this record

| File | Read it when |
|---|---|
| [`01-SETUP.md`](01-SETUP.md) | Setting up a dev machine from zero — CLIs, prerequisites, local run |
| [`02-ARCHITECTURE.md`](02-ARCHITECTURE.md) | Understanding how everything connects (proxies, ports, data flow) |
| [`03-DEPLOYMENT.md`](03-DEPLOYMENT.md) | Deploying the dashboard / Node backend / ML container / Firebase |
| [`04-MCP-AND-SKILLS.md`](04-MCP-AND-SKILLS.md) | Configuring MCP servers, skills, and the gcloud-via-service-account trick |
| [`05-CREDENTIALS.md`](05-CREDENTIALS.md) | What credentials exist, where they live, how to rotate (no secret values) |
| [`06-STATUS.md`](06-STATUS.md) | What works, what's local-only, what still needs building |
| [`07-CHANGELOG.md`](07-CHANGELOG.md) | Version history, tech-stack versions, what changed and why |

Also at repo root: [`/AGENTS.md`](../AGENTS.md) (agent entrypoint) and
[`/CLAUDE.md`](../CLAUDE.md) (Claude Code config). Both point here.

---

## 60-second orientation

- **Dashboard login:** `Billsense` / `admin` (soft client-side gate, pure-JS SHA-256).
- **Live URLs:**
  - cPanel (primary, runs the Node proxy): https://billsense.dev-environment.site
  - Firebase Hosting (static mirror): https://bill-sense-aec6b.web.app
- **The cPanel Node app (`app.js`) is the backend-for-frontend.** It serves the
  static SPA AND proxies `/api/gemini/*` (Billy AI) and `/api/db/*` (Firebase
  RTDB) using server-only keys. The browser never holds a key.
- **Database is write-locked:** RTDB rules = public read, `auth != null` write.
  The dashboard writes via a Firebase **service account** held server-side in the
  cPanel proxy (admin = bypasses rules). Anonymous writes are denied.
- **CI/CD:** push to `main` → GitHub Actions auto-deploys the dashboard to cPanel
  (clean-slate FTPS). Firebase + Docker are **manual** (commands in `03-DEPLOYMENT.md`).
- **The gcloud unlock:** the BillSense GCP project is owned by a Google account
  that interactive `gcloud auth login` couldn't reach ("access blocked"). The
  working method is `gcloud auth activate-service-account --key-file=<SA json>`.
  See `04-MCP-AND-SKILLS.md`.

---

## Golden rules for anyone (esp. AI agents) working here

1. **Local-first.** Verify every change on Vite dev (`localhost:3001`) or the
   Docker container before pushing. The CI/CD auto-deploys — a bad push hits
   production.
2. **Never commit secrets.** All real keys live in gitignored files /
   server-side. `05-CREDENTIALS.md` describes locations, never values.
3. **The Docker container does NOT auto-update.** It bakes a build at image
   build time. Rebuild it explicitly after source changes (command in
   `03-DEPLOYMENT.md`) — this caused repeated "why is my dashboard old"
   confusion.
4. **Three build surfaces, keep them in parity:** cPanel (CI), Firebase
   (manual `firebase deploy`), Docker (manual rebuild). After any frontend
   change, update all three.
5. **Local-only tools** (App Testing, APK Management, local ML mirror) cannot
   work for a remote visitor — that's network reality, not a bug. They show a
   "local developer tool" banner on the live site by design.
6. **Update `07-CHANGELOG.md`** with anything you change so the next agent
   inherits the knowledge.
