package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The Clock's bundled face: Libron Bold (SIL OFL 1.1, Reserved Font Name "Libron"; see
 * THIRD_PARTY_NOTICES.md), shipped unmodified under its own file name in the assets. Loaded once
 * per process; a build or a test that cannot read it gets the platform's bold serif.
 */
final class ClockTypefaces {
    private ClockTypefaces() { }

    static final String LIBRON_BOLD_ASSET = "fonts/Libron-Bold.ttf";

    @Nullable private static Typeface sLibronBold;

    @NonNull static synchronized Typeface libronBold(@NonNull Context context) {
        if (sLibronBold == null) {
            Typeface loaded = null;
            try {
                loaded = Typeface.createFromAsset(context.getAssets(), LIBRON_BOLD_ASSET);
            } catch (RuntimeException ignored) {
                // Fall through to the platform's bold serif.
            }
            sLibronBold = loaded != null ? loaded : Typeface.create(Typeface.SERIF, Typeface.BOLD);
        }
        return sLibronBold;
    }
}
