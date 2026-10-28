package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.dock.DockStyle;
import com.termux.app.place.Element;
import com.termux.app.place.LayoutVariant;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.WallShape;
import com.termux.app.wall.PaneWallPage;
import com.termux.app.wall.WallPages.PageKind;

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
 * says, in Home's half of Style, Corners and Margin and the design picked for it. Home's half is
 * linked to the Workspace's (#84, the preferences' per-half accessors answer one value), and so
 * are the layout's sizes (the store's linked keys), so the crossing never re-sizes or re-shapes
 * the chrome; the Source still names the half, so a resolver over any source asks the same
 * question the store answers. Either way a Home
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

        /**
         * The old Home layout switch. No longer read by the resolver: since version 16 a Home page
         * wears a page layout of its own ({@link #pageLayout}) or the normal one.
         */
        @Deprecated
        default boolean homeLayout() { return false; }

        /**
         * The id of the page {@code page} stands for on screen: the Home page in front for {@link
         * PaneWallPage#WIDGETS}, the Terminal page for a window; -1 where none is known.
         */
        default int pageIdOf(@NonNull PaneWallPage page) { return -1; }

        /** Whether page {@code pageId} holds a page layout of its own (ADR 0015 §3). */
        default boolean pageLayout(int pageId) { return false; }

        /** The Dock design picked on one layout, whether or not it is built yet. */
        @NonNull
        DockStyle dockDesign(@NonNull LayoutVariant layout);

        /** Whether one layout turns the Search field on, beside its design (#96). */
        boolean searchField(@NonNull LayoutVariant layout);

        /** Whether one layout turns the Drawer tile on, beside its design (#97). */
        boolean drawerTile(@NonNull LayoutVariant layout);

        /** One half's Style, Corners and Margin as stored: Home's own, or the Workspace's. */
        @NonNull
        WallShape shape();

        /** Whether a kind of page shows one element: the page model's per-kind visibility. */
        boolean shows(@NonNull PageKind kind, @NonNull Element element);
    }

    /**
     * What the Layout editor does not offer on the Home layout: the extra keys. Only an offer
     * ({@link #wornOn}); what a page wears is its kind's visibility ({@link Source#shows}).
     */
    @NonNull private static final Set<Element> HOME_NOT_OFFERED = EnumSet.of(Element.EXTRA_KEYS);

    /** The kind of page a wall page carries: the Home pages' row, or the Workspace's windows. */
    @NonNull
    public static PageKind kindOf(@NonNull PaneWallPage page) {
        return page == PaneWallPage.WIDGETS ? PageKind.HOME : PageKind.TERMINAL;
    }

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

            @Override public int pageIdOf(@NonNull PaneWallPage page) {
                PlaceLayoutStore store = places.get();
                return store != null ? store.shownPageId(page) : -1;
            }

            @Override public boolean pageLayout(int pageId) {
                PlaceLayoutStore store = places.get();
                return store != null && store.hasPageLayout(pageId);
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

            @NonNull @Override public WallShape shape() {
                PlaceLayoutStore store = places.get();
                return store != null ? store.shape() : WallShape.read(null);
            }

            @Override public boolean shows(@NonNull PageKind kind, @NonNull Element element) {
                PlaceLayoutStore store = places.get();
                return store != null ? store.kindShowsAny(kind, element)
                    : PlaceLayoutStore.kindShowsByDefault(kind, element);
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
        LayoutVariant layout = layoutOf(page);
        PageKind kind = kindOf(page);
        Set<Element> leftOut = EnumSet.noneOf(Element.class);
        for (Element element : Element.values()) {
            if (!mSource.shows(kind, element)) leftOut.add(element);
        }
        return new Dress(layout, leftOut,
            DockStyle.effectiveStyle(mSource.dockDesign(layout)), mSource.searchField(layout),
            mSource.drawerTile(layout), mSource.shape());
    }

    /**
     * The layout the page wears ({@link Dress#layout}), read without the rest of the dress: the
     * question the stack's sizes ask on every pass. A Home page wears its page layout while it
     * holds one, else the normal layout: minimal mode is the windows' alone. A window wears the
     * minimal layout while minimal mode is on, else the Terminal page's own while it holds one,
     * else the normal one (ADR 0015 §3).
     */
    @NonNull
    public LayoutVariant layoutOf(@NonNull PaneWallPage page) {
        boolean home = page == PaneWallPage.WIDGETS;
        if (!home && mSource.minimal()) return LayoutVariant.MINIMAL;
        int id = mSource.pageIdOf(page);
        if (id >= 0 && mSource.pageLayout(id))
            return LayoutVariant.page(id, home ? PageKind.HOME : PageKind.TERMINAL);
        return LayoutVariant.NORMAL;
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
     * Whether {@code page} wears {@code element} ({@link Dress#wears}): its kind shows it, as the
     * store has it now, for a reader that has the page but not the dress.
     */
    public boolean wears(@NonNull PaneWallPage page, @NonNull Element element) {
        return mSource.shows(kindOf(page), element);
    }

    /**
     * Whether the Layout editor offers {@code element} on {@code layout}: the Home layout leaves
     * out the extra keys, the Workspace's layouts are offered whole. An offer only; whether a
     * page wears an element is its kind's visibility ({@link #wears}).
     */
    public static boolean wornOn(@NonNull LayoutVariant layout, @NonNull Element element) {
        return !layout.isHomeKind() || !HOME_NOT_OFFERED.contains(element);
    }

}
