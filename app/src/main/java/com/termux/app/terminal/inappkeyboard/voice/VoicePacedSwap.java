package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;

/**
 * Writes the cleanup's in-place swap as keystrokes: every erase on its own write, a moment
 * apart, then the cleaned text in one go. A shell's line editor would take the erases as one
 * burst, but a TUI that parses stdin chunk by chunk (Ink-style prompts read a chunk as one
 * keypress, or as pasted text) would see a run of DEL bytes as one strange key; spaced like a
 * held backspace, each arrives as its own. Runs on its own thread; a 150-character dictation
 * takes about a third of a second to erase.
 */
public final class VoicePacedSwap implements Runnable {

    /** Where the keystrokes go (the terminal session's write). Called on the swap thread. */
    public interface Writer {
        void write(@NonNull String data);
    }

    /** Between two erases: past a TUI's read loop, well under a held backspace's repeat. */
    static final long ERASE_SPACING_MS = 2L;

    private final int erases;
    private final String replacement;
    private final Writer writer;
    private final Runnable done;
    private final long spacingMs;

    /** @param done runs on the swap thread once the replacement has been written */
    public VoicePacedSwap(int erases, @NonNull String replacement, @NonNull Writer writer, @NonNull Runnable done) {
        this(erases, replacement, writer, done, ERASE_SPACING_MS);
    }

    VoicePacedSwap(int erases, @NonNull String replacement, @NonNull Writer writer, @NonNull Runnable done, long spacingMs) {
        this.erases = erases;
        this.replacement = replacement;
        this.writer = writer;
        this.done = done;
        this.spacingMs = spacingMs;
    }

    /** Starts the swap on a {@code voice-swap} thread. */
    public void start() {
        Thread thread = new Thread(this, "voice-swap");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void run() {
        String erase = String.valueOf(VoiceTypedLine.ERASE);
        for (int i = 0; i < erases; i++) {
            writer.write(erase);
            if (spacingMs > 0) {
                try {
                    Thread.sleep(spacingMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (!replacement.isEmpty()) writer.write(replacement);
        done.run();
    }
}
