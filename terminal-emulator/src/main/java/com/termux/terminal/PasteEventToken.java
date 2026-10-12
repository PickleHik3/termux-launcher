package com.termux.terminal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * The password a paste event hands the program ({@code pw=} on kitty's {@code OSC 5522} paste
 * event): the user's paste is their consent to that one read, so a read that brings the password
 * back is answered without "Let programs read the clipboard". It is random, good for a single
 * read, and gone after {@link #LIFETIME_MILLIS}; a newer paste replaces it.
 *
 * <p>Pure: the caller passes the clock, so expiry is testable.
 */
final class PasteEventToken {

    /** How long a program has to read the paste it was told about. */
    static final long LIFETIME_MILLIS = 60_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** The outstanding password, or null while there is none. */
    private String mToken;

    /** When {@link #mToken} stops being accepted, on the caller's clock. */
    private long mExpiresAt;

    /** A fresh password for a paste made at {@code now}, replacing any earlier one. */
    String issue(long now) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(32);
        for (byte b : bytes) hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        mToken = hex.toString();
        mExpiresAt = now + LIFETIME_MILLIS;
        return mToken;
    }

    /**
     * Whether {@code password} is the outstanding one and still fresh at {@code now}. A match uses
     * it up; a wrong guess leaves it for the program that does hold it.
     */
    boolean redeem(String password, long now) {
        if (mToken == null || password == null) return false;
        if (now >= mExpiresAt) {
            mToken = null;
            return false;
        }
        if (!MessageDigest.isEqual(mToken.getBytes(StandardCharsets.UTF_8), password.getBytes(StandardCharsets.UTF_8)))
            return false;
        mToken = null;
        return true;
    }

    void clear() {
        mToken = null;
    }
}
