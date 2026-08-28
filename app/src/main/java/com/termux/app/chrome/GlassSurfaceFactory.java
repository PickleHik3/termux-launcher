package com.termux.app.chrome;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.NonNull;

import com.termux.app.DockGlassRendering;
import com.termux.app.theme.SchemeTone;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds every glass surface in the app from one model, so the dock, the in-app keyboard and the
 * status bar stay the same material while still being tunable apart. Callers differ only in the
 * values their own controls supply.
 */
public final class GlassSurfaceFactory {

    /** The containing stroke's alpha, out of 255: barely there, or it reads as a drawn border. */
    static final int RIM_ALPHA = 18;

    @NonNull private final ChromeRenderer.Surfaces mSurfaces;

    GlassSurfaceFactory(@NonNull ChromeRenderer.Surfaces surfaces) {
        mSurfaces = surfaces;
    }

    /**
     * Builds the Material-tinted glass surface: an opaque neutral base with a faint top-down sheen
     * layered on top. The earlier corner-to-corner (TL->BR) accent wash read as a "digital"
     * left-light / right-dark gradient; a real glass pane catches ambient light from above, so this
     * uses a gentle, low-contrast top sheen (cool accent at the very top easing to clear well before
     * the bottom) with extra stops for a smooth, bandless falloff. Kept subtle so the blurred
     * wallpaper behind it carries the glass read rather than a synthetic gradient. The host clips
     * this to the dock's rounded outline; the view's own alpha carries the configured opacity.
     */
    @NonNull
    public Drawable dockSurface(float barAlpha) {
        return dockSurface(barAlpha, 0f, 1f);
    }

    /**
     * Builds the dock/keyboard glass tint. The vertical light model — thin cool sheen at the top, a
     * faint accent edge, a clear see-through middle, then a soft dark "foot" at the bottom that
     * suggests the slab's thickness — normally spans the full surface height ({@code sliceStart=0},
     * {@code sliceEnd=1}).
     *
     * <p>When the keyboard is shown, the glass is split across two stacked surfaces (the keyboard
     * host, then the shorter under-pill nav strip below it). Rendering the full model on each would
     * put a dark foot at the keyboard's own bottom AND another at the strip's bottom — a dark band
     * mid-slab and an over-tinted strip. Instead both surfaces render adjacent slices of ONE model
     * spanning keyboard+strip: the keyboard uses {@code [0, f]} and the strip {@code [f, 1]}, so the
     * single foot lands under the pill exactly as it does for the keyboard-off dock (which draws one
     * gradient over dock+nav). This keeps the two states looking identical.</p>
     */
    @NonNull
    public Drawable dockSurface(float barAlpha, float sliceStart, float sliceEnd) {
        return dockSurface(barAlpha, sliceStart, sliceEnd, true);
    }

    /**
     * @param withFoot when false the dark bottom "foot" of the light model is dropped. The default
     *     dock stack (in-content dock/keyboard + under-pill nav strip) sets this false so the strip
     *     is not darker than the dock body — the foot would otherwise land under the pill and read as
     *     a darker nav band. The floating capsule veil / controls bar keep the foot for slab depth.
     */
    @NonNull
    public Drawable dockSurface(float barAlpha, float sliceStart, float sliceEnd, boolean withFoot) {
        TermuxAppSharedPreferences preferences = mSurfaces.preferences();
        int grain = preferences != null
            ? preferences.getDockGlassGrain()
            : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN;
        return surface(barAlpha, sliceStart, sliceEnd, withFoot, grain);
    }

    @NonNull
    public Drawable statusBarSurface(float barAlpha, float sliceStart, float sliceEnd) {
        return statusBarSurface(barAlpha, sliceStart, sliceEnd, false);
    }

    /**
     * The status bar's glass, built from the same model as the dock and the keyboard and differing
     * only in the values its own controls supply.
     *
     * <p>It used to pass {@code withFoot=false}, which dropped the dark bottom foot the other
     * surfaces have, and no caller gave it the containing stroke and corner radius that
     * {@code configureAccessoryCapsuleOutline} gives the dock — so at identical opacity, blur and
     * grain it still read as a flat slab rather than glass. Both now come from the shared builder.
     *
     * @param rim whether this view is the visible slab (as opposed to the behind-status extension
     *            that merges into it, where a stroke would draw a line through the seam)
     */
    @NonNull
    public Drawable statusBarSurface(float barAlpha, float sliceStart, float sliceEnd, boolean rim) {
        TermuxAppSharedPreferences preferences = mSurfaces.preferences();
        int grain = preferences != null
            ? preferences.getStatusBarGrain()
            : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_STATUS_BAR_GRAIN;
        // Height-clamped like the outline clip (min(configured, height/2)): the compact pill's
        // baked stroke must curve exactly with the clip, or the corners double up.
        float cornerRadiusPx = rim && mSurfaces.roundedDockStyle()
            ? mSurfaces.statusBarRimCornerRadiusPx()
            : 0f;
        return surface(barAlpha, sliceStart, sliceEnd, true, grain, cornerRadiusPx, rim);
    }

    @NonNull
    public Drawable surface(float barAlpha, float sliceStart, float sliceEnd, boolean withFoot,
                            int grain) {
        return surface(barAlpha, sliceStart, sliceEnd, withFoot, grain, 0f, false);
    }

    /**
     * The one glass surface builder every surface goes through: tint, vertical light model, grain,
     * and optionally the rounded containing stroke. Callers differ only in the values their own
     * controls supply, which is what keeps the dock, the keyboard and the status bar the same
     * material while still being tunable apart.
     */
    @NonNull
    public Drawable surface(float barAlpha, float sliceStart, float sliceEnd, boolean withFoot,
                            int grain, float cornerRadiusPx, boolean withRim) {
        int base = mSurfaces.glassBaseColor();
        int accent = mSurfaces.accentColor();
        float clamped = barAlpha < 0f ? 0f : (barAlpha > 1f ? 1f : barAlpha);
        // Opacity controls the colored material wash and its lighting. The wallpaper blur and
        // grain are independent physical layers: reducing tint should reveal more frost/texture,
        // not cross-fade back to sharp wallpaper.
        int baseAlpha = ChromePolicy.dockGlassBaseAlpha(clamped);
        int topSheenAlpha = Math.round(16f * clamped);
        int midSheenAlpha = Math.round(8f * clamped);
        int bottomFootAlpha = withFoot ? Math.round(20f * clamped) : 0;
        GradientDrawable baseLayer = new GradientDrawable();
        baseLayer.setColor(SchemeTone.withAlpha(base, baseAlpha / 255f));
        baseLayer.setDither(true);

        int[] sliceColors = DockGlassRendering.lightModelSlice(accent, topSheenAlpha, midSheenAlpha,
            bottomFootAlpha, sliceStart, sliceEnd);
        GradientDrawable lightLayer = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, sliceColors);
        lightLayer.setDither(true);

        List<Drawable> layers = new ArrayList<>();
        layers.add(baseLayer);
        layers.add(lightLayer);
        // Optional film grain over the frosted glass — reads as real glass texture instead of a flat
        // blur. Amount is user-controlled (Appearance > Glass grain); 0 omits the layer entirely.
        if (grain > 0) {
            layers.add(grainLayer(grain));
        }
        if (withRim) {
            // Same barely-there containing stroke the dock's capsule pass draws. Anything heavier
            // reads as a drawn border over the glass rather than the edge of the material.
            GradientDrawable rim = new GradientDrawable();
            rim.setColor(Color.TRANSPARENT);
            rim.setCornerRadius(cornerRadiusPx);
            rim.setStroke(Math.max(1, Math.round(mSurfaces.dpToPx(1))),
                SchemeTone.withAlpha(mSurfaces.outlineColor(), RIM_ALPHA / 255f));
            layers.add(rim);
        }
        if (cornerRadiusPx > 0f) {
            baseLayer.setCornerRadius(cornerRadiusPx);
            lightLayer.setCornerRadius(cornerRadiusPx);
        }
        return new LayerDrawable(layers.toArray(new Drawable[0]));
    }

    /** A tiled grain layer whose strength is controlled only by the grain preference. */
    @NonNull
    public Drawable grainLayer(int grainPercent) {
        return DockGlassRendering.createGrainLayer(mSurfaces.context().getResources(), grainPercent);
    }

}
