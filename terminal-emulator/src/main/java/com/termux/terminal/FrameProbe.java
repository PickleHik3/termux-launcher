package com.termux.terminal;

import android.os.Build;
import android.os.Trace;

/**
 * Names the sources that ask for frames, for the idle audit: each tick of a {@link Source} sets a
 * {@code frames.<name>} counter in the system trace, so a trace taken at rest shows which counters
 * still climb. A tick with tracing off is a field increment and a flag test.
 */
public final class FrameProbe {

    private FrameProbe() {}

    public static Source source(String name) {
        return new Source("frames." + name);
    }

    public static final class Source {
        private final String mCounter;
        private long mCount;

        Source(String counter) {
            mCounter = counter;
        }

        /** One frame requested or drawn by this source. */
        public void tick() {
            mCount++;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.setCounter(mCounter, mCount);
        }
    }
}
