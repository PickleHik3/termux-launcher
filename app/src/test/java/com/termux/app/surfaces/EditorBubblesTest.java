package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;
import com.termux.app.wall.UseAs;

import org.junit.Test;

import java.util.Arrays;

/** Which bubbles the editor bar offers, which is marked and what each opens. */
public class EditorBubblesTest {

    @Test
    public void homeScreenOffersAllThreeInOrder() {
        assertEquals(Arrays.asList(EditorBubbles.GLOBAL, EditorBubbles.HOME, EditorBubbles.WORKSPACE),
            EditorBubbles.offered(UseAs.HOME));
    }

    @Test
    public void terminalOnlyHasNoHomeBubble() {
        assertEquals(Arrays.asList(EditorBubbles.GLOBAL, EditorBubbles.WORKSPACE),
            EditorBubbles.offered(UseAs.TERMINAL));
    }

    @Test
    public void theBubbleOfTheCardOnShowIsMarked() {
        assertEquals(EditorBubbles.GLOBAL, EditorBubbles.markedFor(EditorCard.APPEARANCE));
        assertEquals(EditorBubbles.HOME, EditorBubbles.markedFor(EditorCard.HOME));
        assertEquals(EditorBubbles.WORKSPACE, EditorBubbles.markedFor(EditorCard.TERMINAL));
    }

    @Test
    public void barCardsAndNoCardMarkNothing() {
        assertNull(EditorBubbles.markedFor(EditorCard.STATUS));
        assertNull(EditorBubbles.markedFor(EditorCard.DOCK));
        assertNull(EditorBubbles.markedFor(EditorCard.KEYBOARD));
        assertNull(EditorBubbles.markedFor(null));
    }

    @Test
    public void homeAndWorkspaceTurnTheFrameGlobalDoesNot() {
        assertTrue(EditorBubbles.HOME.turnsFrame());
        assertTrue(EditorBubbles.WORKSPACE.turnsFrame());
        assertFalse(EditorBubbles.GLOBAL.turnsFrame());
        assertEquals(EditorCard.APPEARANCE, EditorBubbles.GLOBAL.card);
        assertEquals(EditorCard.HOME, EditorBubbles.HOME.card);
        assertEquals(EditorCard.TERMINAL, EditorBubbles.WORKSPACE.card);
    }

    @Test
    public void aTurningBubbleSlidesTheFrameFromTheOtherSideAndGlobalNever() {
        assertEquals(PageKind.HOME,
            SurfaceEditorController.pillPlace(EditorBubbles.HOME.card, PageKind.TERMINAL));
        assertEquals(PageKind.TERMINAL,
            SurfaceEditorController.pillPlace(EditorBubbles.WORKSPACE.card, PageKind.HOME));
        assertNull(SurfaceEditorController.pillPlace(EditorBubbles.GLOBAL.card, PageKind.HOME));
        assertNull(SurfaceEditorController.pillPlace(EditorBubbles.GLOBAL.card, PageKind.TERMINAL));
    }
}
