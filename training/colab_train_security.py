# === BillSense SECURITY/COUNTERFEIT model training (3 merged datasets) ===
# Paste into a connected Colab notebook (Runtime -> T4 GPU). Account: userdeveloper554@gmail.com
#
# Trains ONE YOLOv8 detector over THREE Roboflow security datasets that label genuine
# security features AND their FALSE (counterfeit) versions. Output: securitycf.pt with
# 16 canonical classes (8 genuine + 8 false markers). This is what lets BillSense actually
# DETECT watermark / see-through / threads AND flag forgeries (the false_* classes).
#
# IMPORTANT: we download via the DIRECT dataset links (these work), NOT project.download()
# which currently 404s on Roboflow's regional export storage.

import os, io, zipfile, json, shutil, collections, requests

# Direct COCO export links (work without the export glitch). Rotate these keys afterward.
LINKS = [
    "https://app.roboflow.com/ds/MElYlGjczx?key=lcZG9JkZcI",
    "https://app.roboflow.com/ds/3aYQSQ3iiL?key=LvqRNbgvi8",
    "https://app.roboflow.com/ds/IoSpH8Vds4?key=i6d75NgTDS",
]

get_ipython().system('pip -q install ultralytics')

# --- canonicalizer: 50+ source classes -> 16 (genuine + false) -----------------------
DENOM_TOKENS = {'20', '50', '100', '200', '500', '1k', 'new', 'old'}
def canon_security(name):
    s = ' '.join(str(name).lower().split()).replace('shadowthread', 'shadow thread')
    is_false = s.startswith('false') or ' false ' in (' ' + s + ' ')
    base = ' '.join(w for w in s.replace('false', '').split() if w not in DENOM_TOKENS).strip()
    if   'watermark' in base: feat = 'watermark'
    elif 'see through' in base: feat = 'see_through_mark'
    elif 'shadow thread' in base: feat = 'shadow_thread'
    elif 'security thread' in base: feat = 'security_thread'
    elif 'concealed value' in base: feat = 'concealed_value'
    elif 'enhanced value panel' in base: feat = 'enhanced_value_panel'
    elif 'ovi' in base: feat = 'ovi'
    elif base == '': feat = 'bill'
    else: return None
    return ('false_' + feat) if is_false else feat

CLASSES = ['bill', 'watermark', 'see_through_mark', 'shadow_thread', 'security_thread',
           'concealed_value', 'enhanced_value_panel', 'ovi',
           'false_bill', 'false_watermark', 'false_see_through_mark', 'false_shadow_thread',
           'false_security_thread', 'false_concealed_value', 'false_enhanced_value_panel', 'false_ovi']
CIDX = {c: i for i, c in enumerate(CLASSES)}

# --- download + merge (COCO -> YOLO with canonical classes, ~85/15 stem-hash split) ---
import hashlib
OUT = '/content/sec_yolo'
shutil.rmtree(OUT, ignore_errors=True)
for sp in ('train', 'valid'):
    os.makedirs(f'{OUT}/{sp}/images', exist_ok=True); os.makedirs(f'{OUT}/{sp}/labels', exist_ok=True)

report = collections.Counter(); kept = 0
for li, url in enumerate(LINKS):
    print(f"downloading dataset {li+1}/{len(LINKS)} ...")
    z = zipfile.ZipFile(io.BytesIO(requests.get(url, timeout=900).content))
    names = z.namelist()
    for jn in [n for n in names if n.endswith('_annotations.coco.json')]:
        split_dir = os.path.dirname(jn)
        d = json.load(z.open(jn))
        cats = {c['id']: c['name'] for c in d.get('categories', [])}
        imgs = {im['id']: im for im in d.get('images', [])}
        anns = collections.defaultdict(list)
        for a in d.get('annotations', []): anns[a['image_id']].append(a)
        for iid, im in imgs.items():
            W = float(im.get('width') or 1); H = float(im.get('height') or 1); lines = []
            for a in anns.get(iid, []):
                cn = canon_security(cats.get(a['category_id'], ''))
                if cn is None or cn not in CIDX: continue
                x, y, w, h = a['bbox']; report[cn] += 1
                lines.append(f"{CIDX[cn]} {(x+w/2)/W:.6f} {(y+h/2)/H:.6f} {w/W:.6f} {h/H:.6f}")
            if not lines: continue
            stem = f"d{li}_{os.path.splitext(im['file_name'])[0]}"
            dst = 'valid' if int(hashlib.md5(stem.encode()).hexdigest(), 16) % 6 == 0 else 'train'
            src = f"{split_dir}/{im['file_name']}"
            try:
                with z.open(src) as f, open(f"{OUT}/{dst}/images/{stem}.jpg", 'wb') as o: o.write(f.read())
                open(f"{OUT}/{dst}/labels/{stem}.txt", 'w').write('\n'.join(lines)); kept += 1
            except KeyError:
                pass
with open(f'{OUT}/data.yaml', 'w') as f:
    f.write(f"train: {OUT}/train/images\nval: {OUT}/valid/images\nnc: {len(CLASSES)}\nnames: {CLASSES}\n")
print(f"\nMERGED: {kept} images kept. Instances per class:")
for c in CLASSES: print(f"  {c}: {report[c]}")

# --- train ---------------------------------------------------------------------------
from ultralytics import YOLO
YOLO('yolov8s.pt').train(data=f'{OUT}/data.yaml', epochs=100, imgsz=640, batch=16, name='securitycf', patience=25, plots=True)
best = 'runs/detect/securitycf/weights/best.pt'
m = YOLO(best); print("names:", m.names)
print("mAP50:", float(m.val(data=f'{OUT}/data.yaml').box.map50))

# --- upload to GCS for deployment ----------------------------------------------------
from google.colab import auth; auth.authenticate_user()
get_ipython().system('gcloud config set project bill-sense-aec6b -q')
B = "gs://bill-sense-aec6b.firebasestorage.app/billsense-training"
get_ipython().system(f'cp {best} /content/securitycf.pt && gsutil cp /content/securitycf.pt {B}/securitycf.pt')
print("DONE -> securitycf.pt uploaded to", B)
