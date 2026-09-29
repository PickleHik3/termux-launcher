package com.termux.app.chrome;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole glass stack of one surface — shared wallpaper frame, frost, refraction, tint and grain,
 * rim — built from one plain {@link Spec}. The dock, the keyboard's capsule and the card under the
 * keyboard are the same material because they are the same call with their own values, so a change
 * to the material is made here once.
 *
 * <p>Everything that decides a value is pure ({@link Spec}, {@link #lookFor}, {@link #keyboard}),
 * and only {@link #build} touches drawables.</p>
 */
public final class GlassStack {

    private GlassStack() {}

    /** One surface's glass, as values. Immutable; the {@code with} methods return a changed copy. */
    public static final class Spec {
        public final int blurRadiusDp;
        /** The tint's opacity, 0..1: what {@link GlassSurfaceFactory#surface} calls barAlpha. */
        public final float tintAlpha;
        public final int grainPercent;
        public final float cornerRadiusPx;
        public final boolean rim;
        @NonNull public final GlassRefraction.Look look;
        /** {@link GlassRefraction#SEAM_BOTTOM} and friends: edges another surface continues past. */
        public final int seams;
        /** How much of the light model this view renders; less than 1 leaves the rest to a strip. */
        public final float sliceEnd;
        public final boolean foot;
        /** One alpha over the finished stack, in percent; 100 leaves it untouched. */
        public final int stackAlphaPercent;
        /** Stands in for the tint (a colour scheme's background), or null for the glass tint. */
        @Nullable public final Integer tintColor;

        private Spec(int blurRadiusDp, float tintAlpha, int grainPercent, float cornerRadiusPx,
                     boolean rim, @NonNull GlassRefraction.Look look, int seams, float sliceEnd,
                     boolean foot, int stackAlphaPercent, @Nullable Integer tintColor) {
            this.blurRadiusDp = blurRadiusDp;
            this.tintAlpha = tintAlpha;
            this.grainPercent = grainPercent;
            this.cornerRadiusPx = cornerRadiusPx;
            this.rim = rim;
            this.look = look;
            this.seams = seams;
            this.sliceEnd = sliceEnd;
            this.foot = foot;
            this.stackAlphaPercent = stackAlphaPercent;
            this.tintColor = tintColor;
        }

        /** The dock's material: the whole light model, no foot, no seams, the dock's refraction. */
        @NonNull
        public static Spec of(int blurRadiusDp, float tintAlpha, int grainPercent,
                              float cornerRadiusPx, @Nullable GlassRefraction.Look fancierLook) {
            return new Spec(blurRadiusDp, tintAlpha, grainPercent, cornerRadiusPx, false,
                lookFor(fancierLook), 0, 1f, false, 100, null);
        }

        @NonNull public Spec withBlur(int dp) {
            return new Spec(dp, tintAlpha, grainPercent, cornerRadiusPx, rim, look, seams,
                sliceEnd, foot, stackAlphaPercent, tintColor);
        }

        @NonNull public Spec withRim(boolean on) {
            return new Spec(blurRadiusDp, tintAlpha, grainPercent, cornerRadiusPx, on, look, seams,
                sliceEnd, foot, stackAlphaPercent, tintColor);
        }

        @NonNull public Spec withSeams(int edges) {
            return new Spec(blurRadiusDp, tintAlpha, grainPercent, cornerRadiusPx, rim, look, edges,
                sliceEnd, foot, stackAlphaPercent, tintColor);
        }

        @NonNull public Spec withSlice(float end, boolean withFoot) {
            return new Spec(blurRadiusDp, tintAlpha, grainPercent, cornerRadiusPx, rim, look, seams,
                end, withFoot, stackAlphaPercent, tintColor);
        }

        @NonNull public Spec withStackAlpha(int percent) {
            return new Spec(blurRadiusDp, tintAlpha, grainPercent, cornerRadiusPx, rim, look, seams,
                sliceEnd, foot, percent, tintColor);
        }

        @NonNull public Spec withTintColor(@Nullable Integer argb) {
            return new Spec(blurRadiusDp, tintAlpha, grainPercent, cornerRadiusPx, rim, look, seams,
                sliceEnd, foot, stackAlphaPercent, argb);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) return true;
            if (!(other instanceof Spec)) return false;
            Spec that = (Spec) other;
            return blurRadiusDp == that.blurRadiusDp && tintAlpha == that.tintAlpha
                && grainPercent == that.grainPercent && cornerRadiusPx == that.cornerRadiusPx
                && rim == that.rim && look.equals(that.look) && seams == that.seams
                && sliceEnd == that.sliceEnd && foot == that.foot
                && stackAlphaPercent == that.stackAlphaPercent
                && java.util.Objects.equals(tintColor, that.tintColor);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(blurRadiusDp, tintAlpha, grainPercent, cornerRadiusPx,
                rim, look, seams, sliceEnd, foot, stackAlphaPercent, tintColor);
        }
    }

    /**
     * The refraction a glass surface draws with: the user's Fancier Glass look while it is on, and
     * the numbers the dock always ran with otherwise. The one rule for every surface that follows
     * the dock's material, so none of them can be plain while the dock is not. A phone that runs no
     * shader draws the plain frost whatever this answers ({@link SharedFrameDrawable}).
     */
    @NonNull
    public static GlassRefraction.Look lookFor(@Nullable GlassRefraction.Look fancierLook) {
        return fancierLook != null ? fancierLook : GlassRefraction.Look.DEFAULT;
    }

    /**
     * The keyboard's glass as its own slot says: its blur and grain follow Base until detached, its
     * tint follows the dock's opacity ({@code dockAlpha}) while its own Intensity row does, and the
     * whole-stack alpha is its Opacity row once that detaches ({@link #keyboardStackAlphaPercent}).
     * Rim, seams and slice are the caller's to set; the tint colour and rim style come from the
     * factory's look in {@link #build}, the same as the dock's.
     */
    @NonNull
    public static Spec keyboard(@NonNull TermuxAppSharedPreferences preferences, float dockAlpha,
                                float cornerRadiusPx, @Nullable GlassRefraction.Look fancierLook) {
        boolean linked = preferences.isSurfaceInheriting(
            TermuxAppSharedPreferences.SurfaceSlot.KEYBOARD,
            TermuxAppSharedPreferences.SurfaceProperty.OPACITY);
        float tint = linked ? dockAlpha : preferences.getInAppKeyboardBackgroundOpacity() / 100f;
        return Spec.of(preferences.getInAppKeyboardBlurRadius(), tint,
            preferences.getInAppKeyboardGrain(), cornerRadiusPx, fancierLook)
            .withStackAlpha(keyboardStackAlphaPercent(preferences));
    }

    /**
     * The one alpha over the keyboard's finished stack, in percent. While the keyboard's opacity
     * still follows Base it is the dock's material, and a whole-stack fade would let the unblurred,
     * ungrained wallpaper through and read as a different material, so it stays at 100 exactly as
     * the background colour and Intensity are ignored in that state. Detached, the keyboard's own
     * Opacity row applies. The under-keyboard strip asks here too, so both fade together.
     */
    public static int keyboardStackAlphaPercent(@NonNull TermuxAppSharedPreferences preferences) {
        boolean linked = preferences.isSurfaceInheriting(
            TermuxAppSharedPreferences.SurfaceSlot.KEYBOARD,
            TermuxAppSharedPreferences.SurfaceProperty.OPACITY);
        return linked ? 100 : preferences.getInAppKeyboardBackdropOpacity();
    }

    /**
     * The stack itself, bottom to top: {@code backdrop} (the caller's view onto the shared frame,
     * or null where there is nothing to blur) dressed with the frost and the refraction, the tint
     * with its grain and rim, all under the spec's one stack alpha.
     */
    @NonNull
    public static Drawable build(@NonNull GlassSurfaceFactory glass, @NonNull Spec spec,
                                 float density, @Nullable SharedFrameDrawable backdrop) {
        List<Drawable> layers = new ArrayList<>(3);
        if (backdrop != null) {
            // The content-aware light scatter every glass surface's frame wears.
            backdrop.setColorFilter(GlassFilters.frost());
            backdrop.setRefraction(spec.look, density, spec.cornerRadiusPx, spec.seams);
            layers.add(backdrop);
        }
        if (spec.tintColor != null) {
            layers.add(new android.graphics.drawable.ColorDrawable(spec.tintColor));
            if (spec.rim) layers.add(glass.rimDrawable(spec.cornerRadiusPx));
        } else {
            layers.add(glass.surface(spec.tintAlpha, 0f, spec.sliceEnd, spec.foot,
                spec.grainPercent, spec.cornerRadiusPx, spec.rim));
        }
        Drawable material = layers.size() == 1
            ? layers.get(0) : new LayerDrawable(layers.toArray(new Drawable[0]));
        if (spec.stackAlphaPercent < 100)
            material.setAlpha(Math.round(255f * spec.stackAlphaPercent / 100f));
        return material;
    }
}
