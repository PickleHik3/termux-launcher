package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.DisplayPagePolicy;
import com.termux.app.wall.WallPages;
import com.termux.shared.logger.Logger;

/**
 * The wall came to rest on Display page {@code pageId}: the display starts there when it is
 * neither running nor on its way ({@link DisplayPagePolicy#startsOnLanding}) and
 * the autostart setting is on; with it off the page shows its Start button instead.
 * A page bound to a Linux app also brings its app up ({@link DisplayPagePolicy#landing}): its
 * window raised, or the app run. Holds the page the drawer routed a Linux app to. Main thread only.
 */
public final class DisplayLanding {

    private static final String LOG_TAG = "DisplayLanding";

    /** What a landing asks of its window. */
    public interface Host {
        /** The wall is there and the Display is enabled: a landing has somewhere to go. */
        boolean enabled();
        /** The wall's page {@code pageId}; null when it has none such. */
        @Nullable WallPages.Page page(int pageId);
        /** The display window is wanted: its page and the server connection built. */
        void attachEmbeddedDisplay();
        /** The display's frame is built on the wall. */
        boolean hasDisplayWindow();
        /** The row re-read after the display came up or went down. */
        void syncDisplayOnRow();
        /** A Linux app runner is there to run and raise apps. */
        boolean hasLinuxRunner();
        /** Runs {@code app} on the display. */
        void runLinuxApp(@NonNull LinuxAppCatalog.LinuxApp app);
        /** Brings {@code app}'s window forward; false with no runner or no window. */
        boolean raiseLinuxApp(@NonNull LinuxAppCatalog.LinuxApp app);
        /** The display window of {@code app} on the running display, or -1. */
        int linuxWindowIdFor(@NonNull LinuxAppCatalog.LinuxApp app);
        /** The setting that starts the display when a page lands on it. */
        boolean autostartEnabled();
    }

    @NonNull private final Host mHost;
    @NonNull private final DisplayServer mDisplayServer;
    @NonNull private final DisplayPageBinding mBinding;
    /** The Display page the drawer routed a Linux app to, and when (A3c); -1 for none. */
    private int mRoutedLinuxPage = -1;
    private long mRoutedLinuxAtMs;
    /** True while a landing runs its bound app: the runner's show-display step must not slide. */
    private boolean mLandingRunsApp;

    public DisplayLanding(@NonNull Host host, @NonNull DisplayServer displayServer,
                          @NonNull DisplayPageBinding binding) {
        mHost = host;
        mDisplayServer = displayServer;
        mBinding = binding;
    }

    /** The drawer routed a Linux app to Display page {@code pageId}; the next landing there is its. */
    public void routeTo(int pageId) {
        mRoutedLinuxPage = pageId;
        mRoutedLinuxAtMs = android.os.SystemClock.uptimeMillis();
    }

    /** The page the latest route named while it still counts, -1 when it is stale or none. */
    public int freshRoutedPage() {
        boolean routed = android.os.SystemClock.uptimeMillis() - mRoutedLinuxAtMs
            <= DisplayPagePolicy.ROUTED_LANDING_MS;
        return routed ? mRoutedLinuxPage : -1;
    }

    /** True while a landing runs its bound app: the runner's show-display step must not slide. */
    public boolean landingRunsApp() {
        return mLandingRunsApp;
    }

    /** The wall came to rest on Display page {@code pageId}. */
    public void landedAt(int pageId) {
        if (!mHost.enabled()) return;
        WallPages.Page page = mHost.page(pageId);
        String binding = page == null ? null : page.binding;
        LinuxAppCatalog.LinuxApp app = null;
        if (binding != null) {
            app = mBinding.cards().find(binding);
            // Not in the cached catalog (not read yet, or installed since): one scan, on a landing
            // only, never per frame.
            if (app == null) {
                app = LinuxAppCatalog.find(
                    LinuxAppCatalog.scan(
                        LinuxAppCatalog.roots()), binding);
            }
            if (app == null) {
                Logger.logWarn(LOG_TAG, "Display page " + pageId + " is bound to " + binding
                    + ", which is gone; showing nothing new");
            }
        }
        boolean running = mDisplayServer.isRunning();
        boolean routed = DisplayPagePolicy.isRoutedLanding(pageId,
            mRoutedLinuxPage, mRoutedLinuxAtMs, android.os.SystemClock.uptimeMillis());
        // The drawer's route is answered by one landing; the next landing is the user's own.
        if (routed) mRoutedLinuxPage = -1;
        DisplayPagePolicy.Landing landing =
            DisplayPagePolicy.landing(binding, app != null, running,
                mDisplayServer.isRequested(), app != null && !app.session && running
                    && mHost.linuxWindowIdFor(app) >= 0,
                routed,
                DisplayPagePolicy.landingAutostarts(
                    mHost.autostartEnabled(),
                    mDisplayServer.ranThisLaunch()));
        switch (landing) {
            case NOTHING:
                return;
            case RAISE_APP:
                mHost.raiseLinuxApp(app);
                return;
            case RUN_APP:
                if (!mHost.hasLinuxRunner()) return;
                mHost.attachEmbeddedDisplay();
                if (!mHost.hasDisplayWindow()) return;
                mDisplayServer.setRequested(true);
                // The wall is already here: the runner's own "show the display" must not slide it.
                mLandingRunsApp = true;
                try {
                    mHost.runLinuxApp(app);
                } finally {
                    mLandingRunsApp = false;
                }
                mHost.syncDisplayOnRow();
                return;
            default:
                break;
        }
        mHost.attachEmbeddedDisplay();
        if (!mHost.hasDisplayWindow()) return;
        mDisplayServer.setRequested(true);
        // SHOW_START stops here: the window is up in its not-running state and its Start button
        // starts the display (autostart is off).
        if (landing == DisplayPagePolicy.Landing.START_DISPLAY
            && X11CliInstaller.hasKeyboardData()) mDisplayServer.start();
        mHost.syncDisplayOnRow();
    }
}
