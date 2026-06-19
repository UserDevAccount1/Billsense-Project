# BillSense — Session Report (2026-06-19)

Server **API v17.15 → v17.19** · App **1.5.9 → 1.5.10** (versionCode 24 → 25).
All changes verified live and pushed to `feature/billy-brain-admin`.

---

## 1. Deep accumulated live scan — server v17.16
The live WebSocket standard-scan now reflects the **whole scan**, not a single frame, so a
steadier / longer / closer scan yields a truer counterfeit measurement.
- Genuine features accumulate as a **union** across frames (`combine_features_across_images`
  expanded to the full 14-feature set — it was silently dropping 5 newer features).
- Forgery markers must **persist** (`persistent_counterfeit_markers`: ≥2 frames AND ≥40% of
  frames) before they condemn — one spurious flicker washes out.
- The `analyzing` path recomputes denomination + union + coverage + verdict each frame;
  coverage now climbs monotonically (stable denominator, clamped 100%).
- Fixed a latent bug: the `COMPLETE_SCAN` path hardcoded `counterfeit_indicators={}`, so the
  final live verdict could never catch a fake — now uses the accumulated persistent markers.
- **Verified:** coverage non-decreasing across frames; 0/3 genuine bills flagged.

## 2. Genuine bills flagged COUNTERFEIT — fixed (server v17.17)
Root cause (evidence: 3/30 genuine bills flagged via REST): the `securitycf`/EVP `false_*`
markers false-fire on genuine notes, worst on the lighting-dependent features a single
front-lit photo can't verify, so the "genuine-counterpart-present" guard couldn't save them.
Hardened the verdict:
- securitycf false-marker confidence gate 0.60 → 0.75.
- EVP false marker now gated at conf ≥ 0.75 (it had **no** gate before).
- **Corroboration rule:** COUNTERFEIT only when ≥2 distinct condemning markers AND fewer than
  2 hard security features detected. A single marker never condemns; markers are overridden
  when the note shows real, hard-to-fake features.
- **Verified:** 3/30 → **0/30** genuine flagged.

## 3. Scan Reports not showing latest user scans — fixed (server v17.18 + v17.19)
Two real bugs:
- **Wrong database:** the server stored scans in **Firestore** while the admin reads **Realtime
  DB** — two different databases. The app's own RTDB writes are blocked by the
  `.write:"auth!=null"` rule (custom login). Fix: server now **mirrors every scan into RTDB**
  (`Standard/Multi/Video Scan`) in the camelCase shape the admin expects, using the service
  account. Wired into the WS (standard/multi/video) and REST paths.
- **Broken user attribution (v17.19):** the REST endpoints declared `user_id: str = "anonymous"`
  (read as a *query* param) but the app sends `user_id` as a *form* field → every scan saved as
  `anonymous`. Fixed all three to `user_id: str = Form("anonymous")`.
- **Verified:** a scan now lands in RTDB under its real userId (stale 2026-03-23 → live).

## 4. Admin dashboard
- **Scan Reports**: added a **per-user filter** and **auto-refresh** (polls every 12s, "Live"
  indicator) so a finished scan surfaces without a manual reload. Built + deployed to
  **Firebase Hosting** (`bill-sense-aec6b.web.app`).
- **APK Management**: published the **1.5.10** release record.

## 5. App 1.5.10
- Refreshed the **Google Maps API key** (Cases map) — baked into the build, installed on device.
- App bumped to 1.5.10 (versionCode 25).

## 6. Cases page (map + markers) — diagnosis
App side fully verified correct: key baked into the APK, **Maps SDK for Android enabled**,
`CasesActivity` wires the `SupportMapFragment` + `getMapAsync` correctly, and **3 cases with
coordinates exist** in the `Cases` node (First Case, Fake 1000, Scam). The blank map (and
therefore the invisible markers, which render on the map) is the **API key's Android
restriction** in the Google Cloud Console — the only piece outside the repo.
**To fix / isolate:** Console → APIs & Services → Credentials → the key → set Application
restrictions to **None** + API restrictions to **Don't restrict key** → Save → wait 2–3 min →
reopen Cases. If it renders, re-add the Android restriction with package `com.app.billsense`
and debug SHA-1 `39:47:DF:DC:D6:5C:0B:6D:37:59:E4:12:AC:5D:25:FC:6B:39:AC:6D`.

## 7. Open / follow-ups
- **Cases map:** finish the key's Android-app restriction in the Console (above) — only remaining blocker.
- **Phone install:** 1.5.10 installed earlier; USB chronically drops, blocking re-pushes + on-device map logs.
- **cPanel admin:** auto-refresh + user filter are on the Firebase URL; the cPanel copy needs a
  redeploy (no file access from here) — use `bill-sense-aec6b.web.app` for the latest.
- **Historical scans:** Nov 2025–Jun 2026 scans sit in Firestore; not back-filled into RTDB.
- **Rotate** the Maps + Roboflow keys shared in chat.
