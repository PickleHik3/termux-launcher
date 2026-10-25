package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.dock.DockDrop;
import com.termux.app.launcher.model.AppRef;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An app icon moves between Home and the dock, driven through Home's pane controller: a Home icon
 * let go over the dock leaves Home (a move, with no Undo), one the dock refuses goes back to its
 * cell, and a dock icon carried over Home lands in the cell under the finger.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class HomeDockMoveTest {
    private static final AppRef CHROME = new AppRef("com.android.chrome",
        "com.google.android.apps.chrome.Main");
    private static final AppRef CLOCK = new AppRef("com.clock", "com.clock.Main");
    private static final AppRef MAPS = new AppRef("com.maps", "com.maps.Main");

    private static WidgetCellRect cell(int column, int row) {
        return new WidgetCellRect(column, row, column + 1, row + 1);
    }

    @Test public void aHomeIconLetGoOverTheDockMovesThere() {
        FakeDock dock = new FakeDock(true);
        Fixture fixture = new Fixture(dock);
        float[] at = fixture.centreOf(fixture.chromeId);
        fixture.lift(fixture.chromeId, at);

        // Down the screen, past the targets' band, onto the dock.
        fixture.move(at[0], 1500f);
        assertTrue("the dock opened a gap", dock.hovering);
        fixture.release();
        idle();

        assertEquals(Collections.singletonList(CHROME.stableId()), dock.dropped);
        assertNull("it left Home", fixture.repository.get(fixture.chromeId));
        assertFalse("the gap closed", dock.hovering);
        assertNull("a move takes no Undo", fixture.undoNotice());
    }

    @Test public void anIconTheDockRefusesGoesBackToItsCell() {
        FakeDock dock = new FakeDock(false);
        Fixture fixture = new Fixture(dock);
        float[] at = fixture.centreOf(fixture.chromeId);
        fixture.lift(fixture.chromeId, at);

        fixture.move(at[0], 1500f);
        fixture.release();
        idle();

        assertEquals(1, dock.dropped.size());
        LauncherWidgetRecord chrome = fixture.repository.get(fixture.chromeId);
        assertNotNull("it stayed on Home", chrome);
        assertEquals(2, chrome.page);
        assertEquals(cell(0, 0), chrome.cell);
        View view = fixture.pane.grid().cellForId(fixture.chromeId);
        assertNotNull(view);
        assertEquals(View.VISIBLE, view.getVisibility());
    }

    @Test public void anIconOffTheDockNeverReachesIt() {
        FakeDock dock = new FakeDock(true);
        dock.under = false;
        Fixture fixture = new Fixture(dock);
        float[] at = fixture.centreOf(fixture.chromeId);
        fixture.lift(fixture.chromeId, at);

        fixture.move(at[0] + 5f, 1500f);
        fixture.release();
        idle();

        assertTrue("the dock was never let go over", dock.dropped.isEmpty());
        assertNotNull(fixture.repository.get(fixture.chromeId));
    }

    @Test public void aDockIconCarriedOverHomeLandsInTheCellUnderTheFinger() {
        Fixture fixture = new Fixture(new FakeDock(false));
        boolean[] placed = new boolean[1];
        IconCarry carry = new IconCarry(fixture.pane, LiftDropPolicy.Item.DOCK_APP,
            new HomeAppDrop(MAPS, "Maps", new ColorDrawable(0xFF00FF00), 48),
            Collections.emptyList(), new IconCarry.Host() {
                @Override @Nullable public HomeAppDrop.Target home() { return fixture.homeTarget; }
                @Override public void onPlacedOnHome() { placed[0] = true; }
                @Override public boolean reducedMotion() { return true; }
            });
        float[] at = fixture.screenCentreOf(cell(3, 3));

        assertTrue("it lifted", carry.begin(new ColorDrawable(0xFF00FF00), 48, -24f, -24f,
            at[0], at[1]));
        carry.move(at[0] + 2f, at[1] + 2f);
        assertNotNull("the page outlines the cell it would take",
            fixture.pane.widgetDragLayer().ghostBounds());
        carry.end(at[0] + 2f, at[1] + 2f, false);
        idle();

        assertTrue(placed[0]);
        LauncherWidgetRecord maps = null;
        for (LauncherWidgetRecord record : fixture.repository.records()) {
            AppRef app = record.appRef();
            if (app != null && MAPS.packageName.equals(app.packageName)) maps = record;
        }
        assertNotNull("Maps is on Home", maps);
        assertEquals(2, maps.page);
        assertEquals(cell(3, 3), maps.cell);
        assertFalse(fixture.pane.widgetDragLayer().isLifted());
    }

    @Test public void aDockIconLetGoOffTheGridPutsNothingDown() {
        Fixture fixture = new Fixture(new FakeDock(false));
        boolean[] returned = new boolean[1];
        IconCarry carry = new IconCarry(fixture.pane, LiftDropPolicy.Item.DOCK_APP,
            new HomeAppDrop(MAPS, "Maps", new ColorDrawable(0xFF00FF00), 48),
            Collections.emptyList(), new IconCarry.Host() {
                @Override @Nullable public HomeAppDrop.Target home() { return fixture.homeTarget; }
                @Override public void onReturned() { returned[0] = true; }
                @Override public boolean reducedMotion() { return true; }
            });
        float[] at = fixture.screenCentreOf(cell(3, 3));
        int records = fixture.repository.records().size();

        carry.begin(new ColorDrawable(0xFF00FF00), 48, -24f, -24f, at[0], at[1]);
        carry.end(at[0], at[1], true);
        idle();

        assertTrue(returned[0]);
        assertEquals(records, fixture.repository.records().size());
        assertTrue("Home can take the next one", fixture.controller.canTakeAppDrop());
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** A dock that is under the finger whenever asked, and takes or refuses what is let go. */
    private static final class FakeDock implements DockDrop {
        final boolean takes;
        boolean under = true;
        boolean hovering;
        final List<String> dropped = new ArrayList<>();

        FakeDock(boolean takes) {
            this.takes = takes;
        }

        @Override public boolean hoverApp(float rawX, float rawY) {
            hovering = under;
            return under;
        }

        @Override public void leave() {
            hovering = false;
        }

        @Override public boolean dropApp(@NonNull AppRef app, float rawX, float rawY) {
            dropped.add(app.stableId());
            return takes;
        }
    }

    /** Home of three pages, the last one shown: Chrome at (0, 0) beside a Clock icon. */
    private static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final LauncherWidgetRepository repository = WidgetTestFixtures.repository();
        final LauncherWidgetHostController widgets;
        final WidgetPaneView pane;
        final WidgetPaneController controller;
        final int chromeId;
        final HomeAppDrop.Target homeTarget;

        Fixture(@NonNull DockDrop dock) {
            activity.setTheme(com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            assertNotNull(repository.addAppAt(CLOCK, 0, cell(1, 0), label("Clock")));
            repository.setPages(3, Collections.emptyList());
            LauncherWidgetRecord chrome = repository.addAppAt(CHROME, 2, cell(0, 0),
                label("Chrome"));
            assertNotNull(chrome);
            // An empty page would be trimmed away as the drag ends: each page holds something.
            assertNotNull(repository.addAppAt(CLOCK, 1, cell(1, 1), label("Clock")));
            assertNotNull(repository.addAppAt(CLOCK, 2, cell(2, 1), label("Clock")));
            chromeId = chrome.appWidgetId;
            widgets = new LauncherWidgetHostController(activity, repository,
                new WidgetTestFixtures.Platform(activity));
            pane = new WidgetPaneView(activity);
            activity.setContentView(pane);
            controller = new WidgetPaneController(pane, widgets, new WidgetPaneController.Host() {
                @Override public boolean reducedMotion() { return true; }
                @Override public boolean isWidgetSurfaceShowing() { return true; }
                @Override public void captureWidgetSurfaceOrigin() { }
                @Override public void restoreWidgetSurfaceOrigin() { }
                @Override @Nullable public DockDrop dockDrop() { return dock; }
            });
            homeTarget = new HomeAppDrop.Target() {
                @Override public boolean canTakeAppDrop() { return controller.canTakeAppDrop(); }
                @Override public void onAppDropStarted(@NonNull HomeAppDrop drop, float rawX,
                                                       float rawY) {
                    controller.onAppDropStarted(drop, rawX, rawY);
                }
            };
            ReflectionHelpers.setField(controller, "currentPage", 2);
            controller.onWidgetRepositoryChanged(LauncherWidgetHostController.AddResult.IGNORED);
            View root = activity.getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2200, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1080, 2200);
            idle();
            assertNotNull(pane.grid().cellForId(chromeId));
        }

        float[] centreOf(int id) {
            View view = pane.grid().cellForId(id);
            assertNotNull(view);
            int[] at = new int[2];
            view.getLocationOnScreen(at);
            return new float[] {at[0] + view.getWidth() / 2f, at[1] + view.getHeight() / 2f};
        }

        float[] screenCentreOf(@NonNull WidgetCellRect cell) {
            Rect bounds = pane.grid().metrics().boundsFor(cell);
            int[] at = new int[2];
            pane.getLocationOnScreen(at);
            return new float[] {at[0] + pane.grid().getLeft() + bounds.exactCenterX(),
                at[1] + pane.grid().getTop() + bounds.exactCenterY()};
        }

        void lift(int id, float[] at) {
            ReflectionHelpers.callInstanceMethod(controller, "liftIcon",
                ClassParameter.from(int.class, id),
                ClassParameter.from(float.class, at[0]), ClassParameter.from(float.class, at[1]),
                ClassParameter.from(float.class, at[0]), ClassParameter.from(float.class, at[1]));
            assertNotNull("the icon lifted", ReflectionHelpers.getField(controller, "edit"));
        }

        void move(float rawX, float rawY) {
            ReflectionHelpers.callInstanceMethod(controller, "moveDrag",
                ClassParameter.from(float.class, rawX), ClassParameter.from(float.class, rawY));
        }

        void release() {
            ReflectionHelpers.callInstanceMethod(controller, "endMoveDrag",
                ClassParameter.from(boolean.class, false));
        }

        /** The Undo notice, if one is up. */
        @Nullable View undoNotice() {
            View notice = com.termux.app.notice.AppNotice.hostFor(activity);
            return notice != null && notice.getVisibility() == View.VISIBLE ? notice : null;
        }

        private static Bundle label(String value) {
            Bundle options = new Bundle();
            options.putString(LauncherWidgetRecord.APP_LABEL, value);
            return options;
        }
    }
}
