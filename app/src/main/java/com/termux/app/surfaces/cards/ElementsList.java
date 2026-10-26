package com.termux.app.surfaces.cards;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.surfaces.cards.ElementRows.Item;
import com.termux.app.surfaces.rows.EditorGlyphs;
import com.termux.app.surfaces.rows.GlyphNames;
import com.termux.app.surfaces.rows.RowKit;
import com.termux.app.surfaces.rows.SectionHeader;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Elements list at the end of a screen's card: one rounded list in the Bars list's style
 * ({@link BarsList}) with no handles, each row the element's glyph and name, a sliders glyph that
 * opens the element's card, and an eye that shows or hides it. A hidden element greys out; the
 * terminal and the widgets have no eye. Which rows stand and what each does is {@link
 * ElementRows}'; views only, every value read from {@link Host} and every touch reported to it.
 */
public final class ElementsList {

    /** Where the list reads its elements and writes what is done to them. */
    public interface Host {
        /** Whether the page being edited wears {@code item}; its row stands only if it does. */
        boolean offers(@NonNull Item item);
        boolean shown(@NonNull Item item);
        void setShown(@NonNull Item item, boolean shown);
        /** The sliders glyph, or a tap on the row: the element's card. */
        void open(@NonNull Item item);
    }

    @NonNull public final LinearLayout root;
    @NonNull private final Context mContext;
    @NonNull private final Host mHost;
    @NonNull private final Map<Item, Views> mRows = new EnumMap<>(Item.class);

    /** One element's row. */
    private static final class Views {
        @NonNull final LinearLayout row;
        @NonNull final ImageView glyph;
        @NonNull final TextView title;
        @NonNull final MaterialButton sliders;
        @NonNull final MaterialButton eye;
        int eyeIcon;

        Views(@NonNull LinearLayout row, @NonNull ImageView glyph, @NonNull TextView title,
              @NonNull MaterialButton sliders, @NonNull MaterialButton eye) {
            this.row = row;
            this.glyph = glyph;
            this.title = title;
            this.sliders = sliders;
            this.eye = eye;
        }
    }

    private ElementsList(@NonNull Context context, @NonNull Host host, @NonNull List<Item> items,
                         boolean divider) {
        mContext = context;
        mHost = host;
        int edge = RowKit.dp(context, RowKit.EDGE_DP);
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, RowKit.dp(context, 8));
        root.addView(SectionHeader.build(context,
            context.getString(R.string.editor_section_elements), null, divider).root);
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
        for (Map.Entry<Item, Views> entry : mRows.entrySet())
            restate(entry.getKey(), entry.getValue());
    }

    private void restate(@NonNull Item item, @NonNull Views views) {
        setVisible(views.row, mHost.offers(item));
        if (!item.hasEye())
            return;
        boolean shown = mHost.shown(item);
        float alpha = shown ? 1f : BarsList.HIDDEN_ALPHA;
        if (views.glyph.getAlpha() != alpha) {
            views.glyph.setAlpha(alpha);
            views.title.setAlpha(alpha);
        }
        int icon = shown ? R.drawable.ic_ms_visibility : R.drawable.ic_ms_visibility_off;
        if (views.eyeIcon != icon) {
            views.eyeIcon = icon;
            views.eye.setIconResource(icon);
            CharSequence action = mContext.getString(shown ? R.string.editor_elements_hide
                : R.string.editor_elements_show, mContext.getString(item.nameRes));
            views.eye.setContentDescription(action);
            GlyphNames.name(views.eye, action);
        }
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
        row.setPaddingRelative(edge, 0, 0, 0);
        int touch = RowKit.dp(context, RowKit.TOUCH_DP);

        ImageView glyph = RowKit.glyph(context, item.glyphRes, name);
        LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(
            RowKit.dp(context, RowKit.GLYPH_DP), RowKit.dp(context, RowKit.GLYPH_DP));
        glyphParams.setMarginEnd(RowKit.dp(context, 12));
        row.addView(glyph, glyphParams);

        TextView title = RowKit.title(context, name);
        row.addView(title, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialButton sliders = iconButton(context, EditorGlyphs.BAR_SETTINGS,
            context.getString(R.string.editor_elements_open, name));
        row.addView(sliders, new LinearLayout.LayoutParams(touch, touch));

        MaterialButton eye = iconButton(context, R.drawable.ic_ms_visibility,
            context.getString(R.string.editor_elements_hide, name));
        row.addView(eye, new LinearLayout.LayoutParams(touch, touch));
        Views views = new Views(row, glyph, title, sliders, eye);
        views.eyeIcon = R.drawable.ic_ms_visibility;
        if (!item.hasEye()) {
            // The terminal and the widgets cannot be hidden: the eye keeps its room, unseen, so
            // every sliders glyph stands in one column.
            eye.setVisibility(View.INVISIBLE);
            eye.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        View.OnClickListener open = v -> mHost.open(item);
        sliders.setOnClickListener(open);
        row.setOnClickListener(open);
        eye.setOnClickListener(v -> {
            mHost.setShown(item, !mHost.shown(item));
            restate(item, views);
        });
        return views;
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

    /** The eye of {@code item}'s row, for a test. */
    @androidx.annotation.VisibleForTesting
    @NonNull
    MaterialButton eyeOf(@NonNull Item item) {
        Views views = mRows.get(item);
        if (views == null)
            throw new IllegalArgumentException(item + " is not listed");
        return views.eye;
    }
}
