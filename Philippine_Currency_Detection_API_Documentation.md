# Philippine Currency Detection API - Documentation

## 1. Project Overview
I have implemented a FastAPI-based inference service for detecting and classifying Philippine currency notes, including visible-light detection and UV security feature detection.

Two YOLOv8 (Oriented Bounding Box) models are deployed:
- **Simple Model** – Detects and classifies entire banknotes as either Real or Fake.
- **UV Model** – Detects and localizes multiple UV-based and security features visible under ultraviolet light.

The API is deployed on **Google Cloud Run** at the following base URL:
```
https://ph-currency-fast-api-340624938055.asia-east1.run.app
```

The API supports:
- Single image predictions (file upload or URL input)
- Video predictions (file upload or URL input)
- Optional annotated media return (`return_media=true`)
- JSON output containing detection results in a consistent, developer-friendly format

---

## 2. Models Used

### 2.1 Simple Model (Currency Classification)
**Path:** `simple_model.pt`  
**Classes:**
```
Real
Fake
```

**Purpose:** Detects a banknote and classifies it as "Real" or "Fake".

---

### 2.2 UV Model (Security Feature Detection)
**Path:** `uv_model.pt`  
**Classes:**
```
concealed-value
value
serial-number
optically-variable-thread
security-thread
UV-thread
```

**Purpose:** Detects UV-based and visible security features in banknotes imaged under UV light.

---

## 3. API Overview
All endpoints follow the same structure:

### Path Parameters:
- **`model_type`**: `"simple"` or `"uv"`

### Query Parameters:
- **`return_media`** (bool): Whether to return annotated image/video instead of JSON.
- **`conf_threshold`** (float, default = 0.15): Minimum confidence for predictions.

### Input:
- Either:
  - **File upload** (`file` parameter, multipart/form-data)
  - **URL** (`url` query parameter)

### Output:
- **JSON** with predictions.
- If `return_media=true`, annotated media (JPEG for images, MP4 for videos) is returned.

---

## 4. Preprocessing & Prediction Logic
Before inference, all inputs undergo:
1. **Auto Orientation** – Adjusts image rotation using EXIF data.
2. **Letterboxing** – Resizes to `640x640` with aspect ratio preserved, padding added.
3. **YOLOv8 Inference** – Detects objects with Oriented Bounding Boxes.
4. **Bounding Box Mapping** – Coordinates mapped back to original image dimensions.
5. **Drawing** – Each detection is drawn with a colored bounding box.  
   - UV threads **do not** display labels or confidence scores.

---

## 5. Endpoints

### 5.1 Predict Single Image
```
POST /predict/{model_type}/image
```

#### Parameters
- `model_type` = `simple` or `uv`
- `file`: Image file upload (JPEG/PNG)
- `url`: URL of image (alternative to file)
- `return_media`: `true` or `false`
- `conf_threshold`: Confidence threshold (float)

#### Response
- **JSON** only, or annotated JPEG if `return_media=true`.

#### Example PowerShell Call (File Upload)
```powershell
curl.exe -X POST "https://ph-currency-fast-api-340624938055.asia-east1.run.app/predict/simple/image?return_media=false" `
     -H "Content-Type: multipart/form-data" `
     -F "file=@path/to/your/file/image.jpg"
```

---

### 5.2 Predict Video
```
POST /predict/{model_type}/video
```

#### Parameters
- `model_type` = `simple` or `uv`
- `file`: Video file upload (MP4)
- `url`: URL of video (alternative to file)
- `return_media`: `true` or `false`
- `conf_threshold`: Confidence threshold (float)

#### Response
- **JSON** only, or annotated MP4 if `return_media=true`.

#### Example PowerShell Call (File Upload, JSON only)
```powershell
curl.exe -X POST "https://ph-currency-fast-api-340624938055.asia-east1.run.app/predict/uv/video?return_media=false" `
     -H "Content-Type: multipart/form-data" `
     -F "file=@path/to/your/file/video.mp4"
```

#### Example PowerShell Call (File Upload, Video bytes in Response(can be saved into mp4))
```powershell
curl.exe -X POST "https://ph-currency-fast-api-340624938055.asia-east1.run.app/predict/simple/video?return_media=true" `
     -H "Content-Type: multipart/form-data" `
     -F "file=@path/to/your/file/image.mp4" `
     --output annotated_video.mp4
```

---

## 6. JSON Output Format

### Example (Simple Model, Single Image)
```json
{
  "predictions": [
    {
      "class": "Real",
      "confidence": 0.961342453956604,
      "points": [
        [631, 188],
        [631, 451],
        [8, 452],
        [7, 189]
      ]
    }
  ]
}
```

### Example (UV Model, Single Image)
```json
{
  "predictions": [
    {
      "class": "concealed-value",
      "confidence": 0.9302593469619751,
      "points": [[81, 187], [157, 188], [155, 275], [79, 274]]
    },
    {
      "class": "UV-thread",
      "confidence": 0.39564451575279236,
      "points": [[52, 397], [68, 397], [68, 416], [52, 416]]
    }
  ]
}
```

---

## 7. Media Return Behaviour
- **Images**: Returned as JPEG (`image/jpeg`).
- **Videos**: Returned as MP4 (`video/mp4`).
- `return_media=true` returns annotated media **instead of JSON** in the current version.
- If JSON and media are both required in one call, the API would need a multipart/mixed response (future option).

---

## 8. Developer Notes & Best Practices
- Always match `model_type` to the correct image type:  
  - `"simple"` → normal visible-light currency images.  
  - `"uv"` → UV-light images of currency.
- For videos, processing time increases with length and resolution.
- Use `conf_threshold` to control detection strictness.
- Avoid sending extremely large files (>50MB) to keep latency low.

---

## 9. Error Handling
- **400 Bad Request** → Missing `file` or `url`.
- **400 Bad Request** → Invalid `model_type`.
- **Failed to download** → If provided URL is unreachable.

---
