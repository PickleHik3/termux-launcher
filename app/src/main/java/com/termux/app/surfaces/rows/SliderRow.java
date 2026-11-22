package com.termux.app.surfaces.rows;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.surfaces.LegendSlider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A run of value sliders, one row under another (the cards' sliders, horizontal since
 * 2026-10-31; they were a strip of vertical columns before). A row is one line where it fits
 * (2026-11-04): the glyph, the name, and a thick track that prints its value inside at its end,
 * in a 48dp hit area; then a fixed slot at the row's end where the reset arrow (↺) stands while
 * the value is the surface's own, empty otherwise, so every track is one length and the arrows
 * stand in one column (appearance-one-look spec §2). Where the name or the value would not fit at
 * the row's width and font scale ({@link SliderRowFit}), the row keeps two lines: the glyph,
 * name and value, and under them the track, the row's whole width. The row's height is
 * measured, so a larger font scale grows the name line rather than cutting it.
 *
 * <p>A horizontal drag on a track takes the slider at once, a vertical one scrolls the card, and
 * a tap moves the handle where it lands ({@link StripTouchPolicy#decideAcross}). A long press on
 * the name is the row's menu ({@link OnName}).</p>
 *
 * <p>Two neighbouring sliders may be linked (the keyboard's Background and Key opacity): the
 * Linked toggle stands between their rows, centred and about half the row wide (appearance-cards
 * overhaul §4), a tonal button that reads "Linked" with the link glyph, filled, while on and
 * "Not linked" with link_off while off; what the link does is the caller's.</p>
 */
public final class SliderRow {

    /** A slider's value, as it prints ("20%", "24 dp"). */
    public interface Readout {
        @NonNull String format(int value);
    }

    /** What a slider reports. */
    public interface Listener {
        /** A slider moved; {@code dragging} while the finger is still on it. */
        void onValue(int index, int value, boolean dragging);

        /** A slider's finger lifted. */
        void onReleased(int index);

        /** Slider {@code index}'s reset arrow was tapped: its value goes back to Global. */
        default void onReset(int index) {}
    }

    /** The link between two sliders was turned on or off by touch. */
    public interface OnLink {
        void onLink(boolean on);
    }

    /** A long press on a slider's name: the caller opens the row's menu at {@code anchor}. */
    public interface OnName {
        void onName(int index, @NonNull View anchor);
    }

    /** One slider: its glyph, name, range and readout. */
    public static final class Track {
        @DrawableRes public final int glyph;
        @NonNull public final CharSequence name;
        public final int from;
        public final int to;
        public final int step;
        @NonNull public final Readout readout;

        public Track(@DrawableRes int glyph, @NonNull CharSequence name, int from, int to, int step,
                     @NonNull Readout readout) {
            this.glyph = glyph;
            this.name = name;
            this.from = from;
            this.to = Math.max(from + 1, to);
            this.step = Math.max(1, step);
            this.readout = readout;
        }
    }

    /** One built row: its root, glyph, name, value and track. */
    public static final class Item {
        @NonNull public final Line root;
        @NonNull public final ImageView glyph;
        @NonNull public final TextView name;
        @NonNull public final TextView value;
        @NonNull public final LegendSlider slider;
        /** The reset arrow in the row's end slot: shown while the value is the surface's own. */
        @NonNull public final ImageView reset;
        @NonNull private final CharSequence mName;

        Item(@NonNull Line root, @NonNull ImageView glyph, @NonNull TextView name,
             @NonNull TextView value, @NonNull LegendSlider slider, @NonNull ImageView reset,
             @NonNull CharSequence label) {
            this.root = root;
            this.glyph = glyph;
            this.name = name;
            this.value = value;
            this.slider = slider;
            this.reset = reset;
            mName = label;
        }

        /** Whether the finger is on this slider now. */
        public boolean isDragging() {
            return slider.isDragging();
        }

        /**
         * Whether the value is the surface's own: the arrow shows in the end slot (the slot keeps
         * its room either way), and a screen reader hears {@code spoken} for the slider, the
         * row's state ("Opacity, the dock's own"), or the plain name for null. Compares first.
         */
        public void setOwn(boolean own, @Nullable CharSequence spoken) {
            int visibility = own ? View.VISIBLE : View.INVISIBLE;
            if (reset.getVisibility() != visibility)
                reset.setVisibility(visibility);
            CharSequence said = spoken != null ? spoken : mName;
            if (!TextUtils.equals(slider.getContentDescription(), said))
                slider.setContentDescription(said);
        }
    }

    /** The reset arrow's slot at the row's end: one touch target wide, on every row. */
    static final int RESET_SLOT_DP = RowKit.TOUCH_DP;
    /** The gap between the name and the value. */
    static final int VALUE_GAP_DP = 8;
    /** Above a two-line row's name line; the track's 48dp hit area keeps its own room below. */
    static final int ROW_TOP_DP = 8;
    /** Above and below a one-line row's 48dp hit area. */
    static final int ONE_LINE_PAD_DP = 4;

    @NonNull public final LinearLayout root;
    @NonNull public final List<Item> items;
    /** The link between two sliders, or null where the run has none. */
    @Nullable public final MaterialButton link;
    /** The row the link stands centred in, half its width; null where the run has none. */
    @Nullable private final LinearLayout mLinkHolder;
    @NonNull private final List<Track> mTracks;
    private final int mLinkAfter;
    private boolean mSettingLink;

    private SliderRow(@NonNull LinearLayout root, @NonNull List<Item> items,
                      @NonNull List<Track> tracks, @Nullable MaterialButton link, int linkAfter) {
        this.root = root;
        this.items = Collections.unmodifiableList(items);
        this.link = link;
        mLinkHolder = link == null ? null : (LinearLayout) link.getParent();
        mTracks = tracks;
        mLinkAfter = linkAfter;
    }

    /** Builds the sliders at {@code values}. */
    @NonNull
    public static SliderRow build(@NonNull Context context, @NonNull List<Track> tracks,
                                  @NonNull int[] values, @NonNull Listener listener) {
        return build(context, tracks, values, listener, -1, "", false, null, null);
    }

    /**
     * Builds the sliders with a link between slider {@code linkAfter} and the next, named
     * {@code linkName} (its long-press and its spoken name), on while {@code linked}. A
     * {@code linkAfter} with no slider after it builds no link. {@code onName} hears a long press
     * on a slider's name.
     */
    @NonNull
    public static SliderRow build(@NonNull Context context, @NonNull List<Track> tracks,
                                  @NonNull int[] values, @NonNull Listener listener,
                                  int linkAfter, @NonNull CharSequence linkName, boolean linked,
                                  @Nullable OnLink onLink, @Nullable OnName onName) {
        List<Track> held = new ArrayList<>(tracks);
        boolean linking = linkAfter >= 0 && linkAfter < held.size() - 1;
        MaterialButton link = linking ? linkButton(context, linkName) : null;
        int edge = RowKit.dp(context, RowKit.EDGE_DP);
        int touch = RowKit.dp(context, RowKit.TOUCH_DP);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(edge, 0, edge, 0);
        List<Item> items = new ArrayList<>(held.size());
        for (int i = 0; i < held.size(); i++) {
            Item item = item(context, held.get(i), i, listener, onName);
            items.add(item);
            root.addView(item.root, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (link != null && i == linkAfter) {
                // Between the two rows it links, centred, about half the row's width.
                LinearLayout holder = new LinearLayout(context);
                holder.setOrientation(LinearLayout.HORIZONTAL);
                holder.setGravity(Gravity.CENTER);
                holder.setWeightSum(2f);
                LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                link.setMinHeight(touch);
                holder.addView(link, linkParams);
                root.addView(holder, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        SliderRow row = new SliderRow(root, items, held, link, linking ? linkAfter : -1);
        for (int i = 0; i < items.size(); i++)
            row.setValue(i, i < values.length ? values[i] : held.get(i).from);
        if (link != null) {
            row.setLinked(linked);
            dressLink(context, link, link.isChecked());
            link.addOnCheckedChangeListener((button, on) -> {
                dressLink(context, link, on);
                if (!row.mSettingLink && onLink != null)
                    onLink.onLink(on);
            });
        }
        return row;
    }

    /** Shows the link on or off without reporting it. */
    public void setLinked(boolean on) {
        if (link == null || link.isChecked() == on)
            return;
        mSettingLink = true;
        try {
            link.setChecked(on);
        } finally {
            mSettingLink = false;
        }
    }

    /** Whether the link is on; false where there is none. */
    public boolean isLinked() {
        return link != null && link.isChecked();
    }

    /** Shows or puts away slider {@code index}; the link shows while both its rows do. */
    public void setShown(int index, boolean shown) {
        if (index < 0 || index >= items.size())
            return;
        setVisible(items.get(index).root, shown);
        if (link != null) {
            boolean both = items.get(mLinkAfter).root.getVisibility() == View.VISIBLE
                && items.get(mLinkAfter + 1).root.getVisibility() == View.VISIBLE;
            setVisible(link, both);
            if (mLinkHolder != null) setVisible(mLinkHolder, both);
        }
    }

    /** Slider {@code index}'s own-value state: {@link Item#setOwn}. */
    public void setOwn(int index, boolean own, @Nullable CharSequence spoken) {
        if (index >= 0 && index < items.size())
            items.get(index).setOwn(own, spoken);
    }

    /** Shows slider {@code index} at {@code value} without reporting it; not while it drags. */
    public void setValue(int index, int value) {
        if (index < 0 || index >= items.size())
            return;
        Item item = items.get(index);
        if (item.isDragging())
            return;
        Track track = mTracks.get(index);
        int clamped = Math.max(track.from, Math.min(track.to, value));
        if (item.slider.getValue() != clamped)
            item.slider.setValue(clamped);
        showReadout(item.value, track.readout.format(clamped));
    }

    /**
     * Prints a readout: numbers in the fixed-width face the row sizes them in, a note in the
     * row's own text face. The face changes only when the kind of readout does.
     */
    static void showReadout(@NonNull TextView value, @NonNull String text) {
        RowKit.setText(value, text);
        Typeface face = SliderRowFit.isNote(text) ? Typeface.DEFAULT : Typeface.MONOSPACE;
        if (value.getTypeface() != face)
            value.setTypeface(face);
    }

    private static void setVisible(@NonNull View view, boolean shown) {
        int visibility = shown ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != visibility)
            view.setVisibility(visibility);
    }

    /**
     * The Linked toggle: the editor's tonal button ({@code Widget.Material3Expressive.Button
     * .TonalButton}, as the connected groups wear it), checkable, filled with the primary while
     * on, no check mark; its word and glyph follow the state ({@link #dressLink}).
     */
    @NonNull
    private static MaterialButton linkButton(@NonNull Context context,
                                             @NonNull CharSequence name) {
        MaterialButton button = SegmentRow.connectedButton(context);
        button.setCheckable(true);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        // Its name; the checked state says Linked or not.
        button.setContentDescription(name);
        GlyphNames.name(button, name);
        return button;
    }

    /** "Linked" with the link glyph while on, "Not linked" with link_off while off. */
    static void dressLink(@NonNull Context context, @NonNull MaterialButton link, boolean on) {
        link.setText(on ? R.string.editor_opacity_linked : R.string.editor_opacity_not_linked);
        link.setIconResource(on ? R.drawable.ic_ms_link : R.drawable.ic_ms_link_off);
    }

    /**
     * The widest {@code track}'s readout prints in {@code paint}'s size, so the value keeps one
     * width as it changes and a drag never lays the row out again.
     */
    static int widestReadoutPx(@NonNull Track track, @NonNull android.text.TextPaint paint) {
        float widest = 0f;
        int steps = (track.to - track.from) / track.step;
        int stride = Math.max(1, steps / 400) * track.step;
        for (int v = track.from; v <= track.to; v += stride)
            widest = Math.max(widest, paint.measureText(track.readout.format(v)));
        widest = Math.max(widest, paint.measureText(track.readout.format(track.to)));
        return (int) Math.ceil(widest);
    }

    @NonNull
    private static Item item(@NonNull Context context, @NonNull Track track, int index,
                             @NonNull Listener listener, @Nullable OnName onName) {
        ImageView glyph = RowKit.glyph(context, track.glyph, track.name);
        glyph.setImageTintList(ColorStateList.valueOf(RowKit.color(context,
            androidx.appcompat.R.attr.colorPrimary)));

        // The row gives the name what is left and it ellipsizes inside that.
        TextView name = new TextView(context);
        RowKit.textAppearance(name, com.google.android.material.R.attr.textAppearanceBodyLarge);
        name.setTextColor(RowKit.color(context, com.google.android.material.R.attr.colorOnSurface));
        name.setText(track.name);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMinWidth(0);
        name.setMinimumWidth(0);
        name.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        name.setContentDescription(track.name);

        LegendSlider slider = new LegendSlider(context);
        slider.setHorizontal(true);
        slider.setValueFrom(track.from);
        slider.setValueTo(track.to);
        slider.setStepSize(track.step);
        slider.setValue(track.from);
        slider.setContentDescription(track.name);
        slider.setSpokenValue(v -> track.readout.format(Math.round(v)));
        slider.setInsetValue(v -> track.readout.format(Math.round(v)));

        // The two-line row's value; on one line the track prints it, and this stays laid out
        // (so its text changes redraw rather than relayout) but is not drawn.
        TextView value = RowKit.caption(context, track.readout.format(track.from));
        RowKit.textAppearance(value, com.google.android.material.R.attr.textAppearanceLabelLarge);
        value.setTextColor(RowKit.color(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant));
        value.setTypeface(Typeface.MONOSPACE);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        value.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        // The reset arrow: a 48dp target in the row's end slot, hidden (its room kept) until the
        // value is the surface's own.
        android.widget.ImageButton reset = new android.widget.ImageButton(context);
        reset.setImageResource(R.drawable.ic_ms_restart_alt);
        reset.setImageTintList(ColorStateList.valueOf(RowKit.color(context,
            androidx.appcompat.R.attr.colorPrimary)));
        android.util.TypedValue ripple = new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,
            ripple, true);
        reset.setBackgroundResource(ripple.resourceId);
        reset.setScaleType(ImageView.ScaleType.CENTER);
        reset.setContentDescription(context.getString(R.string.editor_row_reset_to_global,
            track.name));
        reset.setVisibility(View.INVISIBLE);
        reset.setOnClickListener(v -> listener.onReset(index));

        Line root = new Line(context, glyph, name, value, slider, reset,
            widestReadoutPx(track, value.getPaint()),
            widestReadoutPx(track, slider.insetValuePaint()));

        Item item = new Item(root, glyph, name, value, slider, reset, track.name);
        slider.addOnChangeListener((s, v, fromUser) -> {
            int at = Math.round(v);
            showReadout(value, track.readout.format(at));
            if (fromUser)
                listener.onValue(index, at, s.isDragging());
        });
        slider.addOnSliderTouchListener(new LegendSlider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull LegendSlider s) {}

            @Override public void onStopTrackingTouch(@NonNull LegendSlider s) {
                listener.onValue(index, Math.round(s.getValue()), false);
                listener.onReleased(index);
            }
        });
        if (onName != null) {
            name.setOnLongClickListener(v -> {
                onName.onName(index, v);
                return true;
            });
        }
        return item;
    }

    /**
     * One slider row, laid out by hand: on one line the glyph, the name and the track (its value
     * inside it, {@link LegendSlider#setInsetValue}); on two, the glyph, name and value over a
     * full-width track. Which, {@link SliderRowFit#oneLine} decides as it measures; the slider is
     * told by a redraw, never a layout, and a drag lays nothing out.
     */
    public static final class Line extends ViewGroup {
        @NonNull private final ImageView mGlyph;
        @NonNull private final TextView mName;
        @NonNull private final TextView mValue;
        @NonNull private final LegendSlider mSlider;
        @NonNull private final View mReset;
        private final int mSlotPx;
        private final int mValueWidthPx;
        private final int mInsetValueWidthPx;
        private final int mGlyphPx;
        private final int mColumnStartPx;
        private final int mTouchPx;
        private final int mTopPx;
        private final int mOneLinePadPx;
        private final int mGapPx;
        private final int mValueGapPx;
        private final int mMinTrackPx;
        private final int mValuePadPx;
        private final int mInsetTrackPx;
        private boolean mOneLine;

        Line(@NonNull Context context, @NonNull ImageView glyph, @NonNull TextView name,
             @NonNull TextView value, @NonNull LegendSlider slider, @NonNull View reset,
             int valueWidthPx, int insetValueWidthPx) {
            super(context);
            mGlyph = glyph;
            mName = name;
            mValue = value;
            mSlider = slider;
            mReset = reset;
            mSlotPx = RowKit.dp(context, RESET_SLOT_DP);
            mValueWidthPx = valueWidthPx;
            mInsetValueWidthPx = insetValueWidthPx;
            mGlyphPx = RowKit.dp(context, RowKit.GLYPH_DP);
            mColumnStartPx = RowKit.dp(context, RowKit.ICON_COLUMN_DP - RowKit.EDGE_DP);
            mTouchPx = RowKit.dp(context, RowKit.TOUCH_DP);
            mTopPx = RowKit.dp(context, ROW_TOP_DP);
            mOneLinePadPx = RowKit.dp(context, ONE_LINE_PAD_DP);
            mGapPx = RowKit.dp(context, SliderRowFit.NAME_GAP_DP);
            mValueGapPx = RowKit.dp(context, VALUE_GAP_DP);
            mMinTrackPx = RowKit.dp(context, SliderRowFit.MIN_TRACK_DP);
            mValuePadPx = RowKit.dp(context, LegendSlider.INSET_VALUE_PAD_DP);
            mInsetTrackPx = RowKit.dp(context, LegendSlider.INSET_TRACK_DP);
            addView(glyph);
            addView(name);
            addView(value);
            addView(slider);
            addView(reset);
        }

        /** Whether the row stands on one line, as last measured. */
        public boolean isOneLine() {
            return mOneLine;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int full = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? RowKit.defaultRowWidthPx(getContext())
                : MeasureSpec.getSize(widthMeasureSpec);
            // Everything but the reset slot at the end.
            int width = Math.max(0, full - mSlotPx);
            int column = Math.max(0, width - mColumnStartPx);
            int exactSlot = MeasureSpec.makeMeasureSpec(mSlotPx, MeasureSpec.EXACTLY);
            mReset.measure(exactSlot, MeasureSpec.makeMeasureSpec(mTouchPx, MeasureSpec.EXACTLY));
            int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            int exactGlyph = MeasureSpec.makeMeasureSpec(mGlyphPx, MeasureSpec.EXACTLY);
            mGlyph.measure(exactGlyph, exactGlyph);
            mName.measure(unspecified, unspecified);
            int nameNatural = mName.getMeasuredWidth();
            String readout = mValue.getText().toString();
            boolean note = SliderRowFit.isNote(readout);
            int valuePx = SliderRowFit.twoLineValuePx(note, mValueWidthPx,
                (int) Math.ceil(mValue.getPaint().measureText(readout)), column);
            mValue.measure(MeasureSpec.makeMeasureSpec(valuePx, MeasureSpec.EXACTLY),
                unspecified);
            Paint.FontMetrics metrics = mSlider.insetValuePaint().getFontMetrics();
            int valueLine = (int) Math.ceil(metrics.descent - metrics.ascent);
            mOneLine = SliderRowFit.oneLine(column, mMinTrackPx, mGapPx, nameNatural,
                SliderRowFit.fitValuePx(note, mInsetValueWidthPx,
                    (int) Math.ceil(mSlider.insetValuePaint().measureText(readout))),
                mValuePadPx, valueLine, mInsetTrackPx);
            int height;
            if (mOneLine) {
                int track = SliderRowFit.trackPx(column, mMinTrackPx);
                mName.measure(MeasureSpec.makeMeasureSpec(
                    SliderRowFit.nameRoomPx(column, mMinTrackPx, mGapPx), MeasureSpec.AT_MOST),
                    unspecified);
                mSlider.measure(MeasureSpec.makeMeasureSpec(track, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(mTouchPx, MeasureSpec.EXACTLY));
                height = 2 * mOneLinePadPx + Math.max(mTouchPx, mName.getMeasuredHeight());
            } else {
                int nameRoom = Math.max(0, column - mValueGapPx - valuePx);
                mName.measure(MeasureSpec.makeMeasureSpec(nameRoom, MeasureSpec.AT_MOST),
                    unspecified);
                mSlider.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(mTouchPx, MeasureSpec.EXACTLY));
                height = mTopPx + headPx() + mTouchPx;
            }
            // A redraw only: the slider keeps its size, so nothing asks for a layout here.
            mSlider.setInsetShown(mOneLine);
            setMeasuredDimension(full, resolveSize(height, heightMeasureSpec));
        }

        /** The two-line row's name line: the glyph, the name and the value, centred on it. */
        private int headPx() {
            return Math.max(mGlyphPx, Math.max(mName.getMeasuredHeight(),
                mValue.getMeasuredHeight()));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int full = r - l;
            int width = Math.max(0, full - mSlotPx);
            if (mOneLine) {
                int centre = (b - t) / 2;
                place(mGlyph, 0, centre - mGlyph.getMeasuredHeight() / 2, full);
                place(mName, mColumnStartPx, centre - mName.getMeasuredHeight() / 2, full);
                int sliderStart = width - mSlider.getMeasuredWidth();
                place(mSlider, sliderStart, centre - mSlider.getMeasuredHeight() / 2, full);
                // Laid out at the track's end, where the track prints it; not drawn.
                place(mValue, width - mValue.getMeasuredWidth(),
                    centre - mValue.getMeasuredHeight() / 2, full);
                place(mReset, width, centre - mReset.getMeasuredHeight() / 2, full);
            } else {
                int head = headPx();
                int centre = mTopPx + head / 2;
                place(mGlyph, 0, centre - mGlyph.getMeasuredHeight() / 2, full);
                place(mName, mColumnStartPx, centre - mName.getMeasuredHeight() / 2, full);
                place(mValue, width - mValue.getMeasuredWidth(),
                    centre - mValue.getMeasuredHeight() / 2, full);
                place(mSlider, 0, mTopPx + head, full);
                place(mReset, width, mTopPx + head, full);
            }
        }

        /** Lays {@code child} at {@code start} from the start edge, mirrored right to left. */
        private void place(@NonNull View child, int start, int top, int width) {
            int w = child.getMeasuredWidth();
            int left = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? width - start - w : start;
            child.layout(left, top, left + w, top + child.getMeasuredHeight());
        }

        @Override
        protected boolean drawChild(@NonNull android.graphics.Canvas canvas, @NonNull View child,
                                    long drawingTime) {
            if (mOneLine && child == mValue)
                return false;
            return super.drawChild(canvas, child, drawingTime);
        }
    }
}
