package com.termux.app.testing;

import org.junit.Assume;

/**
 * Gate for tests whose only job is to write pictures for a person to look at. They stay skipped
 * in an ordinary run and run with {@code -Preports.renders} (forwarded by app/build.gradle as the
 * system property {@code reports.renders}). Call {@link #assumeRequested()} first in the method,
 * or in a {@code @Before} for a whole class.
 */
public final class ReportRenders {

    private ReportRenders() {}

    public static void assumeRequested() {
        Assume.assumeTrue("picture reports run with -Preports.renders",
            Boolean.getBoolean("reports.renders"));
    }
}
