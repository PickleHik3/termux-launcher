package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.dock.DockGeometry;
import com.termux.app.dock.DockGeometry.Half;
import com.termux.app.dock.DockGeometry.Snapshot;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.dock.DockStyle;
import com.termux.app.page.PageKind;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.KeyboardForm;
import com.termux.app.place.PlaceLayout.KeyboardMode;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PageRef;

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
    /** The floating keyboard's width share in the fixtures. */
    public static final float FLOATING_KEYBOARD_WIDTH_SCALE = 0.9f;
    /** Pong's gesture-navigation band, 24dp at 420 dpi. */
    public static final int KEPT_BAND_PX = Math.round(24f * DENSITY);
    /** A flush padding the terminal's rule might stand. */
    public static final int FLUSH_PX = 30;
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
        /** Home's Dock design; the Terminal's is the tray unless {@link #withDesign} set it. */
        @NonNull public final DockStyle homeDesign;
        /** The Terminal's Dock design. */
        @NonNull public final DockStyle terminalDesign;

        /** The navigation band the content keeps clear under the gesture pill; 0 stands none. */
        public final int keptBandPx;
        /** What the terminal's flush rule pads the stack by wherever it may act; 0 stands none. */
        public final int flushPx;

        Fixture(@NonNull String name, int widthPx, int heightPx, @NonNull PlaceLayout window,
                int paneCount) {
            this(name, widthPx, heightPx, window, HOME, paneCount);
        }

        Fixture(@NonNull String name, int widthPx, int heightPx, @NonNull PlaceLayout window,
                @NonNull PlaceLayout home, int paneCount) {
            this.name = name;
            this.widthPx = widthPx;
            this.heightPx = heightPx;
            this.density = DENSITY;
            this.keyboardPx = KEYBOARD_PX;
            this.extraKeysRowPx = EXTRA_KEYS_ROW_PX;
            this.textRowPx = TEXT_ROW_PX;
            this.window = window;
            this.home = home;
            this.paneCount = paneCount;
            this.homeDesign = DockStyle.GLASS_TRAY;
            this.terminalDesign = DockStyle.GLASS_TRAY;
            this.keptBandPx = 0;
            this.flushPx = 0;
        }

        private Fixture(@NonNull Fixture of, @NonNull DockStyle homeDesign,
                        @NonNull DockStyle terminalDesign, @NonNull String name, int keptBandPx,
                        int flushPx) {
            this.name = name;
            this.widthPx = of.widthPx;
            this.heightPx = of.heightPx;
            this.density = of.density;
            this.keyboardPx = of.keyboardPx;
            this.extraKeysRowPx = of.extraKeysRowPx;
            this.textRowPx = of.textRowPx;
            this.window = of.window;
            this.home = of.home;
            this.paneCount = of.paneCount;
            this.homeDesign = homeDesign;
            this.terminalDesign = terminalDesign;
            this.keptBandPx = keptBandPx;
            this.flushPx = flushPx;
        }

        /**
         * This fixture with a navigation band kept under the gesture pill and the terminal's
         * flush rule padding the stack, so the foot and the flush padding are not 0.
         */
        @NonNull
        public Fixture withFoot(int keptBandPx, int flushPx) {
            return new Fixture(this, homeDesign, terminalDesign, name + " with a foot",
                keptBandPx, flushPx);
        }

        /** This fixture with Home's dock in {@code design}. */
        @NonNull
        public Fixture withHomeDesign(@NonNull DockStyle design) {
            return new Fixture(this, design, terminalDesign,
                name + " (Home " + design + ")", keptBandPx, flushPx);
        }

        /** This fixture with the one Dock design both sides wear, as the setting is global. */
        @NonNull
        public Fixture withDesign(@NonNull DockStyle design) {
            return new Fixture(this, design, design, name + " (" + design + ")", keptBandPx,
                flushPx);
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
                .design(layout == home ? homeDesign : terminalDesign).build();
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
            final int flush = flushPx;
            in.flush = (content, margin) -> flush;
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

        /**
         * {@code page}'s rest as the per-rest card answer reads it, filled the way the activity's
         * pass would: the kept band, the Terminal's rest flush padding, the keyboard measured
         * with the foot it wears, and the floating-form keyboard parked in its room.
         */
        @NonNull
        public RestCards.Rest cardsRest(@NonNull PageRef page, boolean keyboardUp,
                                        boolean dockFloating) {
            return cardsRest(page, keyboardUp, keyboardUp, dockFloating);
        }

        /**
         * {@link #cardsRest} with the other side's own rest keyboard {@code otherKeyboardUp}
         * ({@link SideCards#restKeyboardUp}): what the activity hands the answer for the side
         * not in front, whatever the front's keyboard is.
         */
        @NonNull
        public RestCards.Rest cardsRest(@NonNull PageRef page, boolean keyboardUp,
                                        boolean otherKeyboardUp, boolean dockFloating) {
            RestCards.Rest rest = cardsRestOf(page, keyboardUp, dockFloating);
            // The other side's rest, one level, as the answer reads it.
            rest.other = cardsRestOf(page.isHome() ? PageRef.TERMINAL : PageRef.HOME,
                otherKeyboardUp, dockFloating);
            return rest;
        }

        /** {@link #cardsRest} for one side alone: no other side's rest. */
        @NonNull
        public RestCards.Rest cardsRestOf(@NonNull PageRef page, boolean keyboardUp,
                                          boolean dockFloating) {
            StackLayout.Input pass = pass(page, keyboardUp, dockFloating);
            StackLayout.Result r = StackLayout.layout(pass, new DockGeometry());
            boolean floatingForm = floatingKeyboard();
            boolean laidOut = keyboardLaidOutPx() > 0;
            int foot = BottomInsets.keyboardFootPx(keptBandPx, BottomInsets.keyboardWearsFoot(
                dockFloating || floatingForm, floatingForm, false, r.bandsUnderKeyboard,
                laidOut));
            RestCards.Rest rest = new RestCards.Rest(pass, page, widthPx, heightPx, density,
                extraKeysRowPx, laidOut ? keyboardLaidOutPx() + foot : 0,
                com.termux.app.statusbar.StatusBarEdgeGeometry.thicknessPx(Edge.TOP, false, false,
                    density),
                com.termux.app.statusbar.StatusBarEdgeGeometry.thicknessPx(Edge.TOP, true, false,
                    density));
            rest.keptBandPx = keptBandPx;
            // The A-Z bar off the dock, as the activity measures its edge host: the letter band.
            rest.azEdgeThicknessPx = RestCards.azPlacementOf(layoutOf(page))
                == RestCards.AzPlacement.EDGE
                ? com.termux.app.terminal.AccessoryStackLayoutPolicy.computeAzRowHeightPx(true,
                    false, false, density) : 0;
            // A bottom status bar that is a band of the dock's plank, as the activity reads it
            // for each side's own layout (the status bar at the bottom is one).
            rest.statusOnDockPlank = com.termux.app.terminal.AccessoryStackLayoutPolicy
                .statusOnDockPlank(layoutOf(page));
            rest.restFlushPaddingPx = settle(PageRef.TERMINAL, false, dockFloating).flushPaddingPx;
            if (floatingForm) {
                rest.floatingKeyboardRoom = floatingKeyboardRoom();
                rest.floatingKeyboardHeightPx = keyboardPx;
                rest.floatingKeyboardWidthScale = FLOATING_KEYBOARD_WIDTH_SCALE;
            }
            return rest;
        }

        /** The floating keyboard's room: the screen under the status bar. */
        @NonNull
        public ChromeShape.Box floatingKeyboardRoom() {
            return new ChromeShape.Box(0f, Math.round(28f * density), widthPx, heightPx);
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
    public static PageKind place(@NonNull PageRef page) {
        return page.isHome() ? PageKind.HOME : PageKind.TERMINAL;
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

    /** Pong with its keyboard split. */
    public static final Fixture SPLIT_KEYBOARD = new Fixture("split keyboard", 1080, 2412,
        appsUnder(window(Edge.TOP, KeyboardForm.SPLIT)), 1);
    /** The A–Z bar as a row of the dock on both sides, Home's riding its pinned apps. */
    public static final Fixture AZ_ON_DOCK = new Fixture("a-z on the dock", 1080, 2400,
        new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM, RowPlacement.BOTTOM,
            KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5),
        new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM, true, Edge.BOTTOM, RowPlacement.HIDDEN,
            KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5), 1);

    /**
     * A new install's layouts: the Terminal shows the status bar on top and the extra keys along
     * the bottom over the keyboard, no apps row; Home is {@link #HOME}.
     */
    public static final Fixture DEFAULT_INSTALL = new Fixture("default install", 1080, 2412,
        new PlaceLayout(Edge.TOP, RowPlacement.HIDDEN, false, Edge.BOTTOM, RowPlacement.BOTTOM,
            KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5), HOME, 1);

    /**
     * A Terminal with no extra-keys row: its pinned apps along the bottom, the status bar on top
     * (#305: its card, the apps row's, stands apart on its own host).
     */
    public static final Fixture NO_KEYS_ROW = new Fixture("no keys row", 1080, 2412,
        new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM, false, Edge.BOTTOM, RowPlacement.HIDDEN,
            KeyboardMode.RESIZE, KeyboardForm.DOCKED, 4, 5), HOME, 1);

    /**
     * A new install's Terminal with its extra keys standing under the keyboard, in the bands
     * under it, and nothing over it (#305: the Terminal has no card of its own over the keyboard).
     */
    public static final Fixture KEYS_UNDER = new Fixture("keys under the keyboard", 1080, 2412,
        DEFAULT_INSTALL.window.withSlot(Element.EXTRA_KEYS, new Slot(false, Edge.BOTTOM, 0, true)),
        HOME, 1);

    /**
     * Pong with its status bar along the bottom as the outermost band, a band of the dock's plank
     * ({@code AccessoryStackLayoutPolicy#statusOnDockPlank}; #305: the Terminal's card spans it).
     */
    public static final Fixture STATUS_ON_PLANK = new Fixture("status bar on the plank", 1080,
        2412, STATUS_BOTTOM.window.withSlot(Element.STATUS, Slot.on(Edge.BOTTOM, 0))
            .withSlot(Element.EXTRA_KEYS, Slot.on(Edge.BOTTOM, 9)), 1);

    /**
     * The A–Z bar off the dock on {@code edge} on both sides: a bar of its own with a glass sheet
     * of its own (#307), the rest of each side's layout as on pong.
     */
    private static Fixture azOn(@NonNull String name, @NonNull Edge edge) {
        return new Fixture(name, 1080, 2412,
            PONG.window.withSlot(Element.AZ, Slot.on(edge, 0)),
            HOME.withSlot(Element.AZ, Slot.on(edge, 0)), 1);
    }

    /** The A–Z bar along the left edge on both sides (#307). */
    public static final Fixture AZ_LEFT = azOn("a-z on the left edge", Edge.LEFT);
    /** The A–Z bar along the top edge on both sides, under the status bar (#307). */
    public static final Fixture AZ_TOP = azOn("a-z on the top edge", Edge.TOP);
    /** The A–Z bar along the left edge on Home only: the Terminal shows none (#307). */
    public static final Fixture AZ_LEFT_HOME_ONLY = new Fixture("a-z on Home's left edge only",
        1080, 2412, PONG.window, HOME.withSlot(Element.AZ, Slot.on(Edge.LEFT, 0)), 1);
    /** The A–Z bar as its pull tab on both sides: no band and no glass at rest (#307). */
    public static final Fixture AZ_TAB = new Fixture("a-z as a pull tab", 1080, 2412,
        AZ_ON_DOCK.window.withAzTab(true), AZ_ON_DOCK.home.withAzTab(true), 1);

    /**
     * The fixtures the per-rest card answer is held to: {@link #ALL}, pong and tl16 with a foot and flush padding, a split
     * keyboard, the A–Z bar on the dock, on the left and the top edge, on Home's left edge alone
     * and as its pull tab, the floating keyboard with a foot, a new install, a Terminal with no
     * keys row, one with its keys under the keyboard and one with its status bar on the plank,
     * each in every design. Kept apart from {@link #ALL}, whose numbers other tests pin.
     */
    @NonNull
    public static List<Fixture> cardFixtures() {
        List<Fixture> base = new ArrayList<>(ALL);
        base.add(PONG.withFoot(KEPT_BAND_PX, FLUSH_PX));
        base.add(TL16.withFoot(KEPT_BAND_PX, FLUSH_PX));
        base.add(SPLIT_KEYBOARD);
        base.add(AZ_ON_DOCK);
        base.add(AZ_LEFT);
        base.add(AZ_TOP);
        base.add(AZ_LEFT_HOME_ONLY);
        base.add(AZ_TAB);
        base.add(FLOATING_KEYBOARD.withFoot(KEPT_BAND_PX, FLUSH_PX));
        base.add(DEFAULT_INSTALL);
        base.add(NO_KEYS_ROW);
        base.add(KEYS_UNDER);
        base.add(STATUS_ON_PLANK);
        List<Fixture> out = new ArrayList<>();
        for (Fixture fixture : base)
            for (DockStyle design : DockStyle.CHOICES) out.add(fixture.withDesign(design));
        return out;
    }

    /** Every fixture in each of the four Dock designs, worn on both sides (one setting). */
    @NonNull
    public static List<Fixture> allDesigns() {
        List<Fixture> out = new ArrayList<>();
        for (Fixture fixture : ALL)
            for (DockStyle design : DockStyle.CHOICES) out.add(fixture.withDesign(design));
        return out;
    }

    /** {@link #ALL} as JUnit's {@code Parameterized} wants them, named by {@link Fixture#name}. */
    @NonNull
    public static List<Object[]> parameters() {
        List<Object[]> out = new ArrayList<>();
        for (Fixture fixture : ALL) out.add(new Object[] {fixture});
        return out;
    }
}
