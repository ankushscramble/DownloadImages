package com.downloadimages;

import android.app.DownloadManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class ImageDownloader {

    private OnImageLoaderListener imageLoaderListener;
    private Set<String> urlsInProgress = new HashSet<>();
    private final String TAG = "ImageDownloader";
    private int posImage = 0;
    private static final int BUFFER_SIZE = 4096;

    public ImageDownloader(@NonNull OnImageLoaderListener listener) {
        this.imageLoaderListener = listener;
    }

    /**
     * Interface definition for callbacks to be invoked
     * when the image download status changes.
     */
    public interface OnImageLoaderListener {
        /**
         * Invoked if an error has occurred and thus
         * the download did not complete
         *
         * @param error the occurred error
         */
        void onError(ImageError error);

        /**
         * Invoked every time the progress of the download changes
         *
         * @param percent new status in %
         */
        void onProgressChange(int percent);

        /**
         * Invoked after the image has been successfully downloaded
         *
         * @param result the downloaded image
         */
        void onComplete(Bitmap result, String filename, int pos);

        void onComplete(String path, String filename);
    }

    /**
     * Downloads a file from a URL
     *
     * @param fileURL HTTP URL of the file to be downloaded
     * @param saveDir path of the directory to save the file
     * @throws IOException
     */
    public static void downloadFile(String fileURL, String saveDir) throws IOException {
        URL url = new URL(fileURL);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        int responseCode = httpConn.getResponseCode();

        // always check HTTP response code first
        if (responseCode == HttpURLConnection.HTTP_OK) {
            String fileName = "";
            String disposition = httpConn.getHeaderField("Content-Disposition");
            String contentType = httpConn.getContentType();
            int contentLength = httpConn.getContentLength();

            if (disposition != null) {
                // extracts file name from header field
                int index = disposition.indexOf("filename=");
                if (index > 0) {
                    fileName = disposition.substring(index + 10,
                            disposition.length() - 1);
                }
            } else {
                // extracts file name from URL
                fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1,
                        fileURL.length());
            }

            System.out.println("Content-Type = " + contentType);
            System.out.println("Content-Disposition = " + disposition);
            System.out.println("Content-Length = " + contentLength);
            System.out.println("fileName = " + fileName);

            // opens input stream from the HTTP connection
            InputStream inputStream = httpConn.getInputStream();
            String saveFilePath = saveDir + File.separator + fileName;

            // opens an output stream to save into file
            FileOutputStream outputStream = new FileOutputStream(saveFilePath);

            int bytesRead = -1;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.close();
            inputStream.close();

            System.out.println("File downloaded");
        } else {
            System.out.println("No file to download. Server replied HTTP code: " + responseCode);
        }
        httpConn.disconnect();
    }

    /**
     * Downloads the image from the given URL using an {@link AsyncTask}. If a download
     * for the given URL is already in progress this method returns immediately.
     *
     * @param imageUrl        the URL to get the image from
     * @param displayProgress if <b>true</b>, the {@link OnImageLoaderListener#onProgressChange(int)}
     *                        callback will be triggered to notify the caller of the download progress
     */
    public void download(@NonNull final String imageUrl, final int pos, final boolean displayProgress) {
        if (urlsInProgress.contains(imageUrl)) {
            Log.w(TAG, "a download for this url is already running, " +
                    "no further download will be started");
            return;
        }
        new AsyncTask<Void, Integer, Bitmap>() {

            private ImageError error;

            @Override
            protected void onPreExecute() {
                posImage = pos;
                urlsInProgress.add(imageUrl);
                Log.d(TAG, "starting download");
            }

            @Override
            protected void onCancelled() {
                urlsInProgress.remove(imageUrl);
                imageLoaderListener.onError(error);
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                imageLoaderListener.onProgressChange(values[0]);
            }

            @Override
            protected Bitmap doInBackground(Void... params) {
                Bitmap bitmap = null;
                HttpURLConnection connection = null;
                InputStream is = null;
                ByteArrayOutputStream out = null;
                try {
                    connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                    if (displayProgress) {
                        connection.connect();
                        final int length = connection.getContentLength();
                        if (length <= 0) {
                            error = new ImageError("Invalid content length. The URL is probably not pointing to a file")
                                    .setErrorCode(ImageError.ERROR_INVALID_FILE);
                            this.cancel(true);
                        }
                        is = new BufferedInputStream(connection.getInputStream(), 8192);
                        out = new ByteArrayOutputStream();
                        byte bytes[] = new byte[8192];
                        int count;
                        long read = 0;
                        while ((count = is.read(bytes)) != -1) {
                            read += count;
                            out.write(bytes, 0, count);
                            publishProgress((int) ((read * 100) / length));
                        }
                        bitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size());
                    } else {
                        is = connection.getInputStream();
                        bitmap = BitmapFactory.decodeStream(is);
                    }
                } catch (Throwable e) {
                    if (!this.isCancelled()) {
                        error = new ImageError(e).setErrorCode(ImageError.ERROR_GENERAL_EXCEPTION);
                        this.cancel(true);
                    }
                } finally {
                    try {
                        if (connection != null)
                            connection.disconnect();
                        if (out != null) {
                            out.flush();
                            out.close();
                        }
                        if (is != null)
                            is.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return bitmap;
            }

            @Override
            protected void onPostExecute(Bitmap result) {
                Uri imageUri = Uri.parse(imageUrl);
                String fileName = imageUri.getLastPathSegment();
                if (result == null) {
                    Log.e(TAG, "factory returned a null result");
                    imageLoaderListener.onError(new ImageError("downloaded file could not be decoded as bitmap")
                            .setErrorCode(ImageError.ERROR_DECODE_FAILED));
//                    imageLoaderListener.onComplete(result, fileName, pos);
                } else {
                    Log.d(TAG, "download complete, " + result.getByteCount() +
                            " bytes transferred");
                    imageLoaderListener.onComplete(result, fileName, pos);
//                    saveImageToStorage(result, fileName);
                }
                urlsInProgress.remove(imageUrl);
                System.gc();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void download(@NonNull final String imageUrl, final String savePath) {
        if (urlsInProgress.contains(imageUrl)) {
            Log.w(TAG, "a download for this url is already running, " +
                    "no further download will be started");
            return;
        }
        new AsyncTask<Void, Integer, String>() {

            private ImageError error;

            @Override
            protected void onPreExecute() {
                urlsInProgress.add(imageUrl);
                Log.d(TAG, "starting download");
            }

            @Override
            protected void onCancelled() {
                urlsInProgress.remove(imageUrl);
                imageLoaderListener.onError(error);
            }

            @Override
            protected String doInBackground(Void... params) {
                HttpURLConnection connection = null;
                InputStream is = null;
                FileOutputStream outputStream = null;
                try {
                    connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                    is = connection.getInputStream();
                    // opens an output stream to save into file
                    outputStream = new FileOutputStream(savePath);
                    int bytesRead = -1;
                    byte[] buffer = new byte[BUFFER_SIZE];
                    while ((bytesRead = is.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                } catch (Throwable e) {
                    if (!this.isCancelled()) {
                        error = new ImageError(e).setErrorCode(ImageError.ERROR_GENERAL_EXCEPTION);
                        this.cancel(true);
                    }
                } finally {
                    try {
                        if (connection != null)
                            connection.disconnect();
                        if (outputStream != null)
                            outputStream.close();
                        if (is != null)
                            is.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return savePath;
            }

            @Override
            protected void onPostExecute(String result) {
                Uri imageUri = Uri.parse(imageUrl);
                String fileName = imageUri.getLastPathSegment();
                if (result == null) {
                    Log.e(TAG, "factory returned a null result");
                    imageLoaderListener.onError(new ImageError("downloaded file could not be decoded as bitmap")
                            .setErrorCode(ImageError.ERROR_DECODE_FAILED));
                } else {
                    Log.d(TAG, "download complete, " + result);
                    imageLoaderListener.onComplete(result, fileName);
//                    saveImageToStorage(result, fileName);
                }
                urlsInProgress.remove(imageUrl);
                System.gc();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }



    /**
     * Interface definition for callbacks to be invoked when
     * the image save procedure status changes
     */
    public interface OnBitmapSaveListener {
        /**
         * Invoked to notify that the image has been
         * successfully saved
         */
        void onBitmapSaved();

        /**
         * Invoked if an error occurs while saving the image
         *
         * @param error the occurred error
         */
        void onBitmapSaveError(ImageError error);
    }

    /**
     * Tries to write the given Bitmap to device's storage using an {@link AsyncTask}.
     * This method handles common errors and will provide an error message via the
     * {@link OnBitmapSaveListener#onBitmapSaveError(ImageError)} callback in case anything
     * goes wrong.
     *
     * @param context           the actual context
     * @param imageUrl          the actual image url adress
     * @param downloadSubfolder name you want to give to the subfolder in wich you save the
     *                          image.
     */
    public static void writeToDisk(Context context, @NonNull String imageUrl, @NonNull String downloadSubfolder) {
        Uri imageUri = Uri.parse(imageUrl);
        String fileName = imageUri.getLastPathSegment();
        String downloadSubpath = downloadSubfolder + fileName;

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(imageUri);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDescription(imageUrl);
        request.allowScanningByMediaScanner();
        request.setDestinationUri(getDownloadDestination(downloadSubpath));

        downloadManager.enqueue(request);

    }

    @NonNull
    private static Uri getDownloadDestination(String downloadSubpath) {
        File picturesFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File destinationFile = new File(picturesFolder, downloadSubpath);
        destinationFile.mkdirs();
        return Uri.fromFile(destinationFile);
    }

    /**
     * Reads the given file as Bitmap. This is a blocking operation running
     * on the main thread - avoid using it for large images.
     *
     * @param imageFile the file to read
     * @return the Bitmap read from the file or null if the read fails
     * @since 1.1
     */
    public static Bitmap readFromDisk(@NonNull File imageFile) {
        if (!imageFile.exists() || imageFile.isDirectory()) return null;
        return BitmapFactory.decodeFile(imageFile.getAbsolutePath());
    }

    /**
     * Interface definition for callbacks to be invoked
     * after the image read operation finishes
     *
     * @since 1.1
     */
    public interface OnImageReadListener {
        void onImageRead(Bitmap bitmap);

        void onReadFailed();
    }

    /**
     * Reads the given file as Bitmap in the background. The appropriate callback
     * of the provided <i>OnImageReadListener</i> will be triggered upon completion.
     *
     * @param imageFile the file to read
     * @param listener  the listener to notify the caller when the
     *                  image read operation finishes
     * @since 1.1
     */
    public static void readFromDiskAsync(@NonNull File imageFile, @NonNull final OnImageReadListener listener) {
        new AsyncTask<String, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(String... params) {
                return BitmapFactory.decodeFile(params[0]);
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap != null)
                    listener.onImageRead(bitmap);
                else
                    listener.onReadFailed();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, imageFile.getAbsolutePath());
    }


    /**
     * Represents an error that has occurred while
     * downloading image or writing it to disk. Since
     * this class extends {@code Throwable}, you may get the
     * stack trace from an {@code ImageError} object
     */
    public static final class ImageError extends Throwable {

        private int errorCode;
        /**
         * An exception was thrown during an operation.
         * Check the error message for details.
         */
        public static final int ERROR_GENERAL_EXCEPTION = -1;
        /**
         * The URL does not point to a valid file
         */
        public static final int ERROR_INVALID_FILE = 0;
        /**
         * The downloaded file could not be decoded as bitmap
         */
        public static final int ERROR_DECODE_FAILED = 1;
        /**
         * File already exists on disk and shouldOverwrite == false
         */
        public static final int ERROR_FILE_EXISTS = 2;
        /**
         * Could not complete a file operation, most likely due to permission denial
         */
        public static final int ERROR_PERMISSION_DENIED = 3;
        /**
         * The target file is a directory
         */
        public static final int ERROR_IS_DIRECTORY = 4;


        public ImageError(@NonNull String message) {
            super(message);
        }

        public ImageError(@NonNull Throwable error) {
            super(error.getMessage(), error.getCause());
            this.setStackTrace(error.getStackTrace());
        }

        /**
         * @param code the code for the occurred error
         * @return the same ImageError object
         */
        public ImageError setErrorCode(int code) {
            this.errorCode = code;
            return this;
        }

        /**
         * @return the error code that was previously set
         * by {@link #setErrorCode(int)}
         */
        public int getErrorCode() {
            return errorCode;
        }
    }
}
