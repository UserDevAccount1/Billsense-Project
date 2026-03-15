# BillSense - Claude Code Project Configuration

## Project Overview
BillSense is a Philippine currency counterfeit detection Android app that uses YOLOv8 ML models
for real-time banknote authentication via visual and UV security feature analysis.

## Architecture
- **Platform**: Android (Java, minSdk 24, targetSdk 35)
- **Build**: Gradle 8.13, AGP 8.11.1, Kotlin DSL build scripts
- **Firebase Project**: bill-sense-aec6b (Realtime DB, Storage, Messaging)
- **ML Backend**: FastAPI on Google Cloud Run (YOLOv8 OBB models)
  - Simple Model: classifies bills as Real/Fake
  - UV Model: detects security features (concealed-value, serial-number, UV-thread, etc.)

## Key Paths
- **Android project root**: `BillSense/` (Gradle project)
- **Android source**: `BillSense/app/src/main/java/com/app/billsense/`
- **Scan logic**: `BillSense/app/src/main/java/com/app/billsense/scan/`
- **API services**: `BillSense/app/src/main/java/com/app/billsense/scan/pojo/`
  - `CurrencyApiService.java` - REST API client (standard, multi, video scan)
  - `RealTimeScanManager.java` - WebSocket client for live scanning
- **Activities**: `BillSense/app/src/main/java/com/app/billsense/activities/`
- **Utilities**: `BillSense/app/src/main/java/com/app/billsense/utils/`
- **Firebase config**: `BillSense/app/google-services.json`
- **Version catalog**: `BillSense/gradle/libs.versions.toml`
- **API docs**: `Philippine_Currency_Detection_API_Documentation.md`
- **API reference**: `API DOCUMENT/` (docs, HTML test pages, WebSocket tests)

## API Endpoints
- Base HTTP: `https://billsense-api-340624938055.asia-southeast2.run.app`
- Base WSS: `wss://billsense-api-340624938055.asia-southeast2.run.app`
- REST: `/api/standard-scan`, `/api/multi-scan`, `/api/video-scan`, `/api/health`
- WebSocket: `/ws/standard-scan`, `/ws/real-multi-scan`, `/ws/real-video-scan`
- Legacy: `https://ph-currency-fast-api-340624938055.asia-east1.run.app`
  - `/predict/{model_type}/image`, `/predict/{model_type}/video`

## Packages
- `com.app.billsense` - User-facing app
- `com.admin.billsense` - Admin panel

## Conventions
- Java 11 source/target compatibility (NOT Kotlin — generate Java code)
- ViewBinding enabled (no DataBinding, no findViewById)
- BuildConfig enabled for environment variables
- OkHttp for HTTP/WebSocket, Retrofit for structured API calls
- CameraX for camera operations
- Firebase Realtime Database for data storage (NOT Firestore)
- All scan activities run in landscape orientation
- Image frames sent as base64-encoded JPEG over WebSocket (80% quality)
- Glide for image loading
- Material Design components

## Build Commands (from BillSense/ directory)
- Debug build: `./gradlew assembleDebug`
- Install: `./gradlew installDebug`
- Clean: `./gradlew clean`

## Important Notes
- `local.properties` contains MAPS_API_KEY — never commit
- `app/src/main/res/raw/service_account.json` — sensitive, do not expose
- The `gmailbackgroundlibrary` module is a local dependency for email functionality
- Real-time scanning auto-starts on WebSocket connection (no START_SCAN command needed)
- YOLOv8 models use Oriented Bounding Box (OBB) detection with angle output
