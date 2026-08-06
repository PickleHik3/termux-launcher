package com.termux.app.statusbar;

import android.app.Application;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class BackgroundProcessStackViewTest {

    private static final long GRACE = BackgroundProcessModel.SHOW_DELAY_MS;

    /**
     * The blink regression: a background command writing progress to its title re-binds many times a
     * second, and rebuilding the rows handed every one of those binds to the layout transition.
     */
    @Test
    public void rebindingTheSameEntriesReusesRowViews() {
        BackgroundProcessStackView view = new BackgroundProcessStackView(
            ApplicationProvider.getApplicationContext());
        BackgroundProcessModel model = new BackgroundProcessModel();

        model.update(Collections.singletonList(snapshot(10, 101, "pacman", "pacman: 12%")), 0);
        view.bind(model.visibleEntries(-1L, GRACE));
        assertEquals(1, view.getChildCount());
        View row = view.getChildAt(0);

        model.update(Collections.singletonList(snapshot(10, 101, "pacman", "pacman: 44%")), GRACE);
        view.bind(model.visibleEntries(-1L, GRACE));

        assertEquals(1, view.getChildCount());
        assertSame(row, view.getChildAt(0));
        assertEquals("pacman: 44%", titleOf(row).getText().toString());
    }

    /** A replaced foreground pid is a different command, so it must get a fresh row. */
    @Test
    public void aNewForegroundPidGetsItsOwnRow() {
        BackgroundProcessStackView view = new BackgroundProcessStackView(
            ApplicationProvider.getApplicationContext());
        BackgroundProcessModel model = new BackgroundProcessModel();

        model.update(Collections.singletonList(snapshot(10, 101, "make", "make")), 0);
        view.bind(model.visibleEntries(-1L, GRACE));
        View row = view.getChildAt(0);

        model.update(Collections.singletonList(snapshot(10, 102, "ld", "ld")), GRACE);
        view.bind(model.visibleEntries(-1L, GRACE * 2));

        assertEquals(1, view.getChildCount());
        assertNotSame(row, view.getChildAt(0));
        assertEquals("ld", titleOf(view.getChildAt(0)).getText().toString());
    }

    /** Dropping the top row must not leave the survivor sitting under an empty slot. */
    @Test
    public void removingTheFirstRowPromotesTheSurvivorAndDropsItsGap() {
        BackgroundProcessStackView view = new BackgroundProcessStackView(
            ApplicationProvider.getApplicationContext());
        BackgroundProcessModel model = new BackgroundProcessModel();

        model.update(Collections.singletonList(snapshot(10, 101, "first", "first")), 0);
        model.update(Arrays.asList(snapshot(10, 101, "first", "first"),
            snapshot(20, 201, "second", "second")), 10);
        view.bind(model.visibleEntries(-1L, GRACE + 10));
        assertEquals(2, view.getChildCount());
        View second = view.getChildAt(1);

        model.update(Collections.singletonList(snapshot(20, 201, "second", "second")), GRACE + 10);
        view.bind(model.visibleEntries(-1L, GRACE + 10));

        assertEquals(1, view.getChildCount());
        assertSame(second, view.getChildAt(0));
        assertEquals(0, ((BackgroundProcessStackView.LayoutParams)
            second.getLayoutParams()).topMargin);
    }

    @Test
    public void anEmptyBindHidesTheStack() {
        BackgroundProcessStackView view = new BackgroundProcessStackView(
            ApplicationProvider.getApplicationContext());
        BackgroundProcessModel model = new BackgroundProcessModel();

        model.update(Collections.singletonList(snapshot(10, 101, "make", "make")), 0);
        view.bind(model.visibleEntries(-1L, GRACE));
        assertEquals(View.VISIBLE, view.getVisibility());

        model.update(Collections.emptyList(), GRACE);
        view.bind(model.visibleEntries(-1L, GRACE));
        assertEquals(0, view.getChildCount());
        assertEquals(View.GONE, view.getVisibility());
    }

    /** Only three rows are drawn; the third collapses into the overflow count. */
    @Test
    public void overflowIsCappedAtThreeRows() {
        BackgroundProcessStackView view = new BackgroundProcessStackView(
            ApplicationProvider.getApplicationContext());
        BackgroundProcessModel model = new BackgroundProcessModel();

        List<BackgroundProcessModel.Snapshot> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) five.add(snapshot(10 + i, 100 + i, "job" + i, "job" + i));
        model.update(five, 0);
        view.bind(model.visibleEntries(-1L, GRACE));

        assertEquals(3, view.getChildCount());
    }

    /** The notice above owns the top slot; the stack rides under it and slides back up. */
    @Test
    public void theStackFollowsTheNoticeChipDownAndBackUp() {
        BackgroundProcessStackView view = new BackgroundProcessStackView(
            ApplicationProvider.getApplicationContext());
        BackgroundProcessModel model = new BackgroundProcessModel();
        model.update(Collections.singletonList(snapshot(10, 101, "make", "make")), 0);
        view.bind(model.visibleEntries(-1L, GRACE));
        assertEquals(0f, view.getTranslationY(), 0.01f);

        view.setNoticeOccupancyPx(40);
        shadowOf(Looper.getMainLooper()).idle();
        // Derived rather than restated: the offset is the notice's height plus the row gap, and the
        // gap's value is the view's business.
        float gap = view.getTranslationY() - 40f;
        assertTrue("expected a positive gap, got " + gap, gap > 0f);

        view.setNoticeOccupancyPx(60);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(60f + gap, view.getTranslationY(), 0.01f);

        view.setNoticeOccupancyPx(0);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0f, view.getTranslationY(), 0.01f);
    }

    private static TextView titleOf(View row) {
        return (TextView) ((android.view.ViewGroup) row).getChildAt(0);
    }

    private static BackgroundProcessModel.Snapshot snapshot(int shell, int foreground,
                                                            String process, String title) {
        return new BackgroundProcessModel.Snapshot(1L, shell, foreground, process, title, true);
    }
}
