package com.termux.app.terminal.rename;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.TerminalRenameTarget;

import java.util.Collections;
import java.util.List;

/**
 * What is known about a rename target at the moment a name is wanted for it: enough for a person or
 * a model to pick a name without either one poking at live UI state.
 *
 * <p>This exists for the naming path that has no keyboard in it — an on-device backend proposing
 * "what should this window be called". It is a plain immutable value so the suggesting side never
 * touches {@code TermuxActivity}: the coordinator builds it, the provider reads it.
 */
public final class TerminalRenameContext {

    @NonNull public final TerminalRenameTarget target;
    /** The name the target already has, or null when it is unnamed. */
    @Nullable public final String currentName;
    /** Maximum code points a stored name may have, so a suggestion is not silently truncated. */
    public final int maxCodePoints;
    /** Working directory of the focused pane, when known. */
    @Nullable public final String workingDirectory;
    /** Foreground process of the focused pane, when the resolver has it. */
    @Nullable public final String foregroundProcess;
    /** File the foreground editor has open, when the resolver has it. */
    @Nullable public final String openFile;
    /** Titles of the panes inside the target, outermost-first; one entry for a pane target. */
    @NonNull public final List<String> paneTitles;

    public TerminalRenameContext(@NonNull TerminalRenameTarget target, @Nullable String currentName,
                                 int maxCodePoints, @Nullable String workingDirectory,
                                 @Nullable String foregroundProcess, @Nullable String openFile,
                                 @Nullable List<String> paneTitles) {
        this.target = target;
        this.currentName = currentName;
        this.maxCodePoints = maxCodePoints;
        this.workingDirectory = workingDirectory;
        this.foregroundProcess = foregroundProcess;
        this.openFile = openFile;
        this.paneTitles = paneTitles == null ? Collections.emptyList()
            : Collections.unmodifiableList(new java.util.ArrayList<>(paneTitles));
    }
}
