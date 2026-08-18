package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import android.app.Application;
import android.os.Build;
import android.view.MotionEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerDragTerminalDispatchTest {
    @Test public void reentrantInteractivityResetCannotTurnDragTerminalIntoClick() {
        AppDrawerContentView view = new AppDrawerContentView(RuntimeEnvironment.getApplication());
        view.setInteractive(true);
        view.armTerminalDispatchDragLatch();
        view.setInteractive(false);
        assertTrue(ReflectionHelpers.getField(view,
            "mSuppressCellClickDuringTerminalDispatch"));
        MotionEvent up = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, 1, 1, 0);
        view.dispatchTouchEvent(up);
        up.recycle();
        assertFalse(ReflectionHelpers.getField(view,
            "mSuppressCellClickDuringTerminalDispatch"));
    }
}
