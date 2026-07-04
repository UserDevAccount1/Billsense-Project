import requests, glob, os
B = "https://billsense-api-340624938055.asia-southeast2.run.app"
print("api_version:", requests.get(B+"/api/health", timeout=90).json().get("api_version"))
imgs = []
for lf in glob.glob(r"D:\Github\Billsense-Project\training\merged_denom\valid\labels\*.txt"):
    cls = set((l.split() or [''])[0] for l in open(lf) if l.strip())
    if len(cls) == 1:
        p = os.path.join(r"D:\Github\Billsense-Project\training\merged_denom\valid\images",
                         os.path.splitext(os.path.basename(lf))[0]+".jpg")
        if os.path.exists(p): imgs.append((next(iter(cls)), p))
    if len(imgs) >= 10: break
cf = 0
for cid, img in imgs:
    try:
        d = requests.post(B+"/api/standard-scan",
                          files={"file": (os.path.basename(img), open(img, "rb"), "image/jpeg")}, timeout=120).json()
        a = d.get("authenticity") or {}
        st = a.get("status")
        if st == "COUNTERFEIT": cf += 1
        print(f"denom={d.get('denomination'):>5} status={st}")
    except Exception as e:
        print("ERR", str(e)[:50])
print(f"\n{cf}/{len(imgs)} genuine bills flagged COUNTERFEIT (want 0)")
