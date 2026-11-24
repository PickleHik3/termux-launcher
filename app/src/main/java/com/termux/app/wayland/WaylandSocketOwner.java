package com.termux.app.wayland;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * The Wayland socket the launcher listens on while Wayland Display is chosen, so a client started
 * from a terminal finds a display before any app was opened from the drawer. The launcher never
 * talks Wayland on it: the first connection is a signal ({@link Listener#onConnectionWaiting}) to
 * start the host, which takes the socket over ({@link #dupForHost}) and accepts the client that is
 * waiting. The launcher keeps the socket while the host runs and after it stops, so the next
 * client starts it again; an idle socket costs a file descriptor.
 *
 * <p>Fails soft: a socket that cannot be made is logged and left to the host, which binds the path
 * itself as before ({@link #open} false). A file at the path that answers belongs to someone else
 * (a host left running by an earlier launcher process, or the user's own compositor) and is left
 * alone; one that refuses is stale and replaced. The socket is removed only while it is still
 * this one ({@link #close}).
 *
 * <p>Not held: libwayland's {@code wayland-0.lock}. Its compositors take it with {@code flock},
 * which Java cannot; another compositor started with {@code XDG_RUNTIME_DIR=$TMPDIR} may take the
 * name over, and the next {@link #open} notices the socket is gone and makes a new one when the
 * name is free again.
 *
 * <p>Main thread, except the wait for a connection, which runs on its own short-lived thread
 * between {@link #arm} and the connection or {@link #disarm}.
 */
final class WaylandSocketOwner {

    private static final String LOG_TAG = "TLWayland";

    /** Told on the main thread. */
    interface Listener {
        /** A client is waiting on the socket; the wait is disarmed until {@link #arm}. */
        void onConnectionWaiting();
    }

    /** What to do about the path before listening on it. */
    enum Step {
        /** Nothing there: bind. */
        BIND,
        /** A socket nobody serves (a killed launcher's, or a host's that died): remove, then bind. */
        REPLACE,
        /** Someone serves it: leave it, and do not listen. */
        LEAVE,
        /** It is already this socket. */
        KEEP
    }

    /**
     * The step for the path: {@code exists} whether anything is there, {@code ours} whether it is
     * the socket this owner bound (same inode), {@code answers} whether a connect to it succeeded.
     */
    @NonNull
    static Step step(boolean exists, boolean ours, boolean answers) {
        if (!exists) return Step.BIND;
        if (ours) return Step.KEEP;
        return answers ? Step.LEAVE : Step.REPLACE;
    }

    @NonNull private final String path;
    @NonNull private final Handler main;
    @NonNull private final Listener listener;

    @Nullable private LocalSocket socket;
    /** The inode bound, to tell this socket from one that replaced it. */
    private long inode = -1;
    @Nullable private Thread waiter;
    /** The write end that wakes the waiter to stop. */
    @Nullable private FileDescriptor wake;

    WaylandSocketOwner(@NonNull String path, @NonNull Handler main, @NonNull Listener listener) {
        this.path = path;
        this.main = main;
        this.listener = listener;
    }

    /** Whether this owner listens at the path now. */
    boolean isListening() {
        return socket != null && ours();
    }

    /**
     * Listens at the path unless it does already; true when it does after the call. Never throws.
     */
    boolean open() {
        if (socket != null && !ours()) {
            // Removed or taken over behind our back: this fd no longer has the name.
            Log.w(LOG_TAG, path + " is no longer the launcher's socket; making it again");
            closeSocket(false);
        }
        boolean exists = isSymlinkOrSocket();
        Step step = step(exists, socket != null, exists && socket == null && answers());
        switch (step) {
            case KEEP:
                return true;
            case LEAVE:
                Log.i(LOG_TAG, path + " is served by someone else; not listening on it");
                return false;
            case REPLACE:
                try {
                    Os.remove(path);
                } catch (ErrnoException e) {
                    Log.w(LOG_TAG, "cannot remove the stale " + path + ": " + e.getMessage());
                    return false;
                }
                break;
            default:
                break;
        }
        LocalSocket bound = new LocalSocket(LocalSocket.SOCKET_STREAM);
        try {
            bound.bind(new LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM));
            Os.listen(bound.getFileDescriptor(), 64);
            inode = Os.stat(path).st_ino;
            socket = bound;
            Log.i(LOG_TAG, "listening on " + path + " for the Linux windows host");
            return true;
        } catch (IOException | ErrnoException | RuntimeException e) {
            Log.w(LOG_TAG, "cannot listen on " + path + ": " + e);
            closeQuietly(bound);
            return false;
        }
    }

    /**
     * Stops listening and removes the path while it is still this socket, or when it is a stale
     * one nobody serves (a killed launcher's); one that answers is left to its server.
     */
    void close() {
        closeSocket(true);
        if (step(isSymlinkOrSocket(), false, answers()) != Step.REPLACE) return;
        try {
            Os.remove(path);
        } catch (ErrnoException e) {
            Log.w(LOG_TAG, "cannot remove the stale " + path + ": " + e.getMessage());
        }
    }

    /** A duplicate of the listening socket for the host to adopt; null when not listening. */
    @Nullable
    ParcelFileDescriptor dupForHost() {
        LocalSocket s = socket;
        if (s == null || !ours()) return null;
        try {
            return ParcelFileDescriptor.dup(s.getFileDescriptor());
        } catch (IOException | RuntimeException e) {
            Log.w(LOG_TAG, "cannot hand " + path + " over: " + e);
            return null;
        }
    }

    /**
     * Accepts and closes every connection waiting, so a client of a host that did not start or
     * died gets an error instead of waiting forever. Never blocks.
     */
    void dropWaiting() {
        LocalSocket s = socket;
        if (s == null) return;
        FileDescriptor fd = s.getFileDescriptor();
        int dropped = 0;
        try {
            while (dropped < 64 && readable(fd, 0)) {
                FileDescriptor client = Os.accept(fd, null);
                Os.close(client);
                dropped++;
            }
        } catch (ErrnoException | java.net.SocketException | RuntimeException e) {
            Log.w(LOG_TAG, "dropping waiting clients: " + e);
        }
        if (dropped > 0) Log.w(LOG_TAG, "dropped " + dropped + " waiting Wayland client(s)");
    }

    /** Waits for the next connection, off the main thread, unless already waiting. */
    void arm() {
        LocalSocket s = socket;
        if (s == null || waiter != null) return;
        FileDescriptor[] pipe;
        try {
            pipe = Os.pipe();
        } catch (ErrnoException e) {
            Log.w(LOG_TAG, "cannot wait on " + path + ": " + e.getMessage());
            return;
        }
        FileDescriptor listening = s.getFileDescriptor();
        FileDescriptor wakeRead = pipe[0];
        wake = pipe[1];
        Thread thread = new Thread(() -> {
            boolean waiting = false;
            try {
                StructPollfd conn = pollfd(listening);
                StructPollfd stop = pollfd(wakeRead);
                StructPollfd[] fds = {conn, stop};
                while (true) {
                    conn.revents = 0;
                    stop.revents = 0;
                    try {
                        Os.poll(fds, -1);
                    } catch (ErrnoException e) {
                        if (e.errno == OsConstants.EINTR) continue;
                        throw e;
                    }
                    if (stop.revents != 0) break;
                    if ((conn.revents & OsConstants.POLLIN) != 0) {
                        waiting = true;
                        break;
                    }
                    if (conn.revents != 0) break;
                }
            } catch (ErrnoException | RuntimeException e) {
                Log.w(LOG_TAG, "waiting on " + path + ": " + e);
            } finally {
                closeFd(wakeRead);
            }
            boolean connection = waiting;
            Thread self = Thread.currentThread();
            main.post(() -> waiterEnded(self, connection));
        }, "tl-wayland-socket");
        waiter = thread;
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops waiting for a connection (the host accepts them now, or the socket goes). */
    void disarm() {
        FileDescriptor w = wake;
        wake = null;
        waiter = null;
        if (w == null) return;
        try {
            Os.write(w, new byte[]{1}, 0, 1);
        } catch (ErrnoException | java.io.InterruptedIOException | RuntimeException ignored) {
        }
        closeFd(w);
    }

    private void waiterEnded(@NonNull Thread thread, boolean connection) {
        // A waiter stopped by disarm, or replaced since, says nothing.
        if (waiter != thread) return;
        FileDescriptor w = wake;
        wake = null;
        waiter = null;
        if (w != null) closeFd(w);
        if (connection && socket != null) listener.onConnectionWaiting();
    }

    private void closeSocket(boolean removePath) {
        disarm();
        LocalSocket s = socket;
        socket = null;
        if (s == null) return;
        boolean stillOurs = removePath && ours(s);
        closeQuietly(s);
        if (stillOurs) {
            try {
                Os.remove(path);
            } catch (ErrnoException e) {
                Log.w(LOG_TAG, "cannot remove " + path + ": " + e.getMessage());
            }
        }
        inode = -1;
    }

    private boolean ours() {
        return ours(socket);
    }

    private boolean ours(@Nullable LocalSocket s) {
        if (s == null || inode < 0) return false;
        try {
            return Os.lstat(path).st_ino == inode;
        } catch (ErrnoException e) {
            return false;
        }
    }

    private boolean isSymlinkOrSocket() {
        try {
            Os.lstat(path);
            return true;
        } catch (ErrnoException e) {
            return false;
        }
    }

    /** Whether something accepts a connection at the path. */
    private boolean answers() {
        LocalSocket probe = new LocalSocket(LocalSocket.SOCKET_STREAM);
        try {
            probe.connect(new LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM));
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            closeQuietly(probe);
        }
    }

    @VisibleForTesting
    static boolean readable(@NonNull FileDescriptor fd, int timeoutMs) throws ErrnoException {
        StructPollfd p = pollfd(fd);
        return Os.poll(new StructPollfd[]{p}, timeoutMs) > 0 && (p.revents & OsConstants.POLLIN) != 0;
    }

    @NonNull
    private static StructPollfd pollfd(@NonNull FileDescriptor fd) {
        StructPollfd p = new StructPollfd();
        p.fd = fd;
        p.events = (short) OsConstants.POLLIN;
        return p;
    }

    private static void closeFd(@NonNull FileDescriptor fd) {
        try {
            Os.close(fd);
        } catch (ErrnoException | RuntimeException ignored) {
        }
    }

    private static void closeQuietly(@NonNull LocalSocket s) {
        try {
            s.close();
        } catch (IOException | RuntimeException ignored) {
        }
    }
}
