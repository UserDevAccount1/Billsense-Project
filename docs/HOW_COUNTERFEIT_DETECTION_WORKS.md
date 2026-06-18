# How BillSense Scans a Bill & Decides Genuine vs Counterfeit

*Last updated 2026-06-18 · server API v17.10 · denomination model v2 (retrained)*

This document explains, honestly and in detail, **what BillSense actually does** when you
scan a banknote — how the models work, how a verdict is produced, what the scores mean, and
**the real limits of its counterfeit detection.** Source of truth: `docker/app/main.py`.

---

## 1. The honest headline (read this first)

**BillSense does NOT have a single "real-vs-fake" AI model.** There is no model you can hand a
photo and get back "genuine" or "counterfeit." Instead, the verdict is **computed** from two
kinds of evidence:

1. **A specific learned forgery marker** — the EVP model (`evp.pt`) was trained with explicit
   `false 1k/500 enhanced value panel` classes. If it detects one → the note is flagged
   **COUNTERFEIT**. This is the *only* place a model directly says "this is fake," and it only
   covers the **enhanced value panel on ₱500 / ₱1000** notes.
2. **Genuine-feature verification** — the other models *find* security features (security thread,
   serial number, concealed value, watermark, OVI, etc.). The more features verified, the higher
   the **authenticity score**. Few features → *lower confidence*, **not** an accusation.

So the system today is best described as:

> **"A genuine-feature verifier + one specific forgery-marker detector"** — *not* a general
> counterfeit classifier.

**What this means in practice:**
- ✅ It reliably **identifies the denomination** and **verifies genuine security features**.
- ✅ It **catches a fake that carries a false enhanced value panel** (the learned forgery class).
- ⚠️ It will **not** reliably catch a high-quality fake that has decent features and no false-EVP
  marker — such a note reads as **LIKELY GENUINE**, not COUNTERFEIT.
- ⚠️ A single front-lit phone photo physically **cannot see** the watermark/see-through
  (needs backlight) or OVI/OVD (needs tilt). So their absence is treated as *"not verified yet"*,
  never as proof of forgery.

This is a **deliberate trade-off**: condemning a note as fake on weak evidence would false-accuse
real money. BillSense errs toward *"verify what we can; only condemn on a positive forgery signal."*

---

## 2. The six models

All six are YOLOv8 detectors loaded lazily in `LazyModelLoader`. Each **finds objects**; none of
them outputs a "genuine/fake" probability except the EVP model's `false …` classes.

| Model file | Classes it detects | Role |
|---|---|---|
| `denomination2.pt` ⭐ | `20, 50, 100, 200, 500, 1000` | **Identifies the note value.** Gates everything. *(retrained 2026-06-18)* |
| `security_best.pt` | concealed value, security thread, serial number, value, value watermark, watermark, see-through mark | Finds genuine security features (OBB) |
| `counterfeit_best.pt` | UV-thread, concealed-value, security-thread, serial-number, symbol-of-nature, value | **Despite the name, a *feature* detector — NOT a fake classifier** |
| `ovi.pt` | optically variable ink | Color-shifting ink (high-denom) |
| `ovd.pt` | ovd | Optically variable device (high-denom) |
| `evp.pt` 🚩 | 1k EVP, 500 EVP, **false 1k EVP, false 500 EVP** | **The only forgery detector** — the `false …` classes are the counterfeit signal |

> ⭐ Only `denomination2.pt` was retrained. 🚩 `evp.pt` is the lone source of a positive
> "counterfeit" signal. The others answer *"is this genuine feature present?"*, never *"is this fake?"*

---

## 3. The scan pipeline (standard single-photo scan)

```
photo ─► decode ─► process_frame_parallel()
                      │
                      ├─ 1. denomination2.pt ─► denomination  (conf ≥ 0.25)
                      │        └─ if UNKNOWN ─► verdict = UNKNOWN ("re-scan")  ◄── STOPS here
                      │
                      ├─ 2. detect_security_features_parallel()   (only if note identified)
                      │        runs in parallel: security_best, counterfeit_best,
                      │        ovi, ovd, evp, + classic watermark analysis
                      │        └─ coverage = detected_features / expected   (6 low-denom / 9 high-denom)
                      │        └─ counterfeit_indicators.false_enhanced_value_panel  ◄── forgery marker
                      │
                      ├─ 3. analyze_frame_quality()  ─► blur / brightness / contrast score
                      ├─ 4. measure_feature_geometry() ─► each feature's placement vs reference medians
                      │
                      └─ 5. evaluate_counterfeit()  ─► status + 0–100 authenticity_score
                               + annotated image (boxes drawn, stored to Firebase)
```

- **High vs low denomination:** ₱500/₱1000 ("high") expect **9** features (incl. EVP, OVI, OVD);
  others ("low") expect **6**. Coverage is `detected / expected`.
- **Multi-Scan** mode adds `measure_color_shift()` across tilt angles to actually verify OVI/OVD
  (the features a single photo can't capture), and can boost the verdict.

---

## 4. How the verdict is decided — `evaluate_counterfeit()`

In order:

| Condition | Result |
|---|---|
| Denomination not identified | **UNKNOWN** — "re-scan the full note" |
| `false enhanced value panel` detected | **COUNTERFEIT** (score capped ≤ 15) |
| EVP present on a low-denom note (shouldn't exist) | **COUNTERFEIT** |
| Capture quality < 30 (blurry/dark) | **NEEDS_RESCAN** |
| otherwise → compute score, then tier it ↓ | |

**The authenticity score (0–100):**

```
base   = 0.60 × coverage_norm  +  0.40 × mean_detection_confidence
base  += 0.15 × mean_geometry_placement      (BONUS only — never lowers a genuine note)
score  = 100 × base × quality_factor          (quality_factor = clamp(quality/70, 0.4, 1.0))
```

**Score → status:**

| Score | Status |
|---|---|
| ≥ 75 | **GENUINE** (high confidence) |
| ≥ 50 | **GENUINE** (medium) |
| < 50 | **LIKELY GENUINE** (few features visible in one photo — use Multi-Scan) |

**The guiding rule (v17.1):** *missing features ≠ counterfeit.* COUNTERFEIT is reserved for a
**positive** forgery signal (false EVP). Everything else is scored by how much genuine evidence
was gathered. This is why a clean photo of a real note with few visible features reads
"LIKELY GENUINE," not "COUNTERFEIT."

---

## 5. Model score metrics — old vs new (denomination model)

The 2026-06-18 retrain added two new full-res datasets and rebalanced the split. Measured with
`ultralytics` on the held-out validation set (482 images neither model trained on):

| Metric | OLD | **NEW** | Δ |
|---|---|---|---|
| **mAP@50** | 0.726 | **0.928** | +0.20 |
| **mAP@50-95** | 0.558 | **0.818** | +0.26 |
| **Precision** | 0.763 | **0.924** | +0.16 |
| **Recall** | 0.646 | **0.867** | +0.22 |

Per-denomination mAP@50 (and recall):

| Denom | OLD mAP50 | **NEW mAP50** | OLD recall | **NEW recall** |
|---|---|---|---|---|
| ₱20 | 0.671 | **0.993** | 0.375 | **0.987** |
| ₱50 | 0.571 | **0.949** | 0.588 | **0.917** |
| ₱100 | 0.880 | **0.920** | 0.839 | **0.903** |
| ₱200 | 0.894 | **0.938** | 0.776 | **0.821** |
| ₱500 | 0.695 | **0.912** | 0.619 | **0.785** |
| ₱1000 | 0.646 | **0.858** | 0.681 | **0.792** |

> ⚠️ These metrics are for the **denomination** detector only — the model that was retrained.
> The feature/forgery models (`security_best`, `counterfeit_best`, `ovi`, `ovd`, `evp`) were
> **not** retrained in this cycle. Improving denomination recall matters because an unidentified
> note (`UNKNOWN`) produces **no verdict at all** — so this retrain directly increases how often
> the counterfeit-evaluation pipeline can even run (e.g. ₱50 went from frequently-UNKNOWN to
> reliably identified).

---

## 6. How to make counterfeit detection stronger (future work)

The current design is conservative by intent, but if stronger fake-catching is wanted:

1. **Train a dedicated `realfake.pt` classifier** on labelled genuine *and* counterfeit examples,
   then wire it into `evaluate_counterfeit()` as an additional vote (the pipeline already documents
   this path — see `training/retrain.py` and the model contract). It must stay a *vote*, not the
   sole decider, to avoid false accusations.
2. **Expand the forgery-marker classes** beyond the false EVP (e.g. false security thread, mismatched
   serial fonts) so more fake types trip a positive signal.
3. **Use Multi-Scan** for OVI/OVD/watermark — the tilt/backlit features a single photo can't see are
   the hardest to fake; verifying them across angles is the strongest available genuine signal.
4. **UV hardware** — true UV-reactive features need a UV light source the phone camera lacks.

---

## 7. TL;DR

- BillSense **identifies the bill** and **verifies genuine security features**, producing a 0–100
  authenticity score and a GENUINE / LIKELY GENUINE / NEEDS_RESCAN / COUNTERFEIT / UNKNOWN status.
- The **only direct "it's fake" signal** today is the **false enhanced value panel** (₱500/₱1000).
  Otherwise the system measures *genuineness*, and low evidence means *low confidence*, not "fake."
- The 2026-06-18 retrain lifted the **denomination** detector from **mAP50 0.73 → 0.93**, which is
  what lets the rest of the pipeline run on notes (like ₱50) it used to fail to identify.
- A general counterfeit classifier is **future work** (`realfake.pt`), not a current feature.
