package com.termux.app.terminal;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.ContextThemeWrapper;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.color.utilities.Hct;
import com.termux.R;
import com.termux.app.theme.templates.PaletteSet;
import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class MaterialTerminalColorScheme {

    private static final String LOG_TAG = "MaterialTerminalColorScheme";
    private static final String MATERIAL_COLORS_PROPERTIES_PATH = TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/material-colors.properties";
    private static final String MATERIAL_COLORS_SHELL_PATH = TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/material-colors.sh";

    /**
     * The two mode files beside them. Same format, same keys, same writers — only {@code mode} is
     * fixed rather than derived, so a tool can source one of these to dress itself for the mode it
     * is about to be in. The two files above stay exactly what they were: the active palette, which
     * is what {@code config.fish} and every older consumer reads.
     */
    private static final String MATERIAL_COLORS_DARK_PROPERTIES_PATH = TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/material-colors-dark.properties";
    private static final String MATERIAL_COLORS_DARK_SHELL_PATH = TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/material-colors-dark.sh";
    private static final String MATERIAL_COLORS_LIGHT_PROPERTIES_PATH = TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/material-colors-light.properties";
    private static final String MATERIAL_COLORS_LIGHT_SHELL_PATH = TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/material-colors-light.sh";

    /**
     * Canonical ANSI hues for slots 1–6 — red, green, yellow, blue, magenta, cyan — before
     * harmonization. These are what makes a green read as green; the theme supplies everything else.
     */
    private static final double[] ANSI_HUE_ANCHORS = {25d, 145d, 85d, 255d, 330d, 195d};

    /**
     * How far the palette's chroma may travel. The floor keeps a near-grey wallpaper from producing
     * six indistinguishable slots; the ceiling keeps a vivid one from producing the 2014-accent neon
     * this replaced. Between them the slots are exactly as saturated as the theme is.
     */
    private static final double ANSI_CHROMA_MIN = 28d;
    private static final double ANSI_CHROMA_MAX = 52d;

    /**
     * How much chroma the neutral slots and the foreground carry.
     *
     * <p>They come off the neutral palette, so left alone they are the surface hue at almost no
     * chroma — a grey ladder on a grey background, which is what the retired
     * {@code material-terminal-white.fish} was painting over on the phone. The floor is the warm
     * nudge itself: a neutral with no chroma cannot lean anywhere. The ceiling keeps it a neutral —
     * above it the ladder starts reading as a seventh accent.
     */
    private static final double NEUTRAL_CHROMA_MIN = 8d;
    private static final double NEUTRAL_CHROMA_MAX = 12d;

    /**
     * Where the neutrals lean: the warm side of the wheel, around parchment. The surface hue is
     * pulled halfway toward it and never further than {@link #NEUTRAL_MAX_ROTATION}, so a theme
     * already warm barely moves and a cold blue one warms without turning yellow. The background is
     * not nudged — it is the chrome's own surface, and the terminal has to sit on it.
     */
    private static final double NEUTRAL_WARM_HUE = 75d;
    private static final double NEUTRAL_MAX_ROTATION = 20d;

    /** Material's {@code Blend.harmonize} ceiling: never rotate a hue more than this far. */
    private static final double HARMONIZE_MAX_ROTATION = 15d;

    private MaterialTerminalColorScheme() {}

    /**
     * Build a palette for an explicit level; public so ratio and signature tests are deterministic.
     *
     * <p>Colour keys only. The result goes straight to
     * {@code TerminalColors.COLOR_SCHEME.updateWith()}, which throws {@code IllegalArgumentException}
     * on any key that is not {@code foreground} / {@code background} / {@code cursor} / {@code colorN}
     * — mid-iteration over an unordered map, so a stray key leaves the palette half applied. The
     * contrast level travels beside this palette, not inside it; see
     * {@link #createMaterialRoleProperties}.
     */
    @NonNull
    public static Properties create(@NonNull Context context, @NonNull TerminalContrastLevel level) {
        Properties props = new Properties();

        int surface = materialColor(context, com.google.android.material.R.attr.colorSurface,
            R.color.termux_surface_base);
        int foreground = materialColor(context, com.google.android.material.R.attr.colorOnSurface,
            R.color.termux_on_surface);
        int primary = materialColor(context, com.google.android.material.R.attr.colorPrimary,
            R.color.termux_primary);
        // Raw, not materialColor: the anchor is only replaced when the theme really carries an error
        // role. An app-resource fallback would be a colour of ours, not one of the theme's, and the
        // whole point of the substitution is to keep red inside the theme's own tonal system.
        int themeError = MaterialColors.getColor(context, com.google.android.material.R.attr.colorError, 0);

        boolean dark = perceivedBrightness(surface) < 128;
        // Read before the tone move so a surface pushed to tone 4 or 99 — where HCT cannot hold much
        // chroma — still reports the neutral hue the rest of the theme was built from.
        Hct surfaceHct = Hct.fromInt(surface);
        Hct primaryHct = Hct.fromInt(primary);

        int background = surfaceTone(surface, level);

        // The foreground is a neutral too — the same nudge as slots 0/7/8/15, before the legibility
        // search, which keeps hue and chroma and only moves tone. The cursor is an accent and is
        // left alone.
        foreground = warmNeutral(foreground, surfaceHct.getHue());
        foreground = contrastTone(foreground, background, level.foregroundRatio);
        primary = contrastTone(primary, background, level.cursorRatio);

        props.setProperty("background", hex(background));
        props.setProperty("foreground", hex(foreground));
        props.setProperty("cursor", hex(primary));

        props.putAll(ansiSlots(primaryHct.getHue(), primaryHct.getChroma(),
            themeError != 0 ? Hct.fromInt(themeError).getHue() : ANSI_HUE_ANCHORS[0],
            surfaceHct.getHue(), surfaceHct.getChroma(), dark));

        applyAnsiContrastFloor(props, background, level);

        return props;
    }

    /**
     * The legibility floor, per slot.
     *
     * <p>Not every ANSI slot is text. Black and white are what a TUI fills a panel with, and a floor
     * that treats them as glyph colours lifts both to the same mid tone as everything else — which is
     * how ANSI black stopped being dark and started matching bright black. So slots 0, 7 and 15 are
     * exempt — bright white is a fill exactly as much as white is, and holding it to a text ratio is
     * what used to drag it down into the dark end of the neutral ladder; slot 8 keeps a fixed 3.0:1
     * because it really is text — dim text, the one thing the level must not be allowed to brighten
     * into ordinary text; the rest take the level's ratio.
     */
    @VisibleForTesting
    static double ansiFloor(int slot, @NonNull TerminalContrastLevel level) {
        if (slot == 0 || slot == 7 || slot == 15) return 0d;
        if (slot == 8) return 3.0d;
        return level.ansiRatio;
    }

    /** {@link #ansiFloor} over all sixteen slots, in place. */
    @VisibleForTesting
    static void applyAnsiContrastFloor(@NonNull Properties props, @ColorInt int background,
                                       @NonNull TerminalContrastLevel level) {
        for (int slot = 0; slot < 16; slot++) {
            double floor = ansiFloor(slot, level);
            if (floor <= 0d) continue;
            String key = "color" + slot;
            props.setProperty(key,
                hex(contrastTone(Color.parseColor(props.getProperty(key)), background, floor)));
        }
    }

    /**
     * The sixteen ANSI slots as one Material 3 tonal system, with no {@code Context} in sight.
     *
     * <p>Every accent slot is the same colour three ways: the theme's own chroma, a tone band chosen
     * by the background, and a hue that is the canonical ANSI anchor pulled toward the theme. That is
     * what makes the set read as one palette rather than six borrowed accents — the older derivation
     * blended fixed 2014 Material anchors toward the roles, which ignored the wallpaper's chroma
     * entirely and left every terminal with the same neon green.
     *
     * <p>{@code redHue} is passed in rather than taken from {@link #ANSI_HUE_ANCHORS} so a theme that
     * carries an error role can spend it here: red is the one ANSI slot Material already has an
     * opinion about.
     */
    @NonNull
    @VisibleForTesting
    static Properties ansiSlots(double sourceHue, double sourceChroma, double redHue,
                                double neutralHue, double neutralChroma, boolean dark) {
        Properties slots = new Properties();
        double chroma = Math.max(ANSI_CHROMA_MIN, Math.min(ANSI_CHROMA_MAX, sourceChroma));
        double normalTone = dark ? 80d : 40d;
        double brightTone = dark ? 90d : 30d;
        for (int slot = 1; slot <= 6; slot++) {
            double hue = harmonizeHue(slot == 1 ? redHue : ANSI_HUE_ANCHORS[slot - 1], sourceHue);
            slots.setProperty("color" + slot, hex(Hct.from(hue, chroma, normalTone).toInt()));
            slots.setProperty("color" + (slot + 8), hex(Hct.from(hue, chroma, brightTone).toInt()));
        }
        // One ladder, both modes: black, bright black, white, bright white climb in tone whichever
        // way round the background is. The light column used to end at tone 10, which made bright
        // white the darkest neutral of the four and collapsed "black on bright white" into one
        // colour; the accent bands above still flip with the background, the neutrals do not.
        //
        // The hue is the surface's, warmed; the chroma is held inside the neutral band. Tones are
        // untouched by either — the ladder is the ladder whatever colour it is made of.
        double warm = warmNeutralHue(neutralHue);
        double neutral = warmNeutralChroma(neutralChroma);
        slots.setProperty("color0", hex(Hct.from(warm, neutral, 25d).toInt()));
        slots.setProperty("color8", hex(Hct.from(warm, neutral, dark ? 45d : 50d).toInt()));
        slots.setProperty("color7", hex(Hct.from(warm, neutral, dark ? 80d : 75d).toInt()));
        slots.setProperty("color15", hex(Hct.from(warm, neutral, dark ? 96d : 92d).toInt()));
        return slots;
    }

    /**
     * {@code surfaceHue} leaning toward {@link #NEUTRAL_WARM_HUE}: halfway there, at most
     * {@link #NEUTRAL_MAX_ROTATION}. A surface already at the warm hue does not move at all.
     */
    @VisibleForTesting
    static double warmNeutralHue(double surfaceHue) {
        return blendHue(surfaceHue, NEUTRAL_WARM_HUE, NEUTRAL_MAX_ROTATION);
    }

    /** {@code chroma} inside the neutral band — raised to the floor, held under the ceiling. */
    @VisibleForTesting
    static double warmNeutralChroma(double chroma) {
        return Math.max(NEUTRAL_CHROMA_MIN, Math.min(NEUTRAL_CHROMA_MAX, chroma));
    }

    /** {@code color} rebuilt as a warm neutral: the nudged hue and band chroma at its own tone. */
    @ColorInt
    private static int warmNeutral(@ColorInt int color, double surfaceHue) {
        Hct source = Hct.fromInt(color);
        return Hct.from(warmNeutralHue(surfaceHue), warmNeutralChroma(source.getChroma()),
            source.getTone()).toInt();
    }

    /**
     * {@code anchor} rotated toward {@code source} by half the angle between them, at most 15°.
     *
     * <p>The rule and both numbers are Material's {@code Blend.harmonize}, applied to the hue alone:
     * the slot keeps the chroma and tone this palette assigns it, and only its hue is pulled into the
     * theme. Halving the distance is what makes the pull proportional — a hue already near the
     * theme's barely moves, a hue on the far side moves the full 15° and no further, so a green stays
     * a green.
     */
    @VisibleForTesting
    static double harmonizeHue(double anchor, double source) {
        return blendHue(anchor, source, HARMONIZE_MAX_ROTATION);
    }

    /** {@code anchor} rotated halfway toward {@code source}, capped at {@code maxRotation}. */
    private static double blendHue(double anchor, double source, double maxRotation) {
        double rotation = Math.min(differenceDegrees(anchor, source) * 0.5d, maxRotation);
        return sanitizeDegrees(anchor + rotation * rotationDirection(anchor, source));
    }

    /** Shortest angle between two hues, 0–180. */
    private static double differenceDegrees(double first, double second) {
        return 180d - Math.abs(Math.abs(first - second) - 180d);
    }

    /** {@code +1} to reach {@code to} by increasing {@code from}, {@code -1} by decreasing it. */
    private static double rotationDirection(double from, double to) {
        return sanitizeDegrees(to - from) <= 180d ? 1d : -1d;
    }

    private static double sanitizeDegrees(double degrees) {
        double wrapped = degrees % 360d;
        return wrapped < 0d ? wrapped + 360d : wrapped;
    }

    /**
     * The whole export: the active palette plus a dark and a light one.
     *
     * <p>Both halves are the same derivation as the active one, run against a configuration context
     * with the {@code UI_MODE_NIGHT_*} bits forced and re-themed with the activity's own DayNight
     * theme — the trick {@code TermuxApplication}'s background refresh already uses. Dynamic colours
     * are pure resource qualifiers ({@code values-v31} / {@code values-night-v31}), so a forced
     * configuration resolves the other mode's roles exactly as the activity would in it; no activity
     * and no recreation is involved.
     *
     * <p>Must run on a thread that may resolve theme attributes and resources — in practice the main
     * thread, like every other {@link #create} call.
     */
    @NonNull
    public static PaletteSet createPaletteSet(@NonNull Context context,
                                              @NonNull TerminalContrastLevel level) {
        return createPaletteSet(context, level, create(context, level));
    }

    /**
     * As {@link #createPaletteSet(Context, TerminalContrastLevel)}, for a caller that has already
     * built the active terminal palette and handed it to the terminal — the exported files then
     * describe exactly the colours the sessions took, rather than a second derivation of them.
     */
    @NonNull
    public static PaletteSet createPaletteSet(@NonNull Context context,
                                              @NonNull TerminalContrastLevel level,
                                              @NonNull Properties activeTerminalProps) {
        Properties active = createMaterialRoleProperties(context, activeTerminalProps, level);
        return PaletteSet.of(active,
            paletteForNightMode(context, level, Configuration.UI_MODE_NIGHT_YES),
            paletteForNightMode(context, level, Configuration.UI_MODE_NIGHT_NO));
    }

    /** The export as it would be with {@code nightMode} forced, or {@code null} if that failed. */
    @Nullable
    private static Properties paletteForNightMode(@NonNull Context context,
                                                  @NonNull TerminalContrastLevel level,
                                                  int nightMode) {
        try {
            Configuration configuration = new Configuration(context.getResources().getConfiguration());
            configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
            Context themed = new ContextThemeWrapper(
                context.createConfigurationContext(configuration),
                R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            return createMaterialRoleProperties(themed, create(themed, level), level);
        } catch (RuntimeException e) {
            // A palette for the mode the user is not in is worth having, never worth failing the
            // refresh for: the active one is what the terminal is about to wear.
            Logger.logStackTraceWithMessage(LOG_TAG,
                "Cannot derive the palette for uiMode night " + nightMode, e);
            return null;
        }
    }

    /**
     * Role properties around an already-built terminal palette.
     *
     * <p>The palette is passed in rather than rebuilt so the exported files describe exactly the colours
     * the terminal was given, and so the caller pays for one HCT search instead of two.
     *
     * <p>This is where {@code contrast_level} lives. It is a description of the palette, not a colour
     * in it, so it is carried alongside the terminal properties rather than inside them.
     */
    @NonNull
    public static Properties createMaterialRoleProperties(@NonNull Context context,
                                                          @NonNull Properties terminalProps,
                                                          @NonNull TerminalContrastLevel level) {
        Properties props = new Properties();

        putMaterialColor(props, "primary", context, com.google.android.material.R.attr.colorPrimary,
            R.color.termux_primary);
        putMaterialColor(props, "on_primary", context, com.google.android.material.R.attr.colorOnPrimary,
            R.color.termux_on_primary);
        putMaterialColor(props, "secondary", context, com.google.android.material.R.attr.colorSecondary,
            R.color.termux_secondary);
        putMaterialColor(props, "on_secondary", context, com.google.android.material.R.attr.colorOnSecondary,
            R.color.termux_on_secondary);
        putMaterialColor(props, "tertiary", context, com.google.android.material.R.attr.colorTertiary,
            R.color.termux_primary);
        putMaterialColor(props, "on_tertiary", context, com.google.android.material.R.attr.colorOnTertiary,
            R.color.termux_on_primary);
        putMaterialColor(props, "error", context, com.google.android.material.R.attr.colorError,
            R.color.termux_error);
        putMaterialColor(props, "on_error", context, com.google.android.material.R.attr.colorOnError,
            R.color.termux_surface_base);
        putMaterialColor(props, "error_container", context, com.google.android.material.R.attr.colorErrorContainer,
            R.color.termux_error_container);
        // The "on-" partner of every container this exports. Without them a theme cannot draw a
        // filled chip with guaranteed contrast — it has to borrow an accent role, which is why the
        // bundled prompt was painting a virtualenv in the error colours.
        putMaterialColor(props, "on_error_container", context,
            com.google.android.material.R.attr.colorOnErrorContainer, R.color.termux_error);
        putMaterialColor(props, "primary_container", context,
            com.google.android.material.R.attr.colorPrimaryContainer, R.color.termux_primary);
        putMaterialColor(props, "on_primary_container", context,
            com.google.android.material.R.attr.colorOnPrimaryContainer, R.color.termux_on_primary);
        putMaterialColor(props, "secondary_container", context,
            com.google.android.material.R.attr.colorSecondaryContainer, R.color.termux_secondary);
        putMaterialColor(props, "on_secondary_container", context,
            com.google.android.material.R.attr.colorOnSecondaryContainer, R.color.termux_on_secondary);
        putMaterialColor(props, "tertiary_container", context,
            com.google.android.material.R.attr.colorTertiaryContainer, R.color.termux_primary);
        putMaterialColor(props, "on_tertiary_container", context,
            com.google.android.material.R.attr.colorOnTertiaryContainer, R.color.termux_on_primary);
        putMaterialColor(props, "outline", context, com.google.android.material.R.attr.colorOutline,
            R.color.termux_on_surface_variant);
        putMaterialColor(props, "surface", context, com.google.android.material.R.attr.colorSurface,
            R.color.termux_surface_base);
        putMaterialColor(props, "surface_variant", context, com.google.android.material.R.attr.colorSurfaceVariant,
            R.color.termux_surface_panel);
        putMaterialColor(props, "surface_container", context, com.google.android.material.R.attr.colorSurfaceContainer,
            R.color.termux_surface_panel);
        putMaterialColor(props, "surface_container_high", context, com.google.android.material.R.attr.colorSurfaceContainerHigh,
            R.color.termux_surface_panel_high);
        putMaterialColor(props, "surface_container_highest", context, com.google.android.material.R.attr.colorSurfaceContainerHighest,
            R.color.termux_surface_panel_highest);
        putMaterialColor(props, "on_surface", context, com.google.android.material.R.attr.colorOnSurface,
            R.color.termux_on_surface);
        putMaterialColor(props, "on_surface_variant", context, com.google.android.material.R.attr.colorOnSurfaceVariant,
            R.color.termux_on_surface_variant);
        putMaterialColor(props, "outline_variant", context, com.google.android.material.R.attr.colorOutlineVariant,
            R.color.termux_outline_variant);

        // The rest of noctalia's 48-role set. Material Components 1.12.0 defines all of these
        // attributes on its own M3 themes, but a theme that does not derive from one of those
        // (or an old dynamic-color theme) can leave any of them unresolved; MaterialColors.getColor
        // then hands back the sentinel below instead of throwing, and we fall back to the nearest
        // role already in this map rather than to a made-up app resource, so the exported file is
        // always complete even off such a theme.
        putRoleColor(props, "primary_fixed", context,
            com.google.android.material.R.attr.colorPrimaryFixed, "primary_container");
        putRoleColor(props, "primary_fixed_dim", context,
            com.google.android.material.R.attr.colorPrimaryFixedDim, "primary");
        putRoleColor(props, "on_primary_fixed", context,
            com.google.android.material.R.attr.colorOnPrimaryFixed, "on_primary_container");
        putRoleColor(props, "on_primary_fixed_variant", context,
            com.google.android.material.R.attr.colorOnPrimaryFixedVariant, "on_primary_container");
        putRoleColor(props, "secondary_fixed", context,
            com.google.android.material.R.attr.colorSecondaryFixed, "secondary_container");
        putRoleColor(props, "secondary_fixed_dim", context,
            com.google.android.material.R.attr.colorSecondaryFixedDim, "secondary");
        putRoleColor(props, "on_secondary_fixed", context,
            com.google.android.material.R.attr.colorOnSecondaryFixed, "on_secondary_container");
        putRoleColor(props, "on_secondary_fixed_variant", context,
            com.google.android.material.R.attr.colorOnSecondaryFixedVariant, "on_secondary_container");
        putRoleColor(props, "tertiary_fixed", context,
            com.google.android.material.R.attr.colorTertiaryFixed, "tertiary_container");
        putRoleColor(props, "tertiary_fixed_dim", context,
            com.google.android.material.R.attr.colorTertiaryFixedDim, "tertiary");
        putRoleColor(props, "on_tertiary_fixed", context,
            com.google.android.material.R.attr.colorOnTertiaryFixed, "on_tertiary_container");
        putRoleColor(props, "on_tertiary_fixed_variant", context,
            com.google.android.material.R.attr.colorOnTertiaryFixedVariant, "on_tertiary_container");
        putRoleColor(props, "surface_dim", context,
            com.google.android.material.R.attr.colorSurfaceDim, "surface");
        putRoleColor(props, "surface_bright", context,
            com.google.android.material.R.attr.colorSurfaceBright, "surface");
        putRoleColor(props, "surface_container_lowest", context,
            com.google.android.material.R.attr.colorSurfaceContainerLowest, "surface");
        putRoleColor(props, "surface_container_low", context,
            com.google.android.material.R.attr.colorSurfaceContainerLow, "surface");
        putRoleColor(props, "background", context,
            android.R.attr.colorBackground, "surface");
        putRoleColor(props, "on_background", context,
            com.google.android.material.R.attr.colorOnBackground, "on_surface");
        putRoleColor(props, "inverse_surface", context,
            com.google.android.material.R.attr.colorSurfaceInverse, "on_surface");
        putRoleColor(props, "inverse_on_surface", context,
            com.google.android.material.R.attr.colorOnSurfaceInverse, "surface");
        putRoleColor(props, "inverse_primary", context,
            com.google.android.material.R.attr.colorPrimaryInverse, "primary");
        // Not theme attributes: noctalia expects a literal black for both regardless of contrast.
        props.setProperty("shadow", "#000000");
        props.setProperty("scrim", "#000000");

        props.setProperty("contrast_level", level.value);
        for (String key : terminalProps.stringPropertyNames()) {
            props.setProperty("terminal_" + key, terminalProps.getProperty(key));
        }

        // noctalia's terminal_* names, as byte-identical aliases of the keys above.
        String[] ansiNames = {"black", "red", "green", "yellow", "blue", "magenta", "cyan", "white"};
        for (int i = 0; i < ansiNames.length; i++) {
            props.setProperty("terminal_normal_" + ansiNames[i], props.getProperty("terminal_color" + i));
            props.setProperty("terminal_bright_" + ansiNames[i], props.getProperty("terminal_color" + (i + 8)));
        }
        props.setProperty("terminal_cursor_text", props.getProperty("terminal_background"));
        props.setProperty("terminal_selection_fg", props.getProperty("on_surface_variant"));
        props.setProperty("terminal_selection_bg", props.getProperty("surface_variant"));

        // dark below tone 50, light at or above it — the same split MaterialTerminalColorScheme
        // already uses internally via perceivedBrightness, but expressed in HCT tone since that is
        // what the rest of this export is built from.
        double backgroundTone = Hct.fromInt(Color.parseColor(terminalProps.getProperty("background"))).getTone();
        props.setProperty("mode", backgroundTone < 50 ? "dark" : "light");

        return props;
    }

    /**
     * Write the exported palette files. Takes the finished properties rather than a {@link Context}
     * because this runs on a writer thread: resolving theme attributes and reading resources off the
     * main thread is not safe, so all of that has to have happened before the hand-off.
     *
     * <p>Not a public entry point for a refresh: call
     * {@code ThemeTemplates.exportPaletteAndRunPassAsync}, which owns the one thread these writes and
     * the template pass that follows them share.
     */
    public static void writeMaterialColorFiles(@NonNull Properties props) {
        writeFile(MATERIAL_COLORS_PROPERTIES_PATH, toPropertiesText(props));
        writeFile(MATERIAL_COLORS_SHELL_PATH, toShellExports(props));
    }

    /**
     * The active palette's two files, plus one pair per mode the set actually carries.
     *
     * <p>A set with only an active palette — the from-scheme path, which has one palette by
     * definition — writes only the two active files and leaves any mode files a previous dynamic
     * pass left behind alone: they are stale either way, and deleting a file a user's config may be
     * sourcing is the worse of the two.
     */
    public static void writeMaterialColorFiles(@NonNull PaletteSet palettes) {
        writeMaterialColorFiles(palettes.active());
        if (palettes.hasDark())
            writeModeFiles(MATERIAL_COLORS_DARK_PROPERTIES_PATH, MATERIAL_COLORS_DARK_SHELL_PATH,
                palettes.dark(), PaletteSet.MODE_DARK);
        if (palettes.hasLight())
            writeModeFiles(MATERIAL_COLORS_LIGHT_PROPERTIES_PATH, MATERIAL_COLORS_LIGHT_SHELL_PATH,
                palettes.light(), PaletteSet.MODE_LIGHT);
    }

    private static void writeModeFiles(@NonNull String propertiesPath, @NonNull String shellPath,
                                       @NonNull Properties palette, @NonNull String mode) {
        Properties fixed = withMode(palette, mode);
        writeFile(propertiesPath, toPropertiesText(fixed));
        writeFile(shellPath, toShellExports(fixed));
    }

    /**
     * {@code palette} with {@code mode} stated rather than derived.
     *
     * <p>The mode file says what it is for. The derived value is the same in every ordinary case —
     * the dark palette's background really is dark — but a theme can hand back a light surface under
     * {@code values-night}, and a file named {@code -dark} that says {@code mode=light} is a trap for
     * the config reading it. The palette handed in is not modified: it is the one the caller may
     * still be rendering templates from.
     */
    @NonNull
    @VisibleForTesting
    static Properties withMode(@NonNull Properties palette, @NonNull String mode) {
        Properties copy = new Properties();
        copy.putAll(palette);
        copy.setProperty("mode", mode);
        return copy;
    }

    /**
     * Whether the exported files already say exactly this. Shells watch these files by modification
     * time — the bundled fish config re-sources the palette when it moves — so rewriting identical
     * content is not free: it makes every open shell reload on its next prompt, and re-runs the tmux
     * theme script inside tmux.
     */
    @VisibleForTesting
    static boolean alreadyOnDisk(@NonNull String path, @NonNull String content) {
        java.io.File file = new java.io.File(path);
        byte[] wanted = content.getBytes(StandardCharsets.UTF_8);
        if (!file.isFile() || file.length() != wanted.length) return false;
        // Read raw rather than through FileUtils.readTextFromFile: that joins lines with \n and so
        // drops the trailing newline these files end with, which made every comparison fail and every
        // refresh rewrite identical content.
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            byte[] existing = new byte[wanted.length];
            int read = 0;
            while (read < wanted.length) {
                int step = in.read(existing, read, wanted.length - read);
                if (step < 0) return false;
                read += step;
            }
            return in.read() < 0 && java.util.Arrays.equals(existing, wanted);
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /**
     * Every Material role the exported palette is derived from, in a fixed order.
     *
     * <p>The signature has to cover all of them, not just the accents: a wallpaper can move the
     * neutral-variant tones — which is what the bundled prompt fills its slabs with — while leaving
     * primary, secondary and tertiary where they were, and such a change used to read as "unchanged".
     */
    private static final int[] PALETTE_ATTRS = {
        com.google.android.material.R.attr.colorPrimary,
        com.google.android.material.R.attr.colorOnPrimary,
        com.google.android.material.R.attr.colorPrimaryContainer,
        com.google.android.material.R.attr.colorOnPrimaryContainer,
        com.google.android.material.R.attr.colorSecondary,
        com.google.android.material.R.attr.colorOnSecondary,
        com.google.android.material.R.attr.colorSecondaryContainer,
        com.google.android.material.R.attr.colorOnSecondaryContainer,
        com.google.android.material.R.attr.colorTertiary,
        com.google.android.material.R.attr.colorOnTertiary,
        com.google.android.material.R.attr.colorTertiaryContainer,
        com.google.android.material.R.attr.colorOnTertiaryContainer,
        com.google.android.material.R.attr.colorError,
        com.google.android.material.R.attr.colorOnError,
        com.google.android.material.R.attr.colorErrorContainer,
        com.google.android.material.R.attr.colorOnErrorContainer,
        com.google.android.material.R.attr.colorSurface,
        com.google.android.material.R.attr.colorSurfaceVariant,
        com.google.android.material.R.attr.colorSurfaceContainer,
        com.google.android.material.R.attr.colorSurfaceContainerHigh,
        com.google.android.material.R.attr.colorSurfaceContainerHighest,
        com.google.android.material.R.attr.colorOnSurface,
        com.google.android.material.R.attr.colorOnSurfaceVariant,
        com.google.android.material.R.attr.colorOutline,
        com.google.android.material.R.attr.colorOutlineVariant,
    };

    /**
     * Cheap fingerprint of the palette this would generate: the resolved role colours plus the
     * contrast level. Attribute lookups only — deliberately not a {@link #create} and a hash of the
     * result, since the point of the fingerprint is to decide whether that work is needed at all.
     */
    public static int signature(@NonNull Context context, @NonNull TerminalContrastLevel level) {
        int result = 17;
        for (int attr : PALETTE_ATTRS) {
            result = 31 * result + MaterialColors.getColor(context, attr, 0);
        }
        return 31 * result + level.ordinal();
    }

    /** WCAG relative-luminance contrast ratio. */
    public static double contrastRatio(@ColorInt int first, @ColorInt int second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05d) / (Math.min(a, b) + 0.05d);
    }

    @ColorInt
    private static int contrastTone(@ColorInt int color, @ColorInt int surface, double target) {
        if (contrastRatio(color, surface) >= target) return color;
        Hct source = Hct.fromInt(color);
        int best = color;
        double bestDistance = Double.MAX_VALUE;
        // HCT keeps semantic hue/chroma while tone supplies the requested legibility. Searching all
        // displayable tones is more robust than assuming dark themes always want a lighter glyph.
        for (int tone = 0; tone <= 100; tone++) {
            int candidate = Hct.from(source.getHue(), source.getChroma(), tone).toInt();
            if (contrastRatio(candidate, surface) < target) continue;
            double distance = Math.abs(tone - source.getTone());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * The generated terminal background alone.
     *
     * <p>Split out because the surface colour is all the wallpaper-mode overlay needs, and reading it
     * off a full {@link #create} was costing a 101-tone HCT contrast search for the foreground, the
     * cursor and all sixteen ANSI colours — around 700µs on a desktop JVM, several milliseconds on a
     * phone — every time the terminal surface was restyled.
     */
    @ColorInt
    public static int backgroundColor(@NonNull Context context,
                                      @NonNull TerminalContrastLevel level) {
        return surfaceTone(materialColor(context, com.google.android.material.R.attr.colorSurface,
            R.color.termux_surface_base), level);
    }

    @ColorInt
    @VisibleForTesting
    static int surfaceTone(@ColorInt int color, @NonNull TerminalContrastLevel level) {
        boolean dark = perceivedBrightness(color) < 128;
        Hct source = Hct.fromInt(color);
        double tone;
        switch (level) {
            case SOFTER: tone = dark ? 14d : 94d; break;
            case HARDER: tone = dark ? 4d : 99d; break;
            default: tone = dark ? 8d : 97d; break;
        }
        return Hct.from(source.getHue(), source.getChroma(), tone).toInt();
    }

    private static double luminance(@ColorInt int color) {
        return 0.2126d * linear(Color.red(color) / 255d)
            + 0.7152d * linear(Color.green(color) / 255d)
            + 0.0722d * linear(Color.blue(color) / 255d);
    }

    private static double linear(double channel) {
        return channel <= 0.04045d ? channel / 12.92d
            : Math.pow((channel + 0.055d) / 1.055d, 2.4d);
    }

    @ColorInt
    private static int materialColor(@NonNull Context context, int attr, int fallbackRes) {
        return MaterialColors.getColor(context, attr, ContextCompat.getColor(context, fallbackRes));
    }

    private static void putMaterialColor(@NonNull Properties props, @NonNull String key, @NonNull Context context,
                                         int attr, int fallbackRes) {
        props.setProperty(key, hex(materialColor(context, attr, fallbackRes)));
    }

    /**
     * Like {@link #putMaterialColor}, but for the roles that have no app resource of their own to
     * fall back to: an unresolved attribute copies the value already recorded under
     * {@code fallbackKey} instead. {@code fallbackKey} must already be in {@code props}.
     */
    private static void putRoleColor(@NonNull Properties props, @NonNull String key, @NonNull Context context,
                                     int attr, @NonNull String fallbackKey) {
        int resolved = MaterialColors.getColor(context, attr, 0);
        int value = resolved != 0 ? resolved : Color.parseColor(props.getProperty(fallbackKey));
        props.setProperty(key, hex(value));
    }

    /**
     * Replace the file at {@code path} in one step.
     *
     * <p>Written to a sibling temp file and renamed over the target, because these files are sourced
     * rather than read: a shell that starts while a plain truncating write is half done sources a
     * file cut off mid-line, and the prompt it builds from it is wrong until something rewrites the
     * palette. A rename within the directory swaps the whole file or none of it, so a shell either
     * gets the old palette or the new one.
     */
    @VisibleForTesting
    static void writeFile(@NonNull String path, @NonNull String content) {
        if (alreadyOnDisk(path, content)) return;
        Error error = FileUtils.createParentDirectoryFile(LOG_TAG + " palette file parent", path);
        if (error != null) {
            Logger.logErrorExtended(LOG_TAG, error.toString());
            return;
        }
        java.io.File target = new java.io.File(path);
        java.io.File temp = new java.io.File(target.getParentFile(), target.getName() + ".new");
        try (java.io.OutputStream out = new java.io.FileOutputStream(temp)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (java.io.IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG,
                "Cannot write \"" + temp.getAbsolutePath() + "\"", e);
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return;
        }
        if (!temp.renameTo(target)) {
            Logger.logError(LOG_TAG, "Cannot move \"" + temp.getAbsolutePath() + "\" onto \""
                + path + "\"");
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    @VisibleForTesting
    static String toPropertiesText(@NonNull Properties props) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Generated by Termux. Do not edit.\n");
        ArrayList<String> keys = sortedKeys(props);
        for (String key : keys) {
            builder.append(key).append('=').append(props.getProperty(key)).append('\n');
        }
        return builder.toString();
    }

    /**
     * The shell half of the export.
     *
     * <p>Keys are upper-cased against {@link Locale#ROOT}, never the device's. A Turkish locale maps
     * {@code i} to a dotted capital I, so {@code primary} came out as {@code PRİMARY} — not a shell
     * identifier at all, and the whole file stopped sourcing for that user.
     */
    @VisibleForTesting
    static String toShellExports(@NonNull Properties props) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Generated by Termux. Source this file from shell scripts.\n");
        ArrayList<String> keys = sortedKeys(props);
        for (String key : keys) {
            builder.append("export TERMUX_MATERIAL_")
                .append(key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'))
                .append("='")
                .append(props.getProperty(key))
                .append("'\n");
        }
        return builder.toString();
    }

    @NonNull
    private static ArrayList<String> sortedKeys(@NonNull Properties props) {
        ArrayList<String> keys = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            keys.add((String) entry.getKey());
        }
        Collections.sort(keys);
        return keys;
    }

    private static String hex(@ColorInt int color) {
        return String.format("#%06X", color & 0x00FFFFFF);
    }

    private static int perceivedBrightness(@ColorInt int color) {
        return (int) Math.floor(Math.sqrt(
            Math.pow(Color.red(color), 2) * 0.241
                + Math.pow(Color.green(color), 2) * 0.691
                + Math.pow(Color.blue(color), 2) * 0.068
        ));
    }
}
