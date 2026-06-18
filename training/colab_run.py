# === BillSense denomination retrain — paste into your connected Colab notebook & run ===
# Account: userdeveloper554@gmail.com | Runtime: T4 GPU
from google.colab import auth
auth.authenticate_user()                 # approve the popup (your Google account)
!gcloud config set project bill-sense-aec6b -q

B = "gs://bill-sense-aec6b.firebasestorage.app/billsense-training"
!mkdir -p /content/datasets
!gsutil -q cp {B}/ph_banknote_v1.zip      /content/ph.zip
!gsutil -q cp {B}/peso_identifier_coco.zip /content/peso.zip
!gsutil -q cp {B}/retrain.py              /content/retrain.py
!unzip -q -o /content/ph.zip   -d "/content/datasets/PH Banknote.v1-annotated-bounding-box-version.yolov8"
!unzip -q -o /content/peso.zip -d "/content/datasets/Philippine Peso Bill Identifier.coco"

!pip -q install ultralytics pyyaml

# train (denomination model); EPOCHS/IMGSZ/BATCH overridable
!cd /content && LOCAL_DATASETS_ROOT=/content/datasets python /content/retrain.py

# push the trained model back to GCS so it can be deployed
!gsutil cp /content/denomination2.pt {B}/denomination2.pt
print("\n✅ DONE — denomination2.pt uploaded to", B)
