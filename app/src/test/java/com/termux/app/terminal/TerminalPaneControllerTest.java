package com.termux.app.terminal;

import android.app.Application;
import android.os.Build;
import android.graphics.RectF;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

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
    public void moveToEdge_rejectsSinglePaneWithoutChangingIt() {
        TerminalPaneController controller = newController();
        TerminalSession only = terminal();
        TerminalPaneController.Window window = controller.newWindow(only);
        controller.showWindow(window);

        assertFalse(controller.moveActivePaneToEdge(TerminalPaneController.EDGE_LEFT));
        assertEquals(Collections.singletonList(only), controller.shellsOf(window));
        assertEquals(only, controller.getActiveSession());
    }

    private static PaneFixture fourPaneFixture() {
        TerminalPaneController controller = newController();
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

    private static TerminalSession terminal() {
        return new TerminalSession("/bin/sh", "/", new String[0], new String[0], 2000, null);
    }
}
