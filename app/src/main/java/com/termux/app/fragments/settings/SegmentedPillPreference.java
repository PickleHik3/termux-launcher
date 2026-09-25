package com.termux.app.fragments.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

/**
 * Inline segmented preference: a sliding indicator over two or three labelled segments. Defaults
 * to the global Default / Rounded surface-shape pair; {@link #setSegments} swaps in another value
 * set (the third segment stays hidden until a three-value set is configured).
 *
 * <p>{@link #VALUE_NONE} is the one value with no segment of its own: the indicator goes away and
 * no label is lit, which is how a row that stands for several stored values says they disagree.
 */
@Keep
public final class SegmentedPillPreference extends Preference {

    public static final String VALUE_DEFAULT = "default";
    public static final String VALUE_ROUNDED = "rounded";

    /**
     * The one value that is not a segment: nothing is lit and the indicator is away. A row whose
     * store answers for several things at once — the Keyboard page's keyboard type, which stands
     * for every place — reads back as this when they disagree, so the pill says "these differ"
     * rather than picking one of them for the user. Tapping a segment still writes it to all.
     */
    public static final String VALUE_NONE = "";
    private static final String VALUE_LEGACY_VALARIE_CAPSULE = "valarie_capsule";
    private static final long SLIDE_DURATION_MS = 190L;

    private String[] mValues = {VALUE_DEFAULT, VALUE_ROUNDED};
    /** 0 keeps the label text the layout declares (or {@link #mLabelText}); anything else
     *  overrides it with a resource string. */
    private int[] mLabelResIds = {0, 0};
    /** Non-null entries win over {@link #mLabelResIds}; how {@link #setSegments(String[], CharSequence[])}
     *  and the XML {@code android:entries}/{@code android:entryValues} path supply labels. */
    private CharSequence[] mLabelText = {null, null};
    private static final int MAX_SEGMENTS = 4;
    private String mValue = VALUE_DEFAULT;
    private ValueAnimator mIndicatorAnimator;

    public SegmentedPillPreference(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_segmented_pill);
        setIconSpaceReserved(false);
        setSelectable(false);
        configureFromEntryAttrs(context, attrs);
    }

    public SegmentedPillPreference(@NonNull Context context) {
        this(context, null);
    }

    /**
     * Lets a page declare its segments straight in XML, the same way a {@code ListPreference}
     * declares {@code android:entries}/{@code android:entryValues}, instead of a
     * {@code findPreference} + {@link #setSegments} pair in the fragment. Only applies when both
     * arrays are present and non-empty; a row that needs Java-side logic (a computed label, a
     * fourth "imported" segment, etc.) still calls {@link #setSegments} directly, which wins since
     * it runs after inflation.
     */
    private void configureFromEntryAttrs(@NonNull Context context, @Nullable AttributeSet attrs) {
        if (attrs == null) return;
        // Not the framework's android:entries/entryValues: androidx.preference declares its own
        // "entries"/"entryValues" attrs (aliased to the framework ones on ListPreference), and
        // that is what app:entries in the XML resolves to under non-namespaced resource merging.
        TypedArray a = context.obtainStyledAttributes(attrs,
            new int[]{androidx.preference.R.attr.entries, androidx.preference.R.attr.entryValues});
        try {
            CharSequence[] entries = a.getTextArray(0);
            CharSequence[] entryValues = a.getTextArray(1);
            if (entries == null || entryValues == null) return;
            if (entries.length != entryValues.length) return;
            String[] values = new String[entryValues.length];
            for (int i = 0; i < entryValues.length; i++) values[i] = entryValues[i].toString();
            setSegments(values, entries);
        } finally {
            a.recycle();
        }
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
        mLabelText = new CharSequence[values.length];
        mValue = normalize(getPersistedString(mValues[0]));
        notifyChanged();
    }

    /**
     * Same as {@link #setSegments(String[], int[])}, but with the labels given directly as text —
     * the shape a {@code ListPreference}'s {@code android:entries}/{@code android:entryValues}
     * pair already comes in, so a page can hand its existing entries array straight to a pill
     * without adding per-segment string resources.
     */
    public void setSegments(@NonNull String[] values, @NonNull CharSequence[] labels) {
        if (values.length < 2 || values.length > MAX_SEGMENTS || values.length != labels.length)
            throw new IllegalArgumentException("SegmentedPillPreference needs 2 to "
                + MAX_SEGMENTS + " segments");
        mValues = values;
        mLabelResIds = new int[values.length];
        mLabelText = labels;
        mValue = normalize(getPersistedString(mValues[0]));
        notifyChanged();
    }

    /** The currently selected value, or {@link #VALUE_NONE}. */
    @NonNull
    public String getValue() {
        return mValue;
    }

    /**
     * Sets and persists the value programmatically, the way {@code ListPreference.setValue} does —
     * silently, without running the change listener a tap on a segment would trigger. For a page
     * forcing a choice the user did not make (e.g. hiding a row and locking in its one remaining
     * option).
     */
    public void setValue(@NonNull String value) {
        mValue = normalize(value);
        persistString(mValue);
        notifyChanged();
    }

    /** How many segments the current set has — a test's way of confirming a portrait/landscape
     *  segment swap actually took, without reaching into the bound view. */
    @VisibleForTesting
    public int segmentCount() {
        return mValues.length;
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
            if (mLabelText[i] != null) label.setText(mLabelText[i]);
            else if (mLabelResIds[i] != 0) label.setText(mLabelResIds[i]);
            final String value = mValues[i];
            label.setOnClickListener(view -> setValue(value, track, indicator, true));
        }
        track.setContentDescription(getTitle());
        // Labels are coloured by hand, so a disabled row would otherwise look live.
        track.setAlpha(isEnabled() ? 1f : 0.38f);
        track.post(() -> {
            updateIndicatorWidth(track, indicator);
            indicator.setVisibility(selectedIndex() < 0 ? View.INVISIBLE : View.VISIBLE);
            indicator.setTranslationX(indicatorOffset(track));
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
        boolean wasHidden = indicator.getVisibility() != View.VISIBLE;
        mValue = normalized;
        persistString(normalized);
        updateIndicatorWidth(track, indicator);
        indicator.setVisibility(View.VISIBLE);
        float target = indicatorOffset(track);
        if (mIndicatorAnimator != null) mIndicatorAnimator.cancel();
        // Nothing to slide from when the pill was showing no segment at all.
        if (animate && track.isLaidOut() && !wasHidden) {
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
        int selected = resolveColor(com.termux.shared.R.attr.termuxColorOnPrimary,
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

    /** The lit segment, or -1 for {@link #VALUE_NONE}, where none of them is. */
    private int selectedIndex() {
        for (int i = 0; i < mValues.length; i++) {
            if (mValues[i].equals(mValue)) return i;
        }
        return VALUE_NONE.equals(mValue) ? -1 : 0;
    }

    /** Where the indicator rests: the lit segment, or the first one while it is hidden. */
    private float indicatorOffset(@NonNull FrameLayout track) {
        return Math.max(0, selectedIndex()) * segmentWidth(track);
    }

    @NonNull
    private String normalize(String value) {
        if (VALUE_NONE.equals(value)) return VALUE_NONE;
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
