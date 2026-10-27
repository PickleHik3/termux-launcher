package com.termux.app.sound;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.x11.ProotDistro;
import com.termux.shared.logger.Logger;
import com.termux.shared.notification.NotificationUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decisions 11 and 12: the Linux player the helper reports, mirrored into an Android media
 * session — the system's media notification, the lock screen, Bluetooth buttons and the
 * launcher's own media widgets, which read sessions like any other — and the audio focus that
 * playback through the launcher's server holds.
 *
 * <p>Main thread for the session, the notification and focus; everything that reads a file or
 * runs a command goes to the {@link Shell}'s worker.
 */
final class MediaBridge {

    private static final String LOG_TAG = "LauncherSound";
    private static final String CHANNEL_ID = "termux_launcher_media";
    private static final int NOTIFICATION_ID = 24120;
    private static final String MPRIS_PATH = "/org/mpris/MediaPlayer2";
    private static final String PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player";
    /** How far a duck lowers the server: to this share of its volume, in percent. */
    static final int DUCK_PERCENT = 30;
    /** How many parents up a player's process is looked at for its proot. */
    private static final int MAX_ANCESTORS = 16;

    static final String ACTION_PLAY = "play";
    static final String ACTION_PAUSE = "pause";
    static final String ACTION_NEXT = "next";
    static final String ACTION_PREVIOUS = "previous";

    /** The launcher's side: commands with the sound environment, run off the main thread. */
    interface Shell {
        /** Runs {@code task} on the sound worker. */
        void execute(@NonNull Runnable task);

        /** Runs {@code argv} and waits for it; its output, or null. Worker only. */
        @Nullable String run(@NonNull List<String> argv);
    }

    @NonNull private final Context context;
    @NonNull private final Shell shell;
    @NonNull private final Handler main = new Handler(Looper.getMainLooper());
    @NonNull private final PlayerPick players = new PlayerPick();
    @NonNull private final FocusPolicy focus = new FocusPolicy();
    /** Worker only. */
    @NonNull private final MediaArtCache art = new MediaArtCache();
    /** When each player's position was reported, on the elapsed-realtime clock. */
    @NonNull private final Map<String, Long> reportedAt = new HashMap<>();

    private boolean started;
    @Nullable private MediaSession session;
    @Nullable private AudioFocusRequest focusRequest;
    private int streams;
    /** The art the shown player names, and its picture once the worker has decoded it. */
    @Nullable private String artFor;
    @Nullable private Bitmap artBitmap;

    /** Worker only: what a duck or a mute changed, so only that is put back. */
    private int volumeBeforeDuck = -1;
    private boolean mutedByUs;

    MediaBridge(@NonNull Context context, @NonNull Shell shell) {
        this.context = context.getApplicationContext();
        this.shell = shell;
    }

    // ---- From the launcher -------------------------------------------------------------------

    /** Media controls are on and the helper is about to report: start from nothing. */
    void start() {
        main.post(() -> {
            started = true;
            players.clear();
            reportedAt.clear();
            streams = 0;
            refresh();
        });
    }

    /** Media controls off, or the server gone: no session, no notification, no focus. */
    void stop() {
        main.post(() -> {
            started = false;
            players.clear();
            reportedAt.clear();
            streams = 0;
            apply(focus.release());
            cancelNotification();
            MediaSession current = session;
            session = null;
            if (current != null) {
                current.setActive(false);
                current.release();
            }
            artFor = null;
            artBitmap = null;
            shell.execute(art::clear);
        });
    }

    /**
     * Puts back a duck or a mute this made, now, while the server still answers: the server keeps
     * a sink's mute across restarts, so one left behind would greet the next start. Worker only.
     */
    void restoreServerNow() {
        unduck();
        unmute();
    }

    // ---- From the helper, through launcherctl --------------------------------------------------

    void onPlayer(@NonNull PlayerState state) {
        main.post(() -> {
            if (!started) return;
            players.update(state);
            reportedAt.put(state.name, SystemClock.elapsedRealtime());
            onPlayersChanged();
        });
    }

    void onGone(@NonNull String name) {
        main.post(() -> {
            if (!started) return;
            players.remove(name);
            reportedAt.remove(name);
            onPlayersChanged();
        });
    }

    void onSeeked(@NonNull String name, long positionMs) {
        main.post(() -> {
            if (!started) return;
            players.seeked(name, positionMs);
            reportedAt.put(name, SystemClock.elapsedRealtime());
            refresh();
        });
    }

    void onStreams(int playing) {
        main.post(() -> {
            if (!started) return;
            streams = playing;
            apply(focus.onActivity(streams, players.playing()));
        });
    }

    /** A button on the notification. */
    void onControl(@NonNull String action) {
        main.post(() -> {
            PlayerState shown = started ? players.pick() : null;
            if (shown == null) return;
            switch (action) {
                case ACTION_PLAY: call(shown.name, "Play"); break;
                case ACTION_PAUSE: call(shown.name, "Pause"); break;
                case ACTION_NEXT: call(shown.name, "Next"); break;
                case ACTION_PREVIOUS: call(shown.name, "Previous"); break;
                default: break;
            }
        });
    }

    private void onPlayersChanged() {
        apply(focus.onActivity(streams, players.playing()));
        refresh();
    }

    // ---- The session and the notification -----------------------------------------------------

    private void refresh() {
        PlayerState shown = started ? players.pick() : null;
        if (shown == null) {
            if (session != null) session.setActive(false);
            cancelNotification();
            artFor = null;
            artBitmap = null;
            return;
        }
        String url = shown.artUrl == null ? null : shown.name + '\n' + shown.artUrl;
        if (url == null) {
            artFor = null;
            artBitmap = null;
        } else if (!url.equals(artFor)) {
            artFor = url;
            artBitmap = null;
            loadArt(shown, url);
        }
        MediaSession current = ensureSession();
        if (current == null) return;
        current.setMetadata(metadata(shown));
        current.setPlaybackState(playbackState(shown));
        if (!current.isActive()) current.setActive(true);
        postNotification(shown, current);
    }

    @Nullable
    private MediaSession ensureSession() {
        if (session != null) return session;
        try {
            MediaSession created = new MediaSession(context, "termux-launcher-linux-media");
            created.setCallback(new MediaSession.Callback() {
                @Override public void onPlay() { onControl(ACTION_PLAY); }
                @Override public void onPause() { onControl(ACTION_PAUSE); }
                @Override public void onSkipToNext() { onControl(ACTION_NEXT); }
                @Override public void onSkipToPrevious() { onControl(ACTION_PREVIOUS); }
                @Override public void onSeekTo(long positionMs) { seek(positionMs); }
            }, main);
            session = created;
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "No media session: " + e.getMessage());
        }
        return session;
    }

    @NonNull
    private MediaMetadata metadata(@NonNull PlayerState shown) {
        MediaMetadata.Builder builder = new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE,
                shown.title != null ? shown.title : shown.shortName());
        if (shown.artist != null) builder.putString(MediaMetadata.METADATA_KEY_ARTIST, shown.artist);
        if (shown.album != null) builder.putString(MediaMetadata.METADATA_KEY_ALBUM, shown.album);
        if (shown.lengthMs >= 0) builder.putLong(MediaMetadata.METADATA_KEY_DURATION, shown.lengthMs);
        if (artBitmap != null) builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artBitmap);
        return builder.build();
    }

    @NonNull
    private PlaybackState playbackState(@NonNull PlayerState shown) {
        long actions = 0;
        if (shown.canPlay) actions |= PlaybackState.ACTION_PLAY;
        if (shown.canPause) actions |= PlaybackState.ACTION_PAUSE;
        if (shown.canPlay && shown.canPause) actions |= PlaybackState.ACTION_PLAY_PAUSE;
        if (shown.canNext) actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
        if (shown.canPrevious) actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        if (shown.canSeek && shown.trackId != null) actions |= PlaybackState.ACTION_SEEK_TO;
        int state;
        switch (shown.status) {
            case PLAYING: state = PlaybackState.STATE_PLAYING; break;
            case PAUSED: state = PlaybackState.STATE_PAUSED; break;
            default: state = PlaybackState.STATE_STOPPED; break;
        }
        Long at = reportedAt.get(shown.name);
        long position = shown.positionMs >= 0 ? shown.positionMs
            : PlaybackState.PLAYBACK_POSITION_UNKNOWN;
        // Android carries the position forward from here at this speed, until the next report or
        // Seeked; nothing is polled for it.
        float speed = shown.status == PlayerState.Status.PLAYING ? shown.rate : 0f;
        return new PlaybackState.Builder()
            .setActions(actions)
            .setState(state, position, speed, at != null ? at : SystemClock.elapsedRealtime())
            .build();
    }

    private void postNotification(@NonNull PlayerState shown, @NonNull MediaSession current) {
        NotificationManager manager = NotificationUtils.getNotificationManager(context);
        if (manager == null) return;
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationUtils.setupNotificationChannel(context, CHANNEL_ID,
                context.getString(R.string.launcher_media_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        }
        boolean playing = shown.status == PlayerState.Status.PLAYING;
        List<Notification.Action> actions = new ArrayList<>();
        if (shown.canPrevious) {
            actions.add(action(android.R.drawable.ic_media_previous,
                R.string.launcher_media_previous, ACTION_PREVIOUS));
        }
        if (playing ? shown.canPause : shown.canPlay) {
            actions.add(playing
                ? action(android.R.drawable.ic_media_pause, R.string.launcher_media_pause, ACTION_PAUSE)
                : action(android.R.drawable.ic_media_play, R.string.launcher_media_play, ACTION_PLAY));
        }
        if (shown.canNext) {
            actions.add(action(android.R.drawable.ic_media_next, R.string.launcher_media_next,
                ACTION_NEXT));
        }
        int[] compact = new int[Math.min(3, actions.size())];
        for (int i = 0; i < compact.length; i++) compact[i] = i;
        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentTitle(shown.title != null ? shown.title : shown.shortName())
            .setContentText(shown.artist != null ? shown.artist : shown.shortName())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(playing)
            .setStyle(new Notification.MediaStyle()
                .setMediaSession(current.getSessionToken())
                .setShowActionsInCompactView(compact));
        if (artBitmap != null) builder.setLargeIcon(artBitmap);
        for (Notification.Action action : actions) builder.addAction(action);
        Intent open = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (open != null) {
            builder.setContentIntent(PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        try {
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "Could not post the media notification: " + e.getMessage());
        }
    }

    @NonNull
    private Notification.Action action(int icon, int title, @NonNull String action) {
        Intent intent = new Intent(context, MediaControlReceiver.class).setAction(
            MediaControlReceiver.ACTION_PREFIX + action);
        PendingIntent pending = PendingIntent.getBroadcast(context, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(Icon.createWithResource("android", icon),
            context.getString(title), pending).build();
    }

    private void cancelNotification() {
        NotificationManager manager = NotificationUtils.getNotificationManager(context);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    // ---- Artwork --------------------------------------------------------------------------------

    /** Finds and decodes {@code shown}'s art on the worker, then shows it if it is still wanted. */
    private void loadArt(@NonNull PlayerState shown, @NonNull String key) {
        String path = ProotArt.localPath(shown.artUrl);
        if (path == null) return;
        int pid = shown.pid;
        shell.execute(() -> {
            File file = ProotArt.resolve(path, rootfsOf(pid), File::isFile);
            Bitmap bitmap = file == null ? null : art.get(file);
            if (bitmap == null) return;
            main.post(() -> {
                if (!key.equals(artFor)) return;
                artBitmap = bitmap;
                refresh();
            });
        });
    }

    /** The rootfs of the distro {@code pid} runs in, from its process ancestry; null for the prefix. */
    @Nullable
    private static File rootfsOf(int pid) {
        if (pid <= 1) return null;
        List<List<String>> ancestry = new ArrayList<>();
        int current = pid;
        for (int i = 0; i < MAX_ANCESTORS && current > 1; i++) {
            ancestry.add(StepAside.parseCmdline(readBytes(new File("/proc/" + current + "/cmdline")),
                Integer.MAX_VALUE));
            byte[] stat = readBytes(new File("/proc/" + current + "/stat"));
            current = ProotArt.parentPid(stat == null ? null
                : new String(stat, StandardCharsets.UTF_8));
        }
        String name = ProotArt.distroOf(ancestry);
        if (name == null) return null;
        ProotDistro.Container container =
            ProotDistro.byName(ProotDistro.containers(ProotDistro.containersDir()), name);
        return container == null ? null : container.root;
    }

    @Nullable
    private static byte[] readBytes(@NonNull File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Controls and focus ---------------------------------------------------------------------

    /** Calls {@code method} on {@code name}'s player interface, on the worker. */
    private void call(@NonNull String name, @NonNull String method, @NonNull String... args) {
        List<String> argv = SoundFiles.argv(SoundPaths.DBUS_SEND, "--session",
            "--type=method_call", "--dest=" + name, MPRIS_PATH, PLAYER_INTERFACE + "." + method);
        for (String arg : args) argv.add(arg);
        shell.execute(() -> shell.run(argv));
    }

    private void seek(long positionMs) {
        PlayerState shown = started ? players.pick() : null;
        if (shown == null || !shown.canSeek || shown.trackId == null) return;
        call(shown.name, "SetPosition", "objpath:" + shown.trackId,
            "int64:" + Math.max(0, positionMs) * 1000L);
    }

    /** Carries out what the focus policy decided. Main thread. */
    private void apply(@NonNull FocusPolicy.Actions actions) {
        if (actions.isEmpty()) return;
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (actions.abandon && audio != null && focusRequest != null) {
            audio.abandonAudioFocusRequest(focusRequest);
        }
        if (actions.request && audio != null) {
            int result = audio.requestAudioFocus(focusRequest());
            apply(focus.onRequestResult(result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED));
        }
        if (actions.unduck) shell.execute(this::unduck);
        if (actions.duck) shell.execute(this::duck);
        if (actions.unmute) shell.execute(this::unmute);
        if (actions.mute) shell.execute(this::mute);
        for (String name : actions.pause) call(name, "Pause");
        for (String name : actions.play) call(name, "Play");
    }

    @NonNull
    private AudioFocusRequest focusRequest() {
        if (focusRequest == null) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                // The launcher ducks the server itself (decision 12), so the system must tell it
                // rather than duck on its own.
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(this::onFocusChange, main)
                .build();
        }
        return focusRequest;
    }

    private void onFocusChange(int change) {
        FocusPolicy.Change mapped;
        switch (change) {
            case AudioManager.AUDIOFOCUS_GAIN: mapped = FocusPolicy.Change.GAIN; break;
            case AudioManager.AUDIOFOCUS_LOSS: mapped = FocusPolicy.Change.LOSS; break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                mapped = FocusPolicy.Change.LOSS_TRANSIENT; break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                mapped = FocusPolicy.Change.LOSS_TRANSIENT_CAN_DUCK; break;
            default: return;
        }
        apply(focus.onFocusChange(mapped));
    }

    private static final String SINK = "@DEFAULT_SINK@";

    private void duck() {
        int volume = SoundFiles.volumePercent(shell.run(
            SoundFiles.argv(SoundPaths.PACTL, "get-sink-volume", SINK)));
        if (volume <= 0) return;
        volumeBeforeDuck = volume;
        shell.run(SoundFiles.argv(SoundPaths.PACTL, "set-sink-volume", SINK,
            Math.max(1, volume * DUCK_PERCENT / 100) + "%"));
    }

    private void unduck() {
        if (volumeBeforeDuck < 0) return;
        shell.run(SoundFiles.argv(SoundPaths.PACTL, "set-sink-volume", SINK,
            volumeBeforeDuck + "%"));
        volumeBeforeDuck = -1;
    }

    private void mute() {
        if (SoundFiles.isMuted(shell.run(SoundFiles.argv(SoundPaths.PACTL, "get-sink-mute", SINK)))) {
            return;
        }
        shell.run(SoundFiles.argv(SoundPaths.PACTL, "set-sink-mute", SINK, "1"));
        mutedByUs = true;
    }

    private void unmute() {
        if (!mutedByUs) return;
        shell.run(SoundFiles.argv(SoundPaths.PACTL, "set-sink-mute", SINK, "0"));
        mutedByUs = false;
    }
}
