package com.termux.app.fragments.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.slider.Slider;
import com.termux.R;

/**
 * An integer preference edited with a Material 3 {@link Slider}: the stock replacement for
 * androidx's {@code SeekBarPreference}, taking the same {@code app:min}, {@code android:max},
 * {@code android:defaultValue} and {@code app:showSeekBarValue} attributes so a page swaps the tag
 * and nothing else. Steps are whole numbers, as the SeekBar's were; the value is persisted as an
 * int under the preference's key.
 */
@Keep
public final class SliderPreference extends Preference {

    private int mMin;
    private int mMax = 100;
    private int mValue;
    private boolean mShowValue;

    public SliderPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_settings_seekbar);
        setSelectable(false);
        setIconSpaceReserved(false);
        if (attrs == null) return;
        TypedArray a = context.obtainStyledAttributes(attrs, new int[]{
            androidx.preference.R.attr.min, android.R.attr.max,
            androidx.preference.R.attr.showSeekBarValue});
        try {
            mMin = a.getInt(0, mMin);
            mMax = a.getInt(1, mMax);
            mShowValue = a.getBoolean(2, false);
        } finally {
            a.recycle();
        }
    }

    public SliderPreference(@NonNull Context context) {
        this(context, null);
    }

    @Override
    protected Object onGetDefaultValue(@NonNull TypedArray a, int index) {
        return a.getInt(index, 0);
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        int fallback = defaultValue instanceof Integer ? (Integer) defaultValue : mMin;
        mValue = clamp(getPersistedInt(fallback), mMin, mMax);
    }

    public int getMin() {
        return mMin;
    }

    public int getMax() {
        return mMax;
    }

    public int getValue() {
        return mValue;
    }

    public void setMin(int min) {
        mMin = min;
        mValue = clamp(mValue, mMin, mMax);
        notifyChanged();
    }

    public void setMax(int max) {
        mMax = max;
        mValue = clamp(mValue, mMin, mMax);
        notifyChanged();
    }

    /** Sets and persists the value, clamped to the range. Does not run the change listener. */
    public void setValue(int value) {
        int clamped = clamp(value, mMin, mMax);
        if (clamped == mValue) return;
        mValue = clamped;
        persistInt(clamped);
        notifyChanged();
    }

    /** Clamps to {@code [min, max]}; a range that is empty or inverted collapses onto {@code min}. */
    @VisibleForTesting
    static int clamp(int value, int min, int max) {
        if (max <= min) return min;
        return Math.max(min, Math.min(max, value));
    }

    /** A Slider needs {@code valueFrom < valueTo}; a degenerate range gets one step of headroom. */
    @VisibleForTesting
    static float sliderTo(int min, int max) {
        return max > min ? max : min + 1;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View sliderView = holder.findViewById(R.id.seekbar);
        TextView readout = (TextView) holder.findViewById(R.id.seekbar_value);
        if (!(sliderView instanceof Slider)) return;
        Slider slider = (Slider) sliderView;
        slider.clearOnChangeListeners();
        slider.setValueFrom(mMin);
        slider.setValueTo(sliderTo(mMin, mMax));
        slider.setStepSize(1f);
        slider.setValue(clamp(mValue, mMin, mMax));
        slider.setEnabled(isEnabled());
        slider.setContentDescription(getTitle());
        if (readout != null) {
            readout.setVisibility(mShowValue ? View.VISIBLE : View.GONE);
            readout.setText(String.valueOf(mValue));
        }
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (!fromUser) return;
            int next = clamp(Math.round(value), mMin, mMax);
            if (next == mValue) return;
            if (!callChangeListener(next)) {
                s.setValue(mValue);
                return;
            }
            // No notifyChanged here: rebinding the row mid-drag would cancel the gesture.
            mValue = next;
            persistInt(next);
            if (readout != null) readout.setText(String.valueOf(next));
        });
    }
}
