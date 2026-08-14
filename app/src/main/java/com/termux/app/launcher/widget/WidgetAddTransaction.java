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
    @NonNull public final WidgetCellRect cell;
    /** Zero-based pane page the reservation targets; collision checks stay on this page. */
    public final int page;
    public final long gridRevision;
    @Nullable public final String originToken;
    @NonNull private final Bundle requestedOptions;
    public final long startedAtMillis;

    public WidgetAddTransaction(@NonNull String token, int appWidgetId,
                                @NonNull ComponentName provider, long profileSerial,
                                @NonNull Stage stage, @Nullable Bundle requestedOptions,
                                long startedAtMillis) {
        this(token, appWidgetId, provider, profileSerial, stage, new WidgetCellRect(0, 0, 1, 1),
            0, null, requestedOptions, startedAtMillis);
    }

    public WidgetAddTransaction(@NonNull String token, int appWidgetId,
                                @NonNull ComponentName provider, long profileSerial,
                                @NonNull Stage stage, @NonNull WidgetCellRect cell,
                                long gridRevision, @Nullable String originToken,
                                @Nullable Bundle requestedOptions, long startedAtMillis) {
        this(token, appWidgetId, provider, profileSerial, stage, cell, 0, gridRevision,
            originToken, requestedOptions, startedAtMillis);
    }

    public WidgetAddTransaction(@NonNull String token, int appWidgetId,
                                @NonNull ComponentName provider, long profileSerial,
                                @NonNull Stage stage, @NonNull WidgetCellRect cell, int page,
                                long gridRevision, @Nullable String originToken,
                                @Nullable Bundle requestedOptions, long startedAtMillis) {
        if (token.isEmpty()) throw new IllegalArgumentException("token must not be empty");
        if (appWidgetId <= 0) throw new IllegalArgumentException("appWidgetId must be positive");
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        this.token = token;
        this.appWidgetId = appWidgetId;
        this.provider = provider;
        this.profileSerial = profileSerial;
        this.stage = stage;
        this.cell = cell;
        this.page = page;
        this.gridRevision = gridRevision;
        this.originToken = originToken;
        this.requestedOptions = requestedOptions == null ? new Bundle() : new Bundle(requestedOptions);
        this.startedAtMillis = startedAtMillis;
    }

    /** Returns a defensive copy so an external activity cannot mutate durable transaction state. */
    @NonNull public Bundle requestedOptions() { return new Bundle(requestedOptions); }

    @NonNull
    public WidgetAddTransaction withStage(@NonNull Stage value) {
        return new WidgetAddTransaction(token, appWidgetId, provider, profileSerial, value,
            cell, page, gridRevision, originToken, requestedOptions, startedAtMillis);
    }

    @NonNull public WidgetAddTransaction withCell(@NonNull WidgetCellRect value) {
        return new WidgetAddTransaction(token, appWidgetId, provider, profileSerial, stage,
            value, page, gridRevision, originToken, requestedOptions, startedAtMillis);
    }

    @NonNull public WidgetAddTransaction withPage(int value) {
        return new WidgetAddTransaction(token, appWidgetId, provider, profileSerial, stage,
            cell, value, gridRevision, originToken, requestedOptions, startedAtMillis);
    }
}
