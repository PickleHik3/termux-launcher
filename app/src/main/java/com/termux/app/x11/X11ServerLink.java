package com.termux.app.x11;

import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.x11.ICmdEntryInterface;

import java.util.NoSuchElementException;

/**
 * The one Binder a display server is reached through, and the one death link on it.
 *
 * <p>A server announces itself again every second until something takes its socket, and a
 * replaced server announces a different Binder altogether. Linking a death recipient on every
 * announcement therefore piled links up on a live server and left recipients on a dead one that
 * fired against its successor. This holds exactly one link: an announcement of the Binder already
 * held changes nothing, a new Binder replaces the old one and unlinks it first, and a Binder that
 * is already dead is never taken at all.
 */
final class X11ServerLink {

    /** The server this link is on has gone. Runs on a Binder thread. */
    interface Listener {
        void onServerDied();
    }

    @Nullable private IBinder binder;
    @Nullable private ICmdEntryInterface service;
    @Nullable private IBinder.DeathRecipient recipient;

    /**
     * Take an announced Binder. True when it is now the one held, whether it was just taken or was
     * already there; false when it is dead on arrival, in which case nothing is held.
     */
    boolean accept(@NonNull IBinder candidate, @NonNull Listener listener) {
        if (candidate == binder || candidate.equals(binder)) return true;
        release();
        IBinder.DeathRecipient death = new IBinder.DeathRecipient() {
            @Override public void binderDied() {
                // A link that has been replaced or released is no longer this server's voice.
                // unlinkToDeath stops the callback in practice, but a death already in flight on
                // the Binder thread can still land here after the swap.
                if (recipient != this) return;
                listener.onServerDied();
            }
        };
        try {
            candidate.linkToDeath(death, 0);
        } catch (RemoteException e) {
            return false;
        }
        binder = candidate;
        recipient = death;
        service = ICmdEntryInterface.Stub.asInterface(candidate);
        return true;
    }

    /** True when {@code candidate} is the Binder currently held. */
    boolean holds(@Nullable IBinder candidate) {
        return binder != null && (candidate == binder || binder.equals(candidate));
    }

    /** The server behind the held Binder, or null when nothing is held. */
    @Nullable
    ICmdEntryInterface service() {
        return service;
    }

    boolean isLinked() {
        return binder != null;
    }

    /** Drop the Binder and its death link. Safe to call with nothing held. */
    void release() {
        IBinder old = binder;
        IBinder.DeathRecipient death = recipient;
        binder = null;
        service = null;
        recipient = null;
        if (old == null || death == null) return;
        try {
            old.unlinkToDeath(death, 0);
        } catch (NoSuchElementException ignored) {
            // The server died first and the link went with it.
        }
    }
}
