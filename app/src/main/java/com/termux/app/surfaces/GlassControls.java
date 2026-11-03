package com.termux.app.surfaces;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.FrostStops;

/**
 * The Custom row's Glass slider, as pure functions.
 *
 * <p>Glass runs 0 to 100, clear to frosted, and owns blur and grain together:
 * {@code blur = R(4 + 32 t^1.5)} dp and {@code grain = R(2 + 6 t)} percent, with
 * {@code t = Glass / 100} and {@code R} rounding half up. Blur grows slowly at the clear end and
 * fast at the frosted one, which is where the eye tells blur apart; grain follows in step so a
 * frosted face keeps its tooth and never reaches the sand above about 40. The shipped Looks sit on
 * the curve: Clear at {@link #CLEAR}, Tint at {@link #TINT}, Mist at {@link #MIST}. Solid's
 * blur 0 and grain 0 are Glass 0 itself (no frost at all, the cheapest glass, and with Opacity 0 a
 * fully transparent surface); position 1 is where the curve starts, at blur 4 and grain 2.</p>
 *
 * <p>The storage keeps blur and grain, which every renderer reads, and the position the slider
 * was left at, which it reads back while the pair is still that position's ({@link #readBack});
 * otherwise the slider stands at the position whose pair is nearest, squared differences summed,
 * the lower position on a tie ({@link #glassFor}). Reading never writes.</p>
 */
public final class GlassControls {

    private GlassControls() {}

    /** The Glass slider's range. */
    public static final int GLASS_MIN = FrostStops.POSITION_MIN;
    public static final int GLASS_MAX = FrostStops.POSITION_MAX;

    /** The shipped Looks' Glass positions. */
    public static final int CLEAR = FrostStops.positionOf(FrostStops.CLEAR);
    public static final int TINT = FrostStops.positionOf(FrostStops.TINT);
    public static final int MIST = FrostStops.positionOf(FrostStops.MIST);

    /** The blur a Glass position writes, in dp: {@code R(4 + 32 t^1.5)}, 4 to 36; 0 at Glass 0. */
    public static int blurForGlass(int glass) {
        return FrostStops.blurDpAt(glass);
    }

    /** The grain a Glass position writes, in percent: {@code R(2 + 6 t)}, 2 to 8; 0 at Glass 0. */
    public static int grainForGlass(int glass) {
        return FrostStops.grainPercentAt(glass);
    }

    /**
     * Where Glass stands for a stored blur and grain: the position whose pair is nearest, the
     * lower one on a tie. Several neighbouring positions round to one pair; where a shipped Look's
     * position is among them it wins, so a Look's stop reads as the Glass it was written from
     * (Clear 20, not 19). Which position of a tie is read never changes the pair.
     */
    public static int glassFor(int blur, int grain) {
        int best = GLASS_MIN;
        long bestError = Long.MAX_VALUE;
        for (int g = GLASS_MIN; g <= GLASS_MAX; g++) {
            long error = error(g, blur, grain);
            if (error < bestError) {
                bestError = error;
                best = g;
            }
        }
        for (int shipped : new int[] {CLEAR, TINT, MIST}) {
            if (shipped > best && error(shipped, blur, grain) == bestError) return shipped;
        }
        return best;
    }

    private static long error(int glass, int blur, int grain) {
        long db = blurForGlass(glass) - blur;
        long dg = grainForGlass(glass) - grain;
        return db * db + dg * dg;
    }

    /**
     * Where Glass stands: {@code stored}, the position the slider was left at, while the stored
     * pair is still the one it writes, so a release at 5 reads 5 even where 0 to 7 write the same
     * pair; else, with none stored or a pair something else wrote (a Look, wallpaper mode, an
     * older build), the nearest position ({@link #glassFor}).
     */
    public static int readBack(int stored, int blur, int grain) {
        if (stored >= GLASS_MIN && stored <= GLASS_MAX && blurForGlass(stored) == blur
            && grainForGlass(stored) == grain)
            return stored;
        return glassFor(blur, grain);
    }

    /** Whether a stored pair is one some Glass position writes. */
    public static boolean onCurve(int blur, int grain) {
        int g = glassFor(blur, grain);
        return blurForGlass(g) == blur && grainForGlass(g) == grain;
    }

    /**
     * The pair a stored one is migrated to: itself where blur is 0 (no frost, which Glass 0
     * writes only with grain 0; a hand-set grain over no blur is kept), else the nearest Glass
     * position's pair.
     *
     * @return {@code {blur, grain}}
     */
    @NonNull
    public static int[] snapped(int blur, int grain) {
        if (blur <= 0) return new int[] {blur, grain};
        int g = glassFor(blur, grain);
        return new int[] {blurForGlass(g), grainForGlass(g)};
    }

    /**
     * The curve's blur nearest {@code blur} among the positions that write {@code grain}, the
     * lower on a tie: for a blur that is put back on its own over a grain already on the curve
     * (wallpaper mode's remembered blur), so the pair it makes is on the curve. Where no position
     * writes that grain, the nearest pair's blur; no blur stays no blur.
     */
    public static int blurWithGrain(int blur, int grain) {
        if (blur <= 0) return blur;
        int best = -1;
        int bestError = Integer.MAX_VALUE;
        // Position 0 is no blur: a blur that is wanted never lands on it.
        for (int g = GLASS_MIN + 1; g <= GLASS_MAX; g++) {
            if (grainForGlass(g) != grain) continue;
            int error = Math.abs(blurForGlass(g) - blur);
            if (error < bestError) {
                bestError = error;
                best = g;
            }
        }
        return best >= 0 ? blurForGlass(best) : snapped(blur, grain)[0];
    }
}
