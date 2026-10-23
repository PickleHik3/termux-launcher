package com.termux.app.launcher.popup;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.mmin18.widget.RealtimeBlurView;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.termux.app.chrome.ChromeShade;
import com.termux.app.material.M3;

import java.util.Collections;
import java.util.List;

/**
 * One anchored glass panel: it builds the window from a {@link MenuSpec}, places it against an
 * anchor (or beside a sibling menu), tracks the rows it is showing, and takes it away again with the
 * theme's popup transitions.
 *
 * <p>An instance is a slot, not a window — it holds at most one live {@link PopupWindow} and a host
 * keeps one instance per menu it can show. All the sizing and placement arithmetic lives in
 * {@link AnchoredMenuGeometry}; the material (corner radius, glass rim, blur, elevation) and the
 * window flags live here, so every launcher popup is the same surface.
 *
 * <p>The App menu is the exception to that surface: {@link #showAppMenu} shows an
 * {@link AppMenuCard} (grouped rows in the palette's shades, an arrow to the icon, no glass) placed
 * by {@link AppMenuGeometry}, and {@link #dismiss()} runs its close before the window goes.
 */
public final class AnchoredMenu {

    /** Shortest a panel may be, in dp: a one-row menu still reads as a panel. */
    private static final int MIN_PANEL_HEIGHT_DP = 36;

    /** The App menu's motion, Launcher3's numbers: scale in from the arrow, rows fade in. */
    static final long APP_MENU_OPEN_MS = 200L;
    static final long APP_MENU_ROW_FADE_MS = 83L;
    static final long APP_MENU_CLOSE_MS = 233L;
    /** The card's scale at the start of the open and the end of the close. */
    static final float APP_MENU_REST_SCALE = 0.6f;
    private static final Interpolator EMPHASIZED_ACCELERATE =
        new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);
    /** M3's emphasized decelerate, run a few percent past the end and back: the slight overshoot. */
    private static final Interpolator OPEN_SCALE_CURVE = new PathInterpolator(openScalePath());
    private static final Interpolator LINEAR = new LinearInterpolator();

    @NonNull private final View host;
    @NonNull private final AnchoredMenuTheme theme;

    @Nullable private PopupWindow window;
    @NonNull private List<MenuRow> rows = Collections.emptyList();
    /** The App menu's open, so a close that starts mid-open takes over from where it is. */
    @Nullable private Animator appMenuOpen;

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
     * Shows the App menu {@code card} against {@code anchor}: above the icon, or below it when
     * there is no room, its arrow on the edge facing the icon, scaling out of the arrow's point.
     * Closing (any {@link #dismiss()}, or a touch outside) runs the close before the window goes.
     */
    @NonNull
    public PopupWindow showAppMenu(@NonNull AppMenuCard card, @NonNull View anchor,
                                   @Nullable Runnable onDismiss) {
        float density = host.getResources().getDisplayMetrics().density;
        Rect frame = visibleFrame();
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        Rect anchorRect = new Rect(location[0], location[1],
            location[0] + anchor.getWidth(), location[1] + anchor.getHeight());
        int shadowRoom = AnchoredMenuGeometry.dp(density, AppMenuGeometry.SHADOW_ROOM_DP);
        int arrowWidth = AnchoredMenuGeometry.dp(density, AppMenuGeometry.ARROW_WIDTH_DP);
        int arrowHeight = AnchoredMenuGeometry.dp(density, AppMenuGeometry.ARROW_HEIGHT_DP);
        // The arrow stays off the edge's rounding: the outer radius, or a lone row's pill end.
        int edgeRadius = Math.max(AppMenuGeometry.OUTER_RADIUS_DP, AppMenuGeometry.ROW_HEIGHT_DP / 2);
        int arrowInset = AnchoredMenuGeometry.dp(density, edgeRadius) + arrowWidth / 2;
        AppMenuGeometry.Placement placement = new AppMenuGeometry.Placement();
        AppMenuGeometry.place(anchorRect, frame, card.width, card.measureContentHeight(),
            arrowWidth, arrowHeight, shadowRoom,
            AnchoredMenuGeometry.dp(density, AppMenuGeometry.ANCHOR_GAP_DP),
            AnchoredMenuGeometry.dp(density, AppMenuGeometry.EDGE_MARGIN_DP), arrowInset, placement);
        AppMenuCardView content = card.view;
        content.applyPlacement(placement, shadowRoom, arrowWidth, arrowHeight,
            card.arrowShade(placement.arrowOnTop));

        PopupWindow popup = new PopupWindow(content, placement.windowWidth,
            placement.windowHeight, false);
        // No plate, no glass: the rows are the surface, and the window only holds them.
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(0f);
        popup.setAnimationStyle(0);
        popup.setClippingEnabled(false);
        popup.setTouchable(true);
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
        popup.setTouchInterceptor((v, event) -> {
            // A touch outside closes the card with its own motion rather than the window's cut.
            if (event.getActionMasked() != MotionEvent.ACTION_OUTSIDE) return false;
            if (window == popup) {
                dismiss();
            }
            return true;
        });
        popup.setOnDismissListener(() -> {
            if (window == popup) {
                window = null;
                rows = Collections.emptyList();
            }
            if (onDismiss != null) onDismiss.run();
        });
        window = popup;
        rows = card.rows;
        popup.showAtLocation(host, Gravity.TOP | Gravity.LEFT, placement.x, placement.y);
        animateAppMenuOpen(content, placement.pivotX, placement.pivotY);
        return popup;
    }

    private void animateAppMenuOpen(@NonNull View content, float pivotX, float pivotY) {
        content.setPivotX(pivotX);
        content.setPivotY(pivotY);
        content.setScaleX(APP_MENU_REST_SCALE);
        content.setScaleY(APP_MENU_REST_SCALE);
        content.setAlpha(0f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(content, View.SCALE_X, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(content, View.SCALE_Y, 1f);
        scaleX.setDuration(APP_MENU_OPEN_MS);
        scaleY.setDuration(APP_MENU_OPEN_MS);
        scaleX.setInterpolator(OPEN_SCALE_CURVE);
        scaleY.setInterpolator(OPEN_SCALE_CURVE);
        ObjectAnimator fade = ObjectAnimator.ofFloat(content, View.ALPHA, 1f);
        fade.setDuration(APP_MENU_ROW_FADE_MS);
        fade.setInterpolator(LINEAR);
        AnimatorSet open = new AnimatorSet();
        open.playTogether(scaleX, scaleY, fade);
        appMenuOpen = open;
        open.start();
    }

    private void animateAppMenuClose(@NonNull PopupWindow popup, @NonNull View content) {
        if (appMenuOpen != null) {
            appMenuOpen.cancel();
            appMenuOpen = null;
        }
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(content, View.SCALE_X, APP_MENU_REST_SCALE);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(content, View.SCALE_Y, APP_MENU_REST_SCALE);
        ObjectAnimator fade = ObjectAnimator.ofFloat(content, View.ALPHA, 0f);
        AnimatorSet close = new AnimatorSet();
        close.playTogether(scaleX, scaleY, fade);
        close.setDuration(APP_MENU_CLOSE_MS);
        close.setInterpolator(EMPHASIZED_ACCELERATE);
        close.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                try {
                    popup.dismiss();
                } catch (Exception ignored) {
                }
            }
        });
        close.start();
    }

    @NonNull
    private static Path openScalePath() {
        Path path = new Path();
        path.moveTo(0f, 0f);
        path.cubicTo(0.05f, 0.7f, 0.2f, 1.03f, 0.5f, 1.03f);
        path.cubicTo(0.7f, 1.03f, 0.85f, 1f, 1f, 1f);
        return path;
    }

    @NonNull
    private Rect visibleFrame() {
        int screenW = host.getResources().getDisplayMetrics().widthPixels;
        int screenH = host.getResources().getDisplayMetrics().heightPixels;
        Rect visibleFrame = new Rect(0, 0, screenW, screenH);
        host.getWindowVisibleDisplayFrame(visibleFrame);
        if (visibleFrame.isEmpty()) {
            visibleFrame.set(0, 0, screenW, screenH);
        }
        return visibleFrame;
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
        popup.showAtLocation(host, Gravity.TOP | Gravity.LEFT, xy[0], xy[1]);
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
     * placement policy the tracked path uses. {@code animate} is kept for callers' signatures: the
     * enter animation is the theme's popup transition, the same for every menu.
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
            popup.showAtLocation(host, Gravity.TOP | Gravity.LEFT, xy[0], xy[1]);
        } else {
            popup.showAtLocation(host, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0,
                host.getHeight() + gap);
        }
    }

    // ------------------------------------------------------------------ dismissing

    /**
     * Takes the panel away (the theme's popup exit transition), then releases the window.
     */
    public void dismiss() {
        rows = Collections.emptyList();
        PopupWindow popup = window;
        if (popup == null) return;
        View content = popup.getContentView();
        if (content instanceof AppMenuCardView && popup.isShowing()) {
            // The card is no longer this slot's menu the moment it starts closing, so a new one
            // can open over it and nothing highlights a row that is on its way out.
            window = null;
            animateAppMenuClose(popup, content);
            return;
        }
        dismissAnimated(popup, () -> {
            if (window == popup) window = null;
        });
    }

    /** Dismisses {@code popup}; the exit is the theme's popup transition. Shared with detached owners. */
    public void dismissAnimated(@NonNull PopupWindow popup, @Nullable Runnable onDone) {
        try {
            popup.dismiss();
        } catch (Exception ignored) {
        }
        if (onDone != null) onDone.run();
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

        PopupWindow popup = new PopupWindow(host.getContext());
        popup.setWidth(desiredWidth);
        popup.setHeight(desiredHeight);
        if (theme.blurEnabled() && theme.blurRadiusDp() > 0) {
            // The optional glass plate: a blur under a tinted medium-corner surface. The plain
            // path below is the stock menu surface.
            FrameLayout popupRoot = new FrameLayout(host.getContext());
            int alpha = AnchoredMenuGeometry.clamp(
                Math.max(theme.opacityPercent(), spec.minimumOpacityPercent), 0, 100);
            int overlayColor = (((int) (255f * (alpha / 100f))) << 24)
                | (spec.tintBase & 0x00FFFFFF);
            MaterialShapeDrawable plate = M3.surface(host.getContext(),
                com.google.android.material.R.attr.shapeAppearanceCornerMedium, overlayColor);
            plate.setStroke(host.getResources().getDimension(
                    com.google.android.material.R.dimen.m3_comp_outlined_card_outline_width),
                ColorStateList.valueOf(ChromeShade.rim(M3.stateLayer(M3.onSurface(host.getContext()),
                    M3.STATE_DRAGGED))));
            popupRoot.setBackground(plate);
            popupRoot.setClipToOutline(true);
            RealtimeBlurView blurView = new RealtimeBlurView(host.getContext());
            blurView.setBlurRadius(Math.max(0f, (float) dp(theme.blurRadiusDp())));
            blurView.setOverlayColor(overlayColor);
            popupRoot.addView(blurView, new FrameLayout.LayoutParams(desiredWidth, desiredHeight));
            // The dock's grain over the blur and under the rows, the same static tile every glass
            // surface lays (the plate's outline clips it).
            int grain = theme.grainPercent();
            if (grain > 0) {
                View grainView = new View(host.getContext());
                grainView.setBackground(com.termux.app.DockGlassRendering.createGrainLayer(
                    host.getResources(), grain));
                popupRoot.addView(grainView,
                    new FrameLayout.LayoutParams(desiredWidth, desiredHeight));
            }
            popupRoot.addView(scrollView, new FrameLayout.LayoutParams(desiredWidth, desiredHeight));
            popup.setContentView(popupRoot);
            popup.setBackgroundDrawable(null);
            popup.setElevation(host.getResources().getDimension(
                com.google.android.material.R.dimen.m3_comp_menu_container_elevation));
        } else {
            popup.setContentView(scrollView);
            M3.styleMenuPopup(host.getContext(), popup);
            // The plain surface honours the user's menu opacity preference too.
            android.graphics.drawable.Drawable surface = popup.getBackground();
            if (surface != null) {
                int plainAlpha = AnchoredMenuGeometry.clamp(
                    Math.max(theme.opacityPercent(), spec.minimumOpacityPercent), 0, 100);
                surface.setAlpha(Math.round(255f * (plainAlpha / 100f)));
            }
        }
        popup.setFocusable(false);
        popup.setTouchable(true);
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
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
