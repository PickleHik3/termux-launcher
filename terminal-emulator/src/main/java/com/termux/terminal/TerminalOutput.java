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
     * Notify the terminal client that a bell character (ASCII 7, bell, BEL, \a, ^G)) has been received.
     */
    public abstract void onBell();

    public abstract void onColorsChanged();

    /**
     * Return work produced off-thread to the terminal's serialized update thread. Test outputs that do not own a
     * looper may use this default; a live {@link TerminalSession} overrides it and posts to its main-thread handler.
     */
    public void postTerminalUpdate(Runnable update) {
        update.run();
    }

    /**
     * Run work on the terminal's serialized update thread after a delay, used to drive
     * terminal-side kitty graphics animation. The default drops the request — an environment
     * without a looper has no way to wait, and running it synchronously would spin the animation
     * scheduler — so tests drive frame advancement explicitly instead.
     */
    public void postTerminalUpdateDelayed(Runnable update, long delayMillis) {
    }
}
