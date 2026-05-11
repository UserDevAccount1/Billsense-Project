# BillSense — ML Models

This document describes the YOLOv8 OBB ensemble that powers counterfeit detection: what each model does, how they were trained, and how to retrain or extend them.

> **Note on this repo.** The trained `.pt` weights are baked into the Cloud Run image and are **not** version-controlled here. Training scripts and the dataset are also kept outside this repo. If you need the weights locally, run [`docker/extract-models.bat`](../docker/extract-models.bat) to pull them out of the production image. Treat the section below as the canonical training playbook for retraining or fine-tuning.

## The ensemble

Six specialised YOLOv8 OBB (Oriented Bounding Box) detectors run in parallel against every inbound frame. Combining specialised heads beat a single multi-class detector during evaluation because (a) each head can use a tighter dataset and (b) ensemble disagreement is itself a useful signal.

| File | Detector | Output | Loaded from |
|---|---|---|---|
| `denomination2.pt` | Bill value classifier | one of {20, 50, 100, 200, 500, 1000} | `/app/models/` |
| `security_best.pt` | Security-feature detector | watermark, security thread, serial number, value, concealed value, see-through mark | `/app/models/` |
| `ovi.pt` | Optically variable ink | OVI present / absent / suspicious | `/app/models/` |
| `ovd.pt` | Optically variable device | OVD foil region polygons | `/app/models/` |
| `evp.pt` | Enhanced value panel | 500/1000 EVP variants + false-positive guard | `/app/models/` |
| `counterfeit_best.pt` | Direct counterfeit verdict | UV-thread, missing markers, anomaly classes | `/app/models/` |

All models use **YOLOv8 OBB** because banknotes are frequently captured at arbitrary angles. OBB lets each detection carry an angle parameter rather than forcing the network to predict an axis-aligned box around a rotated bill.

## Why an ensemble (and not one big model)

| Approach | Pros | Cons |
|---|---|---|
| Single multi-class YOLO | One inference call, simpler ops | Class imbalance, security-feature rare classes get crushed |
| **Per-feature ensemble** (chosen) | Each model trains on its own balanced dataset; failures are isolable; easy to swap one head without retraining the rest | 6× inference cost — handled with thread-pool parallelism + Cloud Run autoscale |

The threadpool in `docker/app/main.py` (4 workers) submits each model independently, so wall-clock latency stays close to the slowest single model rather than 6× the average.

## Training playbook

The training pipeline below reflects the practical recipe for YOLOv8 OBB on Philippine banknotes. Use this when retraining any single head, fine-tuning on new data, or adding a new detector.

### 0. Environment

```bash
# Fresh env (Python 3.10+ recommended for ultralytics)
python -m venv .venv
.venv\Scripts\activate
pip install ultralytics opencv-python pillow
```

GPU strongly recommended (CUDA 11.8+ / 12.x). CPU training is feasible for tiny datasets but a real run wants an L4 / T4 / 3090 or better.

### 1. Dataset layout

Ultralytics expects the standard YOLO directory tree, with OBB labels in 8-point polygon form:

```
datasets/
└── billsense-security/
    ├── data.yaml
    ├── images/
    │   ├── train/   img_0001.jpg, img_0002.jpg, ...
    │   ├── val/
    │   └── test/
    └── labels/
        ├── train/   img_0001.txt
        ├── val/
        └── test/
```

`data.yaml`:

```yaml
path: ./billsense-security
train: images/train
val: images/val
test: images/test

names:
  0: watermark
  1: security_thread
  2: serial_number
  3: value
  4: concealed_value
  5: see_through_mark
```

OBB label format — **8 normalised coordinates** (4 corners, clockwise), no class confidence:

```
<class_id> x1 y1 x2 y2 x3 y3 x4 y4
0 0.31 0.22 0.55 0.20 0.56 0.34 0.32 0.36
```

Annotate with Roboflow, CVAT (OBB plugin), or LabelImg-Plus. Export as **"YOLOv8 OBB"**.

### 2. Capture and curation guidelines

- **Two-light conditions.** Visible-light captures live in one dataset; UV captures (for `counterfeit_best`) live in a separate dataset with the same denominations.
- **Per-denomination minimums.** Aim for 200+ images per denomination per condition. Authentic samples are easy. Counterfeit samples must come from confiscated/seized bills with chain-of-custody — coordinate with BSP / law enforcement; do not generate synthetic fakes for the classifier head.
- **Angle coverage.** Include rotations every 30° plus skew/perspective — OBB pays off here.
- **Background diversity.** Wood, fabric, hand-held, on-camera — counterfeit hunters scan in chaotic environments.
- **Negative samples.** Add empty backgrounds, non-bill receipts/cards — at least 5% of the train set — so the model doesn't hallucinate detections.

### 3. Training command

YOLOv8 OBB nano/small/medium pick depending on Cloud Run latency budget — `n` for tight latency, `m` for accuracy. Repo's production weights file sizes suggest `s` / `m`.

```bash
yolo task=obb mode=train \
  model=yolov8m-obb.pt \
  data=datasets/billsense-security/data.yaml \
  epochs=150 \
  imgsz=1280 \
  batch=16 \
  patience=30 \
  optimizer=AdamW \
  lr0=0.001 \
  cos_lr=True \
  hsv_h=0.015 hsv_s=0.7 hsv_v=0.4 \
  degrees=20 translate=0.1 scale=0.5 \
  fliplr=0.5 mosaic=1.0 mixup=0.1 \
  project=runs/billsense \
  name=security_v1
```

Key knobs:
- `imgsz=1280` — security features are small relative to the bill; lower resolutions miss them. If GPU memory is tight, drop batch first.
- `degrees=20 fliplr=0.5` — OBB handles rotations natively, so heavy rotation aug is fine and helps.
- `hsv_*` — banknotes have strong colour signatures (security thread metallic shifts especially); aggressive HSV jitter hurts.
- `patience=30` — early stop if val mAP doesn't improve.

Repeat per head with its own dataset:

| Head | Recommended base | Notes |
|---|---|---|
| `denomination2` | `yolov8s-obb.pt` | 6 classes, balanced — small model is plenty |
| `security_best` | `yolov8m-obb.pt` | Small features, needs capacity |
| `ovi` | `yolov8s-obb.pt` | Region-level, fewer classes |
| `ovd` | `yolov8s-obb.pt` | Region-level |
| `evp` | `yolov8m-obb.pt` | False-positive guard needs capacity |
| `counterfeit_best` | `yolov8m-obb.pt` | UV-light samples, careful dataset curation |

### 4. Evaluation

```bash
yolo task=obb mode=val \
  model=runs/billsense/security_v1/weights/best.pt \
  data=datasets/billsense-security/data.yaml \
  split=test \
  imgsz=1280
```

Track per-class mAP@50 and mAP@50-95. For the counterfeit head, also track **per-denomination confusion matrix** — a "fake 500" detector is worthless if it can't tell a 500 from a 1000.

### 5. Export and integration

```bash
# Promote best to canonical name
cp runs/billsense/security_v1/weights/best.pt security_best.pt
```

Drop into `docker/models/` (locally) or rebuild the Cloud Run image with the new weights baked in:

```bash
cd docker
gcloud builds submit --tag asia-southeast2-docker.pkg.dev/bill-sense-aec6b/billsense/billsense-api:latest
gcloud run deploy billsense-api \
  --image asia-southeast2-docker.pkg.dev/bill-sense-aec6b/billsense/billsense-api:latest \
  --region asia-southeast2
```

Then hit `/api/health` — the response includes `models_loaded` and `scan_types`. Cold-start lazy-loads on the first scan, not on the health check.

## Inference internals

Annotation overlay numbering (1–9) is assigned by `docker/app/main.py` when merging detections. Each numbered feature maps consistently across frames within a single WebSocket session — the Android UI relies on this to draw stable callouts.

`DummyFirebaseClient` kicks in when `serviceAccountKey.json` is missing. Scans still complete and return JSON, but Storage uploads and RTDB writes are no-ops. **In production this should fail loudly** — see [audit report](../pdf-output/BillSense_Deep_Analysis.pdf) §7.

## Versioning recommendation

The repo currently has no model manifest. Suggested layout going forward:

```
docker/models/
├── manifest.json
└── weights/
    ├── denomination2-v2.1.pt
    ├── security_best-v3.0.pt
    └── ...
```

`manifest.json`:

```json
{
  "denomination2": {
    "version": "2.1",
    "trained_at": "2026-04-02",
    "dataset_hash": "sha256:...",
    "metrics": { "mAP50": 0.962, "mAP50-95": 0.811 },
    "training_run": "runs/billsense/denomination_v2"
  }
}
```

Backend loads via manifest rather than filename glob, so rollbacks become a one-line change.

## Adding a new detector

1. Build the dataset (steps 1–2 above).
2. Train (step 3).
3. Add a new entry to the `MODELS` registry in `docker/app/main.py`.
4. Wire the head into the numbered-annotation overlay schedule.
5. Bump the manifest version and redeploy.

That's it — the ConnectionManager and inference dispatcher pick up the new head without other changes.
