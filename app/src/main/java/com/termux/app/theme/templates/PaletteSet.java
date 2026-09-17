package com.termux.app.theme.templates;

import java.util.Properties;

/**
 * The palettes one export describes: the active one, and — when the launcher could derive them —
 * the dark and light ones beside it.
 *
 * <p>The active palette is the one the terminal is wearing and the one the two long-standing
 * {@code material-colors.*} files carry. The other two are the same derivation run against a
 * configuration context with the night bits forced, so a template can dress a tool for the mode it
 * is not currently in — which is the whole point of the dark/light templates: nvim, fish and herdr
 * hold both tables and switch between them on the flip, without waiting for the launcher to export
 * again.
 *
 * <p>A set may carry only the active palette: the from-scheme path has exactly one palette by
 * definition, and a palette read back off an old phone's {@code material-colors.properties}
 * predates the dark and light files entirely. {@link #dark()} and {@link #light()} then hand back
 * the active one, so a template written against {@code .dark} renders something true rather than
 * failing. Deliberately free of Android imports, like the renderer it feeds.
 */
public final class PaletteSet {

    /** The mode words a template may name, and what each one resolves to. */
    public static final String MODE_DEFAULT = "default";
    public static final String MODE_DARK = "dark";
    public static final String MODE_LIGHT = "light";

    private final Properties mActive;

    private final Properties mDark;

    private final Properties mLight;

    private PaletteSet(Properties active, Properties dark, Properties light) {
        mActive = active;
        mDark = dark;
        mLight = light;
    }

    /** One palette, standing for all three modes. */
    public static PaletteSet of(Properties active) {
        return new PaletteSet(active, null, null);
    }

    /** All three, as a dynamic-colour pass derives them. {@code dark}/{@code light} may be null. */
    public static PaletteSet of(Properties active, Properties dark, Properties light) {
        return new PaletteSet(active, dark, light);
    }

    /** The palette the terminal is wearing; what {@code {{ mode }}} and {@code .default} describe. */
    public Properties active() {
        return mActive;
    }

    /** The dark palette, or the active one when this set does not carry a separate dark. */
    public Properties dark() {
        return mDark != null ? mDark : mActive;
    }

    /** The light palette, or the active one when this set does not carry a separate light. */
    public Properties light() {
        return mLight != null ? mLight : mActive;
    }

    /** Whether a dark palette of its own was derived — false when {@link #dark()} is the active one. */
    public boolean hasDark() {
        return mDark != null;
    }

    /** Whether a light palette of its own was derived. */
    public boolean hasLight() {
        return mLight != null;
    }

    /**
     * The palette a template's mode word names, or {@code null} when the word is not one of the
     * three. An unknown mode is the template's mistake and fails it, rather than quietly rendering
     * the active palette under a name that means something else.
     */
    public Properties forMode(String mode) {
        if (MODE_DEFAULT.equals(mode)) return mActive;
        if (MODE_DARK.equals(mode)) return dark();
        if (MODE_LIGHT.equals(mode)) return light();
        return null;
    }
}
