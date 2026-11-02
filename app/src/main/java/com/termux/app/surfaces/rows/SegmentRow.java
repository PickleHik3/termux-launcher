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
 * outline glyph, a 16 sp title, and one choice of a few as a Material 3 Expressive connected
 * button group ({@code Widget.Termux.Editor.ConnectedGroup}: tonal buttons 2dp apart, the choice
 * filled with the primary and fully round, no outline, no check mark). A segment shows a glyph alone (its name on long-press and to a screen reader)
 * or a word. The pills stand beside the title when they fit, and under it, across the width,
 * when they do not.
 */
public final class SegmentRow {

    /** A choice was made by touch. */
    public interface OnPick {
        void onPick(int index);
    }

    /** One segment: a glyph alone, a word, or a glyph before its word. */
    public static final class Segment {
        @NonNull public final CharSequence name;
        @DrawableRes public final int glyph;
        private final boolean mWithWord;

        private Segment(@NonNull CharSequence name, @DrawableRes int glyph, boolean withWord) {
            this.name = name;
            this.glyph = glyph;
            mWithWord = withWord;
        }

        /** A segment that reads {@code name}. */
        @NonNull
        public static Segment word(@NonNull CharSequence name) {
            return new Segment(name, 0, true);
        }

        /** A segment that shows {@code glyph} alone; {@code name} on long-press. */
        @NonNull
        public static Segment glyph(@DrawableRes int glyph, @NonNull CharSequence name) {
            return new Segment(name, glyph, false);
        }

        /** A segment that shows {@code glyph} before the word {@code name} (the Dock's Style). */
        @NonNull
        public static Segment both(@DrawableRes int glyph, @NonNull CharSequence name) {
            return new Segment(name, glyph, true);
        }

        public boolean glyphOnly() {
            return glyph != 0 && !mWithWord;
        }
    }

    /** The gap between pills, as the card's segment rows have it. */
    static final int GAP_DP = 2;
    /** A word segment's side padding. */
    static final int WORD_PAD_DP = 8;
    /** The glyph before a word, and the gap after it. */
    static final int WORD_GLYPH_DP = 18;
    static final int WORD_GLYPH_GAP_DP = 6;
    /** The least room kept between the title and the pills beside it. */
    static final int TITLE_GAP_DP = 16;
    /** The least room above and below a group, so groups in a run never touch. */
    static final int GROUP_GAP_DP = 4;

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
        int widestPx = 0;
        for (Segment segment : segments) {
            MaterialButton button = (MaterialButton) inflater.inflate(
                R.layout.editor_connected_button, null, false);
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
                // would stand every unchosen word off its centre. A glyph before the word takes
                // the mark's place on every segment alike.
                if (segment.glyph != 0) {
                    button.setIconResource(segment.glyph);
                    button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
                    button.setIconSize(RowKit.dp(context, WORD_GLYPH_DP));
                    button.setIconPadding(RowKit.dp(context, WORD_GLYPH_GAP_DP));
                } else {
                    button.setIcon(null);
                }
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
            widestPx = Math.max(widestPx, button.getMeasuredWidth());
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

        MaterialButtonToggleGroup group = connectedGroup(context);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        group.setSpacing(RowKit.dp(context, GAP_DP));
        group.setContentDescription(title);
        // Under the title, the pills share the width; where a word would not fit its share
        // (a large text size), they stand one under another instead, so no word is cut.
        if (!inline) {
            // Under the title the words carry the choice alone: a glyph before a word gives up
            // its room, so a share fits the word whole (Floating at 1.3×).
            widestPx = 0;
            for (int i = 0; i < buttons.size(); i++) {
                MaterialButton button = buttons.get(i);
                if (!segments.get(i).glyphOnly() && segments.get(i).glyph != 0) {
                    button.setIcon(null);
                    button.measure(unspecified, unspecified);
                }
                widestPx = Math.max(widestPx, button.getMeasuredWidth());
            }
        }
        boolean vertical = !inline && stacksVertically(widthPx,
            RowKit.dp(context, RowKit.ICON_COLUMN_DP), RowKit.dp(context, RowKit.EDGE_DP),
            RowKit.dp(context, GAP_DP), segments.size(), widestPx);
        if (vertical)
            group.setOrientation(LinearLayout.VERTICAL);
        for (MaterialButton button : buttons) {
            if (vertical) {
                group.addView(button, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                continue;
            }
            group.addView(button, inline
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        if (inline) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginStart(RowKit.dp(context, TITLE_GAP_DP));
            // 4dp above and below: two one-of rows in a run keep at least 8dp between
            // their groups, whatever the button's own height (W1a emulator check).
            params.topMargin = RowKit.dp(context, GROUP_GAP_DP);
            params.bottomMargin = RowKit.dp(context, GROUP_GAP_DP);
            line.addView(group, params);
        } else {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginStart(RowKit.dp(context, RowKit.ICON_COLUMN_DP));
            params.setMarginEnd(RowKit.dp(context, RowKit.EDGE_DP));
            params.bottomMargin = RowKit.dp(context, GROUP_GAP_DP);
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

    /**
     * The editor's connected button group (appearance-cards overhaul §4): Material 3 Expressive,
     * 2dp apart, inner corners 8dp / 4dp pressed / round on the chosen one. Its buttons come from
     * {@link #connectedButton}. Settings pages keep their own segment styles.
     */
    @NonNull
    public static MaterialButtonToggleGroup connectedGroup(@NonNull Context context) {
        return (MaterialButtonToggleGroup) LayoutInflater.from(context).inflate(
            R.layout.editor_connected_group, null, false);
    }

    /** One button of a {@link #connectedGroup}: tonal, filled when chosen, no check mark. */
    @NonNull
    public static MaterialButton connectedButton(@NonNull Context context) {
        return (MaterialButton) LayoutInflater.from(context).inflate(
            R.layout.editor_connected_button, null, false);
    }

    /** The side padding of a word button in a header group that sizes by its words. */
    public static final int FIT_PAD_DP = 12;

    /**
     * Weighs each button of a connected group by the width its word needs (its text and side
     * padding), so a group narrower than the sum shares the room in proportion and a wide enough
     * one shows every word whole. Words are never cut: no ellipsis.
     */
    public static void weighByText(@NonNull List<MaterialButton> buttons) {
        for (MaterialButton button : buttons) {
            button.setEllipsize(null);
            ViewGroup.LayoutParams params = button.getLayoutParams();
            if (!(params instanceof LinearLayout.LayoutParams))
                continue;
            LinearLayout.LayoutParams linear = (LinearLayout.LayoutParams) params;
            linear.width = 0;
            linear.weight = Math.max(1f, neededPx(button));
            button.setLayoutParams(linear);
        }
    }

    /** The width {@code button}'s word needs whole: its text, padding and icon room. */
    public static float neededPx(@NonNull MaterialButton button) {
        CharSequence text = button.getText();
        float word = text == null ? 0f : button.getPaint().measureText(text.toString());
        return word + button.getPaddingStart() + button.getPaddingEnd() + 1f;
    }

    /** Whether every word of {@code buttons} stands whole in a group {@code groupPx} wide. */
    public static boolean fitsWhole(@NonNull List<MaterialButton> buttons, int groupPx,
                                    int gapPx) {
        float need = gapPx * Math.max(0, buttons.size() - 1);
        for (MaterialButton button : buttons)
            need += neededPx(button);
        return need <= groupPx;
    }

    /** A row of word segments ({@link Segment#word}), as {@link #build} lays any row. */
    @NonNull
    public static SegmentRow words(@NonNull Context context, @DrawableRes int glyph,
                                   @NonNull CharSequence title, @NonNull CharSequence[] words,
                                   int selected, int widthPx, @NonNull OnPick onPick) {
        List<Segment> segments = new ArrayList<>(words.length);
        for (CharSequence word : words)
            segments.add(Segment.word(word));
        return build(context, glyph, title, segments, selected, widthPx, onPick);
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

    /**
     * Whether {@code count} pills under the title stand one under another: the widest
     * ({@code widestPx}) is wider than its share of the row from the lead column to the
     * {@code edgePx} gutter, {@code gapPx} between pills.
     */
    static boolean stacksVertically(int rowPx, int leadPx, int edgePx, int gapPx, int count,
                                    int widestPx) {
        if (count <= 1)
            return false;
        int share = (rowPx - leadPx - edgePx - gapPx * (count - 1)) / count;
        return widestPx > share;
    }
}
