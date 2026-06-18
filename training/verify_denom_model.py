"""Local per-denomination recall check for a denomination model.

Evaluates a .pt model on the HELD-OUT validation split (merged_denom/valid) that the
retrain did NOT train on, and prints per-class recall at the server's confidence floor.
Run on the newly-downloaded model to confirm the 50/200 gap closed before deploying.

    python verify_denom_model.py <path-to-model.pt>   (defaults to ../docker/app/models/denomination2.pt)
"""
import sys, os, glob, collections
from ultralytics import YOLO

MODEL = sys.argv[1] if len(sys.argv) > 1 else r"D:\Github\Billsense-Project\docker\app\models\denomination2.pt"
VAL = r"D:\Github\Billsense-Project\training\merged_denom\valid"
DEN = ['20', '50', '100', '200', '500', '1000']
CONF = 0.25

m = YOLO(MODEL)
print("model:", MODEL)
print("names:", m.names)
assert [m.names[i] for i in range(len(m.names))] == DEN, "CONTRACT MISMATCH -- names must be %s" % DEN

# map each held-out val image -> its ground-truth denom (single-denom images only)
truth = {}
for lf in glob.glob(os.path.join(VAL, 'labels', '*.txt')):
    cls = set((ln.split() or [''])[0] for ln in open(lf) if ln.strip())
    if len(cls) == 1:
        stem = os.path.splitext(os.path.basename(lf))[0]
        img = os.path.join(VAL, 'images', stem + '.jpg')
        if os.path.exists(img):
            truth[img] = DEN[int(next(iter(cls)))]

def top1(img):
    """Returns (denom_or_None, readable_bool)."""
    try:
        res = m(img, conf=CONF, verbose=False)
        if not res:
            return None, False
        b = res[0].boxes
        if b is None or len(b) == 0:
            return None, True
        return m.names[int(b.cls[b.conf.argmax()])], True
    except Exception:
        return None, False

ok = collections.Counter(); tot = collections.Counter(); skipped = 0
for img, gt in truth.items():
    pred, readable = top1(img)
    if not readable:
        skipped += 1
        continue
    tot[gt] += 1
    if pred == gt:
        ok[gt] += 1
if skipped:
    print("(skipped %d unreadable images)" % skipped)

print("\n=== Per-denomination recall on held-out valid (conf>=%.2f) ===" % CONF)
print("%6s | %5s | %7s | %6s" % ('denom', 'imgs', 'correct', 'recall'))
T = O = 0
for d in DEN:
    n = tot[d]; c = ok[d]; T += n; O += c
    bar = '#' * int((c / n if n else 0) * 20)
    print("%6s | %5d | %7d | %5.0f%%  %s" % (d, n, c, (c / n * 100 if n else 0), bar))
print("-" * 40)
print("OVERALL: %d/%d = %.0f%%" % (O, T, O / T * 100 if T else 0))
print("\nOLD model baseline (pre-retrain, for reference): 50=38%  200=38%  others 80-100%")
