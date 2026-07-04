"""Evidence: which RTDB nodes actually hold the latest user scans?
Admin ScanReports reads 'Standard Scan'/'Multi Scan'/'Video Scan' (app-written).
Server writes live scans to 'real_time_*_scans' (SA-authed). Compare freshness."""
import requests, json
PROXY = "https://billsense.dev-environment.site/api/db/get"

def get(path):
    try:
        r = requests.post(PROXY, json={"path": path}, timeout=25)
        j = r.json()
        return j.get("data")
    except Exception as e:
        return {"__err__": str(e)}

def summarize(path, nested=True):
    data = get(path)
    if not isinstance(data, dict):
        print(f"  {path!r:34} -> {type(data).__name__} ({data})")
        return
    if "__err__" in data:
        print(f"  {path!r:34} -> ERROR {data['__err__']}")
        return
    # collect records (nested userId->scanId->rec OR flat scanId->rec)
    recs = []
    for k, v in data.items():
        if isinstance(v, dict) and any(isinstance(vv, dict) for vv in v.values()) and nested:
            for sk, sv in v.items():
                if isinstance(sv, dict):
                    recs.append(sv)
        elif isinstance(v, dict):
            recs.append(v)
    ts = sorted([str(r.get("timestamp") or r.get("date") or "") for r in recs if r.get("timestamp") or r.get("date")])
    print(f"  {path!r:34} -> top_keys={len(data)} records={len(recs)} "
          f"latest_ts={ts[-1] if ts else 'NONE'}")

print("=== ADMIN READS (app-written) ===")
for p in ["Standard Scan", "Multi Scan", "Video Scan"]:
    summarize(p)
print("=== SERVER WRITES (SA-authed live scans) ===")
for p in ["real_time_standard_scans", "real_time_multi_scans", "real_time_video_scans"]:
    summarize(p)
print("=== other candidates ===")
for p in ["standard_scans", "multi_scans", "video_scans", "Scans", "scans"]:
    summarize(p)
