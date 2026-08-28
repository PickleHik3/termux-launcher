package com.termux.app.launcher.data;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.termux.R;
import com.termux.ai.TaiManager;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;

/**
 * Categorizes installed apps with the on-device model and writes the assignments into
 * {@code app-categories.conf}. Runs in the foreground because a full catalogue is many inferences
 * and the user leaves Settings while it works.
 *
 * <p>Progress is published as one static snapshot rather than broadcasts: the Settings screens in
 * this repo poll on a handler, so a subscription mechanism would be dead weight.
 */
public final class LauncherCategorySortService extends Service {
    public static final String ACTION_SORT = "com.termux.app.launcher.action.SORT_CATEGORIES";
    public static final String EXTRA_MODEL_ID = "model_id";

    private static final String CHANNEL_ID = "termux_launcher_category_sort";
    /**
     * Distinct from every other notification this app posts. It used to be 24110, the id
     * {@link com.termux.ai.TaiRuntimeService} posts its own foreground notification under from the
     * {@code :tai_runtime} process — so the two overwrote and cancelled each other, and a finished
     * sort could leave its last frame ("saving", bar full) stuck under the runtime's ownership.
     */
    private static final int NOTIFICATION_ID = 24112;
    private static final int RESULT_NOTIFICATION_ID = 24113;
    private static final int MAX_TOKENS = 24;
    private static final long NOTIFICATION_INTERVAL_MS = 750L;

    /**
     * Everything a poller wants to know about the run, read together so a phase is never paired with
     * another phase's count. Immutable; the service swaps in a fresh copy per change.
     */
    static final class Snapshot {
        static final Snapshot IDLE = new Snapshot(false, 0, 0,
            LauncherCategorySortProgress.PHASE_PREPARING, null, false, null);

        final boolean running;
        final int processed;
        final int total;
        /** One of the {@link LauncherCategorySortProgress} phase constants. */
        @NonNull final String phase;
        @Nullable final String errorMessage;
        final boolean cancelRequested;
        /** The last finished run's own words, kept so the settings row can report it on return. */
        @Nullable final String outcome;

        Snapshot(boolean running, int processed, int total, @NonNull String phase,
                 @Nullable String errorMessage, boolean cancelRequested, @Nullable String outcome) {
            this.running = running;
            this.processed = processed;
            this.total = total;
            this.phase = phase;
            this.errorMessage = errorMessage;
            this.cancelRequested = cancelRequested;
            this.outcome = outcome;
        }

        Snapshot withRunning(boolean value) {
            return new Snapshot(value, processed, total, phase, errorMessage, cancelRequested, outcome);
        }
        Snapshot withProcessed(int value) {
            return new Snapshot(running, value, total, phase, errorMessage, cancelRequested, outcome);
        }
        Snapshot withTotal(int value) {
            return new Snapshot(running, processed, value, phase, errorMessage, cancelRequested, outcome);
        }
        Snapshot withPhase(@NonNull String value) {
            return new Snapshot(running, processed, total, value, errorMessage, cancelRequested, outcome);
        }
        Snapshot withErrorMessage(@Nullable String value) {
            return new Snapshot(running, processed, total, phase, value, cancelRequested, outcome);
        }
        Snapshot withCancelRequested(boolean value) {
            return new Snapshot(running, processed, total, phase, errorMessage, value, outcome);
        }
        Snapshot withOutcome(@Nullable String value) {
            return new Snapshot(running, processed, total, phase, errorMessage, cancelRequested, value);
        }
    }

    @NonNull private static volatile Snapshot state = Snapshot.IDLE;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private long lastNotificationUpdateMs;

    /** The current run's state in one read. */
    @NonNull static Snapshot snapshot() { return state; }
    public static boolean isRunning() { return state.running; }
    public static int getProcessed() { return state.processed; }
    public static int getTotal() { return state.total; }
    /** One of the {@link LauncherCategorySortProgress} phase constants. */
    @NonNull public static String getPhase() { return state.phase; }
    @Nullable public static String getErrorMessage() { return state.errorMessage; }
    /**
     * @return what the last finished run did, or null when none has finished in this process. Read
     *     by the settings row, which is routinely re-created after the run it started.
     */
    @Nullable public static String getOutcome() { return state.outcome; }

    public static void cancel() { update(s -> s.withCancelRequested(true)); }
    public static boolean isCancelRequested() { return state.cancelRequested; }

    /** Copy-on-write under one lock: the worker's counts and a cancel from Settings must not race. */
    private static synchronized void update(@NonNull UnaryOperator<Snapshot> change) {
        state = change.apply(state);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureChannel();
        startForeground(NOTIFICATION_ID, buildNotification(
            getString(R.string.settings_app_drawer_category_sort_hint_preparing), 0, true, true));
        if (intent == null || !ACTION_SORT.equals(intent.getAction()) || state.running) {
            stopForeground(true);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        update(s -> Snapshot.IDLE.withRunning(true));
        String modelId = intent.getStringExtra(EXTRA_MODEL_ID);
        executor.execute(() -> {
            try {
                runSort(modelId);
            } catch (Throwable t) {
                String error = t.getMessage() == null ? t.toString() : t.getMessage();
                update(s -> s.withErrorMessage(error).withOutcome(
                    getString(R.string.settings_app_drawer_category_sort_failed, error)));
            } finally {
                update(s -> s.withRunning(false).withCancelRequested(false));
                // Order matters: drop the ongoing progress notification first, then post the
                // result as its own dismissible one — a user who left Settings mid-run learns how
                // it ended without going back in.
                stopForeground(true);
                postResultNotification();
                stopSelf(startId);
            }
        });
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        update(s -> s.withRunning(false));
        super.onDestroy();
    }

    private void runSort(@Nullable String modelId) throws Exception {
        LauncherAppDataProvider provider = LauncherAppDataProvider.getInstance(this);
        LinkedHashMap<String, String> labelByPackage = new LinkedHashMap<>();
        for (LauncherAppEntry entry : provider.getAllAppsBlocking()) {
            if (entry == null) continue;
            // The config file is package-keyed, but a package shows up once per work/private
            // profile, so collapse to the first entry instead of classifying it twice.
            if (labelByPackage.containsKey(entry.appRef.packageName)) continue;
            labelByPackage.put(entry.appRef.packageName, entry.label);
        }

        File file = LauncherCategoryFile.defaultFile();
        LauncherCategoryFile existing;
        try {
            existing = LauncherCategoryFile.parse(file);
        } catch (Exception ignored) {
            existing = LauncherCategoryFile.empty();
        }

        // Only packages the file does not mention yet get classified: a re-run must stay cheap and
        // must never overwrite hand edits or an earlier run's assignments.
        List<String> pending = new ArrayList<>();
        for (String packageName : labelByPackage.keySet()) {
            if (existing.categoryForPackage(packageName) == null) pending.add(packageName);
        }
        update(s -> s.withTotal(pending.size()));
        if (pending.isEmpty()) {
            // Nothing new to classify. Loading a model to sort zero apps would burn 10-20 seconds
            // and then flash a progress bar that was never measuring anything.
            String nothingPending = getString(
                R.string.settings_app_drawer_category_sort_nothing_pending, labelByPackage.size());
            update(s -> s.withOutcome(nothingPending));
            new LauncherCategorySortState(this).recordRun(System.currentTimeMillis(),
                labelByPackage.size(), LauncherCategorySortState.SOURCE_ON_DEVICE_MODEL, modelId);
            return;
        }

        LinkedHashMap<String, List<String>> merged = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> section : existing.sections().entrySet())
            merged.put(section.getKey(), new ArrayList<>(section.getValue()));

        TaiManager manager = TaiManager.getInstance(this);
        // Loading is minutes of the run on a cold runtime, and it used to happen invisibly inside
        // the first inference — which read as "stuck at 0 of N". Load it here so the phase is a
        // phase the user can see.
        update(s -> s.withPhase(LauncherCategorySortProgress.PHASE_LOADING_MODEL));
        updateProgressNotification(true);
        loadModel(manager, modelId);
        update(s -> s.withPhase(LauncherCategorySortProgress.PHASE_SORTING));
        int assigned = 0;
        for (String packageName : pending) {
            if (state.cancelRequested) break;
            String label = labelByPackage.get(packageName);
            String slug = classify(manager, modelId, label == null ? packageName : label, packageName);
            update(s -> s.withProcessed(s.processed + 1));
            updateProgressNotification(false);
            // An unparseable reply leaves the app out of the file entirely so the drawer's built-in
            // classifier keeps handling it; it is a skip, never a fallback category.
            if (slug == null) continue;
            List<String> packages = merged.get(slug);
            if (packages == null) {
                packages = new ArrayList<>();
                merged.put(slug, packages);
            }
            packages.add(packageName);
            assigned++;
        }

        update(s -> s.withPhase(LauncherCategorySortProgress.PHASE_SAVING));
        updateProgressNotification(true);
        if (assigned > 0) LauncherCategoryFile.of(merged).write(file);

        LinkedHashSet<String> written = new LinkedHashSet<>();
        for (List<String> packages : merged.values()) written.addAll(packages);
        new LauncherCategorySortState(this).recordRun(
            System.currentTimeMillis(),
            written.size(),
            LauncherCategorySortState.SOURCE_ON_DEVICE_MODEL,
            modelId
        );
        String done = state.cancelRequested
            ? getString(R.string.settings_app_drawer_category_sort_cancelled, assigned)
            : getString(R.string.settings_app_drawer_category_sort_done, assigned, merged.size());
        update(s -> s.withOutcome(done));

        provider.invalidate();
    }

    /**
     * Loads the model up front. A failure is not fatal on purpose: the per-app inference below will
     * try to load it again and report its own error, and a preflight warning that only blocks the
     * explicit load must not cancel a run the user asked for.
     */
    private void loadModel(@NonNull TaiManager manager, @Nullable String modelId) {
        if (modelId == null || modelId.trim().isEmpty()) return;
        try {
            JSONObject request = new JSONObject();
            request.put("model", modelId);
            manager.loadModel(request.toString());
        } catch (Exception ignored) {
        }
    }

    @Nullable
    private String classify(@NonNull TaiManager manager, @Nullable String modelId,
                            @NonNull String label, @NonNull String packageName) {
        try {
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", LauncherCategorySortPrompt.singleAppPrompt(label, packageName));
            JSONArray messages = new JSONArray();
            messages.put(message);

            JSONObject request = new JSONObject();
            if (modelId != null && !modelId.trim().isEmpty()) request.put("model", modelId);
            request.put("messages", messages);
            request.put("temperature", 0);
            request.put("max_tokens", MAX_TOKENS);
            request.put("stream", false);

            JSONObject response = manager.openAiChatCompletions(request.toString());
            JSONArray choices = response.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return null;
            JSONObject choice = choices.optJSONObject(0);
            if (choice == null) return null;
            JSONObject reply = choice.optJSONObject("message");
            if (reply == null) return null;
            return LauncherCategorySortPrompt.parseCategory(reply.optString("content", ""));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void updateProgressNotification(boolean force) {
        long elapsed = SystemClock.elapsedRealtime();
        if (!force && elapsed - lastNotificationUpdateMs < NOTIFICATION_INTERVAL_MS) return;
        lastNotificationUpdateMs = elapsed;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        Snapshot now = state;
        manager.notify(NOTIFICATION_ID, buildNotification(progressText(now),
            LauncherCategorySortProgress.percent(now.phase, now.processed, now.total),
            LauncherCategorySortProgress.isIndeterminate(now.phase), true));
    }

    /**
     * The run's last word, as a dismissible notification. Posted under its own id so it does not
     * race the foreground notification this service just dropped.
     */
    private void postResultNotification() {
        String text = state.outcome;
        if (text == null || text.trim().isEmpty()) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        ensureChannel();
        manager.notify(RESULT_NOTIFICATION_ID, new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentTitle(getString(R.string.settings_app_drawer_category_sort_title))
            .setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(settingsIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build());
    }

    @NonNull
    private PendingIntent settingsIntent() {
        return PendingIntent.getActivity(this, 0, new Intent(this, SettingsActivity.class),
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    /** The same phase wording the settings row shows, so the two surfaces never disagree. */
    @NonNull
    private String progressText(@NonNull Snapshot now) {
        return getString(LauncherCategorySortProgress.hint(now.phase, now.processed, now.total),
            now.processed, now.total);
    }

    private Notification buildNotification(String text, int percent, boolean indeterminate,
                                           boolean ongoing) {
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            settingsIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentTitle("Sorting apps into categories")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setPriority(NotificationCompat.PRIORITY_LOW);
        if (ongoing) builder.setProgress(indeterminate ? 0 : 100, indeterminate ? 0 : percent,
            indeterminate);
        return builder.build();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "App categorization", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Progress for on-device app categorization");
        manager.createNotificationChannel(channel);
    }
}
