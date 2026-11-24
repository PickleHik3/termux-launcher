package com.termux.app.x11;

import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.haptics.Haptics;

/**
 * What the scale rail does, whichever page it is on: it opens on the stop its host says is in
 * use, a finger that lands on it drags the thumb stop by stop with a tick at each, and the lift
 * hands the stop to the host and puts the rail away. The page owns the rail's view
 * ({@link DisplayScaleRailView}, which only draws) and routes the touches here; what a stop means
 * (the X11 Display's resolution, a Linux window's scale) is the host's.
 *
 * <p>Used by the display window ({@link X11PaneFrame}) and the Linux windows screen.
 */
public final class DisplayScaleRail {

    /** The page the rail is on. */
    public interface Host {
        /** The stop to open on, or a value below 1 to keep the one the rail last showed. */
        int currentScaleStep();

        /** The finger lifted on {@code step}. */
        void onScaleStepChosen(int step);

        /** The rail was used: put it away, with whatever came out with it. */
        void onScaleRailUsed();
    }

    @NonNull private final DisplayScaleRailView mView;
    @NonNull private final Host mHost;
    /** A finger landed on the rail and has not lifted. */
    private boolean mPressed;

    public DisplayScaleRail(@NonNull DisplayScaleRailView view, @NonNull Host host) {
        mView = view;
        mHost = host;
    }

    @NonNull
    public DisplayScaleRailView view() {
        return mView;
    }

    /** Out on the host's current stop. */
    public void show() {
        int step = mHost.currentScaleStep();
        if (step > 0) mView.setStep(step);
        mView.show();
    }

    /** Away; a finger still on it lifts as it would have (the view drops the drag). */
    public void dismiss() {
        mView.dismiss();
    }

    /** Whether the rail is out (or coming out). */
    public boolean isShown() {
        return mView.isRailShown();
    }

    /** Whether a finger at {@code (x, y)} would take the thumb. */
    public boolean hits(float x, float y) {
        return mView.hits(x, y);
    }

    /** Whether the finger that is down is the rail's. */
    public boolean isPressed() {
        return mPressed;
    }

    /**
     * A finger landed. True when it is on the rail, which then takes the gesture: the page sends
     * the rest of it to {@link #onTouch}.
     */
    public boolean down(float x, float y) {
        mPressed = false;
        if (!mView.hits(x, y)) return false;
        mPressed = true;
        mView.beginDrag(y);
        return true;
    }

    /** One event after {@link #down} took the finger. */
    public boolean onTouch(@Nullable MotionEvent event) {
        if (event == null) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (mView.dragTo(event.getY())) Haptics.tick(mView, HapticFeedbackConstants.CLOCK_TICK);
                return true;
            case MotionEvent.ACTION_UP:
                mPressed = false;
                mView.dragTo(event.getY());
                mHost.onScaleStepChosen(mView.endDrag());
                // Used, the rail goes away, as a tapped button does.
                mHost.onScaleRailUsed();
                return true;
            case MotionEvent.ACTION_CANCEL:
                mPressed = false;
                mView.cancelDrag();
                return true;
            default:
                return true;
        }
    }
}
