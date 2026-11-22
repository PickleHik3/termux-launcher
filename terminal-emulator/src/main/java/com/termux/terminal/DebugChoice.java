package com.termux.terminal;

/**
 * The string twin of {@link DebugSwitch}: a system property read at most once per
 * {@link DebugSwitch#REREAD_MS}, between reads a field read. An unset, empty or unreadable
 * property is the default.
 */
public final class DebugChoice {

    private final String mKey;
    private final String mDefault;
    private final DebugSwitch.Reader mReader;
    private volatile String mValue;
    private volatile long mReadAtMs = Long.MIN_VALUE;

    public DebugChoice(String key, String defaultValue, DebugSwitch.Reader reader) {
        mKey = key;
        mDefault = defaultValue;
        mValue = defaultValue;
        mReader = reader;
    }

    /** The property's value, or the default. */
    public String get(long nowMs) {
        if (mReadAtMs == Long.MIN_VALUE || nowMs - mReadAtMs >= DebugSwitch.REREAD_MS
            || nowMs < mReadAtMs) {
            mReadAtMs = nowMs;
            String v = mReader.read(mKey);
            mValue = v == null || v.isEmpty() ? mDefault : v;
        }
        return mValue;
    }
}
