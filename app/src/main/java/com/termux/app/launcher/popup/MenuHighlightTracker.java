package com.termux.app.launcher.popup;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * The drag-to-highlight interaction: the finger that opened a menu with a long press stays down,
 * slides over the rows, and commits one on release.
 *
 * <p>Two things make this more than a hit test. First, the finger is rarely inside a row — it comes
 * up from an icon below the panel — so once the gesture has armed, a point outside the panels is
 * projected onto the nearest row by vertical distance, and when both a main and a side menu are up
 * the nearer panel wins. Second, hovering a row that owns a side menu opens it, and sliding off that
 * row (and off the side menu) closes it again.
 *
 * <p>The tracker owns no windows: it reads two {@link AnchoredMenu} slots and calls back to the host
 * to open or close the side menu, because deciding <em>what</em> the side menu contains is the
 * host's business.
 */
public final class MenuHighlightTracker {

    /** How far below the lowest row the finger may stray before nothing is highlighted, in dp. */
    private static final int PROJECTION_SLACK_DP = 12;

    @NonNull private final View host;
    @NonNull private final MenuRowFactory rowFactory;
    @NonNull private final AnchoredMenu mainMenu;
    @NonNull private final AnchoredMenu sideMenu;

    @Nullable private Runnable submenuOpener;
    @Nullable private Runnable submenuDismisser;
    @Nullable private MenuRow highlighted;
    private int tintBase;

    public MenuHighlightTracker(@NonNull View host, @NonNull MenuRowFactory rowFactory,
                                @NonNull AnchoredMenu mainMenu, @NonNull AnchoredMenu sideMenu) {
        this.host = host;
        this.rowFactory = rowFactory;
        this.mainMenu = mainMenu;
        this.sideMenu = sideMenu;
    }

    /** Called when a row that owns the side menu takes the highlight and no side window exists. */
    public void setSubmenuOpener(@Nullable Runnable opener) {
        this.submenuOpener = opener;
    }

    /** Called when the highlight leaves both the side menu and the row that opens it. */
    public void setSubmenuDismisser(@Nullable Runnable dismisser) {
        this.submenuDismisser = dismisser;
    }

    /** The hue highlighted rows are tinted from; set when a menu is built. */
    public void setTintBase(int tintBase) {
        this.tintBase = tintBase;
    }

    public int tintBase() {
        return tintBase;
    }

    @Nullable
    public MenuRow highlighted() {
        return highlighted;
    }

    /** Drops the highlight, repainting the row that had it. */
    public void clear() {
        if (highlighted != null && highlighted.rowView != null) {
            rowFactory.styleRow(highlighted.rowView, false, tintBase);
        }
        highlighted = null;
    }

    /**
     * Moves the highlight to whatever row {@code rawX}/{@code rawY} resolves to and returns whether
     * anything is highlighted.
     *
     * @param openSubmenuOnFocus whether hovering a submenu row may open the side menu
     * @param allowProjectedOutside whether a point outside the panels projects onto a nearest row;
     *                              false until the gesture has moved far enough to arm a selection
     */
    public boolean updateForRaw(float rawX, float rawY, boolean openSubmenuOnFocus,
                               boolean allowProjectedOutside) {
        MenuRow target = resolveRowAtRaw(rawX, rawY, openSubmenuOnFocus, allowProjectedOutside);
        boolean keepSideVisible = contains(sideMenu.rows(), target)
            || (target != null && target.opensSubmenu);
        if (!keepSideVisible && sideMenu.isShowing() && submenuDismisser != null) {
            submenuDismisser.run();
        }
        setHighlight(target);
        return target != null;
    }

    /** Runs the highlighted row's action; a release with nothing highlighted leaves the menu open. */
    public void commitHighlighted() {
        if (highlighted != null && highlighted.action != null) {
            highlighted.action.run();
        }
    }

    // ------------------------------------------------------------------ internals

    private void setHighlight(@Nullable MenuRow target) {
        if (highlighted == target) return;
        if (highlighted != null && highlighted.rowView != null) {
            rowFactory.styleRow(highlighted.rowView, false, tintBase);
        }
        highlighted = target;
        if (highlighted != null && highlighted.rowView != null) {
            rowFactory.styleRow(highlighted.rowView, true, tintBase);
        }
    }

    @Nullable
    private MenuRow resolveRowAtRaw(float rawX, float rawY, boolean openSubmenuOnFocus,
                                    boolean allowProjectedOutside) {
        List<MenuRow> mainRows = mainMenu.rows();
        List<MenuRow> sideRows = sideMenu.rows();

        MenuRow strictSide = strictInsideRow(sideRows, rawX, rawY);
        if (strictSide != null) {
            return strictSide;
        }
        MenuRow strictMain = strictInsideRow(mainRows, rawX, rawY);
        if (strictMain != null) {
            maybeOpenSubmenuFor(strictMain, openSubmenuOnFocus);
            return strictMain;
        }
        if (!allowProjectedOutside) {
            return null;
        }
        int lowestBottom = Math.max(lowestRowBottom(mainRows), lowestRowBottom(sideRows));
        if (lowestBottom > 0 && rawY > (lowestBottom + dp(PROJECTION_SLACK_DP))) {
            return null;
        }

        boolean hasMain = !mainRows.isEmpty() && mainMenu.isShowing();
        boolean hasSide = !sideRows.isEmpty() && sideMenu.isShowing();
        if (!hasMain && !hasSide) {
            return null;
        }
        if (hasMain && hasSide) {
            float mainDistance = mainMenu.squaredDistanceTo(rawX, rawY);
            float sideDistance = sideMenu.squaredDistanceTo(rawX, rawY);
            if (sideDistance < mainDistance) {
                return nearestRowByY(sideRows, rawY);
            }
            MenuRow row = nearestRowByY(mainRows, rawY);
            maybeOpenSubmenuFor(row, openSubmenuOnFocus);
            return row;
        }
        if (hasSide) {
            return nearestRowByY(sideRows, rawY);
        }
        MenuRow row = nearestRowByY(mainRows, rawY);
        maybeOpenSubmenuFor(row, openSubmenuOnFocus);
        return row;
    }

    private void maybeOpenSubmenuFor(@Nullable MenuRow row, boolean openSubmenuOnFocus) {
        if (row == null || !openSubmenuOnFocus || !row.opensSubmenu) return;
        // A window that exists but is mid-dismiss still counts: reopening under a finger that is
        // already sliding off would flicker the side panel.
        if (sideMenu.window() != null) return;
        if (submenuOpener != null) submenuOpener.run();
    }

    private static boolean contains(@NonNull List<MenuRow> rows, @Nullable MenuRow row) {
        if (row == null) return false;
        for (MenuRow candidate : rows) {
            if (candidate == row) return true;
        }
        return false;
    }

    @Nullable
    private static MenuRow strictInsideRow(@NonNull List<MenuRow> rows, float rawX, float rawY) {
        for (MenuRow row : rows) {
            if (row.rowView != null && AnchoredMenu.isRawInsideView(row.rowView, rawX, rawY)) {
                return row;
            }
        }
        return null;
    }

    @Nullable
    private static MenuRow nearestRowByY(@NonNull List<MenuRow> rows, float rawY) {
        MenuRow nearest = null;
        float bestDistance = Float.MAX_VALUE;
        Rect rowBounds = new Rect();
        for (MenuRow row : rows) {
            if (!AnchoredMenu.screenRect(row.rowView, rowBounds)) continue;
            if (rawY >= rowBounds.top && rawY <= rowBounds.bottom) {
                return row;
            }
            float distance = rawY < rowBounds.top ? (rowBounds.top - rawY) : (rawY - rowBounds.bottom);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = row;
            }
        }
        return nearest;
    }

    private static int lowestRowBottom(@NonNull List<MenuRow> rows) {
        int bottom = -1;
        Rect rowBounds = new Rect();
        for (MenuRow row : rows) {
            if (!AnchoredMenu.screenRect(row.rowView, rowBounds)) continue;
            if (rowBounds.bottom > bottom) bottom = rowBounds.bottom;
        }
        return bottom;
    }

    private int dp(int value) {
        return Math.round(value * host.getResources().getDisplayMetrics().density);
    }
}
