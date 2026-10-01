package com.termux.app.fragments.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.termux.R;

/**
 * Inline segmented preference: a Material 3 segmented button over two to four labelled segments.
 * Defaults to the global Default / Rounded surface-shape pair; {@link #setSegments} swaps in another value
 * set (the third segment stays hidden until a three-value set is configured).
 *
 * <p>{@link #VALUE_NONE} is the one value with no segment of its own: no segment is
 * checked, which is how a row that stands for several stored values says they disagree.
 */
@Keep
public final class SegmentedPillPreference extends Preference {

    public static final String VALUE_DEFAULT = "default";
    public static final String VALUE_ROUNDED = "rounded";

    /**
     * The one value that is not a segment: no segment is checked. A row whose
     * store answers for several things at once — the Keyboard page's keyboard type, which stands
     * for every place — reads back as this when they disagree, so the pill says "these differ"
     * rather than picking one of them for the user. Tapping a segment still writes it to all.
     */
    public static final String VALUE_NONE = "";
    private static final String VALUE_LEGACY_VALARIE_CAPSULE = "valarie_capsule";

    private String[] mValues = {VALUE_DEFAULT, VALUE_ROUNDED};
    /** 0 keeps the label text the layout declares (or {@link #mLabelText}); anything else
     *  overrides it with a resource string. */
    private int[] mLabelResIds = {0, 0};
    /** Non-null entries win over {@link #mLabelResIds}; how {@link #setSegments(String[], CharSequence[])}
     *  and the XML {@code android:entries}/{@code android:entryValues} path supply labels. */
    private CharSequence[] mLabelText = {null, null};
    private static final int MAX_SEGMENTS = 4;
    private String mValue = VALUE_DEFAULT;

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
        View trackView = holder.findViewById(R.id.segmented_pill_track);
        MaterialButton[] buttons = findButtons(holder);
        if (!(trackView instanceof MaterialButtonToggleGroup) || buttons == null) return;
        MaterialButtonToggleGroup group = (MaterialButtonToggleGroup) trackView;

        // Rebinding must not echo the programmatic check back through the old listener.
        Object old = group.getTag(R.id.segmented_pill_track);
        if (old instanceof MaterialButtonToggleGroup.OnButtonCheckedListener) {
            group.removeOnButtonCheckedListener((MaterialButtonToggleGroup.OnButtonCheckedListener) old);
        }
        for (int i = 0; i < buttons.length; i++) {
            MaterialButton button = buttons[i];
            if (i >= mValues.length) {
                button.setVisibility(View.GONE);
                continue;
            }
            button.setVisibility(View.VISIBLE);
            if (mLabelText[i] != null) button.setText(mLabelText[i]);
            else if (mLabelResIds[i] != 0) button.setText(mLabelResIds[i]);
            button.setEnabled(isEnabled());
        }
        group.setContentDescription(getTitle());
        int selected = selectedIndex();
        if (selected < 0) group.clearChecked();
        else group.check(buttons[selected].getId());
        updateCheckedIcons(buttons, selected);

        MaterialButtonToggleGroup.OnButtonCheckedListener listener = (g, checkedId, isChecked) -> {
            if (!isChecked) return;
            int index = indexOfButton(buttons, checkedId);
            if (index < 0 || index >= mValues.length) return;
            String normalized = normalize(mValues[index]);
            if (normalized.equals(mValue)) return;
            if (!callChangeListener(normalized)) {
                // Vetoed: put the group back on the stored value.
                int previous = selectedIndex();
                if (previous < 0) g.clearChecked();
                else g.check(buttons[previous].getId());
                return;
            }
            mValue = normalized;
            persistString(normalized);
            updateCheckedIcons(buttons, selectedIndex());
        };
        group.addOnButtonCheckedListener(listener);
        group.setTag(R.id.segmented_pill_track, listener);
    }

    private MaterialButton[] findButtons(@NonNull PreferenceViewHolder holder) {
        View first = holder.findViewById(R.id.segmented_pill_default);
        View second = holder.findViewById(R.id.segmented_pill_capsule);
        View third = holder.findViewById(R.id.segmented_pill_third);
        View fourth = holder.findViewById(R.id.segmented_pill_fourth);
        if (!(first instanceof MaterialButton) || !(second instanceof MaterialButton)
            || !(third instanceof MaterialButton) || !(fourth instanceof MaterialButton)) return null;
        return new MaterialButton[]{(MaterialButton) first, (MaterialButton) second,
            (MaterialButton) third, (MaterialButton) fourth};
    }

    private static int indexOfButton(@NonNull MaterialButton[] buttons, int id) {
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i].getId() == id) return i;
        }
        return -1;
    }

    /** The M3 segmented button shows a check on the selected segment. */
    private void updateCheckedIcons(@NonNull MaterialButton[] buttons, int selected) {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setIconResource(i == selected ? R.drawable.ic_symbol_check : 0);
            buttons[i].setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        }
    }

    /** The lit segment, or -1 for {@link #VALUE_NONE}, where none of them is. */
    private int selectedIndex() {
        for (int i = 0; i < mValues.length; i++) {
            if (mValues[i].equals(mValue)) return i;
        }
        return VALUE_NONE.equals(mValue) ? -1 : 0;
    }

    /** The index of the lit segment, or -1 for {@link #VALUE_NONE}. */
    @VisibleForTesting
    public int selectedSegment() {
        return selectedIndex();
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
