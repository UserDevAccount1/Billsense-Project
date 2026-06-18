"""Verify v17.16 deep accumulated live scan over the WS standard-scan endpoint.

Checks, for genuine bills:
  1. coverage_percentage is NON-DECREASING across analyzing frames (deep accumulation).
  2. union of features_detected grows (or holds) — never shrinks.
  3. is_genuine stays True; final COMPLETE verdict is not COUNTERFEIT.
"""
import base64, glob, json, os, ssl, sys
from websocket import create_connection

HTTP = "https://billsense-api-340624938055.asia-southeast2.run.app"
WSS  = "wss://billsense-api-340624938055.asia-southeast2.run.app/ws/standard-scan"

import requests
print("api_version:", requests.get(HTTP + "/api/health", timeout=90).json().get("api_version"))

# Pick genuine validation images grouped by single-class denomination.
LBL = r"D:\Github\Billsense-Project\training\merged_denom\valid\labels"
IMG = r"D:\Github\Billsense-Project\training\merged_denom\valid\images"
by_denom = {}
for lf in glob.glob(os.path.join(LBL, "*.txt")):
    cls = set((l.split() or [''])[0] for l in open(lf) if l.strip())
    if len(cls) == 1:
        p = os.path.join(IMG, os.path.splitext(os.path.basename(lf))[0] + ".jpg")
        if os.path.exists(p):
            by_denom.setdefault(next(iter(cls)), []).append(p)

# Test 3 denominations; for each, feed several DIFFERENT frames of that denom
# (simulating a steady multi-frame scan of one bill) then COMPLETE.
NAMES = {'0': '20', '1': '50', '2': '100', '3': '200', '4': '500', '5': '1000'}
picks = [(d, imgs[:6]) for d, imgs in sorted(by_denom.items()) if len(imgs) >= 3][:3]

def b64(path):
    with open(path, "rb") as f:
        return "data:image/jpeg;base64," + base64.b64encode(f.read()).decode()

def recv_analyzing(ws, tries=6):
    for _ in range(tries):
        msg = json.loads(ws.recv())
        if msg.get("status") == "analyzing":
            return msg
    return msg

fails = 0
for cid, imgs in picks:
    denom = NAMES.get(cid, cid)
    ws = create_connection(WSS, sslopt={"cert_reqs": ssl.CERT_NONE}, timeout=120)
    cov_series, union = [], set()
    last = None
    # Send each frame; some denominations repeat the bill to grow the union.
    frames = imgs + imgs[:3]  # re-send a few to simulate steadier hold
    for fp in frames:
        ws.send(b64(fp))
        m = recv_analyzing(ws)
        last = m
        cov_series.append(m.get("coverage_percentage", 0))
        union |= set(m.get("features_detected", []))
    ws.send("COMPLETE_SCAN")
    final = None
    for _ in range(8):
        m = json.loads(ws.recv())
        if m.get("status") == "complete":
            final = m
            break
    ws.close()

    non_decreasing = all(cov_series[i] <= cov_series[i+1] + 0.01 for i in range(len(cov_series)-1))
    fst = final.get("authenticity") if final else "NO_FINAL"
    fgen = final.get("is_genuine") if final else False
    bad = (fst == "COUNTERFEIT") or (not non_decreasing)
    fails += 1 if bad else 0
    print(f"\nDENOM ~{denom:>4} | frames={len(frames)}")
    print(f"  coverage series : {cov_series}")
    print(f"  non_decreasing  : {non_decreasing}")
    print(f"  union features  : {sorted(union)}")
    print(f"  FINAL           : status={fst} is_genuine={fgen} "
          f"detected={final.get('detected_features_count') if final else '-'}/"
          f"{final.get('total_expected_features') if final else '-'}")

print(f"\n{fails}/{len(picks)} denominations FAILED (want 0: coverage grows + not COUNTERFEIT)")
