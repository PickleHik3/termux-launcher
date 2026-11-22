package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.glass.GlassEntry;
import com.termux.app.glass.GlassService;
import com.termux.app.page.PageHost;
import com.termux.app.page.Pages;
import com.termux.app.window.FramePipeline;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** The floats inside windows declare their glass through the Terminal page, as Home's do. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalGlassTest {

    private static final int TERMINAL_CELL = 5;

    private static final class FakeHost implements TerminalPageHost {
        @Nullable TerminalPaneController.Window active;
        View paneHost;
        @Nullable @Override public TerminalPaneController.Window activeWindow() { return active; }
        @NonNull @Override public View paneHost() { return paneHost; }
        @Override public boolean activePaneHoldsKeys() { return true; }
        @Override public boolean handKeysToTerminal() { return true; }
        @Override public boolean backIsEscape() { return false; }
        @Override public boolean lowerKeyboard() { return false; }
    }

    private FakeHost host;
    private GlassService glass;
    private Pages pages;
    private TerminalGlass terminal;
    private TerminalPages stops;
    private TerminalPaneController.Window w1;
    private TerminalPaneController.Window w2;

    @Before
    public void setUp() {
        host = new FakeHost();
        host.paneHost = new FrameLayout(Robolectric.buildActivity(android.app.Activity.class).get());
        glass = new GlassService(new FramePipeline());
        pages = new Pages(PageHost.NONE, () -> { });
        pages.setGlass(glass);
        terminal = new TerminalGlass(glass, pages::declareGlass, () -> 8);
        stops = new TerminalPages(host, terminal);
        pages.putStops(TERMINAL_CELL, stops);
        w1 = new TerminalPaneController.Window(new TerminalPaneController.Leaf(null));
        w2 = new TerminalPaneController.Window(new TerminalPaneController.Leaf(null));
        host.active = w1;
    }

    private static View slab() {
        return new View(RuntimeEnvironment.getApplication());
    }

    private static String element(View slab) {
        return "float@" + Integer.toHexString(System.identityHashCode(slab));
    }

    @Test
    public void aWornSlabDeclaresOneFloatEntryAndDropRemovesIt() {
        View a = slab();
        terminal.wear(a, null);
        assertEquals(1, glass.size());
        assertNotNull(glass.entry(element(a)));
        assertEquals(GlassEntry.PRIORITY_FLOAT, glass.entry(element(a)).priority);
        terminal.drop(a);
        assertEquals(0, glass.size());
    }

    @Test
    public void wearingTheSameSlabAgainDeclaresNothingNew() {
        int[] declares = {0};
        TerminalGlass counted = new TerminalGlass(glass, () -> {
            declares[0]++;
            pages.declareGlass();
        }, () -> 8);
        View a = slab();
        counted.wear(a, null);
        counted.wear(a, null);
        assertEquals(1, declares[0]);
        counted.drop(a);
        assertEquals(2, declares[0]);
    }

    @Test
    public void everyWindowsPageListsTheSameFloats() {
        View a = slab();
        terminal.wear(a, null);
        List<GlassEntry> first = new ArrayList<>();
        List<GlassEntry> second = new ArrayList<>();
        stops.pageOf(w1).declareGlass(first);
        stops.pageOf(w2).declareGlass(second);
        assertEquals(1, first.size());
        assertEquals(element(a), first.get(0).element);
        assertEquals(element(a), second.get(0).element);
    }

    @Test
    public void theFloatsStayWhicheverWindowIsInFrontAndWhileNoneIs() {
        View a = slab();
        terminal.wear(a, null);
        host.active = w2;
        stops.pageOf(w2);
        pages.declareGlass();
        assertEquals(1, glass.size());
        // A window closing leaves none in front for a moment; the others' floats keep their glass.
        host.active = null;
        pages.declareGlass();
        assertEquals(1, glass.size());
        View b = slab();
        terminal.wear(b, null);
        terminal.drop(b);
        assertEquals(1, glass.size());
    }
}
