package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;
import com.termux.app.place.PlaceChromeTravel.Frame;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.wall.PageRef;

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
 *       {@link DockPlan#SLIDE_IN}). Two halves that both stand rows stand both sides' rows at
 *       once, each in its side's lane ({@link StackLanes}), and nothing is laid out or exchanged
 *       on the way: a crossing never lays the stack out again.</li>
 *   <li><b>The status bar.</b> Where both halves wear it alike it stands still (one bar across
 *       the wall, #90). Where Home's layout places or shapes it otherwise, the Workspace's bar
 *       travels with the Workspace's page: its screen position is that page's
 *       ({@link #workspaceScreenXPx}), sliding out with the window toward Home and in with it
 *       from Home, instead of fading in place.</li>
 * </ul>
 *
 * <p>Pure, so the travel is read and tested as arithmetic.
 */
public final class SeamTravel {

    private SeamTravel() {}

    /** The rows that make the dock. */
    private static final Element[] DOCK_ROWS = {Element.APPS, Element.AZ, Element.EXTRA_KEYS};

    /** How the dock's rows cross from the half the chrome is committed to into the other. */
    public enum DockPlan {
        /** Both halves stand rows, or one of them cannot be exchanged: nothing moves. */
        NONE,
        /** The other half has no dock rows: the committed half's slide down over the whole way. */
        SLIDE_OUT,
        /** The committed half has none: the other half's rise over the whole way. */
        SLIDE_IN
    }

    /** Whether two pages are on different halves of the wall: one a Home page, one a window. */
    public static boolean crosses(@NonNull PageKind a, @NonNull PageKind b) {
        return (a == PageKind.HOME) != (b == PageKind.HOME);
    }

    /**
     * The side of {@code frame} on the half the chrome is not committed to, or null while the
     * frame stays on the committed page's half.
     */
    @Nullable
    public static PageKind otherHalfPage(@NonNull Frame frame,
                                             @NonNull PageKind committed) {
        if (crosses(frame.toward, committed)) return frame.toward;
        if (crosses(frame.from, committed)) return frame.from;
        return null;
    }

    /**
     * How far, 0 to 1, the chrome has gone into the other half: the frame's chrome fraction
     * toward its side on that half; 0 on a frame wholly on the committed half and 1 on one wholly
     * on the other. One number for both directions, so a drag that reverses reads it back down.
     */
    public static float otherHalfProgress(@NonNull Frame frame, @NonNull PageKind committed) {
        boolean fromOther = crosses(frame.from, committed);
        boolean towardOther = crosses(frame.toward, committed);
        if (fromOther == towardOther) return fromOther ? 1f : 0f;
        return towardOther ? frame.chromeFraction : 1f - frame.chromeFraction;
    }

    /**
     * Whether two ends are different pages a crossing runs between (#173 slice E): pages of the
     * two halves ({@link #crosses(PageKind, PageKind)}), or two Home pages whose ids are
     * both said and differ. Two windows, or a page with no id said beside one of its kind, are
     * not.
     */
    public static boolean crosses(@NonNull PageRef a, @NonNull PageRef b) {
        return differs(a.kind, a.id, b);
    }

    /** Whether the side {@code kind}, {@code id} of a frame is on another page than {@code end}. */
    private static boolean differs(@NonNull PageKind kind, int id, @NonNull PageRef end) {
        if (crosses(kind, end.kind)) return true;
        return kind == PageKind.HOME && end.isHome() && id >= 0 && end.id >= 0
            && id != end.id;
    }

    /**
     * {@link #otherHalfProgress} for a crossing named by its committed end: how far, 0 to 1, the
     * chrome has gone into the other end. On a frame of the wall, whose sides are named by kind,
     * it is exactly {@link #otherHalfProgress}; on a frame of Home's pager ({@link
     * PlaceChromeTravel#homePages}) between the committed Home page and another, it is the
     * pager's chrome fraction toward the other page, so a slide between two Home pages reads the
     * same way as one across the seam. Builds nothing.
     */
    public static float otherEndProgress(@NonNull Frame frame, @NonNull PageRef committed) {
        boolean fromOther = differs(frame.from, frame.fromId, committed);
        boolean towardOther = differs(frame.toward, frame.towardId, committed);
        if (fromOther == towardOther) return fromOther ? 1f : 0f;
        return towardOther ? frame.chromeFraction : 1f - frame.chromeFraction;
    }

    /** {@link #otherHalfFraction} for a crossing named by its committed end, as above. */
    public static float otherEndFraction(@NonNull Frame frame, @NonNull PageRef committed) {
        boolean fromOther = differs(frame.from, frame.fromId, committed);
        boolean towardOther = differs(frame.toward, frame.towardId, committed);
        if (fromOther == towardOther) return fromOther ? 1f : 0f;
        return towardOther ? frame.fraction : 1f - frame.fraction;
    }

    /**
     * Where Home page {@code end} stands on screen in a frame of Home's pager, as an x offset from
     * its rest: the page in front at the pager's shift, the page coming in a width beyond it on
     * the side the shift opens. A page the frame does not show stands a width off to the right,
     * out of sight. What a status bar only one of two Home pages wears rides at.
     */
    public static float endScreenXPx(@NonNull Frame frame, @NonNull PageRef end, int widthPx) {
        if (end.id >= 0 && end.id == frame.fromId) return -frame.towardSide * frame.fraction * widthPx;
        if (end.id >= 0 && end.id == frame.towardId && frame.towardSide != 0)
            return frame.towardSide * (1f - frame.fraction) * widthPx;
        return widthPx;
    }

    /**
     * Which of two Home pages' layouts wears the bar that crosses between them (#173 slice E):
     * the one that stands it along the top while the other puts it away, so it can ride in or
     * out with that page ({@link #statusRides}); toward it, only where the committed page stands
     * nothing the bar's band would push in ({@link #statusCanArriveOver}). Null where the bar
     * does not cross: both wear it alike, both put it away, or it stands elsewhere, and then it
     * changes at the landing.
     *
     * @return {@code committed} or {@code other}, the layout that wears the bar; null for none
     */
    @Nullable
    public static PlaceLayout homeBarWearer(@NonNull PlaceLayout committed,
                                            @NonNull PlaceLayout other) {
        boolean hereHides = committed.slot(Element.STATUS).hidden;
        boolean thereHides = other.slot(Element.STATUS).hidden;
        if (hereHides == thereHides) return null;
        if (hereHides) return statusRides(other) && statusCanArriveOver(committed) ? other : null;
        return statusRides(committed) ? committed : null;
    }

    /**
     * How far, 0 to 1, the wall itself has gone into the other half: {@link #otherHalfProgress} on
     * the wall's own way rather than the chrome's curve, so it moves over the stretch at either end
     * ({@link SeamFade#START}) where the chrome rests.
     */
    public static float otherHalfFraction(@NonNull Frame frame, @NonNull PageKind committed) {
        boolean fromOther = crosses(frame.from, committed);
        boolean towardOther = crosses(frame.toward, committed);
        if (fromOther == towardOther) return fromOther ? 1f : 0f;
        return towardOther ? frame.fraction : 1f - frame.fraction;
    }

    /** Whether a plan takes the committed half's dock rows off the screen on the way. */
    public static boolean committedRowsLeave(@NonNull DockPlan plan) {
        return plan == DockPlan.SLIDE_OUT;
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
        return DockPlan.NONE;
    }

    /**
     * Whether the stack stands the other half's rows at {@code progress} into it
     * ({@link #otherHalfProgress}): from the first frame for a slide in, where none of the stack
     * shows, and never before settle otherwise.
     */
    public static boolean showsOtherDock(@NonNull DockPlan plan, float progress) {
        return plan == DockPlan.SLIDE_IN && progress > 0f;
    }

    /**
     * How much of the dock rows the stack stands right now shows, 0 to 1, at {@code progress} into
     * the other half: what {@link PlaceChromeTravel#stackTranslationPx} slides the rest of below
     * the screen.
     */
    public static float dockReveal(@NonNull DockPlan plan, float progress) {
        float q = clamp01(progress);
        switch (plan) {
            case SLIDE_OUT: return 1f - q;
            case SLIDE_IN: return q;
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
     * whose stacks the bar's band would push in. The bar overlays Home's canvas (a Home page
     * whose layout stands no bar along the top starts at the canvas's top,
     * {@link PageTopInsets#pageTopInsetPx}), so with those edges empty nothing on Home moves.
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
        boolean fromWorkspace = frame.from != PageKind.HOME;
        boolean towardWorkspace = frame.toward != PageKind.HOME;
        if (fromWorkspace && towardWorkspace) return 0f;
        if (!fromWorkspace && !towardWorkspace) return widthPx;
        // The far side of a frame stands one width to the right of the near one.
        return towardWorkspace ? (1f - frame.fraction) * widthPx : -frame.fraction * widthPx;
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }
}
