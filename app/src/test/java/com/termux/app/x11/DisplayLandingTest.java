package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;
import com.termux.app.wall.WallPages;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** What {@link DisplayLanding} does on a landing, with a fake host and a seeded app catalog. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class DisplayLandingTest {

    private static final int PAGE = 4;

    private static final class FakeHost implements DisplayLanding.Host {
        boolean enabled = true;
        boolean windowBuilt = true;
        boolean runner = true;
        boolean autostart;
        final List<WallPages.Page> pages = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        DisplayLanding landing;
        Boolean runsAppDuringRun;

        @Override public boolean enabled() { return enabled; }
        @Nullable @Override public WallPages.Page page(int pageId) {
            for (WallPages.Page p : pages) if (p.id == pageId) return p;
            return null;
        }
        @Override public void attachEmbeddedDisplay() { calls.add("attach"); }
        @Override public boolean hasDisplayWindow() { return windowBuilt; }
        @Override public void syncDisplayOnRow() { calls.add("sync"); }
        @Override public boolean hasLinuxRunner() { return runner; }
        @Override public void runLinuxApp(@NonNull LinuxAppCatalog.LinuxApp app) {
            calls.add("run");
            runsAppDuringRun = landing.landingRunsApp();
        }
        @Override public boolean raiseLinuxApp(@NonNull LinuxAppCatalog.LinuxApp app) {
            calls.add("raise");
            return true;
        }
        @Override public int linuxWindowIdFor(@NonNull LinuxAppCatalog.LinuxApp app) { return -1; }
        @Override public boolean autostartEnabled() { return autostart; }
    }

    private FakeHost mHost;
    private DisplayLanding mLanding;
    private LinuxAppCatalog.LinuxApp mApp;

    @Before
    public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        mHost = new FakeHost();
        DisplayServer.Host quiet = (DisplayServer.Host) Proxy.newProxyInstance(
            DisplayServer.Host.class.getClassLoader(), new Class<?>[]{DisplayServer.Host.class},
            (proxy, method, args) -> {
                Class<?> type = method.getReturnType();
                return type == boolean.class ? (Object) false : null;
            });
        DisplayServer server = new DisplayServer(activity, quiet);
        DisplayPageBinding binding = new DisplayPageBinding(activity,
            new DisplayPageBinding.Host() {
                @Nullable @Override public X11PaneFrame frame() { return null; }
                @Nullable @Override public WallPages.Page page(int pageId) { return null; }
                @NonNull @Override public List<WallPages.Page> pages() {
                    return Collections.emptyList();
                }
                @Nullable @Override public com.termux.app.wall.PaneWallLayout wall() { return null; }
                @Override public boolean isRunning() { return false; }
                @Override public boolean isGone() { return false; }
                @Override public boolean buildFrameIfMissing() { return false; }
            });
        mApp = new LinuxAppCatalog.LinuxApp("firefox.desktop", "Firefox", "firefox", "", "");
        DisplayCardCache<Drawable> cards = new DisplayCardCache<>(
            () -> Collections.singletonList(mApp), app -> null, Runnable::run, Runnable::run);
        cards.refresh(Collections.singletonList(mApp.id), null);
        binding.setCardsForTest(cards);
        mLanding = new DisplayLanding(mHost, server, binding);
        mHost.landing = mLanding;
    }

    @Test
    public void aRoutedLandingIsConsumedByTheFirstLandingOnThatPage() {
        mHost.pages.add(new WallPages.Page(PAGE, PageKind.DISPLAY, null, mApp.id));
        mLanding.routeTo(PAGE);
        assertEquals(PAGE, mLanding.freshRoutedPage());
        mLanding.landedAt(PAGE);
        // The runner owns a routed landing: nothing is run or raised, and the route is spent.
        assertTrue(mHost.calls.isEmpty());
        assertEquals(-1, mLanding.freshRoutedPage());
        mLanding.landedAt(PAGE);
        assertTrue(mHost.calls.contains("run"));
    }

    @Test
    public void aLandingRunsItsBoundAppWithLandingRunsAppSetOnlyDuringTheRun() {
        mHost.pages.add(new WallPages.Page(PAGE, PageKind.DISPLAY, null, mApp.id));
        assertFalse(mLanding.landingRunsApp());
        mLanding.landedAt(PAGE);
        assertEquals(Boolean.TRUE, mHost.runsAppDuringRun);
        assertFalse(mLanding.landingRunsApp());
        assertEquals(Arrays.asList("attach", "run", "sync"), mHost.calls);
    }

    @Test
    public void aLandingWithoutARunnerOrAWindowDoesNothingMore() {
        mHost.pages.add(new WallPages.Page(PAGE, PageKind.DISPLAY, null, mApp.id));
        mHost.runner = false;
        mLanding.landedAt(PAGE);
        assertTrue(mHost.calls.isEmpty());
        mHost.runner = true;
        mHost.windowBuilt = false;
        mLanding.landedAt(PAGE);
        assertEquals(Collections.singletonList("attach"), mHost.calls);
    }

    @Test
    public void aDisabledDisplayLandsNowhere() {
        mHost.pages.add(new WallPages.Page(PAGE, PageKind.DISPLAY, null, mApp.id));
        mHost.enabled = false;
        mLanding.landedAt(PAGE);
        assertTrue(mHost.calls.isEmpty());
    }
}
