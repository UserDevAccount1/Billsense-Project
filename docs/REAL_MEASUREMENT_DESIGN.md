# BillSense — Real Measurement Design

**Status:** Design (review gate before implementation)
**Author:** BillSense engineering
**Scope:** Adds a *measurement* layer on top of the existing YOLOv8 detection. No model is
replaced. Preserves the v17.2 verdict rule (COUNTERFEIT only on a real forgery signal).

---

## 1. Problem

Today each security feature is reported as a **boolean** ("detected / not detected") plus a raw
detection confidence, and the genuine/counterfeit verdict is driven by **feature coverage** (how
many of the expected features were detected) plus the one forgery marker (a *false* enhanced
value panel). The reference document *"UI and Algorithm impacts.docx"* describes a richer system
where each feature is **measured** — its location, size and optical behaviour checked against a
genuine banknote — and a calibrated authenticity confidence is shown to the user.

This design closes that gap **without changing the model**: it turns "feature detected" into
"feature detected, **correctly placed/sized**, and (for OVI/OVD) **optically behaving like real
ink**", and rolls those measurements into a single 0–100 authenticity score.

## 2. Algorithm: why CNN/YOLO, not ORB

The reference doc proposed two candidate algorithms — **ORB** (Oriented FAST and Rotated BRIEF,
classical keypoint matching against stored templates) and **CNN** (convolutional networks for
detection/classification). BillSense is built on **CNN — specifically YOLOv8 object detection**
(six models: denomination, security features, OVI, OVD, enhanced value panel, and a
security-feature counterfeit detector). ORB is **not** used.

We keep CNN/YOLO and add a measurement layer on top, because:

- YOLO already localises every feature (it returns bounding boxes), so geometry measurement is a
  cheap post-processing step on data we already produce.
- ORB-style template matching is brittle to lighting, wear and the polymer notes; the CNN already
  generalises across these.
- The geometry layer below delivers ORB's intended benefit — *"is this feature where it should
  be on a genuine note"* — using the detections we already have.

## 3. What a single photo can and cannot measure (physics)

| Measurable from one front-lit RGB photo | NOT measurable from one photo |
|---|---|
| Denomination, serial number, value numeral, security-thread *position* | Watermark / see-through register (need **transmitted light**) |
| Feature **geometry** (location & size on the note) | OVI / OVD **colour-shift** (need **tilt** → multiple angles) |
| Image quality (sharpness/brightness/contrast) | UV thread / fluorescent fibres (need a **UV source**) |

Consequence: a standard single-photo scan does **denomination + geometry + quality + basic
feature presence**. The **dynamic** features (OVI/OVD colour-shift, see-through) are measured in
**Multi-Scan** where multiple angle frames are available. UV features remain out of scope (no UV
hardware). This is a hardware/optics limit, not an algorithm limit, and is stated to the user.

## 4. The four measurements

### 4.1 Geometry verification (Component 1) — single photo + multi-scan
For each detected feature, measure where it sits on the banknote and compare to a genuine
reference layout.

- **Banknote frame box** = the highest-confidence denomination detection's bbox (fallback: the
  union of all detections). All feature boxes are expressed **relative** to this frame, so the
  measurement is scale/position invariant.
- For each feature *f* detected at box `(x1,y1,x2,y2)`, compute normalised centre `(cx,cy)` and
  size `(w,h)` ∈ [0,1] within the frame.
- Compare to the reference entry `(cx*, cy*, w*, h*)` with tolerances `(σpos, σsize)`:

  ```
  Δcentre = sqrt((cx-cx*)² + (cy-cy*)²)
  Δsize   = sqrt((w-w*)²   + (h-h*)²)
  position_score = exp( -((Δcentre/σpos)² + (Δsize/σsize)²) )      # 1.0 = perfect, →0 = wrong place
  ```

- Output per feature: `{detected, confidence, position_score, bbox_norm}`. A feature detected in
  the **wrong location** (e.g. a "security thread" box on the far edge) scores near 0 → flagged as
  suspicious even though it was "detected".

### 4.2 Calibrated authenticity score (Component 2) — single photo + multi-scan
Replace the coverage-only feel with a real composite score `authenticity_score` ∈ [0,100]:

```
base = 100 * ( 0.45 * coverage_norm
             + 0.30 * mean(detection_confidence over detected features)
             + 0.25 * mean(position_score over detected features) )
authenticity_score = round( base * quality_factor )          # quality_factor from §4.4, ∈ [0,1]
```

Hard overrides:
- Any real forgery marker (false EVP, or EVP on a low-denomination note) → `authenticity_score ≤ 15`
  and status **COUNTERFEIT**.
- Denomination UNKNOWN → status **UNKNOWN** (rescan), score reported but not trusted.

Status / confidence mapping (no forgery marker):

| Score | Status | Confidence |
|---|---|---|
| ≥ 75 | GENUINE | HIGH |
| 50–74 | GENUINE | MEDIUM |
| 25–49 | LIKELY GENUINE (advise Multi-Scan) | LOW |
| < 25 | NEEDS_RESCAN | LOW |

This keeps the v17.2 guarantee (a genuine single photo is **never** auto-COUNTERFEIT) while making
the confidence bar a real measurement instead of a feature count.

### 4.3 OVI / OVD colour-shift (Component 3) — Multi-Scan only
Optically variable ink/devices change colour with viewing angle; this is the single most reliable
optical authenticator and is **only** measurable with multiple angle frames.

- For each angle frame, crop the OVI/OVD region (its detection bbox) and compute the dominant
  colour (mean HSV of the region, ignoring specular highlights).
- Across the angle set, compute the maximum colour change:

  ```
  Δhue = max pairwise hue distance across angles
  ovi_color_shift = { delta: Δhue, shift_detected: Δhue ≥ τ_shift, from: colourA, to: colourB }
  ```

- Genuine OVI/OVD produces a consistent, large `Δhue` (e.g. the ₱1000 OVD patch / ₱500–₱1000
  value-panel OVI). A flat print or a static reflection does not shift consistently.
- Feeds a bonus into the Multi-Scan `authenticity_score`.

### 4.4 Scan-quality gating (Component 4) — all scans
The server already computes `analyze_frame_quality` (sharpness/brightness/contrast →
`overall_quality` 0–100, `quality_status`). Today it is informational. We make it **gate**:

```
quality_factor = clamp(overall_quality / 70, 0.4, 1.0)        # poor capture shrinks the score
if overall_quality < 30:  status = NEEDS_RESCAN  (+ reason: "image too blurry/dark — rescan")
```

This prevents a blurry/dark photo from producing a confident verdict in either direction.

## 5. Reference geometry — hybrid methodology

`docker/app/reference_geometry.json` maps `denomination → feature → {cx, cy, w, h, σpos, σsize, source}`.

1. **Empirical pass** (`training/build_reference_geometry.py`, run on Colab GPU through the 6
   models): run the genuine dataset images, normalise every feature detection to its banknote
   frame, and aggregate the **median** centre/size + spread (→ tolerances) per (denomination,
   feature). Low-recall features get wider tolerances (documented).
2. **BSP cross-check** (`training/bsp_feature_spec.py`): a hand-encoded table of the Bangko Sentral
   ng Pilipinas **New Generation Currency (NGC)** series security-feature locations (embedded
   security thread, watermark, see-through mark/registration, OVI on ₱500/₱1000, OVD patch on
   ₱1000, concealed value, serial number, value/enhanced value panel). Each empirical entry is
   validated against the BSP relative location; mismatches are flagged for review.
3. The final JSON records both the empirical value and the BSP-consistency flag, and cites the BSP
   NGC security-features reference. (Exact pixel positions come from the empirical pass; BSP
   provides the qualitative location each feature must occupy.)

## 6. Response schema additions

`/api/standard-scan` `authenticity` object gains:
```jsonc
"authenticity_score": 82,                 // 0–100 calibrated
"feature_geometry": {                     // per feature
  "security_thread": { "detected": true, "confidence": 0.91, "position_score": 0.94 },
  ...
},
"quality_gated": false                    // true when NEEDS_RESCAN due to quality
```
`/api/multi-scan` additionally gains:
```jsonc
"ovi_color_shift": { "delta": 38.5, "shift_detected": true, "from": "gold", "to": "green" }
```
All existing fields stay (backward compatible). `status` may now also be `LIKELY GENUINE` /
`NEEDS_RESCAN`.

## 7. App / UI changes

- POJOs (`StandardScanResponse.Authenticity`, `MultiScanResponse`, `RealTimeScanResponse`): add
  numeric `confidence_score` (int) and the optional `feature_geometry` / `ovi_color_shift` maps.
- Standard scan: bind the **existing** `confidenceBar` to the numeric score (currently parses a
  string); colour red (<25) / amber (25–74) / green (≥75). Extend `addFeatureRow` to show "✓ 94%
  placed" using `position_score`.
- Multi / Video result screens: add a ProgressBar mirroring the standard-scan bar; show the OVI
  colour-shift result on multi-scan.
- New statuses (`LIKELY GENUINE`, `NEEDS_RESCAN`) handled in the status→colour mapping.

## 8. Verification

- **API**: re-run the all-denomination `standard-scan` test → `authenticity_score` present + sane;
  genuine notes stay GENUINE; per-feature `position_score` returned.
- **Quality gate**: send a blurred/dark image → `NEEDS_RESCAN`.
- **Geometry**: genuine note → high position_scores; a synthetically mis-placed feature box → low.
- **OVI**: Multi-Scan a ₱1000 across angles → `ovi_color_shift.delta` above threshold.
- **App**: build debug APK, scan, confirm calibrated bar + per-feature "placed %" + bars on
  multi/video.
- **Regression**: confirm v17.2 holds — no false COUNTERFEIT on genuine single photos.

## 9. Limitations (stated honestly)

- A single front-lit photo cannot verify watermark/see-through (need backlight), OVI/OVD
  (need tilt → use Multi-Scan), or UV features (no UV hardware). The standard scan is a
  denomination + geometry + quality + basic-presence check; full optical authentication is the
  Multi-Scan path.
- Geometry reference quality is bounded by feature-model recall on the genuine set.
- ₱20/₱50 denomination recall is currently weaker (separate dataset gap; folded into the next
  retrain) and is independent of this measurement layer.
