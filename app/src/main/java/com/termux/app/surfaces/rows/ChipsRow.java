package com.termux.app.surfaces.rows;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A chips row (editor-redesign §2: More): pills that open other pages (Theme, Layouts, Font,
 * Global, Gestures, Edit pages, Icon pack, Tool colours), each a glyph and a word, wrapping onto
 * as many lines as they need.
 */
public final class ChipsRow {

    /** A chip was tapped. */
    public interface OnOpen {
        void onOpen(int index);
    }

    /** One chip: the page's glyph and name. */
    public static final class Link {
        @DrawableRes public final int glyph;
        @NonNull public final CharSequence name;

        public Link(@DrawableRes int glyph, @NonNull CharSequence name) {
            this.glyph = glyph;
            this.name = name;
        }
    }

    static final int GAP_DP = 8;

    @NonNull public final ChipGroup root;
    @NonNull public final List<Chip> chips;

    private ChipsRow(@NonNull ChipGroup root, @NonNull List<Chip> chips) {
        this.root = root;
        this.chips = Collections.unmodifiableList(chips);
    }

    @NonNull
    public static ChipsRow build(@NonNull Context context, @NonNull List<Link> links,
                                 @NonNull OnOpen onOpen) {
        ChipGroup group = new ChipGroup(context);
        int gap = RowKit.dp(context, GAP_DP);
        group.setChipSpacingHorizontal(gap);
        group.setChipSpacingVertical(gap);
        group.setSingleLine(false);
        int edge = RowKit.dp(context, RowKit.EDGE_DP);
        group.setPadding(edge, RowKit.dp(context, 4), edge, RowKit.dp(context, 8));
        List<Chip> chips = new ArrayList<>(links.size());
        ColorStateList glyphTint = ColorStateList.valueOf(RowKit.color(context,
            androidx.appcompat.R.attr.colorPrimary));
        for (int i = 0; i < links.size(); i++) {
            Link link = links.get(i);
            Chip chip = new Chip(context);
            chip.setText(link.name);
            chip.setChipIconResource(link.glyph);
            chip.setChipIconVisible(true);
            chip.setChipIconTint(glyphTint);
            chip.setCheckable(false);
            chip.setEnsureMinTouchTargetSize(true);
            final int index = i;
            chip.setOnClickListener(v -> onOpen.onOpen(index));
            group.addView(chip, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            chips.add(chip);
        }
        return new ChipsRow(group, chips);
    }
}
