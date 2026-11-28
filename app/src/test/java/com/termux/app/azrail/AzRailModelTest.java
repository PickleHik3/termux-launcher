package com.termux.app.azrail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The A–Z rail's letters, geometry and picks (#437). */
public class AzRailModelTest {

    private AzRailModel mModel;

    private static LauncherAppEntry app(String label) {
        return new LauncherAppEntry(new AppRef("pkg." + label, "A"), label, null);
    }

    @Before
    public void setUp() {
        List<LauncherAppEntry> apps = new ArrayList<>();
        for (String label : new String[] {"Zed", "Alpha", "Apple", "Bravo", "1Pass", "Camera"}) {
            apps.add(app(label));
        }
        mModel = new AzRailModel(apps);
        // 1000 wide, rail 40 against the right edge, y 0..500 over 5 letters (#, A, B, C, Z): 100 each.
        mModel.layout(AzRailModel.Side.RIGHT, 1000f, 0f, 40f, 0f, 500f, 200f, 40f);
    }

    @Test
    public void lettersAreTheOnesAppsStartWithHashFirst() {
        assertEquals(5, mModel.letterCount());
        assertEquals('#', mModel.letterAt(0));
        assertEquals('A', mModel.letterAt(1));
        assertEquals('Z', mModel.letterAt(4));
        assertEquals(2, mModel.fanApps(1).size());
    }

    @Test
    public void theThumbSideIsTheHalfThePressIsIn() {
        assertEquals(AzRailModel.Side.LEFT, AzRailModel.sideOf(100f, 1000f));
        assertEquals(AzRailModel.Side.RIGHT, AzRailModel.sideOf(900f, 1000f));
    }

    @Test
    public void theRailStandsAgainstTheEdgeAndTheFanBesideIt() {
        assertEquals(960f, mModel.railLeft(), 0f);
        assertEquals(1000f, mModel.railRight(), 0f);
        assertEquals(760f, mModel.fanLeft(1), 0f);
        assertEquals(960f, mModel.fanRight(1), 0f);
        mModel.layout(AzRailModel.Side.LEFT, 1000f, 60f, 40f, 0f, 500f, 200f, 40f);
        assertEquals(60f, mModel.railLeft(), 0f);
        assertEquals(100f, mModel.fanLeft(1), 0f);
    }

    @Test
    public void aFingerOnTheRailHoldsTheLetterAtItsHeight() {
        mModel.move(980f, 150f);
        assertEquals(1, mModel.letter());
        mModel.move(980f, 450f);
        assertEquals(4, mModel.letter());
    }

    @Test
    public void aFingerFurtherInHoldsTheLetterByHeightToo() {
        mModel.move(300f, 250f);
        assertEquals(2, mModel.letter());
    }

    @Test
    public void theFanIsCentredOnTheLettersSlotAndKeptInside() {
        assertEquals(110f, mModel.fanTop(1), 0.01f); // centre 150, 2 items of 40 = 80 tall
        assertEquals(30f, mModel.fanTop(0), 0.01f); // one item, centre 50
        assertEquals(430f, mModel.fanTop(4), 0.01f); // centre 450, one item
    }

    @Test
    public void insideTheFanTheLetterHoldsAndTheAppIsPickedByHeight() {
        mModel.move(980f, 150f); // letter A
        assertEquals(0, mModel.move(800f, 120f));
        assertEquals(1, mModel.move(800f, 150f));
        assertEquals(-1, mModel.move(800f, 300f));
        assertEquals(1, mModel.letter());
    }

    @Test
    public void lettingGoOnAnAppOpensItAndAnywhereElseOpensNothing() {
        mModel.move(980f, 150f);
        LauncherAppEntry opened = mModel.release(800f, 120f);
        assertNotNull(opened);
        assertEquals("Alpha", opened.label);
        assertNull(mModel.release(980f, 150f));
        assertNull(mModel.release(800f, 300f));
    }

    @Test
    public void aLongLetterFansEveryAppInColumnsInward() {
        List<LauncherAppEntry> many = new ArrayList<>();
        for (int i = 0; i < 20; i++) many.add(app("A" + (char) ('a' + i)));
        AzRailModel model = new AzRailModel(many);
        model.layout(AzRailModel.Side.RIGHT, 1000f, 0f, 40f, 0f, 500f, 200f, 40f);
        assertEquals(20, model.fanApps(0).size());
        assertEquals(3, model.fanColumns(0));
        model.move(980f, 250f);
        java.util.Set<String> reached = new java.util.HashSet<>();
        for (float x = model.fanLeft(0) + 1f; x < model.fanRight(0); x += 5f) {
            for (float y = model.fanTop(0) + 1f; y < model.fanTop(0) + 7 * 40f; y += 5f) {
                LauncherAppEntry e = model.release(x, y);
                if (e != null) reached.add(e.label);
            }
        }
        assertEquals(20, reached.size());
        assertTrue(model.fanLeft(0) >= 0f && model.fanRight(0) <= 960f);
    }

    @Test
    public void anEmptyCatalogueHoldsNothing() {
        AzRailModel model = new AzRailModel(new ArrayList<>());
        model.layout(AzRailModel.Side.RIGHT, 1000f, 0f, 40f, 0f, 500f, 200f, 40f);
        assertEquals(-1, model.move(980f, 100f));
        assertNull(model.release(800f, 100f));
    }
}
