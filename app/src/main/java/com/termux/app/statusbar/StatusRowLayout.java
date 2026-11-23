package com.termux.app.statusbar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.TerminalWindowBar;

/**
 * The status bar's row: the place's badge and window chips, then the readings. Along a row it
 * runs the chips up to the readings, one {@link StatusRowFitPolicy#CHIPS_READINGS_GAP_DP} short
 * of the first reading's ink, however much of that gap the readings already carry themselves; and
 * when the chips run out of room it stands the readings tight — no dots between them, less room
 * around each — and gives the chips what that frees. A row with room looks exactly as it always
 * did. Standing in a column it is a plain linear layout.
 */
public final class StatusRowLayout extends LinearLayout {

    /** Whether the readings are standing tight right now. */
    private boolean mTight;

    public StatusRowLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /** For tests and callers that care: whether the readings are standing tight. */
    public boolean isReadingsTight() {
        return mTight;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        View strip = findViewById(R.id.terminal_status_place_content);
        TerminalWindowBar bar = findViewById(R.id.terminal_window_bar);
        boolean horizontal = getOrientation() == HORIZONTAL;
        if (!horizontal || strip == null || bar == null || bar.getVisibility() != VISIBLE) {
            // No chips in a row to make room for: the readings stand as they always have.
            if (setTight(false, strip)) super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int gap = Math.round(StatusRowFitPolicy.CHIPS_READINGS_GAP_DP
            * getResources().getDisplayMetrics().density);
        int gain = StatusRowFitPolicy.stripShareOf(readingsSavingPx(strip),
            weightOf(strip), totalWeight());
        boolean tight = StatusRowFitPolicy.readingsTight(bar.restingContentWidthPx(),
            bar.getMeasuredWidth(), mTight, gain, bar.getPaddingStart(),
            StatusRowFitPolicy.chipsEndPaddingPx(gap, readingsLeadPx(strip, bar, false)));
        boolean changed = setTight(tight, strip);
        // The end padding is inside the bar, so the row's own split does not move with it, only
        // where the chips stop. The lead is read in the form just chosen, which the measured
        // widths do not show yet.
        int end = StatusRowFitPolicy.chipsEndPaddingPx(gap, readingsLeadPx(strip, bar, mTight));
        changed |= bar.setTrailingGapPx(end);
        if (changed) super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** Stands every reading after the strip tight or not; whether anything changed. */
    private boolean setTight(boolean tight, @Nullable View strip) {
        if (mTight == tight) return false;
        mTight = tight;
        int index = strip == null ? -1 : indexOfChild(strip);
        for (int i = index + 1; i < getChildCount(); i++) applyTight(getChildAt(i), tight);
        return true;
    }

    private static void applyTight(@NonNull View view, boolean tight) {
        if (view instanceof StatusBarWidgetView) {
            ((StatusBarWidgetView) view).setTight(tight);
        } else if (view instanceof MaterialDotSeparatorView) {
            ((MaterialDotSeparatorView) view).setTucked(tight);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyTight(group.getChildAt(i), tight);
        }
    }

    /** What standing tight saves across every visible reading after the strip. */
    private int readingsSavingPx(@NonNull View strip) {
        int saving = 0;
        for (int i = indexOfChild(strip) + 1; i < getChildCount(); i++) {
            saving += tightSavingPx(getChildAt(i));
        }
        return saving;
    }

    private static int tightSavingPx(@NonNull View view) {
        if (view.getVisibility() == GONE) return 0;
        if (view instanceof StatusBarWidgetView) return ((StatusBarWidgetView) view).tightSavingPx();
        if (view instanceof MaterialDotSeparatorView) {
            return ((MaterialDotSeparatorView) view).widthPx(false);
        }
        if (!(view instanceof ViewGroup)) return 0;
        ViewGroup group = (ViewGroup) view;
        int saving = 0;
        for (int i = 0; i < group.getChildCount(); i++) saving += tightSavingPx(group.getChildAt(i));
        return saving;
    }

    private static float weightOf(@NonNull View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        return params instanceof LayoutParams ? ((LayoutParams) params).weight : 0f;
    }

    private float totalWeight() {
        float total = 0f;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) total += weightOf(child);
        }
        return total;
    }

    /**
     * The space from the end of the chips' viewport to the first reading's ink, with the readings
     * {@code tight} or not: whatever the strip keeps after the bar, then every inkless thing
     * before the first reading. Negative when the chips are followed by no reading at all — only
     * by the row's end, or by the centring spacer the Widgets place stands the stats on.
     */
    int readingsLeadPx(@NonNull View strip, @NonNull View bar, boolean tight) {
        int lead = strip.getPaddingEnd() + marginEnd(bar);
        int index = indexOfChild(strip);
        if (index < 0) return -1;
        for (int i = index + 1; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int ink = inkLead(child, tight);
            if (ink >= 0) return lead + ink;
            if (!(child instanceof ViewGroup)) return -1;
            lead += footprint(child, tight);
        }
        return -1;
    }

    /** How far into {@code view}, margin included, its first ink starts; negative if it has none. */
    private static int inkLead(@NonNull View view, boolean tight) {
        if (view.getVisibility() == GONE) return -1;
        if (view instanceof StatusBarWidgetView) {
            return marginStart(view) + ((StatusBarWidgetView) view).leadingPaddingPx(tight);
        }
        if (view instanceof MaterialDotSeparatorView) {
            return ((MaterialDotSeparatorView) view).widthPx(tight) > 0 ? marginStart(view) : -1;
        }
        if (!(view instanceof ViewGroup)) return -1;
        ViewGroup group = (ViewGroup) view;
        int lead = marginStart(view) + view.getPaddingStart();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int ink = inkLead(child, tight);
            if (ink >= 0) return lead + ink;
            lead += footprint(child, tight);
        }
        return -1;
    }

    /** The room an inkless {@code view} takes along the row, margins included. */
    private static int footprint(@NonNull View view, boolean tight) {
        int width = view instanceof MaterialDotSeparatorView
            ? ((MaterialDotSeparatorView) view).widthPx(tight) : view.getMeasuredWidth();
        return marginStart(view) + width + marginEnd(view);
    }

    private static int marginStart(@NonNull View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        return params instanceof MarginLayoutParams
            ? ((MarginLayoutParams) params).getMarginStart() : 0;
    }

    private static int marginEnd(@NonNull View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        return params instanceof MarginLayoutParams
            ? ((MarginLayoutParams) params).getMarginEnd() : 0;
    }
}
