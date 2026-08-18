package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

/**
 * Two A-Z surfaces now exist and they must never be confused.
 *
 * <p>The dock's own A-Z row writes {@code SuggestionBarView.activeAzLetter}, and B-1's drawer-pull
 * eligibility vetoes a pull-down while that field is set — correctly, because a finger part way
 * through a dock scrub is not asking for the drawer. The drawer's column is a different surface with
 * a different job: it scrolls the drawer's own grid. If it ever wrote that field, or reached any of
 * the dock's A-Z entry points ({@code previewAzLetter}, {@code refreshActiveAzCandidates},
 * {@code getAppsForLetter}, {@code rankForAz}), then scrubbing inside the drawer would veto the
 * gesture that opens it — and the drawer would refuse to open after every scrub, for the rest of
 * the session, with nothing on screen to explain why.
 *
 * <p>The column borrows exactly two things from the dock and neither of them is state: the launcher
 * text colour and the row-haptics preference.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.LEGACY)
public class SuggestionBarDrawerAzIsolationTest {

    private static final int WIDTH = 720;
    private static final int HEIGHT = 1280;

    private Context context;
    private SuggestionBarView dock;
    private AppDrawerContentView content;
    private AppDrawerRopeColumnView column;
    private AppDrawerSearchController search;

    @Before
    public void setUp() {
        Robolectric.getForegroundThreadScheduler().pause();
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        dock = new SuggestionBarView(context, null);

        search = new AppDrawerSearchController();
        search.setHost(new AppDrawerSearchController.Host() {
            @Override
            public boolean isSearchActive() {
                return true;
            }

            @Override
            public void onSearchCommitRequested() {}

            @Override
            public void onSearchDismissRequested() {}
        });

        // The cells render labels only, as the rest of the drawer harness does; the dock is handed to
        // the column, which is the only drawer surface that has any business holding one for A-Z.
        content = new AppDrawerContentView(context);
        column = content.getRopeColumn();
        column.setDock(dock);
        content.setInteractive(true);
        content.setMetrics(AppDrawerGridMetrics.resolve(WIDTH - content.getColumnWidthPx(),
            context.getResources().getDisplayMetrics().density, 30f));
        content.bind(null, search);
        search.setCatalogue(alphabet());
        layout();
    }

    @Test
    public void afterAFullInDrawerScrubTheDockStillHasNoActiveAzLetter() {
        assertNull(azLetter());
        List<?> candidatesBefore = ReflectionHelpers.getField(dock, "activeAzCandidates");

        // A to # and back up again, the longest scrub the column can be given.
        press(0);
        for (int i = 1; i < column.letterCount(); i++) {
            move(i);
        }
        for (int i = column.letterCount() - 2; i >= 0; i--) {
            move(i);
        }
        release(0);

        assertNull("the drawer column wrote the dock's A-Z state", azLetter());
        assertFalse(dock.isAzPreviewActive());
        // The candidate list the dock's own scrub builds is the other half of that state: it is
        // whatever it was, because nothing in the drawer asked the provider for a letter's apps.
        assertEquals(candidatesBefore, ReflectionHelpers.getField(dock, "activeAzCandidates"));
    }

    @Test
    public void soTheNextDockPullDownIsStillEligible() {
        assertTrue(eligibility().azInactive);
        int pagesBefore = dock.getAzVisiblePageCount();

        press(4);
        move(9);
        release(9);

        // The veto B-1 added is read off activeAzLetter. A drawer scrub that tripped it would make
        // the drawer refuse to open for the rest of the session.
        AppDrawerGestureArbiter.Eligibility after = eligibility();
        assertTrue("an in-drawer scrub vetoed the gesture that opens the drawer", after.azInactive);
        assertTrue(after.searchEmpty);
        // And the dock's own idea of what it is showing is untouched.
        assertEquals(pagesBefore, dock.getAzVisiblePageCount());
        assertFalse(dock.hasAzOverflowPages());
    }

    // ------------------------------------------------------------------ plumbing

    private Character azLetter() {
        return ReflectionHelpers.getField(dock, "activeAzLetter");
    }

    private AppDrawerGestureArbiter.Eligibility eligibility() {
        return ReflectionHelpers.callInstanceMethod(dock, "captureDrawerEligibility");
    }

    private float columnX() {
        return column.getLeft() + (column.getWidth() * 0.5f);
    }

    private float columnY(int index) {
        AppDrawerRopeMetrics metrics = column.metrics();
        return column.getTop() + (metrics == null ? 0f : metrics.centerYForIndex(index));
    }

    private void press(int index) {
        dispatch(MotionEvent.ACTION_DOWN, index);
        assertTrue(content.isScrubbing());
    }

    private void move(int index) {
        dispatch(MotionEvent.ACTION_MOVE, index);
    }

    private void release(int index) {
        dispatch(MotionEvent.ACTION_UP, index);
    }

    private void dispatch(int action, int index) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, action, columnX(), columnY(index), 0);
        try {
            content.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private void layout() {
        content.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, WIDTH, HEIGHT);
    }

    private static List<LauncherAppEntry> alphabet() {
        List<LauncherAppEntry> entries = new ArrayList<>();
        for (char letter = 'A'; letter <= 'Z'; letter++) {
            for (int i = 0; i < 3; i++) {
                String label = letter + "pp " + i;
                entries.add(new LauncherAppEntry(
                    new AppRef("com.example." + label.replace(' ', '.'), ".Main"), label, null));
            }
        }
        return entries;
    }
}
