package com.termux.privileged.lane;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.termux.terminal.TerminalPty;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * The lane's Shizuku user service: the half that runs as the shell uid. Shizuku starts a process
 * from this APK ({@code app_process} with the APK on the class path, in the shell domain),
 * instantiates this class there by name and hands its binder back to the launcher
 * ({@link PrivilegedLaneBinding}). Everything here therefore executes as uid 2000, with the
 * launcher's own files out of reach: a binary arrives as a descriptor the launcher opened
 * ({@link #stage}), is copied under {@code /data/local/tmp/tl}, and is started on a pty
 * allocated in this process ({@link #spawn}).
 *
 * <p>The pty comes from libtermux — the same {@code createSubprocess} every terminal session
 * uses — loaded here through {@link TerminalPty}. The user service's class loader is the one a
 * package context gives, which knows the APK's native library directory, so the library resolves
 * the same way it does in the launcher.
 *
 * <p>Nothing in the app references the constructors: {@code proguard-rules.pro} keeps the class
 * whole, and {@link Keep} says so at the source.
 */
@Keep
public final class PrivilegedLaneService extends IPrivilegedLane.Stub {

    private static final String TAG = "PrivilegedLaneService";

    /** Bump when the service's behaviour changes: Shizuku restarts a bound service whose version differs. */
    public static final int VERSION = 1;

    static final String ROOT_DIR = "/data/local/tmp/tl";
    static final String BIN_DIR = ROOT_DIR + "/bin";
    static final String HOME_DIR = ROOT_DIR + "/home";
    static final int DIGEST_PREFIX_LENGTH = 12;

    private static final String DEFAULT_TERM = LaneRequest.DEFAULT_TERM;
    private static final long KILL_GRACE_MS = 1_000L;

    /** A child this service started, until its reaper thread has collected it. */
    private static final class Child {
        final int pid;
        final CountDownLatch exited = new CountDownLatch(1);
        volatile int exitCode = -1;

        Child(int pid) {
            this.pid = pid;
        }
    }

    /** A staged file whose digest was verified, with the mtime and size it had then. */
    private static final class Verified {
        final long lastModified;
        final long length;

        Verified(long lastModified, long length) {
            this.lastModified = lastModified;
            this.length = length;
        }
    }

    private final Map<Integer, Child> children = new HashMap<>();
    private final Map<String, Verified> verified = new HashMap<>();

    /** Shizuku 13 prefers this constructor when it exists; the context is a package context for this APK. */
    public PrivilegedLaneService(@SuppressWarnings("unused") Context context) {
        this();
    }

    public PrivilegedLaneService() {
        Log.i(TAG, "Privileged lane service up as uid " + Process.myUid() + ", pid " + Process.myPid());
    }

    @Override
    public void destroy() {
        Log.i(TAG, "Shizuku asked the lane service to exit");
        System.exit(0);
    }

    @Override
    public int uid() {
        return Process.myUid();
    }

    @Override
    public String stage(ParcelFileDescriptor src, String name, String sha256) {
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(src)) {
            if (name == null || !isPlainName(name)) {
                throw new IllegalArgumentException("refusing to stage a binary named '" + name + "'");
            }
            String digest = sha256 == null ? "" : sha256.toLowerCase(java.util.Locale.ROOT);
            if (!PrivilegedCatalog.isHexDigest(digest)) {
                throw new IllegalArgumentException("stage() wants a hex sha256, not '" + sha256 + "'");
            }
            File bin = new File(BIN_DIR);
            ensurePrivateDir(bin);
            File dest = new File(bin, name + "-" + digest.substring(0, DIGEST_PREFIX_LENGTH));
            if (!isAlreadyStaged(dest, digest)) {
                copyInto(in, dest, digest);
                Log.i(TAG, "Staged " + dest);
            }
            // Executable no matter who wrote it or with what umask; also what makes a fresh copy runnable.
            Os.chmod(dest.getPath(), 0755);
            removeOlderCopies(bin, name, dest);
            return dest.getPath();
        } catch (IOException | ErrnoException e) {
            throw new IllegalStateException("staging " + name + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * Creates {@code dir} and makes it and every parent up to {@link #ROOT_DIR} mode 0700. The
     * service's umask is 0, so a plain mkdirs() leaves them world-writable, and a staged binary
     * any uid could swap would then run as shell.
     */
    private static void ensurePrivateDir(@NonNull File dir) throws ErrnoException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalStateException("cannot create " + dir);
        }
        for (File d = dir; d != null && d.getPath().startsWith(ROOT_DIR); d = d.getParentFile()) {
            Os.chmod(d.getPath(), 0700);
        }
    }

    @Override
    public ParcelFileDescriptor spawn(String path, String[] args, String[] extraEnv, int rows, int cols, int[] pidOut) {
        if (pidOut == null || pidOut.length < 1) throw new IllegalArgumentException("pidOut must hold one int");
        File binary = new File(path == null ? "" : path);
        // Only what stage() put here runs: this is the shell side's own check, independent of
        // the launcher's allowlist, so a confused launcher cannot point it at /system/bin/sh.
        if (!BIN_DIR.equals(binary.getParent()) || !binary.isFile()) {
            throw new IllegalArgumentException(binary + " is not a staged binary");
        }
        String name = nameOf(binary.getName());
        File home = new File(HOME_DIR, name);
        try {
            ensurePrivateDir(home);
        } catch (ErrnoException e) {
            throw new IllegalStateException("cannot make " + home + " private: " + e.getMessage(), e);
        }

        Map<String, String> env = new LinkedHashMap<>();
        env.put("HOME", home.getPath());
        env.put("TERM", DEFAULT_TERM);
        env.put("LANG", "C.UTF-8");
        env.put("PATH", "/system/bin:/system/xbin");
        env.put("TMPDIR", "/data/local/tmp");
        if (extraEnv != null) {
            for (String entry : extraEnv) {
                int eq = entry == null ? -1 : entry.indexOf('=');
                if (eq > 0) env.put(entry.substring(0, eq), entry.substring(eq + 1));
            }
        }
        List<String> envList = new ArrayList<>(env.size());
        for (Map.Entry<String, String> entry : env.entrySet()) {
            envList.add(entry.getKey() + "=" + entry.getValue());
        }

        List<String> argv = new ArrayList<>();
        argv.add(binary.getPath());
        if (args != null) argv.addAll(java.util.Arrays.asList(args));

        int[] pid = new int[1];
        int master;
        try {
            master = TerminalPty.createSubprocess(binary.getPath(), home.getPath(),
                argv.toArray(new String[0]), envList.toArray(new String[0]), pid,
                Math.max(1, rows), Math.max(1, cols));
        } catch (UnsatisfiedLinkError e) {
            throw new IllegalStateException("libtermux did not load in the shell-uid process: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("cannot start " + name + ": " + e.getMessage(), e);
        }

        Child child = new Child(pid[0]);
        synchronized (children) {
            children.put(child.pid, child);
        }
        Thread reaper = new Thread(() -> reap(child), "priv-lane-reap-" + child.pid);
        reaper.setDaemon(true);
        reaper.start();
        pidOut[0] = child.pid;
        Log.i(TAG, "Spawned " + name + " as pid " + child.pid + " on a " + rows + "x" + cols + " pty");

        // adoptFd hands the master to the ParcelFileDescriptor; the AIDL stub writes the reply
        // with PARCELABLE_WRITE_RETURN_VALUE, which closes this side's copy once it is sent.
        // From then on the launcher and, after it, the client hold the only masters: closing
        // the last one hangs up the child's terminal, which is what ends btop on Ctrl-D or a
        // dropped session.
        return ParcelFileDescriptor.adoptFd(master);
    }

    @Override
    public int waitFor(int pid) {
        Child child;
        synchronized (children) {
            child = children.get(pid);
        }
        if (child == null) throw new IllegalArgumentException("pid " + pid + " is not a lane child");
        try {
            child.exited.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for pid " + pid);
        }
        return child.exitCode;
    }

    @Override
    public void kill(int pid) {
        Child child;
        synchronized (children) {
            child = children.get(pid);
        }
        if (child == null || child.exited.getCount() == 0) return;
        signal(child, OsConstants.SIGHUP);
        Thread escalate = new Thread(() -> {
            try {
                if (!child.exited.await(KILL_GRACE_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    signal(child, OsConstants.SIGKILL);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "priv-lane-kill-" + pid);
        escalate.setDaemon(true);
        escalate.start();
    }

    private void reap(@NonNull Child child) {
        int status = TerminalPty.waitFor(child.pid);
        // waitFor negates the signal for a killed child; report it the way a shell would.
        child.exitCode = status >= 0 ? status : 128 - status;
        synchronized (children) {
            children.remove(child.pid);
        }
        child.exited.countDown();
        Log.i(TAG, "pid " + child.pid + " exited with " + child.exitCode);
    }

    private static void signal(@NonNull Child child, int signal) {
        if (child.exited.getCount() == 0) return;
        try {
            Os.kill(child.pid, signal);
        } catch (ErrnoException e) {
            // ESRCH: it went between the check and the kill. Anything else is worth a line.
            if (e.errno != OsConstants.ESRCH) Log.w(TAG, "kill(" + child.pid + ", " + signal + ") failed", e);
        }
    }

    /** True when dest already holds bytes with this digest; re-hashes only when the file changed since last verified. */
    private boolean isAlreadyStaged(@NonNull File dest, @NonNull String digest) throws IOException {
        if (!dest.isFile()) return false;
        long lastModified = dest.lastModified();
        long length = dest.length();
        synchronized (verified) {
            Verified seen = verified.get(dest.getPath());
            if (seen != null && seen.lastModified == lastModified && seen.length == length) return true;
        }
        if (!LaneAllowlist.sha256(dest).equals(digest)) return false;
        synchronized (verified) {
            verified.put(dest.getPath(), new Verified(lastModified, length));
        }
        return true;
    }

    /** Writes in to a sibling temp file, fsyncs, checks the digest and moves it over dest. */
    private void copyInto(@NonNull InputStream in, @NonNull File dest, @NonNull String digest) throws IOException {
        File tmp = new File(dest.getParentFile(), "." + dest.getName() + ".tmp-" + Process.myPid());
        try {
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
                out.getFD().sync();
            }
            String actual = LaneAllowlist.sha256(tmp);
            if (!actual.equals(digest)) {
                throw new IOException("the copy's sha256 is " + actual + ", expected " + digest);
            }
            Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            synchronized (verified) {
                verified.put(dest.getPath(), new Verified(dest.lastModified(), dest.length()));
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    /** Drops every other {@code <name>-*} in bin: one staged copy per tool, the current one. */
    private void removeOlderCopies(@NonNull File bin, @NonNull String name, @NonNull File keep) {
        File[] siblings = bin.listFiles();
        if (siblings == null) return;
        for (File sibling : siblings) {
            if (sibling.equals(keep) || !sibling.isFile()) continue;
            if (!name.equals(nameOf(sibling.getName()))) continue;
            synchronized (verified) {
                verified.remove(sibling.getPath());
            }
            if (sibling.delete()) Log.i(TAG, "Removed older copy " + sibling);
        }
    }

    /** {@code <name>-<12 hex>} back to {@code <name>}; anything else is returned whole. */
    @NonNull
    static String nameOf(@NonNull String fileName) {
        int dash = fileName.lastIndexOf('-');
        if (dash <= 0 || fileName.length() - dash - 1 != DIGEST_PREFIX_LENGTH) return fileName;
        return fileName.substring(0, dash);
    }

    /** A catalog name: letters, digits and {@code . _ + -}; nothing that could leave the bin directory. */
    static boolean isPlainName(@NonNull String name) {
        if (name.isEmpty() || name.length() > 64 || name.startsWith(".")) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '.' || c == '_' || c == '+' || c == '-';
            if (!ok) return false;
        }
        return true;
    }
}
