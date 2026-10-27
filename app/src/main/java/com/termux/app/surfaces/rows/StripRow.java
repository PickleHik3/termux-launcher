package com.termux.app.surfaces.rows;

import android.content.Context;
import android.content.res.ColorStateList;
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
 * A strip (editor-redesign §2: "a vertical slider: glyph on top, value below, name shown while
 * dragging"): a run of vertical sliders, side by side, each its glyph over its track and its value
 * under it. The name stands beside the track only while the finger drags it, and on the glyph's
 * long-press. A swipe that starts on a strip scrolls the card ({@link StripTouchPolicy}, the
 * handle included); a rest of {@link StripTouchPolicy#HOLD_TO_CLAIM_MS} or a tap takes it.
 *
 * <p>The columns stand on as few lines as hold them, spread evenly, the fuller lines first, every
 * column as wide as the first line's, so the tracks keep one grid.</p>
 *
 * <p>Two neighbouring strips may be linked (the keyboard's Background and Key opacity): a toggle
 * with the link glyph stands between them, level with the tracks, and says on or off by its
 * colour; what the link does is the caller's.</p>
 */
public final class StripRow {

    /** A strip's value, as it prints ("20%", "24 dp"). */
    public interface Readout {
        @NonNull String format(int value);
    }

    /** What a strip reports. */
    public interface Listener {
        /** A strip moved; {@code dragging} while the finger is still on it. */
        void onValue(int index, int value, boolean dragging);

        /** A strip's finger lifted. */
        void onReleased(int index);
    }

    /** The link between two strips was turned on or off by touch. */
    public interface OnLink {
        void onLink(boolean on);
    }

    /** One strip: its glyph, name, range and readout. */
    public static final class Strip {
        @DrawableRes public final int glyph;
        @NonNull public final CharSequence name;
        public final int from;
        public final int to;
        public final int step;
        @NonNull public final Readout readout;

        public Strip(@DrawableRes int glyph, @NonNull CharSequence name, int from, int to, int step,
                     @NonNull Readout readout) {
            this.glyph = glyph;
            this.name = name;
            this.from = from;
            this.to = Math.max(from + 1, to);
            this.step = Math.max(1, step);
            this.readout = readout;
        }
    }

    /** One built column: its root, glyph, track and value. */
    public static final class Column {
        @NonNull public final LinearLayout root;
        @NonNull public final ImageView glyph;
        @NonNull public final LegendSlider slider;
        @NonNull public final TextView value;
        boolean dragging;

        Column(@NonNull LinearLayout root, @NonNull ImageView glyph, @NonNull LegendSlider slider,
               @NonNull TextView value) {
            this.root = root;
            this.glyph = glyph;
            this.slider = slider;
            this.value = value;
        }

        /** Whether the finger is on this strip now (its name stands beside the track). */
        public boolean isDragging() {
            return dragging;
        }
    }

    /** The track's length. */
    static final int TRACK_DP = 96;
    static final int GAP_DP = 8;

    @NonNull public final LinearLayout root;
    @NonNull public final List<Column> columns;
    /** The link between two strips, or null where the strip has none. */
    @Nullable public final MaterialButton link;
    @NonNull private final List<Strip> mStrips;
    private boolean mSettingLink;

    private StripRow(@NonNull LinearLayout root, @NonNull List<Column> columns,
                     @NonNull List<Strip> strips, @Nullable MaterialButton link) {
        this.root = root;
        this.columns = Collections.unmodifiableList(columns);
        this.link = link;
        mStrips = strips;
    }

    /** Builds the strips for a card {@code widthPx} wide, at {@code values}. */
    @NonNull
    public static StripRow build(@NonNull Context context, @NonNull List<Strip> strips,
                                 @NonNull int[] values, int widthPx, @NonNull Listener listener) {
        return build(context, strips, values, widthPx, listener, -1, "", false, null);
    }

    /**
     * Builds the strips with a link between strip {@code linkAfter} and the next, named
     * {@code linkName} (its long-press and its spoken name), on while {@code linked}. A
     * {@code linkAfter} with no strip after it builds no link.
     */
    @NonNull
    public static StripRow build(@NonNull Context context, @NonNull List<Strip> strips,
                                 @NonNull int[] values, int widthPx, @NonNull Listener listener,
                                 int linkAfter, @NonNull CharSequence linkName, boolean linked,
                                 @Nullable OnLink onLink) {
        List<Strip> held = new ArrayList<>(strips);
        List<Column> columns = new ArrayList<>(held.size());
        int widest = RowKit.dp(context, RowKit.TOUCH_DP);
        for (int i = 0; i < held.size(); i++) {
            Column column = column(context, held.get(i), i, listener);
            columns.add(column);
            Strip strip = held.get(i);
            widest = Math.max(widest, column.slider.minColumnWidthPx());
            for (int v : new int[] {strip.from, strip.to}) {
                widest = Math.max(widest, (int) Math.ceil(column.value.getPaint()
                    .measureText(strip.readout.format(v))));
            }
        }
        int edge = RowKit.dp(context, RowKit.EDGE_DP);
        int gap = RowKit.dp(context, GAP_DP);
        boolean linking = linkAfter >= 0 && linkAfter < held.size() - 1;
        MaterialButton link = linking ? linkButton(context, linkName) : null;
        int touch = RowKit.dp(context, RowKit.TOUCH_DP);
        int room = Math.max(1, widthPx - 2 * edge - (linking ? touch + gap : 0));
        int[] lines = lines(columns.size(), capacity(room, widest, gap));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(edge, RowKit.dp(context, 8), edge, RowKit.dp(context, 8));
        int next = 0;
        for (int l = 0; l < lines.length; l++) {
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setWeightSum(lines[0]);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (l > 0)
                lineParams.topMargin = gap;
            root.addView(line, lineParams);
            for (int c = 0; c < lines[l]; c++) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (c > 0)
                    params.setMarginStart(gap);
                line.addView(columns.get(next).root, params);
                if (link != null && next == linkAfter) {
                    // Level with the tracks: under the glyph and its gap, centred on the track.
                    LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(touch,
                        touch);
                    linkParams.setMarginStart(gap);
                    linkParams.topMargin = RowKit.dp(context, RowKit.GLYPH_DP + 4)
                        + (RowKit.dp(context, TRACK_DP) - touch) / 2;
                    line.addView(link, linkParams);
                }
                next++;
            }
            int missing = lines[0] - lines[l];
            if (missing > 0) {
                LinearLayout.LayoutParams rest = new LinearLayout.LayoutParams(0, 1, missing);
                rest.setMarginStart(gap * missing);
                line.addView(new View(context), rest);
            }
        }
        StripRow row = new StripRow(root, columns, held, link);
        for (int i = 0; i < columns.size(); i++)
            row.setValue(i, i < values.length ? values[i] : held.get(i).from);
        if (link != null) {
            row.setLinked(linked);
            link.addOnCheckedChangeListener((button, on) -> {
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

    /** Shows or puts away strip {@code index}; its line keeps its place in the grid. */
    public void setShown(int index, boolean shown) {
        if (index < 0 || index >= columns.size())
            return;
        int visibility = shown ? View.VISIBLE : View.INVISIBLE;
        View column = columns.get(index).root;
        if (column.getVisibility() != visibility)
            column.setVisibility(visibility);
    }

    /** The link: the link glyph, on the secondary container while on, a 48dp target. */
    @NonNull
    private static MaterialButton linkButton(@NonNull Context context,
                                             @NonNull CharSequence name) {
        MaterialButton button = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialIconButtonStyle);
        button.setIconResource(R.drawable.ic_ms_link);
        button.setCheckable(true);
        button.setContentDescription(name);
        GlyphNames.name(button, name);
        int[][] states = {{android.R.attr.state_checked}, {}};
        button.setIconTint(new ColorStateList(states, new int[] {
            RowKit.color(context, com.google.android.material.R.attr.colorOnSecondaryContainer),
            RowKit.color(context, com.google.android.material.R.attr.colorOnSurfaceVariant)}));
        button.setBackgroundTintList(new ColorStateList(states, new int[] {
            RowKit.color(context, com.google.android.material.R.attr.colorSecondaryContainer),
            0}));
        return button;
    }

    /** Shows strip {@code index} at {@code value} without reporting it; not while it drags. */
    public void setValue(int index, int value) {
        if (index < 0 || index >= columns.size())
            return;
        Column column = columns.get(index);
        if (column.dragging)
            return;
        Strip strip = mStrips.get(index);
        int clamped = Math.max(strip.from, Math.min(strip.to, value));
        if (column.slider.getValue() != clamped)
            column.slider.setValue(clamped);
        RowKit.setText(column.value, strip.readout.format(clamped));
    }

    /**
     * How a strip of {@code count} columns stands on lines of at most {@code capacity}: as few
     * lines as hold them, spread evenly, the fuller lines first ({@code 9, 7} gives
     * {@code [5, 4]}).
     */
    @NonNull
    static int[] lines(int count, int capacity) {
        if (count <= 0)
            return new int[0];
        int perLine = Math.max(1, capacity);
        int lines = (count + perLine - 1) / perLine;
        int base = count / lines;
        int extra = count % lines;
        int[] out = new int[lines];
        for (int i = 0; i < lines; i++)
            out[i] = base + (i < extra ? 1 : 0);
        return out;
    }

    /** How many columns of at least {@code columnPx} stand on {@code widthPx}; at least one. */
    static int capacity(int widthPx, int columnPx, int gapPx) {
        return Math.max(1, (widthPx + gapPx) / Math.max(1, columnPx + gapPx));
    }

    @NonNull
    private static Column column(@NonNull Context context, @NonNull Strip strip, int index,
                                 @NonNull Listener listener) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView glyph = RowKit.glyph(context, strip.glyph, strip.name);
        glyph.setImageTintList(android.content.res.ColorStateList.valueOf(RowKit.color(context,
            androidx.appcompat.R.attr.colorPrimary)));
        LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(
            RowKit.dp(context, RowKit.GLYPH_DP), RowKit.dp(context, RowKit.GLYPH_DP));
        glyphParams.bottomMargin = RowKit.dp(context, 4);
        root.addView(glyph, glyphParams);

        LegendSlider slider = new LegendSlider(context);
        slider.setValueFrom(strip.from);
        slider.setValueTo(strip.to);
        slider.setStepSize(strip.step);
        slider.setValue(strip.from);
        slider.setContentDescription(strip.name);
        // A swipe on the track moves the slider at once; the card scrolls from off the tracks
        // (decided 2026-10-27, replacing editor-redesign §9's handle that scrolled the card).
        slider.setTrackGrabs(true);
        TextView value = RowKit.caption(context, strip.readout.format(strip.from));
        value.setGravity(Gravity.CENTER_HORIZONTAL);
        value.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        Column column = new Column(root, glyph, slider, value);
        // The name stands beside the track only while the finger is on it.
        slider.setLegend(v -> column.dragging ? strip.name.toString() : "");
        slider.setSpokenValue(v -> strip.readout.format(Math.round(v)));
        slider.addOnChangeListener((s, v, fromUser) -> {
            int at = Math.round(v);
            RowKit.setText(value, strip.readout.format(at));
            if (fromUser)
                listener.onValue(index, at, column.dragging);
        });
        slider.addOnSliderTouchListener(new LegendSlider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull LegendSlider s) {
                column.dragging = true;
                s.invalidate();
            }

            @Override public void onStopTrackingTouch(@NonNull LegendSlider s) {
                column.dragging = false;
                s.invalidate();
                listener.onValue(index, Math.round(s.getValue()), false);
                listener.onReleased(index);
            }
        });
        root.addView(slider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            RowKit.dp(context, TRACK_DP)));
        // The track stands left of the column's centre, the name's band beside it: the glyph
        // and the value follow the track, by translation, so nothing lays out again.
        slider.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            float shift = slider.trackCentreX() - slider.getWidth() / 2f;
            if (glyph.getTranslationX() != shift)
                glyph.setTranslationX(shift);
            if (value.getTranslationX() != shift)
                value.setTranslationX(shift);
        });
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = RowKit.dp(context, 4);
        root.addView(value, valueParams);
        return column;
    }
}
