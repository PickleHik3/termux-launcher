package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.List;

/**
 * One Binder, one death link. The server re-announces itself every second until its socket is
 * taken, so the link must stay a single link however many times the same Binder arrives, and a
 * replaced server must take its recipient with it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class X11ServerLinkTest {

    /** A Binder that remembers who is linked to it and can be killed. */
    private static final class FakeBinder implements IBinder {
        final List<DeathRecipient> links = new ArrayList<>();
        boolean dead;

        void die() {
            dead = true;
            for (DeathRecipient recipient : new ArrayList<>(links)) recipient.binderDied();
        }

        @Override public void linkToDeath(DeathRecipient recipient, int flags)
                throws RemoteException {
            if (dead) throw new DeadObjectException();
            links.add(recipient);
        }
        @Override public boolean unlinkToDeath(DeathRecipient recipient, int flags) {
            return links.remove(recipient);
        }
        @Override public String getInterfaceDescriptor() { return "fake"; }
        @Override public boolean pingBinder() { return !dead; }
        @Override public boolean isBinderAlive() { return !dead; }
        @Override public IInterface queryLocalInterface(String descriptor) { return null; }
        @Override public void dump(FileDescriptor fd, String[] args) { }
        @Override public void dumpAsync(FileDescriptor fd, String[] args) { }
        @Override public boolean transact(int code, Parcel data, Parcel reply, int flags) {
            return false;
        }
    }

    private int deaths;
    private final X11ServerLink.Listener listener = () -> deaths++;

    @Test public void theSameBinderAnnouncedAgainIsOneLink() {
        FakeBinder server = new FakeBinder();
        X11ServerLink link = new X11ServerLink();

        assertTrue(link.accept(server, listener));
        assertTrue(link.accept(server, listener));
        assertTrue(link.accept(server, listener));

        assertEquals("one death link however often the server knocks", 1, server.links.size());
        assertTrue(link.holds(server));
    }

    @Test public void aNewServerUnlinksTheOldOne() {
        FakeBinder first = new FakeBinder();
        FakeBinder second = new FakeBinder();
        X11ServerLink link = new X11ServerLink();
        link.accept(first, listener);

        assertTrue(link.accept(second, listener));

        assertEquals("the replaced server keeps no recipient", 0, first.links.size());
        assertEquals(1, second.links.size());
        assertTrue(link.holds(second));
        assertFalse(link.holds(first));
    }

    @Test public void aReplacedServersDeathIsNotReported() {
        FakeBinder first = new FakeBinder();
        FakeBinder second = new FakeBinder();
        X11ServerLink link = new X11ServerLink();
        link.accept(first, listener);
        link.accept(second, listener);

        first.die();

        assertEquals("a stale server cannot declare the live one dead", 0, deaths);
        assertTrue(link.isLinked());
    }

    @Test public void theHeldServersDeathIsReportedOnce() {
        FakeBinder server = new FakeBinder();
        X11ServerLink link = new X11ServerLink();
        link.accept(server, listener);
        link.accept(server, listener);

        server.die();

        assertEquals(1, deaths);
    }

    @Test public void aDeadBinderIsNeverTaken() {
        FakeBinder server = new FakeBinder();
        server.dead = true;
        X11ServerLink link = new X11ServerLink();

        assertFalse(link.accept(server, listener));

        assertFalse(link.isLinked());
        assertNull(link.service());
    }

    @Test public void releaseUnlinksAndForgets() {
        FakeBinder server = new FakeBinder();
        X11ServerLink link = new X11ServerLink();
        link.accept(server, listener);

        link.release();

        assertEquals(0, server.links.size());
        assertFalse(link.isLinked());
        assertNull(link.service());
        // Released is released: a later death of that server is nobody's business here.
        server.die();
        assertEquals(0, deaths);
    }

    @Test public void serviceSpeaksToTheHeldBinder() {
        FakeBinder server = new FakeBinder();
        X11ServerLink link = new X11ServerLink();
        link.accept(server, listener);

        assertSame(server, link.service().asBinder());
    }
}
