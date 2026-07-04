import requests, glob, os
B = "https://billsense-api-340624938055.asia-southeast2.run.app"
print("api_version:", requests.get(B+"/api/health", timeout=60).json().get("api_version"))
# scan a 1000 (more features) and show how many security features are now reported
img = None
for lf in glob.glob(r"D:\Github\Billsense-Project\training\merged_denom\valid\labels\*.txt"):
    if set((l.split() or [''])[0] for l in open(lf) if l.strip()) == {'5'}:
        img = os.path.join(r"D:\Github\Billsense-Project\training\merged_denom\valid\images",
                           os.path.splitext(os.path.basename(lf))[0]+".jpg"); break
d = requests.post(B+"/api/standard-scan",
                  files={"file": (os.path.basename(img), open(img, "rb"), "image/jpeg")}, timeout=180).json()
a = d.get("authenticity") or {}
print("denom:", d.get("denomination"),
      "| total_expected_features:", a.get("total_expected_features"),
      "| detected:", a.get("detected_features_count"))
print("features_detected:", d.get("features_detected"))
sf = d.get("security_features") or {}
print("security_features keys (%d):" % len(sf), list(sf.keys()))
