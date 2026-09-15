package com.termux.app.surfaces;

import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * The editor survives a rotation mid-edit.
 *
 * <p>That is the manifest: the launcher handles {@code orientation} and {@code screenSize} itself,
 * so a turn of the screen is a layout pass rather than a recreate, and the open card, the entry
 * snapshot and the dirtiness comparison are simply never torn down. That is what this holds — the
 * editor keeps no saved state of its own, so the declaration is the mechanism.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SurfaceEditorRotationTest {

    @Test
    public void theLauncherHandlesRotationItselfSoTheEditorIsNotTornDown() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        PackageManager packages = app.getPackageManager();
        ActivityInfo info = packages.getActivityInfo(
            new ComponentName(app, "com.termux.app.TermuxActivity"), 0);

        assertTrue("the editor's session would not survive a recreate",
            (info.configChanges & ActivityInfo.CONFIG_ORIENTATION) != 0);
        assertTrue((info.configChanges & ActivityInfo.CONFIG_SCREEN_SIZE) != 0);
    }
}
