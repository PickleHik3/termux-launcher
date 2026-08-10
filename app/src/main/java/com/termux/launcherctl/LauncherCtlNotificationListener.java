package com.termux.launcherctl;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.notifications.LauncherNotificationBadgeStore;
import com.termux.app.statusbar.EssentialNotificationRule;
import com.termux.app.statusbar.EssentialNotificationRules;
import com.termux.app.statusbar.PinnedNotification;
import com.termux.app.statusbar.TopPaneFeed;
import com.termux.app.statusbar.TopPaneMediaState;
import com.termux.app.statusbar.TopPaneSlotMode;
import com.termux.shared.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures live notification and media-session state for LauncherCtl local API endpoints, and feeds
 * the top-pane widget slot through {@link TopPaneFeed}. Both the media widget and the pinned
 * notifications depend on listener access, so they surface only while this service is connected.
 */
public class LauncherCtlNotificationListener extends NotificationListenerService
        implements TopPaneFeed.Controls {
    private static final String LOG_TAG = "LauncherCtlNotifListener";
    private static final String NOTIFICATION_LISTENER_SETTINGS_ACTION =
        "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS";
    private static final String NOTIFICATION_LISTENER_HINT =
        "Enable notification access for Termux Launcher to populate notifications and media endpoints.";
    private static final ConcurrentHashMap<String, JSONObject> NOTIFICATIONS = new ConcurrentHashMap<>();
    private static final int MAX_ART_BYTES = 512 * 1024;

    private static volatile boolean listenerConnected;
    private static volatile LauncherCtlNotificationListener activeInstance;
    private static volatile JSONObject nowPlaying;
    private static volatile JSONObject nowPlayingArt;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, String> mAppLabels = new HashMap<>();
    /** Insertion-ordered so a fourth match evicts the oldest pin. */
    private final LinkedHashMap<String, PinnedNotification> mPinned = new LinkedHashMap<>();
    /** Keys unpinned by hand, so an unpinned but still-posted notification does not come back. */
    private final Set<String> mUnpinned = new HashSet<>();

    private final MediaSessionManager.OnActiveSessionsChangedListener mSessionsListener =
        this::attachTopPaneController;

    private final MediaController.Callback mMediaCallback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            publishTopPaneMedia();
        }

        @Override public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            publishTopPaneMedia();
        }

        @Override public void onSessionDestroyed() {
            detachTopPaneController();
            syncTopPaneMedia();
        }
    };

    @Nullable private MediaController mTopPaneController;

    @Override
    public void onListenerConnected() {
        activeInstance = this;
        listenerConnected = true;
        Logger.logInfo(LOG_TAG, "Notification listener connected");
        TopPaneFeed.setControls(this);
        TopPaneFeed.setListenerConnected(true);
        rebuildNotificationsSnapshot();
        refreshNowPlaying();
        registerSessionsListener();
        syncTopPaneMedia();
        rebuildPinnedNotifications();
    }

    @Override
    public void onListenerDisconnected() {
        if (activeInstance == this) activeInstance = null;
        listenerConnected = false;
        LauncherNotificationBadgeStore.clear();
        unregisterSessionsListener();
        detachTopPaneController();
        mPinned.clear();
        mUnpinned.clear();
        TopPaneFeed.setControls(null);
        TopPaneFeed.setListenerConnected(false);
        Logger.logWarn(LOG_TAG, "Notification listener disconnected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        LauncherNotificationBadgeStore.onNotificationPosted(sbn, null);
        updateNotification(sbn);
        persistPosted(sbn);
        refreshNowPlaying();
        rebuildPinnedNotifications();
        syncTopPaneMedia();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, NotificationListenerService.RankingMap rankingMap) {
        LauncherNotificationBadgeStore.onNotificationPosted(sbn, rankingMap);
        updateNotification(sbn);
        persistPosted(sbn);
        refreshNowPlaying();
        rebuildPinnedNotifications();
        syncTopPaneMedia();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        LauncherNotificationBadgeStore.onNotificationRemoved(sbn);
        if (sbn != null) {
            NOTIFICATIONS.remove(sbn.getKey());
            mUnpinned.remove(sbn.getKey());
        }
        persistRemoved(sbn);
        refreshNowPlaying();
        rebuildPinnedNotifications();
        syncTopPaneMedia();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, NotificationListenerService.RankingMap rankingMap) {
        onNotificationRemoved(sbn);
    }

    public static boolean isListenerConnected() {
        return listenerConnected;
    }

    public static boolean dismissNotification(String key) {
        LauncherCtlNotificationListener listener = activeInstance;
        if (listener == null || key == null || key.isEmpty()) return false;
        try {
            listener.cancelNotification(key);
            return true;
        } catch (Throwable throwable) {
            Logger.logWarn(LOG_TAG, "Failed to dismiss notification: " + throwable.getMessage());
            return false;
        }
    }

    public static String getListenerSettingsAction() {
        return NOTIFICATION_LISTENER_SETTINGS_ACTION;
    }

    public static String getListenerHint() {
        return NOTIFICATION_LISTENER_HINT;
    }

    public static JSONObject getNotificationsSnapshot() {
        JSONObject data = new JSONObject();
        try {
            JSONArray notifications = new JSONArray();
            List<JSONObject> entries = new ArrayList<>(NOTIFICATIONS.values());
            entries.sort((a, b) -> Long.compare(b.optLong("postTime", 0), a.optLong("postTime", 0)));
            for (JSONObject notification : entries) {
                notifications.put(new JSONObject(notification.toString()));
            }
            data.put("listenerConnected", listenerConnected);
            data.put("settingsAction", getListenerSettingsAction());
            data.put("count", notifications.length());
            data.put("notifications", notifications);
            if (!listenerConnected) {
                data.put("hint", getListenerHint());
            }
        } catch (JSONException ignored) {
        }
        return data;
    }

    public static JSONObject getNowPlayingSnapshot() {
        JSONObject data = new JSONObject();
        try {
            data.put("listenerConnected", listenerConnected);
            data.put("settingsAction", getListenerSettingsAction());
            if (nowPlaying != null) {
                data.put("nowPlaying", new JSONObject(nowPlaying.toString()));
            } else {
                data.put("nowPlaying", JSONObject.NULL);
            }
            if (!listenerConnected) {
                data.put("hint", getListenerHint());
            }
        } catch (JSONException ignored) {
        }
        return data;
    }

    public static JSONObject getNowPlayingArtSnapshot() {
        JSONObject data = new JSONObject();
        try {
            data.put("listenerConnected", listenerConnected);
            data.put("settingsAction", getListenerSettingsAction());
            if (nowPlayingArt != null) {
                data.put("art", new JSONObject(nowPlayingArt.toString()));
            } else {
                data.put("art", JSONObject.NULL);
            }
            if (!listenerConnected) {
                data.put("hint", getListenerHint());
            }
        } catch (JSONException ignored) {
        }
        return data;
    }

    private void rebuildNotificationsSnapshot() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            NOTIFICATIONS.clear();
            if (active == null) {
                LauncherNotificationBadgeStore.syncFromActiveNotifications(null, null);
                return;
            }
            for (StatusBarNotification sbn : active) {
                updateNotification(sbn);
            }
            LauncherNotificationBadgeStore.syncFromActiveNotifications(active, null);
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to rebuild notification snapshot: " + e.getMessage());
        }
    }

    private void updateNotification(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) {
            return;
        }
        try {
            NOTIFICATIONS.put(sbn.getKey(), toNotificationJson(sbn));
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to parse notification: " + e.getMessage());
        }
    }

    private void persistPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) {
            return;
        }
        try {
            LauncherCtlNotificationStore.getInstance().persistPosted(toNotificationJson(sbn));
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to persist posted notification: " + e.getMessage());
        }
    }

    private void persistRemoved(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) {
            return;
        }
        try {
            LauncherCtlNotificationStore.getInstance().persistRemoved(toNotificationJson(sbn));
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to persist removed notification: " + e.getMessage());
        }
    }

    private JSONObject toNotificationJson(StatusBarNotification sbn) throws JSONException {
        JSONObject data = new JSONObject();
        Bundle extras = sbn.getNotification().extras;
        data.put("key", sbn.getKey());
        data.put("packageName", sbn.getPackageName());
        data.put("id", sbn.getId());
        data.put("tag", sbn.getTag() == null ? JSONObject.NULL : sbn.getTag());
        data.put("postTime", sbn.getPostTime());
        data.put("isOngoing", sbn.isOngoing());
        data.put("isClearable", sbn.isClearable());
        data.put("category", sbn.getNotification().category == null ? JSONObject.NULL : sbn.getNotification().category);
        data.put("title", toStringOrNull(extras, "android.title"));
        data.put("text", toStringOrNull(extras, "android.text"));
        data.put("subText", toStringOrNull(extras, "android.subText"));
        data.put("bigText", toStringOrNull(extras, "android.bigText"));
        return data;
    }

    private String toStringOrNull(Bundle extras, String key) {
        if (extras == null) return null;
        CharSequence value = extras.getCharSequence(key);
        return value == null ? null : value.toString();
    }

    private void refreshNowPlaying() {
        JSONObject current = null;
        JSONObject currentArt = null;
        try {
            MediaSessionManager mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            if (mediaSessionManager != null) {
                List<MediaController> sessions =
                    mediaSessionManager.getActiveSessions(new ComponentName(this, LauncherCtlNotificationListener.class));
                MediaController selected = selectController(sessions);
                if (selected != null) {
                    current = toNowPlayingJson(selected);
                    currentArt = toNowPlayingArtJson(selected);
                }
            }
        } catch (SecurityException e) {
            Logger.logWarn(LOG_TAG, "Media sessions unavailable without notification listener access");
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to refresh media sessions: " + e.getMessage());
        }
        nowPlaying = current;
        nowPlayingArt = currentArt;
    }

    private MediaController selectController(List<MediaController> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return null;
        }
        for (MediaController controller : sessions) {
            PlaybackState state = controller.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                return controller;
            }
        }
        return sessions.get(0);
    }

    private JSONObject toNowPlayingJson(MediaController controller) throws JSONException {
        JSONObject data = new JSONObject();
        PlaybackState state = controller.getPlaybackState();
        MediaMetadata metadata = controller.getMetadata();

        data.put("packageName", controller.getPackageName());
        data.put("sessionTag", controller.getSessionToken() != null ? controller.getSessionToken().toString() : JSONObject.NULL);
        data.put("playbackState", state != null ? state.getState() : PlaybackState.STATE_NONE);
        data.put("playbackStateName", playbackStateName(state != null ? state.getState() : PlaybackState.STATE_NONE));
        data.put("position", state != null ? state.getPosition() : -1);
        data.put("actions", state != null ? state.getActions() : 0);

        if (metadata != null) {
            data.put("title", safeMeta(metadata, MediaMetadata.METADATA_KEY_TITLE));
            data.put("artist", safeMeta(metadata, MediaMetadata.METADATA_KEY_ARTIST));
            data.put("album", safeMeta(metadata, MediaMetadata.METADATA_KEY_ALBUM));
            data.put("duration", metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
        } else {
            data.put("title", JSONObject.NULL);
            data.put("artist", JSONObject.NULL);
            data.put("album", JSONObject.NULL);
            data.put("duration", -1);
        }
        return data;
    }

    private Object safeMeta(MediaMetadata metadata, String key) {
        CharSequence value = metadata.getText(key);
        return value == null ? JSONObject.NULL : value.toString();
    }

    private JSONObject toNowPlayingArtJson(MediaController controller) throws JSONException {
        MediaMetadata metadata = controller.getMetadata();
        if (metadata == null) {
            return null;
        }
        Bitmap bitmap = extractAlbumArt(metadata);
        if (bitmap == null) {
            return null;
        }

        byte[] jpeg = compressArt(bitmap);
        if (jpeg == null || jpeg.length == 0) {
            return null;
        }

        JSONObject data = new JSONObject();
        data.put("packageName", controller.getPackageName());
        data.put("mimeType", "image/jpeg");
        data.put("width", bitmap.getWidth());
        data.put("height", bitmap.getHeight());
        data.put("sizeBytes", jpeg.length);
        data.put("base64", Base64.encodeToString(jpeg, Base64.NO_WRAP));
        data.put("title", safeMeta(metadata, MediaMetadata.METADATA_KEY_TITLE));
        data.put("artist", safeMeta(metadata, MediaMetadata.METADATA_KEY_ARTIST));
        data.put("album", safeMeta(metadata, MediaMetadata.METADATA_KEY_ALBUM));
        return data;
    }

    private Bitmap extractAlbumArt(MediaMetadata metadata) {
        Bitmap art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art != null) return art;
        art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art != null) return art;
        return metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
    }

    private byte[] compressArt(Bitmap bitmap) {
        int quality = 90;
        while (quality >= 50) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                return null;
            }
            byte[] bytes = output.toByteArray();
            if (bytes.length <= MAX_ART_BYTES || quality == 50) {
                return bytes;
            }
            quality -= 10;
        }
        return null;
    }

    // ---- Top pane widget slot --------------------------------------------

    /** Re-evaluate the pin rules, e.g. after a rule was added or removed through the registry. */
    public static void requestPinnedRefresh() {
        LauncherCtlNotificationListener listener = activeInstance;
        if (listener == null) return;
        listener.mMainHandler.post(listener::rebuildPinnedNotifications);
    }

    private void rebuildPinnedNotifications() {
        List<EssentialNotificationRule> rules = EssentialNotificationRules.load(this);
        Map<String, PinnedNotification> matched = new LinkedHashMap<>();
        Set<String> activeKeys = new HashSet<>();
        StatusBarNotification[] active = null;
        try {
            active = getActiveNotifications();
        } catch (Throwable throwable) {
            Logger.logWarn(LOG_TAG, "Failed to read active notifications: " + throwable.getMessage());
        }
        if (active != null) {
            List<StatusBarNotification> sorted = new ArrayList<>();
            for (StatusBarNotification sbn : active) {
                if (sbn == null || sbn.getNotification() == null) continue;
                activeKeys.add(sbn.getKey());
                sorted.add(sbn);
            }
            if (!rules.isEmpty()) {
                sorted.sort(Comparator.comparingLong(StatusBarNotification::getPostTime));
                for (StatusBarNotification sbn : sorted) {
                    if (mUnpinned.contains(sbn.getKey())) continue;
                    PinnedNotification pin = toPinnedNotification(sbn, rules);
                    if (pin != null) matched.put(pin.key, pin);
                }
            }
        }
        mUnpinned.retainAll(activeKeys);

        // Keep the order of pins already on screen, then append new matches oldest-first.
        List<PinnedNotification> ordered = new ArrayList<>();
        for (String key : mPinned.keySet()) {
            PinnedNotification pin = matched.remove(key);
            if (pin != null) ordered.add(pin);
        }
        ordered.addAll(matched.values());
        while (ordered.size() > TopPaneSlotMode.MAX_PINNED) ordered.remove(0);

        mPinned.clear();
        for (PinnedNotification pin : ordered) mPinned.put(pin.key, pin);
        TopPaneFeed.setPinned(ordered);
    }

    @Nullable
    private PinnedNotification toPinnedNotification(@NonNull StatusBarNotification sbn,
                                                    @NonNull List<EssentialNotificationRule> rules) {
        Bundle extras = sbn.getNotification().extras;
        String title = toStringOrNull(extras, "android.title");
        String body = toStringOrNull(extras, "android.text");
        if (body == null || body.isEmpty()) body = toStringOrNull(extras, "android.bigText");
        EssentialNotificationRule rule =
            EssentialNotificationRules.firstMatch(rules, sbn.getPackageName(), title, body);
        if (rule == null) return null;
        return new PinnedNotification(sbn.getKey(), sbn.getPackageName(), title,
            appLabel(sbn.getPackageName()), body, rule.id, rule.clearOnDismiss, sbn.getPostTime());
    }

    private String appLabel(@NonNull String packageName) {
        String cached = mAppLabels.get(packageName);
        if (cached != null) return cached;
        String label = packageName;
        try {
            PackageManager manager = getPackageManager();
            ApplicationInfo info = manager.getApplicationInfo(packageName, 0);
            CharSequence resolved = manager.getApplicationLabel(info);
            if (resolved != null && resolved.length() > 0) label = resolved.toString();
        } catch (Exception ignored) {
        }
        mAppLabels.put(packageName, label);
        return label;
    }

    private void registerSessionsListener() {
        try {
            MediaSessionManager manager =
                (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            if (manager == null) return;
            manager.addOnActiveSessionsChangedListener(mSessionsListener,
                new ComponentName(this, LauncherCtlNotificationListener.class), mMainHandler);
        } catch (Throwable throwable) {
            Logger.logWarn(LOG_TAG, "Failed to observe media sessions: " + throwable.getMessage());
        }
    }

    private void unregisterSessionsListener() {
        try {
            MediaSessionManager manager =
                (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            if (manager != null) manager.removeOnActiveSessionsChangedListener(mSessionsListener);
        } catch (Throwable ignored) {
        }
    }

    private void syncTopPaneMedia() {
        try {
            MediaSessionManager manager =
                (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            if (manager == null) return;
            attachTopPaneController(manager.getActiveSessions(
                new ComponentName(this, LauncherCtlNotificationListener.class)));
        } catch (SecurityException e) {
            Logger.logWarn(LOG_TAG, "Media sessions unavailable without notification listener access");
        } catch (Throwable throwable) {
            Logger.logErrorExtended(LOG_TAG, "Failed to sync media sessions: " + throwable.getMessage());
        }
    }

    private void attachTopPaneController(@Nullable List<MediaController> controllers) {
        MediaController selected = selectTopPaneController(controllers);
        boolean same = selected != null && mTopPaneController != null
            && mTopPaneController.getSessionToken().equals(selected.getSessionToken());
        if (!same) {
            detachTopPaneController();
            mTopPaneController = selected;
            if (selected != null) selected.registerCallback(mMediaCallback, mMainHandler);
        }
        publishTopPaneMedia();
    }

    private void detachTopPaneController() {
        if (mTopPaneController == null) return;
        try {
            mTopPaneController.unregisterCallback(mMediaCallback);
        } catch (Throwable ignored) {
        }
        mTopPaneController = null;
    }

    /** Only playing or paused sessions claim the slot; stopped and released ones release it. */
    @Nullable
    private MediaController selectTopPaneController(@Nullable List<MediaController> controllers) {
        if (controllers == null || controllers.isEmpty()) return null;
        MediaController paused = null;
        for (MediaController controller : controllers) {
            PlaybackState state = controller.getPlaybackState();
            int value = state == null ? PlaybackState.STATE_NONE : state.getState();
            if (value == PlaybackState.STATE_PLAYING) return controller;
            if (paused == null && value == PlaybackState.STATE_PAUSED) paused = controller;
        }
        return paused;
    }

    private void publishTopPaneMedia() {
        MediaController controller = mTopPaneController;
        if (controller == null) {
            TopPaneFeed.setMedia(null);
            return;
        }
        PlaybackState state = controller.getPlaybackState();
        int value = state == null ? PlaybackState.STATE_NONE : state.getState();
        if (value != PlaybackState.STATE_PLAYING && value != PlaybackState.STATE_PAUSED) {
            TopPaneFeed.setMedia(null);
            return;
        }
        MediaMetadata metadata = controller.getMetadata();
        String title = metadata == null ? null : text(metadata, MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata == null ? null : text(metadata, MediaMetadata.METADATA_KEY_ARTIST);
        long duration = metadata == null ? 0L : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        Bitmap art = metadata == null ? null : extractAlbumArt(metadata);
        TopPaneFeed.setMedia(new TopPaneMediaState(controller.getPackageName(), title, artist,
            appLabel(controller.getPackageName()), art,
            state == null ? 0L : state.getPosition(), duration,
            value == PlaybackState.STATE_PLAYING));
    }

    @Nullable
    private String text(@NonNull MediaMetadata metadata, @NonNull String key) {
        CharSequence value = metadata.getText(key);
        return value == null ? null : value.toString();
    }

    @Override
    public boolean skipPrevious() {
        MediaController controller = mTopPaneController;
        if (controller == null) return false;
        controller.getTransportControls().skipToPrevious();
        return true;
    }

    @Override
    public boolean togglePlayPause(boolean play) {
        MediaController controller = mTopPaneController;
        if (controller == null) return false;
        if (play) controller.getTransportControls().play();
        else controller.getTransportControls().pause();
        return true;
    }

    @Override
    public boolean skipNext() {
        MediaController controller = mTopPaneController;
        if (controller == null) return false;
        controller.getTransportControls().skipToNext();
        return true;
    }

    @Override
    public boolean dismissPinned(@NonNull String key, boolean clear) {
        mUnpinned.add(key);
        boolean unpinned = mPinned.remove(key) != null;
        TopPaneFeed.setPinned(new ArrayList<>(mPinned.values()));
        if (!clear) return unpinned;
        try {
            cancelNotification(key);
            return true;
        } catch (Throwable throwable) {
            Logger.logWarn(LOG_TAG, "Failed to cancel pinned notification: " + throwable.getMessage());
            return unpinned;
        }
    }

    /**
     * Opens a pin the way tapping it in the shade would: by sending the notification's own
     * {@code contentIntent}, so the app lands on the screen the notification is about rather than on
     * whatever it happens to show at launch. Only if there is no such intent — or it has been
     * cancelled — does the app's plain launcher entry get used.
     *
     * <p>Auto-cancelling notifications are cleared afterwards, as the shade does; a pin whose source
     * is gone would otherwise stay on the pane until it is dismissed by hand.
     */
    @Override
    public boolean openPinned(@NonNull String key) {
        StatusBarNotification sbn = activeNotification(key);
        android.app.Notification notification = sbn == null ? null : sbn.getNotification();
        String packageName = sbn == null
            ? (mPinned.containsKey(key) ? mPinned.get(key).packageName : null)
            : sbn.getPackageName();
        if (notification != null && notification.contentIntent != null) {
            try {
                notification.contentIntent.send();
                if ((notification.flags & android.app.Notification.FLAG_AUTO_CANCEL) != 0) {
                    dismissPinned(key, true);
                }
                return true;
            } catch (Throwable throwable) {
                Logger.logWarn(LOG_TAG,
                    "Pinned notification content intent failed: " + throwable.getMessage());
            }
        }
        if (packageName == null) return false;
        android.content.Intent launch =
            getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            Logger.logWarn(LOG_TAG, "No way to open " + packageName + " for a pinned notification");
            return false;
        }
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(launch);
            return true;
        } catch (Throwable throwable) {
            Logger.logWarn(LOG_TAG, "Cannot open " + packageName + ": " + throwable.getMessage());
            return false;
        }
    }

    @Nullable
    private StatusBarNotification activeNotification(@NonNull String key) {
        try {
            StatusBarNotification[] active = getActiveNotifications(new String[] {key});
            if (active != null && active.length > 0) return active[0];
        } catch (Throwable throwable) {
            Logger.logWarn(LOG_TAG, "Failed to read notification " + key + ": "
                + throwable.getMessage());
        }
        return null;
    }

    private String playbackStateName(int state) {
        switch (state) {
            case PlaybackState.STATE_NONE: return "NONE";
            case PlaybackState.STATE_STOPPED: return "STOPPED";
            case PlaybackState.STATE_PAUSED: return "PAUSED";
            case PlaybackState.STATE_PLAYING: return "PLAYING";
            case PlaybackState.STATE_FAST_FORWARDING: return "FAST_FORWARDING";
            case PlaybackState.STATE_REWINDING: return "REWINDING";
            case PlaybackState.STATE_BUFFERING: return "BUFFERING";
            case PlaybackState.STATE_ERROR: return "ERROR";
            case PlaybackState.STATE_CONNECTING: return "CONNECTING";
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS: return "SKIPPING_TO_PREVIOUS";
            case PlaybackState.STATE_SKIPPING_TO_NEXT: return "SKIPPING_TO_NEXT";
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM: return "SKIPPING_TO_QUEUE_ITEM";
            default: return "UNKNOWN(" + state + ")";
        }
    }
}
