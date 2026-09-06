package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

import java.util.Calendar;
import java.util.Locale;

/**
 * The clock for a status bar standing in a column: the hour over the minutes, the date under both.
 *
 * <p>A column is too narrow for a time written across it, and the two ways out are to stack the
 * digits or to turn the whole clock on its side. Stacked wins: sideways type cannot be read at a
 * glance, it fights the system font scale — the one thing that actually breaks chrome on other
 * phones — and it would be the only rotated text anywhere in the launcher. Stacked keeps the type
 * upright, on the same ramp as the row's clock, and simply uses the room a column has plenty of.
 */
public final class StatusBarStackedClockView extends LinearLayout {

    private final TextView mHours;
    private final TextView mMinutes;
    private final TextView mDate;
    private final TextView mMeridiem;
    private boolean mUseAmPm;

    public StatusBarStackedClockView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        int onSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        int variant = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            ContextCompat.getColor(context, R.color.termux_on_surface_variant));

        mHours = digitLine(context, onSurface, 22f, Typeface.DEFAULT_BOLD);
        addView(mHours, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        mMinutes = digitLine(context, ColorUtils.setAlphaComponent(onSurface, 205), 22f,
            Typeface.DEFAULT);
        addView(mMinutes, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        mMeridiem = digitLine(context, variant, 9f, Typeface.DEFAULT);
        mMeridiem.setVisibility(GONE);
        addView(mMeridiem, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        mDate = digitLine(context, variant, 10f, Typeface.DEFAULT);
        LayoutParams dateParams =
            new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = Math.round(3f * context.getResources().getDisplayMetrics().density);
        addView(mDate, dateParams);
        refresh();
    }

    @NonNull
    private static TextView digitLine(@NonNull Context context, int color, float sizeSp,
                                      @NonNull Typeface typeface) {
        TextView view = new TextView(context);
        view.setGravity(Gravity.CENTER);
        view.setMaxLines(1);
        view.setIncludeFontPadding(false);
        view.setTextColor(color);
        view.setTypeface(typeface);
        // Autosized rather than fixed: a 1.3x font scale is what clips fixed-height chrome, and
        // the column's width is the one thing this clock cannot grow into.
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        return view;
    }

    /** Whether the hour is written on a twelve-hour clock, following the row clock's own setting. */
    public void setUseAmPm(boolean useAmPm) {
        if (mUseAmPm == useAmPm) return;
        mUseAmPm = useAmPm;
        refresh();
    }

    /** Re-reads the time. Driven by whatever already ticks the bar, so nothing ticks on its own. */
    public void refresh() {
        Calendar now = Calendar.getInstance();
        boolean amPm = mUseAmPm || !DateFormat.is24HourFormat(getContext());
        int hour = amPm ? now.get(Calendar.HOUR) : now.get(Calendar.HOUR_OF_DAY);
        if (amPm && hour == 0) hour = 12;
        mHours.setText(String.format(Locale.getDefault(), amPm ? "%d" : "%02d", hour));
        mMinutes.setText(String.format(Locale.getDefault(), "%02d", now.get(Calendar.MINUTE)));
        mMeridiem.setVisibility(amPm ? VISIBLE : GONE);
        if (amPm) {
            mMeridiem.setText(now.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");
        }
        mDate.setText(DateFormat.format(
            DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd"), now));
    }
}
