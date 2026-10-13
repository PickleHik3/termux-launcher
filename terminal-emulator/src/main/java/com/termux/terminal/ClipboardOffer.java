package com.termux.terminal;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * What the clipboard holds at the moment a program reads it with {@code OSC 5522}: its text, and
 * the other MIME types (pictures) it can be had in. The terminal around the emulator builds one;
 * {@link KittyClipboard} decides from it what a read is answered with.
 */
public interface ClipboardOffer {

    /** The clipboard's text; the empty string when it holds none. */
    String text();

    /** The MIME types on offer besides text, lower-cased, best first; empty for a text clip. */
    List<String> types();

    /**
     * The bytes of one of {@link #types()}. This may block — reading another app's file,
     * re-encoding a picture — so it is only called on the thread the read's
     * {@link KittyClipboard.ReadCallback} ran on, which the terminal keeps off its main thread.
     *
     * @throws TooLargeException when the data is over the read limit
     * @throws IOException when it cannot be read
     */
    byte[] data(String mime) throws IOException;

    /** The data asked for is larger than a read may carry. */
    final class TooLargeException extends IOException {
        public TooLargeException(String message) {
            super(message);
        }
    }

    /** A clipboard of text alone; {@code ""} for an empty but readable one. */
    static ClipboardOffer ofText(String text) {
        String value = text == null ? "" : text;
        return new ClipboardOffer() {
            @Override public String text() {
                return value;
            }

            @Override public List<String> types() {
                return Collections.emptyList();
            }

            @Override public byte[] data(String mime) throws IOException {
                throw new IOException("no " + mime + " on a text clipboard");
            }
        };
    }
}
