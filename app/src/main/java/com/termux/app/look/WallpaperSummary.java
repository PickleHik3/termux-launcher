package com.termux.app.look;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What {@link WallpaperLooks} reads of a wallpaper: how bright it is and how busy, each 0..100.
 * Taken from a small downsampled bitmap ({@link #ofPixels}) or, where the app holds no pixels, from
 * the colours Android reports ({@link #ofColors}).
 */
public final class WallpaperSummary {

    public static final WallpaperSummary NEUTRAL = new WallpaperSummary(50, 50);

    public final int luminance;
    public final int busyness;

    public WallpaperSummary(int luminance, int busyness) {
        this.luminance = Math.max(0, Math.min(100, luminance));
        this.busyness = Math.max(0, Math.min(100, busyness));
    }

    /** Mean brightness, and how much neighbouring pixels differ (a mean step of 25% is fully busy). */
    @NonNull
    public static WallpaperSummary ofPixels(@NonNull int[] argb, int width, int height) {
        if (width <= 0 || height <= 0 || argb.length < width * height) return NEUTRAL;
        double sum = 0;
        double steps = 0;
        int stepCount = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double luma = luma(argb[y * width + x]);
                sum += luma;
                if (x + 1 < width) { steps += Math.abs(luma - luma(argb[y * width + x + 1])); stepCount++; }
                if (y + 1 < height) { steps += Math.abs(luma - luma(argb[(y + 1) * width + x])); stepCount++; }
            }
        }
        int luminance = (int) Math.round(sum / (width * height) * 100);
        int busyness = stepCount == 0 ? 0 : (int) Math.round(Math.min(1.0, steps / stepCount / 0.25) * 100);
        return new WallpaperSummary(luminance, busyness);
    }

    /** The primary colour's brightness; the other two colours' distance from it is the busyness. */
    @NonNull
    public static WallpaperSummary ofColors(int primary, @Nullable Integer secondary,
                                            @Nullable Integer tertiary) {
        double spread = 0;
        int others = 0;
        for (Integer other : new Integer[] {secondary, tertiary}) {
            if (other == null) continue;
            spread += distance(primary, other);
            others++;
        }
        int busyness = others == 0 ? 0 : (int) Math.round(Math.min(1.0, spread / others / 0.6) * 100);
        return new WallpaperSummary((int) Math.round(luma(primary) * 100), busyness);
    }

    /** 0..1 Rec. 709 brightness of an sRGB colour (no gamma: close enough for a step). */
    private static double luma(int argb) {
        return (0.2126 * ((argb >> 16) & 0xFF) + 0.7152 * ((argb >> 8) & 0xFF)
            + 0.0722 * (argb & 0xFF)) / 255.0;
    }

    /** 0..1 mean per-channel difference. */
    private static double distance(int a, int b) {
        return (Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF))
            + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF))
            + Math.abs((a & 0xFF) - (b & 0xFF))) / (3 * 255.0);
    }
}
