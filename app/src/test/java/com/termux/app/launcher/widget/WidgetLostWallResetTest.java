package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A wall whose widget IDs the host no longer knows — restored onto another device, or the host's
 * own data wiped — is taken away in one piece and said once, instead of turning into a page of
 * placeholders. The per-widget case, one provider going while its neighbours stay, is untouched.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetLostWallResetTest {
    @Test public void policyReadsOnlyTheWholesaleLossAsALostWall() {
        List<LauncherWidgetRecord> wall = Arrays.asList(
            record(5, LauncherWidgetRecord.State.ACTIVE),
            record(6, LauncherWidgetRecord.State.ACTIVE));
        assertTrue(WidgetProviderReconcilePolicy.isWallLost(wall, owned(), false));
        // One survivor is enough to keep this a per-widget matter.
        assertFalse(WidgetProviderReconcilePolicy.isWallLost(wall, owned(6), false));
        // An add in flight defers the question to the reconciliation after it settles.
        assertFalse(WidgetProviderReconcilePolicy.isWallLost(wall, owned(), true));
        // A single widget cannot be told apart from an ordinary uninstall.
        assertFalse(WidgetProviderReconcilePolicy.isWallLost(
            Collections.singletonList(record(5, LauncherWidgetRecord.State.ACTIVE)),
            owned(), false));
        assertFalse(WidgetProviderReconcilePolicy.isWallLost(
            Collections.<LauncherWidgetRecord>emptyList(), owned(), false));
    }

    @Test public void tombstonesCountAndDeletionsDoNot() {
        // Placeholders left by an earlier loss are part of the wall that is going.
        assertTrue(WidgetProviderReconcilePolicy.isWallLost(Arrays.asList(
            record(5, LauncherWidgetRecord.State.ACTIVE),
            record(6, LauncherWidgetRecord.State.PROVIDER_MISSING)), owned(), false));
        // A record already on its way out proves nothing either way.
        assertFalse(WidgetProviderReconcilePolicy.isWallLost(Arrays.asList(
            record(5, LauncherWidgetRecord.State.ACTIVE),
            record(6, LauncherWidgetRecord.State.DELETING)), owned(), false));
    }

    @Test public void lostWallEmptiesRecordsShelfAndPagesAndSaysItOnce() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        repository.putRecord(new LauncherWidgetRecord(5, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(0, 0, 1, 1), new Bundle(), null));
        repository.putRecord(new LauncherWidgetRecord(6, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(1, 0, 2, 1), new Bundle(), null));
        repository.addPage();
        // Leave a layout on the shelf: the orientation that is not on screen holds placements for
        // these same IDs, and they have to go with them.
        repository.applyOrientation("portrait", WidgetGridDefinition.DEFAULT_ROWS,
            WidgetGridDefinition.DEFAULT_COLUMNS);
        repository.applyOrientation("landscape", WidgetGridDefinition.DEFAULT_COLUMNS,
            WidgetGridDefinition.DEFAULT_ROWS);
        assertEquals(Collections.singleton("portrait"), repository.storedOrientations());

        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        // The host knows none of these IDs: exactly what a restore onto another device leaves.
        LauncherWidgetHostController controller =
            new LauncherWidgetHostController(activity, repository, platform);
        List<LauncherWidgetHostController.AddResult> heard = new ArrayList<>();
        controller.setListener(heard::add);
        controller.onProvidersChanged();

        assertTrue(repository.records().isEmpty());
        assertEquals(1, repository.pageCount());
        assertTrue(repository.storedOrientations().isEmpty());
        assertNull(repository.pending());
        assertEquals("landscape", repository.orientation());
        assertTrue(platform.deleted.isEmpty());
        assertEquals(1, Collections.frequency(heard,
            LauncherWidgetHostController.AddResult.WALL_RESET));

        // Nothing is left to lose, so a second pass is silent.
        heard.clear();
        controller.onProvidersChanged();
        assertFalse(heard.contains(LauncherWidgetHostController.AddResult.WALL_RESET));
        assertTrue(repository.records().isEmpty());
    }

    @Test public void oneProviderGoingWhileOthersLiveStillTombstonesThatWidgetAlone() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        repository.putRecord(new LauncherWidgetRecord(5, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(0, 0, 1, 1), new Bundle(), null));
        repository.putRecord(new LauncherWidgetRecord(6, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(1, 0, 2, 1), new Bundle(), null));
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.info.put(5, WidgetTestFixtures.info(false));
        platform.info.put(6, WidgetTestFixtures.info(false));
        LauncherWidgetHostController controller =
            new LauncherWidgetHostController(activity, repository, platform);

        platform.info.remove(6);
        controller.onProvidersChanged();

        assertEquals(LauncherWidgetRecord.State.ACTIVE, repository.get(5).state);
        assertEquals(LauncherWidgetRecord.State.PROVIDER_MISSING, repository.get(6).state);
        assertEquals(Collections.singletonList(6), platform.deleted);
    }

    @Test public void anAddInFlightIsNotCollateral() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        // The first cell is left free: that is where the reservation below sits.
        repository.putRecord(new LauncherWidgetRecord(5, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(1, 0, 2, 1), new Bundle(), null));
        repository.putRecord(new LauncherWidgetRecord(6, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(2, 0, 3, 1), new Bundle(), null));
        WidgetAddTransaction pending = new WidgetAddTransaction("token", 7,
            WidgetTestFixtures.PROVIDER, 0, WidgetAddTransaction.Stage.COMMITTING,
            new Bundle(), System.currentTimeMillis());
        repository.setPending(pending);
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.info.put(7, WidgetTestFixtures.info(false));
        LauncherWidgetHostController controller =
            new LauncherWidgetHostController(activity, repository, platform);

        controller.reconcileProviders();

        // The wall was not swept out from under the widget being added; the two it already held
        // take the per-widget path instead.
        assertEquals(LauncherWidgetRecord.State.PROVIDER_MISSING, repository.get(5).state);
        assertEquals(LauncherWidgetRecord.State.PROVIDER_MISSING, repository.get(6).state);
    }

    @Test public void repositoryResetKeepsTheGridAndTheOrientationOnScreen() {
        WidgetTestFixtures.Memory storage = new WidgetTestFixtures.Memory();
        LauncherWidgetRepository repository = new LauncherWidgetRepository(storage);
        repository.applyOrientation("portrait", 3, 3);
        repository.putRecord(new LauncherWidgetRecord(5, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, new WidgetCellRect(0, 0, 1, 1), new Bundle(), null));
        repository.addFreshPage();
        WidgetGridDefinition grid = repository.gridDefinition();
        long before = repository.revision();

        assertTrue(repository.resetToEmptyWall());

        assertTrue(repository.records().isEmpty());
        assertEquals(1, repository.pageCount());
        assertTrue(repository.freshPages().isEmpty());
        assertEquals("portrait", repository.orientation());
        assertEquals(grid, repository.gridDefinition());
        assertTrue(repository.revision() > before);

        // It survives a reload: the empty wall was written, not only held in memory.
        LauncherWidgetRepository reloaded = new LauncherWidgetRepository(storage);
        assertTrue(reloaded.records().isEmpty());
        assertEquals(1, reloaded.pageCount());
        assertEquals("portrait", reloaded.orientation());
    }

    private static Set<Integer> owned(Integer... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    private static LauncherWidgetRecord record(int id, LauncherWidgetRecord.State state) {
        return new LauncherWidgetRecord(id, new ComponentName("pkg", "Provider"), 0, state,
            new Bundle(), null);
    }
}
