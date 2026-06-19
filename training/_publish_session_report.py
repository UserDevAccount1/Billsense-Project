"""Publish the 2026-06-19 session report to RTDB session_reports so it shows in
the admin Scan Reports -> Development Session Reports section."""
import requests, json
from datetime import datetime, timezone
PATCH = "https://billsense.dev-environment.site/api/db/patch"
GET = "https://billsense.dev-environment.site/api/db/get"

key = "session_2026_06_19_scanreports_deepscan"
record = {
    "title": "BillSense — Deep Scan, Counterfeit Fix, Scan Reports & Maps",
    "author": "Claude AI Agent (Opus 4.8)",
    "date": datetime.now(timezone.utc).isoformat(),
    "status": "Complete",
    "summary": ("Server v17.15->v17.19, app 1.5.9->1.5.10. Deep accumulated live scan; fixed "
                "genuine bills wrongly flagged COUNTERFEIT (0/30); fixed Scan Reports not showing "
                "latest user scans (server saved to Firestore but admin reads RTDB) + per-user "
                "attribution (user_id was a query param, app sends form field); admin per-user "
                "filter + 12s auto-refresh; refreshed Maps key + shipped 1.5.10. Cases map blank "
                "traced to the API key's Android restriction in the Cloud Console (app side verified correct)."),
    "issuesFound": [
        {"severity": "high", "file": "docker/app/main.py", "category": "verdict", "item": "Genuine bills flagged COUNTERFEIT — noisy securitycf/EVP false_* markers (3/30)"},
        {"severity": "high", "file": "docker/app/firebase_config.py", "category": "data", "item": "Scans stored in Firestore but admin reads Realtime DB — admin never saw new scans"},
        {"severity": "high", "file": "docker/app/main.py", "category": "data", "item": "user_id read as query param while app sends form field — all scans saved as 'anonymous'"},
        {"severity": "medium", "file": "ScanReports.vue", "category": "ux", "item": "Scan Reports only fetched on mount — no live update after a scan"},
        {"severity": "medium", "file": "docker/app/main.py", "category": "verdict", "item": "Live COMPLETE_SCAN hardcoded counterfeit_indicators={} — final live verdict could never catch a fake"},
        {"severity": "low", "file": "Cloud Console", "category": "config", "item": "Cases map blank — API key Android restriction (SHA-1/package) not matching; not an app bug"},
    ],
    "fixesApplied": [
        {"file": "docker/app/main.py", "category": "scan", "item": "Deep accumulated live scan — union of features + persistent-forgery rule (v17.16)"},
        {"file": "docker/app/main.py", "category": "verdict", "item": "Corroboration rule + higher false-marker confidence gates — 0/30 genuine flagged (v17.17)"},
        {"file": "docker/app/firebase_config.py", "category": "data", "item": "Initialise RTDB + store_scan_rtdb(); mirror every scan into the nodes the admin reads (v17.18)"},
        {"file": "docker/app/main.py", "category": "data", "item": "user_id: str = Form('anonymous') on standard/multi/video — real per-user attribution (v17.19)"},
        {"file": "ScanReports.vue", "category": "ux", "item": "Per-user filter + 12s auto-refresh with live indicator; deployed to Firebase Hosting"},
        {"file": "BillSense/local.properties + build.gradle.kts", "category": "app", "item": "Refreshed Maps API key; app 1.5.10 (versionCode 25)"},
    ],
    "checklist": [
        {"item": "Deep accumulated live scan deployed + verified (v17.16)", "done": True},
        {"item": "Genuine->COUNTERFEIT fixed, verified 0/30 (v17.17)", "done": True},
        {"item": "Scan Reports RTDB mirror deployed + verified (v17.18)", "done": True},
        {"item": "Per-user attribution fixed + verified (v17.19)", "done": True},
        {"item": "Admin per-user filter + auto-refresh deployed", "done": True},
        {"item": "App 1.5.10 built + installed + release record published", "done": True},
        {"item": "All changes pushed to GitHub", "done": True},
        {"item": "Cases map: finish API-key Android restriction in Console", "done": False},
    ],
    "features": [
        {"name": "Deep accumulated live scan"},
        {"name": "Real counterfeit corroboration verdict"},
        {"name": "Server-side scan persistence (RTDB mirror)"},
        {"name": "Per-user scan attribution + admin filter"},
        {"name": "Scan Reports auto-refresh"},
    ],
}
r = requests.post(PATCH, json={"path": f"session_reports/{key}", "data": record}, timeout=25)
print("PATCH:", r.status_code, r.text[:120])
v = requests.post(GET, json={"path": f"session_reports/{key}"}, timeout=25).json().get("data")
print("VERIFY title:", (v or {}).get("title"), "| status:", (v or {}).get("status"))
