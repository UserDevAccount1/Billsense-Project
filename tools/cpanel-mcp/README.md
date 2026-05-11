# cPanel MCP setup

BillSense uses the **[ringo380/cpanel-mcp](https://github.com/ringo380/cpanel-mcp)** server (37 tools: files, databases, email, domains, cron, SSL, DNS, FTP, backups). It's registered in [`.mcp.json`](../../.mcp.json) under the name `cpanel`.

The cloned repo is **gitignored** — every developer clones and builds it on first setup. This keeps third-party code out of our git history.

## One-time setup

```powershell
# from repo root
cd tools
git clone --depth=1 https://github.com/ringo380/cpanel-mcp.git ringo380-cpanel-mcp
cd ringo380-cpanel-mcp
npm install
npm run build
```

The build emits `dist/index.js`, which is what `.mcp.json` points at.

## Credentials

The MCP reads from these env vars (set them in your shell before launching `claude`):

| Var | Required | Value |
|---|---|---|
| `CPANEL_USERNAME` | **yes** | Your cPanel account username (e.g. `epiz_########` on iFastNet free tier) |
| `CPANEL_API_TOKEN` | **yes** | API token from cPanel → Security → Manage API Tokens |
| `CPANEL_HOSTNAME` | no (defaulted in .mcp.json) | `billsense.dev-environment.site` |
| `CPANEL_PORT` | no (defaulted in .mcp.json) | `2083` |
| `CPANEL_SSL` | no (defaulted in .mcp.json) | `true` |

### Windows PowerShell

```powershell
$env:CPANEL_USERNAME  = "<your-cpanel-username>"
$env:CPANEL_API_TOKEN = "<your-api-token>"
claude
```

### macOS / Linux

```bash
export CPANEL_USERNAME="<your-cpanel-username>"
export CPANEL_API_TOKEN="<your-api-token>"
claude
```

To persist, drop these into your shell profile — but **don't ever commit them** and rotate the token after any chat where you paste it.

## Verifying

After launching Claude with the env vars set, ask:

> List the SSL certificates on my cPanel account

Claude will call `list_ssl_certificates` on the cPanel MCP and surface the response.

Quick gut check that doesn't depend on the username being right: ask for the tool list. You should see 37 tools — `list_files`, `list_databases`, `create_database`, `list_email_accounts`, `create_email_account`, `list_domains`, `list_cron_jobs`, `add_cron_job`, `get_disk_usage`, `create_backup`, `list_backups`, `list_ssl_certificates`, `upload_ssl_certificate`, `install_ssl_certificate`, `generate_csr`, `list_subdomains`, `create_subdomain`, `list_dns_records`, `add_dns_record`, `edit_dns_record`, `list_ftp_accounts`, `create_ftp_account`, `list_database_users`, `set_database_privileges`, `upload_file`, `download_file`, `delete_file`, `create_directory`, and friends.

## Security

- MCP runs as a local stdio process — no exposed port, no daemon.
- Credentials live in shell env, never in the repo. `.mcp.json` uses `${CPANEL_USERNAME}` / `${CPANEL_API_TOKEN}` placeholders only.
- Always rotate the token after a chat where it appears: cPanel → Security → Manage API Tokens → Revoke → Create new.
- iFastNet/InfinityFree free tier may rate-limit or block some UAPI modules; don't expect everything in the 37-tool list to succeed there.

## If you need a tool not in the 37

The MCP is open source — fork ringo380/cpanel-mcp, add the tool, rebuild, push back upstream if it's broadly useful. For one-off calls in this project, the raw `Authorization: cpanel <user>:<token>` request against `https://billsense.dev-environment.site:2083/execute/<Module>/<function>` works directly with `curl` or Python `urllib`.
