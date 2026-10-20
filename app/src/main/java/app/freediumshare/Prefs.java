package app.freediumshare;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores the Freedium mirror so it can be changed if the domain moves. */
final class Prefs {
    private static final String FILE = "settings";
    private static final String KEY_MIRROR = "mirror";
    private static final String KEY_AUTO_ROUTE = "auto_route";
    private static final String KEY_FAB_X = "fab_x";
    private static final String KEY_FAB_Y = "fab_y";
    private static final String KEY_LOGO = "logo";

    private Prefs() {}

    /** Index into Branding.MARKS of the chosen app logo. */
    static int logo(Context context) {
        return prefs(context).getInt(KEY_LOGO, 0);
    }

    static void setLogo(Context context, int index) {
        prefs(context).edit().putInt(KEY_LOGO, index).apply();
    }

    /** Remembered drag offset of the floating button (pixels from its default bottom-end anchor). */
    static float fabX(Context context) {
        return prefs(context).getFloat(KEY_FAB_X, 0f);
    }

    static float fabY(Context context) {
        return prefs(context).getFloat(KEY_FAB_Y, 0f);
    }

    static void setFabPos(Context context, float x, float y) {
        prefs(context).edit().putFloat(KEY_FAB_X, x).putFloat(KEY_FAB_Y, y).apply();
    }

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
