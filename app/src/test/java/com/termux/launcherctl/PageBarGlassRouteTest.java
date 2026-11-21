package com.termux.launcherctl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.termux.app.wall.pageswitch.PageBarGlassStyle;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** {@code launcherctl pagebar-glass concave|well}: the route flips the one stored switch. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class PageBarGlassRouteTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @After
    public void tearDown() {
        PageBarGlassStyle.set(context, PageBarGlassStyle.DEFAULT);
    }

    @Test
    public void answersGetAndPostOnly() {
        assertTrue(DeviceControlRoutes.handles("GET", "/v1/pagebar-glass"));
        assertTrue(DeviceControlRoutes.handles("POST", "/v1/pagebar-glass"));
        assertFalse(DeviceControlRoutes.handles("DELETE", "/v1/pagebar-glass"));
    }

    @Test
    public void postFlipsTheStyleAndGetReadsIt() throws Exception {
        JSONObject set = DeviceControlRoutes.handle(context, "POST", "/v1/pagebar-glass",
            "{\"style\":\"well\"}");
        assertTrue(set.toString(), set.getBoolean("ok"));
        assertEquals("well", set.getString("style"));
        assertSame(PageBarGlassStyle.WELL, PageBarGlassStyle.current(context));
        JSONObject get = DeviceControlRoutes.handle(context, "GET", "/v1/pagebar-glass", null);
        assertEquals("well", get.getString("style"));
        DeviceControlRoutes.handle(context, "POST", "/v1/pagebar-glass", "{\"style\":\"concave\"}");
        assertSame(PageBarGlassStyle.CONCAVE, PageBarGlassStyle.current(context));
    }

    @Test
    public void anUnknownStyleChangesNothing() throws Exception {
        DeviceControlRoutes.handle(context, "POST", "/v1/pagebar-glass", "{\"style\":\"well\"}");
        JSONObject bad = DeviceControlRoutes.handle(context, "POST", "/v1/pagebar-glass",
            "{\"style\":\"convex\"}");
        assertFalse(bad.optBoolean("ok", false));
        assertSame(PageBarGlassStyle.WELL, PageBarGlassStyle.current(context));
    }
}
