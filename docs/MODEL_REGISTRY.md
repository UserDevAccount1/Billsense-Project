# BillSense ML Model Registry

Update record for the YOLOv8 models the Cloud Run server (`docker/app/main.py`) loads.
The `.pt` binaries are **not** committed (large; gitignored) — they are baked into the
deployed Cloud Run image and archived in GCS:
`gs://bill-sense-aec6b.firebasestorage.app/billsense-training/`. This file is the
human-readable history of what changed.

| Model file | Role | Latest version | Trained | mAP@50 | Notes |
|---|---|---|---|---|---|
| `denomination2.pt` | Denomination (₱20–₱1000) | **v2** | 2026-06-18 | **0.93** | Retrained on 3 full-res datasets; ₱50 recall 38%→97% |
| `securitycf.pt` ⭐ | Security features + counterfeit (16 cls) | **v1** | 2026-06-18 | see run log | NEW — 8 genuine + 8 `false_*` markers → real counterfeit detection |
| `security_best.pt` | Security feature detector (OBB-era) | v1 (orig) | — | — | watermark/see-through/thread/value classes |
| `counterfeit_best.pt` | Security FEATURE detector (not real/fake) | v1 (orig) | — | — | UV-thread, concealed-value, etc. |
| `evp.pt` | Enhanced value panel + false EVP | v1 (orig) | — | — | the original forgery marker |
| `ovi.pt`, `ovd.pt` | Optically-variable ink / device (OBB) | v1 (orig) | — | — | high-denomination tilt features |

## Change log

### 2026-06-20 — Offline (on-device) TFLite models (app 1.5.12)
- Offline scanning (`scan_mode=on_device` in RTDB `ml_config`) now uses **TFLite exports of the
  improved models** instead of the old `counterfeit_best`/`security_best`:
  `denomination2_int8.tflite` + `securitycf_int8.tflite` (also `_float32` variants) in Firebase
  Storage `ml_models/` (~12 MB int8 / ~45 MB float32 each).
- Export: `training/export_tflite.py` (float32) / `export_tflite_int8.py` (int8, calibrated on
  `training/calib_data.yaml`) — run in a clean venv (`tensorflow-cpu + tf_keras + onnx2tf`,
  `TF_USE_LEGACY_KERAS=1`; the dev global env's numpy/ml_dtypes is corrupted).
- On-device verdict: `TFLiteInference.buildOfflineResponse()` mirrors the server corroboration
  rule; offline scans persist to the admin via the SA cPanel proxy.
- Cutover: `training/_cutover_mlconfig_offline.py CONFIRM` (defaults to int8; swap to `_float32`
  if int8 accuracy is poor on-device).

### 2026-06-18 — `securitycf.pt` v1 (server API v17.13)
- **New model.** Trained on 3 merged Roboflow security datasets (`ph-fake-bill-detection`,
  `ph-false-bill-detection`, `ph-bill-feature-detection`) via `training/colab_train_security.py`.
- 16 canonical classes: `bill, watermark, see_through_mark, shadow_thread, security_thread,
  concealed_value, enhanced_value_panel, ovi` + their `false_*` counterparts.
- Wired into `detect_security_features_parallel` (genuine → checklist; `false_*` →
  `counterfeit_indicators`) and `evaluate_counterfeit` (any `false_*` → **COUNTERFEIT**).
- This is the upgrade from "verify genuine features" to **actually catching fakes**, and it
  gives real watermark / see-through detection (the previous models lacked the labelled data).

### 2026-06-18 — `denomination2.pt` v2 (server API v17.10)
- Retrained on `PH Banknote.coco` (full-res) + `Peso bill Detector` (2025) + `Peso Identifier`.
- Leakage-safe stem-hash 80/20 split; 2402 unique imgs.
- Held-out: **mAP@50 0.726→0.928**, recall 0.65→0.87; ₱50 recall **38%→97%**, ₱200 38%→83%.
- Pipeline: `training/retrain.py` + `training/colab_train_v2.py`; verify: `training/verify_denom_model.py`.

## Reproduce / deploy
- **Train** (Colab T4): see `training/colab_train_v2.py` (denomination) and
  `training/colab_train_security.py` (security/counterfeit). Both upload the `.pt` to GCS.
- **Deploy**: drop the `.pt` into `docker/app/models/`, rebuild the image, `gcloud run deploy`.
- See `docs/HOW_COUNTERFEIT_DETECTION_WORKS.md` for how the verdict uses these models.
