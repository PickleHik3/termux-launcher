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
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * How long a burst of posts and removals is gathered before the pinned cards are rebuilt
     * once for all of it.
     */
    private static final long PINNED_REBUILD_DELAY_MS = 120L;
    /** Stands for a removed notification among the pending shade changes. */
    private static final Object REMOVED = new Object();

    private static volatile boolean listenerConnected;
    private static volatile LauncherCtlNotificationListener activeInstance;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    /**
     * Where the pinned cards are worked out: rule matching, messaging-style parsing and avatars,
     * none of which the main thread needs to wait for. Only the finished cards come back.
     */
    @Nullable private HandlerThread mWorkerThread;
    @Nullable private volatile Handler mWorker;
    private final CoalescingTask mPinnedRebuild = new CoalescingTask(PINNED_REBUILD_DELAY_MS,
        (task, delayMs) -> {
            Handler worker = mWorker;
            return worker != null && worker.postDelayed(task, delayMs);
        },
        this::rebuildPinnedOnWorker);
    /**
     * Posts and removals not yet folded into {@link #mShade}, by key: the latest notification, or
     * {@link #REMOVED}. Written on main as the callbacks arrive, drained by the worker, so a
     * notification re-posted ten times in a burst is read once.
     */
    private final ConcurrentHashMap<String, Object> mShadeChanges = new ConcurrentHashMap<>();
    /** Set when the worker's mirror must be rebuilt from scratch; carries the shade if known. */
    private final AtomicBoolean mShadeReseed = new AtomicBoolean();
    private final AtomicReference<StatusBarNotification[]> mShadeSeed = new AtomicReference<>();
    /**
     * The worker's mirror of the shade, in post order: just what pinning needs from each
     * notification, so it holds no notification (and no bitmap) the rules did not match.
     */
    private final LinkedHashMap<String, ShadeEntry> mShade = new LinkedHashMap<>();
    /** The rules {@link #mShade}'s candidates were matched against; worker only. */
    @Nullable private List<EssentialNotificationRule> mShadeRules;
    /**
     * Group key of every active notification that is not a group summary, by key, so the history
     * can tell whether a summary has children without asking for the whole shade again.
     */
    private final ConcurrentHashMap<String, String> mChildGroups = new ConcurrentHashMap<>();
    @Nullable private volatile TermuxAppSharedPreferences mPreferences;
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
            mTopPaneState = state;
            publishTopPaneMedia();
        }

        @Override public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            mTopPaneMetadata = metadata;
            publishTopPaneMedia();
        }

        @Override public void onSessionDestroyed() {
            detachTopPaneController();
            syncTopPaneMedia();
        }
    };

    @Nullable private MediaController mTopPaneController;
    /**
     * What {@link #mTopPaneController} last reported, kept from its callbacks so republishing
     * needs no binder call and an unchanged track keeps the same artwork bitmap (which is what
     * lets {@link TopPaneFeed#setMedia} see the state as unchanged).
     */
    @Nullable private PlaybackState mTopPaneState;
    @Nullable private MediaMetadata mTopPaneMetadata;

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread thread = new HandlerThread("PinnedNotifications",
            Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mWorkerThread = thread;
        mWorker = new Handler(thread.getLooper());
    }

    @Override
    public void onDestroy() {
        mWorker = null;
        HandlerThread thread = mWorkerThread;
        mWorkerThread = null;
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Override
    public void onListenerConnected() {
        activeInstance = this;
        listenerConnected = true;
        Logger.logInfo(LOG_TAG, "Notification listener connected");
        TopPaneFeed.setControls(this);
        TopPaneFeed.setListenerConnected(true);
        StatusBarNotification[] active = rebuildBadges();
        seedChildGroups(active);
        registerSessionsListener();
        syncTopPaneMedia();
        // The badges just read the whole shade; the worker starts its mirror from that copy.
        mShadeChanges.clear();
        mShadeSeed.set(active);
        mShadeReseed.set(true);
        mPinnedRebuild.request();
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
        mShadeChanges.clear();
        mShadeSeed.set(null);
        mChildGroups.clear();
        Handler worker = mWorker;
        if (worker != null) worker.post(this::clearShadeOnWorker);
        TopPaneFeed.setControls(null);
        TopPaneFeed.setListenerConnected(false);
        Logger.logWarn(LOG_TAG, "Notification listener disconnected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        LauncherNotificationBadgeStore.onNotificationPosted(sbn, null);
        onShadePosted(sbn);
        persistPosted(sbn);
        mPinnedRebuild.request();
        refreshTopPaneMediaFor(sbn);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, NotificationListenerService.RankingMap rankingMap) {
        LauncherNotificationBadgeStore.onNotificationPosted(sbn, rankingMap);
        onShadePosted(sbn);
        persistPosted(sbn);
        mPinnedRebuild.request();
        refreshTopPaneMediaFor(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        LauncherNotificationBadgeStore.onNotificationRemoved(sbn);
        if (sbn != null) {
            mUnpinned.remove(sbn.getKey());
            mChildGroups.remove(sbn.getKey());
            mShadeChanges.put(sbn.getKey(), REMOVED);
        }
        persistRemoved(sbn);
        mPinnedRebuild.request();
        refreshTopPaneMediaFor(sbn);
    }

    /** Records a post for the pinned mirror and the history's group lookup. */
    private void onShadePosted(@Nullable StatusBarNotification sbn) {
        if (sbn == null || sbn.getKey() == null) return;
        if (sbn.getNotification() == null) {
            mChildGroups.remove(sbn.getKey());
            mShadeChanges.put(sbn.getKey(), REMOVED);
            return;
        }
        trackChildGroup(sbn);
        mShadeChanges.put(sbn.getKey(), sbn);
    }

    private void trackChildGroup(@NonNull StatusBarNotification sbn) {
        String groupKey = sbn.getGroupKey();
        if (isGroupSummary(sbn) || groupKey == null) mChildGroups.remove(sbn.getKey());
        else mChildGroups.put(sbn.getKey(), groupKey);
    }

    private void seedChildGroups(@Nullable StatusBarNotification[] active) {
        mChildGroups.clear();
        if (active == null) return;
        for (StatusBarNotification sbn : active) {
            if (sbn == null || sbn.getKey() == null || sbn.getNotification() == null) continue;
            trackChildGroup(sbn);
        }
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

    /** Rebuilds the dots from the whole shade and hands the shade back, or null on failure. */
    @Nullable
    private StatusBarNotification[] rebuildBadges() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            LauncherNotificationBadgeStore.syncFromActiveNotifications(active, null);
            return active;
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to rebuild notification badges: " + e.getMessage());
            return null;
        }
    }

    /**
     * The preferences, built once: building them resolves a package context, which is not
     * something to do per notification. The getters still read the live store, so a change in
     * Settings is seen on the next notification.
     */
    @Nullable
    private TermuxAppSharedPreferences preferences() {
        TermuxAppSharedPreferences preferences = mPreferences;
        if (preferences == null) {
            preferences = TermuxAppSharedPreferences.build(this);
            mPreferences = preferences;
        }
        return preferences;
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
            TermuxAppSharedPreferences preferences = preferences();
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

    /**
     * Whether another, non-summary notification in the shade shares the summary's group. Read
     * from {@link #mChildGroups}, which the callbacks keep level with the shade.
     */
    private boolean groupHasChildren(@NonNull StatusBarNotification summary) {
        String groupKey = summary.getGroupKey();
        if (groupKey == null) return false;
        for (Map.Entry<String, String> child : mChildGroups.entrySet()) {
            if (child.getKey().equals(summary.getKey())) continue;
            if (groupKey.equals(child.getValue())) return true;
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
        listener.mPinnedRebuild.request();
    }

    /** What pinning needs to remember of one notification in the shade. */
    private static final class ShadeEntry {
        @Nullable final String groupKey;
        final boolean summary;
        final long postTime;
        /** The card it would make, or null when no rule matches it. */
        @Nullable final PinnedConversations.Candidate candidate;

        ShadeEntry(@Nullable String groupKey, boolean summary, long postTime,
                   @Nullable PinnedConversations.Candidate candidate) {
            this.groupKey = groupKey;
            this.summary = summary;
            this.postTime = postTime;
            this.candidate = candidate;
        }
    }

    @NonNull
    private ShadeEntry shadeEntry(@NonNull StatusBarNotification sbn,
                                  @NonNull List<EssentialNotificationRule> rules) {
        return new ShadeEntry(sbn.getGroupKey(), isGroupSummary(sbn), sbn.getPostTime(),
            rules.isEmpty() ? null : toPinnedCandidate(sbn, rules));
    }

    private void clearShadeOnWorker() {
        mShade.clear();
        mShadeRules = null;
    }

    /**
     * Brings the mirror level with the shade and works out the cards, on the worker. Only the
     * steps that read state the main thread owns (the hand-unpinned keys, the cards on screen)
     * are left for {@link #applyPinned}.
     */
    private void rebuildPinnedOnWorker() {
        if (activeInstance != this) return;
        List<EssentialNotificationRule> rules = EssentialNotificationRules.loadCached(preferences());
        boolean reseed = mShadeReseed.getAndSet(false);
        StatusBarNotification[] seed = mShadeSeed.getAndSet(null);
        // New rules change which notifications make cards: every candidate is matched again, so
        // the shade is read afresh rather than kept.
        if (rules != mShadeRules) reseed = true;
        if (reseed) {
            if (seed == null) {
                try {
                    seed = getActiveNotifications();
                } catch (Throwable throwable) {
                    Logger.logWarn(LOG_TAG, "Failed to read active notifications: "
                        + throwable.getMessage());
                }
            }
            mShade.clear();
            if (seed != null) {
                for (StatusBarNotification sbn : seed) {
                    if (sbn == null || sbn.getKey() == null || sbn.getNotification() == null) continue;
                    mShade.put(sbn.getKey(), shadeEntry(sbn, rules));
                }
            }
            mShadeRules = rules;
        }
        for (Map.Entry<String, Object> change : mShadeChanges.entrySet()) {
            String key = change.getKey();
            Object value = change.getValue();
            if (value == REMOVED) {
                mShade.remove(key);
            } else {
                // Re-inserted at the end: a re-post is the newest arrival, as in the shade.
                mShade.remove(key);
                mShade.put(key, shadeEntry((StatusBarNotification) value, rules));
            }
            // A newer change to the same key, made meanwhile, stays for the next pass.
            mShadeChanges.remove(key, value);
        }

        Set<String> activeKeys = new HashSet<>(mShade.keySet());
        List<PinnedConversations.Candidate> matched = new ArrayList<>();
        if (!rules.isEmpty()) {
            Set<String> groupsWithChildren = new HashSet<>();
            List<ShadeEntry> sorted = new ArrayList<>(mShade.values());
            for (ShadeEntry entry : sorted) {
                if (!entry.summary && entry.groupKey != null) groupsWithChildren.add(entry.groupKey);
            }
            sorted.sort((a, b) -> Long.compare(a.postTime, b.postTime));
            for (ShadeEntry entry : sorted) {
                // A group summary repeats what its children already say: chat apps post one
                // beside every message, which would pin the same message twice.
                if (entry.summary && groupsWithChildren.contains(entry.groupKey)) continue;
                if (entry.candidate != null) matched.add(entry.candidate);
            }
        }
        mMainHandler.post(() -> applyPinned(matched, activeKeys));
    }

    /**
     * Puts the worker's cards on the pane, on main. {@code found} is every rule-matched
     * notification in post order, group summaries with children already left out.
     */
    private void applyPinned(@NonNull List<PinnedConversations.Candidate> found,
                             @NonNull Set<String> activeKeys) {
        // The listener was disconnected or replaced while the worker ran: the cards are stale.
        if (activeInstance != this || !listenerConnected) return;
        // Two notifications reading exactly the same are one message seen twice, whatever their
        // keys (a summary beside its only child, a re-post): the first is kept and the repeat
        // neither makes a card nor adds to a conversation's count. Messages that differ from one
        // conversation are no longer dropped here — they fold into one card below
        // (PinnedConversations), which counts them.
        List<PinnedConversations.Candidate> candidates = new ArrayList<>();
        Set<String> seenContent = new HashSet<>();
        for (PinnedConversations.Candidate candidate : found) {
            if (mUnpinned.contains(candidate.pin.key)) continue;
            if (!seenContent.add(contentSignature(candidate.pin))) continue;
            candidates.add(candidate);
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
            if (selected != null) {
                mTopPaneMetadata = selected.getMetadata();
                selected.registerCallback(mMediaCallback, mMainHandler);
            }
        }
        if (selected != null) mTopPaneState = selected.getPlaybackState();
        publishTopPaneMedia();
    }

    private void detachTopPaneController() {
        mTopPaneState = null;
        mTopPaneMetadata = null;
        if (mTopPaneController == null) return;
        try {
            mTopPaneController.unregisterCallback(mMediaCallback);
        } catch (Throwable ignored) {
        }
        mTopPaneController = null;
    }

    /**
     * Republishes the media card after a notification event. A post or removal from the playing
     * app (its own media notification moving) also re-reads the playback state, as a backstop for
     * a callback that has not arrived yet; any other app's notification changes nothing here, so
     * the cached state is republished and {@link TopPaneFeed#setMedia} drops it as unchanged.
     */
    private void refreshTopPaneMediaFor(@Nullable StatusBarNotification sbn) {
        MediaController controller = mTopPaneController;
        if (controller != null && sbn != null
            && controller.getPackageName().equals(sbn.getPackageName())) {
            try {
                mTopPaneState = controller.getPlaybackState();
            } catch (Throwable throwable) {
                Logger.logWarn(LOG_TAG, "Failed to read playback state: " + throwable.getMessage());
            }
        }
        publishTopPaneMedia();
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
        PlaybackState state = mTopPaneState;
        int value = state == null ? PlaybackState.STATE_NONE : state.getState();
        if (value != PlaybackState.STATE_PLAYING && value != PlaybackState.STATE_PAUSED) {
            TopPaneFeed.setMedia(null);
            return;
        }
        MediaMetadata metadata = mTopPaneMetadata;
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
        mPinnedRebuild.request();
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
