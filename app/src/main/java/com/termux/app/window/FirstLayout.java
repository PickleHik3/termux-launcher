package com.termux.app.window;

import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.BooleanSupplier;

/**
 * The one helper for a sheet, popup, widget or gate that only waits for its own first layout or
 * draw (ADR 0028 section 3). It is not part of the window's frame order: it never cancels a draw
 * and holds nothing, it runs its callback once the view's window is about to draw (or has drawn,
 * or laid the view out) and lets go of its listener. A wait on something delivered late is a
 * {@link Hold} instead.
 *
 * <p>Every wait hands back a {@link Handle}; cancelling it is how a caller that closed or detached
 * first drops the wait.</p>
 */
public final class FirstLayout {

    private FirstLayout() {
    }

    /** Runs {@code action} once, in the pre-draw pass of the view's window, before that frame draws. */
    @NonNull
    public static Handle beforeNextDraw(@NonNull View view, @NonNull Runnable action) {
        Handle handle = new Handle(view, Handle.BEFORE_DRAW, action, null);
        handle.listenPreDraw();
        return handle;
    }

    /**
     * Calls {@code done} in each pre-draw pass until it returns true, then lets go. For a wait
     * whose end is a state (a view measurable, a layout settled) rather than the next frame.
     */
    @NonNull
    public static Handle beforeEachDrawUntil(@NonNull View view, @NonNull BooleanSupplier done) {
        Handle handle = new Handle(view, Handle.UNTIL, null, done);
        handle.listenPreDraw();
        return handle;
    }

    /**
     * Posts {@code action} on the view's own frame clock, ahead of its next traversal: for a view's
     * own redraw pacing. Off a window it waits in the view's queue until an attach.
     */
    public static void nextFrame(@NonNull View view, @NonNull Runnable action) {
        view.postOnAnimation(action);
    }

    /**
     * Runs {@code action} once, when the view's window has drawn (inside the draw callback, so it
     * may only post work). The listener is let go on the next main-loop turn.
     */
    @NonNull
    public static Handle afterNextDraw(@NonNull View view, @NonNull Runnable action) {
        Handle handle = new Handle(view, Handle.AFTER_DRAW, action, null);
        ViewTreeObserver observer = view.getViewTreeObserver();
        handle.mObserver = observer;
        observer.addOnDrawListener(handle);
        return handle;
    }

    /**
     * Runs {@code action} once, on the main loop after the view's next layout, or when
     * {@code backstopMs} has passed with none: a layout that never comes must not swallow the
     * action.
     */
    @NonNull
    public static Handle afterNextLayout(@NonNull View view, long backstopMs, @NonNull Runnable action) {
        return afterNextLayout(view, backstopMs, byBackstop -> action.run());
    }

    /** What {@link #afterNextLayout(View, long, LayoutAction)} runs, told which way it came. */
    public interface LayoutAction {
        /** {@code byBackstop}: no layout came and the backstop ran it; false: the layout did. */
        void run(boolean byBackstop);
    }

    /** As {@link #afterNextLayout(View, long, Runnable)}, telling the action what released it. */
    @NonNull
    public static Handle afterNextLayout(@NonNull View view, long backstopMs,
                                         @NonNull LayoutAction action) {
        Handle handle = new Handle(view, Handle.AFTER_LAYOUT, null, null);
        handle.mLayoutAction = action;
        view.addOnLayoutChangeListener(handle);
        view.postDelayed(handle.mFireBackstop, backstopMs);
        return handle;
    }

    /** One wait; {@link #cancel} drops it. */
    public static final class Handle implements ViewTreeObserver.OnPreDrawListener,
        ViewTreeObserver.OnDrawListener, View.OnLayoutChangeListener {

        static final int BEFORE_DRAW = 0;
        static final int UNTIL = 1;
        static final int AFTER_DRAW = 2;
        static final int AFTER_LAYOUT = 3;

        @NonNull private final View mView;
        private final int mKind;
        @Nullable private Runnable mAction;
        @Nullable private final BooleanSupplier mDone;
        @Nullable private ViewTreeObserver mObserver;
        private boolean mFinished;
        private final Runnable mFire = this::fire;
        private final Runnable mFireBackstop = () -> fire(true);
        @Nullable private LayoutAction mLayoutAction;
        private final Runnable mDetach = this::detach;

        private Handle(@NonNull View view, int kind, @Nullable Runnable action,
                       @Nullable BooleanSupplier done) {
            mView = view;
            mKind = kind;
            mAction = action;
            mDone = done;
        }

        private void listenPreDraw() {
            ViewTreeObserver observer = mView.getViewTreeObserver();
            mObserver = observer;
            observer.addOnPreDrawListener(this);
        }

        public boolean isActive() {
            return !mFinished;
        }

        /** Drops the wait without running it. */
        public void cancel() {
            if (mFinished) return;
            mFinished = true;
            mAction = null;
            mLayoutAction = null;
            detach();
        }

        @Override
        public boolean onPreDraw() {
            if (mFinished) return true;
            if (mKind == UNTIL) {
                BooleanSupplier done = mDone;
                if (done != null && done.getAsBoolean()) cancel();
                return true;
            }
            Runnable action = mAction;
            mFinished = true;
            mAction = null;
            detach();
            if (action != null) action.run();
            return true;
        }

        @Override
        public void onDraw() {
            if (mFinished) return;
            Runnable action = mAction;
            mFinished = true;
            mAction = null;
            // A draw listener cannot be removed during the draw: the next turn does it.
            mView.post(mDetach);
            if (action != null) action.run();
        }

        @Override
        public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
            // The caller may add views, which a layout pass is no place to do.
            if (!mFinished) v.post(mFire);
        }

        private void fire() {
            fire(false);
        }

        private void fire(boolean byBackstop) {
            if (mFinished) return;
            Runnable action = mAction;
            LayoutAction layoutAction = mLayoutAction;
            mFinished = true;
            mAction = null;
            mLayoutAction = null;
            detach();
            if (action != null) action.run();
            if (layoutAction != null) layoutAction.run(byBackstop);
        }

        private void detach() {
            mView.removeCallbacks(mFire);
            mView.removeCallbacks(mFireBackstop);
            mView.removeCallbacks(mDetach);
            ViewTreeObserver observer = mObserver;
            mObserver = null;
            if (mKind == AFTER_LAYOUT) {
                mView.removeOnLayoutChangeListener(this);
                return;
            }
            if (observer != null && observer.isAlive()) remove(observer);
            // An observer taken before the view was attached is merged into the window's on attach.
            ViewTreeObserver current = mView.getViewTreeObserver();
            if (current != observer && current.isAlive()) remove(current);
        }

        private void remove(@NonNull ViewTreeObserver observer) {
            if (mKind == AFTER_DRAW) observer.removeOnDrawListener(this);
            else observer.removeOnPreDrawListener(this);
        }
    }
}
