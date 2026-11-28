package com.termux.app.terminal;

import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.launcherctl.LauncherToolRegistry;

/** Installs a bindings file's text as the live table, for tests outside this package. */
public final class TerminalBindingConfigTesting {

    private TerminalBindingConfigTesting() {}

    public static void reset() {
        TerminalKeyBindingResolver.resetForTesting();
    }

    public static void install(@NonNull String file) {
        TerminalBindingConfig.Result config = TerminalBindingConfig.parse(file,
            LauncherToolRegistry.getInstance(), true);
        assertTrue(config.errors.toString(), config.errors.isEmpty());
        TerminalKeyBindingResolver.installConfigForTesting(config);
    }
}
