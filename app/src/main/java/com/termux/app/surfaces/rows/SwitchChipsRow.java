package com.termux.app.surfaces.rows;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Switches as chips that wrap (spec #326): each setting a checkable pill, its glyph before its
 * name, lit while on. The chips fill a line and the next ones start the next, so a large font
 * only adds a line; a name longer than the row ellipsizes. A chip is a switch: tapping it toggles
 * and reports the new state, and restating a state reports nothing.
 *
 * <pre>
 * SwitchChipsRow chips = SwitchChipsRow.build(context,
 *     Arrays.asList(new SwitchChipsRow.Item(R.drawable.ic_editor_text_size, "Two-line labels")),
 *     (index, on) -> write(rows.get(index), on));
 * chips.setChecked(0, true);   // a restatement, not echoed
 * </pre>
 */
public final class SwitchChipsRow {

    /** A chip was toggled to {@code on}. */
    public interface OnToggle {
        void onToggle(int index, boolean on);
    }

    /** One chip: the setting's glyph (0 for none) and name. */
    public static final class Item {
        @DrawableRes public final int glyph;
        @NonNull public final CharSequence name;

        public Item(@DrawableRes int glyph, @NonNull CharSequence name) {
            this.glyph = glyph;
            this.name = name;
        }
    }

    @NonNull public final ChipGroup root;
    @NonNull public final List<Chip> chips;
    private boolean mRestating;

    private SwitchChipsRow(@NonNull ChipGroup root, @NonNull List<Chip> chips) {
        this.root = root;
        this.chips = Collections.unmodifiableList(chips);
    }

    @NonNull
    public static SwitchChipsRow build(@NonNull Context context, @NonNull List<Item> items,
                                       @NonNull OnToggle onToggle) {
        ChipGroup group = new ChipGroup(context);
        int gap = RowKit.dp(context, ChipsRow.GAP_DP);
        group.setChipSpacingHorizontal(gap);
        group.setChipSpacingVertical(gap);
        group.setSingleLine(false);
        // Several may be on at once: a switch each, not one choice of them.
        group.setSingleSelection(false);
        int edge = RowKit.dp(context, RowKit.EDGE_DP);
        group.setPadding(edge, RowKit.dp(context, 4), edge, RowKit.dp(context, 8));
        List<Chip> chips = new ArrayList<>(items.size());
        SwitchChipsRow row = new SwitchChipsRow(group, chips);
        ColorStateList glyphTint = ColorStateList.valueOf(RowKit.color(context,
            androidx.appcompat.R.attr.colorPrimary));
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            Chip chip = new Chip(context);
            chip.setText(item.name);
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            if (item.glyph != 0) {
                chip.setChipIconResource(item.glyph);
                chip.setChipIconVisible(true);
                chip.setChipIconTint(glyphTint);
            }
            chip.setCheckable(true);
            chip.setCheckedIconVisible(item.glyph == 0);
            chip.setEnsureMinTouchTargetSize(true);
            chip.setContentDescription(item.name);
            final int index = i;
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (!row.mRestating)
                    onToggle.onToggle(index, checked);
            });
            group.addView(chip, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            chips.add(chip);
        }
        return row;
    }

    /** Shows chip {@code index} on or off without reporting it. */
    public void setChecked(int index, boolean on) {
        Chip chip = chips.get(index);
        if (chip.isChecked() == on)
            return;
        mRestating = true;
        try {
            chip.setChecked(on);
        } finally {
            mRestating = false;
        }
    }

    /** Enables or greys chip {@code index}. */
    public void setEnabled(int index, boolean enabled) {
        Chip chip = chips.get(index);
        if (chip.isEnabled() != enabled)
            chip.setEnabled(enabled);
    }

    /** Shows or puts away chip {@code index}. */
    public void setShown(int index, boolean shown) {
        Chip chip = chips.get(index);
        int visibility = shown ? View.VISIBLE : View.GONE;
        if (chip.getVisibility() != visibility)
            chip.setVisibility(visibility);
    }
}
