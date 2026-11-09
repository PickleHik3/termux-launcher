package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where a graphics benchmark run has got to, read from the step lines the script prints:
 * {@code == Step 3 of 8: Installing the test program and drivers ==}. Pure, so the format the page
 * and the progress notification rely on is tested against the script's own.
 */
public final class X11GpuBenchmarkStep {
    private static final Pattern LINE =
        Pattern.compile("^\\s*== Step (\\d{1,3}) of (\\d{1,3}): (.*?) ==\\s*$", Pattern.MULTILINE);

    public final int number;
    public final int total;
    @NonNull public final String text;

    X11GpuBenchmarkStep(int number, int total, @NonNull String text) {
        this.number = number;
        this.total = total;
        this.text = text;
    }

    /** The last step line in {@code output}, or null when it holds none. */
    @Nullable
    public static X11GpuBenchmarkStep lastIn(@Nullable CharSequence output) {
        if (output == null || output.length() == 0) return null;
        Matcher matcher = LINE.matcher(output);
        X11GpuBenchmarkStep last = null;
        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group(1));
            int total = Integer.parseInt(matcher.group(2));
            String text = matcher.group(3).trim();
            if (number < 1 || total < number || text.isEmpty()) continue;
            last = new X11GpuBenchmarkStep(number, total, text);
        }
        return last;
    }

    /** Whether this is the same step as {@code other}. */
    public boolean sameAs(@Nullable X11GpuBenchmarkStep other) {
        return other != null && other.number == number && other.total == total && other.text.equals(text);
    }
}
