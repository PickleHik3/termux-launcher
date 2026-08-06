package com.termux.app.terminal;

import android.app.Application;
import android.os.Build;
import android.graphics.RectF;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Collections;

import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalPaneControllerTest {

    @Test
    public void clampFirstWeight_keepsBothPanesAtLeastEighteenPercent() {
        assertEquals(.36f, TerminalPaneController.clampFirstWeight(2f, 0f), .001f);
        assertEquals(1.64f, TerminalPaneController.clampFirstWeight(2f, 2f), .001f);
        assertEquals(1.1f, TerminalPaneController.clampFirstWeight(2f, 1.1f), .001f);
    }

    @Test
    public void interactionOverlay_isAbsentForLonePaneButPersistsWhileMaximized() {
        assertFalse(TerminalPaneController.shouldShowInteractionOverlay(1, false));
        assertTrue(TerminalPaneController.shouldShowInteractionOverlay(2, false));
        assertTrue(TerminalPaneController.shouldShowInteractionOverlay(1, true));
    }

    @Test
    public void snapFirstWeightToCell_alignsFinalDividerAndKeepsMinimumPaneSize() {
        assertEquals(1.0f,
            TerminalPaneController.snapFirstWeightToCell(2f, 1000f, .97f, 100f), .001f);
        assertEquals(.36f,
            TerminalPaneController.snapFirstWeightToCell(2f, 1000f, .1f, 100f), .001f);
        assertEquals(1.64f,
            TerminalPaneController.snapFirstWeightToCell(2f, 1000f, 1.9f, 100f), .001f);
    }

    @Test
    public void touchedBorderIndex_preservesOriginalPaneOwnershipAtSharedDivider() {
        java.util.List<RectF> panes = Arrays.asList(
            new RectF(0f, 0f, 499f, 500f),
            new RectF(501f, 0f, 1000f, 500f));

        assertEquals(0, TerminalPaneController.touchedBorderIndex(
            panes, 1, 498f, 250f, 12f));
        assertEquals(1, TerminalPaneController.touchedBorderIndex(
            panes, 0, 502f, 250f, 12f));
        assertEquals(0, TerminalPaneController.touchedBorderIndex(
            panes, 0, 500f, 250f, 12f));
        assertEquals(1, TerminalPaneController.touchedBorderIndex(
            panes, 1, 500f, 250f, 12f));
    }

    @Test
    public void savedWindow_restoresNestedTopologyWeightsAndFocusByStableHandle() {
        TerminalPaneController source = newController();
        TerminalSession first = terminal();
        TerminalSession second = terminal();
        TerminalSession third = terminal();

        TerminalPaneController.Leaf firstLeaf = new TerminalPaneController.Leaf(first);
        TerminalPaneController.Leaf secondLeaf = new TerminalPaneController.Leaf(second);
        TerminalPaneController.Leaf thirdLeaf = new TerminalPaneController.Leaf(third);
        TerminalPaneController.Split nested = new TerminalPaneController.Split();
        nested.orientation = LinearLayout.VERTICAL;
        nested.weightA = .7f;
        nested.weightB = 1.3f;
        nested.a = secondLeaf;
        nested.b = thirdLeaf;
        secondLeaf.parent = nested;
        thirdLeaf.parent = nested;
        TerminalPaneController.Split root = new TerminalPaneController.Split();
        root.orientation = LinearLayout.HORIZONTAL;
        root.weightA = 1.2f;
        root.weightB = .8f;
        root.a = firstLeaf;
        root.b = nested;
        firstLeaf.parent = root;
        nested.parent = root;
        TerminalPaneController.Window window = new TerminalPaneController.Window(firstLeaf);
        window.root = root;
        window.active = thirdLeaf;

        Bundle saved = source.saveWindow(window);
        Map<String, TerminalSession> sessions = new HashMap<>();
        sessions.put(first.mHandle, first);
        sessions.put(second.mHandle, second);
        sessions.put(third.mHandle, third);
        TerminalPaneController.Window restored = newController().restoreWindow(saved, sessions);

        assertTrue(restored.root instanceof TerminalPaneController.Split);
        TerminalPaneController.Split restoredRoot = (TerminalPaneController.Split) restored.root;
        assertEquals(LinearLayout.HORIZONTAL, restoredRoot.orientation);
        assertEquals(1.2f, restoredRoot.weightA, .001f);
        assertTrue(restoredRoot.b instanceof TerminalPaneController.Split);
        TerminalPaneController.Split restoredNested =
            (TerminalPaneController.Split) restoredRoot.b;
        assertEquals(LinearLayout.VERTICAL, restoredNested.orientation);
        assertEquals(.7f, restoredNested.weightA, .001f);
        assertEquals(third, restored.active.session);
    }

    @Test
    public void durableWindow_roundTripsTopologyAndFocusUsingLeafOrder() {
        TerminalPaneController source = newController();
        TerminalSession first = terminal();
        TerminalSession second = terminal();
        TerminalSession third = terminal();
        TerminalPaneController.Leaf firstLeaf = new TerminalPaneController.Leaf(first);
        TerminalPaneController.Leaf secondLeaf = new TerminalPaneController.Leaf(second);
        TerminalPaneController.Leaf thirdLeaf = new TerminalPaneController.Leaf(third);
        TerminalPaneController.Split nested = new TerminalPaneController.Split();
        nested.orientation = LinearLayout.VERTICAL;
        nested.weightA = .75f;
        nested.weightB = 1.25f;
        nested.a = secondLeaf;
        nested.b = thirdLeaf;
        secondLeaf.parent = nested;
        thirdLeaf.parent = nested;
        TerminalPaneController.Split root = new TerminalPaneController.Split();
        root.orientation = LinearLayout.HORIZONTAL;
        root.weightA = 1.4f;
        root.weightB = .6f;
        root.a = firstLeaf;
        root.b = nested;
        firstLeaf.parent = root;
        nested.parent = root;
        TerminalPaneController.Window window = new TerminalPaneController.Window(firstLeaf);
        window.root = root;
        window.active = secondLeaf;

        TerminalWorkspace.Window saved = source.snapshotWorkspaceWindow(window,
            session -> new TerminalWorkspace.Pane("/cwd/" + session.mHandle, null, null));
        assertEquals(1, saved.activePane);
        assertTrue(saved.root instanceof TerminalWorkspace.Split);

        TerminalSession restoredFirst = terminal();
        TerminalSession restoredSecond = terminal();
        TerminalSession restoredThird = terminal();
        TerminalPaneController restoredController = newController();
        TerminalPaneController.Window restored = restoredController.newWorkspaceWindow(saved,
            Arrays.asList(restoredFirst, restoredSecond, restoredThird));
        List<TerminalSession> shells = restoredController.shellsOf(restored);
        assertEquals(Arrays.asList(restoredFirst, restoredSecond, restoredThird), shells);
        assertEquals(restoredSecond, restoredController.windowActiveSession(restored));
        TerminalPaneController.Split restoredRoot = (TerminalPaneController.Split) restored.root;
        assertEquals(LinearLayout.HORIZONTAL, restoredRoot.orientation);
        assertEquals(1.4f, restoredRoot.weightA, .001f);
        TerminalPaneController.Split restoredNested = (TerminalPaneController.Split) restoredRoot.b;
        assertEquals(LinearLayout.VERTICAL, restoredNested.orientation);
        assertEquals(.75f, restoredNested.weightA, .001f);
    }

    @Test
    public void automaticLayouts_preserveShellOrderAndFocusAcrossAllSixStrategies() {
        PaneFixture fixture = fourPaneFixture();
        List<TerminalSession> original = new java.util.ArrayList<>(fixture.sessions);
        TerminalSession focused = fixture.sessions.get(2);

        assertTrue(fixture.controller.applyLayout(TerminalPaneController.LAYOUT_HORIZONTAL));
        assertAllSplitsHaveOrientation(fixture.window.root, LinearLayout.HORIZONTAL);
        assertEquals(original, fixture.controller.shellsOf(fixture.window));
        assertEquals(focused, fixture.controller.getActiveSession());

        assertTrue(fixture.controller.applyLayout(TerminalPaneController.LAYOUT_VERTICAL));
        assertAllSplitsHaveOrientation(fixture.window.root, LinearLayout.VERTICAL);
        assertEquals(original, fixture.controller.shellsOf(fixture.window));

        assertTrue(fixture.controller.applyLayout(TerminalPaneController.LAYOUT_TALL));
        TerminalPaneController.Split tall = (TerminalPaneController.Split) fixture.window.root;
        assertEquals(LinearLayout.HORIZONTAL, tall.orientation);
        assertTrue(tall.a instanceof TerminalPaneController.Leaf);
        assertAllSplitsHaveOrientation(tall.b, LinearLayout.VERTICAL);
        assertEquals(original, fixture.controller.shellsOf(fixture.window));

        assertTrue(fixture.controller.applyLayout(TerminalPaneController.LAYOUT_FAT));
        TerminalPaneController.Split fat = (TerminalPaneController.Split) fixture.window.root;
        assertEquals(LinearLayout.VERTICAL, fat.orientation);
        assertTrue(fat.a instanceof TerminalPaneController.Leaf);
        assertAllSplitsHaveOrientation(fat.b, LinearLayout.HORIZONTAL);

        assertTrue(fixture.controller.applyLayout(TerminalPaneController.LAYOUT_GRID));
        TerminalPaneController.Split grid = (TerminalPaneController.Split) fixture.window.root;
        assertEquals(LinearLayout.VERTICAL, grid.orientation);
        assertAllSplitsHaveOrientation(grid.a, LinearLayout.HORIZONTAL);
        assertAllSplitsHaveOrientation(grid.b, LinearLayout.HORIZONTAL);
        assertEquals(original, fixture.controller.shellsOf(fixture.window));

        assertTrue(fixture.controller.applyLayout(TerminalPaneController.LAYOUT_STACK));
        assertEquals(1, fixture.controller.getVisiblePaneViews().size());
        assertEquals(focused, fixture.controller.getActiveSession());
        assertTrue(fixture.controller.applyLayout(TerminalPaneController.LAYOUT_GRID));
        assertEquals(4, fixture.controller.getVisiblePaneViews().size());
        assertFalse(fixture.controller.applyLayout("spiral"));
    }

    @Test
    public void nextLayoutCycle_visitsEveryLayoutAndKeepsStackOffTheFirstPress() {
        // An unmanaged window must land on a tiling, never on stack: stack hides every unfocused
        // pane, so making it one press away from a fresh window would read as "panes disappeared".
        assertEquals(TerminalPaneController.LAYOUT_GRID,
            TerminalPaneController.nextLayoutAfter(null));
        assertEquals(TerminalPaneController.LAYOUT_GRID,
            TerminalPaneController.nextLayoutAfter("spiral"));

        String[] expected = {
            TerminalPaneController.LAYOUT_TALL,
            TerminalPaneController.LAYOUT_FAT,
            TerminalPaneController.LAYOUT_HORIZONTAL,
            TerminalPaneController.LAYOUT_VERTICAL,
            TerminalPaneController.LAYOUT_STACK,
            TerminalPaneController.LAYOUT_GRID};
        String current = TerminalPaneController.LAYOUT_GRID;
        for (String next : expected) {
            current = TerminalPaneController.nextLayoutAfter(current);
            assertEquals(next, current);
        }

        assertTrue(TerminalPaneController.isKnownLayout(TerminalPaneController.LAYOUT_STACK));
        assertFalse(TerminalPaneController.isKnownLayout("spiral"));
        assertFalse(TerminalPaneController.isKnownLayout(null));
    }

    @Test
    public void nextLayout_appliesAndRetainsTheLayoutItLandsOn() {
        PaneFixture fixture = fourPaneFixture();
        assertEquals(null, fixture.controller.activeLayoutPolicy());

        assertTrue(fixture.controller.nextLayout());
        assertEquals(TerminalPaneController.LAYOUT_GRID, fixture.controller.activeLayoutPolicy());

        assertTrue(fixture.controller.nextLayout());
        assertEquals(TerminalPaneController.LAYOUT_TALL, fixture.controller.activeLayoutPolicy());
        TerminalPaneController.Split tall = (TerminalPaneController.Split) fixture.window.root;
        assertEquals(LinearLayout.HORIZONTAL, tall.orientation);
        assertAllSplitsHaveOrientation(tall.b, LinearLayout.VERTICAL);
    }

    @Test
    public void retainedLayout_reTilesWhenAPaneIsAddedOrClosed() {
        PaneFixture fixture = splittableFourPaneFixture();
        assertTrue(fixture.controller.applyLayout(TerminalPaneController.LAYOUT_VERTICAL));
        assertEquals(TerminalPaneController.LAYOUT_VERTICAL, fixture.controller.activeLayoutPolicy());

        // A one-shot transform would leave the binary split that insertion produced. A retained
        // policy re-tiles, so every divider is still on the layout's axis.
        fixture.controller.split(LinearLayout.HORIZONTAL);
        assertEquals(5, fixture.controller.shellsOf(fixture.window).size());
        assertAllSplitsHaveOrientation(fixture.window.root, LinearLayout.VERTICAL);
        assertEquals(TerminalPaneController.LAYOUT_VERTICAL, fixture.controller.activeLayoutPolicy());

        TerminalSession closed = fixture.controller.shellsOf(fixture.window).get(1);
        assertEquals(TerminalPaneController.FINISHED_PANE,
            fixture.controller.onSessionFinished(closed));
        assertEquals(4, fixture.controller.shellsOf(fixture.window).size());
        assertFalse(fixture.controller.shellsOf(fixture.window).contains(closed));
        assertAllSplitsHaveOrientation(fixture.window.root, LinearLayout.VERTICAL);
        assertEquals(TerminalPaneController.LAYOUT_VERTICAL, fixture.controller.activeLayoutPolicy());
    }

    @Test
    public void handShapingClearsPolicy_soALaterSplitKeepsTheUserTopology() {
        PaneFixture fixture = splittableFourPaneFixture();

        // Rotate produces a tree no preset would produce; retaining the policy would mean the next
        // split silently threw the rotation away.
        assertTrue(fixture.controller.applyLayout(TerminalPaneController.LAYOUT_VERTICAL));
        assertTrue(fixture.controller.rotateLayout(true));
        assertEquals(null, fixture.controller.activeLayoutPolicy());
        fixture.controller.split(LinearLayout.VERTICAL);
        assertEquals(LinearLayout.HORIZONTAL,
            ((TerminalPaneController.Split) fixture.window.root).orientation);

        assertTrue(fixture.controller.applyLayout(TerminalPaneController.LAYOUT_GRID));
        assertTrue(fixture.controller.moveActivePaneToEdge(TerminalPaneController.EDGE_LEFT));
        assertEquals(null, fixture.controller.activeLayoutPolicy());

        assertTrue(fixture.controller.applyLayout(TerminalPaneController.LAYOUT_GRID));
        assertTrue(fixture.controller.resizeActive(android.view.KeyEvent.KEYCODE_DPAD_LEFT));
        assertEquals(null, fixture.controller.activeLayoutPolicy());

        // Equalize only resets ratios, which is consistent with a managed layout, so it keeps it.
        assertTrue(fixture.controller.applyLayout(TerminalPaneController.LAYOUT_GRID));
        assertTrue(fixture.controller.equalizeLayout());
        assertEquals(TerminalPaneController.LAYOUT_GRID, fixture.controller.activeLayoutPolicy());
    }

    @Test
    public void savedWindow_restoresRetainedLayoutAndRejectsAnUnknownName() {
        PaneFixture fixture = fourPaneFixture();
        assertTrue(fixture.controller.applyLayout(TerminalPaneController.LAYOUT_FAT));

        Bundle saved = fixture.controller.saveWindow(fixture.window);
        Map<String, TerminalSession> sessions = new HashMap<>();
        for (TerminalSession session : fixture.sessions) sessions.put(session.mHandle, session);

        TerminalPaneController.Window restored = newController().restoreWindow(saved, sessions);
        assertEquals(TerminalPaneController.LAYOUT_FAT, restored.layoutPolicy);

        // A name this build no longer knows must leave the window manually managed rather than
        // wedge the reapply path on every later split.
        saved.putString("layout_policy", "spiral");
        TerminalPaneController.Window stale = newController().restoreWindow(saved, sessions);
        assertEquals(null, stale.layoutPolicy);
    }

    @Test
    public void equalizeRotateAndMoveEdge_mutateOnlyTopology() {
        PaneFixture fixture = fourPaneFixture();
        List<TerminalSession> original = new java.util.ArrayList<>(fixture.sessions);
        TerminalSession focused = fixture.sessions.get(2);

        assertTrue(fixture.controller.applyLayout(TerminalPaneController.LAYOUT_TALL));
        TerminalPaneController.Split tall = (TerminalPaneController.Split) fixture.window.root;
        tall.weightA = .35f;
        tall.weightB = 1.65f;
        ((TerminalPaneController.Split) tall.b).weightA = .4f;
        assertTrue(fixture.controller.equalizeLayout());
        assertAllSplitWeights(fixture.window.root, 1f, 1f);

        assertTrue(fixture.controller.rotateLayout(true));
        assertEquals(LinearLayout.VERTICAL,
            ((TerminalPaneController.Split) fixture.window.root).orientation);
        assertEquals(focused, fixture.controller.getActiveSession());
        assertTrue(fixture.controller.rotateLayout(false));
        assertEquals(LinearLayout.HORIZONTAL,
            ((TerminalPaneController.Split) fixture.window.root).orientation);
        assertEquals(original, fixture.controller.shellsOf(fixture.window));

        assertTrue(fixture.controller.moveActivePaneToEdge(TerminalPaneController.EDGE_LEFT));
        TerminalPaneController.Split left = (TerminalPaneController.Split) fixture.window.root;
        assertEquals(LinearLayout.HORIZONTAL, left.orientation);
        assertEquals(focused, ((TerminalPaneController.Leaf) left.a).session);
        assertEquals(4, fixture.controller.shellsOf(fixture.window).size());
        assertEquals(focused, fixture.controller.getActiveSession());

        assertTrue(fixture.controller.moveActivePaneToEdge(TerminalPaneController.EDGE_DOWN));
        TerminalPaneController.Split down = (TerminalPaneController.Split) fixture.window.root;
        assertEquals(LinearLayout.VERTICAL, down.orientation);
        assertEquals(focused, ((TerminalPaneController.Leaf) down.b).session);
        assertEquals(4, new java.util.HashSet<>(fixture.controller.shellsOf(fixture.window)).size());
        assertFalse(fixture.controller.moveActivePaneToEdge("center"));
    }

    @Test
    public void clampFloatFractions_enforcesMinimumSizeAndKeepsHandleReachable() {
        // Host 1000x800, minimum pane 120x90px, minimum visible handle 48px.
        RectF grown = TerminalPaneController.clampFloatFractions(
            new RectF(.2f, .2f, .21f, .21f), 1000f, 800f, 120f, 90f, 48f);
        assertEquals(.12f, grown.width(), .001f);
        assertEquals(.1125f, grown.height(), .001f);
        assertEquals(.2f, grown.left, .001f);
        assertEquals(.2f, grown.top, .001f);

        // Dragged far past the bottom-right: at least 48px of the handle row stays on screen
        // sideways, while the bottom edge is pulled fully back inside the host — below it there is
        // no handle to grab and the overflow would paint into the dock band.
        RectF offBottomRight = TerminalPaneController.clampFloatFractions(
            new RectF(2f, 2f, 2.5f, 2.5f), 1000f, 800f, 120f, 90f, 48f);
        assertEquals(1f - 48f / 1000f, offBottomRight.left, .001f);
        assertEquals(1f - offBottomRight.height(), offBottomRight.top, .001f);
        assertEquals(1f, offBottomRight.bottom, .001f);

        // Past the top-left: the top edge carries the handle, so it may never leave upward.
        RectF offTopLeft = TerminalPaneController.clampFloatFractions(
            new RectF(-3f, -3f, -2.5f, -2.5f), 1000f, 800f, 120f, 90f, 48f);
        assertEquals(0f, offTopLeft.top, .001f);
        assertEquals(48f / 1000f - .5f, offTopLeft.left, .001f);

        // Oversized floats cap at the host.
        RectF oversized = TerminalPaneController.clampFloatFractions(
            new RectF(0f, 0f, 3f, 3f), 1000f, 800f, 120f, 90f, 48f);
        assertEquals(1f, oversized.width(), .001f);
        assertEquals(1f, oversized.height(), .001f);
    }

    @Test
    public void toggleFloat_detachesFocusedPaneAndRedocksIt() {
        PaneFixture fixture = fourPaneFixture();
        TerminalSession focused = fixture.sessions.get(2);
        assertEquals(focused, fixture.controller.getActiveSession());

        assertEquals(TerminalPaneController.FLOAT_TOGGLE_FLOATED,
            fixture.controller.toggleFloatActivePane());
        assertTrue(fixture.controller.isActivePaneFloating());
        assertEquals(1, fixture.controller.activeFloatingPaneCount());
        assertEquals(focused, fixture.controller.getActiveSession());
        // Still one of the window's shells and views, but no longer in the tiled tree.
        assertEquals(4, fixture.controller.shellsOf(fixture.window).size());
        assertEquals(4, fixture.controller.getVisiblePaneViews().size());
        assertEquals(3, countLeaves(fixture.window.root));

        assertEquals(TerminalPaneController.FLOAT_TOGGLE_DOCKED,
            fixture.controller.toggleFloatActivePane());
        assertFalse(fixture.controller.isActivePaneFloating());
        assertEquals(0, fixture.controller.activeFloatingPaneCount());
        assertEquals(4, countLeaves(fixture.window.root));
        assertEquals(focused, fixture.controller.getActiveSession());
    }

    @Test
    public void toggleFloat_refusesTheWindowsOnlyTiledPane() {
        TerminalPaneController controller = newController();
        TerminalSession only = terminal();
        TerminalPaneController.Window window = controller.newWindow(only);
        controller.showWindow(window);

        assertEquals(TerminalPaneController.FLOAT_TOGGLE_SINGLE_PANE,
            controller.toggleFloatActivePane());
        assertEquals(Collections.singletonList(only), controller.shellsOf(window));
        assertEquals(only, controller.getActiveSession());
    }

    @Test
    public void savedWindow_roundTripsFloatingPanesAndTheirBounds() {
        PaneFixture fixture = fourPaneFixture();
        assertEquals(TerminalPaneController.FLOAT_TOGGLE_FLOATED,
            fixture.controller.toggleFloatActivePane());
        fixture.window.floating.get(0).floatFrac = new RectF(.25f, .3f, .75f, .8f);

        Bundle saved = fixture.controller.saveWindow(fixture.window);
        Map<String, TerminalSession> sessions = new HashMap<>();
        for (TerminalSession session : fixture.sessions) sessions.put(session.mHandle, session);
        TerminalPaneController.Window restored = newController().restoreWindow(saved, sessions);

        assertEquals(1, restored.floating.size());
        TerminalPaneController.Leaf floating = restored.floating.get(0);
        assertEquals(fixture.sessions.get(2), floating.session);
        assertEquals(.25f, floating.floatFrac.left, .001f);
        assertEquals(.3f, floating.floatFrac.top, .001f);
        assertEquals(.5f, floating.floatFrac.width(), .001f);
        assertEquals(.5f, floating.floatFrac.height(), .001f);
        assertEquals(floating, restored.active);
        assertEquals(3, countLeaves(restored.root));

        // A pre-float bundle (no floats key) must keep restoring.
        saved.remove("floats");
        TerminalPaneController.Window legacy = newController().restoreWindow(saved, sessions);
        assertTrue(legacy.floating.isEmpty());
        assertEquals(3, countLeaves(legacy.root));
    }

    @Test
    public void workspaceWindow_roundTripsFloatingPanes() {
        PaneFixture fixture = fourPaneFixture();
        assertEquals(TerminalPaneController.FLOAT_TOGGLE_FLOATED,
            fixture.controller.toggleFloatActivePane());
        fixture.window.floating.get(0).floatFrac = new RectF(.2f, .25f, .7f, .75f);

        TerminalWorkspace.Window saved = fixture.controller.snapshotWorkspaceWindow(fixture.window,
            session -> new TerminalWorkspace.Pane("/cwd/" + session.mHandle, null, null));
        assertEquals(1, saved.floats.size());
        // The focused float indexes after the three tiled leaves.
        assertEquals(3, saved.activePane);
        assertEquals(.2f, saved.floats.get(0).left, .001f);
        assertEquals(.5f, saved.floats.get(0).width, .001f);

        List<TerminalSession> restoredSessions = Arrays.asList(
            terminal(), terminal(), terminal(), terminal());
        TerminalPaneController restoredController = newController();
        TerminalPaneController.Window restored =
            restoredController.newWorkspaceWindow(saved, restoredSessions);
        assertEquals(1, restored.floating.size());
        assertEquals(restoredSessions.get(3), restored.floating.get(0).session);
        assertEquals(restoredSessions.get(3), restoredController.windowActiveSession(restored));
        assertEquals(.25f, restored.floating.get(0).floatFrac.top, .001f);
        assertEquals(restoredSessions, restoredController.shellsOf(restored));
    }

    @Test
    public void finishedFloatingShell_freesThePaneAndKeepsTheWindow() {
        PaneFixture fixture = fourPaneFixture();
        TerminalSession floated = fixture.controller.getActiveSession();
        assertEquals(TerminalPaneController.FLOAT_TOGGLE_FLOATED,
            fixture.controller.toggleFloatActivePane());

        assertEquals(TerminalPaneController.FINISHED_PANE,
            fixture.controller.onSessionFinished(floated));
        assertEquals(0, fixture.controller.activeFloatingPaneCount());
        assertEquals(3, fixture.controller.shellsOf(fixture.window).size());
        assertFalse(fixture.controller.shellsOf(fixture.window).contains(floated));
        assertEquals(3, fixture.controller.getVisiblePaneViews().size());
        assertTrue(fixture.controller.shellsOf(fixture.window)
            .contains(fixture.controller.getActiveSession()));
    }

    @Test
    public void finishedLastTiledShell_promotesAFloatIntoTheTree() {
        TerminalPaneController controller = newController();
        TerminalSession first = terminal();
        TerminalSession second = terminal();
        TerminalWorkspace.Node root = new TerminalWorkspace.Split(
            TerminalWorkspace.Split.HORIZONTAL, 1f, 1f,
            new TerminalWorkspace.Pane("/a", null, null),
            new TerminalWorkspace.Pane("/b", null, null));
        TerminalPaneController.Window window = controller.newWorkspaceWindow(
            new TerminalWorkspace.Window(1, root), Arrays.asList(first, second));
        controller.showWindow(window);
        assertEquals(TerminalPaneController.FLOAT_TOGGLE_FLOATED,
            controller.toggleFloatActivePane());

        // The tiled root dies while a float survives: the window must live on around the float.
        assertEquals(TerminalPaneController.FINISHED_PANE, controller.onSessionFinished(first));
        assertEquals(Collections.singletonList(second), controller.shellsOf(window));
        assertEquals(0, controller.activeFloatingPaneCount());
        assertEquals(second, controller.getActiveSession());
    }

    @Test
    public void scratchpad_remembersUserShapedBoundsAcrossTogglesAndStateRoundtrip() {
        // Animations off so the hide removes the float synchronously.
        android.provider.Settings.Global.putFloat(
            RuntimeEnvironment.getApplication().getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        TerminalPaneController controller = newScratchpadController();
        TerminalPaneController.Window window = controller.newWindow(terminal());
        controller.showWindow(window);

        assertEquals(TerminalPaneController.SCRATCHPAD_TOGGLE_SHOWN,
            controller.toggleScratchpad());
        RectF shaped = new RectF(0.2f, 0.3f, 0.7f, 0.8f);
        window.floating.get(0).floatFrac = new RectF(shaped);
        assertEquals(TerminalPaneController.SCRATCHPAD_TOGGLE_HIDDEN,
            controller.toggleScratchpad());
        assertEquals(TerminalPaneController.SCRATCHPAD_TOGGLE_SHOWN,
            controller.toggleScratchpad());
        assertEquals(shaped, window.floating.get(0).floatFrac);

        // The remembered bounds also survive a save/restore into a fresh controller.
        assertEquals(TerminalPaneController.SCRATCHPAD_TOGGLE_HIDDEN,
            controller.toggleScratchpad());
        Bundle state = new Bundle();
        controller.saveScratchpadState(state);
        TerminalPaneController restored = newScratchpadController();
        restored.restoreScratchpadState(state);
        TerminalPaneController.Window second = restored.newWindow(terminal());
        restored.showWindow(second);
        assertEquals(TerminalPaneController.SCRATCHPAD_TOGGLE_SHOWN,
            restored.toggleScratchpad());
        assertEquals(shaped, second.floating.get(0).floatFrac);
    }

    @Test
    public void split_reportsWhetherAPaneWasActuallyAdded() {
        // The caller announces the new pane count on the notice chip, so it needs to know.
        TerminalPaneController idle = newController();
        assertFalse(idle.split(LinearLayout.HORIZONTAL));

        PaneFixture fixture = splittableFourPaneFixture();
        int before = fixture.controller.shellsOf(fixture.window).size();
        assertTrue(fixture.controller.split(LinearLayout.HORIZONTAL));
        assertEquals(before + 1, fixture.controller.shellsOf(fixture.window).size());
    }

    @Test
    public void pillBackdrop_isDrawnOnlyForTheExpandedActionStrip() {
        // A collapsed pill drew an opaque 48x18dp slab of surface panel across the top of the
        // float, which read as a black border. The grip alone is the affordance there.
        assertEquals(0, TerminalPaneController.pillBackdropAlpha(false, true));
        assertEquals(0, TerminalPaneController.pillBackdropAlpha(false, false));
        assertTrue(TerminalPaneController.pillBackdropAlpha(true, true) > 0);
        assertTrue(TerminalPaneController.pillBackdropAlpha(true, false) > 0);
        assertTrue(TerminalPaneController.pillBackdropAlpha(true, true)
            >= TerminalPaneController.pillBackdropAlpha(true, false));
    }

    @Test
    public void isScratchpadShellName_acceptsBothSpellingsAndNothingElse() {
        assertTrue(TerminalPaneController.isScratchpadShellName(
            TerminalPaneController.SCRATCHPAD_SESSION_NAME));
        assertTrue(TerminalPaneController.isScratchpadShellName(
            TerminalPaneController.LEGACY_SCRATCHPAD_SESSION_NAME));
        assertFalse(TerminalPaneController.isScratchpadShellName(null));
        assertFalse(TerminalPaneController.isScratchpadShellName(""));
        // The old five-character truncation of "scratchpad" is a real user-visible name now.
        assertFalse(TerminalPaneController.isScratchpadShellName("scrat"));
    }

    @Test
    public void shouldAdoptAsWindowSession_rejectsEveryScratchpadSpelling() {
        assertFalse(TerminalPaneController.shouldAdoptAsWindowSession(
            TerminalPaneController.SCRATCHPAD_SESSION_NAME));
        assertFalse(TerminalPaneController.shouldAdoptAsWindowSession(
            TerminalPaneController.LEGACY_SCRATCHPAD_SESSION_NAME));
        assertTrue(TerminalPaneController.shouldAdoptAsWindowSession("work"));
        assertTrue(TerminalPaneController.shouldAdoptAsWindowSession(null));
    }

    @Test
    public void tiledPaneCount_ignoresFloatsAndTheScratchpad() {
        android.provider.Settings.Global.putFloat(
            RuntimeEnvironment.getApplication().getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        TerminalPaneController controller = newScratchpadController();
        TerminalPaneController.Window window = controller.newWindow(terminal());
        controller.showWindow(window);
        assertEquals(1, controller.tiledPaneCount());

        assertEquals(TerminalPaneController.SCRATCHPAD_TOGGLE_SHOWN, controller.toggleScratchpad());

        // The float is on screen and counted as a pane view, but it is not a tiled pane: the frame
        // line's owner must not change, or the pane behind it reflows its PTY.
        assertEquals(2, controller.getVisiblePaneViews().size());
        assertEquals(1, controller.tiledPaneCount());

        assertEquals(TerminalPaneController.SCRATCHPAD_TOGGLE_HIDDEN, controller.toggleScratchpad());
        assertEquals(1, controller.tiledPaneCount());
    }

    @Test
    public void tiledPaneCount_isNeverZeroAndFollowsTheTree() {
        // max(1, ...) covers the window with no tiled root at all, which dropping the last tiled
        // shell while a float survives can produce.
        assertEquals(1, newController().tiledPaneCount());
        assertEquals(4, fourPaneFixture().controller.tiledPaneCount());
    }

    @Test
    public void scratchpadShow_keepsTheTiledPaneViewsAttached() {
        // The visible jump: a full render detached and re-attached the tiled TerminalView, whose
        // onSizeChanged reflows the emulator and resets the scroll offset.
        android.provider.Settings.Global.putFloat(
            RuntimeEnvironment.getApplication().getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        FrameLayout host = new FrameLayout(RuntimeEnvironment.getApplication());
        TerminalPaneController controller = newScratchpadController(host);
        host.layout(0, 0, 1080, 2000);
        TerminalPaneController.Window window = controller.newWindow(terminal());
        controller.showWindow(window);
        TerminalView tiled = controller.getVisiblePaneViews().get(0);
        android.view.ViewParent tiledParent = tiled.getParent();
        int childrenBefore = host.getChildCount();

        assertEquals(TerminalPaneController.SCRATCHPAD_TOGGLE_SHOWN, controller.toggleScratchpad());

        assertSame(tiled, controller.getVisiblePaneViews().get(0));
        assertSame(tiledParent, tiled.getParent());
        assertEquals(childrenBefore + 1, host.getChildCount());

        assertEquals(TerminalPaneController.SCRATCHPAD_TOGGLE_HIDDEN, controller.toggleScratchpad());

        assertSame(tiled, controller.getVisiblePaneViews().get(0));
        assertSame(tiledParent, tiled.getParent());
        assertEquals(childrenBefore, host.getChildCount());
    }

    @Test
    public void clampFloatFractions_neverLetsAFloatHangBelowTheHost() {
        // Sideways overhang stays intentional: the handle spans the float's full width, so part of
        // it is always grabbable. Downward there is nothing to grab, and the overflow paints into
        // the dock band.
        RectF clamped = TerminalPaneController.clampFloatFractions(
            new RectF(0.1f, 0.8f, 0.7f, 1.4f), 1080f, 2000f, 200f, 180f, 40f);
        assertTrue("bottom " + clamped.bottom, clamped.bottom <= 1.0001f);
        assertEquals(0.6f, clamped.width(), .001f);

        // A host shorter than the minimum float height: the minimum wins, and the float is pinned
        // to the top rather than allowed to run off the bottom.
        RectF tiny = TerminalPaneController.clampFloatFractions(
            new RectF(0.1f, 0.5f, 0.7f, 0.9f), 1080f, 100f, 200f, 180f, 40f);
        assertEquals(0f, tiny.top, .001f);
        assertEquals(1f, tiny.height(), .001f);

        // Horizontal overhang is still permitted.
        RectF sideways = TerminalPaneController.clampFloatFractions(
            new RectF(0.7f, 0.1f, 1.5f, 0.5f), 1080f, 2000f, 200f, 180f, 40f);
        assertTrue("right " + sideways.right, sideways.right > 1f);
    }

    @Test
    public void clampFloatFractions_isIdempotent() {
        // Any future ratchet shows up here: clamping a clamped rect must not move it again.
        RectF[] candidates = {
            new RectF(0.1f, 0.8f, 0.7f, 1.4f),
            new RectF(-0.4f, -0.2f, 0.3f, 0.4f),
            new RectF(0.9f, 0.05f, 1.8f, 0.3f),
            new RectF(0.2f, 0.2f, 0.25f, 0.25f),
        };
        for (RectF candidate : candidates) {
            RectF once = TerminalPaneController.clampFloatFractions(
                candidate, 1080f, 2000f, 200f, 180f, 40f);
            RectF twice = TerminalPaneController.clampFloatFractions(
                once, 1080f, 2000f, 200f, 180f, 40f);
            assertEquals("left " + candidate, once.left, twice.left, .0001f);
            assertEquals("top " + candidate, once.top, twice.top, .0001f);
            assertEquals("right " + candidate, once.right, twice.right, .0001f);
            assertEquals("bottom " + candidate, once.bottom, twice.bottom, .0001f);
        }
    }

    @Test
    public void scratchpadBounds_survivesAKeyboardShrinkAndRegrow() {
        // The regression test for the ratchet: applyFloatBounds used to write its clamp result back
        // into floatFrac, so every keyboard open/close shrank the remembered shape a little more.
        android.provider.Settings.Global.putFloat(
            RuntimeEnvironment.getApplication().getContentResolver(),
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        FrameLayout host = new FrameLayout(RuntimeEnvironment.getApplication());
        TerminalPaneController controller = newScratchpadController(host);
        host.layout(0, 0, 1080, 2000);
        TerminalPaneController.Window window = controller.newWindow(terminal());
        controller.showWindow(window);
        assertEquals(TerminalPaneController.SCRATCHPAD_TOGGLE_SHOWN, controller.toggleScratchpad());

        RectF shaped = new RectF(0.07f, 0.06f, 0.93f, 0.72f);
        TerminalPaneController.Leaf leaf = window.floating.get(0);
        leaf.floatFrac = new RectF(shaped);

        // Keyboard up, then down. Robolectric dispatches the real OnLayoutChangeListener.
        host.layout(0, 0, 1080, 700);
        host.layout(0, 0, 1080, 2000);

        assertEquals(shaped.left, leaf.floatFrac.left, .001f);
        assertEquals(shaped.top, leaf.floatFrac.top, .001f);
        assertEquals(shaped.right, leaf.floatFrac.right, .001f);
        assertEquals(shaped.bottom, leaf.floatFrac.bottom, .001f);
        assertTrue(leaf.floatFrac.bottom <= 1.0001f);

        // And the shape persists across a hide and re-show.
        assertEquals(TerminalPaneController.SCRATCHPAD_TOGGLE_HIDDEN, controller.toggleScratchpad());
        assertEquals(TerminalPaneController.SCRATCHPAD_TOGGLE_SHOWN, controller.toggleScratchpad());
        assertEquals(shaped, window.floating.get(0).floatFrac);
    }

    @Test
    public void moveToEdge_rejectsSinglePaneWithoutChangingIt() {
        TerminalPaneController controller = newController();
        TerminalSession only = terminal();
        TerminalPaneController.Window window = controller.newWindow(only);
        controller.showWindow(window);

        assertFalse(controller.moveActivePaneToEdge(TerminalPaneController.EDGE_LEFT));
        assertEquals(Collections.singletonList(only), controller.shellsOf(window));
        assertEquals(only, controller.getActiveSession());
    }

    @Test
    public void fontSize_pinnedZoomIsInheritedBySplitsAndNewWindows() {
        TerminalPaneController controller = newSplittingController();
        TerminalPaneController.Window window = controller.newWindow(terminal());
        controller.showWindow(window);
        assertEquals(0, controller.getActivePaneFontSize());

        assertTrue(controller.setActivePaneFontSize(30));
        assertTrue(controller.split(LinearLayout.HORIZONTAL));
        assertEquals(30, controller.getActivePaneFontSize());

        TerminalPaneController.Window second = controller.newWindow(terminal());
        controller.showWindow(second);
        assertEquals(30, controller.getActivePaneFontSize());
    }

    @Test
    public void fontSize_savedAndRestoredPerPaneIncludingFloats() {
        TerminalPaneController source = newController();
        TerminalSession first = terminal();
        TerminalSession second = terminal();
        TerminalSession floater = terminal();
        TerminalPaneController.Leaf firstLeaf = new TerminalPaneController.Leaf(first);
        firstLeaf.fontSize = 24;
        TerminalPaneController.Leaf secondLeaf = new TerminalPaneController.Leaf(second);
        TerminalPaneController.Split root = new TerminalPaneController.Split();
        root.orientation = LinearLayout.HORIZONTAL;
        root.a = firstLeaf;
        root.b = secondLeaf;
        firstLeaf.parent = root;
        secondLeaf.parent = root;
        TerminalPaneController.Window window = new TerminalPaneController.Window(firstLeaf);
        window.root = root;
        TerminalPaneController.Leaf floatLeaf = new TerminalPaneController.Leaf(floater);
        floatLeaf.floatFrac = new RectF(.1f, .1f, .6f, .6f);
        floatLeaf.fontSize = 40;
        window.floating.add(floatLeaf);

        Bundle saved = source.saveWindow(window);
        Map<String, TerminalSession> sessions = new HashMap<>();
        sessions.put(first.mHandle, first);
        sessions.put(second.mHandle, second);
        sessions.put(floater.mHandle, floater);
        TerminalPaneController.Window restored = newController().restoreWindow(saved, sessions);

        TerminalPaneController.Split restoredRoot = (TerminalPaneController.Split) restored.root;
        assertEquals(24, ((TerminalPaneController.Leaf) restoredRoot.a).fontSize);
        assertEquals(0, ((TerminalPaneController.Leaf) restoredRoot.b).fontSize);
        assertEquals(40, restored.floating.get(0).fontSize);
    }

    /** Four panes whose controller can also create shells, so {@code split()} actually runs. */
    private static PaneFixture splittableFourPaneFixture() {
        PaneFixture base = fourPaneFixture(newSplittingController());
        return base;
    }

    private static TerminalPaneController newSplittingController() {
        Context context = RuntimeEnvironment.getApplication();
        return new TerminalPaneController(new TerminalPaneController.Host() {
            @Override public TerminalSession createShell(String cwd) { return terminal(); }
            @Override public void configurePaneView(TerminalView view) {}
            @Override public void removeShell(TerminalSession session) {}
            @Override public void onActivePaneChanged() {}
            @Override public void onTreesChanged() {}
            @Override public String defaultCwd() { return "/"; }
        }, new FrameLayout(context), LayoutInflater.from(context));
    }

    private static PaneFixture fourPaneFixture() {
        return fourPaneFixture(newController());
    }

    private static PaneFixture fourPaneFixture(TerminalPaneController controller) {
        List<TerminalSession> sessions = Arrays.asList(
            terminal(), terminal(), terminal(), terminal());
        TerminalWorkspace.Node root = new TerminalWorkspace.Split(
            TerminalWorkspace.Split.HORIZONTAL, 1.3f, .7f,
            new TerminalWorkspace.Pane("/one", null, null),
            new TerminalWorkspace.Split(TerminalWorkspace.Split.VERTICAL, .8f, 1.2f,
                new TerminalWorkspace.Pane("/two", null, null),
                new TerminalWorkspace.Split(TerminalWorkspace.Split.HORIZONTAL, .6f, 1.4f,
                    new TerminalWorkspace.Pane("/three", null, null),
                    new TerminalWorkspace.Pane("/four", null, null))));
        TerminalPaneController.Window window = controller.newWorkspaceWindow(
            new TerminalWorkspace.Window(2, root), sessions);
        controller.showWindow(window);
        return new PaneFixture(controller, window, sessions);
    }

    private static int countLeaves(TerminalPaneController.Node node) {
        if (node instanceof TerminalPaneController.Leaf) return 1;
        TerminalPaneController.Split split = (TerminalPaneController.Split) node;
        return countLeaves(split.a) + countLeaves(split.b);
    }

    private static void assertAllSplitsHaveOrientation(TerminalPaneController.Node node,
                                                       int orientation) {
        if (!(node instanceof TerminalPaneController.Split)) return;
        TerminalPaneController.Split split = (TerminalPaneController.Split) node;
        assertEquals(orientation, split.orientation);
        assertAllSplitsHaveOrientation(split.a, orientation);
        assertAllSplitsHaveOrientation(split.b, orientation);
    }

    private static void assertAllSplitWeights(TerminalPaneController.Node node,
                                              float weightA, float weightB) {
        if (!(node instanceof TerminalPaneController.Split)) return;
        TerminalPaneController.Split split = (TerminalPaneController.Split) node;
        assertEquals(weightA, split.weightA, .001f);
        assertEquals(weightB, split.weightB, .001f);
        assertAllSplitWeights(split.a, weightA, weightB);
        assertAllSplitWeights(split.b, weightA, weightB);
    }

    private static final class PaneFixture {
        final TerminalPaneController controller;
        final TerminalPaneController.Window window;
        final List<TerminalSession> sessions;

        PaneFixture(TerminalPaneController controller, TerminalPaneController.Window window,
                    List<TerminalSession> sessions) {
            this.controller = controller;
            this.window = window;
            this.sessions = sessions;
        }
    }

    private static TerminalPaneController newController() {
        Context context = RuntimeEnvironment.getApplication();
        return new TerminalPaneController(new TerminalPaneController.Host() {
            @Override public TerminalSession createShell(String cwd) { return null; }
            @Override public void configurePaneView(TerminalView view) {}
            @Override public void removeShell(TerminalSession session) {}
            @Override public void onActivePaneChanged() {}
            @Override public void onTreesChanged() {}
            @Override public String defaultCwd() { return "/"; }
        }, new FrameLayout(context), LayoutInflater.from(context));
    }

    /** Controller whose host can create named shells, so the scratchpad toggle actually runs. */
    private static TerminalPaneController newScratchpadController() {
        return newScratchpadController(new FrameLayout(RuntimeEnvironment.getApplication()));
    }

    /** As above, against a caller-owned host so a test can drive its layout size. */
    private static TerminalPaneController newScratchpadController(@NonNull FrameLayout hostView) {
        Context context = RuntimeEnvironment.getApplication();
        return new TerminalPaneController(new TerminalPaneController.Host() {
            @Override public TerminalSession createShell(String cwd) { return terminal(); }
            @Override public TerminalSession createNamedShell(String name, String cwd) {
                TerminalSession session = terminal();
                session.mSessionName = name;
                return session;
            }
            @Override public void configurePaneView(TerminalView view) {}
            @Override public void removeShell(TerminalSession session) {}
            @Override public void onActivePaneChanged() {}
            @Override public void onTreesChanged() {}
            @Override public String defaultCwd() { return "/"; }
        }, hostView, LayoutInflater.from(context));
    }

    private static TerminalSession terminal() {
        return new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);
    }
}
