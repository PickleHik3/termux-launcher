package com.termux.app.chrome;

import android.app.Application;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ChromeRendererTest {

    private FakeChromeSurfaces surfaces;
    private ChromeRenderer chrome;
    private View wallpaperFrame;

    @Before
    public void setUp() {
        surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        chrome = new ChromeRenderer(surfaces);
        wallpaperFrame = new View(RuntimeEnvironment.getApplication());
    }

    private static ShadowLooper mainLooper() {
        return Shadows.shadowOf(RuntimeEnvironment.getApplication().getMainLooper());
    }

    @Test
    public void manyRenderRequestsInOneMainLoopTurnCostOneApply() {
        chrome.requestSync(ChromeRenderer.SCOPE_ACCESSORY_RENDER);
        chrome.requestSync(ChromeRenderer.SCOPE_ACCESSORY_RENDER);
        chrome.requestSync(ChromeRenderer.SCOPE_ACCESSORY_RENDER);

        assertTrue(chrome.isRenderSyncPending());
        assertEquals("the render is coalesced, not run inline", 0, surfaces.applied.size());

        mainLooper().idle();

        assertEquals(1, surfaces.applied.size());
        assertEquals(1, surfaces.invariantsEnforced);
        assertFalse(chrome.isRenderSyncPending());

        // And the next turn can request again.
        chrome.requestSync(ChromeRenderer.SCOPE_ACCESSORY_RENDER);
        mainLooper().idle();
        assertEquals(2, surfaces.applied.size());
    }

    /**
     * The contract this replaced ran the apply inline, so nine call sites that fire during one
     * gesture cost nine full applies. Now they book one commit for the frame.
     */
    @Test
    public void manyApplyRequestsInOneFrameCostOneCommit() {
        chrome.requestSync(ChromeRenderer.SCOPE_APPLY_THIS_FRAME);
        chrome.requestSync(ChromeRenderer.SCOPE_APPLY_THIS_FRAME);
        chrome.requestSync(ChromeRenderer.SCOPE_APPLY_THIS_FRAME | ChromeRenderer.SCOPE_BACKDROPS);

        assertTrue(chrome.isCommitPending());
        assertEquals("the apply is coalesced, not run inline", 0, surfaces.applied.size());

        mainLooper().idle();

        assertEquals(1, surfaces.applied.size());
        assertSame(surfaces.spec, surfaces.applied.get(0));
        assertEquals("a commit is not the accessory render pass", 0, surfaces.invariantsEnforced);
        assertFalse(chrome.isCommitPending());

        // And the next frame can book again.
        chrome.requestSync(ChromeRenderer.SCOPE_APPLY_THIS_FRAME);
        mainLooper().idle();
        assertEquals(2, surfaces.applied.size());
    }

    /** The commit runs before layout, the render after it: two phases, so two passes. */
    @Test
    public void anApplyAndARenderInOneFrameAreStillTwoPasses() {
        chrome.requestSync(ChromeRenderer.SCOPE_APPLY_THIS_FRAME | ChromeRenderer.SCOPE_ACCESSORY_RENDER);
        chrome.requestSync(ChromeRenderer.SCOPE_APPLY_THIS_FRAME);
        chrome.requestSync(ChromeRenderer.SCOPE_ACCESSORY_RENDER);

        assertEquals(0, surfaces.applied.size());
        mainLooper().idle();

        assertEquals(2, surfaces.applied.size());
        assertEquals(1, surfaces.invariantsEnforced);
    }

    @Test
    public void cancellingPendingWorkDropsTheBookedCommit() {
        chrome.requestSync(ChromeRenderer.SCOPE_APPLY_THIS_FRAME);

        chrome.cancelPendingWork();
        mainLooper().idle();

        assertFalse(chrome.isCommitPending());
        assertEquals(0, surfaces.applied.size());
    }

    /** The in-place session recovery resets transient chrome; the activity stays on screen. */
    @Test
    public void cancellingThePendingRenderKeepsTheBookedCommit() {
        chrome.requestSync(ChromeRenderer.SCOPE_APPLY_THIS_FRAME | ChromeRenderer.SCOPE_ACCESSORY_RENDER);

        chrome.cancelPendingRender();
        mainLooper().idle();

        assertFalse(chrome.isRenderSyncPending());
        assertEquals("the commit lands, the render does not", 1, surfaces.applied.size());
        assertEquals(0, surfaces.invariantsEnforced);
    }

    @Test
    public void anEmptyRequestDoesNothingAtAll() {
        chrome.requestSync(0);
        mainLooper().idle();

        assertEquals(0, surfaces.applied.size());
        assertFalse(chrome.isRenderSyncPending());
        assertFalse(chrome.isCommitPending());
    }

    @Test
    public void theDockScopeDoesNotWidenToTheKeyboardsOwnCrop() {
        chrome.ledger().recordApplied(SurfaceDirtyLedger.Backdrop.ACCESSORY, 12, false,
            new android.graphics.Rect(0, 0, 1, 1));
        chrome.ledger().recordApplied(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR, 12, false,
            new android.graphics.Rect(0, 0, 1, 1));
        chrome.ledger().recordApplied(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD, 12, false,
            new android.graphics.Rect(0, 0, 1, 1));

        chrome.requestSync(ChromeRenderer.SCOPE_BACKDROPS);

        assertTrue(chrome.ledger().isDirty(SurfaceDirtyLedger.Backdrop.ACCESSORY));
        assertTrue(chrome.ledger().isDirty(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR));
        assertFalse("only the paths that touched all three may invalidate the keyboard crop",
            chrome.ledger().isDirty(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD));

        chrome.requestSync(ChromeRenderer.SCOPE_KEYBOARD_BACKDROP);
        assertTrue(chrome.ledger().isDirty(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD));
    }

    /** A rebuilt nav strip or a moved dock says so alone, without dragging the other crop along. */
    @Test
    public void eachBackdropHasAScopeOfItsOwn() {
        android.graphics.Rect rect = new android.graphics.Rect(0, 0, 1, 1);
        chrome.ledger().recordApplied(SurfaceDirtyLedger.Backdrop.ACCESSORY, 12, false, rect);
        chrome.ledger().recordApplied(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR, 12, false, rect);

        chrome.requestSync(ChromeRenderer.SCOPE_NAV_STRIP_BACKDROP);
        assertTrue(chrome.ledger().isDirty(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR));
        assertFalse(chrome.ledger().isDirty(SurfaceDirtyLedger.Backdrop.ACCESSORY));

        chrome.ledger().recordApplied(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR, 12, false, rect);
        chrome.requestSync(ChromeRenderer.SCOPE_DOCK_BACKDROP);
        assertTrue(chrome.ledger().isDirty(SurfaceDirtyLedger.Backdrop.ACCESSORY));
        assertFalse(chrome.ledger().isDirty(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR));

        assertEquals("the pair scope is exactly the two singles",
            ChromeRenderer.SCOPE_DOCK_BACKDROP | ChromeRenderer.SCOPE_NAV_STRIP_BACKDROP,
            ChromeRenderer.SCOPE_BACKDROPS);
    }

    @Test
    public void droppingTheBlurCacheAlsoInvalidatesEveryFrostCutFromIt() {
        chrome.blurCache().obtain(0, wallpaperFrame);
        chrome.ledger().clearFrostDirty();
        int clearsBefore = surfaces.cacheClearedCallbacks;

        chrome.requestSync(ChromeRenderer.SCOPE_WALLPAPER_BLUR_CACHE);

        assertEquals(0, chrome.blurCache().residentRadiiCount());
        assertTrue(chrome.ledger().isFrostDirty());
        assertEquals(clearsBefore + 1, surfaces.cacheClearedCallbacks);
    }

    @Test
    public void aRotationDropsTheCacheExactlyOnceAndTheNextFrameIsCapturedFresh() {
        Bitmap portrait = chrome.blurCache().obtain(0, wallpaperFrame);
        assertEquals(1, surfaces.captureCount);
        int clearsBefore = surfaces.cacheClearedCallbacks;

        surfaces.orientation = Configuration.ORIENTATION_LANDSCAPE;
        chrome.onConfigurationChanged();

        assertEquals("the rotation drops the frames exactly once",
            clearsBefore + 1, surfaces.cacheClearedCallbacks);
        assertEquals(0, chrome.blurCache().residentRadiiCount());
        assertEquals("dropping the cache must not capture anything by itself",
            1, surfaces.captureCount);

        // The new layout lands and the next request captures the landscape frame.
        surfaces.frameRect.set(0, 0, 200, 100);
        Bitmap landscape = chrome.blurCache().obtain(0, wallpaperFrame);

        assertNotSame("the outgoing orientation's frame must not be reused", portrait, landscape);
        assertEquals(2, surfaces.captureCount);
        assertEquals(200, chrome.blurCache().frameRectWidth());
    }

    /** A keyboard or navigation change arrives through the same callback and moves nothing. */
    @Test
    public void aConfigurationChangeThatKeepsTheOrientationKeepsTheFrames() {
        chrome.blurCache().obtain(0, wallpaperFrame);
        int clearsBefore = surfaces.cacheClearedCallbacks;

        chrome.onConfigurationChanged();

        assertEquals(1, chrome.blurCache().residentRadiiCount());
        assertEquals(clearsBefore, surfaces.cacheClearedCallbacks);
        assertSame("the same frame is still the one handed out",
            chrome.blurCache().obtain(0, wallpaperFrame), chrome.blurCache().obtain(0, wallpaperFrame));
        assertEquals(1, surfaces.captureCount);
    }

    @Test
    public void aNewWallpaperDropsTheFramesAndSchedulesOneRender() {
        chrome.blurCache().obtain(0, wallpaperFrame);

        chrome.onWallpaperChanged();

        assertEquals(0, chrome.blurCache().residentRadiiCount());
        assertTrue(chrome.ledger().isDirty(SurfaceDirtyLedger.Backdrop.ACCESSORY));
        assertTrue(chrome.ledger().isDirty(SurfaceDirtyLedger.Backdrop.DECOR_NAV_BAR));
        assertEquals(0, surfaces.applied.size());

        mainLooper().idle();
        assertEquals(1, surfaces.applied.size());
    }

    @Test
    public void aMemoryTrimReleasesTheFramesAndMarksEverySurfaceDirty() {
        chrome.blurCache().obtain(0, wallpaperFrame);
        chrome.ledger().recordApplied(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD, 12, false,
            new android.graphics.Rect(0, 0, 1, 1));

        chrome.onTrimMemory();

        assertEquals(0, chrome.blurCache().residentRadiiCount());
        for (SurfaceDirtyLedger.Backdrop backdrop : SurfaceDirtyLedger.Backdrop.values())
            assertTrue(backdrop.name(), chrome.ledger().isDirty(backdrop));
        assertEquals("a trim rebuilds on demand rather than rendering on the way out",
            0, surfaces.applied.size());
    }

    @Test
    public void theBlurRecoveryRetryReRendersWhenABackdropIsMissing() {
        surfaces.blurHealthy = false;
        chrome.ledger().recordApplied(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD, 12, false,
            new android.graphics.Rect(0, 0, 1, 1));

        chrome.requestSync(ChromeRenderer.SCOPE_BLUR_HEALTH);
        assertEquals(0, surfaces.applied.size());

        mainLooper().idleFor(java.time.Duration.ofMillis(150));

        assertTrue("an unhealthy blur invalidates every backdrop",
            chrome.ledger().isDirty(SurfaceDirtyLedger.Backdrop.IN_APP_KEYBOARD));
        assertEquals(1, surfaces.applied.size());
    }

    @Test
    public void blurHealthIsNotPolledWhileTheChromeIsNotOnScreen() {
        surfaces.visible = false;
        surfaces.blurHealthy = false;

        chrome.requestSync(ChromeRenderer.SCOPE_BLUR_HEALTH);
        mainLooper().idleFor(java.time.Duration.ofMillis(500));

        assertEquals(0, surfaces.applied.size());
    }

    @Test
    public void blurHealthIsNotPolledWhileTheDockIsNotBlurred() {
        surfaces.spec = new ChromeSpec(true, false, 0, false, true, false, true, 1f, 0);
        surfaces.blurHealthy = false;

        chrome.requestSync(ChromeRenderer.SCOPE_BLUR_HEALTH);
        mainLooper().idleFor(java.time.Duration.ofMillis(500));

        assertEquals(0, surfaces.applied.size());
    }

    @Test
    public void cancellingPendingWorkDropsTheCoalescedRender() {
        chrome.requestSync(ChromeRenderer.SCOPE_ACCESSORY_RENDER);

        chrome.cancelPendingWork();
        mainLooper().idle();

        assertFalse(chrome.isRenderSyncPending());
        assertEquals(0, surfaces.applied.size());
    }

    @Test
    public void theTopPaneFrostRadiusFollowsTheDockAndFallsBackToTheStatusSlider() {
        surfaces.dockBlurRadiusDp = 18;
        surfaces.statusBlurRadiusDp = 6;
        assertEquals(18, chrome.frost().topGlassFrostRadiusDp());

        surfaces.dockBlurRadiusDp = 0;
        assertEquals(6, chrome.frost().topGlassFrostRadiusDp());
    }

    @Test
    public void aTopPaneFrostPassWithNoInflatedViewsStillRidesTheTerminalGlass() {
        chrome.requestSync(ChromeRenderer.SCOPE_TOP_PANE_FROST);

        assertEquals(1, surfaces.terminalGlassFrostUpdates);
    }
}
