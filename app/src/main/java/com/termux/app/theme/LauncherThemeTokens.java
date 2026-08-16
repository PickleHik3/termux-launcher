package com.termux.app.theme;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The launcher's semantic colour tokens, and how a terminal colour scheme becomes a full set of them.
 *
 * <p>Every launcher surface — dock, status bar, app drawer, in-app keyboard, command palette, glass
 * rims — already resolves its colours through the {@code termuxColor*} theme attributes, which are
 * themselves Material roles. So the whole job of scheme theming is this one mapping: sixteen ANSI
 * colours plus a background and a foreground in, the role set out. Nothing downstream has to know a
 * scheme exists. That is the same shape as a Neovim colorscheme filling in highlight groups, and the
 * token names here are the user-facing contract in {@code ~/.termux/launcher-theme.properties},
 * exactly as highlight-group names are in a colorscheme file.
 *
 * <p>The mapping is anchored, not seeded. {@code background} <em>is</em> the surface and
 * {@code foreground} <em>is</em> the on-surface colour, so gruvbox stays gruvbox; only the tones the
 * scheme has no opinion about — container elevations, "on-container" text — are derived, as a tone
 * ladder off the background with contrast repaired afterwards. Generating a Material tonal palette
 * from a seed instead would harmonise better and look nothing like the scheme the user picked.
 */
public final class LauncherThemeTokens {

    public static final String SURFACE = "surface";
    public static final String SURFACE_DIM = "surface_dim";
    public static final String SURFACE_BRIGHT = "surface_bright";
    public static final String SURFACE_CONTAINER_LOWEST = "surface_container_lowest";
    public static final String SURFACE_CONTAINER_LOW = "surface_container_low";
    public static final String SURFACE_CONTAINER = "surface_container";
    public static final String SURFACE_CONTAINER_HIGH = "surface_container_high";
    public static final String SURFACE_CONTAINER_HIGHEST = "surface_container_highest";
    public static final String ON_SURFACE = "on_surface";
    public static final String ON_SURFACE_VARIANT = "on_surface_variant";
    public static final String OUTLINE = "outline";
    public static final String OUTLINE_VARIANT = "outline_variant";
    public static final String SCROLLBAR = "scrollbar";
    public static final String PRIMARY = "primary";
    public static final String ON_PRIMARY = "on_primary";
    public static final String PRIMARY_CONTAINER = "primary_container";
    public static final String ON_PRIMARY_CONTAINER = "on_primary_container";
    public static final String SECONDARY = "secondary";
    public static final String ON_SECONDARY = "on_secondary";
    public static final String SECONDARY_CONTAINER = "secondary_container";
    public static final String ON_SECONDARY_CONTAINER = "on_secondary_container";
    public static final String TERTIARY = "tertiary";
    public static final String ON_TERTIARY = "on_tertiary";
    public static final String TERTIARY_CONTAINER = "tertiary_container";
    public static final String ON_TERTIARY_CONTAINER = "on_tertiary_container";
    public static final String ERROR = "error";
    public static final String ON_ERROR = "on_error";
    public static final String ERROR_CONTAINER = "error_container";
    public static final String ON_ERROR_CONTAINER = "on_error_container";
    public static final String INVERSE_SURFACE = "inverse_surface";
    public static final String INVERSE_ON_SURFACE = "inverse_on_surface";
    public static final String INVERSE_PRIMARY = "inverse_primary";

    /** Every token, in the order the settings screen and the generated files list them. */
    public static final List<String> NAMES = Collections.unmodifiableList(Arrays.asList(
        SURFACE, SURFACE_DIM, SURFACE_BRIGHT,
        SURFACE_CONTAINER_LOWEST, SURFACE_CONTAINER_LOW, SURFACE_CONTAINER,
        SURFACE_CONTAINER_HIGH, SURFACE_CONTAINER_HIGHEST,
        ON_SURFACE, ON_SURFACE_VARIANT, OUTLINE, OUTLINE_VARIANT, SCROLLBAR,
        PRIMARY, ON_PRIMARY, PRIMARY_CONTAINER, ON_PRIMARY_CONTAINER,
        SECONDARY, ON_SECONDARY, SECONDARY_CONTAINER, ON_SECONDARY_CONTAINER,
        TERTIARY, ON_TERTIARY, TERTIARY_CONTAINER, ON_TERTIARY_CONTAINER,
        ERROR, ON_ERROR, ERROR_CONTAINER, ON_ERROR_CONTAINER,
        INVERSE_SURFACE, INVERSE_ON_SURFACE, INVERSE_PRIMARY));

    /** Body text on a surface. */
    private static final double TEXT_RATIO = 4.5d;
    /** Icons, accents and anything else the user is meant to see but not read. */
    private static final double ACCENT_RATIO = 3.0d;
    /** Hairlines: visible, never loud. */
    private static final double HAIRLINE_MIN_RATIO = 1.28d;
    private static final double HAIRLINE_MAX_RATIO = 2.6d;

    private LauncherThemeTokens() {}

    /**
     * The full token set for {@code scheme}.
     *
     * <p>Insertion-ordered so callers can write the result out and diff it by eye.
     */
    @NonNull
    public static LinkedHashMap<String, Integer> derive(@NonNull SchemeColors scheme) {
        LinkedHashMap<String, Integer> tokens = new LinkedHashMap<>();

        final int surface = scheme.background;
        final boolean dark = scheme.isDark();
        // Elevation raises tone in a dark scheme and lowers it in a light one, so "higher container"
        // always means "further from the surface in the direction the eye reads as raised".
        final double lift = dark ? 1d : -1d;

        final int accent = accent(scheme, surface);
        final int primary = SchemeTone.contrastTone(accent, surface, ACCENT_RATIO);
        final int secondary = SchemeTone.contrastTone(scheme.ansi(6), surface, ACCENT_RATIO);
        final int tertiary = SchemeTone.contrastTone(scheme.ansi(5), surface, ACCENT_RATIO);
        final int error = SchemeTone.contrastTone(scheme.ansi(1), surface, ACCENT_RATIO);

        tokens.put(SURFACE, surface);
        tokens.put(SURFACE_DIM, SchemeTone.toneShift(surface, dark ? -2d : -8d));
        tokens.put(SURFACE_BRIGHT, SchemeTone.toneShift(surface, dark ? 10d : 2d));
        tokens.put(SURFACE_CONTAINER_LOWEST, container(surface, primary, -1.5d * lift, 0f));
        tokens.put(SURFACE_CONTAINER_LOW, container(surface, primary, 1.5d * lift, 0.02f));
        tokens.put(SURFACE_CONTAINER, container(surface, primary, 3d * lift, 0.03f));
        tokens.put(SURFACE_CONTAINER_HIGH, container(surface, primary, 5.5d * lift, 0.05f));
        tokens.put(SURFACE_CONTAINER_HIGHEST, container(surface, primary, 8d * lift, 0.06f));

        int onSurface = SchemeTone.contrastTone(scheme.foreground, surface, TEXT_RATIO);
        int onSurfaceVariant = SchemeTone.contrastTone(
            SchemeTone.blend(scheme.foreground, surface, 0.35f), surface, TEXT_RATIO);
        tokens.put(ON_SURFACE, onSurface);
        tokens.put(ON_SURFACE_VARIANT, onSurfaceVariant);
        tokens.put(OUTLINE, SchemeTone.contrastTone(
            SchemeTone.blend(scheme.ansi(8), onSurface, 0.35f), surface, ACCENT_RATIO));
        tokens.put(OUTLINE_VARIANT, hairline(scheme.ansi(8), onSurface, surface));
        // A scrollbar is drawn over content, not over the surface, so it carries its own alpha the
        // way the stock palette's #66FFFFFF did rather than being a solid role colour.
        tokens.put(SCROLLBAR, SchemeTone.withAlpha(onSurfaceVariant, 0.4f));

        putAccent(tokens, PRIMARY, ON_PRIMARY, PRIMARY_CONTAINER, ON_PRIMARY_CONTAINER,
            primary, surface, scheme, dark);
        putAccent(tokens, SECONDARY, ON_SECONDARY, SECONDARY_CONTAINER, ON_SECONDARY_CONTAINER,
            secondary, surface, scheme, dark);
        putAccent(tokens, TERTIARY, ON_TERTIARY, TERTIARY_CONTAINER, ON_TERTIARY_CONTAINER,
            tertiary, surface, scheme, dark);
        putAccent(tokens, ERROR, ON_ERROR, ERROR_CONTAINER, ON_ERROR_CONTAINER,
            error, surface, scheme, dark);

        int inverseSurface = SchemeTone.toneShift(surface, dark ? 74d : -74d);
        tokens.put(INVERSE_SURFACE, inverseSurface);
        tokens.put(INVERSE_ON_SURFACE, SchemeTone.contrastTone(surface, inverseSurface, TEXT_RATIO));
        tokens.put(INVERSE_PRIMARY, SchemeTone.contrastTone(primary, inverseSurface, ACCENT_RATIO));

        return tokens;
    }

    /**
     * The scheme's accent colour.
     *
     * <p>A scheme's {@code cursor} is the one colour its author chose purely to be noticed against
     * the background, which makes it the best accent the file has — but plenty of schemes set it to
     * the foreground, and a foreground-coloured accent turns every highlighted control into a slab
     * of body text. So the cursor is used only when it is actually chromatic and actually distinct
     * from the foreground; otherwise blue (colour 4) takes the role, as it does in most terminal
     * themes.
     *
     * <p>"Distinct" is measured in hue and saturation as well as tone: gruvbox's yellow cursor and
     * its cream foreground sit at nearly the same luminance and nearly the same hue, and differ
     * mostly in how saturated they are — a contrast-only test would call the most deliberate accent
     * in the file a duplicate of the body text.
     */
    @ColorInt
    private static int accent(@NonNull SchemeColors scheme, @ColorInt int surface) {
        int cursor = scheme.cursor;
        boolean chromatic = SchemeTone.chroma(cursor) >= 12d;
        boolean distinctFromText = SchemeTone.hueDistance(cursor, scheme.foreground) > 20d
            || Math.abs(SchemeTone.tone(cursor) - SchemeTone.tone(scheme.foreground)) > 12d
            || SchemeTone.chroma(cursor) >= SchemeTone.chroma(scheme.foreground) * 1.8d;
        boolean visible = SchemeTone.contrastRatio(cursor, surface) >= 1.8d;
        if (chromatic && distinctFromText && visible) return cursor;
        return scheme.ansi(4);
    }

    /** One accent family: the role itself, its "on" colour, its container and the container's text. */
    private static void putAccent(@NonNull LinkedHashMap<String, Integer> tokens,
                                  @NonNull String roleKey, @NonNull String onRoleKey,
                                  @NonNull String containerKey, @NonNull String onContainerKey,
                                  @ColorInt int role, @ColorInt int surface,
                                  @NonNull SchemeColors scheme, boolean dark) {
        // Filled buttons put text on the role colour itself, and the only two colours guaranteed to
        // belong to this scheme are its background and its foreground — picking whichever of them
        // reads better keeps that text in-palette instead of dropping to raw black or white.
        int onRole = SchemeTone.contrastTone(
            SchemeTone.mostLegible(role, scheme.background, scheme.foreground), role, TEXT_RATIO);
        int container = SchemeTone.blend(role, surface, dark ? 0.70f : 0.78f);
        // Containers sit on the surface; if the blend leaves them indistinguishable from it, the
        // chips and pills that use them vanish.
        if (SchemeTone.contrastRatio(container, surface) < 1.12d) {
            container = SchemeTone.toneShift(container, dark ? 5d : -5d);
        }
        int onContainer = SchemeTone.contrastTone(
            SchemeTone.blend(role, scheme.foreground, 0.3f), container, TEXT_RATIO);

        tokens.put(roleKey, role);
        tokens.put(onRoleKey, onRole);
        tokens.put(containerKey, container);
        tokens.put(onContainerKey, onContainer);
    }

    @ColorInt
    private static int container(@ColorInt int surface, @ColorInt int primary, double toneDelta, float tint) {
        // Material tints elevated surfaces with a trace of the accent. Kept small on purpose: a
        // scheme's background is a deliberate colour, and washing it toward blue is exactly the
        // "gruvbox-ish" result anchoring was chosen to avoid.
        return SchemeTone.blend(SchemeTone.toneShift(surface, toneDelta), primary, tint);
    }

    /**
     * A divider colour: bright enough to see, dim enough not to become a border.
     */
    @ColorInt
    private static int hairline(@ColorInt int base, @ColorInt int onSurface, @ColorInt int surface) {
        int result = base;
        if (SchemeTone.contrastRatio(result, surface) < HAIRLINE_MIN_RATIO) {
            result = SchemeTone.contrastTone(
                SchemeTone.blend(result, onSurface, 0.5f), surface, HAIRLINE_MIN_RATIO);
        }
        return SchemeTone.capContrast(result, surface, HAIRLINE_MAX_RATIO);
    }
}
