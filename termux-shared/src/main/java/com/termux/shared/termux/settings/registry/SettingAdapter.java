package com.termux.shared.termux.settings.registry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * An id source the registry consults after its own declarations: a store that already validates
 * its own values (the look grid, the layout documents) answers for a family of ids ({@code
 * look.dock.opacity}, {@code layout.home.search_field}) without re-declaring each one. The adapter
 * validates, saves and refreshes by the same code the store's own door uses, so its refusals are
 * that door's; the registry only routes. {@link SettingsRegistry#list} and later {@code export}
 * iterate {@link #declarations()} beside the registry's own.
 */
public interface SettingAdapter {

    /**
     * The declaration {@code id} names when this adapter answers for it, else null. Cheap and
     * free of I/O: it checks the id's shape, so a refusal for a value is the store's to give.
     */
    @Nullable
    SettingDeclaration declaration(@NonNull String id);

    /** The ids worth listing, with their ranges; may read the store. */
    @NonNull
    List<SettingDeclaration> declarations();

    /** The current value of a declaration this adapter returned, as text. */
    @NonNull
    String get(@NonNull SettingDeclaration declaration);

    /**
     * Whether {@code declaration} holds a value of its own, so a settings file carries it: false
     * for a value that follows another (a surface's opacity that follows Global), so an export
     * then apply leaves it following. Every id overrides unless the adapter says otherwise.
     */
    default boolean overrides(@NonNull SettingDeclaration declaration) {
        return true;
    }

    /**
     * What {@link #set} would refuse for {@code value}, without saving or refreshing: the same
     * Refusal, or null when the value would be saved. {@code config apply} checks a whole file
     * with it before writing any line.
     */
    @Nullable
    SettingsRegistry.Refusal check(@NonNull SettingDeclaration declaration, @NonNull String value);

    /**
     * Validates and saves {@code value}, and carries out the refresh itself, so a caller on any
     * thread need not: a {@link SettingsRegistry.Change} (whose declaration should name
     * {@link SettingRefresh#LISTENER}, so the applier adds nothing) or a Refusal.
     */
    @NonNull
    SettingsRegistry.Outcome set(@NonNull SettingDeclaration declaration, @NonNull String value);
}
