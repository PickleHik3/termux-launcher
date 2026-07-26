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
