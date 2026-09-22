package com.termux.app.tour;

import androidx.annotation.StringRes;

import com.termux.R;

/**
 * A button a card offers, and what it says.
 *
 * <p>A card's buttons are part of the run's data rather than of the view: a lesson always offers
 * the same three, the home-screen card offers a choice whose wording depends on what the phone is
 * already set to, and a practice hint offers a way out that never touches the run's progress. The
 * view renders whatever the card — or, in practice, {@link TourController#currentActions()} —
 * hands it.
 */
public enum TourAction {

    /** Back to the previous lesson. */
    BACK(R.string.tour_back),
    /** Past this lesson, to the next one. */
    SKIP_STEP(R.string.tour_skip_step),
    /** Out of the run altogether. */
    END_TOUR(R.string.tour_end_tour),

    /** A practice hint's "I have done it", which ends the practice. */
    DONE(R.string.tour_done),
    /** A practice hint's way out without doing it. */
    END_PRACTICE(R.string.tour_end_practice),

    /** The home-screen card, when the launcher is not the home app yet. */
    USE_AS_HOME(R.string.tour_use_as_home),
    /** The same card's other answer. */
    KEEP_TRYING(R.string.tour_keep_trying),
    /** The same card when the launcher already is the home app: there is nothing to decide. */
    CONTINUE(R.string.tour_continue),

    /** The welcome card's yes: the run starts at the first lesson. */
    TAKE_THE_TOUR(R.string.tour_take_the_tour),
    /** The welcome card's other answer: the run ends there and is not offered again. */
    NOT_NOW(R.string.tour_not_now),

    /** The key-row card's yes: take this release's row of keys. */
    SWITCH_KEY_ROW(R.string.extra_keys_default_offer_switch),
    /** The same card's other answer: the row they already have stays. */
    KEEP_KEY_ROW(R.string.extra_keys_default_offer_keep),

    /** The closing card's one action. */
    START_USING(R.string.tour_start_using),

    /** An interrupted run from an older version of the tour, picked up again. */
    RESUME(R.string.tour_resume),
    /** The same run, started over instead. */
    RESTART(R.string.tour_restart);

    @StringRes public final int labelRes;

    TourAction(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }
}
