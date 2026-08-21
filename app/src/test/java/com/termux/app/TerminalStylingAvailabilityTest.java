package com.termux.app;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;

import com.termux.shared.termux.TermuxConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * The terminal long-press menu offers Style only when the Termux:Styling companion is installed.
 *
 * <p>The gate is the reason this resolution is a static taking a {@link PackageManager}: the menu
 * row and the launch have to agree about what "installed" means, and neither can be exercised
 * through a real activity without standing up the whole terminal.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalStylingAvailabilityTest {

    @Test
    public void withoutTheCompanionThereIsNothingToOffer() {
        PackageManager packageManager = RuntimeEnvironment.getApplication().getPackageManager();

        assertNull(TermuxActivity.resolveTerminalStylingIntent(packageManager));
    }

    @Test
    public void theDocumentedActivityIsPreferredWhenItExists() {
        ComponentName styling = new ComponentName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME,
            TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        installStylingActivity(styling, false);

        Intent intent = TermuxActivity.resolveTerminalStylingIntent(
            RuntimeEnvironment.getApplication().getPackageManager());

        assertNotNull(intent);
        assertEquals(styling, intent.getComponent());
    }

    /**
     * The plugin has renamed its entry activity across releases, so a companion that is installed
     * but no longer carries the class this constant names must still produce the row.
     */
    @Test
    public void aRenamedEntryActivityFallsBackToTheLauncherActivity() {
        ComponentName renamed = new ComponentName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME,
            TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_LAUNCHER_ACTIVITY_NAME);
        installStylingActivity(renamed, true);

        Intent intent = TermuxActivity.resolveTerminalStylingIntent(
            RuntimeEnvironment.getApplication().getPackageManager());

        assertNotNull(intent);
        assertEquals(renamed, intent.getComponent());
    }

    private static void installStylingActivity(ComponentName component, boolean asLauncher) {
        ShadowPackageManager shadowPackageManager =
            Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager());
        shadowPackageManager.addActivityIfNotPresent(component);

        if (asLauncher) {
            IntentFilter launcherFilter = new IntentFilter(Intent.ACTION_MAIN);
            launcherFilter.addCategory(Intent.CATEGORY_LAUNCHER);
            shadowPackageManager.addIntentFilterForActivity(component, launcherFilter);
        }
    }
}
