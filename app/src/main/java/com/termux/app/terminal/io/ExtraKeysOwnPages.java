package com.termux.app.terminal.io;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/** Which pages have an extra-keys row of their own; every other page follows Global. */
public final class ExtraKeysOwnPages {

    private ExtraKeysOwnPages() {}

    /** The targets other than Global whose stored line is non-blank, in the editor's order. */
    @NonNull
    public static List<ExtraKeysTarget> of(@NonNull ExtraKeySetResolver.Lines lines) {
        List<ExtraKeysTarget> own = new ArrayList<>(4);
        for (ExtraKeysTarget target : ExtraKeysTarget.values()) {
            if (target == ExtraKeysTarget.GLOBAL) continue;
            if (ExtraKeySetResolver.resolve(target, lines).source == ExtraKeySetResolver.Source.OWN) own.add(target);
        }
        return own;
    }
}
