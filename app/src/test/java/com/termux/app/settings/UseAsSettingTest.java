package com.termux.app.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import com.termux.app.wall.UseAs;
import com.termux.app.wall.WallPagesStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Home screen, then Terminal, then Home screen again: the wall's pages come back as they were. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class UseAsSettingTest {

    @Test
    public void homeScreenTerminalHomeScreenRestoresTheWallPages() {
        Context context = RuntimeEnvironment.getApplication();
        UseAsSetting.write(context, UseAs.HOME);
        int before = WallPagesStore.open(context).current().pages().size();

        UseAsSetting.write(context, UseAs.TERMINAL);
        assertEquals(UseAs.TERMINAL, UseAsSetting.read(context));

        UseAsSetting.write(context, UseAs.HOME);
        assertEquals(UseAs.HOME, UseAsSetting.read(context));
        assertEquals(before, WallPagesStore.open(context).current().pages().size());
        assertTrue(WallPagesStore.open(context).current().detached().isEmpty());
    }
}
