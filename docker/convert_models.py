"""
Convert YOLOv8 .pt models to TFLite format for Firebase ML hosting.

Prerequisites:
    pip install ultralytics tensorflow onnx onnx-tf

Usage:
    python convert_models.py

This script:
1. Loads each .pt model from the models/ directory
2. Exports to TFLite format (float32)
3. Outputs .tflite files ready for Firebase ML upload
"""

import os
from ultralytics import YOLO

MODELS_DIR = os.path.join(os.path.dirname(__file__), "models")

models_to_convert = [
    ("simple_model.pt", "simple_model"),
    ("uv_model.pt", "uv_model"),
]

for filename, display_name in models_to_convert:
    model_path = os.path.join(MODELS_DIR, filename)

    if not os.path.exists(model_path):
        print(f"SKIP: {model_path} not found")
        continue

    print(f"Converting {filename} to TFLite...")
    model = YOLO(model_path)

    # Export to TFLite format
    tflite_path = model.export(format="tflite", imgsz=640)
    print(f"  Exported to: {tflite_path}")
    print(f"  Upload to Firebase ML with display name: {display_name}")
    print()

print("Done! Upload the .tflite files to Firebase ML:")
print("  firebase ml:model:create --project bill-sense-aec6b \\")
print('    --display-name "simple_model" --tflite-file <path>/simple_model_float32.tflite')
print("  firebase ml:model:create --project bill-sense-aec6b \\")
print('    --display-name "uv_model" --tflite-file <path>/uv_model_float32.tflite')
