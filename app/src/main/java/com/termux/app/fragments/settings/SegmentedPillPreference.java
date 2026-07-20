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

/** Inline two-segment preference used for the dock's Default / Capsule style choice. */
@Keep
public final class SegmentedPillPreference extends Preference {

    public static final String VALUE_DEFAULT = "default";
    public static final String VALUE_CAPSULE = "valarie_capsule";
    private static final long SLIDE_DURATION_MS = 190L;

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

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        String fallback = defaultValue instanceof String ? (String) defaultValue : VALUE_DEFAULT;
        mValue = normalize(getPersistedString(fallback));
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        FrameLayout track = (FrameLayout) holder.findViewById(R.id.segmented_pill_track);
        View indicator = holder.findViewById(R.id.segmented_pill_indicator);
        TextView defaultLabel = (TextView) holder.findViewById(R.id.segmented_pill_default);
        TextView capsuleLabel = (TextView) holder.findViewById(R.id.segmented_pill_capsule);
        if (track == null || indicator == null || defaultLabel == null || capsuleLabel == null)
            return;

        View.OnClickListener chooseDefault = view -> setValue(VALUE_DEFAULT, track, indicator, true);
        View.OnClickListener chooseCapsule = view -> setValue(VALUE_CAPSULE, track, indicator, true);
        defaultLabel.setOnClickListener(chooseDefault);
        capsuleLabel.setOnClickListener(chooseCapsule);
        track.setContentDescription(getTitle());
        track.post(() -> {
            updateIndicatorWidth(track, indicator);
            indicator.setTranslationX(isCapsule() ? segmentWidth(track) : 0f);
            updateLabelColors(defaultLabel, capsuleLabel);
        });
    }

    private void setValue(@NonNull String value, @NonNull FrameLayout track,
                          @NonNull View indicator, boolean animate) {
        String normalized = normalize(value);
        if (normalized.equals(mValue)) return;
        if (!callChangeListener(normalized)) return;
        mValue = normalized;
        persistString(normalized);
        updateIndicatorWidth(track, indicator);
        float target = isCapsule() ? segmentWidth(track) : 0f;
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
        TextView defaultLabel = track.findViewById(R.id.segmented_pill_default);
        TextView capsuleLabel = track.findViewById(R.id.segmented_pill_capsule);
        updateLabelColors(defaultLabel, capsuleLabel);
    }

    private void updateIndicatorWidth(@NonNull FrameLayout track, @NonNull View indicator) {
        int width = Math.round(segmentWidth(track));
        if (width <= 0 || indicator.getLayoutParams().width == width) return;
        indicator.getLayoutParams().width = width;
        indicator.requestLayout();
    }

    private float segmentWidth(@NonNull FrameLayout track) {
        return Math.max(0f, (track.getWidth() - track.getPaddingLeft() - track.getPaddingRight()) / 2f);
    }

    private void updateLabelColors(TextView defaultLabel, TextView capsuleLabel) {
        if (defaultLabel == null || capsuleLabel == null) return;
        int selected = resolveColor(com.termux.shared.R.attr.termuxColorOnAccentContainer,
            R.color.termux_on_primary);
        int idle = resolveColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            R.color.termux_on_surface_variant);
        defaultLabel.setTextColor(isCapsule() ? idle : selected);
        capsuleLabel.setTextColor(isCapsule() ? selected : idle);
    }

    private int resolveColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)) return value.data;
        return ContextCompat.getColor(getContext(), fallback);
    }

    private boolean isCapsule() {
        return VALUE_CAPSULE.equals(mValue);
    }

    @NonNull
    private static String normalize(String value) {
        return VALUE_CAPSULE.equals(value) ? VALUE_CAPSULE : VALUE_DEFAULT;
    }
}
