package com.termux.ai;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The download queue's controller: one per app process. It owns the worker threads, decides which
 * queued record starts next (at most {@link TaiSettings#getDownloadParallel()} at a time, in queue
 * order), and turns the persisted records from one state into the next.
 *
 * <p>Who writes a record: while a worker runs, only that worker (through
 * {@link TaiModelDownloader}); the engine steers it through its {@link TaiModelDownloader.Control}
 * and the worker writes the paused or cancelled state itself when it stops. A record with no
 * worker is the engine's to edit. That rule is what keeps two writers from racing on one record.
 *
 * <p>Workers run only while {@link TaiModelDownloadService} is attached: the service is the
 * foreground anchor that keeps the process alive for a multi-gigabyte transfer. Anything that
 * wants the queue pumped starts the service, which attaches and pumps.
 *
 * <p>Resumption after the process dies (D4): the service returns START_STICKY, so the system
 * restarts it and its {@code onStartCommand} reconciles; TaiManager's constructor reconciles too,
 * for the case where the launcher activity comes back first. WorkManager is not in the build and
 * would be a new dependency; a JobScheduler job would have to hand off to a foreground service
 * from the background anyway (it cannot hold a multi-GB transfer in its own window), so it buys
 * nothing here that the sticky restart does not. The launcher is the home app, so its process is
 * rarely gone for long, and every reconcile marks orphaned transfers {@code paused(app_closed)}
 * and lets the network watch resume them on an unmetered connection.
 */
public final class TaiDownloadEngine {
    private static TaiDownloadEngine instance;

    /** Told, on the main thread, when the last worker finished and nothing is queued to start. */
    public interface IdleListener {
        void onDownloadsIdle();
    }

    private final Context appContext;
    private final TaiModelStore store;
    private final TaiSettings settings;
    private final TaiDownloadHub hub;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService pool;
    /** Live workers by transfer id. Guarded by {@code this}. */
    private final Map<String, Worker> running = new LinkedHashMap<>();
    /** Tokens handed in with a request, by model id; TaiSettings' token is the fallback. */
    private final Map<String, String> requestTokens = new HashMap<>();
    private boolean serviceAttached;
    @Nullable private IdleListener idleListener;
    private boolean networkWatching;
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
            if (isUnmetered(capabilities)) autoResume();
        }
    };

    private TaiDownloadEngine(@NonNull Context context) {
        appContext = context.getApplicationContext();
        store = new TaiModelStore(appContext);
        settings = new TaiSettings(appContext);
        hub = TaiDownloadHub.get(appContext);
        AtomicInteger counter = new AtomicInteger();
        pool = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable runnable) {
                Thread thread = new Thread(runnable, "tai-download-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
        reconcile();
    }

    @NonNull
    public static synchronized TaiDownloadEngine getInstance(@NonNull Context context) {
        // A process has one Application, so the second test is the only caller that ever sees a
        // different one; an engine bound to a dead Application's preferences would write records
        // nobody reads.
        if (instance == null || instance.appContext != context.getApplicationContext()) {
            instance = new TaiDownloadEngine(context);
        }
        return instance;
    }

    @NonNull
    public TaiDownloadHub hub() {
        return hub;
    }

    // ---- requests ----

    /**
     * Records a download request as {@code queued} and asks the service to run the queue. A
     * record for the same transfer id that was paused, failed or cancelled is replaced: the
     * partial file (if the cancel did not delete it) is picked up by the worker's resume logic,
     * so re-requesting a paused model continues it. A record that already has a worker is left
     * alone and returned as it is.
     */
    @NonNull
    public JSONObject enqueue(@NonNull JSONObject record, @Nullable String authToken) {
        String transferId = record.optString("id", "");
        String modelId = record.optString("modelId", "");
        synchronized (this) {
            if (running.containsKey(transferId)) {
                JSONObject current = store.getDownload(transferId);
                return current == null ? record : current;
            }
            if (authToken != null && !authToken.trim().isEmpty()) requestTokens.put(modelId, authToken);
            else requestTokens.remove(modelId);
        }
        try {
            record.put("status", TaiModelStore.STATE_QUEUED);
            record.put("queuedAtMs", System.currentTimeMillis());
            record.put("updatedAtMs", System.currentTimeMillis());
            record.remove("pausedReason");
            record.remove("networkRetries");
        } catch (JSONException ignored) {
        }
        store.upsertDownload(record);
        hub.publish(record);
        requestService();
        return record;
    }

    /** Stops a transfer and keeps its partial file. A queued record pauses without ever starting.
     *  Returns false when there is nothing to pause (no record, or one already ended). */
    public boolean pause(@NonNull String modelId, @NonNull String reason) {
        JSONObject record = store.findDownloadForModel(modelId);
        if (record == null) return false;
        String transferId = record.optString("id", "");
        synchronized (this) {
            Worker worker = running.get(transferId);
            if (worker != null) {
                // The worker writes the paused record itself when it stops at the next checkpoint.
                worker.control.requestPause(reason);
                return true;
            }
        }
        if (!TaiModelStore.STATE_QUEUED.equals(record.optString("status", ""))) return false;
        JSONObject paused = store.updateDownload(transferId, r -> {
            r.put("status", TaiModelStore.STATE_PAUSED);
            r.put("pausedReason", reason);
        });
        if (paused != null) hub.publish(paused);
        return paused != null;
    }

    /** Puts a paused, failed or cancelled record back in line (at the back) and runs the queue.
     *  Returns false when there is no such record or it is not in a resumable state. */
    public boolean resume(@NonNull String modelId) {
        JSONObject record = store.findDownloadForModel(modelId);
        if (record == null) return false;
        String status = record.optString("status", "");
        if (!TaiModelStore.STATE_PAUSED.equals(status) && !TaiModelStore.STATE_FAILED.equals(status)
            && !TaiModelStore.STATE_CANCELLED.equals(status)) {
            return false;
        }
        JSONObject queued = requeue(record.optString("id", ""), System.currentTimeMillis(), true);
        if (queued == null) return false;
        requestService();
        return true;
    }

    /** Stops a transfer and deletes its partial files. A record that never started is marked
     *  cancelled directly. Returns false when there is no record for the model. */
    public boolean cancel(@NonNull String modelId) {
        JSONObject record = store.findDownloadForModel(modelId);
        if (record == null) return false;
        String transferId = record.optString("id", "");
        synchronized (this) {
            Worker worker = running.get(transferId);
            if (worker != null) {
                worker.control.requestCancel();
                return true;
            }
        }
        String status = record.optString("status", "");
        if (TaiModelStore.STATE_INSTALLED.equals(status) || TaiModelStore.STATE_CANCELLED.equals(status)) return false;
        TaiModelDownloader.deletePartials(new File(record.optString("path", "")));
        JSONObject cancelled = store.updateDownload(transferId, r -> {
            r.put("status", TaiModelStore.STATE_CANCELLED);
            r.put("error", "cancelled");
            r.remove("pausedReason");
        });
        if (cancelled != null) hub.publish(cancelled);
        return cancelled != null;
    }

    /**
     * "Start now": moves a queued record to the front of the line. When every slot is busy, the
     * transfer that has held its slot longest is stopped (its partial file kept) and put back in
     * line right behind the prioritised one, so it continues by itself when a slot frees. A
     * paused record is re-queued at the front the same way. Returns false when the model has no
     * record, or its record is already running or ended.
     */
    public boolean prioritize(@NonNull String modelId) {
        JSONObject record = store.findDownloadForModel(modelId);
        if (record == null) return false;
        String transferId = record.optString("id", "");
        String status = record.optString("status", "");
        boolean movable = TaiModelStore.STATE_QUEUED.equals(status) || TaiModelStore.STATE_PAUSED.equals(status)
            || TaiModelStore.STATE_FAILED.equals(status);
        synchronized (this) {
            if (running.containsKey(transferId) || !movable) return false;
            long front = TaiDownloadQueue.frontOfQueue(store.getDownloads());
            if (requeue(transferId, front, TaiModelStore.STATE_QUEUED.equals(status) ? false : true) == null) return false;
            if (running.size() >= settings.getDownloadParallel()) {
                JSONObject oldest = TaiDownloadQueue.oldestRunning(store.getDownloads(), running.keySet());
                Worker worker = oldest == null ? null : running.get(oldest.optString("id", ""));
                if (worker != null) worker.control.requestPause(SWAP_OUT);
            }
        }
        requestService();
        return true;
    }

    /** The worker that stops for a swap writes this reason, and is re-queued (right behind the
     *  prioritised one) as soon as it has. */
    private static final String SWAP_OUT = TaiDownloadQueue.PAUSED_SWAP_OUT;

    /** Forgets the process singleton so a test gets a fresh engine over a fresh application. */
    static synchronized void resetForTesting() {
        instance = null;
    }

    // ---- the queue ----

    /** The service calls this when it is up: from now on the queue runs. */
    public void attachService(@NonNull IdleListener listener) {
        synchronized (this) {
            serviceAttached = true;
            idleListener = listener;
        }
        pump();
    }

    public void detachService() {
        synchronized (this) {
            serviceAttached = false;
            idleListener = null;
        }
    }

    public synchronized boolean hasRunningWorkers() {
        return !running.isEmpty();
    }

    @NonNull
    public synchronized Set<String> runningTransferIds() {
        return new HashSet<>(running.keySet());
    }

    /**
     * Starts queued records while there are free slots, in queue order. Without an attached
     * service it only asks for one; the service's attach pumps again. A record that does not fit
     * on the disk is paused ({@code no_space}) and skipped, and a smaller one behind it may start.
     */
    public void pump() {
        List<JSONObject> toStart = new ArrayList<>();
        boolean idle;
        synchronized (this) {
            if (!serviceAttached) {
                if (!TaiDownloadQueue.nextToStart(store.getDownloads(), running.keySet(), TaiDownloadQueue.MAX_PARALLEL).isEmpty()) requestService();
                return;
            }
            int limit = settings.getDownloadParallel();
            // Loop: a candidate that fails the space check frees its slot for the next in line.
            Set<String> skipped = new HashSet<>();
            while (running.size() < limit) {
                JSONObject next = null;
                for (JSONObject candidate : TaiDownloadQueue.nextToStart(store.getDownloads(), running.keySet(), limit)) {
                    if (!skipped.contains(candidate.optString("id", ""))) {
                        next = candidate;
                        break;
                    }
                }
                if (next == null) break;
                String transferId = next.optString("id", "");
                TaiDownloadQueue.SpaceCheck space = spaceFor(next);
                if (!space.fits) {
                    JSONObject paused = store.updateDownload(transferId, r -> {
                        r.put("status", TaiModelStore.STATE_PAUSED);
                        r.put("pausedReason", TaiModelStore.PAUSED_NO_SPACE);
                        r.put("requiredBytes", space.requiredBytes);
                        r.put("freeBytes", space.freeBytes);
                    });
                    if (paused != null) hub.publish(paused);
                    skipped.add(transferId);
                    continue;
                }
                JSONObject started = store.updateDownload(transferId, r -> r.put("startedAtMs", System.currentTimeMillis()));
                if (started == null) {
                    skipped.add(transferId);
                    continue;
                }
                Worker worker = new Worker(started);
                running.put(transferId, worker);
                toStart.add(started);
                pool.execute(worker);
            }
            idle = running.isEmpty();
        }
        for (JSONObject record : toStart) hub.publish(record);
        watchNetworkIfNeeded();
        if (idle) notifyIdle();
    }

    /**
     * Marks records that claim a transfer in flight but have no worker as {@code paused
     * (app_closed)}: the process died under them. Queued records stay queued and are run again
     * when the service is up. Called once when the engine is built and again on a sticky restart.
     */
    public void reconcile() {
        List<JSONObject> orphans;
        synchronized (this) {
            orphans = TaiDownloadQueue.orphaned(store.getDownloads(), running.keySet());
            for (JSONObject orphan : orphans) {
                JSONObject paused = store.updateDownload(orphan.optString("id", ""), r -> {
                    r.put("status", TaiModelStore.STATE_PAUSED);
                    r.put("pausedReason", TaiModelStore.PAUSED_APP_CLOSED);
                    r.remove("currentFile");
                    r.remove("verifiedBytes");
                });
                if (paused != null) hub.publish(paused);
            }
        }
        if (!orphans.isEmpty()) hub.refresh();
        watchNetworkIfNeeded();
        // Anything paused by the app closing or the network continues by itself on an unmetered
        // network (D4); on a metered one it waits for a tap, so no mobile data is spent unasked.
        if (isUnmeteredNow()) autoResume();
        else if (!TaiDownloadQueue.nextToStart(store.getDownloads(), runningTransferIds(), TaiDownloadQueue.MAX_PARALLEL).isEmpty()) requestService();
    }

    /** Re-queues every record paused by the app closing or the network, unless it has failed
     *  to move a byte too many times in a row (then it waits for the person). */
    public void autoResume() {
        JSONArray records = store.getDownloads();
        boolean any = false;
        for (int i = 0; i < records.length(); i++) {
            JSONObject item = records.optJSONObject(i);
            if (item == null || !TaiDownloadQueue.autoResumable(item)) continue;
            if (item.optInt("networkRetries", 0) >= TaiModelDownloader.MAX_NETWORK_RETRIES_WITHOUT_PROGRESS) continue;
            if (requeue(item.optString("id", ""), System.currentTimeMillis(), false) != null) any = true;
        }
        if (any) requestService();
    }

    /** queued, with the given place in line. {@code resetRetries} forgets the network-retry count
     *  (a person asked, so the transfer gets its chances again). */
    @Nullable
    private JSONObject requeue(@NonNull String transferId, long queuedAtMs, boolean resetRetries) {
        JSONObject queued = store.updateDownload(transferId, r -> {
            r.put("status", TaiModelStore.STATE_QUEUED);
            r.put("queuedAtMs", queuedAtMs);
            r.put("error", "");
            r.remove("pausedReason");
            r.remove("requiredBytes");
            r.remove("freeBytes");
            r.remove("currentFile");
            r.remove("verifiedBytes");
            if (resetRetries) r.remove("networkRetries");
        });
        if (queued != null) hub.publish(queued);
        return queued;
    }

    @NonNull
    private TaiDownloadQueue.SpaceCheck spaceFor(@NonNull JSONObject record) {
        File output = new File(record.optString("path", ""));
        File dir = output.getParentFile() == null ? store.getModelsDirectory() : output.getParentFile();
        File probe = dir;
        while (probe != null && !probe.exists()) probe = probe.getParentFile();
        if (probe == null) probe = store.getModelsDirectory();
        long expected = record.optLong("totalBytes", 0L);
        if (expected <= 0L) expected = record.optLong("expectedSizeBytes", 0L);
        File partial = new File(output.getAbsolutePath() + ".part");
        long onDisk = partial.isFile() ? partial.length() : 0L;
        return TaiDownloadQueue.checkSpace(probe.getUsableSpace(), probe.getTotalSpace(), expected, onDisk);
    }

    private void requestService() {
        Intent intent = new Intent(appContext, TaiModelDownloadService.class);
        intent.setAction(TaiModelDownloadService.ACTION_SYNC);
        try {
            appContext.startForegroundService(intent);
        } catch (RuntimeException ignored) {
            // A background start the platform refuses: the records stay queued and the next
            // reconcile (the launcher coming to the front) tries again.
        }
    }

    private void notifyIdle() {
        IdleListener listener;
        synchronized (this) {
            listener = idleListener;
        }
        if (listener != null) mainHandler.post(listener::onDownloadsIdle);
    }

    // ---- network ----

    private static boolean isUnmetered(@NonNull NetworkCapabilities capabilities) {
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private boolean isUnmeteredNow() {
        ConnectivityManager manager = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        try {
            Network active = manager.getActiveNetwork();
            if (active == null) return false;
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(active);
            return capabilities != null && isUnmetered(capabilities);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The default-network callback is registered only while a record is waiting for the network
     *  to come back unmetered, and dropped once none is: no listener runs for nothing. */
    private void watchNetworkIfNeeded() {
        boolean wanted = false;
        JSONArray records = store.getDownloads();
        for (int i = 0; i < records.length() && !wanted; i++) {
            JSONObject item = records.optJSONObject(i);
            wanted = item != null && TaiDownloadQueue.autoResumable(item)
                && item.optInt("networkRetries", 0) < TaiModelDownloader.MAX_NETWORK_RETRIES_WITHOUT_PROGRESS;
        }
        ConnectivityManager manager = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return;
        synchronized (this) {
            if (wanted == networkWatching) return;
            try {
                if (wanted) manager.registerDefaultNetworkCallback(networkCallback);
                else manager.unregisterNetworkCallback(networkCallback);
                networkWatching = wanted;
            } catch (RuntimeException ignored) {
            }
        }
    }

    // ---- workers ----

    private final class Worker implements Runnable {
        final JSONObject record;
        final TaiModelDownloader.Control control = new TaiModelDownloader.Control();
        @Nullable volatile JSONObject last;

        Worker(@NonNull JSONObject record) {
            this.record = record;
        }

        @Override
        public void run() {
            String modelId = record.optString("modelId", "");
            String token;
            synchronized (TaiDownloadEngine.this) {
                token = requestTokens.get(modelId);
            }
            if (token == null || token.trim().isEmpty()) token = settings.getHuggingFaceToken();
            try {
                new TaiModelDownloader(appContext, store).runDownload(record, token, control, transfer -> {
                    last = transfer;
                    hub.publish(transfer);
                });
            } finally {
                finished(this);
            }
        }
    }

    private void finished(@NonNull Worker worker) {
        String transferId = worker.record.optString("id", "");
        JSONObject last = worker.last;
        synchronized (this) {
            running.remove(transferId);
        }
        if (last != null && TaiModelStore.STATE_PAUSED.equals(last.optString("status", ""))
            && SWAP_OUT.equals(last.optString("pausedReason", ""))) {
            // Swapped out by "start now": back in line behind the one that took its slot.
            requeue(transferId, TaiDownloadQueue.frontOfQueue(store.getDownloads()) + 1L, false);
        }
        // A speech download that was going to become the model in use (or replace a model's
        // window graph) settles here, whether or not the settings screen is open to see it end.
        if (TaiModelDownloader.capabilitiesOf(worker.record).contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)) {
            TaiSpeechModels.settlePending(appContext);
        }
        pump();
    }
}
