package app.freediumshare;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
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
 * "Read free" bubble. One tap: the service opens Medium's Share sheet and clicks "Copy link",
 * then launches the Freedium reader, which reads the copied URL — no chooser to navigate.
 * Falls back to clicking our own share entry, then to a manual prompt. Screen text is read
 * on-device only and never leaves the device.
 */
public class MediumWatchService extends AccessibilityService {

    private static final String MEDIUM = "com.medium.reader";
    private static final int NODE_BUDGET = 700;
    private static final long SHARE_WINDOW_MS = 9000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View bubble;
    private WindowManager.LayoutParams params;
    private boolean overlayWarned;

    private boolean awaitingShareSheet; // true after a bubble tap, until we act on the share sheet
    private long shareStartedAt;

    @Override
    protected void onServiceConnected() {
        windowManager = getSystemService(WindowManager.class);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;

        // After a tap we wait for Medium's share sheet, then copy the link (or pick our entry).
        if (awaitingShareSheet) {
            if (System.currentTimeMillis() - shareStartedAt > SHARE_WINDOW_MS) {
                awaitingShareSheet = false;
            } else if (handleShareSheet()) {
                return;
            }
        }

        if (!MEDIUM.contentEquals(pkg)) {
            if (!awaitingShareSheet) hideBubble();
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
    public boolean onUnbind(Intent intent) {
        hideBubble();
        return super.onUnbind(intent);
    }

    // --- one-tap share handling ---

    /** Tapped: open Medium's Share so a sheet with "Copy link" / our entry appears. */
    private void onBubbleTap() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo share = root == null ? null : findMatch(root, "share", new int[]{NODE_BUDGET});
        if (share != null) {
            awaitingShareSheet = true;
            shareStartedAt = System.currentTimeMillis();
            clickable(share).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } else {
            Toast.makeText(this, R.string.tap_share_manually, Toast.LENGTH_LONG).show();
        }
    }

    /** Returns true once it has acted on the share sheet (copied the link or picked our app). */
    private boolean handleShareSheet() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        // Preferred: "Copy link" / "Copy" puts the URL on the clipboard with no further choosing.
        AccessibilityNodeInfo copy = findMatch(root, "copy link", new int[]{NODE_BUDGET});
        if (copy == null) copy = findMatch(root, "copy", new int[]{NODE_BUDGET});
        if (copy != null) {
            clickable(copy).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            awaitingShareSheet = false;
            main.postDelayed(this::launchReaderFromClipboard, 350);
            return true;
        }

        // Fallback: pick our own entry in the chooser.
        AccessibilityNodeInfo mine = findMatch(root, "read on freedium", new int[]{NODE_BUDGET});
        if (mine == null) mine = findMatch(root, "freedium", new int[]{NODE_BUDGET});
        if (mine != null) {
            clickable(mine).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            awaitingShareSheet = false;
            return true;
        }
        return false; // sheet not ready yet; wait for the next event
    }

    private void launchReaderFromClipboard() {
        startActivity(new Intent(this, ReaderActivity.class)
                .setAction(ReaderActivity.ACTION_FROM_CLIPBOARD)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
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

    // --- node search ---

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
                    params.x = Math.max(0, startX - dx); // gravity END: x grows leftward
                    params.y = Math.max(0, startY - dy); // gravity BOTTOM: y grows upward
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
