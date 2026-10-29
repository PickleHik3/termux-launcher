package com.termux.app.setup;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

/** One screen of the setup wizard's sheet. */
interface SetupSection {

    /** The section's name for the meta line, in plain words ("Home screen"). */
    @StringRes int nameRes();

    /** The section's content, built fresh each time the section is shown. */
    @NonNull View createView(@NonNull Context context, @NonNull SetupState state);

    /** Called after the section is on screen. */
    default void onShown() { }

    /** Whether the miniature of the launcher stays above the sheet for this section. */
    default boolean showsMiniature() { return true; }
}
