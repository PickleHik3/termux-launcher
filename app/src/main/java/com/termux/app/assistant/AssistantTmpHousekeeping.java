package com.termux.app.assistant;

import androidx.annotation.NonNull;

import com.termux.shared.file.OlderThanSweeper;

/**
 * The tmp offer's clearing job, for a background thread: clears what has sat untouched for a day.
 * The size measurement is {@code TermuxShellUtils.getTermuxTMPDIRSize}, beside the sweeper.
 */
final class AssistantTmpHousekeeping {

    /** What clears old entries; {@code TermuxShellUtils::clearTermuxTMPDIROlderThan} in the app. */
    interface Clearer {
        @NonNull OlderThanSweeper.Result clear(long ageMillis);
    }

    private AssistantTmpHousekeeping() {}

    /** Clears entries untouched for a day and returns the bytes that freed; 0 when the clear fails. */
    static long clearOld(@NonNull Clearer clearer) {
        try {
            return Math.max(0L, clearer.clear(AssistantOffersPolicy.CLEAR_AGE_MS).bytesFreed);
        } catch (RuntimeException e) {
            return 0L;
        }
    }
}
