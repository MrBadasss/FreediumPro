package app.freediumpro;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Local persistence for the reading list (saved) and recently-read history. */
final class Store {

    static final String SAVED = "saved";
    static final String HISTORY = "history";
    private static final String FILE = "library";
    private static final int HISTORY_MAX = 100;

    /** A stored article. offline is a file path to a saved web archive, or null. */
    static final class Item {
        final String url, title, offline;
        final long ts;

        Item(String url, String title, long ts, String offline) {
            this.url = url;
            this.title = title;
            this.ts = ts;
            this.offline = offline;
        }
    }

    private Store() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static List<Item> list(Context c, String key) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p(c).getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Item(o.getString("u"), o.optString("t", ""), o.optLong("ts", 0),
                        o.has("o") ? o.optString("o") : null));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    static void addHistory(Context c, String url, String title) {
        if (url == null) return;
        List<Item> l = list(c, HISTORY);
        removeUrl(l, url);
        l.add(0, new Item(url, title, System.currentTimeMillis(), null));
        while (l.size() > HISTORY_MAX) l.remove(l.size() - 1);
        write(c, HISTORY, l);
    }

    static boolean isSaved(Context c, String url) {
        for (Item i : list(c, SAVED)) if (i.url.equals(url)) return true;
        return false;
    }

    /** Adds or removes the article from the reading list; returns true if now saved. */
    static boolean toggleSaved(Context c, String url, String title, String offline) {
        List<Item> l = list(c, SAVED);
        boolean wasSaved = removeUrl(l, url);
        if (!wasSaved) l.add(0, new Item(url, title, System.currentTimeMillis(), offline));
        write(c, SAVED, l);
        return !wasSaved;
    }

    static void remove(Context c, String key, String url) {
        List<Item> l = list(c, key);
        removeUrl(l, url);
        write(c, key, l);
    }

    static void clear(Context c, String key) {
        p(c).edit().remove(key).apply();
    }

    private static boolean removeUrl(List<Item> l, String url) {
        boolean removed = false;
        for (int i = l.size() - 1; i >= 0; i--) {
            if (l.get(i).url.equals(url)) {
                l.remove(i);
                removed = true;
            }
        }
        return removed;
    }

    private static void write(Context c, String key, List<Item> l) {
        JSONArray a = new JSONArray();
        for (Item i : l) {
            try {
                JSONObject o = new JSONObject();
                o.put("u", i.url);
                o.put("t", i.title);
                o.put("ts", i.ts);
                if (i.offline != null) o.put("o", i.offline);
                a.put(o);
            } catch (Exception ignored) {
            }
        }
        p(c).edit().putString(key, a.toString()).apply();
    }
}
