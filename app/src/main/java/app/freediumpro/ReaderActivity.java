package app.freediumpro;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shows a Medium (or other supported) article through Freedium, with reader tools. */
public class ReaderActivity extends Activity {

    private static final String STATE_ORIGINAL = "original";
    static final String ACTION_OFFLINE = "app.freediumpro.OFFLINE";
    static final String EXTRA_OFFLINE = "offline_path";

    // Captures Freedium's "Download article" (a blob download a WebView would otherwise ignore):
    // intercepts the download anchor, reads the blob, and hands it to the app to save.
    private static final String DOWNLOAD_JS =
            "(function(){try{if(window.__fzDl)return;window.__fzDl=1;"
            + "document.addEventListener('click',function(e){try{"
            + "var a=e.target.closest?e.target.closest('a[download]'):null;if(!a)return;"
            + "var h=a.href||'';if(h.indexOf('blob:')!==0)return;e.preventDefault();"
            + "var n=a.getAttribute('download')||document.title||'article';"
            + "fetch(h).then(function(r){return r.blob();}).then(function(b){"
            + "var fr=new FileReader();fr.onloadend=function(){var d=''+fr.result;var i=d.indexOf(',');"
            + "var m=d.substring(5,d.indexOf(';'))||'application/octet-stream';"
            + "AndroidDownload.save(d.substring(i+1),m,n);};fr.readAsDataURL(b);}).catch(function(){});"
            + "}catch(err){}},true);}catch(e){}})();";

    private static final String CLEAN_JS =
            "(function(){try{if(window.__fzClean)return;window.__fzClean=1;"
            + "var c='[data-slot=\"drawer-overlay\"],[data-slot=\"drawer-content\"],"
            + "[data-vaul-overlay],[vaul-overlay],[data-vaul-drawer],"
            + "section[aria-label^=\"Notifications\"]"
            + "{display:none!important;visibility:hidden!important;}"
            + "img{max-width:100%!important;}';"
            + "var s=document.createElement('style');s.textContent=c;"
            + "(document.head||document.documentElement).appendChild(s);"
            + "function u(){try{var b=document.body;if(!b)return;b.style.overflow='';"
            + "b.style.position='';b.style.top='';b.style.transform='';b.style.transformOrigin='';"
            + "b.style.borderRadius='';b.removeAttribute('data-vaul-drawer-wrapper');"
            + "var w=document.querySelector('[data-vaul-drawer-wrapper]');"
            + "if(w){w.style.transform='';w.style.borderRadius='';"
            + "w.removeAttribute('data-vaul-drawer-wrapper');}"
            + "document.documentElement.style.overflow='';}catch(e){}}u();"
            + "new MutationObserver(u).observe(document.documentElement,{childList:true,"
            + "subtree:true,attributes:true,attributeFilter:['style','data-vaul-drawer-wrapper']});"
            + "}catch(e){}})();";

    private WebView webView;
    private ProgressBar progress;
    private View readingProgress;
    private View errorView;
    private TextView errorText;

    private List<String> mirrors;
    private int mirrorIndex;
    private String originalUrl;
    private String offlinePath;
    private boolean failed;
    private boolean historyRecorded;
    private View loadingOverlay;

    private TextToSpeech tts;
    private boolean speaking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        progress = findViewById(R.id.progress);
        readingProgress = findViewById(R.id.reading_progress);
        webView = findViewById(R.id.web);
        errorView = findViewById(R.id.error);
        errorText = findViewById(R.id.error_text);
        ((Button) findViewById(R.id.error_retry)).setOnClickListener(v -> retry());
        ((Button) findViewById(R.id.error_browser)).setOnClickListener(v -> openExternally(currentOrPlannedUrl(), false));
        mirrors = buildMirrors();
        setUpWebView();

        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            originalUrl = savedInstanceState.getString(STATE_ORIGINAL);
            return;
        }

        if (ACTION_OFFLINE.equals(getIntent().getAction())) {
            originalUrl = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            offlinePath = getIntent().getStringExtra(EXTRA_OFFLINE);
            setTitle(R.string.loading);
            webView.loadUrl(Uri.fromFile(new File(offlinePath)).toString());
            return;
        }

        originalUrl = FreediumLinks.extractUrl(sharedText(getIntent()));
        if (originalUrl == null) {
            Toast.makeText(this, R.string.no_link, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        loadingOverlay = Branding.showReaderLoading(this);
        load(originalUrl);
    }

    /** User's mirror first, then the other known mirrors, for automatic failover. */
    private List<String> buildMirrors() {
        List<String> out = new ArrayList<>();
        out.add(Prefs.mirror(this));
        for (String m : FreediumLinks.KNOWN_MIRRORS) if (!out.contains(m)) out.add(m);
        return out;
    }

    private String mirror() {
        return mirrors.get(Math.min(mirrorIndex, mirrors.size() - 1));
    }

    /** Resolves short links first, then loads the Freedium page. */
    private void load(String articleUrl) {
        setTitle(R.string.loading);
        hideError();
        if (FreediumLinks.needsRedirectResolution(articleUrl)) {
            progress.setVisibility(View.VISIBLE);
            LinkResolver.resolve(articleUrl, resolved -> {
                originalUrl = resolved;
                webView.loadUrl(FreediumLinks.toFreedium(mirror(), resolved));
            });
        } else {
            webView.loadUrl(FreediumLinks.toFreedium(mirror(), articleUrl));
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

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    private void setUpWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(true);
        s.setTextZoom(Prefs.fontScale(this));
        // Honour the page's width=device-width viewport so full-bleed (vw-based) hero images
        // lay out at the real screen width instead of shifting.
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (!failed && title != null && !title.isEmpty()) setTitle(title.replaceFirst("\\s*-\\s*Freedium$", ""));
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                String url = request.getUrl().toString();
                if (FreediumLinks.isOnMirror(mirror(), url)) return false;
                if (FreediumLinks.isSupportedHost(request.getUrl().getHost())) {
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
                if (!request.isForMainFrame()) return;
                // Try the next mirror before giving up.
                if (offlinePath == null && mirrorIndex < mirrors.size() - 1) {
                    mirrorIndex++;
                    load(originalUrl);
                } else {
                    showError();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                boolean onMirror = FreediumLinks.isOnMirror(mirror(), url);
                if (onMirror || offlinePath != null) {
                    view.evaluateJavascript(CLEAN_JS, null);
                    view.evaluateJavascript(DOWNLOAD_JS, null);
                    applyTheme();
                    dismissLoading();
                    if (!historyRecorded && originalUrl != null) {
                        historyRecorded = true;
                        Store.addHistory(ReaderActivity.this, originalUrl, titleOrUrl());
                    }
                }
            }
        });

        webView.setOnScrollChangeListener((v, x, y, ox, oy) -> updateReadingProgress());
        webView.addJavascriptInterface(new DownloadBridge(), "AndroidDownload");
        webView.setDownloadListener((url, ua, cd, mime, len) -> onDownload(url, cd, mime));
    }

    private void updateReadingProgress() {
        float range = webView.getContentHeight() * getResources().getDisplayMetrics().density - webView.getHeight();
        float f = range > 0 ? Math.min(1f, Math.max(0f, webView.getScrollY() / range)) : 0f;
        readingProgress.setPivotX(0f);
        readingProgress.setScaleX(f);
        readingProgress.setVisibility(f > 0.001f ? View.VISIBLE : View.GONE);
    }

    private String titleOrUrl() {
        CharSequence t = getTitle();
        return t == null || t.length() == 0 ? originalUrl : t.toString();
    }

    private void retry() {
        if (originalUrl != null) load(originalUrl);
    }

    private void showError() {
        failed = true;
        setTitle(R.string.app_name);
        progress.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        dismissLoading();
        errorText.setText(getString(R.string.load_failed, mirror()));
        errorView.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        failed = false;
        errorView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void dismissLoading() {
        if (loadingOverlay != null) {
            Branding.dismiss(loadingOverlay);
            loadingOverlay = null;
        }
    }

    // --- reader tools ---

    private void applyTheme() {
        int theme = Prefs.readerTheme(this);
        String css;
        if (theme == 1) {
            css = "html{filter:sepia(0.6) contrast(0.95)!important;}";
        } else if (theme == 2) {
            css = "html{filter:invert(1) hue-rotate(180deg)!important;background:#0f0f0f!important;}"
                    + "img,video,picture,svg,[style*=\"background-image\"]"
                    + "{filter:invert(1) hue-rotate(180deg)!important;}";
        } else {
            css = "";
        }
        String js = "(function(){var id='fz-theme';var e=document.getElementById(id);"
                + "if(!e){e=document.createElement('style');e.id=id;"
                + "(document.head||document.documentElement).appendChild(e);}"
                + "e.textContent=" + jsString(css) + ";})();";
        webView.evaluateJavascript(js, null);
    }

    private static String jsString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void changeFont(int deltaPercent) {
        int scale = Math.max(70, Math.min(220, Prefs.fontScale(this) + deltaPercent));
        Prefs.setFontScale(this, scale);
        webView.getSettings().setTextZoom(scale);
        Toast.makeText(this, getString(R.string.font_scale, scale), Toast.LENGTH_SHORT).show();
    }

    private void cycleTheme() {
        int t = (Prefs.readerTheme(this) + 1) % 3;
        Prefs.setReaderTheme(this, t);
        applyTheme();
        int[] names = {R.string.theme_default, R.string.theme_sepia, R.string.theme_dark};
        Toast.makeText(this, names[t], Toast.LENGTH_SHORT).show();
    }

    private void toggleSave() {
        boolean saved = Store.toggleSaved(this, originalUrl, titleOrUrl(), null);
        Toast.makeText(this, saved ? R.string.saved_added : R.string.saved_removed, Toast.LENGTH_SHORT).show();
        invalidateOptionsMenu();
    }

    private void saveOffline() {
        File dir = new File(getFilesDir(), "offline");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File out = new File(dir, "a" + Math.abs(originalUrl.hashCode()) + ".mht");
        webView.saveWebArchive(out.getAbsolutePath(), false, path -> {
            if (path != null) {
                Store.toggleSaved(this, originalUrl, titleOrUrl(), path);
                runOnUiThread(() -> Toast.makeText(this, R.string.saved_offline, Toast.LENGTH_SHORT).show());
            } else {
                runOnUiThread(() -> Toast.makeText(this, R.string.offline_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void toggleReadAloud() {
        if (speaking) {
            stopSpeaking();
            return;
        }
        webView.evaluateJavascript(
                "(function(){var a=document.querySelector('article')||document.body;"
                        + "return a?a.innerText:'';})()",
                value -> speak(unquote(value)));
    }

    private void speak(String text) {
        if (text == null || text.trim().length() < 2) {
            Toast.makeText(this, R.string.nothing_to_read, Toast.LENGTH_SHORT).show();
            return;
        }
        final String content = text.length() > 60000 ? text.substring(0, 60000) : text;
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) {
                runOnUiThread(() -> Toast.makeText(this, R.string.tts_unavailable, Toast.LENGTH_SHORT).show());
                return;
            }
            tts.setLanguage(Locale.getDefault());
            int chunk = 0;
            for (int i = 0; i < content.length(); i += 3500) {
                String part = content.substring(i, Math.min(content.length(), i + 3500));
                tts.speak(part, chunk == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, "fz" + chunk);
                chunk++;
            }
            speaking = true;
            runOnUiThread(this::invalidateOptionsMenu);
        });
    }

    private void stopSpeaking() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        speaking = false;
        invalidateOptionsMenu();
    }

    private static String unquote(String jsonString) {
        if (jsonString == null || jsonString.length() < 2 || !jsonString.startsWith("\"")) return jsonString;
        try {
            return new org.json.JSONArray("[" + jsonString + "]").getString(0);
        } catch (Exception e) {
            return jsonString;
        }
    }

    // --- downloads ---

    /** Handles anything the page tries to download that isn't caught by the blob click capturer. */
    private void onDownload(String url, String contentDisposition, String mime) {
        try {
            if (url.startsWith("blob:")) {
                // Fallback: fetch the blob in page context and hand it back to the bridge.
                webView.evaluateJavascript(
                        "(function(u){try{fetch(u).then(function(r){return r.blob();}).then(function(b){"
                                + "var fr=new FileReader();fr.onloadend=function(){var d=''+fr.result;var i=d.indexOf(',');"
                                + "AndroidDownload.save(d.substring(i+1),d.substring(5,d.indexOf(';'))||'application/octet-stream',"
                                + "document.title||'article');};fr.readAsDataURL(b);}).catch(function(){});}catch(e){}})("
                                + jsString(url) + ");", null);
            } else if (url.startsWith("data:")) {
                int comma = url.indexOf(',');
                String meta = url.substring(5, comma);
                byte[] bytes = meta.contains(";base64")
                        ? android.util.Base64.decode(url.substring(comma + 1), android.util.Base64.DEFAULT)
                        : java.net.URLDecoder.decode(url.substring(comma + 1), "UTF-8").getBytes("UTF-8");
                saveBytes(bytes, meta.split(";")[0], guessName(titleOrUrl(), meta.split(";")[0]));
            } else {
                android.app.DownloadManager dm = getSystemService(android.app.DownloadManager.class);
                android.app.DownloadManager.Request req = new android.app.DownloadManager.Request(Uri.parse(url));
                String name = android.webkit.URLUtil.guessFileName(url, contentDisposition, mime);
                req.setMimeType(mime);
                req.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name);
                dm.enqueue(req);
                Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** Writes bytes into the public Downloads collection (no storage permission needed). */
    private void saveBytes(byte[] bytes, String mime, String name) {
        try {
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(android.provider.MediaStore.Downloads.MIME_TYPE,
                    mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
            cv.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) {
                Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            java.io.OutputStream os = getContentResolver().openOutputStream(uri);
            os.write(bytes);
            os.close();
            cv.clear();
            cv.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(uri, cv, null, null);
            Toast.makeText(this, getString(R.string.download_saved, name), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private static String guessName(String base, String mime) {
        String n = base == null ? "article" : base.replaceAll("[^A-Za-z0-9 ._-]", "").trim();
        if (n.isEmpty()) n = "article";
        if (n.length() > 60) n = n.substring(0, 60);
        String ext = extFor(mime);
        if (!n.toLowerCase(Locale.ROOT).endsWith("." + ext)) n = n + "." + ext;
        return n;
    }

    private static String extFor(String mime) {
        if (mime == null) return "bin";
        mime = mime.toLowerCase(Locale.ROOT);
        if (mime.contains("markdown")) return "md";
        if (mime.contains("pdf")) return "pdf";
        if (mime.contains("html")) return "html";
        if (mime.contains("json")) return "json";
        if (mime.contains("epub")) return "epub";
        if (mime.contains("zip")) return "zip";
        if (mime.contains("text/plain")) return "txt";
        return "bin";
    }

    /** Exposed to the Freedium page only to save a user-triggered download into Downloads. */
    private final class DownloadBridge {
        @android.webkit.JavascriptInterface
        public void save(String base64, String mime, String baseName) {
            try {
                byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                runOnUiThread(() -> saveBytes(bytes, mime, guessName(baseName, mime)));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(ReaderActivity.this, R.string.download_failed, Toast.LENGTH_SHORT).show());
            }
        }
    }

    // --- external / menu ---

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
        return originalUrl == null ? null : FreediumLinks.toFreedium(mirror(), originalUrl);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.reader, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem save = menu.findItem(R.id.action_save);
        if (save != null) save.setTitle(originalUrl != null && Store.isSaved(this, originalUrl)
                ? R.string.saved_remove_action : R.string.save_action);
        MenuItem read = menu.findItem(R.id.action_read_aloud);
        if (read != null) read.setTitle(speaking ? R.string.stop_reading : R.string.read_aloud);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            if (failed) retry(); else webView.reload();
        } else if (id == R.id.action_font_bigger) {
            changeFont(10);
        } else if (id == R.id.action_font_smaller) {
            changeFont(-10);
        } else if (id == R.id.action_theme) {
            cycleTheme();
        } else if (id == R.id.action_save) {
            toggleSave();
        } else if (id == R.id.action_save_offline) {
            saveOffline();
        } else if (id == R.id.action_read_aloud) {
            toggleReadAloud();
        } else if (id == R.id.action_share) {
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(Intent.EXTRA_TEXT, currentUrl()), null));
        } else if (id == R.id.action_copy) {
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Freedium link", currentUrl()));
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
        stopSpeaking();
        webView.destroy();
        super.onDestroy();
    }
}
