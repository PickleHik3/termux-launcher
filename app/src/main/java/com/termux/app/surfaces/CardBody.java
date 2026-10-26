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
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.surfaces.EditorRows.Kind;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A redesigned card's rows (editor-redesign §1 and §2), built from the row kinds under
 * {@code surfaces/rows}: section headers with their notes, glyph or word segments, strips with
 * their glyph on top (and the link between two of them), steppers, the Look's preview tiles, a
 * grid of option tiles, action rows, and the More chips. Rows of one kind that follow one another
 * share one widget: strips one strip, switches one tile grid, links one chips row.
 *
 * <p>Views only, as {@link EditorCardSheet}: every value comes from its {@link
 * EditorCardSheet.Model} and every touch goes to it. The row kinds report touches only, so a
 * restatement never echoes back as a write.</p>
 *
 * <p>Which cards are drawn this way is {@link #redesigned}; the rest keep the sheet's own rows
 * until their wave reshapes them.</p>
 */
final class CardBody {

    /** Whether {@code card} is drawn with the redesigned rows. */
    static boolean redesigned(@NonNull EditorCard card) {
        return card == EditorCard.KEYBOARD || card == EditorCard.HOME
            || card == EditorCard.APPEARANCE || card == EditorCard.WORKSPACE;
    }

    /** What a Look tile's glass shows: its opacity over the backdrop, Clear to Solid. */
    private static final float[] LOOK_FILL = {0f, 0.3f, 0.5f, 0.92f};
    private static final float CUSTOM_FILL = 0.55f;

    @NonNull private final Context mContext;
    @NonNull private final EditorCardSheet.Model mModel;
    @NonNull private final Runnable mExpand;
    private final int mWidthPx;
    /** How to read each row again, by row; a grouped row restates its whole group. */
    @NonNull private final Map<Row, Runnable> mRestaters = new EnumMap<>(Row.class);
    @NonNull private final List<Runnable> mOrder = new ArrayList<>();

    private CardBody(@NonNull Context context, @NonNull EditorCardSheet.Model model, int widthPx,
                     @NonNull Runnable expand) {
        mContext = context;
        mModel = model;
        mWidthPx = widthPx;
        mExpand = expand;
    }

    /**
     * Builds {@code specs} into {@code into}, one view per row or group, the views of tier 1
     * tagged with {@code tierOneTag}. {@code expand} raises the sheet to its full detent (an
     * expander chip opened its panel).
     */
    @NonNull
    static CardBody build(@NonNull Context context, @NonNull EditorCardSheet.Model model,
                          @NonNull List<Spec> specs, int widthPx, @NonNull LinearLayout into,
                          @NonNull Object tierOneTag, @NonNull Runnable expand) {
        CardBody body = new CardBody(context, model, widthPx, expand);
        int i = 0;
        while (i < specs.size()) {
            Spec first = specs.get(i);
            int end = body.groupEnd(specs, i);
            List<Row> group = new ArrayList<>(end - i);
            for (int j = i; j < end; j++)
                group.add(specs.get(j).row);
            View view = body.buildGroup(group, into.getChildCount() > 0);
            if (first.tier == 1)
                view.setTag(tierOneTag);
            into.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            i = end;
        }
        body.restate();
        return body;
    }

    /** Every row read again. */
    void restate() {
        for (Runnable restater : mOrder)
            restater.run();
    }

    /** One row read again (with the group it stands in); nothing for a row the card lacks. */
    void restate(@NonNull Row row) {
        Runnable restater = mRestaters.get(row);
        if (restater != null)
            restater.run();
    }

    /** Whether the card holds {@code row}. */
    boolean holds(@NonNull Row row) {
        return mRestaters.containsKey(row);
    }

    // ------------------------------------------------------------------------------- grouping

    /** Where the group that starts at {@code start} ends (exclusive). */
    private int groupEnd(@NonNull List<Spec> specs, int start) {
        Row first = specs.get(start).row;
        int end = start + 1;
        while (end < specs.size() && sameGroup(first, specs.get(end).row))
            end++;
        return end;
    }

    private boolean sameGroup(@NonNull Row first, @NonNull Row next) {
        switch (first.kind) {
            case STRIP:
            case STRIP_LINK:
                return next.kind == Kind.STRIP || next.kind == Kind.STRIP_LINK;
            case SWITCH:
                return next.kind == Kind.SWITCH;
            case LINK:
            case EXPANDER:
                return next.kind == Kind.LINK || next.kind == Kind.EXPANDER;
            default:
                return false;
        }
    }

    @NonNull
    private View buildGroup(@NonNull List<Row> rows, boolean after) {
        Row first = rows.get(0);
        switch (first.kind) {
            case STRIP:
            case STRIP_LINK:
                return strips(rows);
            case SWITCH:
                return tiles(rows);
            case LINK:
            case EXPANDER:
                return chips(rows);
            case SEGMENTS:
                return segments(first);
            case MENU:
                return mModel.swatches(first) != null ? segments(first) : menu(first);
            case STEPPER:
                return stepper(first);
            case SECTION:
                return section(first, after);
            case HINT:
                return hint(first);
            case LOOK:
                return looks(first);
            case WALLPAPER:
            case ACTION:
            case CHIPS:
            default:
                return action(first);
        }
    }

    /** {@code row}'s name on this card. */
    @NonNull
    private CharSequence name(@NonNull Row row) {
        CharSequence name = mModel.name(row);
        return name != null ? name : mContext.getString(row.labelRes);
    }

    /** {@code row}'s glyph on this card. */
    @DrawableRes
    private int glyph(@NonNull Row row) {
        int glyph = mModel.glyph(row);
        return glyph != 0 ? glyph : row.iconRes;
    }

    /** Registers how {@code rows} read again: each names the same restater. */
    private void restater(@NonNull List<Row> rows, @NonNull Runnable restater) {
        for (Row row : rows)
            mRestaters.put(row, restater);
        mOrder.add(restater);
    }

    private void restater(@NonNull Row row, @NonNull Runnable restater) {
        mRestaters.put(row, restater);
        mOrder.add(restater);
    }

    private void showRoot(@NonNull View root, @NonNull Row row) {
        int visibility = mModel.shown(row) ? View.VISIBLE : View.GONE;
        if (root.getVisibility() != visibility)
            root.setVisibility(visibility);
    }

    // ---------------------------------------------------------------------------------- kinds

    /** A strip run; a STRIP_LINK in it links the strips either side. */
    @NonNull
    private View strips(@NonNull List<Row> rows) {
        List<Row> columns = new ArrayList<>();
        List<StripRow.Strip> strips = new ArrayList<>();
        Row linkRow = null;
        int linkAfter = -1;
        for (Row row : rows) {
            if (row.kind == Kind.STRIP_LINK) {
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
        StripRow strip = StripRow.build(mContext, strips, values, mWidthPx,
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
        restater(rows, () -> {
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
        return strip.root;
    }

    /** A run of switches as one grid of tiles; a tile's long-press is its row's summary. */
    @NonNull
    private View tiles(@NonNull List<Row> rows) {
        List<TileGrid.Tile> tiles = new ArrayList<>(rows.size());
        boolean[] on = new boolean[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            tiles.add(new TileGrid.Tile(glyph(row), name(row), mModel.summary(row)));
            on[i] = mModel.value(row) != 0;
        }
        TileGrid grid = TileGrid.build(mContext, tiles, on, mWidthPx,
            (index, checked) -> mModel.onValue(rows.get(index), checked ? 1 : 0, false));
        restater(rows, () -> {
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
        return grid.root;
    }

    /**
     * A run of links as one chips row. An expander's chip opens its panel under the row (the
     * icon packs), and the sheet rises to its full detent to show it.
     */
    @NonNull
    private View chips(@NonNull List<Row> rows) {
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
        restater(rows, () -> {
            for (int i = 0; i < rows.size(); i++) {
                int visibility = mModel.shown(rows.get(i)) ? View.VISIBLE : View.GONE;
                View chip = chips.chips.get(i);
                if (chip.getVisibility() != visibility)
                    chip.setVisibility(visibility);
            }
        });
        return root;
    }

    /**
     * One choice of a few: glyphs alone where the model names glyphs ({@code chipIcons}),
     * colour swatches where it names colours, words otherwise, each in its own typeface where
     * the model gives one.
     */
    @NonNull
    private View segments(@NonNull Row row) {
        String[] choices = mModel.choices(row);
        int[] glyphs = mModel.chipIcons(row);
        int[] swatches = mModel.swatches(row);
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
            mModel.value(row), mWidthPx, index -> mModel.onValue(row, index, false));
        for (int i = 0; i < segmentRow.buttons.size(); i++) {
            MaterialButton button = segmentRow.buttons.get(i);
            if (swatches != null && i < swatches.length) {
                button.setIcon(swatch(swatches[i]));
                button.setIconTint(null);
            }
            Typeface typeface = mModel.choiceTypeface(row, i);
            if (typeface != null)
                button.setTypeface(typeface);
        }
        restater(row, () -> {
            showRoot(segmentRow.root, row);
            segmentRow.select(mModel.value(row));
        });
        return segmentRow.root;
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
    private View menu(@NonNull Row row) {
        ActionRow action = ActionRow.build(mContext, glyph(row), name(row), mModel.summary(row),
            v -> mModel.onAction(row, v));
        restater(row, () -> {
            showRoot(action.root, row);
            action.setAction(mModel.summary(row));
        });
        return action.root;
    }

    @NonNull
    private View stepper(@NonNull Row row) {
        StepperRow stepper = StepperRow.build(mContext, glyph(row), name(row), mModel.from(row),
            mModel.to(row), mModel.step(row), mModel.value(row),
            value -> mModel.readout(row, value), value -> mModel.onValue(row, value, false));
        restater(row, () -> {
            showRoot(stepper.root, row);
            stepper.setValue(mModel.value(row));
        });
        return stepper.root;
    }

    /** A section's header; its note is the model's summary for it. */
    @NonNull
    private View section(@NonNull Row row, boolean divider) {
        SectionHeader header = SectionHeader.build(mContext, name(row), mModel.summary(row),
            divider);
        restater(row, () -> showRoot(header.root, row));
        return header.root;
    }

    @NonNull
    private View hint(@NonNull Row row) {
        TextView hint = RowKit.caption(mContext, name(row));
        hint.setMaxLines(3);
        hint.setEllipsize(null);
        int edge = RowKit.dp(mContext, RowKit.EDGE_DP);
        hint.setPadding(edge, 0, edge, RowKit.dp(mContext, 8));
        restater(row, () -> showRoot(hint, row));
        return hint;
    }

    /**
     * An action row: the word at its end is the model's readout (the Match rows' Copy or Undo),
     * or Change for the wallpaper.
     */
    @NonNull
    private View action(@NonNull Row row) {
        boolean wallpaper = row.kind == Kind.WALLPAPER;
        ActionRow action = ActionRow.build(mContext, glyph(row), name(row),
            wallpaper ? mContext.getString(R.string.appearance_wallpaper_change)
                : mModel.readout(row, mModel.value(row)),
            v -> mModel.onAction(row, v));
        restater(row, () -> {
            showRoot(action.root, row);
            if (!wallpaper)
                action.setAction(mModel.readout(row, mModel.value(row)));
        });
        return action.root;
    }

    /** The Look's stops as preview tiles: each a swatch of glass at its stop's opacity. */
    @NonNull
    private View looks(@NonNull Row row) {
        String[] names = mModel.choices(row);
        List<PreviewTilesRow.Tile> tiles = new ArrayList<>(names.length);
        for (int i = 0; i < names.length; i++)
            tiles.add(new PreviewTilesRow.Tile(names[i], lookPreview(i)));
        PreviewTilesRow looks = PreviewTilesRow.build(mContext, tiles, mModel.value(row), index -> {
            mModel.onValue(row, index, false);
            mModel.onReleased(row);
        });
        restater(row, () -> {
            showRoot(looks.root, row);
            looks.select(mModel.value(row));
        });
        return looks.root;
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
