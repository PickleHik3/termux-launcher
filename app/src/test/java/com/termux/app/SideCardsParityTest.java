package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.dock.DockGeometry;
import com.termux.app.dock.DockPlacement;
import com.termux.app.dock.DockSpec;
import com.termux.app.dock.DockStyle;
import com.termux.app.dock.DockSurfaceSpec;
import com.termux.app.dock.WornDockDesign;
import com.termux.app.place.PlaceChromePolicy;
import com.termux.app.place.RestCards;
import com.termux.app.dock.SideCardsController;
import com.termux.app.dock.SideRowViews;
import com.termux.app.page.PageKind;
import com.termux.app.place.BottomInsets;
import com.termux.app.place.EdgeStackView;
import com.termux.app.place.Element;
import com.termux.app.place.LayoutFixtures;
import com.termux.app.place.LayoutFixtures.Fixture;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.SideCards;
import com.termux.app.place.StackLanes;
import com.termux.app.place.StackLayout;
import com.termux.app.wall.PageRef;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * One owner for the two-phase numbers of the two Floating cards (ADR 0032): over every card
 * fixture, both pages in front, the keyboard up and down, Docked and Floating, the controller's
 * apart state, container margin and Home's drop are what {@link SideCards} gives for the two
 * rests' own margins, and the renderer, handed the controller's numbers, draws the host at Home's
 * slide and drop at rest.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SideCardsParityTest {

    private static final int WIDTH = 1080;

    private final SparseArray<View> mViews = new SparseArray<>();
    private boolean mFrontHome;
    private View mHost;
    private View mHomeRow;
    private View mKeysRow;
    private View mUnderlay;

    private StackRenderer rendererFor(boolean frontHome) {
        Context context = RuntimeEnvironment.getApplication();
        mViews.clear();
        mFrontHome = frontHome;
        FrameLayout container = new FrameLayout(context);
        EdgeStackView rowStack = new EdgeStackView(context);
        rowStack.setEdge(Edge.BOTTOM);
        mHomeRow = new FrameLayout(context);
        mKeysRow = new FrameLayout(context);
        mHomeRow.setId(SideRowViews.id(Element.AZ, SideRowViews.Part.BOTTOM_HOST));
        mKeysRow.setId(SideRowViews.id(Element.EXTRA_KEYS, SideRowViews.Part.BOTTOM_HOST));
        rowStack.addView(mHomeRow);
        rowStack.addView(mKeysRow);
        rowStack.setLane(mHomeRow, StackLanes.HOME);
        rowStack.setLane(mKeysRow, StackLanes.TERMINAL);
        container.addView(rowStack);
        container.layout(0, 0, WIDTH, 600);
        mHost = new FrameLayout(context);
        mUnderlay = new FrameLayout(context);
        mViews.put(R.id.accessory_stack_container, container);
        mViews.put(R.id.accessory_row_stack, rowStack);
        mViews.put(R.id.accessory_surface_host, mHost);
        mViews.put(R.id.apps_bar_az_fx_underlay, mUnderlay);
        mViews.put(mHomeRow.getId(), mHomeRow);
        mViews.put(mKeysRow.getId(), mKeysRow);
        return new StackRenderer(new StackRenderer.Views() {
            @Nullable @Override public View view(int id) { return mViews.get(id); }
            @Override public int keyboardDesiredHeightPx() { return 0; }
            @Override public boolean underKeyboardBandsShown() { return false; }
            @Override public boolean keyboardShown() { return false; }
            @Override public boolean frontIsHome() { return mFrontHome; }
        });
    }

    /** Views the controller lays out besides the keys card (the foot, the Terminal's host). */
    private static final SparseArray<View> sHostViews = new SparseArray<>();

    private static DisplayMetrics metrics(@NonNull Fixture f) {
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.widthPixels = f.widthPx;
        metrics.heightPixels = f.heightPx;
        metrics.density = f.density;
        return metrics;
    }

    private static SideCardsController.Host hostOf(@NonNull Fixture f, @NonNull PageRef front,
                                                   boolean keyboardUp, boolean floating,
                                                   @NonNull StackRenderer renderer) {
        return hostOf(f, front, keyboardUp, keyboardUp, floating, renderer, null);
    }

    /**
     * @param otherUp  the other side's own rest keyboard ({@link SideCards#restKeyboardUp})
     * @param keysCard the Terminal's extra-keys card view, null for none
     */
    private static SideCardsController.Host hostOf(@NonNull Fixture f, @NonNull PageRef front,
                                                   boolean keyboardUp, boolean otherUp,
                                                   boolean floating,
                                                   @NonNull StackRenderer renderer,
                                                   @Nullable View keysCard) {
        final int flush = f.settle(PageRef.TERMINAL, false, floating).flushPaddingPx;
        final StackLayout.Result own = f.settle(front, keyboardUp, floating);
        final int foot = BottomInsets.keyboardFootPx(f.keptBandPx, BottomInsets.keyboardWearsFoot(
            floating || f.floatingKeyboard(), f.floatingKeyboard(), false, own.bandsUnderKeyboard,
            f.keyboardLaidOutPx() > 0));
        return new SideCardsController.Host() {
            @Nullable @Override public View view(int id) {
                return id == R.id.extra_keys_card ? keysCard : sHostViews.get(id);
            }
            @Nullable @Override public PageRef frontPage() { return front; }
            @Nullable @Override public PlaceLayout layoutOf(@NonNull PageRef page) {
                return f.layoutOf(page);
            }
            @NonNull @Override public DockGeometry.Snapshot dockSnapshot(@NonNull PlaceLayout layout,
                                                                         @NonNull PageKind rows) {
                return f.snapshot(rows == PageKind.HOME ? PageRef.HOME : PageRef.TERMINAL, floating);
            }
            @NonNull @Override public DisplayMetrics displayMetrics() { return metrics(f); }
            @Override public int measuredBandPx(@NonNull com.termux.app.place.Element element,
                                                @NonNull PlaceLayout layout) {
                if (element == com.termux.app.place.Element.EXTRA_KEYS) return f.extraKeysRowPx;
                // The A-Z bar off the dock, as the activity measures its edge host.
                return element == com.termux.app.place.Element.AZ
                    && com.termux.app.place.PlaceChromePolicy.azRowShown(layout)
                    && !com.termux.app.place.PlaceChromePolicy.azRowOnDock(layout)
                    && !com.termux.app.place.PlaceChromePolicy.azTabShown(layout)
                    ? com.termux.app.terminal.AccessoryStackLayoutPolicy.computeAzRowHeightPx(true,
                        false, false, f.density) : 0;
            }
            @Override public int measuredKeyboardPx() {
                return f.keyboardLaidOutPx() > 0 ? f.keyboardLaidOutPx() + foot : 0;
            }
            @Override public int restKeyboardPx(@NonNull PageRef page) {
                return SideCards.restKeyboardPx(
                    SideCards.restKeyboardUp(page.equals(front), keyboardUp, otherUp),
                    f.pass(page, true, floating).keyboardHeightPx);
            }
            @Override public int bottomStatusBandPx(@NonNull PlaceLayout layout) {
                return f.bottomStatusBandPx(layout);
            }
            @Override public int keptBandPx() { return f.keptBandPx; }
            @Override public int terminalRestFlushPaddingPx() { return flush; }
            @Override public boolean terminalFlushAllowed() { return f.paneCount <= 1; }
            @Override public boolean keyboardGlassShared() { return true; }
            @Override public void restCards(@Nullable RestCards home, @Nullable RestCards terminal) {
                renderer.setCards(home, terminal);
            }
            @Override public void sidesApart(boolean apart, int homeDropPx, int terminalLiftPx) {
                renderer.setSidesApart(apart, homeDropPx, terminalLiftPx);
            }
        };
    }

    /**
     * The container's inner box has one owner while apart: no padding at either rest, and the
     * padding the Terminal's own rest pads ({@link RestCards#stackPaddingPx}, its
     * {@link BottomInsets#stackPaddingPx}) carried as its own lift, whichever page is in front,
     * so Home's pinned card stands on the container's true bottom and nothing the landing lays
     * out carries the Terminal's lift. Under Docked with the dock's foot that padding stands in
     * the keyboard column's margin, so the lift is none there.
     */
    @Test public void whileApartTheContainerPadsNothingAndTheTerminalCarriesItsInset() {
        int lifted = 0;
        for (Fixture f : LayoutFixtures.cardFixtures()) {
            for (PageRef page : new PageRef[] {PageRef.HOME, PageRef.TERMINAL}) {
                for (boolean up : new boolean[] {false, true}) {
                    for (boolean floating : new boolean[] {false, true}) {
                        String label = f.name + " " + page + " kb=" + up + " floating=" + floating;
                        SideCardsController controller = new SideCardsController(
                            hostOf(f, page, up, floating, rendererFor(page.isHome())));
                        controller.update(f.pass(page, up, floating));
                        int inset = stackPaddingPx(f, page, up, floating);
                        // The Terminal's own rest (hostOf gives both sides this keyboard).
                        int terminalInset = stackPaddingPx(f, PageRef.TERMINAL, up, floating);
                        boolean apart = controller.apart();
                        int padding = controller.containerPaddingPx(inset);
                        int lift = controller.terminalSideLiftPx();
                        assertEquals(label, apart ? 0 : inset, padding);
                        assertEquals(label, apart ? terminalInset : 0, lift);
                        if (lift > 0) lifted++;
                    }
                }
            }
        }
        assertTrue("some Terminal rests stand a lift", lifted > 0);
    }

    /** What {@code page}'s own rest pads the container by, as the activity pads it. */
    private static int stackPaddingPx(@NonNull Fixture f, @NonNull PageRef page, boolean up,
                                      boolean floating) {
        return RestCards.of(f.cardsRestOf(page, up, floating)).stackPaddingPx();
    }

    @Test public void everyFixtureTheControllerAndTheRendererAgreeWithTheRule() {
        int apartCases = 0;
        int cases = 0;
        for (Fixture f : LayoutFixtures.cardFixtures()) {
            for (PageRef page : new PageRef[] {PageRef.HOME, PageRef.TERMINAL}) {
                for (boolean up : new boolean[] {false, true}) {
                    for (boolean floating : new boolean[] {false, true}) {
                        cases++;
                        String label = f.name + " " + page + " kb=" + (up ? "up" : "down")
                            + " floating=" + floating;
                        StackRenderer renderer = rendererFor(page.isHome());
                        SideCardsController controller = new SideCardsController(
                            hostOf(f, page, up, floating, renderer));
                        controller.update(f.pass(page, up, floating));
                        renderer.apply();
                        // The rule, from the two rests' own margins.
                        int homeMargin = f.settle(PageRef.HOME, up, floating).bottomMarginPx;
                        int terminalMargin = f.settle(PageRef.TERMINAL, up, floating).bottomMarginPx;
                        // Every layout stands apart since #305, but a Terminal whose rest
                        // wears Home's Peek card at Home's rect (RestCards#sharesHomesPeek).
                        boolean apart = RestCards.of(f.cardsRest(page, up, floating)).apart();
                        assertEquals(label, apart, controller.apart());
                        int frontMargin = page.isHome() ? homeMargin : terminalMargin;
                        assertEquals(label, SideCards.containerMarginPx(homeMargin, terminalMargin,
                            apart, page.isHome()), controller.containerMarginPx(frontMargin));
                        float drop = SideCards.homeSideDropPx(homeMargin, terminalMargin, apart);
                        // Home's drop, as the renderer draws Home's side at rest.
                        assertEquals(label, drop, mHost.getTranslationY(), 0f);
                        assertEquals(label, drop, mUnderlay.getTranslationY(), 0f);
                        assertEquals(label, drop, mHomeRow.getTranslationY(), 0f);
                        // The Terminal's lane: its own rest's lift, at both rests.
                        float lift = SideCards.terminalSideLiftPx(stackPaddingPx(f,
                            PageRef.TERMINAL, up, floating), apart);
                        assertEquals(label, -lift, mKeysRow.getTranslationY(), 0f);
                        assertEquals(label, lift, controller.terminalSideLiftPx(), 0f);
                        // Home's slide: 0 at Home's rest, a container's width to the left otherwise.
                        float tx = apart && !page.isHome() ? -WIDTH : 0f;
                        assertEquals(label, tx, mHost.getTranslationX(), 0f);
                        if (apart) apartCases++;
                        else assertFalse(label, controller.hostPinned());
                    }
                }
            }
        }
        assertTrue("some fixtures stand apart", apartCases > 0);
        // Since #305 the apps row or A-Z beside the keys stands apart too; the refusals left
        // (no keys row, keys under the keyboard, a status band on the plank) are in no fixture.
    }

    /**
     * While apart the host's dress is Home's rest's, whichever page is in front: the same look
     * from either side, Home's design, the card the answer cuts for it, its foot and its keyboard.
     */
    @Test public void theHostIsDressedAsHomesRestWhicheverPageIsInFront() {
        int looks = 0;
        int peeks = 0;
        int tray = 0;
        for (Fixture base : LayoutFixtures.cardFixtures()) {
            for (DockStyle design : DockStyle.CHOICES) {
                Fixture f = base.withHomeDesign(design);
                for (boolean up : new boolean[] {false, true}) {
                    String label = f.name + " " + design + " kb=" + (up ? "up" : "down");
                    RestCards.HostLook[] seen = new RestCards.HostLook[2];
                    for (PageRef page : new PageRef[] {PageRef.HOME, PageRef.TERMINAL}) {
                        StackRenderer renderer = rendererFor(page.isHome());
                        SideCardsController controller = new SideCardsController(
                            hostOf(f, page, up, true, renderer));
                        controller.update(f.pass(page, up, true));
                        seen[page.isHome() ? 0 : 1] = controller.hostLook();
                        if (!controller.apart() || !controller.hostPinned()) {
                            assertNull(label, controller.hostLook());
                            assertNull(label, controller.hostRestRect());
                        }
                    }
                    if (seen[0] == null || seen[1] == null) {
                        assertEquals(label, seen[0] == null, seen[1] == null);
                        continue;
                    }
                    looks++;
                    RestCards.HostLook look = seen[0];
                    assertSame(label, look.design, seen[1].design);
                    assertEquals(label, look.peek, seen[1].peek);
                    assertEquals(label, look.floats, seen[1].floats);
                    assertEquals(label, look.foot, seen[1].foot);
                    assertEquals(label, look.keyboardUp, seen[1].keyboardUp);
                    assertEquals(label, look.peekCut, seen[1].peekCut);
                    assertEquals(label, look.drawn.toString(), seen[1].drawn.toString());
                    PlaceLayout home = f.layoutOf(PageRef.HOME);
                    DockStyle worn = WornDockDesign.of(f.homeDesign, home);
                    assertSame(label, worn, look.design);
                    // Under Home's keyboard cover the host is dressed as its keyboard-down rest
                    // (#309); a Home whose keyboard is always on is dressed for the one it has.
                    boolean dressedUp = up && com.termux.app.place.HomeKeyboardCover.liftsRows(
                        com.termux.app.page.PageKind.HOME,
                        f.pass(PageRef.HOME, up, true).homeKeyboardAlwaysOn);
                    assertEquals(label, dressedUp, look.keyboardUp);
                    // Apart is Floating: the dock is placed as the card unless the Peek card stands.
                    assertEquals(label, !look.peek, look.floats);
                    assertEquals(label, look.peek, look.peekCut != null);
                    assertEquals(label, DockSpec.footExists(PlaceChromePolicy.dockShown(home),
                        look.floats, dressedUp, false, f.keptBandPx), look.foot);
                    if (look.peek) {
                        peeks++;
                        assertTrue(label, DockSurfaceSpec.forStyle(look.design).peek);
                    } else {
                        tray++;
                    }
                }
            }
        }
        assertTrue("some Home rests wear a look", looks > 0);
        assertTrue("some wear the Peek card", peeks > 0);
        assertTrue("some wear the tray", tray > 0);
    }

    /**
     * Home's keyboard raised on demand draws over the dock (#309): while the cards stand apart the
     * host is pinned from the answer, and Home's keyboard showing or hiding changes none of it (its
     * pin, height, gap, rest rect and dress), in every fixture and Dock design.
     */
    @Test public void homesKeyboardShowingReLaysNothingOfHomesPinnedHost() {
        int pinned = 0;
        for (Fixture base : LayoutFixtures.cardFixtures()) {
            for (DockStyle design : DockStyle.CHOICES) {
                Fixture f = base.withHomeDesign(design);
                // A Terminal wearing the Peek card at one keyboard state stands together with
                // Home there (#305), so Home's host is pinned at one state only.
                if (wearsPeekAtEither(f)) continue;
                String[] layouts = new String[2];
                for (boolean up : new boolean[] {false, true}) {
                    StackRenderer renderer = rendererFor(true);
                    SideCardsController controller = new SideCardsController(
                        hostOf(f, PageRef.HOME, up, true, renderer));
                    controller.update(f.pass(PageRef.HOME, up, true));
                    layouts[up ? 1 : 0] = hostLayoutOf(controller);
                    if (up && controller.hostPinned()) pinned++;
                }
                assertEquals(f.name + " " + design, layouts[0], layouts[1]);
            }
        }
        assertTrue("some fixtures pin the host", pinned > 0);
    }

    /** What the controller lays the glass host out at: its pin, height, gap and Home's dress. */
    private static String hostLayoutOf(@NonNull SideCardsController controller) {
        android.widget.RelativeLayout.LayoutParams params =
            new android.widget.RelativeLayout.LayoutParams(0, 0);
        controller.layOutHost(params, 0);
        RestCards.HostLook look = controller.hostLook();
        return "pinned=" + controller.hostPinned() + " height=" + controller.hostHeightPx()
            + " margins=" + params.leftMargin + "," + params.rightMargin + "," + params.bottomMargin
            + " rect=" + controller.hostRestRect() + " margin=" + controller.containerMarginPx(-1)
            + (look == null ? " look=none" : " look=" + look.design + "/" + look.peek + "/"
                + look.floats + "/" + look.foot + "/" + look.keyboardUp + "/" + look.peekCut
                + "/" + look.drawn);
    }

    /**
     * The two phases of Home's card have one owner: parked while the Terminal is in front, the
     * host is laid out exactly as Home's own rest lays it out (its own keyboard, slack and gap),
     * and the Terminal's keyboard, up or down, changes none of it. So the landing on Home finds
     * the host already at the answer, and toggling the Terminal's keyboard re-lays nothing.
     */
    @Test public void homesParkedHostIsHomesOwnRestWhateverTheTerminalsKeyboard() {
        int cases = 0;
        for (Fixture f : LayoutFixtures.cardFixtures()) {
            // Together where the Terminal's rest wears the Peek card (#305): no parked host.
            if (wearsPeekAtEither(f)) continue;
            for (boolean homeUp : new boolean[] {false, true}) {
                SideCardsController atHome = new SideCardsController(hostOf(f, PageRef.HOME,
                    homeUp, false, true, rendererFor(true), null));
                atHome.update(f.pass(PageRef.HOME, homeUp, true));
                if (!atHome.apart()) continue;
                String want = hostLayoutOf(atHome);
                for (boolean terminalUp : new boolean[] {false, true}) {
                    String label = f.name + " home kb=" + homeUp + " terminal kb=" + terminalUp;
                    SideCardsController parked = new SideCardsController(hostOf(f,
                        PageRef.TERMINAL, terminalUp, homeUp, true, rendererFor(false), null));
                    parked.update(f.pass(PageRef.TERMINAL, terminalUp, true));
                    assertTrue(label, parked.apart());
                    assertEquals(label, want, hostLayoutOf(parked));
                    cases++;
                }
            }
        }
        assertTrue("some fixtures stand apart", cases > 0);
    }

    /**
     * The keyboard's cover on Home (#309) joins the lanes: Home's lane its whole cover drop on
     * top of its own, the stack's lane Home's share of it, the Terminal's lane none; not apart,
     * the layers carry the cover and every lane stays 0.
     */
    @Test public void theCoverDropsHomesLaneAndTheStacksShareAndNotTheTerminals() {
        assertEquals(7f + 40f, SideCards.laneTyPx(StackLanes.HOME, true, 7f, 5, 0.5f, 40f, 20f), 0f);
        assertEquals(-5f, SideCards.laneTyPx(StackLanes.TERMINAL, true, 7f, 5, 0.5f, 40f, 20f), 0f);
        assertEquals(-2.5f + 3.5f + 20f,
            SideCards.laneTyPx(StackLanes.STACK, true, 7f, 5, 0.5f, 40f, 20f), 0f);
        for (int lane : new int[] {StackLanes.STACK, StackLanes.HOME, StackLanes.TERMINAL})
            assertEquals(0f, SideCards.laneTyPx(lane, false, 7f, 5, 0.5f, 40f, 20f), 0f);
    }

    /**
     * The lanes' numbers have one owner (SideCards#laneTyPx): Home's lane draws its drop and the
     * Terminal's its lift at every share of the scene, so neither moves at a landing; only the
     * stack's own lane (Home's drop and the Terminal's lift, each by its side's share) and the
     * keyboard's column, the front page's, follow the share, and the scene's value at each end is
     * that end's rest.
     */
    @Test public void eachLaneDrawsItsOwnSidesNumberAtEveryShare() {
        for (float share : new float[] {0f, 0.25f, 0.5f, 1f}) {
            for (int lift : new int[] {0, 11}) {
                String label = "share=" + share + " lift=" + lift;
                assertEquals(label, 7f, SideCards.laneTyPx(StackLanes.HOME, true, 7f, lift, share), 0f);
                assertEquals(label, -lift,
                    SideCards.laneTyPx(StackLanes.TERMINAL, true, 7f, lift, share), 0f);
                // The stack's lane holds the rows both sides show (#305: the apps row or A-Z
                // beside the keys): Home's drop by Home's share, the Terminal's lift by its own,
                // so at each rest a shared row stands where that side's own rows stand.
                assertEquals(label, -lift * share + 7f * (1f - share),
                    SideCards.laneTyPx(StackLanes.STACK, true, 7f, lift, share), 0f);
                assertEquals(label, lift * share, SideCards.columnLiftPx(true, lift, share), 0f);
                for (int lane : new int[] {StackLanes.STACK, StackLanes.HOME, StackLanes.TERMINAL})
                    assertEquals(label, 0f, SideCards.laneTyPx(lane, false, 7f, lift, share), 0f);
                assertEquals(label, 0f, SideCards.columnLiftPx(false, lift, share), 0f);
            }
        }
    }

    /**
     * Home's rows stand at Home's own rest whatever the Terminal's keyboard, while apart: they
     * stand on the keyboard's column as it is laid out (the answer's) and are drawn Home's lane's
     * number lower, and that sum is the same for the Terminal's keyboard up and down, with either
     * page in front; where the two sides' columns are one height (no bands under either
     * keyboard), it is the same with the Terminal in front as with Home. So the landing on Home
     * moves none of them.
     */
    @Test public void homesRowsStandAtHomesRestWhateverTheTerminalsKeyboard() {
        int cases = 0;
        int across = 0;
        for (Fixture f : LayoutFixtures.cardFixtures()) {
            for (boolean homeUp : new boolean[] {false, true}) {
                Float wantHome = null;
                Float wantParked = null;
                Float wantTaken = null;
                for (boolean terminalUp : new boolean[] {false, true}) {
                    RestCards atHome = RestCards.of(f.cardsRest(PageRef.HOME, homeUp, terminalUp,
                        true));
                    RestCards atTerminal = RestCards.of(f.cardsRest(PageRef.TERMINAL, terminalUp,
                        homeUp, true));
                    if (!atHome.apart()) continue;
                    String label = f.name + " home kb=" + homeUp + " terminal kb=" + terminalUp;
                    assertTrue(label, atTerminal.apart());
                    Float home = homeRowsBottom(atHome, 0f);
                    Float parked = homeRowsBottom(atTerminal, 1f);
                    if (home == null || parked == null) continue;
                    // With the keyboard up at both rests Home takes the Terminal's column
                    // (#307, 2026-11-25), so its rows stand on that column: its own rest only
                    // where Home keeps its own column.
                    com.termux.app.place.ChromeShape.Box homeColumn =
                        atHome.laidOut(RestCards.StackView.KEYBOARD_COLUMN);
                    boolean taken = homeUp && terminalUp && homeColumn != null
                        && homeColumn.equals(
                            atTerminal.laidOut(RestCards.StackView.KEYBOARD_COLUMN));
                    if (taken) {
                        if (wantTaken == null) wantTaken = home;
                        assertEquals(label + " Home takes the Terminal's column", wantTaken,
                            home, 0f);
                    } else {
                        if (wantHome == null) wantHome = home;
                        assertEquals(label + " Home in front", wantHome, home, 0f);
                    }
                    if (taken) {
                        assertEquals(label + " Terminal in front, on its own column", home,
                            parked, 0f);
                    } else {
                        if (wantParked == null) wantParked = parked;
                        assertEquals(label + " Terminal in front", wantParked, parked, 0f);
                    }
                    if (columnHeight(atHome) == columnHeight(atTerminal)) {
                        assertEquals(label + " across the landing", home, parked, 0f);
                        across++;
                    }
                    cases++;
                }
            }
        }
        assertTrue("some fixtures stand apart with a column", cases > 0);
        assertTrue("some with one column height", across > 0);
    }

    private static float columnHeight(@NonNull RestCards answer) {
        com.termux.app.place.ChromeShape.Box column =
            answer.laidOut(RestCards.StackView.KEYBOARD_COLUMN);
        return column == null ? -1f : column.height();
    }

    /** Where Home's rows are drawn to: the laid-out column's top and Home's lane's number. */
    @Nullable
    private static Float homeRowsBottom(@NonNull RestCards answer, float terminalShare) {
        com.termux.app.place.ChromeShape.Box column = answer.laidOut(RestCards.StackView.KEYBOARD_COLUMN);
        if (column == null) return null;
        return column.top + SideCards.laneTyPx(StackLanes.HOME, answer.apart(),
            answer.homeSideDropPx(), answer.terminalSideLiftPx(), terminalShare);
    }

    /**
     * The keys card is laid out where its row is: on the keyboard's column as it is laid out, in
     * both of the Terminal's keyboard states, so the card's laid-out rect is the answer's and its
     * keys are drawn where they stood under the one card (the lift above, the flush padding's
     * upper part above that).
     */
    @Test public void theKeysCardIsLaidOutOnItsRowInBothKeyboardStates() {
        int cases = 0;
        int lifted = 0;
        for (Fixture f : LayoutFixtures.cardFixtures()) {
            for (boolean up : new boolean[] {false, true}) {
                RestCards answer = RestCards.of(f.cardsRest(PageRef.TERMINAL, up, false, true));
                if (!answer.apart()) continue;
                String label = f.name + " terminal kb=" + up;
                com.termux.app.place.ChromeShape.Box keys = answer.laidOut(RestCards.StackView.KEYS_CARD);
                // The Terminal's own column while the keyboard is two cards (#307 round 3).
                com.termux.app.place.ChromeShape.Box column =
                    answer.laidOut(RestCards.StackView.TERMINAL_KEYBOARD) != null
                    ? answer.laidOut(RestCards.StackView.TERMINAL_KEYBOARD)
                    : answer.laidOut(RestCards.StackView.KEYBOARD_COLUMN);
                if (keys == null || column == null) continue;
                assertEquals(label, column.top, keys.bottom, 0f);
                if (answer.terminalSideLiftPx() > 0) lifted++;
                cases++;
            }
        }
        assertTrue("some keys cards", cases > 0);
        assertTrue("some with the keyboard down's lift", lifted > 0);
    }

    /** What the controller lays the Terminal's extra-keys card out at. */
    private static String keysLayoutOf(@NonNull SideCardsController controller,
                                       @NonNull com.termux.app.dock.KeysCardLayout card) {
        android.view.ViewGroup.MarginLayoutParams p =
            (android.view.ViewGroup.MarginLayoutParams) card.getLayoutParams();
        return "on=" + controller.keysCardOn() + " vis=" + card.getVisibility() + " margins="
            + p.leftMargin + "," + p.rightMargin + " height=" + p.height + " radius="
            + card.cornerRadiusPx() + " rect=" + controller.keysCardRestRect();
    }

    private static com.termux.app.dock.KeysCardLayout keysCard() {
        com.termux.app.dock.KeysCardLayout card =
            new com.termux.app.dock.KeysCardLayout(RuntimeEnvironment.getApplication());
        card.setLayoutParams(new FrameLayout.LayoutParams(0, 0));
        return card;
    }

    /** Symmetrically, the keys card stands at the Terminal's own rest whatever Home's keyboard. */
    @Test public void theKeysCardIsTheTerminalsOwnRestWhateverHomesKeyboard() {
        int cases = 0;
        for (Fixture f : LayoutFixtures.cardFixtures()) {
            for (boolean terminalUp : new boolean[] {false, true}) {
                com.termux.app.dock.KeysCardLayout own = keysCard();
                SideCardsController atTerminal = new SideCardsController(hostOf(f,
                    PageRef.TERMINAL, terminalUp, false, true, rendererFor(false), own));
                atTerminal.update(f.pass(PageRef.TERMINAL, terminalUp, true));
                if (!atTerminal.apart()) continue;
                String want = keysLayoutOf(atTerminal, own);
                for (boolean homeUp : new boolean[] {false, true}) {
                    String label = f.name + " terminal kb=" + terminalUp + " home kb=" + homeUp;
                    com.termux.app.dock.KeysCardLayout parkedCard = keysCard();
                    SideCardsController parked = new SideCardsController(hostOf(f,
                        PageRef.HOME, homeUp, terminalUp, true, rendererFor(true), parkedCard));
                    parked.update(f.pass(PageRef.HOME, homeUp, true));
                    assertEquals(label, want, keysLayoutOf(parked, parkedCard));
                    cases++;
                }
            }
        }
        assertTrue("some fixtures stand apart", cases > 0);
    }

    /** The rule both phases read: the front's live keyboard, the other side's own rest's. */
    @Test public void aSidesRestKeyboardIsItsOwnNeverTheFrontsForTheOtherSide() {
        for (boolean live : new boolean[] {false, true}) {
            for (boolean rest : new boolean[] {false, true}) {
                assertEquals(live, SideCards.restKeyboardUp(true, live, rest));
                assertEquals(rest, SideCards.restKeyboardUp(false, live, rest));
            }
        }
        int down = SideCards.restKeyboardPx(false, 700);
        assertFalse(SideCards.restKeyboardShown(down));
        assertEquals(0, SideCards.restKeyboardKeysPx(down));
        int up = SideCards.restKeyboardPx(true, 700);
        assertTrue(SideCards.restKeyboardShown(up));
        assertEquals(700, SideCards.restKeyboardKeysPx(up));
        // A floating-form keyboard is up with no keys in the stack.
        int floatingForm = SideCards.restKeyboardPx(true, 0);
        assertTrue(SideCards.restKeyboardShown(floatingForm));
        assertEquals(0, SideCards.restKeyboardKeysPx(floatingForm));
    }

    @Test public void withoutTheOtherSidesLayoutTheCardsNeverStandApart() {
        Fixture f = LayoutFixtures.PONG;
        StackRenderer renderer = rendererFor(true);
        final SideCardsController.Host base = hostOf(f, PageRef.HOME, false, true, renderer);
        SideCardsController controller = new SideCardsController(new SideCardsController.Host() {
            @Nullable @Override public View view(int id) { return null; }
            @Nullable @Override public PageRef frontPage() { return PageRef.HOME; }
            @Nullable @Override public PlaceLayout layoutOf(@NonNull PageRef page) { return null; }
            @NonNull @Override public DockGeometry.Snapshot dockSnapshot(@NonNull PlaceLayout layout,
                                                                         @NonNull PageKind rows) {
                return base.dockSnapshot(layout, rows);
            }
            @NonNull @Override public DisplayMetrics displayMetrics() { return metrics(f); }
            @Override public int measuredBandPx(@NonNull com.termux.app.place.Element element,
                                                @NonNull PlaceLayout layout) { return 0; }
            @Override public int measuredKeyboardPx() { return 0; }
            @Override public int restKeyboardPx(@NonNull PageRef page) {
                return SideCards.KEYBOARD_DOWN;
            }
            @Override public int bottomStatusBandPx(@NonNull PlaceLayout layout) { return 0; }
            @Override public int keptBandPx() { return 0; }
            @Override public int terminalRestFlushPaddingPx() { return 0; }
            @Override public boolean terminalFlushAllowed() { return false; }
            @Override public boolean keyboardGlassShared() { return true; }
            @Override public void sidesApart(boolean apart, int homeDropPx, int terminalLiftPx) { }
        });
        controller.update(f.pass(PageRef.HOME, false, true));
        assertFalse(controller.apart());
        assertEquals(7, controller.containerMarginPx(7));
    }

    /** Whether the Terminal's rest wears the Peek card with its keyboard down or up. */
    private static boolean wearsPeekAtEither(@NonNull Fixture f) {
        for (boolean up : new boolean[] {false, true})
            for (boolean floating : new boolean[] {false, true})
                if (com.termux.app.place.RestCards.of(f.cardsRestOf(PageRef.TERMINAL, up, floating))
                    .card(com.termux.app.place.RestCards.CardId.PEEK) != null) return true;
        return false;
    }

    /** A view that counts the layouts asked of it. */
    private static final class CountingFrame extends FrameLayout {
        int layouts;

        CountingFrame() {
            super(RuntimeEnvironment.getApplication());
            super.setLayoutParams(new android.widget.RelativeLayout.LayoutParams(0, 0));
        }

        @Override public void setLayoutParams(android.view.ViewGroup.LayoutParams params) {
            layouts++;
            super.setLayoutParams(params);
        }
    }

    /**
     * The foot's own view (#307 views A): where both hosts share the foot, it is laid out at the
     * answer's foot, Home's pinned host and the Terminal's own host stop at its top, and a second
     * pass at the same rest lays out nothing.
     */
    @Test public void theSharedFootIsLaidOutOnceAndBothHostsStopAtItsTop() {
        int shared = 0;
        try {
            for (Fixture base : LayoutFixtures.cardFixtures())
                for (DockStyle design : DockStyle.CHOICES)
                    for (PageRef front : new PageRef[] {PageRef.HOME, PageRef.TERMINAL})
                        for (boolean up : new boolean[] {false, true}) {
                Fixture f = base.withHomeDesign(design);
                CountingFrame foot = new CountingFrame();
                CountingFrame terminalHost = new CountingFrame();
                sHostViews.clear();
                sHostViews.put(R.id.dock_foot_host, foot);
                sHostViews.put(R.id.terminal_dock_host, terminalHost);
                StackRenderer renderer = rendererFor(front.isHome());
                SideCardsController controller = new SideCardsController(
                    hostOf(f, front, up, false, renderer));
                controller.update(f.pass(front, up, false));
                RestCards.Card card = controller.footCard();
                String label = f.name + " " + design + " " + front + " up=" + up;
                if (card == null) {
                    assertEquals(label, View.GONE, foot.getVisibility());
                    continue;
                }
                shared++;
                assertEquals(label, View.VISIBLE, foot.getVisibility());
                android.widget.RelativeLayout.LayoutParams footParams =
                    (android.widget.RelativeLayout.LayoutParams) foot.getLayoutParams();
                int footTop = footParams.bottomMargin + footParams.height;
                assertEquals(label, f.widthPx - footParams.leftMargin - footParams.rightMargin,
                    Math.round(card.box.width()));
                assertTrue(label + " the foot has a band", footParams.height > 0);
                // Home's pinned host stands on the foot's top, measured from the container's bottom.
                // Where the foot is the keyboard's column's (Home takes the Terminal's column,
                // #307, 2026-11-25), Home's host is held under the keyboard's cover instead and
                // stands at Home's own rest, not on the foot.
                RestCards answer = RestCards.of(f.cardsRest(front, up, up, false));
                boolean columnFoot = answer.columnFoot() != null
                    && answer.cardOn(RestCards.StackView.FOOT) == null;
                if (columnFoot) {
                    // Home's own cards are the front's only while Home is in front; the Terminal
                    // in front parks them behind its own page.
                    if (front.isHome()) {
                        boolean dockCovered = false;
                        for (RestCards.Card c : answer.covered())
                            dockCovered |= c.id == RestCards.CardId.DOCK;
                        assertTrue(label + " Home's dock is under the cover", dockCovered);
                    }
                } else if (controller.hostPinned()) {
                    android.widget.RelativeLayout.LayoutParams host =
                        new android.widget.RelativeLayout.LayoutParams(0, 0);
                    controller.layOutHost(host, 0);
                    assertEquals(label + " Home's host stops at the foot's top", footTop,
                        host.bottomMargin, 1);
                }
                // The column's foot sits under the keyboard's bands: the Terminal's host stands
                // above the keys, not on it.
                if (!columnFoot && terminalHost.getVisibility() == View.VISIBLE) {
                    android.widget.RelativeLayout.LayoutParams host =
                        (android.widget.RelativeLayout.LayoutParams) terminalHost.getLayoutParams();
                    assertEquals(label + " the Terminal's host stops at the foot's top", footTop,
                        host.bottomMargin, 1);
                }
                int footLayouts = foot.layouts;
                int hostLayouts = terminalHost.layouts;
                controller.update(f.pass(front, up, false));
                assertEquals(label + " the foot is laid out once per rest", footLayouts,
                    foot.layouts);
                assertEquals(label + " the Terminal's host is laid out once per rest",
                    hostLayouts, terminalHost.layouts);
            }
        } finally {
            sHostViews.clear();
        }
        assertTrue("some Docked rests share the foot", shared > 0);
    }
}
