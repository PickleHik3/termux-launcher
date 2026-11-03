package com.termux.app.place;

import static org.junit.Assert.assertEquals;

import com.termux.app.wall.PageRef;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;

/**
 * Pair 2 of the motion audit: the dock the travel slides ({@link
 * PlaceChromeTravel#dockRowsLaidOutPx}, {@link PlaceChromeTravel#dockLaidOutPx}, read off the
 * stack the settle laid out) is the dock the settle laid out ({@link StackLayout#layout}: the
 * dock's rows, the air under the keyboard and the flush padding), on every layout fixture and in
 * both dock Styles. At rest the travel moves nothing, and a dock going away leaves exactly the
 * keyboard and a bottom status band on screen.
 */
@RunWith(Parameterized.class)
public class DockLiftParityTest {

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> fixtures() {
        return LayoutFixtures.parameters();
    }

    private final LayoutFixtures.Fixture f;

    public DockLiftParityTest(LayoutFixtures.Fixture fixture) {
        f = fixture;
    }

    private void assertTravelDockIsTheSettlesDock(boolean keyboardUp, boolean dockFloating) {
        String what = f + (dockFloating ? ", Floating" : ", Docked")
            + (keyboardUp ? ", keyboard up" : ", keyboard down");
        StackLayout.Input in = f.pass(PageRef.TERMINAL, keyboardUp, dockFloating);
        StackLayout.Result r = StackLayout.layout(in, new com.termux.app.dock.DockGeometry());
        int keyboardPx = keyboardUp ? f.keyboardLaidOutPx() : 0;
        int rows = PlaceChromeTravel.dockRowsLaidOutPx(r.stackHeightPx, keyboardPx,
            r.statusBandPx);
        // What the settle laid out as the dock: its rows (DockGeometry), the air under the
        // keyboard and the flush padding.
        int settled = r.dock.combinedHeight(in.toolbarHeightPx, in.extraKeysRowEnabled)
            + r.underKeyboardAirPx + r.flushPaddingPx;
        assertEquals(what + ": rows", settled, rows);
        int dockPx = PlaceChromeTravel.dockLaidOutPx(rows, r.bottomMarginPx);
        assertEquals(what + ": the dock and the margin under the stack",
            settled + r.bottomMarginPx, dockPx);

        PlaceChromeTravel.Rest rest = new PlaceChromeTravel.Rest(keyboardUp, false);
        assertEquals(what + ": at rest nothing slides", 0f, PlaceChromeTravel.stackTranslationPx(
            PlaceChromeTravel.rest(PaneWallPage.TERMINAL, rest), keyboardPx, dockPx), 0f);
        // The dock gone, the keyboard still up: what stands over the screen's bottom edge is the
        // keyboard and a bottom status band, nothing of the dock.
        float gone = PlaceChromeTravel.stackTranslationPx(keyboardUp ? 1f : 0f, 0f, keyboardPx,
            dockPx);
        assertEquals(what + ": a dock gone leaves the keyboard and the band",
            keyboardPx + r.statusBandPx, r.stackHeightPx + r.bottomMarginPx - gone, 0f);
    }

    @Test
    public void theDockTheTravelSlidesIsTheDockTheSettleLaidOutKeyboardUp() {
        assertTravelDockIsTheSettlesDock(true, false);
        assertTravelDockIsTheSettlesDock(true, true);
    }

    @Test
    public void theDockTheTravelSlidesIsTheDockTheSettleLaidOutKeyboardDown() {
        assertTravelDockIsTheSettlesDock(false, false);
        assertTravelDockIsTheSettlesDock(false, true);
    }
}
