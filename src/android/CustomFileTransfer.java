package com.karamsawalha.customfiletransfer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CustomFileTransfer extends CordovaPlugin {
  private static final String CHANNEL_ID = "UPLOAD_CHANNEL";
  private NotificationManager notificationManager;
  private ExecutorService executorService;

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
    if (action.equals("uploadFiles")) {
      JSONArray filePaths = args.getJSONArray(0);
      String uploadUrl = args.getString(1); // Assuming URL passed in options

      // Set up a thread pool for concurrent uploads
      executorService = Executors.newFixedThreadPool(Math.min(filePaths.length(), 4)); // Adjust pool size as needed

      // Start concurrent uploads
      for (int i = 0; i < filePaths.length(); i++) {
        String filePath = filePaths.getString(i);
        executorService.submit(() -> {
          try {
            uploadFile(new File(filePath), uploadUrl);
            callbackContext.success("File uploaded: " + filePath);
          } catch (Exception e) {
            callbackContext.error("Upload failed for " + filePath + ": " + e.getMessage());
          }
        });
      }
      return true;
    }
    return false;
  }

  private void uploadFile(File file, String uploadUrl) throws Exception {
    initNotificationChannel();

    int chunkSize = 1024 * 1024; // 1MB chunks
    long fileSize = file.length();
    int chunks = (int) Math.ceil((double) fileSize / chunkSize);

    try (FileInputStream fis = new FileInputStream(file)) {
      byte[] buffer = new byte[chunkSize];
      for (int chunkIndex = 0; chunkIndex < chunks; chunkIndex++) {
        int bytesRead = fis.read(buffer);
        if (bytesRead > 0) {
          uploadChunk(uploadUrl, buffer, bytesRead, chunkIndex, chunks, file.getName());
        }
      }
    }
  }

  private void uploadChunk(String uploadUrl, byte[] chunkData, int chunkLength, int chunkIndex, int totalChunks, String fileName) throws Exception {
    URL url = new URL(uploadUrl + "/" + fileName); // Append filename for blob storage
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setDoOutput(true);
    conn.setRequestMethod("PUT");  // Use PUT for Azure blob compatibility
    conn.setRequestProperty("x-ms-blob-type", "BlockBlob"); // Set for Azure Block Blob

    // Add headers to indicate chunk and file status
    conn.setRequestProperty("Chunk-Index", String.valueOf(chunkIndex));
    conn.setRequestProperty("Total-Chunks", String.valueOf(totalChunks));

    try (OutputStream os = conn.getOutputStream()) {
      os.write(chunkData, 0, chunkLength);
    }

    // Update progress notification
    int progress = (int) ((chunkIndex + 1) / (float) totalChunks * 100);
    sendProgressNotification(fileName, progress);
    conn.getResponseCode(); // Ensure server response
  }

  private void initNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "File Uploads", NotificationManager.IMPORTANCE_LOW);
      notificationManager = (NotificationManager) cordova.getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
      notificationManager.createNotificationChannel(channel);
    }
  }

  private void sendProgressNotification(String fileName, int progress) {
    Notification notification = new NotificationCompat.Builder(cordova.getContext(), CHANNEL_ID)
      .setContentTitle("Uploading " + fileName)
      .setContentText("Progress: " + progress + "%")
      .setSmallIcon(android.R.drawable.stat_sys_upload)
      .setProgress(100, progress, false)
      .build();

    notificationManager.notify(fileName.hashCode(), notification);
  }
}
