from fastapi import FastAPI, File, UploadFile, HTTPException, Request, Form, WebSocket
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, HTMLResponse, RedirectResponse, FileResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from ultralytics import YOLO
from typing import List, Dict, Any
import cv2
import numpy as np
import uuid
from datetime import datetime
import base64
import json
import traceback
import os
from pathlib import Path
import tempfile
import asyncio
from concurrent.futures import ThreadPoolExecutor
import time
from starlette.websockets import WebSocketDisconnect
import io
from collections import Counter
from PIL import Image
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
import json
import numpy as np
from typing import Any

class NumpyEncoder(json.JSONEncoder):
    """Custom JSON encoder for numpy types"""
    def default(self, obj):
        if isinstance(obj, (np.integer, np.int32, np.int64)):
            return int(obj)
        elif isinstance(obj, (np.floating, np.float32, np.float64)):
            return float(obj)
        elif isinstance(obj, np.ndarray):
            return obj.tolist()
        elif isinstance(obj, np.bool_):
            return bool(obj)
        elif hasattr(obj, 'tolist'):
            return obj.tolist()
        return super().default(obj)

def safe_json_dumps(obj):
    """Safely convert any object to JSON string"""
    return json.dumps(obj, cls=NumpyEncoder, ensure_ascii=False)

def make_serializable(obj):
    """Convert numpy types and other non-serializable objects to basic types"""
    if obj is None:
        return None
    elif isinstance(obj, (np.integer, np.int32, np.int64)):
        return int(obj)
    elif isinstance(obj, (np.floating, np.float32, np.float64)):
        return float(obj)
    elif isinstance(obj, np.ndarray):
        return obj.tolist()
    elif isinstance(obj, np.bool_):
        return bool(obj)
    elif isinstance(obj, (str, int, float, bool)):
        return obj
    elif isinstance(obj, dict):
        return {str(k): make_serializable(v) for k, v in obj.items()}
    elif isinstance(obj, list):
        return [make_serializable(item) for item in obj]
    elif isinstance(obj, tuple):
        return tuple(make_serializable(item) for item in obj)
    elif hasattr(obj, '__dict__'):
        return make_serializable(obj.__dict__)
    elif hasattr(obj, 'tolist'):
        return obj.tolist()
    else:
        try:
            return str(obj)
        except:
            return None

# ----------------------------
# Firebase import (optional)
# ----------------------------
try:
    from firebase_config import firebase_client
    FIREBASE_AVAILABLE = True
    print("✅ Firebase module imported successfully")
except ImportError as e:
    print(f"❌ Firebase import error: {e}")
    FIREBASE_AVAILABLE = False
    # Create a dummy firebase_client for development
    class DummyFirebaseClient:
        def store_scan_result(self, result_data, user_id, collection_name):
            print(f"🟡 [DUMMY] Would store in {collection_name}: {result_data.get('scan_id', 'unknown')}")
            return f"dummy_id_{uuid.uuid4()}"
        
        def store_annotated_image(self, image, user_id, scan_id, custom_path=None):
            print(f"🟡 [DUMMY] Would store annotated image for scan: {scan_id}")
            return f"dummy_image_url_{scan_id}"
    
    firebase_client = DummyFirebaseClient()

# ----------------------------
# App initialization
# ----------------------------
app = FastAPI(title="BillSense Fake Bill Detection API", version="17.17")

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ----------------------------
# Lazy Loading for YOLO Models
# ----------------------------
class LazyModelLoader:
    def __init__(self):
        self.models = {}
        self.loaded = False
        
    def load_models(self):
        """Load all YOLO models on demand"""
        if self.loaded:
            return
            
        try:
            print("🔄 Starting lazy loading of YOLO models...")
            
            # List available model files
            import os
            model_files = os.listdir("models")
            print(f"📁 Model files found: {model_files}")
            
            print("🔄 Loading denomination model...")
            self.models['denomination'] = YOLO("models/denomination2.pt")
            print("✅ Denomination model loaded")
            
            print("🔄 Loading security model...")
            self.models['security'] = YOLO("models/security_best.pt")
            print("✅ Security model loaded")
            
            print("🔄 Loading OVI model...")
            self.models['ovi'] = YOLO("models/ovi.pt")
            print("✅ OVI model loaded")
            
            print("🔄 Loading OVD model...")
            self.models['ovd'] = YOLO("models/ovd.pt")
            print("✅ OVD model loaded")
            
            print("🔄 Loading EVP model...")
            self.models['evp'] = YOLO("models/evp.pt")
            print("✅ EVP model loaded")
            
            print("🔄 Loading counterfeit model...")
            self.models['counterfeit'] = YOLO("models/counterfeit_best.pt")
            print("✅ Counterfeit model loaded")

            # Merged security/counterfeit model: genuine security features + FALSE markers
            # (watermark, see-through, shadow thread, threads, OVI, EVP + false_* versions).
            try:
                self.models['securitycf'] = YOLO("models/securitycf.pt")
                print("✅ Security-CF model loaded")
            except Exception as e:
                self.models['securitycf'] = None
                print(f"🟡 securitycf model not available (skipping): {e}")

            self.loaded = True
            print("✅ All models loaded successfully!")
            
        except Exception as e:
            print(f"❌ Error loading models: {e}")
            import traceback
            traceback.print_exc()
            # Fallback to dummy models
            class DummyModel:
                def __init__(self, name):
                    self.name = name
                    self.names = {0: '100', 1: '1000', 2: '20', 3: '200', 4: '50', 5: '500'}
                def __call__(self, image, verbose=False):
                    class Results:
                        def __init__(self):
                            self.boxes = None
                    return [Results()]
            
            self.models['denomination'] = DummyModel("denomination2")
            self.models['security'] = DummyModel("security")
            self.models['ovi'] = DummyModel("ovi")
            self.models['ovd'] = DummyModel("ovd")
            self.models['evp'] = DummyModel("evp")
            self.models['counterfeit'] = DummyModel("counterfeit")
            self.models['securitycf'] = None
            self.loaded = True
            print("🟡 Using dummy models due to loading error")
    
    def get_model(self, name):
        """Get a specific model, loading if necessary"""
        if not self.loaded:
            self.load_models()
        return self.models.get(name)

# Global lazy loader instance
model_loader = LazyModelLoader()

# ----------------------------
# Configuration
# ----------------------------
# Minimum % of expected security features that must be verified for a single scan
# to be called GENUINE (lighting/tilt-dependent features can't show in one photo).
# Tune this to trade off false-positives vs false-negatives. Multi-Scan accumulates
# features across angles and will comfortably exceed this.
GENUINE_COVERAGE_THRESHOLD = 50.0

DENOMINATION_CLASSES = ['100', '1000', '20', '200', '50', '500']
SECURITY_MODEL_CLASSES = ['concealed value', 'security thread', 'serial number', 'value', 'value watermark', 'watermark', 'see through mark']
OVI_CLASSES = ['optically variable ink']
OVD_CLASSES = ['ovd']
EVP_CLASSES = ['1k enhanced value panel', '500 enhanced value panel', 'false 1k enhanced value panel', 'false 500 enhanced value panel']
COUNTERFEIT_MODEL_CLASSES = ['UV-thread', 'concealed-value', 'security-thread', 'serial-number', 'symbol-of-nature', 'value']
# Merged security/counterfeit model (securitycf.pt): 8 genuine features + 8 FALSE markers.
SECURITYCF_CLASSES = ['bill', 'watermark', 'see_through_mark', 'shadow_thread', 'security_thread',
                      'concealed_value', 'enhanced_value_panel', 'ovi',
                      'false_bill', 'false_watermark', 'false_see_through_mark', 'false_shadow_thread',
                      'false_security_thread', 'false_concealed_value', 'false_enhanced_value_panel', 'false_ovi']

NUMBER_TO_FEATURE_MAPPING = {
    '1': 'watermark',
    '2': 'value',
    '3': 'serial_number',
    '4': 'security_thread',
    '5': 'concealed_value',
    '6': 'see_through_mark',
    '7': 'optically_variable_ink',
    '8': 'ovd',
    '9': 'enhanced_value_panel',
    '10': 'value_watermark',
    '11': 'uv_thread',
    '12': 'symbol_of_nature',
    '13': 'optically_variable_thread',
    '14': 'shadow_thread',
}

FEATURE_TO_NUMBER_MAPPING = {v: k for k, v in NUMBER_TO_FEATURE_MAPPING.items()}

# ----------------------------
# Thread pool for async processing
# ----------------------------
executor = ThreadPoolExecutor(max_workers=4)

# ----------------------------
# Connection Manager for WebSocket state management
# ----------------------------
class ConnectionManager:
    def __init__(self):
        self.active_connections: Dict[str, Dict] = {}
        
    async def connect(self, websocket: WebSocket, client_id: str):
        await websocket.accept()
        self.active_connections[client_id] = {
            "websocket": websocket,
            "scan_state": "scanning",  # AUTO-START: Immediately set to scanning
            "scan_data": {},
            "last_activity": time.time()
        }
        return client_id
    
    def disconnect(self, client_id: str):
        if client_id in self.active_connections:
            del self.active_connections[client_id]
    
    def update_scan_state(self, client_id: str, state: str, data: Dict = None):
        if client_id in self.active_connections:
            self.active_connections[client_id]["scan_state"] = state
            self.active_connections[client_id]["last_activity"] = time.time()
            if data:
                self.active_connections[client_id]["scan_data"].update(data)
    
    def get_scan_state(self, client_id: str) -> Dict:
        return self.active_connections.get(client_id, {})

manager = ConnectionManager()

# ----------------------------
# Firebase Storage Helper Functions
# ----------------------------

def create_numbered_annotated_image(image: np.ndarray, security_detections: List[Dict], 
                                   ovi_detections: List[Dict], ovd_detections: List[Dict],
                                   evp_detections: List[Dict], counterfeit_detections: List[Dict]) -> np.ndarray:
    """Draw numbered labels on the image instead of bounding boxes"""
    try:
        print("🎨 Creating numbered annotated image...")
        annotated_img = image.copy()
        
        # Combine all detections and map to numbers
        all_detections = []
        all_detections.extend([(det, 'security') for det in security_detections])
        all_detections.extend([(det, 'ovi') for det in ovi_detections])
        all_detections.extend([(det, 'ovd') for det in ovd_detections])
        all_detections.extend([(det, 'evp') for det in evp_detections])
        all_detections.extend([(det, 'counterfeit') for det in counterfeit_detections])
        
        # Map detections to numbers
        numbered_detections = []
        
        for detection, model_type in all_detections:
            label = detection.get('label', '')
            bbox = detection.get('bbox', [])
            confidence = detection.get('confidence', 0)
            
            # Map label to feature and get number
            feature_name = None
            
            # Security model mappings
            if label == 'watermark':
                feature_name = 'watermark'
            elif label == 'value watermark':
                feature_name = 'value_watermark'
            elif label == 'value':
                feature_name = 'value'
            elif label == 'serial number':
                feature_name = 'serial_number'
            elif label == 'security thread':
                feature_name = 'security_thread'
            elif label == 'concealed value':
                feature_name = 'concealed_value'
            elif label == 'see through mark':
                feature_name = 'see_through_mark'

            # OVI model
            elif label == 'optically variable ink':
                feature_name = 'optically_variable_ink'

            # OVD model
            elif label == 'ovd':
                feature_name = 'ovd'

            # EVP model
            elif 'enhanced value panel' in label.lower():
                if 'false' in label.lower():
                    feature_name = 'false_enhanced_value_panel'
                else:
                    feature_name = 'enhanced_value_panel'

            # Counterfeit model mappings
            elif label == 'concealed-value':
                feature_name = 'concealed_value'
            elif label == 'security-thread':
                feature_name = 'security_thread'
            elif label == 'serial-number':
                feature_name = 'serial_number'
            elif label == 'UV-thread':
                feature_name = 'uv_thread'
            elif label == 'symbol-of-nature':
                feature_name = 'symbol_of_nature'
            elif label == 'optically-variable-thread':
                feature_name = 'optically_variable_thread'
            
            if feature_name and feature_name in FEATURE_TO_NUMBER_MAPPING:
                number = FEATURE_TO_NUMBER_MAPPING[feature_name]
                numbered_detections.append({
                    'number': number,
                    'feature': feature_name,
                    'bbox': bbox,
                    'confidence': confidence,
                    'original_label': label
                })
        
        # Human-readable tags for each detected security feature, drawn ON the bill.
        DISPLAY_LABELS = {
            'value': 'Value', 'serial_number': 'Serial No.', 'security_thread': 'Security Thread',
            'concealed_value': 'Concealed Value', 'watermark': 'Watermark', 'value_watermark': 'Value Watermark',
            'see_through_mark': 'See-through', 'uv_thread': 'UV Thread', 'symbol_of_nature': 'Symbol of Nature',
            'optically_variable_ink': 'OVI', 'optically_variable_thread': 'OV Thread',
            'ovd': 'OVD', 'enhanced_value_panel': 'Value Panel', 'false_enhanced_value_panel': 'FALSE PANEL',
        }
        # Adaptive sizing so tags stay readable across image resolutions.
        h, w = annotated_img.shape[:2]
        font = cv2.FONT_HERSHEY_SIMPLEX
        font_scale = max(0.5, min(1.1, w / 1400.0))
        thick = max(1, int(round(font_scale * 2)))
        box_thick = max(2, int(round(font_scale * 3)))

        # De-duplicate by feature (keep the highest-confidence box per feature)
        best_by_feature = {}
        for d in numbered_detections:
            f = d.get('feature')
            if f and (f not in best_by_feature or d.get('confidence', 0) > best_by_feature[f].get('confidence', 0)):
                best_by_feature[f] = d

        for detection in best_by_feature.values():
            bbox = detection.get('bbox', [])
            if len(bbox) != 4:
                continue
            x1, y1, x2, y2 = map(int, bbox)
            feat = detection.get('feature', '')
            is_fake = feat == 'false_enhanced_value_panel'
            color = (0, 0, 255) if is_fake else (40, 175, 60)  # BGR: red = forgery marker, green = genuine
            num = detection.get('number', '')
            name = DISPLAY_LABELS.get(feat, feat.replace('_', ' ').title())
            label = (f"{num}. {name}" if num else name)

            # Bounding box around the feature
            cv2.rectangle(annotated_img, (x1, y1), (x2, y2), color, box_thick)

            # Filled tag with the feature name, placed just above the box (or below if no room)
            (tw, th), base = cv2.getTextSize(label, font, font_scale, thick)
            pad = max(4, int(font_scale * 6))
            ty = y1 - pad if (y1 - th - 2 * pad) > 0 else y2 + th + 2 * pad
            tag_top = ty - th - pad
            tag_bottom = ty + base
            cv2.rectangle(annotated_img, (x1, tag_top), (min(x1 + tw + 2 * pad, w - 1), tag_bottom), color, -1)
            cv2.putText(annotated_img, label, (x1 + pad, ty), font, font_scale, (255, 255, 255), thick, cv2.LINE_AA)
            print(f"  🏷️ Tagged '{label}' at ({x1},{y1})-({x2},{y2})")

        print(f"✅ Tagged annotation completed: {len(best_by_feature)} security features labelled on the bill")
        return annotated_img
        
    except Exception as e:
        print(f"❌ Numbered annotation error: {e}")
        traceback.print_exc()
        return image

async def store_annotated_image_for_realtime(image: np.ndarray, scan_type: str, user_id: str, scan_id: str) -> str:
    """Store annotated image for real-time scans"""
    try:
        if not FIREBASE_AVAILABLE:
            return "firebase_unavailable"
        
        # Create storage path
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        storage_path = f"real_time_{scan_type}/{user_id}/{timestamp}_{scan_id}/annotated.jpg"
        
        # Store image
        image_url = firebase_client.store_annotated_image(image, user_id, scan_id, custom_path=storage_path)
        print(f"✅ Annotated image stored for real-time scan: {image_url}")
        
        return image_url
        
    except Exception as e:
        print(f"❌ Error storing annotated image: {e}")
        return "image_storage_error"

async def store_real_time_scan_result(scan_type: str, result_data: Dict[str, Any], user_id: str = "anonymous") -> str:
    """Store real-time scan results in Firebase WITH annotated images"""
    try:
        if not FIREBASE_AVAILABLE:
            print("🟡 Firebase not available - skipping storage")
            return "firebase_unavailable"
        
        scan_id = result_data.get("scan_id", str(uuid.uuid4()))
        
        # Prepare the scan result for Firebase
        firebase_result = {
            'scan_id': scan_id,
            'user_id': user_id,
            'timestamp': datetime.now().isoformat(),
            'scan_type': f'real_time_{scan_type}',
            'authenticity_status': result_data.get("authenticity", "UNKNOWN"),
            'is_genuine': result_data.get("is_genuine", False),
            'denomination': result_data.get("denomination", "UNKNOWN"),
            'confidence': result_data.get("confidence", "LOW"),
            'coverage_percentage': result_data.get("coverage_percentage", 0),
            'features_detected': result_data.get("features_detected", []),
            'feature_count': result_data.get("feature_count", 0),
            'total_expected_features': result_data.get("total_expected_features", 0),
            'frames_processed': result_data.get("frames_processed", 0),
            'angles_processed': result_data.get("angles_processed", 0),
            'processing_time': result_data.get("processing_time", 0),
            'reasons': result_data.get("reasons", []),
            'has_false_evp': result_data.get("has_false_evp", False),
            'is_high_denomination': result_data.get("is_high_denomination", False),
            'currency': 'PHP',
            'model_used': 'Multi-Model Ensemble',
            'logic_version': '17.17',
            'storage_policy': 'with_annotated_images',
            'annotated_image_url': result_data.get("annotated_image_url", ""),
            'image_stored': bool(result_data.get("annotated_image_url"))
        }
        
        # Add scan-type specific data
        if scan_type == "multi_scan":
            firebase_result.update({
                'angle_results': result_data.get("angle_results", []),
                'total_angles': result_data.get("total_angles", 3),
                'angle_images': result_data.get("angle_images", [])
            })
        elif scan_type == "video_scan":
            firebase_result.update({
                'best_confidence': result_data.get("best_confidence", 0),
                'total_frames_analyzed': result_data.get("frames_processed", 0)
            })
        
        # Determine collection name
        collection_map = {
            "standard_scan": "real_time_standard_scans",
            "multi_scan": "real_time_multi_scans", 
            "video_scan": "real_time_video_scans"
        }
        
        collection_name = collection_map.get(scan_type, "real_time_scans")
        
        # Store in Firebase
        storage_id = firebase_client.store_scan_result(firebase_result, user_id, collection_name)
        print(f"✅ Real-time {scan_type} result stored in Firebase: {storage_id}")
        
        return storage_id
        
    except Exception as e:
        print(f"❌ Error storing real-time scan result: {e}")
        return f"storage_error_{str(e)}"

# ----------------------------
# QUALITY ANALYSIS FUNCTIONS
# ----------------------------

def analyze_frame_quality(frame: np.ndarray) -> Dict[str, float]:
    """Analyze frame quality metrics for ALL scan types"""
    try:
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        
        # Sharpness (Laplacian variance)
        sharpness = cv2.Laplacian(gray, cv2.CV_64F).var()
        
        # Brightness (0-255 scale)
        brightness = np.mean(gray)
        
        # Contrast (standard deviation)
        contrast = np.std(gray)
        
        # Quality score (0-100)
        sharpness_score = min(100, (sharpness / 100) * 100)  # Normalize sharpness
        brightness_score = 100 - abs(brightness - 127) / 127 * 100  # Ideal around 127
        contrast_score = min(100, (contrast / 80) * 100)  # Normalize contrast
        
        overall_quality = (sharpness_score * 0.4 + brightness_score * 0.3 + contrast_score * 0.3)
        
        # Quality assessment
        if overall_quality >= 70:
            quality_status = "EXCELLENT"
        elif overall_quality >= 50:
            quality_status = "GOOD"
        elif overall_quality >= 30:
            quality_status = "FAIR"
        else:
            quality_status = "POOR"
        
        return {
            "sharpness": round(sharpness, 2),
            "brightness": round(brightness, 2),
            "contrast": round(contrast, 2),
            "sharpness_score": round(sharpness_score, 1),
            "brightness_score": round(brightness_score, 1),
            "contrast_score": round(contrast_score, 1),
            "overall_quality": round(overall_quality, 1),
            "quality_status": quality_status
        }
        
    except Exception as e:
        print(f"❌ Error analyzing frame quality: {e}")
        return {
            "sharpness": 0,
            "brightness": 0,
            "contrast": 0,
            "sharpness_score": 0,
            "brightness_score": 0,
            "contrast_score": 0,
            "overall_quality": 0,
            "quality_status": "UNKNOWN"
        }

def is_frame_quality_acceptable(frame: np.ndarray, min_quality: float = 30.0) -> Dict[str, Any]:
    """Check if frame quality meets minimum standards with detailed feedback"""
    quality_metrics = analyze_frame_quality(frame)
    is_acceptable = quality_metrics["overall_quality"] >= min_quality
    
    feedback = []
    if quality_metrics["brightness_score"] < 50:
        feedback.append("Adjust lighting - image is too dark or bright")
    if quality_metrics["sharpness_score"] < 40:
        feedback.append("Hold camera steady - image is blurry")
    if quality_metrics["contrast_score"] < 40:
        feedback.append("Improve contrast - features may not be visible")
    
    return {
        "is_acceptable": is_acceptable,
        "quality_metrics": quality_metrics,
        "feedback": feedback,
        "min_required_quality": min_quality
    }

# ----------------------------
# CORE HELPER FUNCTIONS (WITH COMPREHENSIVE ERROR HANDLING)
# ----------------------------

async def read_image(uploaded_file: UploadFile) -> np.ndarray:
    """Read image from UploadFile with proper error handling"""
    try:
        print(f"📁 Reading image: {uploaded_file.filename}")
        
        # Read the file content
        image_data = await uploaded_file.read()
        
        if not image_data:
            raise HTTPException(status_code=400, detail="Empty image file")
        
        print(f"📊 Image data size: {len(image_data)} bytes")
        
        # Try OpenCV first
        try:
            np_img = np.frombuffer(image_data, np.uint8)
            img = cv2.imdecode(np_img, cv2.IMREAD_COLOR)
            
            if img is None:
                raise ValueError("OpenCV failed to decode image")
            
            print(f"✅ Image loaded via OpenCV: {img.shape}")
            return img
        except Exception as cv_error:
            print(f"⚠️ OpenCV failed, trying PIL: {cv_error}")
        
        # Use PIL as fallback
        try:
            image = Image.open(io.BytesIO(image_data))
            # Convert to RGB if necessary
            if image.mode != 'RGB':
                image = image.convert('RGB')
            img = cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)
            print(f"✅ Image loaded via PIL: {img.shape}")
            return img
        except Exception as pil_error:
            print(f"❌ PIL also failed: {pil_error}")
        
        raise HTTPException(status_code=400, detail="Invalid image format - cannot read with OpenCV or PIL")
        
    except Exception as e:
        print(f"❌ Error reading image: {e}")
        raise HTTPException(status_code=400, detail=f"Error reading image: {str(e)}")

def is_detection_on_bill(bbox, image_shape, margin_ratio=0.1):
    """Check if detection is within reasonable bounds of the bill"""
    try:
        if len(bbox) != 4:
            return False
            
        x1, y1, x2, y2 = bbox
        img_h, img_w = image_shape[:2]
        
        # Calculate center of detection
        center_x = (x1 + x2) / 2
        center_y = (y1 + y2) / 2
        
        margin_w = img_w * margin_ratio
        margin_h = img_h * margin_ratio
        
        # Check if detection is within central area (where bill should be)
        is_valid = (margin_w <= center_x <= img_w - margin_w and 
                   margin_h <= center_y <= img_h - margin_h)
        
        return is_valid
    except Exception:
        return False

def simple_detection(model, image: np.ndarray, use_classes: List[str], min_confidence: float = 0.2) -> List[Dict]:
    """General-purpose detection wrapper with lower confidence threshold for watermarks"""
    try:
        if hasattr(model, 'name') and "dummy" in model.name.lower():
            print("🟡 Using dummy detection -> returning []")
            return []

        print(f"🔍 Running detection on {len(use_classes)} target classes")
        results = model(image, verbose=False)
        detections = []

        if results and len(results) > 0 and getattr(results[0], 'boxes', None) is not None:
            boxes = results[0].boxes
            for i, box in enumerate(boxes):
                try:
                    conf = float(box.conf[0]) if hasattr(box.conf, '__len__') else float(box.conf)
                except Exception:
                    try:
                        conf = float(box.conf)
                    except Exception:
                        conf = 0.0
                
                # LOWER CONFIDENCE THRESHOLD FOR WATERMARK-RELATED CLASSES
                watermark_classes = ['watermark', 'value watermark', 'concealed value']
                current_min_confidence = 0.15 if any(wm_class in use_classes for wm_class in watermark_classes) else min_confidence
                
                if conf < current_min_confidence:
                    continue
                    
                try:
                    cls_id = int(box.cls[0]) if hasattr(box.cls, '__len__') else int(box.cls)
                except Exception:
                    continue
                
                label = model.names.get(cls_id) if isinstance(model.names, dict) else model.names[cls_id]
                
                # Skip embossed design from OVD model
                if model == model_loader.get_model('ovd') and label == 'embossed design':
                    continue
                
                # Handle watermark variations - NORMALIZE LABELS
                if label == 'value watermark':
                    label = 'watermark'
                
                if label in use_classes:
                    bbox = []
                    try:
                        if hasattr(box, 'xyxy') and box.xyxy is not None:
                            bbox = box.xyxy[0].cpu().numpy().tolist()
                        elif hasattr(box, 'xyxyn') and box.xyxyn is not None:
                            bbox = box.xyxyn[0].cpu().numpy().tolist()
                    except Exception:
                        bbox = []
                    
                    # Validate detection is on the bill (not in background)
                    if bbox and len(bbox) == 4 and not is_detection_on_bill(bbox, image.shape):
                        print(f"  ⚠️ Skipping detection in background: {label} (conf: {conf:.3f})")
                        continue
                    
                    detections.append({
                        "label": label,
                        "confidence": round(conf, 3),
                        "bbox": bbox
                    })
                    print(f"  ✅ Detected: {label} (conf: {conf:.3f})")
                    
        print(f"✅ Detection completed: {len(detections)} objects found")
        return detections
        
    except Exception as e:
        print(f"❌ Detection error: {e}")
        traceback.print_exc()
        return []

def detect_watermark_specific(image: np.ndarray) -> Dict[str, Any]:
    """Specialized watermark detection with multiple aggressive attempts"""
    watermark_results = {
        'detected': False,
        'confidence': 0.0,
        'method': 'none',
        'details': []
    }
    
    # Try security model with normal confidence first
    security_dets = simple_detection(model_loader.get_model('security'), image, ['watermark', 'value watermark'], min_confidence=0.15)
    for det in security_dets:
        if det.get('label', '') in ['watermark', 'value watermark']:
            watermark_results['detected'] = True
            watermark_results['confidence'] = max(watermark_results['confidence'], det.get('confidence', 0))
            watermark_results['method'] = 'security_model_normal'
            watermark_results['details'].append({
                'model': 'security',
                'label': det.get('label', ''),
                'confidence': det.get('confidence', 0),
                'bbox': det.get('bbox', [])
            })
    
    # If no watermark detected, try with very low confidence
    if not watermark_results['detected']:
        security_dets_low = simple_detection(model_loader.get_model('security'), image, ['watermark', 'value watermark'], min_confidence=0.05)
        for det in security_dets_low:
            if det.get('label', '') in ['watermark', 'value watermark'] and det.get('confidence', 0) > 0.05:
                watermark_results['detected'] = True
                watermark_results['confidence'] = max(watermark_results['confidence'], det.get('confidence', 0))
                watermark_results['method'] = 'security_model_low_conf'
                watermark_results['details'].append({
                    'model': 'security_low',
                    'label': det.get('label', ''),
                    'confidence': det.get('confidence', 0),
                    'bbox': det.get('bbox', [])
                })
    
    # Final attempt: try with ANY confidence above 0.01
    if not watermark_results['detected']:
        security_dets_very_low = simple_detection(model_loader.get_model('security'), image, ['watermark', 'value watermark'], min_confidence=0.01)
        for det in security_dets_very_low:
            if det.get('label', '') in ['watermark', 'value watermark']:
                watermark_results['detected'] = True
                watermark_results['confidence'] = max(watermark_results['confidence'], det.get('confidence', 0))
                watermark_results['method'] = 'security_model_very_low'
                watermark_results['details'].append({
                    'model': 'security_very_low',
                    'label': det.get('label', ''),
                    'confidence': det.get('confidence', 0),
                    'bbox': det.get('bbox', [])
                })
                print(f"  💧 WATERMARK DETECTED with very low confidence: {det.get('confidence', 0):.3f}")
    
    return watermark_results

def get_detected_denomination(denom_detections: List[Dict]) -> str:
    """Pick the best denomination from detections"""
    if not denom_detections:
        print("⚠️ No denomination detections provided")
        return "UNKNOWN"
    
    confident = [d for d in denom_detections if d.get('confidence', 0) >= 0.3]
    if confident:
        best = max(confident, key=lambda x: x.get('confidence', 0))
        print(f"✅ Denomination detected (confident): {best.get('label', '')} ({best.get('confidence', 0)})")
        return best.get('label', 'UNKNOWN')
    
    best_all = max(denom_detections, key=lambda x: x.get('confidence', 0))
    if best_all.get('confidence', 0) >= 0.2:
        print(f"🟡 Denomination detected (fallback): {best_all.get('label', '')} ({best_all.get('confidence', 0)})")
        return best_all.get('label', 'UNKNOWN')
    
    print("⚠️ Could not determine denomination reliably")
    return "UNKNOWN"

def is_high_denomination(denomination: str) -> bool:
    """Check if denomination is 500 or 1000"""
    return denomination in ['500', '1000']

async def detect_security_features_parallel(image: np.ndarray, denomination: str) -> Dict[str, Any]:
    """Detect security features with PARALLEL model execution"""
    try:
        is_high_denom = is_high_denomination(denomination)
        
        print(f"🔍 PARALLEL DETECTION: Denomination: {denomination}, Is High: {is_high_denom}")
        
        # Initialize ALL security features the models can detect (shown in the checklist).
        basic_features = {
            'value': False,
            'serial_number': False,
            'security_thread': False,
            'concealed_value': False,
            'watermark': False,
            'value_watermark': False,
            'see_through_mark': False,
            'uv_thread': False,
            'symbol_of_nature': False,
            'shadow_thread': False,
        }

        # High denomination only features (₱500 / ₱1000)
        high_denom_features = {
            'optically_variable_ink': False,
            'optically_variable_thread': False,
            'ovd': False,
            'enhanced_value_panel': False,
        }
        
        # Counterfeit indicators (FALSE markers from the securitycf model + EVP model)
        counterfeit_indicators = {
            'false_enhanced_value_panel': False,
            'false_watermark': False,
            'false_see_through_mark': False,
            'false_shadow_thread': False,
            'false_security_thread': False,
            'false_concealed_value': False,
            'false_ovi': False,
            'false_bill': False,
        }
        
        # Run ALL models in parallel
        print("🚀 Starting parallel model execution...")
        
        # Create tasks for all models
        security_task = asyncio.to_thread(simple_detection, model_loader.get_model('security'), image, SECURITY_MODEL_CLASSES, 0.15)
        counterfeit_task = asyncio.to_thread(simple_detection, model_loader.get_model('counterfeit'), image, COUNTERFEIT_MODEL_CLASSES, 0.15)
        watermark_task = asyncio.to_thread(detect_watermark_specific, image)
        
        # High denomination models (only if needed)
        if is_high_denom:
            ovi_task = asyncio.to_thread(simple_detection, model_loader.get_model('ovi'), image, OVI_CLASSES, 0.2)
            ovd_task = asyncio.to_thread(simple_detection, model_loader.get_model('ovd'), image, OVD_CLASSES, 0.2)
            evp_task = asyncio.to_thread(simple_detection, model_loader.get_model('evp'), image, EVP_CLASSES, 0.2)
            
            # Wait for all models to complete
            security_dets, counterfeit_dets, watermark_result, ovi_dets, ovd_dets, evp_dets = await asyncio.gather(
                security_task, counterfeit_task, watermark_task, ovi_task, ovd_task, evp_task
            )
        else:
            # Wait for basic models only
            security_dets, counterfeit_dets, watermark_result = await asyncio.gather(
                security_task, counterfeit_task, watermark_task
            )
            ovi_dets, ovd_dets, evp_dets = [], [], []
        
        print("✅ All models completed parallel execution")
        
        # Process security model results
        for det in security_dets:
            label = det.get('label', '')
            confidence = det.get('confidence', 0)
            
            print(f"  🔎 Security model detected: {label} (conf: {confidence})")
            
            if label == 'concealed value':
                basic_features['concealed_value'] = True
            elif label == 'security thread':
                basic_features['security_thread'] = True
            elif label == 'serial number':
                basic_features['serial_number'] = True
            elif label == 'value':
                basic_features['value'] = True
            elif label == 'watermark':
                basic_features['watermark'] = True
                print(f"  💧 WATERMARK DETECTED with confidence: {confidence}")
            elif label == 'see through mark':
                basic_features['see_through_mark'] = True
            elif label == 'value watermark':
                basic_features['value_watermark'] = True
            elif label == 'optically variable ink':
                high_denom_features['optically_variable_ink'] = True

        # Process specialized watermark detection
        if watermark_result['detected']:
            basic_features['watermark'] = True
            print(f"  💧 SPECIALIZED WATERMARK DETECTION: {watermark_result['method']} (conf: {watermark_result['confidence']:.3f})")
        else:
            print(f"  💧 No watermark detected in specialized detection")

        # Process counterfeit model results
        for det in counterfeit_dets:
            label = det.get('label', '')
            confidence = det.get('confidence', 0)
            print(f"  🔎 Counterfeit model detected: {label} (conf: {confidence})")
            
            if label == 'concealed-value':
                basic_features['concealed_value'] = True
            elif label == 'security-thread':
                basic_features['security_thread'] = True
            elif label == 'serial-number':
                basic_features['serial_number'] = True
            elif label == 'value':
                basic_features['value'] = True
            elif label == 'UV-thread':
                basic_features['uv_thread'] = True
            elif label == 'symbol-of-nature':
                basic_features['symbol_of_nature'] = True
            elif label == 'optically-variable-thread':
                high_denom_features['optically_variable_thread'] = True

        # Merged security/counterfeit model: better watermark/see-through/shadow detection
        # AND the FALSE markers that drive a real counterfeit verdict.
        scf_model = model_loader.get_model('securitycf')
        if scf_model is not None:
            for det in simple_detection(scf_model, image, SECURITYCF_CLASSES, 0.30):
                lbl = det.get('label', ''); conf = det.get('confidence', 0)
                if lbl.startswith('false_'):
                    # 'false_bill' is the whole-note real/fake guess — it is too noisy and
                    # false-fires on genuine notes, so it must NOT condemn on its own. Only a
                    # SPECIFIC feature-forgery marker at HIGH confidence (>=0.75) counts as a
                    # candidate counterfeit signal. securitycf false-fires the lighting-dependent
                    # markers on genuine notes, so the high bar here + the corroboration rule in
                    # evaluate_counterfeit (needs >=2 markers AND weak genuine evidence) together
                    # stop genuine bills being flagged fake.
                    if lbl != 'false_bill' and conf >= 0.75 and lbl in counterfeit_indicators:
                        counterfeit_indicators[lbl] = True
                        print(f"  🚩 securitycf FALSE marker: {lbl} ({conf:.2f})")
                    elif conf >= 0.30:
                        print(f"  ℹ️ securitycf low-conf/ignored false: {lbl} ({conf:.2f})")
                elif lbl == 'enhanced_value_panel':
                    high_denom_features['enhanced_value_panel'] = True
                elif lbl == 'ovi':
                    high_denom_features['optically_variable_ink'] = True
                elif lbl in basic_features:
                    basic_features[lbl] = True
                    print(f"  ✅ securitycf feature: {lbl} ({conf:.2f})")

        # Process high denomination features if applicable
        if is_high_denom:
            if ovi_dets:
                high_denom_features['optically_variable_ink'] = True
                print(f"  🌈 OVI detected: {ovi_dets[0].get('confidence', 0):.3f}")
            
            if ovd_dets:
                high_denom_features['ovd'] = True
                print(f"  ✨ OVD detected: {ovd_dets[0].get('confidence', 0):.3f}")
            
            for det in evp_dets:
                label = det.get('label', '')
                evp_conf = det.get('confidence', 0)
                if 'false' in label.lower():
                    # Gate the false-EVP marker at high confidence — a weak EVP false detection
                    # false-fires on genuine high-denom notes (observed) and must not condemn.
                    if evp_conf >= 0.75:
                        counterfeit_indicators['false_enhanced_value_panel'] = True
                        print(f"  ⚠️ FALSE EVP detected: {evp_conf:.3f}")
                    else:
                        print(f"  ℹ️ low-conf false EVP ignored: {evp_conf:.3f}")
                else:
                    high_denom_features['enhanced_value_panel'] = True
                    print(f"  ✅ Genuine EVP detected: {det.get('confidence', 0):.3f}")
        else:
            print("  🔒 Skipping high denomination feature detection for low denomination note")

        # Combine results based on denomination type. total_expected is the FULL set of
        # security features the models look for (9 base; +4 for high-denomination notes).
        if is_high_denom:
            all_features = {**basic_features, **high_denom_features}
        else:
            all_features = dict(basic_features)
        total_expected_features = len(all_features)

        detected_features_count = sum(all_features.values())
        coverage_percentage = (detected_features_count / total_expected_features) * 100 if total_expected_features > 0 else 0
        
        # DEBUG: Print feature status
        print("📊 PARALLEL FEATURE DETECTION SUMMARY:")
        for feature, detected in all_features.items():
            status = "✅ DETECTED" if detected else "❌ MISSING"
            print(f"  {feature}: {status}")
        
        print(f"📈 Coverage: {detected_features_count}/{total_expected_features} ({coverage_percentage:.1f}%)")
        
        return {
            "security_features": all_features,
            "counterfeit_indicators": counterfeit_indicators,
            "is_high_denomination": is_high_denom,
            "total_expected_features": total_expected_features,
            "detected_features_count": detected_features_count,
            "coverage_percentage": round(coverage_percentage, 2),
            "feature_summary": f"{detected_features_count}/{total_expected_features}",
            "detection_details": {
                'security_detections': security_dets,
                'counterfeit_detections': counterfeit_dets,
                'watermark_result': watermark_result,
                'ovi_detections': ovi_dets if is_high_denom else [],
                'ovd_detections': ovd_dets if is_high_denom else [],
                'evp_detections': evp_dets if is_high_denom else []
            }
        }
        
    except Exception as e:
        print(f"❌ CRITICAL ERROR in detect_security_features_parallel: {e}")
        traceback.print_exc()
        return {
            "security_features": {
                'concealed_value': False, 'security_thread': False, 'serial_number': False,
                'value': False, 'watermark': False, 'see_through_mark': False,
                'optically_variable_ink': False, 'ovd': False, 'enhanced_value_panel': False
            },
            "counterfeit_indicators": {'false_enhanced_value_panel': False},
            "is_high_denomination": False,
            "total_expected_features": 6,
            "detected_features_count": 0,
            "coverage_percentage": 0,
            "feature_summary": "0/6",
            "detection_details": {}
        }

async def process_frame_parallel(frame: np.ndarray) -> Dict[str, Any]:
    """Process a single frame with PARALLEL model execution"""
    try:
        print("🔄 Starting PARALLEL frame processing...")
        
        # Run denomination detection
        denom_detections = simple_detection(model_loader.get_model('denomination'), frame, DENOMINATION_CLASSES, min_confidence=0.25)
        denomination = get_detected_denomination(denom_detections)
        print(f"💰 Denomination: {denomination}")
        
        # Detect security features in PARALLEL
        features_result = await detect_security_features_parallel(frame, denomination)
        
        # Safely access all features with defaults
        security_features = features_result.get("security_features", {})
        counterfeit_indicators = features_result.get("counterfeit_indicators", {})
        is_high_denom = features_result.get("is_high_denomination", False)
        
        # Full set of security features the models look for (drives the checklist).
        required_keys = ['value', 'serial_number', 'security_thread', 'concealed_value',
                         'watermark', 'value_watermark', 'see_through_mark', 'uv_thread',
                         'symbol_of_nature', 'shadow_thread']
        if is_high_denom:
            required_keys.extend(['optically_variable_ink', 'optically_variable_thread', 'ovd', 'enhanced_value_panel'])
        
        for key in required_keys:
            if key not in security_features:
                security_features[key] = False
        
        # Measurement layer: banknote frame box, per-feature geometry, capture quality
        bill_box = get_bill_frame_box(denom_detections)
        geometry = measure_feature_geometry(denomination, features_result.get("detection_details", {}), bill_box)
        try:
            overall_quality = analyze_frame_quality(frame).get("overall_quality")
        except Exception:
            overall_quality = None

        # Evaluate authenticity (geometry + quality aware)
        authenticity = evaluate_counterfeit(denomination, features_result, geometry=geometry, overall_quality=overall_quality)

        # Calibrated confidence score from the evaluator
        coverage = features_result.get("coverage_percentage", 0)
        confidence_score = authenticity.get("authenticity_score", min(100, max(0, coverage)))
        
        # Get detected features list safely
        detected_features = []
        for feature, detected in security_features.items():
            if detected and feature in required_keys:
                detected_features.append(feature)
        
        result = {
            "denomination": denomination,
            "authenticity": authenticity,
            "detected_features": detected_features,
            "feature_count": len(detected_features),
            "total_expected_features": features_result.get("total_expected_features", 0),
            "coverage_percentage": coverage,
            "confidence_score": confidence_score,
            "is_high_denomination": is_high_denom,
            "has_false_evp": counterfeit_indicators.get("false_enhanced_value_panel", False),
            "security_features": security_features,
            "counterfeit_indicators": counterfeit_indicators,
            "feature_geometry": geometry,
            "authenticity_score": confidence_score,
            "processing_mode": "parallel",
            "status": "success"
        }
        
        print(f"✅ PARALLEL frame processing completed: {len(detected_features)} features detected")
        return result
        
    except Exception as e:
        print(f"❌ CRITICAL ERROR in process_frame_parallel: {e}")
        traceback.print_exc()
        return {
            "denomination": "UNKNOWN",
            "authenticity": {
                "status": "UNKNOWN", 
                "confidence": "LOW", 
                "is_genuine": False, 
                "reasons": ["Processing error"],
                "coverage_percentage": 0,
                "detected_features_count": 0,
                "total_expected_features": 0,
                "denomination_type": "UNKNOWN",
                "has_false_evp": False
            },
            "detected_features": [],
            "feature_count": 0,
            "total_expected_features": 0,
            "coverage_percentage": 0,
            "confidence_score": 0,
            "is_high_denomination": False,
            "has_false_evp": False,
            "security_features": {},
            "counterfeit_indicators": {"false_enhanced_value_panel": False},
            "processing_mode": "parallel",
            "status": "error",
            "error": str(e)
        }

import math as _math

# --- Reference geometry for the measurement layer (Component 1) ---
REFERENCE_GEOMETRY = {}
try:
    _ref_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "reference_geometry.json")
    if os.path.exists(_ref_path):
        with open(_ref_path) as _rf:
            REFERENCE_GEOMETRY = (json.load(_rf) or {}).get("geometry", {})
        print(f"✅ Loaded reference geometry for {len(REFERENCE_GEOMETRY)} denominations")
    else:
        print("⚠️ reference_geometry.json not found — geometry position scoring will be neutral")
except Exception as _e:
    print(f"⚠️ Failed to load reference_geometry.json: {_e}")

# model class name -> canonical feature key (mirrors the security-feature mapping)
_GEOM_CLASS_MAP = {
    "concealed value": "concealed_value", "security thread": "security_thread",
    "serial number": "serial_number", "value": "value", "value watermark": "watermark",
    "watermark": "watermark", "see through mark": "see_through_mark",
    "optically variable ink": "optically_variable_ink", "ovd": "ovd",
    "1k enhanced value panel": "enhanced_value_panel", "500 enhanced value panel": "enhanced_value_panel",
}

def _norm_label(s):
    return str(s).lower().replace("-", " ").replace("_", " ").strip()

def get_bill_frame_box(denom_detections):
    """Banknote frame = highest-confidence denomination detection bbox (else None)."""
    valid = [d for d in (denom_detections or []) if d.get("bbox")]
    if not valid:
        return None
    return max(valid, key=lambda d: d.get("confidence", 0)).get("bbox")

def measure_feature_geometry(denomination, detection_details, bill_box):
    """Per-feature geometry vs the genuine reference. Returns
    {feature: {detected, confidence, position_score, bbox_norm}}. position_score is None when
    no reference exists for that feature (neutral)."""
    out = {}
    if not bill_box:
        return out
    try:
        fx1, fy1, fx2, fy2 = bill_box
    except Exception:
        return out
    fw, fh = max(fx2 - fx1, 1e-6), max(fy2 - fy1, 1e-6)
    ref = REFERENCE_GEOMETRY.get(str(denomination), {})
    dd = detection_details or {}
    dets = []
    for key in ("security_detections", "ovi_detections", "ovd_detections", "evp_detections"):
        dets.extend(dd.get(key, []) or [])
    for det in dets:
        try:
            feat = _GEOM_CLASS_MAP.get(_norm_label(det.get("label", "")))
            bbox = det.get("bbox")
            if not feat or not bbox:
                continue
            x1, y1, x2, y2 = bbox
            cx = max(0.0, min(1.0, ((x1 + x2) / 2 - fx1) / fw))
            cy = max(0.0, min(1.0, ((y1 + y2) / 2 - fy1) / fh))
            w = max(0.0, min(1.0, (x2 - x1) / fw))
            h = max(0.0, min(1.0, (y2 - y1) / fh))
            conf = float(det.get("confidence", 0))
            r = ref.get(feat)
            if r:
                sp = max(r.get("sigma_pos", 0.1), 1e-3)
                ss = max(r.get("sigma_size", 0.1), 1e-3)
                dcen = _math.hypot(cx - r.get("cx", cx), cy - r.get("cy", cy))
                dsz = _math.hypot(w - r.get("w", w), h - r.get("h", h))
                pscore = round(_math.exp(-((dcen / sp) ** 2 + (dsz / ss) ** 2)), 3)
            else:
                pscore = None
            prev = out.get(feat)
            if prev is None or conf > prev.get("confidence", 0):
                out[feat] = {"detected": True, "confidence": round(conf, 3),
                             "position_score": pscore,
                             "bbox_norm": [round(cx, 3), round(cy, 3), round(w, 3), round(h, 3)]}
        except Exception:
            continue
    return out

def _region_hue(img, box):
    """Median hue (OpenCV 0-179) of saturated pixels in a bbox region, or None."""
    try:
        x1, y1, x2, y2 = [int(v) for v in box]
        x1, y1 = max(0, x1), max(0, y1)
        crop = img[y1:y2, x1:x2]
        if crop.size == 0:
            return None
        hsv = cv2.cvtColor(crop, cv2.COLOR_BGR2HSV)
        h, s, v = hsv[..., 0], hsv[..., 1], hsv[..., 2]
        mask = (s > 40) & (v > 40) & (v < 250)   # ignore highlights / dark / unsaturated
        vals = h[mask] if int(mask.sum()) >= 20 else h.reshape(-1)
        return float(np.median(vals))
    except Exception:
        return None

def measure_color_shift(angle_frames):
    """Component 3: sample the OVI/OVD region hue across angle frames and report the colour
    shift. Genuine optically-variable ink/devices change colour with viewing angle; a flat
    print or reflection does not shift consistently."""
    try:
        hues = []
        ovi_model = model_loader.get_model('ovi')
        ovd_model = model_loader.get_model('ovd')
        for frame in angle_frames:
            dets = (simple_detection(ovi_model, frame, OVI_CLASSES, min_confidence=0.2)
                    + simple_detection(ovd_model, frame, OVD_CLASSES, min_confidence=0.2))
            dets = [d for d in dets if d.get('bbox')]
            if not dets:
                continue
            best = max(dets, key=lambda d: d.get('confidence', 0))
            hue = _region_hue(frame, best['bbox'])
            if hue is not None:
                hues.append(round(hue, 1))
        if len(hues) < 2:
            return {"delta": 0.0, "shift_detected": False, "hues": hues,
                    "note": "OVI/OVD must be visible in >=2 angles to measure colour shift"}
        def hue_dist(a, b):
            d = abs(a - b)
            return min(d, 180 - d)
        delta = max(hue_dist(a, b) for a in hues for b in hues)
        return {"delta": round(delta, 1), "shift_detected": delta >= 15.0, "hues": hues}
    except Exception as e:
        return {"delta": 0.0, "shift_detected": False, "error": str(e)}

def evaluate_counterfeit(denomination: str, features_result: Dict[str, Any],
                         geometry: Dict[str, Any] = None, overall_quality: float = None) -> Dict[str, Any]:
    """Evaluate authenticity from features + geometry + capture quality. Produces a calibrated
    0-100 authenticity_score. COUNTERFEIT only on a real forgery marker (false EVP)."""
    try:
        reasons = []
        is_genuine = True

        # Safely get features with defaults
        security_features = features_result.get("security_features", {})
        counterfeit_indicators = features_result.get("counterfeit_indicators", {})
        is_high_denom = features_result.get("is_high_denomination", False)
        coverage = features_result.get("coverage_percentage", 0) or 0
        detected = features_result.get("detected_features_count", 0)
        total = features_result.get("total_expected_features", 9 if is_high_denom else 6)

        # Can't even identify the note → don't pass judgment; ask for a clearer rescan.
        if not denomination or denomination == "UNKNOWN":
            return {
                "is_genuine": False, "status": "UNKNOWN", "confidence": "LOW",
                "reasons": ["Could not identify the denomination in this photo. Re-scan the full note in good lighting."],
                "coverage_percentage": coverage, "detected_features_count": detected,
                "total_expected_features": total,
                "denomination_type": "HIGH_DENOMINATION" if is_high_denom else "LOW_DENOMINATION",
                "has_false_evp": counterfeit_indicators.get('false_enhanced_value_panel', False),
            }

        # RELAXED RULE (v17.1): a single front-lit camera photo CANNOT capture the
        # watermark / see-through register (need transmitted light) or OVI / OVD
        # (need tilt). Their absence on one scan must NOT condemn a genuine note —
        # otherwise every real bill is flagged counterfeit. So we judge by how many
        # of the expected features were actually verified (coverage), and keep the
        # real forgery markers as hard fails. Use Multi-Scan to verify the
        # lighting/tilt-dependent features across angles.
        lighting_dependent = ['watermark', 'see_through_mark', 'optically_variable_ink', 'ovd']

        # Any positive FALSE marker (from the securitycf counterfeit model: false watermark,
        # false security thread, false see-through, false OVI/EVP, false bill, etc.) is a
        # strong forgery signal. This is the real counterfeit detection.
        # A false_X marker only condemns if the GENUINE X is NOT also detected — if both fire,
        # it's a model error on a genuine note, not a forgery. 'false_bill' never condemns
        # (noisy whole-note guess). Keeps real counterfeit detection without flagging genuine
        # bills on a single spurious false detection.
        GENUINE_OF = {
            'false_watermark': 'watermark', 'false_see_through_mark': 'see_through_mark',
            'false_shadow_thread': 'shadow_thread', 'false_security_thread': 'security_thread',
            'false_concealed_value': 'concealed_value', 'false_ovi': 'optically_variable_ink',
            'false_enhanced_value_panel': 'enhanced_value_panel',
        }
        false_markers = [k for k, v in (counterfeit_indicators or {}).items()
                         if v and k != 'false_bill'
                         and not security_features.get(GENUINE_OF.get(k, ''), False)]
        # CORROBORATION RULE (v17.17): the securitycf/EVP false_* markers are noisy and
        # false-fire on genuine notes — especially the lighting-dependent ones (watermark,
        # see-through, EVP) that a single front-lit photo cannot independently verify. So a
        # single marker NEVER condemns, and any number of markers is overridden when the note
        # independently shows real, hard-to-fake security features. We only call COUNTERFEIT
        # when the forgery signal is corroborated AND the genuine evidence is weak:
        #   (a) >=2 DISTINCT condemning markers, AND
        #   (b) fewer than 2 hard security features actually detected.
        # This matches the principle that we never assume counterfeit from one unverifiable
        # feature; a real fake shows multiple forgery markers and lacks genuine security features.
        HARD_FEATURES = {'watermark', 'see_through_mark', 'security_thread', 'concealed_value',
                         'shadow_thread', 'uv_thread', 'optically_variable_ink', 'ovd',
                         'enhanced_value_panel'}
        hard_genuine = sum(1 for f in HARD_FEATURES if security_features.get(f, False))
        corroborated_forgery = len(false_markers) >= 2 and hard_genuine < 2
        if corroborated_forgery:
            is_genuine = False
            pretty = ', '.join(sorted(m.replace('false_', '').replace('_', ' ') for m in false_markers))
            reasons.append(f"Multiple counterfeit markers ({pretty}) with no genuine security features verified — strong counterfeit indicator.")
        elif false_markers:
            # Markers fired but not corroborated (single marker, or the note shows real
            # security features) → do NOT condemn; just note it lowers confidence.
            is_genuine = True
            pretty = ', '.join(sorted(m.replace('false_', '').replace('_', ' ') for m in false_markers))
            reasons.append(f"Possible marker(s) ({pretty}) flagged but not corroborated — {hard_genuine} genuine security feature(s) verified, consistent with a genuine note. Use Multi-Scan to confirm.")
        elif (not is_high_denom) and security_features.get('enhanced_value_panel', False):
            is_genuine = False
            reasons.append("Enhanced value panel present on a low-denomination note (should not be there).")
        elif coverage >= GENUINE_COVERAGE_THRESHOLD:
            is_genuine = True
            reasons.append(f"{detected}/{total} security features verified ({coverage:.0f}% coverage) — consistent with a genuine note.")
            missing_light = [f for f in lighting_dependent if not security_features.get(f, False)]
            if missing_light:
                reasons.append("Watermark / see-through / OVI need backlight or tilt — use Multi-Scan to confirm them.")
        else:
            # No positive forgery marker found. A single front-lit photo can't capture
            # the lighting/tilt-dependent features, so low coverage must NOT condemn a
            # genuine note — it only lowers confidence. COUNTERFEIT is reserved for an
            # actual forgery signal (false EVP) handled above.
            is_genuine = True
            reasons.append(f"No counterfeit indicators found. Only {detected}/{total} security features were visible in this photo ({coverage:.0f}%) — watermark / see-through / OVI need backlight or tilt. Use Multi-Scan for full security verification.")

        # --- Calibrated authenticity score (Component 2) + quality gate (Component 4) ---
        forgery = not is_genuine  # only the forgery branches above set is_genuine False
        det_confs = [g.get("confidence", 0) for g in (geometry or {}).values()]
        pos_scores = [g["position_score"] for g in (geometry or {}).values() if g.get("position_score") is not None]
        coverage_norm = min(1.0, (coverage or 0) / 100.0)
        mean_conf = (sum(det_confs) / len(det_confs)) if det_confs else 0.0
        mean_pos = (sum(pos_scores) / len(pos_scores)) if pos_scores else 0.0
        # Reliable signals drive the score (coverage + detection confidence). Geometry
        # placement is a BONUS only — banknote photos vary in crop/angle so position is
        # noisy; it can lift the score for well-placed features but must never penalise a
        # genuine note. Capture quality scales the result.
        if det_confs:
            base = 0.60 * coverage_norm + 0.40 * mean_conf
        else:
            base = coverage_norm
        base = min(1.0, base + 0.15 * mean_pos)
        quality_factor = max(0.4, min(1.0, overall_quality / 70.0)) if overall_quality is not None else 1.0
        score = int(round(100 * base * quality_factor))

        quality_gated = False
        if forgery:
            status, confidence = "COUNTERFEIT", "HIGH"
            score = min(score, 15)
        elif overall_quality is not None and overall_quality < 30:
            # NEEDS_RESCAN is reserved for genuine capture problems, never for a clean
            # photo of an identified note with few visible features.
            status, confidence, quality_gated, is_genuine = "NEEDS_RESCAN", "LOW", True, False
            reasons.append("Image quality too low (blurry/dark/low-contrast) — re-scan in better lighting for a reliable result.")
        elif score >= 75:
            status, confidence = "GENUINE", "HIGH"
        elif score >= 50:
            status, confidence = "GENUINE", "MEDIUM"
        else:
            status, confidence = "LIKELY GENUINE", "LOW"
            reasons.append("Identified as genuine, but only part of the security features are visible in a single photo — use Multi-Scan to fully verify the tilt/backlit features.")

        return {
            "is_genuine": is_genuine,
            "status": status,
            "confidence": confidence,
            "authenticity_score": score,
            "reasons": reasons,
            "coverage_percentage": coverage,
            "detected_features_count": detected,
            "total_expected_features": total,
            "denomination_type": "HIGH_DENOMINATION" if is_high_denom else "LOW_DENOMINATION",
            "has_false_evp": counterfeit_indicators.get('false_enhanced_value_panel', False),
            "quality_gated": quality_gated,
            "feature_geometry": geometry or {},
        }
        
    except Exception as e:
        print(f"❌ CRITICAL ERROR in evaluate_counterfeit: {e}")
        traceback.print_exc()
        # Return safe default values
        return {
            "is_genuine": False,
            "status": "UNKNOWN",
            "confidence": "LOW",
            "reasons": ["Error in authenticity evaluation"],
            "coverage_percentage": 0,
            "detected_features_count": 0,
            "total_expected_features": 0,
            "denomination_type": "UNKNOWN",
            "has_false_evp": False
        }

def get_consensus_denomination(denominations: List[str]) -> str:
    """Determine the most likely denomination from multiple detections"""
    if not denominations:
        return "UNKNOWN"
    
    valid_denoms = [d for d in denominations if d != "UNKNOWN"]
    if not valid_denoms:
        return "UNKNOWN"
    
    counter = Counter(valid_denoms)
    most_common = counter.most_common(1)[0]
    return most_common[0]

def combine_features_across_images(all_features: List[Dict[str, bool]]) -> Dict[str, bool]:
    """Combine features detected across multiple images - feature is True if detected in ANY image"""
    if not all_features:
        return {}
    
    # Initialize with the FULL feature set set to False (union across frames).
    combined = {
        'value': False, 'serial_number': False, 'security_thread': False, 'concealed_value': False,
        'watermark': False, 'value_watermark': False, 'see_through_mark': False, 'uv_thread': False,
        'symbol_of_nature': False, 'shadow_thread': False,
        'optically_variable_ink': False, 'optically_variable_thread': False, 'ovd': False,
        'enhanced_value_panel': False,
    }
    
    # Update with detected features from each image
    for features in all_features:
        if features:  # Check if features is not None or empty
            for feature, detected in features.items():
                if detected and feature in combined:
                    combined[feature] = True

    return combined

def persistent_counterfeit_markers(marker_dicts: List[Dict[str, bool]]) -> Dict[str, bool]:
    """Deep-scan rule: a forgery marker only counts if it PERSISTS across frames.
    A one-off spurious false_* on a single frame must NOT condemn a genuine note —
    we can't assume counterfeit because one feature flickered. A marker must fire in
    >=2 frames AND in >=40% of observed frames to be treated as a real forgery signal.
    Returns a counterfeit_indicators-style dict ({marker: True}) of the persistent ones."""
    if not marker_dicts:
        return {}
    total = len(marker_dicts)
    counts: Dict[str, int] = {}
    for md in marker_dicts:
        if md:
            for k, v in md.items():
                if v:
                    counts[k] = counts.get(k, 0) + 1
    threshold = max(2, int(round(0.4 * total)))
    return {k: True for k, c in counts.items() if c >= threshold}

def extract_best_frame_from_video(video_path: str) -> np.ndarray:
    """Extract the best frame from video based on quality and feature detection"""
    try:
        cap = cv2.VideoCapture(video_path)
        if not cap.isOpened():
            raise HTTPException(status_code=400, detail="Cannot open video file")
        
        best_frame = None
        best_score = -1
        
        while True:
            ret, frame = cap.read()
            if not ret:
                break
                
            # Calculate frame quality score
            quality_metrics = analyze_frame_quality(frame)
            frame_score = quality_metrics["overall_quality"]
            
            if frame_score > best_score:
                best_score = frame_score
                best_frame = frame.copy()
                
        cap.release()
        
        if best_frame is None:
            raise HTTPException(status_code=400, detail="No valid frames found in video")
            
        return best_frame
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Video processing failed: {str(e)}")

def base64_to_image(base64_string: str) -> np.ndarray:
    """Convert base64 string to OpenCV image"""
    try:
        # Remove data URL prefix if present
        if ',' in base64_string:
            base64_string = base64_string.split(',')[1]
        
        # Decode base64
        image_data = base64.b64decode(base64_string)
        np_arr = np.frombuffer(image_data, np.uint8)
        img = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
        
        if img is None:
            raise ValueError("Failed to decode base64 image")
            
        return img
    except Exception as e:
        print(f"❌ Error converting base64 to image: {e}")
        raise

# ----------------------------
# WebSocket Heartbeat Function
# ----------------------------

async def send_heartbeat(websocket: WebSocket, interval: int = 30):
    """Send heartbeat to keep WebSocket connection alive"""
    try:
        while True:
            await asyncio.sleep(interval)
            try:
                await websocket.send_json({"type": "heartbeat", "timestamp": time.time()})
            except Exception:
                break
    except Exception:
        pass

# ----------------------------
# UPDATED REAL-TIME WEB SOCKET ENDPOINTS (AUTO-START + PARALLEL PROCESSING)
# ----------------------------

@app.websocket("/ws/standard-scan")
async def real_time_standard_scan(websocket: WebSocket):
    """Real-time standard scan with AUTO-START and PARALLEL processing"""
    client_id = str(uuid.uuid4())
    await manager.connect(websocket, client_id)
    print(f"🔌 Real-time Standard Scan WebSocket connected: {client_id}")
    
    # AUTO-START: Immediately set to scanning
    await websocket.send_json({
        "status": "scanning", 
        "message": "🔄 Automatic scanning started - send frames for real-time detection"
    })
    
    # Start heartbeat
    heartbeat_task = asyncio.create_task(send_heartbeat(websocket))
    
    try:
        accumulated_results = {
            "denominations": [],
            "features": [],
            "counterfeit_markers": [],
            "authenticity_results": []
        }
        
        frame_count = 0
        scan_start_time = time.time()
        user_id = "anonymous"
        best_frame = None
        best_frame_result = None
        is_completed = False
        
        while True:
            try:
                # Receive frame data from client
                data = await websocket.receive_text()
                
                # AUTO-COMPLETE: User sends complete command
                if data == "COMPLETE_SCAN" and not is_completed:
                    print("🏁 User requested scan completion...")
                    is_completed = True
                    processing_time = round(time.time() - scan_start_time, 2)
                    
                    # Send final result based on accumulated data
                    if accumulated_results["denominations"]:
                        final_denomination = get_consensus_denomination(accumulated_results["denominations"])
                        combined_features = combine_features_across_images(accumulated_results["features"])
                        final_high = is_high_denomination(final_denomination)
                        # Persistent forgery markers from the whole scan (deep-scan rule) — NOT a
                        # hardcoded empty dict, so a real fake actually gets caught at completion.
                        final_markers = persistent_counterfeit_markers(accumulated_results["counterfeit_markers"])
                        final_detected = sum(1 for v in combined_features.values() if v) if combined_features else 0
                        final_total = 9 if final_high else 6

                        # Create final authenticity evaluation from accumulated evidence
                        features_result = {
                            "security_features": combined_features,
                            "counterfeit_indicators": final_markers,
                            "is_high_denomination": final_high,
                            "total_expected_features": final_total,
                            "detected_features_count": final_detected,
                            "coverage_percentage": min(100.0, (final_detected / final_total) * 100) if final_total else 0
                        }

                        final_authenticity = evaluate_counterfeit(final_denomination, features_result)
                        
                        # Create annotated image from best frame
                        annotated_image_url = ""
                        storage_id = ""
                        scan_id = str(uuid.uuid4())
                        
                        if best_frame is not None:
                            try:
                                # Run detections to get annotations for the best frame
                                security_dets = simple_detection(model_loader.get_model('security'), best_frame, SECURITY_MODEL_CLASSES)
                                ovi_dets = simple_detection(model_loader.get_model('ovi'), best_frame, OVI_CLASSES)
                                ovd_dets = simple_detection(model_loader.get_model('ovd'), best_frame, OVD_CLASSES)
                                evp_dets = simple_detection(model_loader.get_model('evp'), best_frame, EVP_CLASSES)
                                counterfeit_dets = simple_detection(model_loader.get_model('counterfeit'), best_frame, COUNTERFEIT_MODEL_CLASSES)
                                
                                annotated_img = create_numbered_annotated_image(
                                    best_frame, security_dets, ovi_dets, ovd_dets, evp_dets, counterfeit_dets
                                )
                                
                                annotated_image_url = await store_annotated_image_for_realtime(
                                    annotated_img, "standard_scan", user_id, scan_id
                                )
                                
                            except Exception as img_error:
                                print(f"❌ Error creating annotated image: {img_error}")
                        
                        final_result = {
                            "scan_id": scan_id,
                            "status": "complete",
                            "message": "Scan completed successfully",
                            "authenticity": final_authenticity.get("status", "UNKNOWN"),
                            "is_genuine": final_authenticity.get("is_genuine", False),
                            "denomination": final_denomination,
                            "features_detected": [feature for feature, detected in combined_features.items() if detected],
                            "feature_count": len([feature for feature, detected in combined_features.items() if detected]),
                            "total_expected_features": features_result["total_expected_features"],
                            "detected_features_count": features_result["detected_features_count"],
                            "coverage_percentage": features_result["coverage_percentage"],
                            "confidence": final_authenticity.get("confidence", "LOW"),
                            "reasons": final_authenticity.get("reasons", []),
                            "frames_processed": frame_count,
                            "processing_time": processing_time,
                            "has_false_evp": final_authenticity.get("has_false_evp", False),
                            "is_high_denomination": features_result["is_high_denomination"],
                            "annotated_image_url": annotated_image_url,
                            "firebase_status": "stored" if FIREBASE_AVAILABLE else "dummy_mode",
                            "storage_id": storage_id
                        }
                        
                        # Store result in Firebase
                        try:
                            storage_id = await store_real_time_scan_result("standard_scan", final_result, user_id)
                            final_result["storage_id"] = storage_id
                        except Exception as storage_error:
                            print(f"❌ Firebase storage error: {storage_error}")
                            final_result["storage_id"] = "storage_failed"
                        
                    else:
                        final_result = {
                            "status": "complete",
                            "message": "Scan completed but no valid detections",
                            "authenticity": "UNKNOWN",
                            "is_genuine": False,
                            "denomination": "UNKNOWN",
                            "features_detected": [],
                            "feature_count": 0,
                            "total_expected_features": 6,
                            "detected_features_count": 0,
                            "confidence": "LOW",
                            "frames_processed": frame_count,
                            "processing_time": processing_time,
                            "firebase_status": "no_data"
                        }
                    
                    print(f"📤 Sending final result: {final_result.get('authenticity', 'UNKNOWN')}")
                    await websocket.send_json(final_result)
                    manager.update_scan_state(client_id, "completed")
                    continue
                
                # Process frame data (base64 image) - AUTO-STARTED already
                if data.startswith('data:image') and not is_completed:
                    try:
                        frame = base64_to_image(data)
                        frame_count += 1
                        print(f"📸 Processing frame {frame_count}...")
                        
                        # Quality analysis
                        quality_metrics = analyze_frame_quality(frame)
                        quality_check = is_frame_quality_acceptable(frame)
                        
                        # Process the frame with PARALLEL execution
                        result = await process_frame_parallel(frame)
                        
                        # Track best frame (highest confidence)
                        current_confidence = result.get("confidence_score", 0)
                        best_confidence = best_frame_result.get("confidence_score", 0) if best_frame_result else -1
                        
                        if best_frame is None or current_confidence > best_confidence:
                            best_frame = frame.copy()
                            best_frame_result = result
                            print(f"🔍 Updated best_frame with confidence: {current_confidence}")
                        
                        # Accumulate results
                        if result.get("denomination") != "UNKNOWN":
                            accumulated_results["denominations"].append(result["denomination"])
                        
                        feature_dict = {}
                        if result.get("security_features"):
                            feature_dict = result["security_features"].copy()
                        elif result.get("detected_features"):
                            feature_dict = {feature: True for feature in result["detected_features"]}
                        
                        accumulated_results["features"].append(feature_dict)
                        accumulated_results["counterfeit_markers"].append(result.get("counterfeit_indicators", {}))

                        if result.get("authenticity"):
                            accumulated_results["authenticity_results"].append(result["authenticity"])

                        # --- DEEP ACCUMULATION (v17.16) ---
                        # The longer / steadier / closer the scan, the more evidence we gather.
                        # Genuine features accumulate as a UNION across frames (a watermark seen
                        # in ANY frame counts), while forgery markers must PERSIST across frames
                        # before they condemn — one spurious false_* frame is washed out. So the
                        # live verdict reflects the BEST accumulated evidence, not a single frame.
                        acc_denom = get_consensus_denomination(accumulated_results["denominations"])
                        acc_features = combine_features_across_images(accumulated_results["features"])
                        acc_high = is_high_denomination(acc_denom)
                        acc_markers = persistent_counterfeit_markers(accumulated_results["counterfeit_markers"])
                        acc_detected = sorted([f for f, v in acc_features.items() if v])
                        acc_count = len(acc_detected)
                        # Stable denominator from the accumulated denomination (NOT the per-frame
                        # total, which bounces frame-to-frame and breaks monotonic coverage). The
                        # union count is non-decreasing, so coverage now only ever climbs.
                        acc_total = 9 if acc_high else 6
                        acc_coverage = round(min(100.0, (acc_count / acc_total) * 100), 1) if acc_total else 0
                        acc_features_result = {
                            "security_features": acc_features,
                            "counterfeit_indicators": acc_markers,
                            "is_high_denomination": acc_high,
                            "total_expected_features": acc_total,
                            "detected_features_count": acc_count,
                            "coverage_percentage": acc_coverage,
                        }
                        acc_quality = quality_metrics.get("overall_quality") if isinstance(quality_metrics, dict) else None
                        acc_auth = evaluate_counterfeit(acc_denom, acc_features_result, overall_quality=acc_quality)

                        # Send real-time results — ACCUMULATED across the whole scan so far
                        await websocket.send_json({
                            "status": "analyzing",
                            "frame_number": frame_count,
                            "message": f"Deep analysis - {acc_count}/{acc_total} features verified over {frame_count} frames",
                            "denomination": acc_denom,
                            "features_detected": acc_detected,
                            "feature_count": acc_count,
                            "total_expected_features": acc_total,
                            "coverage_percentage": acc_coverage,
                            "confidence_score": acc_auth.get("authenticity_score", acc_auth.get("confidence_score", 0)),
                            "authenticity": acc_auth.get("status", "UNKNOWN"),
                            "is_genuine": acc_auth.get("is_genuine", False),
                            "is_high_denomination": acc_high,
                            "has_false_evp": acc_markers.get("false_enhanced_value_panel", False),
                            "frame_features": result.get("detected_features", []),
                            "quality_metrics": quality_metrics,
                            "quality_feedback": quality_check["feedback"] if not quality_check["is_acceptable"] else [],
                            "processing_mode": "parallel",
                            "recommendation": "Hold steady and move closer to verify more features, then press Complete Scan"
                        })
                            
                    except Exception as frame_error:
                        print(f"❌ Frame processing error: {frame_error}")
                        await websocket.send_json({
                            "status": "analyzing",
                            "message": f"Processing frame {frame_count}...",
                            "frame_number": frame_count
                        })
                elif is_completed and data.startswith('data:image'):
                    # Ignore frames after completion
                    print("⚠️ Received frame after completion - ignoring")
                    continue
                else:
                    # Handle other messages
                    await websocket.send_json({
                        "status": "scanning" if not is_completed else "completed",
                        "message": "Send frames for real-time detection" if not is_completed else "Scan completed - send COMPLETE_SCAN to restart"
                    })
                
            except WebSocketDisconnect:
                print("🔌 WebSocket disconnected by client")
                break
            except Exception as e:
                print(f"❌ Error in WebSocket: {e}")
                traceback.print_exc()
                await websocket.send_json({
                    "error": f"Error: {str(e)}",
                    "status": "error"
                })
                
    except Exception as e:
        print(f"❌ Standard scan WebSocket error: {e}")
        traceback.print_exc()
    finally:
        heartbeat_task.cancel()
        manager.disconnect(client_id)

@app.websocket("/ws/real-multi-scan")
async def real_time_multi_scan(websocket: WebSocket):
    """Real-time multi-angle scan with AUTO-START"""
    client_id = str(uuid.uuid4())
    await manager.connect(websocket, client_id)
    print(f"🔌 Real-time Multi-Scan WebSocket connected: {client_id}")
    
    # AUTO-START: Immediately set to scanning
    await websocket.send_json({
        "status": "scanning", 
        "message": "🔄 Automatic multi-angle scan started - capture angles 1, 2, 3",
        "current_angle": 1,
        "total_angles": 3
    })
    
    # Start heartbeat
    heartbeat_task = asyncio.create_task(send_heartbeat(websocket))
    
    try:
        scan_data = {
            "angles_captured": 0,
            "current_angle": 1,
            "total_angles": 3,
            "angle_results": [],
            "all_features": [],
            "denominations": [],
            "counterfeit_indicators": [],
            "angle_frames": [],
            "is_completed": False
        }
        
        scan_start_time = time.time()
        user_id = "anonymous"
        
        while True:
            try:
                data = await websocket.receive_text()
                
                # AUTO-COMPLETE: User sends complete command
                if data == "COMPLETE_MULTI_SCAN" and not scan_data["is_completed"]:
                    print("🏁 User requested multi-scan completion...")
                    scan_data["is_completed"] = True
                    processing_time = round(time.time() - scan_start_time, 2)
                    
                    if scan_data["angles_captured"] > 0:
                        await process_multi_scan_completion(websocket, scan_data, user_id, processing_time)
                    else:
                        await websocket.send_json({
                            "status": "complete",
                            "message": "Multi-scan completed but no angles captured",
                            "authenticity": "UNKNOWN",
                            "is_genuine": False,
                            "denomination": "UNKNOWN",
                            "angles_processed": 0,
                            "total_features": 0,
                            "features_detected": [],
                            "confidence": "LOW",
                            "processing_time": processing_time,
                            "firebase_status": "no_data"
                        })
                    continue
                
                # Process angle captures - AUTO-STARTED already
                if data.startswith("ANGLE_") and not scan_data["is_completed"]:
                    try:
                        # Parse angle data
                        angle_parts = data.split(":", 1)
                        if len(angle_parts) != 2:
                            await websocket.send_json({
                                "status": "error", 
                                "message": "Invalid angle data format"
                            })
                            continue
                            
                        angle_info = angle_parts[0]  # "ANGLE_1", "ANGLE_2", etc.
                        base64_data = angle_parts[1]
                        
                        # Extract angle number
                        try:
                            angle_number = int(angle_info.split("_")[1])
                        except:
                            await websocket.send_json({
                                "status": "error",
                                "message": "Invalid angle number"
                            })
                            continue
                        
                        # Validate angle sequence
                        expected_angle = scan_data["angles_captured"] + 1
                        if angle_number != expected_angle:
                            await websocket.send_json({
                                "status": "angle_sequence_error",
                                "message": f"Expected angle {expected_angle}, but received angle {angle_number}",
                                "expected_angle": expected_angle
                            })
                            continue
                        
                        # Process the angle
                        await process_single_angle(websocket, scan_data, angle_number, base64_data, user_id)
                        
                    except Exception as angle_error:
                        print(f"❌ Angle capture error: {angle_error}")
                        traceback.print_exc()
                        await websocket.send_json({
                            "status": "angle_error",
                            "message": f"Error processing angle {scan_data['angles_captured']}",
                            "angle_number": scan_data["angles_captured"]
                        })
                    continue
                
                else:
                    # Handle other messages
                    if not scan_data["is_completed"]:
                        await websocket.send_json({
                            "status": "ready_for_angle",
                            "message": f"Ready for angle {scan_data['angles_captured'] + 1}",
                            "current_angle": scan_data["current_angle"],
                            "angles_captured": scan_data["angles_captured"],
                            "total_angles": scan_data["total_angles"]
                        })
                    else:
                        await websocket.send_json({
                            "status": "completed",
                            "message": "Multi-scan completed - send COMPLETE_MULTI_SCAN to restart"
                        })
                
            except WebSocketDisconnect:
                print("🔌 Multi-scan WebSocket disconnected by client")
                break
            except Exception as e:
                print(f"❌ Error in multi-scan WebSocket: {e}")
                traceback.print_exc()
                await websocket.send_json({
                    "error": f"Error: {str(e)}",
                    "status": "error"
                })
                
    except Exception as e:
        print(f"❌ Multi-scan WebSocket error: {e}")
        traceback.print_exc()
    finally:
        heartbeat_task.cancel()
        manager.disconnect(client_id)

@app.websocket("/ws/real-video-scan")
async def real_time_video_scan(websocket: WebSocket):
    """Real-time video analysis with AUTO-START"""
    client_id = str(uuid.uuid4())
    await manager.connect(websocket, client_id)
    print(f"🔌 Real-time Video Scan WebSocket connected: {client_id}")
    
    # AUTO-START: Immediately set to recording
    await websocket.send_json({
        "status": "recording", 
        "message": "🔄 Automatic video analysis started - send video frames",
        "is_recording": True
    })
    
    # Start heartbeat
    heartbeat_task = asyncio.create_task(send_heartbeat(websocket))
    
    try:
        scan_state = {
            "is_recording": True,  # AUTO-START: Immediately recording
            "frames_processed": 0,
            "best_confidence": 0,
            "best_result": None,
            "best_frame": None,
            "all_results": [],
            "frame_buffer": [],
            "last_frame_time": 0,
            "is_completed": False
        }
        
        scan_start_time = time.time()
        user_id = "anonymous"
        
        while True:
            try:
                data = await websocket.receive_text()
                
                # AUTO-COMPLETE: User sends complete command
                if data == "STOP_RECORDING" and not scan_state["is_completed"]:
                    print("🏁 User requested video scan completion...")
                    scan_state["is_recording"] = False
                    scan_state["is_completed"] = True
                    processing_time = round(time.time() - scan_start_time, 2)
                    
                    await process_video_scan_completion(websocket, scan_state, user_id, processing_time)
                    continue
                
                # Process frames - AUTO-STARTED already
                if scan_state["is_recording"] and data.startswith("data:image") and not scan_state["is_completed"]:
                    try:
                        current_time = time.time()
                        # Control frame rate (max 2 frames per second)
                        if current_time - scan_state["last_frame_time"] < 0.5:
                            continue
                        
                        scan_state["last_frame_time"] = current_time
                        
                        # Limit frame buffer to prevent memory issues
                        if len(scan_state["frame_buffer"]) > 50:
                            scan_state["frame_buffer"].pop(0)
                        
                        frame = base64_to_image(data)
                        scan_state["frames_processed"] += 1
                        
                        # Quality analysis
                        quality_metrics = analyze_frame_quality(frame)
                        quality_check = is_frame_quality_acceptable(frame)
                        
                        # Process only every 2nd frame to reduce load
                        if scan_state["frames_processed"] % 2 == 0:
                            result = await process_frame_parallel(frame)
                            scan_state["all_results"].append(result)
                            scan_state["frame_buffer"].append((frame, result))
                            
                            if result.get("confidence_score", 0) > scan_state["best_confidence"]:
                                scan_state["best_confidence"] = result.get("confidence_score", 0)
                                scan_state["best_result"] = result
                                scan_state["best_frame"] = frame
                            
                            await websocket.send_json({
                                "status": "recording",
                                "is_recording": True,
                                "frames_processed": scan_state["frames_processed"],
                                "current_confidence": result.get("confidence_score", 0),
                                "best_confidence": scan_state["best_confidence"],
                                "features_detected": result.get("detected_features", []),
                                "denomination": result.get("denomination", "UNKNOWN"),
                                "authenticity": result.get("authenticity", {}).get("status", "UNKNOWN"),
                                "feature_count": result.get("feature_count", 0),
                                "quality_metrics": quality_metrics,
                                "quality_feedback": quality_check["feedback"] if not quality_check["is_acceptable"] else [],
                                "processing_mode": "parallel",
                                "message": f"Analyzing frame {scan_state['frames_processed']} - {result.get('confidence_score', 0)}% confidence"
                            })
                        else:
                            # Send progress for skipped frames
                            await websocket.send_json({
                                "status": "recording",
                                "is_recording": True,
                                "frames_processed": scan_state["frames_processed"],
                                "quality_metrics": quality_metrics,
                                "quality_feedback": quality_check["feedback"] if not quality_check["is_acceptable"] else [],
                                "message": f"Processing frame {scan_state['frames_processed']}..."
                            })
                            
                    except Exception as frame_error:
                        print(f"❌ Frame processing error: {frame_error}")
                        await websocket.send_json({
                            "status": "recording",
                            "is_recording": True,
                            "frames_processed": scan_state["frames_processed"],
                            "message": f"Processing frame {scan_state['frames_processed']}..."
                        })
                elif not scan_state["is_recording"] and data.startswith("data:image"):
                    # Ignore frames when not recording
                    print("⚠️ Received frame but not recording - ignoring")
                    continue
                else:
                    # Handle other messages
                    if scan_state["is_recording"]:
                        await websocket.send_json({
                            "status": "recording",
                            "message": "Recording in progress - send STOP_RECORDING to complete",
                            "is_recording": True
                        })
                    else:
                        await websocket.send_json({
                            "status": "completed", 
                            "message": "Video scan completed - send STOP_RECORDING to restart"
                        })
                
            except WebSocketDisconnect:
                print("🔌 Video scan WebSocket disconnected by client")
                break
            except Exception as e:
                print(f"❌ Error in video scan WebSocket: {e}")
                await websocket.send_json({
                    "error": f"Error: {str(e)}",
                    "status": "error"
                })
                
    except Exception as e:
        print(f"❌ Video scan WebSocket error: {e}")
    finally:
        heartbeat_task.cancel()
        manager.disconnect(client_id)

async def process_single_angle(websocket: WebSocket, scan_data: Dict, angle_number: int, base64_data: str, user_id: str):
    """Process a single angle capture"""
    try:
        # Convert base64 to image
        if not base64_data.startswith('data:image'):
            base64_data = 'data:image/jpeg;base64,' + base64_data
            
        frame = base64_to_image(base64_data)
        scan_data["angle_frames"].append(frame)
        
        # Quality analysis
        quality_metrics = analyze_frame_quality(frame)
        quality_check = is_frame_quality_acceptable(frame)
        
        # Process the frame with PARALLEL execution
        result = await process_frame_parallel(frame)
        
        # Update scan data
        scan_data["angles_captured"] += 1
        scan_data["current_angle"] = scan_data["angles_captured"] + 1
        
        if result.get("denomination") != "UNKNOWN":
            scan_data["denominations"].append(result["denomination"])
        
        scan_data["all_features"].append(result.get("security_features", {}))
        scan_data["counterfeit_indicators"].append(result.get("counterfeit_indicators", {}))
        
        angle_result = {
            "angle_number": scan_data["angles_captured"],
            "denomination": result.get("denomination", "UNKNOWN"),
            "features_detected": result.get("detected_features", []),
            "feature_count": result.get("feature_count", 0),
            "coverage_percentage": result.get("coverage_percentage", 0),
            "authenticity": result.get("authenticity", {}).get("status", "UNKNOWN"),
            "is_genuine": result.get("authenticity", {}).get("is_genuine", False),
            "confidence": result.get("authenticity", {}).get("confidence", "LOW"),
            "quality_metrics": quality_metrics
        }
        scan_data["angle_results"].append(angle_result)
        
        # Calculate all detected features so far
        all_detected_features = []
        for features_dict in scan_data["all_features"]:
            for feature, detected in features_dict.items():
                if detected and feature not in all_detected_features:
                    all_detected_features.append(feature)
        
        await websocket.send_json({
            "status": "angle_captured",
            "angle_number": scan_data["angles_captured"],
            "total_angles": scan_data["total_angles"],
            "features_this_angle": result.get("detected_features", []),
            "all_features": all_detected_features,
            "denomination": result.get("denomination", "UNKNOWN"),
            "feature_count_this_angle": result.get("feature_count", 0),
            "total_features_so_far": len(all_detected_features),
            "message": f"✅ Angle {scan_data['angles_captured']} captured - {result.get('feature_count', 0)} features found",
            "progress": (scan_data["angles_captured"] / scan_data["total_angles"]) * 100,
            "coverage_percentage": result.get("coverage_percentage", 0),
            "quality_metrics": quality_metrics,
            "quality_feedback": quality_check["feedback"] if not quality_check["is_acceptable"] else [],
            "processing_mode": "parallel"
        })
        
        manager.update_scan_state(websocket, "scanning", {"angles_captured": scan_data["angles_captured"]})
        
    except Exception as e:
        print(f"❌ Error processing angle: {e}")
        raise

async def process_multi_scan_completion(websocket: WebSocket, scan_data: Dict, user_id: str, processing_time: float):
    """Process multi-scan completion and send final results"""
    try:
        if scan_data["denominations"]:
            final_denomination = get_consensus_denomination(scan_data["denominations"])
            combined_features = combine_features_across_images(scan_data["all_features"])
            
            combined_counterfeit_indicators = {
                'false_enhanced_value_panel': any(
                    indicator.get('false_enhanced_value_panel', False) 
                    for indicator in scan_data.get("counterfeit_indicators", [])
                )
            }
            
            features_result = {
                "security_features": combined_features,
                "counterfeit_indicators": combined_counterfeit_indicators,
                "is_high_denomination": is_high_denomination(final_denomination),
                "total_expected_features": 9 if is_high_denomination(final_denomination) else 6,
                "detected_features_count": sum(combined_features.values()) if combined_features else 0,
                "coverage_percentage": (sum(combined_features.values()) / (9 if is_high_denomination(final_denomination) else 6)) * 100 if combined_features else 0
            }
            
            final_authenticity = evaluate_counterfeit(final_denomination, features_result)
            
            angle_images = []
            scan_id = str(uuid.uuid4())
            storage_id = ""
            
            # Process and store annotated images for each angle
            for i, frame in enumerate(scan_data["angle_frames"]):
                if i < len(scan_data["angle_frames"]):
                    try:
                        security_dets = simple_detection(model_loader.get_model('security'), frame, SECURITY_MODEL_CLASSES)
                        ovi_dets = simple_detection(model_loader.get_model('ovi'), frame, OVI_CLASSES)
                        ovd_dets = simple_detection(model_loader.get_model('ovd'), frame, OVD_CLASSES)
                        evp_dets = simple_detection(model_loader.get_model('evp'), frame, EVP_CLASSES)
                        counterfeit_dets = simple_detection(model_loader.get_model('counterfeit'), frame, COUNTERFEIT_MODEL_CLASSES)
                        
                        annotated_img = create_numbered_annotated_image(
                            frame, security_dets, ovi_dets, ovd_dets, evp_dets, counterfeit_dets
                        )
                        
                        image_url = await store_annotated_image_for_realtime(
                            annotated_img, "multi_scan", user_id, f"{scan_id}_angle_{i+1}"
                        )
                        angle_images.append({
                            "angle_number": i + 1,
                            "image_url": image_url
                        })
                    except Exception as angle_img_error:
                        print(f"❌ Error processing angle image {i+1}: {angle_img_error}")
                        angle_images.append({
                            "angle_number": i + 1,
                            "image_url": "error"
                        })
            
            detected_features = [feature for feature, detected in combined_features.items() if detected]
            final_result = {
                "scan_id": scan_id,
                "status": "complete",
                "message": "Multi-scan completed successfully",
                "authenticity": final_authenticity.get("status", "UNKNOWN"),
                "is_genuine": final_authenticity.get("is_genuine", False),
                "denomination": final_denomination,
                "angles_processed": scan_data["angles_captured"],
                "total_features": len(detected_features),
                "features_detected": detected_features,
                "coverage_percentage": features_result["coverage_percentage"],
                "confidence": final_authenticity.get("confidence", "LOW"),
                "reasons": final_authenticity.get("reasons", []),
                "angle_results": scan_data["angle_results"],
                "processing_time": processing_time,
                "has_false_evp": final_authenticity.get("has_false_evp", False),
                "is_high_denomination": features_result["is_high_denomination"],
                "total_expected_features": features_result["total_expected_features"],
                "detected_features_count": features_result["detected_features_count"],
                "angle_images": angle_images,
                "annotated_image_url": angle_images[0]["image_url"] if angle_images else "",
                "firebase_status": "stored" if FIREBASE_AVAILABLE else "dummy_mode",
                "storage_id": storage_id,
                "processing_mode": "parallel"
            }
            
            try:
                storage_id = await store_real_time_scan_result("multi_scan", final_result, user_id)
                final_result["storage_id"] = storage_id
            except Exception as storage_error:
                print(f"❌ Firebase storage error: {storage_error}")
                final_result["storage_id"] = "storage_failed"
            
        else:
            final_result = {
                "status": "complete",
                "message": "Multi-scan completed but no valid detections",
                "authenticity": "UNKNOWN",
                "is_genuine": False,
                "denomination": "UNKNOWN",
                "angles_processed": scan_data["angles_captured"],
                "total_features": 0,
                "features_detected": [],
                "total_expected_features": 6,
                "detected_features_count": 0,
                "confidence": "LOW",
                "processing_time": processing_time,
                "firebase_status": "no_data"
            }
        
        print(f"📤 Sending multi-scan final result: {final_result.get('authenticity', 'UNKNOWN')}")
        await websocket.send_json(final_result)
        manager.update_scan_state(websocket, "completed")
        
    except Exception as e:
        print(f"❌ Error in multi-scan completion: {e}")
        await websocket.send_json({
            "status": "error",
            "message": f"Error completing multi-scan: {str(e)}"
        })

async def process_video_scan_completion(websocket: WebSocket, scan_state: Dict, user_id: str, processing_time: float):
    """Process video scan completion and send final results"""
    try:
        annotated_image_url = ""
        storage_id = ""
        scan_id = str(uuid.uuid4())
        
        if scan_state["best_result"] and scan_state["best_frame"] is not None:
            try:
                security_dets = simple_detection(model_loader.get_model('security'), scan_state["best_frame"], SECURITY_MODEL_CLASSES)
                ovi_dets = simple_detection(model_loader.get_model('ovi'), scan_state["best_frame"], OVI_CLASSES)
                ovd_dets = simple_detection(model_loader.get_model('ovd'), scan_state["best_frame"], OVD_CLASSES)
                evp_dets = simple_detection(model_loader.get_model('evp'), scan_state["best_frame"], EVP_CLASSES)
                counterfeit_dets = simple_detection(model_loader.get_model('counterfeit'), scan_state["best_frame"], COUNTERFEIT_MODEL_CLASSES)
                
                annotated_img = create_numbered_annotated_image(
                    scan_state["best_frame"], security_dets, ovi_dets, ovd_dets, evp_dets, counterfeit_dets
                )
                
                annotated_image_url = await store_annotated_image_for_realtime(
                    annotated_img, "video_scan", user_id, scan_id
                )
            except Exception as img_error:
                print(f"❌ Error creating annotated image: {img_error}")
            
            final_result = scan_state["best_result"]
            final_result.update({
                "scan_id": scan_id,
                "status": "analysis_complete",
                "message": "Video analysis completed",
                "frames_processed": scan_state["frames_processed"],
                "best_confidence": scan_state["best_confidence"],
                "processing_time": processing_time,
                "annotated_image_url": annotated_image_url,
                "firebase_status": "stored" if FIREBASE_AVAILABLE else "dummy_mode",
                "storage_id": storage_id,
                "processing_mode": "parallel"
            })
            
            try:
                storage_id = await store_real_time_scan_result("video_scan", final_result, user_id)
                final_result["storage_id"] = storage_id
            except Exception as storage_error:
                print(f"❌ Firebase storage error: {storage_error}")
                final_result["storage_id"] = "storage_failed"
            
        else:
            final_result = {
                "status": "analysis_complete",
                "message": "Video analysis completed but no valid detections",
                "authenticity": "UNKNOWN",
                "denomination": "UNKNOWN",
                "frames_processed": scan_state["frames_processed"],
                "best_confidence": 0,
                "features_detected": [],
                "feature_count": 0,
                "total_expected_features": 6,
                "detected_features_count": 0,
                "confidence": "LOW",
                "processing_time": processing_time,
                "firebase_status": "no_data"
            }
        
        print(f"📤 Sending video scan final result: {final_result.get('authenticity', 'UNKNOWN')}")
        await websocket.send_json(final_result)
        manager.update_scan_state(websocket, "completed")
        
    except Exception as e:
        print(f"❌ Error in video scan completion: {e}")
        await websocket.send_json({
            "status": "error",
            "message": f"Error completing video scan: {str(e)}"
        })

# ----------------------------
# HTTP ENDPOINTS (WITH PARALLEL PROCESSING)
# ----------------------------

@app.post("/api/standard-scan")
async def standard_scan(file: UploadFile = File(...), user_id: str = "anonymous"):
    """Standard scan endpoint with PARALLEL processing"""
    start_time = time.time()
    
    try:
        scan_id = str(uuid.uuid4())
        print("=" * 60)
        print("🔍 PROCESSING STANDARD SCAN (PARALLEL)")
        print("=" * 60)

        # Read image with proper error handling
        img = await read_image(file)
        
        if img is None:
            raise HTTPException(status_code=400, detail="Failed to process image")
        
        print(f"✅ Image loaded successfully: {img.shape}")

        # Quality analysis
        quality_metrics = analyze_frame_quality(img)
        quality_check = is_frame_quality_acceptable(img)

        # Run detections with PARALLEL execution
        try:
            print("=== STARTING PARALLEL DETECTIONS ===")
            
            # Use parallel processing for the frame
            result = await process_frame_parallel(img)
            
            # Create numbered annotated image
            security_dets = simple_detection(model_loader.get_model('security'), img, SECURITY_MODEL_CLASSES)
            ovi_dets = simple_detection(model_loader.get_model('ovi'), img, OVI_CLASSES)
            ovd_dets = simple_detection(model_loader.get_model('ovd'), img, OVD_CLASSES)
            evp_dets = simple_detection(model_loader.get_model('evp'), img, EVP_CLASSES)
            counterfeit_dets = simple_detection(model_loader.get_model('counterfeit'), img, COUNTERFEIT_MODEL_CLASSES)
            
            annotated_img = create_numbered_annotated_image(img, security_dets, ovi_dets, ovd_dets, evp_dets, counterfeit_dets)
            
        except Exception as detection_error:
            print(f"❌ Detection error: {detection_error}")
            import traceback
            traceback.print_exc()
            # Continue with safe defaults
            result = {
                "denomination": "UNKNOWN",
                "authenticity": {
                    "is_genuine": False,
                    "status": "UNKNOWN",
                    "confidence": "LOW",
                    "reasons": ["Error in detection processing"],
                    "coverage_percentage": 0,
                    "detected_features_count": 0,
                    "total_expected_features": 6,
                    "denomination_type": "UNKNOWN",
                    "has_false_evp": False
                },
                "detected_features": [],
                "feature_count": 0,
                "total_expected_features": 6,
                "coverage_percentage": 0,
                "confidence_score": 0,
                "is_high_denomination": False,
                "has_false_evp": False,
                "security_features": {},
                "counterfeit_indicators": {"false_enhanced_value_panel": False}
            }
            annotated_img = img
        
        processing_time = round(time.time() - start_time, 2)
        
        # Store annotated image
        annotated_image_url = ""
        if FIREBASE_AVAILABLE:
            try:
                print("📤 Storing annotated image...")
                annotated_image_url = await store_annotated_image_for_realtime(annotated_img, "standard_scan", user_id, scan_id)
                print(f"✅ Image stored: {annotated_image_url}")
            except Exception as storage_error:
                print(f"❌ Storage error: {storage_error}")
                annotated_image_url = "image_storage_error"
        else:
            print("❌ Firebase not available for image storage")
            annotated_image_url = "firebase_unavailable"
        
        # Prepare result
        final_result = {
            "scan_id": scan_id,
            "timestamp": datetime.now().isoformat(),
            "analysis_type": "standard_scan",
            "denomination": result.get("denomination", "UNKNOWN"),
            "authenticity": result.get("authenticity", {}),
            "security_features": result.get("security_features", {}),
            "counterfeit_indicators": result.get("counterfeit_indicators", {}),
            "is_high_denomination": result.get("is_high_denomination", False),
            "feature_summary": f"{result.get('feature_count', 0)}/{result.get('total_expected_features', 6)}",
            "coverage_percentage": result.get("coverage_percentage", 0),
            "detected_features_count": result.get("feature_count", 0),
            "total_expected_features": result.get("total_expected_features", 6),
            "number_mapping": NUMBER_TO_FEATURE_MAPPING,
            "model_info": "Multi-Model Ensemble (6 models) - PARALLEL",
            "logic_version": "17.17",
            "processing_time": processing_time,
            "annotated_image_url": annotated_image_url,
            "firebase_status": "stored" if FIREBASE_AVAILABLE else "dummy_mode",
            "status": "success",
            "quality_metrics": quality_metrics,
            "quality_feedback": quality_check["feedback"] if not quality_check["is_acceptable"] else [],
            "processing_mode": "parallel"
        }

        print("=" * 60)
        print("✅ STANDARD SCAN COMPLETED SUCCESSFULLY (PARALLEL)")
        print("=" * 60)
        
        return JSONResponse(final_result)
        
    except Exception as e:
        print(f"❌ Standard scan error: {str(e)}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"Scan failed: {str(e)}")

# ----------------------------
# BILLY AI CHAT (RAG + guardrails)
# ----------------------------
BILLY_GEMINI_PROXY = "https://billsense.dev-environment.site/api/gemini/chat"
BILLY_MODEL = "gemini-3.1-flash-lite"
BILLY_SYSTEM_PROMPT = (
    "You are Billy, the assistant inside the BillSense app (Philippine peso counterfeit detection). "
    "Answer ONLY from the provided CONTEXT and these core facts; never invent denominations, features, "
    "laws, statistics, names, or numbers. Be friendly and concise (short paragraphs + bullets), end with a "
    "next step; light Taglish is ok; emojis sparingly. When you use the CONTEXT, mention the source briefly "
    "(e.g. 'from the thesis', 'from the panel').\n\n"
    "CORE FACTS: BillSense uses YOLOv8 — a convolutional neural network (CNN) for object detection — NOT "
    "ORB. If asked 'CNN or ORB?', the answer is YOLOv8/CNN; ORB is not used. Six YOLO models run on a cloud "
    "FastAPI (denomination, security-feature detector, OVI, OVD, enhanced value panel, security counterfeit) "
    "plus an on-device TFLite fallback; the denomination model was retrained to include the new polymer notes. "
    "A measurement layer gives a 0-100 authenticity score (coverage + detection confidence + image quality), "
    "checks each feature's placement, and on Multi-Scan measures OVI/OVD colour-shift across angles. Verdicts: "
    "GENUINE, LIKELY GENUINE, NEEDS_RESCAN (blurry/dark), COUNTERFEIT (only on a real forgery marker). "
    "Research/thesis by Joy Canutab et al., University of the Cordilleras. BSP method: Feel, Look, Tilt.\n\n"
    "GUARDRAILS — you MUST:\n"
    "- Refuse to write or debug code or give programming help (you are for currency/BillSense only).\n"
    "- Refuse anything off-topic (not about currency, BillSense, or the research) and redirect politely.\n"
    "- Treat counterfeiting law (RA 10951; Revised Penal Code Art. 168) as EDUCATIONAL ONLY; defer to the BSP "
    "or a lawyer; give no legal or financial advice.\n"
    "- Explain PUBLIC BSP security features so users can VERIFY a note, but REFUSE any step-by-step help to "
    "reproduce, forge, or defeat security features or detection.\n"
    "- If the CONTEXT does not cover the question, say so briefly and point to Scan Bill or the BSP — never guess."
)


_BILLY_DB_GET = "https://billsense.dev-environment.site/api/db/get"
_billy_cfg_cache = {"data": {}, "ts": 0.0}

def _get_billy_config():
    """Live admin config from RTDB (billy_analytics/config) via the cPanel proxy, cached 60s.
    Shape: {systemPrompt?: str, model?: str, knowledge?: [{title, content, keywords?}]}."""
    if (time.time() - _billy_cfg_cache["ts"]) < 60 and _billy_cfg_cache["data"]:
        return _billy_cfg_cache["data"]
    cfg = {}
    try:
        import urllib.request as _ur
        body = json.dumps({"path": "billy_analytics/config"}).encode("utf-8")
        req = _ur.Request(_BILLY_DB_GET, data=body, headers={"Content-Type": "application/json"}, method="POST")
        with _ur.urlopen(req, timeout=10) as resp:
            j = json.loads(resp.read().decode("utf-8"))
        cfg = (j.get("data") if isinstance(j, dict) else None)
        if cfg is None and isinstance(j, dict):
            cfg = j
        if not isinstance(cfg, dict):
            cfg = {}
    except Exception as e:
        print("Billy config fetch failed: %s" % e)
    _billy_cfg_cache["data"] = cfg
    _billy_cfg_cache["ts"] = time.time()
    return cfg


@app.get("/api/billy/health")
async def billy_health():
    """Infrastructure summary for the admin Billy page (documented + live status)."""
    info = {
        "endpoint": "/api/billy/chat",
        "model_default": BILLY_MODEL,
        "embedding_model": "sentence-transformers all-MiniLM-L6-v2",
        "vector_db": "FAISS (IndexFlatIP, cosine)",
        "documents": ["Thesis (Canutab et al.)", "Currency-Detection documentation", "Panel comments"],
        "guardrails": ["No code generation", "No off-topic", "Educational-only law",
                       "No counterfeiting playbook", "Never invent facts"],
        "chunks": 0, "faiss_ready": False, "logic_version": "17.17",
    }
    try:
        from billy_rag import billy_rag
        info["chunks"] = len(billy_rag.chunks)
        info["faiss_ready"] = bool(getattr(billy_rag, "_ready", False))
    except Exception:
        pass
    cfg = _get_billy_config()
    info["model_active"] = cfg.get("model") or BILLY_MODEL
    info["prompt_overridden"] = bool(cfg.get("systemPrompt"))
    info["knowledge_notes"] = len(cfg.get("knowledge") or []) if isinstance(cfg.get("knowledge"), list) else 0
    return JSONResponse(info)


@app.post("/api/billy/chat")
async def billy_chat(request: Request):
    """RAG-grounded Billy chat: FAISS retrieval over BillSense docs + Gemini 3.1, with guardrails."""
    try:
        body = await request.json()
    except Exception:
        body = {}
    message = str(body.get("message") or "").strip()
    history = body.get("history") if isinstance(body.get("history"), list) else []
    scan_context = body.get("scanContext")
    if not message:
        return JSONResponse({"answer": "Hi! I'm Billy. Ask me about checking a bill, using the app, how "
                                       "BillSense works, or the research behind it. 🔍", "sources": []})
    hits = []
    try:
        from billy_rag import billy_rag
        hits = billy_rag.retrieve(message, k=4)
    except Exception as e:
        print("Billy retrieve error: %s" % e)
    context = "\n".join("[%s] %s" % (h["source"], h["text"]) for h in hits)
    sources = []
    for h in hits:
        if h["source"] not in sources:
            sources.append(h["source"])
    # Live admin config (prompt override / model / extra knowledge notes from the dashboard)
    cfg = _get_billy_config()
    notes = cfg.get("knowledge") if isinstance(cfg.get("knowledge"), list) else []
    note_lines = []
    ql = message.lower()
    for n in notes:
        if not isinstance(n, dict) or not n.get("content"):
            continue
        kw = str(n.get("keywords", "")).strip()
        match = (not kw) or any(k.strip().lower() in ql for k in kw.split(",") if k.strip())
        if match:
            note_lines.append("[admin note: %s] %s" % (n.get("title", ""), n.get("content", "")))
        if len(note_lines) >= 5:
            break

    parts = []
    if note_lines:
        parts.append("ADMIN KNOWLEDGE NOTES (authoritative — prefer these):\n" + "\n".join(note_lines))
    if context:
        parts.append("CONTEXT (retrieved from BillSense documents — use only what is relevant):\n" + context)
    if scan_context:
        parts.append("USER'S LAST SCAN RESULT (explain plainly if asked):\n" + str(scan_context)[:1500])
    parts.append("USER QUESTION: " + message)
    user_message = "\n\n".join(parts)

    payload = {
        "model": cfg.get("model") or BILLY_MODEL,
        "systemPrompt": cfg.get("systemPrompt") or BILLY_SYSTEM_PROMPT,
        "userMessage": user_message,
        "history": history,
        "generationConfig": {"temperature": 0.5, "maxOutputTokens": 1024},
    }
    answer = None
    try:
        import urllib.request as _ur
        req = _ur.Request(BILLY_GEMINI_PROXY, data=json.dumps(payload).encode("utf-8"),
                          headers={"Content-Type": "application/json"}, method="POST")
        with _ur.urlopen(req, timeout=45) as resp:
            gj = json.loads(resp.read().decode("utf-8"))
        cands = gj.get("candidates") or (gj.get("data") or {}).get("candidates")
        if cands:
            answer = cands[0]["content"]["parts"][0]["text"]
    except Exception as e:
        print("Billy Gemini call failed: %s" % e)
    if not answer:
        answer = ("I'm having trouble reaching my AI service right now. " +
                  ("Here's a relevant note:\n\n" + hits[0]["text"][:600] if hits
                   else "Please try again in a moment, or tap Scan Bill to check a note."))
    return JSONResponse({"answer": answer, "sources": sources})


# ----------------------------
# MULTI-SCAN HTTP ENDPOINT
# ----------------------------

@app.post("/api/multi-scan")
async def multi_scan(files: List[UploadFile] = File(...), user_id: str = "anonymous"):
    """Multi-angle scan endpoint with PARALLEL processing"""
    start_time = time.time()
    
    try:
        scan_id = str(uuid.uuid4())
        print("=" * 60)
        print("🔍 PROCESSING MULTI-SCAN (PARALLEL)")
        print("=" * 60)

        if len(files) == 0:
            raise HTTPException(status_code=400, detail="No files provided")
        
        if len(files) > 3:
            raise HTTPException(status_code=400, detail="Maximum 3 angles allowed")
        
        angle_results = []
        all_features = []
        denominations = []
        angle_frames = []
        
        # Process each angle image
        for i, file in enumerate(files):
            print(f"🔄 Processing angle {i+1}...")
            
            # Read image
            img = await read_image(file)
            angle_frames.append(img)
            
            # Quality analysis
            quality_metrics = analyze_frame_quality(img)
            quality_check = is_frame_quality_acceptable(img)
            
            # Process with PARALLEL execution
            result = await process_frame_parallel(img)
            
            angle_result = {
                "angle_number": i + 1,
                "denomination": result.get("denomination", "UNKNOWN"),
                "features_detected": result.get("detected_features", []),
                "feature_count": result.get("feature_count", 0),
                "coverage_percentage": result.get("coverage_percentage", 0),
                "authenticity": result.get("authenticity", {}).get("status", "UNKNOWN"),
                "is_genuine": result.get("authenticity", {}).get("is_genuine", False),
                "confidence": result.get("authenticity", {}).get("confidence", "LOW"),
                "quality_metrics": quality_metrics
            }
            angle_results.append(angle_result)
            
            if result.get("denomination") != "UNKNOWN":
                denominations.append(result["denomination"])
            
            all_features.append(result.get("security_features", {}))
        
        # Combine results from all angles
        final_denomination = get_consensus_denomination(denominations)
        combined_features = combine_features_across_images(all_features)
        
        features_result = {
            "security_features": combined_features,
            "counterfeit_indicators": {"false_enhanced_value_panel": False},
            "is_high_denomination": is_high_denomination(final_denomination),
            "total_expected_features": 9 if is_high_denomination(final_denomination) else 6,
            "detected_features_count": sum(combined_features.values()) if combined_features else 0,
            "coverage_percentage": (sum(combined_features.values()) / (9 if is_high_denomination(final_denomination) else 6)) * 100 if combined_features else 0
        }
        
        final_authenticity = evaluate_counterfeit(final_denomination, features_result)

        # Component 3: OVI/OVD colour-shift across the angle frames (strongest optical authenticator)
        ovi_color_shift = measure_color_shift(angle_frames)
        if ovi_color_shift.get("shift_detected") and not final_authenticity.get("has_false_evp"):
            boosted = min(100, final_authenticity.get("authenticity_score", 0) + 25)
            final_authenticity["authenticity_score"] = boosted
            if final_authenticity.get("status") != "COUNTERFEIT":
                final_authenticity["is_genuine"] = True
                final_authenticity["status"] = "GENUINE" if boosted >= 50 else "LIKELY GENUINE"
                final_authenticity["confidence"] = "HIGH" if boosted >= 75 else "MEDIUM"
            final_authenticity.setdefault("reasons", []).append(
                f"Optically variable ink/device shifts colour across angles (Δhue {ovi_color_shift['delta']}) — strong genuineness signal.")

        processing_time = round(time.time() - start_time, 2)
        
        # Create and store annotated images for each angle
        angle_images = []
        for i, frame in enumerate(angle_frames):
            try:
                security_dets = simple_detection(model_loader.get_model('security'), frame, SECURITY_MODEL_CLASSES)
                ovi_dets = simple_detection(model_loader.get_model('ovi'), frame, OVI_CLASSES)
                ovd_dets = simple_detection(model_loader.get_model('ovd'), frame, OVD_CLASSES)
                evp_dets = simple_detection(model_loader.get_model('evp'), frame, EVP_CLASSES)
                counterfeit_dets = simple_detection(model_loader.get_model('counterfeit'), frame, COUNTERFEIT_MODEL_CLASSES)
                
                annotated_img = create_numbered_annotated_image(
                    frame, security_dets, ovi_dets, ovd_dets, evp_dets, counterfeit_dets
                )
                
                image_url = await store_annotated_image_for_realtime(
                    annotated_img, "multi_scan", user_id, f"{scan_id}_angle_{i+1}"
                )
                angle_images.append({
                    "angle_number": i + 1,
                    "image_url": image_url
                })
            except Exception as img_error:
                print(f"❌ Error creating annotated image for angle {i+1}: {img_error}")
                angle_images.append({
                    "angle_number": i + 1,
                    "image_url": "error"
                })
        
        detected_features = [feature for feature, detected in combined_features.items() if detected]
        final_result = {
            "scan_id": scan_id,
            "timestamp": datetime.now().isoformat(),
            "analysis_type": "multi_scan",
            "authenticity": final_authenticity.get("status", "UNKNOWN"),
            "is_genuine": final_authenticity.get("is_genuine", False),
            "denomination": final_denomination,
            "angles_processed": len(files),
            "total_features": len(detected_features),
            "features_detected": detected_features,
            "coverage_percentage": features_result["coverage_percentage"],
            "confidence": final_authenticity.get("confidence", "LOW"),
            "authenticity_score": final_authenticity.get("authenticity_score", 0),
            "ovi_color_shift": ovi_color_shift,
            "reasons": final_authenticity.get("reasons", []),
            "angle_results": angle_results,
            "processing_time": processing_time,
            "has_false_evp": final_authenticity.get("has_false_evp", False),
            "is_high_denomination": features_result["is_high_denomination"],
            "total_expected_features": features_result["total_expected_features"],
            "detected_features_count": features_result["detected_features_count"],
            "angle_images": angle_images,
            "annotated_image_url": angle_images[0]["image_url"] if angle_images else "",
            "firebase_status": "stored" if FIREBASE_AVAILABLE else "dummy_mode",
            "status": "success",
            "processing_mode": "parallel"
        }
        
        # Store in Firebase
        storage_id = ""
        try:
            storage_id = await store_real_time_scan_result("multi_scan", final_result, user_id)
            final_result["storage_id"] = storage_id
        except Exception as storage_error:
            print(f"❌ Firebase storage error: {storage_error}")
            final_result["storage_id"] = "storage_failed"

        print("=" * 60)
        print("✅ MULTI-SCAN COMPLETED SUCCESSFULLY (PARALLEL)")
        print("=" * 60)
        
        return JSONResponse(final_result)
        
    except Exception as e:
        print(f"❌ Multi-scan error: {str(e)}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"Multi-scan failed: {str(e)}")

# ----------------------------
# VIDEO-SCAN HTTP ENDPOINT
# ----------------------------

@app.post("/api/video-scan")
async def video_scan(file: UploadFile = File(...), user_id: str = "anonymous"):
    """Video scan endpoint with PARALLEL processing"""
    start_time = time.time()
    
    try:
        scan_id = str(uuid.uuid4())
        print("=" * 60)
        print("🎥 PROCESSING VIDEO SCAN (PARALLEL)")
        print("=" * 60)

        # Save uploaded video to temporary file
        with tempfile.NamedTemporaryFile(delete=False, suffix=".mp4") as temp_video:
            video_content = await file.read()
            temp_video.write(video_content)
            temp_video_path = temp_video.name
        
        try:
            # Extract best frame from video
            best_frame = extract_best_frame_from_video(temp_video_path)
            print(f"✅ Best frame extracted: {best_frame.shape}")
            
            # Quality analysis
            quality_metrics = analyze_frame_quality(best_frame)
            quality_check = is_frame_quality_acceptable(best_frame)
            
            # Process with PARALLEL execution
            result = await process_frame_parallel(best_frame)
            
            # Create annotated image
            security_dets = simple_detection(model_loader.get_model('security'), best_frame, SECURITY_MODEL_CLASSES)
            ovi_dets = simple_detection(model_loader.get_model('ovi'), best_frame, OVI_CLASSES)
            ovd_dets = simple_detection(model_loader.get_model('ovd'), best_frame, OVD_CLASSES)
            evp_dets = simple_detection(model_loader.get_model('evp'), best_frame, EVP_CLASSES)
            counterfeit_dets = simple_detection(model_loader.get_model('counterfeit'), best_frame, COUNTERFEIT_MODEL_CLASSES)
            
            annotated_img = create_numbered_annotated_image(
                best_frame, security_dets, ovi_dets, ovd_dets, evp_dets, counterfeit_dets
            )
            
            processing_time = round(time.time() - start_time, 2)
            
            # Store annotated image
            annotated_image_url = ""
            if FIREBASE_AVAILABLE:
                try:
                    annotated_image_url = await store_annotated_image_for_realtime(
                        annotated_img, "video_scan", user_id, scan_id
                    )
                except Exception as storage_error:
                    print(f"❌ Storage error: {storage_error}")
                    annotated_image_url = "image_storage_error"
            else:
                annotated_image_url = "firebase_unavailable"
            
            # Prepare final result
            final_result = {
                "scan_id": scan_id,
                "timestamp": datetime.now().isoformat(),
                "analysis_type": "video_scan",
                "denomination": result.get("denomination", "UNKNOWN"),
                "authenticity": result.get("authenticity", {}),
                "security_features": result.get("security_features", {}),
                "counterfeit_indicators": result.get("counterfeit_indicators", {}),
                "is_high_denomination": result.get("is_high_denomination", False),
                "feature_summary": f"{result.get('feature_count', 0)}/{result.get('total_expected_features', 6)}",
                "coverage_percentage": result.get("coverage_percentage", 0),
                "detected_features_count": result.get("feature_count", 0),
                "total_expected_features": result.get("total_expected_features", 6),
                "number_mapping": NUMBER_TO_FEATURE_MAPPING,
                "model_info": "Multi-Model Ensemble (6 models) - PARALLEL",
                "logic_version": "17.17",
                "processing_time": processing_time,
                "annotated_image_url": annotated_image_url,
                "firebase_status": "stored" if FIREBASE_AVAILABLE else "dummy_mode",
                "status": "success",
                "quality_metrics": quality_metrics,
                "quality_feedback": quality_check["feedback"] if not quality_check["is_acceptable"] else [],
                "processing_mode": "parallel"
            }
            
            # Store in Firebase
            storage_id = ""
            try:
                storage_id = await store_real_time_scan_result("video_scan", final_result, user_id)
                final_result["storage_id"] = storage_id
            except Exception as storage_error:
                print(f"❌ Firebase storage error: {storage_error}")
                final_result["storage_id"] = "storage_failed"
            
            print("=" * 60)
            print("✅ VIDEO SCAN COMPLETED SUCCESSFULLY (PARALLEL)")
            print("=" * 60)
            
            return JSONResponse(final_result)
            
        finally:
            # Clean up temporary file
            os.unlink(temp_video_path)
        
    except Exception as e:
        print(f"❌ Video scan error: {str(e)}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"Video scan failed: {str(e)}")

# ----------------------------
# HEALTH CHECK ENDPOINT
# ----------------------------

@app.get("/api/health")
async def health_check():
    return JSONResponse({
        "status": "healthy",
        "models_loaded": model_loader.loaded,
        "firebase_available": FIREBASE_AVAILABLE,
        "api_version": "17.17",
        "main_logic": "Multi-Model Ensemble with PARALLEL REAL-TIME Detection",
        "scan_types": ["standard_scan", "multi_scan", "video_scan", "real_time"],
        "real_time_endpoints": [
            "/ws/standard-scan",
            "/ws/real-multi-scan", 
            "/ws/real-video-scan"
        ],
        "http_endpoints": [
            "/api/standard-scan",
            "/api/multi-scan",
            "/api/video-scan"
        ],
        "processing_mode": "PARALLEL",
        "auto_start": "ENABLED for all WebSocket scans",
        "user_control": "COMPLETE_SCAN for manual completion",
        "denomination_logic": "separate_feature_sets",
        "real_time_capabilities": {
            "standard_scan": "Auto-start + Parallel processing + Real-time results",
            "multi_scan": "Auto-start + Angle-based parallel processing", 
            "video_scan": "Auto-start + Continuous parallel analysis"
        },
        "firebase_storage": "ENABLED with annotated images",
        "image_storage": "ALL scans store numbered annotated images",
        "error_handling": "COMPREHENSIVE",
        "quality_checking": "ENABLED for ALL scan types"
    })

@app.get("/api/real-time-status")
async def real_time_status():
    """Get real-time scanning capabilities"""
    return JSONResponse({
        "real_time_scanning": {
            "standard_scan": {
                "endpoint": "/ws/standard-scan",
                "features": [
                    "AUTO-START on connection",
                    "PARALLEL model execution", 
                    "Real-time simultaneous results",
                    "Manual COMPLETE_SCAN only",
                    "Quality feedback",
                    "Firebase storage with images"
                ],
                "user_flow": "Connect → Auto-start → See real-time results → Press Complete when ready"
            },
            "multi_scan": {
                "endpoint": "/ws/real-multi-scan",
                "features": [
                    "AUTO-START on connection",
                    "Parallel angle processing",
                    "Real-time feature coverage",
                    "Manual COMPLETE_SCAN only",
                    "Smart angle sequencing"
                ],
                "user_flow": "Connect → Auto-start → Capture angles → Press Complete when ready"
            },
            "video_scan": {
                "endpoint": "/ws/real-video-scan", 
                "features": [
                    "AUTO-START on connection",
                    "Continuous parallel analysis",
                    "Best-frame selection",
                    "Manual STOP_RECORDING only",
                    "Real-time confidence tracking"
                ],
                "user_flow": "Connect → Auto-start → Record video → Press Stop when ready"
            }
        },
        "http_endpoints": {
            "standard_scan": "/api/standard-scan",
            "multi_scan": "/api/multi-scan", 
            "video_scan": "/api/video-scan"
        },
        "processing_improvements": {
            "mode": "PARALLEL_EXECUTION",
            "models_running": "Simultaneously",
            "performance": "40-60% faster",
            "results_delivery": "All data simultaneously"
        },
        "auto_features": {
            "start": "IMMEDIATE on WebSocket connection",
            "complete": "MANUAL user control only",
            "rationale": "User decides when they see enough features"
        }
    })

# ----------------------------
# Cloud Run Compatibility Fix
# ----------------------------
import os

# Get the port from environment variable (Cloud Run provides PORT=8080)
port = int(os.environ.get("PORT", 8000))

if __name__ == "__main__":
    import uvicorn
    print("\n" + "="*70)
    print("🚀 BILLSENSE PARALLEL REAL-TIME SCANNING API STARTED")
    print("="*70)
    print("🎯 REAL-TIME SCAN TYPES (AUTO-START + PARALLEL PROCESSING):")
    print("   📷 Standard Scan: /ws/standard-scan - AUTO-START + PARALLEL")
    print("   🔄 Real Multi Scan: /ws/real-multi-scan - AUTO-START + PARALLEL") 
    print("   🎥 Real Video Scan: /ws/real-video-scan - AUTO-START + PARALLEL")
    print("📁 HTTP ENDPOINTS (PARALLEL PROCESSING):")
    print("   📷 Standard Scan: /api/standard-scan")
    print("   🔄 Multi Scan: /api/multi-scan") 
    print("   🎥 Video Scan: /api/video-scan")
    print("⚡ PROCESSING MODE: PARALLEL (All models run simultaneously)")
    print("🔓 AUTO-START: IMMEDIATE on WebSocket connection")
    print("⏹️  MANUAL COMPLETE: User presses button when satisfied")
    print("📊 RESULTS: All detection data delivered simultaneously")
    print("🔥 FIREBASE: Storage with annotated images")
    print(f"💻 API READY: http://0.0.0.0:{port}")
    print("="*70)
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")