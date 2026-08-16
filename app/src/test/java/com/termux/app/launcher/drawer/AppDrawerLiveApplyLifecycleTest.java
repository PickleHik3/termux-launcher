package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import android.app.Application;
import android.os.Build;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerLiveApplyLifecycleTest {
    @Test public void duplicateConfigNoOpsAndUnbuiltConfigRemainsAValueUntilFirstTree() {
        AppDrawerLayoutConfig pending = new AppDrawerLayoutConfig(AppDrawerViewType.HORIZONTAL,
            44, 4, 6, 5, 2);
        assertEquals(pending, new AppDrawerLayoutConfig(AppDrawerViewType.HORIZONTAL,
            44, 4, 6, 5, 2));
        AppDrawerContentView content = new AppDrawerContentView(RuntimeEnvironment.getApplication());
        content.onDragStateChanged(true);
        content.cancelTransientFolderState();
        content.onDragStateChanged(false);
        content.setViewType(pending.viewType);
        assertEquals(AppDrawerViewType.HORIZONTAL, content.getViewType());
    }
}
