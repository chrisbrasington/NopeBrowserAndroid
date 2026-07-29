package com.chrisincode.render;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Message;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.util.Locale;

/**
 * Renders exactly one URL — the one handed to us by another app — and refuses
 * everything else.
 *
 * <p>There is no address bar, no history, no tabs, no launcher entry. The only
 * way to get a page on screen is for something else to fire an ACTION_VIEW
 * intent at us. Once that page is up, it is a dead end: links do not work.
 */
public final class RenderActivity extends Activity {

    /** Schemes a page is allowed to pull subresources from. */
    private static final String[] SUBRESOURCE_SCHEMES = {"http", "https", "data", "blob", "about"};

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_render);

        webView = findViewById(R.id.webview);
        harden(webView);
        webView.setWebViewClient(new DeadEndClient());
        webView.setWebChromeClient(new SilentChrome());
        webView.setDownloadListener(
                (url, agent, disposition, mime, length) -> toast(R.string.blocked_download));

        render(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        render(intent);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    /**
     * Loads the intent's URL directly. Going through {@link WebView#loadUrl} here
     * deliberately bypasses {@link DeadEndClient}: this one URL is the whole point,
     * everything after it is not.
     */
    private void render(Intent intent) {
        Uri target = intent == null ? null : intent.getData();
        if (!isWebUrl(target)) {
            toast(R.string.blocked_scheme);
            finish();
            return;
        }
        webView.loadUrl(target.toString());
    }

    private static boolean isWebUrl(Uri uri) {
        if (uri == null) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }
        scheme = scheme.toLowerCase(Locale.US);
        return scheme.equals("http") || scheme.equals("https");
    }

    private void toast(int message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Turns off everything a page does not need in order to be read. There is no
     * {@code addJavascriptInterface} call in this project, and adding one would
     * hand the page a bridge into the app.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private static void harden(WebView view) {
        WebView.setWebContentsDebuggingEnabled(false);

        WebSettings settings = view.getSettings();

        // Most of the web is blank without these two.
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        // Local storage on the device is none of a remote page's business.
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);

        // No popups, no second window to escape into.
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        settings.setGeolocationEnabled(false);
        settings.setSaveFormData(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);

        view.setHorizontalScrollBarEnabled(false);
    }

    /**
     * Lets the first page in and nothing else out.
     */
    private final class DeadEndClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) {
                // Images, stylesheets, XHR. Not navigation.
                return false;
            }

            if (request.isRedirect()) {
                // A 3xx from the server is the site answering the request we already
                // made, not the user going somewhere new. http->https upgrades,
                // captive portals and login walls all need this to work.
                return false;
            }

            // A tapped link, a submitted form, a script assigning location. No.
            toast(R.string.blocked_navigation);
            return true;
        }

        /**
         * The page cannot reach {@code file://}, {@code content://}, {@code intent://}
         * or any app's private scheme — not even for a subresource. This is what
         * closes the intent-redirect hole: we never parse or launch a URL as an
         * Intent, anywhere.
         */
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String scheme = request.getUrl().getScheme();
            if (scheme != null) {
                scheme = scheme.toLowerCase(Locale.US);
                for (String allowed : SUBRESOURCE_SCHEMES) {
                    if (scheme.equals(allowed)) {
                        return null; // Normal handling.
                    }
                }
            }
            return new WebResourceResponse(
                    "text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        }

        /**
         * A bad certificate ends the page. Never call {@code handler.proceed()} here —
         * that is the single most common way an Android WebView gets itself
         * man-in-the-middled.
         */
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            toast(R.string.blocked_certificate);
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            // The renderer died. Close cleanly instead of taking the process with it.
            finish();
            return true;
        }
    }

    /**
     * Denies every capability a page can ask the user for, without asking the user.
     */
    private static final class SilentChrome extends WebChromeClient {

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            request.deny(); // Camera, mic, MIDI, protected media.
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(
                String origin, GeolocationPermissions.Callback callback) {
            callback.invoke(origin, false, false);
        }

        @Override
        public boolean onCreateWindow(
                WebView view, boolean dialog, boolean userGesture, Message resultMsg) {
            return false;
        }

        // Modal dialogs are a way to trap someone on a page. Dismiss all of them.

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            result.cancel();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
            result.cancel();
            return true;
        }

        @Override
        public boolean onJsPrompt(
                WebView view, String url, String message, String value, JsPromptResult result) {
            result.cancel();
            return true;
        }

        @Override
        public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
            result.confirm(); // "Are you sure you want to leave?" — yes.
            return true;
        }
    }
}
