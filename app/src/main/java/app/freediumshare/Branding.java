package app.freediumshare;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * In-code branding overlays shown on loading screens. Each place gets a different animation.
 * Strings are hardcoded here on purpose (not in editable resources).
 */
final class Branding {

    static final String NAME = "Mr_Badasss";
    private static final int WHITE = 0xFFFFFFFF, MUTED = 0xFF9AA0A6;

    /** Selectable logo aesthetics; index 0 is the default. Names parallel the entries. */
    static final int[] MARKS = {
            R.drawable.ic_brand_mark,  // Unlocked content (default)
            R.drawable.ic_brand_key,   // Key
            R.drawable.ic_brand_book,  // Open book
            R.drawable.ic_brand_f,     // F monogram
    };
    static final String[] MARK_NAMES = {"Unlocked", "Key", "Reader", "Monogram"};

    private Branding() {}

    static int markRes(android.content.Context c) {
        int i = Prefs.logo(c);
        return (i >= 0 && i < MARKS.length) ? MARKS[i] : MARKS[0];
    }

    private static int dp(Activity a, float v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams row(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = top;
        return lp;
    }

    private static FrameLayout.LayoutParams centered(int w, int h) {
        return new FrameLayout.LayoutParams(w, h, Gravity.CENTER);
    }

    private static void attach(Activity a, View overlay) {
        FrameLayout root = a.findViewById(android.R.id.content);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    }

    /** Fades the overlay out and removes it (and stops any looping animator). */
    static void dismiss(View overlay) {
        if (overlay == null) return;
        overlay.animate().alpha(0f).setStartDelay(0).setDuration(380).withEndAction(() -> remove(overlay));
    }

    private static void remove(View overlay) {
        if (overlay.getTag() instanceof ObjectAnimator) ((ObjectAnimator) overlay.getTag()).cancel();
        if (overlay.getParent() instanceof ViewGroup) ((ViewGroup) overlay.getParent()).removeView(overlay);
    }

    /**
     * App-opening splash: green→black gradient, the mark springs in (overshoot), the wordmark
     * rises up. Self-dismisses.
     */
    static void showOpening(Activity a) {
        FrameLayout overlay = new FrameLayout(a);
        overlay.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF0B2E14, 0xFF0A0A0A}));
        overlay.setClickable(true);

        ImageView logo = new ImageView(a);
        logo.setBackgroundResource(R.drawable.circle_bg);
        logo.setImageResource(markRes(a));
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int p = dp(a, 15);
        logo.setPadding(p, p, p, p);

        TextView eyebrow = new TextView(a);
        eyebrow.setText("CONTRIBUTED BY");
        eyebrow.setTextColor(MUTED);
        eyebrow.setTextSize(11f);
        eyebrow.setLetterSpacing(0.28f);
        eyebrow.setGravity(Gravity.CENTER);

        TextView name = new TextView(a);
        name.setText(NAME);
        name.setTextColor(WHITE);
        name.setTextSize(26f);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setLetterSpacing(0.04f);
        name.setGravity(Gravity.CENTER);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(logo, new LinearLayout.LayoutParams(dp(a, 78), dp(a, 78)));
        col.addView(eyebrow, row(dp(a, 22)));
        col.addView(name, row(dp(a, 4)));

        overlay.addView(col, centered(-2, -2));
        attach(a, overlay);

        logo.setAlpha(0f);
        logo.setScaleX(0.7f);
        logo.setScaleY(0.7f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setInterpolator(new OvershootInterpolator()).setDuration(520).start();
        riseIn(a, eyebrow, 230);
        riseIn(a, name, 370);

        overlay.animate().alpha(0f).setStartDelay(1500).setDuration(480).withEndAction(() -> remove(overlay));
    }

    /**
     * Reader loading screen: dark backdrop, a ring spins around the mark, the wordmark slides in
     * from the left under an "unlocking…" line. Caller dismisses it when the article is ready.
     */
    static View showReaderLoading(Activity a) {
        FrameLayout overlay = new FrameLayout(a);
        overlay.setBackgroundColor(0xFF0E0E0E);
        overlay.setClickable(true);

        ImageView ring = new ImageView(a);
        ring.setImageResource(R.drawable.ic_ring);
        ImageView mark = new ImageView(a);
        mark.setImageResource(markRes(a));
        mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        FrameLayout ringWrap = new FrameLayout(a);
        ringWrap.addView(ring, centered(dp(a, 92), dp(a, 92)));
        ringWrap.addView(mark, centered(dp(a, 40), dp(a, 40)));

        TextView name = new TextView(a);
        name.setText(NAME);
        name.setTextColor(WHITE);
        name.setTextSize(22f);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setLetterSpacing(0.04f);
        name.setGravity(Gravity.CENTER);

        TextView sub = new TextView(a);
        sub.setText("unlocking…");
        sub.setTextColor(MUTED);
        sub.setTextSize(12f);
        sub.setLetterSpacing(0.2f);
        sub.setGravity(Gravity.CENTER);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(ringWrap, new LinearLayout.LayoutParams(dp(a, 92), dp(a, 92)));
        col.addView(name, row(dp(a, 26)));
        col.addView(sub, row(dp(a, 6)));

        overlay.addView(col, centered(-2, -2));
        attach(a, overlay);

        ObjectAnimator spin = ObjectAnimator.ofFloat(ring, "rotation", 0f, 360f);
        spin.setDuration(820);
        spin.setRepeatCount(ValueAnimator.INFINITE);
        spin.setInterpolator(new LinearInterpolator());
        spin.start();
        overlay.setTag(spin); // cancelled on dismiss

        name.setAlpha(0f);
        name.setTranslationX(-dp(a, 40));
        name.animate().alpha(1f).translationX(0f).setStartDelay(140).setDuration(460).start();
        sub.setAlpha(0f);
        sub.animate().alpha(1f).setStartDelay(340).setDuration(460).start();

        // Safety: never let the loading screen get stuck if the page never finishes.
        overlay.postDelayed(() -> dismiss(overlay), 8000);
        return overlay;
    }

    private static void riseIn(Activity a, View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(dp(a, 18));
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(430).start();
    }
}
