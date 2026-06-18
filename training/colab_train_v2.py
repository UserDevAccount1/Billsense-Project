# === BillSense denomination retrain v2 — paste into a connected Colab notebook (T4 GPU) ===
# Account: userdeveloper554@gmail.com (project Owner) | Runtime -> Change runtime type -> T4 GPU
#
# Trains directly on the PRE-MERGED, downscaled dataset (merged_ship.zip) built locally:
#   PH Banknote.coco + Peso bill Detector + Peso Identifier  -> 1908 train / 482 valid imgs,
#   6 classes ['20','50','100','200','500','1000'] (the server contract), balanced 80/20 split.
import os, time
from google.colab import auth
auth.authenticate_user()                       # approve the popup (your Google account)
get_ipython().system('gcloud config set project bill-sense-aec6b -q')

B = "gs://bill-sense-aec6b.firebasestorage.app/billsense-training"
get_ipython().system('gsutil -q cp {B}/merged_ship.zip /content/merged_ship.zip')
get_ipython().system('rm -rf /content/ds && mkdir -p /content/ds')
get_ipython().system('unzip -q -o /content/merged_ship.zip -d /content/ds')

# absolute-path data.yaml (ultralytics resolves val/train against this file's dir, but be explicit)
with open('/content/ds/data.yaml', 'w') as f:
    f.write("path: /content/ds\n")
    f.write("train: train/images\nval: valid/images\nnc: 6\n")
    f.write("names: ['20','50','100','200','500','1000']\n")
get_ipython().system('cat /content/ds/data.yaml')
get_ipython().system('echo train imgs: $(ls /content/ds/train/images | wc -l)  valid imgs: $(ls /content/ds/valid/images | wc -l)')

get_ipython().system('pip -q install ultralytics')

from ultralytics import YOLO
t = time.time()
model = YOLO('yolov8s.pt')
model.train(data='/content/ds/data.yaml', epochs=80, imgsz=640, batch=16,
            name='denom_v2', patience=20, plots=True)
print('train wall-clock: %.0f min' % ((time.time() - t) / 60))

# contract check + per-class metrics
best = 'runs/detect/denom_v2/weights/best.pt'
m = YOLO(best)
print('CONTRACT names:', m.names)   # MUST be {0:'20',1:'50',2:'100',3:'200',4:'500',5:'1000'}
metrics = m.val(data='/content/ds/data.yaml', split='val')
print('mAP50:', round(float(metrics.box.map50), 4), ' mAP50-95:', round(float(metrics.box.map), 4))
try:
    for i, name in m.names.items():
        print('  PHP %-5s  mAP50=%.3f  recall=%.3f' % (
            name, float(metrics.box.ap50[i]), float(metrics.box.r[i])))
except Exception as e:
    print('per-class print skipped:', e)

# push trained model back to GCS for deployment
get_ipython().system('cp {best} /content/denomination2.pt')
get_ipython().system('gsutil cp /content/denomination2.pt {B}/denomination2.pt')
print('\nDONE -- denomination2.pt uploaded to', B + '/denomination2.pt')
