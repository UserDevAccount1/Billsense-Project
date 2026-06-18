"""Validation harness: runs ONLY the data merge (no training) to prove the
YOLO+COCO ingestion, class mapping, and label writing work end-to-end."""
import os
import glob
from retrain import gather_datasets, build_dataset, canon_denom, DENOM_CLASSES

gathered = gather_datasets()
denom = [(lbl, loc) for (lbl, role, loc) in gathered if role in ('denom', 'both')]
print('\nDENOM MEMBERS:', [m[0] for m in denom])

kept = build_dataset('merged_denom', denom, canon_denom, DENOM_CLASSES)

for sp in ('train', 'valid'):
    imgs = len(glob.glob('merged_denom/%s/images/*' % sp))
    lbls = len(glob.glob('merged_denom/%s/labels/*' % sp))
    print('%s: images=%d labels=%d' % (sp, imgs, lbls))

# show one converted label as a sanity check
sample = (glob.glob('merged_denom/train/labels/*') or [None])[0]
if sample:
    print('\nsample label (%s):' % os.path.basename(sample))
    print(open(sample).read().strip()[:200])
print('\ndata.yaml:')
print(open('merged_denom/data.yaml').read())
