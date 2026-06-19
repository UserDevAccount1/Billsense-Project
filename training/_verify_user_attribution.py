"""Confirm a REST scan WITH user_id stores under that user in RTDB (not anonymous)."""
import glob, os, time, requests
API = "https://billsense-api-340624938055.asia-southeast2.run.app"
GET = "https://billsense.dev-environment.site/api/db/get"
UID = "TESTUSER_attrib_check"

img = next(iter(glob.glob(r"D:\Github\Billsense-Project\training\merged_denom\valid\images\*.jpg")), None)
resp = requests.post(API + "/api/standard-scan",
    files={"file": (os.path.basename(img), open(img, "rb"), "image/jpeg")},
    data={"user_id": UID}, timeout=120).json()
sid = resp.get("scan_id")
print(f"posted user_id={UID} scan_id={sid}")
time.sleep(3)
node = requests.post(GET, json={"path": f"Standard Scan/{UID}"}, timeout=25).json().get("data") or {}
if sid in node:
    r = node[sid]
    print(f"[OK] stored under user {UID}: denom={r.get('denomination')} auth={r.get('authenticity')} userId={r.get('userId')}")
else:
    print(f"[FAIL] not found under {UID}; node keys={list(node.keys())[:5]}")
