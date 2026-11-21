package com.termux.app.page;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;

import com.termux.app.glass.GlassEntry;
import com.termux.app.glass.GlassKind;
import com.termux.app.glass.GlassService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

/** A page's glass lives as long as the page is registered, not as long as it is in front. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PagesGlassTest {

    /** A page declaring one entry of {@code element} on {@code view}. */
    static final class GlassPage implements Page {
        final String element;
        final View view;

        GlassPage(String element, View view) {
            this.element = element;
            this.view = view;
        }

        @NonNull @Override public PageKind kind() { return PageKind.ASSISTANT; }
        @NonNull @Override public View view() { return view; }
        @NonNull @Override public Rest rest(@NonNull Rest.Inputs in) { throw new UnsupportedOperationException(); }
        @Override public boolean onBack() { return false; }
        @Override public void onFront() { }
        @Override public void offFront() { }
        @Override public void declareGlass(@NonNull List<GlassEntry> out) {
            out.add(new GlassEntry(element, view, GlassKind.LIVE, GlassEntry.PRIORITY_WIDGET));
        }
    }

    private GlassService glass;
    private Pages pages;
    private View view;

    @Before
    public void setUp() {
        glass = new GlassService(null);
        pages = new Pages(PageHost.NONE, () -> { });
        pages.setGlass(glass);
        view = new View(RuntimeEnvironment.getApplication());
    }

    @Test
    public void putRegistersThePagesEntry() {
        pages.put(1, new GlassPage("w", view));
        assertNotNull(glass.entry("w"));
    }

    @Test
    public void leavingTheFrontKeepsTheEntry() {
        pages.put(1, new GlassPage("w", view));
        pages.landed(1, PageKind.ASSISTANT);
        pages.applyFront();
        pages.committed(2, PageKind.ASSISTANT);
        pages.landed(2, PageKind.ASSISTANT);
        pages.applyFront();
        assertNotNull(glass.entry("w"));
    }

    @Test
    public void removeUnregisters() {
        pages.put(1, new GlassPage("w", view));
        pages.remove(1);
        assertNull(glass.entry("w"));
    }

    @Test
    public void twoPagesDeclaringOneElementShareOneEntry() {
        pages.put(1, new GlassPage("w", view));
        pages.put(2, new GlassPage("w", view));
        assertEquals(1, glass.size());
        pages.remove(1);
        assertNotNull(glass.entry("w"));
        pages.remove(2);
        assertNull(glass.entry("w"));
    }

    @Test
    public void settingNoGlassUndeclares() {
        pages.put(1, new GlassPage("w", view));
        pages.setGlass(null);
        assertNull(glass.entry("w"));
    }
}
