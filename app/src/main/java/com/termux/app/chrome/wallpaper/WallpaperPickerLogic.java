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
        if (a.photo && !samePicture(a.photoFile, b.photoFile)) return false;
        return a.animatedId == null ? b.animatedId == null : a.animatedId.equals(b.animatedId);
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

    /** The Motion toggle: Lock centred, and the lock live wallpaper exists on this API level. */
    public static boolean showsMotion(@NonNull WallpaperSlots.Slot centred, int sdkInt) {
        return centred == WallpaperSlots.Slot.LOCK && WallpaperSlots.lockLiveSupported(sdkInt);
    }

    /** Whether the Motion row has a place on the page at all (it keeps its height on Home). */
    public static boolean motionRowExists(int sdkInt) {
        return WallpaperSlots.lockLiveSupported(sdkInt);
    }

    /**
     * As above, on a page that may not offer the animated backgrounds (below API 34, or Fancier
     * Glass off): with no animated tiles there is nothing for Motion to play.
     */
    public static boolean motionRowExists(int sdkInt, boolean animatedOffered) {
        return animatedOffered && motionRowExists(sdkInt);
    }

    /** What the Motion row holds (living-stills.md, Part D.6). */
    public enum MotionRow {
        /** The Motion switch. */
        SWITCH,
        /** The switch's place, kept empty so the pager never jumps. */
        SWITCH_HIDDEN,
        /** The Bring to life button: a photo with no living still yet. */
        OFFER,
        /** The bar, the stage and a cancel: that photo is being analysed. */
        WORKING,
    }

    /** A slot choice that is a living still ({@code living:<hash>}). */
    public static boolean isLiving(@Nullable WallpaperSlots.Choice c) {
        return c != null && !c.photo && !c.sameAsHome && AnimatedWallpapers.isLivingId(c.animatedId);
    }

    /**
     * What the Motion row shows for the centred slot. A living still always has its switch. A photo
     * with its picture, where living stills are offered (API 34+, animated backgrounds on), has the
     * Bring to life button, or the working bar while that photo is being analysed. Anything else
     * keeps the page's older rule: the switch on Lock from API 34, an empty place on Home.
     *
     * @param livingOffered the page can build living stills at all
     * @param hasLiving     a photo choice whose photo already has one (the page adopts it as the choice)
     * @param working       the job is analysing this photo right now
     */
    @NonNull
    public static MotionRow motionRow(@NonNull WallpaperSlots.Slot centred, int sdkInt, boolean livingOffered,
                                      @NonNull WallpaperSlots.Choice shown, boolean hasLiving, boolean working) {
        if (isLiving(shown)) return MotionRow.SWITCH;
        if (livingOffered && photoWithPicture(shown)) {
            if (working) return MotionRow.WORKING;
            return hasLiving ? MotionRow.SWITCH : MotionRow.OFFER;
        }
        return showsMotion(centred, sdkInt) ? MotionRow.SWITCH : MotionRow.SWITCH_HIDDEN;
    }

    /**
     * The stage label's key: {@code depth}, {@code scene}, {@code subject}, {@code gemma} (the
     * recipe stage while Gemma is asked) or {@code recipe}.
     */
    @NonNull
    public static String stageKey(@NonNull String stage, boolean askingGemma) {
        return "recipe".equals(stage) && askingGemma ? "gemma" : stage;
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
        int w = thumbWidth(maxHeightPx, shortSide, longSide);
        if (availablePx > 0 && tileCount > 0) {
            int cellW = availablePx / tileCount;
            w = Math.min(w, cellW - Math.max(0, minGapPx));
        }
        w = Math.max(1, w);
        int h = Math.max(1, Math.min(maxHeightPx, Math.round(w * (longSide / (float) shortSide))));
        return new int[] {w, h};
    }
}
