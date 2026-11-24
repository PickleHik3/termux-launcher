package com.termux.app.chrome;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;

import com.termux.app.window.FramePipeline;
import com.termux.app.window.FrameStep;

import java.util.ArrayList;
import java.util.List;

/**
 * The Home exit's one animator (ADR 0034): the views of every surface that leaves fade and shrink
 * slightly as one group, alpha and scale only, never a layout. While it runs it is the only
 * writer of those views' alpha and scale.
 *
 * <p>Its end runs once, whichever way it comes: the last frame, {@link #finishNow} (a second home
 * press), {@link #cancel}, or a backstop. The backstops mirror the keyboard reveal gate's: no
 * frame at all within {@link #FIRST_FRAME_MS}, or the time bound {@link #DURATION_MS} plus
 * {@link #BACKSTOP_SLACK_MS}, so a window that never draws cannot leave a surface half faded. The
 * end callback closes every member; the views are then put back to the alpha and scale they had
 * at the start, so a surface that opens again finds them at rest.
 *
 * <p>Per frame nothing is allocated: the views and their rest values are copied once at start.
 */
public final class HomeExit {

    public static final long DURATION_MS = 250L;
    /** No frame by now: the window is not drawing; end it. */
    public static final long FIRST_FRAME_MS = 100L;
    /** Past the duration, how long a stalled run waits for its last frame. */
    public static final long BACKSTOP_SLACK_MS = 120L;
    /** How much of its size the group gives up by the end. */
    static final float SCALE_DROP = 0.04f;

    /** One member's rest values sit side by side in {@code mRest}: alpha, scale X, scale Y. */
    private static final int REST_STRIDE = 3;
    private static final int REST_ALPHA = 0;
    private static final int REST_SCALE_X = 1;
    private static final int REST_SCALE_Y = 2;

    /** The clock and frame source; the test drives its own. */
    public interface Frames {
        long nowMs();
        void requestFrame(@NonNull Runnable callback);
        void removeFrame(@NonNull Runnable callback);
        void postDelayed(@NonNull Runnable runnable, long delayMs);
        void removeDelayed(@NonNull Runnable runnable);
    }

    /**
     * The window's frame pipeline for the ticks (its {@link FrameStep#HOME_EXIT} step, so no
     * frame hook of its own) and the main looper's timers for the backstops.
     */
    public static Frames pipelineFrames(@NonNull FramePipeline pipeline) {
        final Handler handler = new Handler(Looper.getMainLooper());
        return new Frames() {
            private Runnable mTarget;
            private final Runnable mStep = () -> {
                Runnable target = mTarget;
                mTarget = null;
                if (target != null) target.run();
            };

            @Override public long nowMs() {
                return android.os.SystemClock.uptimeMillis();
            }

            @Override public void requestFrame(@NonNull Runnable callback) {
                mTarget = callback;
                pipeline.bind(FrameStep.HOME_EXIT, mStep);
                pipeline.requestFrame(FrameStep.HOME_EXIT);
            }

            @Override public void removeFrame(@NonNull Runnable callback) {
                mTarget = null;
            }

            @Override public void postDelayed(@NonNull Runnable runnable, long delayMs) {
                handler.postDelayed(runnable, delayMs);
            }

            @Override public void removeDelayed(@NonNull Runnable runnable) {
                handler.removeCallbacks(runnable);
            }
        };
    }

    @NonNull private final Frames mFrames;
    @NonNull private final List<View> mViews = new ArrayList<>();
    private float[] mRest = new float[0];
    private Runnable mEnd;
    private boolean mRunning;
    private long mStartMs;
    private int mFrameCount;

    private final Runnable mOnFrame = this::onFrame;
    private final Runnable mFirstFrameBackstop = this::onFirstFrameBackstop;
    private final Runnable mTimeBackstop = this::finishNow;

    public HomeExit(@NonNull Frames frames) {
        mFrames = frames;
    }

    public boolean isRunning() {
        return mRunning;
    }

    /**
     * Starts the exit over {@code views}; {@code end} closes the members and runs once. With
     * reduced motion, or no views, the end state comes at once. A run still going is finished
     * first.
     */
    public void start(@NonNull List<View> views, boolean reducedMotion, @NonNull Runnable end) {
        if (mRunning) finishNow();
        mViews.clear();
        mViews.addAll(views);
        mEnd = end;
        mRunning = true;
        if (reducedMotion || mViews.isEmpty()) {
            // Nothing was moved, so nothing is put back.
            mViews.clear();
            finishNow();
            return;
        }
        int n = mViews.size();
        if (mRest.length < n * REST_STRIDE) mRest = new float[n * REST_STRIDE];
        for (int i = 0; i < n; i++) {
            View v = mViews.get(i);
            // The exit is the only writer from here: a view's own entry or exit animation must
            // not keep writing beside it, nor have its transient value read as the rest.
            v.animate().cancel();
            int at = i * REST_STRIDE;
            mRest[at + REST_ALPHA] = v.getAlpha();
            mRest[at + REST_SCALE_X] = v.getScaleX();
            mRest[at + REST_SCALE_Y] = v.getScaleY();
        }
        mStartMs = mFrames.nowMs();
        mFrameCount = 0;
        mFrames.postDelayed(mFirstFrameBackstop, FIRST_FRAME_MS);
        mFrames.postDelayed(mTimeBackstop, DURATION_MS + BACKSTOP_SLACK_MS);
        mFrames.requestFrame(mOnFrame);
    }

    /** A second home press, or any reason to stop: the end state at once. */
    public void finishNow() {
        if (!mRunning) return;
        mRunning = false;
        mFrames.removeFrame(mOnFrame);
        mFrames.removeDelayed(mFirstFrameBackstop);
        mFrames.removeDelayed(mTimeBackstop);
        Runnable end = mEnd;
        mEnd = null;
        try {
            if (end != null) end.run();
        } finally {
            int n = mViews.size();
            for (int i = 0; i < n; i++) {
                View v = mViews.get(i);
                int at = i * REST_STRIDE;
                v.setAlpha(mRest[at + REST_ALPHA]);
                v.setScaleX(mRest[at + REST_SCALE_X]);
                v.setScaleY(mRest[at + REST_SCALE_Y]);
            }
            mViews.clear();
        }
    }

    /** The animation is dropped: the same end as the last frame. */
    public void cancel() {
        finishNow();
    }

    private void onFirstFrameBackstop() {
        if (mRunning && mFrameCount == 0) finishNow();
    }

    private void onFrame() {
        if (!mRunning) return;
        mFrameCount++;
        long elapsed = mFrames.nowMs() - mStartMs;
        if (elapsed >= DURATION_MS) {
            finishNow();
            return;
        }
        float t = elapsed <= 0 ? 0f : (float) elapsed / DURATION_MS;
        float inv = 1f - t;
        float eased = 1f - inv * inv;
        float alpha = 1f - eased;
        float scale = 1f - SCALE_DROP * eased;
        int n = mViews.size();
        for (int i = 0; i < n; i++) {
            View v = mViews.get(i);
            int at = i * REST_STRIDE;
            v.setAlpha(mRest[at + REST_ALPHA] * alpha);
            v.setScaleX(mRest[at + REST_SCALE_X] * scale);
            v.setScaleY(mRest[at + REST_SCALE_Y] * scale);
        }
        mFrames.requestFrame(mOnFrame);
    }
}
