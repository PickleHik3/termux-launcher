package com.termux.app.editmode;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Round 5: edit mode labels the live scene's surfaces with dashed outlines, as the prototype. */
public class EditOutlinesTest {

    private static final EditOutlines.Labels LABELS =
        new EditOutlines.Labels("Wallpaper", "Home", "Control bar", "Keyboard");
    private static final EditOutlines.Box SCREEN = new EditOutlines.Box(0, 0, 400, 800);
    private static final EditOutlines.Box BAR = new EditOutlines.Box(8, 700, 392, 780);

    private static EditOutlines.Scene home(EditOutlines.Box keyboard) {
        return new EditOutlines.Scene(SCREEN, new EditOutlines.Box(0, 0, 400, 690),
            Collections.emptyList(), BAR, 20f, keyboard, 0f, 1f);
    }

    private static EditOutlines.Scene workspace(EditOutlines.Window... windows) {
        return new EditOutlines.Scene(SCREEN, null, Arrays.asList(windows), BAR, 20f, null, 0f, 1f);
    }

    private static String surfaces(List<EditOutlines.Outline> outlines) {
        StringBuilder text = new StringBuilder();
        for (EditOutlines.Outline outline : outlines) {
            if (text.length() > 0) text.append(' ');
            text.append(outline.surface).append(':').append(outline.label);
        }
        return text.toString();
    }

    @Test
    public void onHomeTheFrameHomeAndTheBarAreLabelled() {
        List<EditOutlines.Outline> outlines = EditOutlines.of(home(null), LABELS, null);
        assertEquals("WALLPAPER:Wallpaper HOME:Home BAR:Control bar", surfaces(outlines));
        // The prototype's insets: the frame 3dp in, Home 10/12/10/18 in from its page.
        assertEquals(new EditOutlines.Box(3, 3, 397, 797), outlines.get(0).box);
        assertEquals(EditOutlines.LabelAt.CHIP, outlines.get(0).labelAt);
        assertEquals(new EditOutlines.Box(10, 12, 390, 672), outlines.get(1).box);
        assertEquals(EditOutlines.LabelAt.INSIDE, outlines.get(1).labelAt);
        assertEquals(EditOutlines.LabelAt.ON_EDGE, outlines.get(2).labelAt);
        for (EditOutlines.Outline outline : outlines) assertFalse("at rest nothing is lit", outline.lit);
    }

    @Test
    public void theKeyboardUpIsLabelledToo() {
        EditOutlines.Box keyboard = new EditOutlines.Box(0, 500, 400, 690);
        List<EditOutlines.Outline> outlines = EditOutlines.of(home(keyboard), LABELS, null);
        assertEquals("WALLPAPER:Wallpaper HOME:Home BAR:Control bar KEYBOARD:Keyboard", surfaces(outlines));
    }

    @Test
    public void onAWorkspaceEachWindowWearsItsName() {
        List<EditOutlines.Outline> outlines = EditOutlines.of(workspace(
            new EditOutlines.Window(new EditOutlines.Box(8, 40, 200, 690), 14f, "fish"),
            new EditOutlines.Window(new EditOutlines.Box(208, 40, 600, 690), 14f, "vim"),
            new EditOutlines.Window(new EditOutlines.Box(-300, 40, -8, 690), 14f, "gone")), LABELS, null);
        assertEquals("a window off screen has none; one half on is clipped",
            "WALLPAPER:Wallpaper WINDOW:fish WINDOW:vim BAR:Control bar", surfaces(outlines));
        assertEquals(new EditOutlines.Box(208, 40, 400, 690), outlines.get(2).box);
        assertEquals(14f, outlines.get(1).radiusPx, 0f);
    }

    @Test
    public void theOpenCardsSurfaceIsLit() {
        assertTrue(EditOutlines.of(home(null), LABELS, EditModeController.Card.WALLPAPER).get(0).lit);
        assertTrue(EditOutlines.of(home(null), LABELS, EditModeController.Card.HOME).get(1).lit);
        assertTrue(EditOutlines.of(home(null), LABELS, EditModeController.Card.BAR).get(2).lit);
        List<EditOutlines.Outline> windows = EditOutlines.of(workspace(
            new EditOutlines.Window(new EditOutlines.Box(8, 40, 200, 690), 14f, "fish")), LABELS,
            EditModeController.Card.WINDOWS);
        assertTrue(windows.get(1).lit);
        assertFalse(windows.get(0).lit);
        // Global's card is every surface's look: no one outline is lit.
        for (EditOutlines.Outline outline : EditOutlines.of(home(null), LABELS, EditModeController.Card.GLOBAL))
            assertFalse(outline.lit);
    }

    @Test
    public void noScreenNoOutlines() {
        assertTrue(EditOutlines.of(new EditOutlines.Scene(new EditOutlines.Box(0, 0, 0, 0), null,
            Collections.emptyList(), null, 0f, null, 0f, 1f), LABELS, null).isEmpty());
    }
}
