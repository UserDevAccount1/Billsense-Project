package com.app.billsense.scan.pojo;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Service class for interacting with the BillSense API.
 * Handles HTTP requests for currency analysis.
 *
 * Based on BillSense API Documentation v16.0.
 */
public class CurrencyApiService {

    private static final String TAG = "CurrencyApiService";
    private static final String BASE_URL = com.app.billsense.BuildConfig.API_BASE_URL;

    private final OkHttpClient client;
    private final Gson gson;

    /**
     * Callback interface for handling API responses asynchronously.
     * @param <T> The type of the successful response object.
     */
    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onFailure(String errorMessage, @NonNull Exception e);
    }

    public CurrencyApiService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }

    /**
     * Performs a standard scan by uploading a single image file for analysis.
     * Corresponds to the POST /api/standard-scan endpoint.
     *
     * @param imageFile The image file of the banknote to be analyzed.
     * @param userId A unique identifier for the user performing the scan.
     * @param callback The callback to handle the success or failure of the API call.
     */
    public void standardScan(@NonNull File imageFile, @NonNull String userId,
                             @NonNull ApiCallback<StandardScanResponse> callback) {
        HttpUrl url = HttpUrl.parse(BASE_URL + "/api/standard-scan");
        if (url == null) {
            callback.onFailure("Invalid API endpoint URL", new IllegalArgumentException("URL is malformed"));
            return;
        }

        String mimeType = getMimeType(imageFile);
        if (mimeType.equals("application/octet-stream")) {
            Log.w(TAG, "Could not determine a specific image MIME type for " + imageFile.getName());
        }

        // Build the multipart request body as specified in the documentation
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", imageFile.getName(), RequestBody.create(imageFile, MediaType.parse(mimeType)))
                .addFormDataPart("user_id", userId)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        Log.d(TAG, "Executing Standard Scan for user: " + userId + " with file: " + imageFile.getName());

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Standard Scan request failed", e);
                callback.onFailure("Network request failed: " + e.getMessage(), e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        String errorBodyString = (responseBody != null) ? responseBody.string() : "Unknown error";
                        Log.e(TAG, "API Error " + response.code() + ": " + errorBodyString);
                        callback.onFailure("API request not successful: " + response.code(), new IOException(errorBodyString));
                        return;
                    }

                    if (responseBody == null) {
                        callback.onFailure("Received empty response body", new IOException("Empty response"));
                        return;
                    }

                    String jsonResponse = responseBody.string();
                    Log.d(TAG, "Standard Scan Response: " + jsonResponse);

                    try {
                        StandardScanResponse scanResponse = gson.fromJson(jsonResponse, StandardScanResponse.class);
                        if (scanResponse == null || scanResponse.getScanId() == null) {
                            throw new JsonSyntaxException("Parsed response or scan_id is null");
                        }
                        callback.onSuccess(scanResponse);
                    } catch (JsonSyntaxException e) {
                        Log.e(TAG, "Failed to parse JSON", e);
                        callback.onFailure("Error parsing server response", e);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error reading response body", e);
                    callback.onFailure("Error processing API response", e);
                }
            }
        });
    }

    /**
     * Performs a multi-scan by uploading multiple image files for a combined analysis.
     * Corresponds to the POST /api/multi-scan endpoint.
     *
     * @param imageFiles A list of image files of the banknote to be analyzed.
     * @param userId A unique identifier for the user performing the scan.
     * @param callback The callback to handle the success or failure of the API call.
     */
    public void multiScan(@NonNull List<File> imageFiles, @NonNull String userId,
                          @NonNull ApiCallback<MultiScanResponse> callback) {
        HttpUrl url = HttpUrl.parse(BASE_URL + "/api/multi-scan");
        if (url == null) {
            callback.onFailure("Invalid API endpoint URL", new IllegalArgumentException("URL is malformed"));
            return;
        }

        // Build the multipart request body
        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);

        // Loop through the list of files and add each one to the request body
        for (File file : imageFiles) {
            if (file.exists()) {
                String mimeType = getMimeType(file);
                bodyBuilder.addFormDataPart("files", file.getName(),
                        RequestBody.create(file, MediaType.parse(mimeType)));
            } else {
                Log.w(TAG, "File provided to multiScan does not exist: " + file.getPath());
            }
        }

        // Add the user_id to the request
        bodyBuilder.addFormDataPart("user_id", userId);

        Request request = new Request.Builder()
                .url(url)
                .post(bodyBuilder.build())
                .build();

        Log.d(TAG, "Executing Multi-Scan for user: " + userId + " with " + imageFiles.size() + " files.");

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Multi-Scan request failed", e);
                callback.onFailure("Network request failed: " + e.getMessage(), e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        String errorBodyString = (responseBody != null) ? responseBody.string() : "Unknown error";
                        Log.e(TAG, "API Error " + response.code() + ": " + errorBodyString);
                        callback.onFailure("API request not successful: " + response.code(), new IOException(errorBodyString));
                        return;
                    }

                    if (responseBody == null) {
                        callback.onFailure("Received empty response body", new IOException("Empty response"));
                        return;
                    }

                    String jsonResponse = responseBody.string();
                    Log.d(TAG, "Multi-Scan Response: " + jsonResponse);

                    try {
                        MultiScanResponse scanResponse = gson.fromJson(jsonResponse, MultiScanResponse.class);
                        if (scanResponse == null || scanResponse.getScanId() == null) {
                            throw new JsonSyntaxException("Parsed response or scan_id is null");
                        }
                        callback.onSuccess(scanResponse);
                    } catch (JsonSyntaxException e) {
                        Log.e(TAG, "Failed to parse JSON", e);
                        callback.onFailure("Error parsing server response", e);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error reading response body", e);
                    callback.onFailure("Error processing API response", e);
                }
            }
        });
    }



    /**
     * Performs a video scan by uploading a single video file for analysis.
     * Corresponds to the POST /api/video-scan endpoint.
     *
     * @param videoFile The video file of the banknote to be analyzed.
     * @param userId A unique identifier for the user performing the scan.
     * @param callback The callback to handle the success or failure of the API call.
     */
    public void videoScan(@NonNull File videoFile, @NonNull String userId,
                          @NonNull ApiCallback<VideoScanResponse> callback) {
        HttpUrl url = HttpUrl.parse(BASE_URL + "/api/video-scan");
        if (url == null) {
            callback.onFailure("Invalid API endpoint URL", new IllegalArgumentException("URL is malformed"));
            return;
        }

        // Build the multipart request body
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", videoFile.getName(),
                        RequestBody.create(videoFile, MediaType.parse("video/mp4")))
                .addFormDataPart("user_id", userId)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        Log.d(TAG, "Executing Video Scan for user: " + userId + " with file: " + videoFile.getName());

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Video Scan request failed", e);
                callback.onFailure("Network request failed: " + e.getMessage(), e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        String errorBodyString = (responseBody != null) ? responseBody.string() : "Unknown error";
                        Log.e(TAG, "API Error " + response.code() + ": " + errorBodyString);
                        callback.onFailure("API request not successful: " + response.code(), new IOException(errorBodyString));
                        return;
                    }

                    if (responseBody == null) {
                        callback.onFailure("Received empty response body", new IOException("Empty response"));
                        return;
                    }

                    String jsonResponse = responseBody.string();
                    Log.d(TAG, "Video Scan Response: " + jsonResponse);

                    try {
                        VideoScanResponse scanResponse = gson.fromJson(jsonResponse, VideoScanResponse.class);
                        if (scanResponse == null || scanResponse.getScanId() == null) {
                            throw new JsonSyntaxException("Parsed response or scan_id is null");
                        }
                        callback.onSuccess(scanResponse);
                    } catch (JsonSyntaxException e) {
                        Log.e(TAG, "Failed to parse JSON", e);
                        callback.onFailure("Error parsing server response", e);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error reading response body", e);
                    callback.onFailure("Error processing API response", e);
                }
            }
        });
    }


    /*
    multi scan second option

    /**
     * Performs a multi-scan by uploading multiple image files for a combined analysis.
     * Corresponds to the POST /api/multi-scan endpoint.
     *
     * @param imageFiles A list of image files of the banknote to be analyzed.
     * @param userId     A unique identifier for the user performing the scan.
     * @param callback   The callback to handle the success or failure of the API call.
   //  *//*
    public void multiScan(@NonNull List<File> imageFiles, @NonNull String userId,
                          @NonNull ApiCallback<MultiScanResponse> callback) {
        HttpUrl url = HttpUrl.parse(BASE_URL + "/api/multi-scan");
        if (url == null) {
            callback.onFailure("Invalid API endpoint URL", new IllegalArgumentException("URL is malformed"));
            return;
        }

        // Build the multipart request body
        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);

        // Loop through the list of files and add each one to the request body
        for (File file : imageFiles) {
            if (file.exists()) {
                String mimeType = getMimeType(file);
                try {
                    // --- START OF THE FIX ---
                    // 1. Read the file into a byte array first.
                    byte[] fileBytes = readBytesFromFile(file);

                    // 2. Create the RequestBody from the byte array.
                    RequestBody fileBody = RequestBody.create(fileBytes, MediaType.parse(mimeType));

                    // 3. Add it to the multipart body.
                    bodyBuilder.addFormDataPart("files", file.getName(), fileBody);
                    // --- END OF THE FIX ---

                } catch (IOException e) {
                    Log.e(TAG, "Failed to read file for multi-scan: " + file.getPath(), e);
                    // Optionally, you could decide to skip this file or fail the entire request
                }
            } else {
                Log.w(TAG, "File provided to multiScan does not exist: " + file.getPath());
            }
        }

        // Add the user_id to the request
        bodyBuilder.addFormDataPart("user_id", userId);

        Request request = new Request.Builder()
                .url(url)
                .post(bodyBuilder.build())
                .build();

        Log.d(TAG, "Executing Multi-Scan for user: " + userId + " with " + imageFiles.size() + " files.");

        // The rest of the client.newCall() method remains exactly the same...
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Multi-Scan request failed", e);
                callback.onFailure("Network request failed: " + e.getMessage(), e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        String errorBodyString = (responseBody != null) ? responseBody.string() : "Unknown error";
                        Log.e(TAG, "API Error " + response.code() + ": " + errorBodyString);
                        callback.onFailure("API request not successful: " + response.code(), new IOException(errorBodyString));
                        return;
                    }
                    // ... and so on
                    if (responseBody == null) {
                        callback.onFailure("Received empty response body", new IOException("Empty response"));
                        return;
                    }

                    String jsonResponse = responseBody.string();
                    Log.d(TAG, "Multi-Scan Response: " + jsonResponse);

                    try {
                        MultiScanResponse scanResponse = gson.fromJson(jsonResponse, MultiScanResponse.class);
                        if (scanResponse == null || scanResponse.getScanId() == null) {
                            throw new JsonSyntaxException("Parsed response or scan_id is null");
                        }
                        callback.onSuccess(scanResponse);
                    } catch (JsonSyntaxException e) {
                        Log.e(TAG, "Failed to parse JSON", e);
                        callback.onFailure("Error parsing server response", e);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error reading response body", e);
                    callback.onFailure("Error processing API response", e);
                }
            }
        });
    }

    // --- ADD THIS HELPER METHOD TO THE CLASS ---

    /**
     * Helper method to read a file's contents into a byte array.
     *
     * @param file The file to read.
     * @return A byte array of the file's contents.
     * @throws IOException if the file cannot be read.
  //   *//*
    private byte[] readBytesFromFile(File file) throws IOException {
        try (java.io.InputStream inputStream = new java.io.FileInputStream(file);
             java.io.ByteArrayOutputStream byteOut = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteOut.write(buffer, 0, bytesRead);
            }
            return byteOut.toByteArray();
        }
    }





     */





    /**
     * Checks the health of the BillSense API.
     * Corresponds to the GET /api/health endpoint.
     *
     * @param callback The callback to handle the success or failure of the API call.
     */
    public void healthCheck(@NonNull ApiCallback<HealthCheckResponse> callback) {
        HttpUrl url = HttpUrl.parse(BASE_URL + "/api/health");
        if (url == null) {
            callback.onFailure("Invalid API endpoint URL", new IllegalArgumentException("URL is malformed"));
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .get() // This is a GET request
                .build();

        Log.d(TAG, "Executing Health Check");

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Health Check request failed", e);
                callback.onFailure("Network request failed: " + e.getMessage(), e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) {
                        String errorBodyString = (responseBody != null) ? responseBody.string() : "Unknown error";
                        Log.e(TAG, "API Error " + response.code() + ": " + errorBodyString);
                        callback.onFailure("API request not successful: " + response.code(), new IOException(errorBodyString));
                        return;
                    }

                    if (responseBody == null) {
                        callback.onFailure("Received empty response body", new IOException("Empty response"));
                        return;
                    }

                    String jsonResponse = responseBody.string();
                    Log.d(TAG, "Health Check Response: " + jsonResponse);

                    try {
                        HealthCheckResponse healthResponse = gson.fromJson(jsonResponse, HealthCheckResponse.class);
                        if (healthResponse == null || healthResponse.getStatus() == null) {
                            throw new JsonSyntaxException("Parsed response or status is null");
                        }
                        callback.onSuccess(healthResponse);
                    } catch (JsonSyntaxException e) {
                        Log.e(TAG, "Failed to parse JSON", e);
                        callback.onFailure("Error parsing server response", e);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error reading response body", e);
                    callback.onFailure("Error processing API response", e);
                }
            }
        });
    }


            /**
     * Determines the MIME type of a file based on its extension.
     * @param file The file to inspect.
     * @return A string representing the MIME type (e.g., "image/jpeg").
     */
    private String getMimeType(File file) {
        String path = file.getPath().toLowerCase();
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (path.endsWith(".png")) {
            return "image/png";
        }
        return "application/octet-stream"; // A safe default
    }

    /**
     * Converts a Bitmap image to a base64 encoded string with the required data URI prefix.
     * @param bitmap The image to convert.
     * @return The base64 encoded string.
     */
    public static String convertBitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        // Compress the image to JPEG format
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        // Encode to base64 and add the required prefix for the API
        return "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }

}
