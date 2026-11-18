package com.termux.app;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.termux.R;
import com.termux.app.page.PageKind;
import com.termux.app.place.PageRoom;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.app.terminal.inappkeyboard.TermuxInAppKeyboard;
import com.termux.app.wall.PaneWallController;
import com.termux.app.wall.WallRow;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.view.TerminalView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

/**
 * The room written for a page that is not in front is the rest it will arrive with (#262): the
 * Terminal left with its keyboard toggled since its room was last written holds, once the wall
 * rests on Home, the room of its arriving rest, so the next crossing resizes nothing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, instrumentedPackages = {"com.termux.x11"})
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityPageRoomMemoryTest {

    private TermuxActivity mActivity;
    private TermuxInAppKeyboard mKeyboard;
    private PaneWallController mWall;

    @After
    public void tearDown() {
        if (mKeyboard != null) mKeyboard.onDestroy();
        if (mActivity != null) mActivity.finish();
    }

    @Test
    public void terminalLeftWithItsKeyboardDownHoldsTheDownRoomOnHome() {
        leaveTerminalToggled(true);
    }

    @Test
    public void terminalLeftWithItsKeyboardUpHoldsTheUpRoomOnHome() {
        leaveTerminalToggled(false);
    }

    /** Starts the Terminal with its keyboard at {@code startUp}, toggles it, leaves for Home. */
    private void leaveTerminalToggled(boolean startUp) {
        start();
        if (!startUp) {
            mKeyboard.hide(TermuxInAppKeyboard.HideReason.USER_EVENT);
            settle();
        }
        // The user toggles the keyboard while on the Terminal.
        if (startUp) mKeyboard.hide(TermuxInAppKeyboard.HideReason.USER_EVENT);
        else mKeyboard.show(TermuxInAppKeyboard.ShowReason.TERMINAL_TAP);
        settle();
        assertEquals(!startUp, mKeyboard.isVisible());

        mWall.goTo(PageKind.HOME, false);
        settle();

        int arriving =PageRoom.wallInsetPx(restOfTerminalPx(), bottomKeptPx());
        assertEquals("the Terminal's room is its arriving rest's, written at rest", arriving,
            mWall.wall().pageBottomInsetPx(PageKind.TERMINAL));
    }

    private int restOfTerminalPx() {
        Object crossing = ReflectionHelpers.getField(mActivity, "mCrossingController");
        return ReflectionHelpers.callInstanceMethod(crossing, "travelRestReservationPx",
            ReflectionHelpers.ClassParameter.from(PageKind.class, PageKind.TERMINAL));
    }

    private int bottomKeptPx() {
        return ReflectionHelpers.getField(mActivity, "mBottomKeptPx");
    }

    private void start() {
        mActivity = Robolectric.buildActivity(TermuxActivity.class).get();
        mActivity.setContentView(R.layout.activity_termux);
        SharedPreferences sp = mActivity.getSharedPreferences(
            "page-room-memory-" + System.nanoTime(), Context.MODE_PRIVATE);
        TermuxAppSharedPreferences preferences =
            new TermuxAppSharedPreferences(mActivity, sp, null);
        preferences.setInAppKeyboardEnabled(true);
        preferences.setShowTerminalToolbar(false);
        preferences.setAppLauncherAppsRowEnabled(false);
        preferences.setAppLauncherAzRowEnabled(false);
        preferences.setAppLauncherExtraKeysRowEnabled(false);
        preferences.setAppLauncherWidgetPaneEnabled(true);
        preferences.setLauncherStartIn(WallRow.START_IN_HOME);

        FrameLayout paneHost = mActivity.findViewById(R.id.terminal_pane_host);
        View pane = mActivity.getLayoutInflater().inflate(R.layout.view_terminal_pane, paneHost, false);
        paneHost.addView(pane);
        TerminalView terminalView = pane.findViewById(R.id.terminal_view);
        TermuxTerminalSessionActivityClient sessionClient =
            mActivity.createTermuxTerminalSessionClient();
        TermuxTerminalViewClient viewClient = mActivity.createTermuxTerminalViewClient(sessionClient);
        terminalView.setTerminalViewClient(viewClient);
        TermuxAppSharedProperties properties = TermuxAppSharedProperties.init(mActivity);
        properties.loadTermuxPropertiesFromDisk();
        ReflectionHelpers.setField(mActivity, "mPreferences", preferences);
        ReflectionHelpers.setField(mActivity, "mProperties", properties);
        ReflectionHelpers.setField(mActivity, "mTerminalView", terminalView);
        ReflectionHelpers.setField(mActivity, "mTermuxTerminalSessionActivityClient", sessionClient);
        ReflectionHelpers.setField(mActivity, "mTermuxTerminalViewClient", viewClient);
        View toolbarPager = mActivity.findViewById(R.id.terminal_toolbar_view_pager);
        ReflectionHelpers.setField(mActivity, "mTerminalToolbarDefaultHeight",
            (float) toolbarPager.getLayoutParams().height);
        layout();

        ReflectionHelpers.callInstanceMethod(mActivity, "createPaneWallController",
            ReflectionHelpers.ClassParameter.from(View.class, paneHost));
        mWall = ReflectionHelpers.getField(mActivity, "mPaneWallController");
        mWall.landOnColdStart(preferences.getLauncherStartIn());
        ReflectionHelpers.callInstanceMethod(mActivity, "initializeInAppKeyboard",
            ReflectionHelpers.ClassParameter.from(android.os.Bundle.class, null));
        mKeyboard = ReflectionHelpers.getField(mActivity, "mInAppKeyboard");
        settle();
        // The first settle records nothing, so the wall's first move is made before the test's.
        mWall.goTo(PageKind.TERMINAL, false);
        settle();
        assertEquals(PageKind.TERMINAL, mWall.currentPage());
        assertEquals(true, mKeyboard.isVisible());
    }

    /** A bounded number of rounds: what the last change booked, then a layout. */
    private void settle() {
        for (int i = 0; i < 4; i++) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
            layout();
        }
    }

    private void layout() {
        ViewGroup root = mActivity.findViewById(R.id.activity_termux_root_view);
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 1920);
    }
}
