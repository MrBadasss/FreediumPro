package app.freediumshare;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * Launcher screen: the genuine medium.com in a WebView (your logged-in feed).
 * Member-only articles you can't read are routed to Freedium automatically, and a
 * floating "Read free" button on any article opens it through Freedium on demand.
 */
public class HomeActivity extends Activity {

    private static final String HOME = "https://medium.com/";

    // Best-effort paywall check: "Member-only story" badge AND an upgrade/sign-up gate
    // (members who can read the story see the badge but no gate, so they stay on Medium).
    private static final String PAYWALL_JS =
            "(function(){try{var b=document.body?document.body.innerText:'';"
            + "if(/Member-only story/i.test(b)&&/(Upgrade to read|Become a member|members? only|"
            + "Sign up to read this story|to read the rest)/i.test(b))return 'locked';"
            + "return 'open';}catch(e){return 'open';}})()";

    private WebView webView;
    private ProgressBar progress;
    private Button readFree;
    private String routedUrl; // guards against re-routing the same page

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        setTitle(R.string.my_medium);
        progress = findViewById(R.id.progress);
        webView = findViewById(R.id.web);
        readFree = findViewById(R.id.read_free);
        readFree.setOnClickListener(v -> openViaFreedium(webView.getUrl()));
        setUpWebView();
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(startUrl(getIntent()));
        }
    }

    /** Opens a Medium URL passed in (data or shared text), otherwise the Medium home feed. */
    private static String startUrl(Intent intent) {
        if (intent != null) {
            String data = intent.getDataString();
            if (data == null) data = FreediumLinks.extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT));
            if (data != null && FreediumLinks.isMediumHost(FreediumLinks.hostOf(data))) return data;
        }
        return HOME;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setUpWebView() {
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                progress.setProgress(p);
                progress.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String host = request.getUrl().getHost();
                // Medium and its sign-in providers stay in this view; anything else goes to a browser.
                if (FreediumLinks.isMediumHost(host) || isAuthHost(host)) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl())
                        .addCategory(Intent.CATEGORY_BROWSABLE));
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                readFree.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                boolean article = FreediumLinks.looksLikeArticle(url);
                readFree.setVisibility(article ? View.VISIBLE : View.GONE);
                if (article && Prefs.autoRoute(HomeActivity.this) && !url.equals(routedUrl)) {
                    view.evaluateJavascript(PAYWALL_JS, value -> {
                        if ("\"locked\"".equals(value)) {
                            routedUrl = url;
                            openViaFreedium(url);
                        }
                    });
                }
            }
        });
    }

    private static boolean isAuthHost(String host) {
        if (host == null) return false;
        return host.contains("google.com") || host.contains("facebook.com")
                || host.contains("apple.com") || host.contains("twitter.com")
                || host.contains("x.com") || host.contains("accounts.");
    }

    /** Opens the given Medium article through Freedium in the reader. */
    private void openViaFreedium(String url) {
        if (!FreediumLinks.looksLikeArticle(url)) {
            Toast.makeText(this, R.string.not_an_article, Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, ReaderActivity.class)
                .setAction(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, url));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_read_free) {
            openViaFreedium(webView.getUrl());
        } else if (id == R.id.action_reload) {
            webView.reload();
        } else if (id == R.id.action_home) {
            webView.loadUrl(HOME);
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
