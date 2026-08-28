package com.termux.app.launcher.popup;

import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mmin18.widget.RealtimeBlurView;

import java.util.Collections;
import java.util.List;

/**
 * One anchored glass panel: it builds the window from a {@link MenuSpec}, places it against an
 * anchor (or beside a sibling menu), tracks the rows it is showing, and takes it away again with the
 * house dismiss animation.
 *
 * <p>An instance is a slot, not a window — it holds at most one live {@link PopupWindow} and a host
 * keeps one instance per menu it can show. All the sizing and placement arithmetic lives in
 * {@link AnchoredMenuGeometry}; the material (corner radius, glass rim, blur, elevation) and the
 * window flags live here, so every launcher popup is the same surface.
 */
public final class AnchoredMenu {

    /** Shortest a panel may be, in dp: a one-row menu still reads as a panel. */
    private static final int MIN_PANEL_HEIGHT_DP = 36;
    private static final int PANEL_CORNER_DP = 14;
    private static final int RIM_COLOR = 0x3DFFFFFF;
    private static final float ELEVATION = 8f;

    @NonNull private final View host;
    @NonNull private final AnchoredMenuTheme theme;

    @Nullable private PopupWindow window;
    @NonNull private List<MenuRow> rows = Collections.emptyList();

    public AnchoredMenu(@NonNull View host, @NonNull AnchoredMenuTheme theme) {
        this.host = host;
        this.theme = theme;
    }

    // ------------------------------------------------------------------ state

    /** The live window, or null between dismissal completing and the next show. */
    @Nullable
    public PopupWindow window() {
        return window;
    }

    public boolean isShowing() {
        return window != null && window.isShowing();
    }

    /** The rows of the menu currently on screen, top to bottom; empty for bespoke content. */
    @NonNull
    public List<MenuRow> rows() {
        return rows;
    }

    // ------------------------------------------------------------------ showing

    /** Builds the panel and places it against {@code anchor}, or bottom-centred if there is none. */
    @NonNull
    public PopupWindow show(@NonNull MenuSpec spec, @Nullable View anchor) {
        PopupWindow popup = build(spec, true);
        window = popup;
        rows = spec.rows;
        placeAtAnchor(popup, anchor, spec.animateEntry);
        return popup;
    }

    /**
     * Builds the panel and places it beside {@code mainMenu}, vertically centred on the row that
     * opened it. Falls back to plain anchoring when the main menu has no content view to align to.
     */
    @NonNull
    public PopupWindow showAlignedToRow(@NonNull MenuSpec spec, @NonNull View rowAnchor,
                                        @NonNull AnchoredMenu mainMenu) {
        PopupWindow popup = build(spec, true);
        window = popup;
        rows = spec.rows;
        PopupWindow main = mainMenu.window;
        View mainRoot = main == null ? null : main.getContentView();
        if (mainRoot == null) {
            placeAtAnchor(popup, rowAnchor, spec.animateEntry);
            return popup;
        }
        int screenW = host.getResources().getDisplayMetrics().widthPixels;
        int screenH = host.getResources().getDisplayMetrics().heightPixels;
        int[] mainLoc = new int[2];
        int[] rowLoc = new int[2];
        mainRoot.getLocationOnScreen(mainLoc);
        rowAnchor.getLocationOnScreen(rowLoc);
        int[] xy = new int[2];
        AnchoredMenuGeometry.sideAlignedPosition(mainLoc[0], main.getWidth(),
            rowLoc[1] + (rowAnchor.getHeight() / 2), popup.getWidth(), popup.getHeight(),
            screenW, screenH, dp(AnchoredMenuGeometry.GAP_DP), xy);
        popup.showAtLocation(host, Gravity.NO_GRAVITY, xy[0], xy[1]);
        View root = popup.getContentView();
        if (root != null) {
            root.setAlpha(0f);
            root.setTranslationX(xy[0] >= mainLoc[0] ? dp(6) : -dp(6));
            root.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(140L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        }
        return popup;
    }

    /**
     * Builds the panel but hands the window over untracked, for a caller that runs its own window
     * lifecycle (the folder popup's spring transition and dim). Such a caller owns dismissal and
     * replaces the dismiss listener, so this menu deliberately keeps no reference to it.
     */
    @NonNull
    public PopupWindow buildDetached(@NonNull MenuSpec spec) {
        return build(spec, false);
    }

    /**
     * Places {@code popup} against {@code anchor}. Public so a detached owner can reuse the exact
     * placement policy the tracked path uses.
     */
    public void placeAtAnchor(@NonNull PopupWindow popup, @Nullable View anchor, boolean animate) {
        int screenW = host.getResources().getDisplayMetrics().widthPixels;
        int screenH = host.getResources().getDisplayMetrics().heightPixels;
        Rect visibleFrame = new Rect(0, 0, screenW, screenH);
        host.getWindowVisibleDisplayFrame(visibleFrame);
        if (visibleFrame.isEmpty()) {
            visibleFrame.set(0, 0, screenW, screenH);
        }
        int gap = dp(AnchoredMenuGeometry.GAP_DP);
        if (anchor != null) {
            int[] location = new int[2];
            anchor.getLocationOnScreen(location);
            Rect anchorRect = new Rect(location[0], location[1],
                location[0] + anchor.getWidth(), location[1] + anchor.getHeight());
            int[] xy = new int[2];
            AnchoredMenuGeometry.anchoredPosition(anchorRect, popup.getWidth(), popup.getHeight(),
                screenW, visibleFrame, gap, xy);
            popup.showAtLocation(host, Gravity.NO_GRAVITY, xy[0], xy[1]);
        } else {
            popup.showAtLocation(host, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0,
                host.getHeight() + gap);
        }
        View root = popup.getContentView();
        if (root != null && animate) {
            root.setAlpha(0f);
            root.setTranslationY(dp(8));
            root.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(150)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        }
    }

    // ------------------------------------------------------------------ dismissing

    /**
     * Takes the panel away with the house fade-and-drop, then releases the window. The menu keeps
     * reporting itself as showing until the animation finishes, which is what makes a re-entrant
     * dismiss a no-op rather than a second window teardown.
     */
    public void dismiss() {
        rows = Collections.emptyList();
        PopupWindow popup = window;
        if (popup == null) return;
        dismissAnimated(popup, () -> {
            if (window == popup) window = null;
        });
    }

    /** Dismisses {@code popup} with the house animation. Static so a detached owner can share it. */
    public void dismissAnimated(@NonNull PopupWindow popup, @Nullable Runnable onDone) {
        View content = popup.getContentView();
        if (content != null && popup.isShowing()) {
            content.animate()
                .alpha(0f)
                .translationY(dp(6))
                .setDuration(110)
                .withEndAction(() -> {
                    try {
                        popup.dismiss();
                    } catch (Exception ignored) {
                    }
                    if (onDone != null) onDone.run();
                })
                .start();
        } else {
            try {
                popup.dismiss();
            } catch (Exception ignored) {
            }
            if (onDone != null) onDone.run();
        }
    }

    // ------------------------------------------------------------------ hit testing

    /** Squared distance from a raw screen point to this panel; {@code MAX_VALUE} when not showing. */
    public float squaredDistanceTo(float rawX, float rawY) {
        if (!isShowing()) return Float.MAX_VALUE;
        Rect bounds = new Rect();
        if (!screenRect(window.getContentView(), bounds)) return Float.MAX_VALUE;
        return AnchoredMenuGeometry.squaredDistanceTo(bounds, rawX, rawY);
    }

    /** The on-screen rect of {@code view}, or false when it has not been laid out. */
    public static boolean screenRect(@Nullable View view, @NonNull Rect outRect) {
        if (view == null || outRect == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return false;
        }
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        outRect.set(loc[0], loc[1], loc[0] + view.getWidth(), loc[1] + view.getHeight());
        return true;
    }

    public static boolean isRawInsideView(@Nullable View view, float rawX, float rawY) {
        Rect bounds = new Rect();
        if (!screenRect(view, bounds)) return false;
        return rawX >= bounds.left && rawX <= bounds.right
            && rawY >= bounds.top && rawY <= bounds.bottom;
    }

    // ------------------------------------------------------------------ construction

    @NonNull
    private PopupWindow build(@NonNull MenuSpec spec, boolean tracked) {
        View content = spec.content;
        float density = host.getResources().getDisplayMetrics().density;
        int screenW = host.getResources().getDisplayMetrics().widthPixels;
        int screenH = host.getResources().getDisplayMetrics().heightPixels;
        int maxWidth = AnchoredMenuGeometry.maxWidth(screenW, density);
        int minWidth = AnchoredMenuGeometry.minWidth(screenW, spec.tightWrap, density);
        int maxHeight = AnchoredMenuGeometry.maxHeight(screenH, density);
        int fixedWidth = spec.requestedWidth > 0
            ? AnchoredMenuGeometry.clamp(spec.requestedWidth, minWidth, maxWidth) : -1;

        // Measure the full vertical content before imposing the popup viewport. Measuring the
        // content itself with AT_MOST can clamp a LinearLayout before it enters the ScrollView,
        // leaving trailing action rows laid out beyond its reported bounds and therefore clipped.
        content.measure(
            fixedWidth > 0
                ? View.MeasureSpec.makeMeasureSpec(fixedWidth, View.MeasureSpec.EXACTLY)
                : View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int desiredWidth = fixedWidth > 0
            ? fixedWidth
            : AnchoredMenuGeometry.clamp(content.getMeasuredWidth(), minWidth, maxWidth);
        int desiredHeight = Math.max(dp(MIN_PANEL_HEIGHT_DP),
            Math.min(content.getMeasuredHeight(), maxHeight));

        ScrollView scrollView = new ScrollView(host.getContext());
        scrollView.setFillViewport(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setVerticalScrollBarEnabled(spec.showVerticalScrollbar);
        scrollView.setScrollbarFadingEnabled(false);
        scrollView.setFadingEdgeLength(dp(18));
        scrollView.setVerticalFadingEdgeEnabled(true);
        scrollView.addView(content, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout popupRoot = new FrameLayout(host.getContext());
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setCornerRadius(dp(PANEL_CORNER_DP));
        int alpha = AnchoredMenuGeometry.clamp(
            Math.max(theme.opacityPercent(), spec.minimumOpacityPercent), 0, 100);
        int overlayColor = (((int) (255f * (alpha / 100f))) << 24) | (spec.tintBase & 0x00FFFFFF);
        panelBg.setColor(overlayColor);
        // Glass rim: the same hairline the drawer plane and FULL pane draw, so every elevated
        // surface reads as the one material family.
        panelBg.setStroke(Math.max(1, Math.round(density * 1.25f)), RIM_COLOR);
        popupRoot.setBackground(panelBg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popupRoot.setClipToOutline(true);
        }
        if (theme.blurEnabled() && theme.blurRadiusDp() > 0) {
            RealtimeBlurView blurView = new RealtimeBlurView(host.getContext());
            blurView.setBlurRadius(Math.max(0f, (float) dp(theme.blurRadiusDp())));
            blurView.setOverlayColor(overlayColor);
            popupRoot.addView(blurView, new FrameLayout.LayoutParams(desiredWidth, desiredHeight));
        }
        popupRoot.addView(scrollView, new FrameLayout.LayoutParams(desiredWidth, desiredHeight));

        PopupWindow popup = new PopupWindow(popupRoot, desiredWidth, desiredHeight, false);
        popup.setFocusable(false);
        popup.setTouchable(true);
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        popup.setBackgroundDrawable(new ColorDrawable(0x00000000));
        popup.setElevation(ELEVATION);
        Runnable onDismiss = spec.onDismiss;
        popup.setOnDismissListener(() -> {
            if (tracked && window == popup && !popup.isShowing()) {
                window = null;
                rows = Collections.emptyList();
            }
            if (onDismiss != null) onDismiss.run();
        });
        return popup;
    }

    private int dp(int value) {
        return Math.round(value * host.getResources().getDisplayMetrics().density);
    }
}
