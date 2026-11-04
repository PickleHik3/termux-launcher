package com.termux.app.surfaces.cards;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;
import com.termux.app.dock.WallStripRowPolicy;
import com.termux.app.place.Element;
import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Offers;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;
import com.termux.app.surfaces.cards.BarOrder.Position;
import com.termux.app.surfaces.cards.BarOrder.Setting;
import com.termux.app.surfaces.rows.ActionRow;
import com.termux.app.surfaces.rows.ChipsRow;
import com.termux.app.surfaces.rows.EditorGlyphs;
import com.termux.app.surfaces.rows.RowKit;
import com.termux.app.surfaces.rows.SectionHeader;
import com.termux.app.surfaces.rows.PreviewTilesRow;
import com.termux.app.surfaces.rows.SegmentRow;
import com.termux.app.surfaces.rows.StepperRow;
import com.termux.app.surfaces.rows.TileGrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Dock card (editor-redesign §2 Dock): Style first, then the Bars list (the apps row, the A–Z
 * strip and the extra keys, each with its own Position and settings), the dock's own glass and
 * shape, its type, and its options. There is no dock-wide Position and no Dock items link.
 *
 * <p>The glass strips are the sheet's own rows; Style, Bars, Dock type and Options are rows the
 * card builds itself (EditorCardSheet.Model#rowView) from the row kinds, reading and writing
 * through its {@link Host}.</p>
 */
public final class DockCard {

    /** The A–Z strip's glass, on or off (editor-redesign §4). Stored now, drawn in wave 5. */
    public static final String KEY_AZ_GLASS_BACKGROUND = "az_glass_background";
    public static final boolean DEFAULT_AZ_GLASS_BACKGROUND = false;
    /** How the A–Z strip shows: {@link #AZ_SHOW_VALUES}. Stored now, drawn in wave 5. */
    public static final String KEY_AZ_SHOW = "az_show";
    /** Shown, Faint, Pull tab, in the order the Show row offers them. */
    public static final String[] AZ_SHOW_VALUES = {"shown", "faint", "pull_tab"};
    /** The palette button on the dock. Stored now, drawn in wave 6. */
    public static final String KEY_COMMAND_PALETTE_BUTTON = "dock_command_palette_button";
    public static final boolean DEFAULT_COMMAND_PALETTE_BUTTON = true;
    /** The dock's New float terminal tile (the apps row's last column); off until switched on. */
    public static final String KEY_FLOAT_TERMINAL_BUTTON = "dock_float_terminal_button";
    public static final boolean DEFAULT_FLOAT_TERMINAL_BUTTON = false;
    /**
     * The palette tile's column in the dock row, an int, one for every layout (the Drawer tile's
     * is per layout). Absent is the row's last ({@code PaletteTilePolicy.DEFAULT_COLUMN}).
     */
    public static final String KEY_COMMAND_PALETTE_COLUMN = "dock_command_palette_column";

    /** The Options tiles, in the order the grid shows them. */
    public enum Option {
        SEARCH_FIELD(R.drawable.ic_symbol_search, R.string.editor_dock_option_search_field, 0),
        DRAWER_ICON(R.drawable.ic_symbol_apps, R.string.editor_dock_option_drawer_icon, 0),
        COMMAND_PALETTE(R.drawable.ic_symbol_terminal, R.string.editor_dock_option_command_palette,
            0),
        NOTIFICATION_DOTS(R.drawable.ic_ms_circle_notifications,
            R.string.editor_dock_option_notification_dots, 0),
        QUICK_REPLY(R.drawable.ic_ms_swipe_up_alt, R.string.editor_dock_option_quick_reply,
            R.string.editor_dock_option_quick_reply_hint),
        MONOCHROME(R.drawable.ic_ed_monochrome, R.string.editor_dock_option_monochrome, 0);

        @DrawableRes final int glyph;
        @StringRes final int word;
        @StringRes final int hint;

        Option(@DrawableRes int glyph, @StringRes int word, @StringRes int hint) {
            this.glyph = glyph;
            this.word = word;
            this.hint = hint;
        }
    }

    /**
     * The Show/hide tiles, in the spec's order (overhaul §2 Dock); Monochrome icons is a switch
     * row of its own ({@link Row#DOCK_MONOCHROME}).
     */
    public static final List<Option> SHOW_HIDE = java.util.Collections.unmodifiableList(
        Arrays.asList(Option.COMMAND_PALETTE, Option.DRAWER_ICON, Option.NOTIFICATION_DOTS,
            Option.QUICK_REPLY, Option.SEARCH_FIELD));

    /** What the card reads and writes. */
    public interface Host {
        @NonNull Context context();

        boolean floating();
        void setFloating(boolean floating);

        /** The Bars list, top to bottom, as it reads on screen. */
        @NonNull List<Element> barOrder();
        void setBarOrder(@NonNull List<Element> order);
        /** Whether the page being edited wears {@code bar} at all (Home has no extra keys). */
        boolean offers(@NonNull Element bar);
        boolean barShown(@NonNull Element bar);
        void setBarShown(@NonNull Element bar, boolean shown);
        @NonNull Position position(@NonNull Element bar);
        void setPosition(@NonNull Element bar, @NonNull Position position);

        int appIcons();
        int appIconsFrom();
        int appIconsTo();
        void setAppIcons(int count);
        /**
         * The most apps that fit in the dock row at the icon size and style it wears now, no
         * more than {@link #appIconsTo}; Apps in the dock's + stops there.
         */
        default int appIconsMostFitting() {
            return appIconsTo();
        }
        /** The icon size's short name (S, M, L, XL), for the "At most N fit" hint. */
        @NonNull default CharSequence iconSizeName() {
            return "";
        }
        boolean mostUsed();
        void setMostUsed(boolean on);
        /** The apps row's icon pack as its row names it. */
        @NonNull CharSequence iconPack();
        void pickIconPack();

        boolean azGlass();
        void setAzGlass(boolean on);
        /** An index of {@link #AZ_SHOW_VALUES}. */
        int azShow();
        void setAzShow(int index);

        void openExtraKeysEditor();

        /** An index of the dock types the Dock type tiles offer ({@link #typeTiles}). */
        int dockType();
        void setDockType(int index);
        boolean option(@NonNull Option option);
        void setOption(@NonNull Option option, boolean on);

        /**
         * The Strip type's row height, in dp (editor-redesign §1: bar Height moves in from the
         * retired Layout mode, where it was the Strip row's handle). Shown under the Dock type
         * tiles while the Strip is the type.
         */
        default int stripHeightDp() {
            return WallStripRowPolicy.DEFAULT_HEIGHT_DP;
        }

        default void setStripHeightDp(int dp) {}
    }

    /** The dock types' glyphs and words, today's, in the order the tiles show them. */
    private static final int[] TYPE_GLYPHS = {R.drawable.ic_symbol_dock_glass_tray,
        R.drawable.ic_symbol_dock_lens_beads, R.drawable.ic_symbol_dock_wall_strip,
        R.drawable.ic_symbol_dock_peek_glass};
    private static final int[] TYPE_WORDS = {R.string.layout_editor_dock_style_glass_tray,
        R.string.layout_editor_dock_style_lens_beads, R.string.layout_editor_dock_style_wall_strip,
        R.string.layout_editor_dock_style_peek_glass};

    /** The Strip's index among the type tiles, which alone carries the Strip height row. */
    static final int STRIP_TYPE = com.termux.app.dock.DockStyle.CHOICES.indexOf(
        com.termux.app.dock.DockStyle.WALL_STRIP);

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        EditorRows.CardRows rows = new EditorRows.CardRows()
            .tier(1, Row.DOCK_TYPE, Row.MARGIN, Row.HOME_ICON_SIZE,
                Row.DOCK_APP_ICONS)
            .tier(3, Row.SECTION_MORE_OPTIONS, Row.DOCK_ICON_PACK, Row.DOCK_OPTIONS,
                Row.DOCK_MONOCHROME)
            .tier(3, Row.SECTION_LAYOUT, Row.DOCK_BARS);
        // Reflection is Peek glass's own knob, shown only while Peek is the type.
        return com.termux.app.surfaces.GlassRows.appearance(rows, offers, 3)
            .tier(3, Row.REFLECTION)
            .tier(3, Row.SECTION_LINKS, Row.LINK_SHEETS, Row.LINK_GLOBAL)
            .build();
    }

    /** The words over the card's glass strip: its Appearance, the dock's own glass and shape. */
    @NonNull
    public static CharSequence stripHeading(@NonNull Context context) {
        return context.getString(R.string.editor_dock_appearance);
    }

    @NonNull private final Host mHost;
    @Nullable private Element mExpanded;
    @Nullable private PreviewTilesRow mStyle;
    @Nullable private BarsList mBars;
    @Nullable private PreviewTilesRow mType;
    /** The Strip type's height, under the type tiles; shown while the Strip is the type. */
    @Nullable private StepperRow mStripHeight;
    @Nullable private TileGrid mOptions;
    @Nullable private com.termux.app.surfaces.rows.SwitchRow mMonochrome;
    @NonNull private final Map<Element, BarViews> mBarViews = new EnumMap<>(Element.class);
    /** A restatement sets the switches; only a touch reports. */
    private boolean mRestating;

    /** One bar's open settings. */
    private static final class BarViews {
        @Nullable SegmentRow position;
        @Nullable List<Position> positions;
        @Nullable MaterialSwitch mostUsed;
        @Nullable MaterialSwitch azGlass;
        @Nullable SegmentRow azShow;
    }

    /** Apps in the dock, and the hint under it while + is out at the fit. */
    @Nullable private StepperRow mAppIcons;
    @Nullable private android.widget.TextView mAppIconsHint;

    /** Apps in the dock's top: the most that fit, never under the bottom or over the top. */
    private int appIconsTop() {
        return clampAppIcons(mHost.appIconsMostFitting(), mHost.appIconsFrom(),
            mHost.appIconsTo());
    }

    /** {@code count} kept inside {@code from}..{@code most}; {@code from} wins a clash. */
    public static int clampAppIcons(int count, int from, int most) {
        return Math.max(from, Math.min(count, most));
    }

    /** Whether "At most N fit at size X." shows: + is out at the fit, not at the hard top. */
    static boolean fitHintShown(int value, int top, int hardTop) {
        return value >= top && top < hardTop;
    }

    private void restateAppIcons() {
        StepperRow row = mAppIcons;
        android.widget.TextView hint = mAppIconsHint;
        if (row == null || hint == null)
            return;
        int top = appIconsTop();
        row.setTo(top);
        row.setValue(mHost.appIcons());
        boolean shown = fitHintShown(row.value(), top, mHost.appIconsTo());
        if (shown)
            RowKit.setText(hint, mHost.context().getString(R.string.editor_dock_apps_fit_hint,
                top, mHost.iconSizeName()));
        int visibility = shown ? View.VISIBLE : View.GONE;
        if (hint.getVisibility() != visibility)
            hint.setVisibility(visibility);
    }

    /** Dock icon pack, under More options. */
    @Nullable private ActionRow mDockIconPack;

    public DockCard(@NonNull Host host) {
        mHost = host;
    }

    /** The bar whose settings are open, or null. */
    @Nullable
    public Element expanded() {
        return mExpanded;
    }

    /**
     * Opens the card on {@code bar}'s settings (a door that names a bar), or with every bar's
     * closed for anything else ({@link BarOrder#expandedOnOpen}).
     */
    public void expand(@Nullable Element bar) {
        mExpanded = BarOrder.expandedOnOpen(bar);
        if (mBars != null)
            mBars.expand(mExpanded);
    }

    /** One of the card's own rows, {@code widthPx} wide; null for a row the sheet draws. */
    @Nullable
    public View view(@NonNull Row row, int widthPx) {
        Context context = mHost.context();
        switch (row) {
            case DOCK_STYLE: {
                mStyle = PreviewTilesRow.build(context, Arrays.asList(
                        glyphTile(context, EditorGlyphs.KB_DOCKED,
                            R.string.appearance_editor_style_docked),
                        glyphTile(context, EditorGlyphs.KB_FLOATING,
                            R.string.appearance_editor_style_floating)),
                    mHost.floating() ? 1 : 0, index -> mHost.setFloating(index == 1));
                return mStyle.picker(context, context.getString(R.string.editor_dock_row_style));
            }
            case DOCK_BARS: {
                mBarViews.clear();
                mBars = BarsList.build(context, barsHost(), widthPx, mExpanded);
                return mBars.root;
            }
            case DOCK_TYPE: {
                List<PreviewTilesRow.Tile> tiles = new ArrayList<>(TYPE_GLYPHS.length);
                for (int i = 0; i < TYPE_GLYPHS.length; i++)
                    tiles.add(glyphTile(context, TYPE_GLYPHS[i], TYPE_WORDS[i]));
                mType = PreviewTilesRow.build(context, tiles, mHost.dockType(), index -> {
                    mHost.setDockType(index);
                    restateType();
                });
                mStripHeight = StepperRow.build(context, R.drawable.ic_ms_height,
                    context.getString(R.string.editor_dock_strip_height),
                    WallStripRowPolicy.MIN_HEIGHT_DP, WallStripRowPolicy.MAX_HEIGHT_DP,
                    WallStripRowPolicy.HEIGHT_STEP_DP, mHost.stripHeightDp(),
                    value -> context.getString(R.string.editor_dock_strip_height_value, value),
                    mHost::setStripHeightDp);
                LinearLayout body = new LinearLayout(context);
                body.setOrientation(LinearLayout.VERTICAL);
                // Type is the Dock section's first row: its tiles, no label row above them.
                mType.root.setContentDescription(context.getString(R.string.editor_dock_row_type));
                body.addView(mType.root);
                body.addView(mStripHeight.root);
                restateStripHeight();
                return body;
            }
            case DOCK_OPTIONS: {
                List<TileGrid.Tile> tiles = new ArrayList<>();
                boolean[] on = new boolean[SHOW_HIDE.size()];
                for (int i = 0; i < SHOW_HIDE.size(); i++) {
                    Option option = SHOW_HIDE.get(i);
                    tiles.add(new TileGrid.Tile(option.glyph, context.getString(option.word),
                        option.hint == 0 ? null : context.getString(option.hint)));
                    on[i] = mHost.option(option);
                }
                mOptions = TileGrid.build(context, tiles, on, widthPx, (index, checked) ->
                    mHost.setOption(SHOW_HIDE.get(index), checked));
                return section(context, R.string.editor_dock_row_options, mOptions.root);
            }
            case DOCK_APP_ICONS: {
                mAppIcons = StepperRow.build(context, R.drawable.ic_ms_tag,
                    context.getString(R.string.editor_dock_apps_in_dock), mHost.appIconsFrom(),
                    appIconsTop(), 1, mHost.appIcons(), String::valueOf, count -> {
                        mHost.setAppIcons(count);
                        // + may just have reached the fit: the hint comes with it.
                        restateAppIcons();
                    });
                mAppIconsHint = RowKit.caption(context, null);
                mAppIconsHint.setPadding(RowKit.dp(context, RowKit.ICON_COLUMN_DP), 0,
                    RowKit.dp(context, RowKit.EDGE_DP), RowKit.dp(context, 8));
                LinearLayout body = new LinearLayout(context);
                body.setOrientation(LinearLayout.VERTICAL);
                body.addView(mAppIcons.root);
                body.addView(mAppIconsHint);
                restateAppIcons();
                return body;
            }
            case DOCK_ICON_PACK: {
                // The pinned apps' pack, the same choice as the Apps row's own Icon pack.
                mDockIconPack = ActionRow.build(context, R.drawable.ic_symbol_icon_pack,
                    context.getString(R.string.editor_dock_icon_pack_row), mHost.iconPack(),
                    v -> mHost.pickIconPack());
                return mDockIconPack.root;
            }
            case DOCK_MONOCHROME: {
                mMonochrome = com.termux.app.surfaces.rows.SwitchRow.build(context,
                    Option.MONOCHROME.glyph, context.getString(Option.MONOCHROME.word),
                    mHost.option(Option.MONOCHROME),
                    on -> mHost.setOption(Option.MONOCHROME, on));
                return mMonochrome.root;
            }
            default:
                return null;
        }
    }

    /** One of the card's own rows read again. */
    public void restate(@NonNull Row row) {
        switch (row) {
            case DOCK_STYLE:
                if (mStyle != null)
                    mStyle.select(mHost.floating() ? 1 : 0);
                break;
            case DOCK_BARS:
                if (mBars != null)
                    mBars.restate();
                break;
            case DOCK_TYPE:
                restateType();
                break;
            case DOCK_OPTIONS:
                if (mOptions != null) {
                    for (int i = 0; i < SHOW_HIDE.size(); i++)
                        mOptions.setOn(i, mHost.option(SHOW_HIDE.get(i)));
                }
                break;
            case DOCK_MONOCHROME:
                if (mMonochrome != null)
                    mMonochrome.setOn(mHost.option(Option.MONOCHROME));
                break;
            case DOCK_APP_ICONS:
                restateAppIcons();
                break;
            case DOCK_ICON_PACK:
                if (mDockIconPack != null)
                    mDockIconPack.setAction(mHost.iconPack());
                break;
            default:
                break;
        }
    }

    private void restateType() {
        PreviewTilesRow type = mType;
        if (type == null)
            return;
        type.select(mHost.dockType());
        restateStripHeight();
    }

    /** A picker option that previews its choice by its glyph. */
    @NonNull
    private static PreviewTilesRow.Tile glyphTile(@NonNull Context context, int glyph, int word) {
        android.widget.ImageView preview = new android.widget.ImageView(context);
        preview.setImageResource(glyph);
        preview.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        return new PreviewTilesRow.Tile(context.getString(word), preview);
    }

    /** The Strip height row shows with the Strip type alone, at the stored height. */
    private void restateStripHeight() {
        StepperRow strip = mStripHeight;
        if (strip == null)
            return;
        strip.root.setVisibility(mHost.dockType() == STRIP_TYPE ? View.VISIBLE : View.GONE);
        strip.setValue(mHost.stripHeightDp());
    }

    // ------------------------------------------------------------------------------- the bars

    @NonNull
    private BarsList.Host barsHost() {
        return new BarsList.Host() {
            @NonNull @Override public List<Element> order() {
                return mHost.barOrder();
            }

            @Override public boolean offers(@NonNull Element bar) {
                return mHost.offers(bar);
            }

            @Override public boolean shown(@NonNull Element bar) {
                return mHost.barShown(bar);
            }

            @Override public void setShown(@NonNull Element bar, boolean shown) {
                mHost.setBarShown(bar, shown);
            }

            @Override public void reorder(@NonNull List<Element> order) {
                mHost.setBarOrder(order);
            }

            @NonNull @Override public View settings(@NonNull Element bar, int widthPx) {
                return barSettings(bar, widthPx);
            }

            @Override public void restateSettings(@NonNull Element bar) {
                restateBar(bar);
            }

            @Override public void expanded(@Nullable Element bar) {
                mExpanded = bar;
            }
        };
    }

    /** {@code bar}'s settings, as {@link BarOrder#settingsOf} lists them. */
    @NonNull
    private View barSettings(@NonNull Element bar, int widthPx) {
        Context context = mHost.context();
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, 0, 0, RowKit.dp(context, 8));
        BarViews views = new BarViews();
        mBarViews.put(bar, views);
        for (Setting setting : BarOrder.settingsOf(bar)) {
            switch (setting) {
                case POSITION: {
                    List<Position> positions = BarOrder.positionsFor(bar);
                    List<SegmentRow.Segment> segments = new ArrayList<>(positions.size());
                    for (Position position : positions)
                        segments.add(SegmentRow.Segment.glyph(glyphOf(position),
                            context.getString(nameOf(position))));
                    views.positions = positions;
                    views.position = SegmentRow.build(context, R.drawable.ic_ms_open_in_full,
                        context.getString(R.string.editor_dock_position), segments,
                        positions.indexOf(mHost.position(bar)), widthPx,
                        index -> mHost.setPosition(bar, positions.get(index)));
                    column.addView(views.position.root);
                    break;
                }
                case MOST_USED:
                    views.mostUsed = switchRow(context, column, R.drawable.ic_ms_star,
                        R.string.editor_dock_most_used, mHost.mostUsed(), mHost::setMostUsed);
                    break;
                case GLASS_BACKGROUND:
                    views.azGlass = switchRow(context, column, R.drawable.ic_ms_blur_on,
                        R.string.editor_dock_az_glass, mHost.azGlass(), mHost::setAzGlass);
                    break;
                case SHOW:
                    views.azShow = SegmentRow.build(context, R.drawable.ic_ms_visibility,
                        context.getString(R.string.editor_dock_az_show), Arrays.asList(
                            SegmentRow.Segment.glyph(EditorGlyphs.AZ_SHOWN,
                                context.getString(R.string.editor_dock_az_shown)),
                            SegmentRow.Segment.glyph(EditorGlyphs.AZ_FAINT,
                                context.getString(R.string.editor_dock_az_faint)),
                            SegmentRow.Segment.glyph(EditorGlyphs.AZ_PULL_TAB,
                                context.getString(R.string.editor_dock_az_pull_tab))),
                        mHost.azShow(), widthPx, mHost::setAzShow);
                    column.addView(views.azShow.root);
                    column.addView(hint(context, R.string.editor_dock_az_hint));
                    break;
                case EXTRA_KEYS_EDITOR: {
                    ChipsRow chips = ChipsRow.build(context, Collections.singletonList(
                        new ChipsRow.Link(R.drawable.ic_symbol_edit,
                            context.getString(R.string.editor_dock_extra_keys_editor))),
                        index -> mHost.openExtraKeysEditor());
                    column.addView(chips.root);
                    break;
                }
                default:
                    break;
            }
        }
        return column;
    }

    /** {@code bar}'s open settings read again. */
    private void restateBar(@NonNull Element bar) {
        BarViews views = mBarViews.get(bar);
        if (views == null)
            return;
        mRestating = true;
        try {
            restateBar(bar, views);
        } finally {
            mRestating = false;
        }
    }

    private void restateBar(@NonNull Element bar, @NonNull BarViews views) {
        if (views.position != null && views.positions != null)
            views.position.select(views.positions.indexOf(mHost.position(bar)));
        if (views.mostUsed != null && views.mostUsed.isChecked() != mHost.mostUsed())
            views.mostUsed.setChecked(mHost.mostUsed());
        if (views.azGlass != null && views.azGlass.isChecked() != mHost.azGlass())
            views.azGlass.setChecked(mHost.azGlass());
        if (views.azShow != null)
            views.azShow.select(mHost.azShow());
    }

    // ----------------------------------------------------------------------------- the pieces

    /** The glyph of a position: the phone outline with the bar on that edge. */
    @DrawableRes
    static int glyphOf(@NonNull Position position) {
        switch (position) {
            case TOP: return EditorGlyphs.POS_TOP;
            case BOTTOM: return EditorGlyphs.POS_BOTTOM;
            case LEFT: return EditorGlyphs.POS_LEFT;
            case RIGHT: return EditorGlyphs.POS_RIGHT;
            case ABOVE_KEYBOARD: return EditorGlyphs.POS_ABOVE_KEYBOARD;
            case RIDES_APPS_ROW:
            default: return EditorGlyphs.POS_RIDES_APPS_ROW;
        }
    }

    @StringRes
    static int nameOf(@NonNull Position position) {
        switch (position) {
            case TOP: return R.string.editor_dock_position_top;
            case BOTTOM: return R.string.editor_dock_position_bottom;
            case LEFT: return R.string.editor_dock_position_left;
            case RIGHT: return R.string.editor_dock_position_right;
            case ABOVE_KEYBOARD: return R.string.editor_dock_position_above_keyboard;
            case RIDES_APPS_ROW:
            default: return R.string.editor_dock_position_rides_apps_row;
        }
    }

    /** A section's heading over {@code body}. */
    @NonNull
    private static View section(@NonNull Context context, @StringRes int title,
                                @NonNull View body) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(SectionHeader.build(context, context.getString(title), null, false).root);
        column.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return column;
    }

    /** A switch row in the Settings row's measures: glyph, 16 sp title, switch at the end. */
    @NonNull
    private MaterialSwitch switchRow(@NonNull Context context, @NonNull LinearLayout into,
                                            @DrawableRes int glyph, @StringRes int title,
                                            boolean on, @NonNull OnSwitch onSwitch) {
        LinearLayout line = RowKit.line(context);
        String name = context.getString(title);
        line.addView(RowKit.glyph(context, glyph, name), RowKit.glyphParams(context));
        line.addView(RowKit.title(context, name), new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialSwitch toggle = new MaterialSwitch(context);
        toggle.setContentDescription(name);
        toggle.setChecked(on);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (!mRestating)
                onSwitch.onSwitch(checked);
        });
        line.addView(toggle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        line.setOnClickListener(v -> toggle.toggle());
        into.addView(line);
        return toggle;
    }

    private interface OnSwitch {
        void onSwitch(boolean on);
    }

    /** A note under a row: as many lines as it needs, from the title's column. */
    @NonNull
    private static TextView hint(@NonNull Context context, @StringRes int text) {
        TextView hint = RowKit.caption(context, context.getString(text));
        hint.setMaxLines(Integer.MAX_VALUE);
        hint.setEllipsize((TextUtils.TruncateAt) null);
        int edge = RowKit.dp(context, RowKit.EDGE_DP);
        hint.setPadding(RowKit.dp(context, RowKit.ICON_COLUMN_DP), 0, edge,
            RowKit.dp(context, 8));
        return hint;
    }
}
