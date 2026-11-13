package com.termux.app.statusbar;

import android.os.BatteryManager;

import androidx.annotation.NonNull;

/**
 * What the status bar's battery chip shows for one battery reading: the percentage text, how full
 * the body draws and which mark (if any) sits inside it. Pure, so every state and the precedence
 * between them is tested without a device; {@link BatteryChipController} feeds it the sticky
 * {@code ACTION_BATTERY_CHANGED} extras and {@link BatteryIconDrawable} draws the answer.
 *
 * <p>Precedence when several apply: {@link Kind#HOT} over {@link Kind#FULL} over
 * {@link Kind#CHARGING} over {@link Kind#LOW} over {@link Kind#PLAIN}. An unknown level is always
 * {@link Kind#PLAIN} with an empty body.</p>
 */
public final class BatteryChipState {

    public enum Kind { PLAIN, LOW, CHARGING, FULL, HOT }

    /** At or under this percentage, and not charging, the chip is in the error colour. */
    public static final int LOW_PERCENT = 15;
    /** At or over this, in tenths of a degree Celsius (45.0 C), the battery reads hot. */
    public static final int HOT_TENTHS = 450;
    /** What the chip says when the device reports no level. */
    public static final String UNKNOWN_TEXT = "--";

    /** The chip before any reading: "--" and an empty outline. */
    public static final BatteryChipState UNKNOWN =
        new BatteryChipState(UNKNOWN_TEXT, 0f, Kind.PLAIN);

    @NonNull private final String mText;
    private final float mFill;
    @NonNull private final Kind mKind;

    private BatteryChipState(@NonNull String text, float fill, @NonNull Kind kind) {
        mText = text;
        mFill = fill;
        mKind = kind;
    }

    /**
     * @param level            {@code EXTRA_LEVEL}, or -1 when missing
     * @param scale            {@code EXTRA_SCALE}, or -1 when missing
     * @param status           {@code EXTRA_STATUS}, or -1
     * @param plugged          {@code EXTRA_PLUGGED}: 0 on battery, else a charger bit set
     * @param health           {@code EXTRA_HEALTH}, or -1
     * @param temperatureTenths {@code EXTRA_TEMPERATURE} in tenths of a degree, or -1
     */
    @NonNull
    public static BatteryChipState of(int level, int scale, int status, int plugged, int health,
                                      int temperatureTenths) {
        if (level < 0 || scale <= 0) return UNKNOWN;
        float fraction = Math.max(0f, Math.min(1f, level / (float) scale));
        int percent = Math.round(fraction * 100f);
        boolean isPlugged = plugged > 0;
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING;
        boolean full = status == BatteryManager.BATTERY_STATUS_FULL
            || (percent >= 100 && isPlugged);
        boolean hot = health == BatteryManager.BATTERY_HEALTH_OVERHEAT
            || temperatureTenths >= HOT_TENTHS;
        Kind kind;
        if (hot) kind = Kind.HOT;
        else if (full) kind = Kind.FULL;
        else if (charging) kind = Kind.CHARGING;
        else if (percent <= LOW_PERCENT) kind = Kind.LOW;
        else kind = Kind.PLAIN;
        return new BatteryChipState(percent + "%", kind == Kind.FULL ? 1f : fraction, kind);
    }

    /** "57%", or "--" when the level is unknown. */
    @NonNull public String text() { return mText; }

    /** How much of the body the fill covers, 0..1, continuous. */
    public float fill() { return mFill; }

    @NonNull public Kind kind() { return mKind; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BatteryChipState)) return false;
        BatteryChipState s = (BatteryChipState) o;
        return mKind == s.mKind && Float.compare(mFill, s.mFill) == 0 && mText.equals(s.mText);
    }

    @Override public int hashCode() {
        return (mText.hashCode() * 31 + Float.floatToIntBits(mFill)) * 31 + mKind.hashCode();
    }
}
