package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/** Pure choices behind the wallpaper picker page, kept apart from views so they can be tested. */
public final class WallpaperPickerLogic {

    private WallpaperPickerLogic() {}

    /** Whether this tile is the stored background. */
    public static boolean isStored(@NonNull String tileId, @Nullable String storedId) {
        return tileId.equals(storedId);
    }

    /** Thumbnail width for a given height at the screen's portrait aspect, at least 1. */
    public static int thumbWidth(int thumbHeightPx, int screenW, int screenH) {
        int shortSide = Math.max(1, Math.min(screenW, screenH));
        int longSide = Math.max(1, Math.max(screenW, screenH));
        return Math.max(1, Math.round(thumbHeightPx * (shortSide / (float) longSide)));
    }

    /**
     * Whether two slot choices name the same thing. Null equals only null. Two photos are the same
     * when they name the same picture file (or neither names one).
     */
    public static boolean sameChoice(@Nullable WallpaperSlots.Choice a, @Nullable WallpaperSlots.Choice b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.sameAsHome != b.sameAsHome || a.photo != b.photo) return false;
        return !a.photo || samePicture(a.photoFile, b.photoFile);
    }

    private static boolean samePicture(@Nullable File a, @Nullable File b) {
        if (a == null || b == null) return a == b;
        return a.getAbsolutePath().equals(b.getAbsolutePath());
    }

    /** A photo that carries its picture: it can be applied to any slot with no new crop. */
    public static boolean photoWithPicture(@Nullable WallpaperSlots.Choice c) {
        return c != null && c.photo && c.photoFile != null;
    }

    /** A pending choice that differs from what is stored, while nothing is running. */
    public static boolean applyEnabled(@Nullable WallpaperSlots.Choice pending,
                                       @Nullable WallpaperSlots.Choice stored, boolean busy) {
        return !busy && pending != null && !sameChoice(pending, stored);
    }

    /**
     * The background the centred card shows, which Apply puts on Home (and so on Lock): its
     * pending choice, Lock's Same as Home resolved to Home's pending choice.
     */
    @NonNull
    public static WallpaperSlots.Choice primaryChoice(@NonNull WallpaperSlots.Slot centred,
                                                      @NonNull WallpaperSlots.Choice pendingHome,
                                                      @NonNull WallpaperSlots.Choice pendingLock) {
        WallpaperSlots.Choice c = centred == WallpaperSlots.Slot.HOME ? pendingHome : pendingLock;
        return c.sameAsHome ? pendingHome : c;
    }

    /**
     * What "Home screen only" or "Lock screen only" puts on {@code target}: the centred card's
     * background for Home; for Lock, the centred card's own pending choice (so Same as Home stays
     * Same as Home).
     */
    @NonNull
    public static WallpaperSlots.Choice slotOnlyChoice(@NonNull WallpaperSlots.Slot target,
                                                       @NonNull WallpaperSlots.Slot centred,
                                                       @NonNull WallpaperSlots.Choice pendingHome,
                                                       @NonNull WallpaperSlots.Choice pendingLock) {
        if (target == WallpaperSlots.Slot.HOME) return primaryChoice(centred, pendingHome, pendingLock);
        return centred == WallpaperSlots.Slot.LOCK ? pendingLock : pendingHome;
    }

    /**
     * Apply (both screens): enabled when putting {@code choice} on Home and Same as Home on Lock
     * changes either slot. A photo needs its picture to reach Home, unless Home holds it already.
     */
    public static boolean applyBothEnabled(@Nullable WallpaperSlots.Choice choice,
                                           @NonNull WallpaperSlots.Choice storedHome,
                                           @NonNull WallpaperSlots.Choice storedLock, boolean busy) {
        if (busy || choice == null || choice.sameAsHome) return false;
        if (choice.photo && !photoWithPicture(choice) && !storedHome.photo) return false;
        return homeChanges(choice, storedHome) || !storedLock.sameAsHome;
    }

    /** Whether Apply (both screens) writes the Home slot, rather than only pointing Lock at it. */
    public static boolean homeChanges(@NonNull WallpaperSlots.Choice choice,
                                      @NonNull WallpaperSlots.Choice storedHome) {
        return !sameChoice(choice, storedHome);
    }

    /**
     * A one-slot menu item: enabled when {@code choice} differs from that slot's stored one. A
     * photo needs its picture, and Home cannot follow itself.
     */
    public static boolean applyOneEnabled(@NonNull WallpaperSlots.Slot target,
                                          @Nullable WallpaperSlots.Choice choice,
                                          @Nullable WallpaperSlots.Choice stored, boolean busy) {
        if (busy || choice == null) return false;
        if (choice.photo && !photoWithPicture(choice)) return false;
        if (choice.sameAsHome && target == WallpaperSlots.Slot.HOME) return false;
        return !sameChoice(choice, stored);
    }

    /** The Same as Home tile leads the strip only while the Lock preview is centred. */
    public static boolean showsSameAsHome(@NonNull WallpaperSlots.Slot centred) {
        return centred == WallpaperSlots.Slot.LOCK;
    }

    /**
     * The lock preview's clock as the glyphs to draw: "9:05" in 12-hour time (no leading zero,
     * 12 for noon and midnight), "09:05" or "21:05" in 24-hour time. Only digits and ':'.
     */
    @NonNull
    public static String composedTime(int hourOfDay, int minute, boolean is24Hour) {
        int h = ((hourOfDay % 24) + 24) % 24;
        int m = ((minute % 60) + 60) % 60;
        String mm = (m < 10 ? "0" : "") + m;
        if (is24Hour) return (h < 10 ? "0" : "") + h + ":" + mm;
        int h12 = h % 12;
        if (h12 == 0) h12 = 12;
        return h12 + ":" + mm;
    }

    /** Milliseconds from {@code nowMs} to the start of the next minute, at least 1. */
    public static long millisToNextMinute(long nowMs) {
        long rem = 60_000L - (((nowMs % 60_000L) + 60_000L) % 60_000L);
        return Math.max(1L, rem);
    }

    /**
     * A preview card's size in a pager {@code pagerW} x {@code pagerH}: the pager's height less the
     * card's own vertical padding (twice) and the label standing over it ({@code labelBlockPx},
     * label and gap), at the overlay's aspect, narrowed to {@code maxWidthFraction} of the pager
     * where the height would make it wider. Returns {@code {width, height}}, at least 1 x 1.
     */
    public static int[] cardSize(int pagerW, int pagerH, int verticalPadPx, int labelBlockPx,
                                 float aspectW, float aspectH, float maxWidthFraction) {
        int cardH = Math.max(1, pagerH - 2 * verticalPadPx - labelBlockPx);
        int cardW = Math.round(cardH * aspectW / aspectH);
        int maxW = Math.round(pagerW * maxWidthFraction);
        if (cardW > maxW) {
            cardW = maxW;
            cardH = Math.round(cardW * aspectH / aspectW);
        }
        return new int[] {Math.max(1, cardW), Math.max(1, cardH)};
    }

    /**
     * How many tiles the strip holds: Same as Home (it shows for Lock only, but is always built)
     * and the recently applied photos, at most {@code maxRecents}. There are no other tiles.
     */
    public static int stripTileCount(int recentPhotos, int maxRecents) {
        return 1 + Math.max(0, Math.min(recentPhotos, maxRecents));
    }

    /**
     * The strip thumbnail size {width, height}: the photo's portrait aspect at {@code maxHeightPx},
     * shrunk (aspect kept) until {@code tileCount} equal cells across {@code availablePx} each hold
     * one with {@code minGapPx} to spare. Never clips; at least 1 px.
     */
    @NonNull
    public static int[] stripThumbSize(int availablePx, int tileCount, int screenShort, int screenLong,
                                       int maxHeightPx, int minGapPx) {
        int shortSide = Math.max(1, Math.min(screenShort, screenLong));
        int longSide = Math.max(1, Math.max(screenShort, screenLong));
        int natural = thumbWidth(maxHeightPx, shortSide, longSide);
        int w = natural;
        if (availablePx > 0 && tileCount > 0) {
            int cellW = availablePx / tileCount;
            w = Math.min(w, cellW - Math.max(0, minGapPx));
        }
        w = Math.max(1, w);
        // At the natural width the height is the budget itself: rounding the width and back
        // would otherwise lose a pixel.
        int h = w >= natural ? maxHeightPx
            : Math.max(1, Math.min(maxHeightPx, Math.round(w * (longSide / (float) shortSide))));
        return new int[] {w, h};
    }
}
