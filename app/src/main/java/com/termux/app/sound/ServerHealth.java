package com.termux.app.sound;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Whether the running sound server is fit to be left alone. Pure: the caller looks at the
 * socket, the process and {@code pactl info}; this decides.
 *
 * <p>A server still starting refuses connections at once and has no socket yet, hence the grace
 * before either counts. A timeout counts at any age: a hung server never recovers.
 */
final class ServerHealth {

    private ServerHealth() {}

    enum Verdict { OK, BROKEN }

    /** How long a fresh server may lack its socket or refuse. */
    static final long GRACE_MS = 10_000;

    @NonNull
    static Verdict judge(boolean socketExists, long uptimeMs, @Nullable String pactlAnswer) {
        if (pactlAnswer == null || pactlAnswer.contains("Timeout")) return Verdict.BROKEN;
        if (uptimeMs <= GRACE_MS) return Verdict.OK;
        if (!socketExists) return Verdict.BROKEN;
        if (pactlAnswer.contains("Connection refused") || pactlAnswer.contains("Connection failure")) {
            return Verdict.BROKEN;
        }
        return Verdict.OK;
    }
}
