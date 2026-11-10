package com.termux.app.dock;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.robolectric.RuntimeEnvironment;

import com.termux.R;
import com.termux.app.AzScrubRowView;
import com.termux.app.SuggestionBarView;
import com.termux.app.chrome.ChromeSpec;
import com.termux.app.chrome.TestChromeRenderers;
import com.termux.app.chrome.ChromeRenderer;
import com.termux.app.chrome.GlassRefraction;
import com.termux.app.place.ChromeShape;
import com.termux.app.place.Element;
import com.termux.app.place.LayoutVariant;
import com.termux.app.place.PlaceChromePolicy;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.ResolvedArrangement;
import com.termux.app.place.SeamCrossing;
import com.termux.app.place.layout.IconSize;
import com.termux.app.wall.PageRef;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.LayoutStyle;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The dock's controller driven through a fake {@link DockController.Host}: which beads it wants,
 * shadows and draws for the crossing's bead share and the design shown.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class DockControllerTest {

    private FakeHost mHost;
    private DockController mDock;

    @Before
    public void setUp() {
        mHost = new FakeHost();
        mDock = new DockController(mHost);
    }

    @Test
    public void beadsAreWantedOnlyWithPreferencesAndAShareOfTheBeadedHalf() {
        mHost.prefs = false;
        mHost.design = DockStyle.LENS_BEADS;
        assertFalse("no preferences, no beads", mDock.dockBeadsWanted());

        mHost.prefs = true;
        mHost.share = Float.NaN;
        assertTrue("Lens beads at rest", mDock.dockBeadsWanted());
        mHost.share = 0.4f;
        assertTrue("Lens beads while some of their half shows", mDock.dockBeadsWanted());
        mHost.share = 0f;
        assertFalse("Lens beads carried wholly off", mDock.dockBeadsWanted());

        mHost.design = DockStyle.GLASS_TRAY;
        mHost.share = Float.NaN;
        assertFalse("a slab at rest", mDock.dockBeadsWanted());
        mHost.share = 0.3f;
        assertTrue("a slab across the seam from the beaded half", mDock.dockBeadsWanted());
        mHost.share = 0f;
        assertFalse("a slab with none of the beaded half showing", mDock.dockBeadsWanted());
    }

    @Test
    public void theShadowsFollowTheMovingBeadsOnlyWhileThereAreBeads() {
        LensBeadGeometry.Beads rest = sevenBeads();
        LensBeadGeometry.Beads moving = rest.offset(-40f, 0f);
        assertNull(mDock.dockBeadsShadowed());

        ReflectionHelpers.setField(mDock, "mDockBeadsMoving", moving);
        assertNull("moving beads with no beads at rest shadow nothing", mDock.dockBeadsShadowed());

        ReflectionHelpers.setField(mDock, "mDockBeads", rest);
        assertSame(moving, mDock.dockBeadsShadowed());

        ReflectionHelpers.setField(mDock, "mDockBeadsMoving", null);
        assertSame(rest, mDock.beads());
        assertSame(rest, mDock.dockBeadsShadowed());
    }

    @Test
    public void theGlassDrawsTheShadowedBeadsUnlessAMeltSwellsThem() {
        LensBeadGeometry.Beads rest = sevenBeads();
        ReflectionHelpers.setField(mDock, "mDockBeads", rest);

        mHost.share = Float.NaN;
        assertSame("at rest the glass draws the beads themselves", rest, mDock.dockBeadsDrawn());
        mHost.share = 1f;
        assertSame("all of the beaded half: no melt", rest, mDock.dockBeadsDrawn());

        mHost.share = 0.5f;
        mHost.surfaceHost = null;
        assertSame("no host to measure the cover: unswollen", rest, mDock.dockBeadsDrawn());

        FrameLayout host = new FrameLayout(RuntimeEnvironment.getApplication());
        host.layout(0, 0, 1080, 200);
        mHost.surfaceHost = host;
        LensBeadGeometry.Beads drawn = mDock.dockBeadsDrawn();
        assertNotNull(drawn);
        float cover = LensBeadGeometry.coverRadiusPx(rest, 1080f, 200f);
        assertEquals(LensBeadGeometry.meltRadiusPx(rest.radiusPx, cover, 0.5f), drawn.radiusPx,
            0.001f);
        assertTrue("a melt swells the beads", drawn.radiusPx > rest.radiusPx);
        assertEquals(LensBeadGeometry.beadShelfAlpha(0.5f), mDock.dockBeadShelfAlpha(), 0f);
    }

    /** Seven 120 px icons, 154 px apart, on a 1080 x 200 host (as DockBeadsLeaveTest). */
    private static LensBeadGeometry.Beads sevenBeads() {
        float[] boxes = new float[7 * 4];
        for (int i = 0; i < 7; i++) {
            float cx = 77f + 154f * i;
            boxes[i * 4] = cx - 60f;
            boxes[i * 4 + 1] = 40f;
            boxes[i * 4 + 2] = cx + 60f;
            boxes[i * 4 + 3] = 160f;
        }
        LensBeadGeometry.Beads beads = LensBeadGeometry.compute(boxes, 7, 0f, 0f, 1080f, 200f, 2.625f);
        assertNotNull("the fixture row must wear beads", beads);
        return beads;
    }

    // ------------------------------------------------------------------ Plank effects and touch light

    /** A 400 x 100 dock stack at the window's origin, the plank the springs are wired to. */
    private View plankAtOrigin() {
        FrameLayout stack = new FrameLayout(RuntimeEnvironment.getApplication());
        stack.layout(0, 0, 400, 100);
        mHost.views.put(R.id.accessory_stack_container, stack);
        return stack;
    }

    private static MotionEvent touch(int action, float x, float y) {
        return MotionEvent.obtain(0L, 0L, action, x, y, 0);
    }

    private boolean plankTouchInside() {
        return ReflectionHelpers.getField(mDock, "mDockPlankTouchInside");
    }

    @Test
    public void aPressOnThePlankDrivesItsSpringsAndAPressOffItDoesNot() {
        mDock.feedDockPlank(touch(MotionEvent.ACTION_DOWN, 200f, 50f));
        assertFalse("no springs before the dock's views are wired", plankTouchInside());

        View plank = plankAtOrigin();
        mDock.setupDockPlankFx();
        assertNotNull(mDock.dockPlankController());
        assertSame(plank, ReflectionHelpers.getField(mDock, "mDockPlankTarget"));

        mDock.feedDockPlank(touch(MotionEvent.ACTION_DOWN, 200f, 50f));
        assertTrue("a press on the plank", plankTouchInside());
        mDock.feedDockPlank(touch(MotionEvent.ACTION_UP, 200f, 50f));
        assertFalse("the lift lets go", plankTouchInside());

        mDock.feedDockPlank(touch(MotionEvent.ACTION_DOWN, 200f, 300f));
        assertFalse("a press below the plank", plankTouchInside());
    }

    @Test
    public void aPressOnAKeySurfaceRidingThePlankLeavesItsSpringsAlone() {
        plankAtOrigin();
        FrameLayout keys = new FrameLayout(RuntimeEnvironment.getApplication());
        keys.layout(0, 0, 400, 60);
        mHost.views.put(R.id.inapp_keyboard_container, keys);
        mDock.setupDockPlankFx();

        mDock.feedDockPlank(touch(MotionEvent.ACTION_DOWN, 200f, 30f));
        assertFalse("a key under the finger", plankTouchInside());
        mDock.feedDockPlank(touch(MotionEvent.ACTION_DOWN, 200f, 80f));
        assertTrue("the dock below the keys", plankTouchInside());
    }

    @Test
    public void theDrawerTakingTheGestureHandsThePlankBack() {
        plankAtOrigin();
        mDock.setupDockPlankFx();
        mDock.feedDockPlank(touch(MotionEvent.ACTION_DOWN, 200f, 50f));
        assertTrue(plankTouchInside());
        mHost.drawerEngaged = true;
        mDock.feedDockPlank(touch(MotionEvent.ACTION_MOVE, 200f, 40f));
        assertFalse("the drawer's drag owns the views now", plankTouchInside());
    }

    @Test
    public void reducedMotionReachesTheSprings() {
        plankAtOrigin();
        mHost.reducedMotion = true;
        mDock.setupDockPlankFx();
        assertSpringsReduced(true);
        mHost.reducedMotion = false;
        mDock.setupDockPlankFx();
        assertSpringsReduced(false);
    }

    private void assertSpringsReduced(boolean reduced) {
        boolean held = ReflectionHelpers.getField(mDock.dockPlankController(), "mReducedMotion");
        assertEquals(reduced, held);
    }

    @Test
    public void belowApi33APressLightsNothingAndBuildsNoFade() {
        plankAtOrigin();
        mDock.feedDockTouchLight(touch(MotionEvent.ACTION_DOWN, 200f, 50f));
        assertFalse(ReflectionHelpers.<Boolean>getField(mDock, "mDockTouchLightDown"));
        assertNull(ReflectionHelpers.getField(mDock, "mDockTouchFade"));
    }

    // ------------------------------------------------------------------ Layout and dress

    /** A Home layout: the pinned apps alone along the bottom. */
    private static final PlaceLayout HOME = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** A window with the pinned apps and the extra keys along the bottom. */
    private static final PlaceLayout WINDOW = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.BOTTOM, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);

    /** Thrown where the dock is worked out again rather than read from the applied one. */
    private static final class Recomputed extends RuntimeException {}

    @Test
    public void theAppliedDockIsReadBackOnlyForItsArrangementAndStoreRevision() {
        PlaceLayoutStore store = new PlaceLayoutStore(
            TermuxAppSharedPreferences.build(RuntimeEnvironment.getApplication(), true));
        mHost.store = store;
        DockLayout dock = TestDockLayouts.capsule(2.625f, 0);
        mDock.applyDockLayout(dock, HOME);
        mHost.recomputeTrips = true;

        assertSame("the same arrangement at the same revision", dock, mDock.dockLayoutIn(HOME));
        mHost.layout = HOME;
        assertSame(dock, mDock.appliedDockLayout());
        assertRecomputed("another arrangement", () -> mDock.dockLayoutIn(WINDOW));

        ReflectionHelpers.<java.util.concurrent.atomic.AtomicInteger>getField(store, "mRevision")
            .incrementAndGet();
        assertRecomputed("the store moved under the same arrangement",
            () -> mDock.dockLayoutIn(HOME));

        mHost.recomputeTrips = false;
        mDock.applyDockLayout(dock);
        mHost.recomputeTrips = true;
        assertRecomputed("a dock applied with no arrangement named",
            () -> mDock.dockLayoutIn(HOME));
    }

    private static void assertRecomputed(String message, Runnable read) {
        try {
            read.run();
        } catch (Recomputed expected) {
            return;
        }
        throw new AssertionError(message + ": the applied dock was handed back");
    }

    @Test
    public void aSettleBetweenPagesOfOneDressLeavesTheDockAlone() {
        mHost.chrome = TestChromeRenderers.overFakeSurfaces(RuntimeEnvironment.getApplication());
        mHost.design = DockStyle.GLASS_TRAY;
        mHost.terminalDesign = DockStyle.GLASS_TRAY;
        mDock.restyleDockSurfaceForPageIfChanged(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL);
        assertEquals("no restyle", 0, mHost.barHeightPasses);
        assertEquals(0, mHost.toolbarPasses);
        assertFalse(mDock.slabWears(DockStyle.GLASS_TRAY, false));
    }

    @Test
    public void aSettleBetweenPagesOfDifferentDesignsRestylesTheDockOnce() {
        mHost.chrome = TestChromeRenderers.overFakeSurfaces(RuntimeEnvironment.getApplication());
        mHost.design = DockStyle.GLASS_TRAY;
        mHost.terminalDesign = DockStyle.LENS_BEADS;
        mHost.lastPage = PaneWallPage.TERMINAL;
        mDock.restyleDockSurfaceForPageIfChanged(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL);
        assertEquals("one restyle", 1, mHost.barHeightPasses);
        assertEquals(1, mHost.toolbarPasses);
        assertTrue("dressed for the page landed on", mDock.slabWears(DockStyle.LENS_BEADS, false));
    }

    @Test
    public void aSettleNeedsPreferencesAndTheChrome() {
        mHost.design = DockStyle.GLASS_TRAY;
        mHost.terminalDesign = DockStyle.LENS_BEADS;
        mDock.restyleDockSurfaceForPageIfChanged(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL);
        assertEquals("no chrome yet", 0, mHost.barHeightPasses);
    }

    /** The dock laid out and shown: the stack up, the apps row in the row stack. */
    private void dockInView() {
        FrameLayout stack = new FrameLayout(RuntimeEnvironment.getApplication());
        stack.layout(0, 0, 400, 100);
        FrameLayout rowStack = new FrameLayout(RuntimeEnvironment.getApplication());
        FrameLayout rowHost = new FrameLayout(RuntimeEnvironment.getApplication());
        rowStack.addView(rowHost);
        mHost.views.put(R.id.accessory_stack_container, stack);
        mHost.views.put(R.id.accessory_row_stack, rowStack);
        mHost.views.put(R.id.apps_bar_row_host, rowHost);
        mHost.rows.apply(new ChromeSpec(PlaceChromePolicy.dockShown(HOME), false, 0, true,
            PlaceChromePolicy.appsRowShown(HOME), PlaceChromePolicy.azRowOnDock(HOME),
            PlaceChromePolicy.extraKeysRowShown(HOME), 0.8f, 24), false, false);
    }

    @Test
    public void aPickedDesignThatBringsTheFieldEasesTheSlabAndASettleCancelsIt() {
        mHost.chrome = TestChromeRenderers.overFakeSurfaces(RuntimeEnvironment.getApplication());
        mHost.fieldWanted = true;
        dockInView();
        assertTrue("an ease starts", mDock.beginDockBandEase());
        assertTrue(mDock.dockBandEaseRunning());
        assertEquals("from what the slab shows", 0f, mDock.promptFieldEaseValue(), 0f);

        mHost.design = DockStyle.GLASS_TRAY;
        mHost.terminalDesign = DockStyle.LENS_BEADS;
        mDock.restyleDockSurfaceForPageIfChanged(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL);
        assertFalse("the settle lands the geometry at once", mDock.dockBandEaseRunning());
        assertEquals("and restyles once", 1, mHost.barHeightPasses);
    }

    @Test
    public void noEaseUnderReducedMotionOrWithNothingToEase() {
        mHost.chrome = TestChromeRenderers.overFakeSurfaces(RuntimeEnvironment.getApplication());
        dockInView();
        assertFalse("nothing comes or goes", mDock.beginDockBandEase());
        mHost.fieldWanted = true;
        mHost.reducedMotion = true;
        assertFalse("reduced motion lands it at once", mDock.beginDockBandEase());
        assertFalse(mDock.dockBandEaseRunning());
    }

    @Test
    public void aDockNotInViewIsNotEased() {
        mHost.chrome = TestChromeRenderers.overFakeSurfaces(RuntimeEnvironment.getApplication());
        mHost.fieldWanted = true;
        assertFalse(mDock.beginDockBandEase());
        assertFalse(mDock.dockBandEaseRunning());
    }

    // ------------------------------------------------------------------ Surface

    @Test
    public void theDockFloatsUnderFloatingUnlessThePeekCardStands() {
        mHost.floating = false;
        mHost.peekWanted = true;
        assertFalse("Docked never floats", mDock.dockFloats());
        assertEquals("Docked never asks after the card", 0, mHost.peekAsks);

        mHost.floating = true;
        mHost.peekWanted = false;
        assertTrue("Floating with no card is the capsule", mDock.dockFloats());
        mHost.peekWanted = true;
        assertFalse("the Peek card rises from the edge as Docked", mDock.dockFloats());

        mHost.floating = false;
        assertTrue("measured in Floating, the card still stands down",
            !mDock.dockFloatsIn(true));
        mHost.peekWanted = false;
        assertTrue(mDock.dockFloatsIn(true));
        assertFalse(mDock.dockFloatsIn(false));
    }

    @Test
    public void theCapsuleRadiusIsCornersCappedAtHalfTheSurface() {
        mHost.cornersPx = 60f;
        assertEquals("Corners on a tall surface", 60f,
            mDock.resolveDockCapsuleCornerRadiusPx(400), 0f);
        assertEquals("half of a short one", 40f, mDock.resolveDockCapsuleCornerRadiusPx(80), 0f);
        assertEquals("no surface, no radius", 0f, mDock.resolveDockCapsuleCornerRadiusPx(-10), 0f);
        assertEquals("the unbounded ask is Corners", 60f,
            mDock.resolveDockCapsuleCornerRadiusPx(Integer.MAX_VALUE), 0f);
    }

    @Test
    public void theGlassCoverIsTheStackPagesOpacityOrNoneBeforePreferences() {
        assertEquals("no preferences", 0f, mDock.dockGlassCover(), 0f);
        mHost.prefsStore = TermuxAppSharedPreferences.build(RuntimeEnvironment.getApplication(), true);
        float expected = mHost.prefsStore.getAppBarOpacity(mHost.stackLookPage()) / 100f;
        assertEquals(expected, mDock.dockGlassCover(), 0f);
    }

    @Test
    public void theProgramEdgeRecutsTheHostOnlyWhenItMovesAndOnlyOnceCut() {
        FrameLayout host = new FrameLayout(RuntimeEnvironment.getApplication());
        host.setId(R.id.accessory_surface_host);
        host.layout(0, 0, 400, 100);
        mHost.surfaceHost = host;
        mHost.peekStands = true;

        mDock.setDockProgramEdge(true);
        assertTrue(mDock.dockGlassDrawsEdge());
        assertEquals("nothing cut yet, nothing re-cut", 0, mHost.peekDresses);

        mDock.applyDockSurfaceShape(host, true, 100, true, true);
        assertEquals(1, mHost.peekDresses);
        assertTrue(mDock.dockShapeCut());
        assertTrue(mDock.dockShapeCapsule());
        assertEquals(100, mDock.dockShapeHeightPx());

        mDock.setDockProgramEdge(true);
        assertEquals("the same edge re-cuts nothing", 1, mHost.peekDresses);
        mDock.setDockProgramEdge(false);
        assertFalse(mDock.dockGlassDrawsEdge());
        assertEquals("a moved edge re-cuts the host as last cut", 2, mHost.peekDresses);
    }

    @Test
    public void theHostDropsItsPaddingAndLensBeadsLeaveItUnclipped() {
        FrameLayout host = new FrameLayout(RuntimeEnvironment.getApplication());
        host.setId(R.id.accessory_surface_host);
        host.setPadding(4, 4, 4, 4);
        host.setBackground(new ColorDrawable(0xFF000000));
        ReflectionHelpers.setField(mDock, "mDockBeads", sevenBeads());
        mDock.applyDockSurfaceShape(host, false, 100, true, true);
        assertEquals(0, host.getPaddingLeft());
        assertEquals(0, host.getPaddingBottom());
        assertNull("no slab under the beads", host.getBackground());
        assertFalse(host.getClipToOutline());
    }

    @Test
    public void theShelfShowsNothingWithoutAChromeState() {
        FrameLayout host = new FrameLayout(RuntimeEnvironment.getApplication());
        mHost.surfaceHost = host;
        com.termux.app.DockShelfView shelf =
            new com.termux.app.DockShelfView(RuntimeEnvironment.getApplication());
        mHost.views.put(R.id.accessory_shelf_fx, shelf);
        mHost.rows.setShelfShown(true);
        mDock.syncDockShelf(null);
        assertFalse(mHost.rows.shelfShown());
    }

    static class FakeHost implements DockController.Host {
        boolean prefs = true;
        float share = Float.NaN;
        @NonNull DockStyle design = DockStyle.LENS_BEADS;
        @Nullable DockStyle terminalDesign;
        @NonNull PaneWallPage lastPage = PaneWallPage.WIDGETS;
        @NonNull PlaceLayout layout = HOME;
        @Nullable View surfaceHost;
        final SparseArray<View> views = new SparseArray<>();
        final DockRows rows = new DockRows(new DockRowViews(id -> null, band -> false));
        final SeamCrossing seam = new SeamCrossing();
        @Nullable PlaceLayoutStore store;
        @Nullable TermuxAppSharedPreferences prefsStore;
        @Nullable ChromeRenderer chrome;
        @Nullable PaneWallPage travelledTo;
        boolean reducedMotion;
        boolean drawerEngaged;
        boolean keyboardShown;
        boolean fieldWanted;
        boolean recomputeTrips;
        boolean floating;
        boolean peekWanted;
        boolean peekStands;
        float cornersPx = 26f * 2.625f;
        int peekAsks;
        int peekDresses;
        int barHeightPasses;
        int toolbarPasses;

        @Nullable
        @Override
        @SuppressWarnings("unchecked")
        public <T extends View> T findViewById(int id) {
            if (id == R.id.accessory_surface_host && surfaceHost != null) return (T) surfaceHost;
            return (T) views.get(id);
        }

        @Nullable
        @Override
        public <T extends View> T hotView(int id) {
            return findViewById(id);
        }

        @NonNull @Override public Context context() { return RuntimeEnvironment.getApplication(); }
        @NonNull @Override public View decorView() { return new View(context()); }
        @Override public float density() { return 2.625f; }
        @Override public float dpToPx(float dp) { return dp * density(); }
        @Override public int withAlphaComponent(int color, int alpha) {
            return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
        }
        @Override public boolean viewContainsScreenPoint(@Nullable View view, float x, float y) {
            if (view == null || !view.isShown()) return false;
            int[] at = new int[2];
            view.getLocationOnScreen(at);
            return x >= at[0] && x < at[0] + view.getWidth()
                && y >= at[1] && y < at[1] + view.getHeight();
        }
        @Nullable @Override public SuggestionBarView dockRow() { return null; }
        @Nullable @Override public PromptLineFieldView promptFieldInRowBand() { return null; }
        @Override public boolean hasPreferences() { return prefs; }
        @Override public boolean isReducedMotionEnabled() { return reducedMotion; }
        @Override public boolean isInAppKeyboardShown() { return keyboardShown; }
        @Override public boolean keyboardSharesDockGlass() { return false; }
        @Override public boolean isAppDrawerEngaged() { return drawerEngaged; }
        @NonNull @Override public PlaceLayout placeLayout() { return layout; }
        @NonNull @Override public DockRows dockRows() { return rows; }
        @Override public boolean floatingStyle() { return floating; }
        @Override public boolean peekCardWanted() {
            peekAsks++;
            return peekWanted;
        }
        @Nullable @Override public PeekGlassGeometry.Card peekCard() { return null; }
        @Override public float layoutCornersPx() { return cornersPx; }
        @Override public int stackLookPage() { return 7; }
        @Override public boolean stackStandsDownOnFrame() { return false; }
        @NonNull @Override public DisplayMetrics displayMetrics() {
            return context().getResources().getDisplayMetrics();
        }
        @NonNull @Override public PlaceOrientation currentPlaceOrientation() {
            return PlaceOrientation.PORTRAIT;
        }
        @Override public int cutoutInsetLeft() { return 0; }
        @Override public int cutoutInsetRight() { return 0; }
        @Override public float terminalToolbarDefaultHeight() { return 0f; }
        @Override public int calculateSuggestionBarMaxButtons(@NonNull DisplayMetrics metrics) {
            return 5;
        }
        @Override public int homeMarginDp() { return 16; }
        @Nullable @Override public TermuxAppSharedPreferences preferences() { return prefsStore; }
        @Nullable @Override public PlaceLayoutStore placeLayoutStore() { return store; }
        @NonNull @Override public ResolvedArrangement arrangementOf(@NonNull PageRef page) {
            return arranged(page.isHome() ? design : designOf(PaneWallPage.TERMINAL), page);
        }
        @NonNull @Override public ResolvedArrangement arrangementOf(@NonNull PaneWallPage page) {
            return arranged(designOf(page), arrangedPageOf(page));
        }
        private DockStyle designOf(PaneWallPage page) {
            return page == PaneWallPage.TERMINAL && terminalDesign != null ? terminalDesign : design;
        }
        private ResolvedArrangement arranged(DockStyle style, PageRef page) {
            return ResolvedArrangement.of(HOME, LayoutStyle.DOCKED, style, false, false,
                PlaceOrientation.PORTRAIT, LayoutVariant.HOME, page);
        }
        @NonNull @Override public PageRef arrangedPageOf(@NonNull PaneWallPage page) {
            return page == PaneWallPage.TERMINAL ? PageRef.TERMINAL : PageRef.HOME;
        }
        @NonNull @Override public LayoutVariant layoutOf(@NonNull PaneWallPage page) {
            return LayoutVariant.HOME;
        }
        @Override public int arrangementRevision() { return 0; }
        @NonNull @Override public IconSize drawnIconSize(@NonNull PlaceLayoutStore store) {
            return IconSize.XL;
        }
        @NonNull @Override public PaneWallPage lastWallPage() { return lastPage; }
        @NonNull @Override public PaneWallPage stackDressPage() {
            if (recomputeTrips) throw new Recomputed();
            return lastPage;
        }
        @Override public boolean committedSearchField() { return false; }
        @Override public boolean promptFieldWanted() { return fieldWanted; }
        @Override public float promptFieldReveal() { return 0f; }
        @Override public float wallStripReveal() { return 0f; }
        @Override public float appliedPromptFieldReveal() { return 0f; }
        @Override public float appliedWallStripReveal() { return 0f; }
        @Nullable @Override public PaneWallPage drawerTileRowsPage() { return lastPage; }
        @Override public boolean isStatusBarCompact() { return false; }
        @NonNull @Override public PlaceLayout.Edge statusBarEdge() { return Edge.TOP; }
        @Override public int halfRedressCount() { return 0; }
        @Nullable @Override public AzScrubRowView azScrubRowView() { return null; }
        @Nullable @Override public AzScrubRowView azTabRowView() { return null; }
        @NonNull @Override public PlaceLayout.Edge azBarEdge() { return Edge.BOTTOM; }
        @Override public boolean isInAppKeyboardVisible() { return false; }
        @NonNull @Override public ChromeShape chromeShape() {
            throw new UnsupportedOperationException();
        }
        @NonNull @Override public ChromeShape chromeShape(@NonNull PlaceLayout layout) {
            throw new UnsupportedOperationException();
        }
        @NonNull @Override public List<ChromeShape.PieceId> dockPieceIds(boolean withKeyboard) {
            return new ArrayList<>();
        }
        @NonNull @Override public List<ChromeShape.PieceId> chromePieceIds(
            @NonNull List<Element> elements) {
            List<ChromeShape.PieceId> ids = new ArrayList<>(elements.size());
            for (Element element : elements) ids.add(ChromeShape.PieceId.of(element));
            return ids;
        }
        @Override public int chromeRimEdges(@NonNull ChromeShape.PieceId... ids) { return 0; }
        @Override public boolean unifiedKeyboardGlass() { return false; }
        @Override public boolean frameStandsDown() { return false; }
        @Nullable @Override public ChromeRenderer chrome() { return chrome; }
        @Override public void rebuildBackdropGlassEffectForLens() { }
        @Override public boolean glassParamsValid() { return true; }
        @Nullable @Override public GlassRefraction.Program dockGlassProgram() { return null; }
        @Override public boolean keyLensActive() { return false; }
        @Override public float glassOriginLeft() { return 0f; }
        @Override public float glassOriginTop() { return 0f; }
        @Override public boolean isGlassTouchLightAllowed() { return true; }
        @Override public int resolveDockAccentColor() { return 0xFF3366CC; }
        @Nullable @Override public Drawable deckSlabOr(@Nullable Drawable glass) { return glass; }
        @Override public float beadShare() { return share; }
        @NonNull @Override public SeamCrossing seam() { return seam; }
        @Nullable @Override public PaneWallPage dockDesignTravelledTo() { return travelledTo; }
        @Override public void setDockDesignTravelledTo(@Nullable PaneWallPage page) {
            travelledTo = page;
        }
        @Override public void syncFrameBeadCut() { }
        @Override public void onDockBeadsReplaced(@Nullable View host, boolean modeChanged) { }
        @Override public void syncDrawerTile() { }
        @Override public boolean updateAppLauncherBarHeight() {
            barHeightPasses++;
            return false;
        }
        @Override public boolean setTerminalToolbarHeight(boolean requestTerminalResize) {
            toolbarPasses++;
            return false;
        }
        @Override public void syncWallStripFeed() { }
        @Override public boolean updateViewBottomMargin(int viewId, int marginBottom) { return false; }
        @Override public boolean applyDockRowHorizontalInsets(@NonNull DockLayout layout) {
            return false;
        }
        @Override public boolean syncPromptLineField(@NonNull DockLayout layout) { return false; }
        @Override public boolean syncWallStripRow(@NonNull DockLayout layout) { return false; }
        @Override public boolean dressPeekCard(@NonNull View surface) {
            peekDresses++;
            return peekStands;
        }
    }
}
