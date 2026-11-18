package com.termux.app.terminal.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

/**
 * What the extra keys go to, and with it where their set is stored. {@link #GLOBAL} is the
 * everywhere set; the others follow it unless they carry a set of their own, which takes over while the keys go there.
 */
public enum ExtraKeysTarget {
    GLOBAL(TermuxPropertyConstants.KEY_EXTRA_KEYS, TermuxPropertyConstants.KEY_EXTRA_KEYS2,
        TermuxPropertyConstants.KEY_TERMINAL_TOOLBAR_HEIGHT_SCALE_FACTOR,
        null),
    TERMINAL(TermuxPropertyConstants.KEY_EXTRA_KEYS_TERMINAL, TermuxPropertyConstants.KEY_EXTRA_KEYS_TERMINAL2,
        TermuxPropertyConstants.KEY_EXTRA_KEYS_TERMINAL_HEIGHT, PaneWallPage.TERMINAL),
    DISPLAY(TermuxPropertyConstants.KEY_EXTRA_KEYS_DISPLAY, TermuxPropertyConstants.KEY_EXTRA_KEYS_DISPLAY2,
        TermuxPropertyConstants.KEY_EXTRA_KEYS_DISPLAY_HEIGHT, PaneWallPage.DISPLAY),
    HOME(TermuxPropertyConstants.KEY_EXTRA_KEYS_HOME, TermuxPropertyConstants.KEY_EXTRA_KEYS_HOME2,
        TermuxPropertyConstants.KEY_EXTRA_KEYS_HOME_HEIGHT, PaneWallPage.WIDGETS),
    ASSISTANT(TermuxPropertyConstants.KEY_EXTRA_KEYS_ASSISTANT, TermuxPropertyConstants.KEY_EXTRA_KEYS_ASSISTANT2,
        TermuxPropertyConstants.KEY_EXTRA_KEYS_ASSISTANT_HEIGHT, PaneWallPage.ASSISTANT);

    private final String mPage1Key;
    private final String mPage2Key;
    private final String mHeightKey;
    @Nullable private final PaneWallPage mMarkingPlace;

    ExtraKeysTarget(@NonNull String page1Key, @NonNull String page2Key, @NonNull String heightKey,
                    @Nullable PaneWallPage markingPlace) {
        mPage1Key = page1Key;
        mPage2Key = page2Key;
        mHeightKey = heightKey;
        mMarkingPlace = markingPlace;
    }

    /** The target a page's own extra keys go to. */
    @NonNull
    public static ExtraKeysTarget forPage(@NonNull PageKind kind) {
        switch (kind) {
            case HOME: return HOME;
            case DISPLAY: return DISPLAY;
            case ASSISTANT: return ASSISTANT;
            case TERMINAL:
            default: return TERMINAL;
        }
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

    /** The place the keys of this target are judged against when marking keys that do not act there; none for Global. */
    @Nullable
    public PaneWallPage markingPlace() {
        return mMarkingPlace;
    }
}
