# 06 — Status: what works, what's local-only, what's left

Snapshot: 2026-06-17, HEAD `0b6d2d7` (main consolidated from thesis-validator +
TFLite reconciliation). Update this when state changes.

## Dashboard pages (all behind login `Billsense`/`admin`)

| Page | Route | Status | Data source |
|---|---|---|---|
| Dashboard | `/` | ✅ works everywhere | connection tiles |
| Billy AI | `/billy` | ✅ works everywhere | `/api/gemini/*` proxy (Flash tier; Pro needs billing) |
| Scan Reports | `/scan-reports` | ✅ works | `/api/db/*` — real bill-image gallery (annotatedImageUrl) + session reports |
| Users | `/users` | ✅ works | `/api/db/*` `Users` |
| ML Models | `/ml-models` | ✅ works | `ml_config` + Cloud Run `/api/health` |
| Cases | `/cases` | ✅ works, **admin CRUD** | `/api/db/*` `Cases` (status/archive/delete) |
| Voting Posts | `/voting-posts` | ✅ works, **admin CRUD** | `/api/db/*` `Voting Posts` (poll toggle/status/delete/comment moderation) |
| Connection Health | `/connection-health` | ✅ works | mixed; Docker card = neutral "optional" |
| GitNexus | `/gitnexus` | ✅ works | iframe → gitnexus.vercel.app + AI repo analyzer via gemini proxy |
| Settings | `/settings` | ✅ works | `Announcements` + env readout |
| App Testing | `/app-testing` | ⚠️ local-only | dev-server `localhost:3003` |
| APK Management | `/apk-management` | ⚠️ local-only | dev-server `localhost:3003` |
| Login | `/login` | ✅ works http+https | pure-JS SHA-256 |

"local-only" = functional only when the dashboard is opened over
`http://localhost:3000` or `:3001` with `node dev-server.mjs` running. On the
live site they show a clear blue "Local developer tool" banner (by design — a
remote browser physically cannot reach the developer's localhost).

## Infrastructure

| Thing | Status |
|---|---|
| cPanel live site (https + http) | ✅ healthy, login works both schemes |
| Firebase Hosting mirror | ✅ healthy |
| cPanel Node proxy `/api/gemini/*` `/api/db/*` | ✅ healthy, SA + Gemini key configured |
| Cloud Run ML API | ❌ DOWN 503 — `BILLING_DISABLED` on `bill-sense-aec6b`; re-enable billing in console to restore (rev `00013` ready, fast-fails only because billing off) |
| Local ML mirror (`localhost:8080`) | ✅ running this session (9.93 GB image pulled) — optional |
| Firebase RTDB | ✅ read public, **write locked** (anon write → 401) |
| CI/CD (push → cPanel) | ✅ clean-slate FTPS, reliable |
| Docker `billsense-admin` | ✅ rebuilt to current; remember it's manual |
| gcloud | ✅ authed as Firebase SA on `bill-sense-aec6b` |

## What's intentionally NOT done / open items

1. **Gemini Pro tier** — free-tier Pro quota is ~50/day; Billy falls back to
   `gemini-3-flash-preview` (newer than 2.5 Pro, fine). For consistent Pro:
   enable billing on the Gemini API's GCP project.
2. **RTDB read still public** — `Users.password`/email/phone readable by anyone
   with the DB URL. User chose write-lock-only. To fully close: `auth != null`
   read rules + verify the Android app authenticates before all reads (it has
   Firebase Auth/LoginActivity so likely safe — needs an app smoke-test).
3. **Credential rotation** — all chat-exposed secrets should be rotated
   (`05-CREDENTIALS.md`). Zero-downtime.
4. **Legacy ML API** (`ph-currency-fast-api…asia-east1`) — still live, candidate
   for retirement; decide and remove one of the two API base URLs.
5. **Soft login gate** — client-side only; not a security boundary. Upgrade
   path: server-side session in the cPanel proxy or Firebase Auth.
6. **Scan deep-dive** — Scan Reports shows per-scan image cards + counts; a
   full per-scan drilldown / per-user history view is not built.
7. **Firebase deploy is manual** — could add a second CI job (needs a Firebase
   CI token / service account) so cPanel + Firebase deploy from one push.
8. **HTTPS not enforced at the edge** — the app.js 301 only fires if
   `x-forwarded-proto` is present; iFastNet's proxy doesn't reliably send it.
   Login works over http anyway (pure-JS sha256). True edge HTTPS-only would
   need a cPanel/Cloudflare redirect rule.

## Quick health-check script (any agent can run)

```python
# python (use the explicit Python312 path on Windows)
import urllib.request, ssl, json
ctx=ssl.create_default_context(); ctx.check_hostname=False; ctx.verify_mode=ssl.CERT_NONE
H='https://billsense.dev-environment.site'
print('gemini/health', json.load(urllib.request.urlopen(H+'/api/gemini/health',timeout=10,context=ctx)))
print('db/health    ', json.load(urllib.request.urlopen(H+'/api/db/health',timeout=10,context=ctx)))
r=urllib.request.urlopen(urllib.request.Request(H+'/api/db/get',
  data=json.dumps({'path':'Users'}).encode(),
  headers={'Content-Type':'application/json'}),timeout=15,context=ctx)
print('db Users count', len(json.load(r).get('data') or {}))
# anon-write must be DENIED:
try:
  urllib.request.urlopen(urllib.request.Request(
    'https://bill-sense-aec6b-default-rtdb.firebaseio.com/_x.json',
    data=b'1',method='PUT'),timeout=8,context=ctx)
  print('SECURITY REGRESSION: anon write OPEN')
except urllib.error.HTTPError as e:
  print('anon write DENIED', e.code, '(secure)')
```
Expected: `keyConfigured:true`, `saConfigured:true`, Users count = 3,
anon write DENIED 401.
