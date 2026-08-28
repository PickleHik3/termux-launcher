package com.termux.app.launcher.popup;

import android.graphics.Rect;

import androidx.annotation.NonNull;

/**
 * The whole placement/sizing policy of the launcher's anchored menus, as pure arithmetic.
 *
 * <p>Nothing here touches a window, so every rule the menus obey — the 320dp cap, the 45%-of-screen
 * height ceiling, the thirds-based horizontal alignment, the flip-under-a-top-anchor escape and the
 * final clamp into the visible frame — is testable without inflating anything.
 */
public final class AnchoredMenuGeometry {

    /** Widest a menu panel may ever be, in dp, before the screen-relative caps apply. */
    public static final int MAX_WIDTH_DP = 320;
    /** Width a non-tight-wrapping menu is padded out to, in dp. */
    public static final int MIN_WIDTH_DP = 188;
    /** Fraction of screen height a menu panel may occupy. */
    public static final float MAX_HEIGHT_FACTOR = 0.45f;
    /** Gap between an anchor (or a sibling menu) and the menu panel, in dp. */
    public static final int GAP_DP = 4;

    private AnchoredMenuGeometry() {
    }

    /** dp -> px with the host's density, rounded the same way the launcher's views round it. */
    public static int dp(float density, int value) {
        return Math.round(value * density);
    }

    public static int maxWidth(int screenW, float density) {
        return Math.min(screenW - dp(density, 24),
            Math.min(dp(density, MAX_WIDTH_DP), (int) (screenW * 0.9f)));
    }

    public static int minWidth(int screenW, boolean tightWrap, float density) {
        return Math.min(maxWidth(screenW, density), dp(density, tightWrap ? 0 : MIN_WIDTH_DP));
    }

    public static int maxHeight(int screenH, float density) {
        return Math.min(screenH - dp(density, 80), (int) (screenH * MAX_HEIGHT_FACTOR));
    }

    /**
     * Where a menu of {@code popupWidth} x {@code popupHeight} goes for an anchor whose on-screen
     * rect is {@code anchor}.
     *
     * <p>Horizontally the menu aligns to the anchor's side of the screen — a left-third anchor
     * left-aligns, a right-third anchor right-aligns, anything between centres over the anchor — so
     * the menu opens away from the nearest edge. Vertically it opens upward, which is right for a
     * dock icon and impossible for a first-row drawer cell: when there is no room above, it flips
     * under the anchor rather than being clamped on top of it. Both axes are then clamped into
     * {@code visibleFrame}.
     */
    public static void anchoredPosition(
        @NonNull Rect anchor,
        int popupWidth,
        int popupHeight,
        int screenW,
        @NonNull Rect visibleFrame,
        int gap,
        @NonNull int[] outXY
    ) {
        int anchorCenterX = anchor.left + (anchor.width() / 2);
        int third = screenW / 3;
        int x;
        if (anchorCenterX <= third) {
            x = anchor.left;
        } else if (anchorCenterX >= (screenW - third)) {
            x = anchor.left + anchor.width() - popupWidth;
        } else {
            x = anchorCenterX - (popupWidth / 2);
        }
        int y = anchor.top - popupHeight - gap;
        if (y < visibleFrame.top) {
            y = anchor.top + anchor.height() + gap;
        }
        outXY[0] = clamp(x, visibleFrame.left,
            Math.max(visibleFrame.left, visibleFrame.right - popupWidth));
        outXY[1] = clamp(y, visibleFrame.top,
            Math.max(visibleFrame.top, visibleFrame.bottom - popupHeight));
    }

    /**
     * Where a side menu goes so that it sits beside {@code mainLeft}/{@code mainWidth} and is
     * vertically centred on the row that opened it. Prefers the right of the main panel, falls back
     * to its left when the right would overflow the screen and the left would not.
     */
    public static void sideAlignedPosition(
        int mainLeft,
        int mainWidth,
        int rowCenterY,
        int popupWidth,
        int popupHeight,
        int screenW,
        int screenH,
        int gap,
        @NonNull int[] outXY
    ) {
        int preferredRightX = mainLeft + mainWidth + gap;
        int preferredLeftX = mainLeft - popupWidth - gap;
        int x = preferredRightX;
        if (preferredRightX + popupWidth > screenW && preferredLeftX >= 0) {
            x = preferredLeftX;
        }
        outXY[0] = clamp(x, 0, Math.max(0, screenW - popupWidth));
        outXY[1] = clamp(rowCenterY - (popupHeight / 2), 0, Math.max(0, screenH - popupHeight));
    }

    /** Squared distance from a raw screen point to {@code bounds}; 0 while the point is inside. */
    public static float squaredDistanceTo(@NonNull Rect bounds, float rawX, float rawY) {
        float dx = 0f;
        if (rawX < bounds.left) {
            dx = bounds.left - rawX;
        } else if (rawX > bounds.right) {
            dx = rawX - bounds.right;
        }
        float dy = 0f;
        if (rawY < bounds.top) {
            dy = bounds.top - rawY;
        } else if (rawY > bounds.bottom) {
            dy = rawY - bounds.bottom;
        }
        return (dx * dx) + (dy * dy);
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
