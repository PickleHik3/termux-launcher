package com.termux.app.dock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromeSpec;
import com.termux.app.place.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dock rows: the one adapter between the activity and the dock's accessory views (ADR 0011). Handed
 * the chrome spec and the applied DockLayout, it writes which of the views show (the stack, the
 * dock's glass host and its sheet, the pinned apps' pager and their page band, the A–Z row and its
 * effect layers, the extra keys, the bands under the keyboard) and how tall the rows are, and it
 * answers whether an Element's row is on screen and which design dresses the rows shown.
 *
 * <p>Nothing else reads the views' visibility to learn this: a reader asks here, or subscribes
 * ({@link Listener}) and is told once per change. Subscribers are told before the views are
 * written, so a drag on the pinned apps is cancelled while its source is still on screen.</p>
 *
 * <p>The design is the applied layout's ({@link DockLayout#surface}), which Dock geometry measured
 * for the half whose rows the frame shows. The rows never learn which half is committed, so a
 * crossing that stands the other half's rows is dressed as that half from its first frame.</p>
 *
 * <p>The Android views are the production adapter ({@link Views}); tests hand a fake.</p>
 */
public final class DockRows {

    /** What stands in the pinned apps' page band. */
    public enum PageBand {
        /** No apps row on the dock: the band goes with it. */
        GONE,
        /**
         * An apps row with no ticks of its own bound yet: the band is kept, empty, since the dock's
         * height counts it ({@link DockLayout#combinedHeight}) and a gone band would be a gap.
         */
        HELD,
        /** The row's own ticks draw the band; whether anything shows in it is the row's answer. */
        TICKS
    }

    /** The dock's accessory views, as this adapter writes them. */
    public interface Views {
        /** The whole accessory stack: the dock, the keyboard, a status bar along the bottom. */
        void setStackShown(boolean shown);
        /** The dock's glass host, behind its rows. */
        void setSurfaceShown(boolean shown);
        /** The dock's own sheet of glass inside that host. */
        void setDockGlassShown(boolean shown);
        /** The pager of {@link Element#APPS}, the row of {@link Element#AZ}, or the extra keys. */
        void setRowShown(@NonNull Element row, boolean shown);
        /** The pinned apps' page band; {@link PageBand#TICKS} hands it to the row's ticks. */
        void setPageBand(@NonNull PageBand band);
        /** The A–Z row's effect and label layers, which rest hidden. */
        void setAzLayersShown(boolean shown);
        /** The stack under the keyboard; answers whether it moved. */
        boolean setUnderKeyboardShown(boolean shown);
        /** The height of {@link Element#APPS}' pager or {@link Element#AZ}'s row; whether it moved. */
        boolean setRowHeight(@NonNull Element row, int px);
        /** The pinned apps' page band's height; whether it moved. */
        boolean setPageBandHeight(int px);
        /** Whether the ticks the pinned apps feed are the dock's own band (not another edge's). */
        boolean pageTicksAreTheDocks();
    }

    /** Told when what the dock shows changes. */
    public interface Listener {
        void onRowsChanged(@NonNull Shown shown);
    }

    /** What the dock shows, as a value: one per pass, compared to tell a change. */
    public static final class Shown {
        public final boolean stack;
        /** Whether the dock's glass host is up (the dock has rows to stand). */
        public final boolean surfaceHost;
        public final boolean dockGlass;
        public final boolean apps;
        public final boolean az;
        public final boolean extraKeys;
        public final boolean underKeyboard;
        /** A status bar standing along the bottom, in the stack beside the dock's rows. */
        public final boolean statusOnStack;
        @NonNull public final PageBand pageBand;
        /** The design the shown rows are dressed in; null before a layout was applied. */
        @Nullable public final DockSurfaceSpec surface;

        Shown(boolean stack, boolean surfaceHost, boolean dockGlass, boolean apps, boolean az,
              boolean extraKeys, boolean underKeyboard, boolean statusOnStack,
              @NonNull PageBand pageBand, @Nullable DockSurfaceSpec surface) {
            this.stack = stack;
            this.surfaceHost = surfaceHost;
            this.dockGlass = dockGlass;
            this.apps = apps;
            this.az = az;
            this.extraKeys = extraKeys;
            this.underKeyboard = underKeyboard;
            this.statusOnStack = statusOnStack;
            this.pageBand = pageBand;
            this.surface = surface;
        }

        /**
         * Whether {@code element}'s row is on screen in the dock's stack. An Element standing on
         * another edge has no row here.
         */
        public boolean rowOnScreen(@NonNull Element element) {
            switch (element) {
                case APPS: return apps;
                case AZ: return az;
                case EXTRA_KEYS: return extraKeys;
                case STATUS: return statusOnStack;
                default: return false;
            }
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) return true;
            if (!(other instanceof Shown)) return false;
            Shown that = (Shown) other;
            return stack == that.stack && surfaceHost == that.surfaceHost
                && dockGlass == that.dockGlass && apps == that.apps && az == that.az
                && extraKeys == that.extraKeys && underKeyboard == that.underKeyboard
                && statusOnStack == that.statusOnStack && pageBand == that.pageBand
                && surface == that.surface;
        }

        @Override
        public int hashCode() {
            return Objects.hash(stack, surfaceHost, dockGlass, apps, az, extraKeys, underKeyboard,
                statusOnStack, pageBand, surface == null ? null : surface.name);
        }
    }

    @NonNull private final Views mViews;
    @NonNull private final List<Listener> mListeners = new ArrayList<>();
    @NonNull private Shown mShown;
    @Nullable private DockLayout mLayout;

    public DockRows(@NonNull Views views) {
        mViews = views;
        mShown = new Shown(false, false, false, false, false, false, false, false,
            PageBand.GONE, null);
    }

    public void addListener(@NonNull Listener listener) {
        if (!mListeners.contains(listener)) mListeners.add(listener);
    }

    public void removeListener(@NonNull Listener listener) {
        mListeners.remove(listener);
    }

    /**
     * What shows for {@code chrome}: the dock's rows while it has any, the stack while the dock,
     * the keyboard or a status bar along the bottom stands in it. Where the navigation strip
     * carries the dock's glass ({@code glassOnNavStrip}) the dock's own sheet stands down.
     */
    @NonNull
    public static Shown presence(@NonNull ChromeSpec chrome, boolean bottomStatusBar,
                                 boolean glassOnNavStrip, boolean underKeyboard,
                                 boolean ticksAreTheDocks, @Nullable DockSurfaceSpec surface) {
        boolean dock = chrome.toolbarShown;
        boolean apps = dock && chrome.appsRowEnabled;
        PageBand band = !apps ? PageBand.GONE : ticksAreTheDocks ? PageBand.TICKS : PageBand.HELD;
        return new Shown(
            stackShown(dock, chrome.keyboardShown, bottomStatusBar),
            dock,
            dock && !glassOnNavStrip,
            apps,
            dock && chrome.azRowEnabled,
            dock && chrome.extraKeysRowEnabled,
            underKeyboard,
            bottomStatusBar,
            band,
            surface);
    }

    /**
     * Whether the accessory stack is on screen. Since the whole bottom edge stands in it, a status
     * bar the place put along the bottom keeps it up on its own — the dock's rows can all be
     * hidden or standing on other edges and the bar still has to be drawn.
     */
    public static boolean stackShown(boolean dockShown, boolean keyboardShown,
                                     boolean bottomStatusBar) {
        return dockShown || keyboardShown || bottomStatusBar;
    }

    /**
     * One chrome pass: the views shown as {@code chrome} says, written every pass (some are
     * shared — the letters' slot with the keybind strip, the band with the row's ticks — and the
     * pass has always put them back), and the subscribers told first when anything changed.
     */
    public void apply(@NonNull ChromeSpec chrome, boolean bottomStatusBar,
                      boolean glassOnNavStrip) {
        Shown shown = presence(chrome, bottomStatusBar, glassOnNavStrip, mShown.underKeyboard,
            mViews.pageTicksAreTheDocks(), surfaceOf(mLayout));
        announce(shown);
        mViews.setStackShown(shown.stack);
        mViews.setDockGlassShown(shown.dockGlass);
        mViews.setSurfaceShown(shown.surfaceHost);
        mViews.setRowShown(Element.APPS, shown.apps);
        mViews.setPageBand(shown.pageBand);
        mViews.setRowShown(Element.EXTRA_KEYS, shown.extraKeys);
        mViews.setRowShown(Element.AZ, shown.az);
        mViews.setAzLayersShown(false);
    }

    /**
     * The geometry pass's applied layout: the rows' heights, and the design the shown rows wear.
     *
     * @return whether a height moved
     */
    public boolean applyLayout(@NonNull DockLayout layout) {
        mLayout = layout;
        Shown shown = mShown;
        if (shown.surface != layout.surface) {
            announce(new Shown(shown.stack, shown.surfaceHost, shown.dockGlass, shown.apps,
                shown.az, shown.extraKeys, shown.underKeyboard, shown.statusOnStack,
                shown.pageBand, layout.surface));
        }
        boolean moved = mViews.setRowHeight(Element.APPS, layout.appsBarHeightPx);
        moved |= mViews.setPageBandHeight(layout.indicatorBandHeightPx);
        moved |= mViews.setRowHeight(Element.AZ, layout.azRowHeightPx);
        return moved;
    }

    /**
     * Whether bands stand under the keyboard: the stack below it shows while it holds one.
     *
     * @return whether the stack's presence moved
     */
    public boolean setUnderKeyboardBands(boolean shown) {
        Shown now = mShown;
        if (now.underKeyboard != shown) {
            announce(new Shown(now.stack, now.surfaceHost, now.dockGlass, now.apps, now.az,
                now.extraKeys, shown, now.statusOnStack, now.pageBand, now.surface));
        }
        return mViews.setUnderKeyboardShown(shown);
    }

    /** Whether {@code element}'s row is on screen in the dock's stack. */
    public boolean isRowOnScreen(@NonNull Element element) {
        return mShown.rowOnScreen(element);
    }

    /** Whether the accessory stack is up. */
    public boolean stackShown() {
        return mShown.stack;
    }

    /** Whether the dock's glass host is up. */
    public boolean surfaceShown() {
        return mShown.surfaceHost;
    }

    /** Whether bands stand under the keyboard. */
    public boolean underKeyboardBandsShown() {
        return mShown.underKeyboard;
    }

    /**
     * The design the rows on screen are dressed in: the applied layout's, measured for the half
     * whose rows the frame shows. Null before a layout was applied.
     */
    @Nullable
    public DockSurfaceSpec surface() {
        return mShown.surface;
    }

    /** What the dock shows now. */
    @NonNull
    public Shown shown() {
        return mShown;
    }

    /** The layout last applied; null before one. */
    @Nullable
    public DockLayout layout() {
        return mLayout;
    }

    @Nullable
    private static DockSurfaceSpec surfaceOf(@Nullable DockLayout layout) {
        return layout == null ? null : layout.surface;
    }

    private void announce(@NonNull Shown shown) {
        if (shown.equals(mShown)) return;
        mShown = shown;
        for (Listener listener : new ArrayList<>(mListeners)) listener.onRowsChanged(shown);
    }
}
