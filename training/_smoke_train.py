"""Smoke test: trains yolov8n for 2 epochs on a SMALL subset of merged_denom just to
prove the train -> export -> denomination2.pt path executes on our exact data format.
NOT a usable model. Run _validate_merge.py first (creates merged_denom/)."""
import os
import glob
import shutil

SRC = 'merged_denom'
DST = 'merged_denom_smoke'
N_TRAIN, N_VAL = 80, 30

for sp, n in (('train', N_TRAIN), ('valid', N_VAL)):
    os.makedirs('%s/%s/images' % (DST, sp), exist_ok=True)
    os.makedirs('%s/%s/labels' % (DST, sp), exist_ok=True)
    imgs = sorted(glob.glob('%s/%s/images/*' % (SRC, sp)))[:n]
    for img in imgs:
        stem = os.path.splitext(os.path.basename(img))[0]
        lbl = '%s/%s/labels/%s.txt' % (SRC, sp, stem)
        if not os.path.exists(lbl):
            continue
        shutil.copy(img, '%s/%s/images/' % (DST, sp))
        shutil.copy(lbl, '%s/%s/labels/' % (DST, sp))

abs_dst = os.path.abspath(DST)
with open('%s/data.yaml' % DST, 'w') as f:
    f.write('train: %s/train/images\n' % abs_dst)
    f.write('val: %s/valid/images\n' % abs_dst)
    f.write("nc: 6\nnames: ['20', '50', '100', '200', '500', '1000']\n")

print('subset: train=%d valid=%d' % (
    len(glob.glob('%s/train/images/*' % DST)), len(glob.glob('%s/valid/images/*' % DST))))

from ultralytics import YOLO
m = YOLO('yolov8n.pt')
m.train(data='%s/data.yaml' % DST, epochs=2, imgsz=160, batch=4, name='smoke',
        workers=0, verbose=False, plots=False)
best = 'runs/detect/smoke/weights/best.pt'
if os.path.exists(best):
    shutil.copy(best, 'denomination2_smoke.pt')
    print('\nSMOKE OK -> denomination2_smoke.pt (size %d bytes)' % os.path.getsize('denomination2_smoke.pt'))
else:
    print('\nSMOKE FAILED: no best.pt produced')
