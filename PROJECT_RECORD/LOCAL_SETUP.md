# BillSense — Local Workstation Setup Record

Recorded 2026-11-12 before cleaning the local workstation. This repo is **public**, so this file lists
*what* exists and *where to get it*, never secret values.

## 1. Secret / local-only files (NOT in git — gitignored)

| File | Contents (names only) | Where to recover it |
|---|---|---|
| `BillSense/local.properties` | `sdk.dir`, `MAPS_API_KEY`, `ABACUS_DEPLOYMENT_ID`, `ABACUS_DEPLOYMENT_TOKEN`, `EMAIL_SENDER_ADDRESS`, `EMAIL_SENDER_PASSWORD` | Encrypted backup (see §2). Maps key: Google Cloud Console → APIs & Services → Credentials (project `bill-sense-aec6b`). Abacus: Abacus.AI deployment page. Email password: Gmail App Password. |
| `BillSense/app/google-services.json` | Firebase Android app config | Encrypted backup, or Firebase Console → Project settings → Your apps → download `google-services.json` |
| `admin-panel/serviceAccountKey.json` (was under `Resources/Archives/Billsense-Project/`) | Firebase Admin SDK service-account private key | Encrypted backup. Or Firebase Console → Project settings → Service accounts → Generate new private key (then revoke the old one). |
| `.mcp.json` env vars | `CPANEL_USERNAME`, `CPANEL_API_TOKEN` (read from environment, not stored in repo) | cPanel → Security → Manage API Tokens |

Firebase project: **`bill-sense-aec6b`** (see `.firebaserc`).

## 2. Encrypted secrets backup

All files in §1 are stored in an AES-256 encrypted zip:

- Location: `D:\Github\Billsense-Secrets-Backup\billsense-secrets-<date>.zip`
- Password: kept in the owner's password manager (entry: "BillSense secrets backup")
- Opens with 7-Zip / WinRAR / `pyzipper`.

To restore: extract, then copy each file back to the path shown in its folder name inside the zip.

## 3. Toolchain

- Android SDK: `D:\Software\AndroidSdk` (`ANDROID_HOME`, `ANDROID_SDK_ROOT`)
- Android user home / AVDs: `D:\Software\android studio\.android` (`ANDROID_USER_HOME`, `ANDROID_AVD_HOME`)
- AVDs used: `BillSense_Dev`, `billsense_admin`, `Pixel_7_Pro_Baklava` (plus old `Billsense_main*`)
- Admin panel: Node + npm — `cd "BillSense Admin/admin-panel" && npm install && npm run dev`
- Docker ML service: `docker/` (`docker-compose.yml`, `Dockerfile`, `Dockerfile.gcp`, `run-local.bat`, `extract-models.bat`)
- Firebase CLI: `firebase deploy` (config in `firebase.json`, `.firebaserc`)
- cPanel MCP: `tools/cpanel-mcp`

## 4. Large local data (gitignored, too large for GitHub)

| Folder | Size | Contents |
|---|---|---|
| `Resources/Datasets` | ~13 GB | Banknote image datasets for training |
| `Resources/Archives` | ~0.9 GB | Older project copies |
| `Resources/ml-model-weights-backup` | ~0.3 GB | Model weight backups |
| `Resources/Videos, Documents, Images, UI Mockups, Logos, API Reference` | ~75 MB | Project media and docs |
| `training/merged_denom` | ~6.5 GB | Merged denomination dataset |
| `training/merged_ship` + `.zip` | ~1.1 GB | Shipping dataset |
| `training/tflite_out`, `calib_subset`, `*.pt` | ~0.3 GB | Exported models, calibration, PyTorch weights |
| `docker/app/models` | ~0.4 GB | Models for the docker service (generate via `extract-models.bat`) |

These stay on the D: drive (or external/cloud storage), not in git.

## 5. Branches

All branches are on GitHub. `main-march-snapshot` (March TFLite offline ML + admin views + tooling work)
was pushed on 2026-11-12; before that it existed only locally.

## 6. TFLite INT8 export venv (`D:\bsexport_venv`, removed 2026-11-12)

Used by `training/export_tflite_int8.py`. Python **3.12.10**, run with `TF_USE_LEGACY_KERAS=1`.
Exact package list: `training/requirements-bsexport-venv.txt` (82 packages). Recreate with:

```powershell
py -3.12 -m venv D:\bsexport_venv
D:\bsexport_venv\Scripts\python -m pip install -r training\requirements-bsexport-venv.txt
```
