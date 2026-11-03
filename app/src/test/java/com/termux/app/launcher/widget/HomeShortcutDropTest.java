package com.termux.app.launcher.widget;

import static com.termux.app.launcher.widget.HomeDockMoveTest.cell;
import static com.termux.app.launcher.widget.HomeDockMoveTest.idle;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.AppRef;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * An app shortcut dragged out of the App menu lands on Home as a pinned shortcut, in the cell under
 * the finger, through Home's pane controller; let go anywhere else, nothing is put down.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class HomeShortcutDropTest {
    private static final AppRef MAPS = new AppRef("com.maps", "com.maps.Main");

    @Test public void aShortcutLetGoOnHomeIsPinnedInTheCellUnderTheFinger() {
        HomeDockMoveTest.Fixture fixture =
            new HomeDockMoveTest.Fixture(new HomeDockMoveTest.FakeDock(false));
        boolean[] placed = new boolean[1];
        IconCarry carry = carry(fixture, () -> placed[0] = true);
        float[] at = fixture.screenCentreOf(cell(3, 3));

        assertTrue(carry.begin(new ColorDrawable(0xFF0000FF), 48, -24f, -24f, at[0], at[1]));
        carry.move(at[0] + 3f, at[1] - 3f);
        carry.end(at[0] + 3f, at[1] - 3f, false);
        idle();

        assertTrue(placed[0]);
        LauncherWidgetRecord shortcut = find(fixture);
        assertNotNull("the shortcut is on Home", shortcut);
        assertTrue(shortcut.isShortcut());
        assertEquals("navigate", shortcut.shortcutId());
        assertEquals(MAPS.packageName, shortcut.provider.getPackageName());
        assertEquals("Navigate home", shortcut.appLabel());
        assertEquals(2, shortcut.page);
        assertEquals(cell(3, 3), shortcut.cell);
    }

    @Test public void aShortcutLetGoElsewhereIsNotPinned() {
        HomeDockMoveTest.Fixture fixture =
            new HomeDockMoveTest.Fixture(new HomeDockMoveTest.FakeDock(true));
        IconCarry carry = carry(fixture, () -> { });
        float[] at = fixture.screenCentreOf(cell(3, 3));

        carry.begin(new ColorDrawable(0xFF0000FF), 48, -24f, -24f, at[0], at[1]);
        carry.end(at[0], at[1], true);
        idle();

        assertNull(find(fixture));
        assertTrue("Home can take the next one", fixture.controller.canTakeAppDrop());
    }

    @NonNull
    private static IconCarry carry(@NonNull HomeDockMoveTest.Fixture fixture,
                                   @NonNull Runnable onPlaced) {
        HomeAppDrop drop = new HomeAppDrop(MAPS, "Navigate home", new ColorDrawable(0xFF0000FF),
            48).asShortcut(new HomeAppDrop.Shortcut(MAPS.packageName, "navigate", 0L));
        return new IconCarry(fixture.pane, LiftDropPolicy.Item.SHORTCUT, drop,
            LiftDropPolicy.targetsFor(LiftDropPolicy.Item.SHORTCUT, false),
            new IconCarry.Host() {
                @Override @Nullable public HomeAppDrop.Target home() { return fixture.homeTarget; }
                @Override public boolean hoverDock(float rawX, float rawY) {
                    throw new AssertionError("a shortcut never goes to the dock");
                }
                @Override public void onPlacedOnHome() { onPlaced.run(); }
                @Override public boolean reducedMotion() { return true; }
            });
    }

    @Nullable
    private static LauncherWidgetRecord find(@NonNull HomeDockMoveTest.Fixture fixture) {
        for (LauncherWidgetRecord record : fixture.repository.records()) {
            if (record.isShortcut()) return record;
        }
        return null;
    }
}
