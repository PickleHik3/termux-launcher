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
        final int flush = f.settle(PageRef.TERMINAL, false, floating).flushPaddingPx;
        final StackLayout.Result own = f.settle(front, keyboardUp, floating);
        final int foot = BottomInsets.keyboardFootPx(f.keptBandPx, BottomInsets.keyboardWearsFoot(
            floating || f.floatingKeyboard(), f.floatingKeyboard(), false, own.bandsUnderKeyboard,
            f.keyboardLaidOutPx() > 0));
        return new SideCardsController.Host() {
            @Nullable @Override public View view(int id) { return null; }
            @Override public boolean crossing() { return false; }
            @Nullable @Override public PageRef frontPage() { return front; }
            @Nullable @Override public PlaceLayout layoutOf(@NonNull PageRef page) {
                return f.layoutOf(page);
            }
            @NonNull @Override public DockGeometry.Snapshot dockSnapshot(@NonNull PlaceLayout layout,
                                                                         @NonNull PageKind rows) {
                return f.snapshot(rows == PageKind.HOME ? PageRef.HOME : PageRef.TERMINAL, floating);
            }
            @NonNull @Override public DisplayMetrics displayMetrics() { return metrics(f); }
            @Override public int measuredExtraKeysRowPx() { return f.extraKeysRowPx; }
            @Override public int measuredKeyboardPx() {
                return f.keyboardLaidOutPx() > 0 ? f.keyboardLaidOutPx() + foot : 0;
            }
            @Override public int bottomStatusBandPx(@NonNull PlaceLayout layout) {
                return f.bottomStatusBandPx(layout);
            }
            @Override public int keptBandPx() { return f.keptBandPx; }
            @Override public int terminalRestFlushPaddingPx() { return flush; }
            @Override public boolean terminalFlushAllowed() { return f.paneCount <= 1; }
            @Override public boolean statusOnDockPlank() { return false; }
            @Override public boolean keyboardGlassShared() { return true; }
            @Override public void sidesApart(boolean apart, int homeDropPx) {
                renderer.setSidesApart(apart, homeDropPx);
            }
        };
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
                        boolean apart = SideCards.apart(f.layoutOf(PageRef.HOME),
                            f.layoutOf(PageRef.TERMINAL), floating, false);
                        assertEquals(label, apart, controller.apart());
                        int frontMargin = page.isHome() ? homeMargin : terminalMargin;
                        assertEquals(label, SideCards.containerMarginPx(homeMargin, terminalMargin,
                            apart, page.isHome()), controller.containerMarginPx(frontMargin));
                        float drop = SideCards.homeSideDropPx(homeMargin, terminalMargin, apart);
                        // Home's drop, as the renderer draws Home's side at rest.
                        assertEquals(label, drop, mHost.getTranslationY(), 0f);
                        assertEquals(label, drop, mUnderlay.getTranslationY(), 0f);
                        assertEquals(label, drop, mHomeRow.getTranslationY(), 0f);
                        assertEquals(label, 0f, mKeysRow.getTranslationY(), 0f);
                        // Home's slide: 0 at Home's rest, a container's width to the left otherwise.
                        float tx = apart && !page.isHome() ? -WIDTH : 0f;
                        assertEquals(label, tx, mHost.getTranslationX(), 0f);
                        assertEquals(label, 0f, renderer.hostShiftPx(), 0f);
                        if (apart) apartCases++;
                        else assertFalse(label, controller.hostPinned());
                    }
                }
            }
        }
        assertTrue("some fixtures stand apart", apartCases > 0);
        assertTrue("and some do not", apartCases < cases);
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
                    assertEquals(label, up, look.keyboardUp);
                    // Apart is Floating: the dock is placed as the card unless the Peek card stands.
                    assertEquals(label, !look.peek, look.floats);
                    assertEquals(label, look.peek, look.peekCut != null);
                    assertEquals(label, DockSpec.footExists(PlaceChromePolicy.dockShown(home),
                        look.floats, up, false, f.keptBandPx), look.foot);
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

    @Test public void withoutTheOtherSidesLayoutTheCardsNeverStandApart() {
        Fixture f = LayoutFixtures.PONG;
        StackRenderer renderer = rendererFor(true);
        final SideCardsController.Host base = hostOf(f, PageRef.HOME, false, true, renderer);
        SideCardsController controller = new SideCardsController(new SideCardsController.Host() {
            @Nullable @Override public View view(int id) { return null; }
            @Override public boolean crossing() { return false; }
            @Nullable @Override public PageRef frontPage() { return PageRef.HOME; }
            @Nullable @Override public PlaceLayout layoutOf(@NonNull PageRef page) { return null; }
            @NonNull @Override public DockGeometry.Snapshot dockSnapshot(@NonNull PlaceLayout layout,
                                                                         @NonNull PageKind rows) {
                return base.dockSnapshot(layout, rows);
            }
            @NonNull @Override public DisplayMetrics displayMetrics() { return metrics(f); }
            @Override public int measuredExtraKeysRowPx() { return 0; }
            @Override public int measuredKeyboardPx() { return 0; }
            @Override public int bottomStatusBandPx(@NonNull PlaceLayout layout) { return 0; }
            @Override public int keptBandPx() { return 0; }
            @Override public int terminalRestFlushPaddingPx() { return 0; }
            @Override public boolean terminalFlushAllowed() { return false; }
            @Override public boolean statusOnDockPlank() { return false; }
            @Override public boolean keyboardGlassShared() { return true; }
            @Override public void sidesApart(boolean apart, int homeDropPx) { }
        });
        controller.update(f.pass(PageRef.HOME, false, true));
        assertFalse(controller.apart());
        assertEquals(7, controller.containerMarginPx(7));
    }
}
