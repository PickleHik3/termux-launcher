package com.termux.app.terminal.inappkeyboard;

import android.app.Application;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.ChromeSpec;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins the in-app keyboard's reveal protocol on a fake {@link
 * KeyboardGeometryChoreographer.Surface}: the ordering that keeps a blurred glass keyboard from
 * flashing base-colour glass on its first frame, the fail-safe that stops the gate from wedging the
 * window, the close-seam cover, and the system-IME inset gate.
 *
 * <p>The fake's {@code applyChromeSpec} calls back into
 * {@link KeyboardGeometryChoreographer#completePendingOpenReveal(ChromeSpec)} and
 * {@link KeyboardGeometryChoreographer#completePendingCloseGeometry(ChromeSpec)} exactly where
 * {@code TermuxActivity.applyChromeSpec()} does, so the gates here settle the same way they do on a
 * device — with a recorded call log instead of a display.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class KeyboardGeometryChoreographerTest {

    private FakeSurface mSurface;
    private KeyboardGeometryChoreographer mChoreographer;

    @Before
    public void setUp() {
        mSurface = new FakeSurface(RuntimeEnvironment.getApplication());
        mChoreographer = new KeyboardGeometryChoreographer(mSurface);
    }

    // ------------------------------------------------------------ deferral predicate

    @Test
    public void deferralNeedsAFreshOpenOfBlurredGlassWithoutItsBackdrop() {
        assertTrue(KeyboardGeometryChoreographer.shouldDeferReveal(true, true, true, false));
        // A backdrop that is already installed, a re-show that never went GONE, an opaque keyboard
        // and a blur-less one each have nothing to wait for.
        assertFalse(KeyboardGeometryChoreographer.shouldDeferReveal(true, true, true, true));
        assertFalse(KeyboardGeometryChoreographer.shouldDeferReveal(false, true, true, false));
        assertFalse(KeyboardGeometryChoreographer.shouldDeferReveal(true, false, true, false));
        assertFalse(KeyboardGeometryChoreographer.shouldDeferReveal(true, true, false, false));
    }

    // ------------------------------------------------------------------- open reveal

    @Test
    public void blurredGlassOpenLaysOutInvisibleAndPaintsTheSurfaceBeforeAnyKeyIsVisible() {
        mSurface.glassSurface = true;
        mSurface.blurEnabled = true;
        mSurface.backdropReady = false;

        mChoreographer.onVisibilityRequested(true);

        // INVISIBLE participates in destination layout without allowing a draw.
        assertEquals(View.INVISIBLE, mSurface.keyboardContainer.getVisibility());
        assertTrue(mChoreographer.isOpenRevealPending());
        assertNotNull("a deferred open must arm the pre-draw gate",
            mChoreographer.openPreDrawListener());
        // Crops are invalidated, then the spec is built, then the keyboard surface is painted —
        // all strictly before anything can become visible.
        assertEquals(List.of("invalidateTransitionCrops", "buildChromeSpec",
                "keyboardBackdropReady", "keyboardGlassSurface", "applyKeyboardSurfaceState"),
            mSurface.log);
        // And the backstop is armed so the keyboard cannot stay invisible forever.
        assertEquals(1, mSurface.posted.size());
        assertEquals(KeyboardGeometryChoreographer.OPEN_REVEAL_BACKSTOP_MS,
            mSurface.posted.get(0).delayMs);
    }

    @Test
    public void revealWaitsForTheDestinationBackdropAndThenHappensInOnePass() {
        mSurface.glassSurface = true;
        mSurface.blurEnabled = true;
        mSurface.backdropReady = false;
        mChoreographer.onVisibilityRequested(true);

        mChoreographer.completePendingOpenReveal(spec(true));
        assertEquals("a render pass without the crop must not reveal keys",
            View.INVISIBLE, mSurface.keyboardContainer.getVisibility());
        assertTrue(mChoreographer.isOpenRevealPending());

        mSurface.backdropReady = true;
        mChoreographer.completePendingOpenReveal(spec(true));

        assertEquals(View.VISIBLE, mSurface.keyboardContainer.getVisibility());
        assertFalse(mChoreographer.isOpenRevealPending());
        assertNull("the gate must be released with the reveal",
            mChoreographer.openPreDrawListener());
    }

    @Test
    public void openGateBlocksTheFrameAndReRendersUntilTheBackdropLands() {
        mSurface.glassSurface = true;
        mSurface.blurEnabled = true;
        mSurface.backdropReady = false;
        mChoreographer.onVisibilityRequested(true);
        ViewTreeObserver.OnPreDrawListener gate = mChoreographer.openPreDrawListener();
        assertNotNull(gate);
        mSurface.log.clear();

        assertFalse("a frame with the stale dock-only crop must not be allowed to draw",
            gate.onPreDraw());
        assertEquals(1, mChoreographer.openRevealBlockedFrames());
        // The gate refreshes the spec synchronously — a posted render would let the stale crop draw.
        assertTrue(mSurface.log.contains("applyChromeSpec"));
        assertTrue(mSurface.log.contains("requestAccessoryRenderSync"));

        mSurface.backdropReady = true;
        assertTrue(gate.onPreDraw());
        assertEquals(View.VISIBLE, mSurface.keyboardContainer.getVisibility());
        assertFalse(mChoreographer.isOpenRevealPending());
        assertNull(mChoreographer.openPreDrawListener());
        assertEquals("no backstop may outlive the gate", 0, mSurface.posted.size());
    }

    @Test
    public void openGateRevealsAnywayOnceItHasHeldTheCapNumberOfFrames() {
        mSurface.glassSurface = true;
        mSurface.blurEnabled = true;
        mSurface.backdropReady = false;
        mChoreographer.onVisibilityRequested(true);
        ViewTreeObserver.OnPreDrawListener gate = mChoreographer.openPreDrawListener();
        assertNotNull(gate);

        for (int frame = 1; frame < KeyboardGeometryChoreographer.MAX_OPEN_REVEAL_BLOCKED_FRAMES;
             frame++) {
            assertFalse("frame " + frame + " must still be held", gate.onPreDraw());
            assertEquals(frame, mChoreographer.openRevealBlockedFrames());
            assertEquals(View.INVISIBLE, mSurface.keyboardContainer.getVisibility());
        }

        // Worst case is the old one-frame crop mismatch, never a frozen window.
        assertTrue(gate.onPreDraw());
        assertEquals(View.VISIBLE, mSurface.keyboardContainer.getVisibility());
        assertFalse(mChoreographer.isOpenRevealPending());
        assertNull(mChoreographer.openPreDrawListener());
    }

    @Test
    public void backstopRevealsTheKeyboardWhenNoFrameEverDraws() {
        mSurface.glassSurface = true;
        mSurface.blurEnabled = true;
        mSurface.backdropReady = false;
        mChoreographer.onVisibilityRequested(true);
        assertEquals(View.INVISIBLE, mSurface.keyboardContainer.getVisibility());

        mSurface.runPosted();

        assertEquals(View.VISIBLE, mSurface.keyboardContainer.getVisibility());
        assertFalse(mChoreographer.isOpenRevealPending());
    }

    @Test
    public void anOpaqueOrBlurLessKeyboardIsVisibleInTheSamePass() {
        mSurface.glassSurface = false;
        mSurface.blurEnabled = true;
        mSurface.backdropReady = false;

        mChoreographer.onVisibilityRequested(true);

        assertEquals(View.VISIBLE, mSurface.keyboardContainer.getVisibility());
        assertFalse(mChoreographer.isOpenRevealPending());
        assertNull("nothing to wait for means no gate", mChoreographer.openPreDrawListener());
        assertEquals("and no backstop either", 0, mSurface.posted.size());
    }

    @Test
    public void aRevealPendingFromAnEarlierOpenSurvivesAReShow() {
        mSurface.glassSurface = true;
        mSurface.blurEnabled = true;
        mSurface.backdropReady = false;
        mChoreographer.onVisibilityRequested(true);

        // The container is INVISIBLE now, so this second request is not "opening from GONE" — but
        // the outstanding promise must still hold, or the keys draw over the stale crop.
        mChoreographer.onVisibilityRequested(true);

        assertTrue(mChoreographer.isOpenRevealPending());
        assertEquals(View.INVISIBLE, mSurface.keyboardContainer.getVisibility());
    }

    @Test
    public void aRenderPassThatNoLongerWantsTheKeyboardDropsThePromise() {
        mSurface.glassSurface = true;
        mSurface.blurEnabled = true;
        mSurface.backdropReady = false;
        mChoreographer.onVisibilityRequested(true);

        mChoreographer.completePendingOpenReveal(spec(false));

        assertFalse(mChoreographer.isOpenRevealPending());
        assertNull(mChoreographer.openPreDrawListener());
    }

    // -------------------------------------------------------------------- close path

    @Test
    public void closeGoesGoneAndKeepsTheSeamCoveredUntilDockOnlyLayoutIsObserved() {
        mSurface.glassSurface = true;
        mSurface.blurEnabled = true;
        mSurface.backdropReady = true;
        mChoreographer.onVisibilityRequested(true);
        mSurface.log.clear();

        mChoreographer.onVisibilityRequested(false);

        assertEquals(View.GONE, mSurface.keyboardContainer.getVisibility());
        assertTrue(mChoreographer.isCloseGeometryPending());
        assertNotNull(mChoreographer.closePreDrawListener());
        assertEquals(List.of("invalidateTransitionCrops", "onKeyboardClosed"), mSurface.log);

        // Still expanded: the destination has not been laid out, so the cover stays.
        mSurface.log.clear();
        mChoreographer.completePendingCloseGeometry(spec(true));
        assertTrue(mChoreographer.isCloseGeometryPending());

        // Dock-only spec, but the accessory stack has not reached its new exact height yet.
        mSurface.accessoryContainer.getLayoutParams().height = 300;
        mSurface.accessoryContainer.layout(0, 0, 1080, 120);
        mChoreographer.completePendingCloseGeometry(spec(false));
        assertTrue("a mid-relayout pass must not retire the conservative cover",
            mChoreographer.isCloseGeometryPending());
        assertFalse(mSurface.log.contains("invalidateCloseSettledCrops"));

        mSurface.accessoryContainer.layout(0, 0, 1080, 300);
        mChoreographer.completePendingCloseGeometry(spec(false));

        assertFalse(mChoreographer.isCloseGeometryPending());
        assertEquals(List.of("invalidateCloseSettledCrops", "requestAccessoryRenderSync"),
            mSurface.log);
    }

    @Test
    public void closeCorrectionHoldsTheFrameUntilTheDockCropIsSafeForTheDestination() {
        mChoreographer.onVisibilityRequested(true);
        mChoreographer.onVisibilityRequested(false);
        ViewTreeObserver.OnPreDrawListener correction = mChoreographer.closePreDrawListener();
        assertNotNull(correction);
        mSurface.log.clear();

        mSurface.keyboardShown = false;
        mSurface.dockBackdropSafe = false;
        assertFalse(correction.onPreDraw());
        // The dock-only crop is rebuilt synchronously, before that geometry may draw.
        assertEquals(List.of("buildChromeSpec", "invalidateAccessoryCrop", "applyChromeSpec",
            "dockBackdropSafeForDestination"), mSurface.log);
        assertNotNull(mChoreographer.closePreDrawListener());

        mSurface.dockBackdropSafe = true;
        assertTrue(correction.onPreDraw());
        assertNull(mChoreographer.closePreDrawListener());
    }

    @Test
    public void reopeningReleasesTheCloseCorrectionAndItsCover() {
        mChoreographer.onVisibilityRequested(true);
        mChoreographer.onVisibilityRequested(false);
        assertNotNull(mChoreographer.closePreDrawListener());

        mChoreographer.onVisibilityRequested(true);

        assertNull(mChoreographer.closePreDrawListener());
        assertFalse(mChoreographer.isCloseGeometryPending());
    }

    @Test
    public void stoppingDropsEveryPendingFrameScopedPromise() {
        mSurface.glassSurface = true;
        mSurface.blurEnabled = true;
        mSurface.backdropReady = false;
        mChoreographer.onVisibilityRequested(true);
        mChoreographer.onVisibilityRequested(false);
        assertTrue(mChoreographer.isCloseGeometryPending());

        mChoreographer.onStop();

        assertNull(mChoreographer.openPreDrawListener());
        assertNull(mChoreographer.closePreDrawListener());
        assertFalse(mChoreographer.isCloseGeometryPending());
        assertEquals("a gate left armed across a stop would hold the next window's first draw",
            0, mSurface.posted.size());
    }

    // ------------------------------------------------------------------- measurement

    @Test
    public void measuredHeightIsMemoizedOnTheRootBoundsItWasMeasuredAgainst() {
        mSurface.keyboardContainer.measuredHeight = 420;

        assertEquals(420, mChoreographer.measureHeightPx());
        assertEquals(1, mSurface.keyboardContainer.measurePasses);
        assertEquals(420, mChoreographer.measureHeightPx());
        assertEquals("a clean memo must not re-measure",
            1, mSurface.keyboardContainer.measurePasses);
        assertEquals(420, mChoreographer.desiredHeightPx());

        // A surface-geometry change makes the height wrong but keeps the last value readable, which
        // is what the container's layout-change retry compares against.
        mSurface.keyboardContainer.measuredHeight = 500;
        mChoreographer.markHeightDirty();
        assertEquals(420, mChoreographer.desiredHeightPx());
        assertEquals(500, mChoreographer.measureHeightPx());
        assertEquals(2, mSurface.keyboardContainer.measurePasses);

        // New root bounds are a different key, so the memo misses on its own.
        mSurface.availableRoot.layout(0, 0, 1080, 1600);
        assertEquals(500, mChoreographer.measureHeightPx());
        assertEquals(3, mSurface.keyboardContainer.measurePasses);
    }

    @Test
    public void previewInvalidationDropsTheBoundsKeyTooBecauseThoseDoNotChange() {
        mSurface.keyboardContainer.measuredHeight = 420;
        assertEquals(420, mChoreographer.measureHeightPx());

        mSurface.keyboardContainer.measuredHeight = 460;
        mChoreographer.invalidateMeasurement();

        assertEquals(0, mChoreographer.desiredHeightPx());
        assertEquals(460, mChoreographer.measureHeightPx());
        assertEquals(2, mSurface.keyboardContainer.measurePasses);
    }

    @Test
    public void previewGeometrySyncsCollapseToOnePassPerFrame() {
        mChoreographer.requestPreviewGeometrySync();
        mChoreographer.requestPreviewGeometrySync();
        mChoreographer.requestPreviewGeometrySync();

        assertEquals(1, mSurface.posted.size());
        assertEquals(KeyboardGeometryChoreographer.PREVIEW_GEOMETRY_SYNC_MS,
            mSurface.posted.get(0).delayMs);

        mSurface.runPosted();
        assertTrue(mSurface.geometryReasons.contains("inapp-keyboard"));

        // The frame is over; the next slider event may post again.
        mChoreographer.requestPreviewGeometrySync();
        assertEquals(1, mSurface.posted.size());
    }

    @Test
    public void geometrySyncDropsTheMeasuredHeightAndRelayoutsTheAccessoryStackTwice() {
        mSurface.keyboardContainer.measuredHeight = 420;
        mChoreographer.measureHeightPx();

        mChoreographer.requestGeometrySync();

        assertEquals(0, mChoreographer.desiredHeightPx());
        assertEquals(List.of("inapp-keyboard"), mSurface.geometryReasons);
        // The second pass runs once the container has actually laid out at the new height.
        mSurface.keyboardContainer.drainPosted();
        assertEquals(List.of("inapp-keyboard", "inapp-keyboard:layout"), mSurface.geometryReasons);
    }

    @Test
    public void theShownLatchOnlyReportsTransitions() {
        assertFalse("the stack starts out laid out for a hidden keyboard",
            mChoreographer.applyKeyboardShown(false));
        assertTrue(mChoreographer.applyKeyboardShown(true));
        assertFalse(mChoreographer.applyKeyboardShown(true));
        assertTrue(mChoreographer.applyKeyboardShown(false));
    }

    // ---------------------------------------------------------------- system-IME gate

    @Test
    public void imeInsetsAreIgnoredUntilAnInActivityFlowAsksForTheSystemKeyboard() {
        // Insets can retain the previous app's mid-transition IME snapshot across a home resume.
        assertFalse(mChoreographer.acceptsSystemImeInsets());

        mChoreographer.onSystemImeRequested();

        assertTrue(mChoreographer.acceptsSystemImeInsets());
        assertTrue("the request must re-deliver insets, or the gate opens a frame late",
            mSurface.log.contains("requestApplyInsets"));

        mChoreographer.onSystemImeReleased();
        assertFalse(mChoreographer.acceptsSystemImeInsets());
    }

    @Test
    public void imeVisibilityProbeOnlyReportsChanges() {
        assertFalse(mChoreographer.lastImeVisible());
        assertTrue(mChoreographer.onImeVisibilityProbed(true));
        assertTrue(mChoreographer.lastImeVisible());
        assertFalse("an unchanged probe must not re-run the IME choreography",
            mChoreographer.onImeVisibilityProbed(true));
        assertTrue(mChoreographer.onImeVisibilityProbed(false));
        assertFalse(mChoreographer.lastImeVisible());

        mChoreographer.resetImeVisibility(true);
        assertTrue(mChoreographer.lastImeVisible());
        assertFalse(mChoreographer.onImeVisibilityProbed(true));
    }

    // ------------------------------------------------------------------------ helpers

    @NonNull
    private ChromeSpec spec(boolean keyboardShown) {
        return new ChromeSpec(true, keyboardShown, keyboardShown ? 420 : 0, mSurface.blurEnabled,
            true, false, true, 1f, 20);
    }

    /** A view whose measured height the test owns, so the memo can be observed. */
    private static final class MeasuredView extends FrameLayout {
        int measuredHeight = 0;
        int measurePasses;
        private final List<Runnable> mPosted = new ArrayList<>();

        MeasuredView(@NonNull Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            measurePasses++;
            setMeasuredDimension(View.MeasureSpec.getSize(widthMeasureSpec), measuredHeight);
        }

        @Override
        public boolean post(Runnable action) {
            mPosted.add(action);
            return true;
        }

        void drainPosted() {
            List<Runnable> pending = new ArrayList<>(mPosted);
            mPosted.clear();
            for (Runnable runnable : pending) {
                runnable.run();
            }
        }
    }

    private static final class Posted {
        final Runnable runnable;
        final long delayMs;

        Posted(Runnable runnable, long delayMs) {
            this.runnable = runnable;
            this.delayMs = delayMs;
        }
    }

    private final class FakeSurface implements KeyboardGeometryChoreographer.Surface {

        final MeasuredView keyboardContainer;
        final View gateHost;
        final View availableRoot;
        final View accessoryContainer;
        final DisplayMetrics metrics;

        final List<String> log = new ArrayList<>();
        final List<String> geometryReasons = new ArrayList<>();
        final List<Posted> posted = new ArrayList<>();

        boolean glassSurface;
        boolean blurEnabled = true;
        boolean backdropReady;
        boolean dockBackdropSafe = true;
        boolean keyboardShown = true;
        boolean activityAlive = true;

        FakeSurface(@NonNull Context context) {
            keyboardContainer = new MeasuredView(context);
            keyboardContainer.setVisibility(View.GONE);
            gateHost = new View(context);
            availableRoot = new View(context);
            availableRoot.layout(0, 0, 1080, 1920);
            accessoryContainer = new View(context);
            accessoryContainer.setLayoutParams(new ViewGroup.LayoutParams(1080, 0));
            metrics = context.getResources().getDisplayMetrics();
        }

        @Nullable
        @Override
        public View findView(int viewId) {
            if (viewId == R.id.inapp_keyboard_container) return keyboardContainer;
            if (viewId == R.id.activity_termux_root_view) return gateHost;
            if (viewId == R.id.activity_termux_root_relative_layout) return availableRoot;
            if (viewId == R.id.accessory_stack_container) return accessoryContainer;
            return null;
        }

        @NonNull
        @Override
        public DisplayMetrics displayMetrics() {
            return metrics;
        }

        @Nullable
        @Override
        public View attachedKeyboardView() {
            return null;
        }

        @NonNull
        @Override
        public ChromeSpec buildChromeSpec() {
            log.add("buildChromeSpec");
            return spec(keyboardShown);
        }

        @Override
        public void applyChromeSpec(@NonNull ChromeSpec chromeSpec) {
            log.add("applyChromeSpec");
            // Exactly where TermuxActivity.applyChromeSpec() settles the two promises.
            mChoreographer.completePendingOpenReveal(chromeSpec);
            mChoreographer.completePendingCloseGeometry(chromeSpec);
        }

        @Override
        public void applyKeyboardSurfaceState(@NonNull ChromeSpec chromeSpec) {
            log.add("applyKeyboardSurfaceState");
        }

        @Override
        public void requestAccessoryRenderSync() {
            log.add("requestAccessoryRenderSync");
        }

        @Override
        public void applyAccessoryGeometry(@NonNull String reason) {
            geometryReasons.add(reason);
        }

        @Override
        public boolean keyboardGlassSurface() {
            log.add("keyboardGlassSurface");
            return glassSurface;
        }

        @Override
        public boolean keyboardBackdropReady(@NonNull ChromeSpec chromeSpec) {
            log.add("keyboardBackdropReady");
            return backdropReady;
        }

        @Override
        public boolean dockBackdropSafeForDestination(@NonNull ChromeSpec chromeSpec) {
            log.add("dockBackdropSafeForDestination");
            return dockBackdropSafe;
        }

        @Override
        public void invalidateTransitionCrops() {
            log.add("invalidateTransitionCrops");
        }

        @Override
        public void invalidateCloseSettledCrops() {
            log.add("invalidateCloseSettledCrops");
        }

        @Override
        public void invalidateAccessoryCrop() {
            log.add("invalidateAccessoryCrop");
        }

        @Override
        public void onKeyboardClosed() {
            log.add("onKeyboardClosed");
        }

        @Override
        public void requestApplyInsets() {
            log.add("requestApplyInsets");
        }

        @Override
        public void postDelayed(@NonNull Runnable runnable, long delayMs) {
            posted.add(new Posted(runnable, delayMs));
        }

        @Override
        public void removeCallbacks(@NonNull Runnable runnable) {
            posted.removeIf(entry -> entry.runnable == runnable);
        }

        @Override
        public boolean isActivityAlive() {
            return activityAlive;
        }

        void runPosted() {
            List<Posted> pending = new ArrayList<>(posted);
            posted.clear();
            for (Posted entry : pending) {
                entry.runnable.run();
            }
        }
    }
}
