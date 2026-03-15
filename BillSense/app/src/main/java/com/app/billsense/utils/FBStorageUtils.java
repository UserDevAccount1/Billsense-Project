package com.app.billsense.utils;

import android.net.Uri;
import android.util.Log;

import com.app.billsense.interfaces.FBInterface;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.UUID;

public class FBStorageUtils {
    public static String ID_IMAGE_PATH = "ID_Images";
    public static String PROFILE_IMAGE_PATH = "Profile_Images";
    public static String POST_IMAGE_PATH = "Post_Images";
    public static String POST_VIDEO_PATH = "Post_Videos";
    public static String CASE_IMAGE_PATH = "Case_Images";

    public static void uploadImage(String imagePath, Uri imageUri,
            FBInterface.ImageUploadCallback callback) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference();
        String imageName = UUID.randomUUID().toString();
        StorageReference imageRef = storageRef.child(imagePath + "/" + imageName);

        UploadTask uploadTask = imageRef.putFile(imageUri);

        uploadTask.addOnFailureListener(callback::onImageUploadFailure)
                .addOnSuccessListener(taskSnapshot -> {
            imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String downloadUrl = uri.toString();
                callback.onImageUploadSuccess(downloadUrl);
            }).addOnFailureListener(callback::onImageUploadFailure);
        });
    }

    public enum FileType {
        IMAGE, VIDEO
    }

    public static void uploadFile(String filePath, Uri fileUri, FileType fileType,
                                  FBInterface.FileUploadCallback callback) {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference();

        // Generate a unique file name
        String fileName = UUID.randomUUID().toString();
        if (fileType == FileType.VIDEO) {
            fileName += ".mp4";
        } else {
            fileName += ".jpg";
        }
        StorageReference fileRef = storageRef.child(filePath + "/" + fileName);

        UploadTask uploadTask = fileRef.putFile(fileUri);

        uploadTask.addOnFailureListener(callback::onFileUploadFailure)
                .addOnSuccessListener(taskSnapshot -> {
                    fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String downloadUrl = uri.toString();
                        callback.onFileUploadSuccess(downloadUrl);
                    }).addOnFailureListener(callback::onFileUploadFailure);
                })
                .addOnProgressListener(snapshot -> {
                    double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                    callback.onFileUploadProgress(progress);
                    Log.d("Upload", "File upload progress: " + progress + "%");
                });
    }

}
