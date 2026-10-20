package app.freediumshare;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Mirror configuration, paywall auto-routing toggle, default-handler shortcut, and Medium sign-out. */
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

        Switch autoRoute = findViewById(R.id.auto_route);
        autoRoute.setChecked(Prefs.autoRoute(this));
        autoRoute.setOnCheckedChangeListener((CompoundButton b, boolean on) -> Prefs.setAutoRoute(this, on));

        findViewById(R.id.overlay_button).setOnClickListener(v -> requestOverlay());
        findViewById(R.id.a11y_button).setOnClickListener(v -> openAccessibilitySettings());

        showMirror();
    }

    @Override
    protected void onResume() {
        super.onResume();
        showBubbleStatus();
    }

    private void showBubbleStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean watcher = isWatcherEnabled();
        String state = overlay && watcher ? getString(R.string.bubble_on)
                : getString(R.string.bubble_steps,
                        overlay ? "✓" : "•", watcher ? "✓" : "•");
        ((TextView) findViewById(R.id.bubble_status)).setText(state);
    }

    private boolean isWatcherEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        String me = new ComponentName(this, MediumWatchService.class).flattenToString();
        for (String part : enabled.split(":")) {
            if (part.equalsIgnoreCase(me)) return true;
        }
        return false;
    }

    private void requestOverlay() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_granted, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, R.string.open_settings_manually, Toast.LENGTH_LONG).show();
        }
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, R.string.find_watcher, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.open_settings_manually, Toast.LENGTH_LONG).show();
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
