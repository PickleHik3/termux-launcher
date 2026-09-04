package com.termux.app.launcher.data;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LauncherUsageStatsStoreVersionTest {

    /**
     * The drawer's category memo asks this instead of diffing rankings: unchanged between reads
     * means the suggestions could not have moved, and every mutation moves it.
     */
    @Test
    public void versionMovesOnEveryMutationAndOnlyThen() {
        LauncherUsageStatsStore store = new LauncherUsageStatsStore(RuntimeEnvironment.getApplication());
        long initial = store.version();

        assertEquals(initial, store.version());
        store.recordLaunch("com.example/.Main");
        long afterLaunch = store.version();
        assertNotEquals(initial, afterLaunch);
        store.clear();
        assertNotEquals(afterLaunch, store.version());
    }
}
