package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Which activity's session client the terminal sessions report to.
 *
 * <p>The service was written for one activity, but Android can run two: {@code singleTask} is
 * per display, so a launch on a second display puts a second TermuxActivity in the same process.
 * Each one attaches its own client on connecting. Before this roster the newest attach simply
 * replaced the previous client, and the newest detach fell all the way back to the headless
 * service client, so when a second instance came and went the first, still visible, instance
 * stopped hearing about output and repainted only on the cursor blink.
 *
 * <p>The roster keeps every attached client in attach order. The current client is the most
 * recently attached one still on the roster: a departing client hands the sessions back to the
 * one attached before it, and only the last departure leaves nobody. Pure, so it is testable
 * without a service.
 */
public final class TerminalSessionClientRoster<C> {

    private final List<C> attached = new ArrayList<>();

    /** Attach {@code client}, or move it to the front if already attached; it becomes current. */
    @NonNull
    public C attach(@NonNull C client) {
        attached.remove(client);
        attached.add(client);
        return client;
    }

    /**
     * Detach {@code client}. Returns the client the sessions should report to afterwards, which is
     * the current client when a non-current one leaves, the previous one when the current leaves,
     * and null when the roster is empty.
     */
    @Nullable
    public C detach(@NonNull C client) {
        attached.remove(client);
        return current();
    }

    /** Every attached client, most recent last; used to tear the roster down on unbind. */
    @NonNull
    public List<C> detachAll() {
        List<C> gone = new ArrayList<>(attached);
        attached.clear();
        return gone;
    }

    @Nullable
    public C current() {
        return attached.isEmpty() ? null : attached.get(attached.size() - 1);
    }

    public boolean contains(@NonNull C client) {
        return attached.contains(client);
    }

    public boolean isEmpty() {
        return attached.isEmpty();
    }
}
