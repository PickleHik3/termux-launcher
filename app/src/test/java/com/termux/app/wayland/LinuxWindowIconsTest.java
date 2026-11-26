package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;

/**
 * The one owner of a Linux window's icon: the run-time icon wins, the desktop entry's is the
 * fallback, and the pixels held stay under the budget and go with the window. Robolectric only
 * because the icons are real bitmaps.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LinuxWindowIconsTest {

    /** The entry icons and the theme names that resolve. */
    private static final class Lookup implements LinuxWindowIcons.Lookup {
        final Map<String, Bitmap> apps = new HashMap<>();
        final Map<String, Bitmap> names = new HashMap<>();

        @Nullable @Override public Bitmap forAppId(@NonNull String appId) {
            return apps.get(appId);
        }

        @Nullable @Override public Bitmap named(@NonNull String name) {
            return names.get(name);
        }
    }

    private static Bitmap bitmap(int edge) {
        return Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888);
    }

    @Test
    public void theRunTimeIconWinsOverTheEntryIcon() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        Lookup lookup = new Lookup();
        Bitmap runTime = bitmap(8);
        lookup.apps.put("firefox", bitmap(8));
        icons.set(7, null, runTime);
        assertSame(runTime, icons.iconOf(7, "firefox", lookup));
    }

    @Test
    public void anAppThatSetsNoIconKeepsItsEntryIcon() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        Lookup lookup = new Lookup();
        Bitmap entry = bitmap(8);
        lookup.apps.put("firefox", entry);
        assertSame(entry, icons.iconOf(7, "firefox", lookup));
    }

    @Test
    public void takingTheIconAwayGoesBackToTheEntryIcon() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        Lookup lookup = new Lookup();
        Bitmap entry = bitmap(8);
        lookup.apps.put("firefox", entry);
        icons.set(7, null, bitmap(8));
        icons.set(7, null, null);
        assertSame(entry, icons.iconOf(7, "firefox", lookup));
        assertFalse(icons.has(7));
        assertEquals(0, icons.heldBytes());
    }

    @Test
    public void noIconAnywhereIsNull() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        Lookup lookup = new Lookup();
        assertNull(icons.iconOf(7, "firefox", lookup));
        assertNull(icons.iconOf(7, "", lookup));
        assertNull(icons.iconOf(7, null, lookup));
    }

    @Test
    public void anIconGivenByNameResolvesLikeAnEntrysAndYieldsWhileUnknown() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        Lookup lookup = new Lookup();
        Bitmap entry = bitmap(8);
        Bitmap named = bitmap(8);
        lookup.apps.put("app", entry);
        icons.set(7, "app-icon", null);
        // Not resolved yet: the entry's icon shows meanwhile.
        assertSame(entry, icons.iconOf(7, "app", lookup));
        lookup.names.put("app-icon", named);
        assertSame(named, icons.iconOf(7, "app", lookup));
    }

    @Test
    public void pixelsWinOverAName() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        Lookup lookup = new Lookup();
        Bitmap pixels = bitmap(8);
        lookup.names.put("app-icon", bitmap(8));
        icons.set(7, "app-icon", pixels);
        assertSame(pixels, icons.iconOf(7, "app", lookup));
    }

    @Test
    public void theKeyNamesTheWindowAndChangesWheneverItsIconDoes() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        String none = icons.key(7);
        icons.set(7, null, bitmap(8));
        String first = icons.key(7);
        icons.set(7, null, bitmap(8));
        String second = icons.key(7);
        icons.set(7, null, null);
        String gone = icons.key(7);
        assertNotEquals(none, first);
        assertNotEquals(first, second);
        assertNotEquals(second, gone);
        for (String key : new String[] {none, first, second, gone}) {
            assertEquals(7L, LinuxWindowIcons.windowOfKey(key));
        }
        assertNotEquals(icons.key(7), icons.key(8));
    }

    @Test
    public void aChangedAppIdChangesTheKeyToo() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        String before = icons.key(7);
        icons.appChanged(7);
        assertNotEquals(before, icons.key(7));
    }

    @Test
    public void everyChangeRaisesTheVersionAndNoChangeDoesNot() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        int v = icons.version();
        icons.set(7, null, null);
        assertEquals(v, icons.version());
        icons.set(7, null, bitmap(8));
        assertNotEquals(v, icons.version());
    }

    @Test
    public void aDisplayPagesAppIdIsNotAWindowKey() {
        assertEquals(0L, LinuxWindowIcons.windowOfKey("firefox"));
        assertEquals(0L, LinuxWindowIcons.windowOfKey(null));
        assertEquals(0L, LinuxWindowIcons.windowOfKey("linux-window:x:1"));
        assertEquals(0L, LinuxWindowIcons.windowOfKey("linux-window:12"));
    }

    @Test
    public void theHeldPixelsStayUnderTheBudgetAndTheLeastRecentGoesFirst() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        // 64 px silhouettes cost 16 KB each: 32 fill the budget exactly.
        int fit = (int) (LinuxWindowIcons.BUDGET_BYTES / (64 * 64 * 4));
        for (long id = 1; id <= fit; id++) icons.set(id, null, bitmap(64));
        assertEquals(LinuxWindowIcons.BUDGET_BYTES, icons.heldBytes());
        // Window 1 is asked, so window 2 is the least recent.
        icons.iconOf(1, "", new Lookup());
        String before = icons.key(2);
        icons.set(1000, null, bitmap(64));
        assertTrue(icons.heldBytes() <= LinuxWindowIcons.BUDGET_BYTES);
        assertTrue(icons.has(1));
        assertFalse(icons.has(2));
        assertTrue(icons.has(1000));
        // An evicted icon is a changed icon.
        assertNotEquals(before, icons.key(2));
    }

    @Test
    public void aBitmapBiggerThanTheWholeBudgetIsNotKept() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        icons.set(7, null, bitmap(400));
        assertEquals(0, icons.heldBytes());
        assertFalse(icons.has(7));
    }

    @Test
    public void theIconGoesWhenItsWindowCloses() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        Lookup lookup = new Lookup();
        icons.set(7, null, bitmap(64));
        icons.set(8, null, bitmap(64));
        assertEquals(2L * 64 * 64 * 4, icons.heldBytes());
        icons.drop(7);
        assertEquals(64L * 64 * 4, icons.heldBytes());
        assertNull(icons.iconOf(7, "", lookup));
        assertFalse(icons.has(7));
        icons.drop(7);
        icons.clear();
        assertEquals(0, icons.heldBytes());
        assertEquals(0, icons.size());
    }

    @Test
    public void replacingAnIconGivesBackThePixelsOfTheOldOne() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        icons.set(7, null, bitmap(64));
        icons.set(7, null, bitmap(32));
        assertEquals(32L * 32 * 4, icons.heldBytes());
    }
}
