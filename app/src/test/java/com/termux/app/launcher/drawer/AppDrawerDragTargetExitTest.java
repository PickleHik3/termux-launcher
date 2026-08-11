package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Application;
import android.os.Build;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerDragTargetExitTest {
    @Test public void targetExitImmediatelyDisarmsDwellAndAutoscroll() throws Exception {
        AppDrawerContentView content = new AppDrawerContentView(
            RuntimeEnvironment.getApplication());
        set(content, "mDragEdgeDirection", 1);
        set(content, "mDragEdgeConsumed", true);
        set(content, "mDragAutoscrollVelocity", 900f);

        content.onDragTargetExited();

        assertEquals(0, get(content, "mDragEdgeDirection"));
        assertFalse((Boolean) get(content, "mDragEdgeConsumed"));
        assertEquals(0f, (Float) get(content, "mDragAutoscrollVelocity"), 0f);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
