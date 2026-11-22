package com.termux.app.launcher.notifications;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Lightweight active-notification index for launcher dots.
 *
 * The notification listener owns updates. Badge observers receive only package names; live
 * notification objects remain in-memory and are exposed on demand for the dock's swipe-up panel.
 * Nothing from this UI index is persisted.
 */
public final class LauncherNotificationBadgeStore {

    public interface Listener {
        void onNotificationBadgePackagesChanged(@NonNull Set<String> packages);
    }

    private static final ConcurrentHashMap<String, String> ACTIVE_BADGE_KEYS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, StatusBarNotification> ACTIVE_NOTIFICATIONS = new ConcurrentHashMap<>();
    private static final CopyOnWriteArraySet<Listener> LISTENERS = new CopyOnWriteArraySet<>();

    @NonNull private static volatile Set<String> activePackages = Collections.emptySet();

    private LauncherNotificationBadgeStore() {
    }

    public static void addListener(@NonNull Listener listener) {
        LISTENERS.add(listener);
        listener.onNotificationBadgePackagesChanged(getActivePackages());
    }

    public static void removeListener(@Nullable Listener listener) {
        if (listener != null) {
            LISTENERS.remove(listener);
        }
    }

    public static boolean hasBadge(@Nullable String packageName) {
        return packageName != null && activePackages.contains(packageName);
    }

    /** Active badgeable notifications across {@code packages}, for a count badge. */
    public static int countFor(@NonNull Set<String> packages) {
        if (packages.isEmpty()) return 0;
        int count = 0;
        for (String packageName : ACTIVE_BADGE_KEYS.values()) {
            if (packages.contains(packageName)) count++;
        }
        return count;
    }

    @NonNull
    public static Set<String> getActivePackages() {
        return activePackages;
    }

    @NonNull
    public static List<StatusBarNotification> getNotificationsForPackage(@Nullable String packageName) {
        if (packageName == null) return Collections.emptyList();
        List<StatusBarNotification> result = new ArrayList<>();
        for (StatusBarNotification notification : ACTIVE_NOTIFICATIONS.values()) {
            if (notification != null && packageName.equals(notification.getPackageName())) {
                result.add(notification);
            }
        }
        result.sort((left, right) -> Long.compare(right.getPostTime(), left.getPostTime()));
        return result;
    }

    public static void syncFromActiveNotifications(
        @Nullable StatusBarNotification[] notifications,
        @Nullable NotificationListenerService.RankingMap rankingMap
    ) {
        ACTIVE_BADGE_KEYS.clear();
        ACTIVE_NOTIFICATIONS.clear();
        if (notifications != null) {
            for (StatusBarNotification sbn : notifications) {
                putIfBadgeable(sbn, rankingMap);
            }
        }
        publish(true);
    }

    public static void onNotificationPosted(
        @Nullable StatusBarNotification sbn,
        @Nullable NotificationListenerService.RankingMap rankingMap
    ) {
        if (sbn == null) {
            return;
        }
        String key = sbn.getKey();
        if (isBadgeable(sbn, rankingMap)) {
            ACTIVE_BADGE_KEYS.put(key, sbn.getPackageName());
            ACTIVE_NOTIFICATIONS.put(key, sbn);
        } else {
            ACTIVE_BADGE_KEYS.remove(key);
            ACTIVE_NOTIFICATIONS.remove(key);
        }
        publish(true);
    }

    public static void onNotificationRemoved(@Nullable StatusBarNotification sbn) {
        if (sbn != null) {
            boolean existed = ACTIVE_NOTIFICATIONS.remove(sbn.getKey()) != null;
            existed |= ACTIVE_BADGE_KEYS.remove(sbn.getKey()) != null;
            if (existed) publish(true);
        }
    }

    public static void clear() {
        ACTIVE_BADGE_KEYS.clear();
        ACTIVE_NOTIFICATIONS.clear();
        publish(true);
    }

    private static void putIfBadgeable(
        @Nullable StatusBarNotification sbn,
        @Nullable NotificationListenerService.RankingMap rankingMap
    ) {
        if (isBadgeable(sbn, rankingMap)) {
            ACTIVE_BADGE_KEYS.put(sbn.getKey(), sbn.getPackageName());
            ACTIVE_NOTIFICATIONS.put(sbn.getKey(), sbn);
        }
    }

    private static boolean isBadgeable(
        @Nullable StatusBarNotification sbn,
        @Nullable NotificationListenerService.RankingMap rankingMap
    ) {
        if (sbn == null || sbn.getNotification() == null || sbn.getPackageName() == null) {
            return false;
        }
        Notification notification = sbn.getNotification();
        if (sbn.isOngoing() || !sbn.isClearable()) {
            return false;
        }
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return false;
        }
        if (rankingMap != null) {
            NotificationListenerService.Ranking ranking = new NotificationListenerService.Ranking();
            if (rankingMap.getRanking(sbn.getKey(), ranking) && !ranking.canShowBadge()) {
                return false;
            }
        }
        return true;
    }

    private static void publish(boolean force) {
        Set<String> packages = Collections.unmodifiableSet(new HashSet<>(ACTIVE_BADGE_KEYS.values()));
        if (!force && packages.equals(activePackages)) {
            return;
        }
        activePackages = packages;
        for (Listener listener : LISTENERS) {
            listener.onNotificationBadgePackagesChanged(packages);
        }
    }
}
