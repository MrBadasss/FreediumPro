package app.freediumshare;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores the Freedium mirror so it can be changed if the domain moves. */
final class Prefs {
    private static final String FILE = "settings";
    private static final String KEY_MIRROR = "mirror";
    private static final String KEY_AUTO_ROUTE = "auto_route";

    private Prefs() {}

    static boolean autoRoute(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_ROUTE, true);
    }

    static void setAutoRoute(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_AUTO_ROUTE, on).apply();
    }

    static String mirror(Context context) {
        String saved = prefs(context).getString(KEY_MIRROR, null);
        String normalized = FreediumLinks.normalizeMirror(saved);
        return normalized != null ? normalized : FreediumLinks.DEFAULT_MIRROR;
    }

    static void setMirror(Context context, String mirror) {
        prefs(context).edit().putString(KEY_MIRROR, mirror).apply();
    }

    static void resetMirror(Context context) {
        prefs(context).edit().remove(KEY_MIRROR).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
