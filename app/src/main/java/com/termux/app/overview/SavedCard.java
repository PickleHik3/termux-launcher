package com.termux.app.overview;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.tiling.SavedWorkspace;
import com.termux.app.tiling.TilingStack;
import com.termux.app.tiling.TilingWorkspace;

/**
 * One card of the Saved row (#359; under Terl a saved Workspace, #431): its name, the layout it is
 * drawn from (CardFacts, never a bitmap) and whether it opens at start; null facts draw a plain
 * face, for a file that would not read.
 */
public final class SavedCard {
    @NonNull public final String name;
    @Nullable public final CardFacts facts;
    /** A saved Workspace marked Open at start. */
    public final boolean openAtStart;

    public SavedCard(@NonNull String name, @Nullable CardFacts facts) {
        this(name, facts, false);
    }

    public SavedCard(@NonNull String name, @Nullable CardFacts facts, boolean openAtStart) {
        this.name = name;
        this.facts = facts;
        this.openAtStart = openAtStart;
    }

    /** Saved Workspace {@code name}'s card, drawn as its Workspace's schematic; {@code saved} null for a file that would not read. */
    @NonNull
    public static SavedCard of(@NonNull String name, @Nullable SavedWorkspace saved) {
        TilingWorkspace workspace = saved == null ? null : saved.preview();
        CardFacts facts = workspace == null ? null : StackOverviewModel.schematic(workspace, TilingStack.NO_WINDOW);
        return new SavedCard(name, facts, saved != null && saved.openAtStart());
    }
}
