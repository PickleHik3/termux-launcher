package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.color.utilities.Hct;
import com.termux.R;
import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

public final class MaterialTerminalColorScheme {

    private static final String LOG_TAG = "MaterialTerminalColorScheme";
    private static final String MATERIAL_COLORS_PROPERTIES_PATH = TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/material-colors.properties";
    private static final String MATERIAL_COLORS_SHELL_PATH = TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/material-colors.sh";

    private MaterialTerminalColorScheme() {}

    /** Build a palette for an explicit level; public so ratio and signature tests are deterministic. */
    @NonNull
    public static Properties create(@NonNull Context context, @NonNull TerminalContrastLevel level) {
        Properties props = new Properties();

        int background = materialColor(context, com.google.android.material.R.attr.colorSurface,
            R.color.termux_surface_base);
        int foreground = materialColor(context, com.google.android.material.R.attr.colorOnSurface,
            R.color.termux_on_surface);
        int primary = materialColor(context, com.google.android.material.R.attr.colorPrimary,
            R.color.termux_primary);
        int secondary = materialColor(context, com.google.android.material.R.attr.colorSecondary,
            R.color.termux_secondary);
        int tertiary = materialColor(context, com.google.android.material.R.attr.colorTertiary,
            R.color.termux_primary);
        int error = materialColor(context, com.google.android.material.R.attr.colorError,
            R.color.termux_error);
        int errorContainer = materialColor(context, com.google.android.material.R.attr.colorErrorContainer,
            R.color.termux_error_container);
        int neutral = materialColor(context, com.google.android.material.R.attr.colorSurfaceVariant,
            R.color.termux_surface_panel);
        int subtleText = materialColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant,
            R.color.termux_on_surface_variant);

        boolean dark = perceivedBrightness(background) < 128;
        background = surfaceTone(background, level);

        foreground = contrastTone(foreground, background, level.foregroundRatio);
        primary = contrastTone(primary, background, level.cursorRatio);

        props.setProperty("contrast_level", level.value);
        props.setProperty("background", hex(background));
        props.setProperty("foreground", hex(foreground));
        props.setProperty("cursor", hex(primary));

        props.setProperty("color0", hex(dark ? darken(neutral, 0.72f) : darken(subtleText, 0.38f)));
        props.setProperty("color1", hex(tintToward(error, errorContainer, dark ? 0.12f : 0.18f)));
        props.setProperty("color2", hex(materialAnsi("#5CF19E", "#00753B", secondary, dark)));
        props.setProperty("color3", hex(materialAnsi("#FFD740", "#855000", tertiary, dark)));
        props.setProperty("color4", hex(materialAnsi("#40C4FF", "#005FA8", primary, dark)));
        props.setProperty("color5", hex(materialAnsi("#FF4081", "#9C2764", primary, dark)));
        props.setProperty("color6", hex(materialAnsi("#64FCDA", "#00746C", secondary, dark)));
        props.setProperty("color7", hex(dark ? lighten(neutral, 0.72f) : darken(neutral, 0.54f)));

        props.setProperty("color8", hex(dark ? lighten(neutral, 0.34f) : darken(subtleText, 0.18f)));
        props.setProperty("color9", hex(dark ? lighten(error, 0.22f) : lighten(error, 0.16f)));
        props.setProperty("color10", hex(materialAnsi("#B9F6CA", "#00844A", secondary, dark)));
        props.setProperty("color11", hex(materialAnsi("#FFE57F", "#956000", tertiary, dark)));
        props.setProperty("color12", hex(materialAnsi("#80D8FF", "#006DAF", primary, dark)));
        props.setProperty("color13", hex(materialAnsi("#FF80AB", "#AD3774", primary, dark)));
        props.setProperty("color14", hex(materialAnsi("#A7FDEB", "#008078", secondary, dark)));
        props.setProperty("color15", hex(foreground));

        for (int i = 0; i < 16; i++) {
            String key = "color" + i;
            int value = Color.parseColor(props.getProperty(key));
            props.setProperty(key, hex(contrastTone(value, background, level.ansiRatio)));
        }

        return props;
    }

    /**
     * Role properties around an already-built terminal palette.
     *
     * <p>The palette is passed in rather than rebuilt so the exported files describe exactly the colours
     * the terminal was given, and so the caller pays for one HCT search instead of two.
     */
    @NonNull
    public static Properties createMaterialRoleProperties(@NonNull Context context,
                                                          @NonNull Properties terminalProps) {
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

        props.setProperty("contrast_level", terminalProps.getProperty("contrast_level",
            TerminalContrastLevel.DEFAULT.value));
        for (String key : terminalProps.stringPropertyNames()) {
            props.setProperty("terminal_" + key, terminalProps.getProperty(key));
        }

        return props;
    }

    /**
     * Write the exported palette files. Takes the finished properties rather than a {@link Context}
     * because this runs on a writer thread: resolving theme attributes and reading resources off the
     * main thread is not safe, so all of that has to have happened before the hand-off.
     */
    public static void writeMaterialColorFiles(@NonNull Properties props) {
        writeFile(MATERIAL_COLORS_PROPERTIES_PATH, toPropertiesText(props));
        writeFile(MATERIAL_COLORS_SHELL_PATH, toShellExports(props));
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
    private static int surfaceTone(@ColorInt int color, @NonNull TerminalContrastLevel level) {
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

    private static void writeFile(@NonNull String path, @NonNull String content) {
        if (alreadyOnDisk(path, content)) return;
        Error error = FileUtils.writeTextToFile(path, path, StandardCharsets.UTF_8, content, false);
        if (error != null) {
            Logger.logErrorExtended(LOG_TAG, error.toString());
        }
    }

    private static String toPropertiesText(@NonNull Properties props) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Generated by Termux. Do not edit.\n");
        ArrayList<String> keys = sortedKeys(props);
        for (String key : keys) {
            builder.append(key).append('=').append(props.getProperty(key)).append('\n');
        }
        return builder.toString();
    }

    private static String toShellExports(@NonNull Properties props) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Generated by Termux. Source this file from shell scripts.\n");
        ArrayList<String> keys = sortedKeys(props);
        for (String key : keys) {
            builder.append("export TERMUX_MATERIAL_")
                .append(key.toUpperCase().replace('.', '_').replace('-', '_'))
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

    @ColorInt
    private static int materialAnsi(String darkBaseHex, String lightBaseHex, @ColorInt int materialColor, boolean dark) {
        int semanticBase = Color.parseColor(dark ? darkBaseHex : lightBaseHex);
        // Light palettes need the ANSI hue to remain distinct. A stronger
        // Material blend makes greens, blues and cyans converge into gray.
        return tintToward(semanticBase, materialColor, dark ? 0.42f : 0.18f);
    }

    @ColorInt
    private static int tintToward(@ColorInt int base, @ColorInt int target, float amount) {
        float[] baseHsv = new float[3];
        float[] targetHsv = new float[3];
        Color.colorToHSV(base, baseHsv);
        Color.colorToHSV(target, targetHsv);
        baseHsv[1] = Math.max(0f, Math.min(1f, baseHsv[1] * (1f - amount) + targetHsv[1] * amount));
        baseHsv[2] = Math.max(0f, Math.min(1f, baseHsv[2] * (1f - amount) + targetHsv[2] * amount));
        return blend(Color.HSVToColor(baseHsv), target, amount * 0.45f);
    }

    @ColorInt
    private static int lighten(@ColorInt int color, float amount) {
        return blend(color, Color.WHITE, amount);
    }

    @ColorInt
    private static int darken(@ColorInt int color, float amount) {
        return blend(color, Color.BLACK, amount);
    }

    @ColorInt
    private static int blend(@ColorInt int from, @ColorInt int to, float amount) {
        float clamped = Math.max(0f, Math.min(1f, amount));
        int red = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * clamped);
        int green = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * clamped);
        int blue = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * clamped);
        return Color.rgb(red, green, blue);
    }

    private static int perceivedBrightness(@ColorInt int color) {
        return (int) Math.floor(Math.sqrt(
            Math.pow(Color.red(color), 2) * 0.241
                + Math.pow(Color.green(color), 2) * 0.691
                + Math.pow(Color.blue(color), 2) * 0.068
        ));
    }
}
