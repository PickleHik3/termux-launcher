package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.graphics.ColorUtils;

/**
 * One quiet line beside the clock that says what the place on screen holds — the widgets on the
 * Widgets page, nothing on the others yet. It wears the place's accent and slides with the wall
 * like the rest of the place's content.
 */
public final class TopPanePlaceSummaryView extends AppCompatTextView {

    public TopPanePlaceSummaryView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setAllCaps(true);
        setLetterSpacing(0.22f);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
        setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        setIncludeFontPadding(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int lens = Math.round(PlaceContentStrip.LENS_WIDTH_DP
            * getResources().getDisplayMetrics().density);
        // The line ends before the lens, where the glyph of the next place rests; it only ever
        // enters the lens on its way out with the wall.
        int clearance = lens + Math.round(4f * getResources().getDisplayMetrics().density);
        setPaddingRelative(clearance, 0, clearance, 0);
        setHorizontalFadingEdgeEnabled(true);
        setFadingEdgeLength(lens);
    }

    /** The line to show, or empty for none; {@code accent} is the place's colour. */
    public void setSummary(@Nullable CharSequence text, int accent) {
        setText(text == null ? "" : text);
        setTextColor(ColorUtils.setAlphaComponent(accent, 217));
    }

    public boolean hasSummary() {
        return !TextUtils.isEmpty(getText());
    }

    @Override protected float getLeftFadingEdgeStrength() { return 1f; }

    @Override protected float getRightFadingEdgeStrength() { return 1f; }
}
