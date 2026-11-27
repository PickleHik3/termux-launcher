package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.preference.PreferenceViewHolder;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * The Desktops page's install panel: binding its row puts the page's watch-only terminal in it,
 * and a rebuilt page's new row takes it from the old one. The row's root used to be found by its
 * id, which {@code Preference.onBindViewHolder} overwrites, so the panel stayed empty.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class DesktopInstallTerminalPreferenceTest {

    @Test
    public void bindingTheRowShowsTheTerminalInIt() {
        Context context = themed();
        WatchOnlyTerminal terminal = new WatchOnlyTerminal(context, 64);
        View row = bind(context, terminal);

        assertSame(row, terminal.getParent());
        assertEquals(1, ((FrameLayout) row).getChildCount());
    }

    @Test
    public void aRebuiltRowTakesTheTerminalFromTheOldOne() {
        Context context = themed();
        WatchOnlyTerminal terminal = new WatchOnlyTerminal(context, 64);
        View first = bind(context, terminal);
        View second = bind(context, terminal);

        assertSame(second, terminal.getParent());
        assertEquals(0, ((FrameLayout) first).getChildCount());
    }

    private static View bind(Context context, WatchOnlyTerminal terminal) {
        View row = LayoutInflater.from(context)
            .inflate(R.layout.preference_desktop_install_terminal, new FrameLayout(context), false);
        DesktopInstallTerminalPreference preference = new DesktopInstallTerminalPreference(context);
        preference.setTerminal(terminal);
        preference.onBindViewHolder(PreferenceViewHolder.createInstanceForTests(row));
        return row;
    }

    private static Context themed() {
        Application app = RuntimeEnvironment.getApplication();
        return new ContextThemeWrapper(app, R.style.Theme_TermuxApp_DayNight_NoActionBar);
    }
}
