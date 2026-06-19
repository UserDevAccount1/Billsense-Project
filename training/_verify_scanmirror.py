"""Verify v17.18: a scan via the API lands in RTDB 'Standard Scan' (what the
admin Scan Reports page reads). Run AFTER deploy."""
import glob, os, time, requests
API = "https://billsense-api-340624938055.asia-southeast2.run.app"
GET = "https://billsense.dev-environment.site/api/db/get"

print("api_version:", requests.get(API + "/api/health", timeout=60).json().get("api_version"))

# pick one genuine validation image
IMG = r"D:\Github\Billsense-Project\training\merged_denom\valid\images"
img = next(iter(glob.glob(os.path.join(IMG, "*.jpg"))), None)
print("scanning:", os.path.basename(img))
resp = requests.post(API + "/api/standard-scan",
                     files={"file": (os.path.basename(img), open(img, "rb"), "image/jpeg")},
                     timeout=120).json()
sid = resp.get("scan_id")
print(f"scan_id={sid} denom={resp.get('denomination')} "
      f"status={(resp.get('authenticity') or {}).get('status')}")

# give RTDB a moment, then look for the scan_id in 'Standard Scan'
time.sleep(3)
tree = requests.post(GET, json={"path": "Standard Scan"}, timeout=25).json().get("data") or {}
found = None
latest = []
for uid, scans in tree.items():
    if isinstance(scans, dict):
        for skey, rec in scans.items():
            if isinstance(rec, dict):
                latest.append(rec.get("timestamp", ""))
                if rec.get("scanId") == sid or skey == sid:
                    found = (uid, skey, rec)
latest.sort()
print(f"\nRTDB 'Standard Scan' now has latest_ts={latest[-1] if latest else 'NONE'}")
if found:
    uid, skey, rec = found
    print(f"[OK] FOUND new scan in RTDB under {uid}/{skey}")
    print(f"   denom={rec.get('denomination')} auth={rec.get('authenticity')} "
          f"cov={rec.get('coveragePercentage')} feats={rec.get('detectedFeaturesCount')}/"
          f"{rec.get('totalExpectedFeatures')} img={'yes' if rec.get('annotatedImageUrl') else 'no'}")
    print("Admin Scan Reports will now show the latest user scans.")
else:
    print("[FAIL] scan NOT found in RTDB — mirror not working")
