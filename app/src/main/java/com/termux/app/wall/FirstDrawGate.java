package com.termux.app.wall;

import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

/**
 * Holds a settle's clock until the content it brings in has drawn once (#173). A page that
 * arrives - another window, another Home page - costs its first layout and draw, and that frame
 * can take about as long as the whole settle: a clock started beside it found its animation over
 * by the next frame, and the transition jumped to rest. Behind this gate the settle is posed at
 * its start for that frame and its clock starts on the frame after, so every frame of it is seen.
 *
 * <p>The gate opens once: after the next draw of the window the view is in has finished, or after
 * {@link #TIMEOUT_MS} if no draw comes. What waits on it runs then, in the order it was added and
 * in one go, so settles that are one sheet start on the same frame. A view that is not in a window
 * has no draw coming, and its gate is open from the start.
 */
public final class FirstDrawGate {

    /** How long a gate waits for a draw that never comes. */
    public static final long TIMEOUT_MS = 100L;

    @Nullable private View mView;
    @Nullable private ViewTreeObserver mObserver;
    private boolean mOpen;
    private boolean mDrawn;
    private final ArrayList<Runnable> mWaiting = new ArrayList<>(2);
    private final Runnable mOpenNow = this::open;
    private final ViewTreeObserver.OnDrawListener mOnDraw = this::onDraw;

    private FirstDrawGate() { }

    /** A gate that is open already: what is added to it runs at once. */
    @NonNull
    public static FirstDrawGate opened() {
        FirstDrawGate gate = new FirstDrawGate();
        gate.mOpen = true;
        return gate;
    }

    /** A gate that opens once the window {@code view} is in has drawn again. */
    @NonNull
    public static FirstDrawGate afterNextDraw(@NonNull View view) {
        FirstDrawGate gate = new FirstDrawGate();
        ViewTreeObserver observer = view.getViewTreeObserver();
        if (!view.isAttachedToWindow() || !observer.isAlive()) {
            gate.mOpen = true;
            return gate;
        }
        gate.mView = view;
        gate.mObserver = observer;
        observer.addOnDrawListener(gate.mOnDraw);
        view.postDelayed(gate.mOpenNow, TIMEOUT_MS);
        return gate;
    }

    /** Runs {@code action} once the gate opens: now, if it is open. */
    public void then(@NonNull Runnable action) {
        if (mOpen) action.run();
        else mWaiting.add(action);
    }

    public boolean isOpen() {
        return mOpen;
    }

    /**
     * The window drew. Opened after the frame is done, not inside it: the draw is the expensive
     * frame, and the clock belongs to the one after. A listener cannot be removed during the draw
     * either.
     */
    void onDraw() {
        View view = mView;
        if (mOpen || mDrawn || view == null) return;
        mDrawn = true;
        view.removeCallbacks(mOpenNow);
        view.post(mOpenNow);
    }

    /** Opens the gate now and runs what waits on it. */
    public void open() {
        if (mOpen) return;
        mOpen = true;
        View view = mView;
        ViewTreeObserver observer = mObserver;
        mView = null;
        mObserver = null;
        if (view != null) view.removeCallbacks(mOpenNow);
        if (observer != null && observer.isAlive()) observer.removeOnDrawListener(mOnDraw);
        ArrayList<Runnable> waiting = new ArrayList<>(mWaiting);
        mWaiting.clear();
        for (Runnable action : waiting) action.run();
    }
}
