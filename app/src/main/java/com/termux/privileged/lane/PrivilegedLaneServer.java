package com.termux.privileged.lane;

import android.content.Context;
import android.net.Credentials;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.store.TlstoreInstaller;
import com.termux.privileged.PrivilegedPolicyStore;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The privileged lane's front door: a {@link LocalServerSocket} in the abstract namespace named
 * {@code <package>.priv}, which a command-line client in a terminal session connects to with one
 * request line ({@link LaneRequest}). A request that passes the allowlist ({@link LaneAllowlist})
 * is staged and spawned as the shell uid through the Shizuku user service
 * ({@link PrivilegedLaneBinding}, {@link PrivilegedLaneService}), and the pty master comes back
 * to the client attached to the {@code ok} line. Every connection gets its own thread.
 *
 * <p>Only this uid may connect: the peer credentials are checked before a byte is read, and a
 * connection from anyone else is closed and logged. The server runs whether or not Shizuku is
 * there, so a client always gets an {@code err} line it can print rather than a refused connect.
 */
public final class PrivilegedLaneServer {

    private static final String TAG = "PrivilegedLaneServer";

    /** Appended to the package name: {@code com.termux.priv}, {@code io.vaj.tl.priv}. */
    public static final String SOCKET_SUFFIX = ".priv";

    /** How long a request waits for the user service to come up before giving the client an answer. */
    private static final long BIND_TIMEOUT_MS = 10_000L;

    private static PrivilegedLaneServer instance;

    private Context appContext;
    private LaneAllowlist allowlist;
    private LocalServerSocket serverSocket;
    private Thread acceptThread;
    private final AtomicInteger connectionCount = new AtomicInteger();

    private PrivilegedLaneServer() {
    }

    public static synchronized PrivilegedLaneServer getInstance() {
        if (instance == null) instance = new PrivilegedLaneServer();
        return instance;
    }

    @NonNull
    public static String socketName(@NonNull Context context) {
        return context.getPackageName() + SOCKET_SUFFIX;
    }

    /** Binds the socket and starts accepting; a second call is a no-op. */
    public synchronized void start(@NonNull Context context) {
        if (acceptThread != null) return;
        this.appContext = context.getApplicationContext();
        allowlist = new LaneAllowlist(this.appContext.getFilesDir(),
            TlstoreInstaller.shippedCatalogFile(), TlstoreInstaller.refreshedCatalogFile());
        String name = socketName(this.appContext);
        try {
            serverSocket = new LocalServerSocket(name);
        } catch (IOException e) {
            Log.e(TAG, "Cannot bind @" + name + "; the privileged lane is off", e);
            return;
        }
        PrivilegedLaneBinding.getInstance().start(this.appContext);
        acceptThread = new Thread(this::acceptLoop, "priv-lane-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        Log.i(TAG, "Privileged lane listening on @" + name);
    }

    public synchronized boolean isRunning() {
        return acceptThread != null && acceptThread.isAlive();
    }

    private void acceptLoop() {
        LocalServerSocket server = serverSocket;
        while (server != null) {
            LocalSocket socket;
            try {
                socket = server.accept();
            } catch (IOException e) {
                Log.w(TAG, "accept() failed; the privileged lane stops", e);
                return;
            }
            Thread worker = new Thread(() -> serve(socket), "priv-lane-conn-" + connectionCount.incrementAndGet());
            worker.setDaemon(true);
            worker.start();
        }
    }

    private void serve(@NonNull LocalSocket socket) {
        try {
            Credentials peer = socket.getPeerCredentials();
            if (peer == null || peer.getUid() != Process.myUid()) {
                Log.w(TAG, "Rejected a lane connection from uid " + (peer == null ? "?" : peer.getUid())
                    + " (pid " + (peer == null ? "?" : peer.getPid()) + ")");
                return;
            }
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            String line;
            try {
                line = readLine(in);
            } catch (LaneRequest.Refused e) {
                sendError(out, e.getMessage());
                return;
            }
            if (line == null) return;  // Connected and left without a word.

            LaneRequest request;
            LaneAllowlist.Resolved resolved;
            IPrivilegedLane lane;
            try {
                request = LaneRequest.parse(line);
                if (!PrivilegedPolicyStore.isLaneEnabled(appContext)) {
                    throw new LaneRequest.Refused("the privileged lane is turned off in Settings › Shizuku");
                }
                resolved = allowlist.resolve(request.path);
                lane = PrivilegedLaneBinding.getInstance().acquire(BIND_TIMEOUT_MS);
            } catch (LaneRequest.Refused | PrivilegedLaneBinding.Unavailable e) {
                sendError(out, e.getMessage());
                return;
            }

            String staged;
            try (ParcelFileDescriptor src = ParcelFileDescriptor.open(resolved.file, ParcelFileDescriptor.MODE_READ_ONLY)) {
                staged = lane.stage(src, resolved.name, resolved.digest);
            } catch (RemoteException | RuntimeException | IOException e) {
                sendError(out, "staging " + resolved.name + " failed: " + e.getMessage());
                return;
            }

            int[] pid = new int[1];
            ParcelFileDescriptor master;
            try {
                master = lane.spawn(staged, request.args.toArray(new String[0]),
                    new String[] { "TERM=" + request.term }, request.rows, request.cols, pid);
            } catch (RemoteException | RuntimeException e) {
                sendError(out, "starting " + resolved.name + " failed: " + e.getMessage());
                return;
            }
            if (master == null) {
                sendError(out, "starting " + resolved.name + " failed: no pty came back");
                return;
            }

            // One write carries the line and the descriptor (SCM_RIGHTS); afterwards this
            // process holds no master, so the client's close is the child's hangup.
            try {
                socket.setFileDescriptorsForSend(new FileDescriptor[] { master.getFileDescriptor() });
                out.write(("ok\t" + pid[0] + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } finally {
                closeQuietly(master);
            }
            Log.i(TAG, "Handed " + resolved.name + " (pid " + pid[0] + ") to the client");

            AtomicBoolean exited = new AtomicBoolean(false);
            Thread hangup = new Thread(() -> {
                // The client sends nothing after its request; a read that returns is its EOF.
                drain(in);
                if (!exited.get()) {
                    Log.i(TAG, "Client left before pid " + pid[0] + " exited; killing it");
                    try {
                        lane.kill(pid[0]);
                    } catch (RemoteException | RuntimeException e) {
                        Log.w(TAG, "kill(" + pid[0] + ") failed", e);
                    }
                }
            }, "priv-lane-eof-" + pid[0]);
            hangup.setDaemon(true);
            hangup.start();

            int code;
            try {
                code = lane.waitFor(pid[0]);
            } catch (RemoteException | RuntimeException e) {
                Log.w(TAG, "waitFor(" + pid[0] + ") failed; reporting 255", e);
                code = 255;
            }
            exited.set(true);
            try {
                out.write(("exit\t" + code + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException ignored) {
                // The client is already gone; the hangup thread dealt with the child.
            }
        } catch (IOException e) {
            Log.w(TAG, "Lane connection failed", e);
        } finally {
            closeQuietly(socket);
        }
    }

    /** One line, without its LF; null on EOF before any byte; refused when it is too long or unterminated. */
    @Nullable
    private static String readLine(@NonNull InputStream in) throws IOException, LaneRequest.Refused {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
        int b;
        while ((b = in.read()) >= 0) {
            if (b == '\n') return bytes.toString("UTF-8");
            bytes.write(b);
            if (bytes.size() > LaneRequest.MAX_LINE_BYTES) {
                throw new LaneRequest.Refused("request line longer than " + LaneRequest.MAX_LINE_BYTES + " bytes");
            }
        }
        if (bytes.size() == 0) return null;
        throw new LaneRequest.Refused("request line must end in a newline");
    }

    private static void sendError(@NonNull OutputStream out, @Nullable String message) throws IOException {
        String text = message == null ? "unknown error" : message.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
        Log.w(TAG, "Refused: " + text);
        out.write(("err\t" + text + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void drain(@NonNull InputStream in) {
        byte[] sink = new byte[256];
        try {
            //noinspection StatementWithEmptyBody
            while (in.read(sink) >= 0) {
            }
        } catch (IOException ignored) {
            // Closed from the other thread after exit, or by the client: either way it is over.
        }
    }

    private static void closeQuietly(@Nullable ParcelFileDescriptor pfd) {
        if (pfd == null) return;
        try {
            pfd.close();
        } catch (IOException ignored) {
        }
    }

    private static void closeQuietly(@NonNull LocalSocket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
