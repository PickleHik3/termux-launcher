package com.termux.launcherctl;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;

import com.termux.app.launcher.notifications.LauncherNotificationBadgeStore;
import com.termux.app.statusbar.EssentialNotificationRule;
import com.termux.app.statusbar.EssentialNotificationRules;
import com.termux.app.statusbar.PinnedConversations;
import com.termux.app.statusbar.PinnedNotification;
import com.termux.app.statusbar.TopPaneFeed;
import com.termux.app.statusbar.TopPaneMediaState;
import com.termux.app.statusbar.TopPaneSlotMode;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feeds the top-pane widget slot through {@link TopPaneFeed}, keeps the app-icon dots current, and
 * hands the notifications of the apps the user enabled to {@link LauncherCtlNotificationStore}
 * (and to the {@code /v1/notifications/active} route through {@link #captureActive}). The media
 * widget and the pinned notifications depend on listener access, so they surface only while this
 * service is connected.
 */
public class LauncherCtlNotificationListener extends NotificationListenerService
        implements TopPaneFeed.Controls {
    private static final String LOG_TAG = "LauncherCtlNotifListener";
    private static final String NOTIFICATION_LISTENER_SETTINGS_ACTION =
        "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS";
    private static final String NOTIFICATION_LISTENER_HINT =
        "Enable notification access for Termux Launcher so notification history can be recorded.";

    private static volatile boolean listenerConnected;
    private static volatile LauncherCtlNotificationListener activeInstance;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    /** Read from the listener, main and API threads, so concurrent. */
    private final Map<String, String> mAppLabels = new ConcurrentHashMap<>();
    /**
     * The cards on the pane, by conversation ({@link PinnedNotification#conversationId}), in the
     * order they are shown; insertion-ordered so a match past the ceiling evicts the oldest.
     */
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
        rebuildBadges();
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
        persistPosted(sbn);
        rebuildPinnedNotifications();
        publishTopPaneMedia();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, NotificationListenerService.RankingMap rankingMap) {
        LauncherNotificationBadgeStore.onNotificationPosted(sbn, rankingMap);
        persistPosted(sbn);
        rebuildPinnedNotifications();
        publishTopPaneMedia();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        LauncherNotificationBadgeStore.onNotificationRemoved(sbn);
        if (sbn != null) {
            mUnpinned.remove(sbn.getKey());
        }
        persistRemoved(sbn);
        rebuildPinnedNotifications();
        publishTopPaneMedia();
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

    private void rebuildBadges() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            LauncherNotificationBadgeStore.syncFromActiveNotifications(active, null);
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to rebuild notification badges: " + e.getMessage());
        }
    }

    /**
     * What the history keeps, read from preferences on every call so a change in Settings takes
     * effect on the next notification.
     *
     * <p>Notification access alone does not authorise writing anything: it is granted for dots,
     * the status bar and the top pane, which read notifications in memory and forget them. History
     * persists them under the Termux home, where anything running as the app UID -- any package
     * installed in the shell, any script a user pastes -- can read message bodies long after the
     * notification itself is gone. So it records only the packages the user put in the set.
     */
    private static final class HistoryConfig {
        final Set<String> packages;
        final boolean maskCodes;
        final int retentionDays;

        HistoryConfig(Set<String> packages, boolean maskCodes, int retentionDays) {
            this.packages = packages;
            this.maskCodes = maskCodes;
            this.retentionDays = retentionDays;
        }
    }

    @Nullable
    private HistoryConfig historyConfig() {
        try {
            TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(this);
            if (preferences == null) return null;
            return new HistoryConfig(preferences.getNotificationHistoryPackages(),
                preferences.isNotificationHistoryMaskCodesEnabled(),
                preferences.getNotificationHistoryRetentionDays());
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to read notification history preferences: " + e.getMessage());
            return null;
        }
    }

    private void persistPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        HistoryConfig config = historyConfig();
        if (config == null || !config.packages.contains(sbn.getPackageName())) return;
        try {
            List<LauncherCtlNotificationEvent> rows = capture(sbn);
            LauncherCtlNotificationStore.getInstance()
                .persistPosted(rows, config.maskCodes, config.retentionDays);
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to persist posted notification: " + e.getMessage());
        }
    }

    private void persistRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        HistoryConfig config = historyConfig();
        if (config == null || !config.packages.contains(sbn.getPackageName())) return;
        LauncherCtlNotificationStore.getInstance().persistRemoved(sbn.getKey());
    }

    /**
     * What is in the shade now for the given apps, newest first, or null when the listener is not
     * connected. Cut from the same capture the history uses, so noise (ongoing, progress, media,
     * a group summary its children already cover) is left out here too.
     */
    @Nullable
    public static List<LauncherCtlNotificationEvent> captureActive(@NonNull Set<String> packages,
                                                                   boolean maskCodes) {
        LauncherCtlNotificationListener listener = activeInstance;
        if (listener == null) return null;
        List<LauncherCtlNotificationEvent> rows = new ArrayList<>();
        try {
            StatusBarNotification[] active = listener.getActiveNotifications();
            if (active == null) return rows;
            for (StatusBarNotification sbn : active) {
                if (sbn == null || sbn.getNotification() == null) continue;
                if (!packages.contains(sbn.getPackageName())) continue;
                for (LauncherCtlNotificationEvent row : listener.capture(sbn)) {
                    rows.add(maskCodes ? row.mapText(LauncherCtlNotificationMasker::mask) : row);
                }
            }
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to read active notifications: " + e.getMessage());
        }
        rows.sort((a, b) -> Long.compare(b.messageTime, a.messageTime));
        return rows;
    }

    /**
     * One row per distinct message in a notification, or none when it is noise.
     *
     * <p>Noise is what says nothing a person would look up later: ongoing notifications (a running
     * service, a call), progress bars, media controls, and a group summary. A summary is skipped
     * when its children are in the shade, since they carry the individual messages, and when it has
     * no lines of its own (it is only a counter, "3 new messages"). One that does carry InboxStyle
     * lines or messages and has no children is the only place the content lives, so it is kept.
     */
    @NonNull
    private List<LauncherCtlNotificationEvent> capture(@NonNull StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras != null ? notification.extras : new Bundle();
        if (isHistoryNoise(sbn, notification, extras)) return new ArrayList<>();

        List<String> lines = null;
        CharSequence[] rawLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (rawLines != null && rawLines.length > 0) {
            lines = new ArrayList<>();
            for (CharSequence line : rawLines) if (line != null) lines.add(line.toString());
        }

        String conversation = toStringOrNull(extras, Notification.EXTRA_CONVERSATION_TITLE);
        List<LauncherCtlNotificationEvent.Message> messages = null;
        try {
            NotificationCompat.MessagingStyle style =
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification);
            if (style != null) {
                CharSequence styleTitle = style.getConversationTitle();
                if (styleTitle != null && styleTitle.length() > 0) conversation = styleTitle.toString();
                messages = new ArrayList<>();
                for (NotificationCompat.MessagingStyle.Message message : style.getMessages()) {
                    CharSequence text = message.getText();
                    Person person = message.getPerson();
                    CharSequence name = person == null ? null : person.getName();
                    messages.add(new LauncherCtlNotificationEvent.Message(
                        name == null ? null : name.toString(),
                        text == null ? null : text.toString(), message.getTimestamp()));
                }
            }
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Could not read messaging style: " + e.getMessage());
        }

        boolean carriesLines = (lines != null && !lines.isEmpty())
            || (messages != null && !messages.isEmpty());
        if (isGroupSummary(sbn) && (!carriesLines || groupHasChildren(sbn))) {
            return new ArrayList<>();
        }

        String packageName = sbn.getPackageName();
        return LauncherCtlNotificationEvent.explode(sbn.getKey(), packageName, appLabel(packageName),
            notification.category, notification.getChannelId(), conversation,
            toStringOrNull(extras, Notification.EXTRA_TITLE),
            toStringOrNull(extras, Notification.EXTRA_TEXT),
            toStringOrNull(extras, Notification.EXTRA_BIG_TEXT),
            toStringOrNull(extras, Notification.EXTRA_SUB_TEXT), sbn.getPostTime(), lines, messages);
    }

    private static boolean isHistoryNoise(@NonNull StatusBarNotification sbn,
                                          @NonNull Notification notification, @NonNull Bundle extras) {
        if (sbn.isOngoing() || (notification.flags
            & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE)) != 0) {
            return true;
        }
        if (extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0
            || extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
            || Notification.CATEGORY_PROGRESS.equals(notification.category)) {
            return true;
        }
        String template = extras.getString(Notification.EXTRA_TEMPLATE);
        return Notification.CATEGORY_TRANSPORT.equals(notification.category)
            || extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
            || (template != null && template.endsWith("MediaStyle"));
    }

    private boolean groupHasChildren(@NonNull StatusBarNotification summary) {
        String groupKey = summary.getGroupKey();
        if (groupKey == null) return false;
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active == null) return false;
            for (StatusBarNotification other : active) {
                if (other == null || other.getNotification() == null) continue;
                if (other.getKey().equals(summary.getKey()) || isGroupSummary(other)) continue;
                if (groupKey.equals(other.getGroupKey())) return true;
            }
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Failed to look for group children: " + e.getMessage());
        }
        return false;
    }

    private String toStringOrNull(Bundle extras, String key) {
        if (extras == null) return null;
        CharSequence value = extras.getCharSequence(key);
        return value == null ? null : value.toString();
    }

    private Bitmap extractAlbumArt(MediaMetadata metadata) {
        Bitmap art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art != null) return art;
        art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art != null) return art;
        return metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
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
        List<PinnedConversations.Candidate> candidates = new ArrayList<>();
        Set<String> activeKeys = new HashSet<>();
        StatusBarNotification[] active = null;
        try {
            active = getActiveNotifications();
        } catch (Throwable throwable) {
            Logger.logWarn(LOG_TAG, "Failed to read active notifications: " + throwable.getMessage());
        }
        if (active != null) {
            List<StatusBarNotification> sorted = new ArrayList<>();
            Set<String> groupsWithChildren = new HashSet<>();
            for (StatusBarNotification sbn : active) {
                if (sbn == null || sbn.getNotification() == null) continue;
                activeKeys.add(sbn.getKey());
                sorted.add(sbn);
                if (!isGroupSummary(sbn) && sbn.getGroupKey() != null) {
                    groupsWithChildren.add(sbn.getGroupKey());
                }
            }
            if (!rules.isEmpty()) {
                sorted.sort(Comparator.comparingLong(StatusBarNotification::getPostTime));
                // Two notifications reading exactly the same are one message seen twice, whatever
                // their keys (a summary beside its only child, a re-post): the first is kept and
                // the repeat neither makes a card nor adds to a conversation's count. Messages
                // that differ from one conversation are no longer dropped here — they fold into
                // one card below (PinnedConversations), which counts them.
                Set<String> seenContent = new HashSet<>();
                for (StatusBarNotification sbn : sorted) {
                    if (mUnpinned.contains(sbn.getKey())) continue;
                    // A group summary repeats what its children already say: chat apps post one
                    // beside every message, which would pin the same message twice.
                    if (isGroupSummary(sbn) && groupsWithChildren.contains(sbn.getGroupKey())) {
                        continue;
                    }
                    PinnedConversations.Candidate candidate = toPinnedCandidate(sbn, rules);
                    if (candidate == null) continue;
                    if (!seenContent.add(contentSignature(candidate.pin))) continue;
                    candidates.add(candidate);
                }
            }
        }
        mUnpinned.retainAll(activeKeys);

        Map<String, PinnedNotification> matched = new LinkedHashMap<>();
        for (PinnedNotification card : PinnedConversations.group(candidates)) {
            matched.put(card.conversationId, card);
        }
        // Keep the order of cards already on screen, then append new conversations oldest-first.
        List<PinnedNotification> ordered = new ArrayList<>();
        for (String conversation : mPinned.keySet()) {
            PinnedNotification pin = matched.remove(conversation);
            if (pin != null) ordered.add(pin);
        }
        ordered.addAll(matched.values());
        while (ordered.size() > TopPaneSlotMode.MAX_PINNED) ordered.remove(0);

        mPinned.clear();
        for (PinnedNotification pin : ordered) mPinned.put(pin.conversationId, pin);
        TopPaneFeed.setPinned(ordered);
    }

    private static boolean isGroupSummary(@NonNull StatusBarNotification sbn) {
        return (sbn.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0;
    }

    /** Two pins reading exactly the same are one message seen twice, whatever their keys. */
    @NonNull
    private static String contentSignature(@NonNull PinnedNotification pin) {
        return pin.packageName + '\n' + pin.sender + '\n' + pin.body;
    }

    /**
     * A rule-matched notification with what grouping needs: its conversation (shortcut id, else
     * conversation title, else sender; {@link PinnedConversations#key}), how many messages its
     * MessagingStyle carries, and the sender's picture — the latest incoming message's person
     * icon, else the notification's large icon, else none (the card then shows the app icon).
     */
    @Nullable
    private PinnedConversations.Candidate toPinnedCandidate(@NonNull StatusBarNotification sbn,
                                                            @NonNull List<EssentialNotificationRule> rules) {
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        String title = toStringOrNull(extras, Notification.EXTRA_TITLE);
        String body = toStringOrNull(extras, Notification.EXTRA_TEXT);
        if (body == null || body.isEmpty()) body = toStringOrNull(extras, Notification.EXTRA_BIG_TEXT);
        EssentialNotificationRule rule =
            EssentialNotificationRules.firstMatch(rules, sbn.getPackageName(), title, body);
        if (rule == null) return null;

        String conversationTitle = toStringOrNull(extras, Notification.EXTRA_CONVERSATION_TITLE);
        int messages = 0;
        Icon avatar = null;
        try {
            NotificationCompat.MessagingStyle style =
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification);
            if (style != null) {
                CharSequence styleTitle = style.getConversationTitle();
                if (styleTitle != null && styleTitle.length() > 0) {
                    conversationTitle = styleTitle.toString();
                }
                List<NotificationCompat.MessagingStyle.Message> list = style.getMessages();
                messages = list.size();
                for (int i = list.size() - 1; i >= 0; i--) {
                    // A message with no person is the user's own reply; the picture wanted is
                    // the latest one from the other side.
                    Person person = list.get(i).getPerson();
                    if (person == null) continue;
                    if (person.getIcon() != null) avatar = person.getIcon().toIcon(this);
                    break;
                }
            }
        } catch (Throwable throwable) {
            Logger.logWarn(LOG_TAG, "Could not read pinned messaging style: " + throwable.getMessage());
        }
        if (avatar == null) avatar = largeIcon(notification);

        String key = sbn.getKey();
        PinnedNotification pin = new PinnedNotification(key, sbn.getPackageName(), title,
            appLabel(sbn.getPackageName()), body, rule.id, rule.clearOnDismiss, sbn.getPostTime(),
            null, null, 1, avatar);
        String conversation = PinnedConversations.key(sbn.getPackageName(),
            notification.getShortcutId(), conversationTitle, title, key);
        return new PinnedConversations.Candidate(pin, conversation, messages);
    }

    @Nullable
    private static Icon largeIcon(@NonNull Notification notification) {
        try {
            Icon icon = notification.getLargeIcon();
            if (icon != null) return icon;
            Object extra = notification.extras == null ? null
                : notification.extras.get(Notification.EXTRA_LARGE_ICON);
            if (extra instanceof Icon) return (Icon) extra;
            if (extra instanceof Bitmap) return Icon.createWithBitmap((Bitmap) extra);
        } catch (Throwable ignored) {
        }
        return null;
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
            value == PlaybackState.STATE_PLAYING,
            state == null ? 0L : state.getLastPositionUpdateTime()));
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
        // The card goes as a whole: the slot dismisses every key folded into it, and whichever
        // of them arrives first takes the card off the pane.
        String conversation = conversationOf(key);
        boolean unpinned = conversation != null && mPinned.remove(conversation) != null;
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
        Notification notification = sbn == null ? null : sbn.getNotification();
        String conversation = conversationOf(key);
        String packageName = sbn == null
            ? (conversation != null ? mPinned.get(conversation).packageName : null)
            : sbn.getPackageName();
        if (notification != null && notification.contentIntent != null) {
            try {
                notification.contentIntent.send();
                if ((notification.flags & Notification.FLAG_AUTO_CANCEL) != 0) {
                    // The conversation was opened: its older messages folded into the card go
                    // with the latest, or the card would come back showing one of them.
                    List<String> keys = conversation == null
                        ? java.util.Collections.singletonList(key)
                        : new ArrayList<>(mPinned.get(conversation).keys);
                    for (String folded : keys) dismissPinned(folded, true);
                }
                return true;
            } catch (Throwable throwable) {
                Logger.logWarn(LOG_TAG,
                    "Pinned notification content intent failed: " + throwable.getMessage());
            }
        }
        if (packageName == null) return false;
        Intent launch =
            getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            Logger.logWarn(LOG_TAG, "No way to open " + packageName + " for a pinned notification");
            return false;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(launch);
            return true;
        } catch (Throwable throwable) {
            Logger.logWarn(LOG_TAG, "Cannot open " + packageName + ": " + throwable.getMessage());
            return false;
        }
    }

    @Override
    public void refreshPinned() {
        mMainHandler.post(this::rebuildPinnedNotifications);
    }

    /** The conversation whose card holds {@code key}, or null. */
    @Nullable
    private String conversationOf(@NonNull String key) {
        for (Map.Entry<String, PinnedNotification> entry : mPinned.entrySet()) {
            if (entry.getValue().keys.contains(key)) return entry.getKey();
        }
        return null;
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
}
