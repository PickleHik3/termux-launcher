package com.termux.app.launcher.widget;

import android.app.Activity;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.LauncherApps;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Lifecycle and transaction owner for launcher-hosted widgets. A-1 intentionally has no picker UI. */
public final class LauncherWidgetHostController implements LauncherAppWidgetHost.Callback {
    public static final int REQUEST_BIND_APPWIDGET = 4714;
    public static final int REQUEST_CONFIGURE_APPWIDGET = 4715;

    public enum Capability { AVAILABLE, UNSUPPORTED }
    public enum AddResult {
        STARTED,
        READY,
        BUSY,
        UNSUPPORTED,
        STORAGE_FAILURE,
        DECLINED,
        CONFIGURATION_UNAVAILABLE,
        FAILED,
        NO_SPACE,
        REMOVED,
        REMOVE_FAILED,
        IGNORED
    }

    public interface Listener {
        void onWidgetRepositoryChanged(@NonNull AddResult result);
    }

    /** Injectable platform boundary used by deterministic lifecycle and cleanup tests. */
    public interface Platform {
        boolean hasAppWidgetsFeature();
        int allocateAppWidgetId();
        void deleteAppWidgetId(int appWidgetId);
        boolean bindIfAllowed(int appWidgetId, @NonNull UserHandle profile,
                              @NonNull ComponentName provider, @NonNull Bundle options);
        void launchBindConsent(@NonNull Intent intent, int requestCode);
        void launchConfiguration(int appWidgetId, int requestCode, @NonNull Bundle options);
        @Nullable AppWidgetProviderInfo getInfo(int appWidgetId);
        int[] getOwnedIds();
        void startListening();
        void stopListening();
        @NonNull AppWidgetHostView createView(int appWidgetId, @NonNull AppWidgetProviderInfo info);
        void updateOptions(int appWidgetId, @NonNull Bundle options);
        long profileSerial(@NonNull UserHandle profile);
        boolean configureActivityAvailable(@NonNull ComponentName configure,
                                           @Nullable UserHandle profile);
    }

    private final Activity activity;
    private final LauncherWidgetRepository repository;
    private final LauncherAppWidgetHost host;
    private final Platform platform;
    private final Capability capability;
    private final Map<Integer, AppWidgetHostView> hostViews = new HashMap<>();
    private final Set<String> resumedPendingTokens = new HashSet<>();
    private boolean listening;
    @Nullable private Listener listener;

    public LauncherWidgetHostController(@NonNull Activity activity) {
        this(activity, LauncherWidgetRepository.create(activity), null);
    }

    public LauncherWidgetHostController(@NonNull Activity activity,
                                        @NonNull LauncherWidgetRepository repository,
                                        @Nullable Platform injectedPlatform) {
        this.activity = activity;
        this.repository = repository;
        host = new LauncherAppWidgetHost(activity);
        host.setCallback(this);
        platform = injectedPlatform == null ? new AndroidPlatform(activity, host) : injectedPlatform;
        boolean supported;
        try {
            supported = platform.hasAppWidgetsFeature();
        } catch (RuntimeException exception) {
            supported = false;
        }
        capability = supported ? Capability.AVAILABLE : Capability.UNSUPPORTED;
    }

    @NonNull public Capability capability() { return capability; }
    @NonNull public LauncherWidgetRepository repository() { return repository; }
    @NonNull public LauncherAppWidgetHost host() { return host; }
    public void setListener(@Nullable Listener value) { listener = value; }

    private void notifyChanged(@NonNull AddResult result) {
        if (listener != null) listener.onWidgetRepositoryChanged(result);
    }

    /**
     * User-driven per-ID removal through A-1's durable tombstone path. The activity-owned host is
     * deliberately retained; only this allocation is released.
     */
    @NonNull
    public AddResult removeWidget(int appWidgetId) {
        if (repository.get(appWidgetId) == null) return AddResult.IGNORED;
        if (!repository.beginRecordDeletion(appWidgetId)) {
            notifyChanged(AddResult.REMOVE_FAILED);
            return AddResult.REMOVE_FAILED;
        }
        try {
            platform.deleteAppWidgetId(appWidgetId);
            hostViews.remove(appWidgetId);
            if (!repository.completeDeletion(appWidgetId, null)) {
                notifyChanged(AddResult.REMOVE_FAILED);
                return AddResult.REMOVE_FAILED;
            }
        } catch (RuntimeException exception) {
            // Reconciliation resumes the persisted DELETING record; the ID never becomes orphaned.
            notifyChanged(AddResult.REMOVE_FAILED);
            return AddResult.REMOVE_FAILED;
        }
        notifyChanged(AddResult.REMOVED);
        return AddResult.REMOVED;
    }

    public void onStart() {
        if (capability == Capability.UNSUPPORTED || listening) return;
        try {
            platform.startListening();
            listening = true;
        } catch (RuntimeException ignored) {
            // A partial framework/service initialization is retried on the next onStart.
        }
        reconcileProviders();
    }

    public void onStop() {
        if (!listening) return;
        try {
            platform.stopListening();
        } catch (RuntimeException ignored) {
            // stopListening is best effort; the activity-owned host is no longer consulted.
        } finally {
            listening = false;
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) hostViews.clear();
        }
    }

    @NonNull
    public AddResult cancelPendingAdd() {
        WidgetAddTransaction pending = repository.pending();
        return pending == null ? AddResult.IGNORED : abandon(pending, AddResult.DECLINED);
    }

    @NonNull
    public AddResult beginAdd(@NonNull AppWidgetProviderInfo selected,
                              @Nullable Bundle initialOptions) {
        WidgetGridPlacementPolicy.Result placement = WidgetGridPlacementPolicy.findPlacement(
            repository.gridDefinition(), repository.records(), 1, 1);
        if (placement.outcome != WidgetGridPlacementPolicy.Outcome.PLACED) return AddResult.NO_SPACE;
        return beginAdd(selected, placement.rect, repository.revision(), initialOptions, null);
    }

    @NonNull
    public AddResult beginAdd(@NonNull AppWidgetProviderInfo selected,
                              @NonNull WidgetCellRect reservedCell, long expectedGridRevision,
                              @Nullable Bundle initialOptions, @Nullable String originToken) {
        if (capability == Capability.UNSUPPORTED) return AddResult.UNSUPPORTED;
        if (repository.pending() != null) return AddResult.BUSY;
        if (!repository.canReserve(expectedGridRevision, reservedCell)) return AddResult.NO_SPACE;
        ComponentName provider = selected.provider;
        UserHandle profile = selected.getProfile() == null ? Process.myUserHandle() : selected.getProfile();
        long profileSerial;
        try {
            profileSerial = platform.profileSerial(profile);
        } catch (RuntimeException exception) {
            return AddResult.FAILED;
        }
        Bundle options = initialOptions == null ? new Bundle() : new Bundle(initialOptions);
        int id;
        try {
            id = platform.allocateAppWidgetId();
        } catch (RuntimeException exception) {
            return AddResult.FAILED;
        }
        WidgetAddTransaction transaction = new WidgetAddTransaction(UUID.randomUUID().toString(),
            id, provider, profileSerial, WidgetAddTransaction.Stage.ALLOCATED, reservedCell,
            expectedGridRevision, originToken, options, System.currentTimeMillis());
        if (!repository.reservePending(expectedGridRevision, transaction)) {
            deleteUnpersistedAllocation(id);
            return repository.canReserve(expectedGridRevision, reservedCell)
                ? AddResult.STORAGE_FAILURE : AddResult.NO_SPACE;
        }
        try {
            boolean bound = platform.bindIfAllowed(id, profile, provider, options);
            WidgetBindFlowPolicy.Decision decision = WidgetBindFlowPolicy.afterAllocation(bound);
            transaction = transaction.withStage(decision.nextStage);
            if (!repository.setPending(transaction)) return abandon(transaction, AddResult.STORAGE_FAILURE);
            if (bound) return continueAfterBound(transaction);

            Intent consent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, profile)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options);
            platform.launchBindConsent(consent, REQUEST_BIND_APPWIDGET);
            return AddResult.STARTED;
        } catch (ActivityNotFoundException | SecurityException exception) {
            return abandon(transaction, AddResult.FAILED);
        } catch (RuntimeException exception) {
            return abandon(transaction, AddResult.FAILED);
        }
    }

    /** @return true only for the two widget request codes, even when the durable result is stale. */
    public boolean handleActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode != REQUEST_BIND_APPWIDGET && requestCode != REQUEST_CONFIGURE_APPWIDGET) {
            return false;
        }
        WidgetAddTransaction pending = repository.pending();
        int returnedId = data == null ? -1
            : data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        boolean ok = resultCode == Activity.RESULT_OK;
        boolean matches = false;
        if (ok && pending != null) {
            try {
                matches = providerMatches(pending, platform.getInfo(pending.appWidgetId));
            } catch (RuntimeException ignored) {
                // An OK result without verifiable provider/profile identity is failed and deleted.
            }
        }
        WidgetBindFlowPolicy.Decision decision = requestCode == REQUEST_BIND_APPWIDGET
            ? WidgetBindFlowPolicy.onBindResult(pending, pending == null ? -1 : pending.appWidgetId,
                returnedId, ok, matches)
            : WidgetBindFlowPolicy.onConfigureResult(pending,
                pending == null ? -1 : pending.appWidgetId, returnedId, ok, matches);
        if (decision.outcome == WidgetBindFlowPolicy.Outcome.IGNORE_FOREIGN_RESULT) return true;
        if (pending == null) return true;
        if (decision.deleteId) {
            AddResult result = abandon(pending, decision.outcome == WidgetBindFlowPolicy.Outcome.DECLINED
                ? AddResult.DECLINED : AddResult.FAILED);
            notifyChanged(result);
            return true;
        }
        WidgetAddTransaction next = pending.withStage(decision.nextStage);
        if (!repository.setPending(next)) {
            notifyChanged(abandon(pending, AddResult.STORAGE_FAILURE));
            return true;
        }
        AddResult result = requestCode == REQUEST_BIND_APPWIDGET
            ? continueAfterBound(next) : commitActive(next);
        notifyChanged(result);
        return true;
    }

    @NonNull
    private AddResult continueAfterBound(@NonNull WidgetAddTransaction transaction) {
        AppWidgetProviderInfo info;
        WidgetConfigurePolicy.Decision configure;
        try {
            info = platform.getInfo(transaction.appWidgetId);
            if (!providerMatches(transaction, info)) return abandon(transaction, AddResult.FAILED);
            configure = WidgetConfigurePolicy.decide(info.configure,
                Build.VERSION.SDK_INT >= 28 ? info.widgetFeatures : 0, Build.VERSION.SDK_INT,
                info.configure == null
                    || platform.configureActivityAvailable(info.configure, info.getProfile()));
        } catch (RuntimeException exception) {
            return abandon(transaction, AddResult.FAILED);
        }
        WidgetBindFlowPolicy.Decision flow = WidgetBindFlowPolicy.afterConfigureDecision(configure);
        if (flow.deleteId) return abandon(transaction, AddResult.CONFIGURATION_UNAVAILABLE);
        WidgetAddTransaction next = transaction.withStage(flow.nextStage);
        if (!repository.setPending(next)) return abandon(transaction, AddResult.STORAGE_FAILURE);
        if (configure == WidgetConfigurePolicy.Decision.NONE) return commitActive(next);
        try {
            platform.launchConfiguration(next.appWidgetId, REQUEST_CONFIGURE_APPWIDGET,
                next.requestedOptions());
            return AddResult.STARTED;
        } catch (ActivityNotFoundException | SecurityException exception) {
            return abandon(next, AddResult.CONFIGURATION_UNAVAILABLE);
        } catch (RuntimeException exception) {
            return abandon(next, AddResult.FAILED);
        }
    }

    @NonNull
    private AddResult commitActive(@NonNull WidgetAddTransaction transaction) {
        WidgetAddTransaction committing = transaction.withStage(WidgetAddTransaction.Stage.COMMITTING);
        if (!repository.setPending(committing)) return abandon(transaction, AddResult.STORAGE_FAILURE);
        LauncherWidgetRecord record = new LauncherWidgetRecord(committing.appWidgetId,
            committing.provider, committing.profileSerial, LauncherWidgetRecord.State.ACTIVE,
            committing.cell, committing.requestedOptions(), null);
        if (!repository.finalizeActive(committing.token, record)) {
            return abandon(committing, AddResult.STORAGE_FAILURE);
        }
        return AddResult.READY;
    }

    @NonNull
    private AddResult abandon(@NonNull WidgetAddTransaction transaction, @NonNull AddResult result) {
        if (!repository.beginPendingDeletion(transaction)) {
            // Storage is already failing, so retaining the system allocation would create an
            // untrackable leak. Delete the one known ID immediately; the older durable pending
            // record, if any, is harmless and can be cleared by a later reconciliation.
            deleteUnpersistedAllocation(transaction.appWidgetId);
            return AddResult.STORAGE_FAILURE;
        }
        try {
            platform.deleteAppWidgetId(transaction.appWidgetId);
            hostViews.remove(transaction.appWidgetId);
            repository.completeDeletion(transaction.appWidgetId, transaction.token);
        } catch (RuntimeException ignored) {
            // The durable DELETING record makes the per-ID cleanup resumable after process death.
        }
        return result;
    }

    private void deleteUnpersistedAllocation(int id) {
        try { platform.deleteAppWidgetId(id); } catch (RuntimeException ignored) { }
    }

    /** Best-effort provider metadata for edit affordances; null when the platform throws. */
    @Nullable public AppWidgetProviderInfo providerInfo(int appWidgetId) {
        try {
            return platform.getInfo(appWidgetId);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Nullable
    public AppWidgetHostView createHostView(int appWidgetId) {
        LauncherWidgetRecord record = repository.get(appWidgetId);
        if (record == null || record.state != LauncherWidgetRecord.State.ACTIVE) return null;
        AppWidgetHostView existing = hostViews.get(appWidgetId);
        if (existing != null) return existing;
        try {
            AppWidgetProviderInfo info = platform.getInfo(appWidgetId);
            if (!providerMatches(record, info)) return null;
            AppWidgetHostView created = platform.createView(appWidgetId, info);
            hostViews.put(appWidgetId, created);
            return created;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public boolean onHostSizeCommitted(int appWidgetId, int widthPx, int heightPx, int orientation) {
        LauncherWidgetRecord record = repository.get(appWidgetId);
        if (record == null || record.state != LauncherWidgetRecord.State.ACTIVE) return false;
        WidgetSizeOptionsPolicy.Result result = WidgetSizeOptionsPolicy.calculate(record.sizeOptions(),
            widthPx, heightPx, activity.getResources().getDisplayMetrics().density,
            orientation, Build.VERSION.SDK_INT);
        if (!result.valid || !result.changed) return false;
        LauncherWidgetRecord changed = record.withSizeOptions(result.options);
        if (!repository.putRecord(changed)) return false;
        try {
            platform.updateOptions(appWidgetId, result.options);
            return true;
        } catch (RuntimeException exception) {
            repository.putRecord(record.withRenderFailure("options"));
            return false;
        }
    }

    public void reconcileProviders() { reconcileProviders(-1); }

    private void reconcileProviders(int changedId) {
        if (capability == Capability.UNSUPPORTED) return;
        Set<Integer> owned = new HashSet<>();
        try {
            for (int id : platform.getOwnedIds()) owned.add(id);
        } catch (RuntimeException exception) {
            // Without the host allocation snapshot, absence cannot safely mean uninstall.
            return;
        }
        for (LauncherWidgetRecord record : repository.records()) {
            AppWidgetProviderInfo info;
            try {
                info = platform.getInfo(record.appWidgetId);
            } catch (RuntimeException exception) {
                // A transient app-widget service failure is not provider removal.
                continue;
            }
            boolean matches;
            try {
                matches = providerMatches(record, info);
            } catch (RuntimeException exception) {
                continue;
            }
            WidgetProviderReconcilePolicy.Decision decision =
                WidgetProviderReconcilePolicy.forRecord(record, true,
                    owned.contains(record.appWidgetId),
                    matches, changedId == record.appWidgetId && matches);
            switch (decision) {
                case REFRESH_AFTER_UPDATE:
                    repository.putRecord(record.withRenderFailure(null));
                    break;
                case RETRY_TOMBSTONE_DELETE_ID:
                    try { platform.deleteAppWidgetId(record.appWidgetId); }
                    catch (RuntimeException ignored) { }
                    hostViews.remove(record.appWidgetId);
                    break;
                case TOMBSTONE_AND_DELETE_ID:
                    LauncherWidgetRecord tombstone = new LauncherWidgetRecord(record.appWidgetId,
                        record.provider, record.profileSerial,
                        LauncherWidgetRecord.State.PROVIDER_MISSING, record.cell, record.sizeOptions(),
                        record.lastRenderFailure);
                    if (repository.putRecord(tombstone)) {
                        try { platform.deleteAppWidgetId(record.appWidgetId); }
                        catch (RuntimeException ignored) { }
                        hostViews.remove(record.appWidgetId);
                    }
                    break;
                case RESUME_DELETION:
                    try {
                        platform.deleteAppWidgetId(record.appWidgetId);
                        hostViews.remove(record.appWidgetId);
                        WidgetAddTransaction deletingPending = repository.pending();
                        repository.completeDeletion(record.appWidgetId,
                            deletingPending != null
                                && deletingPending.appWidgetId == record.appWidgetId
                                ? deletingPending.token : null);
                    } catch (RuntimeException ignored) { }
                    break;
                default:
                    break;
            }
        }
        // Recover host-owned IDs omitted by a corrupt/partially migrated repository one by one.
        Set<Integer> represented = new HashSet<>();
        for (LauncherWidgetRecord record : repository.records()) represented.add(record.appWidgetId);
        WidgetAddTransaction representedPending = repository.pending();
        if (representedPending != null) represented.add(representedPending.appWidgetId);
        java.util.ArrayList<Integer> recoverableOwned = new java.util.ArrayList<>(owned);
        java.util.Collections.sort(recoverableOwned);
        for (int id : recoverableOwned) {
            if (represented.contains(id)) continue;
            AppWidgetProviderInfo info;
            try { info = platform.getInfo(id); }
            catch (RuntimeException exception) { continue; }
            if (info == null) {
                try { platform.deleteAppWidgetId(id); } catch (RuntimeException ignored) { }
                continue;
            }
            WidgetGridPlacementPolicy.Result placement = WidgetGridPlacementPolicy.findPlacement(
                repository.gridDefinition(), repository.records(), 1, 1);
            if (placement.outcome != WidgetGridPlacementPolicy.Outcome.PLACED) continue;
            try {
                long serial = platform.profileSerial(info.getProfile());
                repository.putRecord(new LauncherWidgetRecord(id, info.provider, serial,
                    LauncherWidgetRecord.State.ACTIVE, placement.rect, new Bundle(), null));
            } catch (RuntimeException ignored) {
                // Keep the allocation owned and retry recovery on the next reconciliation.
            }
        }
        WidgetAddTransaction pending = repository.pending();
        if (pending == null) return;
        boolean pendingMatches;
        try {
            pendingMatches = providerMatches(pending, platform.getInfo(pending.appWidgetId));
        } catch (RuntimeException exception) {
            // A transient system-service failure must neither expire nor relaunch this allocation.
            return;
        }
        WidgetProviderReconcilePolicy.Decision pendingDecision =
            WidgetProviderReconcilePolicy.forPending(pending, pendingMatches,
                System.currentTimeMillis());
        if (pendingDecision == WidgetProviderReconcilePolicy.Decision.EXPIRE_PENDING_AND_DELETE_ID) {
            abandon(pending, AddResult.FAILED);
        } else if (pendingDecision == WidgetProviderReconcilePolicy.Decision.RESUME_ACTIVE_COMMIT) {
            commitActive(pending);
        } else if (pendingDecision == WidgetProviderReconcilePolicy.Decision.RESUME_CONFIGURATION
            && resumedPendingTokens.add(pending.token)) {
            continueAfterBound(pending.withStage(WidgetAddTransaction.Stage.BOUND));
        }
    }

    private boolean providerMatches(WidgetAddTransaction expected, @Nullable AppWidgetProviderInfo info) {
        return info != null && expected.provider.equals(info.provider)
            && expected.profileSerial == platform.profileSerial(info.getProfile());
    }

    private boolean providerMatches(LauncherWidgetRecord expected, @Nullable AppWidgetProviderInfo info) {
        return info != null && expected.provider.equals(info.provider)
            && expected.profileSerial == platform.profileSerial(info.getProfile());
    }

    @Override
    public void onProviderChanged(int appWidgetId, @NonNull AppWidgetProviderInfo info) {
        reconcileProviders(appWidgetId);
        notifyChanged(AddResult.IGNORED);
    }

    @Override public void onProvidersChanged() { reconcileProviders(); notifyChanged(AddResult.IGNORED); }
    @Override public void onAppWidgetRemoved(int appWidgetId) {
        reconcileProviders(appWidgetId); notifyChanged(AddResult.IGNORED);
    }

    @Override
    public void onRenderFailure(int appWidgetId, @NonNull String phase) {
        LauncherWidgetRecord record = repository.get(appWidgetId);
        if (record != null) repository.putRecord(record.withRenderFailure(phase));
    }

    @Override
    public void onRenderRecovered(int appWidgetId) {
        LauncherWidgetRecord record = repository.get(appWidgetId);
        if (record != null) repository.putRecord(record.withRenderFailure(null));
    }

    private static final class AndroidPlatform implements Platform {
        private final Activity activity;
        private final LauncherAppWidgetHost host;
        private final AppWidgetManager manager;
        private final UserManager users;

        AndroidPlatform(Activity activity, LauncherAppWidgetHost host) {
            this.activity = activity;
            this.host = host;
            manager = AppWidgetManager.getInstance(activity);
            users = (UserManager) activity.getSystemService(Activity.USER_SERVICE);
        }

        @Override public boolean hasAppWidgetsFeature() {
            return activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS);
        }
        @Override public int allocateAppWidgetId() { return host.allocateAppWidgetId(); }
        @Override public void deleteAppWidgetId(int id) { host.deleteAppWidgetId(id); }
        @Override public boolean bindIfAllowed(int id, UserHandle profile, ComponentName provider,
                                               Bundle options) {
            return manager.bindAppWidgetIdIfAllowed(id, profile, provider, options);
        }
        @Override public void launchBindConsent(Intent intent, int code) {
            activity.startActivityForResult(intent, code);
        }
        @Override public void launchConfiguration(int id, int code, Bundle options) {
            host.startAppWidgetConfigureActivityForResult(activity, id, 0, code, options);
        }
        @Override public AppWidgetProviderInfo getInfo(int id) { return manager.getAppWidgetInfo(id); }
        @Override public int[] getOwnedIds() { return host.getAppWidgetIds(); }
        @Override public void startListening() { host.startListening(); }
        @Override public void stopListening() { host.stopListening(); }
        @Override public AppWidgetHostView createView(int id, AppWidgetProviderInfo info) {
            return host.createView(activity, id, info);
        }
        @Override public void updateOptions(int id, Bundle options) {
            manager.updateAppWidgetOptions(id, options);
        }
        @Override public long profileSerial(UserHandle profile) {
            return users == null || profile == null ? 0L : users.getSerialNumberForUser(profile);
        }
        @Override public boolean configureActivityAvailable(ComponentName configure,
                                                            UserHandle profile) {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(configure);
            if (profile != null && !profile.equals(Process.myUserHandle())) {
                LauncherApps launcherApps = (LauncherApps) activity.getSystemService(
                    Activity.LAUNCHER_APPS_SERVICE);
                return launcherApps != null
                    && launcherApps.resolveActivity(intent, profile) != null;
            }
            ResolveInfo info = activity.getPackageManager().resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
            return info != null;
        }
    }
}
