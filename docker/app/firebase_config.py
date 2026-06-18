import firebase_admin
from firebase_admin import credentials, firestore, storage
import uuid
from datetime import datetime
import cv2
import base64
from io import BytesIO
from PIL import Image
import numpy as np
import os
from pathlib import Path
from datetime import timedelta

class FirebaseClient:
    def __init__(self):
        try:
            # Initialize Firebase
            cred_path = "serviceAccountKey.json"
            print(f"🔍 Looking for service account file at: {cred_path}")
            print(f"🔍 File exists: {os.path.exists(cred_path)}")
            print(f"🔍 Current directory: {os.getcwd()}")
            
            # List files in current directory for debugging
            print(f"📁 Directory contents:")
            for file in os.listdir('.'):
                print(f"   - {file}")
            
            if os.path.exists(cred_path):
                print("✅ Service account key found, initializing Firebase...")
                cred = credentials.Certificate(cred_path)
                firebase_admin.initialize_app(cred, {
                    'storageBucket': 'bill-sense-aec6b.firebasestorage.app'
                })
                
                self.db = firestore.client()
                self.bucket = storage.bucket()
                
                print("✅ Firebase initialized successfully with service account key")
                print(f"🔍 Project ID from credentials: {cred.project_id}")
                print(f"📦 Storage bucket: {self.bucket.name}")
                
                # Test Firestore connection
                self.test_firestore()
                # Test Storage connection
                self.test_storage()
                
            else:
                print("❌ Service account key file not found")
                print("💡 Checking for environment variable fallback...")
                # Try environment variable as fallback
                if os.environ.get('GOOGLE_APPLICATION_CREDENTIALS'):
                    print("✅ Using GOOGLE_APPLICATION_CREDENTIALS from environment")
                    cred = credentials.ApplicationDefault()
                    firebase_admin.initialize_app(cred, {
                        'storageBucket': 'bill-sense-aec6b.firebasestorage.app'
                    })
                    self.db = firestore.client()
                    self.bucket = storage.bucket()
                    print("✅ Firebase initialized with application default credentials")
                else:
                    raise FileNotFoundError("serviceAccountKey.json not found and no fallback credentials available")
                
        except Exception as e:
            print(f"❌ Firebase initialization error: {e}")
            import traceback
            traceback.print_exc()
            raise

    def test_firestore(self):
        """Test Firestore connection"""
        try:
            print("🔥 Testing Firestore connection...")
            test_ref = self.db.collection('connection_tests').document(str(uuid.uuid4()))
            test_data = {
                'message': 'Firebase connection test',
                'status': 'success',
                'test_time': datetime.now().isoformat(),
                'environment': 'cloud_run' if os.getenv('K_SERVICE') else 'local'
            }
            test_ref.set(test_data)
            
            # Read back
            doc = test_ref.get()
            if doc.exists:
                print("✅ Firestore write test: SUCCESS")
                print("✅ Firestore read test: SUCCESS")
                print(f"🔍 Test document data: {doc.to_dict()}")
            else:
                print("❌ Firestore read test: FAILED")
                
        except Exception as e:
            print(f"❌ Firestore test error: {e}")

    def test_storage(self):
        """Test Storage connection"""
        try:
            print("📦 Testing Storage connection...")
            # Test if bucket is accessible
            if self.bucket:
                print(f"✅ Storage bucket accessible: {self.bucket.name}")
                # Try to list a few blobs to test permissions
                blobs = list(self.bucket.list_blobs(max_results=2))
                print(f"✅ Storage list test: SUCCESS (found {len(blobs)} blobs)")
            else:
                print("❌ Storage bucket not available")
                
        except Exception as e:
            print(f"❌ Storage test error: {e}")

    def store_scan_result(self, scan_data, user_id="anonymous", collection_name="scans"):
        """Store scan results in organized Firestore collections"""
        try:
            print(f"🔥 Storing scan result in Firestore collection: {collection_name}")
            
            # Add storage metadata
            scan_data['firebase_stored_at'] = datetime.now().isoformat()
            scan_data['storage_collection'] = collection_name
            scan_data['user_id'] = user_id
            
            # Store in specified collection
            doc_ref = self.db.collection(collection_name).document()
            doc_ref.set(scan_data)
            
            print(f"✅ Results stored in {collection_name} with ID: {doc_ref.id}")
            print(f"📋 Scan type: {scan_data.get('analysis_type', 'unknown')}")
            print(f"👤 User: {user_id}")
            
            return doc_ref.id
            
        except Exception as e:
            print(f"❌ Firebase storage error: {e}")
            raise e

    def store_annotated_image(self, image: np.ndarray, user_id: str, scan_id: str, custom_path: str = None) -> str:
        """Store annotated image in organized Firebase Storage structure"""
        try:
            print("📤 Uploading annotated image to Firebase Storage...")
            
            if image is None:
                raise ValueError("Image is None")
            
            # Convert OpenCV image to bytes
            success, buffer = cv2.imencode('.jpg', image, [cv2.IMWRITE_JPEG_QUALITY, 85])
            if not success:
                raise Exception("Failed to encode image to JPEG")
            
            image_bytes = buffer.tobytes()
            print(f"📷 Image encoded: {len(image_bytes)} bytes")
            
            # Use custom path if provided, otherwise use organized structure
            if custom_path:
                # Remove any existing .jpg extension to avoid duplication
                storage_path = custom_path.replace('.jpg', '') + '.jpg'
            else:
                # Create organized structure
                timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
                scan_type = "standard"
                storage_path = f"{scan_type}_scans/{user_id}/{timestamp}_{scan_id}.jpg"
            
            print(f"📁 Storage path: {storage_path}")
            
            # Upload with a Firebase download token. This works with uniform bucket-level
            # access (the default), unlike blob.make_public() which needs per-object ACLs
            # and throws on such buckets -> previously caused "image_storage_error".
            import uuid as _uuid
            from urllib.parse import quote as _quote
            token = str(_uuid.uuid4())
            blob = self.bucket.blob(storage_path)
            blob.metadata = {'firebaseStorageDownloadTokens': token}
            blob.upload_from_string(image_bytes, content_type='image/jpeg')

            image_url = ("https://firebasestorage.googleapis.com/v0/b/"
                         f"{self.bucket.name}/o/{_quote(storage_path, safe='')}?alt=media&token={token}")
            print(f"✅ Image stored successfully: {image_url}")
            return image_url
            
        except Exception as e:
            print(f"❌ Image storage error: {e}")
            import traceback
            traceback.print_exc()
            raise e

    def get_user_scan_history(self, user_id: str, scan_type: str = None, limit: int = 10):
        """Get user's scan history from organized collections"""
        try:
            # Define collection mapping
            collections = {
                "standard": "standard_scans",
                "multi": "multi_scans",
                "video": "video_scans",
                "real_time": "real_time_scans",
                "all": ["standard_scans", "multi_scans", "video_scans", "real_time_scans"]
            }
            
            scan_history = []
            
            if scan_type and scan_type in collections and scan_type != "all":
                # Get specific scan type
                collection_name = collections[scan_type]
                scans_ref = self.db.collection(collection_name)
                query = scans_ref.where('user_id', '==', user_id)\
                                .order_by('timestamp', direction='DESCENDING')\
                                .limit(limit)
                docs = query.stream()
                
                for doc in docs:
                    scan_data = doc.to_dict()
                    scan_data['document_id'] = doc.id
                    scan_data['collection'] = collection_name
                    scan_history.append(scan_data)
                    
            else:
                # Get all scan types
                target_collections = collections["all"] if scan_type == "all" else collections.values()
                
                for collection_name in target_collections:
                    scans_ref = self.db.collection(collection_name)
                    query = scans_ref.where('user_id', '==', user_id)\
                                    .order_by('timestamp', direction='DESCENDING')\
                                    .limit(limit // len(target_collections))
                    docs = query.stream()
                    
                    for doc in docs:
                        scan_data = doc.to_dict()
                        scan_data['document_id'] = doc.id
                        scan_data['collection'] = collection_name
                        scan_history.append(scan_data)
                
                # Sort by timestamp
                scan_history.sort(key=lambda x: x.get('timestamp', ''), reverse=True)
                scan_history = scan_history[:limit]
            
            print(f"✅ Retrieved {len(scan_history)} scans for user {user_id}")
            return scan_history
            
        except Exception as e:
            print(f"❌ Error getting scan history: {e}")
            return []

    def cleanup_old_scans(self, user_id: str, older_than_days: int = 30):
        """Clean up old scans (admin function)"""
        try:
            cutoff_date = datetime.now() - timedelta(days=older_than_days)
            cutoff_timestamp = cutoff_date.isoformat()
            
            collections = ["standard_scans", "multi_scans", "video_scans", "real_time_scans"]
            deleted_count = 0
            
            for collection_name in collections:
                scans_ref = self.db.collection(collection_name)
                query = scans_ref.where('user_id', '==', user_id)\
                                .where('timestamp', '<', cutoff_timestamp)
                docs = query.stream()
                
                for doc in docs:
                    doc.reference.delete()
                    deleted_count += 1
            
            print(f"✅ Cleaned up {deleted_count} scans older than {older_than_days} days for user {user_id}")
            return deleted_count
            
        except Exception as e:
            print(f"❌ Error cleaning up old scans: {e}")
            return 0

# Global Firebase client instance with enhanced debugging
try:
    print("=" * 50)
    print("🔄 INITIALIZING FIREBASE CLIENT...")
    print("=" * 50)
    firebase_client = FirebaseClient()
    print("✅ Firebase client initialized successfully")
    print(f"🔍 Firebase client object: {firebase_client}")
    print(f"🔍 Has Firestore: {hasattr(firebase_client, 'db')}")
    print(f"🔍 Has Storage: {hasattr(firebase_client, 'bucket')}")
    if hasattr(firebase_client, 'bucket') and firebase_client.bucket:
        print(f"📦 Storage bucket: {firebase_client.bucket.name}")
    print("=" * 50)
except Exception as e:
    print(f"❌ Failed to initialize Firebase client: {e}")
    import traceback
    traceback.print_exc()
    firebase_client = None