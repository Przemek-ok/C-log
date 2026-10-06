package pl.sq3tom.clog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String TAG = "C-log";
    private static final String LATEST_API = "https://api.github.com/repos/Przemek-ok/C-log/releases/latest";
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            settings.setAlgorithmicDarkeningAllowed(false);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            settings.setForceDark(WebSettings.FORCE_DARK_OFF);
        }

        webView.addJavascriptInterface(new UpdateBridge(), "AndroidUpdater");
        WebView.setWebContentsDebuggingEnabled(true);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "C-log page loaded: " + url);
                view.evaluateJavascript(
                        "(function(){document.documentElement.setAttribute('data-android','ok');" +
                        "if(window.renderHome){window.renderHome();}})()", null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                Log.e(TAG, "JS: " + message.message() + " @" + message.lineNumber() + " " + message.sourceId());
                return true;
            }
        });

        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    private class UpdateBridge {
        @JavascriptInterface
        public void checkForUpdate() {
            new Thread(() -> checkUpdate()).start();
        }
    }

    private void checkUpdate() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(LATEST_API);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "C-log-Android-Updater");

            if (connection.getResponseCode() != 200) {
                showMessage("Aktualizacja", "Nie udało się sprawdzić aktualizacji. Kod: " + connection.getResponseCode());
                return;
            }

            String json = readAll(connection.getInputStream());
            JSONObject release = new JSONObject(json);
            String tag = release.optString("tag_name", "");
            int latestCode = parseVersionCode(tag);
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long currentCode = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;

            JSONArray assets = release.optJSONArray("assets");
            String downloadUrl = null;
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if ("C-log.apk".equals(asset.optString("name"))) {
                        downloadUrl = asset.optString("browser_download_url", null);
                        break;
                    }
                }
            }

            final String assetUrl = downloadUrl;
            if (latestCode <= currentCode) {
                showMessage("Aktualizacja C-log", "Masz najnowszą wersję (" + info.versionName + ").");
            } else if (assetUrl == null || assetUrl.isEmpty()) {
                showMessage("Aktualizacja C-log", "Jest nowa wersja " + tag + ", ale GitHub nie udostępnia pliku C-log.apk.");
            } else {
                showUpdateDialog(tag, assetUrl);
            }
        } catch (Exception e) {
            Log.e(TAG, "Update check failed", e);
            showMessage("Aktualizacja", "Nie udało się połączyć z GitHub. Sprawdź internet.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private int parseVersionCode(String tag) {
        try {
            String digits = tag.replaceAll("[^0-9]", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (Exception e) {
            return 0;
        }
    }

    private void showUpdateDialog(String tag, String url) {
        runOnUiThread(() -> new AlertDialog.Builder(this)
                .setTitle("Nowa wersja C-log")
                .setMessage("Dostępna jest wersja " + tag + ". Pobierz ją bezpośrednio z GitHub?")
                .setNegativeButton("Później", null)
                .setPositiveButton("POBIERZ", (d, w) -> downloadAndInstall(url, tag))
                .show());
    }

    private void downloadAndInstall(String url, String tag) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle("Zezwól na aktualizację")
                    .setMessage("Android wymaga zgody na instalowanie aktualizacji pobranych przez C-log. Włącz zgodę dla C-log, wróć do aplikacji i kliknij aktualizację ponownie.")
                    .setNegativeButton("ANULUJ", null)
                    .setPositiveButton("USTAWIENIA", (d, w) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    }).show();
            return;
        }

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                File dir = new File(getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("Nie można utworzyć katalogu aktualizacji");
                File apk = new File(dir, "C-log.apk");
                URL u = new URL(url);
                connection = (HttpURLConnection) u.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "C-log-Android-Updater");
                if (connection.getResponseCode() != 200) throw new Exception("HTTP " + connection.getResponseCode());

                try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(apk)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                }
                installApk(apk);
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                showMessage("Aktualizacja", "Nie udało się pobrać APK z GitHub.");
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void installApk(File apk) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        runOnUiThread(() -> startActivity(intent));
    }

    private String readAll(InputStream input) throws Exception {
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = input.read(buffer)) != -1) sb.append(new String(buffer, 0, n, java.nio.charset.StandardCharsets.UTF_8));
        return sb.toString();
    }

    private void showMessage(String title, String message) {
        runOnUiThread(() -> new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show());
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
