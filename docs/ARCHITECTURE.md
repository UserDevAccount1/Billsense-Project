# BillSense — Architecture

Deep dive on how the pieces fit together. For the high-level overview, see the [project README](../README.md).

## System diagram

```
                    ┌──────────────────────────────┐
                    │    Android user app          │
                    │    com.app.billsense         │
                    │  • CameraX capture           │
                    │  • OkHttp WebSocket          │
                    │  • Retrofit REST             │
                    │  • Firebase Auth + FCM       │
                    └──────────────┬───────────────┘
                                   │
                                   │  HTTPS + WSS  (base64 JPEG @ 80%)
                                   │
              ┌────────────────────┼────────────────────┐
              │                    │                    │
              ▼                    ▼                    ▼
       /api/standard-scan   /ws/standard-scan      /api/health
       /api/multi-scan      /ws/real-multi-scan    /api/real-time-status
       /api/video-scan      /ws/real-video-scan
              │
              ▼
  ┌──────────────────────────────────────────────────────────────┐
  │ FastAPI Cloud Run service (asia-southeast2)                  │
  │                                                              │
  │  ConnectionManager → tracks ws clients                       │
  │  ThreadPoolExecutor(4) → inference jobs                      │
  │                                                              │
  │  Lazy-loaded YOLOv8 OBB models:                              │
  │    denomination2.pt · security_best.pt · ovi.pt              │
  │    ovd.pt · evp.pt · counterfeit_best.pt                     │
  │                                                              │
  │  numpy-aware JSON encoder · CORS *                           │
  │  DummyFirebaseClient fallback when SA key absent             │
  └────────────────────────────┬─────────────────────────────────┘
                               │ admin SDK
                               ▼
  ┌──────────────────────────────────────────────────────────────┐
  │ Firebase (project: bill-sense-aec6b)                         │
  │   RTDB        scan history, app_config, FCM topics           │
  │   Storage     annotated scan images                          │
  │   Messaging   push notifications                             │
  └────────────────────────────▲─────────────────────────────────┘
                               │
                               │ Firebase Web SDK
                               │
  ┌────────────────────────────┴─────────────────────────────────┐
  │ Admin dashboard — Vue 3 / Vite 6                             │
  │   /                  Dashboard                               │
  │   /connection-health ConnectionHealth                        │
  │   /gitnexus          GitNexus (Puppeteer trigger)            │
  │   /apk-management    ApkManagement (dev-server :3003)        │
  │   /app-testing       AppTesting (ADB via dev-server)         │
  └──────────────────────────────────────────────────────────────┘

Dev-only sidecars:
  ┌─────────────────────┐    ┌─────────────────────┐
  │ Dev-server :3003    │    │ GitNexus agent :3002│
  │ (host Node 20)      │    │ (Docker, Puppeteer) │
  └─────────────────────┘    └─────────────────────┘
```

## Scan request lifecycle

### Real-time WebSocket flow (`/ws/standard-scan`)

```
Android                   FastAPI                   Firebase
   │                         │                          │
   │ 1. WS handshake         │                          │
   ├────────────────────────▶│                          │
   │ 2. accept + client_id   │                          │
   │◀────────────────────────┤                          │
   │ 3. auto-start scanning  │                          │
   │    (no START_SCAN req)  │                          │
   │                         │                          │
   │ 4. frame N (b64 jpeg)   │                          │
   ├────────────────────────▶│                          │
   │                         │ 5. submit to threadpool  │
   │                         │ 6. run 6 models in       │
   │                         │    parallel              │
   │                         │ 7. merge detections      │
   │                         │ 8. write annotated PNG   │
   │                         ├─────────────────────────▶│
   │                         │    Firebase Storage      │
   │                         │ 9. RTDB summary upsert   │
   │                         ├─────────────────────────▶│
   │ 10. detection JSON      │                          │
   │◀────────────────────────┤                          │
   │                         │                          │
   │ ... repeat 4-10 ...     │                          │
   │                         │                          │
   │ 11. close               │                          │
   ├────────────────────────▶│                          │
   │                         │ 12. ConnectionManager    │
   │                         │     evicts client_id     │
```

Key points:
- The server does not require a `START_SCAN` envelope. Inference begins on the first frame.
- `client_id` is currently user-supplied and **not validated** — see audit report §7.
- The threadpool size (4) is the cap on concurrent in-flight inferences per process. Cloud Run scales horizontally beyond that.

### REST one-shot flow (`/api/standard-scan`)

```
Android  ──POST multipart (image)──▶  FastAPI  ──parallel models──▶  Firebase Storage + RTDB
                                          │
                                          ▼
                                     JSON response
```

## Data shapes

### Detection response (abbreviated)

```jsonc
{
  "status": "ok",
  "scan_id": "uuid-v4",
  "verdict": "REAL",                    // REAL | FAKE | INCONCLUSIVE
  "confidence": 0.94,
  "denomination": "500",
  "features": [
    {
      "id": 1,                          // overlay number 1-9
      "name": "watermark",
      "model": "security_best",
      "bbox_obb": [x1,y1,x2,y2,x3,y3,x4,y4],
      "angle": 17.4,
      "score": 0.91
    }
    // ...
  ],
  "annotated_image_url": "https://firebasestorage.googleapis.com/v0/b/.../scan_<id>.png",
  "models_used": ["denomination2","security_best","ovi","ovd","evp","counterfeit_best"],
  "elapsed_ms": 412
}
```

### Firebase RTDB layout

```
/users/<uid>/profile
/users/<uid>/scans/<scan_id>
/scans/<scan_id>                        // global, queryable
/app_config                             // version gate, feature flags
/voting_posts/<post_id>                 // educational/voting feature
/cases/<case_id>                        // counterfeit case files
/educational_content/<id>
```

## Dashboard ↔ backend dependency matrix

The dashboard isn't gated by any single backend — it polls each independently and renders the cards green/yellow/red.

| Service surface | Module | Hard dep? |
|---|---|---|
| Local admin SPA | `localhost:3000` | yes — self |
| Firebase RTDB | `services/firebase.js` | yes — auth + reads |
| Cloud Run API | `https://billsense-api-...run.app` | no — degraded UX |
| Local Docker API | `localhost:8080` | no — operator-only |
| Dev-server | `localhost:3003` | no — APK page only |
| GitNexus agent | `localhost:3002` | no — GitNexus page only |
| GitHub API | `api.github.com` | no — repo card only |

## Process and port matrix

| Service | Port | Where it runs | Lifecycle owner |
|---|---|---|---|
| Admin SPA | 3000 | Docker (nginx) | `docker compose up billsense-admin` |
| GitNexus agent | 3002 | Docker (node 20) | `docker compose up gitnexus-agent` |
| Dev-server | 3003 | Host (`node dev-server.mjs`) | Manual or wrapper script |
| Local API | 8080 | Docker (uvicorn) | `docker compose up billsense-api` (needs auth) |
| Vite dev | 5173 | Host (`npm run dev`) | Manual, dev only |
| Android emulator | 5554 (adb) | Host | `emulator -avd BillSense_Test` |

## Threat model summary

(Full list in [audit report](../pdf-output/BillSense_Deep_Analysis.pdf) §7.)

- CORS open on FastAPI — restrict to known origins
- WebSocket `client_id` unvalidated — add regex allowlist
- Firebase service-account silent fallback — make explicit in prod
- VITE_ secrets shipped to browser — move behind a proxy
- MAPS_API_KEY in `local.properties` — use Gradle secrets plugin
