package com.termux.terminal;

/**
 * A runtime switch read from a system property ({@code adb shell setprop debug.tl.echo_inline 0})
 * so before and after can be measured on one build without a UI. The property is re-read at most
 * once per {@link #REREAD_MS}; between reads {@link #isOn} is a field read.
 */
public final class DebugSwitch {

    public static final long REREAD_MS = 1000L;

    /** Where the value comes from; a lambda in tests. */
    public interface Reader {
        /** The property's value, or null when unset or unreadable. */
        String read(String key);
    }

    /** {@code android.os.SystemProperties.get}, reached by reflection as it is not public API. */
    public static final Reader SYSTEM_PROPERTIES = new Reader() {
        private java.lang.reflect.Method mGet;
        private boolean mFailed;

        @Override public String read(String key) {
            if (mFailed) return null;
            try {
                if (mGet == null) {
                    mGet = Class.forName("android.os.SystemProperties")
                        .getMethod("get", String.class, String.class);
                }
                return (String) mGet.invoke(null, key, "");
            } catch (Throwable t) {
                mFailed = true;
                return null;
            }
        }
    };

    private final String mKey;
    private final boolean mDefault;
    private final Reader mReader;
    private volatile boolean mValue;
    private volatile long mReadAtMs = Long.MIN_VALUE;

    public DebugSwitch(String key, boolean defaultOn, Reader reader) {
        mKey = key;
        mDefault = defaultOn;
        mValue = defaultOn;
        mReader = reader;
    }

    /** "1" is on, "0" is off, anything else (unset included) is the default. */
    public boolean isOn(long nowMs) {
        if (mReadAtMs == Long.MIN_VALUE || nowMs - mReadAtMs >= REREAD_MS || nowMs < mReadAtMs) {
            mReadAtMs = nowMs;
            String v = mReader.read(mKey);
            mValue = "1".equals(v) ? true : "0".equals(v) ? false : mDefault;
        }
        return mValue;
    }
}
