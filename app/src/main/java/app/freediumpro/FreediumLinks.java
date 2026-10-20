package app.freediumpro;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure link handling (no Android APIs) so it can be unit tested on the JVM. */
public final class FreediumLinks {

    public static final String DEFAULT_MIRROR = "https://freedium-mirror.cfd";

    /** Known Freedium mirrors offered as presets; the first is the default. */
    public static final String[] KNOWN_MIRRORS = {
            "https://freedium-mirror.cfd",
            "https://freedium.cfd",
    };

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);
    private static final String TRAILING_JUNK = ".,;:!?)]}>»”’'\"";

    private FreediumLinks() {}

    /** Returns the first http(s) URL found in shared text, or null. */
    public static String extractUrl(String text) {
        if (text == null) return null;
        Matcher m = URL.matcher(text);
        if (!m.find()) return null;
        String url = m.group();
        while (!url.isEmpty() && TRAILING_JUNK.indexOf(url.charAt(url.length() - 1)) >= 0) {
            url = url.substring(0, url.length() - 1);
        }
        return url.isEmpty() ? null : url;
    }

    /** Turns user input like "freedium-mirror.cfd/" into "https://freedium-mirror.cfd", or null if invalid. */
    public static String normalizeMirror(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;
        if (!s.matches("(?i)^https?://.*")) s = "https://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        String host = hostOf(s);
        return host == null || !host.contains(".") ? null : s;
    }

    /** Removes the fragment and tracking parameters (source, utm_*, ref, etc.) from an article URL. */
    public static String cleanArticleUrl(String url) {
        int hash = url.indexOf('#');
        if (hash >= 0) url = url.substring(0, hash);
        int q = url.indexOf('?');
        if (q < 0) return url;
        StringBuilder kept = new StringBuilder();
        for (String param : url.substring(q + 1).split("&")) {
            if (param.isEmpty() || isTrackingParam(param.split("=", 2)[0])) continue;
            kept.append(kept.length() == 0 ? '?' : '&').append(param);
        }
        return url.substring(0, q) + kept;
    }

    private static boolean isTrackingParam(String name) {
        name = name.toLowerCase(Locale.ROOT);
        return name.equals("source") || name.startsWith("utm_") || name.equals("ref")
                || name.equals("gi") || name.equals("_branch_match_id") || name.equals("_branch_referrer");
    }

    /** Builds the Freedium URL for an article; links already on the mirror are returned unchanged. */
    public static String toFreedium(String mirror, String articleUrl) {
        if (isOnMirror(mirror, articleUrl)) return articleUrl;
        return mirror + "/" + cleanArticleUrl(articleUrl);
    }

    public static boolean isOnMirror(String mirror, String url) {
        String a = hostOf(mirror), b = hostOf(url);
        return a != null && a.equals(b);
    }

    /** True for medium.com, any *.medium.com subdomain (incl. link.medium.com), and medium.com short links. */
    public static boolean isMediumHost(String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("medium.com") || host.endsWith(".medium.com");
    }

    /** Other paywalled sites Freedium can unlock, in addition to Medium. */
    private static final String[] SUPPORTED_SUFFIXES = {
            "nytimes.com", "washingtonpost.com", "bloomberg.com", "reuters.com",
            "economist.com", "ft.com", "theathletic.com", "quora.com",
            "towardsdatascience.com", "medium.freecodecamp.org",
    };

    /** Hosts the app will route through Freedium (Medium plus other supported publishers). */
    public static boolean isSupportedHost(String host) {
        if (isMediumHost(host)) return true;
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        for (String suffix : SUPPORTED_SUFFIXES) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) return true;
        }
        return false;
    }

    /** Heuristic: does this Medium URL point at an article (vs the feed, a profile, settings)? */
    public static boolean looksLikeArticle(String url) {
        if (!isMediumHost(hostOf(url))) return false;
        try {
            String path = new URI(url).getPath();
            if (path == null) return false;
            if (path.startsWith("/p/") || path.startsWith("/m/")) return true;
            // A story slug ends with a hex id, e.g. .../welcome-to-medium-9e53ca408c48
            return path.matches(".*-[0-9a-f]{6,}/?$");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Short links (link.medium.com, rsci.app.link) are redirect wrappers whose real article URL
     * must be followed before Freedium can read them.
     */
    public static boolean needsRedirectResolution(String url) {
        String host = hostOf(url);
        if (host == null) return false;
        return host.equals("link.medium.com") || host.endsWith(".app.link");
    }

    public static String hostOf(String url) {
        try {
            String host = new URI(url).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }
}
