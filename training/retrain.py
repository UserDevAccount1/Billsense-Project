"""
BillSense model retraining pipeline.

Merges local YOLOv8 + COCO datasets (auto-detected) with an auto-canonicalizing
class remap (so '100 pesos' / '100 polymer' / 'PHP100_front' all collapse to '100'),
and trains:
  - denomination2.pt : drop-in replacement for the server's denomination model
  - realfake.pt      : NEW genuine/counterfeit detector (only if a real/fake dataset
                       is present; needs main.py wiring afterwards)

Current setup: train LOCALLY, no Roboflow key needed. Datasets live in
Resources/Datasets (see LOCAL_DATASETS below). Roboflow downloads are optional and
only happen if ROBOFLOW_API_KEY is set.

    pip install ultralytics pyyaml
    python retrain.py

The class-name contract MUST match the server (docker/app/main.py):
    denomination -> ['20','50','100','200','500','1000']
See training/README.md for the integration + redeploy steps.
"""
import os
import sys
import glob
import shutil
import hashlib

# ---------------------------------------------------------------- config
ROBOFLOW_API_KEY = os.environ.get('ROBOFLOW_API_KEY', '').strip()

# (slug, role) -- role is 'denom', 'realfake', or 'both'
DATASETS = [
    ('thesis2024/ph-bill-with-fake-detection', 'realfake'),
    ('pamantasan-ng-lungsod-ng-muntinlupa/philippine-paper-bill', 'denom'),
    ('myspace-sq9cp/ph-bills-pgvvf', 'denom'),
    ('graphics-visual-computing-3ekmj/philippine-peso-bill-identifier-rssna', 'denom'),
    ('cobra-mi40f/cashmate-ph-banknotes-wrvan', 'denom'),
    ('yolox-vezlp/ph-banknote', 'denom'),  # == the locally pasted "PH Banknote.v1" dataset
]

# Local YOLOv8 dataset folders (each must contain data.yaml + train/valid[/test]).
# Used when present; skipped on Colab if the path does not exist. Deduped against the
# Roboflow downloads by roboflow workspace/project, so a dataset listed in both is
# only trained on once. Override the root with env LOCAL_DATASETS_ROOT.
_LOCAL_ROOT = os.environ.get(
    'LOCAL_DATASETS_ROOT',
    'D:/Github/Billsense-Project/Resources/Datasets')
LOCAL_DATASETS = [
    (_LOCAL_ROOT + '/PH Banknote.coco',
     'phbanknote_coco', 'denom'),   # COCO, full-res. Superset of the old v1 yolov8 export
                                    # (100% of v1's images, full resolution) -> v1 retired.
    (_LOCAL_ROOT + '/Peso bill Detector.coco-segmentation',
     'peso_detector', 'denom'),     # COCO-seg (bbox used as HBB); 2025 captures, new/old
                                    # design variety -> targets the 50/200 recall gap.
    (_LOCAL_ROOT + '/Philippine Peso Bill Identifier.coco',
     'peso_identifier', 'denom'),   # COCO; polymer classes folded into plain value.
]

DENOM_CLASSES = ['20', '50', '100', '200', '500', '1000']
REALFAKE_CLASSES = ['genuine', 'counterfeit']

EPOCHS = int(os.environ.get('EPOCHS', '80'))
IMGSZ = int(os.environ.get('IMGSZ', '640'))
BATCH = int(os.environ.get('BATCH', '16'))
BASE_WEIGHTS = os.environ.get('BASE_WEIGHTS', 'yolov8s.pt')


# ---------------------------------------------------------------- canonicalizers
def canon_denom(name):
    """Map any source class name to one of the 6 canonical denominations, or None."""
    s = str(name).lower().strip()
    if 'thousand' in s or '1k' in s:
        return '1000'
    digits = ''.join(c for c in s if c.isdigit())
    if digits in DENOM_CLASSES:
        return digits
    for v in ['1000', '500', '200', '100', '50', '20']:
        if digits.startswith(v):
            return v
    return None


def canon_realfake(name):
    s = str(name).lower().strip()
    if any(t in s for t in ['fake', 'counterfeit', 'false', 'fraud', 'forg']):
        return 'counterfeit'
    if any(t in s for t in ['real', 'genuine', 'authentic', 'legit', 'valid', 'true']):
        return 'genuine'
    return None


# ---------------------------------------------------------------- gather (download + local)
def _project_key(loc):
    """(workspace, project) identity of a YOLOv8 folder, for dedup. Falls back to folder name."""
    try:
        import yaml
        d = yaml.safe_load(open(os.path.join(loc, 'data.yaml')))
        rb = d.get('roboflow') or {}
        if rb.get('workspace') and rb.get('project'):
            return (rb['workspace'], rb['project'])
    except Exception:
        pass
    return (None, os.path.basename(os.path.normpath(loc)))


def gather_datasets():
    """Returns [(label, role, location)]. Downloads Roboflow datasets if a key is set,
    then adds any local datasets that exist and were not already downloaded."""
    out = []
    seen = set()

    if ROBOFLOW_API_KEY:
        from roboflow import Roboflow
        rf = Roboflow(api_key=ROBOFLOW_API_KEY)
        for slug, role in DATASETS:
            ws, proj = slug.split('/', 1)
            try:
                p = rf.workspace(ws).project(proj)
                vnum = max(int(v.version) for v in p.versions())
                ds = p.version(vnum).download('yolov8')
                print('downloaded %s v%d -> %s' % (slug, vnum, ds.location))
                out.append((slug.replace('/', '_'), role, ds.location))
                seen.add((ws, proj))
            except Exception as e:
                print('WARN could not download %s: %s' % (slug, e))
    else:
        print('No ROBOFLOW_API_KEY set -> skipping Roboflow downloads, using local datasets only.')

    for path, label, role in LOCAL_DATASETS:
        if not os.path.isdir(path):
            print('local dataset not found, skipping: %s' % path)
            continue
        key = _project_key(path)
        if key in seen:
            print('local dataset %s already downloaded via Roboflow (%s) -> skip dedup' % (label, key))
            continue
        print('using local dataset %s -> %s' % (label, path))
        out.append((label, role, path))
        seen.add(key)

    return out


# ---------------------------------------------------------------- merge
def load_names(loc):
    import yaml
    d = yaml.safe_load(open(os.path.join(loc, 'data.yaml')))
    n = d['names']
    if isinstance(n, dict):
        n = [n[k] for k in sorted(n, key=lambda x: int(x))]
    return list(n)


def _detect_format(loc):
    for sp in ('train', 'valid', 'test'):
        if os.path.exists(os.path.join(loc, sp, '_annotations.coco.json')):
            return 'coco'
    if os.path.exists(os.path.join(loc, 'data.yaml')):
        return 'yolo'
    return None


def iter_samples(loc):
    """Yield (split, image_path, image_basename, [(src_class_name, xc, yc, w, h)]) with
    coords normalized 0..1. Supports both YOLOv8 and COCO datasets."""
    fmt = _detect_format(loc)
    if fmt == 'yolo':
        names = load_names(loc)
        for sp in ('train', 'valid', 'test'):
            img_dir = os.path.join(loc, sp, 'images')
            lbl_dir = os.path.join(loc, sp, 'labels')
            if not os.path.isdir(img_dir):
                continue
            for img in glob.glob(img_dir + '/*'):
                stem = os.path.splitext(os.path.basename(img))[0]
                lbl = os.path.join(lbl_dir, stem + '.txt')
                boxes = []
                if os.path.exists(lbl):
                    for line in open(lbl):
                        p = line.split()
                        if len(p) < 5:
                            continue
                        cid = int(float(p[0]))
                        if cid < len(names):
                            boxes.append((names[cid], p[1], p[2], p[3], p[4]))
                yield (sp, img, os.path.basename(img), boxes)
    elif fmt == 'coco':
        import json
        for sp in ('train', 'valid', 'test'):
            jp = os.path.join(loc, sp, '_annotations.coco.json')
            if not os.path.exists(jp):
                continue
            data = json.load(open(jp))
            cats = {c['id']: c['name'] for c in data.get('categories', [])}
            imgs = {im['id']: im for im in data.get('images', [])}
            anns = {}
            for a in data.get('annotations', []):
                anns.setdefault(a['image_id'], []).append(a)
            for iid, im in imgs.items():
                W = float(im.get('width') or 0) or 1.0
                H = float(im.get('height') or 0) or 1.0
                boxes = []
                for a in anns.get(iid, []):
                    x, y, w, h = a['bbox']
                    boxes.append((cats.get(a['category_id'], ''),
                                  '%.6f' % ((x + w / 2.0) / W), '%.6f' % ((y + h / 2.0) / H),
                                  '%.6f' % (w / W), '%.6f' % (h / H)))
                img_path = os.path.join(loc, sp, im['file_name'])
                if os.path.exists(img_path):
                    yield (sp, img_path, im['file_name'], boxes)
    else:
        print('WARN unknown dataset format, skipping: %s' % loc)


def build_dataset(out_dir, members, canon, classes):
    """members = [(label, location)]. Normalizes YOLO/COCO -> merged YOLO dataset,
    remapping every box's class via canon()."""
    for sp in ['train', 'valid']:
        os.makedirs('%s/%s/images' % (out_dir, sp), exist_ok=True)
        os.makedirs('%s/%s/labels' % (out_dir, sp), exist_ok=True)
    report = {}
    kept = 0
    # Deterministic ~80/20 split by image stem, IGNORING source splits. Source splits
    # here are unreliable -- e.g. PH Banknote.coco ships test(650) > train(599), which when
    # folded test->valid starves 20-peso *training*. All stems are unique across datasets
    # (verified: zero cross-dataset overlap, no augmentation duplicates), so a stem-hash
    # split is leakage-safe and gives balanced per-class train coverage. Override the valid
    # fraction with env VAL_EVERY (default 5 -> 20% valid).
    val_every = int(os.environ.get('VAL_EVERY', '5'))
    for label, loc in members:
        for src_split, img_path, base, boxes in iter_samples(loc):
            stem = os.path.splitext(base)[0]
            h = int(hashlib.md5(('%s/%s' % (label, stem)).encode()).hexdigest(), 16)
            dst_split = 'valid' if (h % val_every == 0) else 'train'
            new_lines = []
            for (src_name, xc, yc, w, h) in boxes:
                c = canon(src_name)
                report[src_name] = c
                if c is None or c not in classes:
                    continue
                new_lines.append('%d %s %s %s %s' % (classes.index(c), xc, yc, w, h))
            if not new_lines:
                continue
            stem = os.path.splitext(base)[0]
            shutil.copy(img_path, '%s/%s/images/%s_%s' % (out_dir, dst_split, label, base))
            with open('%s/%s/labels/%s_%s.txt' % (out_dir, dst_split, label, stem), 'w') as f:
                f.write('\n'.join(new_lines))
            kept += 1
    abs_out = os.path.abspath(out_dir)
    with open('%s/data.yaml' % out_dir, 'w') as f:
        f.write('train: %s/train/images\n' % abs_out)
        f.write('val: %s/valid/images\n' % abs_out)
        f.write('nc: %d\n' % len(classes))
        f.write('names: %s\n' % classes)
    print('\n=== %s : %d images kept ===' % (out_dir, kept))
    print('MAPPING REPORT (source class -> canonical):')
    for k in sorted(report, key=str):
        flag = '' if report[k] else '   <-- UNMAPPED (dropped)'
        print('  %r -> %s%s' % (k, report[k], flag))
    return kept


# ---------------------------------------------------------------- train
def train(data_yaml, run_name):
    from ultralytics import YOLO
    model = YOLO(BASE_WEIGHTS)
    model.train(data=data_yaml, epochs=EPOCHS, imgsz=IMGSZ, batch=BATCH,
                name=run_name, patience=20)
    best = 'runs/detect/%s/weights/best.pt' % run_name
    return best


# ---------------------------------------------------------------- main
def main():
    gathered = gather_datasets()
    if not gathered:
        sys.exit('No datasets available. Set ROBOFLOW_API_KEY or provide a local dataset.')
    denom_members = [(lbl, loc) for (lbl, role, loc) in gathered if role in ('denom', 'both')]
    rf_members = [(lbl, loc) for (lbl, role, loc) in gathered if role in ('realfake', 'both')]

    produced = []

    if denom_members:
        if build_dataset('merged_denom', denom_members, canon_denom, DENOM_CLASSES):
            best = train('merged_denom/data.yaml', 'denom')
            shutil.copy(best, 'denomination2.pt')
            produced.append('denomination2.pt')

    if rf_members:
        if build_dataset('merged_realfake', rf_members, canon_realfake, REALFAKE_CLASSES):
            best = train('merged_realfake/data.yaml', 'realfake')
            shutil.copy(best, 'realfake.pt')
            produced.append('realfake.pt')

    print('\nDONE. Produced: %s' % produced)
    print('Next: drop denomination2.pt into docker/app/models/ and redeploy (see README).')
    print('realfake.pt is NEW -> needs main.py wiring before it is used.')


if __name__ == '__main__':
    main()
