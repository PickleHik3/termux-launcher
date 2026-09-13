package com.termux.app.tour;

import com.termux.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The run, as data: nine cards in the order the launcher teaches itself.
 *
 * <p>Steps 1 and 2 are wired end to end. The rest carry their copy, their target and their
 * gestures so the table is readable in one place, and wait on signals nothing emits yet — a card
 * whose signal never arrives is still skippable, which is how the run behaves today past step 2.
 */
public final class TourRun {

    private static final List<TourStep> STEPS = Collections.unmodifiableList(Arrays.asList(
        new TourStep("status_place", R.string.tour_card_status_place,
            R.string.tour_card_status_place_then, TourTargets.STATUS_BAR,
            new String[] {TourSignals.PLACE_CHANGED, TourSignals.PLACE_RETURNED},
            new TourGesture[] {TourGesture.SWIPE_LEFT, TourGesture.SWIPE_RIGHT}),

        new TourStep("status_expand", R.string.tour_card_status_expand, 0, TourTargets.STATUS_BAR,
            new String[] {TourSignals.STATUS_BAR_EXPANDED, TourSignals.STATUS_BAR_COLLAPSED},
            new TourGesture[] {TourGesture.DRAG_DOWN, TourGesture.DRAG_UP}),

        new TourStep("window", R.string.tour_card_window, R.string.tour_card_window_then,
            TourTargets.PLUS_BUTTON,
            new String[] {TourSignals.WINDOW_OPENED, TourSignals.WINDOW_CHIP_SELECTED,
                TourSignals.WINDOW_CLOSED},
            new TourGesture[] {TourGesture.TAP, TourGesture.TAP, TourGesture.TAP}),

        new TourStep("split", R.string.tour_card_split, 0, TourTargets.SPLIT_KEY,
            new String[] {TourSignals.PANE_SPLIT},
            new TourGesture[] {TourGesture.SWIPE_UP}),

        new TourStep("pane_corner", R.string.tour_card_pane_corner, 0, TourTargets.PANE_CORNER,
            new String[] {TourSignals.PANE_CORNER_MENU},
            new TourGesture[] {TourGesture.TAP}),

        new TourStep("drawer", R.string.tour_card_drawer, R.string.tour_card_drawer_then,
            TourTargets.DOCK,
            new String[] {TourSignals.DRAWER_OPENED, TourSignals.DRAWER_CLOSED},
            new TourGesture[] {TourGesture.DRAG_DOWN, TourGesture.DRAG_DOWN}),

        new TourStep("az_scrub", R.string.tour_card_az_scrub, 0, TourTargets.AZ_ROW,
            new String[] {TourSignals.APP_LAUNCHED_FROM_SCRUB},
            new TourGesture[] {TourGesture.SCRUB}),

        new TourStep("palette", R.string.tour_card_palette, 0, TourTargets.SPACE_BAR,
            new String[] {TourSignals.PALETTE_OPENED},
            new TourGesture[] {TourGesture.SWIPE_UP}),

        // TODO(tour phase 2): the closing card carries three edition-aware lines and a
        // Copy commands button; today it is the one card that ends on Done alone.
        new TourStep("closing", R.string.tour_card_closing, 0, "",
            new String[] {},
            new TourGesture[] {TourGesture.NONE})
    ));

    /** The run, in order. */
    public static List<TourStep> steps() {
        return STEPS;
    }

    private TourRun() {}
}
