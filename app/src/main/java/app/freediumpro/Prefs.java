package app.freediumpro;

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
    private static final String KEY_FONT = "font_scale";
    private static final String KEY_READER_THEME = "reader_theme";
    private static final String KEY_FONT_FAMILY = "font_family";
    private static final String KEY_LINE_SPACING = "line_spacing";
    private static final String KEY_READER_WIDTH = "reader_width";
    private static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    private static final String KEY_IMMERSIVE = "immersive";
    private static final String KEY_AUTO_OFFLINE = "auto_offline";

    private Prefs() {}

    static int intPref(Context c, String k, int def) { return prefs(c).getInt(k, def); }
    static void setInt(Context c, String k, int v) { prefs(c).edit().putInt(k, v).apply(); }

    static int fontFamily(Context c) { return intPref(c, KEY_FONT_FAMILY, 0); }   // 0 default, 1 serif, 2 sans
    static void setFontFamily(Context c, int v) { setInt(c, KEY_FONT_FAMILY, v); }
    static int lineSpacing(Context c) { return intPref(c, KEY_LINE_SPACING, 1); }  // 0 compact, 1 normal, 2 relaxed
    static void setLineSpacing(Context c, int v) { setInt(c, KEY_LINE_SPACING, v); }
    static int readerWidth(Context c) { return intPref(c, KEY_READER_WIDTH, 1); }  // 0 narrow, 1 normal, 2 wide
    static void setReaderWidth(Context c, int v) { setInt(c, KEY_READER_WIDTH, v); }

    static boolean keepScreenOn(Context c) { return prefs(c).getBoolean(KEY_KEEP_SCREEN_ON, false); }
    static void setKeepScreenOn(Context c, boolean v) { prefs(c).edit().putBoolean(KEY_KEEP_SCREEN_ON, v).apply(); }
    static boolean immersive(Context c) { return prefs(c).getBoolean(KEY_IMMERSIVE, false); }
    static void setImmersive(Context c, boolean v) { prefs(c).edit().putBoolean(KEY_IMMERSIVE, v).apply(); }
    static boolean autoOffline(Context c) { return prefs(c).getBoolean(KEY_AUTO_OFFLINE, false); }
    static void setAutoOffline(Context c, boolean v) { prefs(c).edit().putBoolean(KEY_AUTO_OFFLINE, v).apply(); }

    /** Reader text zoom percent (WebSettings.setTextZoom). */
    static int fontScale(Context context) {
        return prefs(context).getInt(KEY_FONT, 100);
    }

    static void setFontScale(Context context, int percent) {
        prefs(context).edit().putInt(KEY_FONT, percent).apply();
    }

    /** 0 = default, 1 = sepia, 2 = dark. */
    static int readerTheme(Context context) {
        return prefs(context).getInt(KEY_READER_THEME, 0);
    }

    static void setReaderTheme(Context context, int theme) {
        prefs(context).edit().putInt(KEY_READER_THEME, theme).apply();
    }

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

    static String exportJson(Context c) {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("mirror", mirror(c));
            o.put("autoRoute", autoRoute(c));
            o.put("font", fontScale(c));
            o.put("theme", readerTheme(c));
            o.put("logo", logo(c));
            return o.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    static boolean importJson(Context c, String json) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            if (o.has("mirror")) {
                String m = FreediumLinks.normalizeMirror(o.optString("mirror"));
                if (m != null) setMirror(c, m);
            }
            if (o.has("autoRoute")) setAutoRoute(c, o.optBoolean("autoRoute", true));
            if (o.has("font")) setFontScale(c, o.optInt("font", 100));
            if (o.has("theme")) setReaderTheme(c, o.optInt("theme", 0));
            if (o.has("logo")) setLogo(c, o.optInt("logo", 0));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
