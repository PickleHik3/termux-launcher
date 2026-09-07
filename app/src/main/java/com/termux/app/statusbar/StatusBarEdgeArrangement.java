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
 * moved between the content column — where a row along the top or the bottom takes its slice of
 * the terminal's height — and the root container, where a column down a side lives beside the
 * padded content root, in the same band the apps rail and the extra keys column use. Everything
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
     * Moves the host into the parent the edge belongs to and gives it that parent's kind of layout
     * parameters. A row goes into {@code column} — first for the top edge, last for the bottom, so
     * the terminal keeps the middle; a column goes into {@code container}, in front of the rail so
     * whatever shares its band draws over it rather than under.
     *
     * @return whether the host actually moved
     */
    public static boolean moveHost(@NonNull View host, @NonNull ViewGroup column,
                                   @NonNull ViewGroup container, @Nullable View contentRoot,
                                   @NonNull Edge edge, int thicknessPx) {
        ViewGroup target = StatusBarEdgeGeometry.isVertical(edge) ? container : column;
        int index = targetIndex(target, column, container, contentRoot, edge);
        ViewGroup parent = host.getParent() instanceof ViewGroup
            ? (ViewGroup) host.getParent() : null;
        boolean moved = parent != target || parent.indexOfChild(host) != index;
        if (moved && parent != null) parent.removeView(host);
        ViewGroup.LayoutParams params = layoutParams(host, edge, thicknessPx);
        if (moved) {
            target.addView(host, Math.max(0, Math.min(index, target.getChildCount())), params);
        } else {
            host.setLayoutParams(params);
        }
        return moved;
    }

    private static int targetIndex(@NonNull ViewGroup target, @NonNull ViewGroup column,
                                   @NonNull ViewGroup container, @Nullable View contentRoot,
                                   @NonNull Edge edge) {
        if (target == column) return edge == Edge.TOP ? 0 : column.getChildCount();
        // Straight after the padded content root: over the window's ground, under the rail, the
        // extra keys column, the app drawer plane and the command palette.
        int after = contentRoot == null ? 0 : container.indexOfChild(contentRoot) + 1;
        return Math.max(0, after);
    }

    @NonNull
    private static ViewGroup.LayoutParams layoutParams(@NonNull View host, @NonNull Edge edge,
                                                       int thicknessPx) {
        ViewGroup.MarginLayoutParams existing =
            host.getLayoutParams() instanceof ViewGroup.MarginLayoutParams
                ? (ViewGroup.MarginLayoutParams) host.getLayoutParams() : null;
        ViewGroup.MarginLayoutParams params;
        if (StatusBarEdgeGeometry.isVertical(edge)) {
            FrameLayout.LayoutParams frame = new FrameLayout.LayoutParams(thicknessPx,
                ViewGroup.LayoutParams.MATCH_PARENT,
                (edge == Edge.RIGHT ? Gravity.END : Gravity.START) | Gravity.TOP);
            params = frame;
        } else {
            params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                thicknessPx);
        }
        if (existing != null) {
            // The surface's own screen margins are the style's, not the edge's; keep them.
            params.setMargins(existing.leftMargin, existing.topMargin, existing.rightMargin,
                existing.bottomMargin);
        }
        return params;
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
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof StatusBarWidgetView) {
                ((StatusBarWidgetView) child).setStacked(vertical);
                fit(child, vertical);
            } else if (child instanceof MaterialDotSeparatorView) {
                // The dot between two stats keeps its size; only the margin it wears turns.
                turnMargins(child, vertical);
            }
        }
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

    private static void turnMargins(@NonNull View view, boolean vertical) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        int spacing = Math.max(margins.leftMargin, margins.topMargin);
        if (vertical) margins.setMargins(0, spacing, 0, spacing);
        else margins.setMargins(spacing, 0, spacing, 0);
        view.setLayoutParams(margins);
    }
}
