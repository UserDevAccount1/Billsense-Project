# BillSense — Session Report (2026-06-20)

App **1.5.10 → 1.5.15** · Server **v17.19** (stable) · Admin redeployed. All changes verified
(much of it on-device) and pushed to `feature/billy-brain-admin`.

The through-line: the RTDB rule `".write": "auth != null"` was silently breaking every app
feature that writes (the app uses a custom login, so `auth == null`). Each was rerouted through
the service-account cPanel proxy or switched to a read.

---

## 1. Offline scanning ported to the improved models (app 1.5.11–1.5.12)
- Exported `denomination2` + `securitycf` to TFLite (**int8 ~12 MB** + float32) in a clean venv
  (the global Python env's numpy/ml_dtypes was corrupted); uploaded to Firebase Storage.
- Rewrote `TFLiteInference` (6-class denom + 16-class securitycf + `buildOfflineResponse()` that
  mirrors the server corroboration verdict) and `UploadScanActivity` to run both models.
- Offline scans now **persist to the admin** via the SA proxy (were blocked + wrong node).
- `ml_config` cut over to the int8 models; default scan mode set back to **Online (cloud)**.

## 2. Cases map — fixed end-to-end (app 1.5.13–1.5.15)
- **Blank map:** the new `maps_core` renderer failed silently. Forcing the **legacy renderer**
  surfaced the real cause — an **API-key Authorization failure** from the **wrong SHA-1**. The
  correct debug SHA-1 is `25:7F:73:90:9C:5A:06:28:1B:11:92:AB:1A:FD:3F:D6:98:21:B7:A4`
  (gradle signs with `D:\Software\android studio\.android\debug.keystore`, not `~/.android`).
  After the Console fix → **map renders** (verified on-device).
- **Missing markers:** `getAllDataFromPath` fetched via REST then *wrote the data back*
  (`ref.setValue`) to materialise a `DataSnapshot` — that write was blocked → no markers. Switched
  to a one-shot SDK read `ref.get()`. **Markers render** (also fixes scan-history fragments).
- **User can't submit cases:** `saveCaseEvidenceData` wrote via SDK (blocked) → routed through
  the SA proxy. **Verified**: created a case → marker shows → tap → details dialog.
- **Approval gate:** the map now plots a case only if the admin hasn't rejected/archived it.

## 3. Admin
- **Cases — full CRUD:** added **Create** (New Case modal) to the existing Read/Update(status,
  archive)/Delete; added `Pending` to the status control.
- **Scan Reports:** mirrors every scan, per-user attribution, per-user filter, 12 s auto-refresh.
- **APK Management / ML Models / Session Reports:** kept current (1.5.15, offline TFLite models).
- Rebuilt + deployed to Firebase Hosting (`bill-sense-aec6b.web.app`).

## 4. Verified working
| Area | Result |
|---|---|
| **Cases map + markers + details** | ✅ on-device |
| **Cases CRUD (admin)** | ✅ create/read/update/delete |
| **Billy (admin)** | ✅ `/api/gemini/chat` → "YOLOv8" answer |
| **Billy (app)** | ✅ `/api/billy/chat` → Joy Canutab answer (graceful fallback under load) |
| **Models / counterfeit verdict** | ✅ 0/30 genuine flagged |
| **Scan reports** | ✅ mirror + attribution + auto-refresh |
| **Content posting (admin)** | ✅ Content.vue save/delete via proxy |

## 5. Open / follow-ups
- Eyeball an **offline scan's int8 accuracy** on a real bill (swap `ml_config` to `_float32` if poor).
- Phone Google account showed `BAD_AUTHENTICATION` — re-add the account (hygiene; map works regardless).
- Decide if the map should require **explicit approval** vs the current "not-rejected" gate.
