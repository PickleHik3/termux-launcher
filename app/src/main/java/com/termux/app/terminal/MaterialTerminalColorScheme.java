package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.shared.errors.Error;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

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

    @NonNull
    public static Properties create(@NonNull Context context) {
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

        return props;
    }

    @NonNull
    public static Properties createMaterialRoleProperties(@NonNull Context context) {
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
        putMaterialColor(props, "error", context, com.google.android.material.R.attr.colorError,
            R.color.termux_error);
        putMaterialColor(props, "error_container", context, com.google.android.material.R.attr.colorErrorContainer,
            R.color.termux_error_container);
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

        Properties terminalProps = create(context);
        for (String key : terminalProps.stringPropertyNames()) {
            props.setProperty("terminal_" + key, terminalProps.getProperty(key));
        }

        return props;
    }

    public static void writeMaterialColorFiles(@NonNull Context context) {
        Properties props = createMaterialRoleProperties(context);
        writeFile(MATERIAL_COLORS_PROPERTIES_PATH, toPropertiesText(props));
        writeFile(MATERIAL_COLORS_SHELL_PATH, toShellExports(props));
    }

    public static int signature(@NonNull Context context) {
        int result = 17;
        result = 31 * result + materialColor(context, com.google.android.material.R.attr.colorSurface,
            R.color.termux_surface_base);
        result = 31 * result + materialColor(context, com.google.android.material.R.attr.colorOnSurface,
            R.color.termux_on_surface);
        result = 31 * result + materialColor(context, com.google.android.material.R.attr.colorPrimary,
            R.color.termux_primary);
        result = 31 * result + materialColor(context, com.google.android.material.R.attr.colorSecondary,
            R.color.termux_secondary);
        result = 31 * result + materialColor(context, com.google.android.material.R.attr.colorTertiary,
            R.color.termux_primary);
        result = 31 * result + materialColor(context, com.google.android.material.R.attr.colorError,
            R.color.termux_error);
        return result;
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
