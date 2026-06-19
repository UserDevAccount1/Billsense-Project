"""Inspect ml_config (offline-scan model config the app downloads)."""
import requests, json
# Public read on RTDB (rules: .read true)
url = "https://bill-sense-aec6b-default-rtdb.firebaseio.com/ml_config.json"
data = requests.get(url, timeout=25).json()
print(json.dumps(data, indent=2) if data else "ml_config EMPTY/None")
