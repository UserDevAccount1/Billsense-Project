"""Verify Billy works: admin path (cPanel /api/gemini/chat) + app path (server /api/billy/chat)."""
import requests, json

# Admin Billy → cPanel Gemini proxy
try:
    r = requests.post("https://billsense.dev-environment.site/api/gemini/chat",
        json={"model": "gemini-3.1-flash-lite",
              "systemPrompt": "You are Billy, the BillSense assistant. Answer in one short sentence.",
              "history": [{"role": "user", "text": "What model does BillSense use to detect bills?"}]},
        timeout=60)
    j = r.json()
    txt = (j.get("text") or j.get("reply") or json.dumps(j))[:160]
    print(f"[admin Billy /api/gemini/chat] HTTP {r.status_code}: {txt}")
except Exception as e:
    print("[admin Billy] ERR", str(e)[:120])

# App Billy → server RAG endpoint
try:
    r = requests.post("https://billsense-api-340624938055.asia-southeast2.run.app/api/billy/chat",
        json={"message": "Who is the researcher behind BillSense?", "history": []}, timeout=90)
    j = r.json()
    txt = (j.get("response") or j.get("answer") or j.get("text") or json.dumps(j))[:160]
    print(f"[app Billy /api/billy/chat] HTTP {r.status_code}: {txt}")
except Exception as e:
    print("[app Billy] ERR", str(e)[:120])
