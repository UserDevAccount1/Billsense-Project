"""Dump the most recent RTDB scans across all three nodes so we can see whether
the user's app scan actually landed, and under what userId."""
import requests
GET = "https://billsense.dev-environment.site/api/db/get"

for node in ["Standard Scan", "Multi Scan", "Video Scan"]:
    data = requests.post(GET, json={"path": node}, timeout=25).json().get("data") or {}
    recs = []
    for uid, scans in (data.items() if isinstance(data, dict) else []):
        if isinstance(scans, dict):
            for k, r in scans.items():
                if isinstance(r, dict):
                    recs.append((r.get("timestamp", ""), uid, k, r))
    recs.sort(reverse=True)
    print(f"\n=== {node}: top_uids={len(data)} total_records={len(recs)} ===")
    for ts, uid, k, r in recs[:6]:
        print(f"  {ts} | uid={uid} | denom={r.get('denomination')} "
              f"auth={r.get('authenticity')} cov={r.get('coveragePercentage')} "
              f"feats={r.get('detectedFeaturesCount')}/{r.get('totalExpectedFeatures')}")
    print(f"  userIds present: {list(data.keys()) if isinstance(data, dict) else 'NONE'}")
