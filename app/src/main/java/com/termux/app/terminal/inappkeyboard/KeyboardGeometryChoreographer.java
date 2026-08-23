package com.termux.app.terminal.inappkeyboard;

import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.ChromeSpec;

import juloo.keyboard2.Keyboard2View;

/**
 * The in-app keyboard's geometry and its flash-free reveal choreography.
 *
 * <p>Showing the embedded keyboard is not one state change but an ordered protocol. Its height has
 * to be measured against a stable root (never against the accessory stack it is about to resize, or
 * the two {@code AT_MOST} passes chase each other), the destination glass crop has to be installed
 * before any key is allowed to draw, and on the way out the conservative close cover has to survive
 * exactly until dock-only layout is observed. Those steps used to be ten methods and eleven
 * correlated flags spread over four thousand lines of {@code TermuxActivity}, where nothing said
 * which of them had to happen first.</p>
 *
 * <p>They are one mechanism, so they live in one module. Callers state <em>what happened</em> —
 * {@link #onVisibilityRequested(boolean)}, {@link #completePendingOpenReveal(ChromeSpec)},
 * {@link #onImeVisibilityProbed(boolean)} — and the module owns the ordering. Everything it needs
 * from the Activity goes through {@link Surface}, which is what makes the ordering assertable
 * without a window: the reveal cannot be observed as "keys visible before crop installed" on a fake
 * any more than it can on a device.</p>
 *
 * <p>The chrome <em>painters</em> deliberately stay on the Activity (they are welded to the glass
 * factory, the backdrop bitmaps and the dock layout) and are invoked through {@link Surface}. What
 * lives here is the protocol: the pending-reveal and pending-close flags, the two pre-draw gates,
 * the blocked-frame fail-safe, the deferral predicate and the measurement cache.</p>
 */
public final class KeyboardGeometryChoreographer {

    /**
     * Fail-safe cap on how many frames the open gate may hold the window. Worst case after this
     * many blocked frames is the old one-frame crop mismatch, never a frozen UI.
     */
    static final int MAX_OPEN_REVEAL_BLOCKED_FRAMES = 3;

    /** Backstop for windows that never draw (or test environments with no draw pass). */
    static final long OPEN_REVEAL_BACKSTOP_MS = 160L;

    /** Slider events can outrun display frames; collapse them to one geometry pass per frame. */
    static final long PREVIEW_GEOMETRY_SYNC_MS = 16L;

    /** The Activity-side slots, painters and lookups the choreography drives. */
    public interface Surface {

        /** Resolves a view slot by id; null before inflation, or when the slot is absent. */
        @Nullable View findView(int viewId);

        @NonNull DisplayMetrics displayMetrics();

        /** The keyboard view currently attached to the host, or null while detached. */
        @Nullable View attachedKeyboardView();

        // ---- the chrome render pass

        @NonNull ChromeSpec buildChromeSpec();

        /** Applies a spec synchronously — used by the gates, which must not wait for a post. */
        void applyChromeSpec(@NonNull ChromeSpec spec);

        /** Paints the keyboard's own margins, clip, glass and backdrop for this spec. */
        void applyKeyboardSurfaceState(@NonNull ChromeSpec spec);

        /** Requests a coalesced accessory re-render. */
        void requestAccessoryRenderSync();

        /** Re-measures and re-lays out the accessory stack around the keyboard, for {@code reason}. */
        void applyAccessoryGeometry(@NonNull String reason);

        // ---- readiness the reveal gate waits on

        /** True while the keyboard's surface renders as glass (so a blurred backdrop is required). */
        boolean keyboardGlassSurface();

        /**
         * Whether the blurred destination backdrop for this spec is installed — the unified
         * default-dock surface waits on the shared accessory crop, a capsule/local surface on its
         * own keyboard backdrop bitmap.
         */
        boolean keyboardBackdropReady(@NonNull ChromeSpec spec);

        /** Whether the dock's installed crop is already valid for the destination (close gate). */
        boolean dockBackdropSafeForDestination(@NonNull ChromeSpec spec);

        // ---- crop invalidation the transition needs

        /** Every geometry-dependent crop, rects included; the shared blur frames are preserved. */
        void invalidateTransitionCrops();

        /** Keyboard + under-pill strip, once dock-only destination layout is observed. */
        void invalidateCloseSettledCrops();

        /** The shared dock/unified accessory crop only. */
        void invalidateAccessoryCrop();

        // ---- misc Activity behaviour on the transition

        /** The keyboard just went away; linger-hide the keybind hints it was annotating. */
        void onKeyboardClosed();

        /** Re-delivers window insets after an in-activity flow explicitly asked for the IME. */
        void requestApplyInsets();

        void postDelayed(@NonNull Runnable runnable, long delayMs);

        void removeCallbacks(@NonNull Runnable runnable);

        /** False once the Activity is finishing or destroyed; posted work must then not run. */
        boolean isActivityAlive();
    }

    @NonNull private final Surface mSurface;

    // ---- reveal / close protocol state

    /** Keeps a unified glass keyboard hidden until the expanded dock+keyboard crop is installed. */
    private boolean mPendingOpenReveal;
    /** Keeps the under-pill glass covering stale close geometry until dock-only layout settles. */
    private boolean mPendingCloseGeometry;
    private int mOpenRevealBlockedFrames;
    @Nullable private ViewTreeObserver.OnPreDrawListener mOpenPreDrawListener;
    @Nullable private View mOpenPreDrawView;
    @Nullable private ViewTreeObserver.OnPreDrawListener mClosePreDrawListener;
    @Nullable private View mClosePreDrawView;

    // ---- measurement cache

    private boolean mHeightDirty = true;
    private int mDesiredHeightPx;
    private int mMeasureWidthPx;
    private int mAvailableHeightPx;
    private boolean mPreviewGeometrySyncPosted;
    /** Last {@code keyboardShown} the accessory stack was actually laid out for. */
    private boolean mAppliedKeyboardShown;

    // ---- system-IME gate

    /**
     * Insets can retain the previous app's mid-transition IME snapshot across a home resume, so
     * they count only after an input flow in this activity explicitly asked for the system IME.
     */
    private boolean mAcceptSystemImeInsets;
    private boolean mLastImeVisible;

    private final Runnable mOpenRevealBackstopRunnable = this::revealIfStillPending;
    private final Runnable mPreviewGeometrySyncRunnable;

    public KeyboardGeometryChoreographer(@NonNull Surface surface) {
        mSurface = surface;
        mPreviewGeometrySyncRunnable = () -> {
            mPreviewGeometrySyncPosted = false;
            if (mSurface.isActivityAlive())
                requestGeometrySync();
        };
    }

    // ---------------------------------------------------------------- measurement

    /**
     * The keyboard's desired height for the current bounds, memoized on the bounds it was measured
     * against. 0 while there is no keyboard container.
     */
    public int measureHeightPx() {
        View keyboardContainer = mSurface.findView(R.id.inapp_keyboard_container);
        if (keyboardContainer == null)
            return 0;
        View availableRoot = mSurface.findView(R.id.activity_termux_root_relative_layout);
        int width = availableRoot != null ? availableRoot.getWidth() : 0;
        int availableHeight = availableRoot != null ? availableRoot.getHeight() : 0;
        DisplayMetrics metrics = mSurface.displayMetrics();
        if (width <= 0)
            width = metrics.widthPixels;
        if (availableHeight <= 0)
            availableHeight = metrics.heightPixels;
        View attached = mSurface.attachedKeyboardView();
        if (attached instanceof Keyboard2View) {
            // The keyboard is measured here against the full content root, but RelativeLayout later
            // measures it inside the shorter exact accessory stack. Keep its fractional height cap
            // tied to this stable root height so both AT_MOST passes resolve identically.
            ((Keyboard2View) attached).setHeightCapReferencePx(Math.max(0, availableHeight));
        }
        if (!mHeightDirty && mDesiredHeightPx > 0
            && mMeasureWidthPx == width
            && mAvailableHeightPx == availableHeight) {
            return mDesiredHeightPx;
        }
        // Measure the wrap-content keyboard independently of accessory_stack_container. The stack's
        // current exact height may have been computed from an older keyboard measurement, so using
        // its normal parent-provided spec here creates a shrinking feedback loop. This AT_MOST spec
        // is always based on the full content root and lets Keyboard2View apply its orientation cap.
        keyboardContainer.measure(
            View.MeasureSpec.makeMeasureSpec(Math.max(0, width), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(Math.max(0, availableHeight), View.MeasureSpec.AT_MOST));
        mDesiredHeightPx = Math.max(0, keyboardContainer.getMeasuredHeight());
        mMeasureWidthPx = width;
        mAvailableHeightPx = availableHeight;
        mHeightDirty = false;
        return mDesiredHeightPx;
    }

    /** The last measured desired height, without re-measuring. */
    public int desiredHeightPx() {
        return mDesiredHeightPx;
    }

    /**
     * Marks the cached height stale while keeping the last measured value readable. The surface
     * painters use this: a margin or padding change makes the height wrong but the previously
     * measured value is still the one the laid-out container is being compared against.
     */
    public void markHeightDirty() {
        mHeightDirty = true;
    }

    /** Marks the height stale and forgets the measured value with it. */
    public void discardMeasuredHeight() {
        mDesiredHeightPx = 0;
        mHeightDirty = true;
    }

    /**
     * Drops the whole measurement memo, bounds key included. The cache is keyed by the available
     * bounds, which do not change while the user previews keyboard geometry, so a preview has to
     * invalidate the key too.
     */
    public void invalidateMeasurement() {
        mDesiredHeightPx = 0;
        mMeasureWidthPx = 0;
        mAvailableHeightPx = 0;
        mHeightDirty = true;
    }

    /**
     * Invalidates our memo <em>and</em> Android's same-spec measurement cache, which would
     * otherwise replay the previous height through the following geometry sync.
     */
    public void invalidateMeasurementAndForceLayout() {
        invalidateMeasurement();
        View attached = mSurface.attachedKeyboardView();
        if (attached != null)
            attached.forceLayout();
        View keyboardContainer = mSurface.findView(R.id.inapp_keyboard_container);
        if (keyboardContainer != null)
            keyboardContainer.forceLayout();
    }

    /** Re-measures the keyboard and re-lays out the accessory stack around its new height. */
    public void requestGeometrySync() {
        View keyboardContainer = mSurface.findView(R.id.inapp_keyboard_container);
        discardMeasuredHeight();
        if (keyboardContainer != null)
            keyboardContainer.requestLayout();
        mSurface.applyAccessoryGeometry("inapp-keyboard");
        if (keyboardContainer != null) {
            keyboardContainer.post(() -> {
                if (mSurface.isActivityAlive())
                    mSurface.applyAccessoryGeometry("inapp-keyboard:layout");
            });
        }
    }

    /**
     * Coalesced geometry sync for live preview (height slider, drag). Keeps the latest renderer
     * values but collapses measurement, layout and backdrop work into one update per frame.
     */
    public void requestPreviewGeometrySync() {
        invalidateMeasurement();
        if (mPreviewGeometrySyncPosted)
            return;
        mPreviewGeometrySyncPosted = true;
        mSurface.postDelayed(mPreviewGeometrySyncRunnable, PREVIEW_GEOMETRY_SYNC_MS);
    }

    /**
     * Records the {@code keyboardShown} the accessory stack was just laid out for.
     *
     * @return true when it differs from the previous layout, which is one of the triggers that
     *     makes the terminal re-measure its rows.
     */
    public boolean applyKeyboardShown(boolean keyboardShown) {
        boolean changed = keyboardShown != mAppliedKeyboardShown;
        mAppliedKeyboardShown = keyboardShown;
        return changed;
    }

    // ------------------------------------------------------------ reveal protocol

    /**
     * Any blurred glass keyboard needs a destination-backdrop gate on a fresh open — the unified
     * default-dock surface waits on the shared accessory crop, the capsule/local surface waits on
     * its own keyboard backdrop bitmap. Without the gate the first frame draws base-color glass.
     */
    public static boolean shouldDeferReveal(boolean openingFromGone,
                                            boolean glassSurface,
                                            boolean blurEnabled,
                                            boolean backdropReady) {
        return openingFromGone && glassSurface && blurEnabled && !backdropReady;
    }

    /** True while the open reveal is still waiting on its destination backdrop. */
    public boolean isOpenRevealPending() {
        return mPendingOpenReveal;
    }

    /** True while the close seam is still being covered conservatively. */
    public boolean isCloseGeometryPending() {
        return mPendingCloseGeometry;
    }

    /**
     * The keyboard's visibility was requested. Drives the whole ordered transition: crop
     * invalidation, the INVISIBLE-then-reveal open path with its pre-draw gate, or the GONE close
     * path with its pre-draw correction.
     */
    public void onVisibilityRequested(boolean visible) {
        View keyboardContainer = mSurface.findView(R.id.inapp_keyboard_container);
        if (keyboardContainer == null) {
            return;
        }
        if (visible) {
            boolean openingFromGone = keyboardContainer.getVisibility() == View.GONE;
            removeClosePreDrawCorrection();
            if (openingFromGone)
                mSurface.invalidateTransitionCrops();
            mPendingCloseGeometry = false;
            ChromeSpec state = mSurface.buildChromeSpec();
            boolean backdropReady = mSurface.keyboardBackdropReady(state);
            boolean deferReveal = mPendingOpenReveal
                || shouldDeferReveal(openingFromGone, mSurface.keyboardGlassSurface(),
                    state.blurEnabled, backdropReady);
            mPendingOpenReveal = deferReveal;
            // INVISIBLE participates in destination layout without allowing a draw. The render
            // pass can therefore install the expanded crop before keys and their glass backing
            // become visible; non-unified surfaces keep the immediate path.
            keyboardContainer.setVisibility(deferReveal ? View.INVISIBLE : View.VISIBLE);
            mSurface.applyKeyboardSurfaceState(state);
            if (deferReveal) {
                installOpenPreDrawGate();
            }
        } else {
            boolean closingToGone = keyboardContainer.getVisibility() != View.GONE;
            if (closingToGone)
                mSurface.invalidateTransitionCrops();
            mPendingOpenReveal = false;
            removeOpenPreDrawGate();
            mPendingCloseGeometry = closingToGone;
            keyboardContainer.setVisibility(View.GONE);
            mSurface.onKeyboardClosed();
            if (closingToGone)
                installClosePreDrawCorrection();
        }
    }

    /** Reveals keys in the same UI transaction that installs the destination unified backdrop. */
    public void completePendingOpenReveal(@NonNull ChromeSpec state) {
        if (!mPendingOpenReveal) {
            return;
        }
        View keyboardContainer = mSurface.findView(R.id.inapp_keyboard_container);
        if (keyboardContainer == null || !state.keyboardShown) {
            mPendingOpenReveal = false;
            removeOpenPreDrawGate();
            return;
        }
        boolean backdropReady = mSurface.keyboardBackdropReady(state);
        if (mSurface.keyboardGlassSurface() && state.blurEnabled && !backdropReady) {
            return;
        }
        mPendingOpenReveal = false;
        keyboardContainer.setVisibility(View.VISIBLE);
        removeOpenPreDrawGate();
    }

    private void revealIfStillPending() {
        if (mPendingOpenReveal) forceRevealNow();
    }

    /** Immediately reveals the keyboard regardless of backdrop readiness (fail-safe path). */
    public void forceRevealNow() {
        mPendingOpenReveal = false;
        View keyboardContainer = mSurface.findView(R.id.inapp_keyboard_container);
        if (keyboardContainer != null && keyboardContainer.getVisibility() == View.INVISIBLE)
            keyboardContainer.setVisibility(View.VISIBLE);
        removeOpenPreDrawGate();
    }

    /** Runs after destination layout but before its first draw, closing the one-frame stale-crop gap. */
    private void installOpenPreDrawGate() {
        if (mOpenPreDrawListener != null) {
            return;
        }
        View gateView = mSurface.findView(R.id.activity_termux_root_view);
        if (gateView == null) {
            // No view to gate on — reveal now rather than leaving the keyboard invisible.
            forceRevealNow();
            return;
        }
        mOpenPreDrawView = gateView;
        mOpenRevealBlockedFrames = 0;
        mOpenPreDrawListener = () -> {
            if (!mPendingOpenReveal) {
                removeOpenPreDrawGate();
                return true;
            }
            // Posted render syncs run after traversal and would permit one draw with the old,
            // dock-only crop. Refresh synchronously now that destination geometry is measurable.
            mSurface.applyChromeSpec(mSurface.buildChromeSpec());
            boolean readyToDraw = !mPendingOpenReveal;
            if (!readyToDraw) {
                // Fail-safe: the gate must never wedge the whole window if the backdrop cannot
                // become ready (wallpaper unavailable, blur crop failing). Worst case after three
                // blocked frames is the old one-frame mismatch, never a frozen UI.
                if (++mOpenRevealBlockedFrames >= MAX_OPEN_REVEAL_BLOCKED_FRAMES) {
                    forceRevealNow();
                    return true;
                }
                mSurface.requestAccessoryRenderSync();
            }
            return readyToDraw;
        };
        gateView.getViewTreeObserver().addOnPreDrawListener(mOpenPreDrawListener);
        // Backstop for windows that stop drawing entirely (or test environments with no draw
        // pass): reveal shortly after install even if no pre-draw callback ever fires.
        mSurface.removeCallbacks(mOpenRevealBackstopRunnable);
        mSurface.postDelayed(mOpenRevealBackstopRunnable, OPEN_REVEAL_BACKSTOP_MS);
    }

    private void removeOpenPreDrawGate() {
        mSurface.removeCallbacks(mOpenRevealBackstopRunnable);
        View gateView = mOpenPreDrawView;
        ViewTreeObserver.OnPreDrawListener listener = mOpenPreDrawListener;
        mOpenPreDrawView = null;
        mOpenPreDrawListener = null;
        if (gateView == null || listener == null) {
            return;
        }
        ViewTreeObserver observer = gateView.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnPreDrawListener(listener);
        }
    }

    // ------------------------------------------------------------- close protocol

    /** Stops conservative close-seam coverage after dock-only destination layout is observed. */
    public void completePendingCloseGeometry(@NonNull ChromeSpec state) {
        View accessoryContainer = mSurface.findView(R.id.accessory_stack_container);
        ViewGroup.LayoutParams accessoryParams = accessoryContainer != null
            ? accessoryContainer.getLayoutParams() : null;
        int expectedAccessoryHeight = accessoryParams != null && accessoryParams.height > 0
            ? accessoryParams.height : 0;
        boolean destinationLayoutReady = accessoryContainer != null
            && expectedAccessoryHeight > 0
            && accessoryContainer.getHeight() == expectedAccessoryHeight;
        if (!mPendingCloseGeometry || state.keyboardShown || accessoryContainer == null) {
            return;
        }
        if (destinationLayoutReady) {
            mPendingCloseGeometry = false;
            // Re-evaluate the strip once without the conservative close overscan. At this point the
            // measured dock bottom is stable, so the exact seam crop can replace the safe cover.
            mSurface.invalidateCloseSettledCrops();
            mSurface.requestAccessoryRenderSync();
        }
    }

    /** Rebuilds dock-only blur after close layout and before that geometry is allowed to draw. */
    private void installClosePreDrawCorrection() {
        if (mClosePreDrawListener != null)
            return;
        View gateView = mSurface.findView(R.id.activity_termux_root_view);
        if (gateView == null)
            return;
        mClosePreDrawView = gateView;
        mClosePreDrawListener = () -> {
            ChromeSpec state = mSurface.buildChromeSpec();
            if (!state.keyboardShown) {
                mSurface.invalidateAccessoryCrop();
                mSurface.applyChromeSpec(state);
            }
            if (!mSurface.dockBackdropSafeForDestination(state))
                return false;
            removeClosePreDrawCorrection();
            return true;
        };
        gateView.getViewTreeObserver().addOnPreDrawListener(mClosePreDrawListener);
    }

    private void removeClosePreDrawCorrection() {
        View gateView = mClosePreDrawView;
        ViewTreeObserver.OnPreDrawListener listener = mClosePreDrawListener;
        mClosePreDrawView = null;
        mClosePreDrawListener = null;
        if (gateView == null || listener == null)
            return;
        ViewTreeObserver observer = gateView.getViewTreeObserver();
        if (observer.isAlive())
            observer.removeOnPreDrawListener(listener);
    }

    /**
     * The Activity is stopping: drop every pending frame-scoped promise. A gate left installed
     * across a stop would hold the next window's first draw.
     */
    public void onStop() {
        mSurface.removeCallbacks(mPreviewGeometrySyncRunnable);
        mPreviewGeometrySyncPosted = false;
        mPendingCloseGeometry = false;
        removeOpenPreDrawGate();
        removeClosePreDrawCorrection();
    }

    // ------------------------------------------------------------- system-IME gate

    /**
     * Marks subsequent IME insets as activity-owned rather than inherited from the previous app,
     * and asks for them to be re-delivered. Every in-activity flow that hands input to the system
     * keyboard goes through here — see {@code TerminalHost.onSystemImeRequested()}.
     */
    public void onSystemImeRequested() {
        mAcceptSystemImeInsets = true;
        mSurface.requestApplyInsets();
    }

    /** Drops activity ownership of the IME insets: what arrives next is inherited, not ours. */
    public void onSystemImeReleased() {
        mAcceptSystemImeInsets = false;
    }

    /** True while IME insets are this activity's to act on. */
    public boolean acceptsSystemImeInsets() {
        return mAcceptSystemImeInsets;
    }

    /** The last observed system-IME visibility, without probing for it again. */
    public boolean lastImeVisible() {
        return mLastImeVisible;
    }

    /** Seeds the tracked visibility when the global-layout probe is installed. */
    public void resetImeVisibility(boolean imeVisible) {
        mLastImeVisible = imeVisible;
    }

    /**
     * Feeds one global-layout probe of system-IME visibility.
     *
     * @return true when it changed, which is the only case the Activity reacts to.
     */
    public boolean onImeVisibilityProbed(boolean imeVisible) {
        if (imeVisible == mLastImeVisible)
            return false;
        mLastImeVisible = imeVisible;
        return true;
    }

    // ---------------------------------------------------------- test-visible seams

    /** The armed open gate, or null when none is installed. */
    @Nullable
    ViewTreeObserver.OnPreDrawListener openPreDrawListener() {
        return mOpenPreDrawListener;
    }

    /** The armed close correction, or null when none is installed. */
    @Nullable
    ViewTreeObserver.OnPreDrawListener closePreDrawListener() {
        return mClosePreDrawListener;
    }

    /** How many frames the open gate has held so far, against the fail-safe cap. */
    int openRevealBlockedFrames() {
        return mOpenRevealBlockedFrames;
    }
}
