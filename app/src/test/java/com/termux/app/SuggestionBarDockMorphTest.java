package com.termux.app;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;

import com.termux.app.dock.DockGeometry;
import com.termux.app.dock.DockLayout;
import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.dock.DockMorph;
import com.termux.app.dock.DockStyle;
import com.termux.app.dock.LensBeadGeometry;
import com.termux.app.launcher.icon.AsyncIconBinder;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceLayout.RowPlacement;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The pinned row across a crossing (ADR 0012): it draws the dock's morph rather than laying it
 * out. Told both rests once, it renders its icons at the larger; every frame scales each slot to
 * the frame's icon and moves it to its share of the frame's row, with no layout requested; and at
 * the landing it lays out the landed rest once, which draws exactly what the last frame drew.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SuggestionBarDockMorphTest {

    private static final int SLOTS = 5;
    private static final int ROW_HEIGHT = 200;
    private static final float STEP = 0.05f;

    /** A window with the pinned apps and the extra keys along the bottom. */
    private static final PlaceLayout WINDOW = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.BOTTOM, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);
    /** Home: the pinned apps alone along the bottom. */
    private static final PlaceLayout HOME = new PlaceLayout(Edge.TOP, RowPlacement.BOTTOM,
        false, Edge.BOTTOM, RowPlacement.HIDDEN, PlaceLayout.KeyboardMode.RESIZE,
        PlaceLayout.KeyboardForm.DOCKED, 4, 5);

    private Context context;
    private DockLayout home;
    private DockLayout workspace;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        AsyncIconBinder.setSynchronousForTesting(true);
        // Home on Lens beads with a Search field, Floating; the Workspace on the Tray: their slots
        // differ in icon, width and inset, as the 118 to 143 px snap did on the phone (#133).
        float density = context.getResources().getDisplayMetrics().density;
        DockGeometry.Half homeHalf = DockGeometry.Half.builder(HOME).floating(true)
            .design(DockStyle.LENS_BEADS).searchField(true).build();
        DockGeometry.Half workspaceHalf = DockGeometry.Half.builder(WINDOW)
            .design(DockStyle.GLASS_TRAY).build();
        DockGeometry.Snapshot snapshot = DockGeometry.Snapshot.builder(homeHalf, workspaceHalf)
            .density(density)
            .widthPx(Math.round(411 * density))
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(density))
            .pinnedSlotCount(SLOTS)
            .build();
        home = DockGeometry.compute(snapshot.restOf(DockGeometry.Side.HOME));
        workspace = DockGeometry.compute(snapshot.restOf(DockGeometry.Side.WORKSPACE));
    }

    @After
    public void tearDown() {
        AsyncIconBinder.setSynchronousForTesting(false);
    }

    private static List<LauncherAppEntry> entries(int count) {
        List<LauncherAppEntry> out = new ArrayList<>();
        for (int i = 0; i < count; i++)
            out.add(new LauncherAppEntry(new AppRef("com.example.app" + i, "Main"), "App " + i, null));
        return out;
    }

    /** The row laid out as the dock lays it out at {@code rest}: its usable width, its slots. */
    private static void layOut(SuggestionBarView bar, DockLayout rest) {
        int width = rest.appsRowUsableWidthPx;
        bar.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(ROW_HEIGHT, View.MeasureSpec.EXACTLY));
        bar.layout(0, 0, width, ROW_HEIGHT);
    }

    private SuggestionBarView rowAt(DockLayout rest) {
        SuggestionBarView bar = new SuggestionBarView(context, null);
        bar.setMaxButtonCount(SLOTS);
        bar.setDockLayout(rest);
        layOut(bar, rest);
        ReflectionHelpers.callInstanceMethod(bar, "renderButtons",
            ClassParameter.from(List.class, entries(SLOTS)),
            ClassParameter.from(boolean.class, false));
        layOut(bar, rest);
        return bar;
    }

    private static void runResize(SuggestionBarView bar) {
        ReflectionHelpers.setField(bar, "slotIconResizePosted", false);
        Runnable resize = ReflectionHelpers.getField(bar, "slotIconResizeRunnable");
        resize.run();
    }

    /** Where slot {@code i}'s icon is drawn across the dock: its centre's x from the dock's side. */
    private static float drawnCentre(SuggestionBarView bar, int i, DockLayout laidOutAt) {
        return laidOutAt.appsRowEdgeInsetPx + bar.drawnSlotIcon(i)[0];
    }

    private static float drawnSize(SuggestionBarView bar, int i) {
        return bar.drawnSlotIcon(i)[1];
    }

    private static void frame(SuggestionBarView bar, DockMorph morph) {
        bar.dockMorphFrame(morph.slotIconPx(), morph.appsRowUsableWidthPx(),
            morph.appsRowEdgeInsetPx(), morph.drawerTileAlpha());
    }

    @Test
    public void theRestsDressTheRowDifferently() {
        assertNotEquals(home.slotIconPx, workspace.slotIconPx);
        assertNotEquals(home.appsRowEdgeInsetPx, workspace.appsRowEdgeInsetPx);
    }

    @Test
    public void framesDrawTheMorphWithoutALayoutAndTheLandingLaysItOutOnce() {
        for (boolean fromHome : new boolean[] {true, false}) {
            DockLayout leaving = fromHome ? home : workspace;
            DockLayout arriving = fromHome ? workspace : home;
            SuggestionBarView bar = rowAt(leaving);
            float[] restCentres = new float[SLOTS];
            for (int i = 0; i < SLOTS; i++) restCentres[i] = drawnCentre(bar, i, leaving);

            // Begin: told both rests, the row renders at the larger icon, once.
            bar.beginDockMorph(leaving, arriving);
            assertTrue(bar.isDockMorphing());
            int larger = Math.max(leaving.slotIconPx, arriving.slotIconPx);
            assertEquals(larger, bar.iconSizePx());
            // Before the boxes take the larger icon, they are still drawn at the leaving one.
            for (int i = 0; i < SLOTS; i++)
                assertEquals(leaving.slotIconPx, drawnSize(bar, i), 0.5f);
            runResize(bar);
            layOut(bar, leaving);
            assertFalse(bar.isLayoutRequested());

            DockMorph morph = new DockMorph();
            morph.between(leaving, false, arriving, false);
            frame(bar, morph);
            for (int i = 0; i < SLOTS; i++) {
                assertEquals("slot " + i + " at 0: the leaving icon", leaving.slotIconPx,
                    drawnSize(bar, i), 0.5f);
                assertEquals("slot " + i + " at 0: where it rested", restCentres[i],
                    drawnCentre(bar, i, leaving), 0.5f);
            }
            float[] last = restCentres.clone();
            float lastSize = leaving.slotIconPx;
            DockLayout laidOut = leaving;
            boolean swapped = false;
            for (float t = STEP; t <= 1f + 1e-6f; t += STEP) {
                morph.at(Math.min(1f, t));
                frame(bar, morph);
                // A pass mid-slide hands the row its answer: noted as the layout its slots stand
                // in, its sizes not applied, nothing rendered or requested.
                bar.setDockLayout(laidOut);
                assertFalse("no layout at " + t, bar.isLayoutRequested());
                assertEquals("no re-render at " + t, larger, bar.iconSizePx());
                if (!swapped && t >= 0.5f) {
                    // Halfway the stack stands the other half's rows (a dip's swap): its pass
                    // lays the row out in the arriving rest's inset and width, and the slots are
                    // drawn where they were.
                    float[] before = new float[SLOTS];
                    for (int i = 0; i < SLOTS; i++) before[i] = drawnCentre(bar, i, laidOut);
                    laidOut = arriving;
                    bar.setDockLayout(laidOut);
                    layOut(bar, laidOut);
                    swapped = true;
                    for (int i = 0; i < SLOTS; i++)
                        assertEquals("slot " + i + " across the swap", before[i],
                            drawnCentre(bar, i, laidOut), 1.5f);
                    assertEquals(larger, bar.iconSizePx());
                }
                float size = drawnSize(bar, 0);
                assertTrue("the icon moves one way at " + t,
                    Math.signum(size - lastSize) * Math.signum(arriving.slotIconPx - leaving.slotIconPx) >= 0f);
                lastSize = size;
                for (int i = 0; i < SLOTS; i++) last[i] = drawnCentre(bar, i, laidOut);
            }
            for (int i = 0; i < SLOTS; i++)
                assertEquals("slot " + i + " at 1: the arriving icon", arriving.slotIconPx,
                    drawnSize(bar, i), 0.5f);

            // The landing: the row lays out the arriving rest once, and nothing moves.
            bar.endDockMorph(arriving);
            assertFalse(bar.isDockMorphing());
            assertEquals(arriving.slotIconPx, bar.iconSizePx());
            for (int i = 0; i < SLOTS; i++)
                assertEquals("slot " + i + " until resized", arriving.slotIconPx,
                    drawnSize(bar, i), 0.5f);
            runResize(bar);
            layOut(bar, arriving);
            for (int i = 0; i < SLOTS; i++) {
                assertEquals("slot " + i + " landed", arriving.slotIconPx, drawnSize(bar, i),
                    0.5f);
                assertEquals("slot " + i + " where the last frame drew it", last[i],
                    drawnCentre(bar, i, arriving), 1.5f);
            }
        }
    }

    /** Both halves on Lens beads, Home Floating and the Workspace Docked: their insets differ. */
    private DockLayout[] beadedRests() {
        float density = context.getResources().getDisplayMetrics().density;
        DockGeometry.Half homeHalf = DockGeometry.Half.builder(HOME).floating(true)
            .design(DockStyle.LENS_BEADS).build();
        DockGeometry.Half workspaceHalf = DockGeometry.Half.builder(WINDOW)
            .design(DockStyle.LENS_BEADS).build();
        DockGeometry.Snapshot snapshot = DockGeometry.Snapshot.builder(homeHalf, workspaceHalf)
            .density(density)
            .widthPx(Math.round(411 * density))
            .baseToolbarHeightPx(DockLayoutPolicy.baseToolbarHeightPx(density))
            .pinnedSlotCount(SLOTS)
            .build();
        return new DockLayout[] {DockGeometry.compute(snapshot.restOf(DockGeometry.Side.HOME)),
            DockGeometry.compute(snapshot.restOf(DockGeometry.Side.WORKSPACE))};
    }

    /** The beads the dock stands on the row's drawn boxes, on a surface clear of every edge. */
    private LensBeadGeometry.Beads beadsOf(SuggestionBarView bar, LensBeadGeometry.Beads rest) {
        float[] boxes = new float[(LensBeadGeometry.MAX_BEADS + 1) * 4];
        int count = bar.drawnIconBoxes(boxes);
        float density = context.getResources().getDisplayMetrics().density;
        return rest == null
            ? LensBeadGeometry.compute(boxes, count, -10000f, -10000f, 10000f, 10000f, density)
            : LensBeadGeometry.drawnAt(rest, boxes, count, -10000f, -10000f, 10000f, 10000f,
                density);
    }

    @Test
    public void eachBeadStandsAtItsIconsDrawnCentreAndSizeThroughTheMorph() {
        DockLayout[] rests = beadedRests();
        DockLayout leaving = rests[0];
        DockLayout arriving = rests[1];
        assertNotEquals("the insets differ", leaving.appsRowEdgeInsetPx,
            arriving.appsRowEdgeInsetPx);
        SuggestionBarView bar = rowAt(leaving);
        LensBeadGeometry.Beads rest = beadsOf(bar, null);
        assertNotNull(rest);
        bar.beginDockMorph(leaving, arriving);
        runResize(bar);
        layOut(bar, leaving);
        DockMorph morph = new DockMorph();
        morph.between(leaving, false, arriving, false);
        for (float t : new float[] {0f, 0.5f, 1f}) {
            morph.at(t);
            frame(bar, morph);
            LensBeadGeometry.Beads beads = beadsOf(bar, rest);
            assertEquals(SLOTS, beads.count());
            for (int i = 0; i < SLOTS; i++) {
                float[] icon = bar.drawnSlotIcon(i);
                assertEquals("bead " + i + " under its icon at " + t, icon[0], beads.centerX(i),
                    0.01f);
                assertEquals("bead " + i + " the icon's size at " + t, icon[1],
                    2f * beads.iconHalfPx, 0.01f);
            }
        }
    }

    @Test
    public void noFrameBetweenTheLastMorphFrameAndTheLandedLayoutDrawsAnotherIcon() {
        for (boolean fromHome : new boolean[] {true, false}) {
            DockLayout leaving = fromHome ? home : workspace;
            DockLayout arriving = fromHome ? workspace : home;
            SuggestionBarView bar = rowAt(leaving);
            bar.beginDockMorph(leaving, arriving);
            runResize(bar);
            layOut(bar, leaving);
            DockMorph morph = new DockMorph();
            morph.between(leaving, false, arriving, false);
            morph.at(1f);
            frame(bar, morph);
            // The last frame, with the row still laid out in the leaving rest's inset and width.
            float[][] last = new float[SLOTS][];
            for (int i = 0; i < SLOTS; i++)
                last[i] = new float[] {drawnCentre(bar, i, leaving), drawnSize(bar, i)};
            String at = fromHome ? "from Home" : "to Home";
            // The landing, then the resize it asks for, then the dock's answer and its layout:
            // every frame between draws what the last frame drew.
            bar.endDockMorph(arriving);
            assertSlotsAt(at + ", landed", bar, leaving, last, 0.01f);
            runResize(bar);
            layOut(bar, leaving);
            assertSlotsAt(at + ", resized", bar, leaving, last, 0.5f);
            assertTrue(bar.isDockMorphSettling());
            bar.setDockLayout(arriving);
            layOut(bar, arriving);
            assertFalse(at + ": laid out, the row draws as laid out", bar.isDockMorphSettling());
            assertSlotsAt(at + ", laid out", bar, arriving, last, 1.5f);
        }
    }

    private static void assertSlotsAt(String message, SuggestionBarView bar, DockLayout laidOutAt,
                                      float[][] expected, float delta) {
        for (int i = 0; i < SLOTS; i++) {
            assertEquals(message + ": slot " + i + " centre", expected[i][0],
                drawnCentre(bar, i, laidOutAt), delta);
            assertEquals(message + ": slot " + i + " icon", expected[i][1], drawnSize(bar, i),
                0.5f);
        }
    }

    @Test
    public void aRowStandingUpIsDrawnAsLaidOut() {
        SuggestionBarView bar = rowAt(workspace);
        bar.setVerticalForm(true);
        bar.beginDockMorph(workspace, home);
        assertFalse(bar.isDockMorphing());
    }

    @Test
    public void aRowThatGoesAwayMidSlideNeverWaitsForALanding() {
        SuggestionBarView bar = rowAt(workspace);
        bar.beginDockMorph(workspace, home);
        assertTrue(bar.isDockMorphing());
        ReflectionHelpers.callInstanceMethod(bar, "onDetachedFromWindow");
        assertFalse(bar.isDockMorphing());
        // The dock's next answer is laid out as at rest.
        bar.setDockLayout(home);
        assertEquals(home.slotIconPx, bar.iconSizePx());
    }
}
