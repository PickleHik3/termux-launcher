package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.termux.app.wayland.LinuxWindowIcons;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

/**
 * A Linux window's mark key is answered by the one window-icon owner; any other key (a Display
 * page's bound app id) goes to the entry icon resolver as before.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ResolverPageMarkIconsTest {

    @Test
    public void aWindowKeyAsksTheWindowIconOwner() {
        Bitmap icon = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        ResolverPageMarkIcons marks = new ResolverPageMarkIcons(
            RuntimeEnvironment.getApplication().getResources(), () -> null, () -> null,
            id -> id == 7 ? icon : null);
        Drawable drawable = marks.icon(new LinuxWindowIcons().key(7));
        assertNotNull(drawable);
        assertSame(icon, ((BitmapDrawable) drawable).getBitmap());
        assertNull(marks.icon(new LinuxWindowIcons().key(8)));
    }

    @Test
    public void anAppIdKeyNeverReachesTheWindowIconOwner() {
        boolean[] called = new boolean[1];
        ResolverPageMarkIcons marks = new ResolverPageMarkIcons(
            RuntimeEnvironment.getApplication().getResources(), () -> null, () -> null, id -> {
                called[0] = true;
                return null;
            });
        assertNull(marks.icon("firefox"));
        assertFalse(called[0]);
    }

    @Test
    public void withoutAnOwnerAWindowKeyHasNoIcon() {
        ResolverPageMarkIcons marks = new ResolverPageMarkIcons(
            RuntimeEnvironment.getApplication().getResources(), () -> null, () -> null);
        assertNull(marks.icon(new LinuxWindowIcons().key(7)));
    }
}
