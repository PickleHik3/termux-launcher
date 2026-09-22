package com.termux.app.notice;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * The material chrome borrows from the terminal it floats over.
 *
 * <p>One dress for everything transient the launcher draws on top of a shell: the same radius the
 * terminal's own corners are wearing, the same fill its panes are painted with, and the hairline
 * every hint that hangs off the terminal already uses. The notice pill used to be a Material
 * surface-container capsule instead — a correct Material component, and visibly a foreign one: it
 * kept its own radius while the corner knob moved the window under it, and it kept Material's
 * elevation over a terminal whose whole hint family is flat.
 *
 * <p>Values are read where they live rather than copied. With a terminal on screen that is a
 * {@link Source} the activity supplies, so the dress follows the surface editor live, including the
 * glass tint. On a screen with no terminal — Settings, the keyboard colour scheme — the same
 * numbers come from stored preferences, so a notice raised there is still recognisably the same
 * pill.
 */
public final class TerminalDress {

    /**
     * What the terminal is wearing right now. Supplied by the activity that owns it; absent on
     * every other screen, where {@link #stored} answers instead.
     */
    public interface Source {

        /** The terminal's own corner radius in px, as its top corners actually draw it. */
        float terminalCornerRadiusPx();

        /**
         * The fill a pane is painted with: the glass tint while the panes are slabs, the terminal
         * background otherwise, and transparent while the terminal paints no surface at all (the
         * wallpaper is showing through).
         */
        int terminalFillColor();
    }

    /**
     * Near opaque, the alpha the hint cards settle at: the transcript stays visible around the pill
     * without reading through the words on it. Independent of the terminal's own opacity slider —
     * a message on a 40% terminal still has to be legible.
     */
    static final int FILL_ALPHA = 246;
    /** The hairline every terminal hint is edged with. */
    static final int STROKE_ALPHA = 70;
    /** The subtitle's share of the title's ink. */
    static final int SUB_TEXT_ALPHA = 122;
    /**
     * The softening the pill keeps where the terminal has no radius to lend it. A flush terminal is
     * square, but a pill with literally square corners reads as a torn rectangle rather than as a
     * note — the same 4dp the hint cards fall back to for the same reason.
     */
    static final float MIN_RADIUS_DP = 4f;
    /**
     * The radius the stored fallback uses for Floating, where the terminal rounds by the dock
     * capsule capped well under its pill. The live source reports the real number; this is only the
     * stand-in for a screen with no terminal on it.
     */
    static final float FLOATING_RADIUS_DP = 14f;

    /**
     * Built once per application: the wrapper is a thin view over the app's shared preferences, so
     * the numbers below are read live through it while the package context behind it is not rebuilt
     * for every notice. Keyed on the application it was built from, so it never outlives one.
     */
    @Nullable private static Context sPreferencesContext;
    @Nullable private static TermuxAppSharedPreferences sStoredPreferences;

    /** The terminal's own corner radius, before it is capped to the height of what wears it. */
    public final float terminalRadiusPx;
    public final int fillColor;
    public final int strokeColor;
    public final float strokeWidthPx;
    public final int textColor;
    public final int subTextColor;

    private final float mDensity;

    private TerminalDress(float terminalRadiusPx, int fillColor, int strokeColor,
                          float strokeWidthPx, int textColor, int subTextColor, float density) {
        this.terminalRadiusPx = terminalRadiusPx;
        this.fillColor = fillColor;
        this.strokeColor = strokeColor;
        this.strokeWidthPx = strokeWidthPx;
        this.textColor = textColor;
        this.subTextColor = subTextColor;
        this.mDensity = density;
    }

    /** The dress as the terminal is wearing it, or as preferences remember it when there is none. */
    @NonNull
    public static TerminalDress resolve(@NonNull Context context, @Nullable Source live) {
        return live == null
            ? build(context, storedRadiusPx(context), Color.TRANSPARENT)
            : build(context, live.terminalCornerRadiusPx(), live.terminalFillColor());
    }

    /** The dress for a screen with no terminal on it. */
    @NonNull
    public static TerminalDress stored(@NonNull Context context) {
        return resolve(context, null);
    }

    @NonNull
    private static TerminalDress build(@NonNull Context context, float terminalRadiusPx,
                                       int terminalFillColor) {
        float density = context.getResources().getDisplayMetrics().density;
        int base = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSurfaceBase,
            ContextCompat.getColor(context, R.color.termux_surface_base));
        int outline = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOutlineVariant,
            ContextCompat.getColor(context, R.color.termux_outline_variant));
        int onSurface = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        return new TerminalDress(Math.max(0f, terminalRadiusPx),
            fillColor(terminalFillColor, base),
            ColorUtils.setAlphaComponent(outline, STROKE_ALPHA),
            Math.max(1f, Math.round(density)),
            onSurface, ColorUtils.setAlphaComponent(onSurface, SUB_TEXT_ALPHA),
            density);
    }

    /**
     * The pill's fill: what the terminal is painted with, laid over the theme's own ground and then
     * thickened to {@link #FILL_ALPHA}.
     *
     * <p>One rule covers all three terminals. A glass pane hands over its tint, which is an alpha
     * meant to sit on a blur the pill has none of, so compositing it on the ground is what keeps the
     * pill the same hue as the slab under it. A docked terminal hands over its background at
     * whatever the opacity slider says, and a terminal painting nothing at all hands over
     * transparent — both land on the ground, which is exactly the colour the window would be.
     */
    static int fillColor(int terminalFillColor, int surfaceBaseColor) {
        int opaqueBase = ColorUtils.setAlphaComponent(surfaceBaseColor, 255);
        int composited = Color.alpha(terminalFillColor) == 0
            ? opaqueBase : ColorUtils.compositeColors(terminalFillColor, opaqueBase);
        return ColorUtils.setAlphaComponent(composited, FILL_ALPHA);
    }

    /**
     * The radius something of this height actually draws with: the terminal's own, floored so a
     * square terminal still softens its notices, and capped at half the height, past which a corner
     * has stopped being a corner and the shape is a pill.
     *
     * @param heightPx the measured height, or 0 before there is one.
     */
    public static float cornerRadiusPx(float terminalRadiusPx, int heightPx, float density) {
        float radius = Math.max(terminalRadiusPx, MIN_RADIUS_DP * density);
        return heightPx > 0 ? Math.min(radius, heightPx / 2f) : radius;
    }

    /** {@link #cornerRadiusPx(float, int, float)} for this dress. */
    public float cornerRadiusPx(int heightPx) {
        return cornerRadiusPx(terminalRadiusPx, heightPx, mDensity);
    }

    /**
     * The pill itself: fill, hairline and the radius for this height. Flat — no elevation, no
     * shadow — like every other surface that hangs off the terminal.
     */
    @NonNull
    public GradientDrawable background(int heightPx) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fillColor);
        shape.setStroke(Math.round(strokeWidthPx), strokeColor);
        shape.setCornerRadius(cornerRadiusPx(heightPx));
        return shape;
    }

    /**
     * The terminal's radius as preferences remember it. Docked rounds by the terminal's own knob;
     * Floating rounds by the dock capsule, capped where the panes cap it.
     */
    private static float storedRadiusPx(@NonNull Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        TermuxAppSharedPreferences preferences = storedPreferences(context);
        if (preferences == null) return MIN_RADIUS_DP * density;
        boolean floating = TERMUX_APP.APP_LAUNCHER_DOCK_STYLE_ROUNDED.equals(
            preferences.getAppLauncherDockStyle());
        return density * (floating ? FLOATING_RADIUS_DP : preferences.getTerminalCornerRadius());
    }

    @Nullable
    private static synchronized TermuxAppSharedPreferences storedPreferences(
            @NonNull Context context) {
        Context application = context.getApplicationContext();
        if (sPreferencesContext != application) {
            sPreferencesContext = application;
            sStoredPreferences = TermuxAppSharedPreferences.build(application);
        }
        return sStoredPreferences;
    }
}
