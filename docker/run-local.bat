@echo off
echo ============================================
echo  BillSense API - Local Docker Deployment
echo ============================================
echo.
echo NOTE: Place your model files in the models/ directory:
echo   - models/simple_model.pt
echo   - models/uv_model.pt
echo.
echo Starting BillSense API on http://localhost:8080
echo.
docker compose up --build
