package com.termux.app.launcher.widget;

import android.content.ComponentName;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.AppRef;

/**
 * Immutable durable identity and lifecycle state for one item on a Home page: a launcher-owned
 * app-widget ID bound to another app's provider, one of the launcher's own built-in widgets, or
 * an app icon. {@link #kind} says which.
 *
 * <p>A built-in widget has no platform ID. It is keyed by a negative number the repository hands
 * out ({@link LauncherWidgetRepository#allocateBuiltinId()}), so every map, outline and drag that
 * is keyed by {@link #appWidgetId} works unchanged, and its {@link #provider} is the synthetic
 * {@link #builtinProvider(String)} so the provider-equality rules keep holding. {@link #builtinKind}
 * names which built-in it is.</p>
 *
 * <p>An app icon ({@link Kind#APP}) is keyed the same way, by a negative number from the same
 * allocator, so it shares the page and cell space with the widgets and every move, page and
 * collision rule holds for it unchanged. Its {@link #provider} is the app's activity, its
 * {@link #profileSerial} the profile's serial, and {@link #appRef} the whole reference the launch
 * needs. Its options bundle is the icon's own: the label it was added under ({@link #APP_LABEL}),
 * and the room a per-icon override will take later.</p>
 */
public final class LauncherWidgetRecord {
    public enum State { ACTIVE, PROVIDER_MISSING, DELETING }

    /**
     * What a Home item is. {@link #FOLDER} is reserved for the folders still to come: no record
     * of that kind can be made yet, and a stored one is left out on load.
     */
    public enum Kind { APP_WIDGET, BUILTIN, APP, FOLDER }

    /** The package a built-in widget's synthetic provider names; never a real package. */
    public static final String BUILTIN_PROVIDER_PACKAGE = "com.termux.launcher.builtin";

    /** The app icon's options key for the label it was added under, shown while the app is gone. */
    public static final String APP_LABEL = "label";

    public final int appWidgetId;
    @NonNull public final ComponentName provider;
    /** The built-in widget this record is, or null for an app widget bound to a provider. */
    @Nullable public final String builtinKind;
    @NonNull public final Kind kind;
    /** The app an {@link Kind#APP} item opens; null for every other kind. */
    @Nullable private final AppRef appRef;
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
        this(appWidgetId, provider, null, Kind.APP_WIDGET, null, profileSerial, state, cell, page,
            sizeOptions, lastRenderFailure);
    }

    private LauncherWidgetRecord(int appWidgetId, @NonNull ComponentName provider,
                                 @Nullable String builtinKind, @NonNull Kind kind,
                                 @Nullable AppRef appRef,
                                 long profileSerial, @NonNull State state,
                                 @NonNull WidgetCellRect cell, int page,
                                 @Nullable Bundle sizeOptions,
                                 @Nullable String lastRenderFailure) {
        switch (kind) {
            case APP_WIDGET:
                if (appWidgetId <= 0) {
                    throw new IllegalArgumentException("appWidgetId must be positive");
                }
                if (builtinKind != null || appRef != null) {
                    throw new IllegalArgumentException("an app widget names no built-in or app");
                }
                break;
            case BUILTIN:
                if (builtinKind == null) throw new IllegalArgumentException("built-in kind missing");
                if (appWidgetId >= 0) {
                    throw new IllegalArgumentException("a built-in widget's id must be negative");
                }
                break;
            case APP:
                if (appRef == null) throw new IllegalArgumentException("an app icon needs its app");
                if (builtinKind != null) {
                    throw new IllegalArgumentException("an app icon is not a built-in");
                }
                if (appWidgetId >= 0) {
                    throw new IllegalArgumentException("an app icon's id must be negative");
                }
                break;
            case FOLDER:
            default:
                throw new IllegalArgumentException("folders on Home are not supported yet");
        }
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        this.appWidgetId = appWidgetId;
        this.provider = provider;
        this.builtinKind = builtinKind;
        this.kind = kind;
        this.appRef = appRef == null ? null : appRef.copy();
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
        return new LauncherWidgetRecord(id, builtinProvider(kind), kind, Kind.BUILTIN, null, 0L,
            State.ACTIVE, cell, page, config, null);
    }

    /**
     * An app icon for {@code app}, keyed by {@code id} (negative, from the repository's
     * allocator). {@code options} is the icon's own bundle: {@link #APP_LABEL} now, its override
     * later.
     */
    @NonNull
    public static LauncherWidgetRecord app(int id, @NonNull AppRef app,
                                           @NonNull WidgetCellRect cell, int page,
                                           @Nullable Bundle options) {
        return new LauncherWidgetRecord(id, new ComponentName(app.packageName, app.activityName),
            null, Kind.APP, app, app.userSerialNumber, State.ACTIVE, cell, page, options, null);
    }

    /** The synthetic provider a built-in widget of {@code kind} is recorded under. */
    @NonNull public static ComponentName builtinProvider(@NonNull String kind) {
        return new ComponentName(BUILTIN_PROVIDER_PACKAGE, kind);
    }

    /** True for one of the launcher's own widgets; false for an app widget with a platform ID. */
    public boolean isBuiltin() { return builtinKind != null; }

    /** True for an app icon on a Home page. */
    public boolean isApp() { return kind == Kind.APP; }

    /**
     * True only for an app widget bound to a platform ID: the one kind with a provider process, a
     * host view and an allocation to give back. Built-ins and app icons are the launcher's own.
     */
    public boolean hasPlatformId() { return kind == Kind.APP_WIDGET; }

    /** A copy of the app an app icon opens; null for every other kind. */
    @Nullable public AppRef appRef() { return appRef == null ? null : appRef.copy(); }

    /** The label an app icon was added under, or empty. */
    @NonNull public String appLabel() {
        String label = sizeOptions.getString(APP_LABEL);
        return label == null ? "" : label;
    }

    /** Returns a defensive copy so repository snapshots stay immutable to callers. */
    @NonNull public Bundle sizeOptions() { return new Bundle(sizeOptions); }

    @NonNull
    public LauncherWidgetRecord withState(@NonNull State value) {
        return new LauncherWidgetRecord(appWidgetId, provider, builtinKind, kind, appRef,
            profileSerial, value, cell, page, sizeOptions, lastRenderFailure);
    }

    @NonNull
    public LauncherWidgetRecord withSizeOptions(@NonNull Bundle value) {
        return new LauncherWidgetRecord(appWidgetId, provider, builtinKind, kind, appRef,
            profileSerial, state, cell, page, value, lastRenderFailure);
    }

    @NonNull
    public LauncherWidgetRecord withRenderFailure(@Nullable String value) {
        return new LauncherWidgetRecord(appWidgetId, provider, builtinKind, kind, appRef,
            profileSerial, state, cell, page, sizeOptions, value);
    }

    @NonNull public LauncherWidgetRecord withCell(@NonNull WidgetCellRect value) {
        return new LauncherWidgetRecord(appWidgetId, provider, builtinKind, kind, appRef,
            profileSerial, state, value, page, sizeOptions, lastRenderFailure);
    }

    @NonNull public LauncherWidgetRecord withPage(int value) {
        return new LauncherWidgetRecord(appWidgetId, provider, builtinKind, kind, appRef,
            profileSerial, state, cell, value, sizeOptions, lastRenderFailure);
    }
}
