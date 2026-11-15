package com.termux.app.setup;

import androidx.annotation.NonNull;

/**
 * What each way out of the wizard does. Pure: the controller asks, then writes, marks the wizard
 * done, closes it and offers the cards in that order.
 *
 * <p>Finish and Skip both keep the choices as they stand (Skip moves on with each section's
 * current value) and then offer the Setup cards at once. Keep my current setup puts the snapshot
 * back and offers no cards.
 */
final class SetupWizardExit {

    enum Way { FINISH, SKIP, KEEP }

    private SetupWizardExit() {}

    /** Writes the wizard's values as they stand, as Finish does. */
    static boolean appliesChoices(@NonNull Way way) {
        return way != Way.KEEP;
    }

    /** Puts back the snapshot taken when the wizard opened. */
    static boolean restoresSnapshot(@NonNull Way way) {
        return way == Way.KEEP;
    }

    /** Every card that exists now counts as shown, so none is due after this way out. */
    static boolean marksCardsSeen(@NonNull Way way) {
        return way == Way.KEEP;
    }

    /** Opens the Setup cards right after the wizard closes, when any is due. */
    static boolean offersCards(@NonNull Way way) {
        return way != Way.KEEP;
    }
}
