"""Export the improved YOLOv8 models to TFLite for on-device (offline) scanning.
denomination2.pt (detect, 6 cls) + securitycf.pt (detect, 16 cls).
ultralytics auto-installs the export toolchain (onnx, onnx2tf, tensorflow)."""
import os, shutil, sys
from ultralytics import YOLO

MODELS = r"D:\Github\Billsense-Project\docker\app\models"
OUT = r"D:\Github\Billsense-Project\training\tflite_out"
os.makedirs(OUT, exist_ok=True)

def export(pt_name, out_name):
    src = os.path.join(MODELS, pt_name)
    print(f"\n=== exporting {pt_name} ===", flush=True)
    m = YOLO(src)
    print("classes:", m.names, flush=True)
    path = m.export(format="tflite", imgsz=640)  # float32
    print("exported ->", path, flush=True)
    # ultralytics writes a *_saved_model/ dir with <stem>_float32.tflite
    dst = os.path.join(OUT, out_name)
    shutil.copy(path, dst)
    print("copied ->", dst, f"({os.path.getsize(dst)/1e6:.1f} MB)", flush=True)
    return dst

export("denomination2.pt", "denomination2_float32.tflite")
export("securitycf.pt", "securitycf_float32.tflite")
print("\nDONE", flush=True)
