package com.termux.launcherctl;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hides one-time codes in notification text before it is written to the history.
 *
 * <p>The history sits inside the shell's trust domain, so a verification code that outlived its
 * notification would be readable by anything running as the app UID. This masks a 4-8 digit run
 * only when a word like code, OTP, verification, passcode, one-time or PIN stands within
 * {@link #NEAR} characters of it. Deliberately conservative: an order number, an amount or a time
 * with no such word beside it is left alone, because a history full of {@code ••••••} is no use to
 * the agent reading it. Codes written as {@code 123 456} or {@code 123-456} count as one code.
 */
public final class LauncherCtlNotificationMasker {
    public static final String MASK = "••••••";

    /** How far, in characters, the keyword may sit from the digits, on either side. */
    static final int NEAR = 40;

    private static final Pattern KEYWORD = Pattern.compile(
        "(?i)(?<![a-z])(codes?|otp|verification|passcode|one[- ]time|pin)(?![a-z])");

    /**
     * Digits standing alone: not inside a longer number, a word, a decimal or a time. The split
     * form is tried first so {@code 123 456} is not read as two three-digit runs.
     */
    private static final Pattern CODE = Pattern.compile(
        "(?<![\\dA-Za-z])(?<!\\d[.,:])(?:\\d{3}[ -]\\d{3}|\\d{4,8})(?![\\dA-Za-z])(?![.,:]\\d)");

    private LauncherCtlNotificationMasker() {
    }

    @Nullable
    public static String mask(@Nullable String text) {
        if (text == null || text.length() < 4) return text;
        Matcher matcher = CODE.matcher(text);
        StringBuilder out = null;
        int copied = 0;
        while (matcher.find()) {
            if (!hasKeywordNear(text, matcher.start(), matcher.end())) continue;
            if (out == null) out = new StringBuilder(text.length());
            out.append(text, copied, matcher.start()).append(MASK);
            copied = matcher.end();
        }
        if (out == null) return text;
        return out.append(text, copied, text.length()).toString();
    }

    private static boolean hasKeywordNear(String text, int start, int end) {
        int from = Math.max(0, start - NEAR);
        int to = Math.min(text.length(), end + NEAR);
        return KEYWORD.matcher(text.substring(from, to)).find();
    }
}
