# BillSense cPanel MCP

A tiny MCP server that wraps the cPanel UAPI so Claude Code can manage the BillSense hosting account directly.

## Tools exposed

| Tool | What it does |
|---|---|
| `cpanel_whoami` | Verify creds and dump account info, document root, quota |
| `cpanel_list_addon_domains` | List addon + parked domains |
| `cpanel_list_ftp_accounts` | List FTP accounts |
| `cpanel_create_ftp_account` | Create a scoped deploy FTP account |
| `cpanel_list_dir` | List a directory under the account root |
| `cpanel_node_apps` | List Phusion Passenger Node.js apps |
| `cpanel_disk_usage` | Disk + inode usage |
| `cpanel_raw_uapi` | Escape hatch — call any UAPI module/function directly |

## Configuration

Credentials come from environment variables, never from this repo:

| Var | Required | Default | What |
|---|---|---|---|
| `CPANEL_HOST` | yes | `billsense.dev-environment.site` | cPanel hostname |
| `CPANEL_PORT` | no | `2083` | SSL port |
| `CPANEL_USER` | **yes** | — | cPanel username (e.g. `epiz_XXXXXXXX` on iFastNet free tier) |
| `CPANEL_TOKEN` | **yes** | — | API token from cPanel → Manage API Tokens |
| `CPANEL_STRICT_SSL` | no | `true` | Set to `false` if your host serves a self-signed / mismatched cert |

## Setup

The MCP is already registered in `.mcp.json` (workspace scope). To use it, set the env vars in your shell before launching Claude Code:

### Windows PowerShell

```powershell
$env:CPANEL_USER = "<your-cpanel-username>"
$env:CPANEL_TOKEN = "<your-api-token>"
claude
```

### macOS / Linux

```bash
export CPANEL_USER="<your-cpanel-username>"
export CPANEL_TOKEN="<your-api-token>"
claude
```

Or persist them in your shell profile if you use this regularly.

## Verifying

In a Claude Code session with the env vars set, ask:

> Run cpanel_whoami

You should get back account info: home directory, document root, quota, suspension state.

## Security

- The MCP is a **local stdio server** — it doesn't open a network port. Only Claude Code on this machine can call it.
- Credentials live in shell env, not in this repo. `.mcp.json` references `${CPANEL_USER}` / `${CPANEL_TOKEN}` placeholders only.
- After every session that touched the token, rotate it: cPanel → Manage API Tokens → Revoke → Create new.

## Adding a new tool

Edit `server.mjs`, append to the `tools` array:

```js
{
  name: "cpanel_do_thing",
  description: "...",
  inputSchema: { type: "object", properties: { ... }, required: [...] },
  handler: (args) => callUapi("Module", "function_name", args),
},
```

For one-off calls you don't want to wrap, use `cpanel_raw_uapi` from inside Claude:

> Call cpanel_raw_uapi with module=Email function=list_pops
