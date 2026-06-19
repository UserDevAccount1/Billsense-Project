"""Export the improved YOLOv8 models to INT8 TFLite (~12 MB each vs ~45 MB float32)
for a lighter offline download. Calibrated on real banknote images. Run in the
clean venv (D:\\bsexport_venv) with TF_USE_LEGACY_KERAS=1."""
import os, glob, shutil
from ultralytics import YOLO

MODELS = r"D:\Github\Billsense-Project\docker\app\models"
OUT = r"D:\Github\Billsense-Project\training\tflite_out"
DATA = r"D:\Github\Billsense-Project\training\calib_data.yaml"
os.makedirs(OUT, exist_ok=True)

def export_int8(pt, out_name):
    src = os.path.join(MODELS, pt)
    print(f"\n=== int8 export {pt} ===", flush=True)
    m = YOLO(src)
    print("classes:", m.names, flush=True)
    p = str(m.export(format="tflite", imgsz=640, int8=True, data=DATA))
    cand = (glob.glob(os.path.join(os.path.dirname(p) or '.', '*int8.tflite')) or [p])[0]
    dst = os.path.join(OUT, out_name)
    shutil.copy(cand, dst)
    print("->", dst, round(os.path.getsize(dst) / 1e6, 1), "MB", flush=True)

export_int8("denomination2.pt", "denomination2_int8.tflite")
export_int8("securitycf.pt", "securitycf_int8.tflite")
print("\nDONE", flush=True)
