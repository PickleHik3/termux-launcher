package com.termux.app.fragments.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

/**
 * Inline segmented preference: a sliding indicator over two or three labelled segments. Defaults
 * to the global Default / Rounded surface-shape pair; {@link #setSegments} swaps in another value
 * set (the third segment stays hidden until a three-value set is configured).
 */
@Keep
public final class SegmentedPillPreference extends Preference {

    public static final String VALUE_DEFAULT = "default";
    public static final String VALUE_ROUNDED = "rounded";
    private static final String VALUE_LEGACY_VALARIE_CAPSULE = "valarie_capsule";
    private static final long SLIDE_DURATION_MS = 190L;

    private String[] mValues = {VALUE_DEFAULT, VALUE_ROUNDED};
    /** 0 keeps the label text the layout declares; anything else overrides it. */
    private int[] mLabelResIds = {0, 0};
    private static final int MAX_SEGMENTS = 4;
    private String mValue = VALUE_DEFAULT;
    private ValueAnimator mIndicatorAnimator;

    public SegmentedPillPreference(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_segmented_pill);
        setIconSpaceReserved(false);
        setSelectable(false);
    }

    public SegmentedPillPreference(@NonNull Context context) {
        this(context, null);
    }

    /**
     * Replaces the segment set. Re-reads the persisted value against the new set, since the value
     * restored on attach was normalized against the default Default / Rounded pair.
     */
    public void setSegments(@NonNull String[] values, @NonNull int[] labelResIds) {
        if (values.length < 2 || values.length > MAX_SEGMENTS || values.length != labelResIds.length)
            throw new IllegalArgumentException("SegmentedPillPreference needs 2 to "
                + MAX_SEGMENTS + " segments");
        mValues = values;
        mLabelResIds = labelResIds;
        mValue = normalize(getPersistedString(mValues[0]));
        notifyChanged();
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        String fallback = defaultValue instanceof String ? (String) defaultValue : mValues[0];
        mValue = normalize(getPersistedString(fallback));
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        FrameLayout track = (FrameLayout) holder.findViewById(R.id.segmented_pill_track);
        View indicator = holder.findViewById(R.id.segmented_pill_indicator);
        TextView[] labels = findLabels(holder);
        if (track == null || indicator == null || labels == null) return;

        for (int i = 0; i < labels.length; i++) {
            TextView label = labels[i];
            if (i >= mValues.length) {
                label.setVisibility(View.GONE);
                label.setOnClickListener(null);
                continue;
            }
            label.setVisibility(View.VISIBLE);
            if (mLabelResIds[i] != 0) label.setText(mLabelResIds[i]);
            final String value = mValues[i];
            label.setOnClickListener(view -> setValue(value, track, indicator, true));
        }
        track.setContentDescription(getTitle());
        track.post(() -> {
            updateIndicatorWidth(track, indicator);
            indicator.setTranslationX(selectedIndex() * segmentWidth(track));
            updateLabelColors(labels);
        });
    }

    private TextView[] findLabels(@NonNull PreferenceViewHolder holder) {
        TextView first = (TextView) holder.findViewById(R.id.segmented_pill_default);
        TextView second = (TextView) holder.findViewById(R.id.segmented_pill_capsule);
        TextView third = (TextView) holder.findViewById(R.id.segmented_pill_third);
        TextView fourth = (TextView) holder.findViewById(R.id.segmented_pill_fourth);
        if (first == null || second == null || third == null || fourth == null) return null;
        return new TextView[]{first, second, third, fourth};
    }

    private void setValue(@NonNull String value, @NonNull FrameLayout track,
                          @NonNull View indicator, boolean animate) {
        String normalized = normalize(value);
        if (normalized.equals(mValue)) return;
        if (!callChangeListener(normalized)) return;
        mValue = normalized;
        persistString(normalized);
        updateIndicatorWidth(track, indicator);
        float target = selectedIndex() * segmentWidth(track);
        if (mIndicatorAnimator != null) mIndicatorAnimator.cancel();
        if (animate && track.isLaidOut()) {
            mIndicatorAnimator = ValueAnimator.ofFloat(indicator.getTranslationX(), target);
            mIndicatorAnimator.setDuration(SLIDE_DURATION_MS);
            mIndicatorAnimator.setInterpolator(new DecelerateInterpolator());
            mIndicatorAnimator.addUpdateListener(animator ->
                indicator.setTranslationX((Float) animator.getAnimatedValue()));
            mIndicatorAnimator.start();
        } else {
            indicator.setTranslationX(target);
        }
        updateLabelColors(new TextView[]{
            track.findViewById(R.id.segmented_pill_default),
            track.findViewById(R.id.segmented_pill_capsule),
            track.findViewById(R.id.segmented_pill_third),
            track.findViewById(R.id.segmented_pill_fourth)});
    }

    private void updateIndicatorWidth(@NonNull FrameLayout track, @NonNull View indicator) {
        int width = Math.round(segmentWidth(track));
        if (width <= 0 || indicator.getLayoutParams().width == width) return;
        indicator.getLayoutParams().width = width;
        indicator.requestLayout();
    }

    private float segmentWidth(@NonNull FrameLayout track) {
        return Math.max(0f, (track.getWidth() - track.getPaddingLeft() - track.getPaddingRight())
            / (float) mValues.length);
    }

    private void updateLabelColors(TextView[] labels) {
        if (labels == null) return;
        int selected = resolveColor(com.termux.shared.R.attr.termuxColorOnAccentContainer,
            R.color.termux_on_primary);
        int idle = resolveColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            R.color.termux_on_surface_variant);
        int selectedIndex = selectedIndex();
        for (int i = 0; i < labels.length && i < mValues.length; i++) {
            if (labels[i] == null) continue;
            labels[i].setTextColor(i == selectedIndex ? selected : idle);
        }
    }

    private int resolveColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)) return value.data;
        return ContextCompat.getColor(getContext(), fallback);
    }

    private int selectedIndex() {
        for (int i = 0; i < mValues.length; i++) {
            if (mValues[i].equals(mValue)) return i;
        }
        return 0;
    }

    @NonNull
    private String normalize(String value) {
        for (String known : mValues) {
            if (known.equals(value)) return value;
        }
        if (VALUE_LEGACY_VALARIE_CAPSULE.equals(value)) {
            for (String known : mValues) {
                if (VALUE_ROUNDED.equals(known)) return VALUE_ROUNDED;
            }
        }
        return mValues[0];
    }
}
