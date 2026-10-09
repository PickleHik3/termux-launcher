// The privileged lane's Shizuku user service: runs as the shell uid (2000) in a process Shizuku
// starts from this APK. The launcher process is the only client (PrivilegedLaneBinding).
package com.termux.privileged.lane;

import android.os.ParcelFileDescriptor;

interface IPrivilegedLane {
    // Shizuku calls this before it kills the service process; the transaction code is fixed by
    // the Shizuku server (FIRST_CALL_TRANSACTION + 16777114).
    void destroy() = 16777114;

    // Copies src to /data/local/tmp/tl/bin/<name>-<first 12 hex of sha256> unless a copy with
    // that digest is already there, drops older <name>-* copies, and returns the staged path.
    String stage(in ParcelFileDescriptor src, String name, String sha256) = 1;

    // Starts path inside a fresh pseudo-terminal of rows x cols as the shell uid and returns the
    // pty master; pidOut[0] receives the child's pid. extraEnv entries ("K=V") override the
    // service's fixed environment (HOME, PATH, LANG, TMPDIR, TERM). The service keeps no copy
    // of the master: when the caller closes the returned descriptor the child gets SIGHUP.
    ParcelFileDescriptor spawn(String path, in String[] args, in String[] extraEnv, int rows, int cols, out int[] pidOut) = 2;

    // Blocks until the child exits; returns its exit status, 128 + signal when it was killed.
    int waitFor(int pid) = 3;

    // SIGHUP, then SIGKILL a second later if the child is still there. A pid this service did
    // not spawn, or one that already exited, is ignored.
    void kill(int pid) = 4;

    // The uid the service runs as, for the settings screen's status row.
    int uid() = 5;
}
