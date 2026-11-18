package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.PageRef;

import org.junit.Test;

/**
 * The keyboard's foot (ADR 0029 section 6) changes the keyboard's own host and nothing the stack's
 * numbers stand on: with the keyboard down the stack lands where it did (the translation is the
 * keys' height), and the container is as tall with or without a foot, either side of a landing
 * and either side of a keyboard toggle at rest.
 */
public class KeyboardFootParityTest {

    private static final int NAV = 63;
    private static final int KEYS = LayoutFixtures.KEYBOARD_PX;

    @Test
    public void keyboardDownTranslationIsTheKeysHeightWhateverTheFoot() {
        for (int foot : new int[] {0, NAV}) {
            int keys = BottomInsets.keyboardKeysPx(KEYS + foot, foot);
            assertEquals(KEYS, keys);
            assertEquals(KEYS, StackScene.keyboardTranslationPx(0f, keys), 0f);
            assertEquals(0f, StackScene.keyboardTranslationPx(1f, keys), 0f);
        }
    }

    /** The rows' bottom, with the keyboard down, is where it was: the keys' translation lands it. */
    @Test
    public void rowsLandWhereTheyDidWithTheKeyboardDown() {
        for (boolean dockFoot : new boolean[] {false, true}) {
            BottomInsets today = BottomInsets.forKept(0, NAV, dockFoot, 0, 0);
            BottomInsets foot = BottomInsets.forKept(0, NAV, dockFoot, 0, 0, true);
            assertEquals(today.rowsBottomFromScreenBottomPx(), foot.rowsBottomFromScreenBottomPx());
            // What stands under the keys: the column margin, the foot (in the keyboard) and the
            // stack's padding.
            assertEquals(today.keyboardColumnMarginPx + today.stackPaddingPx,
                foot.keyboardColumnMarginPx + foot.keyboardFootPx + foot.stackPaddingPx);
        }
    }

    @Test
    public void containerHeightIsTheSameWithOrWithoutTheFootEitherSideOfAToggle() {
        for (LayoutFixtures.Fixture f : LayoutFixtures.ALL)
            for (boolean dockFoot : new boolean[] {false, true})
                for (boolean up : new boolean[] {false, true}) {
                    StackLayout.Result r = f.settle(PageRef.TERMINAL, up, false);
                    int keysDown = up ? 0 : f.keyboardLaidOutPx();
                    boolean flag = !f.floatingKeyboard();
                    BottomInsets today = BottomInsets.forKept(0, NAV, dockFoot, r.bottomMarginPx,
                        r.flushBottomInsetPx);
                    BottomInsets foot = BottomInsets.forKept(0, NAV, dockFoot, r.bottomMarginPx,
                        r.flushBottomInsetPx, flag);
                    String at = f.name + " dockFoot=" + dockFoot + " up=" + up;
                    assertEquals(at, StackScene.containerHeightPx(
                            r.stackHeightPx + today.containerExtraPx(), keysDown, 0),
                        StackScene.containerHeightPx(
                            r.stackHeightPx + foot.containerExtraPx(), keysDown, 0));
                    // Floating keeps its own geometry: a capsule keyboard has no foot.
                    if (!flag) assertEquals(at, 0, foot.keyboardFootPx);
                }
    }

    /** At keyboard-down rest the cut hides the keyboard whole, foot included. */
    @Test
    public void sceneCutsTheDownKeyboardWholeUnderThePill() {
        StackGeometry g = new StackGeometry();
        g.displayWidthPx = 1080;
        g.displayHeightPx = 2412;
        g.containerWidthPx = 1080;
        g.containerHeightPx = 1500;
        g.keyboardPresent = true;
        g.keyboardInColumn = true;
        g.keyboardWidthPx = 1080;
        g.keyboardHeightPx = KEYS + NAV;
        g.keyboardFootPx = NAV;
        StackScene.Motion m = new StackScene.Motion();
        m.dockTravelPx = KEYS;
        m.keyboardSharePx = KEYS;
        StackScene.Frame out = new StackScene.Frame();
        StackScene.at(null, null, 1f, m, g, out);
        assertTrue(out.clipped[StackScene.V_KEYBOARD]);
        assertEquals(0, out.clipBottom[StackScene.V_KEYBOARD]);
        m.dockTravelPx = 0f;
        m.keyboardSharePx = 0f;
        StackScene.at(null, null, 1f, m, g, out);
        assertFalse(out.clipped[StackScene.V_KEYBOARD]);
    }
}
