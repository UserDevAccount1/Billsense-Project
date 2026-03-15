# BillSense Android App - Claude Instructions

## Source Structure
```
app/src/main/java/com/app/billsense/
├── activities/       # 20+ Activity classes (Home, Login, Detection, Chat, etc.)
├── adapters/         # 30+ RecyclerView adapters
├── api/pojo/         # Abacus AI chatbot API (RetrofitClient, AbacusApiService)
├── fcm/              # Firebase Cloud Messaging (notifications)
├── fragments/        # Scan fragments, Maps
├── interfaces/       # Firebase callbacks (FBInterface)
├── model/            # Data models (StandardScan, MultiScan, VideoScan, Users, etc.)
├── scan/
│   ├── standard/     # StandardScanActivity, StandardPostScanActivity
│   ├── multi/        # MultiScanActivity, MultiPostScanActivity
│   ├── video/        # VideoScanActivity, VideoPostScanActivity
│   └── pojo/         # CurrencyApiService, RealTimeScanManager, response classes
└── utils/            # FBUtils, FBStorageUtils, PrefManager, YuvToRgbConverter
```

## Scan Pipeline
1. CameraX captures frames (landscape orientation)
2. YUV → RGB conversion (YuvToRgbConverter.java)
3. Bitmap → base64 JPEG (80% quality)
4. Send over WebSocket to inference API
5. Receive JSON predictions (class, confidence, OBB points)
6. Draw results on overlay

## Key Patterns
- Activities use ViewBinding: `ActivityXxxBinding binding = ActivityXxxBinding.inflate(getLayoutInflater())`
- Firebase operations go through `FBUtils` and `FBStorageUtils`
- SharedPreferences via `PrefManager`
- All network calls use OkHttp (direct) or Retrofit (structured)
- Camera permission check pattern: check → request → handle in onRequestPermissionsResult

## Dependencies (from libs.versions.toml + build.gradle.kts)
- Firebase: database 21.0.0, storage 21.0.1, messaging 24.1.1
- Networking: OkHttp 4.12.0, Retrofit 2.9.0, Gson 2.10.1
- Camera: CameraX 1.4.2
- UI: Material 1.12.0, Glide 4.16.0, CircleImageView, PhotoView
- Media: Media3 ExoPlayer 1.3.1
- Auth: Google Auth Library 1.24.0
- Maps: Play Services Maps 19.2.0, Places 4.3.1
