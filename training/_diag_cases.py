"""Check the Cases node (what the app's Cases page lists as 'features'/markers)."""
import requests, json
GET = "https://billsense.dev-environment.site/api/db/get"
data = requests.post(GET, json={"path": "Cases"}, timeout=25).json().get("data")
if not data:
    print("Cases node is EMPTY/None -> no case markers/features to show (not a bug, just no data).")
else:
    print(f"Cases node has {len(data)} entries")
    for k, v in list(data.items())[:5]:
        if isinstance(v, dict):
            print(f"  {k}: lat={v.get('latitude')} lng={v.get('longitude')} status={v.get('status')} title={v.get('title') or v.get('caseTitle')}")
        else:
            print(f"  {k}: {v}")
