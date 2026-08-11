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
    @NonNull private final Bundle sizeOptions;
    @Nullable public final String lastRenderFailure;

    public LauncherWidgetRecord(int appWidgetId, @NonNull ComponentName provider,
                                long profileSerial, @NonNull State state,
                                @Nullable Bundle sizeOptions,
                                @Nullable String lastRenderFailure) {
        if (appWidgetId <= 0) throw new IllegalArgumentException("appWidgetId must be positive");
        this.appWidgetId = appWidgetId;
        this.provider = provider;
        this.profileSerial = profileSerial;
        this.state = state;
        this.sizeOptions = sizeOptions == null ? new Bundle() : new Bundle(sizeOptions);
        this.lastRenderFailure = lastRenderFailure;
    }

    /** Returns a defensive copy so repository snapshots stay immutable to callers. */
    @NonNull public Bundle sizeOptions() { return new Bundle(sizeOptions); }

    @NonNull
    public LauncherWidgetRecord withState(@NonNull State value) {
        return new LauncherWidgetRecord(appWidgetId, provider, profileSerial, value,
            sizeOptions, lastRenderFailure);
    }

    @NonNull
    public LauncherWidgetRecord withSizeOptions(@NonNull Bundle value) {
        return new LauncherWidgetRecord(appWidgetId, provider, profileSerial, state,
            value, lastRenderFailure);
    }

    @NonNull
    public LauncherWidgetRecord withRenderFailure(@Nullable String value) {
        return new LauncherWidgetRecord(appWidgetId, provider, profileSerial, state,
            sizeOptions, value);
    }
}
