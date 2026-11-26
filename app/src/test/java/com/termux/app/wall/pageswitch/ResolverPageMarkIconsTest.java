package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.termux.app.wayland.LinuxWindowIcons;
import com.termux.app.x11.X11WindowIconResolver;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

/**
 * A Linux window's mark key is answered by the one window-icon owner, and any other key (a
 * Display page's bound app id) by the icon resolver's colour form; neither is the chip's white
 * silhouette.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ResolverPageMarkIconsTest {

    @Test
    public void aWindowKeyAsksTheWindowIconOwnerAndKeepsItsColour() {
        Bitmap icon = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        icon.eraseColor(Color.RED);
        ResolverPageMarkIcons marks = new ResolverPageMarkIcons(
            RuntimeEnvironment.getApplication().getResources(), () -> null, () -> null,
            id -> id == 7 ? icon : null);
        Drawable drawable = marks.icon(new LinuxWindowIcons().key(7));
        assertNotNull(drawable);
        Bitmap shown = ((BitmapDrawable) drawable).getBitmap();
        assertSame(icon, shown);
        assertEquals(Color.RED, shown.getPixel(1, 1));
        assertNull(marks.icon(new LinuxWindowIcons().key(8)));
    }

    @Test
    public void anAppIdKeyNeverReachesTheWindowIconOwnerAndAsksTheResolver() {
        boolean[] called = new boolean[1];
        X11WindowIconResolver resolver = new X11WindowIconResolver(
            RuntimeEnvironment.getApplication().getResources(), (wmClass, icon, name) -> { });
        ResolverPageMarkIcons marks = new ResolverPageMarkIcons(
            RuntimeEnvironment.getApplication().getResources(), () -> resolver, () -> null, id -> {
                called[0] = true;
                return null;
            });
        // Nothing resolved yet (the resolution is asynchronous): no icon, never the owner's.
        assertNull(marks.icon("firefox"));
        assertFalse(called[0]);
        resolver.shutdown();
    }

    @Test
    public void withoutAnOwnerAWindowKeyHasNoIcon() {
        ResolverPageMarkIcons marks = new ResolverPageMarkIcons(
            RuntimeEnvironment.getApplication().getResources(), () -> null, () -> null);
        assertNull(marks.icon(new LinuxWindowIcons().key(7)));
    }
}
