package com.termux.app.launcher.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.glass.GlassKind;
import com.termux.app.glass.GlassService;
import com.termux.app.page.PageHost;
import com.termux.app.page.PageKind;
import com.termux.app.page.Pages;
import com.termux.app.window.FramePipeline;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** Home's glass: the widgets' and floats' entries live with the Home pages, not with the front. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class HomePageGlassTest {

    private static final class Host implements HomePageHost {
        @Override public void dismissControls() { }
        @Override public void leaving() { }
        @Override public void shown() { }
        @Override public boolean widgetBack() { return false; }
        @Override public boolean lowerFloatKeyboard() { return false; }
        @Nullable @Override public PageKind committedKind() { return null; }
        @NonNull @Override public View view() { throw new UnsupportedOperationException(); }
    }

    private GlassService glass;
    private Pages pages;
    private HomeGlass home;

    @Before
    public void setUp() {
        glass = new GlassService(new FramePipeline());
        pages = new Pages(PageHost.NONE, () -> { });
        pages.setGlass(glass);
        home = new HomeGlass(glass, pages::declareGlass, () -> 8);
    }

    private static View slab() {
        return new View(RuntimeEnvironment.getApplication());
    }

    private static String element(View slab) {
        return "widget@" + Integer.toHexString(System.identityHashCode(slab));
    }

    private HomePage put(int id) {
        HomePage page = new HomePage(id, new Host(), home);
        pages.put(id, page);
        return page;
    }

    @Test
    public void oneEntryPerWornWidgetAndFloat() {
        put(1);
        home.wearWidget(slab(), 12, null);
        home.wearWidget(slab(), 12, null);
        home.wearFloat(slab(), null);
        assertEquals(3, glass.size());
    }

    @Test
    public void dropRemovesTheEntryOnceThePageDeclaresAgain() {
        put(1);
        View a = slab();
        home.wearWidget(a, 12, null);
        assertEquals(1, glass.size());
        home.drop(a);
        assertEquals(0, glass.size());
    }

    @Test
    public void theEntriesStayThroughAnOffFrontAndGoWithTheLastHomePage() {
        put(1);
        put(2);
        home.wearWidget(slab(), 12, null);
        pages.landed(1, PageKind.HOME);
        pages.applyFront();
        pages.committed(5, PageKind.TERMINAL);
        pages.landed(5, PageKind.TERMINAL);
        pages.applyFront();
        assertEquals(1, glass.size());
        pages.remove(1);
        assertEquals(1, glass.size());
        pages.remove(2);
        assertEquals(0, glass.size());
    }

    @Test
    public void aSwitchOfHomePageIdsThatPutsFirstKeepsTheEntryRegistered() {
        put(1);
        View a = slab();
        home.wearWidget(a, 12, null);
        GlassKind before = glass.granted(element(a));
        put(2);
        pages.remove(1);
        assertNotNull(glass.entry(element(a)));
        assertEquals(before, glass.granted(element(a)));
        assertEquals(1, glass.size());
    }

    @Test
    public void aSlabWornBeforeAnyHomePageGetsItsRedressOnceOneIsPut() {
        List<GlassKind> told = new ArrayList<>();
        home.wearWidget(slab(), 12, () -> told.add(GlassKind.LIVE));
        assertEquals(0, glass.size());
        put(1);
        assertEquals(1, glass.size());
        assertEquals(1, told.size());
    }

    @Test
    public void wearingTheSameSlabTwiceKeepsOneEntryAndTheFirstRedress() {
        List<String> told = new ArrayList<>();
        put(1);
        View a = slab();
        home.wearWidget(a, 12, () -> told.add("first"));
        home.wearWidget(a, 12, () -> told.add("second"));
        assertEquals(1, glass.size());
        glass.setLiveOn(true);
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper()).idle();
        glass.setLiveOn(false);
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper()).idle();
        assertEquals(java.util.Arrays.asList("first", "first"), told);
    }

    @Test
    public void theDeclaredKindsRedressRunsWhenLiveMoves() {
        List<String> told = new ArrayList<>();
        put(1);
        home.wearWidget(slab(), 12, () -> told.add("moved"));
        glass.setLiveOn(true);
        Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper()).idle();
        assertEquals(1, told.size());
    }
}
