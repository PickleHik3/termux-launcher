package com.termux.app.wall.pageswitch;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Where the page bar's views get the pixels of a mark that is not a plain shape: a Linux app's own
 * icon ({@link PageBarModel.Entry#iconKey}) and the face a full-screen program's glyph
 * ({@link PageBarModel.Entry#glyph}) is drawn in. Both answer null when they have nothing, and the
 * view falls back to the plain mark.
 */
public interface PageMarkIcons {

    /** The icon for {@code iconKey}, or null while unknown (a miss starts its resolution). */
    @Nullable Drawable icon(@NonNull String iconKey);

    /** The typeface holding the program glyphs, the one the window bar draws them in; may be null. */
    @Nullable Typeface glyphTypeface();
}
