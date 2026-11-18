package com.termux.app.terminal.inappkeyboard;

import android.util.DisplayMetrics;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.ChromeSpec;

import juloo.keyboard2.Keyboard2View;

/**
 * The in-app keyboard's geometry: its measurement against a stable root and the visibility request.
 *
 * <p>The keyboard's height has to be measured against a stable root (never against the accessory
 * stack it is about to resize, or the two {@code AT_MOST} passes chase each other). Showing it is
 * one call, {@link #onVisibilityRequested(boolean)}: the container is visible on the frame it is
 * asked for, and the chrome's normal render pass cuts the crops. There are no gates and no close
 * correction.</p>
 *
 * <p>Callers state <em>what happened</em> and everything the module needs from the Activity goes
 * through {@link Surface}, which is what makes the protocol assertable without a window. The chrome
 * <em>painters</em> stay on the Activity (they are welded to the glass factory, the backdrop bitmaps
 * and the dock layout) and are invoked through {@link Surface}. What lives here is the measurement
 * cache and the system-IME gate.</p>
 */
public final class KeyboardGeometryChoreographer {

    /** Slider events can outrun display frames; collapse them to one geometry pass per frame. */
    static final long PREVIEW_GEOMETRY_SYNC_MS = 16L;

    /**
     * The bounds a keyboard hosted outside the accessory stack is measured against — the floating
     * frame's own width and the room it may be placed in.
     *
     * <p>The keyboard's fractional height cap needs one stable reference or its two {@code AT_MOST}
     * passes chase each other. Docked, that reference is the content root. Floating, it is the
     * frame: the width is a share of the content rather than all of it, and the height the cap is a
     * fraction of is the room the frame can be dragged around in.</p>
     */
    public static final class HostReference {

        public final int widthPx;
        public final int availableHeightPx;

        public HostReference(int widthPx, int availableHeightPx) {
            this.widthPx = Math.max(0, widthPx);
            this.availableHeightPx = Math.max(0, availableHeightPx);
        }
    }

    /** The Activity-side slots, painters and lookups the choreography drives. */
    public interface Surface {

        /** Resolves a view slot by id; null before inflation, or when the slot is absent. */
        @Nullable View findView(int viewId);

        /**
         * The floating frame the keyboard is hosted in, or null while it is docked in the accessory
         * stack. A floating keyboard is measured and laid out by its frame, so it contributes no
         * height to the stack and takes no room from the place under it.
         */
        @Nullable default HostReference floatingKeyboardReference() {
            return null;
        }

        @NonNull DisplayMetrics displayMetrics();

        /**
         * Dresses the keyboard's host in the margins and padding its card or frame slice wears for
         * the form, style and chin in force now. Run before every measure, so the height the stack
         * reserves includes them however the form or the chrome changed since the last render.
         *
         * @return true when anything changed, which makes a memoized height stale
         */
        default boolean dressKeyboardHost() {
            return false;
        }

        /**
         * Whether the docked in-app keyboard stays laid out while it is down: the in-app
         * keyboard is on, it is not switched off, not floating, and the system IME is not the
         * input. Then "down" is the stack scene's translation, never GONE.
         */
        default boolean keyboardStaysLaidOut() {
            return false;
        }

        /** The keyboard went up or down while laid out: the stack's renderer writes its rest. */
        default void requestStackRest() {}

        /** The keyboard view currently attached to the host, or null while detached. */
        @Nullable View attachedKeyboardView();

        // ---- the chrome render pass

        @NonNull ChromeSpec buildChromeSpec();

        /** Paints the keyboard's own margins, clip, glass and backdrop for this spec. */
        void applyKeyboardSurfaceState(@NonNull ChromeSpec spec);

        /** Requests a coalesced accessory re-render. */
        void requestAccessoryRenderSync();

        /**
         * Books the whole chrome apply for the frame this request lands in, before its draw: the
         * keyboard's surface is dressed there with the rest of the chrome rather than on its own.
         * Without a frame to ride, the keyboard is dressed at once.
         */
        default void requestApplyThisFrame() {
            applyKeyboardSurfaceState(buildChromeSpec());
        }

        /**
         * Re-lays out the accessory stack around the keyboard, for {@code reason}, before the next
         * traversal.
         */
        void applyAccessoryGeometry(@NonNull String reason);

        // ---- crop invalidation the transition needs

        /** Every geometry-dependent crop, rects included; the shared blur frames are preserved. */
        void invalidateTransitionCrops();

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

    // ---- visibility state

    /** The keyboard is down but still laid out ({@link Surface#keyboardStaysLaidOut}). */
    private boolean mDownLaidOut;

    // ---- measurement cache

    private boolean mHeightDirty = true;
    /** The bounds the put-away keyboard was last pre-measured against ({@link #prewarmMeasure}). */
    private boolean mPrewarmed;
    private int mPrewarmWidthPx;
    private int mPrewarmHeightPx;
    private int mDesiredHeightPx;
    private int mMeasureWidthPx;
    private int mAvailableHeightPx;
    private boolean mPreviewGeometrySyncPosted;
    /** Last height-cap reference handed to the keyboard view, for tests. */
    private int mHeightCapReferencePx;
    /** Last {@code keyboardShown} the accessory stack was actually laid out for. */
    private boolean mAppliedKeyboardShown;
    /** The keyboard height the accessory stack was last sized to hold; 0 while it holds none. */
    private int mReservedHeightPx;
    /** The reservation and laid-out height of the last miss a correction was booked for. */
    private int mMissedReservationPx = -1;
    private int mMissedLaidOutPx = -1;

    // ---- system-IME gate

    private boolean mLastImeVisible;

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
        // The host's margins and padding are part of the height; measured undressed, the stack
        // reserves a keyboard shorter than the one it lays out and the dock rows are squeezed.
        if (mSurface.dressKeyboardHost())
            mHeightDirty = true;
        HostReference floating = mSurface.floatingKeyboardReference();
        int width = boundsWidthPx(floating);
        int availableHeight = boundsHeightPx(floating);
        mHeightCapReferencePx = availableHeight;
        View attached = mSurface.attachedKeyboardView();
        if (attached instanceof Keyboard2View) {
            // The keyboard is measured here against the full content root, but RelativeLayout later
            // measures it inside the shorter exact accessory stack. Keep its fractional height cap
            // tied to this stable root height so both AT_MOST passes resolve identically. A floating
            // keyboard is measured inside its frame instead, so the frame is the reference there.
            ((Keyboard2View) attached).setHeightCapReferencePx(Math.max(0, availableHeight));
        }
        if (floating != null) {
            // The frame lays the keyboard out itself, over the place rather than inside the stack.
            // Its height is therefore not the stack's to reserve and not the content's to give back.
            return 0;
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

    /** The width the keyboard is measured against: the floating frame's, else the content root's. */
    private int boundsWidthPx(@Nullable HostReference floating) {
        View availableRoot = mSurface.findView(R.id.activity_termux_root_relative_layout);
        int width = floating != null ? floating.widthPx
            : availableRoot != null ? availableRoot.getWidth() : 0;
        return width > 0 ? width : mSurface.displayMetrics().widthPixels;
    }

    /** The height the keyboard is measured against, as {@link #boundsWidthPx}. */
    private int boundsHeightPx(@Nullable HostReference floating) {
        View availableRoot = mSurface.findView(R.id.activity_termux_root_relative_layout);
        int height = floating != null ? floating.availableHeightPx
            : availableRoot != null
            ? com.termux.app.place.BottomInsetFrameLayout.roomHeightPx(availableRoot) : 0;
        return height > 0 ? height : mSurface.displayMetrics().heightPixels;
    }

    /**
     * Whether a hidden keyboard's measure is worth doing now: the container is put away (GONE),
     * it is the stack's (not a floating frame's) and the bounds are not the ones last measured.
     */
    static boolean needsPrewarm(boolean containerGone, boolean floating, boolean warm,
                                int warmWidthPx, int warmHeightPx, int widthPx, int heightPx) {
        return containerGone && !floating && widthPx > 0 && heightPx > 0
            && !(warm && warmWidthPx == widthPx && warmHeightPx == heightPx);
    }

    /**
     * Measures the put-away keyboard against the bounds it will be shown at, so that the show
     * the swipe's first frame asks for finds the keys' measure cached by Android's same-spec
     * cache and lays out nothing from scratch. The keyboard stays GONE (the stack, the insets
     * and the touches never see it) and the height memo is left as it is: this is the same
     * measure {@link #measureHeightPx} makes, on the same specs, without its answer being kept.
     *
     * @return whether a measure ran
     */
    public boolean prewarmMeasure() {
        View keyboardContainer = mSurface.findView(R.id.inapp_keyboard_container);
        if (keyboardContainer == null) return false;
        HostReference floating = mSurface.floatingKeyboardReference();
        int width = boundsWidthPx(floating);
        int availableHeight = boundsHeightPx(floating);
        if (!needsPrewarm(keyboardContainer.getVisibility() == View.GONE, floating != null,
                mPrewarmed, mPrewarmWidthPx, mPrewarmHeightPx, width, availableHeight)) {
            return false;
        }
        android.os.Trace.beginSection("Keyboard.prewarmMeasure");
        try {
            View attached = mSurface.attachedKeyboardView();
            if (attached instanceof Keyboard2View)
                ((Keyboard2View) attached).setHeightCapReferencePx(Math.max(0, availableHeight));
            keyboardContainer.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(availableHeight, View.MeasureSpec.AT_MOST));
        } finally {
            android.os.Trace.endSection();
        }
        mPrewarmed = true;
        mPrewarmWidthPx = width;
        mPrewarmHeightPx = availableHeight;
        return true;
    }

    /** The last measured desired height, without re-measuring. */
    public int desiredHeightPx() {
        return mDesiredHeightPx;
    }

    /**
     * Marks the cached height stale while keeping the last measured value readable. The surface
     * painters use this: a margin or padding change makes the height wrong, but readers that only
     * want the last answer (the shape model before the host has laid out) keep one.
     */
    public void markHeightDirty() {
        mHeightDirty = true;
    }

    /** Marks the height stale and forgets the measured value with it. */
    public void discardMeasuredHeight() {
        mPrewarmed = false;
        mDesiredHeightPx = 0;
        mHeightDirty = true;
    }

    /**
     * Drops the whole measurement memo, bounds key included. The cache is keyed by the available
     * bounds, which do not change while the user previews keyboard geometry, so a preview has to
     * invalidate the key too.
     */
    public void invalidateMeasurement() {
        mPrewarmed = false;
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

    /**
     * Re-measures the keyboard and re-lays out the accessory stack around its new height, once.
     * The height is measured independently of the stack ({@link #measureHeightPx}), so the pass
     * has the answer the container's own layout will reach; a laid-out height that still disagrees
     * with what the stack reserved ({@link #reservationMissedBy}) is caught by the container's
     * layout listener, which asks for its own pass.
     */
    public void requestGeometrySync() {
        View keyboardContainer = mSurface.findView(R.id.inapp_keyboard_container);
        discardMeasuredHeight();
        if (keyboardContainer != null)
            keyboardContainer.requestLayout();
        mSurface.applyAccessoryGeometry("inapp-keyboard");
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

    /** Records the keyboard height the accessory stack was just sized to hold; 0 for none. */
    public void noteReservedHeightPx(int reservedHeightPx) {
        mReservedHeightPx = Math.max(0, reservedHeightPx);
    }

    /** The keyboard height the accessory stack was last sized to hold. */
    public int reservedHeightPx() {
        return mReservedHeightPx;
    }

    /**
     * Whether the docked keyboard just laid out at a height the stack did not reserve for it, and
     * no correction has been booked for this miss yet. Asked on every layout of the container,
     * resized or not: a keyboard that keeps its height across a form switch while the reservation
     * changed under it is still a miss. A miss answers true once, so a reservation that cannot
     * be met costs one geometry pass, not one per layout; meeting it re-arms the check.
     */
    public boolean reservationMissedBy(int laidOutHeightPx) {
        if (mReservedHeightPx <= 0 || laidOutHeightPx == mReservedHeightPx) {
            mMissedReservationPx = -1;
            mMissedLaidOutPx = -1;
            return false;
        }
        if (mMissedReservationPx == mReservedHeightPx && mMissedLaidOutPx == laidOutHeightPx)
            return false;
        mMissedReservationPx = mReservedHeightPx;
        mMissedLaidOutPx = laidOutHeightPx;
        return true;
    }

    // ------------------------------------------------------------ visibility request

    /**
     * The keyboard's visibility was requested. On show the container is visible in this call; on
     * close it is put down laid out or GONE. Crops are invalidated and the chrome's render pass
     * rebuilds them in the normal frame.
     */
    public void onVisibilityRequested(boolean visible) {
        onVisibilityRequested(visible, false);
    }

    /**
     * The same, for a keyboard that opens {@code belowScreen}: brought up by a slide's travel
     * (a seam crossing's pre-roll) and held below the screen by its translation until the wall
     * brings it up. Nothing of it shows before the frame's own chrome apply, which dresses it with
     * the rest of the chrome, so it is not dressed inside the request.
     */
    public void onVisibilityRequested(boolean visible, boolean belowScreen) {
        View keyboardContainer = mSurface.findView(R.id.inapp_keyboard_container);
        if (keyboardContainer == null) {
            return;
        }
        if (visible) {
            boolean openingFromGone = keyboardContainer.getVisibility() == View.GONE || mDownLaidOut;
            mDownLaidOut = false;
            if (openingFromGone)
                mSurface.invalidateTransitionCrops();
            keyboardContainer.setVisibility(View.VISIBLE);
            if (!dressesOnRequest(belowScreen)) {
                // Dressing it here as well cost the travel's first frame 7-12 ms on pong
                // (Chrome.kb.host inside Keyboard.setContainerVisible), for a dress that the
                // frame's apply then made again.
                mSurface.requestApplyThisFrame();
                if (openingFromGone) mSurface.requestAccessoryRenderSync();
                mSurface.requestStackRest();
                return;
            }
            mSurface.applyKeyboardSurfaceState(mSurface.buildChromeSpec());
            // The crops cut for the stack without the keyboard are rebuilt by this frame's render,
            // so nothing waits for them.
            if (openingFromGone) mSurface.requestAccessoryRenderSync();
            mSurface.requestStackRest();
        } else if (mSurface.keyboardStaysLaidOut()) {
            // Down is the scene's translation: the container is never put away, so it is measured
            // and laid out at its full height and a landing writes nothing for it.
            boolean closing = !mDownLaidOut;
            if (closing)
                mSurface.invalidateTransitionCrops();
            mDownLaidOut = true;
            if (keyboardContainer.getVisibility() != View.VISIBLE)
                keyboardContainer.setVisibility(View.VISIBLE);
            mSurface.onKeyboardClosed();
            mSurface.requestStackRest();
            if (closing)
                mSurface.requestAccessoryRenderSync();
        } else {
            mDownLaidOut = false;
            boolean closingToGone = keyboardContainer.getVisibility() != View.GONE;
            if (closingToGone)
                mSurface.invalidateTransitionCrops();
            keyboardContainer.setVisibility(View.GONE);
            mSurface.onKeyboardClosed();
            if (closingToGone)
                mSurface.requestAccessoryRenderSync();
        }
    }

    /**
     * Whether an open dresses the keyboard's surface inside the request. One the travel brings up
     * {@code belowScreen} does not: no frame shows it before that frame's chrome apply dresses it.
     */
    static boolean dressesOnRequest(boolean belowScreen) {
        return !belowScreen;
    }

    /**
     * The Activity is stopping: drop the pending preview geometry sync.
     */
    public void onStop() {
        mSurface.removeCallbacks(mPreviewGeometrySyncRunnable);
        mPreviewGeometrySyncPosted = false;
    }

    // ------------------------------------------------------------- system-IME gate

    /** Asks for the insets to be re-delivered (a system-keyboard request, see {@code TermuxActivity}). */
    public void requestApplyInsets() {
        mSurface.requestApplyInsets();
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

    /** The stable available height the keyboard's fractional height cap was last measured from. */
    int heightCapReferencePx() {
        return mHeightCapReferencePx;
    }
}
