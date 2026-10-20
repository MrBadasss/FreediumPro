package app.freediumpro;

import android.os.Handler;
import android.os.Looper;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

/** Follows HTTP redirects for short links (e.g. link.medium.com) off the main thread. */
final class LinkResolver {

    interface Callback {
        void onResolved(String finalUrl);
    }

    private static final int MAX_HOPS = 6;
    private static final int TIMEOUT_MS = 12000;
    private static final String UA =
            "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36";

    private LinkResolver() {}

    /** Resolves on a background thread, then calls back on the main thread. Falls back to the input URL. */
    static void resolve(String url, Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            String resolved = followRedirects(url);
            main.post(() -> callback.onResolved(resolved));
        });
    }

    private static String followRedirects(String url) {
        String current = url;
        try {
            for (int hop = 0; hop < MAX_HOPS; hop++) {
                HttpURLConnection c = (HttpURLConnection) new URL(current).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(TIMEOUT_MS);
                c.setReadTimeout(TIMEOUT_MS);
                c.setRequestProperty("User-Agent", UA);
                c.setRequestMethod("GET");
                try {
                    int code = c.getResponseCode();
                    if (code < 300 || code >= 400) return current;
                    String location = c.getHeaderField("Location");
                    if (location == null || location.isEmpty()) return current;
                    current = new URL(new URL(current), location).toString();
                } finally {
                    c.disconnect();
                }
            }
        } catch (Exception e) {
            return url; // network failure -> let Freedium try the original
        }
        return current;
    }
}
