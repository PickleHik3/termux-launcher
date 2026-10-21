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
 * Corners and Margin, its dock the tray it has always been (until #93 gives it the layout's
 * design). A <b>Home page</b> wears the normal layout less the extra keys, in the same Style,
 * Corners and Margin, its dock the Dock design that layout holds, unless Home holds a <b>Home
 * layout</b> of its own: then it wears that ({@link LayoutVariant#HOME}), whatever minimal mode
 * says, in Home's own Style, Corners and Margin and the design picked for it. Until the bar on
 * Home lands (#90) a Home page also leaves out the status bar, on either layout.
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

        /** One half's Style, Corners and Margin as stored: Home's own, or the Workspace's. */
        @NonNull
        WallHalfShape shape(boolean home);
    }

    /**
     * What a Home page leaves out, whichever layout it wears: the extra keys are the Workspace's
     * alone, and the status bar stays off Home until #90.
     */
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

            @Override public boolean homeLayout() {
                return places.hasHomeLayout();
            }

            @NonNull @Override public DockStyle dockDesign(@NonNull LayoutVariant layout) {
                return places.forVariant(layout).dockStyle();
            }

            @NonNull @Override public WallHalfShape shape(boolean home) {
                return places.halfShape(home);
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
            home ? DockStyle.effectiveStyle(mSource.dockDesign(layout)) : DockStyle.GLASS_TRAY,
            picksDockDesign(page), mSource.shape(home && layout == LayoutVariant.HOME));
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
     * dress: the question the seam's bead melt asks on every frame of a slide. A window reads
     * nothing: its dock is the tray.
     */
    @NonNull
    public DockStyle dockDesignOf(@NonNull PaneWallPage page) {
        if (page != PaneWallPage.WIDGETS) return DockStyle.GLASS_TRAY;
        return DockStyle.effectiveStyle(mSource.dockDesign(layoutOf(page)));
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

    /**
     * Whether {@code page} draws a Dock design the user picks ({@link Dress#picksDockDesign}):
     * Home, on whichever layout it wears. A window's dock is the tray until #93.
     */
    public static boolean picksDockDesign(@NonNull PaneWallPage page) {
        return page == PaneWallPage.WIDGETS;
    }

    /**
     * The Dock design {@code page} draws ({@link Dress#dockDesign}) when its layout holds
     * {@code picked}: on Home the one picked, as it draws today ({@link
     * DockStyle#effectiveStyle}), and on a window the tray.
     */
    @NonNull
    public static DockStyle dockDesignOn(@NonNull PaneWallPage page, @Nullable DockStyle picked) {
        return picksDockDesign(page) ? DockStyle.effectiveStyle(picked) : DockStyle.GLASS_TRAY;
    }

    /**
     * {@link #dockDesignOn(PaneWallPage, DockStyle)} for a layout named without its page, as the
     * store resolves one: the Home layout's pages are Home pages, the Workspace's are windows.
     */
    @NonNull
    public static DockStyle dockDesignOn(@NonNull LayoutVariant layout,
                                         @Nullable DockStyle picked) {
        return dockDesignOn(layout == LayoutVariant.HOME ? PaneWallPage.WIDGETS
            : PaneWallPage.TERMINAL, picked);
    }

    /**
     * Whether the two pages wear the same dress, so the chrome need not change between them:
     * any two windows do. Home never quite does a window's, since it leaves out the extra keys
     * and picks its dock's design; on one layout the two still share the layout and the shape,
     * which is what the stack's dress across the seam compares.
     */
    public boolean dressAlike(@NonNull PaneWallPage a, @NonNull PaneWallPage b) {
        return dressOf(a).equals(dressOf(b));
    }
}
