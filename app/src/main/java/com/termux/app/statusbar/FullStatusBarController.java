package com.termux.app.statusbar;

import android.view.Choreographer;

import androidx.annotation.NonNull;

import com.termux.app.Spring;

/** One-spring controller for real FULL pane geometry and settle-only terminal resize delivery. */
public final class FullStatusBarController {
    public enum Motion { IDLE, OPENING, FULL, CLOSING, DRAGGING }

    /** Fraction of the full travel past which a released drag commits to FULL. */
    public static final float DRAG_COMMIT_PROGRESS = 0.35f;
    /** Release velocity, as full-travels per second, that commits regardless of progress. */
    public static final float DRAG_COMMIT_VELOCITY_TRAVELS = 2.0f;
    /** Upward release velocity, as full-travels per second, that dismisses regardless. */
    public static final float DRAG_DISMISS_VELOCITY_TRAVELS = 1.0f;

    public interface Host {
        int currentHeight();
        int normalHeight(@NonNull TopStatusBarState state);
        int parentMeasuredHeight();
        int parentPaddingTop();
        int parentPaddingBottom();
        int hostTopMargin();
        boolean reducedMotion();
        void cancelNormalAnimatorKeepingCurrent();
        void beginTerminalResize();
        void applyFrame(int height, float fullProgress);
        void finishTerminalResizeAfterLayout();
        void applyNormalState(@NonNull TopStatusBarState state);
        void onEngagementChanged(boolean engaged, @NonNull TopStatusBarState normalTarget);
        default void onFullSettled(boolean settled) { }
    }

    public interface FrameScheduler {
        void post(@NonNull Runnable frame);
        void remove(@NonNull Runnable frame);
        long nowNanos();
    }

    private final Host host;
    private final FrameScheduler scheduler;
    /** The only motion channel owned by this controller. */
    private final Spring progress = new Spring(0f, 420f, 41f);
    private final Runnable frame = this::doFrame;
    @NonNull private Motion motion = Motion.IDLE;
    @NonNull private TopStatusBarState prior = TopStatusBarState.EXPANDED;
    private int segmentStartHeight;
    private int segmentTargetHeight;
    private int dragStartHeight;
    private long lastFrameNanos;
    private boolean framePosted;
    private boolean resizeOpen;

    public FullStatusBarController(@NonNull Host host) {
        this(host, new ChoreographerScheduler());
    }

    public FullStatusBarController(@NonNull Host host, @NonNull FrameScheduler scheduler) {
        this.host = host;
        this.scheduler = scheduler;
    }

    @NonNull public Motion motion() { return motion; }
    @NonNull public TopStatusBarState priorState() { return prior; }
    public boolean isEngaged() { return motion != Motion.IDLE; }
    public Spring springForTests() { return progress; }

    public boolean open(@NonNull TopStatusBarState capturedPrior) {
        if (isEngaged() || capturedPrior == TopStatusBarState.FULL) return false;
        prior = capturedPrior;
        segmentStartHeight = Math.max(0, host.currentHeight());
        motion = Motion.OPENING;
        host.onEngagementChanged(true, prior);
        // Latch FULL before cancellation: the old animator's cancel/end callbacks are reentrant
        // and must see the new owner, otherwise they can snap the captured in-flight height.
        host.cancelNormalAnimatorKeepingCurrent();
        segmentTargetHeight = resolveFullHeight();
        beginResizeIfNeeded();
        startSegment();
        return true;
    }

    /**
     * A pull-down owns the pane 1:1: the bar's height tracks the finger until release, when
     * {@link #dragEnd} springs it to FULL or back to the captured prior form.
     */
    public boolean dragBegin(@NonNull TopStatusBarState capturedPrior) {
        if (isEngaged() || capturedPrior == TopStatusBarState.FULL) return false;
        prior = capturedPrior;
        dragStartHeight = Math.max(0, host.currentHeight());
        motion = Motion.DRAGGING;
        host.onEngagementChanged(true, prior);
        host.cancelNormalAnimatorKeepingCurrent();
        beginResizeIfNeeded();
        cancelFrame();
        return true;
    }

    public void dragUpdate(float dragPx) {
        if (motion != Motion.DRAGGING) return;
        int full = resolveFullHeight();
        int normal = host.normalHeight(prior);
        int floor = Math.min(normal, dragStartHeight);
        int height = Math.max(floor, Math.min(full,
            Math.round(dragStartHeight + (Float.isFinite(dragPx) ? dragPx : 0f))));
        FullStatusBarGeometry.Frame geometry = FullStatusBarGeometry.calculate(normal,
            host.parentMeasuredHeight(), host.parentPaddingTop(), host.parentPaddingBottom(),
            host.hostTopMargin(), FullStatusBarGeometry.progressForHeight(height, normal, full));
        host.applyFrame(height < normal ? height : geometry.height, geometry.progress);
    }

    public void dragEnd(float velocityPxPerSec) {
        if (motion != Motion.DRAGGING) return;
        int full = resolveFullHeight();
        int normal = host.normalHeight(prior);
        float travel = Math.max(1f, full - normal);
        float progress = FullStatusBarGeometry.progressForHeight(host.currentHeight(),
            normal, full);
        float velocity = Float.isFinite(velocityPxPerSec) ? velocityPxPerSec : 0f;
        boolean flingOpen = velocity > travel * DRAG_COMMIT_VELOCITY_TRAVELS;
        boolean flingClose = velocity < -travel * DRAG_DISMISS_VELOCITY_TRAVELS;
        boolean commit = !flingClose && (flingOpen || progress >= DRAG_COMMIT_PROGRESS);
        segmentStartHeight = Math.max(0, host.currentHeight());
        if (commit) {
            segmentTargetHeight = full;
            motion = Motion.OPENING;
        } else {
            segmentTargetHeight = normal;
            motion = Motion.CLOSING;
            host.onFullSettled(false);
        }
        startSegment();
    }

    /** With FULL settled, a pull-up owns the close 1:1; {@link #dragEnd} decides at release. */
    public boolean dragBeginClose() {
        if (motion != Motion.FULL) return false;
        dragStartHeight = Math.max(0, host.currentHeight());
        motion = Motion.DRAGGING;
        host.onFullSettled(false);
        beginResizeIfNeeded();
        cancelFrame();
        return true;
    }

    public void dragCancel() {
        if (motion != Motion.DRAGGING) return;
        segmentStartHeight = Math.max(0, host.currentHeight());
        segmentTargetHeight = host.normalHeight(prior);
        motion = Motion.CLOSING;
        host.onFullSettled(false);
        startSegment();
    }

    /** FULL is the first Back consumer, including repeated Back while closing. */
    public boolean onBackPressed() {
        if (!isEngaged()) return false;
        if (motion == Motion.CLOSING) return true;
        segmentStartHeight = Math.max(0, host.currentHeight());
        segmentTargetHeight = host.normalHeight(prior);
        motion = Motion.CLOSING;
        host.onFullSettled(false);
        beginResizeIfNeeded();
        startSegment();
        return true;
    }

    /** Follow a parent/accessory relayout without initiating any accessory geometry operation. */
    public void onParentLayoutChanged() {
        if (!isEngaged()) return;
        int full = resolveFullHeight();
        if (motion == Motion.OPENING) {
            int current = host.currentHeight();
            segmentStartHeight = current;
            segmentTargetHeight = full;
            startSegment();
        } else if (motion == Motion.FULL && host.currentHeight() != full) {
            beginResizeIfNeeded();
            host.applyFrame(full, 1f);
            finishResizeIfNeeded();
        }
    }

    public void restoreFullImmediate(@NonNull TopStatusBarState capturedPrior) {
        if (capturedPrior == TopStatusBarState.FULL) return;
        prior = capturedPrior;
        motion = Motion.FULL;
        host.onEngagementChanged(true, prior);
        beginResizeIfNeeded();
        host.applyFrame(resolveFullHeight(), 1f);
        host.onFullSettled(true);
        finishResizeIfNeeded();
    }

    public void closeImmediateToPrior() {
        if (!isEngaged()) return;
        host.onFullSettled(false);
        cancelFrame();
        beginResizeIfNeeded();
        host.applyFrame(host.normalHeight(prior), 0f);
        // Disengage before the normal-state restore: the normal-state writers it re-runs are
        // gated off while FULL is engaged, so they must observe IDLE to re-anchor the status row.
        motion = Motion.IDLE;
        host.applyNormalState(prior);
        finishResizeIfNeeded();
        host.onEngagementChanged(false, prior);
    }

    private void startSegment() {
        progress.reset(0f);
        progress.target = 1f;
        lastFrameNanos = scheduler.nowNanos();
        if (host.reducedMotion()) {
            applySegment(1f);
            settle();
        } else {
            postFrame();
        }
    }

    private void doFrame() {
        framePosted = false;
        long now = scheduler.nowNanos();
        float dt = Spring.clampDelta((now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        boolean moving = progress.tick(false, dt);
        applySegment(progress.value);
        if (moving) postFrame(); else settle();
    }

    private void applySegment(float value) {
        float p = Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 1f;
        int height = Math.round(segmentStartHeight
            + (segmentTargetHeight - segmentStartHeight) * p);
        int normal = host.normalHeight(prior);
        FullStatusBarGeometry.Frame geometry = FullStatusBarGeometry.calculate(normal,
            host.parentMeasuredHeight(), host.parentPaddingTop(), host.parentPaddingBottom(),
            host.hostTopMargin(), FullStatusBarGeometry.progressForHeight(height, normal,
                resolveFullHeight()));
        // Preserve a no-snap takeover below the recorded prior endpoint while the segment enters.
        int applied = motion == Motion.OPENING && height < normal ? height : geometry.height;
        host.applyFrame(applied, geometry.progress);
    }

    private void settle() {
        cancelFrame();
        if (motion == Motion.OPENING) {
            host.applyFrame(resolveFullHeight(), 1f);
            motion = Motion.FULL;
            host.onFullSettled(true);
            finishResizeIfNeeded();
        } else if (motion == Motion.CLOSING) {
            host.applyFrame(host.normalHeight(prior), 0f);
            // Disengage before the normal-state restore (see closeImmediateToPrior()).
            motion = Motion.IDLE;
            host.applyNormalState(prior);
            finishResizeIfNeeded();
            host.onEngagementChanged(false, prior);
        }
    }

    private int resolveFullHeight() {
        return Math.max(host.normalHeight(prior), FullStatusBarGeometry.resolveFullHeight(
            host.parentMeasuredHeight(), host.parentPaddingTop(), host.parentPaddingBottom(),
            host.hostTopMargin()));
    }

    private float currentFullProgress(int full) {
        return FullStatusBarGeometry.progressForHeight(host.currentHeight(),
            host.normalHeight(prior), full);
    }

    private void beginResizeIfNeeded() {
        if (resizeOpen) return;
        resizeOpen = true;
        host.beginTerminalResize();
    }

    private void finishResizeIfNeeded() {
        if (!resizeOpen) return;
        resizeOpen = false;
        host.finishTerminalResizeAfterLayout();
    }

    private void postFrame() {
        if (framePosted) return;
        framePosted = true;
        scheduler.post(frame);
    }

    private void cancelFrame() {
        if (framePosted) scheduler.remove(frame);
        framePosted = false;
    }

    private static final class ChoreographerScheduler implements FrameScheduler {
        private final Choreographer choreographer = Choreographer.getInstance();
        private final java.util.IdentityHashMap<Runnable, Choreographer.FrameCallback> callbacks =
            new java.util.IdentityHashMap<>();

        @Override public void post(@NonNull Runnable frame) {
            Choreographer.FrameCallback callback = ignored -> {
                callbacks.remove(frame);
                frame.run();
            };
            callbacks.put(frame, callback);
            choreographer.postFrameCallback(callback);
        }
        @Override public void remove(@NonNull Runnable frame) {
            Choreographer.FrameCallback callback = callbacks.remove(frame);
            if (callback != null) choreographer.removeFrameCallback(callback);
        }
        @Override public long nowNanos() { return System.nanoTime(); }
    }
}
