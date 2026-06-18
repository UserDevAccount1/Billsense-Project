"""Phase-1 evidence: do securitycf false_* markers fire on GENUINE bills via REST?
Scans many single-class validation images, surfaces every positive false_* marker,
the genuine features detected, the verdict + reasons. Root-cause for genuine->COUNTERFEIT."""
import glob, os, requests
B = "https://billsense-api-340624938055.asia-southeast2.run.app"
print("api_version:", requests.get(B + "/api/health", timeout=90).json().get("api_version"))
LBL = r"D:\Github\Billsense-Project\training\merged_denom\valid\labels"
IMG = r"D:\Github\Billsense-Project\training\merged_denom\valid\images"
NAMES = {'0':'20','1':'50','2':'100','3':'200','4':'500','5':'1000'}

imgs = []
for lf in sorted(glob.glob(os.path.join(LBL, "*.txt"))):
    cls = set((l.split() or [''])[0] for l in open(lf) if l.strip())
    if len(cls) == 1:
        p = os.path.join(IMG, os.path.splitext(os.path.basename(lf))[0] + ".jpg")
        if os.path.exists(p):
            imgs.append((NAMES.get(next(iter(cls)), '?'), p))
    if len(imgs) >= 30:
        break

cf = 0
marker_hits = {}
for denom, img in imgs:
    try:
        d = requests.post(B + "/api/standard-scan",
                          files={"file": (os.path.basename(img), open(img, "rb"), "image/jpeg")},
                          timeout=120).json()
        a = d.get("authenticity") or {}
        ci = d.get("counterfeit_indicators") or {}
        sf = d.get("security_features") or {}
        false_on = sorted([k for k, v in ci.items() if v])
        for k in false_on:
            marker_hits[k] = marker_hits.get(k, 0) + 1
        gen_on = sorted([k for k, v in sf.items() if v])
        st = a.get("status")
        if st == "COUNTERFEIT":
            cf += 1
        flag = " <<< COUNTERFEIT" if st == "COUNTERFEIT" else ""
        print(f"~{denom:>4} {st:<14} false={false_on} gen={gen_on}{flag}")
        if st == "COUNTERFEIT":
            print(f"        reasons: {a.get('reasons')}")
    except Exception as e:
        print("ERR", str(e)[:60])

print(f"\n{cf}/{len(imgs)} genuine flagged COUNTERFEIT")
print("false_* marker frequency across genuine bills:", marker_hits)
