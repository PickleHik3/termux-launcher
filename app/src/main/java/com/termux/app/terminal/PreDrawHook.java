package com.termux.app.terminal;

import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Runs a callback before every frame a view's window draws, for as long as it is switched on and
 * the view is attached. The listener goes on the window's own observer when the view attaches and
 * comes off when it detaches, so a view that comes and goes (a pane, the floating keyboard's card)
 * never leaves one behind, and a view that was switched on before it was attached starts on attach.
 *
 * <p>A pre-draw pass only runs when something in the window is about to draw, so a still screen
 * costs nothing; the callback must not allocate on the frames where nothing it reads has moved.
 */
public final class PreDrawHook implements View.OnAttachStateChangeListener,
    ViewTreeObserver.OnPreDrawListener {

    @NonNull private final View mView;
    @NonNull private final Runnable mCallback;
    private boolean mOn;
    /** The observer the listener is on, or null while it is on none. */
    @Nullable private ViewTreeObserver mObserver;

    public PreDrawHook(@NonNull View view, @NonNull Runnable callback) {
        mView = view;
        mCallback = callback;
        view.addOnAttachStateChangeListener(this);
    }

    public void setOn(boolean on) {
        if (mOn == on) return;
        mOn = on;
        if (on && mView.isAttachedToWindow()) listen();
        else if (!on) unlisten();
    }

    /** Off for good: the view keeps no reference to this hook. */
    public void release() {
        setOn(false);
        mView.removeOnAttachStateChangeListener(this);
    }

    @Override
    public boolean onPreDraw() {
        mCallback.run();
        return true;
    }

    @Override
    public void onViewAttachedToWindow(@NonNull View view) {
        if (mOn) listen();
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull View view) {
        unlisten();
    }

    private void listen() {
        if (mObserver != null) return;
        mObserver = mView.getViewTreeObserver();
        mObserver.addOnPreDrawListener(this);
    }

    private void unlisten() {
        ViewTreeObserver observer = mObserver;
        mObserver = null;
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
    }
}
