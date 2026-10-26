package com.termux.app.surfaces.rows;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;

/**
 * A glyph's name on long-press (editor-redesign §8: cards keep no summaries, each glyph's name
 * shows on long-press). The app's tooltip, as the keyboard layout and colour pages use it.
 */
public final class GlyphNames {

    private GlyphNames() {}

    /** Long-pressing {@code view} shows {@code name}. */
    public static void name(@NonNull View view, @Nullable CharSequence name) {
        TooltipCompat.setTooltipText(view, name);
        // A tooltip opens from the long-press the view's touch handling raises, which a view
        // that is neither clickable nor long-clickable (a bare glyph) never receives.
        if (name != null && !view.isClickable() && !view.isLongClickable())
            view.setLongClickable(true);
    }

    /** The name {@code view} shows on long-press, or null. */
    @Nullable
    public static CharSequence of(@NonNull View view) {
        return view.getTooltipText();
    }
}
