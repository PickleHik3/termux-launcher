package com.termux.app.surfaces.rows;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A long choice folded into a menu that opens in place (spec #326: Cursor trail, Clock face):
 * one 56dp line holding the glyph, the name, the chosen word and a chevron; tapping it opens the
 * choices under it as chips that wrap, and choosing one reports it and folds the list again. It
 * opens inside the card, never as a popup, so the card keeps its scroll position. The name and
 * the chosen word are single lines that ellipsize. A restatement ({@link #select}) changes the
 * word and the chosen chip and reports nothing.
 *
 * <pre>
 * FoldChoiceRow fold = FoldChoiceRow.build(context, R.drawable.ic_symbol_tune, "Cursor trail",
 *     Arrays.asList("Off", "Default", "Motion", "Railgun"), selected, index -> write(index));
 * </pre>
 */
public final class FoldChoiceRow {

    /** A choice was made. */
    public interface OnPick {
        void onPick(int index);
    }

    @NonNull public final LinearLayout root;
    @NonNull public final LinearLayout line;
    @NonNull public final TextView title;
    @NonNull public final TextView current;
    @NonNull public final ImageView chevron;
    @NonNull public final ChipGroup list;
    @NonNull public final List<Chip> chips;
    @NonNull private final List<CharSequence> mNames;
    private boolean mOpen;
    private boolean mSelecting;
    private int mSelected = -1;

    private FoldChoiceRow(@NonNull LinearLayout root, @NonNull LinearLayout line,
                          @NonNull TextView title, @NonNull TextView current,
                          @NonNull ImageView chevron, @NonNull ChipGroup list,
                          @NonNull List<Chip> chips, @NonNull List<CharSequence> names) {
        this.root = root;
        this.line = line;
        this.title = title;
        this.current = current;
        this.chevron = chevron;
        this.list = list;
        this.chips = Collections.unmodifiableList(chips);
        mNames = names;
    }

    @NonNull
    public static FoldChoiceRow build(@NonNull Context context, @DrawableRes int glyph,
                                      @NonNull CharSequence name,
                                      @NonNull List<? extends CharSequence> choices, int selected,
                                      @NonNull OnPick onPick) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout line = RowKit.line(context);
        line.addView(RowKit.glyph(context, glyph, name), RowKit.glyphParams(context));
        TextView title = RowKit.title(context, name);
        title.setMinWidth(0);
        line.addView(title, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView current = RowKit.title(context, "");
        RowKit.textAppearance(current, com.google.android.material.R.attr.textAppearanceBodyLarge);
        current.setTextColor(RowKit.color(context, androidx.appcompat.R.attr.colorPrimary));
        current.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        current.setMinWidth(0);
        // The chosen word never takes more than half the line: the name keeps the rest.
        int half = context.getResources().getDisplayMetrics().widthPixels / 2;
        current.setMaxWidth(half);
        LinearLayout.LayoutParams currentParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        currentParams.setMarginStart(RowKit.dp(context, 8));
        line.addView(current, currentParams);
        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.ic_editor_expand_more);
        chevron.setImageTintList(ColorStateList.valueOf(RowKit.color(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant)));
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int glyphPx = RowKit.dp(context, RowKit.GLYPH_DP);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(glyphPx, glyphPx);
        chevronParams.setMarginStart(RowKit.dp(context, 4));
        line.addView(chevron, chevronParams);
        line.setClickable(true);
        line.setFocusable(true);
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        ChipGroup list = new ChipGroup(context);
        int gap = RowKit.dp(context, ChipsRow.GAP_DP);
        list.setChipSpacingHorizontal(gap);
        list.setChipSpacingVertical(gap);
        list.setSingleLine(false);
        list.setSingleSelection(true);
        list.setSelectionRequired(true);
        // Under the title: the chips start at the lead column.
        list.setPadding(RowKit.dp(context, RowKit.ICON_COLUMN_DP), 0,
            RowKit.dp(context, RowKit.EDGE_DP), RowKit.dp(context, 8));
        list.setVisibility(View.GONE);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        List<CharSequence> names = new ArrayList<>(choices);
        List<Chip> chips = new ArrayList<>(names.size());
        FoldChoiceRow row = new FoldChoiceRow(root, line, title, current, chevron, list, chips,
            names);
        for (int i = 0; i < names.size(); i++) {
            Chip chip = new Chip(context);
            chip.setText(names.get(i));
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setEnsureMinTouchTargetSize(true);
            chip.setId(View.generateViewId());
            final int index = i;
            chip.setOnClickListener(v -> {
                if (row.mSelecting)
                    return;
                row.select(index);
                row.setOpen(false);
                onPick.onPick(index);
            });
            list.addView(chip, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            chips.add(chip);
        }
        line.setOnClickListener(v -> row.setOpen(!row.mOpen));
        row.select(selected);
        row.dressLine();
        return row;
    }

    /** Whether the list stands open. */
    public boolean isOpen() {
        return mOpen;
    }

    /** Opens or folds the list in place; nothing is reported. */
    public void setOpen(boolean open) {
        if (mOpen == open)
            return;
        mOpen = open;
        list.setVisibility(open ? View.VISIBLE : View.GONE);
        dressLine();
    }

    /** Shows choice {@code index} as the chosen one, without reporting it. */
    public void select(int index) {
        int at = Math.max(0, Math.min(mNames.size() - 1, index));
        if (at == mSelected || mNames.isEmpty())
            return;
        mSelected = at;
        mSelecting = true;
        try {
            list.check(chips.get(at).getId());
        } finally {
            mSelecting = false;
        }
        RowKit.setText(current, mNames.get(at));
        dressLine();
    }

    public int selected() {
        return mSelected;
    }

    /** The line's rotation and spoken state follow the open state and the chosen word. */
    private void dressLine() {
        float rotation = mOpen ? 180f : 0f;
        if (chevron.getRotation() != rotation)
            chevron.setRotation(rotation);
        line.setContentDescription(title.getText() + ", " + current.getText());
    }
}
