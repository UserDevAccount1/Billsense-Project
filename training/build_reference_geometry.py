"""
Build reference_geometry.json — the genuine per-denomination feature layout used by the server's
geometry measurement (measure_feature_geometry). EMPIRICAL pass: run the feature models over
genuine banknote images, normalise every detected feature box to its banknote frame, and
aggregate median centre/size + tolerance per (denomination, feature). Cross-check each entry
against the BSP spec (bsp_feature_spec.py) and record a consistency flag.

Run on Google Colab (GPU). Expects:
  MODELS_DIR (default /content/models) with: denomination2.pt, security_best.pt, ovi.pt, ovd.pt,
                                             evp.pt  (counterfeit_best.pt not needed here)
  DATA_ROOT  (default /content/datasets) — any folder tree of genuine banknote images (.jpg/.png)
Writes: /content/reference_geometry.json   (then upload to GCS from the Colab cell)

  pip install ultralytics
  MODELS_DIR=/content/models DATA_ROOT=/content/datasets python build_reference_geometry.py
"""
import os, glob, json, statistics
from bsp_feature_spec import bsp_consistent, HIGH_DENOMS

MODELS_DIR = os.environ.get("MODELS_DIR", "/content/models")
DATA_ROOT = os.environ.get("DATA_ROOT", "/content/datasets")
OUT = os.environ.get("OUT", "/content/reference_geometry.json")
MAX_PER_DENOM = int(os.environ.get("MAX_PER_DENOM", "400"))
SIGMA_FLOOR = 0.06

DENOM_CONF = 0.25
FEAT_CONF = 0.20

def norm(s):
    return str(s).lower().replace("-", " ").replace("_", " ").strip()

SEC_MAP = {
    "concealed value": "concealed_value", "security thread": "security_thread",
    "serial number": "serial_number", "value": "value", "value watermark": "watermark",
    "watermark": "watermark", "see through mark": "see_through_mark",
}
OVI_MAP = {"optically variable ink": "optically_variable_ink"}
OVD_MAP = {"ovd": "ovd"}
EVP_MAP = {"1k enhanced value panel": "enhanced_value_panel",
           "500 enhanced value panel": "enhanced_value_panel"}  # 'false ...' intentionally skipped


def best_box(res, conf):
    """Return (label, [x1,y1,x2,y2], conf) of the highest-confidence detection >= conf, else None."""
    best = None
    for r in res:
        names = r.names
        if r.boxes is None:
            continue
        for b in r.boxes:
            c = float(b.conf[0])
            if c < conf:
                continue
            if best is None or c > best[2]:
                xy = b.xyxy[0].tolist()
                best = (names[int(b.cls[0])], xy, c)
    return best


def all_boxes(res, conf):
    out = []
    for r in res:
        names = r.names
        if r.boxes is None:
            continue
        for b in r.boxes:
            c = float(b.conf[0])
            if c < conf:
                continue
            out.append((names[int(b.cls[0])], b.xyxy[0].tolist(), c))
    return out


def normalise(box, frame):
    fx1, fy1, fx2, fy2 = frame
    fw, fh = max(fx2 - fx1, 1e-6), max(fy2 - fy1, 1e-6)
    x1, y1, x2, y2 = box
    cx = ((x1 + x2) / 2 - fx1) / fw
    cy = ((y1 + y2) / 2 - fy1) / fh
    w = (x2 - x1) / fw
    h = (y2 - y1) / fh
    clip = lambda v: max(0.0, min(1.0, v))
    return clip(cx), clip(cy), clip(w), clip(h)


def main():
    from ultralytics import YOLO
    denom_m = YOLO(os.path.join(MODELS_DIR, "denomination2.pt"))
    sec_m = YOLO(os.path.join(MODELS_DIR, "security_best.pt"))
    ovi_m = YOLO(os.path.join(MODELS_DIR, "ovi.pt"))
    ovd_m = YOLO(os.path.join(MODELS_DIR, "ovd.pt"))
    evp_m = YOLO(os.path.join(MODELS_DIR, "evp.pt"))

    imgs = []
    for ext in ("*.jpg", "*.jpeg", "*.png", "*.JPG"):
        imgs += glob.glob(os.path.join(DATA_ROOT, "**", ext), recursive=True)
    print("found %d images under %s" % (len(imgs), DATA_ROOT))

    # accum[denom][feature] = list of (cx,cy,w,h)
    accum, per_denom = {}, {}
    for i, img in enumerate(imgs):
        try:
            d = best_box(denom_m(img, verbose=False), DENOM_CONF)
            if not d:
                continue
            denom = norm(d[0])
            if denom not in ("20", "50", "100", "200", "500", "1000"):
                continue
            if per_denom.get(denom, 0) >= MAX_PER_DENOM:
                continue
            per_denom[denom] = per_denom.get(denom, 0) + 1
            frame = d[1]
            accum.setdefault(denom, {})

            dets = [(norm(n), b) for (n, b, c) in all_boxes(sec_m(img, verbose=False), FEAT_CONF)]
            mapped = [(SEC_MAP[n], b) for (n, b) in dets if n in SEC_MAP]
            if denom in HIGH_DENOMS:
                for m, mp in ((ovi_m, OVI_MAP), (ovd_m, OVD_MAP), (evp_m, EVP_MAP)):
                    for (n, b, c) in all_boxes(m(img, verbose=False), FEAT_CONF):
                        if norm(n) in mp:
                            mapped.append((mp[norm(n)], b))

            for feat, box in mapped:
                accum[denom].setdefault(feat, []).append(normalise(box, frame))
        except Exception as e:
            print("skip", img, e)
        if (i + 1) % 100 == 0:
            print("processed %d/%d" % (i + 1, len(imgs)))

    ref = {}
    for denom, feats in accum.items():
        ref[denom] = {}
        for feat, pts in feats.items():
            if len(pts) < 3:
                continue
            cxs, cys, ws, hs = zip(*pts)
            cx, cy = statistics.median(cxs), statistics.median(cys)
            w, h = statistics.median(ws), statistics.median(hs)
            sp = max(statistics.pstdev(cxs), statistics.pstdev(cys), SIGMA_FLOOR)
            ss = max(statistics.pstdev(ws), statistics.pstdev(hs), SIGMA_FLOOR)
            ref[denom][feat] = {
                "cx": round(cx, 4), "cy": round(cy, 4), "w": round(w, 4), "h": round(h, 4),
                "sigma_pos": round(sp, 4), "sigma_size": round(ss, 4), "n": len(pts),
                "bsp_ok": bsp_consistent(denom, feat, cx, cy),
            }

    with open(OUT, "w") as f:
        json.dump({"_meta": {"source": "empirical(median) + BSP cross-check",
                             "per_denom_images": per_denom}, "geometry": ref}, f, indent=2)
    print("\nWROTE", OUT)
    for denom in sorted(ref):
        flags = [f for f, v in ref[denom].items() if not v["bsp_ok"]]
        print("  %-5s: %d features%s" % (denom, len(ref[denom]),
              ("  BSP-FLAGGED: " + ",".join(flags)) if flags else ""))


if __name__ == "__main__":
    main()
