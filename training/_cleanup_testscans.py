"""Remove ONLY the test scan records created during this debugging session
(the 'anonymous' node + the synthetic TESTUSER node) so the admin Scan Reports
shows only real user scans. The 'anonymous' node did not exist before today's
tests; real scans live under the Firebase push-id user keys, which are untouched."""
import requests
DEL = "https://billsense.dev-environment.site/api/db/delete"
GET = "https://billsense.dev-environment.site/api/db/get"

targets = [
    "Standard Scan/anonymous",
    "Standard Scan/TESTUSER_attrib_check",
]
for path in targets:
    before = requests.post(GET, json={"path": path}, timeout=25).json().get("data")
    n = len(before) if isinstance(before, dict) else 0
    r = requests.post(DEL, json={"path": path}, timeout=25)
    print(f"deleted {path!r} ({n} records) -> HTTP {r.status_code} {r.text[:80]}")

# Confirm remaining users are only the real ones
tree = requests.post(GET, json={"path": "Standard Scan"}, timeout=25).json().get("data") or {}
print("remaining userIds:", list(tree.keys()))
