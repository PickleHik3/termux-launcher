package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class LauncherWidgetHostLifecycleTest {
    @Test public void startStopAreIdempotentAndHostIdStable() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            WidgetTestFixtures.repository(), platform);
        controller.onStart(); controller.onStart();
        controller.onStop(); controller.onStop();
        assertEquals(1, platform.starts);
        assertEquals(1, platform.stops);
        assertEquals(0x544C, LauncherAppWidgetHost.APPWIDGET_HOST_ID);
    }

    @Test public void recreationDoesNotAllocateForActiveRecords() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        repository.putRecord(new LauncherWidgetRecord(9, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, null, null));
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.info.put(9, WidgetTestFixtures.info(false));
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            repository, platform);
        controller.onStart();
        assertEquals(0, platform.allocations);
    }

    @Test public void partialListeningInitializationIsSafeAndRetried() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.failStart = true;
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            WidgetTestFixtures.repository(), platform);
        controller.onStart();
        controller.onStop();
        platform.failStart = false;
        controller.onStart();
        controller.onStop();
        assertEquals(2, platform.starts);
        assertEquals(1, platform.stops);
    }

    @Test public void unsupportedDevicesNeverListenOrAllocate() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.feature = false;
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            WidgetTestFixtures.repository(), platform);
        controller.onStart();
        assertEquals(LauncherWidgetHostController.Capability.UNSUPPORTED,
            controller.capability());
        assertEquals(LauncherWidgetHostController.AddResult.UNSUPPORTED,
            controller.beginAdd(WidgetTestFixtures.info(false), null));
        assertEquals(0, platform.starts);
        assertEquals(0, platform.allocations);
    }

    @Test public void unboundAllocatedOrBindConsentReservationIsDeletedAndDoesNotLockAdds() {
        for (WidgetAddTransaction.Stage stage : new WidgetAddTransaction.Stage[] {
            WidgetAddTransaction.Stage.ALLOCATED,
            WidgetAddTransaction.Stage.WAITING_FOR_BIND_CONSENT
        }) {
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            LauncherWidgetRepository repository = WidgetTestFixtures.repository();
            WidgetAddTransaction pending = new WidgetAddTransaction("reserved", 19,
                WidgetTestFixtures.PROVIDER, 0, stage, new Bundle(), 100);
            repository.setPending(pending);
            WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
            LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
                repository, platform);
            controller.onStart();
            assertEquals(stage.name(), null, repository.pending());
            assertEquals(stage.name(), java.util.Collections.singletonList(19), platform.deleted);
            assertEquals(stage.name(), LauncherWidgetHostController.AddResult.STARTED,
                controller.beginAdd(WidgetTestFixtures.info(false), null));
        }
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.O, application = Application.class)
    public void api26StopStartRecreatesFrameworkRegisteredHostView() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        repository.putRecord(new LauncherWidgetRecord(9, WidgetTestFixtures.PROVIDER, 0,
            LauncherWidgetRecord.State.ACTIVE, null, null));
        WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        platform.info.put(9, WidgetTestFixtures.info(false));
        LauncherWidgetHostController controller = new LauncherWidgetHostController(activity,
            repository, platform);
        controller.onStart();
        android.appwidget.AppWidgetHostView first = controller.createHostView(9);
        controller.onStop();
        controller.onStart();
        assertNotSame(first, controller.createHostView(9));
    }
}
