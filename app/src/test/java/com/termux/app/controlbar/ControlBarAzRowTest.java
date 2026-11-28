package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.az.AzBarHostGeometry;
import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingWindow;
import com.termux.app.tiling.WorkspaceLayout;
import com.termux.app.tiling.desktop.TilingDesktop;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;

/**
 * Home's A–Z under Terl (Terl decisions 2026-11-28): at the bottom the letters are a row of the
 * control bar's card, one glass with it, on Home only and never on a rail; on a side they are a
 * compact column grouped toward the bottom, ending above the bar.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ControlBarAzRowTest {

    private Activity activity;
    private Place front = Place.home(0);
    private ControlBar bar;
    private View row;
    private int relayouts;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        bar = new ControlBar(activity, new FakeHost(), new FakeDesktop());
        bar.setAzRowListener(() -> relayouts++);
        row = new View(activity);
        bar.setForm(ControlBarForm.TWO_ROWS);
        bar.onFrontChanged();
    }

    @Test public void atTheBottomOnHomeTheLettersAreARowOfTheCard() {
        bar.setAzRow(row);
        assertFalse("not asked until the letters stand at the bottom", bar.azRowAsked());
        assertEquals(View.GONE, row.getVisibility());

        bar.setAzRowWanted(true);

        assertTrue(bar.azRowAsked());
        assertTrue(bar.carriesAzRow());
        assertEquals(View.VISIBLE, row.getVisibility());
        float density = activity.getResources().getDisplayMetrics().density;
        assertEquals("the card keeps the letters' band at its bottom",
            Math.round(ControlBarPlacement.AZ_ROW_DP * density), ((ControlBarView) bar.view()).azBandPx());
        assertEquals("the layout hears the bar grow", 1, relayouts);
    }

    @Test public void onAWorkspaceTheBarGivesTheRowBack() {
        bar.setAzRow(row);
        bar.setAzRowWanted(true);

        front = Place.workspace(1);
        bar.onFrontChanged();

        assertFalse(bar.azRowAsked());
        assertEquals(View.GONE, row.getVisibility());
        assertEquals(0, ((ControlBarView) bar.view()).azBandPx());
        assertEquals("grew, then gave it back", 2, relayouts);
    }

    @Test public void aRailNeverCarriesTheRow() {
        bar.setAzRow(row);
        bar.setAzRowWanted(true);

        bar.setForm(ControlBarForm.RAIL);

        assertFalse(bar.carriesAzRow());
        assertEquals(View.GONE, row.getVisibility());
        assertEquals(0, ((ControlBarView) bar.view()).azBandPx());
        assertEquals(ControlBarPlacement.thicknessDp(ControlBarForm.RAIL),
            ControlBarPlacement.thicknessDp(ControlBarForm.RAIL, true), 0f);
    }

    @Test public void theRowRidesWithTheBar() {
        bar.setAzRow(row);
        bar.setAzRowWanted(true);

        bar.ride(-120f);

        assertEquals(bar.view().getTranslationY(), row.getTranslationY(), 0f);
        assertEquals(-120f, row.getTranslationY(), 0f);
    }

    @Test public void theBarGrowsByTheLettersRow() {
        assertEquals(ControlBarPlacement.HEIGHT_DP + ControlBarPlacement.AZ_ROW_DP,
            ControlBarPlacement.thicknessDp(ControlBarForm.TWO_ROWS, true), 0f);
        assertEquals(ControlBarPlacement.ONE_ROW_DP + ControlBarPlacement.AZ_ROW_DP,
            ControlBarPlacement.thicknessDp(ControlBarForm.ONE_ROW, true), 0f);
        assertEquals(ControlBarPlacement.HEIGHT_DP,
            ControlBarPlacement.thicknessDp(ControlBarForm.TWO_ROWS, false), 0f);
    }

    /** On a side: a compact column at the bottom of its band, ending above the bar. */
    @Test public void aSideColumnIsGroupedTowardTheBottom() {
        float density = 2f;
        int column = AzBarHostGeometry.groupedColumnLengthPx(2000, 28, density, 8);
        assertEquals(Math.round(28 * AzBarHostGeometry.GROUPED_SLOT_DP * density) + 16, column);
        assertTrue("shorter than the band", column < 2000);
        assertEquals("never longer than the band", 300,
            AzBarHostGeometry.groupedColumnLengthPx(300, 28, density, 8));
        // The band runs down behind the bar: the foot clears the bar's top by the gap.
        assertEquals(240 + 16, AzBarHostGeometry.groupedColumnFootPx(2000, 1760, 16));
        assertEquals("a band ending well above the bar needs no foot",
            0, AzBarHostGeometry.groupedColumnFootPx(1500, 1760, 16));
    }

    private final class FakeDesktop implements ControlBar.Desktop {
        @Override public void setFrontListener(@Nullable TilingDesktop.FrontListener listener) { }
        @NonNull @Override public Place front() { return front; }
        @NonNull @Override public List<TilingWindow> frontWindows() { return Collections.emptyList(); }
        @Nullable @Override public TilingWindow frontMainWindow() { return null; }
        @Override public long frontFocusedWindowId() { return -1; }
        @Override public boolean focusWindow(long windowId) { return true; }
        @Override public void split() { }
        @Override public void newTerminal() { }
        @Override public boolean toggleFrontKeyboard() { return false; }
        @Override public boolean toggleFrontMouseMode() { return false; }
        @Nullable @Override public WorkspaceLayout frontLayout() { return front.isHome() ? null : WorkspaceLayout.SCROLLING; }
        @Override public boolean setFrontLayout(@NonNull WorkspaceLayout layout) { return true; }
        @Override public boolean frontFullScreen() { return false; }
        @Override public boolean setFrontFullScreen(boolean value) { return false; }
    }

    private static final class FakeHost implements ControlBar.Host {
        @Override public int homePageCount() { return 1; }
        @Override public int homePageInFront() { return 0; }
        @Override public void showHomePage(int page) { }
        @NonNull @Override public List<ControlBarView.PinnedApp> pinnedApps() { return Collections.emptyList(); }
        @Override public void openPinnedApp(int index) { }
        @Override public void holdPinnedApp(int index, @NonNull View anchor) { }
        @Override public void toggleKeyboard() { }
        @Override public void openSearch() { }
        @Override public void openDrawer() { }
        @Override public void openOverview() { }
        @Override public boolean drawerToolShown() { return false; }
        @Nullable @Override public Drawable glass(float radiusPx, @NonNull View anchor) { return null; }
        @Override public float cornerRadiusPx() { return 0f; }
        @Override public int ink() { return 0xFFFFFFFF; }
        @Override public int accent() { return 0xFF00FF00; }
    }
}
