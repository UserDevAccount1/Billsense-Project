"""Probe a Google Maps API key: validity + what restrictions/APIs are enabled.
NOTE: a Maps SDK for Android key is validated by app SHA-1+package, which an HTTP
probe can't supply — so these server-side calls mainly reveal key validity and
application/API restrictions, not the final on-device Android-SDK behavior."""
import requests
KEY = "AIzaSyA2bXWH1lOIJqbTTbxsQCmDWByO7BftSqE"

def probe(name, url):
    try:
        r = requests.get(url, timeout=30)
        j = r.json()
        status = j.get("status")
        err = j.get("error_message", "")
        print(f"[{name}] HTTP {r.status_code} status={status}")
        if err:
            print(f"        error_message: {err}")
        return j
    except Exception as e:
        print(f"[{name}] EXC {e}")
        return {}

# Geocoding API — generic validity / restriction probe.
probe("Geocoding",
      f"https://maps.googleapis.com/maps/api/geocode/json?address=Manila&key={KEY}")
# Static Maps — closer to map rendering.
r = requests.get(
    f"https://maps.googleapis.com/maps/api/staticmap?center=Manila&zoom=12&size=200x200&key={KEY}",
    timeout=30)
ct = r.headers.get("content-type", "")
print(f"[StaticMap] HTTP {r.status_code} content-type={ct} bytes={len(r.content)}")
if "image" not in ct:
    print(f"        body: {r.text[:200]}")
