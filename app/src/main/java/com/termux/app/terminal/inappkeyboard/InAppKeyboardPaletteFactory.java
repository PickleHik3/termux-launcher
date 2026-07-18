package com.termux.app.terminal.inappkeyboard;

import android.content.Context;
import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

import juloo.keyboard2.Theme;

/** Builds the embedded keyboard palette from the host's active Material roles. */
public final class InAppKeyboardPaletteFactory {

    private static final double MIN_TEXT_CONTRAST = 4.5d;

    private InAppKeyboardPaletteFactory() {}

    /**
     * Builds a palette for the stored preference values {@code system}, {@code light},
     * {@code dark}, {@code black}, or one of the fixed design themes ({@code steel_teal},
     * {@code mint_fuji}, {@code neon_nightfall}, {@code sakura_wood}, {@code ink_plum}).
     * Unknown values safely use {@code system}. The legacy {@code dock} value maps to the
     * glass rendition of the system theme.
     */
    @NonNull
    public static Theme.Palette create(@NonNull Context context, String variant) {
        String normalizedVariant = variant == null ? "system" : variant;
        if ("dock".equals(normalizedVariant))
            return createGlass(context, "system");
        DesignTheme fixed = designTheme(normalizedVariant);
        if (fixed != null)
            return createFixed(context, fixed);
        SourceRoles roles = resolve(context);

        int keyboard = roles.surface;
        int key = roles.surfaceContainerHigh;
        int action = roles.secondaryContainer;
        int space = roles.surfaceContainerHighest;
        int activated = roles.primaryContainer;
        int label = roles.onSurface;
        int subLabel = roles.onSurfaceVariant;
        int border = roles.outlineVariant;

        switch (normalizedVariant) {
            case "light":
                keyboard = towardLuminance(keyboard, 0.92d);
                key = towardLuminance(key, 0.82d);
                action = towardLuminance(action, 0.78d);
                space = towardLuminance(space, 0.74d);
                activated = towardLuminance(activated, 0.70d);
                label = towardLuminance(label, 0.05d);
                subLabel = towardLuminance(subLabel, 0.12d);
                border = ColorUtils.setAlphaComponent(opaque(border), 209);
                break;
            case "dark":
                keyboard = towardLuminance(keyboard, 0.025d);
                key = towardLuminance(key, 0.055d);
                action = towardLuminance(action, 0.10d);
                space = towardLuminance(space, 0.11d);
                activated = towardLuminance(activated, 0.14d);
                label = towardLuminance(label, 0.90d);
                subLabel = towardLuminance(subLabel, 0.62d);
                border = ColorUtils.setAlphaComponent(opaque(border), 184);
                break;
            case "black":
                keyboard = Color.BLACK;
                key = Color.BLACK;
                action = Color.BLACK;
                space = Color.BLACK;
                activated = ColorUtils.compositeColors(
                    ColorUtils.setAlphaComponent(roles.primary, 64), Color.BLACK);
                border = ColorUtils.setAlphaComponent(opaque(border), 92);
                break;
            case "system":
            default:
                break;
        }

        key = opaque(key);
        action = opaque(action);
        space = opaque(space);
        activated = opaque(activated);
        label = ensureContrast(label, key);
        subLabel = ensureContrast(subLabel, key);
        // Preserve the Material action role whenever it is readable. If its dynamic hue lands too
        // close to either label, fall back to the already contrast-checked normal key surface.
        if (!hasMinimumContrast(label, action) || !hasMinimumContrast(subLabel, action))
            action = key;
        int activatedLabel = ensureContrast(roles.primary, activated);
        int pressedLabel = ensureContrast(roles.primary, activated);
        int lockedLabel = ensureContrast(roles.secondary, activated);

        float density = context.getResources().getDisplayMetrics().density;
        return new Theme.Palette(
            opaque(keyboard), key, action, space, activated,
            label, subLabel, activatedLabel, pressedLabel, lockedLabel,
            border, true, 1f * density, 6f * density, 1f
        );
    }

    /** Hash of the currently resolved source roles used to decide whether to rebuild. */
    public static int signature(@NonNull Context context) {
        return resolve(context).signature();
    }

    /** Material roles used as the color editor's initial, wallpaper-aware swatches. */
    @NonNull
    public static int[] defaultEditorSwatches(@NonNull Context context) {
        SourceRoles roles = resolve(context);
        int tertiary = materialColor(context, com.google.android.material.R.attr.colorTertiary,
            ColorUtils.blendARGB(roles.primary, roles.secondary, 0.5f));
        int error = materialColor(context, com.google.android.material.R.attr.colorError,
            0xFFBA1A1A);
        return new int[] {
            // Keep the original six entries first so persisted per-key assignments migrate
            // without changing appearance. Advanced mode exposes the complete Base16-sized set.
            opaque(roles.surfaceContainerHigh),
            opaque(roles.primary),
            opaque(roles.secondary),
            opaque(roles.onSurface),
            opaque(roles.onSurfaceVariant),
            opaque(roles.secondaryContainer),
            opaque(roles.surface),
            opaque(roles.surfaceContainerHighest),
            opaque(error),
            opaque(tertiary),
            opaque(roles.primaryContainer),
            opaque(materialColor(context, com.google.android.material.R.attr.colorOnPrimary,
                roles.surface)),
            opaque(materialColor(context, com.google.android.material.R.attr.colorOnSecondary,
                roles.surface)),
            opaque(materialColor(context, com.google.android.material.R.attr.colorOnTertiary,
                roles.surface)),
            opaque(roles.outlineVariant),
            opaque(materialColor(context, com.google.android.material.R.attr.colorErrorContainer,
                ColorUtils.blendARGB(roles.surface, error, 0.20f)))
        };
    }

    /**
     * Glass base color of the launcher dock — must stay in sync with
     * {@code TermuxActivity#resolveAccessoryGlassBaseColor()}.
     */
    @ColorInt
    public static int resolveDockGlassBaseColor(@NonNull Context context) {
        if (isNightMode(context)) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                int colorResId = context.getResources()
                    .getIdentifier("system_neutral1_900", "color", "android");
                if (colorResId != 0)
                    return ContextCompat.getColor(context, colorResId);
            }
            return 0xFF1C1B1F;
        }
        return MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
            ContextCompat.getColor(context, R.color.termux_surface_panel_high));
    }

    private static boolean isNightMode(@NonNull Context context) {
        return (context.getResources().getConfiguration().uiMode
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Glass rendition of any theme, used when the dock-match mode enables the glass surface.
     * The keyboard background stays fully transparent — the activity renders the same
     * blurred-wallpaper + tinted-glass stack the dock uses behind the keys — while keys become
     * translucent chips of the theme's own surface colors. A vertical light-from-above gradient
     * and a hairline rim give the caps physical depth; every legend is contrast-checked against
     * its chip composited over the glass base color.
     */
    @NonNull
    public static Theme.Palette createGlass(@NonNull Context context, String variant) {
        String normalizedVariant = variant == null || "dock".equals(variant)
            ? "system" : variant;
        Theme.Palette base = create(context, normalizedVariant);
        boolean night = isNightMode(context);
        int glassBase = resolveDockGlassBaseColor(context);

        int key = ColorUtils.setAlphaComponent(base.keyBackground, night ? 128 : 165);
        int action = ColorUtils.setAlphaComponent(base.actionKeyBackground, night ? 110 : 140);
        int space = ColorUtils.setAlphaComponent(base.spaceBarBackground, night ? 110 : 140);
        int activated = ColorUtils.setAlphaComponent(base.activatedKeyBackground, 216);

        int keyOnBase = ColorUtils.compositeColors(key, glassBase);
        int actionOnBase = ColorUtils.compositeColors(action, glassBase);
        int activatedOnBase = ColorUtils.compositeColors(activated, glassBase);

        int label = ensureContrast(base.labelColor, keyOnBase);
        int subLabel = ensureContrast(base.subLabelColor, keyOnBase);
        int actionLabel = ensureContrast(base.actionLabelColor, actionOnBase);
        int actionSubLabel = ensureContrast(base.actionSubLabelColor, actionOnBase);
        int activatedLabel = ensureContrast(base.activatedLabelColor, activatedOnBase);
        int pressedLabel = ensureContrast(base.pressedLabelColor, activatedOnBase);
        int lockedLabel = ensureContrast(base.lockedModifierColor, activatedOnBase);

        // Rim and cap shading follow the chip's effective brightness, not the phone's UI
        // mode, so a light theme under a dark phone still reads as light physical caps.
        boolean lightChips = ColorUtils.calculateLuminance(keyOnBase) > 0.5d;
        int gradientTop = ColorUtils.setAlphaComponent(Color.WHITE, lightChips ? 46 : 26);
        int gradientBottom = ColorUtils.setAlphaComponent(Color.BLACK, lightChips ? 26 : 56);
        int border = lightChips
            ? ColorUtils.setAlphaComponent(Color.BLACK, 48)
            : ColorUtils.setAlphaComponent(Color.WHITE, 64);

        float density = context.getResources().getDisplayMetrics().density;
        return new Theme.Palette(
            Color.TRANSPARENT, key, action, space, activated,
            label, subLabel, activatedLabel, pressedLabel, lockedLabel,
            border, true, 1f * density, 6f * density, 1f,
            0.25f, 0.5f, actionLabel, actionSubLabel, null,
            gradientTop, gradientBottom
        );
    }

    /**
     * Fixed color-token themes from the KeyboardThemes design handoff. Tokens map onto the
     * keyboard roles as: case = board, alpha = normal keys, mod = action keys + space bar,
     * accent = activated/pressed key highlight, accent2 = locked-modifier legend,
     * Decorative underglow strips are intentionally omitted from every scheme.
     */
    private static DesignTheme designTheme(@NonNull String variant) {
        switch (variant) {
            case "steel_teal":
                return new DesignTheme(0xFFE9E7E2, 0xFFF4F2EC, 0xFF48484A,
                    0xFF727A80, 0xFFF3F4F2, 0xFF1C7A71, 0xFFFFFFFF, 0xFF2B3034);
            case "mint_fuji":
                return new DesignTheme(0xFF141414, 0xFFF6F3EA, 0xFF2C2C2C,
                    0xFFA4DCC4, 0xFF22463A, 0xFF90CDEE, 0xFF1D3F57, 0xFFB8EAD4);
            case "neon_nightfall":
                return new DesignTheme(0xFF0D0D10, 0xFF17171B, 0xFFF26AA6,
                    0xFF141418, 0xFFC76BFF, 0xFFFF2D78, 0xFF12010A, 0xFF7C4DFF);
            case "sakura_wood":
                return new DesignTheme(0xFF5F4331, 0xFFE8DABF, 0xFF5C4832,
                    0xFF8A5A3C, 0xFFF0E6D2, 0xFFB1502E, 0xFFF7EFE2, 0xFF7D8A5F);
            case "ink_plum":
                return new DesignTheme(0xFFC7C7C7, 0xFFFBFBFB, 0xFF1A1A1A,
                    0xFFEDEDED, 0xFF1A1A1A, 0xFFC0241D, 0xFFFFFFFF, 0xFF2F2F2F);
            default:
                return null;
        }
    }

    @NonNull
    private static Theme.Palette createFixed(@NonNull Context context, @NonNull DesignTheme t) {
        int label = ensureContrast(t.alphaText, t.alpha);
        int subLabel = ensureContrast(
            ColorUtils.blendARGB(t.alphaText, t.alpha, 0.25f), t.alpha);
        int actionLabel = ensureContrast(t.modText, t.mod);
        int actionSubLabel = ensureContrast(
            ColorUtils.blendARGB(t.modText, t.mod, 0.25f), t.mod);
        int activatedLabel = ensureContrast(t.accentText, t.accent);
        int lockedLabel = ensureContrast(t.accent2, t.accent);
        int border = ColorUtils.setAlphaComponent(
            ColorUtils.blendARGB(t.alpha, t.alphaText, 0.4f), 150);

        float density = context.getResources().getDisplayMetrics().density;
        return new Theme.Palette(
            t.caseColor, t.alpha, t.mod, t.mod, t.accent,
            label, subLabel, activatedLabel, activatedLabel, lockedLabel,
            border, true, 1f * density, 6f * density, 1f,
            0.25f, 0.5f, actionLabel, actionSubLabel, null
        );
    }

    private static SourceRoles resolve(@NonNull Context context) {
        int surface = materialColor(context, com.google.android.material.R.attr.colorSurface,
            ContextCompat.getColor(context, R.color.termux_surface_base));
        int onSurface = materialColor(context, com.google.android.material.R.attr.colorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        int surfaceVariant = materialColor(context,
            com.google.android.material.R.attr.colorSurfaceVariant,
            ColorUtils.blendARGB(surface, onSurface, 0.08f));
        int primary = materialColor(context, com.google.android.material.R.attr.colorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        int secondary = materialColor(context, com.google.android.material.R.attr.colorSecondary,
            primary);

        return new SourceRoles(
            surface,
            materialColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh,
                ColorUtils.blendARGB(surfaceVariant, onSurface, 0.04f)),
            materialColor(context, com.google.android.material.R.attr.colorSecondaryContainer,
                ColorUtils.blendARGB(surface, secondary, 0.18f)),
            materialColor(context, com.google.android.material.R.attr.colorSurfaceContainerHighest,
                ColorUtils.blendARGB(surfaceVariant, onSurface, 0.08f)),
            materialColor(context, com.google.android.material.R.attr.colorPrimaryContainer,
                ColorUtils.blendARGB(surface, primary, 0.20f)),
            onSurface,
            materialColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant,
                ColorUtils.blendARGB(onSurface, surface, 0.28f)),
            primary,
            secondary,
            materialColor(context, com.google.android.material.R.attr.colorOutlineVariant,
                ColorUtils.blendARGB(surface, onSurface, 0.24f))
        );
    }

    @ColorInt
    private static int materialColor(@NonNull Context context, int attr, @ColorInt int fallback) {
        return MaterialColors.getColor(context, attr, fallback);
    }

    /** Moves a color toward white or black only as far as needed to reach a luminance. */
    @ColorInt
    private static int towardLuminance(@ColorInt int color, double target) {
        color = opaque(color);
        double current = ColorUtils.calculateLuminance(color);
        if (Math.abs(current - target) < 0.0001d) return color;

        int endpoint = current < target ? Color.WHITE : Color.BLACK;
        int best = color;
        float low = 0f;
        float high = 1f;
        for (int i = 0; i < 20; i++) {
            float amount = (low + high) / 2f;
            int candidate = ColorUtils.blendARGB(color, endpoint, amount);
            double luminance = ColorUtils.calculateLuminance(candidate);
            best = candidate;
            if ((current < target && luminance < target)
                || (current > target && luminance > target)) {
                low = amount;
            } else {
                high = amount;
            }
        }
        return opaque(best);
    }

    @ColorInt
    private static int ensureContrast(@ColorInt int foreground, @ColorInt int background) {
        foreground = opaque(foreground);
        background = opaque(background);
        if (ColorUtils.calculateContrast(foreground, background) >= MIN_TEXT_CONTRAST)
            return foreground;

        int endpoint = ColorUtils.calculateContrast(Color.WHITE, background)
            >= ColorUtils.calculateContrast(Color.BLACK, background) ? Color.WHITE : Color.BLACK;
        int best = endpoint;
        float low = 0f;
        float high = 1f;
        for (int i = 0; i < 20; i++) {
            float amount = (low + high) / 2f;
            int candidate = ColorUtils.blendARGB(foreground, endpoint, amount);
            if (ColorUtils.calculateContrast(candidate, background) >= MIN_TEXT_CONTRAST) {
                best = candidate;
                high = amount;
            } else {
                low = amount;
            }
        }
        return opaque(best);
    }

    private static boolean hasMinimumContrast(@ColorInt int foreground,
                                              @ColorInt int background) {
        return ColorUtils.calculateContrast(opaque(foreground), opaque(background))
            >= MIN_TEXT_CONTRAST;
    }

    @ColorInt
    private static int opaque(@ColorInt int color) {
        return ColorUtils.setAlphaComponent(color, 255);
    }

    static int sourceRoleSignature(int... colors) {
        int result = 17;
        for (int color : colors) result = 31 * result + color;
        return result;
    }

    private static final class DesignTheme {
        final int caseColor;
        final int alpha;
        final int alphaText;
        final int mod;
        final int modText;
        final int accent;
        final int accentText;
        final int accent2;
        DesignTheme(int caseColor, int alpha, int alphaText, int mod, int modText,
                    int accent, int accentText, int accent2) {
            this.caseColor = caseColor;
            this.alpha = alpha;
            this.alphaText = alphaText;
            this.mod = mod;
            this.modText = modText;
            this.accent = accent;
            this.accentText = accentText;
            this.accent2 = accent2;
        }
    }

    private static final class SourceRoles {
        final int surface;
        final int surfaceContainerHigh;
        final int secondaryContainer;
        final int surfaceContainerHighest;
        final int primaryContainer;
        final int onSurface;
        final int onSurfaceVariant;
        final int primary;
        final int secondary;
        final int outlineVariant;

        SourceRoles(int surface, int surfaceContainerHigh, int secondaryContainer,
                    int surfaceContainerHighest, int primaryContainer, int onSurface,
                    int onSurfaceVariant, int primary, int secondary, int outlineVariant) {
            this.surface = surface;
            this.surfaceContainerHigh = surfaceContainerHigh;
            this.secondaryContainer = secondaryContainer;
            this.surfaceContainerHighest = surfaceContainerHighest;
            this.primaryContainer = primaryContainer;
            this.onSurface = onSurface;
            this.onSurfaceVariant = onSurfaceVariant;
            this.primary = primary;
            this.secondary = secondary;
            this.outlineVariant = outlineVariant;
        }

        int signature() {
            return sourceRoleSignature(surface, surfaceContainerHigh, secondaryContainer,
                surfaceContainerHighest, primaryContainer, onSurface, onSurfaceVariant,
                primary, secondary, outlineVariant);
        }
    }
}
