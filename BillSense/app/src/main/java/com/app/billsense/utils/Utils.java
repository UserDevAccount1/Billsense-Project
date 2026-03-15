package com.app.billsense.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.app.billsense.model.Tokens;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class Utils {

    public static ArrayList<Tokens> customerTokens = new ArrayList<>();
    public static ArrayList<Tokens> technicianTokens = new ArrayList<>();
    public static final String fcmTopic = "customer_notification";
    public static final String TAG = "Utils";

    public static void showToast(Context context, String message) {
        if (context != null) {
            try {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e("FBHelper", "Error showing toast: " + e.getMessage());
            }
        }
    }

    public static String generateUniqueCode() {
        Random random = new Random();
        int code = 10000 + random.nextInt(90000); // Generate a random number between 10000 and 99999
        return String.valueOf(code);
    }

    public static String generateTicketNo() {
        Random random = new Random();
        int code = 10000 + random.nextInt(90000); // Generate a random number between 10000 and 99999
        return String.valueOf(code);
    }

    public static String getCurrentDateTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        Date date = new Date();
        return dateFormat.format(date);
    }

    public static String getCurrentDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        Date date = new Date();
        return dateFormat.format(date);
    }

    public static String getCurrentTime() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        Date date = new Date();
        return timeFormat.format(date);
    }

    public static String getVotingStatusText(String dateTimeRange) {
        // dateTimeRange should be in the format "dd/MM/yyyy HH:mm  |  dd/MM/yyyy HH:mm"
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        if (dateTimeRange == null) {
            return "Invalid date/time format";
        }

        try {
            String[] parts = dateTimeRange.split("\\|", -1); // Split by "|"
            if (parts.length == 2) {
                String startPart = parts[0].trim();  // Just trim, no substring
                String endPart = parts[1].trim();    // Just trim, no substring

                Date startDate = sdf.parse(startPart);
                Date endDate = sdf.parse(endPart);

                if (startDate != null && endDate != null) {
                    return getVotingStatusString(startDate, endDate);
                } else {
                    return "Invalid date/time format";
                }
            } else {
                return "Invalid date/time range format";
            }
        } catch (ParseException e) {
            e.printStackTrace();
            return "Error parsing date/time";
        }
    }

    private static String getVotingStatusString(Date startDate, Date endDate) {
        Calendar now = Calendar.getInstance();
        Date currentDate = now.getTime();

        if (currentDate.before(startDate)) {
            // Voting has not started yet
            long timeDiffMillis = startDate.getTime() - currentDate.getTime();
            String timeLeft = formatTimeRemaining(timeDiffMillis);
            return "Voting starts in: " + timeLeft;

        } else if (currentDate.after(endDate)) {
            // Voting has ended
            return "Voting has ended";

        } else {
            // Voting is in progress
            long timeDiffMillis = endDate.getTime() - currentDate.getTime();
            String timeLeft = formatTimeRemaining(timeDiffMillis);
            return "Voting ends in: " + timeLeft;
        }
    }

    private static String formatTimeRemaining(long timeDiffMillis) {
        long days = timeDiffMillis / (1000 * 60 * 60 * 24);
        long hours = (timeDiffMillis % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
        long minutes = (timeDiffMillis % (1000 * 60 * 60)) / (1000 * 60);

        if (days > 0) {
            return String.format("%d days, %d hours, %d minutes", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%d hours, %d minutes", hours, minutes);
        } else {
            return String.format("%d minutes", minutes);
        }
    }

    /**
     * Helper method to convert a content URI (from CameraX or Gallery) to a File object.
     * This is necessary because the API service expects a File.
     * Ensure this method exists in your StandardScanActivity.
     */
    public static File getFileFromContentUri(Activity activity, Uri contentUri) {
        if (contentUri == null) {
            Log.e(TAG, "getFileFromContentUri: Input contentUri is null.");
            return null;
        }
        File tempFile = null;
        String tempFileName = "upload_image_" + System.currentTimeMillis();
        String extension = ".jpg"; // Default or try to infer

        // A more robust way to get extension might be needed if URIs are diverse
        // For now, assuming JPG or let the server handle it.

        try (InputStream inputStream = activity.getContentResolver().openInputStream(contentUri)) {
            if (inputStream != null) {
                tempFile = File.createTempFile(tempFileName, extension,
                        activity.getApplicationContext().getCacheDir());
                tempFile.deleteOnExit(); // Ensure temporary file is cleaned up

                try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[4 * 1024]; // 4KB buffer
                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, read);
                    }
                    outputStream.flush();
                }
                Log.d(TAG, "getFileFromContentUri: Successfully created temp file: " + tempFile.getAbsolutePath());
                return tempFile;
            } else {
                Log.e(TAG, "getFileFromContentUri: Could not open InputStream for URI: " + contentUri);
                return null;
            }
        } catch (IOException e) {
            Log.e(TAG, "getFileFromContentUri: IOException while creating temp file from URI: " + contentUri, e);
            return null;
        } catch (SecurityException se) {
            Log.e(TAG, "getFileFromContentUri: SecurityException, permission issue with URI? " + contentUri, se);
            return null;
        }
    }

    public static Bitmap getBitmapFromFile(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }


    // ... (existing methods like getBitmapFromFile)

    /**
     * Converts a content URI (from CameraX or Gallery) into a temporary File object.
     * This is necessary because the API service needs a File to upload.
     *
     * @param context The application context.
     * @param uri     The content URI of the image.
     * @return A File object pointing to a temporary copy of the image, or null on failure.
     */
    public static File uriToFile(Context context, Uri uri) {
        if (uri == null) {
            Log.e(TAG, "uriToFile: Input URI is null.");
            return null;
        }
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                Log.e(TAG, "uriToFile: Could not open InputStream for URI: " + uri);
                return null;
            }

            // Create a temporary file in the app's cache directory
            File file = new File(context.getCacheDir(), "temp_upload_" + System.currentTimeMillis() + ".jpeg");

            // Copy the contents of the URI's stream to the temporary file
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024]; // 4k buffer
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
            }
            Log.d(TAG, "uriToFile: Successfully created temp file: " + file.getAbsolutePath());
            return file;
        } catch (Exception e) {
            Log.e(TAG, "uriToFile: Failed to convert URI to File", e);
            return null;
        }
    }




}
