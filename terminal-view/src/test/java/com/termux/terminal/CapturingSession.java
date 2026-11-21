package com.termux.terminal;

import java.io.ByteArrayOutputStream;

/**
 * A session with no shell behind it: it keeps every byte written to it, and its emulator is the one
 * made by {@link #attach}. The emulator's own output (replies, key bytes the emulator encodes)
 * lands in the same capture, as it does in a live session.
 */
public final class CapturingSession extends TerminalSession {

    private final ByteArrayOutputStream mCaptured = new ByteArrayOutputStream();

    public CapturingSession() {
        super("/bin/sh", "/", new String[0], new String[0], 100, quietClient());
    }

    private static TerminalSessionClient quietClient() {
        return (TerminalSessionClient) java.lang.reflect.Proxy.newProxyInstance(
            TerminalSessionClient.class.getClassLoader(), new Class<?>[] { TerminalSessionClient.class },
            (proxy, method, args) -> {
                Class<?> type = method.getReturnType();
                if (type == boolean.class) return false;
                if (type == int.class) return 0;
                if (type == float.class) return 0f;
                return null;
            });
    }

    /** A new emulator whose output is this session. */
    public TerminalEmulator attach(int columns, int rows) {
        mEmulator = new TerminalEmulator(this, false, columns, rows, 10, 20, 100, null);
        return mEmulator;
    }

    @Override
    public void write(byte[] data, int offset, int count) {
        mCaptured.write(data, offset, count);
    }

    /** Everything written since the last call, as ISO-8859-1 (one char per byte). */
    public String drain() {
        String s = new String(mCaptured.toByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1);
        mCaptured.reset();
        return s;
    }

    /** What the emulator reads as the input property when its Fast core attaches; null restores the system property. */
    public static void inputPropertyForTests(String value) {
        TerminalEmulator.setInputPropertyReaderForTests(value == null ? null : name -> value);
    }
}
