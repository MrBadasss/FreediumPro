package app.freediumshare;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * Launcher screen: the genuine medium.com (your logged-in feed) in a full-screen WebView, with no
 * app chrome of our own (Medium has its own header). A small draggable button shows an unlock icon
 * on paywalled articles — one tap reads it free via Freedium; long-press opens the app menu.
 */
public class HomeActivity extends Activity {

    private static final String HOME = "https://medium.com/";

    // Article + paywall detection, ported from the user's userscript: the al:android:url meta marks
    // a Medium article; "Member-only" + an upgrade gate marks one this account can't read.
    private static final String PAYWALL_JS =
            "(function(){try{"
            + "var a=document.querySelector('meta[property=\"al:android:url\"][content^=\"medium://p/\"]');"
            + "if(!a)return 'not_article';"
            + "var b=document.body?document.body.innerText:'';"
            + "if(b.indexOf('Member-only')<0)return 'open';"
            + "return /(Upgrade to read|Become a member|Sign up to read this story|to read the rest|"
            + "Get unlimited access)/i.test(b)?'locked':'open';"
            + "}catch(e){return 'open';}})()";

    // Persistently hides Medium's "Open in app" banner (it would bounce to the Play Store).
    // Only touches the matching link/button and a SMALL wrapper — never a large container,
    // so it can't blank the article.
    private static final String HIDE_OPENAPP_JS =
            "(function(){try{if(window.__fzMed)return;window.__fzMed=1;function k(){try{"
            + "var e=document.querySelectorAll('a,button');for(var i=0;i<e.length;i++){"
            + "var el=e[i],t=(el.textContent||'').trim().toLowerCase();"
            + "if(t==='open in app'||t==='open in the app'||t==='open in the medium app'){"
            + "el.style.setProperty('display','none','important');var p=el.parentElement;"
            + "if(p&&p!==document.body&&p.getBoundingClientRect().height<=72)"
            + "p.style.setProperty('display','none','important');}}}catch(e){}}"
            + "k();new MutationObserver(k).observe(document.documentElement,{childList:true,subtree:true});"
            + "}catch(e){}})();";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable urlWatch = new Runnable() {
        @Override
        public void run() {
            checkUrl();
            ui.postDelayed(this, 700);
        }
    };

    private WebView webView;
    private ProgressBar progress;
    private ImageButton fab;
    private String routedUrl;
    private String lastUrl;
    private boolean onArticle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        progress = findViewById(R.id.progress);
        webView = findViewById(R.id.web);
        fab = findViewById(R.id.fab);
        fab.setTranslationX(Prefs.fabX(this));
        fab.setTranslationY(Prefs.fabY(this));
        fab.setOnTouchListener(new DragClick());
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

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
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
                android.net.Uri uri = request.getUrl();
                String host = uri.getHost();
                // Never leave for the Play Store or an app deep link (the "Open in app" trap).
                if (isStoreOrAppLink(uri.getScheme(), host)) return true;
                if (FreediumLinks.isMediumHost(host) || isAuthHost(host)) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE));
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                onArticle = false;
                fab.setImageResource(R.drawable.ic_more);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(HIDE_OPENAPP_JS, null);
                checkUrl();
            }
        });
    }

    /** The unlock button only shows on article pages; follow the live URL (Medium is an SPA). */
    private void checkUrl() {
        if (webView == null) return;
        String url = webView.getUrl();
        onArticle = FreediumLinks.looksLikeArticle(url);
        // Unlock icon on an article (tap = read free), a menu dot otherwise (tap = menu).
        fab.setImageResource(onArticle ? R.drawable.ic_lock_open : R.drawable.ic_more);
        fab.setAlpha(onArticle ? 1f : 0.55f);
        if (url == null || url.equals(lastUrl)) return;
        lastUrl = url;
        webView.evaluateJavascript(HIDE_OPENAPP_JS, null);
        if (onArticle && Prefs.autoRoute(this)) {
            maybeAutoRoute(url);
            ui.postDelayed(() -> maybeAutoRoute(url), 1500); // content may still be loading on an SPA nav
        }
    }

    private void maybeAutoRoute(String url) {
        if (webView == null || url.equals(routedUrl) || !url.equals(webView.getUrl())) return;
        webView.evaluateJavascript(PAYWALL_JS, value -> {
            if ("\"locked\"".equals(value)) {
                routedUrl = url;
                openViaFreedium(url);
            }
        });
    }

    /** Opens the current Medium article through Freedium in the reader. */
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

    private void showMenu() {
        PopupMenu menu = new PopupMenu(this, fab);
        menu.getMenuInflater().inflate(R.menu.home, menu.getMenu());
        menu.getMenu().findItem(R.id.action_read_free).setVisible(onArticle);
        menu.setOnMenuItemClickListener(item -> {
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
                return false;
            }
            return true;
        });
        menu.show();
    }

    /** Play Store links and app deep links that would bounce the user out to the Play Store. */
    private static boolean isStoreOrAppLink(String scheme, String host) {
        if (scheme != null) {
            String s = scheme.toLowerCase();
            if (s.equals("market") || s.equals("intent") || s.equals("android-app") || s.equals("medium")) {
                return true;
            }
        }
        return host != null && (host.contains("play.google.com") || host.equals("market.android.com"));
    }

    private static boolean isAuthHost(String host) {
        if (host == null) return false;
        return host.contains("google.com") || host.contains("facebook.com")
                || host.contains("apple.com") || host.contains("twitter.com")
                || host.contains("x.com") || host.contains("accounts.");
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
        ui.removeCallbacks(urlWatch);
        CookieManager.getInstance().flush();
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        ui.removeCallbacks(urlWatch);
        ui.post(urlWatch);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(urlWatch);
        webView.destroy();
        super.onDestroy();
    }

    /** Drag to move the button; a tap reads the article free, a long-press opens the menu. */
    private final class DragClick implements View.OnTouchListener {
        private float downRawX, downRawY, startTx, startTy;
        private boolean dragging;
        private final int slop = ViewConfiguration.get(HomeActivity.this).getScaledTouchSlop();
        private final Runnable longPress = () -> {
            dragging = true; // suppress the tap that would otherwise fire on UP
            showMenu();
        };

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = e.getRawX();
                    downRawY = e.getRawY();
                    startTx = v.getTranslationX();
                    startTy = v.getTranslationY();
                    dragging = false;
                    ui.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downRawX;
                    float dy = e.getRawY() - downRawY;
                    if (!dragging && Math.hypot(dx, dy) > slop) {
                        dragging = true;
                        ui.removeCallbacks(longPress);
                    }
                    if (dragging) {
                        v.setTranslationX(clampX(v, startTx + dx));
                        v.setTranslationY(clampY(v, startTy + dy));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    ui.removeCallbacks(longPress);
                    if (dragging) {
                        Prefs.setFabPos(HomeActivity.this, v.getTranslationX(), v.getTranslationY());
                    } else if (onArticle) {
                        openViaFreedium(webView.getUrl());
                    } else {
                        showMenu();
                    }
                    return true;
                default:
                    return false;
            }
        }

        // Anchored bottom-end: translation 0 is home; negative moves left/up. Keep it on screen.
        private float clampX(View v, float tx) {
            float gap = dp(8);
            float minTx = -(((View) v.getParent()).getWidth() - v.getWidth() - gap);
            if (tx > 0) return 0;
            return tx < minTx ? minTx : tx;
        }

        private float clampY(View v, float ty) {
            float gap = dp(8);
            float minTy = -(((View) v.getParent()).getHeight() - v.getHeight() - gap);
            if (ty > 0) return 0;
            return ty < minTy ? minTy : ty;
        }

        private float dp(int v) {
            return v * getResources().getDisplayMetrics().density;
        }
    }
}
