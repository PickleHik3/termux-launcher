package com.termux.app.launcher.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Home's sheet opens and closes by its slide, and a drag-dismissed sheet opens again. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w411dp-h918dp-420dpi", application = Application.class)
public class HomeActionsSheetSlideTest {
    private final List<String> steps = new ArrayList<>();
    private HomeActionsSheet sheet;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        FrameLayout content = new FrameLayout(themed);
        activity.setContentView(content, new ViewGroup.LayoutParams(1080, 2412));
        sheet = new HomeActionsSheet(themed, new HomeActionsSheet.Listener() {
            @Override public void onHomeAction(@NonNull HomeActionsSheetPolicy.Item item) {
                steps.add("door " + item);
            }
            @Override public void onSheetClosed() { steps.add("closed"); }
            @Override public void onSheetGone() { steps.add("gone"); }
        });
        content.addView(sheet, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void open() {
        sheet.open(locked -> HomeActionsSheetPolicy.itemsFor(false, true, true, 2, locked), false);
    }

    @Test public void aRowsCloseRunsItsActionAfterTheSheetIsHidden() {
        open();
        idle(1000);
        assertTrue(sheet.isOpen());
        View c = sheet.findViewById(R.id.home_actions_card);
        assertEquals("the card stands on the bottom edge", sheet.getHeight(), c.getBottom());
        View row = sheet.rowFor(HomeActionsSheetPolicy.Item.WALLPAPER);
        assertNotNull(row);
        row.performClick();
        assertTrue("still sliding away: " + steps, steps.isEmpty());
        idle(1000);
        assertEquals(View.GONE, sheet.getVisibility());
        assertEquals(0f, sheet.cardTranslationYForTest(), 0f);
        assertEquals(Arrays.asList("closed", "door WALLPAPER", "gone"), steps);
    }

    @Test public void aReopenAfterADragDismissEndsExpandedAtRest() {
        open();
        idle(1000);
        sheet.behaviorForTest().setState(BottomSheetBehavior.STATE_HIDDEN);
        idle(2000);
        assertEquals(View.GONE, sheet.getVisibility());
        assertEquals(BottomSheetBehavior.STATE_HIDDEN, sheet.behaviorForTest().getState());
        open();
        idle(1000);
        assertTrue(sheet.isOpen());
        assertEquals(View.VISIBLE, sheet.getVisibility());
        assertEquals(BottomSheetBehavior.STATE_EXPANDED, sheet.behaviorForTest().getState());
        assertEquals(0f, sheet.cardTranslationYForTest(), 0f);
    }

    private static void idle(long ms) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }
}
