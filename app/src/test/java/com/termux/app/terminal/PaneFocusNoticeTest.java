package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PaneFocusNoticeTest {

    private final Object windowA = new Object();
    private final Object windowB = new Object();
    private final Object shell1 = new Object();
    private final Object shell2 = new Object();
    private final Object view1 = new Object();
    private final Object view2 = new Object();

    @Test
    public void fingerOnThePaneAlreadyFocusedTellsNothing() {
        // Every swipe that starts on the Workspace lands here: the bar shows this pane already.
        assertEquals(PaneFocusNotice.Kind.NONE, PaneFocusNotice.of(
            shell1, windowA, view1, shell1, windowA, view1, false));
    }

    @Test
    public void anotherPaneOfTheSameWindowMovesTheChipsOnly() {
        assertEquals(PaneFocusNotice.Kind.PANE, PaneFocusNotice.of(
            shell1, windowA, view1, shell2, windowA, view2, false));
    }

    @Test
    public void aRebuiltViewForTheSameShellIsPassedOn() {
        // The host keeps the active view for the keyboard and extra keys; a new view must reach it.
        assertEquals(PaneFocusNotice.Kind.PANE, PaneFocusNotice.of(
            shell1, windowA, view1, shell1, windowA, view2, false));
    }

    @Test
    public void anotherWindowIsTheFullRefresh() {
        assertEquals(PaneFocusNotice.Kind.FULL, PaneFocusNotice.of(
            shell1, windowA, view1, shell2, windowB, view2, false));
    }

    @Test
    public void theFirstNoticeIsTheFullRefresh() {
        assertEquals(PaneFocusNotice.Kind.FULL, PaneFocusNotice.of(
            null, null, null, shell1, windowA, view1, false));
    }

    @Test
    public void takingFocusBackFromAHomeFloatIsTheFullRefresh() {
        // Same pane as told, but the float gave the keyboard back: the bar's selection moves.
        assertEquals(PaneFocusNotice.Kind.FULL, PaneFocusNotice.of(
            shell1, windowA, view1, shell1, windowA, view1, true));
    }
}
