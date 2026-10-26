package com.termux.app.surfaces;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.surfaces.EditorRows.Kind;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;
import com.termux.app.surfaces.cards.CardRowView;
import com.termux.app.surfaces.rows.ActionRow;
import com.termux.app.surfaces.rows.ChipsRow;
import com.termux.app.surfaces.rows.PreviewTilesRow;
import com.termux.app.surfaces.rows.RowKit;
import com.termux.app.surfaces.rows.SectionHeader;
import com.termux.app.surfaces.rows.SegmentRow;
import com.termux.app.surfaces.rows.StepperRow;
import com.termux.app.surfaces.rows.StripRow;
import com.termux.app.surfaces.rows.TileGrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Keyboard, Home, Global and Workspace cards' rows (editor-redesign §1 and §2), built from the
 * row kinds under {@code surfaces/rows}: section headers with their notes, glyph or word
 * segments, strips with their glyph on top (and the link between two of them), steppers, the
 * Look's preview tiles, a grid of option tiles, action rows, and the More chips. Rows of one kind
 * that follow one another in a tier share one widget: strips one strip, switches one tile grid,
 * links one chips row.
 *
 * <p>It is the per-row builder behind {@link EditorCardSheet.Model#rowView}, as the Status bar,
 * Terminal and Dock cards' files are: the sheet asks for each row in turn, the first row of a
 * shared widget gets the widget and the rest of its rows an empty view, and {@link #restate}
 * reads a row (with the widget it stands in) again. Views only: every value comes from its
 * {@link EditorCardSheet.Model} and every touch goes to it, and the row kinds report touches only,
 * so a restatement never echoes back as a write.</p>
 */
final class CardBody {

    /** Whether {@code card} is drawn with these rows. */
    static boolean redesigned(@NonNull EditorCard card) {
        return card == EditorCard.KEYBOARD || card == EditorCard.HOME
            || card == EditorCard.APPEARANCE || card == EditorCard.WORKSPACE;
    }

    /**
     * What a card says of a row beyond its values: the name, glyph, colour swatches and typeface
     * it gives the row where they are not the row's own.
     */
    interface Looks {
        /** The name the card gives {@code row} ("Background opacity", the chip "Theme"), or null. */
        @Nullable
        default CharSequence name(@NonNull Row row) {
            return null;
        }

        /** The glyph the card gives {@code row}, or 0 for the row's own. */
        default int glyph(@NonNull Row row) {
            return 0;
        }

        /** A row's choices as colour swatches, one per choice (Tint colour), or null. */
        @Nullable
        default int[] swatches(@NonNull Row row) {
            return null;
        }

        /** The typeface a choice's word is written in (Numerals), or null for the row's own. */
        @Nullable
        default Typeface choiceTypeface(@NonNull Row row, int index) {
            return null;
        }
    }

    /** How a row is drawn here: its {@link Kind}, or the row itself where the Kind stands in. */
    enum Shape { STRIP, STRIP_LINK, TILES, CHIPS, SEGMENTS, MENU, STEPPER, SECTION, HINT, LOOK,
        ACTION }

    /** How {@code row} is drawn on these cards. */
    @NonNull
    static Shape shape(@NonNull Row row) {
        switch (row) {
            case OPACITY_LINK:
                return Shape.STRIP_LINK;
            case HOME_COLUMNS:
            case HOME_ROWS:
                return Shape.STEPPER;
            case SECTION_GLASS:
            case SECTION_SHAPE:
            case SECTION_MORE:
            case SECTION_GRID:
            case SECTION_LOOK:
            case SECTION_LABELS_FOLDERS:
            case SECTION_OPTIONS:
            case SECTION_WALLPAPER:
            case SECTION_COLOUR:
            case SECTION_GLASS_MATERIAL:
                return Shape.SECTION;
            case HINT_GLASS_MATERIAL:
                return Shape.HINT;
            default:
                break;
        }
        switch (row.kind) {
            case STRIP: return Shape.STRIP;
            case SWITCH: return Shape.TILES;
            case LINK:
            case EXPANDER:
                return Shape.CHIPS;
            case SEGMENTS: return Shape.SEGMENTS;
            case MENU: return Shape.MENU;
            case LOOK: return Shape.LOOK;
            case WALLPAPER:
            case ACTION:
            case CHIPS:
            default:
                return Shape.ACTION;
        }
    }

    /** What a Look tile's glass shows: its opacity over the backdrop, Clear to Solid. */
    private static final float[] LOOK_FILL = {0f, 0.3f, 0.5f, 0.92f};
    private static final float CUSTOM_FILL = 0.55f;

    /** Rows drawn as one widget, and how that widget reads again once it is built. */
    private static final class Group {
        @NonNull final List<Row> rows;
        /** Whether the divider stands over it: not first on the card or in its tier. */
        final boolean divider;
        @Nullable CardRowView built;

        Group(@NonNull List<Row> rows, boolean divider) {
            this.rows = rows;
            this.divider = divider;
        }
    }

    @NonNull private final Context mContext;
    @NonNull private final EditorCardSheet.Model mModel;
    @NonNull private final Looks mLooks;
    @NonNull private final EditorCard mCard;
    @NonNull private final List<Spec> mSpecs;
    @NonNull private final Runnable mExpand;
    /** The group each row of the card stands in. */
    @NonNull private final Map<Row, Group> mGroups = new EnumMap<>(Row.class);

    /**
     * The body of {@code card} with its rows {@code specs}; {@code expand} raises the sheet to its
     * full detent (an expander chip opened its panel).
     */
    CardBody(@NonNull Context context, @NonNull EditorCardSheet.Model model, @NonNull Looks looks,
             @NonNull EditorCard card, @NonNull List<Spec> specs, @NonNull Runnable expand) {
        mContext = context;
        mModel = model;
        mLooks = looks;
        mCard = card;
        mSpecs = Collections.unmodifiableList(new ArrayList<>(specs));
        mExpand = expand;
        int i = 0;
        while (i < specs.size()) {
            int end = groupEnd(specs, i);
            List<Row> rows = new ArrayList<>(end - i);
            for (int j = i; j < end; j++)
                rows.add(specs.get(j).row);
            Group group = new Group(rows, i > 0 && specs.get(i - 1).tier == specs.get(i).tier);
            for (Row row : rows)
                mGroups.put(row, group);
            i = end;
        }
    }

    /** Whether this body draws {@code card} with exactly the rows {@code specs}. */
    boolean drawsFor(@NonNull EditorCard card, @NonNull List<Spec> specs) {
        return mCard == card && mSpecs.equals(specs);
    }

    /** Whether the card holds {@code row}. */
    boolean holds(@NonNull Row row) {
        return mGroups.containsKey(row);
    }

    /**
     * {@code row}'s view, {@code widthPx} wide: the widget of the group it starts (built afresh,
     * read from the values), an empty view for a row a widget before it already draws, or null
     * for a row the card does not hold.
     */
    @Nullable
    View rowView(@NonNull Row row, int widthPx) {
        Group group = mGroups.get(row);
        if (group == null)
            return null;
        if (group.rows.get(0) != row)
            return new Space(mContext);
        CardRowView built = buildGroup(group, widthPx);
        group.built = built;
        built.restate();
        return built.view;
    }

    /** Reads {@code row} again, with the widget it stands in; nothing for a row not built here. */
    void restate(@NonNull Row row, @NonNull View view) {
        Group group = mGroups.get(row);
        CardRowView built = group == null ? null : group.built;
        if (built != null)
            built.restate();
    }

    // ------------------------------------------------------------------------------- grouping

    /** Where the group that starts at {@code start} ends (exclusive); a group keeps to its tier. */
    private static int groupEnd(@NonNull List<Spec> specs, int start) {
        Spec first = specs.get(start);
        int end = start + 1;
        while (end < specs.size() && specs.get(end).tier == first.tier
            && sameGroup(shape(first.row), shape(specs.get(end).row)))
            end++;
        return end;
    }

    private static boolean sameGroup(@NonNull Shape first, @NonNull Shape next) {
        switch (first) {
            case STRIP:
            case STRIP_LINK:
                return next == Shape.STRIP || next == Shape.STRIP_LINK;
            case TILES:
                return next == Shape.TILES;
            case CHIPS:
                return next == Shape.CHIPS;
            default:
                return false;
        }
    }

    @NonNull
    private CardRowView buildGroup(@NonNull Group group, int widthPx) {
        List<Row> rows = group.rows;
        Row first = rows.get(0);
        switch (shape(first)) {
            case STRIP:
            case STRIP_LINK:
                return strips(rows, widthPx);
            case TILES:
                return tiles(rows, widthPx);
            case CHIPS:
                return chips(rows);
            case SEGMENTS:
                return segments(first, widthPx);
            case MENU:
                return mLooks.swatches(first) != null ? segments(first, widthPx) : menu(first);
            case STEPPER:
                return stepper(first);
            case SECTION:
                return section(first, group.divider);
            case HINT:
                return hint(first);
            case LOOK:
                return looks(first);
            case ACTION:
            default:
                return action(first);
        }
    }

    /** {@code row}'s name on this card. */
    @NonNull
    private CharSequence name(@NonNull Row row) {
        CharSequence name = mLooks.name(row);
        return name != null ? name : mContext.getString(row.labelRes);
    }

    /** {@code row}'s glyph on this card. */
    @DrawableRes
    private int glyph(@NonNull Row row) {
        int glyph = mLooks.glyph(row);
        return glyph != 0 ? glyph : row.iconRes;
    }

    private void showRoot(@NonNull View root, @NonNull Row row) {
        int visibility = mModel.shown(row) ? View.VISIBLE : View.GONE;
        if (root.getVisibility() != visibility)
            root.setVisibility(visibility);
    }

    // ---------------------------------------------------------------------------------- kinds

    /** A strip run; the opacity link in it links the strips either side. */
    @NonNull
    private CardRowView strips(@NonNull List<Row> rows, int widthPx) {
        List<Row> columns = new ArrayList<>();
        List<StripRow.Strip> strips = new ArrayList<>();
        Row linkRow = null;
        int linkAfter = -1;
        for (Row row : rows) {
            if (shape(row) == Shape.STRIP_LINK) {
                linkRow = row;
                linkAfter = columns.size() - 1;
                continue;
            }
            columns.add(row);
            strips.add(new StripRow.Strip(glyph(row), name(row), mModel.from(row),
                mModel.to(row), mModel.step(row), value -> mModel.readout(row, value)));
        }
        int[] values = new int[columns.size()];
        for (int i = 0; i < values.length; i++)
            values[i] = mModel.value(columns.get(i));
        Row link = linkRow;
        StripRow strip = StripRow.build(mContext, strips, values, widthPx,
            new StripRow.Listener() {
                @Override public void onValue(int index, int value, boolean dragging) {
                    mModel.onValue(columns.get(index), value, dragging);
                }

                @Override public void onReleased(int index) {
                    mModel.onReleased(columns.get(index));
                }
            },
            linkAfter, link == null ? "" : name(link), link != null && mModel.value(link) != 0,
            on -> {
                if (link != null)
                    mModel.onValue(link, on ? 1 : 0, false);
            });
        return new CardRowView(strip.root, () -> {
            boolean any = false;
            for (int i = 0; i < columns.size(); i++) {
                Row row = columns.get(i);
                boolean shown = mModel.shown(row);
                strip.setShown(i, shown);
                any |= shown;
                strip.setValue(i, mModel.value(row));
                // The set-here dot: the surface's own value, where Reset would take it back.
                strip.columns.get(i).slider.setMarked(mModel.ownValue(row));
            }
            if (link != null)
                strip.setLinked(mModel.value(link) != 0);
            int visibility = any ? View.VISIBLE : View.GONE;
            if (strip.root.getVisibility() != visibility)
                strip.root.setVisibility(visibility);
        });
    }

    /** A run of switches as one grid of tiles; a tile's long-press is its row's summary. */
    @NonNull
    private CardRowView tiles(@NonNull List<Row> rows, int widthPx) {
        List<TileGrid.Tile> tiles = new ArrayList<>(rows.size());
        boolean[] on = new boolean[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            tiles.add(new TileGrid.Tile(glyph(row), name(row), mModel.summary(row)));
            on[i] = mModel.value(row) != 0;
        }
        TileGrid grid = TileGrid.build(mContext, tiles, on, widthPx,
            (index, checked) -> mModel.onValue(rows.get(index), checked ? 1 : 0, false));
        return new CardRowView(grid.root, () -> {
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                grid.setOn(i, mModel.value(row) != 0);
                MaterialButton tile = grid.tiles.get(i);
                boolean enabled = mModel.enabled(row);
                if (tile.isEnabled() != enabled)
                    tile.setEnabled(enabled);
                int visibility = mModel.shown(row) ? View.VISIBLE : View.INVISIBLE;
                if (tile.getVisibility() != visibility)
                    tile.setVisibility(visibility);
            }
        });
    }

    /**
     * A run of links as one chips row. An expander's chip opens its panel under the row (the
     * icon packs), and the sheet rises to its full detent to show it.
     */
    @NonNull
    private CardRowView chips(@NonNull List<Row> rows) {
        List<ChipsRow.Link> links = new ArrayList<>(rows.size());
        for (Row row : rows)
            links.add(new ChipsRow.Link(glyph(row), name(row)));
        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(LinearLayout.VERTICAL);
        FrameLayout panel = new FrameLayout(mContext);
        panel.setVisibility(View.GONE);
        Row[] open = {null};
        ChipsRow chips = ChipsRow.build(mContext, links, index -> {
            Row row = rows.get(index);
            View anchor = ((ViewGroup) root.getChildAt(0)).getChildAt(index);
            if (row.kind != Kind.EXPANDER) {
                mModel.onAction(row, anchor);
                return;
            }
            boolean opening = open[0] != row;
            panel.removeAllViews();
            if (opening) {
                View content = mModel.expanderContent(row);
                if (content != null) {
                    if (content.getParent() instanceof ViewGroup)
                        ((ViewGroup) content.getParent()).removeView(content);
                    panel.addView(content, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
            }
            open[0] = opening ? row : null;
            panel.setVisibility(opening ? View.VISIBLE : View.GONE);
            mModel.onAction(row, anchor);
            if (opening)
                mExpand.run();
        });
        root.addView(chips.root, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(panel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return new CardRowView(root, () -> {
            for (int i = 0; i < rows.size(); i++) {
                int visibility = mModel.shown(rows.get(i)) ? View.VISIBLE : View.GONE;
                View chip = chips.chips.get(i);
                if (chip.getVisibility() != visibility)
                    chip.setVisibility(visibility);
            }
        });
    }

    /**
     * One choice of a few: glyphs alone where the model names glyphs ({@code chipIcons}),
     * colour swatches where the card names colours, words otherwise, each in its own typeface
     * where the card gives one.
     */
    @NonNull
    private CardRowView segments(@NonNull Row row, int widthPx) {
        String[] choices = mModel.choices(row);
        int[] glyphs = mModel.chipIcons(row);
        int[] swatches = mLooks.swatches(row);
        List<SegmentRow.Segment> segments = new ArrayList<>(choices.length);
        for (int i = 0; i < choices.length; i++) {
            if (swatches != null)
                segments.add(SegmentRow.Segment.glyph(R.drawable.ic_editor_tint, choices[i]));
            else if (glyphs != null && i < glyphs.length)
                segments.add(SegmentRow.Segment.glyph(glyphs[i], choices[i]));
            else
                segments.add(SegmentRow.Segment.word(choices[i]));
        }
        SegmentRow segmentRow = SegmentRow.build(mContext, glyph(row), name(row), segments,
            mModel.value(row), widthPx, index -> mModel.onValue(row, index, false));
        for (int i = 0; i < segmentRow.buttons.size(); i++) {
            MaterialButton button = segmentRow.buttons.get(i);
            if (swatches != null && i < swatches.length) {
                button.setIcon(swatch(swatches[i]));
                button.setIconTint(null);
            }
            Typeface typeface = mLooks.choiceTypeface(row, i);
            if (typeface != null)
                button.setTypeface(typeface);
        }
        return new CardRowView(segmentRow.root, () -> {
            showRoot(segmentRow.root, row);
            segmentRow.select(mModel.value(row));
        });
    }

    /** A colour swatch for a segment: a filled circle the size of a glyph. */
    @NonNull
    private GradientDrawable swatch(int color) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        dot.setStroke(Math.max(1, RowKit.dp(mContext, 1)), RowKit.color(mContext,
            com.google.android.material.R.attr.colorOutlineVariant));
        int size = RowKit.dp(mContext, 18);
        dot.setSize(size, size);
        return dot;
    }

    /** A menu with no swatches: its current choice as the word at its end, which opens it. */
    @NonNull
    private CardRowView menu(@NonNull Row row) {
        ActionRow action = ActionRow.build(mContext, glyph(row), name(row), mModel.summary(row),
            v -> mModel.onAction(row, v));
        return new CardRowView(action.root, () -> {
            showRoot(action.root, row);
            action.setAction(mModel.summary(row));
        });
    }

    @NonNull
    private CardRowView stepper(@NonNull Row row) {
        StepperRow stepper = StepperRow.build(mContext, glyph(row), name(row), mModel.from(row),
            mModel.to(row), mModel.step(row), mModel.value(row),
            value -> mModel.readout(row, value), value -> mModel.onValue(row, value, false));
        return new CardRowView(stepper.root, () -> {
            showRoot(stepper.root, row);
            stepper.setValue(mModel.value(row));
        });
    }

    /**
     * A section's header; its note is the model's summary for it. The divider stands over it
     * unless it opens the card or a tier, where the sheet's own divider already stands.
     */
    @NonNull
    private CardRowView section(@NonNull Row row, boolean divider) {
        SectionHeader header = SectionHeader.build(mContext, name(row), mModel.summary(row),
            divider);
        return new CardRowView(header.root, () -> showRoot(header.root, row));
    }

    @NonNull
    private CardRowView hint(@NonNull Row row) {
        TextView hint = RowKit.caption(mContext, name(row));
        hint.setMaxLines(3);
        hint.setEllipsize(null);
        int edge = RowKit.dp(mContext, RowKit.EDGE_DP);
        hint.setPadding(edge, 0, edge, RowKit.dp(mContext, 8));
        return new CardRowView(hint, () -> showRoot(hint, row));
    }

    /**
     * An action row: the word at its end is the model's readout (the Match rows' Copy or Undo),
     * or Change for the wallpaper.
     */
    @NonNull
    private CardRowView action(@NonNull Row row) {
        boolean wallpaper = row.kind == Kind.WALLPAPER;
        ActionRow action = ActionRow.build(mContext, glyph(row), name(row),
            wallpaper ? mContext.getString(R.string.appearance_wallpaper_change)
                : mModel.readout(row, mModel.value(row)),
            v -> mModel.onAction(row, v));
        return new CardRowView(action.root, () -> {
            showRoot(action.root, row);
            if (!wallpaper)
                action.setAction(mModel.readout(row, mModel.value(row)));
        });
    }

    /** The Look's stops as preview tiles: each a swatch of glass at its stop's opacity. */
    @NonNull
    private CardRowView looks(@NonNull Row row) {
        String[] names = mModel.choices(row);
        List<PreviewTilesRow.Tile> tiles = new ArrayList<>(names.length);
        for (int i = 0; i < names.length; i++)
            tiles.add(new PreviewTilesRow.Tile(names[i], lookPreview(i)));
        PreviewTilesRow looks = PreviewTilesRow.build(mContext, tiles, mModel.value(row), index -> {
            mModel.onValue(row, index, false);
            mModel.onReleased(row);
        });
        return new CardRowView(looks.root, () -> {
            showRoot(looks.root, row);
            looks.select(mModel.value(row));
        });
    }

    /** A Look tile's preview: glass at the stop's opacity over a tinted ground; Custom's tune. */
    @NonNull
    private View lookPreview(int stop) {
        boolean custom = AppearanceLooks.isCustomStop(stop);
        float fill = custom ? CUSTOM_FILL : LOOK_FILL[Math.max(0, Math.min(stop,
            LOOK_FILL.length - 1))];
        FrameLayout box = new FrameLayout(mContext);
        int radius = RowKit.dp(mContext, 8);
        GradientDrawable ground = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[] {RowKit.color(mContext, androidx.appcompat.R.attr.colorPrimary),
                RowKit.color(mContext, com.google.android.material.R.attr.colorTertiary)});
        ground.setCornerRadius(radius);
        box.setBackground(ground);
        View glass = new View(mContext);
        GradientDrawable pane = new GradientDrawable();
        int surface = RowKit.color(mContext, com.google.android.material.R.attr.colorSurface);
        pane.setColor((Math.round(fill * 255) << 24) | (surface & 0x00FFFFFF));
        pane.setCornerRadius(radius);
        glass.setBackground(pane);
        int inset = RowKit.dp(mContext, 4);
        FrameLayout.LayoutParams paneParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        paneParams.setMargins(inset, inset, inset, inset);
        box.addView(glass, paneParams);
        if (custom) {
            ImageView tune = new ImageView(mContext);
            tune.setImageResource(R.drawable.ic_ms_tune);
            tune.setImageTintList(ColorStateList.valueOf(RowKit.color(mContext,
                com.google.android.material.R.attr.colorOnSurface)));
            int size = RowKit.dp(mContext, 18);
            box.addView(tune, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        }
        return box;
    }
}
