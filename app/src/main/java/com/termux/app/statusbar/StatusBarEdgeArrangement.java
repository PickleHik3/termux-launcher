package com.termux.app.statusbar;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.terminal.TerminalWindowBar;

/**
 * Stands the status bar's own contents on the edge the place asks for.
 *
 * <p>There is one bar, one set of views and one set of ids wherever it goes: the host itself is
 * re-parented between the four edge stacks, where a row along the top or the bottom takes its slice
 * of the terminal's height and a column down a side stands beside the padded content root, in the
 * same stack the apps rail and the extra keys column stand in. Everything
 * that binds to those views by id (the clock, the stat widgets, the window list, the glass and its
 * wallpaper frost) therefore keeps working untouched.
 *
 * <p>A row is what the bar has always been. A column turns the same content on its side: the strip
 * stacks, the window pills become a chip per window, the stat widgets put their mark over their
 * value, and the clock is the stacked one rather than the row's.
 */
public final class StatusBarEdgeArrangement {

    private StatusBarEdgeArrangement() {}

    /**
     * Gives the host the band the edge it now stands on asks for: a row spans its stack and takes
     * {@code thicknessPx} of its depth, a column spans the screen's height and takes that much of
     * its width. Which stack the host is in is {@code TermuxActivity.applyEdgeStacks}'s answer —
     * every edge is an {@code EdgeStackView}, so the parameters are always a stack's.
     *
     * <p>The surface's own screen margins are the style's, not the edge's, and are kept.
     */
    public static void band(@NonNull View host, @NonNull Edge edge, int thicknessPx) {
        boolean column = StatusBarEdgeGeometry.isVertical(edge);
        LinearLayout.LayoutParams params =
            host.getLayoutParams() instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) host.getLayoutParams()
                : new LinearLayout.LayoutParams(0, 0);
        if (host.getLayoutParams() instanceof ViewGroup.MarginLayoutParams
            && !(host.getLayoutParams() instanceof LinearLayout.LayoutParams)) {
            ViewGroup.MarginLayoutParams existing =
                (ViewGroup.MarginLayoutParams) host.getLayoutParams();
            params.setMargins(existing.leftMargin, existing.topMargin, existing.rightMargin,
                existing.bottomMargin);
        }
        params.width = column ? thicknessPx : ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = column ? ViewGroup.LayoutParams.MATCH_PARENT : thicknessPx;
        host.setLayoutParams(params);
    }

    /**
     * Turns the bar's contents to face the edge it now stands on. Idempotent, so it can be run on
     * every arrangement pass rather than only when the edge moves.
     */
    public static void apply(@NonNull ViewGroup host, @NonNull Edge edge) {
        boolean vertical = StatusBarEdgeGeometry.isVertical(edge);

        StatusBarLensView lens = host.findViewById(R.id.terminal_status_lens);
        if (lens != null) lens.setEdge(edge);

        // The modular widget slot - the row clock, the media card, a pinned notification - is the
        // row's. A column shows the stacked clock in its place. In a row the slot keeps the bar's
        // outer edge and the status row its inner one: the clock stands along the top of a top
        // bar and along the bottom of a bottom bar, and the two never share a stretch of the bar.
        View widgetSlot = host.findViewById(R.id.terminal_top_widget_area);
        if (widgetSlot != null && vertical) widgetSlot.setVisibility(View.GONE);
        if (widgetSlot != null
            && widgetSlot.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams slotParams =
                (FrameLayout.LayoutParams) widgetSlot.getLayoutParams();
            int gravity = edge == Edge.BOTTOM ? Gravity.BOTTOM : Gravity.TOP;
            if (slotParams.gravity != gravity) {
                slotParams.gravity = gravity;
                widgetSlot.setLayoutParams(slotParams);
            }
        }
        View stackedClock = host.findViewById(R.id.terminal_status_column_clock);
        if (stackedClock != null && !vertical) stackedClock.setVisibility(View.GONE);

        LinearLayout row = host.findViewById(R.id.terminal_status_row);
        if (row != null) {
            row.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            row.setGravity(vertical ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        }

        PlaceContentStrip strip = host.findViewById(R.id.terminal_status_place_content);
        if (strip != null) {
            strip.setVertical(vertical);
            strip.setGravity(vertical ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
            stretch(strip, vertical);
        }

        View sessions = host.findViewById(R.id.terminal_sessions_indicator);
        if (sessions != null) fit(sessions, vertical);

        TerminalWindowBar windows = host.findViewById(R.id.terminal_window_bar);
        StatusBarWindowColumn windowColumn =
            host.findViewById(R.id.terminal_status_window_column);
        if (windowColumn != null) stretch(windowColumn, vertical);
        if (windows != null && vertical) windows.setVisibility(View.GONE);
        if (windowColumn != null && !vertical) windowColumn.setVisibility(View.GONE);

        stack(host.findViewById(R.id.terminal_status_stats_cluster), vertical);
        stack(host.findViewById(R.id.terminal_status_widgets), vertical);
        View spacer = host.findViewById(R.id.terminal_status_stats_center_spacer);
        if (spacer != null) stretch(spacer, vertical);
    }

    /** A container of stat widgets, turned to run down the bar rather than along it. */
    private static void stack(@Nullable View container, boolean vertical) {
        if (!(container instanceof LinearLayout)) return;
        LinearLayout group = (LinearLayout) container;
        group.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        group.setGravity(vertical ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        fit(group, vertical);
        // The group's own gaps and paddings are spacings along the bar: they keep the stats clear
        // of the bar's end, so they turn with it instead of nudging the whole cluster off the
        // bar's middle.
        centreAcross(group, vertical);
        turnAlongMargins(group, vertical);
        turnAlongPadding(group, vertical);
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            // A stat and the dot before it sit on the bar's middle line and are spaced from
            // each other along it. Both of those turn with the bar; a start margin left standing
            // in a column shifts the stat sideways, and a row's centre_vertical gravity has no
            // horizontal part at all, so the dot falls to the column's edge.
            centreAcross(child, vertical);
            turnAlongMargins(child, vertical);
            if (child instanceof StatusBarWidgetView) {
                ((StatusBarWidgetView) child).setStacked(vertical);
                fit(child, vertical);
            }
        }
    }

    /** A child centred across the bar, whichever axis that is. */
    private static void centreAcross(@NonNull View view, boolean vertical) {
        if (!(view.getLayoutParams() instanceof LinearLayout.LayoutParams)) return;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        int gravity = vertical ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL;
        if (params.gravity == gravity) return;
        params.gravity = gravity;
        view.setLayoutParams(params);
    }

    /** A child that fills the bar across its width and wraps along it, or the other way round. */
    private static void fit(@NonNull View view, boolean vertical) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) return;
        // A chip given an exact size by the bar's style keeps it: square along a row is square
        // down a column too.
        if (params.width > 0 && params.height > 0) return;
        // The bar's own thickness is the "across" axis, which its content fills; the length is the
        // "along" axis, which the content wraps to.
        params.width = vertical
            ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
        params.height = vertical
            ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
        view.setLayoutParams(params);
    }

    /** A child that takes whatever room is left along the bar. */
    private static void stretch(@NonNull View view, boolean vertical) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof LinearLayout.LayoutParams)) return;
        LinearLayout.LayoutParams linear = (LinearLayout.LayoutParams) params;
        if (vertical) {
            linear.width = ViewGroup.LayoutParams.MATCH_PARENT;
            linear.height = 0;
        } else {
            linear.width = 0;
            linear.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        view.setLayoutParams(linear);
    }

    /**
     * The gaps a child wears at its two ends along the bar, turned with it: what was the space
     * before and after it along a row is the space above and below it down a column. Each end
     * keeps its own size, so a leading gap stays a leading gap, and turning back restores the row.
     */
    private static void turnAlongMargins(@NonNull View view, boolean vertical) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        int leading = Math.max(margins.getMarginStart(), margins.topMargin);
        int trailing = Math.max(margins.getMarginEnd(), margins.bottomMargin);
        if (leading == 0 && trailing == 0) return;
        margins.setMarginStart(vertical ? 0 : leading);
        margins.setMarginEnd(vertical ? 0 : trailing);
        margins.topMargin = vertical ? leading : 0;
        margins.bottomMargin = vertical ? trailing : 0;
        view.setLayoutParams(margins);
    }

    /** The same for a group's own padding: the clearance it keeps at the bar's two ends. */
    private static void turnAlongPadding(@NonNull View view, boolean vertical) {
        int leading = Math.max(view.getPaddingStart(), view.getPaddingTop());
        int trailing = Math.max(view.getPaddingEnd(), view.getPaddingBottom());
        if (leading == 0 && trailing == 0) return;
        view.setPaddingRelative(vertical ? 0 : leading, vertical ? leading : 0,
            vertical ? 0 : trailing, vertical ? trailing : 0);
    }
}
