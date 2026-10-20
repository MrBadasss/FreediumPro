package app.freediumpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Auto-route toggle, mirror editor + test, default-handler shortcut, and Medium sign-out. */
public class SettingsActivity extends Activity {

    private EditText linkInput;
    private EditText mirrorInput;
    private TextView mirrorCurrent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.settings);
        linkInput = findViewById(R.id.link_input);
        mirrorInput = findViewById(R.id.mirror_input);
        mirrorCurrent = findViewById(R.id.mirror_current);

        findViewById(R.id.open_button).setOnClickListener(v -> open(linkInput.getText().toString()));
        findViewById(R.id.default_button).setOnClickListener(v -> openDefaultAppsSettings());
        findViewById(R.id.mirror_save).setOnClickListener(v -> saveMirror());
        findViewById(R.id.mirror_test).setOnClickListener(v -> testMirror());
        findViewById(R.id.mirror_reset).setOnClickListener(v -> {
            Prefs.resetMirror(this);
            showMirror();
        });
        findViewById(R.id.signout_button).setOnClickListener(v -> confirmSignOut());
        findViewById(R.id.fastest_button).setOnClickListener(v -> findFastestMirror());
        findViewById(R.id.clearcache_button).setOnClickListener(v -> clearCache());
        findViewById(R.id.backup_button).setOnClickListener(v -> backupSettings());
        findViewById(R.id.restore_button).setOnClickListener(v -> restoreSettings());

        Switch autoRoute = findViewById(R.id.auto_route);
        autoRoute.setChecked(Prefs.autoRoute(this));
        autoRoute.setOnCheckedChangeListener((CompoundButton b, boolean on) -> Prefs.setAutoRoute(this, on));

        buildLogoPicker();
        findViewById(R.id.app_icon_button).setOnClickListener(v -> {
            LauncherIcons.apply(this, Prefs.logo(this));
            Toast.makeText(this, R.string.app_icon_done, Toast.LENGTH_LONG).show();
        });
        showMirror();
    }

    /** A row of circular logo choices; the selected one gets a green ring. */
    private void buildLogoPicker() {
        android.widget.LinearLayout row = findViewById(R.id.logo_row);
        row.removeAllViews();
        int size = Math.round(56 * getResources().getDisplayMetrics().density);
        int gap = Math.round(10 * getResources().getDisplayMetrics().density);
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        int selected = Prefs.logo(this);
        for (int i = 0; i < Branding.MARKS.length; i++) {
            final int index = i;
            android.widget.ImageView chip = new android.widget.ImageView(this);
            chip.setBackgroundResource(R.drawable.circle_bg);
            chip.setImageResource(Branding.MARKS[i]);
            chip.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
            chip.setPadding(pad, pad, pad, pad);
            chip.setForeground(i == selected ? getDrawable(R.drawable.ring_select) : null);
            chip.setContentDescription(Branding.MARK_NAMES[i]);
            chip.setOnClickListener(v -> {
                Prefs.setLogo(this, index);
                buildLogoPicker();
                Toast.makeText(this, R.string.logo_selected, Toast.LENGTH_SHORT).show();
            });
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(gap);
            row.addView(chip, lp);
        }
    }

    private void showMirror() {
        String mirror = Prefs.mirror(this);
        mirrorCurrent.setText(getString(R.string.mirror_current, mirror));
        mirrorInput.setText(mirror);
    }

    private void saveMirror() {
        String mirror = FreediumLinks.normalizeMirror(mirrorInput.getText().toString());
        if (mirror == null) {
            Toast.makeText(this, R.string.mirror_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        Prefs.setMirror(this, mirror);
        showMirror();
        Toast.makeText(this, R.string.mirror_saved, Toast.LENGTH_SHORT).show();
    }

    private void testMirror() {
        String mirror = FreediumLinks.normalizeMirror(mirrorInput.getText().toString());
        if (mirror == null) {
            Toast.makeText(this, R.string.mirror_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.mirror_testing, Toast.LENGTH_SHORT).show();
        LinkResolver.resolve(mirror, finalUrl ->
                Toast.makeText(this, FreediumLinks.hostOf(finalUrl) != null ? R.string.mirror_ok
                        : R.string.mirror_unreachable, Toast.LENGTH_LONG).show());
    }

    private void open(String text) {
        if (FreediumLinks.extractUrl(text) == null) {
            Toast.makeText(this, R.string.no_link, Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, ReaderActivity.class)
                .setAction(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text));
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sign_out)
                .setMessage(R.string.sign_out_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.sign_out, (d, w) -> {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    WebStorage.getInstance().deleteAllData();
                    Toast.makeText(this, R.string.signed_out, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private static final int REQ_BACKUP = 11, REQ_RESTORE = 12;

    /** Pings the known mirrors and selects the first that responds. */
    private void findFastestMirror() {
        java.util.List<String> candidates = new java.util.ArrayList<>();
        candidates.add(Prefs.mirror(this));
        for (String m : FreediumLinks.KNOWN_MIRRORS) if (!candidates.contains(m)) candidates.add(m);
        Toast.makeText(this, R.string.mirror_testing, Toast.LENGTH_SHORT).show();
        tryMirror(candidates, 0);
    }

    private void tryMirror(java.util.List<String> list, int i) {
        if (i >= list.size()) {
            Toast.makeText(this, R.string.no_mirror_found, Toast.LENGTH_LONG).show();
            return;
        }
        String m = list.get(i);
        LinkResolver.resolve(m, finalUrl -> {
            if (FreediumLinks.hostOf(finalUrl) != null && finalUrl.startsWith(m.substring(0, Math.min(8, m.length())))) {
                Prefs.setMirror(this, m);
                showMirror();
                Toast.makeText(this, getString(R.string.mirror_picked, m), Toast.LENGTH_LONG).show();
            } else {
                tryMirror(list, i + 1);
            }
        });
    }

    private void clearCache() {
        try {
            android.webkit.WebView w = new android.webkit.WebView(this);
            w.clearCache(true);
            w.destroy();
            android.webkit.WebStorage.getInstance().deleteAllData();
            deleteDir(getCacheDir());
        } catch (Exception ignored) {
        }
        Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show();
    }

    private static void deleteDir(java.io.File dir) {
        if (dir == null) return;
        java.io.File[] kids = dir.listFiles();
        if (kids != null) for (java.io.File f : kids) {
            if (f.isDirectory()) deleteDir(f);
            else //noinspection ResultOfMethodCallIgnored
                f.delete();
        }
    }

    private void backupSettings() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, "freedium-settings.json");
        try {
            startActivityForResult(i, REQ_BACKUP);
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void restoreSettings() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        try {
            startActivityForResult(i, REQ_RESTORE);
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (req == REQ_BACKUP) {
                java.io.OutputStream os = getContentResolver().openOutputStream(data.getData());
                os.write(Prefs.exportJson(this).getBytes("UTF-8"));
                os.close();
                Toast.makeText(this, R.string.backup_done, Toast.LENGTH_SHORT).show();
            } else if (req == REQ_RESTORE) {
                java.io.InputStream is = getContentResolver().openInputStream(data.getData());
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) > 0) b.write(buf, 0, n);
                is.close();
                boolean ok = Prefs.importJson(this, b.toString("UTF-8"));
                showMirror();
                buildLogoPicker();
                Toast.makeText(this, ok ? R.string.restore_done : R.string.restore_failed, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.restore_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** Opens the system screen where the user can make this app open Medium links. */
    private void openDefaultAppsSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
                Toast.makeText(this, R.string.open_settings_manually, Toast.LENGTH_LONG).show();
            }
        }
    }
}
