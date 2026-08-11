package com.termux.app.statusbar;

import android.view.Choreographer;

import androidx.annotation.NonNull;

import com.termux.app.Spring;

/** One-spring controller for real FULL pane geometry and settle-only terminal resize delivery. */
public final class FullStatusBarController {
    public enum Motion { IDLE, OPENING, FULL, CLOSING }

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

    /** FULL is the first Back consumer, including repeated Back while closing. */
    public boolean onBackPressed() {
        if (!isEngaged()) return false;
        if (motion == Motion.CLOSING) return true;
        segmentStartHeight = Math.max(0, host.currentHeight());
        segmentTargetHeight = host.normalHeight(prior);
        motion = Motion.CLOSING;
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
        finishResizeIfNeeded();
    }

    public void closeImmediateToPrior() {
        if (!isEngaged()) return;
        cancelFrame();
        beginResizeIfNeeded();
        host.applyFrame(host.normalHeight(prior), 0f);
        host.applyNormalState(prior);
        motion = Motion.IDLE;
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
            finishResizeIfNeeded();
        } else if (motion == Motion.CLOSING) {
            host.applyFrame(host.normalHeight(prior), 0f);
            host.applyNormalState(prior);
            motion = Motion.IDLE;
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
