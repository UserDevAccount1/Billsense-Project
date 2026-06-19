"""Colab cells — export the improved YOLOv8 models to TFLite for offline scanning.
Run on Colab (CPU is fine for export; no GPU needed). Produces float32 .tflite files
the BillSense app downloads from Firebase Storage (ml_config active_models).

USAGE on Colab:
  1) !pip -q install ultralytics
  2) Upload denomination2.pt and securitycf.pt (files.upload()) OR pull from GCS:
       !gsutil cp gs://bill-sense-aec6b.firebasestorage.app/billsense-training/denomination2.pt .
       !gsutil cp gs://bill-sense-aec6b.firebasestorage.app/billsense-training/securitycf.pt .
  3) Run this script: !python colab_export_tflite.py
  4) files.download the produced *_float32.tflite files.
The script PRINTS each model's class index order — paste that back so the app's
TFLiteInference class arrays match the model output indices exactly.
"""
import os, glob, shutil
from ultralytics import YOLO

OUT = "tflite_out"; os.makedirs(OUT, exist_ok=True)

def export(pt, out_name):
    if not os.path.exists(pt):
        print(f"!! missing {pt} — upload it first"); return
    m = YOLO(pt)
    print(f"\n=== {pt} ===\nCLASS ORDER (index -> name): {m.names}")
    path = m.export(format="tflite", imgsz=640)          # float32 saved_model/*.tflite
    # find the float32 tflite ultralytics produced
    cand = path if str(path).endswith(".tflite") else None
    if not cand:
        hits = glob.glob(os.path.join(os.path.dirname(str(path)) or ".", "*float32.tflite"))
        cand = hits[0] if hits else None
    dst = os.path.join(OUT, out_name)
    shutil.copy(cand, dst)
    print(f"exported -> {dst} ({os.path.getsize(dst)/1e6:.1f} MB)")

export("denomination2.pt", "denomination2_float32.tflite")
export("securitycf.pt", "securitycf_float32.tflite")
print("\nDONE — download tflite_out/*.tflite and send me the CLASS ORDER lines above.")
