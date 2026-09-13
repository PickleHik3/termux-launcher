package com.termux.app.tour;

import androidx.annotation.NonNull;

import com.termux.R;

/**
 * The last card's three lines, and the commands its Copy button puts on the clipboard.
 *
 * <p>Two of the three lines name a package manager, so they are chosen by edition rather than
 * written once with a placeholder: the Nix edition installs from nixpkgs into a profile and has no
 * {@code x11-repo} to enable, which is a different sentence and not a different noun.
 *
 * <p>Pure, so the table below is a unit test rather than three screenshots.
 */
public final class TourClosingCard {

    /** The card's body, in order, for the edition that is running. */
    @NonNull
    public static int[] bodyLines(@NonNull TourEdition edition) {
        return edition.usesNixPackages()
            ? new int[] {R.string.tour_closing_hints, R.string.tour_closing_extras_nix,
                R.string.tour_closing_graphical_nix}
            : new int[] {R.string.tour_closing_hints, R.string.tour_closing_extras,
                R.string.tour_closing_graphical};
    }

    /** What Copy commands puts on the clipboard: the lines above, as commands, one per line. */
    public static int commands(@NonNull TourEdition edition) {
        return edition.usesNixPackages()
            ? R.string.tour_closing_commands_nix : R.string.tour_closing_commands;
    }

    private TourClosingCard() {}
}
