"""Publish the BillSense 1.5.10 release record into RTDB apk_releases so the
admin APK Management page shows it. Proxy PATCH merges the child node."""
import requests, json
from datetime import datetime, timezone

PATCH = "https://billsense.dev-environment.site/api/db/patch"
GET   = "https://billsense.dev-environment.site/api/db/get"

key = "v1_5_10_main"
record = {
    "id": key,
    "version": "1.5.10",
    "versionCode": 25,
    "variant": "main",
    "buildType": "debug",
    "status": "testing",
    "buildDate": datetime.now(timezone.utc).isoformat(),
    "size": "55.4 MB",
    "minSdk": 24,
    "targetSdk": 35,
    "packageName": "com.app.billsense",
    "distribution": "Direct install (adb) to device G10000000042672",
    "downloadUrl": "",
    "description": ("Maps API key refreshed (Cases map) + runs against server v17.18: "
                    "genuine bills no longer flagged COUNTERFEIT, deep accumulated live "
                    "scan, and Scan Reports now record the latest user scans."),
    "changes": [
        "New Google Maps API key baked in for the Cases map",
        "Server v17.18 corroboration rule — genuine bills no longer flagged COUNTERFEIT (verified 0/30)",
        "Deep accumulated live scan — coverage grows across frames for a truer verdict",
        "Scan Reports fix — server now mirrors every scan into RTDB so the admin shows latest user scans",
    ],
    "dependencies": [],
}

r = requests.post(PATCH, json={"path": f"apk_releases/{key}", "data": record}, timeout=25)
print("PATCH status:", r.status_code, r.text[:300])

# Verify
v = requests.post(GET, json={"path": f"apk_releases/{key}"}, timeout=25).json()
print("VERIFY:", json.dumps(v.get("data"), indent=2)[:400])
