package com.termux.app.launcher.data;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.termux.R;
import com.termux.ai.TaiManager;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.shared.termux.TermuxConstants;

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

/**
 * Categorizes installed apps with the on-device model and writes the assignments into
 * {@code app-categories.conf}. Runs in the foreground because a full catalogue is many inferences
 * and the user leaves Settings while it works.
 *
 * <p>Progress is published as static volatile fields rather than broadcasts: the Settings screens
 * in this repo poll on a handler, so a subscription mechanism would be dead weight.
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
    private static final String CATEGORY_FILE_NAME = "app-categories.conf";
    private static final int MAX_TOKENS = 24;
    private static final long NOTIFICATION_INTERVAL_MS = 750L;

    private static volatile boolean running;
    private static volatile int processed;
    private static volatile int total;
    @NonNull private static volatile String phase = LauncherCategorySortProgress.PHASE_PREPARING;
    @Nullable private static volatile String errorMessage;
    private static volatile boolean cancelRequested;
    /** The last finished run's own words, kept so the settings row can report it on return. */
    @Nullable private static volatile String outcome;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private long lastNotificationUpdateMs;

    public static boolean isRunning() { return running; }
    public static int getProcessed() { return processed; }
    public static int getTotal() { return total; }
    /** One of the {@link LauncherCategorySortProgress} phase constants. */
    @NonNull public static String getPhase() { return phase; }
    @Nullable public static String getErrorMessage() { return errorMessage; }
    /**
     * @return what the last finished run did, or null when none has finished in this process. Read
     *     by the settings row, which is routinely re-created after the run it started.
     */
    @Nullable public static String getOutcome() { return outcome; }

    public static void cancel() { cancelRequested = true; }
    public static boolean isCancelRequested() { return cancelRequested; }

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
        if (intent == null || !ACTION_SORT.equals(intent.getAction()) || running) {
            stopForeground(true);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        running = true;
        cancelRequested = false;
        processed = 0;
        total = 0;
        phase = LauncherCategorySortProgress.PHASE_PREPARING;
        errorMessage = null;
        outcome = null;
        String modelId = intent.getStringExtra(EXTRA_MODEL_ID);
        executor.execute(() -> {
            try {
                runSort(modelId);
            } catch (Throwable t) {
                errorMessage = t.getMessage() == null ? t.toString() : t.getMessage();
                outcome = getString(R.string.settings_app_drawer_category_sort_failed, errorMessage);
            } finally {
                running = false;
                cancelRequested = false;
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
        running = false;
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

        File file = new File(TermuxConstants.TERMUX_HOME_DIR_PATH + "/" + CATEGORY_FILE_NAME);
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
        total = pending.size();
        if (pending.isEmpty()) {
            // Nothing new to classify. Loading a model to sort zero apps would burn 10-20 seconds
            // and then flash a progress bar that was never measuring anything.
            outcome = getString(R.string.settings_app_drawer_category_sort_nothing_pending,
                labelByPackage.size());
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
        phase = LauncherCategorySortProgress.PHASE_LOADING_MODEL;
        updateProgressNotification(true);
        loadModel(manager, modelId);
        phase = LauncherCategorySortProgress.PHASE_SORTING;
        int assigned = 0;
        for (String packageName : pending) {
            if (cancelRequested) break;
            String label = labelByPackage.get(packageName);
            String slug = classify(manager, modelId, label == null ? packageName : label, packageName);
            processed++;
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

        phase = LauncherCategorySortProgress.PHASE_SAVING;
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
        outcome = cancelRequested
            ? getString(R.string.settings_app_drawer_category_sort_cancelled, assigned)
            : getString(R.string.settings_app_drawer_category_sort_done, assigned, merged.size());

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
        long now = android.os.SystemClock.elapsedRealtime();
        if (!force && now - lastNotificationUpdateMs < NOTIFICATION_INTERVAL_MS) return;
        lastNotificationUpdateMs = now;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        int done = processed;
        int count = total;
        manager.notify(NOTIFICATION_ID, buildNotification(progressText(done, count),
            LauncherCategorySortProgress.percent(phase, done, count),
            LauncherCategorySortProgress.isIndeterminate(phase), true));
    }

    /**
     * The run's last word, as a dismissible notification. Posted under its own id so it does not
     * race the foreground notification this service just dropped.
     */
    private void postResultNotification() {
        String text = outcome;
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
    private String progressText(int done, int count) {
        return getString(LauncherCategorySortProgress.hint(phase, done, count), done, count);
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
