package com.termux.privileged.lane;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.termux.privileged.PrivilegedBackendManager;
import com.termux.terminal.TerminalPty;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The lane for a rooted phone without Shizuku. It lives in the launcher process (calls are
 * direct), stages binaries with root file operations only, and starts every tool as uid 2000
 * through {@code su 2000 -c}, on a pty the launcher allocates. Nothing here runs a tool as
 * uid 0. Closing the pty master hangs up the su client, and su's own pty relay passes the
 * hang-up on to the tool.
 */
final class RootLane extends IPrivilegedLane.Stub {

    private static final String TAG = "RootLane";
    private static final long KILL_GRACE_MS = 1_000L;
    private static final long STAGE_TIMEOUT_S = 60L;

    /** A su client this lane started, until its reaper thread has collected it. */
    private static final class Child {
        final int pid;
        final CountDownLatch exited = new CountDownLatch(1);
        volatile int exitCode = -1;

        Child(int pid) {
            this.pid = pid;
        }
    }

    @NonNull private final Context context;
    private final Map<Integer, Child> children = new HashMap<>();

    RootLane(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public int uid() {
        return RootLaneCommands.SHELL_UID;
    }

    /** No injection from here: the shell's input service is not reachable without its uid's binder. */
    @Override
    public boolean injectMotion(MotionEvent event, int displayId) {
        if (event != null) event.recycle();
        return false;
    }

    @Override
    public void destroy() {
        List<Child> live;
        synchronized (children) {
            live = new ArrayList<>(children.values());
        }
        for (Child child : live) signal(child, OsConstants.SIGKILL);
    }

    @Override
    public String stage(ParcelFileDescriptor src, String name, String sha256) {
        File temp = null;
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(src)) {
            if (name == null || !PrivilegedLaneService.isPlainName(name)) {
                throw new IllegalArgumentException("refusing to stage a binary named '" + name + "'");
            }
            String digest = sha256 == null ? "" : sha256.toLowerCase(Locale.ROOT);
            if (!PrivilegedCatalog.isHexDigest(digest)) {
                throw new IllegalArgumentException("stage() wants a hex sha256, not '" + sha256 + "'");
            }
            temp = new File(context.getCacheDir(), "lane-stage-" + System.nanoTime() + ".tmp");
            String actual = copyAndHash(in, temp);
            if (!actual.equals(digest)) {
                throw new IOException("the copy's sha256 is " + actual + ", expected " + digest);
            }
            String output = PrivilegedBackendManager.getInstance()
                .executeCommand(RootLaneCommands.stageScript(temp.getPath(), name, digest))
                .get(STAGE_TIMEOUT_S, TimeUnit.SECONDS);
            if (!RootLaneCommands.stagedOk(output)) {
                throw new IOException(output == null || output.trim().isEmpty()
                    ? "the root command failed" : output.trim());
            }
            Log.i(TAG, "Staged " + name);
            return RootLaneCommands.stagedPath(name, digest);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("staging " + name + " was interrupted", e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("staging " + name + " failed: " + e.getMessage(), e);
        } finally {
            if (temp != null) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        }
    }

    @NonNull
    private static String copyAndHash(@NonNull InputStream in, @NonNull File out) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (FileOutputStream stream = new FileOutputStream(out)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                md.update(buffer, 0, read);
                stream.write(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest()) hex.append(String.format(Locale.ROOT, "%02x", b));
        return hex.toString();
    }

    @Override
    public ParcelFileDescriptor spawn(String path, String[] args, String[] extraEnv, int rows, int cols, int[] pidOut) {
        if (pidOut == null || pidOut.length < 1) throw new IllegalArgumentException("pidOut must hold one int");
        File binary = new File(path == null ? "" : path);
        // The same shell-side check as the service. The tree is 0700 and owned by uid 2000, so
        // the launcher cannot stat the file; the script's exec fails loudly when it is missing.
        if (!PrivilegedLaneService.BIN_DIR.equals(binary.getParent())) {
            throw new IllegalArgumentException(binary + " is not a staged binary");
        }
        String[] argv = RootLaneCommands.spawnArgv(binary.getPath(), args, extraEnv);
        // su's own environment: nothing of the launcher's.
        String[] env = {"PATH=/system/bin:/system/xbin"};
        int[] pid = new int[1];
        int master;
        try {
            master = TerminalPty.createSubprocess("su", context.getFilesDir().getPath(), argv, env, pid,
                Math.max(1, rows), Math.max(1, cols));
        } catch (RuntimeException e) {
            throw new IllegalStateException("cannot start su: " + e.getMessage(), e);
        }
        Child child = new Child(pid[0]);
        synchronized (children) {
            children.put(child.pid, child);
        }
        Thread reaper = new Thread(() -> reap(child), "root-lane-reap-" + child.pid);
        reaper.setDaemon(true);
        reaper.start();
        pidOut[0] = child.pid;
        Log.i(TAG, "Spawned su client pid " + child.pid + " for " + binary.getName());
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
        // The su client runs as the launcher's uid, so the launcher may signal it.
        signal(child, OsConstants.SIGHUP);
        Thread escalate = new Thread(() -> {
            try {
                if (!child.exited.await(KILL_GRACE_MS, TimeUnit.MILLISECONDS)) {
                    signal(child, OsConstants.SIGKILL);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "root-lane-kill-" + pid);
        escalate.setDaemon(true);
        escalate.start();
    }

    private void reap(@NonNull Child child) {
        int status = TerminalPty.waitFor(child.pid);
        child.exitCode = status >= 0 ? status : 128 - status;
        synchronized (children) {
            children.remove(child.pid);
        }
        child.exited.countDown();
        Log.i(TAG, "su client " + child.pid + " exited with " + child.exitCode);
    }

    private static void signal(@NonNull Child child, int signal) {
        if (child.exited.getCount() == 0) return;
        try {
            Os.kill(child.pid, signal);
        } catch (ErrnoException e) {
            if (e.errno != OsConstants.ESRCH) Log.w(TAG, "kill(" + child.pid + ", " + signal + ") failed", e);
        }
    }
}
