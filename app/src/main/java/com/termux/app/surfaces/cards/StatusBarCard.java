package com.termux.app.surfaces.cards;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import com.termux.R;
import com.termux.app.place.PlaceLayout;
import com.termux.app.statusbar.TopPaneClockForm;
import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;
import com.termux.app.surfaces.rows.EditorGlyphs;
import com.termux.app.surfaces.rows.PreviewTilesRow;
import com.termux.app.surfaces.rows.SectionHeader;
import com.termux.app.surfaces.rows.SegmentRow;
import com.termux.app.surfaces.rows.TileGrid;
import com.termux.app.terminal.TerminalClockWidget;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * The Status bar card (editor-redesign §2, Status bar): Position (Top, Bottom), the glass and
 * shape strip under its Glass header, the Clock as live face previews with Clock position under
 * it, then the Options and Indicators tiles. The Visible switch is the header's eye.
 *
 * <p>{@link #rows} is the card's table and the pure policy below it maps stored values to the
 * choices the rows show, so JVM tests hold both; {@link #view} builds the card's own rows from the
 * row kinds over a {@link Host} the editor implements.</p>
 */
public final class StatusBarCard {

    private StatusBarCard() {}

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        return new EditorRows.CardRows()
            .tier(1, Row.STATUS_POSITION, Row.GLASS_SECTION, Row.FROST, Row.OPACITY, Row.TINT,
                Row.CORNERS, Row.MARGIN)
            .tier(2, Row.CLOCK_FACE, Row.CLOCK_POSITION, Row.STATUS_OPTIONS,
                Row.STATUS_INDICATORS)
            .build();
    }

    // ------------------------------------------------------------------------------ policy

    /** Position's segments, in order: the edges the bar stands on along the screen. */
    public static final PlaceLayout.Edge[] POSITIONS = {PlaceLayout.Edge.TOP,
        PlaceLayout.Edge.BOTTOM};

    /** The Position segment for the bar's {@code edge}; -1 for a bar standing in a column. */
    public static int positionIndex(@Nullable PlaceLayout.Edge edge) {
        if (edge == null)
            return 0;
        for (int i = 0; i < POSITIONS.length; i++)
            if (POSITIONS[i] == edge) return i;
        return -1;
    }

    /** The clock faces, one tile each, in the review's order; Slab is the default. */
    public static final String[] FACES = {TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP,
        TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD, TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL,
        TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED, TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE,
        TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB};

    @StringRes
    static final int[] FACE_NAMES = {R.string.editor_st_clock_flip, R.string.editor_st_clock_lcd,
        R.string.editor_st_clock_minimal, R.string.editor_st_clock_led,
        R.string.editor_st_clock_tape, R.string.editor_st_clock_slab};

    /**
     * The tile of the stored {@code style}: Minimal's tile for either of its weights, Flip for an
     * unknown value (the face the bar itself falls back to), Slab for none.
     */
    public static int faceIndex(@Nullable String style) {
        if (style == null)
            return indexOf(FACES, TERMUX_APP.DEFAULT_TOP_PANE_CLOCK_STYLE);
        if (TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL_MEDIUM.equals(style))
            return indexOf(FACES, TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL);
        int index = indexOf(FACES, style);
        return index < 0 ? 0 : index;
    }

    /**
     * The style a tap on tile {@code index} stores while the bar wears {@code current}: the
     * tile's face, except that Minimal's tile keeps the weight Minimal already wears.
     */
    @NonNull
    public static String faceStyle(int index, @Nullable String current) {
        String face = FACES[Math.max(0, Math.min(FACES.length - 1, index))];
        if (TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL.equals(face)
            && TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL_MEDIUM.equals(current))
            return TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL_MEDIUM;
        return face;
    }

    /** Clock position's segments, in order. */
    public static final String[] ALIGNMENTS = {TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_LEFT,
        TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER, TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT};

    /** The segment of the stored alignment; Left for an unknown one, as the clock reads it. */
    public static int alignmentIndex(@Nullable String alignment) {
        int index = indexOf(ALIGNMENTS, alignment);
        return index < 0 ? 0 : index;
    }

    /** The Options tiles, in order. */
    public static final int OPTION_12H = 0;
    public static final int OPTION_SECONDS = 1;
    public static final int OPTION_FAHRENHEIT = 2;
    /** A shortcut, not a switch: it reads on while the bar leaves a camera gap, and opens it. */
    public static final int OPTION_CAMERA_GAP = 3;
    static final int OPTION_COUNT = 4;

    /** The Indicators tiles, in order. */
    public static final int INDICATOR_CPU = 0;
    public static final int INDICATOR_RAM = 1;
    public static final int INDICATOR_WEATHER = 2;
    public static final int INDICATOR_NOTIFICATIONS = 3;
    public static final int INDICATOR_MEDIA = 4;
    static final int INDICATOR_COUNT = 5;

    /** Whether the Camera gap tile reads on: the bar leaves a gap (Auto or Custom), not Off. */
    public static boolean cameraGapOn(@Nullable String cutoutMode) {
        return !TERMUX_APP.STATUS_BAR_CUTOUT_OFF.equals(cutoutMode);
    }

    /** The time every face tile reads, 07:48, today in the phone's own zone. */
    @VisibleForTesting
    static long previewTime() {
        Calendar at = Calendar.getInstance();
        at.set(Calendar.HOUR_OF_DAY, 7);
        at.set(Calendar.MINUTE, 48);
        at.set(Calendar.SECOND, 0);
        at.set(Calendar.MILLISECOND, 0);
        return at.getTimeInMillis();
    }

    private static int indexOf(@NonNull String[] values, @Nullable String value) {
        for (int i = 0; i < values.length; i++)
            if (values[i].equals(value)) return i;
        return -1;
    }

    // ------------------------------------------------------------------------------- views

    /** What the card's own rows read and write: the editor's side. */
    public interface Host {
        /** Position's segment ({@link #positionIndex}). */
        int position();
        void setPosition(int index);
        /** The face tile ({@link #faceIndex}). */
        int clockFace();
        void setClockFace(int index);
        /** Clock position's segment ({@link #alignmentIndex}). */
        int clockPosition();
        void setClockPosition(int index);
        /** Option tile {@code index} ({@link #OPTION_12H} and on). */
        boolean option(int index);
        void setOption(int index, boolean on);
        /** Indicator tile {@code index} ({@link #INDICATOR_CPU} and on). */
        boolean indicator(int index);
        void setIndicator(int index, boolean on);
        /** The Camera gap tile was tapped: the #163 cutout setting opens. */
        void openCameraGap();
    }

    /**
     * The card's own row for {@code row}, {@code widthPx} wide, or null for a row the sheet
     * draws (the strips).
     */
    @Nullable
    public static CardRowView view(@NonNull Context context, @NonNull Row row, int widthPx,
                                   @NonNull Host host) {
        switch (row) {
            case STATUS_POSITION: return position(context, widthPx, host);
            case GLASS_SECTION: return glassHeader(context, true);
            case CLOCK_FACE: return clockFaces(context, host);
            case CLOCK_POSITION: return clockPosition(context, widthPx, host);
            case STATUS_OPTIONS: return options(context, widthPx, host);
            case STATUS_INDICATORS: return indicators(context, widthPx, host);
            default: return null;
        }
    }

    /**
     * The header over a card's glass and shape strip: "Glass", noted "Shape"; the divider over it
     * where a row of its own tier stands before it.
     */
    @NonNull
    static CardRowView glassHeader(@NonNull Context context, boolean divider) {
        SectionHeader header = SectionHeader.build(context,
            context.getString(R.string.editor_st_section_glass),
            context.getString(R.string.editor_st_section_glass_note), divider);
        return new CardRowView(header.root, () -> {});
    }

    @NonNull
    private static CardRowView position(@NonNull Context context, int widthPx,
                                        @NonNull Host host) {
        SegmentRow row = SegmentRow.build(context, R.drawable.ic_editor_position,
            context.getString(R.string.editor_st_position),
            Arrays.asList(
                SegmentRow.Segment.glyph(EditorGlyphs.POS_TOP,
                    context.getString(R.string.editor_st_position_top)),
                SegmentRow.Segment.glyph(EditorGlyphs.POS_BOTTOM,
                    context.getString(R.string.editor_st_position_bottom))),
            host.position(), widthPx, host::setPosition);
        return new CardRowView(row.root, () -> row.select(host.position()));
    }

    @NonNull
    private static CardRowView clockFaces(@NonNull Context context, @NonNull Host host) {
        List<PreviewTilesRow.Tile> tiles = new ArrayList<>(FACES.length);
        for (int i = 0; i < FACES.length; i++)
            tiles.add(new PreviewTilesRow.Tile(context.getString(FACE_NAMES[i]),
                facePreview(context, FACES[i])));
        PreviewTilesRow row = PreviewTilesRow.build(context, tiles, host.clockFace(),
            host::setClockFace);
        // The sheet's tier divider stands over the Clock section.
        View section = CardRowView.section(context,
            context.getString(R.string.editor_st_section_clock), null, false, row.root);
        return new CardRowView(section, () -> row.select(host.clockFace()));
    }

    /**
     * One face as the bar draws it, held at 07:48 with no seconds and no period so every tile
     * reads the same time, scaled down to its tile.
     */
    @NonNull
    static View facePreview(@NonNull Context context, @NonNull String style) {
        TerminalClockWidget clock = new TerminalClockWidget(context, null);
        clock.setForm(TopPaneClockForm.COMPACT);
        clock.setStyle(style);
        clock.setUseAmPm(false);
        clock.setSeconds(false);
        clock.holdTime(previewTime());
        FitFrame frame = new FitFrame(context);
        frame.addView(clock, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        return frame;
    }

    @NonNull
    private static CardRowView clockPosition(@NonNull Context context, int widthPx,
                                             @NonNull Host host) {
        SegmentRow row = SegmentRow.build(context, R.drawable.ic_editor_clock,
            context.getString(R.string.editor_st_clock_position),
            Arrays.asList(
                SegmentRow.Segment.glyph(EditorGlyphs.CLOCK_ALIGN_LEFT,
                    context.getString(R.string.editor_st_clock_left)),
                SegmentRow.Segment.glyph(EditorGlyphs.CLOCK_ALIGN_CENTER,
                    context.getString(R.string.editor_st_clock_center)),
                SegmentRow.Segment.glyph(EditorGlyphs.CLOCK_ALIGN_RIGHT,
                    context.getString(R.string.editor_st_clock_right))),
            host.clockPosition(), widthPx, host::setClockPosition);
        return new CardRowView(row.root, () -> row.select(host.clockPosition()));
    }

    @NonNull
    private static CardRowView options(@NonNull Context context, int widthPx,
                                       @NonNull Host host) {
        List<TileGrid.Tile> tiles = Arrays.asList(
            new TileGrid.Tile(R.drawable.ic_ms_schedule,
                context.getString(R.string.editor_st_option_12h)),
            new TileGrid.Tile(R.drawable.ic_ms_timer,
                context.getString(R.string.editor_st_option_seconds)),
            new TileGrid.Tile(R.drawable.ic_ms_thermostat,
                context.getString(R.string.editor_st_option_fahrenheit)),
            new TileGrid.Tile(R.drawable.ic_ms_crop_free,
                context.getString(R.string.editor_st_option_camera_gap),
                context.getString(R.string.editor_st_option_camera_gap_hint)));
        TileGrid[] grid = new TileGrid[1];
        grid[0] = TileGrid.build(context, tiles, options(host), widthPx, (index, on) -> {
            if (index == OPTION_CAMERA_GAP) {
                // A shortcut: the tile keeps reading the stored gap and opens its setting.
                View tile = grid[0].tiles.get(index);
                tile.post(() -> grid[0].setOn(index, host.option(index)));
                host.openCameraGap();
                return;
            }
            host.setOption(index, on);
        });
        View section = CardRowView.section(context,
            context.getString(R.string.editor_st_section_options), null, true, grid[0].root);
        return new CardRowView(section, () -> {
            boolean[] on = options(host);
            for (int i = 0; i < on.length; i++)
                grid[0].setOn(i, on[i]);
        });
    }

    @NonNull
    private static boolean[] options(@NonNull Host host) {
        boolean[] on = new boolean[OPTION_COUNT];
        for (int i = 0; i < on.length; i++)
            on[i] = host.option(i);
        return on;
    }

    @NonNull
    private static CardRowView indicators(@NonNull Context context, int widthPx,
                                          @NonNull Host host) {
        List<TileGrid.Tile> tiles = Arrays.asList(
            new TileGrid.Tile(R.drawable.ic_ms_memory,
                context.getString(R.string.editor_st_indicator_cpu)),
            new TileGrid.Tile(R.drawable.ic_ms_developer_board,
                context.getString(R.string.editor_st_indicator_ram)),
            new TileGrid.Tile(R.drawable.ic_ms_partly_cloudy_day,
                context.getString(R.string.editor_st_indicator_weather)),
            new TileGrid.Tile(R.drawable.ic_ms_notifications,
                context.getString(R.string.editor_st_indicator_notifications),
                context.getString(R.string.editor_st_indicator_notifications_hint)),
            new TileGrid.Tile(R.drawable.ic_ms_music_note,
                context.getString(R.string.editor_st_indicator_media)));
        boolean[] on = new boolean[INDICATOR_COUNT];
        for (int i = 0; i < on.length; i++)
            on[i] = host.indicator(i);
        TileGrid grid = TileGrid.build(context, tiles, on, widthPx, host::setIndicator);
        View section = CardRowView.section(context,
            context.getString(R.string.editor_st_section_indicators), null, true, grid.root);
        return new CardRowView(section, () -> {
            for (int i = 0; i < INDICATOR_COUNT; i++)
                grid.setOn(i, host.indicator(i));
        });
    }

    /**
     * Holds one preview at its own width and scales it down, about its centre, until it fits the
     * frame: a face drawn at the bar's size, shown whole on a tile.
     */
    static final class FitFrame extends FrameLayout {

        FitFrame(@NonNull Context context) {
            super(context);
            setClipChildren(true);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            setMeasuredDimension(width, height);
            if (getChildCount() == 0)
                return;
            View child = getChildAt(0);
            child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(Math.max(0, height), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            if (getChildCount() == 0)
                return;
            View child = getChildAt(0);
            int width = right - left;
            int height = bottom - top;
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            int x = (width - childWidth) / 2;
            int y = (height - childHeight) / 2;
            child.layout(x, y, x + childWidth, y + childHeight);
            float scale = fitScale(width, height, childWidth, childHeight);
            child.setPivotX(childWidth / 2f);
            child.setPivotY(childHeight / 2f);
            child.setScaleX(scale);
            child.setScaleY(scale);
        }

        /** The scale a {@code childW} by {@code childH} preview takes to fit; never above 1. */
        static float fitScale(int width, int height, int childW, int childH) {
            if (childW <= 0 || childH <= 0)
                return 1f;
            return Math.min(1f, Math.min(width / (float) childW, height / (float) childH));
        }
    }
}
