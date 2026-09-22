package com.sunelectrical.service;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;
import java.io.InputStream;
import java.security.MessageDigest;

public class MainActivity extends Activity {
    private WebView webView;
    private BroadcastReceiver updateReceiver;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        WebViewAssetLoader assets = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.addJavascriptInterface(new AndroidUpdater(), "AndroidUpdater");
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assets.shouldInterceptRequest(request.getUrl());
            }
        });
        setContentView(webView);
        webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html");
    }

    public class AndroidUpdater {
        @JavascriptInterface public String downloadAndInstall(String url, String version, String expectedHash) {
            if (url == null || !url.startsWith("https://")) return "{\"error\":\"Update URL must use HTTPS\"}";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
                runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))));
                return "{\"requiresPermission\":true}";
            }
            runOnUiThread(() -> startDownload(url, version, expectedHash));
            return "{\"started\":true}";
        }
    }

    private void startDownload(String url, String version, String expectedHash) {
        String safeVersion = String.valueOf(version).replaceAll("[^0-9A-Za-z._-]", "-");
        DownloadManager manager = (DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
            .setTitle("SUN Electrical Service update")
            .setDescription("Downloading version " + safeVersion)
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "SUN-Electrical-Service-" + safeVersion + ".apk");
        long id = manager.enqueue(request);
        updateReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return;
                try {
                    unregisterReceiver(this); updateReceiver = null;
                    Uri apk = manager.getUriForDownloadedFile(id);
                    if (apk == null) { Toast.makeText(context, "Update download failed", Toast.LENGTH_LONG).show(); return; }
                    if (expectedHash != null && !expectedHash.trim().isEmpty() && !sha256(apk).equalsIgnoreCase(expectedHash.trim())) {
                        manager.remove(id); Toast.makeText(context, "Update security check failed", Toast.LENGTH_LONG).show(); return;
                    }
                    Intent install = new Intent(Intent.ACTION_VIEW).setDataAndType(apk, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(install);
                } catch (Exception error) { Toast.makeText(context, "Update install failed", Toast.LENGTH_LONG).show(); }
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updateReceiver, filter, Context.RECEIVER_EXPORTED); else registerReceiver(updateReceiver, filter);
    }

    private String sha256(Uri uri) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            byte[] buffer = new byte[8192]; int count;
            while (input != null && (count = input.read(buffer)) > 0) digest.update(buffer, 0, count);
        }
        StringBuilder value = new StringBuilder();
        for (byte b : digest.digest()) value.append(String.format("%02x", b));
        return value.toString();
    }

    @Override public void onBackPressed() { if (webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() { if (updateReceiver != null) try { unregisterReceiver(updateReceiver); } catch (Exception ignored) {} if (webView != null) webView.destroy(); super.onDestroy(); }
}
