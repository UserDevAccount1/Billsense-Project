"""Publish app 1.5.15 release + the 2026-06-20 final session report to the admin."""
import requests
from datetime import datetime, timezone
PATCH = "https://billsense.dev-environment.site/api/db/patch"

rel_key = "v1_5_15_main"
rel = {
    "id": rel_key, "version": "1.5.15", "versionCode": 30, "variant": "main",
    "buildType": "debug", "status": "testing",
    "buildDate": datetime.now(timezone.utc).isoformat(), "size": "55.4 MB",
    "minSdk": 24, "targetSdk": 35, "packageName": "com.app.billsense",
    "distribution": "Direct install (adb) to device G10000000042672", "downloadUrl": "",
    "description": ("Cases workflow complete + offline scanning. Map renders (correct SHA-1), "
                    "markers + details work, users can submit cases (SA proxy), admin approval "
                    "gates the map. Offline on-device denomination2 + securitycf (int8)."),
    "changes": [
        "Cases map renders + shows markers with view-details (fixed SHA-1 + read path)",
        "Users can submit cases (routed through SA proxy)",
        "Map shows only admin-allowed (non-rejected/archived) cases",
        "Offline on-device scanning with denomination2 + securitycf int8 TFLite",
    ],
    "dependencies": ["Play Services Maps 19.2.0", "TFLite 2.x", "CameraX 1.4.2"],
}
print("apk 1.5.15:", requests.post(PATCH, json={"path": f"apk_releases/{rel_key}", "data": rel}, timeout=25).status_code)

sr_key = "session_2026_06_20_final"
sr = {
    "title": "BillSense — Cases Workflow + Offline Port + Full Verification",
    "author": "Claude AI Agent (Opus 4.8)", "date": datetime.now(timezone.utc).isoformat(),
    "status": "Complete",
    "summary": ("App 1.5.10->1.5.15. Fixed the Cases map (wrong SHA-1 -> Authorization failure, "
                "surfaced via legacy renderer), restored markers + view-details, enabled user case "
                "submission (SA proxy) with an admin approval gate; ported offline scanning to "
                "denomination2 + securitycf int8 TFLite; added full Cases CRUD in the admin. "
                "Verified Billy (admin+app), map, CRUD, models, scan reports, content posting. "
                "Root cause across features: the auth!=null RTDB write rule blocking the app's "
                "custom-login writes — all rerouted via the service-account proxy."),
    "issuesFound": [
        {"severity": "high", "file": "Maps Console", "category": "maps", "item": "Cases map blank — API key had the WRONG SHA-1 (39:47 from default keystore; real one is 25:7F:73)"},
        {"severity": "high", "file": "FBUtils.java", "category": "data", "item": "getAllDataFromPath wrote data back (ref.setValue) to make a DataSnapshot -> blocked -> no markers/scan-history"},
        {"severity": "high", "file": "FBUtils.java", "category": "data", "item": "saveCaseEvidenceData wrote via SDK -> blocked -> users could not submit cases"},
        {"severity": "medium", "file": "Cases.vue", "category": "admin", "item": "Cases admin lacked Create (no full CRUD)"},
    ],
    "fixesApplied": [
        {"file": "CasesActivity.java", "category": "maps", "item": "Force legacy Maps renderer (surfaces auth errors) + approval gate on markers"},
        {"file": "FBUtils.java", "category": "data", "item": "getAllDataFromPath -> one-shot read; saveCaseEvidenceData -> SA proxy write"},
        {"file": "Cases.vue", "category": "admin", "item": "Added New Case modal -> full CRUD; Pending status"},
        {"file": "TFLiteInference/UploadScanActivity", "category": "offline", "item": "On-device denomination2 + securitycf int8 with server-matched verdict + proxy persistence"},
    ],
    "checklist": [
        {"item": "Cases map renders on-device", "done": True},
        {"item": "Case markers + view-details work", "done": True},
        {"item": "User case submission works (proxy)", "done": True},
        {"item": "Admin Cases full CRUD", "done": True},
        {"item": "Billy admin + app verified", "done": True},
        {"item": "Offline TFLite port shipped (1.5.15)", "done": True},
        {"item": "All pushed to GitHub", "done": True},
        {"item": "Offline int8 accuracy eyeballed on a real bill", "done": False},
    ],
    "features": [
        {"name": "Cases map + markers + details"},
        {"name": "User case submission + admin approval gate"},
        {"name": "Admin Cases full CRUD"},
        {"name": "Offline on-device detection"},
    ],
}
print("session report:", requests.post(PATCH, json={"path": f"session_reports/{sr_key}", "data": sr}, timeout=25).status_code)
print("DONE")
