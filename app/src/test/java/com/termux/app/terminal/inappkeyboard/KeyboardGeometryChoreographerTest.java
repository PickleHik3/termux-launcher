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
import static org.junit.Assert.assertTrue;

/**
 * Pins the in-app keyboard's visibility request on a fake {@link
 * KeyboardGeometryChoreographer.Surface}: the keyboard is visible on the call that asks for it,
 * neither open nor close installs a pre-draw listener, and the measurement cache and the
 * system-IME inset gate behave.
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

    // ------------------------------------------------------------ put-away pre-measure

    @Test
    public void prewarmMeasuresAPutAwayKeyboardAndLeavesItGoneWithItsMemoUntouched() {
        mSurface.keyboardContainer.measuredHeight = 600;
        assertEquals(0, mChoreographer.desiredHeightPx());

        assertTrue(mChoreographer.prewarmMeasure());

        assertEquals("the stack, insets and touches never see it", View.GONE,
            mSurface.keyboardContainer.getVisibility());
        assertEquals(1, mSurface.keyboardContainer.measurePasses);
        assertEquals("the height memo is the show's to fill, as with the keyboard GONE", 0,
            mChoreographer.desiredHeightPx());
        assertTrue("no layout request leaves the pre-measure", mSurface.log.isEmpty());
    }

    @Test
    public void prewarmMeasuresOncePerBoundsAndAgainAfterTheMeasurementIsDiscarded() {
        assertTrue(mChoreographer.prewarmMeasure());
        assertFalse(mChoreographer.prewarmMeasure());
        mChoreographer.discardMeasuredHeight();
        assertTrue(mChoreographer.prewarmMeasure());
    }

    @Test
    public void prewarmNeverMeasuresAShownOrAFloatingKeyboard() {
        mSurface.keyboardContainer.setVisibility(View.VISIBLE);
        assertFalse(mChoreographer.prewarmMeasure());
        mSurface.keyboardContainer.setVisibility(View.GONE);
        mSurface.floatingReference = new KeyboardGeometryChoreographer.HostReference(900, 800);
        assertFalse(mChoreographer.prewarmMeasure());
        assertEquals(0, mSurface.keyboardContainer.measurePasses);
    }

    @Test
    public void prewarmRuleNeedsAGoneStackKeyboardWhoseBoundsMoved() {
        assertTrue(KeyboardGeometryChoreographer.needsPrewarm(true, false, false, 0, 0, 1080, 1920));
        assertTrue(KeyboardGeometryChoreographer.needsPrewarm(true, false, true, 1080, 1920, 1080, 1800));
        assertFalse(KeyboardGeometryChoreographer.needsPrewarm(true, false, true, 1080, 1920, 1080, 1920));
        assertFalse(KeyboardGeometryChoreographer.needsPrewarm(false, false, false, 0, 0, 1080, 1920));
        assertFalse(KeyboardGeometryChoreographer.needsPrewarm(true, true, false, 0, 0, 1080, 1920));
        assertFalse(KeyboardGeometryChoreographer.needsPrewarm(true, false, false, 0, 0, 0, 1920));
    }

    // ------------------------------------------------------------------- open reveal

    @Test
    public void aKeyboardAskedToShowIsVisibleOnThatCallEvenWithItsBackdropNotReady() {
        mSurface.blurEnabled = true;
        assertEquals(View.GONE, mSurface.keyboardContainer.getVisibility());
        int before = countPreDrawListeners(mSurface.gateHost.getViewTreeObserver());

        mChoreographer.onVisibilityRequested(true);

        assertEquals(View.VISIBLE, mSurface.keyboardContainer.getVisibility());
        assertEquals("no pre-draw listener is added to the root", before,
            countPreDrawListeners(mSurface.gateHost.getViewTreeObserver()));
    }

    @Test
    public void closingInstallsNoPreDrawListener() {
        mChoreographer.onVisibilityRequested(true);
        int before = countPreDrawListeners(mSurface.gateHost.getViewTreeObserver());

        mChoreographer.onVisibilityRequested(false);
        assertEquals(before, countPreDrawListeners(mSurface.gateHost.getViewTreeObserver()));

        mSurface.staysLaidOut = false;
        mChoreographer.onVisibilityRequested(true);
        mChoreographer.onVisibilityRequested(false);
        assertEquals(before, countPreDrawListeners(mSurface.gateHost.getViewTreeObserver()));
        assertEquals(View.GONE, mSurface.keyboardContainer.getVisibility());
    }

    /** Counts listeners through the observer's own bookkeeping, since there is no getter. */
    private static int countPreDrawListeners(@NonNull ViewTreeObserver observer) {
        try {
            java.lang.reflect.Field field =
                ViewTreeObserver.class.getDeclaredField("mOnPreDrawListeners");
            field.setAccessible(true);
            Object list = field.get(observer);
            if (list == null) return 0;
            java.lang.reflect.Method size = list.getClass().getDeclaredMethod("size");
            size.setAccessible(true);
            return (Integer) size.invoke(list);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void aKeyboardOpeningBelowTheScreenIsRevealedAtOnceWithOneRenderPassAndNoGate() {
        // A seam crossing's pre-roll: the travel holds the keyboard below the screen, so no frame
        // of the drag is held back for its backdrop, and no chrome pass runs inside a frame.
        mSurface.blurEnabled = true;

        mChoreographer.onVisibilityRequested(true, true);

        assertEquals(View.VISIBLE, mSurface.keyboardContainer.getVisibility());
        assertFalse("no chrome pass runs synchronously", mSurface.log.contains("applyChromeSpec"));
        assertEquals("one posted render pass cuts its crop", 1,
            java.util.Collections.frequency(mSurface.log, "requestAccessoryRenderSync"));
    }

    @Test
    public void aBelowScreenOpenLeavesTheKeyboardsDressToTheFramesOwnApply() {
        mSurface.blurEnabled = true;

        mChoreographer.onVisibilityRequested(true, true);

        assertFalse("the dress is not made inside the request",
            mSurface.log.contains("applyKeyboardSurfaceState"));
        assertFalse(mSurface.log.contains("buildChromeSpec"));
        assertEquals("the frame's apply is booked once", 1,
            java.util.Collections.frequency(mSurface.log, "requestApplyThisFrame"));
        assertTrue(KeyboardGeometryChoreographer.dressesOnRequest(false));
        assertFalse(KeyboardGeometryChoreographer.dressesOnRequest(true));
    }

    @Test
    public void anOnScreenOpenStillDressesTheKeyboardAtOnce() {
        mChoreographer.onVisibilityRequested(true, false);

        assertTrue(mSurface.log.contains("applyKeyboardSurfaceState"));
        assertFalse(mSurface.log.contains("requestApplyThisFrame"));
    }

    // -------------------------------------------------------------------- close path

    @Test
    public void aDownKeyboardStaysLaidOutAndNeverGoesGone() {
        mSurface.blurEnabled = true;
        mChoreographer.onVisibilityRequested(true);
        mSurface.log.clear();
        int rests = mSurface.stackRests;

        mChoreographer.onVisibilityRequested(false);

        assertEquals("down is the scene's translation, not GONE", View.VISIBLE,
            mSurface.keyboardContainer.getVisibility());
        assertEquals("the stack's renderer is asked for the rest", rests + 1, mSurface.stackRests);
        assertEquals(List.of("invalidateTransitionCrops", "onKeyboardClosed",
            "requestAccessoryRenderSync"), mSurface.log);

        // A second hide while down changes nothing and arms nothing again.
        mSurface.log.clear();
        mChoreographer.onVisibilityRequested(false);
        assertEquals(View.VISIBLE, mSurface.keyboardContainer.getVisibility());
        assertFalse(mSurface.log.contains("invalidateTransitionCrops"));
    }

    @Test
    public void aKeyboardThatWasNeverShownIsPutDownLaidOutNotGone() {
        assertEquals(View.GONE, mSurface.keyboardContainer.getVisibility());

        mChoreographer.onVisibilityRequested(false);

        assertEquals(View.VISIBLE, mSurface.keyboardContainer.getVisibility());
    }

    @Test
    public void stoppingDropsThePendingPreviewSync() {
        mChoreographer.requestPreviewGeometrySync();
        assertEquals(1, mSurface.posted.size());

        mChoreographer.onStop();

        assertEquals(0, mSurface.posted.size());
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
    public void aDockedKeyboardIsCappedAgainstTheContentRootItIsMeasuredIn() {
        mSurface.keyboardContainer.measuredHeight = 420;

        assertEquals(420, mChoreographer.measureHeightPx());
        assertEquals("measured against the full content root, not the accessory stack",
            1080, mSurface.keyboardContainer.getMeasuredWidth());
        assertEquals(1920, mChoreographer.heightCapReferencePx());
    }

    @Test
    public void aFloatingKeyboardReservesNoStackHeightAndIsCappedAgainstItsFrame() {
        mSurface.keyboardContainer.measuredHeight = 420;
        mChoreographer.measureHeightPx();
        int passesWhileDocked = mSurface.keyboardContainer.measurePasses;

        mSurface.floatingReference =
            new KeyboardGeometryChoreographer.HostReference(648, 1600);

        // The frame draws over the place rather than standing in the stack, so there is nothing
        // for the stack to reserve and nothing for the content root to give back.
        assertEquals(0, mChoreographer.measureHeightPx());
        // And the frame, not the content root, is what the fractional height cap is a share of.
        assertEquals(1600, mChoreographer.heightCapReferencePx());
        assertEquals("the frame's own layout pass measures it",
            passesWhileDocked, mSurface.keyboardContainer.measurePasses);

        // Docked again, the stack reserves it again.
        mSurface.floatingReference = null;
        assertEquals(420, mChoreographer.measureHeightPx());
        assertEquals(1920, mChoreographer.heightCapReferencePx());
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
    public void geometrySyncDropsTheMeasuredHeightAndRelayoutsTheAccessoryStackOnce() {
        mSurface.keyboardContainer.measuredHeight = 420;
        mChoreographer.measureHeightPx();

        mChoreographer.requestGeometrySync();

        assertEquals(0, mChoreographer.desiredHeightPx());
        assertEquals(List.of("inapp-keyboard"), mSurface.geometryReasons);
        // No second pass after the container's layout: the height was measured independently of
        // the stack, and a disagreeing layout is the container listener's to report.
        mSurface.keyboardContainer.drainPosted();
        assertEquals(List.of("inapp-keyboard"), mSurface.geometryReasons);
    }

    @Test
    public void theHostIsDressedBeforeEveryMeasureAndARedressBeatsACleanMemo() {
        mSurface.keyboardContainer.measuredHeight = 611;
        assertEquals(611, mChoreographer.measureHeightPx());
        assertEquals(1, mSurface.dressPasses);

        // A form switch redresses the host between two measures; the memo must not answer for it.
        mSurface.redressPending = true;
        mSurface.keyboardContainer.measuredHeight = 654;
        assertEquals(654, mChoreographer.measureHeightPx());
        assertEquals(2, mSurface.dressPasses);
        assertEquals(2, mSurface.keyboardContainer.measurePasses);

        // Already dressed: the clean memo answers.
        assertEquals(654, mChoreographer.measureHeightPx());
        assertEquals(2, mSurface.keyboardContainer.measurePasses);
    }

    @Test
    public void aMissedReservationIsReportedOncePerMissAndReArmedByAMatch() {
        assertFalse("nothing reserved, nothing to miss", mChoreographer.reservationMissedBy(654));

        mChoreographer.noteReservedHeightPx(611);
        assertTrue(mChoreographer.reservationMissedBy(654));
        assertFalse("the same miss is already being corrected",
            mChoreographer.reservationMissedBy(654));

        mChoreographer.noteReservedHeightPx(654);
        assertFalse(mChoreographer.reservationMissedBy(654));

        // Met once, the same miss later on is a new one.
        mChoreographer.noteReservedHeightPx(611);
        assertTrue(mChoreographer.reservationMissedBy(654));
        mChoreographer.noteReservedHeightPx(0);
        assertFalse("a floating or hidden keyboard reserves nothing",
            mChoreographer.reservationMissedBy(654));
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
    public void aSystemKeyboardRequestRedeliversInsets() {
        mChoreographer.requestApplyInsets();
        assertTrue(mSurface.log.contains("requestApplyInsets"));
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

        @Nullable KeyboardGeometryChoreographer.HostReference floatingReference;

        boolean blurEnabled = true;
        boolean keyboardShown = true;
        boolean activityAlive = true;
        /** The docked in-app keyboard stays laid out while down; false is GONE. */
        boolean staysLaidOut = true;
        int stackRests;
        /** The next dress changes the host's margins or padding. */
        boolean redressPending;
        int dressPasses;

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

        @Override
        public boolean keyboardStaysLaidOut() {
            return staysLaidOut;
        }

        @Override
        public void requestStackRest() {
            stackRests++;
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

        @Override
        public boolean dressKeyboardHost() {
            dressPasses++;
            boolean changed = redressPending;
            redressPending = false;
            return changed;
        }

        @Nullable
        @Override
        public View attachedKeyboardView() {
            return null;
        }

        @Nullable
        @Override
        public KeyboardGeometryChoreographer.HostReference floatingKeyboardReference() {
            return floatingReference;
        }

        @NonNull
        @Override
        public ChromeSpec buildChromeSpec() {
            log.add("buildChromeSpec");
            return spec(keyboardShown);
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
        public void requestApplyThisFrame() {
            log.add("requestApplyThisFrame");
        }

        @Override
        public void applyAccessoryGeometry(@NonNull String reason) {
            geometryReasons.add(reason);
        }

        @Override
        public void invalidateTransitionCrops() {
            log.add("invalidateTransitionCrops");
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
