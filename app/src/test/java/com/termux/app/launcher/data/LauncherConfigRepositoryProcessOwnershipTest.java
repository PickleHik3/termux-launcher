package com.termux.app.launcher.data;

import static org.junit.Assert.assertSame;

import android.app.Application;
import android.os.Build;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LauncherConfigRepositoryProcessOwnershipTest {
    @Test public void everyOwnerReceivesOneProcessLiveRepository() {
        Application application = RuntimeEnvironment.getApplication();
        assertSame(LauncherConfigRepository.getInstance(application),
            LauncherConfigRepository.getInstance(application.getBaseContext()));
    }
}
