package com.termux.app.chrome.wallpaper;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.termux.R;
import com.termux.app.chrome.scene.ShaderCheck;
import com.termux.app.chrome.scene.ShaderErrors;
import com.termux.app.chrome.scene.ShaderStore;
import com.termux.app.chrome.scene.WallpaperMotion;
import com.termux.app.surfaces.AppearanceEditorEntry;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Send to AI's round trip, as one notification: the user copies the photo image and the prompt
 * into any AI chat, and pastes the chat's shader into the inline reply. A shader that compiles is
 * saved into its folder and made the wallpaper as the editor's Apply and Done would; one that
 * does not shows its first error with Copy error and the reply field again.
 *
 * <p>This is the one apply outside the Appearance editor's Done, and only on the user's paste.
 * An open wallpaper picker learns of it through its {@link Watcher}, so its own Done or discard
 * keeps the new wallpaper instead of writing back the one it opened with. While the notification
 * asks, it holds the shader's folder ({@link #holds}); closing it without an apply drops the
 * folder unless the editor still has it open ({@link PhotoShaderHandoff}).</p>
 *
 * <p>A paste over a saved source keeps the old one beside it; Undo puts it and the wallpaper from
 * before the paste back and asks again, so another answer can be pasted ({@link PhotoShaderUndo}).</p>
 */
public final class PhotoShaderNotification {

    private static final String LOG_TAG = "PhotoShaderNotification";
    private static final String ACTION_PREFIX = "com.termux.app.chrome.wallpaper.photo_shader.";
    static final String ACTION_COPY_IMAGE = ACTION_PREFIX + "COPY_IMAGE";
    static final String ACTION_COPY_PROMPT = ACTION_PREFIX + "COPY_PROMPT";
    static final String ACTION_REPLY = ACTION_PREFIX + "REPLY";
    static final String ACTION_COPY_ERROR = ACTION_PREFIX + "COPY_ERROR";
    static final String ACTION_CLOSE = ACTION_PREFIX + "CLOSE";
    static final String ACTION_UNDO = ACTION_PREFIX + "UNDO";
    static final String ACTION_DONE = ACTION_PREFIX + "DONE";
    private static final String EXTRA_ID = "shader_id";
    private static final String EXTRA_PREVIOUS = "previous_motion";
    private static final String EXTRA_ERRORS = "errors";
    static final String KEY_REPLY_TEXT = "photo_shader_reply";

    private static final String CHANNEL_ID = "termux_launcher_photo_shader";
    private static final int NOTIFICATION_ID = 24114;
    /** The copied image, replaced by each copy; served by {@link com.termux.app.terminal.ClipboardFileProvider}. */
    private static final String IMAGE_FILE = "wallpaper-photo.jpg";
    private static final String CLIPBOARD_DIR = "clipboard";
    private static final String AUTHORITY = TermuxConstants.TERMUX_PACKAGE_NAME + ".clipboard";

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "photo-shader-notification");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** The open wallpaper picker, told about an apply from here; main thread. */
    public interface Watcher {
        /** Whether its editor is open on shader {@code id}. */
        boolean isEditing(@NonNull String id);

        /** The notification made {@code motion} the wallpaper (a paste, or its Undo). */
        void onMotionApplied(@NonNull WallpaperMotion motion);

        /**
         * Undo put shader {@code id}'s earlier source back (after {@link #onMotionApplied} with the
         * earlier wallpaper): an editor open on it must not save the undone code back.
         */
        void onUndone(@NonNull String id);

        /** The phone's Home and Lock changed after that apply. */
        void onSystemWallpaperChanged();

        /** A themed context the wallpaper's still reads its colours from. */
        @NonNull
        Context context();
    }

    @NonNull private static WeakReference<Watcher> sWatcher = new WeakReference<>(null);
    /** The shader the asking notification holds; main thread. */
    @Nullable private static String sPendingId;
    /** The shader the applied notification shows, whose replaced source is kept for Undo; main thread. */
    @Nullable private static String sAppliedId;

    private PhotoShaderNotification() {}

    // --- the editor's side ---

    @MainThread
    public static void watch(@NonNull Watcher watcher) {
        sWatcher = new WeakReference<>(watcher);
    }

    @MainThread
    public static void unwatch(@NonNull Watcher watcher) {
        if (sWatcher.get() == watcher) sWatcher = new WeakReference<>(null);
    }

    /** Whether the asking notification holds shader {@code id}'s folder. */
    @MainThread
    public static boolean holds(@Nullable String id) {
        return id != null && id.equals(sPendingId);
    }

    /** @return true when notifications would actually show. */
    public static boolean canPost(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * Posts the asking notification for shader {@code id}, whose folder already holds its photo,
     * depth image and settings. A shader it held before goes as a close would drop it.
     *
     * @return false when notifications are blocked
     */
    @MainThread
    public static boolean post(@NonNull Context context, @NonNull String id) {
        Context app = context.getApplicationContext();
        if (!canPost(app) || !validId(id)) return false;
        hold(app, id);
        // The applied notification and its Undo are replaced: the source Undo would restore goes.
        String applied = sAppliedId;
        if (applied != null) endApplied(app, applied, PhotoShaderUndo.End.NEW_ASK);
        return showAsking(app, id, R.string.photo_shader_notification_text);
    }

    /** The asking notification holds shader {@code id}; a shader it held before goes as a close would drop it. */
    @MainThread
    private static void hold(@NonNull Context app, @NonNull String id) {
        String previous = sPendingId;
        sPendingId = id;
        if (previous != null && !previous.equals(id)) dropIfUnheld(app, previous);
    }

    /** The applied notification for shader {@code id} ends: its kept source goes unless Undo used it. */
    @MainThread
    private static void endApplied(@NonNull Context app, @NonNull String id, @NonNull PhotoShaderUndo.End end) {
        if (id.equals(sAppliedId)) sAppliedId = null;
        if (PhotoShaderUndo.dropsKept(end)) WORKER.execute(() -> ShaderStore.dropPreviousSource(app, id));
    }

    // --- the notification's states ---

    /** Copy image, Copy prompt and the reply field, with {@code text} as its line. */
    @MainThread
    private static boolean showAsking(@NonNull Context app, @NonNull String id, int textRes) {
        if (!canPost(app)) return false;
        ensureChannel(app);
        String text = app.getString(textRes);
        NotificationCompat.Builder builder = base(app, editorIntent(app, id, true))
            .setContentTitle(app.getString(R.string.photo_shader_notification_title))
            .setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
            // Its three slots hold Copy image, Copy prompt and Paste, so a swipe is the only way to
            // close it: ongoing only where a swipe still dismisses an ongoing notification (14+).
            .setOngoing(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            .setDeleteIntent(broadcast(app, ACTION_CLOSE, id, 5, null))
            .addAction(action(app, R.string.photo_shader_notification_copy_image,
                broadcast(app, ACTION_COPY_IMAGE, id, 1, null)))
            .addAction(action(app, R.string.photo_shader_notification_copy_prompt,
                broadcast(app, ACTION_COPY_PROMPT, id, 2, null)))
            .addAction(replyAction(app, id));
        return notify(app, builder);
    }

    /** The paste compiled and is the wallpaper: Undo, Edit, Done; no longer ongoing. */
    private static void showApplied(@NonNull Context app, @NonNull String id, @NonNull String previousMotion) {
        if (!canPost(app)) return;
        ensureChannel(app);
        sAppliedId = id;
        Bundle undo = new Bundle();
        undo.putString(EXTRA_PREVIOUS, previousMotion);
        PendingIntent edit = editorIntent(app, id, false);
        notify(app, base(app, edit)
            .setContentTitle(app.getString(R.string.photo_shader_notification_applied_title))
            .setContentText(app.getString(R.string.photo_shader_notification_applied_text))
            .setOngoing(false)
            .setAutoCancel(true)
            // A swipe ends it as Done does: the source Undo would restore goes.
            .setDeleteIntent(broadcast(app, ACTION_DONE, id, 8, null))
            .addAction(action(app, R.string.photo_shader_notification_undo,
                broadcast(app, ACTION_UNDO, id, 6, undo)))
            .addAction(action(app, R.string.photo_shader_notification_edit, edit))
            .addAction(action(app, R.string.photo_shader_notification_done,
                broadcast(app, ACTION_DONE, id, 8, null))));
    }

    /** The paste did not compile: the first error, Copy error, the reply field again, Close; still ongoing. */
    private static void showFailed(@NonNull Context app, @NonNull String id, @NonNull List<ShaderErrors.Error> errors) {
        if (!canPost(app)) return;
        ensureChannel(app);
        String first = PhotoShaderPrompt.firstError(errors);
        Bundle report = new Bundle();
        report.putString(EXTRA_ERRORS, PhotoShaderPrompt.errorReport(
            app.getString(R.string.photo_shader_error_report_intro), errors));
        notify(app, base(app, editorIntent(app, id, true))
            .setContentTitle(app.getString(R.string.photo_shader_notification_failed_title))
            .setContentText(first)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(first))
            .setOngoing(true)
            .setDeleteIntent(broadcast(app, ACTION_CLOSE, id, 5, null))
            .addAction(action(app, R.string.photo_shader_notification_copy_error,
                broadcast(app, ACTION_COPY_ERROR, id, 9, report)))
            .addAction(replyAction(app, id))
            .addAction(action(app, R.string.photo_shader_notification_close,
                broadcast(app, ACTION_CLOSE, id, 10, null))));
    }

    private static void cancel(@NonNull Context app) {
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    /** {@code body}: where a tap on the notification goes (the shader's editor). */
    @NonNull
    private static NotificationCompat.Builder base(@NonNull Context app, @NonNull PendingIntent body) {
        return new NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentIntent(body)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW);
    }

    /**
     * The launcher with the wallpaper picker's editor open on shader {@code id}: the shader being
     * made, in its photo flow ({@code photoFlow}), or the saved one as the Shader list opens it.
     */
    @NonNull
    private static PendingIntent editorIntent(@NonNull Context app, @NonNull String id, boolean photoFlow) {
        Intent intent = AppearanceEditorEntry.shaderIntent(app, id, photoFlow).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(app, photoFlow ? 0 : 7, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
    }

    @NonNull
    private static NotificationCompat.Action action(@NonNull Context app, int label, @NonNull PendingIntent intent) {
        return new NotificationCompat.Action.Builder(0, app.getString(label), intent).build();
    }

    @NonNull
    private static NotificationCompat.Action replyAction(@NonNull Context app, @NonNull String id) {
        RemoteInput input = new RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel(app.getString(R.string.photo_shader_notification_paste_hint))
            .build();
        return new NotificationCompat.Action.Builder(0, app.getString(R.string.photo_shader_notification_paste),
            broadcast(app, ACTION_REPLY, id, 3, null))
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .build();
    }

    @NonNull
    private static PendingIntent broadcast(@NonNull Context app, @NonNull String action, @NonNull String id,
                                           int requestCode, @Nullable Bundle extras) {
        Intent intent = new Intent(app, ActionReceiver.class).setAction(action).putExtra(EXTRA_ID, id);
        if (extras != null) intent.putExtras(extras);
        // The reply's text is written into its intent, so that one must stay mutable.
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (ACTION_REPLY.equals(action)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
        } else {
            flags |= immutableFlag();
        }
        return PendingIntent.getBroadcast(app, requestCode, intent, flags);
    }

    private static int immutableFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private static boolean notify(@NonNull Context app, @NonNull NotificationCompat.Builder builder) {
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        if (manager == null) return false;
        try {
            manager.notify(NOTIFICATION_ID, builder.build());
            return true;
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Posting the notification failed", e);
            return false;
        }
    }

    private static void ensureChannel(@NonNull Context app) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
            app.getString(R.string.photo_shader_notification_channel), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(app.getString(R.string.photo_shader_notification_channel_description));
        manager.createNotificationChannel(channel);
    }

    // --- the work behind the buttons ---

    /** A shader id that names a folder; anything else from an intent is ignored. */
    private static boolean validId(@Nullable String id) {
        return id != null && WallpaperMotion.shader(id).kind == WallpaperMotion.Kind.SHADER;
    }

    @Nullable
    private static Watcher watcher() {
        return sWatcher.get();
    }

    /** Drops shader {@code id}'s folder when nothing was ever applied from it and the editor does not have it open. */
    @MainThread
    private static void dropIfUnheld(@NonNull Context app, @NonNull String id) {
        Watcher w = watcher();
        final boolean editorHolds = w != null && w.isEditing(id);
        WORKER.execute(() -> {
            boolean hasSource = ShaderStore.sourceFile(app, id).isFile();
            if (PhotoShaderHandoff.notificationDrops(hasSource, editorHolds)) ShaderStore.delete(app, id);
        });
    }

    @MainThread
    private static void setClip(@NonNull Context app, @NonNull ClipData clip) {
        ClipboardManager cm = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        try {
            cm.setPrimaryClip(clip);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Putting the copy on the clipboard failed", e);
        }
    }

    /** Whether shader {@code id} has a depth image on disk now. */
    @WorkerThread
    private static boolean hasDepth(@NonNull Context app, @NonNull String id) {
        File depth = ShaderStore.depthFile(app, id, ShaderStore.readMeta(app, id));
        return depth != null && depth.isFile();
    }

    @WorkerThread
    private static void copyImage(@NonNull Context app, @NonNull String id) {
        ShaderStore.Meta meta = ShaderStore.readMeta(app, id);
        File photo = ShaderStore.textureFile(app, id, meta);
        File out = new File(new File(app.getCacheDir(), CLIPBOARD_DIR), IMAGE_FILE);
        Boolean written = photo == null ? null : SideBySideImage.write(photo, ShaderStore.depthFile(app, id, meta), out);
        Uri uri = null;
        if (written != null) {
            try {
                uri = FileProvider.getUriForFile(app, AUTHORITY, out);
            } catch (RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Serving the photo image failed", e);
            }
        }
        final Uri copied = uri;
        MAIN.post(() -> {
            if (copied == null) {
                Toast.makeText(app, R.string.shader_texture_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                setClip(app, ClipData.newUri(app.getContentResolver(),
                    app.getString(R.string.photo_shader_notification_title), copied));
            } catch (RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Copying the photo image failed", e);
            }
        });
    }

    @WorkerThread
    private static void copyPrompt(@NonNull Context app, @NonNull String id) {
        String prompt = PhotoShaderPrompt.read(app, hasDepth(app, id));
        if (prompt == null) return;
        MAIN.post(() -> setClip(app, ClipData.newPlainText(
            app.getString(R.string.photo_shader_notification_title), prompt)));
    }

    /** Reads the answer, checks it and either saves and applies it or shows why not. */
    @WorkerThread
    private static void paste(@NonNull Context app, @NonNull String id, @Nullable String answer) {
        String code = PhotoShaderPrompt.extractCode(answer);
        List<ShaderErrors.Error> errors;
        if (code.isEmpty()) {
            errors = Collections.singletonList(new ShaderErrors.Error(-1, app.getString(R.string.shader_empty)));
        } else {
            errors = ShaderCheck.run(code);
        }
        if (!ShaderCheck.passed(errors)) {
            final List<ShaderErrors.Error> shown = new ArrayList<>(errors);
            MAIN.post(() -> showFailed(app, id, shown));
            return;
        }
        try {
            save(app, id, code);
        } catch (IOException | RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Saving the pasted shader failed", e);
            MAIN.post(() -> showFailed(app, id, Collections.singletonList(
                new ShaderErrors.Error(-1, app.getString(R.string.wallpaper_picker_apply_failed)))));
            return;
        }
        MAIN.post(() -> applyPasted(app, id));
    }

    /** The source, and settings that name only images still in the folder, as Apply saves them. */
    @WorkerThread
    private static void save(@NonNull Context app, @NonNull String id, @NonNull String code) throws IOException {
        boolean hasMeta = ShaderStore.metaFile(app, id).isFile();
        ShaderStore.Meta meta = hasMeta ? ShaderStore.readMeta(app, id)
            : ShaderStore.Meta.defaults(app.getString(R.string.shader_default_name));
        File texture = ShaderStore.textureFile(app, id, meta);
        File depth = ShaderStore.depthFile(app, id, meta);
        boolean textureOk = texture != null && texture.isFile();
        boolean depthOk = depth != null && depth.isFile();
        if (!hasMeta || (meta.texture != null && !textureOk) || (meta.depth != null && !depthOk)) {
            ShaderStore.writeMeta(app, id, new ShaderStore.Meta(meta.name, meta.quality, meta.rateHz,
                textureOk ? meta.texture : null, depthOk ? meta.depth : null));
        }
        // The source this replaces is kept for Undo; with none, an older kept one goes.
        boolean hadSource = ShaderStore.sourceFile(app, id).isFile();
        if (!(PhotoShaderUndo.pasteKeeps(hadSource) && ShaderStore.keepPreviousSource(app, id))) {
            ShaderStore.dropPreviousSource(app, id);
        }
        ShaderStore.writeSource(app, id, code);
    }

    @MainThread
    private static void applyPasted(@NonNull Context app, @NonNull String id) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, false);
        if (prefs == null) {
            showFailed(app, id, Collections.singletonList(
                new ShaderErrors.Error(-1, app.getString(R.string.wallpaper_picker_apply_failed))));
            return;
        }
        String previous = ShaderStore.readMotion(prefs.getSharedPreferences()).format();
        useMotion(app, WallpaperMotion.shader(id));
        // Saved now: the folder is a shader of its own, which no close drops.
        if (holds(id)) sPendingId = null;
        showApplied(app, id, previous);
    }

    /** Undo, off the main thread: the kept source goes back, then the wallpaper from before. */
    @WorkerThread
    private static void undo(@NonNull Context app, @NonNull String id, @Nullable String previousMotion) {
        boolean kept = ShaderStore.previousSourceFile(app, id).isFile();
        if (PhotoShaderUndo.undoRestores(kept) == PhotoShaderUndo.Restores.SOURCE_AND_WALLPAPER
            && !ShaderStore.restorePreviousSource(app, id)) {
            Logger.logWarn(LOG_TAG, "Putting the earlier shader back failed");
        }
        MAIN.post(() -> undone(app, id, WallpaperMotion.parse(previousMotion)));
    }

    /** The earlier source is back: the earlier wallpaper too, and the notification asks again. */
    @MainThread
    private static void undone(@NonNull Context app, @NonNull String id, @NonNull WallpaperMotion previous) {
        endApplied(app, id, PhotoShaderUndo.End.UNDO);
        useMotion(app, previous);
        Watcher w = watcher();
        if (w != null) w.onUndone(id);
        hold(app, id);
        showAsking(app, id, R.string.photo_shader_notification_undone_text);
    }

    /** Makes {@code motion} the wallpaper as Done does, and tells an open picker. */
    @MainThread
    private static void useMotion(@NonNull Context app, @NonNull WallpaperMotion motion) {
        Watcher w = watcher();
        LiveWallpaperApplier.Callback done = result -> {
            Watcher now = watcher();
            if (result == LiveWallpaperApplier.Result.SET && now != null) now.onSystemWallpaperChanged();
        };
        if (motion.moves()) {
            LiveWallpaperApplier.useMotion(w == null ? app : w.context(), motion,
                WallpaperSlots.launcherIsHomeApp(app), WallpaperSlots.alsoSetPhone(app), done);
        } else {
            LiveWallpaperApplier.backToPhoto(app, done);
        }
        if (w != null) w.onMotionApplied(motion);
    }

    /** Handles every button of the notification. Registered in the manifest, not exported. */
    public static final class ActionReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (context == null || intent == null || intent.getAction() == null) return;
            final Context app = context.getApplicationContext();
            final String id = intent.getStringExtra(EXTRA_ID);
            if (!validId(id)) return;
            final String action = intent.getAction();
            if (ACTION_CLOSE.equals(action)) {
                cancel(app);
                if (holds(id)) sPendingId = null;
                endApplied(app, id, PhotoShaderUndo.End.CLOSE);
                dropIfUnheld(app, id);
            } else if (ACTION_DONE.equals(action)) {
                cancel(app);
                endApplied(app, id, PhotoShaderUndo.End.DONE);
            } else if (ACTION_UNDO.equals(action)) {
                final String previous = intent.getStringExtra(EXTRA_PREVIOUS);
                runAsync(() -> undo(app, id, previous));
            } else if (ACTION_COPY_ERROR.equals(action)) {
                String report = intent.getStringExtra(EXTRA_ERRORS);
                if (report != null) {
                    setClip(app, ClipData.newPlainText(app.getString(R.string.photo_shader_notification_title), report));
                }
            } else if (ACTION_COPY_PROMPT.equals(action)) {
                runAsync(() -> copyPrompt(app, id));
            } else if (ACTION_COPY_IMAGE.equals(action)) {
                runAsync(() -> copyImage(app, id));
            } else if (ACTION_REPLY.equals(action)) {
                final String answer = replyText(intent);
                runAsync(() -> {
                    try {
                        paste(app, id, answer);
                    } catch (RuntimeException e) {
                        // The reply field spins until the notification is posted again.
                        Logger.logStackTraceWithMessage(LOG_TAG, "Reading the pasted shader failed", e);
                        MAIN.post(() -> showFailed(app, id, Collections.singletonList(
                            new ShaderErrors.Error(-1, app.getString(R.string.wallpaper_picker_apply_failed)))));
                    }
                });
            }
        }

        /** Work too slow for the receiver's main-thread window: the broadcast stays open across it. */
        private void runAsync(@NonNull Runnable work) {
            final PendingResult pending = goAsync();
            WORKER.execute(() -> {
                try {
                    work.run();
                } catch (RuntimeException e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "A notification action failed", e);
                } finally {
                    if (pending != null) pending.finish();
                }
            });
        }

        @Nullable
        private static String replyText(@NonNull Intent intent) {
            Bundle results = RemoteInput.getResultsFromIntent(intent);
            if (results == null) return null;
            CharSequence text = results.getCharSequence(KEY_REPLY_TEXT);
            return text == null ? null : text.toString();
        }
    }
}
