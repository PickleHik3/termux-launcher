package com.termux.app.launcher.icon;

/**
 * How much artwork {@link LauncherIconStore} keeps per icon and in total, as a function of what
 * the screen needs. Pure: no Android types, so the numbers are pinned by a unit test.
 *
 * <p>The per-icon cap is the pixels the largest icon this device will draw needs
 * ({@code ceil(dp * density)}), never below {@link #BASE_PX} and never above {@link #CEILING_PX}.
 * 288 px is the framework's own system-density rasterisation (284) rounded up to a multiple of
 * 96, so the cap never asks for more than the loader holds, and it covers a 60dp icon up to a
 * density of 4.8. A normal phone (density up to 3.2) on Auto, Small or Medium needs at most
 * 54dp x 3.2 = 173 px and so keeps exactly {@link #BASE_PX}.</p>
 *
 * <p>The total budget is today's heap share ({@link HeapBudget}) while the cap is
 * {@link #BASE_PX}, and grows with the pixel area once the cap rises, so the store still holds as
 * many icons as it did, up to {@link #MAX_BYTES_CEILING}.</p>
 */
public final class IconArtworkPolicy {

    /** Today's per-icon cap, and the floor of every other. */
    public static final int BASE_PX = 192;
    /** The most artwork kept per icon: one icon costs 324 KB at this size. */
    public static final int CEILING_PX = 288;

    private static final int HEAP_DIVISOR = 16;
    private static final int MIN_BYTES = 4 * 1024 * 1024;
    /** The heap share's ceiling while the cap is {@link #BASE_PX}. */
    public static final int BASE_MAX_BYTES = 16 * 1024 * 1024;
    /** The ceiling once the cap has raised the budget. */
    public static final int MAX_BYTES_CEILING = 24 * 1024 * 1024;

    private IconArtworkPolicy() {
    }

    /** The per-icon cap in pixels for icons drawn at most {@code largestIconDp} on {@code density}. */
    public static int capPx(float density, float largestIconDp) {
        if (!(density > 0f) || !(largestIconDp > 0f)) return BASE_PX;
        int needed = (int) Math.ceil(largestIconDp * density - 1e-3f);
        return Math.max(BASE_PX, Math.min(CEILING_PX, needed));
    }

    /**
     * The total budget for artwork kept at {@code capPx}: today's heap share at {@link #BASE_PX},
     * scaled by the pixel area above it, never past {@link #MAX_BYTES_CEILING}.
     */
    public static int budgetBytes(int memoryClassMb, int capPx) {
        int base = HeapBudget.of(memoryClassMb, HEAP_DIVISOR, MIN_BYTES, BASE_MAX_BYTES);
        int cap = Math.max(BASE_PX, capPx);
        if (cap == BASE_PX) return base;
        long scaled = (long) base * cap * cap / ((long) BASE_PX * BASE_PX);
        return (int) Math.min(MAX_BYTES_CEILING, scaled);
    }
}
