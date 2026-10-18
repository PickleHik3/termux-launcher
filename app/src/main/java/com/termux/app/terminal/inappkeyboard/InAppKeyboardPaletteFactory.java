package com.termux.app.terminal.inappkeyboard;

import android.content.Context;
import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.chrome.GlassLook;

import juloo.keyboard2.Theme;

/** Builds the embedded keyboard palette from the host's active Material roles. */
public final class InAppKeyboardPaletteFactory {

    private static final double MIN_TEXT_CONTRAST = 4.5d;

    /**
     * The palette's {@code secondaryDimming}: the keyboard draws every key flagged secondary (Ctrl,
     * Alt, Fn, Shift, the arrows, Esc, Tab, Backspace, Enter, 123+) in its legend colour moved this
     * far toward mid grey ({@link #secondaryOf}), not in the colour itself.
     */
    private static final float SECONDARY_DIMMING = 0.25f;

    // How far each part of a keyboard on Obsidian glass sits from the capsule, toward its ink.
    private static final float KEY_LIFT = 0.16f;
    private static final float FUNCTION_LIFT = 0.08f;
    private static final float LABEL_LIFT = 0.90f;
    private static final float SUB_LABEL_LIFT = 0.68f;

    private InAppKeyboardPaletteFactory() {}

    /**
     * Builds a palette for the stored preference values {@code system}, {@code light}, or
     * {@code dark}. Unknown values safely use {@code system}. The legacy {@code dock} value maps
     * to the glass rendition of the system theme.
     */
    @NonNull
    public static Theme.Palette create(@NonNull Context context, String variant) {
        String normalizedVariant = variant == null ? "system" : variant;
        if ("dock".equals(normalizedVariant))
            return createGlass(context, "system");
        SourceRoles roles = resolve(context);

        // The whitest/most-elevated container tier is named oppositely across the two M3
        // schemes: dark elevates by lightening (surfaceContainerHighest is the lightest
        // container), light elevates by darkening (surfaceContainerHighest is the darkest — the
        // lightest, "white-ish" tier there is surfaceContainerLowest). "system" tracks whichever
        // scheme is actually active; "light"/"dark" force the opposite mode by bending these
        // same starting points toward that mode's canonical luminance below.
        boolean night = isNightMode(context);
        int keyboard = night ? roles.surfaceContainer : roles.surfaceContainerLow;
        int key = night ? roles.surfaceContainerHighest : roles.surfaceContainerLowest;
        int function = roles.surfaceContainerHigh;
        int space = key;
        int action = roles.primary;
        int activated = roles.primaryContainer;
        int label = roles.onSurface;
        int subLabel = roles.onSurfaceVariant;
        int border = roles.outlineVariant;

        switch (normalizedVariant) {
            case "light":
                keyboard = towardLuminance(keyboard, 0.93d);
                key = towardLuminance(key, 0.98d);
                function = towardLuminance(function, 0.85d);
                space = towardLuminance(space, 0.98d);
                action = towardLuminance(action, 0.18d);
                activated = towardLuminance(activated, 0.70d);
                label = towardLuminance(label, 0.05d);
                subLabel = towardLuminance(subLabel, 0.12d);
                border = ColorUtils.setAlphaComponent(opaque(border), 209);
                break;
            case "dark":
                keyboard = towardLuminance(keyboard, 0.020d);
                key = towardLuminance(key, 0.055d);
                function = towardLuminance(function, 0.035d);
                space = towardLuminance(space, 0.055d);
                action = towardLuminance(action, 0.60d);
                activated = towardLuminance(activated, 0.14d);
                label = towardLuminance(label, 0.90d);
                subLabel = towardLuminance(subLabel, 0.62d);
                border = ColorUtils.setAlphaComponent(opaque(border), 184);
                break;
            case "system":
            default:
                break;
        }

        key = opaque(key);
        function = opaque(function);
        action = opaque(action);
        space = opaque(space);
        activated = opaque(activated);
        label = ensureContrast(label, key);
        subLabel = ensureContrast(subLabel, key);
        // Function keys read one tone lower than letters (surfaceContainerHigh vs.
        // surfaceContainerHighest/Lowest) but share the same neutral label hue.
        int functionLabel = ensureContrast(subLabel, function);
        // The enter/action key is a filled Material button: primary background, onPrimary
        // label. Unlike the old shared-label action role, ensureContrast alone keeps this
        // readable regardless of the resolved primary's hue, so no key-surface fallback is
        // needed here the way plain labels once needed one.
        int actionLabel = ensureContrast(roles.onPrimary, action);
        int activatedLabel = ensureContrast(roles.onPrimaryContainer, activated);
        // Non-selected labels on a pressed cap sit back so the resolved direction reads as the
        // single dominant glyph. The keyboard's label paint owns the alpha channel outright
        // (labelBrightness), so the set-back is pre-composited in RGB: the bright label sunk
        // halfway toward the pressed chip it draws on. Touch FX reuse this color for hue only.
        int pressedLabel = ColorUtils.blendARGB(activated, activatedLabel, 130f / 255f);
        int lockedLabel = ensureContrast(roles.secondary, activated);

        float density = context.getResources().getDisplayMetrics().density;
        return new Theme.Palette(
            opaque(keyboard), key, action, space, activated,
            label, subLabel, activatedLabel, pressedLabel, lockedLabel,
            border, false, 0f, 10f * density, 1f,
            0.25f, 0.5f, actionLabel, actionLabel, null, 0, 0,
            function, functionLabel
        );
    }

    /** Hash of the currently resolved source roles used to decide whether to rebuild. */
    public static int signature(@NonNull Context context) {
        return resolve(context).signature();
    }

    /**
     * Material roles behind every dynamic swatch of the color editor. Re-resolved on every load
     * and on every {@link InAppKeyboardColorScheme#refreshDynamicSwatches}, so unpinned slots
     * follow the system theme and the wallpaper.
     *
     * <p>Slot → role (base16 index in the editor's advanced mode):
     * <pre>
     * 00 surfaceContainerHigh   01 primary                02 secondary
     * 03 onSurface              04 onSurfaceVariant       05 secondaryContainer
     * 06 surface                07 surfaceContainerHighest
     * 08 error                  09 tertiary               0A primaryContainer
     * 0B onPrimary              0C onSecondary            0D onTertiary
     * 0E outlineVariant         0F errorContainer         10 surface
     * 11 surfaceContainer       12 error+onSurface 20%    13 tertiary+onSurface 20%
     * 14 primary+onSurface 20%  15 secondary+onSurface 20%
     * 16 tertiary+primary 50%   17 primary+secondary 50%
     * </pre>
     */
    @NonNull
    public static int[] defaultEditorSwatches(@NonNull Context context) {
        SourceRoles roles = resolve(context);
        int tertiary = materialColor(context, com.google.android.material.R.attr.colorTertiary,
            ColorUtils.blendARGB(roles.primary, roles.secondary, 0.5f));
        int error = materialColor(context, androidx.appcompat.R.attr.colorError,
            0xFFBA1A1A);
        return new int[] {
            // Keep the original six entries first so persisted per-key assignments migrate
            // without changing appearance. Advanced mode exposes a Base24-sized set so imported
            // Base16, Base24, and Tinted8 schemes can all retain their complete palette.
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
                ColorUtils.blendARGB(roles.surface, error, 0.20f))),
            opaque(roles.surface),
            opaque(materialColor(context,
                com.google.android.material.R.attr.colorSurfaceContainer, roles.surface)),
            opaque(ColorUtils.blendARGB(error, roles.onSurface, 0.20f)),
            opaque(ColorUtils.blendARGB(tertiary, roles.onSurface, 0.20f)),
            opaque(ColorUtils.blendARGB(roles.primary, roles.onSurface, 0.20f)),
            opaque(ColorUtils.blendARGB(roles.secondary, roles.onSurface, 0.20f)),
            opaque(ColorUtils.blendARGB(tertiary, roles.primary, 0.50f)),
            opaque(ColorUtils.blendARGB(roles.primary, roles.secondary, 0.50f))
        };
    }

    /**
     * Glass base color of the launcher dock — must stay in sync with
     * {@code TermuxActivity#resolveAccessoryGlassBaseColor()}.
     */
    @ColorInt
    public static int resolveDockGlassBaseColor(@NonNull Context context) {
        // Same scheme gate as TermuxActivity#resolveAccessoryGlassBaseColor(): the Material You
        // neutral is only the right base while the chrome is on wallpaper colours.
        if (isNightMode(context)
                && !com.termux.app.theme.LauncherSchemeTheme.isSchemeChromeActive(context)) {
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
        return createGlass(context, variant, GlassLook.SCHEME);
    }

    /**
     * {@link #createGlass(Context, String)} on the surface the preset draws. Under a
     * {@code scheme} look nothing changes. Under an Obsidian look the capsule is the look's tint,
     * not the theme's panel colour, so the chips, their legends and the glass they are contrasted
     * against are derived from that same surface colour and the keys read as part of the capsule.
     */
    @NonNull
    public static Theme.Palette createGlass(@NonNull Context context, String variant,
                                            @NonNull GlassLook look) {
        return createGlass(context, variant, look, Color.TRANSPARENT);
    }

    /**
     * {@link #createGlass(Context, String, GlassLook)} with every legend checked against its chip
     * over {@code face}, the glass the keys really stand on as measured (the wallpaper, the dim,
     * the Shade and the tint at its opacity; {@code ChromeInk#bandFace}), rather than over the
     * theme's glass base. A theme legend that already reads there is kept; one that does not moves
     * toward whichever of white or black reads. {@link Color#TRANSPARENT} for not measured yet:
     * the glass base stands in, as before.
     */
    @NonNull
    public static Theme.Palette createGlass(@NonNull Context context, String variant,
                                            @NonNull GlassLook look, @ColorInt int face) {
        return createGlass(context, variant, look,
            Color.alpha(face) == 0 ? new int[0] : new int[] {face});
    }

    /**
     * {@link #createGlass(Context, String, GlassLook, int)} on several faces at once: the glass
     * under each row of the keyboard, top to bottom, as measured ({@code ChromeInk#bandFaces}).
     * Every legend is checked on its chip over every one of them, so a label the top row's bright
     * wallpaper passes is not drawn dark on the near-black wallpaper under the bottom row. Where no
     * tone clears all of them, the side that reads best on the worst face wins and the band's veil
     * (measured on the same rows) is what buys the rest. Empty for not measured yet: the glass
     * base stands in, as before.
     */
    @NonNull
    public static Theme.Palette createGlass(@NonNull Context context, String variant,
                                            @NonNull GlassLook look, @NonNull int[] faces) {
        return createGlass(context, variant, look, faces,
            com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP
                .DEFAULT_IN_APP_KEYBOARD_KEY_OPACITY);
    }

    /**
     * {@link #createGlass(Context, String, GlassLook, int[])} at the user's key opacity
     * ({@code keyOpacityPercent}, 0 to 100; negative for the theme's own). The keyboard draws
     * every cap but a pressed one at that alpha in place of the palette's
     * ({@code juloo.keyboard2.Theme.Computed.Key}), so the legends are chosen on the caps as
     * drawn at it: letters, the space bar, Enter and the function keys alike, each over every
     * row's face. At 0 the caps are clear and the legends are chosen on the glass face itself.
     * The function chip's raise does not apply there, since the view would draw over it.
     */
    @NonNull
    public static Theme.Palette createGlass(@NonNull Context context, String variant,
                                            @NonNull GlassLook look, @NonNull int[] faces,
                                            int keyOpacityPercent) {
        return createGlass(context, variant, look, faces, keyOpacityPercent, TONE_ANY);
    }

    /** {@link #createGlass(Context, String, GlassLook, int[], int, int)}: the tone is the faces'. */
    public static final int TONE_ANY = 0;
    /** The launcher chose dark legends on the bare face ({@code ChromeInk#bandTone}). */
    public static final int TONE_DARK = 1;
    /** The launcher chose pale legends on the bare face ({@code ChromeInk#bandTone}). */
    public static final int TONE_PALE = 2;

    /**
     * {@link #createGlass(Context, String, GlassLook, int[], int)} with the letters and the
     * function legends held to {@code tone}, the side the launcher chose on the bare face
     * ({@code ChromeInk#onFixedInkEitherTone}, which bought the veil, if any, for that side): each
     * the theme's legend where it is on that side and reads, else moved toward that side's
     * endpoint as far as it and its secondary rendition need, else the endpoint. So the legends
     * follow the one decision, hysteresis and all, instead of flipping on the faces near the
     * line. {@link #TONE_ANY} chooses on the faces, as before.
     */
    @NonNull
    public static Theme.Palette createGlass(@NonNull Context context, String variant,
                                            @NonNull GlassLook look, @NonNull int[] faces,
                                            int keyOpacityPercent, int tone) {
        String normalizedVariant = variant == null || "dock".equals(variant)
            ? "system" : variant;
        Theme.Palette base = create(context, normalizedVariant);
        boolean night = isNightMode(context);
        int glassBase = resolveDockGlassBaseColor(context);
        int baseKey = base.keyBackground;
        int baseSpace = base.spaceBarBackground;
        int baseFunction = base.functionKeyBackground;
        int baseLabel = base.labelColor;
        int baseSubLabel = base.subLabelColor;
        int baseFunctionLabel = base.functionLabelColor;
        if (look.obsidianTint) {
            glassBase = surfaceTinted(look, glassBase);
            night = ColorUtils.calculateLuminance(glassBase) < 0.5d;
            baseKey = chipOn(glassBase, KEY_LIFT);
            baseSpace = baseKey;
            baseFunction = chipOn(glassBase, FUNCTION_LIFT);
            baseLabel = chipOn(glassBase, LABEL_LIFT);
            baseSubLabel = chipOn(glassBase, SUB_LABEL_LIFT);
            baseFunctionLabel = baseSubLabel;
        }

        // Glass keeps the Material tiers legible: letters are the most solid chip, function keys
        // let more glass through so they sit one tone lower, and Enter stays a filled accent button.
        int key = ColorUtils.setAlphaComponent(baseKey, night ? 150 : 180);
        int action = ColorUtils.setAlphaComponent(base.actionKeyBackground, night ? 228 : 236);
        int function = ColorUtils.setAlphaComponent(baseFunction, night ? 96 : 124);
        int space = ColorUtils.setAlphaComponent(baseSpace, night ? 150 : 180);
        int activated = ColorUtils.setAlphaComponent(base.activatedKeyBackground, 216);

        // What the chips are laid on: the measured faces where there are some, else the base.
        int[] under = measuredOrBase(faces, glassBase);
        int[] keyOn = chipsOn(key, under);
        int[] actionOn = chipsOn(action, under);
        int[] activatedOn = chipsOn(activated, under);
        // The cap shading and the pressed blend follow the face the chips mostly stand on.
        int keyOnBase = ColorUtils.compositeColors(key, meanOf(under));
        int activatedOnBase = ColorUtils.compositeColors(activated, meanOf(under));

        // Rim and cap shading follow the chip's effective brightness, not the phone's UI
        // mode, so a light theme under a dark phone still reads as light physical caps.
        boolean lightChips = ColorUtils.calculateLuminance(keyOnBase) > 0.5d;
        int gradientTop = ColorUtils.setAlphaComponent(Color.WHITE, lightChips ? 46 : 26);
        int gradientBottom = ColorUtils.setAlphaComponent(Color.BLACK, lightChips ? 26 : 56);

        boolean userKeys = keyOpacityPercent >= 0;
        if (userKeys) {
            // The caps as the view draws them at the user's opacity (activated ones keep theirs).
            int alpha = Math.round(255f * Math.min(100, keyOpacityPercent) / 100f);
            keyOn = drawnChipsOn(ColorUtils.setAlphaComponent(key, alpha), gradientTop,
                gradientBottom, under);
            actionOn = drawnChipsOn(ColorUtils.setAlphaComponent(action, alpha), gradientTop,
                gradientBottom, under);
            function = ColorUtils.setAlphaComponent(function, alpha);
        }
        int label = legibleOnTone(baseLabel, keyOn, tone);
        int subLabel = ensureContrastOnAll(baseSubLabel, keyOn);
        // Enter stands on its own accent button, which the launcher's tone was not judged on:
        // its legend is chosen on that button over the faces, which no longer carry a veil
        // bought for the legends drawn before.
        int actionLabel = legibleWithSecondary(base.actionLabelColor, actionOn);
        int actionSubLabel = ensureContrastOnAll(base.actionSubLabelColor, actionOn);
        // The function legends (Ctrl, Alt, Fn, the gear, Shift, Backspace, the right-hand
        // cluster) are chosen on their chip as the keyboard really draws it over every row's
        // face, and where no tone reads on that, the chip's fill is raised until one does (not
        // at a user key opacity, which the view draws in place of any fill).
        // Nearly every function legend is drawn secondary, so the tone is chosen for that too.
        int[] functionOn = drawnChipsOn(function, gradientTop, gradientBottom, under);
        int functionLabel = legibleOnTone(baseFunctionLabel, functionOn, tone);
        if (!userKeys && drawnContrast(functionLabel, functionOn) < MIN_TEXT_CONTRAST) {
            for (int alpha = Color.alpha(function) + 1; alpha <= 255; alpha++) {
                int raised = ColorUtils.setAlphaComponent(baseFunction, alpha);
                int[] on = drawnChipsOn(raised, gradientTop, gradientBottom, under);
                int ink = legibleOnTone(baseFunctionLabel, on, tone);
                if (alpha == 255 || drawnContrast(ink, on) >= MIN_TEXT_CONTRAST) {
                    function = raised;
                    functionLabel = ink;
                    break;
                }
            }
        }
        int activatedLabel = ensureContrastOnAll(base.activatedLabelColor, activatedOn);
        // Re-sunk against the composited chip; see the non-glass path for why RGB, not alpha.
        int pressedLabel = ColorUtils.blendARGB(activatedOnBase, activatedLabel, 130f / 255f);
        int lockedLabel = ensureContrastOnAll(base.lockedModifierColor, activatedOn);

        int border = lightChips
            ? ColorUtils.setAlphaComponent(Color.BLACK, 48)
            : ColorUtils.setAlphaComponent(Color.WHITE, 64);

        float density = context.getResources().getDisplayMetrics().density;
        return new Theme.Palette(
            Color.TRANSPARENT, key, action, space, activated,
            label, subLabel, activatedLabel, pressedLabel, lockedLabel,
            border, false, 0f, 10f * density, 1f,
            0.25f, 0.5f, actionLabel, actionSubLabel, null,
            gradientTop, gradientBottom, function, functionLabel
        );
    }

    /** The capsule's flat colour under {@code look}: its tint with the wash over it, opaque. */
    @ColorInt
    private static int surfaceTinted(@NonNull GlassLook look, @ColorInt int glassBase) {
        return opaque(look.flatTint(opaque(glassBase)));
    }

    /**
     * A tone of the capsule's own colour: {@code surface} moved {@code amount} toward white on a
     * dark capsule and toward black on a light one, so chips and legends share its hue.
     */
    @ColorInt
    private static int chipOn(@ColorInt int surface, float amount) {
        int ink = ColorUtils.calculateLuminance(surface) < 0.5d ? Color.WHITE : Color.BLACK;
        return ColorUtils.blendARGB(surface, ink, amount);
    }

    private static SourceRoles resolve(@NonNull Context context) {
        int surface = materialColor(context, com.google.android.material.R.attr.colorSurface,
            ContextCompat.getColor(context, R.color.termux_surface_base));
        int onSurface = materialColor(context, com.google.android.material.R.attr.colorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        int surfaceVariant = materialColor(context,
            com.google.android.material.R.attr.colorSurfaceVariant,
            ColorUtils.blendARGB(surface, onSurface, 0.08f));
        int primary = materialColor(context, androidx.appcompat.R.attr.colorPrimary,
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
                ColorUtils.blendARGB(surface, onSurface, 0.24f)),
            materialColor(context, com.google.android.material.R.attr.colorSurfaceContainer,
                ColorUtils.blendARGB(surfaceVariant, onSurface, 0.02f)),
            materialColor(context, com.google.android.material.R.attr.colorSurfaceContainerLow,
                ColorUtils.blendARGB(surface, onSurface, 0.02f)),
            materialColor(context, com.google.android.material.R.attr.colorSurfaceContainerLowest,
                surface),
            materialColor(context, com.google.android.material.R.attr.colorOnPrimary,
                ContextCompat.getColor(context, android.R.color.white)),
            materialColor(context, com.google.android.material.R.attr.colorOnPrimaryContainer,
                primary)
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

    /** The opaque faces, or the glass base alone where none was measured. */
    @NonNull
    private static int[] measuredOrBase(@NonNull int[] faces, @ColorInt int glassBase) {
        int count = 0;
        for (int face : faces) if (Color.alpha(face) != 0) count++;
        if (count == 0) return new int[] {opaque(glassBase)};
        int[] out = new int[count];
        int at = 0;
        for (int face : faces) if (Color.alpha(face) != 0) out[at++] = opaque(face);
        return out;
    }

    /** {@code chip} composited over each of {@code faces}, opaque. */
    @NonNull
    private static int[] chipsOn(@ColorInt int chip, @NonNull int[] faces) {
        int[] out = new int[faces.length];
        for (int i = 0; i < faces.length; i++)
            out[i] = opaque(ColorUtils.compositeColors(chip, faces[i]));
        return out;
    }

    /**
     * A key cap as the keyboard draws it ({@code juloo.keyboard2.Theme.Computed.Key}): the fill
     * becomes a vertical gradient from {@code gradientTop} over it to {@code gradientBottom} over
     * it, and that gradient is painted with the fill's own colour on the paint, whose alpha the
     * canvas multiplies into the shader. So a glass chip reaches the screen at roughly its alpha
     * squared: a light theme's function chip of 124/255 shows about 70/255 of its colour, which
     * is why its legends, chosen on the fill alone, were drawn dark on a near-black chip over dark
     * wallpaper. Returns the two ends of that gradient (the darkest and palest the cap gets), or
     * the fill alone where the palette draws no gradient.
     */
    @NonNull
    public static int[] drawnCapStops(@ColorInt int fill, @ColorInt int gradientTop,
                                      @ColorInt int gradientBottom) {
        if ((gradientTop == 0 && gradientBottom == 0) || Color.alpha(fill) == 0)
            return new int[] {fill};
        return new int[] {
            drawnStop(compositeOver(gradientTop, fill), Color.alpha(fill)),
            drawnStop(compositeOver(gradientBottom, fill), Color.alpha(fill))
        };
    }

    /** One gradient stop with the paint's alpha multiplied into it. */
    @ColorInt
    private static int drawnStop(@ColorInt int stop, int paintAlpha) {
        return ColorUtils.setAlphaComponent(stop,
            Math.round(Color.alpha(stop) * paintAlpha / 255f));
    }

    /** {@code Theme.Computed.Key#compositeOver}: source-over keeping {@code base}'s translucency. */
    @ColorInt
    private static int compositeOver(@ColorInt int overlay, @ColorInt int base) {
        if (overlay == 0) return base;
        float oa = Color.alpha(overlay) / 255f;
        float ba = Color.alpha(base) / 255f;
        float outA = oa + ba * (1f - oa);
        if (outA <= 0f) return Color.TRANSPARENT;
        int r = Math.round((Color.red(overlay) * oa + Color.red(base) * ba * (1f - oa)) / outA);
        int g = Math.round((Color.green(overlay) * oa + Color.green(base) * ba * (1f - oa)) / outA);
        int b = Math.round((Color.blue(overlay) * oa + Color.blue(base) * ba * (1f - oa)) / outA);
        return Color.argb(Math.round(outA * 255f), r, g, b);
    }

    /** Each end of the drawn cap ({@link #drawnCapStops}) composited over each of {@code faces}. */
    @NonNull
    private static int[] drawnChipsOn(@ColorInt int fill, @ColorInt int gradientTop,
                                      @ColorInt int gradientBottom, @NonNull int[] faces) {
        int[] stops = drawnCapStops(fill, gradientTop, gradientBottom);
        int[] out = new int[stops.length * faces.length];
        int at = 0;
        for (int stop : stops)
            for (int face : faces)
                out[at++] = opaque(ColorUtils.compositeColors(stop, face));
        return out;
    }

    /** The plain sRGB mean of {@code colors}, opaque. */
    @ColorInt
    private static int meanOf(@NonNull int[] colors) {
        long red = 0, green = 0, blue = 0;
        for (int color : colors) {
            red += Color.red(color);
            green += Color.green(color);
            blue += Color.blue(color);
        }
        int n = Math.max(1, colors.length);
        return Color.rgb((int) (red / n), (int) (green / n), (int) (blue / n));
    }

    /**
     * {@code legend} as the keyboard draws it on a key flagged secondary
     * ({@code juloo.keyboard2.Theme.adjustLight} at {@link #SECONDARY_DIMMING}): its value moved
     * toward mid grey, {@code v' = d + (1 - 2d) v}, so black becomes a dark grey and white a light
     * one. Never black or white, which is why a legend that only just reads leaves its Ctrl, Alt
     * and arrows unreadable on a mid face.
     */
    @ColorInt
    public static int secondaryOf(@ColorInt int legend) {
        float[] hsv = new float[3];
        Color.colorToHSV(legend, hsv);
        hsv[2] = SECONDARY_DIMMING - (2f * SECONDARY_DIMMING - 1f) * hsv[2];
        return Color.HSVToColor(0xFF, hsv);
    }

    /** The lower of a legend's own contrast and its secondary rendition's, on every background. */
    private static double drawnContrast(@ColorInt int legend, @NonNull int[] backgrounds) {
        return Math.min(minContrast(legend, backgrounds),
            minContrast(secondaryOf(legend), backgrounds));
    }

    /**
     * {@link #ensureContrastOnAll} for a legend the keyboard also draws secondary
     * ({@link #secondaryOf}): that tone where its secondary rendition reads as well; else white or
     * black, whichever leaves the secondary rendition reading best on the worst background,
     * without the legend itself falling under what that tone gave it (or the floor). The veil
     * ({@code ChromeInk#onFixedInk}) is asked for the secondary rendition, so where neither reads it
     * rises.
     */
    @ColorInt
    static int legibleWithSecondary(@ColorInt int foreground, @NonNull int[] backgrounds) {
        int ink = ensureContrastOnAll(foreground, backgrounds);
        if (backgrounds.length == 0
                || minContrast(secondaryOf(ink), backgrounds) >= MIN_TEXT_CONTRAST)
            return ink;
        double floor = Math.min(MIN_TEXT_CONTRAST, minContrast(ink, backgrounds));
        int best = ink;
        double bestSecondary = minContrast(secondaryOf(ink), backgrounds);
        for (int endpoint : new int[] {Color.WHITE, Color.BLACK}) {
            if (minContrast(endpoint, backgrounds) < floor) continue;
            double secondary = minContrast(secondaryOf(endpoint), backgrounds);
            if (secondary > bestSecondary) {
                best = endpoint;
                bestSecondary = secondary;
            }
        }
        return opaque(best);
    }

    /**
     * {@link #legibleWithSecondary} held to {@code tone}'s side ({@link #TONE_ANY}: either). On
     * the side: {@code foreground} where it is on that side and it and its secondary rendition
     * read; else moved toward the side's endpoint only as far as both need; else the endpoint.
     * Off the side, the endpoint.
     */
    @ColorInt
    static int legibleOnTone(@ColorInt int foreground, @NonNull int[] backgrounds, int tone) {
        if (tone != TONE_DARK && tone != TONE_PALE)
            return legibleWithSecondary(foreground, backgrounds);
        int endpoint = tone == TONE_PALE ? Color.WHITE : Color.BLACK;
        foreground = opaque(foreground);
        boolean onSide = (ColorUtils.calculateLuminance(foreground) > 0.5d) == (tone == TONE_PALE);
        if (!onSide) return endpoint;
        if (backgrounds.length == 0
                || drawnContrast(foreground, backgrounds) >= MIN_TEXT_CONTRAST)
            return foreground;
        if (drawnContrast(endpoint, backgrounds) < MIN_TEXT_CONTRAST) return endpoint;
        int best = endpoint;
        float low = 0f;
        float high = 1f;
        for (int i = 0; i < 20; i++) {
            float amount = (low + high) / 2f;
            int candidate = ColorUtils.blendARGB(foreground, endpoint, amount);
            if (drawnContrast(candidate, backgrounds) >= MIN_TEXT_CONTRAST) {
                best = candidate;
                high = amount;
            } else {
                low = amount;
            }
        }
        return opaque(best);
    }

    /** The lowest contrast {@code foreground} has on any of {@code backgrounds}. */
    private static double minContrast(@ColorInt int foreground, @NonNull int[] backgrounds) {
        double worst = Double.MAX_VALUE;
        for (int background : backgrounds)
            worst = Math.min(worst, ColorUtils.calculateContrast(opaque(foreground), background));
        return worst;
    }

    /**
     * {@link #ensureContrast} held on every one of {@code backgrounds} at once (opaque): the
     * label itself where it already reads on all of them; else moved toward white or black only
     * as far as all of them need, on the side that reads best on the worst of them; and where
     * neither endpoint clears them all, that endpoint itself, the most legible tone there is. One
     * background is exactly {@link #ensureContrast}.
     */
    @ColorInt
    public static int ensureContrastOnAll(@ColorInt int foreground, @NonNull int[] backgrounds) {
        foreground = opaque(foreground);
        if (backgrounds.length == 0) return foreground;
        if (backgrounds.length == 1) return ensureContrast(foreground, backgrounds[0]);
        if (minContrast(foreground, backgrounds) >= MIN_TEXT_CONTRAST) return foreground;
        double white = minContrast(Color.WHITE, backgrounds);
        double black = minContrast(Color.BLACK, backgrounds);
        // The endpoint that reads best on the worst background: the only one that can clear all
        // of them if either can, and the most legible tone where neither can.
        int endpoint = white >= black ? Color.WHITE : Color.BLACK;
        if (minContrast(endpoint, backgrounds) < MIN_TEXT_CONTRAST) return endpoint;
        int best = endpoint;
        float low = 0f;
        float high = 1f;
        for (int i = 0; i < 20; i++) {
            float amount = (low + high) / 2f;
            int candidate = ColorUtils.blendARGB(foreground, endpoint, amount);
            if (minContrast(candidate, backgrounds) >= MIN_TEXT_CONTRAST) {
                best = candidate;
                high = amount;
            } else {
                low = amount;
            }
        }
        return opaque(best);
    }

    /**
     * Lifts a label toward white or black only as far as 4.5:1 against its composited
     * background needs. Public so surfaces outside the keyboard — the command palette's
     * glass ledger — hold the same contrast floor as the keycaps do.
     */
    @ColorInt
    public static int ensureContrast(@ColorInt int foreground, @ColorInt int background) {
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

    @ColorInt
    private static int opaque(@ColorInt int color) {
        return ColorUtils.setAlphaComponent(color, 255);
    }

    static int sourceRoleSignature(int... colors) {
        int result = 17;
        for (int color : colors) result = 31 * result + color;
        return result;
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
        final int surfaceContainer;
        final int surfaceContainerLow;
        final int surfaceContainerLowest;
        final int onPrimary;
        final int onPrimaryContainer;

        SourceRoles(int surface, int surfaceContainerHigh, int secondaryContainer,
                    int surfaceContainerHighest, int primaryContainer, int onSurface,
                    int onSurfaceVariant, int primary, int secondary, int outlineVariant,
                    int surfaceContainer, int surfaceContainerLow, int surfaceContainerLowest,
                    int onPrimary, int onPrimaryContainer) {
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
            this.surfaceContainer = surfaceContainer;
            this.surfaceContainerLow = surfaceContainerLow;
            this.surfaceContainerLowest = surfaceContainerLowest;
            this.onPrimary = onPrimary;
            this.onPrimaryContainer = onPrimaryContainer;
        }

        int signature() {
            return sourceRoleSignature(surface, surfaceContainerHigh, secondaryContainer,
                surfaceContainerHighest, primaryContainer, onSurface, onSurfaceVariant,
                primary, secondary, outlineVariant, surfaceContainer, surfaceContainerLow,
                surfaceContainerLowest, onPrimary, onPrimaryContainer);
        }
    }
}
