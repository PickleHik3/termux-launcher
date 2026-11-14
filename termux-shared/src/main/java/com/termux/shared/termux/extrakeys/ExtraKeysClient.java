package com.termux.shared.termux.extrakeys;

import android.view.View;

import androidx.annotation.NonNull;

/** Receives the keys of an extra-keys bar, without depending on a particular bar view. */
public interface ExtraKeysClient {

    /** A key was pressed. */
    void onExtraKeyClick(@NonNull View view, @NonNull ExtraKeyButton key);

    /** Return true if the client performed haptic feedback for the key itself. */
    boolean performExtraKeyHapticFeedback(@NonNull View view, @NonNull ExtraKeyButton key);
}
