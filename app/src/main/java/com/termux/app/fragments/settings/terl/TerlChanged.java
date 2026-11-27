package com.termux.app.fragments.settings.terl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The Changed filter: every setting of Terl's Settings whose value is not its declaration's
 * default, each reset through the registry ({@code default}, so an adapter's own reset runs), and
 * a row's Copy as config line.
 */
public final class TerlChanged {

    private TerlChanged() {}

    /** One setting off its default. */
    public static final class Setting {
        @NonNull public final SettingDeclaration declaration;
        @NonNull public final String value;

        Setting(@NonNull SettingDeclaration declaration, @NonNull String value) {
            this.declaration = declaration;
            this.value = value;
        }
    }

    /**
     * The settings among {@code ids} whose value differs from the default, in {@code ids}' order.
     * {@code shown} leaves out a row this phone does not show; an id the registry does not know
     * is skipped.
     */
    @NonNull
    public static List<Setting> changed(@NonNull SettingsRegistry registry, @NonNull List<String> ids,
                                        @NonNull Predicate<String> shown) {
        List<Setting> out = new ArrayList<>();
        for (String id : ids) {
            if (!shown.test(id)) continue;
            SettingDeclaration declaration = registry.declaration(id);
            String value = registry.get(id);
            if (declaration == null || value == null) continue;
            if (!value.equals(declaration.defaultValue())) out.add(new Setting(declaration, value));
        }
        return out;
    }

    /** Every Terl Settings row this phone shows, off its default. */
    @NonNull
    public static List<Setting> changed(@NonNull SettingsRegistry registry,
                                        @NonNull Predicate<String> shown) {
        return changed(registry, TerlSettingsTree.settingIds(), shown);
    }

    /** Puts {@code id} back on its default: the Change, or the Refusal. */
    @NonNull
    public static SettingsRegistry.Outcome reset(@NonNull SettingsRegistry registry, @NonNull String id) {
        return registry.set(id, SettingsRegistry.DEFAULT);
    }

    /** Whether {@code id} is off its default now. */
    public static boolean isChanged(@NonNull SettingsRegistry registry, @Nullable String id) {
        SettingDeclaration declaration = registry.declaration(id);
        String value = registry.get(id);
        return declaration != null && value != null && !value.equals(declaration.defaultValue());
    }
}
