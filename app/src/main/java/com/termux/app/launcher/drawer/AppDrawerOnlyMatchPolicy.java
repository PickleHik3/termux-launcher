package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * Whether a drawer search that has narrowed to one app opens it by itself. Enter always opens the
 * first result; this is the optional hands-free step, after a pause long enough to keep typing.
 */
public final class AppDrawerOnlyMatchPolicy {

    /** The pause after the last keystroke before the lone match opens. */
    public static final long AUTO_OPEN_DELAY_MS = 800L;

    private AppDrawerOnlyMatchPolicy() {}

    /** True when a lone match should be opened once the pause passes without another edit. */
    public static boolean shouldAutoOpen(boolean enabled, @NonNull CharSequence query,
                                         int resultCount) {
        return enabled && query.length() > 0 && resultCount == 1;
    }
}
