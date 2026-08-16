package com.termux.app.terminal.rename;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Seam for a backend that proposes names — today nothing implements it; an on-device model backend
 * is the intended first one.
 *
 * <p>Kept deliberately thin so a provider can be asynchronous without the rename UI knowing: the
 * callback may arrive on any thread and the coordinator hops to the main thread itself. A provider
 * that has no answer calls back with null, which leaves the draft alone rather than clearing it.
 */
public interface TerminalRenameSuggestionProvider {

    interface Callback {
        /** @param name proposed name, or null when the provider has nothing to offer. */
        void onSuggestion(@Nullable String name);
    }

    /**
     * Propose a name for {@code context}. Called at most once per request, and never from the
     * suggestion callback of another request.
     */
    void suggest(@NonNull TerminalRenameContext context, @NonNull Callback callback);
}
