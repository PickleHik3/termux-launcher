package com.termux.app.fragments.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.StaticLayout;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.content.res.AppCompatResources;
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
    /** True once a label did not fit side by side ({@link #labelsDoNotFit}): the segments then
     *  stack full-width so no label is cut or shrunk. Reset when the segment set changes. */
    private boolean mStacked;
    /** True once a label fitted only with the segments' side padding trimmed to
     *  {@link #COMPACT_PADDING_DP}: a large text size keeps the row on one line that way before
     *  it stacks. Reset when the segment set changes. */
    private boolean mCompact;
    /** The side padding a segment keeps around its label when the style's 12dp would push the
     *  row into a stack. */
    private static final int COMPACT_PADDING_DP = 4;

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
        mStacked = false;
        mCompact = false;
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
        mStacked = false;
        mCompact = false;
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
        // Every bind: the row's view may have been another pill's, trimmed or not.
        applyPadding(buttons, mCompact);
        applyStacking(group, buttons, mStacked);
        int selected = selectedIndex();
        if (selected < 0) group.clearChecked();
        else group.check(buttons[selected].getId());
        updateCheckedIcons(buttons, selected);
        // Fit is judged against the laid-out segment width, so it waits for the next layout.
        group.post(() -> fitAfterLayout(group, buttons));

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

    /**
     * The segments' side padding: the style's, kept on each button the first time it is seen, or
     * {@link #COMPACT_PADDING_DP}. Compares before setting.
     */
    private static void applyPadding(@NonNull MaterialButton[] buttons, boolean compact) {
        for (MaterialButton button : buttons) {
            Object kept = button.getTag(R.id.segmented_pill_track);
            int[] style;
            if (kept instanceof int[]) {
                style = (int[]) kept;
            } else {
                style = new int[] {button.getPaddingStart(), button.getPaddingEnd()};
                button.setTag(R.id.segmented_pill_track, style);
            }
            int side = Math.round(COMPACT_PADDING_DP
                * button.getResources().getDisplayMetrics().density);
            int start = compact ? Math.min(side, style[0]) : style[0];
            int end = compact ? Math.min(side, style[1]) : style[1];
            if (button.getPaddingStart() == start && button.getPaddingEnd() == end) continue;
            button.setPaddingRelative(start, button.getPaddingTop(), end,
                button.getPaddingBottom());
        }
    }

    /** Full-width stacked segments (vertical) or the weighted single row. */
    private static void applyStacking(@NonNull MaterialButtonToggleGroup group,
                                      @NonNull MaterialButton[] buttons, boolean stacked) {
        int orientation = stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL;
        if (group.getOrientation() == orientation) return;
        group.setOrientation(orientation);
        for (MaterialButton button : buttons) {
            ViewGroup.LayoutParams raw = button.getLayoutParams();
            if (!(raw instanceof LinearLayout.LayoutParams)) continue;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
            lp.width = stacked ? ViewGroup.LayoutParams.MATCH_PARENT : 0;
            lp.weight = stacked ? 0f : 1f;
            button.setLayoutParams(lp);
        }
    }

    /**
     * Whether some label cannot sit in its segment side by side: wider than the segment's content
     * width on one line (three or four segments), or more than two lines (two segments). The
     * width a label gets is the segment less its own padding only; the check mark gives way
     * ({@link #checkFits}) rather than pushing a label onto a second line. Segments not laid out
     * yet are skipped.
     */
    @VisibleForTesting
    static boolean labelsDoNotFit(@NonNull MaterialButton[] buttons, int segmentCount) {
        for (int i = 0; i < buttons.length && i < segmentCount; i++) {
            MaterialButton button = buttons[i];
            int available = contentWidth(button);
            if (available <= 0) continue;
            CharSequence label = button.getText();
            if (label == null || label.length() == 0) continue;
            if (segmentCount > 2) {
                if (button.getPaint().measureText(label, 0, label.length()) > available) return true;
                continue;
            }
            StaticLayout layout = StaticLayout.Builder
                .obtain(label, 0, label.length(), button.getPaint(), available)
                .build();
            if (layout.getLineCount() > 2) return true;
        }
        return false;
    }

    /** Whether the check mark and its padding fit beside the label on one line. */
    @VisibleForTesting
    static boolean checkFits(@NonNull MaterialButton button) {
        int available = contentWidth(button);
        if (available <= 0) return true;
        CharSequence label = button.getText();
        float text = label == null ? 0f : button.getPaint().measureText(label, 0, label.length());
        return text + button.getIconSize() + button.getIconPadding() <= available;
    }

    /** The width a label gets: the segment less its own padding (no icon slot). 0 before layout. */
    private static int contentWidth(@NonNull MaterialButton button) {
        int width = button.getWidth();
        if (width <= 0) return 0;
        return width - button.getPaddingLeft() - button.getPaddingRight();
    }

    private void fitAfterLayout(@NonNull MaterialButtonToggleGroup group,
                                @NonNull MaterialButton[] buttons) {
        if (group.getWidth() == 0) return;
        if (!mStacked && labelsDoNotFit(buttons, mValues.length) && !mCompact) {
            // The segments' widths do not move with their padding: trimmed, the labels are judged
            // again at once, and stack only when even that is not enough.
            mCompact = true;
            applyPadding(buttons, true);
            if (labelsDoNotFit(buttons, mValues.length)) {
                mCompact = false;
                applyPadding(buttons, false);
            }
        }
        if (!mStacked && labelsDoNotFit(buttons, mValues.length)) {
            mStacked = true;
            applyStacking(group, buttons, true);
            // The stacked segments are full width; the check is placed after that layout.
            group.post(() -> updateCheckedIcons(buttons, selectedIndex()));
            return;
        }
        updateCheckedIcons(buttons, selectedIndex());
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

    /**
     * Only the selected segment carries the check, and only when it fits beside the label; the
     * others carry no icon at all, so their labels get the whole segment and sit centred. (The
     * style's placeholder slot would take 22dp from every label.) Compares before setting, since
     * a new icon relays the button out.
     */
    private void updateCheckedIcons(@NonNull MaterialButton[] buttons, int selected) {
        for (int i = 0; i < buttons.length && i < mValues.length; i++) {
            MaterialButton button = buttons[i];
            boolean wanted = i == selected && checkFits(button);
            if (wanted == (button.getIcon() != null)) continue;
            button.setIcon(wanted
                ? AppCompatResources.getDrawable(button.getContext(), R.drawable.ic_symbol_check)
                : null);
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
