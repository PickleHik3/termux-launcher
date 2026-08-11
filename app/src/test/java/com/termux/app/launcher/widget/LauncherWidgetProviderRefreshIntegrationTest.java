package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class LauncherWidgetProviderRefreshIntegrationTest {
    @Test public void productionPackageSeamIsAboveDockGuard() throws Exception {
        String source = read("app/src/main/java/com/termux/app/TermuxActivity.java");
        int method = source.indexOf("private void refreshSuggestionBarFromPackageState");
        int reconcile = source.indexOf("mWidgetHostController.reconcileProviders()", method);
        int dockGuard = source.indexOf("if (!isLauncherCatalogEnabled()", method);
        assertTrue(reconcile > method && reconcile < dockGuard);
    }

    @Test public void providerUpdateKeepsIdAndUninstallTombstonesAndDeletes() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        repository.putRecord(new LauncherWidgetRecord(5, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, null, "draw"));
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.info.put(5, WidgetTestFixtures.info(false));
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            repository, platform);
        controller.onProviderChanged(5, platform.info.get(5));
        assertEquals(LauncherWidgetRecord.State.ACTIVE, repository.get(5).state);
        assertEquals(null, repository.get(5).lastRenderFailure);
        platform.info.remove(5);
        controller.onProvidersChanged();
        assertEquals(LauncherWidgetRecord.State.PROVIDER_MISSING, repository.get(5).state);
        assertEquals(1, platform.deleted.size());
    }

    @Test public void committingTransactionIsFinalizedOnFirstStartupReconcile() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        WidgetAddTransaction committing = new WidgetAddTransaction("commit", 7,
            WidgetTestFixtures.PROVIDER, 0, WidgetAddTransaction.Stage.COMMITTING,
            new android.os.Bundle(), System.currentTimeMillis());
        repository.setPending(committing);
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.info.put(7, WidgetTestFixtures.info(false));
        new LauncherWidgetHostController(activity, repository, platform).onStart();
        assertNull(repository.pending());
        assertEquals(LauncherWidgetRecord.State.ACTIVE, repository.get(7).state);
    }

    @Test public void failedTombstoneDeletionRetriesWhileAllocationRemainsOwned() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        repository.putRecord(new LauncherWidgetRecord(5, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.PROVIDER_MISSING, null, null));
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.info.put(5, WidgetTestFixtures.info(false));
        platform.deleteFailure = new RuntimeException("service");
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            repository, platform);
        controller.reconcileProviders();
        platform.deleteFailure = null;
        controller.reconcileProviders();
        assertEquals(2, platform.deleted.size());
        assertEquals(LauncherWidgetRecord.State.PROVIDER_MISSING, repository.get(5).state);
    }

    @Test public void resumedDeletionConsumesMatchingPendingLock() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        WidgetAddTransaction pending = new WidgetAddTransaction("delete", 6,
            WidgetTestFixtures.PROVIDER, 0, WidgetAddTransaction.Stage.ALLOCATED,
            new android.os.Bundle(), System.currentTimeMillis());
        repository.setPending(pending);
        repository.beginPendingDeletion(pending);
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.info.put(6, WidgetTestFixtures.info(false));
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            repository, platform);
        controller.reconcileProviders();
        assertNull(repository.pending());
        assertEquals(LauncherWidgetHostController.AddResult.STARTED,
            controller.beginAdd(WidgetTestFixtures.info(false), null));
    }

    @Test public void providerRefreshRetainsFrameworkRefreshedHostViewIdentity() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        repository.putRecord(new LauncherWidgetRecord(5, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, null, "draw"));
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.info.put(5, WidgetTestFixtures.info(false));
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            repository, platform);
        android.appwidget.AppWidgetHostView displayed = controller.createHostView(5);
        controller.onProviderChanged(5, platform.info.get(5));
        assertSame(displayed, controller.createHostView(5));
    }

    private static String read(String relative) throws Exception {
        Path path = Paths.get(relative); if (!Files.exists(path)) path = Paths.get("..").resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
