package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.statusbar.StatusBarLensPolicy.Growth;

/**
 * Hosts the single status-bar detail card. Exactly one is shown at a time; opening a new one
 * dismisses the previous. The card is a {@link PopupWindow} that drops beneath the status bar and
 * dismisses on an outside tap, on Back, or whenever {@link #dismiss()} is called (e.g. a window
 * change). Width is constrained to the portrait-screen bounds; the popup itself keeps the card on
 * screen vertically.
 *
 * <p>Every detail card opens in the same place — centred on the canvas across the bar's own axis
 * at {@link #STANDARD_WIDTH_DP}, just clear of the bar — regardless of which widget was tapped. The
 * bar's widgets are entry points to one shared surface, not owners of their own popups; a card that
 * jumped to sit under whichever icon happened to be hit would read as several unrelated windows.
 *
 * <p>Which side of the bar "just clear of it" is belongs to {@link StatusBarLensPolicy}: the card
 * grows <em>towards the middle of the screen</em>, so it drops below a top bar, rises off a bottom
 * one and opens inward off a column, and it slides in out of the bar it came from. A card that
 * always dropped downward fell off the bottom of the screen the moment the bar stood there.
 */
public final class StatusCardHost {

    /** One width for every detail card, so the stats, weather and future cards share a silhouette. */
    public static final int STANDARD_WIDTH_DP = 360;

    /** Supplies the current status-bar styling so the card matches Default glass or the capsule. */
    public interface StyleProvider {
        @NonNull Drawable cardBackground();
        float cornerRadiusPx();
        float contentInsetPx();
    }

    private static final long ENTER_DURATION_MS = 200L;
    private static final long EXIT_DURATION_MS = 180L;
    /** Thin, shared gap between the status bar's bottom edge and every card. */
    private static final int DROP_GAP_DP = 4;

    @Nullable private PopupWindow mPopup;
    @Nullable private View mAnchor;
    @Nullable private View mContent;
    @Nullable private View mContainer;
    @Nullable private View mDropEdge;
    @NonNull private Edge mEdge = Edge.TOP;

    /** The edge the status bar stands on; the card opens off it, towards the screen's middle. */
    public void setEdge(@NonNull Edge edge) {
        mEdge = edge;
    }

    @NonNull public Edge edge() { return mEdge; }

    /**
     * The surface whose bottom edge every card drops from — the status bar host. Anchors sit at
     * varying heights inside the bar (the status row ends above the bar's edge), so offsetting
     * from the anchor alone would give each widget's card a different, sometimes zero, gap.
     */
    public void setDropEdge(@Nullable View dropEdge) {
        mDropEdge = dropEdge;
    }

    public boolean isShowing() {
        return mPopup != null && mPopup.isShowing();
    }

    public boolean isShowingFor(@Nullable View anchor) {
        return isShowing() && mAnchor == anchor;
    }

    /**
     * Whether {@code content} is the view inside the open card. The toggle identity for cards that
     * several widgets open — CPU and RAM both lead to the stats card, and a toggle keyed on the
     * tapped widget would close-and-reopen the same card instead of just closing it.
     */
    public boolean isShowingContent(@Nullable View content) {
        return isShowing() && content != null && mContent == content;
    }

    /**
     * Show {@code content} in the standard card: {@link #STANDARD_WIDTH_DP} wide, centred beneath
     * the bar. Any currently open card is dismissed first. {@code onDismiss} runs when this card
     * goes away for any reason.
     */
    public void show(@NonNull View anchor, @NonNull View content, @NonNull StyleProvider style,
                     @Nullable Runnable onDismiss) {
        show(anchor, content, style, STANDARD_WIDTH_DP, false, true, onDismiss);
    }

    /**
     * Passive variant for surfaces that narrate an ongoing key chord: the card drops beneath the
     * bar with the standard styling and choreography, but the popup takes no focus and swallows
     * no outside touch — the keyboard hold that raised it keeps working underneath, and only the
     * owner ever dismisses it. Width wraps the content up to {@code desiredWidthDp}.
     */
    public void showPassive(@NonNull View anchor, @NonNull View content,
                            @NonNull StyleProvider style, int desiredWidthDp,
                            @Nullable Runnable onDismiss) {
        showPassive(anchor, content, style, desiredWidthDp, onDismiss, null);
    }

    /**
     * As above, additionally watching for a tap outside the card. The tap is only observed —
     * it still lands wherever it was aimed — so a sticky passive card can retire itself without
     * costing the user the touch.
     */
    public void showPassive(@NonNull View anchor, @NonNull View content,
                            @NonNull StyleProvider style, int desiredWidthDp,
                            @Nullable Runnable onDismiss, @Nullable Runnable onOutsideTap) {
        show(anchor, content, style, desiredWidthDp, true, false, onDismiss);
        PopupWindow popup = mPopup;
        if (popup == null || onOutsideTap == null) return;
        popup.setOutsideTouchable(true);
        popup.setTouchInterceptor((view, event) -> {
            if (event.getAction() != MotionEvent.ACTION_OUTSIDE) return false;
            onOutsideTap.run();
            return true;
        });
    }

    /** Re-measures the open popup after its content changed size in place. */
    public void refreshSize() {
        PopupWindow popup = mPopup;
        View container = mContainer;
        if (popup == null || container == null || !popup.isShowing()) return;
        container.requestLayout();
        popup.update();
    }

    private void show(@NonNull View anchor, @NonNull View content, @NonNull StyleProvider style,
                      int desiredWidthDp, boolean animate, boolean focusable,
                      @Nullable Runnable onDismiss) {
        dismiss();
        Context context = anchor.getContext();
        Growth growth = StatusBarLensPolicy.growthFor(mEdge);
        int maxWidth = Math.max(dp(context, 200),
            Math.min(portraitMaxWidthPx(context, desiredWidthDp), widthCapPx(anchor, growth)));

        FrameLayout container = new FrameLayout(context);
        final float radius = style.cornerRadiusPx();
        container.setBackground(style.cardBackground());
        container.setClipToOutline(true);
        container.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        // No elevation: the popup surface is exactly the card's bounding box, so a cast shadow
        // cannot fall outside it — it renders only inside the four corner notches beyond the
        // rounded arc and is clipped square at the window edge, which reads as tinted sharp
        // corners behind the card (issue #13). The 1dp outline stroke carries the edge instead.
        int pad = Math.round(style.contentInsetPx());
        container.setPadding(pad, pad, pad, pad);
        container.addView(content,
            new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        PopupWindow popup = new PopupWindow(container,
            focusable ? maxWidth : ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, focusable);
        popup.setBackgroundDrawable(new ColorDrawable(0));   // required for outside-touch dismissal
        popup.setOutsideTouchable(focusable);
        popup.setClippingEnabled(true);                      // keep the card inside screen bounds
        final Runnable dismissCallback = onDismiss;
        popup.setOnDismissListener(() -> {
            // An animated exit hands the window off before it actually goes away, so only the
            // still-current card may clear the fields; otherwise it would clobber its successor.
            if (mPopup == popup) {
                mPopup = null;
                mAnchor = null;
                mContent = null;
                mContainer = null;
            }
            if (dismissCallback != null) dismissCallback.run();
        });

        mPopup = popup;
        mAnchor = anchor;
        mContent = content;
        mContainer = container;

        if (animate) {
            popup.setAnimationStyle(0);
        }
        if (animate && focusable) {
            popup.setTouchInterceptor((view, event) -> {
                if (event.getAction() != MotionEvent.ACTION_OUTSIDE) return false;
                dismissAnimated();
                return true;
            });
            container.setFocusableInTouchMode(true);
            container.setOnKeyListener((view, keyCode, event) -> {
                if (keyCode != KeyEvent.KEYCODE_ESCAPE
                    || event.getAction() != KeyEvent.ACTION_UP) return false;
                dismissAnimated();
                return true;
            });
        }

        // Cards open centred on the canvas — the standard place, whichever widget was tapped —
        // and clear of the bar on the side the lens grows towards.
        int centeredWidth = maxWidth;
        if (!focusable || growth != Growth.DOWN) {
            // A wrap-width popup has no window width to centre on until it is measured, and a card
            // that opens anywhere but straight down has to know its own height to be placed at all.
            container.measure(
                View.MeasureSpec.makeMeasureSpec(maxWidth,
                    focusable ? View.MeasureSpec.EXACTLY : View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            centeredWidth = container.getMeasuredWidth();
        }
        if (growth == Growth.DOWN) {
            // The drop below a top bar is what every install already has: left on the platform's
            // own anchored path rather than re-derived, so the shipped bar is a zero-diff.
            int xOffset = windowCenteredXOffset(anchor, centeredWidth);
            popup.showAsDropDown(anchor, xOffset, dropYOffset(anchor), Gravity.START);
        } else {
            StatusBarLensPolicy.Placement placement = placeCard(anchor, growth, centeredWidth,
                container.getMeasuredHeight());
            popup.showAtLocation(anchor, Gravity.NO_GRAVITY, placement.x, placement.y);
        }
        if (animate) {
            if (focusable) container.requestFocus();
            animateIn(container, growth);
        }
    }

    /** How wide the card may be without covering the bar it grew out of; a row caps at nothing. */
    private int widthCapPx(@NonNull View anchor, @NonNull Growth growth) {
        if (StatusBarLensPolicy.isVertical(growth)) return Integer.MAX_VALUE;
        View bar = mDropEdge != null && mDropEdge.isAttachedToWindow() ? mDropEdge : anchor;
        int[] location = new int[2];
        bar.getLocationInWindow(location);
        View root = anchor.getRootView();
        int canvasRight = root != null && root.getWidth() > 0 ? root.getWidth()
            : anchor.getResources().getDisplayMetrics().widthPixels;
        Context context = anchor.getContext();
        return StatusBarLensPolicy.widthCapPx(growth, location[0], location[0] + bar.getWidth(),
            dp(context, DROP_GAP_DP), 0, canvasRight, dp(context, 12));
    }

    /**
     * The card's resting top-left in the window's own coordinates: the bar it grew out of, the
     * window it is clamped inside, and {@link #DROP_GAP_DP} of air between the two.
     */
    @NonNull
    private StatusBarLensPolicy.Placement placeCard(@NonNull View anchor, @NonNull Growth growth,
                                                    int cardWidth, int cardHeight) {
        View bar = mDropEdge != null && mDropEdge.isAttachedToWindow() ? mDropEdge : anchor;
        int[] location = new int[2];
        bar.getLocationInWindow(location);
        View root = anchor.getRootView();
        DisplayMetrics dm = anchor.getResources().getDisplayMetrics();
        int canvasRight = root != null && root.getWidth() > 0 ? root.getWidth() : dm.widthPixels;
        int canvasBottom = root != null && root.getHeight() > 0 ? root.getHeight() : dm.heightPixels;
        return StatusBarLensPolicy.card(growth, location[0], location[1],
            location[0] + bar.getWidth(), location[1] + bar.getHeight(),
            cardWidth, cardHeight, dp(anchor.getContext(), DROP_GAP_DP),
            0, 0, canvasRight, canvasBottom);
    }

    public void dismiss() {
        if (mPopup != null) {
            PopupWindow popup = mPopup;
            mPopup = null;   // guard against re-entrancy through the dismiss listener
            mAnchor = null;
            mContent = null;
            mContainer = null;
            popup.dismiss();
        }
    }

    /** Fade + lift the card away before dismissing it. Falls back to an instant dismiss. */
    public void dismissAnimated() {
        PopupWindow popup = mPopup;
        View container = mContainer;
        if (popup == null || container == null) {
            dismiss();
            return;
        }
        mPopup = null;
        mAnchor = null;
        mContent = null;
        mContainer = null;
        container.animate().cancel();
        Growth growth = StatusBarLensPolicy.growthFor(mEdge);
        float exit = dp(container.getContext(), 6);
        container.animate()
            .alpha(0f)
            .translationX(StatusBarLensPolicy.enterOffsetXPx(growth, exit))
            .translationY(StatusBarLensPolicy.enterOffsetYPx(growth, exit))
            .setDuration(EXIT_DURATION_MS)
            .setInterpolator(motionInterpolator())
            .withEndAction(popup::dismiss)
            .start();
    }

    private static void animateIn(@NonNull View container, @NonNull Growth growth) {
        float enter = dp(container.getContext(), 8);
        container.setAlpha(0f);
        container.setTranslationX(StatusBarLensPolicy.enterOffsetXPx(growth, enter));
        container.setTranslationY(StatusBarLensPolicy.enterOffsetYPx(growth, enter));
        container.animate()
            .alpha(1f)
            .translationX(0f)
            .translationY(0f)
            .setDuration(ENTER_DURATION_MS)
            .setInterpolator(motionInterpolator())
            .start();
    }

    private static Interpolator motionInterpolator() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            ? new PathInterpolator(0.16f, 1f, 0.3f, 1f)
            : new DecelerateInterpolator(1.8f);
    }

    /** {@link #DROP_GAP_DP} below the drop edge's bottom, or below the anchor without one. */
    private int dropYOffset(@NonNull View anchor) {
        int offset = dp(anchor.getContext(), DROP_GAP_DP);
        View edge = mDropEdge;
        if (edge != null && edge.isAttachedToWindow() && anchor.isAttachedToWindow()) {
            int[] location = new int[2];
            anchor.getLocationInWindow(location);
            int anchorBottom = location[1] + anchor.getHeight();
            edge.getLocationInWindow(location);
            int edgeBottom = location[1] + edge.getHeight();
            if (edgeBottom > anchorBottom) offset += edgeBottom - anchorBottom;
        }
        return offset;
    }

    private static int windowCenteredXOffset(@NonNull View anchor, int cardWidth) {
        // showAsDropDown aligns the popup's start to the anchor's start; shift so the card sits
        // centred in the window no matter where in the bar the tapped widget lives.
        int[] location = new int[2];
        anchor.getLocationInWindow(location);
        View root = anchor.getRootView();
        int windowWidth = root != null && root.getWidth() > 0 ? root.getWidth()
            : anchor.getResources().getDisplayMetrics().widthPixels;
        return (windowWidth - cardWidth) / 2 - location[0];
    }

    private static int portraitMaxWidthPx(@NonNull Context context, int desiredWidthDp) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int screenWidth = Math.min(dm.widthPixels, dm.heightPixels); // portrait width regardless of orientation
        int margin = dp(context, 12);
        int desired = dp(context, Math.max(200, desiredWidthDp));
        return Math.min(desired, Math.max(dp(context, 200), screenWidth - 2 * margin));
    }

    private static int dp(@NonNull Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    // Kept for callers that want to clamp custom content to the same visible portrait rect.
    public static void clampToPortrait(@NonNull Rect out, @NonNull Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        out.set(0, 0, Math.min(dm.widthPixels, dm.heightPixels), Math.max(dm.widthPixels, dm.heightPixels));
    }
}
