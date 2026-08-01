package com.termux.terminal;

import android.icu.text.BreakIterator;

import java.util.Locale;

/** Incremental extended-grapheme decisions backed by Android ICU on supported devices. */
final class GraphemeClusterer {

    private static final int MAX_CLUSTER_UTF16_UNITS = 64;

    private final StringBuilder mCluster = new StringBuilder();
    private BreakIterator mIterator;
    private boolean mIcuUnavailable;
    private int mFirstCodePoint = -1;
    private int mLastCodePoint = -1;

    /** Accept a code point and return whether it extends the preceding accepted grapheme. */
    boolean accept(int codePoint, boolean precedingCellIsCurrent) {
        if (!precedingCellIsCurrent || mCluster.length() == 0
            || mCluster.length() + Character.charCount(codePoint) > MAX_CLUSTER_UTF16_UNITS) {
            resetTo(codePoint);
            return false;
        }
        // UAX #29 never joins ordinary printable ASCII to the preceding grapheme. Keep the shell,
        // editor and compiler fast path out of ICU and free of temporary strings.
        if (codePoint >= 0x20 && codePoint < 0x7F) {
            resetTo(codePoint);
            return false;
        }

        int oldLength = mCluster.length();
        mCluster.appendCodePoint(codePoint);
        boolean joins = isSingleGrapheme();
        if (!joins) {
            mCluster.delete(0, oldLength);
            mFirstCodePoint = codePoint;
        }
        mLastCodePoint = codePoint;
        return joins;
    }

    boolean shouldWidenCell() {
        return isRegionalIndicator(mFirstCodePoint) && isRegionalIndicator(mLastCodePoint);
    }

    void reset() {
        mCluster.setLength(0);
        mFirstCodePoint = mLastCodePoint = -1;
    }

    private void resetTo(int codePoint) {
        mCluster.setLength(0);
        mCluster.appendCodePoint(codePoint);
        mFirstCodePoint = mLastCodePoint = codePoint;
    }

    private boolean isSingleGrapheme() {
        if (mIcuUnavailable) return fallbackContinuation();
        try {
            if (mIterator == null) mIterator = BreakIterator.getCharacterInstance(Locale.ROOT);
            mIterator.setText(mCluster.toString());
            mIterator.first();
            return mIterator.next() == mCluster.length();
        } catch (RuntimeException | LinkageError error) {
            // android.jar stubs used by local JVM tests do not implement ICU. Production Android
            // always has it at this project's API 26 minimum; retain legacy zero-width behavior if
            // an unusual runtime still cannot provide it.
            mIcuUnavailable = true;
            return fallbackContinuation();
        }
    }

    private boolean fallbackContinuation() {
        int last = mCluster.codePointBefore(mCluster.length());
        return WcWidth.width(last) <= 0;
    }

    private static boolean isRegionalIndicator(int codePoint) {
        return codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF;
    }
}
