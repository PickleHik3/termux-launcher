package com.termux.app.chrome;

import androidx.annotation.NonNull;

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

    /** What a Home page leaves out: the status bar and the extra keys are the Workspace's alone. */
    @NonNull private static final Set<Element> HOME_LEAVES_OUT =
        EnumSet.of(Element.STATUS, Element.EXTRA_KEYS);

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
        if (page == PaneWallPage.WIDGETS) {
            return new Dress(LayoutVariant.HOME, HOME_LEAVES_OUT,
                DockStyle.effectiveStyle(mSource.homeDockStyle()), true, mSource.shape(true));
        }
        return new Dress(LayoutVariant.of(mSource.minimal()), EnumSet.noneOf(Element.class),
            DockStyle.GLASS_TRAY, false, mSource.shape(false));
    }

    /**
     * Whether the two pages wear the same dress, so the chrome need not change between them:
     * any two windows do; Home and a window, each on a layout of its own, never do.
     */
    public boolean dressAlike(@NonNull PaneWallPage a, @NonNull PaneWallPage b) {
        return dressOf(a).equals(dressOf(b));
    }
}
