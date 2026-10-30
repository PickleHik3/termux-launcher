package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.dock.DockGeometry;
import com.termux.app.dock.DockGeometry.Half;
import com.termux.app.dock.DockGeometry.Snapshot;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.dock.DockStyle;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PageRef;
import com.termux.app.wall.PaneWallPage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Five real layouts the geometry policies are run through, so a number two phases work out
 * (one predicts or pre-sizes it, a later one settles it) is checked on every one of them and a
 * formula that diverges on one layout fails here instead of on a phone
 * ({@code project-docs/active/motion-invariants-2026-10-30.md} section 5).
 *
 * <p>Each fixture builds both sides the way the activity builds them: {@link #pass} fills a
 * {@link StackLayout.Input} as {@code setTerminalToolbarHeight} does for the place on screen, and
 * {@link #rest} fills a {@link StackLayout.RestInput} as {@code travelRestReservationPx} does for
 * a place the crossing reads while Home is committed. The flush rule is 0 on both sides (it only
 * acts with the keyboard down on a lone terminal), and the bottom status band is the one number
 * both sides are handed: the activity measures it twice (the live bar's host, and its Style's
 * thickness at rest), which these fixtures cannot reach.</p>
 */
public final class LayoutFixtures {

    private LayoutFixtures() {}

    /** Pong's density, 420 dpi. */
    public static final float DENSITY = 2.625f;
    /** Pong's in-app keyboard, as the pass measures it. */
    public static final int KEYBOARD_PX = 670;
    /** One row of extra keys: the pager's 37.5dp at 420 dpi, 98 px. */
    public static final int EXTRA_KEYS_ROW_PX = Math.round(37.5f * DENSITY);
    /** Pong's text row. */
    public static final int TEXT_ROW_PX = 32;
    /** A Docked status bar along the bottom: its 28dp at 420 dpi. */
    public static final int BOTTOM_STATUS_BAND_PX = Math.round(28f * DENSITY);

    /** Home: its pinned apps alone along the bottom, no extra keys, no status bar of its own. */
    public static final PlaceLayout HOME = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.HIDDEN, KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5);

    /** A window: its pinned apps and its extra keys along the bottom, the status bar on top. */
    private static PlaceLayout window(@NonNull Edge statusEdge, @NonNull KeyboardForm form) {
        return new PlaceLayout(statusEdge, RowPlacement.BOTTOM, false, Edge.BOTTOM,
            RowPlacement.BOTTOM, KeyboardMode.RESIZE, form, 4, 5);
    }

    /** The same window with its pinned apps standing under the keyboard (pong's). */
    private static PlaceLayout appsUnder(@NonNull PlaceLayout window) {
        return window.withSlot(Element.APPS, new Slot(false, Edge.BOTTOM, 0, true));
    }

    /** One real layout and the screen it stands on. */
    public static final class Fixture {
        @NonNull public final String name;
        public final int widthPx;
        public final int heightPx;
        public final float density;
        /** The in-app keyboard's height, docked; what a floating one would be, floating. */
        public final int keyboardPx;
        public final int extraKeysRowPx;
        public final int textRowPx;
        /** The Terminal's layout. */
        @NonNull public final PlaceLayout window;
        /** Home's layout. */
        @NonNull public final PlaceLayout home;
        /** The terminal's tiled panes. */
        public final int paneCount;

        Fixture(@NonNull String name, int widthPx, int heightPx, @NonNull PlaceLayout window,
                int paneCount) {
            this.name = name;
            this.widthPx = widthPx;
            this.heightPx = heightPx;
            this.density = DENSITY;
            this.keyboardPx = KEYBOARD_PX;
            this.extraKeysRowPx = EXTRA_KEYS_ROW_PX;
            this.textRowPx = TEXT_ROW_PX;
            this.window = window;
            this.home = HOME;
            this.paneCount = paneCount;
        }

        public boolean floatingKeyboard() {
            return window.keyboardForm == KeyboardForm.FLOATING;
        }

        /**
         * The keyboard's height as the stack lays it out: 0 for a floating one, which its own
         * frame lays out ({@code KeyboardGeometryChoreographer#measureHeightPx}).
         */
        public int keyboardLaidOutPx() {
            return floatingKeyboard() ? 0 : keyboardPx;
        }

        @NonNull
        public PlaceLayout layoutOf(@NonNull PageRef page) {
            return page.isHome() ? home : window;
        }

        /** The band a status bar along the bottom claims on {@code layout}, 0 elsewhere. */
        public int bottomStatusBandPx(@NonNull PlaceLayout layout) {
            return EdgeStackPolicy.stack(layout, Edge.BOTTOM).contains(Element.STATUS)
                ? BOTTOM_STATUS_BAND_PX : 0;
        }

        private Half half(@NonNull PlaceLayout layout, boolean dockFloating) {
            return Half.builder(layout).floating(dockFloating).dockHeightScale(1.95f)
                .design(DockStyle.GLASS_TRAY).build();
        }

        /** The device's dock snapshot with {@code rows}' rows standing, on {@code arrangement}. */
        @NonNull
        public Snapshot snapshot(@NonNull PageRef rows, boolean dockFloating) {
            return Snapshot.builder(half(home, dockFloating), half(window, dockFloating))
                .density(density).widthPx(widthPx)
                .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(density))
                .pinnedSlotCount(7)
                .preferencesAvailable(true)
                .rows(rows).arrangement(layoutOf(rows))
                .build();
        }

        /**
         * The geometry pass on {@code page}, committed and on screen, keyboard up or down, filled
         * the way the activity fills it.
         */
        @NonNull
        public StackLayout.Input pass(@NonNull PageRef page, boolean keyboardUp,
                                      boolean dockFloating) {
            PlaceLayout layout = layoutOf(page);
            boolean home = page.isHome();
            StackLayout.Input in = new StackLayout.Input(snapshot(page, dockFloating));
            in.toolbarShown = PlaceChromePolicy.dockShown(layout);
            in.extraKeysRowEnabled = PlaceChromePolicy.extraKeysRowShown(layout);
            in.toolbarHeightPx = in.extraKeysRowEnabled ? extraKeysRowPx : 0;
            in.keyboardShown = keyboardUp;
            in.keyboardHeightPx = keyboardUp ? keyboardLaidOutPx() : 0;
            in.statusBandPx = bottomStatusBandPx(layout);
            in.keyboardOverlays = KeyboardOverlayPolicy.overlays(place(page), layout);
            in.dockOverlays = home;
            in.flushAllowed = !home && paneCount <= 1;
            return in;
        }

        /** {@link #pass} laid out. */
        @NonNull
        public StackLayout.Result settle(@NonNull PageRef page, boolean keyboardUp,
                                         boolean dockFloating) {
            return StackLayout.layout(pass(page, keyboardUp, dockFloating), new DockGeometry());
        }

        /**
         * {@code page}'s rest as a crossing reads it while {@code committed} is on screen, its
         * keyboard up or down, filled the way the activity fills it.
         */
        @NonNull
        public StackLayout.RestInput rest(@NonNull PageRef committed, @NonNull PageRef page,
                                          boolean keyboardUp, boolean dockFloating) {
            StackLayout.RestInput in = new StackLayout.RestInput(
                snapshot(committed, dockFloating), page).ownRows(extraKeysRowPx);
            in.statusBand = floating -> BOTTOM_STATUS_BAND_PX;
            in.keyboardShown = keyboardUp;
            // The keyboard's last measured height while it is down; the pass's while it shows,
            // which is 0 for a floating one.
            boolean shownNow = committed.equals(page) && keyboardUp;
            in.keyboardHeightPx = shownNow ? keyboardLaidOutPx() : keyboardPx;
            in.keyboardOverlays = KeyboardOverlayPolicy.overlays(place(page), layoutOf(page));
            return in;
        }

        /** {@link #rest} from Home. */
        public int restReservationPx(@NonNull PageRef page, boolean keyboardUp,
                                     boolean dockFloating) {
            return StackLayout.restReservationPx(rest(PageRef.HOME, page, keyboardUp,
                dockFloating));
        }

        @NonNull @Override public String toString() {
            return name;
        }
    }

    /** The wall place a page stands for. */
    @NonNull
    public static PaneWallPage place(@NonNull PageRef page) {
        return page.isHome() ? PaneWallPage.WIDGETS : PaneWallPage.TERMINAL;
    }

    /** Pong: dock rows under the docked keyboard, extra keys above it, status bar on top. */
    public static final Fixture PONG = new Fixture("pong", 1080, 2412,
        appsUnder(window(Edge.TOP, KeyboardForm.DOCKED)), 1);
    /** The tl16 emulator: nothing under the keyboard. */
    public static final Fixture TL16 = new Fixture("tl16", 1080, 2400,
        window(Edge.TOP, KeyboardForm.DOCKED), 1);
    /** Pong with a floating keyboard. */
    public static final Fixture FLOATING_KEYBOARD = new Fixture("floating keyboard", 1080, 2412,
        appsUnder(window(Edge.TOP, KeyboardForm.FLOATING)), 1);
    /** Pong with its status bar along the bottom. */
    public static final Fixture STATUS_BOTTOM = new Fixture("status bar at the bottom", 1080,
        2412, appsUnder(window(Edge.BOTTOM, KeyboardForm.DOCKED)), 1);
    /**
     * Pong with two panes. Stack layout takes no pane count; the panes only stand the flush
     * absorption down ({@link StackLayout.Input#flushAllowed}), which the keyboard does too.
     */
    public static final Fixture SPLIT_PANE = new Fixture("split pane", 1080, 2412,
        appsUnder(window(Edge.TOP, KeyboardForm.DOCKED)), 2);

    public static final List<Fixture> ALL = Collections.unmodifiableList(Arrays.asList(
        PONG, TL16, FLOATING_KEYBOARD, STATUS_BOTTOM, SPLIT_PANE));

    /** {@link #ALL} as JUnit's {@code Parameterized} wants them, named by {@link Fixture#name}. */
    @NonNull
    public static List<Object[]> parameters() {
        List<Object[]> out = new ArrayList<>();
        for (Fixture fixture : ALL) out.add(new Object[] {fixture});
        return out;
    }
}
