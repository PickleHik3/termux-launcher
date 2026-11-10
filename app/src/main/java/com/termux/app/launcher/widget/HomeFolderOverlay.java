package com.termux.app.launcher.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.launcher.folder.FolderExpandMotion;
import com.termux.app.launcher.popup.AnchoredMenu;
import com.termux.app.launcher.popup.AnchoredMenuGeometry;

/**
 * The Home folder popup's layer: a view over the Home pane, inside the activity, not a window of
 * its own. The panel sits against its anchor as an anchored menu does
 * ({@link AnchoredMenuGeometry#anchoredPosition}, kept inside this layer) and grows out of the
 * anchor's icon and back into it ({@link FolderExpandMotion}). The layer is transparent: nothing
 * dims the pane.
 *
 * <p>Being a view of the activity is the point. A popup window over the activity takes every
 * touch outside it as {@code ACTION_OUTSIDE}, so while a folder was being renamed the in-app
 * keyboard below could never be pressed, and its dim darkened the keyboard too. This layer covers
 * the Home pane only: the in-app keyboard, the dock and the status bar stand outside it and over
 * it in z-order, and take their touches as ever.</p>
 *
 * <p>A tap on the pane outside the panel closes it and goes no further, so it opens
 * nothing under it; touches outside the pane are the owner's to judge
 * ({@link HomeFolderPopup#onWindowTouchDown}). While the layer is up it keeps the gesture it was
 * pressed in from the pane's own page and palette swipes.</p>
 */
final class HomeFolderOverlay extends FrameLayout {

    @NonNull private final FolderExpandMotion motion;
    @NonNull private final View panel;
    @NonNull private final View anchor;
    private final int panelWidth;
    private final int panelHeight;
    private final int gap;
    private final int[] lastPosition = {Integer.MIN_VALUE, Integer.MIN_VALUE};
    @Nullable private Runnable onDismissed;
    private boolean closing;
    private boolean finished;
    private boolean opened;
    private final int[] scratch = new int[2];

    HomeFolderOverlay(@NonNull Context context, @NonNull View panel, int panelWidth,
                      int panelHeight, @NonNull View anchor, int gap,
                      @Nullable Runnable onDismissed) {
        super(context);
        this.panel = panel;
        this.anchor = anchor;
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.gap = gap;
        this.onDismissed = onDismissed;
        this.motion = new FolderExpandMotion(panel);
        setClipChildren(false);
        addView(panel, new LayoutParams(panelWidth, panelHeight));
    }

    /** Puts the layer over {@code host}, on top of everything in it, and grows the panel out of its anchor. */
    void showIn(@NonNull ViewGroup host) {
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
        host.addView(this, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        closing = false;
        opened = false;
        // Hidden until the first layout has placed it and the motion has its geometry.
        panel.setAlpha(0f);
    }

    /** True from {@link #showIn} until a dismissal starts. */
    boolean isOpen() { return !closing && !finished && getParent() != null; }

    /** Shrinks the panel back into its anchor, then takes the layer away. */
    void dismiss() {
        if (finished || closing) return;
        closing = true;
        motion.close(this::finish);
    }

    /** Takes the layer away now, without the motion. */
    void dismissImmediate() {
        if (finished) return;
        closing = true;
        motion.cancel();
        finish();
    }

    /** Whether this screen point lies on the panel. */
    boolean panelContains(float rawX, float rawY) {
        return AnchoredMenu.isRawInsideView(panel, rawX, rawY);
    }

    @NonNull @VisibleForTesting View panel() { return panel; }

    // ---- Touch -------------------------------------------------------------------------------

    @Override public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // The pane above would otherwise read a drag across the dim as a page or palette swipe.
            ViewParent parent = getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        }
        // While it shrinks back, nothing under it or on it is pressed.
        if (closing) return true;
        return super.dispatchTouchEvent(event);
    }

    /** What the panel did not take: a press outside the panel closes the popup; all of it stops here. */
    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
            && !panelContains(event.getRawX(), event.getRawY())) {
            dismiss();
        }
        return true;
    }

    // ---- Layout ------------------------------------------------------------------------------

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        place();
    }

    /**
     * The panel against its anchor, as an anchored menu is placed, inside this layer's bounds:
     * re-run on every layout, so a keyboard rising under Home (the pane shrinking) keeps the panel
     * in sight.
     */
    private void place() {
        int width = Math.min(panelWidth, Math.max(0, getWidth()));
        int height = Math.min(panelHeight, Math.max(0, getHeight()));
        int[] self = new int[2];
        getLocationOnScreen(self);
        int x;
        int y;
        if (anchor.isAttachedToWindow() && anchor.getWidth() > 0) {
            int[] at = new int[2];
            anchor.getLocationOnScreen(at);
            Rect anchorRect = new Rect(at[0], at[1], at[0] + anchor.getWidth(),
                at[1] + anchor.getHeight());
            Rect frame = new Rect(self[0], self[1], self[0] + getWidth(), self[1] + getHeight());
            int[] xy = new int[2];
            AnchoredMenuGeometry.anchoredPosition(anchorRect, width, height,
                getResources().getDisplayMetrics().widthPixels, frame, gap, xy);
            x = xy[0] - self[0];
            y = xy[1] - self[1];
            lastPosition[0] = x;
            lastPosition[1] = y;
        } else if (lastPosition[0] != Integer.MIN_VALUE) {
            // The anchor went (its page was drawn again): the panel stays where it was.
            x = lastPosition[0];
            y = lastPosition[1];
        } else {
            x = (getWidth() - width) / 2;
            y = (getHeight() - height) / 2;
        }
        x = Math.max(0, Math.min(x, getWidth() - width));
        y = Math.max(0, Math.min(y, getHeight() - height));
        panel.layout(x, y, x + width, y + height);
        panel.setPivotX(width / 2f);
        panel.setPivotY(height / 2f);
        if (!opened && !closing) startOpen(self[0] + x, self[1] + y, width, height);
    }

    /**
     * Once, at the first placement: the motion takes the anchor's icon and the panel's rest rect
     * (both on screen) and grows the panel out of the one into the other. An anchor that is gone or
     * has no size leaves the motion its no-icon look.
     */
    private void startOpen(int panelLeft, int panelTop, int panelWidth, int panelHeight) {
        opened = true;
        if (anchor.isAttachedToWindow() && anchor.getWidth() > 0 && anchor.getHeight() > 0) {
            anchor.getLocationOnScreen(scratch);
            motion.setGeometry(scratch[0], scratch[1], scratch[0] + anchor.getWidth(),
                scratch[1] + anchor.getHeight(), panelLeft, panelTop, panelLeft + panelWidth,
                panelTop + panelHeight);
        } else {
            motion.setFallbackGeometry();
        }
        motion.open();
    }

    private void finish() {
        if (finished) return;
        finished = true;
        motion.cancel();
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
        Runnable listener = onDismissed;
        onDismissed = null;
        if (listener != null) listener.run();
    }
}
