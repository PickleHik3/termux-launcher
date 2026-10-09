package com.termux.app.x11;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

/**
 * A Linux app installed from a terminal pane has to reach the drawer without the user running
 * {@code termux-reload-settings}.
 *
 * <p>{@code refreshLinuxApps} used to end at {@code refreshAsync(null, null)}.
 * {@code LauncherAppDataProvider} has no listener list — a refresh reports only to the callback it
 * was handed — so a null one rebuilt the catalogue where nothing could see it. The drawer reads
 * the provider in {@code AppDrawerContentView.bind()}, which has already run by the time
 * {@code onDrawerOpenSettled} fires the refresh, so the new app showed up an open later.
 *
 * <p>Asserted against the source because the method is private on a 19k-line Activity whose
 * construction needs the whole launcher; what matters is the shape of the call, and that survives
 * a text check.
 */
public class LinuxAppsReachTheDrawerTest {

    @Test public void aDesktopFileChangeIsPushedToTheDrawerAndNotJustTheProvider() throws Exception {
        String body = refreshLinuxAppsBody();
        assertTrue("the drawer is told, or the new app never reaches the screen",
            body.contains("mAppDrawerController.onAppCatalogChanged()"));
        assertTrue("the dock refreshes through the path that fires the catalogue listener",
            body.contains("mSuggestionBarView.refreshAllApps(null)"));
    }

    /**
     * The provider is still refreshed when there is no suggestion bar to do it: the drawer's
     * catalogue is not the dock's, and the display can be used with the dock switched off.
     */
    @Test public void theProviderStillRefreshesWithoutASuggestionBar() throws Exception {
        String body = refreshLinuxAppsBody();
        assertTrue(body.contains("refreshAsync(null, null)"));
        assertTrue("guarded, so the bar-less case is the one that falls through to it",
            body.contains("isLauncherCatalogEnabled() && mSuggestionBarView != null"));
    }

    /** The signature check stays: a drawer open that changed nothing must not rebuild anything. */
    @Test public void anUnchangedPrefixStillCostsNothingButTheSignature() throws Exception {
        String body = refreshLinuxAppsBody();
        assertTrue(body.contains("if (!force && signature == mLinuxAppsSignature) return;"));
        assertTrue(body.indexOf("signature == mLinuxAppsSignature")
            < body.indexOf("onAppCatalogChanged"));
    }

    private static String refreshLinuxAppsBody() throws Exception {
        String activity = source("app/src/main/java/com/termux/app/TermuxActivity.java");
        int start = activity.indexOf("private void refreshLinuxApps(boolean force)");
        assertTrue("refreshLinuxApps(boolean) not found", start > 0);
        int end = activity.indexOf("private void closeAppDrawerForLinuxLaunch", start);
        assertTrue("end of refreshLinuxApps not found", end > start);
        return activity.substring(start, end);
    }

    private static String source(String relative) throws Exception {
        Path root = Paths.get(System.getProperty("user.dir"));
        Path path = root.resolve(relative);
        if (!Files.exists(path)) path = root.getParent().resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
