import requests, io, zipfile, json, collections
links = {
    "feature_36MB": "https://app.roboflow.com/ds/IoSpH8Vds4?key=i6d75NgTDS",
    "false_718MB":  "https://app.roboflow.com/ds/3aYQSQ3iiL?key=LvqRNbgvi8",
}
for name, url in links.items():
    try:
        r = requests.get(url, timeout=600)
        z = zipfile.ZipFile(io.BytesIO(r.content))
        jsons = [n for n in z.namelist() if n.endswith('_annotations.coco.json')]
        imgs = 0
        anncount = collections.Counter()
        cats_by_id = {}
        for jn in jsons:
            d = json.load(z.open(jn))
            imgs += len(d.get('images', []))
            cats_by_id = {c['id']: c['name'] for c in d.get('categories', [])}
            for a in d.get('annotations', []):
                anncount[cats_by_id.get(a['category_id'], '?')] += 1
        print(f"\n=== {name}: ~{imgs} imgs, {len(jsons)} splits ===")
        for cname, cnt in sorted(anncount.items(), key=lambda x: -x[1]):
            print(f"  {cname}: {cnt}")
    except Exception as e:
        print(f"{name}: ERR {str(e)[:120]}")
