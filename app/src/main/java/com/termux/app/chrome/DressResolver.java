package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.dock.DockStyle;
import com.termux.app.place.Element;
import com.termux.app.place.LayoutVariant;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.WallHalfShape;
import com.termux.app.wall.PaneWallPage;

import java.util.EnumSet;
import java.util.Set;

/**
 * The one answer to what a page of the wall wears ({@link Dress}), so "does Home wear this?" is
 * asked here rather than as a page check per surface (ADR 0009, "Deepenings").
 *
 * <p>Today it dresses the halves as ADR 0008 left them. A <b>Home page</b> wears the Home layout
 * ({@link LayoutVariant#HOME}), whatever minimal mode says, less the status bar and the extra
 * keys, in Home's own Style, Corners and Margin and the Dock design picked for it. A
 * <b>window</b> wears the Workspace's layout whole, the minimal one while minimal mode is on,
 * in the Workspace's Style, Corners and Margin, its dock the tray it has always been.
 *
 * <p>Pure over a {@link Source}, so every answer is read and tested without a window; {@link
 * #of} reads the launcher's store.
 */
public final class DressResolver {

    /** What the store holds that a dress is made of, read each time a dress is asked for. */
    public interface Source {
        /** Whether the launcher is in minimal mode. */
        boolean minimal();

        /** The Dock design picked for Home, whether or not it is built yet. */
        @NonNull
        DockStyle homeDockStyle();

        /** One half's Style, Corners and Margin as stored: Home's, or the Workspace's. */
        @NonNull
        WallHalfShape shape(boolean home);
    }

    /** What the Home layout leaves out: the status bar and the extra keys are the Workspace's alone. */
    @NonNull private static final Set<Element> HOME_LEAVES_OUT =
        EnumSet.of(Element.STATUS, Element.EXTRA_KEYS);

    @NonNull private static final Set<Element> NONE_LEFT_OUT = EnumSet.noneOf(Element.class);

    @NonNull private final Source mSource;

    public DressResolver(@NonNull Source source) {
        mSource = source;
    }

    /** The resolver over the launcher's store, whichever variant's view of it is passed. */
    @NonNull
    public static DressResolver of(@NonNull PlaceLayoutStore places) {
        return new DressResolver(new Source() {
            @Override public boolean minimal() {
                return places.isMinimal();
            }

            @NonNull @Override public DockStyle homeDockStyle() {
                return places.dockStyle();
            }

            @NonNull @Override public WallHalfShape shape(boolean home) {
                return places.halfShape(home);
            }
        });
    }

    /** What the page wears, as the store stands now. */
    @NonNull
    public Dress dressOf(@NonNull PaneWallPage page) {
        boolean home = page == PaneWallPage.WIDGETS;
        LayoutVariant layout = layoutOf(page);
        return new Dress(layout, home ? HOME_LEAVES_OUT : NONE_LEFT_OUT,
            dockDesignOn(layout, home ? mSource.homeDockStyle() : null), picksDockDesign(layout),
            mSource.shape(home));
    }

    /**
     * The layout the page wears ({@link Dress#layout}), read without the rest of the dress: the
     * question the stack's sizes ask on every pass.
     */
    @NonNull
    public LayoutVariant layoutOf(@NonNull PaneWallPage page) {
        return page == PaneWallPage.WIDGETS ? LayoutVariant.HOME
            : LayoutVariant.of(mSource.minimal());
    }

    /**
     * The Dock design the page draws ({@link Dress#dockDesign}), read without the rest of the
     * dress: the question the seam's bead melt asks on every frame of a slide.
     */
    @NonNull
    public DockStyle dockDesignOf(@NonNull PaneWallPage page) {
        return dockDesignOn(layoutOf(page),
            page == PaneWallPage.WIDGETS ? mSource.homeDockStyle() : null);
    }

    /**
     * Whether a page on {@code layout} wears {@code element} ({@link Dress#wears}), for a reader
     * that holds a layout rather than a page: the Layout editor's offers, the store's resolve.
     */
    public static boolean wornOn(@NonNull LayoutVariant layout, @NonNull Element element) {
        return layout != LayoutVariant.HOME || !HOME_LEAVES_OUT.contains(element);
    }

    /** Whether {@code layout} holds a Dock design the user picks ({@link Dress#picksDockDesign}). */
    public static boolean picksDockDesign(@NonNull LayoutVariant layout) {
        return layout == LayoutVariant.HOME;
    }

    /**
     * The Dock design a page on {@code layout} draws ({@link Dress#dockDesign}): the one picked,
     * as it draws today ({@link DockStyle#effectiveStyle}), where the layout holds one, and the
     * tray where it does not.
     */
    @NonNull
    public static DockStyle dockDesignOn(@NonNull LayoutVariant layout,
                                         @Nullable DockStyle picked) {
        return picksDockDesign(layout) ? DockStyle.effectiveStyle(picked) : DockStyle.GLASS_TRAY;
    }

    /**
     * Whether the two pages wear the same dress, so the chrome need not change between them:
     * any two windows do; Home and a window, each on a layout of its own, never do.
     */
    public boolean dressAlike(@NonNull PaneWallPage a, @NonNull PaneWallPage b) {
        return dressOf(a).equals(dressOf(b));
    }
}
