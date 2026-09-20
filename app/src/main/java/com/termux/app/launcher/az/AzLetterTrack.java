package com.termux.app.launcher.az;

/**
 * The letters' own slots along the bar: how wide one is, where its centre sits, and which one a
 * finger at some distance along the bar is holding.
 *
 * <p>One copy for both orientations. A horizontal bar's "along" is its width and a column's is its
 * height, and the arithmetic never learns which — so the row a thumb scrubs and the column it
 * scrubs pick the same letter at the same fraction of the bar.
 *
 * <p>Pure: no {@code View}, no {@code Canvas}.
 */
public final class AzLetterTrack {

    private AzLetterTrack() {}

    /**
     * How far past a slot's boundary the finger must travel before the letter changes, as a
     * fraction of a slot. Keeps a thumb parked on a boundary from flickering between two letters.
     */
    public static final float SLOT_HYSTERESIS_RATIO = 0.22f;

    /** The usable length of the track; never zero, so a slot is never zero-wide. */
    public static float trackLengthPx(float lengthPx) {
        return Math.max(1f, lengthPx);
    }

    /** One letter's slot. */
    public static float slotSizePx(float lengthPx, int count) {
        return trackLengthPx(lengthPx) / Math.max(1, count);
    }

    /** The centre of one letter's slot, measured along the bar from its start. */
    public static float centerPx(int index, float lengthPx, int count) {
        float slot = slotSizePx(lengthPx, count);
        return (slot * index) + (slot * 0.5f);
    }

    /** The letter a point {@code alongPx} into the bar lands in; clamped to the ends. */
    public static int indexAt(float alongPx, float lengthPx, int count) {
        int len = Math.max(1, count);
        int index = (int) ((alongPx / trackLengthPx(lengthPx)) * len);
        return Math.max(0, Math.min(len - 1, index));
    }

    /**
     * The letter to hold now, given the one held by the last sample. Neighbouring slots trade only
     * once the finger is clear of the boundary between them; a jump of more than one slot, and the
     * first sample of a gesture, take the raw answer.
     *
     * @param lastIndex      the letter the previous sample held, or negative for none
     * @param applyHysteresis false on the first sample of a gesture, which has nothing to hold on to
     */
    public static int indexWithHysteresis(float alongPx, int lastIndex, float lengthPx, int count,
                                          boolean applyHysteresis) {
        int index = indexAt(alongPx, lengthPx, count);
        if (lastIndex < 0 || !applyHysteresis || Math.abs(index - lastIndex) > 1) {
            return index;
        }
        if (index == lastIndex) {
            return lastIndex;
        }
        float slot = slotSizePx(lengthPx, count);
        float boundary = Math.max(index, lastIndex) * slot;
        float hysteresis = slot * SLOT_HYSTERESIS_RATIO;
        if (index > lastIndex) {
            return alongPx >= (boundary + hysteresis) ? index : lastIndex;
        }
        return alongPx <= (boundary - hysteresis) ? index : lastIndex;
    }
}
