package com.termux.app.x11;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The display servers this app's uid runs, found and stopped from here rather than through the
 * prefix's {@code pkill}, which an install without procps (or a nix prefix) does not have.
 *
 * <p>Every server, the launcher's or one a shell started, is {@code termux-x11} — the launcher's
 * script in {@code $PREFIX/bin} — and that script names its process {@code termux-x11 <package>
 * <args>} ({@code X11CliInstaller.serverScript}). The name is what is matched, so Stop stops the
 * server on the display window whoever started it, the same as {@code pkill termux-x11} would. A
 * process of another uid is not visible here and could not be signalled anyway.
 */
public final class X11ServerProcesses {

    private static final String LOG_TAG = "X11ServerProcesses";

    private X11ServerProcesses() {}

    /**
     * Whether {@code cmdline} — {@code /proc/<pid>/cmdline} up to its first NUL — is a display
     * server of {@code packageName}: the nice name, then the end or a space. Another edition's
     * {@code com.termux.nix} is not {@code com.termux}'s.
     */
    public static boolean isServer(@Nullable String cmdline, @NonNull String packageName) {
        if (cmdline == null) return false;
        String prefix = "termux-x11 " + packageName;
        if (!cmdline.startsWith(prefix)) return false;
        return cmdline.length() == prefix.length() || cmdline.charAt(prefix.length()) == ' ';
    }

    /**
     * Whether {@code cmdline} (see {@link #isServer}) carries its own session: a {@code -xstartup}
     * argument, which is how the launcher starts one, or upstream's {@code --} separator before a
     * command ({@code termux-x11 :1 -- cmd}). A server with neither has nobody to start a window
     * manager for it.
     */
    public static boolean startsOwnSession(@Nullable String cmdline) {
        if (cmdline == null) return false;
        String padded = cmdline + " ";
        return padded.contains(" -xstartup ") || padded.contains(" -- ");
    }

    /**
     * Whether some server of {@code packageName} was started without a session of its own. File
     * work: call off the main thread.
     */
    public static boolean anyWithoutSession(@NonNull String packageName) {
        File[] entries = new File("/proc").listFiles();
        if (entries == null) return false;
        int self = android.os.Process.myPid();
        for (File entry : entries) {
            int pid = pidOf(entry.getName());
            if (pid <= 0 || pid == self) continue;
            String cmdline = readCmdline(new File(entry, "cmdline"));
            if (isServer(cmdline, packageName) && !startsOwnSession(cmdline)) return true;
        }
        return false;
    }

    /**
     * Which server of {@code packageName} is running, as {@code <pid>:<start time>}, or null when
     * none is. The start time (from {@code /proc/<pid>/stat}) keeps a reused pid from passing for
     * the server before it, so the answer names one server start and no other. Used to start the
     * display's Start with choice once per server ({@link DisplayStartWith}). File work: call off
     * the main thread.
     */
    @Nullable
    public static String runningServerIdentity(@NonNull String packageName) {
        File[] entries = new File("/proc").listFiles();
        if (entries == null) return null;
        int self = android.os.Process.myPid();
        for (File entry : entries) {
            int pid = pidOf(entry.getName());
            if (pid <= 0 || pid == self) continue;
            if (!isServer(readCmdline(new File(entry, "cmdline")), packageName)) continue;
            String startTime = startTimeOf(readCmdline(new File(entry, "stat")));
            return pid + ":" + (startTime == null ? "" : startTime);
        }
        return null;
    }

    /**
     * The start time, field 22, of a {@code /proc/<pid>/stat} line, or null when it cannot be read.
     * Fields are counted after the command name's closing parenthesis, which may itself hold
     * spaces. Pure.
     */
    @Nullable
    static String startTimeOf(@Nullable String stat) {
        if (stat == null) return null;
        int close = stat.lastIndexOf(')');
        if (close < 0) return null;
        String[] fields = stat.substring(close + 1).trim().split("\\s+");
        // Field 3 (the state) is the first after the name, so field 22 is index 19.
        return fields.length > 19 ? fields[19] : null;
    }

    /**
     * Ask every display server of {@code packageName} to stop (SIGTERM, what {@code pkill} sends).
     * File work: call off the main thread. Answers how many were signalled.
     */
    public static int stopAll(@NonNull String packageName) {
        File[] entries = new File("/proc").listFiles();
        if (entries == null) return 0;
        int self = android.os.Process.myPid();
        int stopped = 0;
        for (File entry : entries) {
            int pid = pidOf(entry.getName());
            if (pid <= 0 || pid == self) continue;
            if (!isServer(readCmdline(new File(entry, "cmdline")), packageName)) continue;
            try {
                Os.kill(pid, OsConstants.SIGTERM);
                stopped++;
            } catch (ErrnoException e) {
                Logger.logWarn(LOG_TAG, "Could not stop display server " + pid + ": " + e.getMessage());
            }
        }
        return stopped;
    }

    private static int pidOf(@NonNull String name) {
        if (name.isEmpty()) return -1;
        for (int i = 0; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) return -1;
        }
        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The command line up to its first NUL, or null when it cannot be read. */
    @Nullable
    private static String readCmdline(@NonNull File file) {
        byte[] buffer = new byte[1024];
        try (FileInputStream in = new FileInputStream(file)) {
            int read = in.read(buffer);
            if (read <= 0) return null;
            int end = 0;
            while (end < read && buffer[end] != 0) end++;
            return new String(buffer, 0, end, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException e) {
            return null;
        }
    }
}
