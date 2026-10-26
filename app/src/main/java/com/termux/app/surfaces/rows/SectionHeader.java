package com.termux.app.surfaces.rows;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

/**
 * A card section's header (editor-redesign §2): its name ("Glass", "Options"), and at its end an
 * optional caption that says where the section reaches ("Workspace only", "widgets, status bar,
 * dock", "drag to reorder"). A divider over it parts it from the section before.
 */
public final class SectionHeader {

    @NonNull public final LinearLayout root;
    @NonNull public final TextView title;
    /** The caption at the end; gone when there is none. */
    @NonNull public final TextView caption;

    private SectionHeader(@NonNull LinearLayout root, @NonNull TextView title,
                          @NonNull TextView caption) {
        this.root = root;
        this.title = title;
        this.caption = caption;
    }

    @NonNull
    public static SectionHeader build(@NonNull Context context, @NonNull CharSequence title,
                                      @Nullable CharSequence caption, boolean divider) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int edge = RowKit.dp(context, RowKit.EDGE_DP);
        if (divider) {
            View line = new View(context);
            line.setBackgroundColor(RowKit.color(context,
                com.google.android.material.R.attr.colorOutlineVariant));
            line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, RowKit.dp(context, 1)));
            params.setMarginStart(edge);
            params.setMarginEnd(edge);
            params.bottomMargin = RowKit.dp(context, 8);
            root.addView(line, params);
        }
        LinearLayout band = new LinearLayout(context);
        band.setOrientation(LinearLayout.HORIZONTAL);
        band.setGravity(Gravity.CENTER_VERTICAL);
        band.setPadding(edge, RowKit.dp(context, 4), edge, RowKit.dp(context, 4));
        TextView titleView = new TextView(context);
        RowKit.textAppearance(titleView,
            com.google.android.material.R.attr.textAppearanceTitleSmall);
        titleView.setTextColor(RowKit.color(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant));
        titleView.setMaxLines(1);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setText(title);
        ViewCompat.setAccessibilityHeading(titleView, true);
        band.addView(titleView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView captionView = RowKit.caption(context, caption);
        captionView.setGravity(Gravity.END);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        captionParams.setMarginStart(RowKit.dp(context, 16));
        band.addView(captionView, captionParams);
        captionView.setVisibility(caption == null || caption.length() == 0 ? View.GONE
            : View.VISIBLE);
        root.addView(band, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return new SectionHeader(root, titleView, captionView);
    }
}
