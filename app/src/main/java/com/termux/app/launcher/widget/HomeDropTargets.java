package com.termux.app.launcher.widget;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Remove and Uninstall targets a Home icon or folder can be dropped on while it is dragged:
 * which of them show, where they sit in the band at the top of the screen, and which one a finger
 * is over. Launcher3's {@code DeleteDropTarget} and {@code SecondaryDropTarget}: Remove always,
 * Uninstall only for an app that can be uninstalled. Pure, so all of it is testable without a view;
 * every coordinate is on screen.
 */
public final class HomeDropTargets {

    public enum Target {
        /** Takes the item off Home: an app's icon, or a folder (its apps are not uninstalled). */
        REMOVE,
        /** The system's uninstall flow for the app; the icon goes back to its cell. */
        UNINSTALL
    }

    /** How tall a target's pill is, at most: the band may be shorter, and then the pill is. */
    static final int PILL_HEIGHT_DP = 40;
    /** The air between two pills. */
    static final int GAP_DP = 12;
    /** The pill's padding at each end, and between its glyph and its label. */
    static final int PAD_DP = 16;
    static final int GLYPH_GAP_DP = 8;
    static final int GLYPH_DP = 18;
    /** How far below the system bar the band reaches when there is no status chrome along the top. */
    static final int FALLBACK_BAND_DP = 56;

    /** A rectangle on screen; Android's own would need a device to be tested. */
    public static final class Box {
        public final int left, top, right, bottom;

        public Box(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public int width() { return right - left; }
        public int height() { return bottom - top; }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Box)) return false;
            Box box = (Box) other;
            return left == box.left && top == box.top && right == box.right && bottom == box.bottom;
        }

        @Override public int hashCode() {
            return ((left * 31 + top) * 31 + right) * 31 + bottom;
        }

        @NonNull @Override public String toString() {
            return "Box(" + left + ", " + top + ", " + right + ", " + bottom + ")";
        }
    }

    /** Where the pills are drawn, and the area each one answers to, in the order of the targets. */
    public static final class Layout {
        @NonNull public final List<Box> pills;
        @NonNull public final List<Box> hits;

        Layout(@NonNull List<Box> pills, @NonNull List<Box> hits) {
            this.pills = Collections.unmodifiableList(pills);
            this.hits = Collections.unmodifiableList(hits);
        }
    }

    private HomeDropTargets() {}

    /** The targets a dragged item shows, left to right: Remove, then Uninstall when it applies. */
    @NonNull
    public static List<Target> targetsFor(boolean folder, boolean uninstallable) {
        List<Target> targets = new ArrayList<>(2);
        targets.add(Target.REMOVE);
        if (!folder && uninstallable) targets.add(Target.UNINSTALL);
        return targets;
    }

    /**
     * Whether an app on Home can be uninstalled from here: an Android app of this profile (not a
     * Linux app, one of the launcher's own entries, or a work or cloned profile's app) that is
     * installed, is not part of the system image, and is not this launcher.
     */
    public static boolean canUninstall(boolean androidPackage, boolean installed,
                                       boolean systemApp, boolean ownPackage) {
        return androidPackage && installed && !systemApp && !ownPackage;
    }

    /**
     * The band when there is no status chrome along the top to put the targets in: the system
     * bar's inset and {@link #FALLBACK_BAND_DP} under it, across the window.
     */
    @NonNull
    public static Box fallbackBand(int windowLeft, int windowTop, int windowWidth, int insetTopPx,
                                   float density) {
        int bottom = windowTop + Math.max(0, insetTopPx) + Math.round(FALLBACK_BAND_DP * density);
        return new Box(windowLeft, windowTop, windowLeft + windowWidth, bottom);
    }

    /**
     * Lays the pills out in {@code band}: each as wide as its content (capped to its share of the
     * band), {@code pillHeightPx} tall or the band's height if that is less, centred in the band
     * as a row with {@code gapPx} between them. The band is split between the targets at the
     * middle of each gap, and each target answers for its share from the band's foot all the way
     * up, so a drag flung to the top of the screen still lands on one.
     */
    @NonNull
    public static Layout layout(@NonNull Box band, @NonNull int[] contentWidths, int pillHeightPx,
                                int gapPx) {
        int count = contentWidths.length;
        List<Box> pills = new ArrayList<>(count);
        List<Box> hits = new ArrayList<>(count);
        if (count == 0 || band.width() <= 0) return new Layout(pills, hits);
        int gap = Math.max(0, gapPx);
        int share = Math.max(1, (band.width() - gap * (count - 1)) / count);
        int[] widths = new int[count];
        int total = gap * (count - 1);
        for (int i = 0; i < count; i++) {
            widths[i] = Math.max(1, Math.min(share, contentWidths[i]));
            total += widths[i];
        }
        int height = Math.max(1, Math.min(pillHeightPx, band.height()));
        int top = band.top + (band.height() - height) / 2;
        int left = band.left + (band.width() - total) / 2;
        for (int i = 0; i < count; i++) {
            pills.add(new Box(left, top, left + widths[i], top + height));
            left += widths[i] + gap;
        }
        for (int i = 0; i < count; i++) {
            int hitLeft = i == 0 ? band.left : (pills.get(i - 1).right + pills.get(i).left) / 2;
            int hitRight = i == count - 1 ? band.right
                : (pills.get(i).right + pills.get(i + 1).left) / 2;
            hits.add(new Box(hitLeft, Integer.MIN_VALUE, hitRight, band.bottom));
        }
        return new Layout(pills, hits);
    }

    /** The index of the target a finger at ({@code x}, {@code y}) is over, or -1 for none. */
    public static int hit(@NonNull Layout layout, float x, float y) {
        for (int i = 0; i < layout.hits.size(); i++) {
            Box box = layout.hits.get(i);
            if (x >= box.left && x < box.right && y < box.bottom) return i;
        }
        return -1;
    }

    /** A pill's width for a label {@code labelPx} wide: padding, the glyph, its gap and the label. */
    public static int contentWidth(int labelPx, float density) {
        return Math.round((2 * PAD_DP + GLYPH_DP + GLYPH_GAP_DP) * density) + Math.max(0, labelPx);
    }
}
