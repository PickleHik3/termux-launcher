package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * A keyboard all the way down is cut whole at every page's rest, whatever bottom padding that
 * page's stack has, so a landing between two such pages writes nothing to its clip.
 */
public class StackSceneKeyboardDownCutTest {

    private static final int KEYBOARD_PX = 654;

    private static StackScene.Frame restDown(int stackPaddingPx) {
        StackGeometry g = new StackGeometry();
        g.displayWidthPx = 1080;
        g.displayHeightPx = 2400;
        g.keyboardPresent = true;
        g.keyboardInColumn = true;
        g.keyboardWidthPx = 1080;
        g.keyboardHeightPx = KEYBOARD_PX;
        g.keyboardFootPx = 0;
        g.underShown = false;
        g.belowKeyboardInColumnPx = 0;
        g.stackPaddingBottomPx = stackPaddingPx;
        StackScene.Motion m = new StackScene.Motion();
        m.dockTravelPx = KEYBOARD_PX;
        m.keyboardSharePx = StackScene.keyboardTranslationPx(0f, KEYBOARD_PX);
        StackScene.Frame out = new StackScene.Frame();
        StackScene.at(null, null, 0f, m, g, out);
        return out;
    }

    @Test
    public void aDownKeyboardIsCutWholeWhateverThePagesStackPadding() {
        int v = StackScene.V_KEYBOARD;
        for (int pad : new int[] {0, 1, 3}) {
            StackScene.Frame out = restDown(pad);
            assertEquals("pad=" + pad + " clipped", true, out.clipped[v]);
            assertEquals("pad=" + pad + " clipBottom", 0, out.clipBottom[v]);
        }
    }
}
