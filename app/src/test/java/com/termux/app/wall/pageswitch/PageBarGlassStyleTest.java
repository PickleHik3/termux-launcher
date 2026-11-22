package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.termux.app.chrome.GlassRefraction;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicInteger;

/** The page bar's glass switch: one owner, concave by default, flipped by launcherctl. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class PageBarGlassStyleTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        PageBarGlassStyle.forget();
    }

    @After
    public void tearDown() {
        PageBarGlassStyle.forget();
    }

    @Test
    public void concaveByDefault() {
        assertSame(PageBarGlassStyle.CONCAVE, PageBarGlassStyle.DEFAULT);
        assertSame(PageBarGlassStyle.CONCAVE, PageBarGlassStyle.current(context));
        assertSame(PageBarGlassStyle.CONCAVE, PageBarGlassStyle.ofStored(null));
        assertSame(PageBarGlassStyle.CONCAVE, PageBarGlassStyle.ofStored("bogus"));
    }

    @Test
    public void namesParseInAnyCase() {
        assertSame(PageBarGlassStyle.WELL, PageBarGlassStyle.parse(" Well "));
        assertSame(PageBarGlassStyle.CONCAVE, PageBarGlassStyle.parse("CONCAVE"));
        assertNull(PageBarGlassStyle.parse("convex"));
        assertNull(PageBarGlassStyle.parse(null));
    }

    @Test
    public void setStoresTheStyleAndTellsEveryWatcher() {
        AtomicInteger told = new AtomicInteger();
        Runnable watcher = told::incrementAndGet;
        PageBarGlassStyle.watch(watcher);
        try {
            PageBarGlassStyle.set(context, PageBarGlassStyle.WELL);
            assertEquals(1, told.get());
            assertSame(PageBarGlassStyle.WELL, PageBarGlassStyle.current(context));
            // Read back from the store, not only the kept value.
            PageBarGlassStyle.forget();
            assertSame(PageBarGlassStyle.WELL, PageBarGlassStyle.current(context));
            TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
            assertEquals("well", preferences.getPageBarGlassStyle());
            PageBarGlassStyle.set(context, PageBarGlassStyle.CONCAVE);
            assertEquals(2, told.get());
            assertSame(PageBarGlassStyle.CONCAVE, PageBarGlassStyle.current(context));
        } finally {
            PageBarGlassStyle.unwatch(watcher);
        }
        int before = PageBarGlassStyle.watcherCount();
        PageBarGlassStyle.set(context, PageBarGlassStyle.CONCAVE);
        assertEquals(before, PageBarGlassStyle.watcherCount());
        assertEquals(2, told.get());
    }

    @Test
    public void onlyTheConcaveGlassWithFancierGlassBends() {
        GlassRefraction.Look fancier = GlassRefraction.Look.DEFAULT;
        int tint = 0x66000000;
        // The well wears no refraction, Fancier Glass or not.
        assertNull(PageBarGlass.refractionFor(PageBarGlassStyle.WELL, fancier, tint));
        // Fancier Glass off: the plain blur, no bend anywhere.
        assertNull(PageBarGlass.refractionFor(PageBarGlassStyle.CONCAVE, null, tint));
        GlassRefraction.Look concave = PageBarGlass.refractionFor(PageBarGlassStyle.CONCAVE, fancier, tint);
        assertTrue(concave.concave);
        assertEquals(40, concave.coverPercent);
        assertFalse(fancier.concave);
    }
}
