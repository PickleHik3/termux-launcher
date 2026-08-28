package com.termux.app.launcher.popup;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * The width negotiation between a text menu's rows and its header.
 *
 * <p>Rows all take the width of the widest one, so the highlight fill is a uniform slab rather than
 * a ragged edge. The header then gets a say: a long app name may widen the panel, but only up to a
 * medium-name budget, past which it wraps to a second line and ellipsises. Whatever width the
 * header settles on is pushed back onto the rows.
 */
public final class MenuRowWidths {

    /** The longest name the header is allowed to widen the panel for, as a glyph run. */
    private static final String MEDIUM_NAME_BUDGET = "MMMMMMMMMMMM";

    private MenuRowWidths() {
    }

    /** Measures every row, widens them all to the widest, and returns that width (0 if none). */
    public static int normalize(@NonNull List<MenuRow> rows) {
        if (rows.isEmpty()) return 0;
        int maxWidth = 0;
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        for (MenuRow row : rows) {
            if (row.rowView == null) continue;
            row.rowView.measure(unspecified, unspecified);
            maxWidth = Math.max(maxWidth, row.rowView.getMeasuredWidth());
        }
        if (maxWidth <= 0) return 0;
        applyWidth(rows, maxWidth);
        return maxWidth;
    }

    /** Forces every row to {@code targetWidth}; a non-positive target is a no-op. */
    public static void constrainRows(@NonNull List<MenuRow> rows, int targetWidth) {
        if (targetWidth <= 0) return;
        applyWidth(rows, targetWidth);
    }

    private static void applyWidth(@NonNull List<MenuRow> rows, int width) {
        for (MenuRow row : rows) {
            if (row.rowView == null) continue;
            ViewGroup.LayoutParams params = row.rowView.getLayoutParams();
            if (params == null) {
                params = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            } else {
                params.width = width;
            }
            row.rowView.setLayoutParams(params);
        }
    }

    /**
     * Lets the header widen the panel beyond {@code targetWidth}, but never past the medium-name
     * budget; returns the width the panel's content should end up at.
     */
    public static int constrainHeader(@NonNull TextView header, int targetWidth) {
        if (targetWidth <= 0) return 0;
        String title = header.getText() == null ? "" : header.getText().toString();
        int horizontalPadding = header.getPaddingLeft() + header.getPaddingRight();
        int titleTextWidth = (int) Math.ceil(header.getPaint().measureText(title));
        int mediumNameLimitWidth = (int) Math.ceil(header.getPaint().measureText(MEDIUM_NAME_BUDGET));
        int desiredSingleLineWidth = titleTextWidth + horizontalPadding;
        int boundedWidth = Math.max(targetWidth,
            Math.min(desiredSingleLineWidth, mediumNameLimitWidth + horizontalPadding));

        header.setSingleLine(false);
        header.setEllipsize(TextUtils.TruncateAt.END);
        header.setMaxLines(desiredSingleLineWidth <= boundedWidth ? 1 : 2);
        ViewGroup.LayoutParams params = header.getLayoutParams();
        if (params == null) {
            params = new LinearLayout.LayoutParams(boundedWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            params.width = boundedWidth;
        }
        header.setLayoutParams(params);
        return boundedWidth;
    }
}
