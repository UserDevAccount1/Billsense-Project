@echo off
echo ============================================
echo  BillSense - Full Project Setup
echo ============================================
echo.

echo === 1. Firebase Login ===
firebase login
firebase use bill-sense-aec6b
echo.

echo === 2. Install Admin Panel Dependencies ===
cd admin-panel
npm install
cd ..
echo.

echo === 3. Start Docker Services ===
echo Make sure Docker Desktop is running!
echo To start all services:
echo   cd docker
echo   docker compose up --build
echo.
echo This starts:
echo   - BillSense API on port 8080
echo   - Admin Panel on port 3000
echo.

echo === 4. Run Admin Panel (dev mode) ===
echo   cd admin-panel
echo   npm run dev
echo.

echo === 5. Android Build ===
echo   cd BillSense
echo   ./gradlew assembleDebug
echo   ./gradlew installDebug
echo.

echo === Setup Complete ===
echo.
echo Manual steps remaining:
echo   1. Run 'firebase login' if not authenticated
echo   2. Install gcloud CLI for model extraction
echo   3. Run scripts\setup-firebase-ml.bat for ML model hosting
echo   4. Add SSH key to GitHub: https://github.com/settings/keys
echo      Your public key: C:\Users\Gab\.ssh\id_ed25519.pub
echo.
pause
