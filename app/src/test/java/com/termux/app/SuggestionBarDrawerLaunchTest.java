package com.termux.app;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;

import com.termux.app.launcher.data.LauncherUsageStatsStore;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A drawer cell launches the same way a dock icon does or it is a second implementation of the
 * launch ladder waiting to drift.
 *
 * <p>The observable proof that {@code launchEntryFromDrawer} really goes through
 * {@code launchEntryFromTouch} rather than straight to {@code launchEntry} is the touch-launch
 * delay: nothing starts on the calling frame, the activity appears only after the press animation
 * window. Usage recording matters because the most-used page and the A-Z ranking are built from it —
 * a drawer that launched without recording would slowly make the dock wrong.
 *
 * <p>The clone/work-profile branch is checked by its refusal: an entry marked {@code clonedProfile}
 * with no resolvable user must go down the {@link com.termux.app.launcher.LauncherAppLauncher} path
 * and stop there, never falling through to the ordinary package-manager ladder under another user's
 * identity.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarDrawerLaunchTest {

    private static final String PACKAGE = "com.example.drawerapp";
    private static final String ACTIVITY = "com.example.drawerapp.Main";

    private Application application;
    private ActivityController<Activity> controller;
    private Activity activity;
    private SuggestionBarView row;

    @Before
    public void setUp() {
        application = RuntimeEnvironment.getApplication();
        // The row has to be attached to a window: the touch-launch delay goes through
        // View.postDelayed, which parks the runnable in a detached view's run queue forever.
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        row = new SuggestionBarView(activity, null);
        activity.setContentView(row, new ViewGroup.LayoutParams(720, 160));
        idle(0);
    }

    @After
    public void tearDown() {
        if (controller != null) controller.close();
    }

    @Test
    public void aDrawerLaunch_takesTheTouchLaunchPathAndRecordsTheLaunch() {
        View cell = cell();
        LauncherAppEntry entry = entry(false, -1);

        assertTrue(row.launchEntryFromDrawer(cell, entry));

        // launchEntryFromTouch defers the launch by the press-animation window; launchEntry does not.
        assertNull("the launch must be deferred like the dock's", nextStartedActivity());
        idle(200);

        Intent started = nextStartedActivity();
        assertNotNull("the deferred launch never ran", started);
        assertEquals(new ComponentName(PACKAGE, ACTIVITY), started.getComponent());
        assertEquals(1, launchCount(entry));
    }

    @Test
    public void aDrawerCell_neverBecomesTheDocksLaunchAnimationTarget() {
        View cell = cell();

        row.launchEntryFromDrawer(cell, entry(false, -1));
        idle(200);

        Map<String, ?> byComponent = ReflectionHelpers.getField(row, "launchTargetViews");
        Map<String, ?> byPackage = ReflectionHelpers.getField(row, "launchTargetViewsByPackage");
        assertTrue("a drawer cell must not claim the dock's launch target slot", byComponent.isEmpty());
        assertTrue("a drawer cell must not claim the dock's launch target slot", byPackage.isEmpty());
    }

    @Test
    public void aClonedProfileEntry_takesTheProfileBranch() {
        View cell = cell();
        // clonedProfile with userId < 0: the profile launcher refuses immediately, and the ordinary
        // ladder must never pick the launch up behind it.
        LauncherAppEntry cloned = entry(true, -1);

        row.launchEntryFromDrawer(cell, cloned);
        idle(200);

        assertNull("a clone entry must not fall through to the package-manager ladder",
            nextStartedActivity());
        assertEquals(0, launchCount(cloned));

        // The same component without the clone flag does launch, so the refusal above is the branch
        // and not a broken fixture.
        LauncherAppEntry ordinary = entry(false, -1);
        row.launchEntryFromDrawer(cell, ordinary);
        idle(200);
        assertNotNull(nextStartedActivity());
        assertEquals(1, launchCount(ordinary));
    }

    @Test
    public void aNullEntry_isRefusedRatherThanLaunched() {
        assertFalse(row.launchEntryFromDrawer(cell(), null));
        idle(200);
        assertNull(nextStartedActivity());
    }

    @Test
    public void withAnimationsOff_theLaunchIsImmediate() {
        Settings.Global.putFloat(application.getContentResolver(),
            Settings.Global.ANIMATOR_DURATION_SCALE, 0f);

        row.launchEntryFromDrawer(cell(), entry(false, -1));

        assertNotNull("with animations disabled there is nothing to wait for", nextStartedActivity());
    }

    // -------------------------------------------------------------------- helpers

    private View cell() {
        View cell = new View(activity);
        cell.setLayoutParams(new ViewGroup.LayoutParams(120, 120));
        cell.layout(0, 0, 120, 120);
        return cell;
    }

    private LauncherAppEntry entry(boolean clonedProfile, int userId) {
        AppRef ref = new AppRef(PACKAGE, ACTIVITY, userId, -1L, clonedProfile, "");
        return new LauncherAppEntry(ref, "Drawer App", new ColorDrawable(0xFF223344));
    }

    private int launchCount(LauncherAppEntry entry) {
        LauncherUsageStatsStore store = ReflectionHelpers.getField(row, "usageStatsStore");
        if (store == null) return 0;
        Map<String, ?> usage = ReflectionHelpers.getField(store, "usageByStableId");
        Object stat = usage.get(entry.appRef.stableId());
        if (stat == null) return 0;
        Integer count = ReflectionHelpers.getField(stat, "count");
        return count == null ? 0 : count;
    }

    private Intent nextStartedActivity() {
        return Shadows.shadowOf(application).getNextStartedActivity();
    }

    private void idle(long ms) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS);
    }
}
