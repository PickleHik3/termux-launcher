package com.termux.app.terminal.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

/**
 * What the extra keys go to, and with it where their set is stored. {@link #GLOBAL} is the
 * everywhere set; the others can carry a set of their own, which takes over while the keys go there.
 */
public enum ExtraKeysTarget {
    GLOBAL(TermuxPropertyConstants.KEY_EXTRA_KEYS, TermuxPropertyConstants.KEY_EXTRA_KEYS2,
        TermuxPropertyConstants.KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR,
        TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS, null),
    TERMINAL(TermuxPropertyConstants.KEY_EXTRA_KEYS_TERMINAL, TermuxPropertyConstants.KEY_EXTRA_KEYS_TERMINAL2,
        TermuxPropertyConstants.KEY_EXTRA_KEYS_TERMINAL_HEIGHT, null, PaneWallPage.TERMINAL),
    DISPLAY(TermuxPropertyConstants.KEY_EXTRA_KEYS_DISPLAY, TermuxPropertyConstants.KEY_EXTRA_KEYS_DISPLAY2,
        TermuxPropertyConstants.KEY_EXTRA_KEYS_DISPLAY_HEIGHT,
        TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_DISPLAY, PaneWallPage.DISPLAY),
    HOME(TermuxPropertyConstants.KEY_EXTRA_KEYS_HOME, TermuxPropertyConstants.KEY_EXTRA_KEYS_HOME2,
        TermuxPropertyConstants.KEY_EXTRA_KEYS_HOME_HEIGHT, null, PaneWallPage.WIDGETS),
    ASSISTANT(TermuxPropertyConstants.KEY_EXTRA_KEYS_ASSISTANT, TermuxPropertyConstants.KEY_EXTRA_KEYS_ASSISTANT2,
        TermuxPropertyConstants.KEY_EXTRA_KEYS_ASSISTANT_HEIGHT,
        TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_ASSISTANT, PaneWallPage.ASSISTANT);

    private final String mPage1Key;
    private final String mPage2Key;
    private final String mHeightKey;
    @Nullable private final String mShippedPage1;
    @Nullable private final PaneWallPage mMarkingPlace;

    ExtraKeysTarget(@NonNull String page1Key, @NonNull String page2Key, @NonNull String heightKey,
                    @Nullable String shippedPage1, @Nullable PaneWallPage markingPlace) {
        mPage1Key = page1Key;
        mPage2Key = page2Key;
        mHeightKey = heightKey;
        mShippedPage1 = shippedPage1;
        mMarkingPlace = markingPlace;
    }

    /** The two page lines, in order. */
    @NonNull
    public String[] pageKeys() {
        return new String[]{mPage1Key, mPage2Key};
    }

    @NonNull
    String page1Key() {
        return mPage1Key;
    }

    @NonNull
    String page2Key() {
        return mPage2Key;
    }

    /** The key size line (Global's is {@code terminal-toolbar-height}). */
    @NonNull
    public String heightKey() {
        return mHeightKey;
    }

    /** The set the app ships for this target: its own for Display and Assistant, the Global row for Global, else none. */
    @Nullable
    public String shippedPage1() {
        return mShippedPage1;
    }

    /** The place the keys of this target are judged against when marking keys that do not act there; none for Global. */
    @Nullable
    public PaneWallPage markingPlace() {
        return mMarkingPlace;
    }
}
