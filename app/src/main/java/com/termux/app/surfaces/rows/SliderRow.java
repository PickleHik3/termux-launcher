package com.termux.app.surfaces.rows;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
 * 2026-10-31; they were a strip of vertical columns before). A row is two lines: the glyph, the
 * name with its source dot after it, and the value at the end; under them the track, the row's
 * whole width, in a 48dp hit area. The row's height is measured, so a larger font scale grows
 * the name line rather than cutting it.
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

    /**
     * Where a slider's value stands: under the name line ({@link #TWO_LINES}, the default) or
     * on it, between the name and the value. One switch, should the two-line row be overturned.
     */
    static final boolean TWO_LINES = true;

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
        @NonNull public final LinearLayout root;
        @NonNull public final ImageView glyph;
        @NonNull public final TextView name;
        @NonNull public final TextView value;
        @NonNull public final LegendSlider slider;
        @NonNull private final CharSequence mName;
        @NonNull private final GradientDrawable mDot = new GradientDrawable();
        private int mDotColor;

        Item(@NonNull LinearLayout root, @NonNull ImageView glyph, @NonNull TextView name,
             @NonNull TextView value, @NonNull LegendSlider slider, @NonNull CharSequence label) {
            this.root = root;
            this.glyph = glyph;
            this.name = name;
            this.value = value;
            this.slider = slider;
            mName = label;
            int dot = RowKit.dp(root.getContext(), DOT_DP);
            mDot.setShape(GradientDrawable.OVAL);
            mDot.setBounds(0, 0, dot, dot);
        }

        /** Whether the finger is on this slider now. */
        public boolean isDragging() {
            return slider.isDragging();
        }

        /**
         * Where the value comes from: the dot after the name in {@code color} (0 for none) and
         * the words a screen reader says for the name and the slider ("Frost, from Home page").
         * Compares before it sets.
         */
        public void setSource(int color, @Nullable CharSequence spoken) {
            Drawable want = color != 0 ? mDot : null;
            if (color != 0 && color != mDotColor) {
                mDotColor = color;
                mDot.setColor(color);
            }
            if (name.getCompoundDrawablesRelative()[2] != want)
                name.setCompoundDrawablesRelative(null, null, want, null);
            CharSequence said = spoken != null ? spoken : mName;
            if (!TextUtils.equals(name.getContentDescription(), said))
                name.setContentDescription(said);
            slider.setMark(color, spoken);
        }
    }

    /** The source dot after the name, and its gap. */
    static final int DOT_DP = 8;
    static final int DOT_GAP_DP = 6;
    /** The gap between the name and the value. */
    static final int VALUE_GAP_DP = 8;
    /** Above a row's name line; the track's 48dp hit area keeps its own room below. */
    static final int ROW_TOP_DP = 8;

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

    /** Slider {@code index}'s source: {@link Item#setSource}. */
    public void setSource(int index, int color, @Nullable CharSequence spoken) {
        if (index >= 0 && index < items.size())
            items.get(index).setSource(color, spoken);
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
        RowKit.setText(item.value, track.readout.format(clamped));
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
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, RowKit.dp(context, ROW_TOP_DP), 0, 0);

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView glyph = RowKit.glyph(context, track.glyph, track.name);
        glyph.setImageTintList(ColorStateList.valueOf(RowKit.color(context,
            androidx.appcompat.R.attr.colorPrimary)));
        head.addView(glyph, RowKit.glyphParams(context));

        // The name takes what the glyph and the value leave, and ellipsizes inside it; the dot
        // rides after its last glyph as a compound drawable, so it follows a short name.
        FrameLayout nameBox = new FrameLayout(context);
        TextView name = new TextView(context);
        RowKit.textAppearance(name, com.google.android.material.R.attr.textAppearanceBodyLarge);
        name.setTextColor(RowKit.color(context, com.google.android.material.R.attr.colorOnSurface));
        name.setText(track.name);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMinWidth(0);
        name.setMinimumWidth(0);
        name.setCompoundDrawablePadding(RowKit.dp(context, DOT_GAP_DP));
        name.setContentDescription(track.name);
        nameBox.addView(name, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL));
        head.addView(nameBox, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LegendSlider slider = new LegendSlider(context);
        slider.setHorizontal(true);
        slider.setValueFrom(track.from);
        slider.setValueTo(track.to);
        slider.setStepSize(track.step);
        slider.setValue(track.from);
        slider.setContentDescription(track.name);
        slider.setSpokenValue(v -> track.readout.format(Math.round(v)));
        if (!TWO_LINES)
            head.addView(slider, new LinearLayout.LayoutParams(0, RowKit.dp(context,
                RowKit.TOUCH_DP), 2f));

        TextView value = RowKit.caption(context, track.readout.format(track.from));
        RowKit.textAppearance(value, com.google.android.material.R.attr.textAppearanceLabelLarge);
        value.setTextColor(RowKit.color(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant));
        value.setTypeface(Typeface.MONOSPACE);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        value.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
            widestReadoutPx(track, value.getPaint()), ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.setMarginStart(RowKit.dp(context, VALUE_GAP_DP));
        head.addView(value, valueParams);
        root.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        if (TWO_LINES)
            root.addView(slider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, RowKit.dp(context, RowKit.TOUCH_DP)));

        Item item = new Item(root, glyph, name, value, slider, track.name);
        slider.addOnChangeListener((s, v, fromUser) -> {
            int at = Math.round(v);
            RowKit.setText(value, track.readout.format(at));
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
}
