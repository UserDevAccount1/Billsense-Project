"""CUTOVER (run ONLY after on-device validation of app 1.5.11 offline scanning).
Flips ml_config.active_models to the new TFLite models so on_device mode uses the
improved denomination2 + securitycf models. The app (1.5.11) must already be
installed, or it will mis-handle the new models. NOT idempotent-destructive: it
replaces active_models cleanly.
Run: python _cutover_mlconfig_offline.py CONFIRM
"""
import sys, requests
from datetime import datetime, timezone
BASE = "https://billsense.dev-environment.site/api/db"
if len(sys.argv) < 2 or sys.argv[1] != "CONFIRM":
    print("Dry run. To apply, run: python _cutover_mlconfig_offline.py CONFIRM");
    print("This will switch offline models to denomination2 + securitycf.")
    raise SystemExit(0)

# 1) clear old active_models (removes counterfeit/security keys)
requests.post(f"{BASE}/delete", json={"path": "ml_config/active_models"}, timeout=25)
# 2) write the new config
cfg = {
    "scan_mode": "on_device",
    "updated_at": datetime.now(timezone.utc).isoformat(),
    "updated_by": "claude-offline-port-v1.5.11",
    # int8 (~11.5 MB each) for a light offline download — consistent with the old
    # int8 security model. float32 (~45 MB) is also in ml_models/ if int8 accuracy
    # proves poor on-device; swap the file/size/path to *_float32.tflite to use it.
    "active_models": {
        "denomination": {"enabled": True, "file": "denomination2_int8.tflite",
                          "size_mb": 11.5, "storage_path": "ml_models/denomination2_int8.tflite"},
        "securitycf": {"enabled": True, "file": "securitycf_int8.tflite",
                       "size_mb": 11.5, "storage_path": "ml_models/securitycf_int8.tflite"},
    },
}
r = requests.post(f"{BASE}/patch", json={"path": "ml_config", "data": cfg}, timeout=25)
print("cutover PATCH:", r.status_code, r.text[:120])
v = requests.get("https://bill-sense-aec6b-default-rtdb.firebaseio.com/ml_config.json", timeout=25).json()
print("active_models now:", list((v or {}).get("active_models", {}).keys()))
