package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetAddPlacementRoutingTest {
    @Test public void directConsentAndConfigureAllKeepExactReservation() {
        WidgetCellRect cell = new WidgetCellRect(1, 2, 3, 4);
        // Direct/no-config.
        Fixture direct = new Fixture(false); direct.platform.directBind = true;
        direct.platform.directlyBoundInfo = direct.info;
        assertEquals(LauncherWidgetHostController.AddResult.READY,
            direct.controller.beginAdd(direct.info, cell, 0, new Bundle(), "origin"));
        assertEquals(cell, direct.repository.records().get(0).cell);

        // Consent/no-config.
        Fixture consent = new Fixture(false);
        assertEquals(LauncherWidgetHostController.AddResult.STARTED,
            consent.controller.beginAdd(consent.info, cell, 0, new Bundle(), "origin"));
        int id = consent.repository.pending().appWidgetId; consent.platform.info.put(id, consent.info);
        assertTrue(consent.controller.handleActivityResult(4714, Activity.RESULT_OK, result(id)));
        assertEquals(cell, consent.repository.get(id).cell);

        // Consent then mandatory configure.
        Fixture configure = new Fixture(true);
        configure.controller.beginAdd(configure.info, cell, 0, new Bundle(), "origin");
        id = configure.repository.pending().appWidgetId; configure.platform.info.put(id, configure.info);
        configure.controller.handleActivityResult(4714, Activity.RESULT_OK, result(id));
        assertEquals(1, configure.platform.configureLaunches);
        configure.controller.handleActivityResult(4715, Activity.RESULT_OK, result(id));
        assertEquals(cell, configure.repository.get(id).cell);
    }

    @Test public void cancelDeletesOneIdReleasesCellAndForeignResultDoesNothing() {
        Fixture fixture = new Fixture(false); WidgetCellRect cell = new WidgetCellRect(0, 0, 2, 2);
        fixture.controller.beginAdd(fixture.info, cell, 0, null, "origin");
        int id = fixture.repository.pending().appWidgetId;
        assertTrue(fixture.controller.handleActivityResult(4714, Activity.RESULT_CANCELED, result(id)));
        assertEquals(java.util.Collections.singletonList(id), fixture.platform.deleted);
        assertNull(fixture.repository.pending()); assertTrue(fixture.repository.records().isEmpty());
        assertTrue(fixture.controller.handleActivityResult(4714, Activity.RESULT_OK, result(999)));
        assertEquals(1, fixture.platform.deleted.size());
    }

    private static Intent result(int id) {
        return new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
    }
    private static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final WidgetTestFixtures.Platform platform = new WidgetTestFixtures.Platform(activity);
        final LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        final LauncherWidgetHostController controller = new LauncherWidgetHostController(activity, repository, platform);
        final AppWidgetProviderInfo info;
        Fixture(boolean configure) { info = WidgetTestFixtures.info(configure); }
    }
}
