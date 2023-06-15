package com.supertavor.puninstaller2;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private static final String FILE_NAME = "puni.apk";
    private static final String DOWNLOAD_PAGE_URL = "https://yokai-watch-puni-puni.en.uptodown.com/android/download";

    private Button downloadButton;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        downloadButton = findViewById(R.id.downloadButton);
        progressBar = findViewById(R.id.progressBar);

        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDownload();
            }
        });

        downloadButton.setEnabled(true);
    }

    private void startDownload() {
        new DownloadAndInstallTask().execute();
    }

    public String getFilePathFromUri(Uri uri) {
        String filePath = null;
        if (uri.getScheme().equals("content")) {
            ContentResolver contentResolver = getContentResolver();
            Cursor cursor = null;
            try {
                cursor = contentResolver.query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                    filePath = cursor.getString(columnIndex);
                }
            } catch (Exception e) {
                Log.e("TAG", "Error getting file path from URI: " + e.getMessage());
            } finally {
                if (cursor != null)
                    cursor.close();
            }
        } else if (uri.getScheme().equals("file")) {
            filePath = uri.getPath();
        }
        return filePath;
    }

    private class DownloadAndInstallTask extends AsyncTask<Void, Integer, Boolean> {

        private String fileUrl;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            downloadButton.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                // Scrape the download link from the website
                Document document = Jsoup.connect(DOWNLOAD_PAGE_URL).get();
                Element buttonGroup = document.getElementsByClass("button-group").first();
                Elements buttons = buttonGroup.getElementsByTag("button");
                for (Element button : buttons) {
                    String buttonId = button.attr("id");
                    if (buttonId.equals("detail-download-button")) {
                        fileUrl = button.attr("data-url");
                        break;
                    }
                }

                if (fileUrl != null && !fileUrl.isEmpty()) {
                    File privateDir = getExternalFilesDir(null);
                    File outputFile = new File(privateDir, FILE_NAME);
                    if (outputFile.exists()) {
                        outputFile.delete();
                    }
                    outputFile.createNewFile();

                    FileOutputStream outputStream = new FileOutputStream(outputFile);
                    URL url = new URL(fileUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.connect();

                    InputStream inputStream = connection.getInputStream();
                    int length = connection.getContentLength();
                    byte[] buffer = new byte[1024];
                    int i = 0;
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        if (i % 10000 == 0) {
                            int progress = (int) (i * 100.0 / length);
                            publishProgress(progress);
                            System.out.println("downloading... " + i + "/" + length);
                        }
                        i++;
                    }

                    outputStream.close();
                    inputStream.close();

                    return true;
                } else {
                    return false;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            int progress = values[0];
            progressBar.setProgress(progress);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            downloadButton.setEnabled(true);
            progressBar.setVisibility(View.GONE);

            if (result) {
                Toast.makeText(MainActivity.this, "Download completed.", Toast.LENGTH_SHORT).show();
                // Call the method to install the downloaded APK file here
                installApk(MainActivity.this);
            } else {
                Toast.makeText(MainActivity.this, "Download failed.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void installApk(Context context) {
        String apkFileName = "puni.apk";
        File apkFile = new File(context.getExternalFilesDir(null), apkFileName);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean hasInstallPermission = context.getPackageManager().canRequestPackageInstalls();
            if (!hasInstallPermission) {
                // Open the permission request dialog for unknown sources
                Uri packageUri = Uri.fromParts("package", context.getPackageName(), null);
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri);
                context.startActivity(intent);
                return;
            }
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apkFile);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
    }
}
