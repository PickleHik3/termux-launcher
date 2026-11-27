package com.termux.app.wayland;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
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
 * The one owner of a Linux window's icon, in its two forms (the chip's silhouette, the mark's
 * colour) from one order: the run-time icon wins, the desktop entry's is the fallback, and the
 * pixels held stay under the budget and go with the window. Robolectric only because the icons
 * are real bitmaps.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LinuxWindowIconsTest {

    private static final LinuxWindowIcons.Form COLOUR = LinuxWindowIcons.Form.COLOUR;
    private static final LinuxWindowIcons.Form SILHOUETTE = LinuxWindowIcons.Form.SILHOUETTE;

    /** The entry icons and the theme names that resolve, one bitmap per form. */
    private static final class Lookup implements LinuxWindowIcons.Lookup {
        final Map<String, Bitmap> apps = new HashMap<>();
        final Map<String, Bitmap> names = new HashMap<>();

        @Nullable @Override public Bitmap forAppId(@NonNull String appId, @NonNull LinuxWindowIcons.Form form) {
            return apps.get(appId + form);
        }

        @Nullable @Override public Bitmap named(@NonNull String name, @NonNull LinuxWindowIcons.Form form) {
            return names.get(name + form);
        }
    }

    private static void both(Map<String, Bitmap> map, String key, Bitmap colour, Bitmap silhouette) {
        map.put(key + COLOUR, colour);
        map.put(key + SILHOUETTE, silhouette);
    }

    /** Bytes of one icon at {@code edge} px in both forms: the colour pixels and the 64 px silhouette. */
    private static long cost(int edge) {
        return (long) edge * edge * 4 + 64L * 64 * 4;
    }

    private static Bitmap bitmap(int edge) {
        return Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888);
    }

    private static Bitmap red(int edge) {
        Bitmap b = bitmap(edge);
        b.eraseColor(Color.RED);
        return b;
    }

    @Test
    public void theRunTimeIconWinsInBothForms() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        Lookup lookup = new Lookup();
        both(lookup.apps, "firefox", bitmap(8), bitmap(8));
        icons.set(7, null, red(32));
        Bitmap colour = icons.iconOf(7, "firefox", COLOUR, lookup);
        Bitmap silhouette = icons.iconOf(7, "firefox", SILHOUETTE, lookup);
        assertNotNull(colour);
        assertNotNull(silhouette);
        assertTrue(colour != silhouette);
        assertTrue(colour != lookup.apps.get("firefox" + COLOUR));
        assertTrue(silhouette != lookup.apps.get("firefox" + SILHOUETTE));
    }

    @Test
    public void theMarksFormKeepsTheColourAndTheChipsIsASilhouette() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        Lookup lookup = new Lookup();
        icons.set(7, null, red(32));
        Bitmap colour = icons.iconOf(7, "", COLOUR, lookup);
        Bitmap silhouette = icons.iconOf(7, "", SILHOUETTE, lookup);
        int c = colour.getPixel(16, 16);
        assertEquals(255, Color.alpha(c));
        assertEquals(255, Color.red(c));
        assertEquals(0, Color.green(c));
        assertEquals(0, Color.blue(c));
        int s = silhouette.getPixel(32, 32);
        assertEquals(255, Color.red(s));
        assertEquals(255, Color.green(s));
        assertEquals(255, Color.blue(s));
    }

    @Test
    public void aBigRunTimeIconIsKeptAtMostNinetySixPixels() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        icons.set(7, null, red(192));
        Bitmap colour = icons.iconOf(7, "", COLOUR, new Lookup());
        assertEquals(LinuxWindowIcons.COLOUR_MAX_PX, colour.getWidth());
        assertEquals(LinuxWindowIcons.COLOUR_MAX_PX, colour.getHeight());
        assertEquals(cost(96), icons.heldBytes());
    }

    @Test
    public void anAppThatSetsNoIconKeepsItsEntryIconInBothForms() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        Lookup lookup = new Lookup();
        Bitmap colour = bitmap(8);
        Bitmap silhouette = bitmap(8);
        both(lookup.apps, "firefox", colour, silhouette);
        assertSame(colour, icons.iconOf(7, "firefox", COLOUR, lookup));
        assertSame(silhouette, icons.iconOf(7, "firefox", SILHOUETTE, lookup));
    }

    @Test
    public void takingTheIconAwayGoesBackToTheEntryIcon() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        Lookup lookup = new Lookup();
        Bitmap entry = bitmap(8);
        both(lookup.apps, "firefox", entry, bitmap(8));
        icons.set(7, null, red(32));
        icons.set(7, null, null);
        assertSame(entry, icons.iconOf(7, "firefox", COLOUR, lookup));
        assertFalse(icons.has(7));
        assertEquals(0, icons.heldBytes());
    }

    @Test
    public void noIconAnywhereIsNull() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        Lookup lookup = new Lookup();
        for (LinuxWindowIcons.Form form : LinuxWindowIcons.Form.values()) {
            assertNull(icons.iconOf(7, "firefox", form, lookup));
            assertNull(icons.iconOf(7, "", form, lookup));
            assertNull(icons.iconOf(7, null, form, lookup));
        }
    }

    @Test
    public void anIconGivenByNameResolvesLikeAnEntrysAndYieldsWhileUnknown() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        Lookup lookup = new Lookup();
        Bitmap entry = bitmap(8);
        Bitmap named = bitmap(8);
        both(lookup.apps, "app", entry, bitmap(8));
        icons.set(7, "app-icon", null);
        // Not resolved yet: the entry's icon shows meanwhile.
        assertSame(entry, icons.iconOf(7, "app", COLOUR, lookup));
        both(lookup.names, "app-icon", named, bitmap(8));
        assertSame(named, icons.iconOf(7, "app", COLOUR, lookup));
    }

    @Test
    public void pixelsWinOverAName() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        Lookup lookup = new Lookup();
        both(lookup.names, "app-icon", bitmap(8), bitmap(8));
        icons.set(7, "app-icon", red(32));
        Bitmap colour = icons.iconOf(7, "app", COLOUR, lookup);
        assertTrue(colour != lookup.names.get("app-icon" + COLOUR));
        assertEquals(Color.RED, colour.getPixel(1, 1));
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
    public void theHeldPixelsOfBothFormsStayUnderTheBudgetAndTheLeastRecentGoesFirst() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        int fit = (int) (LinuxWindowIcons.BUDGET_BYTES / cost(96));
        assertEquals(19, fit);
        for (long id = 1; id <= fit; id++) icons.set(id, null, bitmap(96));
        assertEquals(fit * cost(96), icons.heldBytes());
        assertTrue(icons.heldBytes() <= LinuxWindowIcons.BUDGET_BYTES);
        // Window 1 is asked, so window 2 is the least recent.
        icons.iconOf(1, "", COLOUR, new Lookup());
        String before = icons.key(2);
        icons.set(1000, null, bitmap(96));
        assertTrue(icons.heldBytes() <= LinuxWindowIcons.BUDGET_BYTES);
        assertTrue(icons.has(1));
        assertFalse(icons.has(2));
        assertTrue(icons.has(1000));
        // An evicted icon is a changed icon.
        assertNotEquals(before, icons.key(2));
    }

    @Test
    public void theIconGoesWhenItsWindowCloses() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        Lookup lookup = new Lookup();
        icons.set(7, null, bitmap(64));
        icons.set(8, null, bitmap(64));
        assertEquals(2 * cost(64), icons.heldBytes());
        icons.drop(7);
        assertEquals(cost(64), icons.heldBytes());
        assertNull(icons.iconOf(7, "", COLOUR, lookup));
        assertFalse(icons.has(7));
        icons.drop(7);
        icons.clear();
        assertEquals(0, icons.heldBytes());
        assertEquals(0, icons.size());
    }

    @Test
    public void replacingAnIconGivesBackThePixelsOfTheOldOne() {
        LinuxWindowIcons icons = new LinuxWindowIcons();
        icons.set(7, null, bitmap(96));
        icons.set(7, null, bitmap(32));
        assertEquals(cost(32), icons.heldBytes());
    }
}
