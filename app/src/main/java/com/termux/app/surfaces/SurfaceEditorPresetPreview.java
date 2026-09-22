package com.termux.app.surfaces;

/**
 * Geometry for the preset tiles: a corner of a surface, drawn at true device size.
 *
 * <p>The tiles used to be 42 x 68 dp mini phones scaled down from a 360 dp reference, which made
 * every value a preset actually differs by worth about a ninth of itself — a 24 dp corner drew as
 * 2.8 dp and a 4 dp margin as half a pixel. Classic, Mist, Slate and Bare came out as four
 * near-identical dark outlines, and the cause was the scale, not the art. There is no scale here.
 * Every number a preset carries is drawn at the dp it really is, on a 72 x 40 tile where a 24 dp
 * corner is a third of the width and a 10 dp margin is a visible band of wallpaper.
 *
 * <p>What the tile shows is the leading-bottom corner of a surface over a fixed crop: the crop
 * blurred by the preset's blur, the surface filled at the preset's opacity and grain, inset by the
 * preset's margin, cornered at the preset's radius. It shows no bars, no status pill and no phone
 * outline — a preset does not move any of those, and drawing an arrangement it does not change is
 * what made four different looks look the same.
 *
 * <p>Docked and Floating are two formulas and stay two: Docked is flush at the edges it touches and
 * rounds only where it does not, Floating is a card that pulls in on every edge it shows.
 *
 * <p>Pure arithmetic on pixels, no views.
 */
public final class SurfaceEditorPresetPreview {

    private SurfaceEditorPresetPreview() {}

    public static final int CARD_WIDTH_DP = 72;
    public static final int CARD_HEIGHT_DP = 40;

    /**
     * The band of crop kept above the surface. Without it a Docked preset at full opacity fills
     * the tile and there is nothing left for its opacity, its blur or its grain to read against.
     */
    public static final int WALLPAPER_BAND_DP = 10;

    /** The tile cannot be rounder than this and still be a rectangle. */
    private static final int MAX_TILE_CORNER_DP = CARD_HEIGHT_DP / 2;

    private static int px(float dp, float density) {
        return Math.round(dp * Math.max(0.01f, density));
    }

    /**
     * The tile's own corner, at the preset's radius rather than a fixed one: a square-cornered
     * preset gives a square tile and a 24 dp one gives a tile a third as round as it is wide, which
     * is the difference the strip exists to show. Clamped at half the tile's height, past which a
     * rounded rectangle is a stadium and the radius stops reading as a number.
     */
    public static float tileCornerPx(float density, int radiusDp) {
        return Math.min(Math.max(0, radiusDp), MAX_TILE_CORNER_DP) * Math.max(0.01f, density);
    }

    /**
     * Where the surface sits on the tile, as {left, top, right, bottom} pixel insets.
     *
     * <p>The trailing edge is never inset: the tile is a crop, and the surface runs off the side of
     * it. The leading and bottom edges carry the preset's margin while Floating and nothing at all
     * while Docked, which is the rule the real render path applies — a docked surface is flush with
     * the screen's edges by definition.
     */
    public static int[] surfaceInsets(int widthPx, int heightPx, float density, int marginDp,
                                      boolean floating) {
        int band = Math.min(px(WALLPAPER_BAND_DP, density), Math.max(0, heightPx / 2));
        int margin = floating ? px(Math.max(0, marginDp), density) : 0;
        margin = Math.min(margin, Math.max(0, Math.min(widthPx / 2, (heightPx - band) / 2)));
        return new int[] {margin, band, 0, margin};
    }

    /**
     * The surface's four corners on the tile, as {leading-top, trailing-top, trailing-bottom,
     * leading-bottom} pixel radii.
     *
     * <p>Two formulas, not one. Floating is already a card: every corner it shows is its own.
     * Docked is square at rest and flush at the bottom, so only the edge it does not touch rounds.
     * The trailing corners are always square because the surface runs off that side of the crop.
     */
    public static float[] surfaceCornerRadiiPx(float density, int radiusDp, boolean floating) {
        float radius = Math.max(0, radiusDp) * Math.max(0.01f, density);
        return floating
            ? new float[] {radius, 0f, 0f, radius}
            : new float[] {radius, 0f, 0f, 0f};
    }

    /**
     * How small the crop is sampled to before being drawn back at full size, which is how the
     * preset's blur is shown at this size. A blur of 0 samples at full size and is not blurred at
     * all; the strongest blur either editor offers (30 dp) samples at about a seventh.
     */
    public static int backdropSamplePx(int sidePx, float density, int blurDp) {
        float divisor = 1f + (Math.max(0, blurDp) / 5f);
        return Math.max(1, Math.round(Math.max(1, sidePx) / divisor));
    }

    /** The whole strip's width: five tiles and the gaps between them. */
    public static int stripWidthPx(int tileCount, int gapPx, float density) {
        int tiles = Math.max(0, tileCount);
        return (tiles * px(CARD_WIDTH_DP, density)) + (Math.max(0, tiles - 1) * gapPx);
    }
}
