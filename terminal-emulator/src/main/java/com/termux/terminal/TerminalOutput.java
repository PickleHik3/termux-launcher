package com.termux.terminal;

import java.nio.charset.StandardCharsets;

/**
 * A client which receives callbacks from events triggered by feeding input to a {@link TerminalEmulator}.
 */
public abstract class TerminalOutput {

    /**
     * Write a string using the UTF-8 encoding to the terminal client.
     */
    public final void write(String data) {
        if (data == null)
            return;
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        write(bytes, 0, bytes.length);
    }

    /**
     * Write bytes to the terminal client.
     */
    public abstract void write(byte[] data, int offset, int count);

    /**
     * Notify the terminal client that the terminal title has changed.
     */
    public abstract void titleChanged(String oldTitle, String newTitle);

    /**
     * Notify the terminal client that text should be copied to clipboard.
     */
    public abstract void onCopyTextToClipboard(String text);

    /**
     * Notify the terminal client that text should be pasted from clipboard.
     */
    public abstract void onPasteTextFromClipboard();

    /**
     * Ask the terminal client for the text on the clipboard, for an OSC 52 query
     * ({@code ESC ] 52 ; c ; ? BEL}). The default has no client to ask, so it answers as if the
     * clipboard were empty rather than leaving the query unanswered.
     */
    public String onReadTextFromClipboard() {
        return null;
    }

    /**
     * Notify the terminal client that a bell character (ASCII 7, bell, BEL, \a, ^G)) has been received.
     */
    public abstract void onBell();

    public abstract void onColorsChanged();

    /**
     * Notify the terminal client that the program asked for the user's attention with a message —
     * an iTerm2-style {@code OSC 9 ; body} or an rxvt-style {@code OSC 777 ; notify ; title ; body}.
     * Agents emit these when a turn ends or an approval is needed. The default drops it.
     */
    public void onNotification(String title, String body) {
    }

    /**
     * Notify the terminal client of a whole desktop-notification request — {@code OSC 99} — with
     * everything the program said about it: a name to close or replace it by, how urgent it is,
     * whether it wants to be told the user tapped it.
     *
     * <p>The default passes the words along the older, plainer path, so a client that only knows
     * how to show a title and a body keeps working.
     */
    public void onKittyNotification(KittyNotification notification) {
        onNotification(notification.getTitle(), notification.getBody());
    }

    /** Take down the notification the program named, if it is still up. */
    public void onKittyNotificationClose(String id) {
    }

    /**
     * The running program asked for a mouse pointer shape — {@code OSC 22} — or, with null, for
     * the terminal's own again. The default has no pointer to change.
     */
    public void onPointerShapeChanged(String shape) {
    }

    /**
     * Ask the terminal client to redraw, for a change to what is on screen that it has no other
     * way to notice — the pixels behind a kitty animation's cells, which move without a single
     * cell or byte of the screen buffer changing. Everything the emulator parses already reaches
     * the client through the screen-update path; this is the one thing that does not. The default
     * has no client to tell.
     */
    public void onScreenChanged() {
    }

    /**
     * Return work produced off-thread to the terminal's serialized update thread. Test outputs that do not own a
     * looper may use this default; a live {@link TerminalSession} overrides it and posts to its main-thread handler.
     */
    public void postTerminalUpdate(Runnable update) {
        update.run();
    }

    /**
     * Run work on the terminal's serialized update thread after a delay, used to drive
     * terminal-side kitty graphics animation. The delayed work asks for a redraw itself, through
     * {@link #onScreenChanged}, if it changed anything visible — the animation scheduler wakes at
     * the next frame deadline, and a tick that finds nothing to flip must not cost a frame. The
     * default drops the request — an environment without a looper has no way to wait, and running
     * it synchronously would spin the animation scheduler — so tests drive frame advancement
     * explicitly instead.
     */
    public void postTerminalUpdateDelayed(Runnable update, long delayMillis) {
    }

    /**
     * Withdraw a pending {@link #postTerminalUpdateDelayed} of exactly this runnable. A delayed
     * animation tick keeps its whole terminal — emulator, buffers, every stored frame — alive on
     * the looper queue and re-arms itself when it runs, so suspending or tearing one down has to
     * be able to take it back off the queue. The default has nothing scheduled to withdraw.
     */
    public void cancelTerminalUpdateDelayed(Runnable update) {
    }
}
