# CI/CD: cPanel Deploy

## What it does
On every push to `main` that touches `BillSense Admin/admin-panel/`, GitHub Actions builds the Vue dashboard and uploads `dist/` to your cPanel host via FTPS.

## One-time setup

### 1. Disable the Node.js app in cPanel
cPanel → **Setup Node.js App** → find the app pointing at `billsense.dev-environment.site` → **Stop** and **Remove**.
(The "It works!" page comes from Phusion Passenger's default Node app — kill it so static files serve.)

### 2. Note your document root
cPanel → **File Manager** → find where `billsense.dev-environment.site` points. Common values:
- `public_html/billsense` (subfolder)
- `public_html` (root domain)
- `/home/<cpaneluser>/billsense.dev-environment.site`

### 3. Add GitHub repo Secrets
Repo → Settings → Secrets and variables → Actions → **New repository secret**:

| Secret | Value |
|---|---|
| `CPANEL_FTP_HOST` | `ftp.dev-environment.site` (or your cPanel FTP server, often the same as the domain) |
| `CPANEL_FTP_USER` | Your cPanel FTP username (often `cpaneluser@dev-environment.site`) |
| `CPANEL_FTP_PASS` | Your cPanel/FTP password |
| `VITE_GEMINI_API_KEY` | (optional) Gemini key for GitNexus |
| `VITE_OPENAI_API_KEY` | (optional) OpenAI key for GitNexus |
| `VITE_GITHUB_PAT` | (optional) GitHub PAT for GitNexus auto-clone |

### 4. Add GitHub repo Variables (same page, **Variables** tab)
| Variable | Value |
|---|---|
| `CPANEL_REMOTE_DIR` | `/billsense.dev-environment.site/public/` (must start and end with `/`) |
| `VITE_GITNEXUS_REPO` | `UserDevAccount1/Billsense-Project` (or your repo) |

### 5. FTP credentials
A scoped deploy FTP account named `deploy@dev-environment.site` was already created (homedir `/home/devenvir/billsense.dev-environment.site`). The password is in `tools/cpanel-mcp/.deploy-credentials.local.txt` — that file is gitignored. Copy the password into the `CPANEL_FTP_PASS` repo secret. **Do not commit it.**

If you ever lose the password, regenerate it in cPanel → FTP Accounts → Change Password for `deploy@dev-environment.site`, and update the GitHub secret.

## How to deploy
```bash
git push origin main
```
Or trigger manually: Actions tab → **Deploy Admin Panel to cPanel** → **Run workflow**.

## Verifying
After the run finishes, hit https://billsense.dev-environment.site/ — should serve the Vue dashboard, not "It works!".

## Why FTPS not SFTP
cPanel ships FTPS out of the box; SFTP requires SSH access (often disabled on shared plans). If you have SSH, swap the action for `appleboy/scp-action` and use `secrets.CPANEL_SSH_KEY` instead.

## Why no MCP?
There is no production-quality cPanel MCP server. cPanel's API (UAPI/WHM API) is REST-over-HTTP-Basic-Auth — building one is feasible but overkill for a single deploy job. GitHub Actions FTPS is the de-facto pattern and what every cPanel-hosted SPA uses.
