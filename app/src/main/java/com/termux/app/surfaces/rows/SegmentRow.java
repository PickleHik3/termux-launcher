package com.termux.app.surfaces.rows;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A segment row (editor-redesign §2: Position, Style, Type, Clock position, Contrast, Theme): an
 * outline glyph, a 16 sp title, and one choice of a few as segment pills in the app's pill style
 * ({@code Widget.Termux.Button.Segment}: white, no outline, the choice on the primary container,
 * no check mark). A segment shows a glyph alone (its name on long-press and to a screen reader)
 * or a word. The pills stand beside the title when they fit, and under it, across the width,
 * when they do not.
 */
public final class SegmentRow {

    /** A choice was made by touch. */
    public interface OnPick {
        void onPick(int index);
    }

    /** One segment: a glyph alone, or a word. */
    public static final class Segment {
        @NonNull public final CharSequence name;
        @DrawableRes public final int glyph;

        private Segment(@NonNull CharSequence name, @DrawableRes int glyph) {
            this.name = name;
            this.glyph = glyph;
        }

        /** A segment that reads {@code name}. */
        @NonNull
        public static Segment word(@NonNull CharSequence name) {
            return new Segment(name, 0);
        }

        /** A segment that shows {@code glyph} alone; {@code name} on long-press. */
        @NonNull
        public static Segment glyph(@DrawableRes int glyph, @NonNull CharSequence name) {
            return new Segment(name, glyph);
        }

        public boolean glyphOnly() {
            return glyph != 0;
        }
    }

    /** The gap between pills, as the card's segment rows have it. */
    static final int GAP_DP = 2;
    /** A word segment's side padding. */
    static final int WORD_PAD_DP = 8;
    /** The least room kept between the title and the pills beside it. */
    static final int TITLE_GAP_DP = 16;

    @NonNull public final LinearLayout root;
    @NonNull public final TextView title;
    @NonNull public final MaterialButtonToggleGroup group;
    @NonNull public final List<MaterialButton> buttons;
    /** Whether the pills stand beside the title. */
    public final boolean inline;
    private boolean mSetting;

    private SegmentRow(@NonNull LinearLayout root, @NonNull TextView title,
                       @NonNull MaterialButtonToggleGroup group,
                       @NonNull List<MaterialButton> buttons, boolean inline) {
        this.root = root;
        this.title = title;
        this.group = group;
        this.buttons = Collections.unmodifiableList(buttons);
        this.inline = inline;
    }

    /**
     * Builds the row for a card {@code widthPx} wide, {@code selected} chosen. {@code onPick}
     * hears touches only, never {@link #select}.
     */
    @NonNull
    public static SegmentRow build(@NonNull Context context, @DrawableRes int glyph,
                                   @NonNull CharSequence title, @NonNull List<Segment> segments,
                                   int selected, int widthPx, @NonNull OnPick onPick) {
        LayoutInflater inflater = LayoutInflater.from(context);
        List<MaterialButton> buttons = new ArrayList<>(segments.size());
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int controlPx = 0;
        for (Segment segment : segments) {
            MaterialButton button = (MaterialButton) inflater.inflate(R.layout.segment_button,
                null, false);
            button.setId(View.generateViewId());
            button.setMinWidth(0);
            button.setMinimumWidth(RowKit.dp(context, RowKit.TOUCH_DP));
            button.setMinHeight(RowKit.dp(context, RowKit.TOUCH_DP));
            if (segment.glyphOnly()) {
                button.setText(null);
                button.setIconResource(segment.glyph);
                button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
                button.setIconPadding(0);
                button.setIconSize(RowKit.dp(context, RowKit.GLYPH_DP));
                int pad = RowKit.dp(context, 12);
                button.setPaddingRelative(pad, button.getPaddingTop(), pad,
                    button.getPaddingBottom());
                button.setContentDescription(segment.name);
            } else {
                // No check mark: the container colour carries the choice, and a mark's room
                // would stand every unchosen word off its centre.
                button.setIcon(null);
                button.setText(segment.name);
                int pad = RowKit.dp(context, WORD_PAD_DP);
                button.setPaddingRelative(pad, button.getPaddingTop(), pad,
                    button.getPaddingBottom());
                button.setMaxLines(1);
                button.setEllipsize(TextUtils.TruncateAt.END);
            }
            GlyphNames.name(button, segment.name);
            button.measure(unspecified, unspecified);
            controlPx += button.getMeasuredWidth();
            buttons.add(button);
        }
        controlPx += RowKit.dp(context, GAP_DP) * Math.max(0, segments.size() - 1);

        TextView titleView = RowKit.title(context, title);
        float titlePx = titleView.getPaint().measureText(title.toString());
        boolean inline = fitsInline(widthPx, RowKit.dp(context, RowKit.ICON_COLUMN_DP),
            RowKit.dp(context, RowKit.EDGE_DP), titlePx, RowKit.dp(context, TITLE_GAP_DP),
            controlPx);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = RowKit.dp(context, 4);
        root.setPadding(0, pad, 0, inline ? pad : RowKit.dp(context, 8));
        LinearLayout line = RowKit.line(context);
        line.addView(RowKit.glyph(context, glyph, title), RowKit.glyphParams(context));
        line.addView(titleView, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialButtonToggleGroup group = new MaterialButtonToggleGroup(context);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        group.setSpacing(RowKit.dp(context, GAP_DP));
        group.setContentDescription(title);
        for (MaterialButton button : buttons) {
            group.addView(button, inline
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        if (inline) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginStart(RowKit.dp(context, TITLE_GAP_DP));
            line.addView(group, params);
        } else {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginStart(RowKit.dp(context, RowKit.ICON_COLUMN_DP));
            params.setMarginEnd(RowKit.dp(context, RowKit.EDGE_DP));
            root.addView(group, params);
        }
        SegmentRow row = new SegmentRow(root, titleView, group, buttons, inline);
        row.select(selected);
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (row.mSetting || !isChecked)
                return;
            for (int i = 0; i < buttons.size(); i++) {
                if (buttons.get(i).getId() == checkedId) {
                    onPick.onPick(i);
                    return;
                }
            }
        });
        return row;
    }

    /** Shows {@code index} chosen without reporting it; out of range leaves the row as it is. */
    public void select(int index) {
        if (index < 0 || index >= buttons.size())
            return;
        int id = buttons.get(index).getId();
        if (group.getCheckedButtonId() == id)
            return;
        mSetting = true;
        try {
            group.check(id);
        } finally {
            mSetting = false;
        }
    }

    /** The chosen index, or -1. */
    public int selected() {
        int id = group.getCheckedButtonId();
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).getId() == id)
                return i;
        }
        return -1;
    }

    /** The segment at {@code index}'s glyph or word, for a caller that restates its names. */
    @Nullable
    public CharSequence nameAt(int index) {
        return index < 0 || index >= buttons.size() ? null : GlyphNames.of(buttons.get(index));
    }

    /**
     * Whether the pills ({@code controlPx}) stand beside a title {@code titlePx} wide on a row
     * {@code rowPx} wide: the title starts at the lead column, keeps {@code gapPx} before the
     * pills, and the pills end at the {@code edgePx} gutter.
     */
    static boolean fitsInline(int rowPx, int leadPx, int edgePx, float titlePx, int gapPx,
                              int controlPx) {
        return leadPx + titlePx + gapPx + controlPx + edgePx <= rowPx;
    }
}
