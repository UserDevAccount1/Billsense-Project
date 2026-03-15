@echo off
echo ============================================
echo  Extract models from Cloud Run container
echo ============================================
echo.
echo Step 1: Authenticate with GCP
gcloud auth login
gcloud config set project bill-sense-aec6b
echo.

echo Step 2: Get Cloud Run image URI
for /f "tokens=*" %%i in ('gcloud run services describe billsense-api --region asia-southeast2 --format="value(spec.template.spec.containers[0].image)"') do set IMAGE_URI=%%i
echo Image URI: %IMAGE_URI%
echo.

echo Step 3: Pull container image
docker pull %IMAGE_URI%
echo.

echo Step 4: Extract model files
docker create --name temp-billsense %IMAGE_URI%
docker cp temp-billsense:/app/models/simple_model.pt ./models/simple_model.pt
docker cp temp-billsense:/app/models/uv_model.pt ./models/uv_model.pt
docker rm temp-billsense
echo.

echo Done! Models extracted to models/ directory.
pause
