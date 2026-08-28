package com.termux.app.terminal;

import android.view.View;

import androidx.annotation.Nullable;

/**
 * The window's soft-input flags, which the view client sets and reads as one group. Part of
 * {@link TerminalHost}; split out so the group can be named on its own.
 */
public interface SoftKeyboardPolicy {

    boolean shouldDelaySoftKeyboardShowOnResume();

    boolean areSoftKeyboardFlagsDisabled();

    void disableSoftKeyboard(@Nullable View view);

    void clearDisableSoftKeyboardFlags();

    void setSoftKeyboardAlwaysHiddenFlags();

    void setSoftInputModeAdjustResize();
}
