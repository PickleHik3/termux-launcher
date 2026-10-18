package com.termux.app.chrome;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    /** Who says how much veil a band needs; null in the tests that only drive the layer shapes. */
    @Nullable private final ChromeInk mInk;

    GlassSurfaceFactory(@NonNull ChromeRenderer.Surfaces surfaces) {
        this(surfaces, null);
    }

    GlassSurfaceFactory(@NonNull ChromeRenderer.Surfaces surfaces, @Nullable ChromeInk ink) {
        this(surfaces, ink, null);
    }

    private GlassSurfaceFactory(@NonNull ChromeRenderer.Surfaces surfaces, @Nullable ChromeInk ink,
                                @Nullable GlassLook lookOverride) {
        mSurfaces = surfaces;
        mInk = ink;
        mLookOverride = lookOverride;
    }

    /** A preset's tint and rim standing in for the live preferences'; null reads the preferences. */
    @Nullable private final GlassLook mLookOverride;

    /** This factory's material, drawn in {@code look} instead of the preferences' (the preset tiles). */
    @NonNull
    public GlassSurfaceFactory withLook(@NonNull GlassLook look) {
        return new GlassSurfaceFactory(mSurfaces, mInk, look);
    }

    /**
     * The ink, rim and accent hue every surface built here wears: the preset's, or the live one,
     * at amount 0. Its accent colours are resolved only once an amount asks for them
     * ({@link #look(int)}).
     */
    @NonNull
    public GlassLook look() {
        return mLookOverride != null ? mLookOverride : GlassLook.of(mSurfaces.preferences());
    }

    /**
     * This factory's look mixing in {@code tintAmountPercent} of its accent (the Tint control),
     * with the accent's colours resolved from the theme whenever the amount is above 0, whatever
     * the Look's ink: Clear and Mist take the accent exactly as the Tint Look does.
     */
    @NonNull
    public GlassLook look(int tintAmountPercent) {
        GlassLook look = look().withAmountPercent(tintAmountPercent);
        if (!look.needsAccentColors()) return look;
        android.content.Context context = mSurfaces.context();
        int accent = mSurfaces.accentColor();
        int containerAttr;
        int roleAttr;
        switch (look.hue) {
            case SECONDARY:
                containerAttr = com.google.android.material.R.attr.colorSecondaryContainer;
                roleAttr = com.google.android.material.R.attr.colorSecondary;
                break;
            case TERTIARY:
                containerAttr = com.google.android.material.R.attr.colorTertiaryContainer;
                roleAttr = com.google.android.material.R.attr.colorTertiary;
                break;
            case PRIMARY:
            default:
                containerAttr = com.google.android.material.R.attr.colorPrimaryContainer;
                // M3's surface tint is the primary role; MDC exposes no colorSurfaceTint attribute.
                roleAttr = androidx.appcompat.R.attr.colorPrimary;
                break;
        }
        int container = com.google.android.material.color.MaterialColors.getColor(
            context, containerAttr, accent);
        int role = com.google.android.material.color.MaterialColors.getColor(
            context, roleAttr, accent);
        return look.withAccentColors(container, role);
    }

    /** The colour a surface mixing in {@code tintAmountPercent} of the accent draws its tint in. */
    public int tintBase(int tintAmountPercent) {
        return look(tintAmountPercent).tintBase(mSurfaces.glassBaseColor());
    }

    /** The dock's tint strength, percent: what every dock-material surface wears. */
    private int dockTintStrength() {
        TermuxAppSharedPreferences preferences = mSurfaces.preferences();
        return preferences != null ? preferences.getDockTintStrength()
            : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_TINT;
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
        return dockSurface(barAlpha, sliceStart, sliceEnd, withFoot, dockTintStrength());
    }

    /**
     * The dock's glass wearing {@code tintStrengthPercent} of the tint: for a surface that is the
     * dock's material but belongs to another slot's Tint control (the status bar's fills).
     */
    @NonNull
    public Drawable dockSurface(float barAlpha, float sliceStart, float sliceEnd, boolean withFoot,
                                int tintStrengthPercent) {
        TermuxAppSharedPreferences preferences = mSurfaces.preferences();
        int grain = preferences != null
            ? preferences.getDockGlassGrain()
            : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN;
        return surface(barAlpha, sliceStart, sliceEnd, withFoot, grain, tintStrengthPercent);
    }

    /** The status bar's tint strength, percent. */
    public int statusTintStrength() {
        TermuxAppSharedPreferences preferences = mSurfaces.preferences();
        return preferences != null ? preferences.getStatusBarTintStrength()
            : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_SURFACE_BASE_TINT;
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
        return statusBarSurface(barAlpha, sliceStart, sliceEnd, rim, null);
    }

    /**
     * @param band which chrome band this slab is, so its glass can be measured and — when the
     *     wallpaper under it leaves the mode's ink unreadable — veiled by exactly as much as
     *     {@link ChromeInk} says it needs. Null for a slab no ink stands on.
     */
    @NonNull
    public Drawable statusBarSurface(float barAlpha, float sliceStart, float sliceEnd, boolean rim,
                                     @Nullable GlassBackdropCache.Band band) {
        return statusBarSurface(barAlpha, sliceStart, sliceEnd,
            rim ? ChromeEdgeRule.ALL : ChromeEdgeRule.NONE, band);
    }

    /**
     * @param strokeEdges the {@link ChromeEdgeRule} edges the containing stroke runs along: all
     *     four for the capsule, only the inner edge for a docked bar, whose other edges run into
     *     the screen and the strip behind the system status bar
     */
    @NonNull
    public Drawable statusBarSurface(float barAlpha, float sliceStart, float sliceEnd,
                                     int strokeEdges, @Nullable GlassBackdropCache.Band band) {
        return statusBarSurface(barAlpha, sliceStart, sliceEnd, strokeEdges, band, false);
    }

    /**
     * @param glassDrawsEdge the frost under this bar draws its outline itself
     *     ({@link #frostDrawsEdge}): the bar keeps the corner {@code strokeEdges} gives it and lays
     *     no stroke, so the gathered light is its one edge
     */
    @NonNull
    public Drawable statusBarSurface(float barAlpha, float sliceStart, float sliceEnd,
                                     int strokeEdges, @Nullable GlassBackdropCache.Band band,
                                     boolean glassDrawsEdge) {
        TermuxAppSharedPreferences preferences = mSurfaces.preferences();
        int grain = preferences != null
            ? preferences.getStatusBarGrain()
            : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_STATUS_BAR_GRAIN;
        // Height-clamped like the outline clip (min(configured, height/2)): the compact pill's
        // baked stroke must curve exactly with the clip, or the corners double up.
        float cornerRadiusPx = strokeEdges != ChromeEdgeRule.NONE && mSurfaces.roundedDockStyle()
            ? mSurfaces.statusBarRimCornerRadiusPx()
            : 0f;
        return surface(barAlpha, sliceStart, sliceEnd, true, grain, cornerRadiusPx,
            glassDrawsEdge ? ChromeEdgeRule.NONE : strokeEdges, band, band, statusTintStrength());
    }

    /** The dock's material: {@code grain} given, the tint the dock's. */
    @NonNull
    public Drawable surface(float barAlpha, float sliceStart, float sliceEnd, boolean withFoot,
                            int grain) {
        return surface(barAlpha, sliceStart, sliceEnd, withFoot, grain, 0f, false);
    }

    /** A surface wearing {@code tintStrengthPercent} of the tint: the keyboard's strip. */
    @NonNull
    public Drawable surface(float barAlpha, float sliceStart, float sliceEnd, boolean withFoot,
                            int grain, int tintStrengthPercent) {
        return surface(barAlpha, sliceStart, sliceEnd, withFoot, grain, 0f,
            ChromeEdgeRule.NONE, null, null, tintStrengthPercent);
    }

    @NonNull
    public Drawable surface(float barAlpha, float sliceStart, float sliceEnd, boolean withFoot,
                            int grain, float cornerRadiusPx, boolean withRim) {
        return surface(barAlpha, sliceStart, sliceEnd, withFoot, grain, cornerRadiusPx, withRim,
            null);
    }

    /**
     * The one glass surface builder every surface goes through: tint, vertical light model, the
     * band's veil, grain, and optionally the rounded containing stroke. Callers differ only in the
     * values their own controls supply, which is what keeps the dock, the keyboard and the status
     * bar the same material while still being tunable apart.
     *
     * <p>{@code band} is what turns this from a fixed material into a measured one. Given a band,
     * the surface tells {@link ChromeInk} what glass it draws — the opacity, the foot, the slice of
     * the light model this view renders — so the ink can be resolved against what is really there,
     * and it draws back whatever veil that resolution asked for.</p>
     */
    @NonNull
    public Drawable surface(float barAlpha, float sliceStart, float sliceEnd, boolean withFoot,
                            int grain, float cornerRadiusPx, boolean withRim,
                            @Nullable GlassBackdropCache.Band band) {
        return surface(barAlpha, sliceStart, sliceEnd, withFoot, grain, cornerRadiusPx, withRim,
            band, dockTintStrength());
    }

    /** {@link #surface} wearing {@code tintStrengthPercent} of the tint (the stack's own slot). */
    @NonNull
    public Drawable surface(float barAlpha, float sliceStart, float sliceEnd, boolean withFoot,
                            int grain, float cornerRadiusPx, boolean withRim,
                            @Nullable GlassBackdropCache.Band band, int tintStrengthPercent) {
        return surface(barAlpha, sliceStart, sliceEnd, withFoot, grain, cornerRadiusPx,
            withRim ? ChromeEdgeRule.ALL : ChromeEdgeRule.NONE, band, band, tintStrengthPercent);
    }

    /**
     * The glass a band's pane is continued by, on a strip no content stands on: the same tint, the
     * same slice of the light model, the same frost and blur, and no question of its own.
     *
     * <p>The strip behind the system status bar is the case. It is not a band — nothing is drawn on
     * it, so there is nothing to keep legible — and giving it one put the pane's whole veil on the
     * one piece of glass that did not need it: a whitish wash under the system status bar in light
     * mode with a hard edge where the pane began, a dark wash in dark mode, and the content below
     * still standing on unveiled glass.</p>
     *
     * @param veilOf the pane whose veil this strip repeats, for a seamless sheet; null leaves it
     *     bare glass, which is the launcher's choice — the veil belongs under the content, and the
     *     step at the system bar's bottom edge is accepted
     */
    @NonNull
    public Drawable statusBarExtensionSurface(float barAlpha, float sliceStart, float sliceEnd,
                                              @Nullable GlassBackdropCache.Band veilOf) {
        TermuxAppSharedPreferences preferences = mSurfaces.preferences();
        int grain = preferences != null
            ? preferences.getStatusBarGrain()
            : TermuxPreferenceConstants.TERMUX_APP.DEFAULT_STATUS_BAR_GRAIN;
        return surface(barAlpha, sliceStart, sliceEnd, true, grain, 0f, ChromeEdgeRule.NONE, null,
            veilOf, statusTintStrength());
    }

    /**
     * @param glassBand the band whose glass this surface <em>is</em> — it reports its opacity, its
     *     foot and its slice under this name, and a measurement of that band is a measurement of
     *     this material. Null for a surface nothing is measured against.
     * @param veilBand the band whose veil this surface draws. Normally the same one; a strip that
     *     only continues another pane's material names that pane here and nothing above.
     */
    @NonNull
    private Drawable surface(float barAlpha, float sliceStart, float sliceEnd, boolean withFoot,
                             int grain, float cornerRadiusPx, int strokeEdges,
                             @Nullable GlassBackdropCache.Band glassBand,
                             @Nullable GlassBackdropCache.Band veilBand, int tintStrengthPercent) {
        GlassLook look = look(tintStrengthPercent);
        int base = look.tintBase(mSurfaces.glassBaseColor());
        int accent = mSurfaces.accentColor();
        float clamped = barAlpha < 0f ? 0f : (barAlpha > 1f ? 1f : barAlpha);
        // Opacity controls the colored material wash and its lighting. The wallpaper blur and
        // grain are independent physical layers: reducing tint should reveal more frost/texture,
        // not cross-fade back to sharp wallpaper.
        int baseAlpha = ChromePolicy.dockGlassBaseAlpha(clamped);
        int topSheenAlpha = DockGlassRendering.topSheenAlpha(clamped);
        int midSheenAlpha = DockGlassRendering.midSheenAlpha(clamped);
        int bottomFootAlpha = DockGlassRendering.footAlpha(clamped, withFoot);
        if (glassBand != null && mInk != null) {
            mInk.noteBandGlass(glassBand, clamped, withFoot, sliceStart, sliceEnd, base);
        }
        GradientDrawable baseLayer = new GradientDrawable();
        baseLayer.setColor(SchemeTone.withAlpha(base, baseAlpha / 255f));
        baseLayer.setDither(true);

        // Obsidian glass has no sheen and no foot: one white wash over the tint, flat.
        int[] sliceColors = look.obsidianTint
            ? new int[] {GlassLook.wash(), GlassLook.wash()}
            : DockGlassRendering.lightModelSlice(accent, topSheenAlpha, midSheenAlpha,
                bottomFootAlpha, sliceStart, sliceEnd);
        GradientDrawable lightLayer = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, sliceColors);
        lightLayer.setDither(true);

        List<Drawable> layers = new ArrayList<>();
        layers.add(baseLayer);
        layers.add(lightLayer);
        // The band's veil, when it needs one: another pane of its own base colour, never a white
        // wash, at the smallest alpha that lets the chrome's ink clear its target. It goes here,
        // over the light model, because that is where OnGlass measured it — the dark foot is the
        // one part of the model that can push a row under the promised ratio, and drawing the veil
        // under it would let the foot undo exactly what the veil was bought for. It also leaves the
        // base layer alone, so the user's opacity slider keeps meaning what it says.
        int veil = veilBand != null && mInk != null ? mInk.bandVeil(veilBand) : Color.TRANSPARENT;
        if (Color.alpha(veil) > 0) {
            GradientDrawable veilLayer = new GradientDrawable();
            veilLayer.setColor(veil);
            veilLayer.setDither(true);
            if (cornerRadiusPx > 0f) veilLayer.setCornerRadius(cornerRadiusPx);
            layers.add(veilLayer);
        }
        // Optional film grain over the frosted glass — reads as real glass texture instead of a flat
        // blur. Amount is user-controlled (Appearance > Glass grain); 0 omits the layer entirely.
        if (grain > 0) {
            layers.add(grainLayer(grain));
        }
        Drawable rim = rimDrawable(cornerRadiusPx, strokeEdges);
        if (rim != null) layers.add(rim);
        if (cornerRadiusPx > 0f) {
            baseLayer.setCornerRadius(cornerRadiusPx);
            lightLayer.setCornerRadius(cornerRadiusPx);
        }
        return new LayerDrawable(layers.toArray(new Drawable[0]));
    }

    /**
     * The veil {@code band}'s glass is drawn with, for a surface that lays its own tint rather than
     * going through {@link #surface}: {@link Color#TRANSPARENT} for no band, or none needed.
     */
    int bandVeil(@Nullable GlassBackdropCache.Band band) {
        return band != null && mInk != null ? mInk.bandVeil(band) : Color.TRANSPARENT;
    }

    /**
     * Tells the ink the blur {@code band}'s glass is drawn at ({@link ChromeInk#noteBandBlur}), so
     * its rows are measured on the frame it really shows. Nothing for no band or no ink.
     */
    void noteBandBlur(@Nullable GlassBackdropCache.Band band, int blurRadiusDp) {
        if (band != null && mInk != null) mInk.noteBandBlur(band, blurRadiusDp);
    }

    /**
     * {@code band}'s veil alone, as a layer for a view that stands over glass another surface
     * draws: the docked keyboard over the unified dock glass is the case. The unified glass spans
     * the dock rows and the keyboard, so its own veil would darken rows no key label stands on;
     * the keyboard host instead wears this as its background — sized to the host's own bounds,
     * over the glass behind it and under its keys, the order a pane's slab draws its veil in.
     *
     * <p>The glass under it is described here as it is ({@code barAlpha}, the slice, the foot) so
     * the band is measured against the material that is really there. Null when the band needs no
     * veil, or nothing measures it.</p>
     */
    @Nullable
    public Drawable veilOnlyLayer(@NonNull GlassBackdropCache.Band band, float barAlpha,
                                  float sliceStart, float sliceEnd, boolean withFoot) {
        if (mInk == null) return null;
        float clamped = barAlpha < 0f ? 0f : (barAlpha > 1f ? 1f : barAlpha);
        int base = tintBase(dockTintStrength());
        mInk.noteBandGlass(band, clamped, withFoot, sliceStart, sliceEnd, base);
        // The glass under it is the dock's, blurred at the dock's radius.
        mInk.noteBandBlur(band, mSurfaces.effectiveDockBlurRadiusDp());
        int veil = mInk.bandVeil(band);
        if (Color.alpha(veil) <= 0) return null;
        GradientDrawable layer = new GradientDrawable();
        layer.setColor(veil);
        layer.setDither(true);
        return layer;
    }

    /**
     * Tells the ink that {@code band}'s content stands on the dock's glass, drawn by another view
     * at {@code barAlpha}: the A&ndash;Z letters on the dock, the plank or their own capsule, the
     * extra keys on the dock. The base is the dock's tint at its Tint and hue, so an answer for the
     * band is an answer on that face. Draws nothing.
     *
     * @return whether that face moved since it was last told; false with no ink to tell
     */
    public boolean noteDockGlassUnder(@NonNull GlassBackdropCache.Band band, float barAlpha) {
        if (mInk == null) return false;
        float clamped = barAlpha < 0f ? 0f : (barAlpha > 1f ? 1f : barAlpha);
        int base = tintBase(dockTintStrength());
        return mInk.noteBandGlass(band, clamped, false, 0f, 1f, base);
    }

    /**
     * The one containing stroke every glass surface draws: barely there, or it reads as a drawn
     * border over the glass rather than the edge of the material. Also the dock capsule's
     * background, whose outline it supplies.
     */
    @NonNull
    public GradientDrawable rim(float cornerRadiusPx) {
        GradientDrawable rim = new GradientDrawable();
        rim.setColor(Color.TRANSPARENT);
        rim.setCornerRadius(cornerRadiusPx);
        rim.setStroke(Math.max(1, Math.round(mSurfaces.dpToPx(1))),
            SchemeTone.withAlpha(mSurfaces.outlineColor(), RIM_ALPHA / 255f));
        return rim;
    }

    /**
     * Whether the frost {@code frost} stands for draws its own outline: it is on screen and its
     * {@link SharedFrameDrawable} runs the program, whose gathered light is then the edge, so the
     * tint over it lays no rim of its own. Asked of the frost itself, never of the look, so a
     * frost that is hidden, plain or refused by the driver leaves the tint its rim. Below Android
     * 13 nothing refracts and every rim stays.
     */
    public static boolean frostDrawsEdge(@Nullable android.widget.ImageView frost) {
        if (frost == null || frost.getVisibility() != android.view.View.VISIBLE) return false;
        SharedFrameDrawable shared = SharedFrameDrawable.of(frost.getDrawable());
        return shared != null && shared.refracts();
    }

    /**
     * The dock's outline with nothing drawn: what its host wears in place of
     * {@link #rimUnderGlass} while the dock's program draws its edge, so the capsule still clips to
     * the shape the rim's outline gave it.
     */
    @NonNull
    public Drawable outlineOnly(float cornerRadiusPx) {
        return new OutlineOnlyDrawable(Math.max(0f, cornerRadiusPx));
    }

    /**
     * Draws nothing and answers {@link GradientRimDrawable}'s outline, the same round rect at
     * full alpha, so a view clipping to its background's outline clips exactly as it did.
     */
    private static final class OutlineOnlyDrawable extends Drawable {
        private final float mRadiusPx;

        OutlineOnlyDrawable(float radiusPx) {
            mRadiusPx = radiusPx;
        }

        @Override public void draw(@NonNull android.graphics.Canvas canvas) {}

        @Override public void getOutline(@NonNull android.graphics.Outline outline) {
            outline.setRoundRect(getBounds(), mRadiusPx);
        }

        @Override public void setAlpha(int alpha) {}

        @Override public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {}

        @Override public int getOpacity() {
            return android.graphics.PixelFormat.TRANSPARENT;
        }
    }

    /**
     * The rim every surface wears over its glass, under every look: the dock's gradient as the dock
     * shows it (white 0.10 at the top-left to 0.01), so the keyboard, status bar, panes and cards
     * read like the dock. Every such rim goes through here.
     */
    @NonNull
    public Drawable rimDrawable(float cornerRadiusPx) {
        return new GradientRimDrawable(cornerRadiusPx, Math.max(1, Math.round(mSurfaces.dpToPx(1))),
            GlassTokens.RIM_OVER_GLASS_START, GlassTokens.RIM_OVER_GLASS_END);
    }

    /**
     * The rim along {@code strokeEdges} only: the whole rim for all four, nothing for none, and
     * otherwise the same rim with the other sides carried off the surface
     * ({@link OpenEdgeDrawable}), so what is left is the line along the edges that keep it.
     */
    @Nullable
    public Drawable rimDrawable(float cornerRadiusPx, int strokeEdges) {
        return alongEdges(rimDrawable(cornerRadiusPx), cornerRadiusPx, strokeEdges);
    }

    /**
     * The dock's rim, which is its host's background and so draws under the dock's blurred
     * backdrop: the full gradient, which the backdrop softens to what {@link #rimDrawable(float)}
     * draws over glass. Its outline is the capsule the dock clips to.
     */
    @NonNull
    public Drawable rimUnderGlass(float cornerRadiusPx) {
        return new GradientRimDrawable(cornerRadiusPx, Math.max(1, Math.round(mSurfaces.dpToPx(1))),
            GlassTokens.RIM_START, GlassTokens.RIM_END);
    }

    /** {@link #rimUnderGlass(float)} along {@code strokeEdges} only, as {@link #rimDrawable(float, int)}. */
    @Nullable
    public Drawable rimUnderGlass(float cornerRadiusPx, int strokeEdges) {
        return alongEdges(rimUnderGlass(cornerRadiusPx), cornerRadiusPx, strokeEdges);
    }

    @Nullable
    private Drawable alongEdges(@NonNull Drawable rim, float cornerRadiusPx, int strokeEdges) {
        int edges = strokeEdges & ChromeEdgeRule.ALL;
        if (edges == ChromeEdgeRule.NONE) return null;
        if (edges == ChromeEdgeRule.ALL) return rim;
        int reachPx = Math.round(Math.max(0f, cornerRadiusPx) + mSurfaces.dpToPx(2));
        return new OpenEdgeDrawable(rim, ChromeEdgeRule.ALL & ~edges, reachPx);
    }

    /** A tiled grain layer whose strength is controlled only by the grain preference. */
    @NonNull
    public Drawable grainLayer(int grainPercent) {
        return DockGlassRendering.createGrainLayer(mSurfaces.context().getResources(), grainPercent);
    }

}
