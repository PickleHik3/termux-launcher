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
    /**
     * Whether the pills stand beside the title: a first guess at build, then what the last
     * measure decided with the width the row really got.
     */
    public boolean inline;
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

        MaterialButtonToggleGroup group = connectedGroup(context);
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        group.setSpacing(RowKit.dp(context, GAP_DP));
        group.setContentDescription(title);
        for (MaterialButton button : buttons)
            group.addView(button);

        TextView titleView = RowKit.title(context, title);
        // A row's title is never cut: it wraps to a second line where even alone it cannot fit.
        titleView.setEllipsize(null);
        titleView.setSingleLine(false);
        titleView.setMaxLines(2);
        float titlePx = titleView.getPaint().measureText(title.toString());
        // The first guess, for callers that read it before a layout; the layout decides.
        boolean inline = fitsInline(widthPx, RowKit.dp(context, RowKit.ICON_COLUMN_DP),
            RowKit.dp(context, RowKit.EDGE_DP), titlePx, RowKit.dp(context, TITLE_GAP_DP),
            controlPx);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        Body body = new Body(context, RowKit.glyph(context, glyph, title), titleView, group,
            buttons, segments);
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        SegmentRow row = new SegmentRow(root, titleView, group, buttons, inline);
        body.mRow = row;
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

    /**
     * The row's glyph, title and group, laid out by what the row really gets: on every measure
     * the title's whole text and the group as it draws are set against the given width, and the
     * group stands beside the title only where both fit with the 16dp gap between them;
     * otherwise it goes under the title across the width, word glyphs dropped, its pills sharing
     * the width or one under another where a word would not fit its share. The title is never
     * ellipsized.
     */
    /** Whether {@code group}, a row's pills, stood beside its title at the last layout. */
    public static boolean standsInline(@NonNull View group) {
        android.view.ViewParent parent = group.getParent();
        return parent instanceof Body && ((Body) parent).isInline();
    }

    static final class Body extends ViewGroup {
        @NonNull private final View mGlyph;
        @NonNull private final TextView mTitle;
        @NonNull private final MaterialButtonToggleGroup mGroup;
        @NonNull private final List<MaterialButton> mButtons;
        @NonNull private final List<Segment> mSegments;
        @NonNull private final android.graphics.drawable.Drawable[] mIcons;
        @Nullable SegmentRow mRow;
        private boolean mInline = true;
        private boolean mVertical;
        private int mGroupTop;
        /** The group's width beside the title, as last measured there; -1 before. */
        private int mInlineGroupPx = -1;

        Body(@NonNull Context context, @NonNull View glyph, @NonNull TextView title,
             @NonNull MaterialButtonToggleGroup group, @NonNull List<MaterialButton> buttons,
             @NonNull List<Segment> segments) {
            super(context);
            mGlyph = glyph;
            mTitle = title;
            mGroup = group;
            mButtons = buttons;
            mSegments = segments;
            mIcons = new android.graphics.drawable.Drawable[buttons.size()];
            for (int i = 0; i < buttons.size(); i++)
                mIcons[i] = buttons.get(i).getIcon();
            addView(glyph);
            addView(title);
            addView(group);
        }

        boolean isInline() {
            return mInline;
        }

        /**
         * Puts the group beside the title or under it; glyphs and shares follow. Only a change
         * touches the buttons, so a measure that decides the same again requests nothing.
         */
        void setInline(boolean inline) {
            if (mRow != null) mRow.inline = inline;
            if (mInline == inline)
                return;
            mInline = inline;
            if (mRow != null) mRow.inline = inline;
            for (int i = 0; i < mButtons.size(); i++) {
                MaterialButton button = mButtons.get(i);
                Segment segment = mSegments.get(i);
                // Under the title the words carry the choice alone.
                if (!segment.glyphOnly() && segment.glyph != 0) {
                    android.graphics.drawable.Drawable icon = inline ? mIcons[i] : null;
                    if (button.getIcon() != icon) button.setIcon(icon);
                }
            }
            mVertical = false;
            applyParams();
        }

        private void setVertical(boolean vertical) {
            if (mVertical == vertical)
                return;
            mVertical = vertical;
            applyParams();
        }

        /** The group's direction and each pill's params for the state the row is in. */
        private void applyParams() {
            boolean vertical = mVertical;
            mGroup.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            for (MaterialButton button : mButtons) {
                LinearLayout.LayoutParams params;
                if (mInline)
                    params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                else if (vertical)
                    params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                else
                    params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f);
                button.setLayoutParams(params);
            }
        }

        private int dp(float value) {
            return RowKit.dp(getContext(), value);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int lead = dp(RowKit.ICON_COLUMN_DP);
            int edge = dp(RowKit.EDGE_DP);
            int gap = dp(TITLE_GAP_DP);
            int groupGap = dp(GROUP_GAP_DP);
            int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            int glyphPx = dp(RowKit.GLYPH_DP);
            mGlyph.measure(MeasureSpec.makeMeasureSpec(glyphPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(glyphPx, MeasureSpec.EXACTLY));
            float titleNeed = mTitle.getPaint().measureText(String.valueOf(mTitle.getText()))
                + mTitle.getPaddingLeft() + mTitle.getPaddingRight();
            int titleNeedPx = (int) Math.ceil(titleNeed);

            // The group as it draws beside the title (glyphs on, each pill its own width),
            // measured while it stands there; under the title the last such width stands for it.
            if (mInline) {
                mGroup.measure(unspecified, unspecified);
                mInlineGroupPx = mGroup.getMeasuredWidth();
            }
            int groupPx = mInlineGroupPx;
            boolean inline = width > 0 && groupPx >= 0
                && lead + titleNeedPx + gap + groupPx + edge <= width;
            if (inline != mInline) {
                setInline(inline);
                if (inline) {
                    mGroup.measure(unspecified, unspecified);
                    mInlineGroupPx = groupPx = mGroup.getMeasuredWidth();
                }
            }

            int rowMin = dp(RowKit.ROW_MIN_DP);
            int height;
            if (inline) {
                int titleRoom = Math.max(0, width - lead - edge - gap - groupPx);
                mTitle.measure(MeasureSpec.makeMeasureSpec(titleRoom, MeasureSpec.EXACTLY),
                    unspecified);
                int groupH = mGroup.getMeasuredHeight() + 2 * groupGap;
                height = Math.max(rowMin, Math.max(mTitle.getMeasuredHeight(), groupH))
                    + 2 * dp(4);
                mGroupTop = (height - mGroup.getMeasuredHeight()) / 2;
            } else {
                int run = Math.max(0, width - lead - edge);
                mTitle.measure(MeasureSpec.makeMeasureSpec(run, MeasureSpec.AT_MOST), unspecified);
                // The pills share the run; a word wider than its share stands them in a column.
                int widest = 0;
                for (MaterialButton button : mButtons) {
                    button.measure(unspecified, unspecified);
                    widest = Math.max(widest, button.getMeasuredWidth());
                }
                setVertical(stacksVertically(width, lead, edge, dp(GAP_DP), mButtons.size(),
                    widest));
                mGroup.measure(MeasureSpec.makeMeasureSpec(run, MeasureSpec.EXACTLY), unspecified);
                int lineH = Math.max(rowMin, mTitle.getMeasuredHeight());
                mGroupTop = dp(4) + lineH;
                height = mGroupTop + mGroup.getMeasuredHeight() + groupGap + dp(8);
            }
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l;
            int lead = dp(RowKit.ICON_COLUMN_DP);
            int edge = dp(RowKit.EDGE_DP);
            int lineTop = dp(4);
            int lineH = mInline ? (b - t) - 2 * dp(4)
                : Math.max(dp(RowKit.ROW_MIN_DP), mTitle.getMeasuredHeight());
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            int glyphW = mGlyph.getMeasuredWidth();
            int glyphTop = lineTop + (lineH - mGlyph.getMeasuredHeight()) / 2;
            int glyphLeft = rtl ? width - edge - glyphW : edge;
            mGlyph.layout(glyphLeft, glyphTop, glyphLeft + glyphW,
                glyphTop + mGlyph.getMeasuredHeight());
            int titleW = mTitle.getMeasuredWidth();
            int titleTop = lineTop + (lineH - mTitle.getMeasuredHeight()) / 2;
            int titleLeft = rtl ? width - lead - titleW : lead;
            mTitle.layout(titleLeft, titleTop, titleLeft + titleW,
                titleTop + mTitle.getMeasuredHeight());
            int groupW = mGroup.getMeasuredWidth();
            int groupLeft = mInline ? (rtl ? edge : width - edge - groupW)
                : (rtl ? width - lead - groupW : lead);
            mGroup.layout(groupLeft, mGroupTop, groupLeft + groupW,
                mGroupTop + mGroup.getMeasuredHeight());
        }
    }
}
