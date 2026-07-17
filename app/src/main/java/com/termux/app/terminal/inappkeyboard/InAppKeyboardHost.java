package com.termux.app.terminal.inappkeyboard;

import android.view.View;

import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

/**
 * Narrow activity seam used by the in-app keyboard controller.
 *
 * <p>WP4 implements this on {@code TermuxActivity}; keeping the controller behind this interface
 * lets its lifecycle and layout policy be tested without constructing the full activity.</p>
 */
public interface InAppKeyboardHost extends HostActions {

    /** The container whose context and visibility belong to the hosted keyboard. */
    View getKeyboardContainer();

    void attachKeyboardView(View keyboardView);

    void detachKeyboardView();

    void requestAccessoryGeometrySync();

    /**
     * Invalidate any host-cached keyboard measurement before a geometry sync. Required when the
     * keyboard view's intrinsic size changes without a container layout pass (height scale,
     * key margin, or corner radius previews) — the host cache is keyed only on the container's
     * width and available height and would otherwise pin the stack at the stale height.
     */
    void invalidateKeyboardMeasurement();

    /** Shows or hides the activity-owned geometry sliders, drag handle, and transaction controls. */
    void setKeyboardHeightAdjustmentVisible(boolean visible);

    TerminalView getTerminalView();

    TerminalSession getCurrentSession();

    /** Re-run the pre-in-app-keyboard soft-input policy exactly once after disabling the feature. */
    void restoreLegacySoftKeyboardState();

    /** Execute a view mutation on the activity main thread. */
    void runOnMain(Runnable runnable);
}
