package com.termux.app.launcher.pin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayDeque;

/**
 * The seam between a pin request and Home. The confirm card
 * runs in this process but owns no Home model (the widget repository lives in the launcher
 * activity, one instance, so a second would overwrite the first); they hand their work to the
 * Home pane through here. The activity attaches its pane from start to stop; work that arrives
 * while nothing is attached waits, a few items at most, and runs on the next attach.
 */
public final class HomePinHandoff {

    /** Work for Home: runs once, on the main thread, against the attached target. */
    public interface Task {
        void run(@NonNull HomePinTarget target);
    }

    /** More than this waiting is a sender flooding; the oldest is dropped. */
    static final int MAX_WAITING = 8;

    private static final Object LOCK = new Object();
    @Nullable private static HomePinTarget attached;
    private static final ArrayDeque<Task> waiting = new ArrayDeque<>();

    private HomePinHandoff() {}

    /** The activity's pane is ready for work: whatever waited runs now. */
    public static void attach(@NonNull HomePinTarget target) {
        ArrayDeque<Task> due;
        synchronized (LOCK) {
            attached = target;
            due = new ArrayDeque<>(waiting);
            waiting.clear();
        }
        for (Task task : due) task.run(target);
    }

    /** The activity stopped; {@code target} stops taking work unless another replaced it. */
    public static void detach(@NonNull HomePinTarget target) {
        synchronized (LOCK) {
            if (attached == target) attached = null;
        }
    }

    /** The attached Home, or null while the launcher activity is stopped or gone. */
    @Nullable
    public static HomePinTarget target() {
        synchronized (LOCK) {
            return attached;
        }
    }

    /**
     * Runs {@code task} now when Home is attached; otherwise keeps it for the next attach. Returns
     * whether it ran now.
     */
    public static boolean deliver(@NonNull Task task) {
        HomePinTarget target;
        synchronized (LOCK) {
            target = attached;
            if (target == null) {
                if (waiting.size() >= MAX_WAITING) waiting.pollFirst();
                waiting.addLast(task);
                return false;
            }
        }
        task.run(target);
        return true;
    }
}
