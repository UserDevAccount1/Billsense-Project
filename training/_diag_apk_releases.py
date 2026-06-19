"""Check apk_releases in RTDB (what admin APK Management reads)."""
import requests, json
GET = "https://billsense.dev-environment.site/api/db/get"
data = requests.post(GET, json={"path": "apk_releases"}, timeout=25).json().get("data")
if not data:
    print("apk_releases EMPTY/None")
else:
    print(f"apk_releases has {len(data)} record(s):")
    rows = sorted(data.items(), key=lambda kv: str(kv[1].get("buildDate","")), reverse=True)
    for k, v in rows:
        print(f"  {k}: v{v.get('version')} code={v.get('versionCode')} variant={v.get('variant')} "
              f"status={v.get('status')} buildDate={v.get('buildDate')}")
