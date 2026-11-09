package com.termux.terminal;

import android.icu.text.BreakIterator;

import java.text.CharacterIterator;
import java.util.Locale;

/** Incremental extended-grapheme decisions backed by Android ICU on supported devices. */
final class GraphemeClusterer {

    private static final int MAX_CLUSTER_UTF16_UNITS = 64;

    /**
     * The current cluster's text. While {@link #mClusterIsLoneAscii} is set it is stale and the
     * cluster is the single code point {@link #mFirstCodePoint} instead.
     */
    private final StringBuilder mCluster = new StringBuilder();
    /** What the break iterator reads: {@link #mCluster} in place, with no String copy. */
    private final ClusterText mClusterText = new ClusterText(mCluster);
    private BreakIterator mIterator;
    private boolean mIcuUnavailable;
    private int mFirstCodePoint = -1;
    private int mLastCodePoint = -1;

    /**
     * The cluster is one printable ASCII code point that has not been copied into
     * {@link #mCluster}. Plain text starts a new cluster for almost every character, so the
     * copy is made only when a non-ASCII code point arrives that might join it.
     */
    private boolean mClusterIsLoneAscii;

    GraphemeClusterer() {
    }

    /** For tests: decide breaks with this iterator instead of Android ICU. */
    GraphemeClusterer(BreakIterator iterator) {
        mIterator = iterator;
    }

    /** Accept a code point and return whether it extends the preceding accepted grapheme. */
    boolean accept(int codePoint, boolean precedingCellIsCurrent) {
        int clusterLength = mClusterIsLoneAscii ? 1 : mCluster.length();
        if (!precedingCellIsCurrent || clusterLength == 0
            || clusterLength + Character.charCount(codePoint) > MAX_CLUSTER_UTF16_UNITS) {
            resetTo(codePoint);
            return false;
        }
        // UAX #29 never joins ordinary printable ASCII to the preceding grapheme. Keep the shell,
        // editor and compiler fast path out of ICU and free of temporary strings.
        if (codePoint >= 0x20 && codePoint < 0x7F) {
            resetTo(codePoint);
            return false;
        }

        if (mClusterIsLoneAscii) {
            mCluster.setLength(0);
            mCluster.append((char) mFirstCodePoint);
            mClusterIsLoneAscii = false;
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
        mClusterIsLoneAscii = false;
        mFirstCodePoint = mLastCodePoint = -1;
    }

    private void resetTo(int codePoint) {
        if (codePoint >= 0x20 && codePoint < 0x7F) {
            mClusterIsLoneAscii = true;
        } else {
            mCluster.setLength(0);
            mCluster.appendCodePoint(codePoint);
            mClusterIsLoneAscii = false;
        }
        mFirstCodePoint = mLastCodePoint = codePoint;
    }

    private boolean isSingleGrapheme() {
        if (mIcuUnavailable) return fallbackContinuation();
        try {
            if (mIterator == null) mIterator = BreakIterator.getCharacterInstance(Locale.ROOT);
            mClusterText.rewind();
            mIterator.setText(mClusterText);
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

    /**
     * A {@link CharacterIterator} over a StringBuilder that another object keeps changing. The
     * clusterer asks ICU about its text for most non-ASCII code points, so the iterator is made
     * once and {@link #rewind} points it at the builder's current length, where a String and a
     * StringCharacterIterator would be made on every call. It answers exactly as
     * java.text.StringCharacterIterator does over the builder's text at the time of the rewind.
     */
    static final class ClusterText implements CharacterIterator {
        private final CharSequence mText;
        private int mEnd;
        private int mPosition;

        ClusterText(CharSequence text) {
            mText = text;
        }

        /** Cover the text as it is now and stand at its start. */
        void rewind() {
            mEnd = mText.length();
            mPosition = 0;
        }

        @Override
        public char first() {
            mPosition = 0;
            return current();
        }

        @Override
        public char last() {
            mPosition = (mEnd != 0) ? mEnd - 1 : mEnd;
            return current();
        }

        @Override
        public char setIndex(int position) {
            if (position < 0 || position > mEnd) throw new IllegalArgumentException("Invalid index");
            mPosition = position;
            return current();
        }

        @Override
        public char current() {
            return (mPosition >= 0 && mPosition < mEnd) ? mText.charAt(mPosition) : DONE;
        }

        @Override
        public char next() {
            if (mPosition < mEnd - 1) {
                mPosition++;
                return mText.charAt(mPosition);
            }
            mPosition = mEnd;
            return DONE;
        }

        @Override
        public char previous() {
            if (mPosition > 0) {
                mPosition--;
                return mText.charAt(mPosition);
            }
            return DONE;
        }

        @Override
        public int getBeginIndex() {
            return 0;
        }

        @Override
        public int getEndIndex() {
            return mEnd;
        }

        @Override
        public int getIndex() {
            return mPosition;
        }

        @Override
        public Object clone() {
            // An independent snapshot of the text as it is now, as a StringCharacterIterator
            // clone would be. ICU does not clone while it breaks a cluster.
            ClusterText copy = new ClusterText(mText.subSequence(0, mEnd).toString());
            copy.mEnd = mEnd;
            copy.mPosition = mPosition;
            return copy;
        }
    }
}
