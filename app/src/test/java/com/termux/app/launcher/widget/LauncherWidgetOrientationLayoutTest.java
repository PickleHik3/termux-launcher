package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Rotation. Each orientation keeps its own cell assignments, so a turn to landscape and back
 * leaves the portrait wall exactly as the user left it. The first turn into an orientation still
 * seeds from the other one's layout, and a save written before any of this existed moves nothing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class LauncherWidgetOrientationLayoutTest {

    private static final String PORTRAIT_KEY = "portrait";
    private static final String LANDSCAPE_KEY = "landscape";
    private static final WidgetGridDefinition PORTRAIT = new WidgetGridDefinition(5, 4);
    private static final WidgetGridDefinition LANDSCAPE = new WidgetGridDefinition(3, 6);

    /** The reproduction: without a layout per orientation, the second reflow starts from the first. */
    @Test public void portraitToLandscapeAndBackRestoresTheOriginalCells() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertEquals(PORTRAIT, repository.gridDefinition());
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 4, 4), 0)));
        assertTrue(repository.putRecord(record(2, new WidgetCellRect(0, 4, 2, 5), 0)));
        // The orientation the wall was arranged in is named first; nothing moves for it.
        assertFalse(repository.applyOrientation(PORTRAIT_KEY, PORTRAIT));

        assertTrue(repository.applyOrientation(LANDSCAPE_KEY, LANDSCAPE));
        assertEquals(LANDSCAPE, repository.gridDefinition());
        // Landscape is shorter: the tall widget was squeezed to fit it.
        assertEquals(new WidgetCellRect(0, 0, 4, 3), repository.get(1).cell);

        assertTrue(repository.applyOrientation(PORTRAIT_KEY, PORTRAIT));

        assertEquals(PORTRAIT, repository.gridDefinition());
        assertEquals(new WidgetCellRect(0, 0, 4, 4), repository.get(1).cell);
        assertEquals(new WidgetCellRect(0, 4, 2, 5), repository.get(2).cell);
        assertEquals(0, repository.get(1).page);
        assertEquals(0, repository.get(2).page);
        assertEquals(1, repository.pageCount());
    }

    @Test public void eachSideKeepsItsOwnArrangementOnceItHasOne() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 2, 2), 0)));
        assertFalse(repository.applyOrientation(PORTRAIT_KEY, PORTRAIT));
        assertTrue(repository.applyOrientation(LANDSCAPE_KEY, LANDSCAPE));

        // Moved by hand while landscape was on screen.
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(4, 1, 6, 3), 0)));

        assertTrue(repository.applyOrientation(PORTRAIT_KEY, PORTRAIT));
        assertEquals("portrait is untouched by a landscape move",
            new WidgetCellRect(0, 0, 2, 2), repository.get(1).cell);
        assertTrue(repository.applyOrientation(LANDSCAPE_KEY, LANDSCAPE));
        assertEquals("landscape kept the hand-placed cell",
            new WidgetCellRect(4, 1, 6, 3), repository.get(1).cell);

        // And across a reload: the shelf is durable.
        LauncherWidgetRepository reloaded = new LauncherWidgetRepository(storage);
        assertEquals(LANDSCAPE_KEY, reloaded.orientation());
        assertTrue(reloaded.applyOrientation(PORTRAIT_KEY, PORTRAIT));
        assertEquals(new WidgetCellRect(0, 0, 2, 2), reloaded.get(1).cell);
    }

    @Test public void theSameGridInBothOrientationsStillKeepsTwoArrangements() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 2, 2), 0)));
        assertFalse(repository.applyOrientation(PORTRAIT_KEY, PORTRAIT));
        assertTrue(repository.applyOrientation(LANDSCAPE_KEY, PORTRAIT));
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(2, 3, 4, 5), 0)));

        assertTrue(repository.applyOrientation(PORTRAIT_KEY, PORTRAIT));
        assertEquals(new WidgetCellRect(0, 0, 2, 2), repository.get(1).cell);
    }

    @Test public void aWidgetAddedWhileTheOtherSideWasShowingKeepsItsPlaceAndFindsRoom() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 2, 2), 0)));
        assertFalse(repository.applyOrientation(PORTRAIT_KEY, PORTRAIT));
        assertTrue(repository.applyOrientation(LANDSCAPE_KEY, LANDSCAPE));
        // Added while landscape was on screen, so portrait's shelf has never heard of them.
        assertTrue(repository.putRecord(record(2, new WidgetCellRect(2, 0, 4, 2), 0)));
        assertTrue(repository.putRecord(record(3, new WidgetCellRect(4, 2, 5, 3), 0)));

        assertTrue(repository.applyOrientation(PORTRAIT_KEY, PORTRAIT));

        assertEquals("the widget portrait remembers is where it was",
            new WidgetCellRect(0, 0, 2, 2), repository.get(1).cell);
        assertEquals(3, repository.records().size());
        // Neither newcomer was dropped, and neither sits on top of anything.
        assertTrue(WidgetGridPlacementPolicy.validate(PORTRAIT, repository.recordsOnPage(0)));
    }

    @Test public void oneRemovedOnTheOtherSideIsNotBroughtBack() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 2, 2), 0)));
        assertTrue(repository.putRecord(record(2, new WidgetCellRect(2, 0, 4, 2), 0)));
        assertFalse(repository.applyOrientation(PORTRAIT_KEY, PORTRAIT));
        assertTrue(repository.applyOrientation(LANDSCAPE_KEY, LANDSCAPE));
        assertTrue(repository.removeRecord(2));

        assertTrue(repository.applyOrientation(PORTRAIT_KEY, PORTRAIT));
        assertEquals(1, repository.records().size());
        assertNull(repository.get(2));
        assertEquals(new WidgetCellRect(0, 0, 2, 2), repository.get(1).cell);
    }

    @Test public void aV3SaveIsAdoptedWithoutMovingAnything() throws Exception {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository seed = new LauncherWidgetRepository(storage);
        assertTrue(seed.putRecord(record(1, new WidgetCellRect(1, 2, 3, 4), 0)));
        JSONObject v3 = new JSONObject(storage.value).put("version", 3);
        v3.remove("orientation");
        v3.remove("layouts");
        storage.value = v3.toString();

        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertNull("a v3 save names no orientation", repository.orientation());
        assertEquals(new WidgetCellRect(1, 2, 3, 4), repository.get(1).cell);
        // Naming the orientation on screen moves nothing, and the next write is v4.
        assertFalse(repository.applyOrientation(PORTRAIT_KEY, PORTRAIT));
        assertEquals(new WidgetCellRect(1, 2, 3, 4), repository.get(1).cell);
        assertEquals(4, new JSONObject(storage.value).getInt("version"));
        assertEquals(PORTRAIT_KEY, new JSONObject(storage.value).getString("orientation"));
    }

    @Test public void aTurnIsRefusedWhileAnAddIsInFlight() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertFalse(repository.applyOrientation(PORTRAIT_KEY, PORTRAIT));
        long revision = repository.revision();
        assertTrue(repository.reservePending(revision, new WidgetAddTransaction("token", 9,
            new ComponentName("pkg", "P9"), 0, WidgetAddTransaction.Stage.ALLOCATED,
            new WidgetCellRect(0, 0, 2, 2), 0, revision, null, new Bundle(), 10)));

        assertFalse(repository.applyOrientation(LANDSCAPE_KEY, LANDSCAPE));
        assertEquals(PORTRAIT_KEY, repository.orientation());
        assertEquals(PORTRAIT, repository.gridDefinition());
    }

    @Test public void theShelfSurvivesAReloadAndNamesOnlyTheOtherSides() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        assertTrue(repository.putRecord(record(1, new WidgetCellRect(0, 0, 4, 4), 0)));
        assertFalse(repository.applyOrientation(PORTRAIT_KEY, PORTRAIT));
        assertTrue(repository.applyOrientation(LANDSCAPE_KEY, LANDSCAPE));

        assertEquals(java.util.Collections.singleton(PORTRAIT_KEY), repository.storedOrientations());
        LauncherWidgetRepository reloaded = new LauncherWidgetRepository(storage);
        assertEquals(LANDSCAPE_KEY, reloaded.orientation());
        assertEquals(java.util.Collections.singleton(PORTRAIT_KEY), reloaded.storedOrientations());
    }

    private static LauncherWidgetRecord record(int id, WidgetCellRect cell, int page) {
        return new LauncherWidgetRecord(id, new ComponentName("pkg", "P" + id), 0L,
            LauncherWidgetRecord.State.ACTIVE, cell, page, null, null);
    }
}
