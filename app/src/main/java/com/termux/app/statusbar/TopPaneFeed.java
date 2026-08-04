package com.termux.app.statusbar;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Process-wide broker between the notification listener service and the top-pane widget slot.
 *
 * <p>The service owns the data (it is the only component with listener access) and the slot owns the
 * rendering, so neither holds a reference to the other. State lives here so it also survives an
 * Activity recreation.
 */
public final class TopPaneFeed {

    /** Transport and dismiss actions, implemented by the listener service while it is connected. */
    public interface Controls {
        boolean skipPrevious();

        boolean togglePlayPause(boolean play);

        boolean skipNext();

        /** Unpin {@code key}, cancelling the source notification when {@code clear} is set. */
        boolean dismissPinned(@NonNull String key, boolean clear);

        /** Open what {@code key} points at, the way tapping it in the shade would. */
        boolean openPinned(@NonNull String key);
    }

    public interface Observer {
        @MainThread
        void onTopPaneFeedChanged();
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArrayList<Observer> OBSERVERS = new CopyOnWriteArrayList<>();

    private static volatile List<PinnedNotification> pinned = Collections.emptyList();
    private static volatile TopPaneMediaState media;
    private static volatile Controls controls;
    private static volatile boolean listenerConnected;

    private TopPaneFeed() {
    }

    public static void addObserver(@NonNull Observer observer) {
        if (!OBSERVERS.contains(observer)) OBSERVERS.add(observer);
    }

    public static void removeObserver(@NonNull Observer observer) {
        OBSERVERS.remove(observer);
    }

    @NonNull
    public static List<PinnedNotification> getPinned() {
        return listenerConnected ? pinned : Collections.emptyList();
    }

    @Nullable
    public static TopPaneMediaState getMedia() {
        return listenerConnected ? media : null;
    }

    public static boolean isListenerConnected() {
        return listenerConnected;
    }

    public static void setControls(@Nullable Controls value) {
        controls = value;
    }

    public static void setListenerConnected(boolean connected) {
        if (listenerConnected == connected) return;
        listenerConnected = connected;
        if (!connected) {
            pinned = Collections.emptyList();
            media = null;
        }
        notifyChanged();
    }

    public static void setPinned(@Nullable List<PinnedNotification> value) {
        List<PinnedNotification> next = value == null || value.isEmpty()
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(value));
        if (sameKeysAndContent(pinned, next)) return;
        pinned = next;
        notifyChanged();
    }

    public static void setMedia(@Nullable TopPaneMediaState value) {
        TopPaneMediaState previous = media;
        media = value;
        if (previous == value) return;
        notifyChanged();
    }

    public static boolean skipPrevious() {
        Controls current = controls;
        return current != null && current.skipPrevious();
    }

    public static boolean togglePlayPause(boolean play) {
        Controls current = controls;
        return current != null && current.togglePlayPause(play);
    }

    public static boolean skipNext() {
        Controls current = controls;
        return current != null && current.skipNext();
    }

    public static boolean dismissPinned(@NonNull String key, boolean clear) {
        Controls current = controls;
        return current != null && current.dismissPinned(key, clear);
    }

    public static boolean openPinned(@NonNull String key) {
        Controls current = controls;
        return current != null && current.openPinned(key);
    }

    /**
     * Optimistic play/pause flip so the glyph responds to the tap; the real playback callback
     * reconciles it a frame or two later.
     */
    public static void applyOptimisticPlayState(boolean play) {
        TopPaneMediaState current = media;
        if (current == null || current.playing == play) return;
        media = current.withPlaying(play);
        notifyChanged();
    }

    private static void notifyChanged() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatch();
        } else {
            MAIN.post(TopPaneFeed::dispatch);
        }
    }

    private static void dispatch() {
        for (Observer observer : OBSERVERS) observer.onTopPaneFeedChanged();
    }

    private static boolean sameKeysAndContent(@NonNull List<PinnedNotification> a,
                                             @NonNull List<PinnedNotification> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).sameContentAs(b.get(i))) return false;
        }
        return true;
    }
}
