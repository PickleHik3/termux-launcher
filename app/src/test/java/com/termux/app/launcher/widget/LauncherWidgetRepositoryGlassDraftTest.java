package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.os.Bundle;

import com.termux.app.launcher.widget.builtin.BuiltinWidgetGlass;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The Appearance editor's "Glass widgets" switch drafts the widgets' own glass choices (#254):
 * shown at once, written once on Done, dropped on Discard with nothing to put back.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = android.app.Application.class)
public class LauncherWidgetRepositoryGlassDraftTest {

    private static LauncherWidgetRecord builtin(int id, int column, String glass) {
        Bundle config = new Bundle();
        if (glass != null) config.putString(BuiltinWidgetGlass.CONFIG_KEY, glass);
        return LauncherWidgetRecord.builtin(id, "battery", new WidgetCellRect(column, 0, column + 1, 1), 0,
            config);
    }

    private static String glass(LauncherWidgetRecord record) {
        return record.sizeOptions().getString(BuiltinWidgetGlass.CONFIG_KEY);
    }

    private static String shown(LauncherWidgetRepository repository, int id) {
        for (LauncherWidgetRecord record : repository.records())
            if (record.appWidgetId == id) return glass(record);
        throw new AssertionError("no widget " + id);
    }

    /** What the file holds, read afresh. */
    private static String stored(WidgetTestFixtures.Memory memory, int id) {
        return glass(new LauncherWidgetRepository(memory).get(id));
    }

    private static LauncherWidgetRepository wall(WidgetTestFixtures.Memory memory) {
        LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
        assertTrue(repository.putRecord(builtin(-1, 0, BuiltinWidgetGlass.ON)));
        assertTrue(repository.putRecord(builtin(-2, 1, BuiltinWidgetGlass.OFF)));
        return repository;
    }

    @Test public void theSwitchShowsEveryWidgetFollowingThePageAndWritesNothing() {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = wall(memory);
        long revision = repository.revision();
        assertTrue(repository.draftGlassFollowsPage());
        assertNull(shown(repository, -1));
        assertNull(glass(repository.get(-2)));
        assertNull(glass(repository.recordsOnPage(0).get(0)));
        assertEquals("nothing written", revision, repository.revision());
        assertEquals(BuiltinWidgetGlass.ON, stored(memory, -1));
        assertTrue(repository.isGlassDraftDirty());
    }

    @Test public void doneWritesTheDraftOnce() {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = wall(memory);
        long revision = repository.revision();
        repository.draftGlassFollowsPage();
        assertTrue(repository.commitGlassDraft());
        assertEquals("one write", revision + 1, repository.revision());
        assertNull(stored(memory, -1));
        assertNull(stored(memory, -2));
        assertFalse(repository.isGlassDraftDirty());
    }

    @Test public void discardLeavesTheStoreAsItWas() {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = wall(memory);
        long revision = repository.revision();
        repository.draftGlassFollowsPage();
        assertTrue("something to show again", repository.discardGlassDraft());
        assertEquals(revision, repository.revision());
        assertEquals(BuiltinWidgetGlass.ON, shown(repository, -1));
        assertEquals(BuiltinWidgetGlass.OFF, shown(repository, -2));
        assertFalse(repository.isGlassDraftDirty());
        assertFalse("nothing held now", repository.discardGlassDraft());
    }

    @Test public void aWidgetThatAlreadyFollowsThePageIsNoUnsavedChange() {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(memory);
        assertTrue(repository.putRecord(builtin(-1, 0, null)));
        assertTrue(repository.putRecord(builtin(-2, 1, BuiltinWidgetGlass.BARE)));
        assertFalse("nothing to show differently", repository.draftGlassFollowsPage());
        assertFalse(repository.isGlassDraftDirty());
        assertEquals("Bare is a look of its own", BuiltinWidgetGlass.BARE, shown(repository, -2));
    }

    @Test public void aWidgetWrittenFromOutsideSurvivesDoneAndDiscard() {
        for (boolean done : new boolean[]{true, false}) {
            WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
            LauncherWidgetRepository repository = wall(memory);
            repository.draftGlassFollowsPage();
            // A widget added meanwhile, with a choice of its own: not in the draft.
            assertTrue(repository.putRecord(builtin(-3, 2, BuiltinWidgetGlass.ON)));
            assertEquals("shows at once", BuiltinWidgetGlass.ON, shown(repository, -3));
            if (done) repository.commitGlassDraft();
            else repository.discardGlassDraft();
            assertEquals(BuiltinWidgetGlass.ON, stored(memory, -3));
            assertEquals(done ? null : BuiltinWidgetGlass.ON, stored(memory, -1));
        }
    }

    @Test public void aWidgetBothTouchedShowsTheDraftUntilDone() {
        WidgetTestFixtures.Memory memory = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = wall(memory);
        repository.draftGlassFollowsPage();
        assertTrue(repository.putRecord(builtin(-1, 0, BuiltinWidgetGlass.OFF)));
        assertNull("the draft wins", shown(repository, -1));
        assertEquals(BuiltinWidgetGlass.OFF, stored(memory, -1));
        repository.commitGlassDraft();
        assertNull(stored(memory, -1));
    }
}
