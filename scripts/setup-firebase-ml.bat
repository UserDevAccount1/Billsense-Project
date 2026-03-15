@echo off
echo ============================================
echo  BillSense - Firebase ML Model Setup
echo ============================================
echo.
echo This script will:
echo   1. Login to Firebase
echo   2. Extract models from Cloud Run
echo   3. Convert models to TFLite
echo   4. Upload to Firebase ML
echo.
echo Prerequisites:
echo   - Firebase CLI installed (firebase --version)
echo   - gcloud CLI installed (gcloud --version)
echo   - Python with ultralytics + tensorflow
echo   - Docker Desktop running
echo.
pause

echo.
echo === Step 1: Firebase Login ===
firebase login
firebase use bill-sense-aec6b
echo.

echo === Step 2: Install gcloud (if needed) ===
where gcloud >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo gcloud not found. Please install from:
    echo   https://cloud.google.com/sdk/docs/install
    echo After installing, run: gcloud auth login
    pause
)
echo.

echo === Step 3: Authenticate with GCP ===
gcloud auth login
gcloud config set project bill-sense-aec6b
echo.

echo === Step 4: Get Cloud Run Image URI ===
for /f "tokens=*" %%i in ('gcloud run services describe billsense-api --region asia-southeast2 --format="value(spec.template.spec.containers[0].image)"') do set IMAGE_URI=%%i
echo Image: %IMAGE_URI%
echo.

echo === Step 5: Pull and Extract Models ===
docker pull %IMAGE_URI%
docker create --name temp-billsense %IMAGE_URI%
mkdir docker\models 2>nul
docker cp temp-billsense:/app/models/simple_model.pt docker\models\simple_model.pt
docker cp temp-billsense:/app/models/uv_model.pt docker\models\uv_model.pt
docker rm temp-billsense
echo Models extracted to docker\models\
echo.

echo === Step 6: Convert to TFLite ===
python docker\convert_models.py
echo.

echo === Step 7: Upload to Firebase ML ===
echo Looking for .tflite files...
for %%f in (docker\models\*float32.tflite) do (
    echo Found: %%f
)
echo.
echo Run these commands to upload:
echo   firebase ml:model:create --project bill-sense-aec6b --display-name "simple_model" --tflite-file docker\models\simple_model_float32.tflite
echo   firebase ml:model:create --project bill-sense-aec6b --display-name "uv_model" --tflite-file docker\models\uv_model_float32.tflite
echo.
pause
