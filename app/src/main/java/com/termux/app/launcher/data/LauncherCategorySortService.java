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
import com.termux.ai.TaiCallerRequests;
import com.termux.ai.TaiFunction;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiRuntimePresence;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.launcher.drawer.AppDrawerCategory;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Categorizes installed apps with the on-device model and writes the assignments into
 * {@code app-categories.conf}. Runs in the foreground because a full catalogue is many inferences
 * and the user leaves Settings while it works. The on-device model is asked a few apps at a time
 * ({@link LauncherCategoryLocalSort}), so the category list is not re-read for every app; the
 * remote model is asked in blocks ({@link LauncherCategoryRemoteSort}), because free plans count
 * requests.
 *
 * <p>Progress is published as one static snapshot rather than broadcasts: the Settings screens in
 * this repo poll on a handler, so a subscription mechanism would be dead weight.
 */
public final class LauncherCategorySortService extends Service {
    public static final String ACTION_SORT = "com.termux.app.launcher.action.SORT_CATEGORIES";
    /** The local model id, or {@code remote/<id>} for the remote provider: app sorting's plan. */
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
    private static final long NOTIFICATION_INTERVAL_MS = 750L;
    /** One remote block is up to a hundred apps' answer from a free-plan model; give it time. */
    private static final long REMOTE_BLOCK_TIMEOUT_MS = 180_000L;

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
        List<LauncherAppEntry> catalogue = provider.getAllAppsBlocking();
        // Excludes x11:linux (every Linux app's shared package) as well as work/private twins; see
        // LauncherCategoryCatalogue for why the categoriser must never see that one as an "app".
        LinkedHashMap<String, String> labelByPackage = LauncherCategoryCatalogue.labelByPackage(catalogue);

        File file = LauncherCategoryFile.defaultFile();
        LauncherCategoryFile existing;
        try {
            existing = LauncherCategoryFile.parse(file);
        } catch (Exception ignored) {
            existing = LauncherCategoryFile.empty();
        }

        // Only what LauncherCategoryPendingApps calls pending is asked: nothing a drag or the file
        // already places (a re-run must stay cheap and never overwrite hand edits or an earlier
        // run's assignments), and nothing the drawer's own classifier places surely, which costs no
        // inference at all and is left out of the file.
        List<String> pending = new ArrayList<>();
        for (LauncherAppEntry entry : LauncherCategoryPendingApps.pending(catalogue,
                LauncherCategoryPendingApps.placement(this))) {
            pending.add(entry.appRef.packageName);
        }
        update(s -> s.withTotal(pending.size()));
        Assignments assignments = new Assignments(existing);
        if (pending.isEmpty()) {
            // Nothing new to classify. Loading a model to sort zero apps would burn 10-20 seconds
            // and then flash a progress bar that was never measuring anything.
            String nothingPending = getString(
                R.string.settings_app_drawer_category_sort_nothing_pending, labelByPackage.size());
            update(s -> s.withOutcome(nothingPending));
            recordRun(modelId, pending, labelByPackage.size(), assignments);
            return;
        }

        TaiManager manager = TaiManager.getInstance(this);
        // The sort unloads what it loads when it is done, and leaves a model it found resident.
        TaiRuntimePresence.Snapshot before = TaiRuntimePresence.read(this);
        String residentBefore = before.loaded ? before.modelId : null;
        // Loading is minutes of the run on a cold runtime, and it used to happen invisibly inside
        // the first inference — which read as "stuck at 0 of N". Load it here so the phase is a
        // phase the user can see.
        update(s -> s.withPhase(LauncherCategorySortProgress.PHASE_LOADING_MODEL));
        updateProgressNotification(true);
        String loadFailure = loadModel(manager, modelId);
        if (loadFailure != null) {
            // Every app would retry the same load on its own and fail the same way; under memory
            // pressure that is a reload per app. One clear stop instead.
            String failed = getString(R.string.settings_app_drawer_category_sort_load_failed, loadFailure);
            update(s -> s.withOutcome(failed));
            return;
        }
        try {
            sortPending(manager, modelId, pending, labelByPackage, assignments, file, provider);
        } finally {
            restoreRuntime(manager, modelId, residentBefore);
        }
    }

    private void sortPending(@NonNull TaiManager manager, @Nullable String modelId,
                             @NonNull List<String> pending, @NonNull Map<String, String> labelByPackage,
                             @NonNull Assignments assignments, @NonNull File file,
                             @NonNull LauncherAppDataProvider provider) throws Exception {
        update(s -> s.withPhase(LauncherCategorySortProgress.PHASE_SORTING));
        String stopError = null;
        boolean stalled = false;
        if (TaiCallerRequests.isRemoteModel(modelId)) {
            // Stops on the first failed request already: see LauncherCategoryRemoteSort.
            LauncherCategoryRemoteSort.Result result = LauncherCategoryRemoteSort.run(pending,
                new RemoteRequests(manager, modelId, labelByPackage, assignments));
            stopError = result.error;
        } else {
            List<LauncherCategorySortPrompt.AppEntry> apps = new ArrayList<>();
            for (String packageName : pending) {
                String label = labelByPackage.get(packageName);
                apps.add(new LauncherCategorySortPrompt.AppEntry(packageName, label == null ? packageName : label));
            }
            stalled = LauncherCategoryLocalSort.run(apps, new LocalRequests(manager, modelId, assignments)).stalled;
        }
        int assigned = assignments.assigned;

        update(s -> s.withPhase(LauncherCategorySortProgress.PHASE_SAVING));
        updateProgressNotification(true);
        if (assigned > 0) LauncherCategoryFile.of(assignments.merged).write(file);
        recordRun(modelId, pending, labelByPackage.size(), assignments);

        String done = state.cancelRequested
            ? getString(R.string.settings_app_drawer_category_sort_cancelled, assigned)
            : stalled
            ? getString(R.string.settings_app_drawer_category_sort_stalled, assigned)
            : stopError != null
            ? getString(R.string.settings_app_drawer_category_sort_stopped, assigned, stopError)
            : getString(R.string.settings_app_drawer_category_sort_done, assigned, assignments.merged.size());
        update(s -> s.withOutcome(done));

        provider.invalidate();
    }

    /**
     * Records the run, and the apps it answered {@code other} for that are still unplaced (now or by
     * an earlier run): those stay pending, so the next sort asks again, but the drawer's notice and
     * the settings row do not count them as waiting. Anything placed since drops out of that set.
     */
    private void recordRun(@Nullable String modelId, @NonNull List<String> pending, int appCount,
                           @NonNull Assignments assignments) {
        LauncherCategorySortState sortState = new LauncherCategorySortState(this);
        Set<String> before = sortState.getAnsweredOther();
        Set<String> answeredOther = new HashSet<>();
        for (String packageName : pending) {
            String lower = packageName.toLowerCase(Locale.US);
            if (assignments.answeredOther.contains(packageName)
                || before.contains(lower) && !assignments.assignedPackages.contains(packageName)) {
                answeredOther.add(lower);
            }
        }
        sortState.setAnsweredOther(answeredOther);
        // Covered: every app but those this run asked about and got no answer for.
        int unanswered = pending.size() - assignments.assigned - assignments.answeredOther.size();
        sortState.recordRun(System.currentTimeMillis(), appCount - Math.max(0, unanswered),
            LauncherCategorySortState.modelSource(modelId), modelId);
    }

    /**
     * The file's sections with this run's answers merged in. An answer of {@code other} is not
     * written: the drawer's classifier falls to Other by itself, and leaving the app out keeps a
     * later curated row, platform category or a better model free to place it.
     */
    static final class Assignments {
        final LinkedHashMap<String, List<String>> merged = new LinkedHashMap<>();
        final Set<String> assignedPackages = new HashSet<>();
        final Set<String> answeredOther = new HashSet<>();
        int assigned;

        Assignments(@NonNull LauncherCategoryFile existing) {
            for (Map.Entry<String, List<String>> section : existing.sections().entrySet())
                merged.put(section.getKey(), new ArrayList<>(section.getValue()));
        }

        void accept(@NonNull String packageName, @NonNull String slug) {
            if (AppDrawerCategory.OTHER.slug.equals(slug)) {
                answeredOther.add(packageName);
                return;
            }
            List<String> packages = merged.get(slug);
            if (packages == null) {
                packages = new ArrayList<>();
                merged.put(slug, packages);
            }
            packages.add(packageName);
            assignedPackages.add(packageName);
            assigned++;
        }
    }

    /**
     * Loads the model up front, unless the model is the remote provider's (nothing to load, and the
     * runtime process stays asleep). The load names the feature, so it follows app sorting's plan
     * (its accelerator, speculative decoding and 1024 window), and a resident model whose window
     * serves the sort is used as it is rather than reloaded.
     *
     * @return null when it loaded, otherwise the runtime's own sentence for why it did not. An
     *     explicit load only fails on something the per-app requests would hit too (not enough free
     *     memory, a missing file), so a failure here ends the run.
     */
    @Nullable
    private String loadModel(@NonNull TaiManager manager, @Nullable String modelId) {
        if (modelId == null || modelId.trim().isEmpty()) return null;
        if (TaiCallerRequests.isRemoteModel(modelId)) return null;
        try {
            JSONObject request = new JSONObject();
            request.put("model", modelId);
            request.put(TaiCallerRequests.FUNCTION, TaiFunction.APP_CATEGORIES.id());
            JSONObject result = manager.loadModel(request.toString());
            if (result.optBoolean("ok", false)) return null;
            String message = result.optString("message", "").trim();
            return message.isEmpty() ? getString(R.string.settings_app_drawer_category_sort_unavailable_model) : message;
        } catch (Exception e) {
            return getString(R.string.settings_app_drawer_category_sort_unavailable_model);
        }
    }

    /**
     * Ends the sort's hold on the runtime: the model it loaded is unloaded, and the one resident before
     * is not reloaded (the feature load plan's residency for app sorting); the next feature loads its own.
     * Only that chat model goes, and only while it is still the sort's: speech, embeddings and a model
     * another feature has loaded since stay.
     */
    private void restoreRuntime(@NonNull TaiManager manager, @Nullable String sortModel,
                                @Nullable String residentBefore) {
        try {
            if (TaiCallerRequests.restoreAfterSort(residentBefore, sortModel) == TaiCallerRequests.Restore.UNLOAD) {
                manager.unloadChatModel(sortModel);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * The on-device sort's requests: a batch, or one app the batch's reply missed. The bodies are
     * {@link TaiCallerRequests#categoryBody}'s: thinking off, temperature 0, no user system prompt,
     * and the feature named so TAI loads by app sorting's plan. An unparseable reply leaves the app
     * out of the file so the drawer's built-in classifier keeps it; a request that brought back no
     * reply at all, or an empty one, is a failed request.
     */
    private final class LocalRequests implements LauncherCategoryLocalSort.Requests {
        private final TaiManager manager;
        @Nullable private final String modelId;
        private final Assignments assignments;

        LocalRequests(@NonNull TaiManager manager, @Nullable String modelId, @NonNull Assignments assignments) {
            this.manager = manager;
            this.modelId = modelId;
            this.assignments = assignments;
        }

        @Override
        public boolean cancelled() {
            return state.cancelRequested;
        }

        @NonNull
        @Override
        public LauncherCategoryRemoteSort.Answer batch(@NonNull List<LauncherCategorySortPrompt.AppEntry> apps) {
            return ask(manager, modelId, LauncherCategorySortPrompt.batchPrompt(apps),
                LauncherCategorySortPrompt.batchMaxTokens(apps), 0L, true,
                content -> LauncherCategorySortPrompt.parseBatchReply(content, apps));
        }

        @NonNull
        @Override
        public LauncherCategoryRemoteSort.Answer single(@NonNull LauncherCategorySortPrompt.AppEntry app) {
            return ask(manager, modelId, LauncherCategorySortPrompt.singleAppPrompt(app.label, app.packageName),
                LauncherCategorySortPrompt.MAX_TOKENS, 0L, true, content -> {
                    String slug = LauncherCategorySortPrompt.parseCategory(content);
                    return slug == null ? Collections.emptyMap() : Collections.singletonMap(app.packageName, slug);
                });
        }

        @Override
        public void settled(@NonNull Map<String, String> answered, int settled) {
            for (Map.Entry<String, String> entry : answered.entrySet())
                assignments.accept(entry.getKey(), entry.getValue());
            update(s -> s.withProcessed(s.processed + settled));
            updateProgressNotification(false);
        }
    }

    /**
     * One category request: the reply's content goes to {@code parse}; no reply at all is a failed
     * request, and so is an empty one when {@code emptyFails}. A {@code timeoutMs} of 0 is TAI's own
     * default.
     */
    @NonNull
    private LauncherCategoryRemoteSort.Answer ask(@NonNull TaiManager manager, @Nullable String modelId,
                                                  @NonNull String prompt, int maxTokens, long timeoutMs,
                                                  boolean emptyFails,
                                                  @NonNull Function<String, Map<String, String>> parse) {
        try {
            String body = TaiCallerRequests.categoryBody(modelId, prompt, maxTokens).toString();
            JSONObject response = timeoutMs > 0 ? manager.openAiChatCompletions(body, timeoutMs)
                : manager.openAiChatCompletions(body);
            JSONArray choices = response.optJSONArray("choices");
            JSONObject choice = choices == null ? null : choices.optJSONObject(0);
            if (choice == null) return LauncherCategoryRemoteSort.Answer.failed(errorText(response));
            JSONObject reply = choice.optJSONObject("message");
            String content = reply == null ? "" : reply.optString("content", "");
            if (emptyFails && content.trim().isEmpty())
                return LauncherCategoryRemoteSort.Answer.failed(getString(R.string.settings_app_drawer_category_sort_remote_failed));
            return LauncherCategoryRemoteSort.Answer.of(parse.apply(content));
        } catch (Exception e) {
            String message = e.getMessage();
            return LauncherCategoryRemoteSort.Answer.failed(message == null || message.trim().isEmpty()
                ? getString(R.string.settings_app_drawer_category_sort_remote_failed) : message);
        }
    }

    @NonNull
    private String errorText(@NonNull JSONObject response) {
        String message = response.optString("message", "").trim();
        if (message.isEmpty()) {
            JSONObject error = response.optJSONObject("error");
            if (error != null) message = error.optString("message", "").trim();
        }
        return message.isEmpty() ? getString(R.string.settings_app_drawer_category_sort_remote_failed) : message;
    }

    /**
     * The remote sort's requests: a block in the clipboard prompt's format, or one app in the
     * on-device prompt. The bodies are {@link TaiCallerRequests#categoryBody}'s (temperature 0, no
     * user system prompt); TaiManager strips the TAI-only fields before they leave the phone.
     */
    private final class RemoteRequests implements LauncherCategoryRemoteSort.Requests {
        private final TaiManager manager;
        private final String modelId;
        private final Map<String, String> labelByPackage;
        private final Assignments assignments;

        RemoteRequests(@NonNull TaiManager manager, @NonNull String modelId,
                       @NonNull Map<String, String> labelByPackage, @NonNull Assignments assignments) {
            this.manager = manager;
            this.modelId = modelId;
            this.labelByPackage = labelByPackage;
            this.assignments = assignments;
        }

        @Override
        public boolean cancelled() {
            return state.cancelRequested;
        }

        @NonNull
        @Override
        public LauncherCategoryRemoteSort.Answer batch(@NonNull List<String> packages) {
            List<LauncherCategorySortPrompt.AppEntry> apps = new ArrayList<>();
            for (String packageName : packages) apps.add(new LauncherCategorySortPrompt.AppEntry(packageName, label(packageName)));
            return ask(manager, modelId, LauncherCategorySortPrompt.pasteablePrompt(apps),
                LauncherCategoryRemoteSort.maxTokens(packages.size()), REMOTE_BLOCK_TIMEOUT_MS, false,
                content -> LauncherCategorySortPrompt.parsePastedReply(content, new HashSet<>(packages)));
        }

        @NonNull
        @Override
        public LauncherCategoryRemoteSort.Answer single(@NonNull String packageName) {
            return ask(manager, modelId, LauncherCategorySortPrompt.singleAppPrompt(label(packageName), packageName),
                LauncherCategorySortPrompt.MAX_TOKENS, 0L, false, content -> {
                    String slug = LauncherCategorySortPrompt.parseCategory(content);
                    return slug == null ? Collections.emptyMap() : Collections.singletonMap(packageName, slug);
                });
        }

        @Override
        public void settled(@NonNull Map<String, String> assigned, int settled) {
            for (Map.Entry<String, String> entry : assigned.entrySet())
                assignments.accept(entry.getKey(), entry.getValue());
            update(s -> s.withProcessed(s.processed + settled));
            updateProgressNotification(false);
        }

        @NonNull
        private String label(@NonNull String packageName) {
            String label = labelByPackage.get(packageName);
            return label == null ? packageName : label;
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
