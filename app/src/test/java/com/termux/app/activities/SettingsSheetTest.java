package com.termux.app.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;
import android.view.View;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

import java.time.Duration;

/**
 * Settings as a sheet over Home: it rests at its resting height, the header drags it and the
 * pages do not, reaching full height keeps it there, and Back at the root or a tap on Home above
 * closes it once it has slid away.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w411dp-h891dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SettingsSheetTest {

    private static SettingsActivity open() {
        SettingsActivity activity = Robolectric.buildActivity(SettingsActivity.class,
            new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class))
            .create().start().resume().visible().get();
        activity.getSupportFragmentManager().executePendingTransactions();
        shadowOf(Looper.getMainLooper()).idle();
        return activity;
    }

    private static void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
    }

    @Test
    public void itRestsAtItsRestingHeight() {
        SettingsActivity activity = open();
        settle();
        View root = activity.findViewById(R.id.settings_sheet_root);
        float density = activity.getResources().getDisplayMetrics().density;
        BottomSheetBehavior<View> behavior = activity.sheet().behavior();
        assertEquals(SettingsSheetPolicy.restingHeight(root.getHeight(), 0, density), behavior.getPeekHeight());
        assertEquals(BottomSheetBehavior.STATE_COLLAPSED, behavior.getState());
        assertFalse(activity.sheet().isRaised());
    }

    @Test
    public void theHeaderDragsTheSheetAndThePageDoesNot() {
        SettingsActivity activity = open();
        settle();
        View sheet = activity.findViewById(R.id.settings_sheet);
        View toolbar = activity.findViewById(com.termux.shared.R.id.toolbar);
        View page = activity.findViewById(R.id.settings);
        int[] at = new int[2];

        toolbar.getLocationInWindow(at);
        activity.sheet().onTouchDown(at[0] + toolbar.getWidth() / 2f, at[1] + toolbar.getHeight() / 2f);
        assertTrue("a drag on the toolbar moves the sheet", activity.sheet().behavior().isDraggable());

        page.getLocationInWindow(at);
        activity.sheet().onTouchDown(at[0] + page.getWidth() / 2f, at[1] + 10);
        assertFalse("a drag on the page scrolls the page", activity.sheet().behavior().isDraggable());

        sheet.getLocationInWindow(at);
        activity.sheet().onTouchDown(at[0] + 10, at[1] - 10);
        assertFalse("a touch above the sheet is Home's", activity.sheet().behavior().isDraggable());
    }

    @Test
    public void fullHeightIsKeptUntilClosed() {
        SettingsActivity activity = open();
        settle();
        View root = activity.findViewById(R.id.settings_sheet_root);
        BottomSheetBehavior<View> behavior = activity.sheet().behavior();

        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        settle();

        assertTrue(activity.sheet().isRaised());
        assertEquals("the resting stop is gone", SettingsSheetPolicy.fullHeight(root.getHeight(), 0),
            behavior.getPeekHeight());
    }

    @Test
    public void backAtTheRootClosesOnceTheSheetHasGone() {
        SettingsActivity activity = open();
        settle();

        activity.onBackPressed();
        assertFalse("the sheet slides away first", activity.isFinishing());
        settle();

        assertTrue(activity.isFinishing());
    }

    @Test
    public void aTapOnHomeAboveClosesIt() {
        SettingsActivity activity = open();
        settle();

        activity.findViewById(R.id.settings_sheet_scrim).performClick();
        settle();

        assertTrue(activity.isFinishing());
    }

    @Test
    public void openingAgainWhileItGoesBringsItBack() {
        SettingsActivity activity = open();
        settle();

        activity.onBackPressed();
        activity.onNewIntent(new Intent(RuntimeEnvironment.getApplication(), SettingsActivity.class));
        settle();

        assertFalse(activity.isFinishing());
        assertEquals(BottomSheetBehavior.STATE_COLLAPSED, activity.sheet().behavior().getState());
    }
}
