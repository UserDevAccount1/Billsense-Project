# BillSense — Session Report (2026-06-18)

End-to-end work across the Android app, Cloud Run ML server, admin panel, and ML models.
Versions moved: **app 1.5.2 → 1.5.9** (versionCode 17 → 24) · **server API v17.9 → v17.13**.

---

## 1. Denomination model — retrained (v2)
- Retrained `denomination2.pt` on 3 full-res datasets (`PH Banknote.coco` + 2025 `Peso bill
  Detector` + `Peso Identifier`), leakage-safe stem-hash 80/20 split, 2,402 unique images.
- **Held-out: mAP@50 0.726 → 0.928, recall 0.65 → 0.87.** ₱50 recall **38% → 97%**, ₱200 38% → 83%.
- Root-caused first: the gap was a genuine recall problem (not a threshold artifact), and ₱20
  training was being starved by an inverted source split — both fixed.
- Pipeline: `training/retrain.py`, `training/colab_train_v2.py`, verify `training/verify_denom_model.py`.
- Deployed in server **v17.10**; verified live (₱50/₱200/₱20/₱1000 scan correctly).

## 2. Real counterfeit detection — NEW `securitycf` model
- Trained `securitycf.pt` (16 classes = **8 genuine features + 8 `false_*` markers**) on 3 merged
  Roboflow security datasets via `training/colab_train_security.py` (downloaded through direct
  links; `project.download()` had a server-side export glitch).
- Wired into the scan path: genuine detections populate the checklist; **any `false_*` marker →
  COUNTERFEIT** in `evaluate_counterfeit()` (names the marker). Also gives real watermark /
  see-through detection (the prior models lacked the labelled data).
- Deployed in server **v17.13**; verified a genuine bill is *not* false-flagged.

## 3. Security-feature surfacing — 6 → 14
- The checklist/annotation were hard-capped at 6/9; the models already detected more. Expanded
  the full chain (server detection + `required_keys` + annotation maps + app checklist) to the
  full set: value, serial_number, security_thread, concealed_value, watermark, value_watermark,
  see_through_mark, uv_thread, symbol_of_nature, shadow_thread (+ OVI, OV-thread, OVD, EVP on
  ₱500/₱1000). Post-scan annotation now tags each detected feature with a labelled box.

## 4. Billy assistant — fixed & upgraded
- Confirmed working (on-device log: HTTP 200 + real RAG answers).
- Removed the repetitive "Hi! I'm Billy" preamble (answers lead with content) — live config.
- Now credits **Joy Canutab et al.** for research questions — live knowledge note.
- **Clear-chat fixed:** routed delete through the SA-authed cPanel proxy (the app's custom login
  can't write `/Billy Chats` under the `auth!=null` RTDB rule).
- **Answer-wipe fixed:** removed the chat-history ValueEventListener whose optimistic-write →
  server-reject revert was rebuilding the chat as just the welcome message.
- Admin/web Billy: reordered the Gemini fallback chain to lead with `gemini-3.1-flash-lite`
  (the quota-stable tier) so it stops failing when Pro/Flash hit 429/503.

## 5. Scan UX
- Annotated bill: tagged labelled boxes per detected feature (was bare numbers / blank).
- Pre-detection **overview modals** for all three scan types (Standard / Multi / Video) — readable
  white text + never-blank fallback + live `Detections` records filled.

## 6. Earlier in session (also shipped)
- Annotation `image_storage_error` fixed (Firebase ADC) — annotated images load.
- Billy v2 server-side FAISS RAG + guardrails; Billy Brain admin page.

## 7. Records, docs, deployment
- `docs/MODEL_REGISTRY.md` — model update history (committed).
- `docs/HOW_COUNTERFEIT_DETECTION_WORKS.md` + Word/PDF (`Resources/Documents/`) — refreshed.
- Admin **App Management**: `apk_releases` records for every build 1.5.3 → 1.5.9.
- Merged to `main` via PRs #4–#13. App 1.5.9 installed on device G10000000042672.

## 8. Open items / follow-ups
- **Rotate exposed keys** shared in chat: Roboflow (`DhWIA…`) and the Gemini key placed in the DB.
- **Demonstrate COUNTERFEIT** end-to-end with a real fake bill (logic verified; needs a fake sample).
- **Configure the rotated Gemini key** in cPanel `.gemini-key` + restart the Node app (optional —
  Billy works on the current key; this unlocks the Pro tier's fresh quota).
- Watermark/see-through detection improves further with more **backlit** labelled images.
