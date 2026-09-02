package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

/**
 * The Android-keyboard search: a keyboard request is answered by focusing the drawer's own text
 * field, and a committed close lets it go exactly once.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerTextFieldSearchTest {

    private TermuxAppSharedPreferences preferences;
    private FakeAppDrawerHost host;
    private AppDrawerController controller;
    private AppDrawerContentView content;

    @Before public void setUp() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        SharedPreferences raw = activity.getSharedPreferences("drawer-text-field-search",
            Context.MODE_PRIVATE);
        raw.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(activity, raw, null);
        host = new FakeAppDrawerHost(activity, preferences);
        controller = new AppDrawerController(host);
        content = new AppDrawerContentView(activity);
        // The two listeners buildContent() wires; the plane itself is never built here.
        content.setRevealListener(() -> { });
        content.setSearchKeyboardRequestListener(controller::onSearchKeyboardRequested);
        ReflectionHelpers.setField(controller, "mContent", content);
        ReflectionHelpers.setField(controller, "mOpenRect", new Frame(0f, 0f, 720f, 1280f));
    }

    @Test public void theRequestGoesToTheDrawersOwnFieldNotTheTerminal() {
        preferences.setAppLauncherDrawerSearchOnOpenEnabled(true);
        ReflectionHelpers.setField(controller, "mTextFieldSearch", true);

        settleOpen();

        // The content is not in a window here, so the focus is owed to the frame loop rather than
        // paid at once; either way the terminal is never asked.
        assertEquals(0, host.searchKeyboardRequests);
        assertTrue((boolean) ReflectionHelpers.getField(controller, "mTextFieldFocusPending"));
        assertEquals(0, host.textFieldSearchBegins);
    }

    @Test public void aCommittedCloseLetsTheFieldGoOnce() {
        preferences.setAppLauncherDrawerSearchOnOpenEnabled(true);
        ReflectionHelpers.setField(controller, "mTextFieldSearch", true);
        settleOpen();

        ReflectionHelpers.callInstanceMethod(controller, "settle",
            ReflectionHelpers.ClassParameter.from(boolean.class, false),
            ReflectionHelpers.ClassParameter.from(float.class, 0f));
        ReflectionHelpers.callInstanceMethod(controller, "onClosed");

        assertEquals(1, host.textFieldSearchEnds);
    }

    @Test public void withTheSwitchOffTheTerminalIsStillAsked() {
        preferences.setAppLauncherDrawerSearchOnOpenEnabled(true);

        settleOpen();

        assertEquals(0, host.textFieldSearchBegins);
        assertEquals(1, host.searchKeyboardRequests);
    }

    private void settleOpen() {
        ReflectionHelpers.callInstanceMethod(controller, "settle",
            ReflectionHelpers.ClassParameter.from(boolean.class, true),
            ReflectionHelpers.ClassParameter.from(float.class, 0f));
    }
}

