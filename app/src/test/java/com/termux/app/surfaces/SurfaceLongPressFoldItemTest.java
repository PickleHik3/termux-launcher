package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import android.app.Application;
import org.robolectric.annotation.Config;

/** The status bar menu's fold item: its words and what it asks of the activity. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class SurfaceLongPressFoldItemTest {

    @Test public void labelIsExpandOnACompactBarAndCompactOnAnExpandedOne() {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals("Expand", SurfaceLongPress.foldLabel(context, true));
        assertEquals("Compact", SurfaceLongPress.foldLabel(context, false));
    }

    @Test public void actionDismissesThenAsksForTheOtherForm() {
        List<String> calls = new ArrayList<>();
        SurfaceLongPress.Host host = (SurfaceLongPress.Host) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{SurfaceLongPress.Host.class},
            (proxy, method, args) -> {
                if (method.getName().equals("setStatusBarCompact"))
                    calls.add("compact=" + args[0]);
                return null;
            });

        SurfaceLongPress.foldTo(host, false, () -> calls.add("dismiss"));
        SurfaceLongPress.foldTo(host, true, () -> calls.add("dismiss"));

        assertEquals(java.util.Arrays.asList("dismiss", "compact=true", "dismiss",
            "compact=false"), calls);
    }
}
