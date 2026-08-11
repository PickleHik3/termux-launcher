package com.termux.app.launcher.widget;

import android.content.ComponentName;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** One durable external add transaction. There may be at most one in the repository. */
public final class WidgetAddTransaction {
    public enum Stage {
        ALLOCATED,
        WAITING_FOR_BIND_CONSENT,
        BOUND,
        WAITING_FOR_CONFIGURATION,
        COMMITTING
    }

    @NonNull public final String token;
    public final int appWidgetId;
    @NonNull public final ComponentName provider;
    public final long profileSerial;
    @NonNull public final Stage stage;
    @NonNull private final Bundle requestedOptions;
    public final long startedAtMillis;

    public WidgetAddTransaction(@NonNull String token, int appWidgetId,
                                @NonNull ComponentName provider, long profileSerial,
                                @NonNull Stage stage, @Nullable Bundle requestedOptions,
                                long startedAtMillis) {
        if (token.isEmpty()) throw new IllegalArgumentException("token must not be empty");
        if (appWidgetId <= 0) throw new IllegalArgumentException("appWidgetId must be positive");
        this.token = token;
        this.appWidgetId = appWidgetId;
        this.provider = provider;
        this.profileSerial = profileSerial;
        this.stage = stage;
        this.requestedOptions = requestedOptions == null ? new Bundle() : new Bundle(requestedOptions);
        this.startedAtMillis = startedAtMillis;
    }

    /** Returns a defensive copy so an external activity cannot mutate durable transaction state. */
    @NonNull public Bundle requestedOptions() { return new Bundle(requestedOptions); }

    @NonNull
    public WidgetAddTransaction withStage(@NonNull Stage value) {
        return new WidgetAddTransaction(token, appWidgetId, provider, profileSerial, value,
            requestedOptions, startedAtMillis);
    }
}
