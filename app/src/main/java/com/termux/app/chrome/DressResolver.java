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
 * <p>One layout dresses both halves (ADR 0009, build step 1). A <b>window</b> wears the
 * Workspace's layout whole, the minimal one while minimal mode is on (minimal mode is the
 * windows' alone), in the Workspace's Style,
 * Corners and Margin, its dock in the Dock design that layout holds (#93; the minimal layout
 * holds its own). A <b>Home page</b> wears the normal layout less the extra keys, in the same Style,
 * Corners and Margin, its dock the Dock design that layout holds, unless Home holds a <b>Home
 * layout</b> of its own: then it wears that ({@link LayoutVariant#HOME}), whatever minimal mode
 * says, in Home's own Style, Corners and Margin and the design picked for it. Either way a Home
 * page wears the status bar its layout places (#90): one bar across the wall.
 *
 * <p>Pure over a {@link Source}, so every answer is read and tested without a window; {@link
 * #of} reads the launcher's store.
 */
public final class DressResolver {

    /** What the store holds that a dress is made of, read each time a dress is asked for. */
    public interface Source {
        /** Whether the launcher is in minimal mode. */
        boolean minimal();

        /** Whether Home holds a Home layout of its own (the Home layout switch). */
        boolean homeLayout();

        /** The Dock design picked on one layout, whether or not it is built yet. */
        @NonNull
        DockStyle dockDesign(@NonNull LayoutVariant layout);

        /** Whether one layout turns the Search field on, beside its design (#96). */
        boolean searchField(@NonNull LayoutVariant layout);

        /** Whether one layout turns the Drawer tile on, beside its design (#97). */
        boolean drawerTile(@NonNull LayoutVariant layout);

        /** One half's Style, Corners and Margin as stored: Home's own, or the Workspace's. */
        @NonNull
        WallHalfShape shape(boolean home);
    }

    /**
     * What a Home page leaves out, whichever layout it wears: the extra keys are the Workspace's
     * alone. The status bar is worn on both halves (#90).
     */
    @NonNull private static final Set<Element> HOME_LEAVES_OUT = EnumSet.of(Element.EXTRA_KEYS);

    @NonNull private static final Set<Element> NONE_LEFT_OUT = EnumSet.noneOf(Element.class);

    @NonNull private final Source mSource;

    public DressResolver(@NonNull Source source) {
        mSource = source;
    }

    /** The resolver over the launcher's store, whichever variant's view of it is passed. */
    @NonNull
    public static DressResolver of(@NonNull PlaceLayoutStore places) {
        return over(() -> places);
    }

    /**
     * The resolver over whichever store {@code places} answers each time a dress is asked for:
     * the activity's, which it builds once the preferences are read and builds again when they
     * are. While it answers null, the dress of a store with nothing in it.
     */
    @NonNull
    public static DressResolver over(
            @NonNull java.util.function.Supplier<PlaceLayoutStore> places) {
        return new DressResolver(new Source() {
            @Override public boolean minimal() {
                PlaceLayoutStore store = places.get();
                return store != null && store.isMinimal();
            }

            @Override public boolean homeLayout() {
                PlaceLayoutStore store = places.get();
                return store != null && store.hasHomeLayout();
            }

            @NonNull @Override public DockStyle dockDesign(@NonNull LayoutVariant layout) {
                PlaceLayoutStore store = places.get();
                return store != null ? store.forVariant(layout).dockStyle() : DockStyle.GLASS_TRAY;
            }

            @Override public boolean searchField(@NonNull LayoutVariant layout) {
                PlaceLayoutStore store = places.get();
                return store != null && store.forVariant(layout).searchField();
            }

            @Override public boolean drawerTile(@NonNull LayoutVariant layout) {
                PlaceLayoutStore store = places.get();
                return store != null && store.forVariant(layout).drawerTile();
            }

            @NonNull @Override public WallHalfShape shape(boolean home) {
                PlaceLayoutStore store = places.get();
                return store != null ? store.halfShape(home) : WallHalfShape.read(null, home);
            }
        });
    }

    /**
     * What the page wears, as the store stands now. Builds a value and reads the store: asked
     * where the chrome is arranged or settles, never per frame ({@link #layoutOf} and {@link
     * #dockDesignOf} are the narrow reads for that).
     */
    @NonNull
    public Dress dressOf(@NonNull PaneWallPage page) {
        boolean home = page == PaneWallPage.WIDGETS;
        LayoutVariant layout = layoutOf(page);
        return new Dress(layout, home ? HOME_LEAVES_OUT : NONE_LEFT_OUT,
            DockStyle.effectiveStyle(mSource.dockDesign(layout)), mSource.searchField(layout),
            mSource.drawerTile(layout), mSource.shape(home && layout == LayoutVariant.HOME));
    }

    /**
     * The layout the page wears ({@link Dress#layout}), read without the rest of the dress: the
     * question the stack's sizes ask on every pass. Home's own only while it holds one, else the
     * normal layout: minimal mode is the windows' alone.
     */
    @NonNull
    public LayoutVariant layoutOf(@NonNull PaneWallPage page) {
        if (page == PaneWallPage.WIDGETS) {
            return mSource.homeLayout() ? LayoutVariant.HOME : LayoutVariant.NORMAL;
        }
        return LayoutVariant.of(mSource.minimal());
    }

    /**
     * The Dock design the page draws ({@link Dress#dockDesign}), read without the rest of the
     * dress: the question the dock's surface asks wherever its glass is built, a key's lens
     * rebuilding it per frame included. The design the page's layout holds, as it draws.
     */
    @NonNull
    public DockStyle dockDesignOf(@NonNull PaneWallPage page) {
        return DockStyle.effectiveStyle(mSource.dockDesign(layoutOf(page)));
    }

    /**
     * Whether the page's dock carries the Search field ({@link Dress#searchField}), read without
     * the rest of the dress, as {@link #dockDesignOf} is: the dock's pass asks it.
     */
    public boolean searchFieldOf(@NonNull PaneWallPage page) {
        return mSource.searchField(layoutOf(page));
    }

    /**
     * Whether the page's dock carries the Drawer tile ({@link Dress#drawerTile}), read without
     * the rest of the dress, as {@link #searchFieldOf} is: the dock's row asks it.
     */
    public boolean drawerTileOf(@NonNull PaneWallPage page) {
        return mSource.drawerTile(layoutOf(page));
    }

    /**
     * Whether {@code page} wears {@code element} ({@link Dress#wears}) on whichever layout it
     * wears, for a reader that has the page but not the dress: the store's resolve for a page.
     */
    public static boolean wornBy(@NonNull PaneWallPage page, @NonNull Element element) {
        return page != PaneWallPage.WIDGETS || !HOME_LEAVES_OUT.contains(element);
    }

    /**
     * Whether a page on {@code layout} wears {@code element} ({@link Dress#wears}), for a reader
     * that holds a layout rather than a page: the Layout editor's offers, the store's resolve.
     * The Home layout is worn only on Home, so it leaves out what Home leaves out; the
     * Workspace's layouts are offered whole, since a window wears them so.
     */
    public static boolean wornOn(@NonNull LayoutVariant layout, @NonNull Element element) {
        return layout != LayoutVariant.HOME || !HOME_LEAVES_OUT.contains(element);
    }

}
