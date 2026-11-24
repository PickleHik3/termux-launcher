package com.termux.app.surfaces.rows;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.surfaces.LegendSlider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Two related sliders on one row (spec #326: Opacity with Frost, Corner radius with Margin): each
 * cell has its name at the start and its value at the end over its own track, with the reset
 * arrow beside the value while the value is the surface's own. The cells stand side by side where
 * {@link PairFit} says they hold {@link PairFit#MIN_SLIDER_CELL_DP}, and one under the other
 * otherwise. The tracks are {@link LegendSlider}s, which claim a gesture that starts on them, so
 * the card never scrolls under a finger on a track. Touches go to the {@link SliderRow.Listener};
 * restating a value reports nothing.
 *
 * <pre>
 * SliderPairRow pair = SliderPairRow.build(context,
 *     Arrays.asList(new SliderRow.Track(0, "Opacity", 0, 100, 1, v -> v + "%"),
 *         new SliderRow.Track(0, "Frost", 0, 40, 1, v -> v + " dp")),
 *     new int[] {50, 10}, listener);
 * pair.setValue(0, 70);   // a restatement, not echoed
 * </pre>
 */
public final class SliderPairRow {

    /** One cell: the name and value over the track. */
    public static final class Cell {
        @NonNull public final LinearLayout root;
        @NonNull public final TextView name;
        @NonNull public final TextView value;
        @NonNull public final LegendSlider slider;
        @NonNull public final ImageButton reset;

        Cell(@NonNull LinearLayout root, @NonNull TextView name, @NonNull TextView value,
             @NonNull LegendSlider slider, @NonNull ImageButton reset) {
            this.root = root;
            this.name = name;
            this.value = value;
            this.slider = slider;
            this.reset = reset;
        }

        /** Whether a finger is on the track. */
        public boolean isDragging() {
            return slider.isDragging();
        }
    }

    @NonNull public final PairRow root;
    @NonNull public final List<Cell> cells;
    @NonNull private final List<SliderRow.Track> mTracks;

    private SliderPairRow(@NonNull PairRow root, @NonNull List<Cell> cells,
                          @NonNull List<SliderRow.Track> tracks) {
        this.root = root;
        this.cells = Collections.unmodifiableList(cells);
        mTracks = tracks;
    }

    /** Builds the pair of {@code tracks} (exactly two) at {@code values}. */
    @NonNull
    public static SliderPairRow build(@NonNull Context context,
                                      @NonNull List<SliderRow.Track> tracks,
                                      @NonNull int[] values,
                                      @NonNull SliderRow.Listener listener) {
        if (tracks.size() != 2)
            throw new IllegalArgumentException("a pair holds two sliders, not " + tracks.size());
        List<Cell> cells = new ArrayList<>(2);
        for (int i = 0; i < 2; i++)
            cells.add(cell(context, tracks.get(i), i, listener));
        PairRow row = new PairRow(context, cells.get(0).root, cells.get(1).root, null,
            RowKit.dp(context, PairFit.MIN_SLIDER_CELL_DP));
        SliderPairRow pair = new SliderPairRow(row, cells, new ArrayList<>(tracks));
        for (int i = 0; i < 2; i++)
            pair.setValue(i, i < values.length ? values[i] : tracks.get(i).from);
        return pair;
    }

    /** Shows slider {@code index} at {@code value} without reporting it; not while it drags. */
    public void setValue(int index, int value) {
        Cell cell = cells.get(index);
        if (cell.isDragging())
            return;
        SliderRow.Track track = mTracks.get(index);
        int clamped = Math.max(track.from, Math.min(track.to, value));
        if (cell.slider.getValue() != clamped)
            cell.slider.setValue(clamped);
        SliderRow.showReadout(cell.value, track.readout.format(clamped));
    }

    /** Shows or puts away slider {@code index}; the other then takes the whole row. */
    public void setShown(int index, boolean shown) {
        int visibility = shown ? View.VISIBLE : View.GONE;
        View cell = cells.get(index).root;
        if (cell.getVisibility() != visibility)
            cell.setVisibility(visibility);
    }

    /** Slider {@code index}'s own-value state: the reset arrow stands while it is. */
    public void setOwn(int index, boolean own, @Nullable CharSequence spoken) {
        Cell cell = cells.get(index);
        int visibility = own ? View.VISIBLE : View.INVISIBLE;
        if (cell.reset.getVisibility() != visibility)
            cell.reset.setVisibility(visibility);
        cell.reset.setEnabled(own);
        if (spoken != null)
            cell.root.setStateDescription(own ? spoken : null);
    }

    @NonNull
    private static Cell cell(@NonNull Context context, @NonNull SliderRow.Track track, int index,
                             @NonNull SliderRow.Listener listener) {
        int touch = RowKit.dp(context, RowKit.TOUCH_DP);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(context);
        RowKit.textAppearance(name, com.google.android.material.R.attr.textAppearanceBodyLarge);
        name.setTextColor(RowKit.color(context,
            com.google.android.material.R.attr.colorOnSurface));
        name.setText(track.name);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setMinWidth(0);
        name.setContentDescription(track.name);
        head.addView(name, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = RowKit.caption(context, track.readout.format(track.from));
        RowKit.textAppearance(value, com.google.android.material.R.attr.textAppearanceLabelLarge);
        value.setTextColor(RowKit.color(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant));
        value.setTypeface(Typeface.MONOSPACE);
        value.setSingleLine(true);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        value.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // As wide as the widest value, so a drag never lays the row out again.
        value.setMinWidth(SliderRow.widestReadoutPx(track, value.getPaint()));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.setMarginStart(RowKit.dp(context, SliderRow.VALUE_GAP_DP));
        head.addView(value, valueParams);

        ImageButton reset = new ImageButton(context);
        reset.setImageResource(R.drawable.ic_ms_restart_alt);
        reset.setImageTintList(ColorStateList.valueOf(RowKit.color(context,
            androidx.appcompat.R.attr.colorPrimary)));
        android.util.TypedValue ripple = new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,
            ripple, true);
        reset.setBackgroundResource(ripple.resourceId);
        reset.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        reset.setContentDescription(context.getString(R.string.editor_row_reset_to_global,
            track.name));
        reset.setVisibility(View.INVISIBLE);
        reset.setOnClickListener(v -> listener.onReset(index));
        int slot = RowKit.dp(context, 32);
        head.addView(reset, new LinearLayout.LayoutParams(slot, slot));
        root.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            touch / 2 + RowKit.dp(context, 8)));

        LegendSlider slider = new LegendSlider(context);
        slider.setHorizontal(true);
        slider.setValueFrom(track.from);
        slider.setValueTo(track.to);
        slider.setStepSize(track.step);
        slider.setValue(track.from);
        slider.setContentDescription(track.name);
        slider.setSpokenValue(v -> track.readout.format(Math.round(v)));
        root.addView(slider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            touch));

        Cell cell = new Cell(root, name, value, slider, reset);
        slider.addOnChangeListener((s, v, fromUser) -> {
            int at = Math.round(v);
            SliderRow.showReadout(value, track.readout.format(at));
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
        return cell;
    }
}
