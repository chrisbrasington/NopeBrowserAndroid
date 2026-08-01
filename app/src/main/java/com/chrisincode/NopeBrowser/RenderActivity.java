package com.chrisincode.NopeBrowser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.util.Locale;

/**
 * Renders exactly one URL — the one handed to us by another app — and refuses
 * everything else.
 *
 * <p>There is no history and no tabs. The only way to get a page on screen is for
 * something else to fire an ACTION_VIEW intent at us. Once that page is up, it is a
 * dead end: links do not work.
 *
 * <p>The exception is the whitelist. On a page whose host is on it, an address bar
 * appears and links work — that is a set of domains the owner of the phone has
 * decided they trust themselves with. The moment a navigation leaves the whitelist
 * the address bar goes away and the dead end is back, so wandering off costs you
 * one page and ends there.
 *
 * <p>The app does appear in the app list, because an invisible app is its own kind
 * of confusing. Opening it that way shows a notice, counts down, and closes itself.
 * The notice lists the whitelisted domains, and that list is the only thing on the
 * screen that goes anywhere — there is still no field to type a URL into.
 */
public final class RenderActivity extends Activity {

    /** Schemes a page is allowed to pull subresources from. */
    private static final String[] SUBRESOURCE_SCHEMES = {"http", "https", "data", "blob", "about"};

    /** Blocked navigation attempts allowed per page before the app closes itself. */
    private static final int REFUSAL_LIMIT = 2;

    /** How long the notice screen stays up before the app closes itself. */
    private static final int NOTICE_SECONDS = 5;

    private static final long ONE_SECOND_MS = 1000L;

    /**
     * Long enough for the parting toast to be read. The activity is already gone by
     * then; this delay only holds the process open so the toast survives.
     */
    private static final long QUIT_DELAY_MS = 2000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WebView webView;
    private View browser;
    private View addressBar;
    private EditText address;
    private View notice;
    private View whitelistTitle;
    private ViewGroup whitelistLinks;
    private TextView countdown;

    private int secondsLeft;

    /** Counts the notice screen down, then closes the app. */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (secondsLeft <= 0) {
                toast(R.string.notice_closing);
                quit();
                return;
            }
            countdown.setText(getString(R.string.notice_countdown, secondsLeft));
            secondsLeft--;
            handler.postDelayed(this, ONE_SECOND_MS);
        }
    };

    /** Domains from res/values/blocklist.xml. Subdomains of each are included. */
    private String[] blockedDomains;

    /**
     * Domains from res/values/whitelist.xml, or from $NOPE_WHITELIST at build time.
     * Subdomains of each are included. Empty by default, which leaves the app exactly
     * as it was before whitelisting existed.
     */
    private String[] whitelistedDomains;

    /**
     * True while the page on screen is on the whitelist. Everything the address bar
     * and {@link DeadEndClient} do differently hangs off this one flag, and
     * {@link #syncChrome} is the only thing that sets it — from the URL the WebView
     * actually started loading, so a redirect cannot leave it stale.
     */
    private boolean onWhitelistedPage;

    /**
     * Blocked navigation attempts on the page currently loaded. Reset by every
     * {@link #render}, so the count is per page rather than per session — and a fresh
     * launch is a fresh process, which starts it at zero anyway.
     */
    private int refusals;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_render);

        blockedDomains = getResources().getStringArray(R.array.blocked_domains);
        whitelistedDomains = getResources().getStringArray(R.array.whitelisted_domains);

        notice = findViewById(R.id.notice);
        whitelistTitle = findViewById(R.id.whitelist_title);
        whitelistLinks = findViewById(R.id.whitelist_links);
        countdown = findViewById(R.id.countdown);
        browser = findViewById(R.id.browser);
        addressBar = findViewById(R.id.address_bar);
        address = findViewById(R.id.address);
        address.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                go();
                return true;
            }
            return false;
        });

        webView = findViewById(R.id.webview);
        harden(webView);
        webView.setWebViewClient(new DeadEndClient());
        webView.setWebChromeClient(new SilentChrome());
        webView.setDownloadListener(
                (url, agent, disposition, mime, length) -> toast(R.string.blocked_download));

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onDestroy() {
        // Only the countdown — the pending kill from quit() posts to this same handler
        // and has to outlive the activity.
        handler.removeCallbacks(tick);

        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void handleIntent(Intent intent) {
        Uri target = intent == null ? null : intent.getData();

        if (isWebUrl(target)) {
            if (isBlocked(target)) {
                nope();
                return;
            }
            render(target);
        } else if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            // Something sent us a link we will not touch: a custom app scheme, a
            // mailto:, a malformed URL.
            toast(R.string.blocked_scheme);
            finish();
        } else {
            // Launched from the app list. There is no URL and no way to supply one.
            showNotice();
        }
    }

    /**
     * Loads the URL directly. Going through {@link WebView#loadUrl} here deliberately
     * bypasses {@link DeadEndClient}: this one URL is the whole point, everything
     * after it is not.
     */
    private void render(Uri target) {
        // A link arriving while the notice is up cancels the countdown: it came from
        // somewhere, so there is something to show.
        handler.removeCallbacks(tick);

        refusals = 0;
        notice.setVisibility(View.GONE);
        browser.setVisibility(View.VISIBLE);

        // Also done in onPageStarted; doing it here too means the bar does not sit
        // there for the length of a page load after we have already left the
        // whitelist, and does not flash in on the way to a page that never was.
        syncChrome(target.toString());
        webView.loadUrl(target.toString());
    }

    private void showNotice() {
        browser.setVisibility(View.GONE);
        notice.setVisibility(View.VISIBLE);
        listWhitelist();

        secondsLeft = NOTICE_SECONDS;
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    /**
     * Puts the whitelisted domains on the notice screen as tappable rows. They are
     * the only thing here worth going to, so this is the one way into the app that
     * does not start with a link from somewhere else.
     *
     * <p>The countdown is not paused for them. Ignore the list and the app still
     * closes on schedule; tapping one cancels it by way of {@link #render}.
     */
    private void listWhitelist() {
        whitelistLinks.removeAllViews();

        for (String entry : whitelistedDomains) {
            String domain = entry.trim();
            if (domain.isEmpty()) {
                continue;
            }

            Uri target = Uri.parse("https://" + domain);
            if (isBlocked(target)) {
                // In both files. The blocklist wins, so do not offer it.
                continue;
            }

            TextView link = (TextView) getLayoutInflater()
                    .inflate(R.layout.whitelist_link, whitelistLinks, false);
            link.setText(domain);
            link.setOnClickListener(view -> render(target));
            whitelistLinks.addView(link);
        }

        int visibility = whitelistLinks.getChildCount() > 0 ? View.VISIBLE : View.GONE;
        whitelistTitle.setVisibility(visibility);
        whitelistLinks.setVisibility(visibility);
    }

    /**
     * Shows or hides the address bar to match the page being loaded, and records
     * which side of the whitelist we are on.
     */
    private void syncChrome(String url) {
        onWhitelistedPage = matches(Uri.parse(url), whitelistedDomains);

        addressBar.setVisibility(onWhitelistedPage ? View.VISIBLE : View.GONE);
        if (onWhitelistedPage) {
            // Not while it has focus — that would rewrite what is being typed.
            if (!address.hasFocus()) {
                address.setText(url);
            }
        } else {
            address.setText("");
            address.clearFocus();
            hideKeyboard();
        }
    }

    /** Follows whatever is in the address bar. */
    private void go() {
        String typed = address.getText().toString().trim();
        if (typed.isEmpty()) {
            return;
        }

        Uri target = Uri.parse(typed);
        if (target.getScheme() == null) {
            // "example.com/page" is what people type. Assume the good scheme.
            target = Uri.parse("https://" + typed);
        }

        if (!isWebUrl(target) || target.getHost() == null) {
            toast(R.string.blocked_scheme);
            return;
        }
        if (isBlocked(target)) {
            nope();
            return;
        }

        address.clearFocus();
        hideKeyboard();

        // Off-whitelist is allowed and deliberate: it renders once and dead-ends,
        // same as a link tapped on a whitelisted page.
        render(target);
    }

    private void hideKeyboard() {
        InputMethodManager keyboard = getSystemService(InputMethodManager.class);
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(address.getWindowToken(), 0);
        }
    }

    @Override
    public void onBackPressed() {
        // History only exists inside the whitelist. On a dead-end page back is what
        // it always was: the way out of the app.
        if (onWhitelistedPage && webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    private boolean isBlocked(Uri uri) {
        return matches(uri, blockedDomains);
    }

    /**
     * True if the URI's host is one of the domains or any subdomain of one. Matching
     * on "." + domain is what makes old.reddit.com fall under reddit.com without also
     * catching something like notreddit.com.
     */
    private static boolean matches(Uri uri, String[] domains) {
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.US);

        for (String entry : domains) {
            String domain = entry.trim().toLowerCase(Locale.US);
            if (domain.isEmpty()) {
                continue;
            }
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    /** Says so, then gets out of the way. Nothing is rendered. */
    private void nope() {
        toast(R.string.blocked_domain);
        finish();
    }

    /**
     * Refuses a navigation attempt. The first one on a page is a warning; the second
     * closes the app. Tapping links on a dead-end page is the beginning of browsing,
     * and this is the cheapest way to end it.
     */
    private void refuseNavigation() {
        refusals++;
        if (refusals >= REFUSAL_LIMIT) {
            toast(R.string.blocked_navigation_final);
            quit();
            return;
        }
        toast(R.string.blocked_navigation);
    }

    /**
     * Drops the task from recents, then ends the process once the toast has had time
     * to be read — cookies, page state and the WebView renderer all go with it.
     */
    private void quit() {
        finishAndRemoveTask();
        handler.postDelayed(() -> Process.killProcess(Process.myPid()), QUIT_DELAY_MS);
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
     * Lets the first page in and nothing else out — unless the page is whitelisted,
     * in which case it lets everything through and hands the next page whatever the
     * whitelist says about it.
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
                //
                // Re-checked against the blocklist because a shortener is a redirect:
                // without this, a t.co link lands on a blocked domain anyway.
                if (isBlocked(request.getUrl())) {
                    nope();
                    return true;
                }
                return false;
            }

            if (onWhitelistedPage) {
                // Browsing, for as long as it stays on the whitelist. A link that
                // leaves it is still followed — once — and onPageStarted takes the
                // address bar away on arrival, so the page it lands on is a dead end.
                if (isBlocked(request.getUrl())) {
                    nope();
                    return true;
                }
                return false;
            }

            // A tapped link, a submitted form, a script assigning location. No.
            refuseNavigation();
            return true;
        }

        /**
         * Fires for every page that actually loads, however it got here: the launch
         * intent, a link inside the whitelist, a redirect, the address bar, back. That
         * makes it the one honest place to decide whether the page on screen is
         * whitelisted.
         */
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            // A page that loaded is a fresh start; the two strikes are per page.
            refusals = 0;
            syncChrome(url);
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
