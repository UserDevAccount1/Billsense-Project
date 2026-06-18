"""
BillyRAG — server-side retrieval over the BillSense documents (thesis, currency-detection doc,
panel comments) for the /api/billy/chat endpoint.

Lazy + isolated: the corpus loads at import (cheap), but the FAISS index + embedding model build
on the FIRST retrieve() call, all wrapped in try/except. If sentence-transformers/faiss are
unavailable, retrieve() falls back to keyword scoring. Nothing here can break the scan endpoints.
"""
import os
import re
import json
import traceback

import numpy as np

_DOCS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "billy_docs")


class BillyRAG:
    def __init__(self):
        self.chunks = []        # list of {"source": str, "text": str}
        self._index = None
        self._model = None
        self._ready = False     # FAISS index built
        self._tried = False     # attempted to build (avoid retrying every call)
        self._load_corpus()

    def _load_corpus(self):
        try:
            tp = os.path.join(_DOCS_DIR, "thesis.json")
            if os.path.exists(tp):
                data = json.load(open(tp, encoding="utf-8"))
                for k, sec in (data.get("sections") or {}).items():
                    title = sec.get("title", k)
                    for c in self._chunk(sec.get("content", ""), 700):
                        self.chunks.append({"source": "thesis · " + str(title), "text": c})
            for fname, label in (("currency_doc.txt", "currency-detection doc"),
                                 ("panel.txt", "panel comments")):
                fp = os.path.join(_DOCS_DIR, fname)
                if os.path.exists(fp):
                    for c in self._chunk(open(fp, encoding="utf-8").read(), 700):
                        self.chunks.append({"source": label, "text": c})
            print("✅ BillyRAG corpus: %d chunks" % len(self.chunks))
        except Exception as e:
            print("⚠️ BillyRAG corpus load failed: %s" % e)

    @staticmethod
    def _chunk(text, target):
        out, cur = [], ""
        for p in re.split(r"\n+", text or ""):
            p = p.strip()
            if not p:
                continue
            if len(cur) + len(p) > target and cur:
                out.append(cur)
                cur = ""
            while len(p) > target * 2:
                out.append(p[:target])
                p = p[target:]
            cur = (cur + " " + p).strip()
        if cur:
            out.append(cur)
        return [c for c in out if len(c) > 40]

    def _ensure_index(self):
        if self._ready or self._tried or not self.chunks:
            return
        self._tried = True
        try:
            import faiss
            from sentence_transformers import SentenceTransformer
            self._model = SentenceTransformer("all-MiniLM-L6-v2")
            embs = self._model.encode([c["text"] for c in self.chunks],
                                      normalize_embeddings=True, show_progress_bar=False)
            embs = np.asarray(embs, dtype="float32")
            self._index = faiss.IndexFlatIP(embs.shape[1])
            self._index.add(embs)
            self._ready = True
            print("✅ BillyRAG FAISS index ready (%d x %d)" % (embs.shape[0], embs.shape[1]))
        except Exception as e:
            print("⚠️ BillyRAG FAISS unavailable (%s) — keyword fallback" % e)
            traceback.print_exc()

    def retrieve(self, query, k=4):
        """Return up to k {source, text, score} most relevant to query."""
        if not self.chunks or not query:
            return []
        self._ensure_index()
        if self._ready:
            try:
                q = np.asarray(self._model.encode([query], normalize_embeddings=True), dtype="float32")
                D, I = self._index.search(q, min(k, len(self.chunks)))
                return [{"source": self.chunks[i]["source"], "text": self.chunks[i]["text"],
                         "score": float(D[0][n])}
                        for n, i in enumerate(I[0]) if i >= 0]
            except Exception as e:
                print("⚠️ FAISS search failed: %s" % e)
        # keyword fallback
        qw = [w for w in re.sub(r"[^a-z0-9 ]", " ", query.lower()).split() if len(w) >= 4]
        scored = []
        for ch in self.chunks:
            t = ch["text"].lower()
            s = sum(t.count(w) for w in qw)
            if s > 0:
                scored.append((s, ch))
        scored.sort(key=lambda x: x[0], reverse=True)
        return [{"source": ch["source"], "text": ch["text"], "score": float(s)} for s, ch in scored[:k]]


# module singleton — corpus loads now; FAISS index builds lazily on first retrieve()
billy_rag = BillyRAG()
