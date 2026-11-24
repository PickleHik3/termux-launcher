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
    /** The New float terminal tile's column, kept the way the palette tile's is. */
    public static final String KEY_FLOAT_TERMINAL_COLUMN = "dock_float_terminal_column";

    /** The Options tiles, in the order the grid shows them. */
    public enum Option {
        SEARCH_FIELD(R.drawable.ic_symbol_search, R.string.editor_dock_option_search_field, 0),
        DRAWER_ICON(R.drawable.ic_rl_apps, R.string.editor_dock_option_drawer_icon, 0),
        COMMAND_PALETTE(R.drawable.ic_rl_terminal, R.string.editor_dock_option_command_palette,
            0),
        FLOAT_TERMINAL(R.drawable.ic_rl_window_add,
            R.string.editor_dock_option_float_terminal, 0),
        NOTIFICATION_DOTS(R.drawable.ic_ms_circle_notifications,
            R.string.editor_dock_option_notification_dots, 0),
        QUICK_REPLY(R.drawable.ic_ms_swipe_up_alt, R.string.editor_dock_option_quick_reply,
            R.string.editor_dock_option_quick_reply_hint),
        MONOCHROME(R.drawable.ic_ed_monochrome, R.string.editor_dock_option_monochrome, 0),
        MOST_USED(R.drawable.ic_ms_star, R.string.editor_dock_most_used, 0);

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
        Arrays.asList(Option.COMMAND_PALETTE, Option.FLOAT_TERMINAL, Option.NOTIFICATION_DOTS,
            Option.QUICK_REPLY));

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
    private static final int[] TYPE_GLYPHS = {R.drawable.ic_rl_dock_tray,
        R.drawable.ic_rl_dock_beads, R.drawable.ic_rl_dock_strip,
        R.drawable.ic_rl_dock_peek};
    private static final int[] TYPE_WORDS = {R.string.layout_editor_dock_style_glass_tray,
        R.string.layout_editor_dock_style_lens_beads, R.string.layout_editor_dock_style_wall_strip,
        R.string.layout_editor_dock_style_peek_glass};

    /** The Strip's index among the type tiles, which alone carries the Strip height row. */
    static final int STRIP_TYPE = com.termux.app.dock.DockStyle.CHOICES.indexOf(
        com.termux.app.dock.DockStyle.WALL_STRIP);

    /** Every row of the card, tier by tier, in the order the card shows them. */
    @NonNull
    public static List<Spec> rows(@NonNull Offers offers) {
        // Reflection is Peek glass's own knob, shown only while Peek is the type.
        // The design, icons and icon options are the apps row's: a place without one lists none.
        EditorRows.CardRows rows = new EditorRows.CardRows();
        if (offers.appsRow)
            rows.tier(1, Row.DOCK_TYPE, Row.DOCK_APP_ICONS)
                .tier(2, Row.DOCK_ICON_PACK, Row.DOCK_OPTIONS, Row.DOCK_MONOCHROME,
                    Row.DOCK_MOST_USED);
        return rows
            .pair(3, Row.OPACITY, Row.FROST)
            .tier(3, Row.REFLECTION)
            .build();
    }

    /** The words over the card's glass strip: its Appearance, the dock's own glass and shape. */
    @NonNull
    public static CharSequence stripHeading(@NonNull Context context) {
        return context.getString(R.string.editor_dock_appearance);
    }

    @NonNull private final Host mHost;
    @Nullable private PreviewTilesRow mStyle;
    @Nullable private PreviewTilesRow mType;
    /** The Strip type's height, under the type tiles; shown while the Strip is the type. */
    @Nullable private StepperRow mStripHeight;
    @Nullable private com.termux.app.surfaces.rows.SwitchChipsRow mOptions;
    /** A restatement sets the switches; only a touch reports. */
    private boolean mRestating;

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
                // The show/hide options and Monochrome icons share one row of switch chips;
                // Monochrome's own row stands in it.
                List<com.termux.app.surfaces.rows.SwitchChipsRow.Item> items = new ArrayList<>();
                for (Option option : chipOptions())
                    items.add(new com.termux.app.surfaces.rows.SwitchChipsRow.Item(option.glyph,
                        context.getString(option.word)));
                List<Option> options = chipOptions();
                mOptions = com.termux.app.surfaces.rows.SwitchChipsRow.build(context, items,
                    (index, checked) -> setChip(options.get(index), checked));
                restateOptions();
                return mOptions.root;
            }
            case DOCK_MONOCHROME:
            case DOCK_MOST_USED: {
                return new android.widget.Space(context);
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
            case AZ_GLASS: {
                mAzGlass = com.termux.app.surfaces.rows.SwitchRow.build(context,
                    R.drawable.ic_ms_blur_on, context.getString(R.string.editor_dock_az_glass),
                    mHost.azGlass(), mHost::setAzGlass);
                return mAzGlass.root;
            }
            case AZ_SHOW: {
                mAzShow = SegmentRow.build(context, R.drawable.ic_ms_visibility,
                    context.getString(R.string.editor_dock_az_show), Arrays.asList(
                        SegmentRow.Segment.glyph(EditorGlyphs.AZ_SHOWN,
                            context.getString(R.string.editor_dock_az_shown)),
                        SegmentRow.Segment.glyph(EditorGlyphs.AZ_FAINT,
                            context.getString(R.string.editor_dock_az_faint)),
                        SegmentRow.Segment.glyph(EditorGlyphs.AZ_PULL_TAB,
                            context.getString(R.string.editor_dock_az_pull_tab))),
                    mHost.azShow(), widthPx, mHost::setAzShow);
                return mAzShow.root;
            }
            case EXTRA_KEYS_EDITOR: {
                ChipsRow chips = ChipsRow.build(context, Collections.singletonList(
                    new ChipsRow.Link(R.drawable.ic_symbol_edit,
                        context.getString(R.string.editor_dock_extra_keys_editor))),
                    index -> mHost.openExtraKeysEditor());
                return chips.root;
            }
            default:
                return null;
        }
    }

    @Nullable private com.termux.app.surfaces.rows.SwitchRow mAzGlass;
    @Nullable private SegmentRow mAzShow;

    /** The rows of the small A-Z card: the dock's Opacity and Frost, Glass background, Show. */
    @NonNull
    public static List<Spec> azRows() {
        return new EditorRows.CardRows()
            .pair(1, Row.OPACITY, Row.FROST)
            .tier(1, Row.AZ_GLASS, Row.AZ_SHOW)
            .build();
    }

    /** The rows of the small Extra keys card: the dock's Opacity and Frost, the editor link. */
    @NonNull
    public static List<Spec> extraKeysRows() {
        return new EditorRows.CardRows()
            .pair(1, Row.OPACITY, Row.FROST)
            .links(1, Row.EXTRA_KEYS_EDITOR)
            .build();
    }

    /** One of the card's own rows read again. */
    public void restate(@NonNull Row row) {
        switch (row) {
            case DOCK_STYLE:
                if (mStyle != null)
                    mStyle.select(mHost.floating() ? 1 : 0);
                break;
            case DOCK_TYPE:
                restateType();
                break;
            case DOCK_OPTIONS:
                restateOptions();
                break;
            case DOCK_MONOCHROME:
                restateOptions();
                break;
            case AZ_GLASS:
                if (mAzGlass != null)
                    mAzGlass.setOn(mHost.azGlass());
                break;
            case AZ_SHOW:
                if (mAzShow != null)
                    mAzShow.select(mHost.azShow());
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

    /** The options the chips row holds, in order: the show/hide ones, then Monochrome icons. */
    @NonNull
    static List<Option> chipOptions() {
        List<Option> options = new ArrayList<>(SHOW_HIDE);
        options.add(Option.MONOCHROME);
        options.add(Option.MOST_USED);
        return options;
    }

    /** Most used is the apps row's; every other chip is a dock option. */
    private boolean chipOn(@NonNull Option option) {
        return option == Option.MOST_USED ? mHost.mostUsed() : mHost.option(option);
    }

    private void setChip(@NonNull Option option, boolean on) {
        if (option == Option.MOST_USED)
            mHost.setMostUsed(on);
        else
            mHost.setOption(option, on);
    }

    private void restateOptions() {
        com.termux.app.surfaces.rows.SwitchChipsRow chips = mOptions;
        if (chips == null)
            return;
        List<Option> options = chipOptions();
        for (int i = 0; i < options.size(); i++)
            chips.setChecked(i, chipOn(options.get(i)));
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
        // The tile row tints the glyph with its name's ink (PreviewTilesRow).
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
