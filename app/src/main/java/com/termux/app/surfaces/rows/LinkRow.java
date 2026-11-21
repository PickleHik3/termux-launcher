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

import com.termux.R;

/**
 * One link as a list row (appearance-cards overhaul §4): its glyph, its name over a one-line
 * subtitle, and a trailing › where it opens a card in the editor or ↗ (open_in_new) where it
 * opens a Settings page, whose path the subtitle names. The whole row is the touch target.
 */
public final class LinkRow {

    @NonNull public final LinearLayout root;
    @NonNull public final TextView title;
    @NonNull public final TextView subtitle;
    @NonNull public final ImageView trail;
    /** Whether the row opens a Settings page (↗) rather than a card (›). */
    public final boolean external;

    private LinkRow(@NonNull LinearLayout root, @NonNull TextView title,
                    @NonNull TextView subtitle, @NonNull ImageView trail, boolean external) {
        this.root = root;
        this.title = title;
        this.subtitle = subtitle;
        this.trail = trail;
        this.external = external;
    }

    @NonNull
    public static LinkRow build(@NonNull Context context, @DrawableRes int glyph,
                                @NonNull CharSequence name, @Nullable CharSequence subtitle,
                                boolean external, @NonNull View.OnClickListener onClick) {
        LinearLayout line = RowKit.line(context);
        line.setMinimumHeight(RowKit.dp(context, RowKit.ROW_MIN_DP));
        line.addView(RowKit.glyph(context, glyph, name), RowKit.glyphParams(context));
        LinearLayout words = new LinearLayout(context);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView title = RowKit.title(context, name);
        words.addView(title);
        TextView caption = RowKit.caption(context, subtitle);
        caption.setSingleLine(true);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.setVisibility(subtitle == null || subtitle.length() == 0 ? View.GONE : View.VISIBLE);
        words.addView(caption);
        line.addView(words, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView trail = new ImageView(context);
        trail.setImageResource(external ? R.drawable.ic_ms_open_in_new
            : R.drawable.ic_editor_chevron_right);
        trail.setImageTintList(ColorStateList.valueOf(RowKit.color(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant)));
        trail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int glyphPx = RowKit.dp(context, RowKit.GLYPH_DP);
        LinearLayout.LayoutParams trailParams = new LinearLayout.LayoutParams(glyphPx, glyphPx);
        trailParams.setMarginStart(RowKit.dp(context, 8));
        trailParams.gravity = Gravity.CENTER_VERTICAL;
        line.addView(trail, trailParams);
        line.setClickable(true);
        line.setFocusable(true);
        line.setOnClickListener(onClick);
        CharSequence opens = context.getString(external ? R.string.editor_link_opens_settings
            : R.string.editor_link_opens_card);
        line.setContentDescription(subtitle == null ? name + ". " + opens
            : name + ", " + subtitle + ". " + opens);
        return new LinkRow(line, title, caption, trail, external);
    }
}
