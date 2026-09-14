package com.termux.app.tour;

import com.termux.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The run, as data: nine cards in the order the launcher teaches itself.
 *
 * <p>Every card's signals are emitted by the chrome now, and every target but the closing card's
 * resolves to a control the overlay can glow. A card whose control is not in the layout in front
 * of the user — no plus on the landscape rail, no space bar with the keyboard down — shows
 * without a glow, and is still cleared by the gesture and still skippable.
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

        // The + makes the window; everything after it happens on the chip the + just added, so
        // the glow moves there rather than staying on a button the user has finished with.
        new TourStep("window", R.string.tour_card_window, R.string.tour_card_window_then,
            new String[] {TourTargets.PLUS_BUTTON, TourTargets.WINDOW_CHIP,
                TourTargets.WINDOW_CLOSE},
            new String[] {TourSignals.WINDOW_OPENED, TourSignals.WINDOW_CHIP_SELECTED,
                TourSignals.WINDOW_CLOSED},
            new TourGesture[] {TourGesture.TAP, TourGesture.TAP, TourGesture.TAP}),

        // A tap on the split key is what dispatches the split: the keys row spends a swipe up on
        // the key's secondary, which for this one is "new window".
        new TourStep("split", R.string.tour_card_split, 0, TourTargets.SPLIT_KEY,
            new String[] {TourSignals.PANE_SPLIT},
            new TourGesture[] {TourGesture.TAP}),

        // The corner controls are a menu, and the user is left inside it: the card asks for the
        // way out before it moves on, or the next card arrives over a menu that is still up.
        new TourStep("pane_corner", R.string.tour_card_pane_corner,
            R.string.tour_card_pane_corner_then,
            new String[] {TourTargets.PANE_CORNER, TourTargets.NONE},
            new String[] {TourSignals.PANE_CORNER_MENU, TourSignals.PANE_CONTROLS_DISMISSED},
            new TourGesture[] {TourGesture.TAP, TourGesture.TAP}),

        // Once the drawer is open it covers the dock, so the second half points at nothing and the
        // card falls back to the middle of the plane the gesture is performed on.
        new TourStep("drawer", R.string.tour_card_drawer, R.string.tour_card_drawer_then,
            new String[] {TourTargets.DOCK, TourTargets.NONE},
            new String[] {TourSignals.DRAWER_OPENED, TourSignals.DRAWER_CLOSED},
            new TourGesture[] {TourGesture.DRAG_DOWN, TourGesture.DRAG_DOWN}),

        // Anchored at the top rather than against the row: the scrub filters the app icons just
        // above the letters and throws a preview up beside the finger, and a card resting on the
        // row covers both of the things the user is picking between.
        new TourStep("az_scrub", R.string.tour_card_az_scrub, 0,
            new String[] {TourTargets.AZ_ROW},
            new String[] {TourSignals.APP_LAUNCHED_FROM_SCRUB},
            new TourGesture[] {TourGesture.SCRUB}, true),

        new TourStep("palette", R.string.tour_card_palette, 0, TourTargets.SPACE_BAR,
            new String[] {TourSignals.PALETTE_OPENED},
            new TourGesture[] {TourGesture.SWIPE_UP}),

        // The only card with no target and no signal: it carries the three edition-aware lines
        // and ends on its own buttons.
        new TourStep("closing", R.string.tour_card_closing, 0, TourTargets.NONE,
            new String[] {},
            new TourGesture[] {TourGesture.NONE})
    ));

    /** The run, in order. */
    public static List<TourStep> steps() {
        return STEPS;
    }

    private TourRun() {}
}
