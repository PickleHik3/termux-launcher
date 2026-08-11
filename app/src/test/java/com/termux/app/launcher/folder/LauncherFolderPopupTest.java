package com.termux.app.launcher.folder;

import static org.junit.Assert.*;
import android.app.Application;
import android.os.Build;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class LauncherFolderPopupTest {
    @Test public void sharedControllerForcesFocuslessImeFlagsAndCleansOnce() {
        LauncherFolderPopupController controller = new LauncherFolderPopupController();
        PopupWindow popup = new PopupWindow(new FrameLayout(RuntimeEnvironment.getApplication()),
            100, 100, true);
        AtomicInteger dismisses = new AtomicInteger();
        controller.show(popup, "folder", () -> {}, dismisses::incrementAndGet);
        assertFalse(popup.isFocusable());
        assertEquals(PopupWindow.INPUT_METHOD_NOT_NEEDED, popup.getInputMethodMode());
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED, popup.getSoftInputMode());
        assertEquals("folder", controller.folderId());
        controller.dismissImmediate();
        controller.dismissImmediate();
        assertEquals(1, dismisses.get());
        assertNull(controller.folderId());
    }
}
