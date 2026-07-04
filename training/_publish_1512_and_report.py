"""Publish app 1.5.12 release record + the 2026-06-20 offline-port session report
to the admin (RTDB apk_releases + session_reports)."""
import requests, json
from datetime import datetime, timezone
PATCH = "https://billsense.dev-environment.site/api/db/patch"

# --- 1.5.12 APK release ---
rel_key = "v1_5_12_main"
rel = {
    "id": rel_key, "version": "1.5.12", "versionCode": 27, "variant": "main",
    "buildType": "debug", "status": "testing",
    "buildDate": datetime.now(timezone.utc).isoformat(), "size": "55.4 MB",
    "minSdk": 24, "targetSdk": 35, "packageName": "com.app.billsense",
    "distribution": "Direct install (adb) to device G10000000042672", "downloadUrl": "",
    "description": ("Offline scanning ported to the improved models: on-device denomination2 + "
                    "securitycf (int8 TFLite, ~12MB each) with a server-matched verdict, and "
                    "offline scans now persist to the admin via the SA proxy. Maps key + all "
                    "server fixes (v17.19) included."),
    "changes": [
        "Offline (on-device) scanning now uses denomination2 + securitycf TFLite (was old counterfeit/security)",
        "Offline verdict mirrors the server corroboration rule (no genuine->COUNTERFEIT)",
        "Offline scans persist + attribute to the admin Scan Reports (SA proxy)",
        "int8 models (~12MB) for a light offline download",
    ],
    "dependencies": ["TFLite 2.x", "Play Services Maps 19.2.0", "CameraX 1.4.2"],
}
r1 = requests.post(PATCH, json={"path": f"apk_releases/{rel_key}", "data": rel}, timeout=25)
print("apk_releases 1.5.12:", r1.status_code)

# --- session report ---
sr_key = "session_2026_06_20_offline_port"
sr = {
    "title": "BillSense — Offline Port (on-device denomination2 + securitycf)",
    "author": "Claude AI Agent (Opus 4.8)", "date": datetime.now(timezone.utc).isoformat(),
    "status": "Complete",
    "summary": ("Ported the improved models to offline (scan_mode=on_device): exported "
                "denomination2 + securitycf to TFLite (float32 + int8) in a clean venv (global "
                "env was corrupted), uploaded to Firebase Storage, rewrote TFLiteInference + "
                "UploadScanActivity with a server-matched verdict, and routed offline scan saves "
                "through the SA proxy so they persist to the admin. App 1.5.12 built + installed; "
                "ml_config cut over to int8. Also: admin APK Management shows 1.5.12; all server "
                "fixes (v17.16-17.19) + admin Scan Reports auto-refresh/user-filter live."),
    "issuesFound": [
        {"severity": "high", "file": "ml_config", "category": "offline", "item": "Offline mode used OLD models + had NO denomination model; offline scans never persisted (direct RTDB blocked)"},
        {"severity": "medium", "file": "dev-env", "category": "tooling", "item": "Local TFLite export blocked by corrupted numpy/ml_dtypes; onnx2tf OOM on full calib set"},
    ],
    "fixesApplied": [
        {"file": "TFLiteInference.java", "category": "offline", "item": "Added denomination (6) + securitycf (16) classes + buildOfflineResponse() server-matched verdict"},
        {"file": "UploadScanActivity.java", "category": "offline", "item": "Run denomination + securitycf; persist offline scans via SA proxy into Standard Scan"},
        {"file": "Firebase Storage / ml_config", "category": "models", "item": "Exported + uploaded int8/float32 TFLite; cut ml_config over to int8 (~12MB)"},
        {"file": "venv", "category": "tooling", "item": "Clean venv (tensorflow-cpu 2.18 + tf_keras + onnx2tf) for reliable export"},
    ],
    "checklist": [
        {"item": "Export denomination2 + securitycf to TFLite (float32 + int8)", "done": True},
        {"item": "Upload models to Firebase Storage", "done": True},
        {"item": "Rewrite on-device inference + verdict", "done": True},
        {"item": "Offline scans persist to admin via proxy", "done": True},
        {"item": "App 1.5.12 built + installed; ml_config cut over", "done": True},
        {"item": "On-device offline-scan accuracy validated", "done": False},
        {"item": "All changes pushed to GitHub", "done": True},
    ],
    "features": [
        {"name": "Offline on-device denomination + counterfeit detection"},
        {"name": "Offline scan persistence to admin"},
        {"name": "int8 lightweight models"},
    ],
}
r2 = requests.post(PATCH, json={"path": f"session_reports/{sr_key}", "data": sr}, timeout=25)
print("session_report:", r2.status_code)
print("DONE")
