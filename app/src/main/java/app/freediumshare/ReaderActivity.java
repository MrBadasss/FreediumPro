package app.freediumshare;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/** Receives a shared/opened Medium link and shows the article through Freedium. */
public class ReaderActivity extends Activity {

    private static final String STATE_ORIGINAL = "original";

    // Hides Freedium's donation drawer / overlays and keeps the page scrollable, re-applying as
    // the Svelte app re-renders. Targets shadcn/Vaul drawer slots, not the article.
    private static final String CLEAN_JS =
            "(function(){try{if(window.__fzClean)return;window.__fzClean=1;"
            + "var c='[data-slot=\"drawer-overlay\"],[data-slot=\"drawer-content\"],"
            + "[data-vaul-overlay],[vaul-overlay],[data-vaul-drawer],"
            + "section[aria-label^=\"Notifications\"]"  // Freedium "updated"/"new sources" toasts
            + "{display:none!important;visibility:hidden!important;}';"
            + "var s=document.createElement('style');s.textContent=c;"
            + "(document.head||document.documentElement).appendChild(s);"
            + "function u(){try{var b=document.body;if(!b)return;b.style.overflow='';"
            + "b.style.position='';b.style.top='';b.removeAttribute('data-vaul-drawer-wrapper');"
            + "document.documentElement.style.overflow='';}catch(e){}}u();"
            + "new MutationObserver(u).observe(document.documentElement,{childList:true,"
            + "subtree:true,attributes:true,attributeFilter:['style','data-vaul-drawer-wrapper']});"
            + "}catch(e){}})();";

    private WebView webView;
    private ProgressBar progress;
    private View errorView;
    private TextView errorText;
    private String mirror;
    private String originalUrl;
    private boolean failed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        progress = findViewById(R.id.progress);
        webView = findViewById(R.id.web);
        errorView = findViewById(R.id.error);
        errorText = findViewById(R.id.error_text);
        ((Button) findViewById(R.id.error_retry)).setOnClickListener(v -> retry());
        ((Button) findViewById(R.id.error_browser)).setOnClickListener(v -> openExternally(currentOrPlannedUrl(), false));
        mirror = Prefs.mirror(this);
        setUpWebView();

        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            originalUrl = savedInstanceState.getString(STATE_ORIGINAL);
            return;
        }

        originalUrl = FreediumLinks.extractUrl(sharedText(getIntent()));
        if (originalUrl == null) {
            Toast.makeText(this, R.string.no_link, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        load(originalUrl);
    }

    /** Resolves short links first, then loads the Freedium page. */
    private void load(String articleUrl) {
        setTitle(R.string.loading);
        hideError();
        if (FreediumLinks.needsRedirectResolution(articleUrl)) {
            progress.setVisibility(View.VISIBLE);
            LinkResolver.resolve(articleUrl, resolved -> {
                originalUrl = resolved;
                webView.loadUrl(FreediumLinks.toFreedium(mirror, resolved));
            });
        } else {
            webView.loadUrl(FreediumLinks.toFreedium(mirror, articleUrl));
        }
    }

    private static String sharedText(Intent intent) {
        if (intent == null) return null;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) return intent.getDataString();
        if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            return text == null ? null : text.toString();
        }
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (FreediumLinks.extractUrl(text) == null) text = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        return text;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setUpWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (!failed && title != null && !title.isEmpty()) {
                    setTitle(title.replaceFirst("\\s*-\\s*Freedium$", ""));
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                String url = request.getUrl().toString();
                if (FreediumLinks.isOnMirror(mirror, url)) return false;
                if (FreediumLinks.isMediumHost(request.getUrl().getHost())) {
                    // Keep Medium articles inside Freedium.
                    originalUrl = url;
                    load(url);
                    return true;
                }
                openExternally(url, false);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showError();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (FreediumLinks.isOnMirror(mirror, url)) view.evaluateJavascript(CLEAN_JS, null);
            }
        });
    }

    private void retry() {
        if (originalUrl != null) load(originalUrl);
    }

    private void showError() {
        failed = true;
        setTitle(R.string.app_name);
        progress.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        errorText.setText(getString(R.string.load_failed, mirror));
        errorView.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        failed = false;
        errorView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    /** Opens a URL in another app; non-Medium links leave the reader. */
    private void openExternally(String url, boolean chooser) {
        if (url == null) return;
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme) && !"mailto".equals(scheme)) return;
        String host = uri.getHost();
        if (host != null && (host.contains("play.google.com") || host.equals("market.android.com"))) return;
        Intent intent = new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(chooser ? Intent.createChooser(intent, null) : intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private String currentUrl() {
        String url = webView.getUrl();
        return url != null ? url : currentOrPlannedUrl();
    }

    private String currentOrPlannedUrl() {
        return originalUrl == null ? null : FreediumLinks.toFreedium(mirror, originalUrl);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.reader, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            if (failed) retry(); else webView.reload();
        } else if (id == R.id.action_share) {
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, currentUrl());
            startActivity(Intent.createChooser(send, null));
        } else if (id == R.id.action_copy) {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            cm.setPrimaryClip(ClipData.newPlainText("Freedium link", currentUrl()));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        } else if (id == R.id.action_browser) {
            openExternally(currentUrl(), false);
        } else if (id == R.id.action_original) {
            if (originalUrl != null) openExternally(originalUrl, true);
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (webView.getVisibility() == View.VISIBLE && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
        outState.putString(STATE_ORIGINAL, originalUrl);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
