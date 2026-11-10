package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.chrome.ChromeRenderer;
import com.termux.app.chrome.Dress;
import com.termux.app.chrome.DressResolver;
import com.termux.app.chrome.TestChromeRenderers;
import com.termux.app.dock.DockGeometry;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.dock.DockMorph;
import com.termux.app.dock.DockStyle;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.wall.PageRef;
import com.termux.app.wall.PaneWallController;
import com.termux.app.wall.PaneWallLayout;
import com.termux.app.wall.PaneWallPage;
import com.termux.app.wall.WallPages;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The crossing's driver (CrossingController, ADR 0018 D17) through its public interface: a real
 * {@link Crossing} over a recording adapter, a real {@link PaneWallController} moved by a
 * finger, and a Host whose every call is recorded. What is asserted is when the crossing begins,
 * what a frame writes, what the settle (done here the way the activity's settle does it, on the
 * Crossing) leaves behind, and that a frame that changes nothing writes nothing the landing would
 * have to undo.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class CrossingControllerTest {

    private static final int WIDTH = 1080;
    private static final int REVISION = 7;
    private static final float DENSITY = 2.625f;
    private static final WallShape SHAPE = WallShape.of(LayoutStyle.DOCKED, 12, 8);

    /** A window with the pinned apps and the extra keys along the bottom. */
    private static final PlaceLayout WINDOW = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.BOTTOM, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** Home: the pinned apps alone along the bottom. */
    private static final PlaceLayout HOME = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);

    /** Another Home page's layout: the A-Z index along the bottom as well. */
    private static final PlaceLayout HOME_AZ = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        true, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);

    private static ResolvedArrangement home(PlaceLayout layout, PageRef page, LayoutVariant v) {
        return ResolvedArrangement.of(layout, LayoutStyle.DOCKED, DockStyle.LENS_BEADS, true,
            true, PlaceOrientation.PORTRAIT, v, page);
    }

    private static final ResolvedArrangement HOME_ARRANGED =
        home(HOME, PageRef.HOME, LayoutVariant.HOME);
    private static final ResolvedArrangement WORKSPACE_ARRANGED = ResolvedArrangement.of(WINDOW,
        LayoutStyle.DOCKED, DockStyle.GLASS_TRAY, false, false, PlaceOrientation.PORTRAIT,
        LayoutVariant.NORMAL, PageRef.TERMINAL);

    /** One dress per kind, as the resolver answers it; both Home pages wear the Home one. */
    private static final class Stored implements DressResolver.Source {
        @Override public boolean minimal() { return false; }
        @Override public boolean homeLayout() { return true; }
        @NonNull @Override public DockStyle dockDesign(@NonNull LayoutVariant variant) {
            return variant == LayoutVariant.HOME ? DockStyle.LENS_BEADS : DockStyle.GLASS_TRAY;
        }
        @Override public boolean searchField(@NonNull LayoutVariant variant) {
            return variant == LayoutVariant.HOME;
        }
        @Override public boolean drawerTile(@NonNull LayoutVariant variant) {
            return variant == LayoutVariant.HOME;
        }
        @Override public boolean shows(@NonNull WallPages.PageKind kind, @NonNull Element element) {
            return kind != WallPages.PageKind.HOME || element != Element.EXTRA_KEYS;
        }
        @NonNull @Override public WallShape shape() { return SHAPE; }
    }

    // ---- The adapter, recorded ----

    private static final class Moves implements Crossing.Stack, Crossing.Bar, Crossing.Cover,
            Crossing.Keyboard, Crossing.Dock {
        float dockPx = Float.NaN, keyboardPx = Float.NaN, sinkPx = Float.NaN;
        float statusAlpha = Float.NaN, sideAlpha = Float.NaN, barX = Float.NaN, slide = Float.NaN;
        int coverPx = -1;
        int morphBegins, dresses, morphEnds, folds, covers, preSizes;
        final List<PaneWallPage> preRolls = new ArrayList<>();
        DockLayout leaving, arriving, landed;
        DockMorph dressed;
        /** Every write the adapter took, so a frame that moves nothing can be told. */
        int writes;

        @Override public void translate(float dock, float keyboard) {
            dockPx = dock;
            keyboardPx = keyboard;
            writes++;
        }
        @Override public void sink(float px) { sinkPx = px; writes++; }
        @Override public void fold() { folds++; writes++; }
        @Override public void alpha(float status, float side) {
            statusAlpha = status;
            sideAlpha = side;
            writes++;
        }
        @Override public void rideX(float px) { barX = px; writes++; }
        @Override public void cover(int px) { coverPx = px; covers++; writes++; }
        @Override public void preSize(int px) { preSizes++; writes++; }
        @Override public void slide(float widths) { slide = widths; writes++; }
        @Override public void preRoll(@NonNull PaneWallPage target) {
            preRolls.add(target);
            writes++;
        }
        @Override public void beginMorph(@NonNull DockLayout from, @NonNull DockLayout to) {
            morphBegins++;
            leaving = from;
            arriving = to;
            writes++;
        }
        @Override public void dress(@NonNull DockMorph morph) {
            dresses++;
            assertTrue("the same morph every frame", dressed == null || dressed == morph);
            dressed = morph;
            writes++;
        }
        @Override public void endMorph(@NonNull DockLayout rest) {
            morphEnds++;
            landed = rest;
            writes++;
        }

        int rowsPx() { return dressed.rowsPx(); }
    }

    // ---- The Host, recorded ----

    /**
     * {@link CrossingController.Host} has near a hundred methods; this is a Proxy over it that
     * answers the reads the driver makes from the fields below, a zero or null for the rest, and
     * records every call. {@link #effects} are the calls that change something: every void
     * method and the status bar's travel.
     */
    private final class FakeHost implements InvocationHandler {
        final Map<String, Integer> counts = new HashMap<>();
        final List<String> effects = new ArrayList<>();
        final CrossingController.Host proxy = (CrossingController.Host) Proxy.newProxyInstance(
            CrossingController.Host.class.getClassLoader(),
            new Class<?>[] {CrossingController.Host.class}, this);

        boolean chromeTravelMoved;
        int movedTrue;
        View stack;
        // Home's pager.
        boolean homeHome;
        float shiftFraction;
        boolean settling;
        int pageIndex;
        int pageInFront;
        final List<Runnable> settleQueue = new ArrayList<>();
        // What the settle was asked to land on.
        final List<PageRef> landedOn = new ArrayList<>();
        final List<Boolean> variantReset = new ArrayList<>();

        int count(String name) {
            Integer n = counts.get(name);
            return n == null ? 0 : n;
        }

        PlaceChromeTravel.Rest rest() {
            return new PlaceChromeTravel.Rest(false, false, true);
        }

        ResolvedArrangement arrangementOfId(int id) {
            return home(id == homeIds.get(0) ? HOME : HOME_AZ, PageRef.of(PaneWallPage.WIDGETS, id),
                LayoutVariant.HOME);
        }

        ResolvedArrangement committed() {
            return wallCtl.currentPage() == PaneWallPage.WIDGETS ? HOME_ARRANGED
                : WORKSPACE_ARRANGED;
        }

        DockGeometry.Snapshot snapshot() {
            DockGeometry.Half homeHalf = DockGeometry.Half.builder(
                homeHome ? arrangementOfId(homeIds.get(0)) : HOME_ARRANGED).build();
            DockGeometry.Half workspace = DockGeometry.Half.builder(WORKSPACE_ARRANGED).build();
            return DockGeometry.Snapshot.builder(homeHalf, workspace)
                .density(DENSITY)
                .widthPx(Math.round(411 * DENSITY))
                .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(DENSITY))
                .pinnedSlotCount(7)
                .build();
        }

        /** What the activity's settle does to the Crossing. */
        void settle(PaneWallPage page, PageRef landed) {
            landedOn.add(landed);
            crossing.land(landed, REVISION);
            crossing.end();
            chromeTravelMoved = false;
        }

        @Override
        public Object invoke(Object self, Method m, Object[] a) {
            String name = m.getName();
            counts.put(name, count(name) + 1);
            if (m.getReturnType() == void.class || name.equals("syncStatusTravel")) {
                effects.add(name);
            }
            switch (name) {
                case "hotView":
                    return a[0].equals(R.id.accessory_stack_container) ? stack : null;
                case "context": return app;
                case "preferences": return prefs;
                case "chrome": return chrome;
                case "lastWallPage":
                case "currentWallPlace": return wallCtl.currentPage();
                case "paneWallController": return wallCtl;
                case "placesOf": return a[0];
                case "wallPages": return pages;
                case "homePageInFront": return pageInFront;
                case "homeRunLast": return -1;
                case "hasWidgetPaneController": return true;
                case "homePageShiftFraction": return shiftFraction;
                case "isHomePageSettling": return settling;
                case "homePageIndex": return pageIndex;
                case "runWhenHomePageSettles": settleQueue.add((Runnable) a[0]); return null;
                case "placeLayoutStore": return store;
                case "currentPlaceOrientation": return PlaceOrientation.PORTRAIT;
                case "arrangement":
                case "committedArrangement": return committed();
                case "arrangementOf":
                    if (a[0] instanceof PageRef) {
                        PageRef p = (PageRef) a[0];
                        return p.isHome() && p.id >= 0 ? arrangementOfId(p.id)
                            : p.isHome() ? HOME_ARRANGED : WORKSPACE_ARRANGED;
                    }
                    return a[0] == PaneWallPage.WIDGETS ? HOME_ARRANGED : WORKSPACE_ARRANGED;
                case "arrangementRevision": return REVISION;
                case "arrangedPageOf":
                case "endpointPageOf": return PageRef.of((PaneWallPage) a[0]);
                case "dressOf":
                    return a[0] instanceof PageRef ? dresses.dressOf(((PageRef) a[0]).kind)
                        : dresses.dressOf((PaneWallPage) a[0]);
                case "dressLayoutOf": {
                    PageRef p = (PageRef) a[0];
                    if (p.isHome() && p.id >= 0)
                        return p.id == homeIds.get(0) ? LayoutVariant.HOME : LayoutVariant.NORMAL;
                    return dresses.dressOf(p.kind).layout();
                }
                case "stackDressPage": return wallCtl.currentPage();
                case "dockSnapshot": return snapshot();
                case "dockHalf":
                    return DockGeometry.Half.builder(a[0] instanceof PageRef
                        && ((PageRef) a[0]).id >= 0 ? arrangementOfId(((PageRef) a[0]).id)
                        : ((PageRef) a[0]).isHome() ? HOME_ARRANGED : WORKSPACE_ARRANGED).build();
                case "dockTravelTranslationPx":
                    return Float.isNaN(moves.dockPx) ? 0f : moves.dockPx;
                case "chromeRestOf": return rest();
                case "chromeRestStates": return (PlaceChromeTravel.States) place -> rest();
                case "frameStandsDown": return (SeamCrossing.FrameGlass) () -> false;
                case "chromeTravelMoved": return chromeTravelMoved;
                case "setChromeTravelMoved":
                    chromeTravelMoved = (Boolean) a[0];
                    if (chromeTravelMoved) movedTrue++;
                    return null;
                case "settlePlaceChrome":
                    settle((PaneWallPage) a[0], (PageRef) a[1]);
                    return null;
                case "setAppliedChromeVariant": variantReset.add(a[0] == null); return null;
                default:
                    break;
            }
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return false;
            if (r == int.class) return 0;
            if (r == float.class) return 0f;
            return null;
        }
    }

    private Application app;
    private TermuxAppSharedPreferences prefs;
    private PlaceLayoutStore store;
    private ChromeRenderer chrome;
    private DressResolver dresses;
    private WallPages pages;
    private List<Integer> homeIds;
    private Moves moves;
    private Crossing crossing;
    private FakeHost host;
    private CrossingController controller;
    private PaneWallController wallCtl;
    private PaneWallLayout wall;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        SharedPreferences sp = app.getSharedPreferences("crossing-controller-test",
            Context.MODE_PRIVATE);
        sp.edit().clear().commit();
        prefs = new TermuxAppSharedPreferences(app, sp, null);
        store = new PlaceLayoutStore(prefs);
        chrome = TestChromeRenderers.overFakeSurfaces(app);
        dresses = new DressResolver(new Stored());
        WallPages p = WallPages.EMPTY.add(WallPages.PageKind.HOME, WallPages.END, null).pages;
        pages = p.add(WallPages.PageKind.HOME, WallPages.END, null).pages;
        homeIds = pages.homePageIdsForRepository();
        assertEquals(2, homeIds.size());

        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        PaneWallLayout layout = new PaneWallLayout(activity);
        View terminal = new FrameLayout(activity);
        View widgets = new FrameLayout(activity);
        layout.addView(widgets);
        layout.addView(terminal);
        wallCtl = new PaneWallController(layout, new PaneWallController.Host() {
            @Override public boolean reducedMotion() { return true; }
        });
        wallCtl.attachTerminalPage(terminal);
        layout.setPageView(PaneWallPage.WIDGETS, widgets);
        layout.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY));
        layout.layout(0, 0, WIDTH, 1800);
        wall = wallCtl.wall();

        moves = new Moves();
        crossing = new Crossing(moves, moves, moves, moves, moves);
        host = new FakeHost();
        // The stack the travel reads its height from: 200 px of rows over a 20 px margin.
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 200);
        lp.bottomMargin = 20;
        host.stack = new View(activity);
        host.stack.setLayoutParams(lp);
        controller = new CrossingController(crossing, host.proxy);
        host.pageInFront = homeIds.get(0);
        // The wall starts on the Terminal; Home is where these gestures begin.
        wallCtl.goHome(false);
        assertEquals(PaneWallPage.WIDGETS, wallCtl.currentPage());
        assertFalse(wall.isMoving());
    }

    // ---- The wall, driven ----

    /** One frame of the wall under a finger {@code dxPx} from where it went down. */
    private void drag(float dxPx) {
        wall.dragTo(dxPx);
        controller.syncChromeTravel(wall.offsetPx());
    }

    /** The finger lets go at {@code velocity}; the (reduced-motion) wall snaps to where it lands. */
    private void release(float velocity) {
        wall.endDrag(velocity);
        assertFalse("the wall is at rest", wall.isMoving());
        assertEquals(0f, wall.offsetPx(), 0f);
    }

    private static PageRef ref(PaneWallPage page) {
        return PageRef.of(page);
    }

    /** The two ends' dock rows, in px, of the rest the row is dressed to. */
    private int restRowsPx(DockLayout rest) {
        return DockMorph.rowsPx(rest);
    }

    private void goTo(PaneWallPage page) {
        wallCtl.goTo(page, false);
        assertEquals(page, wallCtl.currentPage());
        assertFalse(wall.isMoving());
    }

    // ---- 1. Home to Terminal, Terminal to Home ----

    @Test
    public void homeToTerminalBeginsOnTheFirstFrameFramesThenLandsAndRestWritesNothing() {
        crossingAcross(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, -1f);
    }

    @Test
    public void terminalToHomeBeginsOnTheFirstFrameFramesThenLandsAndRestWritesNothing() {
        goTo(PaneWallPage.TERMINAL);
        crossingAcross(PaneWallPage.TERMINAL, PaneWallPage.WIDGETS, 1f);
    }

    private void crossingAcross(PaneWallPage from, PaneWallPage to, float sign) {
        // At rest nothing is written and nothing begins.
        controller.syncChromeTravel(0f);
        assertFalse(crossing.crossing());
        assertTrue("a wall at rest writes nothing", host.effects.isEmpty());
        assertEquals(0, moves.writes);

        wall.beginPlainDrag();
        assertTrue(wall.isMoving());
        // The first frame that reaches the other half begins the crossing, once.
        drag(sign * 20f);
        assertTrue("the crossing began", crossing.crossing());
        assertEquals(to, crossing.seam().other());
        assertEquals(1, host.count("flushLandingRestyle"));
        assertEquals(1, host.count("beginNavStripFade"));
        assertEquals(1, host.count("beginTopBandFade"));
        assertEquals(1, moves.morphBegins);
        assertTrue("something moved with the first frame", host.movedTrue > 0);
        DockLayout fromRest = moves.leaving;
        DockLayout toRest = moves.arriving;
        assertEquals(restRowsPx(CrossingTest.restOf(from)), restRowsPx(fromRest));
        assertEquals(restRowsPx(CrossingTest.restOf(to)), restRowsPx(toRest));
        int start = restRowsPx(fromRest);
        int end = restRowsPx(toRest);
        assertTrue("the two halves' docks differ", start != end);

        // A few frames: the dock's dress, the stack's sink and the status alpha only go one way.
        int last = moves.rowsPx();
        float lastTranslation = moves.dockPx;
        for (float f = 0.1f; f <= 0.91f; f += 0.1f) {
            drag(sign * f * WIDTH);
            assertTrue(crossing.crossing());
            assertEquals("nothing begins again", 1, moves.morphBegins);
            assertEquals(1, host.count("beginNavStripFade"));
            assertEquals(1, host.count("beginTopBandFade"));
            int rows = moves.rowsPx();
            int dir = Integer.signum(rows - last);
            assertTrue("dress rows move toward " + to + " at " + f + ": " + last + " -> " + rows,
                dir == 0 || dir == Integer.signum(end - start));
            last = rows;
            // The stack is only ever taken down or faded out on the way, never back up.
            assertTrue("stack translation grows or holds at " + f + ": " + lastTranslation
                + " -> " + moves.dockPx, moves.dockPx >= lastTranslation - 0.001f);
            lastTranslation = moves.dockPx;
            assertTrue("status alpha " + moves.statusAlpha,
                moves.statusAlpha >= 0f && moves.statusAlpha <= 1f);
            assertTrue("side alpha " + moves.sideAlpha,
                moves.sideAlpha >= 0f && moves.sideAlpha <= 1f);
        }
        drag(sign * WIDTH);
        assertEquals("at the far end the row wears the arriving rest", end, moves.rowsPx());
        assertTrue(host.chromeTravelMoved);

        // The finger lets go past the middle: the wall lands on the other page, the settle lands
        // the crossing.
        release(sign * 3000f);
        assertEquals(to, wallCtl.currentPage());
        assertTrue("the crossing waits for the settle", crossing.crossing());
        host.settle(to, ref(to));
        assertFalse(crossing.crossing());
        assertTrue("nothing of the crossing is held", crossing.idle());
        assertEquals(1, moves.morphEnds);
        assertEquals(restRowsPx(toRest), restRowsPx(moves.landed));
        assertEquals(0f, moves.dockPx, 0f);
        assertEquals(1f, moves.statusAlpha, 0f);

        // The rest frame after it writes nothing.
        int effects = host.effects.size();
        int writes = moves.writes;
        controller.syncChromeTravel(wall.offsetPx());
        assertEquals("no host effect at rest after the landing", effects, host.effects.size());
        assertEquals("no adapter write at rest after the landing", writes, moves.writes);
        assertFalse(crossing.crossing());
        assertEquals(1, moves.morphBegins);
    }

    // ---- 2. Abandoned and returned ----

    @Test
    public void aGestureTakenBackToTheStartLandsOnTheCommittedPageAndBeginsOnce() {
        wall.beginPlainDrag();
        drag(-100f);
        assertTrue(crossing.crossing());
        drag(-300f);
        drag(-500f);
        drag(-300f);
        drag(-100f);
        assertEquals("nothing begins twice", 1, moves.morphBegins);
        assertEquals(1, host.count("beginNavStripFade"));
        assertEquals(1, host.count("beginTopBandFade"));
        assertEquals(1, host.count("flushLandingRestyle"));
        // Back at the start, the finger still down: the row stands the committed rest.
        drag(0f);
        assertTrue(wall.isMoving());
        assertEquals(restRowsPx(moves.leaving), moves.rowsPx());
        assertEquals("still one crossing", 1, moves.morphBegins);

        // Let go with no speed: the wall stays on Home and the settle lands the committed page.
        release(0f);
        assertEquals(PaneWallPage.WIDGETS, wallCtl.currentPage());
        assertTrue(crossing.crossing());
        host.settle(PaneWallPage.WIDGETS, ref(PaneWallPage.WIDGETS));
        assertEquals(PageRef.HOME, host.landedOn.get(host.landedOn.size() - 1));
        assertTrue(crossing.idle());
        assertEquals(1, moves.morphEnds);
        assertEquals("the landed rest is the committed one", restRowsPx(moves.leaving),
            restRowsPx(moves.landed));

        int effects = host.effects.size();
        int writes = moves.writes;
        controller.syncChromeTravel(wall.offsetPx());
        assertEquals(effects, host.effects.size());
        assertEquals(writes, moves.writes);
        assertEquals("nothing began again at rest", 1, moves.morphBegins);
    }

    // ---- 3. The same offset twice ----

    @Test
    public void aRepeatedOffsetLaysNothingOutAgain() {
        wall.beginPlainDrag();
        drag(-200f);
        drag(-400f);
        assertTrue(crossing.crossing());
        float offset = wall.offsetPx();

        int layouts = host.count("syncPlaceLayout");
        int restyles = host.count("restyleDockSurface");
        int preRollKeyboard = host.count("preRollTravelKeyboard");
        int preRollContent = host.count("preRollTravelContent");
        int begins = host.count("beginNavStripFade") + host.count("beginTopBandFade")
            + host.count("beginTravelHold") + host.count("flushLandingRestyle");
        int geometry = host.count("geometryRequestNow");
        int morphBegins = moves.morphBegins;
        int writes = moves.writes;
        int dresses = moves.dresses;
        float dock = moves.dockPx, sink = moves.sinkPx, status = moves.statusAlpha,
            side = moves.sideAlpha, slide = moves.slide;
        int rows = moves.rowsPx();

        controller.syncChromeTravel(offset);

        assertEquals(layouts, host.count("syncPlaceLayout"));
        assertEquals(restyles, host.count("restyleDockSurface"));
        assertEquals(preRollKeyboard, host.count("preRollTravelKeyboard"));
        assertEquals(preRollContent, host.count("preRollTravelContent"));
        assertEquals(begins, host.count("beginNavStripFade") + host.count("beginTopBandFade")
            + host.count("beginTravelHold") + host.count("flushLandingRestyle"));
        assertEquals("no geometry pass requested again", geometry,
            host.count("geometryRequestNow"));
        assertEquals(morphBegins, moves.morphBegins);
        assertTrue(moves.preRolls.isEmpty());
        // Observed: the repeat is not skipped. The crossing's answers are written again, to the
        // same values: the dock's dress (the same morph), the stack's translation, sink, slide
        // and the alphas. Nothing is laid out and nothing is begun.
        assertTrue("the adapter is written again, to the same values", moves.writes > writes);
        assertEquals(dresses + 1, moves.dresses);
        assertEquals(dock, moves.dockPx, 0f);
        assertEquals(sink, moves.sinkPx, 0f);
        assertEquals(status, moves.statusAlpha, 0f);
        assertEquals(side, moves.sideAlpha, 0f);
        assertEquals(slide, moves.slide, 0f);
        assertEquals(rows, moves.rowsPx());
    }

    // ---- 4. Home to Home ----

    /** The pager's frame: {@code fraction} of the width, negative toward the next page. */
    private void pager(float fraction, boolean settling) {
        host.shiftFraction = fraction;
        host.settling = settling;
        controller.syncHomePagesTravel();
    }

    private void homeRest() {
        host.homeHome = true;
        host.pageIndex = 0;
        host.pageInFront = homeIds.get(0);
    }

    @Test
    public void homeToHomeBeginsOnTheFirstShiftedFrameFramesAndLandsOnTheSettle() {
        homeRest();
        // At rest, nothing.
        pager(0f, false);
        assertFalse(crossing.crossing());
        assertTrue(host.effects.isEmpty());

        pager(-0.05f, false);
        assertTrue("begun on the pager's first shifted frame", crossing.crossing());
        assertTrue(crossing.seam().oneKind());
        assertEquals(1, moves.morphBegins);
        int start = restRowsPx(moves.leaving);
        int end = restRowsPx(moves.arriving);
        assertTrue("the two pages' docks differ", start != end);

        int last = moves.rowsPx();
        for (float f = 0.1f; f <= 0.91f; f += 0.1f) {
            pager(-f, false);
            assertEquals(1, moves.morphBegins);
            int rows = moves.rowsPx();
            int dir = Integer.signum(rows - last);
            assertTrue("rows move toward the other page at " + f,
                dir == 0 || dir == Integer.signum(end - start));
            last = rows;
        }
        pager(-1f, false);
        assertEquals("the dock rows morphed to the other page's", end, moves.rowsPx());
        assertEquals("nothing landed while the finger is down", 0, host.landedOn.size());

        // The pager lets go and settles on the next page: the landing waits for the settle.
        pager(-0.95f, true);
        assertEquals(1, host.settleQueue.size());
        pager(-0.97f, true);
        assertEquals("queued once", 1, host.settleQueue.size());
        assertEquals(0, host.landedOn.size());

        host.pageIndex = 1;
        host.pageInFront = homeIds.get(1);
        host.shiftFraction = 0f;
        host.settling = false;
        host.settleQueue.get(0).run();
        assertFalse(crossing.crossing());
        assertTrue(crossing.idle());
        assertEquals(1, host.landedOn.size());
        assertEquals(PageRef.of(PaneWallPage.WIDGETS, homeIds.get(1)), host.landedOn.get(0));
        assertEquals("settlePlaceChrome(WIDGETS, landed)", 1, host.count("settlePlaceChrome"));
        assertEquals("landed on the other page: its variant is applied again", 1,
            host.variantReset.size());
        assertTrue(host.variantReset.get(0));
        assertEquals(1, host.count("syncChromeArrangement"));
        assertEquals(1, moves.morphEnds);
        assertEquals(end, restRowsPx(moves.landed));

        // A rest frame after it writes nothing.
        int effects = host.effects.size();
        int writes = moves.writes;
        pager(0f, false);
        assertEquals(effects, host.effects.size());
        assertEquals(writes, moves.writes);
    }

    @Test
    public void homeToHomeTakenBackLandsOnTheCommittedPageWithoutReapplyingTheLayout() {
        homeRest();
        pager(-0.1f, false);
        assertTrue(crossing.crossing());
        pager(-0.4f, false);
        pager(-0.2f, false);
        // The finger lets go short of the middle: the pager settles back to page one.
        pager(-0.15f, true);
        assertEquals(1, host.settleQueue.size());
        host.shiftFraction = 0f;
        host.settling = false;
        host.settleQueue.get(0).run();
        assertFalse(crossing.crossing());
        assertEquals(1, moves.morphBegins);
        assertEquals(1, host.landedOn.size());
        assertEquals(PageRef.of(PaneWallPage.WIDGETS, homeIds.get(0)), host.landedOn.get(0));
        assertEquals(1, host.count("settlePlaceChrome"));
        assertEquals("landed on the committed page: no variant reset", 0,
            host.variantReset.size());
        assertEquals(0, host.count("syncChromeArrangement"));
        assertEquals(restRowsPx(moves.leaving), restRowsPx(moves.landed));
    }

    @Test
    public void homeToHomeThatComesBackToRestWithNoSettleLandsAtOnce() {
        homeRest();
        pager(-0.3f, false);
        assertTrue(crossing.crossing());
        // The shift reads zero with no settle to wait for: the crossing lands in that frame.
        pager(0f, false);
        assertFalse(crossing.crossing());
        assertEquals(1, host.count("settlePlaceChrome"));
        assertEquals(PageRef.of(PaneWallPage.WIDGETS, homeIds.get(0)), host.landedOn.get(0));
        assertEquals(0, host.variantReset.size());
        assertTrue("no settle was waited for", host.settleQueue.isEmpty());
    }
}
