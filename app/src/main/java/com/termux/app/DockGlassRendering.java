package com.termux.app;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.NonNull;

import java.util.Random;

/** Shared dock-glass math and texture generation used by both launcher and Settings preview. */
public final class DockGlassRendering {
    private static Bitmap grainBitmap;
    private DockGlassRendering() {}

    public static int grainAlpha(int percent) {
        return Math.round(Math.max(0, Math.min(100, percent)) / 100f * 60f);
    }

    /**
     * Builds the complete Material glass light model used by compact Settings specimens. The
     * launcher can split this model into slices for its dock/keyboard stack; specimens always draw
     * the complete slab and therefore use the same base, sheen, grain, and dark-foot math.
     */
    @NonNull public static Drawable createGlassSurface(@NonNull Resources resources,
                                                       int baseColor, int accentColor,
                                                       float opacity, int grainPercent,
                                                       boolean withFoot) {
        float amount = Math.max(0f, Math.min(1f, opacity));
        GradientDrawable base = new GradientDrawable();
        base.setColor(withAlpha(baseColor, Math.round(255f * amount)));
        base.setDither(true);

        int topAlpha = Math.round(16f * amount);
        int middleAlpha = Math.round(8f * amount);
        int footAlpha = withFoot ? Math.round(20f * amount) : 0;
        GradientDrawable light = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {
                withAlpha(accentColor, topAlpha),
                withAlpha(accentColor, middleAlpha),
                Color.TRANSPARENT,
                withAlpha(Color.BLACK, footAlpha)
            });
        light.setDither(true);

        if (grainPercent <= 0)
            return new LayerDrawable(new Drawable[] {base, light});
        return new LayerDrawable(new Drawable[] {
            base, light, createGrainLayer(resources, grainPercent)
        });
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    @NonNull public static synchronized Drawable createGrainLayer(
            @NonNull Resources resources, int percent) {
        if (grainBitmap == null) {
            int size = 110;
            int[] pixels = new int[size * size];
            Random random = new Random(0x6A11E);
            for (int i = 0; i < pixels.length; i++) {
                int value = random.nextInt(256);
                int alpha = random.nextInt(256);
                pixels[i] = (alpha << 24) | (value << 16) | (value << 8) | value;
            }
            grainBitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
        }
        BitmapDrawable drawable = new BitmapDrawable(resources, grainBitmap);
        drawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        drawable.setDither(true);
        drawable.setAlpha(grainAlpha(percent));
        return drawable;
    }
}
