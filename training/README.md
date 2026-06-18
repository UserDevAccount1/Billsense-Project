# BillSense Model Retraining

Retrain the YOLOv8 models from public Roboflow datasets — **adds polymer banknote
support** and reduces genuine→counterfeit false positives.

## Why this exists
A genuine bill (especially the new **polymer** ₱1000/₱500/₱100/₱50) was being
flagged COUNTERFEIT. Two root causes:
1. The denomination model had **never seen polymer notes**, so it couldn't identify
   them — and an unidentified note cascades into a bad verdict.
2. The verdict logic was too strict (relaxed separately in `docker/app/main.py`,
   `evaluate_counterfeit()` + `GENUINE_COVERAGE_THRESHOLD`).

Retraining the **denomination** model with polymer data is the durable fix for (1).

## The server compatibility contract (READ THIS FIRST)
The FastAPI server (`docker/app/main.py`) reads models **by class-name string**, not
by index. A retrained model is drop-in **only if its class names match exactly**:

| Model file (in `docker/app/models/`) | Required class names | Role |
|---|---|---|
| `denomination2.pt` | `20, 50, 100, 200, 500, 1000` | identifies the bill value |
| `counterfeit_best.pt` | `UV-thread, concealed-value, security-thread, serial-number, symbol-of-nature, value` | **security-feature detector** (not real/fake!) |
| `evp.pt` | `1k enhanced value panel, 500 enhanced value panel, false 1k enhanced value panel, false 500 enhanced value panel` | enhanced value panel + forgery marker |
| `security_best.pt`, `ovi.pt`, `ovd.pt` | (unchanged this round) | OBB security models |

> ⚠️ The verdict (genuine vs counterfeit) is **computed in `evaluate_counterfeit()`**
> from feature presence + the EVP `false ...` classes. There is **no binary real/fake
> model** in the current pipeline.

### What we retrain in this round
- **`denomination2.pt`** — retrain from the **local datasets** (no Roboflow). Class
  names stay the exact 6 strings above → pure file swap, no server change. **Priority.**
- **`realfake.pt`** — DEFERRED. Its data (`thesis2024/ph-bill-with-fake-detection`) is
  Roboflow-only, and we're not using Roboflow for training right now. The script simply
  skips it when there are no real/fake datasets present.

### Decisions in effect
- **No Roboflow for training** — run with no API key; the script uses `LOCAL_DATASETS`.
- **Skip polymer as a separate class** — polymer categories (e.g. `1000 polymer`) are
  folded into their plain denomination value (`1000`). Polymer *images* still train the
  model; there's just no polymer-specific class or rule.

## Datasets — LOCAL (what we actually train on)
Configured in `LOCAL_DATASETS` (root: `Resources/Datasets`, override with env
`LOCAL_DATASETS_ROOT`). The script auto-detects YOLOv8 vs COCO format.

| Folder | Format | Imgs | Notes |
|---|---|---|---|
| `PH Banknote.v1-annotated-bounding-box-version.yolov8` | YOLOv8 | 1,391 | classes already = the exact 6 contract names |
| `Philippine Peso Bill Identifier.coco` | COCO | 138 | has `100/1000 polymer` classes → folded to `100/1000` |

Add more by dropping a YOLOv8 or COCO folder into `Resources/Datasets` and appending a
row to `LOCAL_DATASETS` in `retrain.py`.

### Optional Roboflow datasets (only if you later set an API key)
`thesis2024/ph-bill-with-fake-detection` (real/fake), plus denomination sets
`pamantasan-ng-lungsod-ng-muntinlupa/philippine-paper-bill`, `myspace-sq9cp/ph-bills-pgvvf`,
`graphics-visual-computing-3ekmj/philippine-peso-bill-identifier-rssna`,
`cobra-mi40f/cashmate-ph-banknotes-wrvan`, `yolox-vezlp/ph-banknote`. These are **inert
without a key**; local datasets that duplicate a Roboflow project are deduped automatically.

## How to run (Google Colab — free GPU)
1. Get a free **Roboflow API key**: roboflow.com → Settings → Roboflow API → Private key.
2. New Colab notebook → `Runtime → Change runtime type → T4 GPU`.
3. Upload `retrain.py` (left panel → Files → upload), then run these cells:
   ```python
   !pip install ultralytics roboflow pyyaml
   import os; os.environ['ROBOFLOW_API_KEY'] = 'YOUR_KEY_HERE'
   !python retrain.py
   ```
4. The merge step **auto-canonicalizes** the fragmented class names
   (`PHP1000_front`, `1k`, `Thousand` → `1000`) and prints a **MAPPING REPORT** —
   sanity-check it (watch for `UNMAPPED (dropped)` lines) before trusting the result.
5. Two trainings run: `denom` then `realfake`. Ultralytics prints val mAP per epoch.
6. Download the outputs:
   ```python
   from google.colab import files
   files.download('denomination2.pt'); files.download('realfake.pt')
   ```
   Tune with env vars before the run if needed: `EPOCHS`, `IMGSZ`, `BATCH`, `BASE_WEIGHTS`.

### Train locally with no API key (the current path)
```bash
pip install ultralytics pyyaml
cd training
python retrain.py          # no ROBOFLOW_API_KEY -> uses LOCAL_DATASETS only
```
Trains `denomination2.pt` from the **~1,529 local images** across the two datasets
(YOLOv8 + COCO, auto-detected). `realfake.pt` is **skipped** (no real/fake dataset
locally). Needs a GPU for a reasonable runtime; CPU works but is slow. Point at a
different datasets root with `LOCAL_DATASETS_ROOT` (useful on Colab after uploading).

## Integrate the retrained models
1. **Denomination (drop-in):**
   - Replace `docker/app/models/denomination2.pt` with the new file.
   - Rebuild + redeploy (see below). No code change.
2. **Real/Fake (new — needs wiring):**
   - Copy `realfake.pt` to `docker/app/models/realfake.pt`.
   - In `main.py`: load it in `LazyModelLoader`, run it in the scan path, and in
     `evaluate_counterfeit()` add: high-confidence `counterfeit` → force COUNTERFEIT;
     high-confidence `genuine` → boost the genuine vote. (Ask Claude to wire this once
     the model exists — keep it as an *additional* signal, not the sole decider.)

## Rebuild + redeploy Cloud Run (same pipeline that already works)
```bash
# staged build context = docker/app contents + models/
gcloud auth login                       # project Owner: userdeveloper554@gmail.com
gcloud config set project bill-sense-aec6b
gcloud builds submit <build-context-dir> \
  --tag asia-southeast2-docker.pkg.dev/bill-sense-aec6b/billsense/billsense-api:latest --timeout=1800s
gcloud run deploy billsense-api \
  --image asia-southeast2-docker.pkg.dev/bill-sense-aec6b/billsense/billsense-api:latest --region asia-southeast2
```
A failed build is safe — Cloud Run keeps serving the current revision until a new one succeeds.

## Verify after deploy
Scan a genuine polymer ₱1000 → expect `denomination: 1000` and a GENUINE (or
needs-rescan) verdict, not COUNTERFEIT.
