package com.termux.app.launcher.widget;

import android.content.ComponentName;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Immutable durable identity and lifecycle state for one widget on the wall: a launcher-owned
 * app-widget ID bound to another app's provider, or one of the launcher's own built-in widgets.
 *
 * <p>A built-in widget has no platform ID. It is keyed by a negative number the repository hands
 * out ({@link LauncherWidgetRepository#allocateBuiltinId()}), so every map, outline and drag that
 * is keyed by {@link #appWidgetId} works unchanged, and its {@link #provider} is the synthetic
 * {@link #builtinProvider(String)} so the provider-equality rules keep holding. {@link #builtinKind}
 * names which built-in it is.</p>
 */
public final class LauncherWidgetRecord {
    public enum State { ACTIVE, PROVIDER_MISSING, DELETING }

    /** The package a built-in widget's synthetic provider names; never a real package. */
    public static final String BUILTIN_PROVIDER_PACKAGE = "com.termux.launcher.builtin";

    public final int appWidgetId;
    @NonNull public final ComponentName provider;
    /** The built-in widget this record is, or null for an app widget bound to a provider. */
    @Nullable public final String builtinKind;
    public final long profileSerial;
    @NonNull public final State state;
    @NonNull public final WidgetCellRect cell;
    /** Zero-based pane page this widget lives on. Collision rules are per page. */
    public final int page;
    @NonNull private final Bundle sizeOptions;
    @Nullable public final String lastRenderFailure;

    public LauncherWidgetRecord(int appWidgetId, @NonNull ComponentName provider,
                                long profileSerial, @NonNull State state,
                                @Nullable Bundle sizeOptions,
                                @Nullable String lastRenderFailure) {
        this(appWidgetId, provider, profileSerial, state, new WidgetCellRect(0, 0, 1, 1),
            sizeOptions, lastRenderFailure);
    }

    public LauncherWidgetRecord(int appWidgetId, @NonNull ComponentName provider,
                                long profileSerial, @NonNull State state,
                                @NonNull WidgetCellRect cell, @Nullable Bundle sizeOptions,
                                @Nullable String lastRenderFailure) {
        this(appWidgetId, provider, profileSerial, state, cell, 0, sizeOptions, lastRenderFailure);
    }

    public LauncherWidgetRecord(int appWidgetId, @NonNull ComponentName provider,
                                long profileSerial, @NonNull State state,
                                @NonNull WidgetCellRect cell, int page,
                                @Nullable Bundle sizeOptions,
                                @Nullable String lastRenderFailure) {
        this(appWidgetId, provider, null, profileSerial, state, cell, page, sizeOptions,
            lastRenderFailure);
    }

    private LauncherWidgetRecord(int appWidgetId, @NonNull ComponentName provider,
                                 @Nullable String builtinKind,
                                 long profileSerial, @NonNull State state,
                                 @NonNull WidgetCellRect cell, int page,
                                 @Nullable Bundle sizeOptions,
                                 @Nullable String lastRenderFailure) {
        if (builtinKind == null && appWidgetId <= 0) {
            throw new IllegalArgumentException("appWidgetId must be positive");
        }
        if (builtinKind != null && appWidgetId >= 0) {
            throw new IllegalArgumentException("a built-in widget's id must be negative");
        }
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        this.appWidgetId = appWidgetId;
        this.provider = provider;
        this.builtinKind = builtinKind;
        this.profileSerial = profileSerial;
        this.state = state;
        this.cell = cell;
        this.page = page;
        this.sizeOptions = sizeOptions == null ? new Bundle() : new Bundle(sizeOptions);
        this.lastRenderFailure = lastRenderFailure;
    }

    /**
     * A built-in widget of {@code kind}, keyed by {@code id} (negative, from the repository). Its
     * options bundle is the widget's own configuration, never pushed to the platform.
     */
    @NonNull
    public static LauncherWidgetRecord builtin(int id, @NonNull String kind,
                                               @NonNull WidgetCellRect cell, int page,
                                               @Nullable Bundle config) {
        return new LauncherWidgetRecord(id, builtinProvider(kind), kind, 0L, State.ACTIVE, cell,
            page, config, null);
    }

    /** The synthetic provider a built-in widget of {@code kind} is recorded under. */
    @NonNull public static ComponentName builtinProvider(@NonNull String kind) {
        return new ComponentName(BUILTIN_PROVIDER_PACKAGE, kind);
    }

    /** True for one of the launcher's own widgets; false for an app widget with a platform ID. */
    public boolean isBuiltin() { return builtinKind != null; }

    /** Returns a defensive copy so repository snapshots stay immutable to callers. */
    @NonNull public Bundle sizeOptions() { return new Bundle(sizeOptions); }

    @NonNull
    public LauncherWidgetRecord withState(@NonNull State value) {
        return new LauncherWidgetRecord(appWidgetId, provider, builtinKind, profileSerial, value,
            cell, page, sizeOptions, lastRenderFailure);
    }

    @NonNull
    public LauncherWidgetRecord withSizeOptions(@NonNull Bundle value) {
        return new LauncherWidgetRecord(appWidgetId, provider, builtinKind, profileSerial, state,
            cell, page, value, lastRenderFailure);
    }

    @NonNull
    public LauncherWidgetRecord withRenderFailure(@Nullable String value) {
        return new LauncherWidgetRecord(appWidgetId, provider, builtinKind, profileSerial, state,
            cell, page, sizeOptions, value);
    }

    @NonNull public LauncherWidgetRecord withCell(@NonNull WidgetCellRect value) {
        return new LauncherWidgetRecord(appWidgetId, provider, builtinKind, profileSerial, state,
            value, page, sizeOptions, lastRenderFailure);
    }

    @NonNull public LauncherWidgetRecord withPage(int value) {
        return new LauncherWidgetRecord(appWidgetId, provider, builtinKind, profileSerial, state,
            cell, value, sizeOptions, lastRenderFailure);
    }
}
