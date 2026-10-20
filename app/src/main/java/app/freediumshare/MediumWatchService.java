package app.freediumshare;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

/**
 * Watches only the Medium app. When a member-only article is on screen it shows a floating
 * "Read free" bubble; tapping it triggers Medium's own Share action so the article URL can be
 * handed to Freedium (the reader is the share target). Scoped to Medium + the share chooser;
 * screen text is read on-device only and never leaves the device.
 */
public class MediumWatchService extends AccessibilityService {

    private static final String MEDIUM = "com.medium.reader";
    private static final int NODE_BUDGET = 600;
    private static final long ARM_WINDOW_MS = 9000;

    private WindowManager windowManager;
    private View bubble;
    private WindowManager.LayoutParams params;
    private boolean overlayWarned;
    private long armedAt; // when the bubble was last tapped (for auto-picking our chooser entry)

    @Override
    protected void onServiceConnected() {
        windowManager = getSystemService(WindowManager.class);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;

        // Right after the bubble is tapped, auto-select our entry in the share chooser.
        if (armedAt > 0 && System.currentTimeMillis() - armedAt < ARM_WINDOW_MS && isChooser(pkg)) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                AccessibilityNodeInfo hit = findMatch(root, "read on freedium", new int[]{NODE_BUDGET});
                if (hit == null) hit = findMatch(root, "freedium", new int[]{NODE_BUDGET});
                if (hit != null) {
                    clickable(hit).performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    armedAt = 0;
                }
            }
            return;
        }

        if (!MEDIUM.contentEquals(pkg)) {
            hideBubble();
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        boolean memberOnly = findMatch(root, "member-only", new int[]{NODE_BUDGET}) != null;
        if (memberOnly) showBubble(); else hideBubble();
    }

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        hideBubble();
        return super.onUnbind(intent);
    }

    // --- bubble ---

    private void showBubble() {
        if (bubble != null) return;
        if (!Settings.canDrawOverlays(this)) {
            if (!overlayWarned) {
                overlayWarned = true;
                Toast.makeText(this, R.string.overlay_needed, Toast.LENGTH_LONG).show();
            }
            return;
        }
        bubble = LayoutInflater.from(this).inflate(R.layout.bubble, null);
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.x = dp(16);
        params.y = dp(96);
        bubble.setOnTouchListener(new DragClick());
        windowManager.addView(bubble, params);
    }

    private void hideBubble() {
        if (bubble == null) return;
        try {
            windowManager.removeView(bubble);
        } catch (Exception ignored) {
        }
        bubble = null;
    }

    /** Tapped: ask Medium to share the current article; the chooser hands the URL to Freedium. */
    private void onBubbleTap() {
        armedAt = System.currentTimeMillis();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo share = root == null ? null : findMatch(root, "share", new int[]{NODE_BUDGET});
        if (share != null) {
            clickable(share).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } else {
            Toast.makeText(this, R.string.tap_share_manually, Toast.LENGTH_LONG).show();
        }
    }

    // --- node search ---

    /** Depth-first search for a node whose text or description contains needle (lower-case). */
    private AccessibilityNodeInfo findMatch(AccessibilityNodeInfo node, String needle, int[] budget) {
        if (node == null || budget[0] <= 0) return null;
        budget[0]--;
        CharSequence t = node.getText();
        CharSequence d = node.getContentDescription();
        String s = ((t == null ? "" : t) + " " + (d == null ? "" : d)).toLowerCase();
        if (s.contains(needle)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo r = findMatch(node.getChild(i), needle, budget);
            if (r != null) return r;
        }
        return null;
    }

    private AccessibilityNodeInfo clickable(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo c = node;
        while (c != null && !c.isClickable()) c = c.getParent();
        return c != null ? c : node;
    }

    private boolean isChooser(CharSequence pkg) {
        String p = pkg.toString();
        return p.equals("android") || p.contains("intentresolver") || p.contains("resolver")
                || p.contains("sharesheet");
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Lets the bubble be dragged, and treats a small movement as a tap. */
    private final class DragClick implements View.OnTouchListener {
        private int startX, startY;
        private float touchX, touchY;
        private boolean moved;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = params.x;
                    startY = params.y;
                    touchX = e.getRawX();
                    touchY = e.getRawY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (e.getRawX() - touchX);
                    int dy = (int) (e.getRawY() - touchY);
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true;
                    // gravity is BOTTOM|END, so x grows leftward and y grows upward
                    params.x = Math.max(0, startX - dx);
                    params.y = Math.max(0, startY - dy);
                    windowManager.updateViewLayout(bubble, params);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) onBubbleTap();
                    return true;
                default:
                    return false;
            }
        }
    }
}
