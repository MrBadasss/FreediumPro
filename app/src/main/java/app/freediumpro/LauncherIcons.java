package app.freediumpro;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/** Switches the home-screen launcher icon by toggling activity-aliases. */
final class LauncherIcons {

    // Fully-qualified alias class names (code namespace is app.freediumpro, not the applicationId).
    private static final String[] ALIASES = {
            "app.freediumpro.Alias0", "app.freediumpro.Alias1",
            "app.freediumpro.Alias2", "app.freediumpro.Alias3",
    };

    private LauncherIcons() {}

    /** Enables the alias for the chosen logo index and disables the others. */
    static void apply(Context c, int index) {
        PackageManager pm = c.getPackageManager();
        String pkg = c.getPackageName();
        for (int i = 0; i < ALIASES.length; i++) {
            int state = (i == index)
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            try {
                pm.setComponentEnabledSetting(
                        new ComponentName(pkg, ALIASES[i]),
                        state, PackageManager.DONT_KILL_APP);
            } catch (Exception ignored) {
            }
        }
    }
}
