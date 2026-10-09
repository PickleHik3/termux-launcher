package com.termux.app.statusbar;

import androidx.annotation.Nullable;

/**
 * Whether a shell has a command actively working in it this instant — an agent thinking, a build
 * compiling — as opposed to sitting at a prompt or waiting for a key. The window pill turns its
 * ring on this answer, and {@link ShellPhaseTracker} adds the memory (the grace after a quiet
 * spell, and what the quiet turned out to mean).
 *
 * <p>Two signals, either of which counts once the foreground is not the shell itself: the
 * foreground process group's CPU use between the resolver's polls, and sustained output. Output
 * counts because a command waiting on the network burns no CPU while its progress keeps printing;
 * it is gated by the foreground being a command rather than the shell (which rules out the echo)
 * and by the input grace (which rules out the keystrokes themselves).
 *
 * <p>A full-screen program is the exception: on the alternate screen only measured CPU counts. An
 * interactive program sitting idle — a multiplexer, an editor, ssh into a remote tmux — repaints
 * its status line or clock every second, and counting that output kept the ring turning (and the
 * pill redrawing thirty times a second) for as long as it stayed open. Agents are not affected:
 * known ones answer for their own pane through their title, hook and screen rules.
 *
 * <p>Input silences the indication outright: while the user is typing, the pane is being
 * interacted with, not working in the background. A CPU reading whose interval the user typed in
 * is discounted ({@link WindowForegroundResolver.ForegroundInfo#isWorkingAsOf(long, long)}).
 */
public final class ShellWorkingPolicy {

    private ShellWorkingPolicy() {
    }

    /**
     * @param nowMs              the instant being judged, on the uptime clock
     * @param lastWriteMs        when the user last wrote to the pane; {@code 0} or less when never
     * @param inputGraceMs       how long after a keystroke the pane counts as being interacted with
     * @param info               the latest foreground reading, or {@code null} when none is available
     *                           (no privileged backend, an unreadable procfs)
     * @param fullScreen         whether the pane is on the alternate screen buffer
     * @param outputBurstWorking whether {@link ShellActivityTracker} holds a sustained output burst
     */
    public static boolean isWorking(long nowMs, long lastWriteMs, long inputGraceMs,
                                    @Nullable WindowForegroundResolver.ForegroundInfo info,
                                    boolean fullScreen, boolean outputBurstWorking) {
        if (lastWriteMs > 0L && nowMs - lastWriteMs < inputGraceMs) return false;
        if (info != null && info.idle) return false;          // the shell itself has the terminal
        if (info != null && info.isWorkingAsOf(nowMs, lastWriteMs)) return true;
        if (fullScreen) return false;
        return outputBurstWorking;
    }
}
