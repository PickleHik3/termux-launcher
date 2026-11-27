package com.termux.app.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import java.util.function.BooleanSupplier;

/**
 * The registry's veto on choices this build or phone cannot run: Wayland as the display server
 * in a version built without the Wayland host, and libghostty as the terminal core where it does
 * not load. Settings hides those rows; this makes terl.conf, launcherctl and the Assistant refuse
 * them too, in the registry's words.
 */
public final class RunnableChoices implements SettingsRegistry.Veto {

    private final boolean mWaylandBuilt;
    /** Asked only when libghostty is chosen: the first ask loads the library. */
    @NonNull private final BooleanSupplier mGhosttyLoads;

    public RunnableChoices(boolean waylandBuilt, boolean ghosttyLoads) {
        this(waylandBuilt, () -> ghosttyLoads);
    }

    public RunnableChoices(boolean waylandBuilt, @NonNull BooleanSupplier ghosttyLoads) {
        mWaylandBuilt = waylandBuilt;
        mGhosttyLoads = ghosttyLoads;
    }

    /** This build and phone; libghostty is loaded only if someone picks it. */
    @NonNull
    public static RunnableChoices forDevice() {
        return new RunnableChoices(com.termux.BuildConfig.WAYLAND_DISPLAY,
            com.termux.terminal.TerminalEngine::isAvailable);
    }

    @Nullable
    @Override
    public SettingsRegistry.Refusal refuse(@NonNull SettingDeclaration declaration, @NonNull String value) {
        if (!mWaylandBuilt && declaration.id.equals(SettingDeclarations.LINUX_DISPLAY_SERVER.id)
            && value.equals("wayland"))
            return onlyDefault(declaration, "this version has no Wayland display");
        if (declaration.id.equals(SettingDeclarations.TERMINAL_CORE.id)
            && value.equals("libghostty") && !mGhosttyLoads.getAsBoolean())
            return onlyDefault(declaration, "libghostty does not load on this phone");
        return null;
    }

    @NonNull
    private static SettingsRegistry.Refusal onlyDefault(@NonNull SettingDeclaration declaration,
                                                        @NonNull String why) {
        String only = declaration.defaultValue();
        return new SettingsRegistry.Refusal(declaration.id + " can only be " + only + ": " + why, only);
    }
}
