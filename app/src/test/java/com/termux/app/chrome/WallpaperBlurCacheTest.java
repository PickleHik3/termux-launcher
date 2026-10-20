package com.termux.app.chrome;

import android.app.Application;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import com.termux.app.terminal.PaneGlassBackdropView;

import java.util.ArrayDeque;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WallpaperBlurCacheTest {

    private FakeWallpaperBlurSource source;
    private WallpaperBlurCache cache;
    private View wallpaperFrame;

    @Before
    public void setUp() {
        source = new FakeWallpaperBlurSource();
        cache = new WallpaperBlurCache(source);
        wallpaperFrame = new View(RuntimeEnvironment.getApplication());
    }

    @Test
    public void aResidentRadiusIsReusedInsteadOfReblurred() {
        Bitmap first = cache.obtain(12, wallpaperFrame);
        Bitmap again = cache.obtain(12, wallpaperFrame);

        assertSame(first, again);
        assertEquals(1, source.captureCount);
    }

    @Test
    public void independentlyTunedRadiiEachKeepTheirOwnFrame() {
        Bitmap dock = cache.obtain(12, wallpaperFrame);
        Bitmap status = cache.obtain(30, wallpaperFrame);

        assertNotSame(dock, status);
        assertEquals(2, source.captureCount);
        // Alternating between two tuned radii must not re-blur either of them.
        assertSame(dock, cache.obtain(12, wallpaperFrame));
        assertSame(status, cache.obtain(30, wallpaperFrame));
        assertEquals(2, source.captureCount);
    }

    @Test
    public void theLeastRecentlyUsedRadiusIsEvictedAtTheCap() {
        for (int radius : new int[] {4, 8, 12, 16, 20, 24}) cache.obtain(radius, wallpaperFrame);
        assertEquals(WallpaperBlurCache.MAX_CACHED_WALLPAPER_BLUR_RADII, cache.residentRadiiCount());

        // Touch the oldest so recency, not insertion order, decides what goes.
        cache.obtain(4, wallpaperFrame);
        cache.obtain(28, wallpaperFrame);

        assertEquals(WallpaperBlurCache.MAX_CACHED_WALLPAPER_BLUR_RADII, cache.residentRadiiCount());
        assertFalse("the least recently used radius should be gone", cache.hasRadius(8));
        for (int radius : new int[] {4, 12, 16, 20, 24, 28}) assertTrue(cache.hasRadius(radius));
    }

    @Test
    public void theByteBudgetEvictsBeforeTheRadiusCapDoes() {
        Bitmap probe = cache.obtain(4, wallpaperFrame);
        long frameBytes = probe.getAllocationByteCount();
        // Room for one frame and a half: the second frame in must push the first out.
        WallpaperBlurCache budgeted = new WallpaperBlurCache(source, null, frameBytes * 3 / 2);

        budgeted.obtain(4, wallpaperFrame);
        budgeted.obtain(8, wallpaperFrame);

        assertEquals(1, budgeted.residentRadiiCount());
        assertFalse(budgeted.hasRadius(4));
        assertTrue("the frame just cut always stays, however large", budgeted.hasRadius(8));
        assertTrue(budgeted.residentBytes() <= frameBytes * 3 / 2);
    }

    @Test
    public void anEvictedFrameSomeViewIsStillDrawingIsDroppedWithoutRecycling() {
        Bitmap doomed = cache.obtain(8, wallpaperFrame);
        source.inUse.add(doomed);
        for (int radius : new int[] {12, 16, 20, 24, 28, 32}) cache.obtain(radius, wallpaperFrame);

        assertFalse(cache.hasRadius(8));
        assertFalse("recycling a frame a view holds crashes its next draw", doomed.isRecycled());
    }

    @Test
    public void aNewSystemWallpaperInvalidatesEveryResidentRadius() {
        cache.obtain(8, wallpaperFrame);
        cache.obtain(12, wallpaperFrame);
        int clearsBefore = source.clearedCount;
        source.systemWallpaperId = 8;

        cache.obtain(8, wallpaperFrame);

        assertEquals(1, cache.residentRadiiCount());
        assertFalse(cache.hasRadius(12));
        assertEquals("a changed wallpaper drops the whole cache once",
            clearsBefore + 1, source.clearedCount);
    }

    @Test
    public void switchingToTheManagedWallpaperSourceInvalidatesTheCache() {
        Bitmap system = cache.obtain(8, wallpaperFrame);
        source.managedSource = true;

        Bitmap managed = cache.obtain(8, wallpaperFrame);

        assertNotSame(system, managed);
        assertEquals(2, source.captureCount);
    }

    /**
     * The shipped rotation race: {@code onConfigurationChanged} arrives before the window is
     * re-laid out, so a crop taken during that pass records the outgoing orientation's frame rect.
     * With only the rect to compare, that stale frame matched itself forever after — landscape kept
     * showing a mismatched, brighter wallpaper region with a hard seam at the pane's left edge.
     */
    @Test
    public void aFrameCapturedBeforeTheNewLayoutIsNotReusedAfterIt() {
        Bitmap portrait = cache.obtain(8, wallpaperFrame);

        // The configuration flips first; the window has not been re-laid out, so the frame rect the
        // source reports is still the portrait one.
        source.orientation = Configuration.ORIENTATION_LANDSCAPE;
        Bitmap capturedMidRotation = cache.obtain(8, wallpaperFrame);
        assertNotSame("the portrait frame must not survive the orientation change",
            portrait, capturedMidRotation);

        // Now the new layout lands and the frame rect finally describes landscape.
        source.frameRect.set(0, 0, 200, 100);
        Bitmap landscape = cache.obtain(8, wallpaperFrame);

        assertNotSame("the mid-rotation frame must not match itself once layout catches up",
            capturedMidRotation, landscape);
        assertEquals(200, cache.frameRectWidth());
        assertEquals(100, cache.frameRectHeight());
        assertEquals(3, source.captureCount);
    }

    @Test
    public void theFrameRectDescribesTheScreenWhateverSizeTheFrameIsHeldAt() {
        // The renderer hands back the frame at the size it blurred it at; the rect the cache
        // records is still the screen rect it was captured for, which is what every surface aims
        // its shader from.
        source.blurScale = 4;
        Bitmap frame = cache.obtain(8, wallpaperFrame);
        Rect frameRect = new Rect();
        cache.copyFrameRect(frameRect);

        assertEquals(25, frame.getWidth());
        assertEquals(50, frame.getHeight());
        assertEquals(new Rect(0, 0, 100, 200), frameRect);
        assertSame(frame, cache.obtain(8, wallpaperFrame));
    }

    @Test
    public void clearingEmptiesTheCacheAndTellsTheOutsideWorld() {
        Bitmap frame = cache.obtain(8, wallpaperFrame);
        int clearsBefore = source.clearedCount;

        cache.clear();

        assertEquals(0, cache.residentRadiiCount());
        assertFalse("a frame was handed out: dropped, never recycled", frame.isRecycled());
        assertEquals(0, cache.frameRectWidth());
        assertEquals(clearsBefore + 1, source.clearedCount);
        // And the next request captures again rather than trusting the emptied identity.
        cache.obtain(8, wallpaperFrame);
        assertEquals(2, source.captureCount);
    }

    // ------------------------------------------------------------- crossfade tagging

    @Test
    public void aPlainClearNeverTagsAnyRadiusForCrossfade() {
        cache.obtain(8, wallpaperFrame);
        cache.obtain(12, wallpaperFrame);

        cache.clear();
        cache.obtain(8, wallpaperFrame);

        assertFalse("a rotation or a radius change swaps outright, never fades",
            cache.isCrossfadedRadius(8));
    }

    @Test
    public void aWallpaperChangeClearTagsEveryRadiusItDisplaced() {
        cache.obtain(8, wallpaperFrame);
        cache.obtain(12, wallpaperFrame);

        cache.clearForWallpaperChange();
        assertFalse("nothing has refilled yet", cache.isCrossfadedRadius(8));

        cache.obtain(8, wallpaperFrame);
        assertTrue("the first surface to refill a displaced radius crossfades into it",
            cache.isCrossfadedRadius(8));
        // The other displaced radius is untouched until something asks for it too.
        assertFalse(cache.isCrossfadedRadius(12));
        cache.obtain(12, wallpaperFrame);
        assertTrue(cache.isCrossfadedRadius(12));
    }

    @Test
    public void aRadiusNeverResidentBeforeIsNeverTaggedForCrossfade() {
        cache.obtain(8, wallpaperFrame);

        cache.clearForWallpaperChange();
        // 12 was never resident, so a wallpaper change never promised it a crossfade — a radius the
        // editor happens to settle on right after is a plain new key, not a fade.
        cache.obtain(12, wallpaperFrame);

        assertFalse(cache.isCrossfadedRadius(12));
    }

    @Test
    public void aSecondWallpaperChangeBeforeARadiusRefillsStillCrossfadesIt() {
        cache.obtain(8, wallpaperFrame);

        cache.clearForWallpaperChange();
        // A second wallpaper change lands before 8 was ever refilled. The surface is still showing
        // its last good frame, so the frame that finally lands fades in over it all the same.
        cache.clearForWallpaperChange();
        cache.obtain(8, wallpaperFrame);

        assertTrue("the later clear still tags it, from its own moment on",
            cache.isCrossfadedRadius(8));
    }

    @Test
    public void refillingForAnUnrelatedReasonDropsTheCrossfadeTag() {
        cache.obtain(8, wallpaperFrame);
        cache.clearForWallpaperChange();
        cache.obtain(8, wallpaperFrame);
        assertTrue(cache.isCrossfadedRadius(8));

        // A later plain clear (a rotation, a radius change) and refill is not a wallpaper change;
        // the stale tag from the earlier crossfade must not leak into it.
        cache.clear();
        cache.obtain(8, wallpaperFrame);

        assertFalse(cache.isCrossfadedRadius(8));
    }

    // ------------------------------------------------------------- held drags and the capture

    private final ArrayDeque<Runnable> worker = new ArrayDeque<>();
    private int framesReady;

    /** A cache with a worker the test drains by hand, as the launcher's runs off the main thread. */
    private WallpaperBlurCache workerCache() {
        return new WallpaperBlurCache(source, null,
            WallpaperBlurCache.DEFAULT_MAX_CACHED_WALLPAPER_BLUR_BYTES, worker::add,
            Runnable::run, () -> framesReady++);
    }

    private void runWorker() {
        while (!worker.isEmpty()) worker.poll().run();
    }

    /**
     * What a Glass drag cost before the radius was held: every whole dp the thumb crossed was a
     * new radius, so a capture and a blur, per tick. The figure the held drag below is measured
     * against.
     */
    @Test
    public void anUnheldGlassDragCapturesAndBlursOnEveryTick() {
        WallpaperBlurCache drag = workerCache();
        drag.obtain(12, wallpaperFrame);
        runWorker();
        int capturesBefore = drag.capturesBegun();
        int blursBefore = drag.blursRun();

        for (int radius = 13; radius <= 32; radius++) {
            drag.obtain(radius, wallpaperFrame);
            runWorker();
        }

        assertEquals(20, drag.capturesBegun() - capturesBefore);
        assertEquals(20, drag.blursRun() - blursBefore);
        assertFalse("the six-slot LRU lost the drag's own start radius", drag.hasRadius(12));
    }

    @Test
    public void aHeldGlassDragBakesNothingUntilReleaseAndOnceAfter() {
        WallpaperBlurCache drag = workerCache();
        drag.retainCapture(true);
        for (int radius : new int[] {0, 12, 40}) drag.obtain(radius, wallpaperFrame);
        runWorker();
        Bitmap start = drag.obtain(12, wallpaperFrame);
        int capturesBefore = drag.capturesBegun();
        int blursBefore = drag.blursRun();
        int readyBefore = framesReady;

        drag.holdFrameBakes(true);
        for (int radius = 13; radius <= 32; radius++) {
            assertSame("a held tick keeps the frame the drag began with", start,
                drag.obtain(radius, wallpaperFrame));
            runWorker();
        }

        assertEquals("no capture during the drag", capturesBefore, drag.capturesBegun());
        assertEquals("no blur during the drag", blursBefore, drag.blursRun());
        assertTrue(worker.isEmpty());
        assertEquals(3, drag.residentRadiiCount());
        for (int radius : new int[] {0, 12, 40}) assertTrue(drag.hasRadius(radius));

        drag.holdFrameBakes(false);
        assertEquals("the release sends every surface back for its real radius",
            readyBefore + 1, framesReady);
        // Every surface the drag moved asks for the radius it settled on: one job between them.
        assertNull(drag.obtain(32, wallpaperFrame));
        assertNull(drag.obtain(32, wallpaperFrame));
        runWorker();

        assertEquals("exactly one blur after the release", blursBefore + 1, drag.blursRun());
        assertEquals("the retained capture is blurred again, not re-read",
            capturesBefore, drag.capturesBegun());
        assertTrue(drag.hasRadius(32));
        for (int radius : new int[] {0, 12, 40}) assertTrue(drag.hasRadius(radius));
    }

    @Test
    public void aHeldTickPrefersANearerResidentFrameOverTheDragsChain() {
        Bitmap twelve = cache.obtain(12, wallpaperFrame);
        Bitmap twenty = cache.obtain(20, wallpaperFrame);
        cache.holdFrameBakes(true);

        assertSame(twelve, cache.obtain(13, wallpaperFrame));
        assertSame("the drag's chain beats a resident as near as 12 itself", twelve,
            cache.obtain(16, wallpaperFrame));
        assertSame(twelve, cache.obtain(17, wallpaperFrame));
        assertSame(twelve, cache.obtain(18, wallpaperFrame));
        assertSame("a tie stays with the frame the drag began from", twelve,
            cache.obtain(19, wallpaperFrame));
        assertSame("a resident strictly nearer wins", twenty, cache.obtain(22, wallpaperFrame));
        assertSame("an exact resident radius is its own frame", twenty,
            cache.obtain(20, wallpaperFrame));
        assertEquals(2, source.captureCount);
    }

    @Test
    public void aReleaseWithNoStandInServedAsksForNoRender() {
        WallpaperBlurCache drag = workerCache();
        drag.obtain(12, wallpaperFrame);
        runWorker();
        int readyBefore = framesReady;

        drag.holdFrameBakes(true);
        drag.obtain(12, wallpaperFrame);
        drag.holdFrameBakes(false);

        assertEquals(readyBefore, framesReady);
    }

    @Test
    public void aTintReleaseReBakesFromTheRetainedCapture() {
        cache.retainCapture(true);
        for (int radius : new int[] {0, 8, 12}) cache.obtain(radius, wallpaperFrame);
        assertEquals(1, cache.capturesBegun());
        assertTrue(cache.holdsCapture());

        source.frameTint = GlassFrameTint.of(60, 10, 0xFF4F378B, 0xFFD0BCFF);
        assertTrue(source.frameTint != GlassFrameTint.NONE);
        Bitmap eight = cache.obtain(8, wallpaperFrame);
        Bitmap twelve = cache.obtain(12, wallpaperFrame);

        assertEquals("a re-bake reads no wallpaper", 1, cache.capturesBegun());
        assertEquals(1, source.captureCount);
        assertFalse(eight.isRecycled());
        assertFalse(twelve.isRecycled());
        assertFalse("the capture survives the re-bake", source.captured.get(0).isRecycled());
    }

    @Test
    public void aVibrancyReleaseReBakesFromTheRetainedCapture() {
        cache.retainCapture(true);
        cache.obtain(8, wallpaperFrame);
        cache.obtain(12, wallpaperFrame);

        source.vibrancyPercent = 150;
        cache.obtain(8, wallpaperFrame);
        cache.obtain(12, wallpaperFrame);

        assertEquals(1, source.captureCount);
        assertEquals(4, cache.blursRun());
    }

    @Test
    public void withoutRetentionACaptureLivesOnlyAsLongAsItsJob() {
        cache.obtain(8, wallpaperFrame);

        assertFalse(cache.holdsCapture());
        assertTrue(source.captured.get(0).isRecycled());
    }

    @Test
    public void radiiAskedForWhileACaptureIsInFlightShareIt() {
        WallpaperBlurCache shared = workerCache();
        shared.obtain(8, wallpaperFrame);
        shared.obtain(12, wallpaperFrame);
        shared.obtain(20, wallpaperFrame);
        assertEquals(3, worker.size());

        runWorker();

        assertEquals("one capture for the three radii", 1, source.captureCount);
        assertEquals(3, shared.residentRadiiCount());
        assertTrue("released once no job reads it", source.captured.get(0).isRecycled());
    }

    @Test
    public void aWallpaperChangeDropsTheRetainedCapture() {
        cache.retainCapture(true);
        cache.obtain(8, wallpaperFrame);
        Bitmap capture = source.captured.get(0);

        cache.clearForWallpaperChange();

        assertFalse(cache.holdsCapture());
        assertTrue(capture.isRecycled());
        cache.obtain(8, wallpaperFrame);
        assertEquals(2, source.captureCount);
    }

    @Test
    public void aClearOrTrimDropsTheRetainedCapture() {
        cache.retainCapture(true);
        cache.obtain(8, wallpaperFrame);
        Bitmap capture = source.captured.get(0);

        cache.clear();

        assertFalse(cache.holdsCapture());
        assertTrue(capture.isRecycled());
    }

    @Test
    public void aMovedSourceTakesANewCaptureEvenWhileRetained() {
        cache.retainCapture(true);
        cache.obtain(8, wallpaperFrame);
        source.systemWallpaperId = 9;

        cache.obtain(8, wallpaperFrame);

        assertEquals(2, source.captureCount);
        assertTrue(source.captured.get(0).isRecycled());
    }

    @Test
    public void radiusZeroIsTheCaptureAndOutlivesItsRelease() {
        cache.retainCapture(true);
        Bitmap wall = cache.obtain(0, wallpaperFrame);
        assertSame(source.captured.get(0), wall);

        cache.retainCapture(false);

        assertFalse(cache.holdsCapture());
        assertFalse("a resident frame is never recycled with the capture", wall.isRecycled());
        assertSame(wall, cache.obtain(0, wallpaperFrame));
    }

    @Test
    public void aCaptureAJobStillReadsIsNotRecycledByAClear() {
        WallpaperBlurCache inFlight = workerCache();
        inFlight.retainCapture(true);
        inFlight.obtain(8, wallpaperFrame);
        Bitmap capture = source.captured.get(0);

        inFlight.clear();
        assertFalse("the worker has not read it yet", capture.isRecycled());

        runWorker();
        assertTrue(capture.isRecycled());
        assertEquals(0, inFlight.residentRadiiCount());
    }

    @Test
    public void endingRetentionReleasesTheCapture() {
        cache.retainCapture(true);
        cache.obtain(8, wallpaperFrame);
        Bitmap capture = source.captured.get(0);
        assertFalse(capture.isRecycled());

        cache.retainCapture(false);

        assertFalse(cache.holdsCapture());
        assertTrue(capture.isRecycled());
    }

    // ------------------------------------------------------------- frames a pane still holds

    private static final Rect PANE_FRAME_RECT = new Rect(0, 0, 100, 200);

    /**
     * Pong, 1.1.0-pre.5: setting a wallpaper from the picker crashed in {@code BitmapShader}. An
     * X11 pane's corner mask holds the wall-behind frame, radius 0, which is the capture itself.
     * The wallpaper change cleared the cache while a job still read that capture, the activity's
     * shadow of the frame was dropped with the clear, and the job's landing then recycled the
     * capture under the pane; the next dress kept the recycled frame and wrapped it in a shader.
     */
    @Test
    public void aWallpaperChangeNeverRecyclesTheWallBehindFrameAPaneHolds() {
        WallpaperBlurCache shared = workerCache();
        shared.retainCapture(true);   // the Look editor is open
        shared.obtain(0, wallpaperFrame);
        runWorker();
        Bitmap wall = shared.obtain(0, wallpaperFrame);
        assertSame(source.captured.get(0), wall);
        PaneGlassBackdropView mask = new PaneGlassBackdropView(RuntimeEnvironment.getApplication());
        mask.setGlass(wall, PANE_FRAME_RECT, 0x40000000, null, 0, 0f, null);

        assertNull(shared.obtain(12, wallpaperFrame));   // a job now reads the capture
        shared.clearForWallpaperChange();
        runWorker();                                       // its stale result lands and settles it

        assertFalse("a pane still draws it", wall.isRecycled());
        // The style pass that follows: nothing is resident yet, so the pane keeps what it has.
        mask.setGlass(shared.obtain(0, wallpaperFrame), PANE_FRAME_RECT, 0x50000000, null, 0, 0f,
            null, true);
        assertSame(wall, mask.heldFrame());
        runWorker();
        Bitmap fresh = shared.obtain(0, wallpaperFrame);
        assertNotSame(wall, fresh);
        mask.setGlass(fresh, PANE_FRAME_RECT, 0x50000000, null, 0, 0f, null, true);
        assertSame("the pane gets the new wallpaper's frame", fresh, mask.heldFrame());
        assertFalse(fresh.isRecycled());
    }

    @Test
    public void aClearNeverRecyclesAFrameItHandedOut() {
        WallpaperBlurCache shared = workerCache();
        for (int radius : new int[] {0, 8, 12}) shared.obtain(radius, wallpaperFrame);
        runWorker();
        Bitmap[] frames = new Bitmap[3];
        int[] radii = {0, 8, 12};
        for (int i = 0; i < radii.length; i++) frames[i] = shared.obtain(radii[i], wallpaperFrame);

        shared.clear();
        runWorker();

        for (Bitmap frame : frames) assertFalse(frame.isRecycled());
    }

    @Test
    public void anEvictedFrameIsDroppedNeverRecycled() {
        Bitmap first = cache.obtain(4, wallpaperFrame);
        for (int radius : new int[] {8, 12, 16, 20, 24, 28}) cache.obtain(radius, wallpaperFrame);

        assertFalse(cache.hasRadius(4));
        assertFalse(first.isRecycled());
    }

    /** Defence: whoever recycles a frame, the pane's next dress goes tint-only rather than throwing. */
    @Test
    public void aPaneWhoseHeldFrameWasRecycledDropsItOnTheNextDress() {
        PaneGlassBackdropView pane = new PaneGlassBackdropView(RuntimeEnvironment.getApplication());
        Bitmap frame = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888);
        pane.setGlass(frame, PANE_FRAME_RECT, 0x40000000, null, 0, 0f, null);
        frame.recycle();

        pane.setGlass(null, PANE_FRAME_RECT, 0x50000000, null, 0, 0f, null, true);

        assertNull(pane.heldFrame());
        Bitmap next = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888);
        pane.setGlass(next, PANE_FRAME_RECT, 0x50000000, null, 0, 0f, null, true);
        assertSame(next, pane.heldFrame());
    }

    // ------------------------------------------------------------- loans a capture borrows

    @Test
    public void aJobThatRanGivesItsLoanBackOnce() {
        source.borrowing = true;
        WallpaperBlurCache shared = workerCache();
        shared.obtain(8, wallpaperFrame);
        shared.obtain(12, wallpaperFrame);
        assertEquals(1, source.loansOut);

        runWorker();

        assertEquals(0, source.loansOut);
        assertEquals(1, source.loansReturned);
        assertEquals(2, shared.residentRadiiCount());
    }

    @Test
    public void aRejectedJobGivesItsLoanBack() {
        source.borrowing = true;
        WallpaperBlurCache rejecting = new WallpaperBlurCache(source, null,
            WallpaperBlurCache.DEFAULT_MAX_CACHED_WALLPAPER_BLUR_BYTES,
            runnable -> { throw new RejectedExecutionException(); }, Runnable::run, () -> { });

        assertNull(rejecting.obtain(8, wallpaperFrame));

        assertEquals(0, source.loansOut);
        assertEquals(1, source.loansReturned);
        assertFalse(rejecting.isPending(8));
    }

    @Test
    public void aJobTheShutDownWorkerNeverRanGivesItsLoanBackOnce() {
        source.borrowing = true;
        WallpaperBlurCache dying = workerCache();
        dying.obtain(8, wallpaperFrame);
        dying.obtain(12, wallpaperFrame);
        assertEquals(1, source.loansOut);

        // What the renderer's onDestroy does: shutdownNow drops the queue, then the cache is told.
        dying.abandonQueuedJobs();
        dying.clear();

        assertEquals(0, source.loansOut);
        assertEquals(1, source.loansReturned);
        // A queued job that ran after all reads nothing and gives nothing back twice.
        runWorker();
        assertEquals(1, source.loansReturned);
        assertEquals(0, dying.residentRadiiCount());
    }
}
