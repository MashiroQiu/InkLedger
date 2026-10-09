package cn.inkledger.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.graphics.Insets;
import android.widget.FrameLayout;
import android.util.AtomicFile;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/** Offline-only Android shell. No INTERNET permission or remote navigation. */
public final class MainActivity extends Activity {
    private static final String ORIGIN = "https://inkledger.local/";
    private WebView web;
    private AtomicFile store;
    private ValueCallback<Uri[]> chooser;
    private String pendingBackup;
    private float requestedMotionRate;
    private volatile String systemInsets = "{\"top\":0,\"right\":0,\"bottom\":0,\"left\":0}";
    private final Handler motionHandler = new Handler(Looper.getMainLooper());
    private final Runnable releaseMotionRate = () -> {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = 0;
        getWindow().setAttributes(params);
        requestedMotionRate = 0;
        if (Build.VERSION.SDK_INT >= 35 && web != null)
            web.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT);
    };
    private static final int IMPORT_FILE = 11, EXPORT_FILE = 12;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        configureEdgeToEdge();
        store = new AtomicFile(new File(getFilesDir(), "ledger.json"));
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            WebView.setWebContentsDebuggingEnabled(true);
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(222,222,222));
        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            updateSystemInsets(insets);
            if (Build.VERSION.SDK_INT >= 30) {
                int keyboard = insets.isVisible(WindowInsets.Type.ime())
                        ? insets.getInsets(WindowInsets.Type.ime()).bottom : 0;
                FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) web.getLayoutParams();
                if (layout.bottomMargin != keyboard) {
                    layout.bottomMargin = keyboard;
                    web.setLayoutParams(layout);
                }
            }
            return insets;
        });
        root.requestApplyInsets();
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (ORIGIN.equals(url)) publishSystemInsets();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return !req.getUrl().toString().startsWith(ORIGIN);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (!url.startsWith(ORIGIN)) return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                String path = req.getUrl().getPath();
                if (path == null || path.equals("/")) path = "/index.html";
                if (!path.matches("/(index\\.html|style\\.css|app\\.js)")) return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                try {
                    String mime = path.endsWith(".css") ? "text/css" : path.endsWith(".js") ? "application/javascript" : "text/html";
                    HashMap<String,String> headers = new HashMap<>();
                    headers.put("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'");
                    return new WebResourceResponse(mime, "UTF-8", 200, "OK", headers, getAssets().open(path.substring(1)));
                } catch (IOException error) { return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0])); }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("*/*"); intent.addCategory(Intent.CATEGORY_OPENABLE);
                try { startActivityForResult(intent, IMPORT_FILE); } catch (Exception error) { chooser.onReceiveValue(null); chooser = null; }
                return true;
            }
        });
        web.addJavascriptInterface(new StoreBridge(), "AndroidStore");
        web.loadUrl(ORIGIN);
    }
    @SuppressWarnings("deprecation")
    private void configureEdgeToEdge() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().setNavigationBarDividerColor(Color.TRANSPARENT);
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attributes);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
    }
    @SuppressWarnings("deprecation")
    private void updateSystemInsets(WindowInsets insets) {
        int top, right, bottom, left;
        if (Build.VERSION.SDK_INT >= 30) {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            top = bars.top; right = bars.right; bottom = bars.bottom; left = bars.left;
        } else {
            top = insets.getStableInsetTop(); right = insets.getStableInsetRight();
            bottom = insets.getStableInsetBottom(); left = insets.getStableInsetLeft();
            if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                top = Math.max(top, insets.getDisplayCutout().getSafeInsetTop());
                right = Math.max(right, insets.getDisplayCutout().getSafeInsetRight());
                bottom = Math.max(bottom, insets.getDisplayCutout().getSafeInsetBottom());
                left = Math.max(left, insets.getDisplayCutout().getSafeInsetLeft());
            }
        }
        float density = getResources().getDisplayMetrics().density;
        String next = "{\"top\":" + top/density + ",\"right\":" + right/density
                + ",\"bottom\":" + bottom/density + ",\"left\":" + left/density + "}";
        if (!next.equals(systemInsets)) { systemInsets = next; publishSystemInsets(); }
    }
    private void publishSystemInsets() {
        if (web != null) web.evaluateJavascript("window.applySystemInsets && window.applySystemInsets(" + systemInsets + ")", null);
    }
    private void boostMotionRate() {
        if (requestedMotionRate > 0) return;
        Display display = web.getDisplay();
        if (display == null) return;
        Display.Mode current = display.getMode();
        float rate = current.getRefreshRate();
        for (Display.Mode mode : display.getSupportedModes()) {
            // Keep the current resolution; request the fastest compatible mode.
            if (mode.getPhysicalWidth() == current.getPhysicalWidth()
                    && mode.getPhysicalHeight() == current.getPhysicalHeight())
                rate = Math.max(rate, mode.getRefreshRate());
        }
        WindowManager.LayoutParams params = getWindow().getAttributes();
        if (params.preferredRefreshRate != rate) {
            params.preferredRefreshRate = rate;
            getWindow().setAttributes(params);
        }
        if (Build.VERSION.SDK_INT >= 35 && web.getRequestedFrameRate() != rate)
            web.setRequestedFrameRate(rate);
        requestedMotionRate = rate;
        motionHandler.removeCallbacks(releaseMotionRate);
    }
    @Override protected void onResume() {
        super.onResume();
        requestedMotionRate = 0;
        web.post(this::boostMotionRate);
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        requestedMotionRate = 0;
        web.post(this::boostMotionRate);
    }
    public final class StoreBridge {
        @JavascriptInterface public String readSystemInsets() { return systemInsets; }
        @JavascriptInterface public void requestMotionRate() {
            runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) boostMotionRate(); });
        }
        @JavascriptInterface public synchronized String read() {
            try { return new String(store.readFully(), StandardCharsets.UTF_8); }
            catch (FileNotFoundException error) { return ""; }
            catch (IOException error) { return "INVALID_UNREADABLE_STORE"; }
        }
        @JavascriptInterface public synchronized boolean write(String raw) {
            if (raw == null || raw.length() > 10 * 1024 * 1024) return false;
            FileOutputStream stream = null;
            try { new JSONObject(raw); stream = store.startWrite(); stream.write(raw.getBytes(StandardCharsets.UTF_8)); store.finishWrite(stream); return true; }
            catch (Exception error) { if (stream != null) store.failWrite(stream); return false; }
        }
        @JavascriptInterface public void exportBackup(String raw) {
            runOnUiThread(() -> {
                pendingBackup = raw;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "InkLedger-backup.json");
                try { startActivityForResult(intent, EXPORT_FILE); } catch (Exception error) { Toast.makeText(MainActivity.this,"无法打开文件保存器",Toast.LENGTH_SHORT).show(); }
            });
        }
    }
    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code,result,data);
        if (code == IMPORT_FILE && chooser != null) {
            chooser.onReceiveValue(result == RESULT_OK && data != null ? new Uri[]{data.getData()} : null); chooser = null;
        }
        if (code == EXPORT_FILE && result == RESULT_OK && data != null && pendingBackup != null) {
            try (OutputStream stream = getContentResolver().openOutputStream(data.getData())) {
                if (stream == null) throw new IOException();
                stream.write(pendingBackup.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this,"备份已保存",Toast.LENGTH_SHORT).show();
            } catch (IOException error) { Toast.makeText(this,"备份保存失败",Toast.LENGTH_SHORT).show(); }
        }
        pendingBackup = null;
    }
    @Override public void onBackPressed() {
        web.evaluateJavascript("window.onNativeBack ? window.onNativeBack() : false", result -> { if (!"true".equals(result)) super.onBackPressed(); });
    }
    @Override protected void onPause() { motionHandler.removeCallbacks(releaseMotionRate); releaseMotionRate.run(); super.onPause(); }
    @Override protected void onDestroy() { motionHandler.removeCallbacksAndMessages(null); web.removeJavascriptInterface("AndroidStore"); web.destroy(); super.onDestroy(); }
}
