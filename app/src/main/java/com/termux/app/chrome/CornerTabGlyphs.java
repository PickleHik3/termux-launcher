package com.termux.app.chrome;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.R;

/**
 * What a corner tab's buttons are marked with. Every place on the wall answers a corner tap with
 * the same tab — the Widgets page, the Display page and a terminal pane — and each of them used to
 * carry its own copy of the code points, so the glyph that opens the editor was spelled out in
 * three files and the terminal drew its own by hand. Adding a button is one edit here.
 *
 * <p>These are Nerd Font code points, drawn with the bundled symbols face
 * ({@code NerdFontSpans.typeface}), which is why they are single strings rather than drawables: a
 * tab button is one glyph in one paint, at whatever size the tab is scaled to.
 *
 * <p>Help is the odd one out and always was — it is the question mark itself, in the tab's own
 * text font, so it reads as a word rather than an icon. It lives here anyway, because "what goes
 * on a corner tab button" is the question this class answers.
 */
public final class CornerTabGlyphs {

    private CornerTabGlyphs() {
    }

    /** nf-fa-gear: a place's own settings. */
    public static final String SETTINGS = "";

    /** nf-fa-pencil: start editing what the place is holding. */
    public static final String EDIT = "";

    /** nf-fa-power_off: turn the display on or off. */
    public static final String POWER = "";

    /**
     * nf-md-palette (U+F03D8, a surrogate pair like {@link #LAYOUT}): the Appearance editor. The
     * same palette the editor itself wears on its floating pill and its shared-layer heading
     * ({@code ic_symbol_palette}), so the button and the thing it opens carry one mark. It used to
     * be nf-fa-sliders, which read as "settings" and told nobody a palette was behind it.
     */
    public static final String APPEARANCE = "\uDB80\uDFD8";

    /**
     * nf-fa-book: the whole of help, topic by topic — what the floating catalogue button beside an
     * open guide opens. A book rather than a list, because what it opens is the reference, not one
     * more menu.
     */
    public static final String CATALOGUE = "";

    /**
     * nf-md-view_dashboard (U+F056E, a surrogate pair): the Layout editor — where a place's bars,
     * dock, keyboard and grid sit. The Material Design icons Nerd Fonts carries live in plane 15,
     * so this one is two chars where the Font Awesome glyphs above are one.
     */
    public static final String LAYOUT = "󰕮";

    /** The question mark the help button wears, in the tab's text font rather than the symbols one. */
    @NonNull
    public static String help(@NonNull Context context) {
        return context.getString(R.string.help_button);
    }
}
