package com.termux.app.surfaces.cards;

import android.content.Context;
import android.content.res.ColorStateList;
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
import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;
import com.termux.app.surfaces.cards.ElementRows.Item;
import com.termux.app.surfaces.rows.EditorGlyphs;
import com.termux.app.surfaces.rows.GlyphNames;
import com.termux.app.surfaces.rows.RowKit;
import com.termux.app.surfaces.rows.SectionHeader;
import com.termux.app.surfaces.rows.SegmentRow;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Layout list of a screen's card (overhaul §4): one rounded list in the Bars list's style
 * ({@link BarsList}) with no handles, each row the element's glyph, its name (over the card it
 * opens where that card is another's, "Dock card"), a switch that shows or hides it and a
 * trailing ›; a tap anywhere else on the row opens the element's card. A hidden element greys
 * out; the terminal and the widgets have no switch. Which rows stand and what each does is {@link
 * ElementRows}'; views only, every value read from {@link Host} and every touch reported to it.
 */
public final class ElementsList {

    /** Where the list reads its elements and writes what is done to them. */
    public interface Host {
        /**
         * The layout being edited, named for the heading ("Home layout", ADR 0020 §9); null keeps
         * the plain Elements heading.
         */
        @androidx.annotation.Nullable
        default String layoutTitle() { return null; }

        /** Whether the page being edited wears {@code item}; its row stands only if it does. */
        boolean offers(@NonNull Item item);
        boolean shown(@NonNull Item item);
        void setShown(@NonNull Item item, boolean shown);
        /** The sliders glyph, or a tap on the row: the element's card. */
        void open(@NonNull Item item);

        /**
         * The chip's two words, the kind side then the page ("Home pages", "Home 2"); null hides
         * the chip (minimal mode, or no page known).
         */
        @Nullable
        default CharSequence[] chipLabels() { return null; }

        /** Whether the chip stands on the page side: the page wears a layout of its own. */
        default boolean pageSide() { return false; }

        default void setPageSide(boolean pageSide) {}

        /**
         * A line under the element's name for its state, or null for none: Customization's
         * "Home only" where the two kinds disagree.
         */
        @Nullable
        default CharSequence summary(@NonNull Item item) { return null; }
    }

    @NonNull public final LinearLayout root;
    @NonNull private final Context mContext;
    @NonNull private final Host mHost;
    private final boolean mDivider;
    @NonNull private String mTitle;
    @NonNull private final Map<Item, Views> mRows = new EnumMap<>(Item.class);
    /** Where the chip stands, under the header; the chip itself, and the words it was built with. */
    @NonNull private final FrameLayout mChipSlot;
    @Nullable private SegmentRow mChip;
    @Nullable private CharSequence[] mChipLabels;

    /** One element's row. */
    private static final class Views {
        @NonNull final LinearLayout row;
        @NonNull final ImageView glyph;
        @NonNull final TextView title;
        @NonNull final MaterialSwitch toggle;
        /** The state line under the name, unseen while the host has none. */
        @NonNull final TextView summary;

        Views(@NonNull LinearLayout row, @NonNull ImageView glyph, @NonNull TextView title,
              @NonNull MaterialSwitch toggle, @NonNull TextView summary) {
            this.row = row;
            this.glyph = glyph;
            this.title = title;
            this.toggle = toggle;
            this.summary = summary;
        }
    }

    /** A restatement sets the switches; only a touch reports. */
    private boolean mRestating;

    private ElementsList(@NonNull Context context, @NonNull Host host, @NonNull List<Item> items,
                         boolean divider) {
        mContext = context;
        mHost = host;
        int edge = RowKit.dp(context, RowKit.EDGE_DP);
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, RowKit.dp(context, 8));
        mDivider = divider;
        mTitle = heading();
        root.addView(SectionHeader.build(context, mTitle, null, divider).root);
        mChipSlot = new FrameLayout(context);
        root.addView(mChipSlot, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(RowKit.color(context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh));
        background.setCornerRadius(RowKit.dp(context, BarsList.CORNER_DP));
        list.setBackground(background);
        list.setClipToOutline(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(edge);
        params.setMarginEnd(edge);
        params.topMargin = RowKit.dp(context, 4);
        root.addView(list, params);
        for (Item item : items) {
            Views views = row(item);
            mRows.put(item, views);
            list.addView(views.row);
        }
    }

    /** The list of {@code items}, read from {@code host}; {@code divider} stands over it. */
    @NonNull
    public static ElementsList build(@NonNull Context context, @NonNull Host host,
                                     @NonNull List<Item> items, boolean divider) {
        ElementsList list = new ElementsList(context, host, items, divider);
        list.restate();
        return list;
    }

    /** Every row read again: which stand, which are hidden. Compares before it sets. */
    public void restate() {
        restateHeading();
        restateChip();
        for (Map.Entry<Item, Views> entry : mRows.entrySet())
            restate(entry.getKey(), entry.getValue());
    }

    @NonNull
    private String heading() {
        String title = mHost.layoutTitle();
        return title != null ? title : mContext.getString(R.string.editor_section_elements);
    }

    /** The heading follows the layout being edited: another kind's page, minimal mode. */
    private void restateHeading() {
        String title = heading();
        if (title.equals(mTitle)) return;
        mTitle = title;
        root.removeViewAt(0);
        root.addView(SectionHeader.build(mContext, title, null, mDivider).root, 0);
    }

    /**
     * The chip read again: built anew only when its words change (another page), else only its
     * side set. A card segment row, as the Dock card's toggles are.
     */
    private void restateChip() {
        CharSequence[] labels = mHost.chipLabels();
        if (labels == null || labels.length != 2) {
            if (mChip != null) {
                mChipSlot.removeAllViews();
                mChip = null;
                mChipLabels = null;
            }
            setVisible(mChipSlot, false);
            return;
        }
        if (mChip == null || !java.util.Arrays.equals(labels, mChipLabels)) {
            mChipSlot.removeAllViews();
            mChip = SegmentRow.build(mContext, R.drawable.ic_ms_layers,
                mContext.getString(R.string.editor_elements_dress_for), java.util.Arrays.asList(
                    SegmentRow.Segment.word(labels[0]), SegmentRow.Segment.word(labels[1])),
                mHost.pageSide() ? 1 : 0, mContext.getResources().getDisplayMetrics().widthPixels,
                index -> {
                    mHost.setPageSide(index == 1);
                    restate();
                });
            mChipLabels = labels.clone();
            mChipSlot.addView(mChip.root);
        }
        mChip.select(mHost.pageSide() ? 1 : 0);
        setVisible(mChipSlot, true);
    }

    private void restate(@NonNull Item item, @NonNull Views views) {
        setVisible(views.row, mHost.offers(item));
        CharSequence summary = mHost.summary(item);
        if (summary != null && !summary.toString().contentEquals(views.summary.getText()))
            views.summary.setText(summary);
        setVisible(views.summary, summary != null);
        if (!item.hasEye())
            return;
        boolean shown = mHost.shown(item);
        float alpha = shown ? 1f : BarsList.HIDDEN_ALPHA;
        if (views.glyph.getAlpha() != alpha) {
            views.glyph.setAlpha(alpha);
            views.title.setAlpha(alpha);
        }
        if (views.toggle.isChecked() != shown) {
            mRestating = true;
            try {
                views.toggle.setChecked(shown);
            } finally {
                mRestating = false;
            }
        }
        CharSequence action = mContext.getString(shown ? R.string.editor_elements_hide
            : R.string.editor_elements_show, mContext.getString(item.nameRes));
        views.toggle.setContentDescription(action);
    }

    @NonNull
    private Views row(@NonNull Item item) {
        Context context = mContext;
        String name = context.getString(item.nameRes);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(RowKit.dp(context, BarsList.ROW_DP));
        int edge = RowKit.dp(context, RowKit.EDGE_DP);
        row.setPaddingRelative(edge, 0, RowKit.dp(context, 12), 0);

        ImageView glyph = RowKit.glyph(context, item.glyphRes, name);
        LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(
            RowKit.dp(context, RowKit.GLYPH_DP), RowKit.dp(context, RowKit.GLYPH_DP));
        glyphParams.setMarginEnd(RowKit.dp(context, 12));
        row.addView(glyph, glyphParams);

        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView title = RowKit.title(context, name);
        words.addView(title);
        CharSequence opens = opensLine(context, item);
        if (opens != null)
            words.addView(RowKit.caption(context, opens));
        TextView summary = RowKit.caption(context, "");
        summary.setVisibility(View.GONE);
        words.addView(summary);
        row.addView(words, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialSwitch toggle = new MaterialSwitch(context);
        toggle.setContentDescription(context.getString(R.string.editor_elements_hide, name));
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toggleParams.setMarginStart(RowKit.dp(context, 8));
        row.addView(toggle, toggleParams);
        if (!item.hasEye()) {
            // The terminal and the widgets cannot be hidden: the switch keeps its room, unseen,
            // so every › stands in one column.
            toggle.setVisibility(View.INVISIBLE);
            toggle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.ic_editor_chevron_right);
        chevron.setImageTintList(ColorStateList.valueOf(RowKit.color(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant)));
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
            RowKit.dp(context, RowKit.GLYPH_DP), RowKit.dp(context, RowKit.GLYPH_DP));
        chevronParams.setMarginStart(RowKit.dp(context, 8));
        row.addView(chevron, chevronParams);

        Views views = new Views(row, glyph, title, toggle, summary);
        row.setContentDescription(context.getString(R.string.editor_elements_open, name));
        row.setOnClickListener(v -> mHost.open(item));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (mRestating)
                return;
            mHost.setShown(item, checked);
            restate(item, views);
        });
        return views;
    }

    /**
     * The row's second line: the card it opens where that is another's ("Dock card" for the apps
     * row), or null where the element opens its own card.
     */
    @Nullable
    static CharSequence opensLine(@NonNull Context context, @NonNull Item item) {
        String card = context.getString(item.card.titleRes);
        if (card.equals(context.getString(item.nameRes)))
            return null;
        return context.getString(R.string.editor_elements_opens_card, card);
    }

    @NonNull
    private static MaterialButton iconButton(@NonNull Context context, @DrawableRes int icon,
                                             @NonNull CharSequence description) {
        MaterialButton button = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialIconButtonStyle);
        button.setIconResource(icon);
        button.setIconTint(ColorStateList.valueOf(RowKit.color(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant)));
        button.setContentDescription(description);
        GlyphNames.name(button, description);
        return button;
    }

    private static void setVisible(@NonNull View view, boolean visible) {
        int next = visible ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != next)
            view.setVisibility(next);
    }

    /** The row of {@code item}, for a test. */
    @androidx.annotation.VisibleForTesting
    @NonNull
    View rowOf(@NonNull Item item) {
        Views views = mRows.get(item);
        if (views == null)
            throw new IllegalArgumentException(item + " is not listed");
        return views.row;
    }

    /** The show/hide switch of {@code item}'s row, for a test. */
    @androidx.annotation.VisibleForTesting
    @NonNull
    MaterialSwitch switchOf(@NonNull Item item) {
        Views views = mRows.get(item);
        if (views == null)
            throw new IllegalArgumentException(item + " is not listed");
        return views.toggle;
    }
}
