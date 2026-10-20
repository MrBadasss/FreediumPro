package app.freediumpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Reading list (saved) and recently-read history. */
public class LibraryActivity extends Activity {

    private String tab = Store.SAVED;
    private LinearLayout listCol;
    private Button tabSaved, tabHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.library);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(pad(8), pad(8), pad(8), 0);
        tabSaved = tabButton(getString(R.string.tab_saved), v -> switchTab(Store.SAVED));
        tabHistory = tabButton(getString(R.string.tab_history), v -> switchTab(Store.HISTORY));
        tabs.addView(tabSaved);
        tabs.addView(tabHistory);
        root.addView(tabs);

        listCol = new LinearLayout(this);
        listCol.setOrientation(LinearLayout.VERTICAL);
        listCol.setPadding(pad(12), pad(8), pad(12), pad(24));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(listCol);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void switchTab(String which) {
        tab = which;
        render();
    }

    private void render() {
        boolean saved = tab.equals(Store.SAVED);
        tabSaved.setAlpha(saved ? 1f : 0.5f);
        tabHistory.setAlpha(saved ? 0.5f : 1f);

        listCol.removeAllViews();
        List<Store.Item> items = Store.list(this, tab);
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(saved ? R.string.empty_saved : R.string.empty_history);
            empty.setTextColor(0xFF9AA0A6);
            empty.setPadding(0, pad(40), 0, 0);
            empty.setGravity(Gravity.CENTER);
            listCol.addView(empty);
            return;
        }

        Button clear = new Button(this);
        clear.setText(R.string.clear_all);
        clear.setOnClickListener(v -> confirmClear());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.gravity = Gravity.END;
        listCol.addView(clear, clp);

        for (Store.Item item : items) listCol.addView(row(item));
    }

    private View row(Store.Item item) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(pad(4), pad(12), pad(4), pad(12));
        r.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(item.title == null || item.title.isEmpty() ? item.url : item.title);
        title.setTextSize(16f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(2);
        TextView host = new TextView(this);
        String h = FreediumLinks.hostOf(item.url);
        host.setText((item.offline != null ? "⤓ " : "") + (h == null ? item.url : h));
        host.setTextSize(12f);
        host.setTextColor(0xFF9AA0A6);
        texts.addView(title);
        texts.addView(host);
        r.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        Button del = new Button(this, null, android.R.attr.borderlessButtonStyle);
        del.setText("✕");
        del.setOnClickListener(v -> {
            Store.remove(this, tab, item.url);
            render();
        });
        r.addView(del);

        r.setOnClickListener(v -> open(item));
        return r;
    }

    private void open(Store.Item item) {
        if (item.offline != null) {
            startActivity(new Intent(this, ReaderActivity.class)
                    .setAction(ReaderActivity.ACTION_OFFLINE)
                    .putExtra(Intent.EXTRA_TEXT, item.url)
                    .putExtra(ReaderActivity.EXTRA_OFFLINE, item.offline));
        } else {
            startActivity(new Intent(this, ReaderActivity.class)
                    .setAction(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, item.url));
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.clear_all_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.clear_all, (d, w) -> {
                    Store.clear(this, tab);
                    render();
                    Toast.makeText(this, R.string.cleared, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private Button tabButton(String text, View.OnClickListener l) {
        Button b = new Button(this, null, android.R.attr.borderlessButtonStyle);
        b.setText(text);
        b.setOnClickListener(l);
        return b;
    }

    private int pad(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
