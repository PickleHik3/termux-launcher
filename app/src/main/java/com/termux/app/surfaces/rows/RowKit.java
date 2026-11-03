package com.termux.app.surfaces.rows;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

/**
 * What every row kind of the editor's cards shares (editor-redesign §1 and §8): the Settings row's
 * measures (a 16dp gutter, a 24dp outline glyph in a 56dp lead column, a 56dp least height), its
 * 16 sp title, and the glyph whose name shows on long-press. Cards keep no summaries.
 */
public final class RowKit {

    private RowKit() {}

    /** The side gutter, as Settings' {@code settings_gutter}. */
    public static final int EDGE_DP = 16;
    /** The lead column: the glyph and the gap after it; the title starts here. */
    public static final int ICON_COLUMN_DP = 56;
    /** A row's least height. */
    public static final int ROW_MIN_DP = 56;
    /** A glyph's size, as Settings' {@code settings_row_icon}. */
    public static final int GLYPH_DP = 24;
    /** A touch target's least size. */
    public static final int TOUCH_DP = 48;

    public static int dp(@NonNull Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** A colour role of {@code context}'s theme. */
    public static int color(@NonNull Context context, @AttrRes int attr) {
        return MaterialColors.getColor(context, attr, 0);
    }

    /** Gives a text view the theme's text appearance behind {@code attr}. */
    public static void textAppearance(@NonNull TextView view, @AttrRes int attr) {
        TypedValue value = new TypedValue();
        if (view.getContext().getTheme().resolveAttribute(attr, value, true)
            && value.resourceId != 0)
            view.setTextAppearance(value.resourceId);
    }

    /** Sets {@code text} only when it differs, so a restatement never asks for a layout. */
    public static void setText(@NonNull TextView view, @Nullable CharSequence text) {
        CharSequence next = text == null ? "" : text;
        if (!TextUtils.equals(view.getText(), next))
            view.setText(next);
    }

    /** A row's title: the Settings row's 16 sp TitleMedium, one line, ellipsized. */
    @NonNull
    public static TextView title(@NonNull Context context, @NonNull CharSequence text) {
        TextView title = new TextView(context);
        textAppearance(title, com.google.android.material.R.attr.textAppearanceTitleMedium);
        title.setTextColor(color(context, com.google.android.material.R.attr.colorOnSurface));
        title.setText(text);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        return title;
    }

    /** A small caption: a value under a strip, a section's note, a preview tile's word. */
    @NonNull
    public static TextView caption(@NonNull Context context, @Nullable CharSequence text) {
        TextView caption = new TextView(context);
        textAppearance(caption, com.google.android.material.R.attr.textAppearanceLabelMedium);
        caption.setTextColor(color(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant));
        caption.setText(text);
        caption.setMaxLines(1);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        return caption;
    }

    /**
     * A 24dp outline glyph in the row's muted colour, its name on long-press
     * ({@link GlyphNames}). Beside a title it is decoration to a screen reader.
     */
    @NonNull
    public static ImageView glyph(@NonNull Context context, @DrawableRes int glyph,
                                  @NonNull CharSequence name) {
        ImageView view = new ImageView(context);
        view.setImageResource(glyph);
        view.setImageTintList(ColorStateList.valueOf(color(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant)));
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GlyphNames.name(view, name);
        return view;
    }

    /** The lead glyph's place: 24dp, with the gap that starts the title at the lead column. */
    @NonNull
    public static LinearLayout.LayoutParams glyphParams(@NonNull Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(context, GLYPH_DP),
            dp(context, GLYPH_DP));
        params.setMarginEnd(dp(context, ICON_COLUMN_DP - EDGE_DP - GLYPH_DP));
        return params;
    }

    /** A horizontal row line: centred, at least 56dp, the 16dp gutter on both sides. */
    @NonNull
    public static LinearLayout line(@NonNull Context context) {
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(dp(context, ROW_MIN_DP));
        int edge = dp(context, EDGE_DP);
        line.setPadding(edge, 0, edge, 0);
        return line;
    }

    /** The width a row of the card stands in when the caller has none yet: the display's. */
    public static int defaultRowWidthPx(@NonNull Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }
}
