package com.termux.app.surfaces.cards;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;

import com.termux.R;
import com.termux.app.statusbar.StatusRowItem;
import com.termux.app.statusbar.StatusRowOrder;
import com.termux.app.statusbar.TopPaneClockForm;
import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;
import com.termux.app.surfaces.rows.EditorGlyphs;
import com.termux.app.surfaces.rows.PreviewTilesRow;
import com.termux.app.surfaces.rows.ReorderList;
import com.termux.app.surfaces.rows.SectionHeader;
import com.termux.app.surfaces.rows.SegmentRow;
import com.termux.app.surfaces.rows.FoldChoiceRow;
import com.termux.app.surfaces.rows.SwitchChipsRow;
import com.termux.app.surfaces.rows.TileGrid;
import com.termux.app.terminal.TerminalClockWidget;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * The Status bar card (editor-redesign §2, Status bar): the glass and shape strip under its
 * Glass header, the Clock as live face previews with Clock position under it, then Options and
 * Elements: the clock and media tiles, and the row's items in the bar's order, dragged to move.
 * Where the bar stands, and whether it shows, is set on the editor's frame (drag, Hide).
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
            .tier(1, Row.STATUS_PLACE, Row.STATUS_PAGE_BAR, Row.STATUS_INDICATORS)
            .tier(2, Row.CLOCK_FACE, Row.CLOCK_POSITION, Row.STATUS_HOURS, Row.STATUS_SECONDS,
                Row.STATUS_UNIT)
            .pair(3, Row.OPACITY, Row.FROST)
            .links(3, Row.CAMERA_CUTOUT, Row.PINNED_NOTIFICATIONS)
            .build();
    }

    // ------------------------------------------------------------------------------ policy

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

    /** The Elements tiles, in order; Clock (show or hide the bar's clock) first. */
    public static final int INDICATOR_CLOCK = 0;
    public static final int INDICATOR_CPU = 1;
    public static final int INDICATOR_GPU = 2;
    public static final int INDICATOR_RAM = 3;
    public static final int INDICATOR_WEATHER = 4;
    public static final int INDICATOR_NOTIFICATIONS = 5;
    public static final int INDICATOR_MEDIA = 6;
    public static final int INDICATOR_BATTERY = 7;
    public static final int INDICATOR_FONT_TOGGLE = 8;
    static final int INDICATOR_COUNT = 9;

    /** The Elements tiles that are not in the row (the clock and the media stand above it). */
    static final int[] OFF_ROW_INDICATORS = {INDICATOR_CLOCK, INDICATOR_MEDIA};

    /**
     * The indicator switch of the row's {@code item}, or -1 for an item with none: the AI glyph
     * and the mouse show on their own while they are in use.
     */
    public static int indicatorFor(@NonNull StatusRowItem item) {
        switch (item) {
            case CPU: return INDICATOR_CPU;
            case GPU: return INDICATOR_GPU;
            case RAM: return INDICATOR_RAM;
            case BATTERY: return INDICATOR_BATTERY;
            case WEATHER: return INDICATOR_WEATHER;
            case TEXT_SIZE: return INDICATOR_FONT_TOGGLE;
            case NOTIFICATIONS: return INDICATOR_NOTIFICATIONS;
            default: return -1;
        }
    }

    /** The row's {@code item} as the Elements list draws it: its glyph. */
    @DrawableRes
    static int glyphFor(@NonNull StatusRowItem item) {
        switch (item) {
            case CPU: return R.drawable.ic_rl_cpu;
            case GPU: return R.drawable.ic_rl_gpu;
            case RAM: return R.drawable.ic_rl_memory;
            case BATTERY: return R.drawable.ic_ms_battery_full;
            case WEATHER: return R.drawable.ic_ms_partly_cloudy_day;
            case TEXT_SIZE: return R.drawable.ic_rl_text_size;
            case NOTIFICATIONS: return R.drawable.ic_ms_notifications;
            case AI: return R.drawable.ic_rl_assistant;
            default: return R.drawable.ic_rl_mouse;
        }
    }

    /** The row's {@code item} as the Elements list names it. */
    @StringRes
    static int nameFor(@NonNull StatusRowItem item) {
        switch (item) {
            case CPU: return R.string.editor_st_indicator_cpu;
            case GPU: return R.string.editor_st_indicator_gpu;
            case RAM: return R.string.editor_st_indicator_ram;
            case BATTERY: return R.string.editor_st_indicator_battery;
            case WEATHER: return R.string.editor_st_indicator_weather;
            case TEXT_SIZE: return R.string.editor_st_indicator_font_toggle;
            case NOTIFICATIONS: return R.string.editor_st_indicator_notifications;
            case AI: return R.string.editor_st_indicator_ai;
            default: return R.string.editor_st_indicator_mouse;
        }
    }

    /** Each place's item, as {@link ReorderList} takes it: the item's ordinal. */
    @NonNull
    static int[] placesOf(@NonNull StatusRowItem[] order) {
        int[] out = new int[order.length];
        for (int i = 0; i < order.length; i++)
            out[i] = order[i].ordinal();
        return out;
    }

    /** The order stored after the item at place {@code from} is dropped at place {@code to}. */
    @NonNull
    public static String movedOrder(@Nullable String stored, int from, int to) {
        return StatusRowOrder.format(StatusRowOrder.move(StatusRowOrder.parse(stored), from, to));
    }

    /**
     * Whether {@code row} is one of the Clock &amp; Weather section's clock rows, which stand
     * disabled ("Clock is hidden") while the Clock tile is off; °C · °F is the weather's and stays.
     */
    public static boolean clockRow(@NonNull Row row) {
        return row == Row.CLOCK_FACE || row == Row.CLOCK_POSITION || row == Row.STATUS_SECONDS
            || row == Row.STATUS_HOURS;
    }

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
        /**
         * The choice row {@code row} stands on: Clock face
         * ({@link #faceIndex}), Clock position ({@link #alignmentIndex}), Page bar (0 Glyphs,
         * 1 Marks, {@link #PAGE_BAR_MARKS}) or Place (0 Strip, 1 In the pane, {@link #PLACE_PANE}).
         */
        int choice(@NonNull Row row);
        void setChoice(@NonNull Row row, int index);
        /** Option tile {@code index} ({@link #OPTION_12H} and on). */
        boolean option(int index);
        void setOption(int index, boolean on);
        /** Indicator tile {@code index} ({@link #INDICATOR_CPU} and on). */
        boolean indicator(int index);
        void setIndicator(int index, boolean on);
        /** The row's order as stored ({@link StatusRowOrder}). */
        @Nullable String rowOrder();
        void setRowOrder(@NonNull String order);
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
            case GLASS_SECTION: return glassHeader(context, true);
            case CLOCK_FACE: return clockHeld(context, clockFaces(context, host), host);
            case CLOCK_POSITION:
                return clockHeld(context, clockPosition(context, widthPx, host), host);
            // Clock format, Seconds and Temperature unit share one row of chips, which the
            // first of them builds; the other two stand in it.
            case STATUS_HOURS: return formatChips(context, host);
            case STATUS_SECONDS:
            case STATUS_UNIT:
                return new CardRowView(new android.widget.Space(context), () -> {});
            case STATUS_PAGE_BAR: return pageBar(context, widthPx, host);
            case STATUS_PLACE: return place(context, widthPx, host);
            case STATUS_INDICATORS: return indicators(context, widthPx, host);
            default: return null;
        }
    }

    /**
     * A clock row under the Clock tile: while the bar's clock is hidden the row stands disabled
     * and dimmed; restated with the row.
     */
    @NonNull
    static CardRowView clockHeld(@NonNull Context context, @NonNull CardRowView row,
                                 @NonNull Host host) {
        Runnable hold = () -> {
            boolean shown = host.indicator(INDICATOR_CLOCK);
            setEnabledDeep(row.view, shown);
            row.view.setAlpha(shown ? 1f : 0.5f);
        };
        hold.run();
        return new CardRowView(row.view, () -> {
            row.restate();
            hold.run();
        });
    }

    /** {@code view} and everything in it enabled or not. */
    static void setEnabledDeep(@NonNull View view, boolean enabled) {
        if (view.isEnabled() != enabled)
            view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                setEnabledDeep(group.getChildAt(i), enabled);
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

    /** Clock face, folded into one line whose list opens in place. */
    @NonNull
    private static CardRowView clockFaces(@NonNull Context context, @NonNull Host host) {
        List<String> names = new ArrayList<>(FACES.length);
        for (int name : FACE_NAMES)
            names.add(context.getString(name));
        FoldChoiceRow row = FoldChoiceRow.build(context, R.drawable.ic_editor_clock,
            context.getString(R.string.appearance_row_clock_face), names,
            host.choice(Row.CLOCK_FACE), index -> host.setChoice(Row.CLOCK_FACE, index));
        return new CardRowView(row.root, () -> row.select(host.choice(Row.CLOCK_FACE)));
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
                SegmentRow.Segment.both(EditorGlyphs.CLOCK_ALIGN_LEFT,
                    context.getString(R.string.editor_st_clock_left)),
                SegmentRow.Segment.both(EditorGlyphs.CLOCK_ALIGN_CENTER,
                    context.getString(R.string.editor_st_clock_center)),
                SegmentRow.Segment.both(EditorGlyphs.CLOCK_ALIGN_RIGHT,
                    context.getString(R.string.editor_st_clock_right))),
            host.choice(Row.CLOCK_POSITION), widthPx, index -> host.setChoice(Row.CLOCK_POSITION, index));
        return new CardRowView(row.root, () -> row.select(host.choice(Row.CLOCK_POSITION)));
    }

    /**
     * 12 h, Seconds and °F as one row of switch chips, each bound to the option it always wrote
     * (the 12-hour option, Seconds, Fahrenheit). The two clock chips stand disabled while the
     * bar's clock is hidden; °F is the weather's and stays.
     */
    @NonNull
    private static CardRowView formatChips(@NonNull Context context, @NonNull Host host) {
        List<SwitchChipsRow.Item> items = Arrays.asList(
            new SwitchChipsRow.Item(R.drawable.ic_ms_schedule,
                context.getString(R.string.editor_st_hours_12)),
            new SwitchChipsRow.Item(R.drawable.ic_ms_timer,
                context.getString(R.string.editor_st_option_seconds)),
            new SwitchChipsRow.Item(R.drawable.ic_ms_thermostat,
                context.getString(R.string.editor_st_unit_f)));
        SwitchChipsRow row = SwitchChipsRow.build(context, items,
            (index, on) -> host.setOption(FORMAT_OPTIONS[index], on));
        return new CardRowView(row.root, () -> {
            boolean clock = host.indicator(INDICATOR_CLOCK);
            for (int i = 0; i < FORMAT_OPTIONS.length; i++) {
                row.setChecked(i, host.option(FORMAT_OPTIONS[i]));
                row.setEnabled(i, i == 2 || clock);
            }
        });
    }

    /** The options the format chips write, in the chips' order. */
    static final int[] FORMAT_OPTIONS = {OPTION_12H, OPTION_SECONDS, OPTION_FAHRENHEIT};

    /** Page bar's segments, in order: the window chips, then the line of marks. */
    public static final int PAGE_BAR_GLYPHS = 0;
    public static final int PAGE_BAR_MARKS = 1;

    /** The Page bar segment of the stored value; Glyphs for none or an unknown one. */
    public static int pageBarIndex(@Nullable String stored) {
        return com.termux.app.wall.pageswitch.PageBarLook.fromValue(stored).marksShown()
            ? PAGE_BAR_MARKS : PAGE_BAR_GLYPHS;
    }

    /** The value Page bar's segment {@code index} stores. */
    @NonNull
    public static String pageBarValue(int index) {
        return (index == PAGE_BAR_MARKS ? com.termux.app.wall.pageswitch.PageBarLook.MARKS
            : com.termux.app.wall.pageswitch.PageBarLook.GLYPHS).value;
    }

    /** Glyphs · Marks, a connected group bound to the global Page bar look. */
    @NonNull
    private static CardRowView pageBar(@NonNull Context context, int widthPx, @NonNull Host host) {
        SegmentRow row = SegmentRow.words(context, R.drawable.ic_ms_more_horiz,
            context.getString(R.string.editor_st_page_bar),
            new CharSequence[] {context.getString(R.string.editor_st_page_bar_glyphs),
                context.getString(R.string.editor_st_page_bar_marks)},
            host.choice(Row.STATUS_PAGE_BAR), widthPx, index -> host.setChoice(Row.STATUS_PAGE_BAR, index));
        row.root.setContentDescription(context.getString(R.string.editor_st_page_bar) + ". "
            + context.getString(R.string.editor_st_page_bar_summary));
        return new CardRowView(row.root, () -> row.select(host.choice(Row.STATUS_PAGE_BAR)));
    }

    /** Place's segments, in order: the status bar's own strip, then the thin row in the pane. */
    public static final int PLACE_STRIP = 0;
    public static final int PLACE_PANE = 1;

    /** The Place segment of the stored value; Strip for none or an unknown one. */
    public static int placeIndex(@Nullable String stored) {
        return com.termux.app.statusbar.StatusPlace.fromValue(stored).inPane()
            ? PLACE_PANE : PLACE_STRIP;
    }

    /** The value Place's segment {@code index} stores. */
    @NonNull
    public static String placeValue(int index) {
        return (index == PLACE_PANE ? com.termux.app.statusbar.StatusPlace.PANE
            : com.termux.app.statusbar.StatusPlace.STRIP).value;
    }

    /** Strip · In the pane, a connected group bound to the global Place. */
    @NonNull
    private static CardRowView place(@NonNull Context context, int widthPx, @NonNull Host host) {
        SegmentRow row = SegmentRow.words(context, R.drawable.ic_ms_more_horiz,
            context.getString(R.string.editor_st_place),
            new CharSequence[] {context.getString(R.string.editor_st_place_strip),
                context.getString(R.string.editor_st_place_pane)},
            host.choice(Row.STATUS_PLACE), widthPx, index -> host.setChoice(Row.STATUS_PLACE, index));
        row.root.setContentDescription(context.getString(R.string.editor_st_place) + ". "
            + context.getString(R.string.editor_st_place_summary));
        return new CardRowView(row.root, () -> row.select(host.choice(Row.STATUS_PLACE)));
    }

    @NonNull
    private static CardRowView indicators(@NonNull Context context, int widthPx,
                                          @NonNull Host host) {
        // The clock and the media stand above the row: plain on/off tiles, first.
        List<TileGrid.Tile> tiles = Arrays.asList(
            new TileGrid.Tile(R.drawable.ic_editor_clock,
                context.getString(R.string.editor_st_indicator_clock)),
            new TileGrid.Tile(R.drawable.ic_ms_music_note,
                context.getString(R.string.editor_st_indicator_media)));
        boolean[] tilesOn = new boolean[OFF_ROW_INDICATORS.length];
        for (int i = 0; i < tilesOn.length; i++)
            tilesOn[i] = host.indicator(OFF_ROW_INDICATORS[i]);
        TileGrid grid = TileGrid.build(context, tiles, tilesOn, widthPx,
            (index, on) -> host.setIndicator(OFF_ROW_INDICATORS[index], on));

        // The row's items, in the bar's order: drag a line to move it, switch it on or off.
        StatusRowItem[] items = StatusRowItem.values();
        List<ReorderList.Entry> entries = new ArrayList<>(items.length);
        boolean[] itemsOn = new boolean[items.length];
        for (StatusRowItem item : items) {
            int indicator = indicatorFor(item);
            entries.add(new ReorderList.Entry(glyphFor(item), context.getString(nameFor(item)),
                indicator >= 0));
            itemsOn[item.ordinal()] = indicator >= 0 && host.indicator(indicator);
        }
        ReorderList[] built = new ReorderList[1];
        ReorderList list = ReorderList.build(context, entries,
            placesOf(StatusRowOrder.parse(host.rowOrder())), itemsOn,
            (from, to) -> {
                host.setRowOrder(movedOrder(host.rowOrder(), from, to));
                // The dropped line takes its place at once, whatever the sheet restates.
                built[0].setOrder(placesOf(StatusRowOrder.parse(host.rowOrder())));
            },
            (entry, on) -> host.setIndicator(indicatorFor(items[entry]), on));
        built[0] = list;

        android.widget.LinearLayout body = new android.widget.LinearLayout(context);
        body.setOrientation(android.widget.LinearLayout.VERTICAL);
        body.addView(grid.root, new android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(list.root, new android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // "Elements" is a plain label inside the Status bar section, not a section heading.
        View section = CardRowView.section(context,
            context.getString(R.string.editor_st_section_indicators), null, true, body);
        return new CardRowView(section, () -> {
            for (int i = 0; i < OFF_ROW_INDICATORS.length; i++)
                grid.setOn(i, host.indicator(OFF_ROW_INDICATORS[i]));
            for (StatusRowItem item : items) {
                int indicator = indicatorFor(item);
                if (indicator >= 0) list.setOn(item.ordinal(), host.indicator(indicator));
            }
            list.setOrder(placesOf(StatusRowOrder.parse(host.rowOrder())));
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
