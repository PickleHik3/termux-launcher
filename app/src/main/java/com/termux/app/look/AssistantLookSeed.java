package com.termux.app.look;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.LookGrid;

/**
 * Where the Assistant kind's look starts (ADR 0022 section 2, as ADR 0020 section 4 started
 * Display): every cell Home's kind holds is copied to {@code @kind:assistant} when the Assistant
 * page is first created. A cell the Assistant already holds is left alone, so a page added again
 * keeps what was edited.
 */
public final class AssistantLookSeed {

    private AssistantLookSeed() {}

    /** Copies Home's kind cells to the Assistant's where it holds none; whether any moved. */
    public static boolean seed(@NonNull LookGrid.Store store) {
        return new LookGrid(store).seedKind(LookGrid.Kind.HOME, LookGrid.Kind.ASSISTANT);
    }

    /** {@link #seed(LookGrid.Store)} over a look store. */
    public static boolean seed(@NonNull LookStore store) {
        return seed(LookKeys.gridStore(store));
    }
}
