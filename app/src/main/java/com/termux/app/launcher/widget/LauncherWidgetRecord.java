package com.termux.app.launcher.widget;

import android.content.ComponentName;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Immutable durable identity and lifecycle state for one launcher-owned app-widget ID. */
public final class LauncherWidgetRecord {
    public enum State { ACTIVE, PROVIDER_MISSING, DELETING }

    public final int appWidgetId;
    @NonNull public final ComponentName provider;
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
        if (appWidgetId <= 0) throw new IllegalArgumentException("appWidgetId must be positive");
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        this.appWidgetId = appWidgetId;
        this.provider = provider;
        this.profileSerial = profileSerial;
        this.state = state;
        this.cell = cell;
        this.page = page;
        this.sizeOptions = sizeOptions == null ? new Bundle() : new Bundle(sizeOptions);
        this.lastRenderFailure = lastRenderFailure;
    }

    /** Returns a defensive copy so repository snapshots stay immutable to callers. */
    @NonNull public Bundle sizeOptions() { return new Bundle(sizeOptions); }

    @NonNull
    public LauncherWidgetRecord withState(@NonNull State value) {
        return new LauncherWidgetRecord(appWidgetId, provider, profileSerial, value,
            cell, page, sizeOptions, lastRenderFailure);
    }

    @NonNull
    public LauncherWidgetRecord withSizeOptions(@NonNull Bundle value) {
        return new LauncherWidgetRecord(appWidgetId, provider, profileSerial, state,
            cell, page, value, lastRenderFailure);
    }

    @NonNull
    public LauncherWidgetRecord withRenderFailure(@Nullable String value) {
        return new LauncherWidgetRecord(appWidgetId, provider, profileSerial, state,
            cell, page, sizeOptions, value);
    }

    @NonNull public LauncherWidgetRecord withCell(@NonNull WidgetCellRect value) {
        return new LauncherWidgetRecord(appWidgetId, provider, profileSerial, state,
            value, page, sizeOptions, lastRenderFailure);
    }

    @NonNull public LauncherWidgetRecord withPage(int value) {
        return new LauncherWidgetRecord(appWidgetId, provider, profileSerial, state,
            cell, value, sizeOptions, lastRenderFailure);
    }
}
