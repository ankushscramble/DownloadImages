package com.downloadimages;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.AppCompatImageView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.downloadimages.utils.DownloadFile;
import com.downloadimages.utils.Utility;
import com.mikhaellopez.circularprogressbar.CircularProgressBar;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    @Bind(R.id.urlText)
    AppCompatEditText urlText;
    @Bind(R.id.imageView)
    AppCompatImageView imageView;
    @Bind(R.id.totalTime)
    TextView totalTime;
    Date startDate;
    @Bind(R.id.progressBar)
    CircularProgressBar progressBar;

    @OnClick(R.id.downloadBtn)
    void onDownloadBtnClick(View v) {
//        final DownloadFile downloadFile = new DownloadFile();
        startDate = new Date(System.currentTimeMillis());
        String url = urlText.getText().toString();
        if (!TextUtils.isEmpty(url)) {
            downloadFile(url);
//            downloadFile.downloadFile(url);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
    }

    private void downloadFile(String url) {
        final Uri imageUri = Uri.parse(url);
        String fileName = imageUri.getLastPathSegment();
        if (TextUtils.isEmpty(fileName)) {
            fileName = String.valueOf(System.currentTimeMillis()) + ".png";
        }

        final String dirPath = Environment.getExternalStorageDirectory() + "/DownloadImages/";
        File dir = new File(dirPath);
        if (!dir.exists()) {
            boolean mkdirs = dir.mkdirs();
        }
        progressBar.setVisibility(View.VISIBLE);
        final String finalFileName = fileName;
        ImageDownloader.OnImageLoaderListener onImageLoaderListener = new ImageDownloader.OnImageLoaderListener() {
            @Override
            public void onError(ImageDownloader.ImageError error) {
                if (Utility.isDebug)
                    Log.d("onError", ">>>>>" + error.getMessage());
                progressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, R.string.download_error, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onProgressChange(int percent) {
                if (Utility.isDebug)
                    Log.d("onProgressChange", ">>>>>" + percent);
                progressBar.setProgress(percent);
            }

            @Override
            public void onComplete(Bitmap result, String filename, int pos) {
                progressBar.setVisibility(View.GONE);
                if (Utility.isDebug) {
                    Log.d("onComplete", ">>>>>" + filename);
                    Log.d("pos", ">>>>>" + pos);
                }
                if (null != result) {
//                    Bitmap b = Bitmap.createScaledBitmap(result, 1000, 1000, false);//scale the bitmap
//                    imageView.setImageBitmap(b);
                    String filePath = Utility.saveImageToStorage(result, dirPath, finalFileName);
                    Toast.makeText(MainActivity.this, filePath, Toast.LENGTH_LONG).show();
//                    imageView.setImageURI(Uri.fromFile(new File(filePath)));
                    Picasso.with(MainActivity.this)
                            .load(new File(filePath))
                            .resize(1000, 1000)
                            .centerCrop()
                            .into(imageView);
                    totalTime.setText(Utility.printDifference(startDate));
                } else {
                    Toast.makeText(MainActivity.this, R.string.download_error, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onComplete(String path, String filename) {
                progressBar.setVisibility(View.GONE);
                if (Utility.isDebug) {
                    Log.d("onComplete", ">>>>>" + filename);
                    Log.d("path", ">>>>>" + path);
                }
            }
        };
        ImageDownloader imageDownloader = new ImageDownloader(onImageLoaderListener);
        imageDownloader.download(url, 0, true);
    }

}
