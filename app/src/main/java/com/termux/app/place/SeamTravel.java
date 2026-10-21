package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.wall.PaneWallPage;

import java.util.List;

/**
 * The chrome across the seam between the wall's halves: the last Home page and the Workspace's
 * window beside it. The two halves are arranged by layouts of their own (ADR 0008), so a slide
 * between them can change which dock rows stand on the bottom edge and whether there is a status
 * bar at all. {@link PlaceChromeTravel} moves the keyboard and the content's room as functions of
 * the wall's offset; this says how the rest crosses, as functions of the same frame:
 *
 * <ul>
 *   <li><b>The dock's rows.</b> A half with no dock rows lets the other half's slide down out of
 *       the screen, or rise in, over the whole of the chrome's way ({@link DockPlan#SLIDE_OUT},
 *       {@link DockPlan#SLIDE_IN}). Two halves with different rows exchange them: the leaving
 *       rows go down over the first half of the way, the arriving rows are laid out in their
 *       place at the midpoint, while the whole dock is below the screen's edge, and rise over the
 *       second half ({@link DockPlan#DIP}). Either way the rows on screen are only ever moved
 *       (the stack's translation), and laid out once, where nobody can see it.</li>
 *   <li><b>The status bar.</b> It is the Workspace's alone, so it travels with the Workspace's
 *       page: its screen position is that page's ({@link #workspaceScreenXPx}), sliding out
 *       with the window toward Home and in with it from Home, instead of fading in place.</li>
 * </ul>
 *
 * <p>Only an arrangement whose dock rows all lie along the bottom, none under the keyboard and
 * no status bar on the bottom edge, is exchanged this way; anything that would move the canvas's
 * sides mid-slide keeps changing at settle ({@link DockPlan#NONE}).
 *
 * <p>Pure, so the travel is read and tested as arithmetic.
 */
public final class SeamTravel {

    private SeamTravel() {}

    /** The rows that make the dock: what the exchange swaps between the halves. */
    private static final Element[] DOCK_ROWS = {Element.APPS, Element.AZ, Element.EXTRA_KEYS};

    /**
     * Where on the way a {@link DockPlan#DIP} lays the arriving rows out in the leaving ones'
     * place: the midpoint, where neither half's rows are showing.
     */
    public static final float DIP_SWAP = 0.5f;

    /** How the dock's rows cross from the half the chrome is committed to into the other. */
    public enum DockPlan {
        /** Both halves stand the same rows, or one of them cannot be exchanged: nothing moves. */
        NONE,
        /** The other half has no dock rows: the committed half's slide down over the whole way. */
        SLIDE_OUT,
        /** The committed half has none: the other half's are laid out at once and rise. */
        SLIDE_IN,
        /** Different rows on each: down over the first half, swapped, up over the second. */
        DIP
    }

    /** Whether two pages are on different halves of the wall: one a Home page, one a window. */
    public static boolean crosses(@NonNull PaneWallPage a, @NonNull PaneWallPage b) {
        return (a == PaneWallPage.WIDGETS) != (b == PaneWallPage.WIDGETS);
    }

    /**
     * The side of {@code frame} on the half the chrome is not committed to, or null while the
     * frame stays on the committed page's half.
     */
    @Nullable
    public static PaneWallPage otherHalfPage(@NonNull Frame frame,
                                             @NonNull PaneWallPage committed) {
        if (crosses(frame.toward, committed)) return frame.toward;
        if (crosses(frame.from, committed)) return frame.from;
        return null;
    }

    /**
     * How far, 0 to 1, the chrome has gone into the other half: the frame's chrome fraction
     * toward its side on that half; 0 on a frame wholly on the committed half and 1 on one wholly
     * on the other. One number for both directions, so a drag that reverses reads it back down.
     */
    public static float otherHalfProgress(@NonNull Frame frame, @NonNull PaneWallPage committed) {
        boolean fromOther = crosses(frame.from, committed);
        boolean towardOther = crosses(frame.toward, committed);
        if (fromOther == towardOther) return fromOther ? 1f : 0f;
        return towardOther ? frame.chromeFraction : 1f - frame.chromeFraction;
    }

    /**
     * How far, 0 to 1, the wall itself has gone into the other half: {@link #otherHalfProgress} on
     * the wall's own way rather than the chrome's, so it moves over the stretch at either end
     * ({@link PlaceChromeTravel#LANDING}) where the chrome rests too.
     */
    public static float otherHalfFraction(@NonNull Frame frame, @NonNull PaneWallPage committed) {
        boolean fromOther = crosses(frame.from, committed);
        boolean towardOther = crosses(frame.toward, committed);
        if (fromOther == towardOther) return fromOther ? 1f : 0f;
        return towardOther ? frame.fraction : 1f - frame.fraction;
    }

    /**
     * Whether a plan takes the committed half's dock rows off the screen on the way into the other
     * half: a slide out, or a dip (the other half's rows come up after them).
     */
    public static boolean committedRowsLeave(@NonNull DockPlan plan) {
        return plan == DockPlan.SLIDE_OUT || plan == DockPlan.DIP;
    }

    /**
     * Whether a layout's dock can be exchanged mid-slide: every dock row it shows lies along the
     * bottom above the keyboard, and its status bar is not a band of the same stack. A row on a
     * side or the top edge takes room from the canvas's sides or top, which no translation of the
     * stack can stand in for.
     */
    public static boolean dockExchangeable(@NonNull PlaceLayout layout) {
        for (Element element : DOCK_ROWS) {
            Slot slot = layout.slot(element);
            if (slot.hidden) continue;
            if (slot.edge != Edge.BOTTOM || EdgeStackPolicy.standsUnderKeyboard(layout, element))
                return false;
        }
        Slot status = layout.slot(Element.STATUS);
        return status.hidden || status.edge != Edge.BOTTOM;
    }

    /** How the dock crosses from the committed half's layout to the other half's. */
    @NonNull
    public static DockPlan dockPlan(@NonNull PlaceLayout committed, @NonNull PlaceLayout other) {
        if (!dockExchangeable(committed) || !dockExchangeable(other)) return DockPlan.NONE;
        List<Element> leaving = EdgeStackPolicy.stack(committed, Edge.BOTTOM);
        List<Element> arriving = EdgeStackPolicy.stack(other, Edge.BOTTOM);
        if (leaving.equals(arriving)) return DockPlan.NONE;
        if (arriving.isEmpty()) return DockPlan.SLIDE_OUT;
        if (leaving.isEmpty()) return DockPlan.SLIDE_IN;
        return DockPlan.DIP;
    }

    /**
     * Whether the stack stands the other half's rows at {@code progress} into it
     * ({@link #otherHalfProgress}): from the first frame for a slide in, past the midpoint for a
     * dip, and never before settle for a slide out or no exchange.
     */
    public static boolean showsOtherDock(@NonNull DockPlan plan, float progress) {
        switch (plan) {
            case SLIDE_IN: return progress > 0f;
            case DIP: return progress >= DIP_SWAP;
            default: return false;
        }
    }

    /**
     * How much of the dock rows the stack stands right now shows, 0 to 1, at {@code progress} into
     * the other half: what {@link PlaceChromeTravel#stackTranslationPx} slides the rest of below
     * the screen. At a dip's swap both sides read 0, so the swap is never seen.
     */
    public static float dockReveal(@NonNull DockPlan plan, float progress) {
        float q = clamp01(progress);
        switch (plan) {
            case SLIDE_OUT: return 1f - q;
            case SLIDE_IN: return q;
            case DIP: return q < DIP_SWAP ? 1f - q / DIP_SWAP : (q - DIP_SWAP) / (1f - DIP_SWAP);
            default: return 1f;
        }
    }

    /** {@code base} with the dock rows standing as {@code source} stands them. */
    @NonNull
    public static PlaceLayout withDockOf(@NonNull PlaceLayout base, @NonNull PlaceLayout source) {
        PlaceLayout result = base;
        for (Element element : DOCK_ROWS) result = result.withSlot(element, source.slot(element));
        return result;
    }

    /** {@code base} with the status bar standing as {@code source} stands it. */
    @NonNull
    public static PlaceLayout withStatusOf(@NonNull PlaceLayout base,
                                           @NonNull PlaceLayout source) {
        return base.withSlot(Element.STATUS, source.slot(Element.STATUS));
    }

    /**
     * Whether the Workspace layout's status bar can travel with its page: a bar along the top. A
     * column on a side or a band of the dock takes room the canvas would have to give up mid-slide,
     * and keeps changing at settle.
     */
    public static boolean statusRides(@NonNull PlaceLayout workspace) {
        Slot status = workspace.slot(Element.STATUS);
        return !status.hidden && status.edge == Edge.TOP;
    }

    /**
     * Whether the Workspace's bar can be laid out over a Home page for a slide toward the
     * Workspace without moving anything on that page: Home stands nothing on its top or side edges,
     * whose stacks the bar's band would push in. The bar overlays Home's canvas
     * (a Home page starts at the canvas's top, {@link PageTopInsets#pageTopInsetPx}), so with those edges empty nothing on Home moves.
     */
    public static boolean statusCanArriveOver(@NonNull PlaceLayout home) {
        return EdgeStackPolicy.stack(home, Edge.TOP).isEmpty()
            && EdgeStackPolicy.stack(home, Edge.LEFT).isEmpty()
            && EdgeStackPolicy.stack(home, Edge.RIGHT).isEmpty();
    }

    /**
     * Where the Workspace's page stands on screen in {@code frame}, as an x offset from its rest,
     * which is where the status bar is drawn while it travels with that page: 0 on a frame between
     * two windows, the page's own offset on a frame across the seam, and one width off to the
     * right on a frame between two Home pages, the side the Workspace stands on in the row.
     */
    public static float workspaceScreenXPx(@NonNull Frame frame, int widthPx) {
        boolean fromWorkspace = frame.from != PaneWallPage.WIDGETS;
        boolean towardWorkspace = frame.toward != PaneWallPage.WIDGETS;
        if (fromWorkspace && towardWorkspace) return 0f;
        if (!fromWorkspace && !towardWorkspace) return widthPx;
        // The far side of a frame stands one width to the right of the near one.
        return towardWorkspace ? (1f - frame.fraction) * widthPx : -frame.fraction * widthPx;
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }
}
