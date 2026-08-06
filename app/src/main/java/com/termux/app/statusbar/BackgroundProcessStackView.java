package com.termux.app.statusbar;

import android.animation.LayoutTransition;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Non-interactive top-trailing stack of persistent background command titles. */
public final class BackgroundProcessStackView extends LinearLayout {

    /** Rows drawn before the last one collapses into an overflow count. */
    private static final int MAX_ROWS = 3;
    /** Gap between stacked rows, and between the stack and the notice chip above it. */
    private static final int ROW_GAP_DP = 4;
    /** Row height, matched to the notice chip's own so the column reads as one list. */
    private static final int ROW_HEIGHT_DP = 30;
    /** Matches the notice chip's own entrance and exit, since this slide is caused by them. */
    private static final long SLIDE_DOWN_MS = 180L;
    private static final long SLIDE_UP_MS = 160L;

    /** Material surface-container fill and outline alphas, matched to the notice chip above. */
    private static final int SURFACE_ALPHA = 224;
    private static final int OUTLINE_ALPHA = 82;

    private final int mOnSurface;
    private final int mSurface;
    private final int mOutline;
    private float mTopOffsetPx;

    /** One row per model entry, keyed by its stable shell/foreground pair and held in draw order. */
    private static final class Row {
        final FrameLayout frame;
        final TextView title;
        final TextView dots;
        @Nullable String text;
        boolean overflow;
        int hiddenCount = -1;

        Row(@NonNull FrameLayout frame, @NonNull TextView title, @NonNull TextView dots) {
            this.frame = frame;
            this.title = title;
            this.dots = dots;
        }
    }

    private final Map<Long, Row> mRows = new LinkedHashMap<>();

    public BackgroundProcessStackView(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.END);
        setClickable(false);
        setFocusable(false);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        mOnSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        // One surface family with the notice chip above, a step lower in the elevation scale so the
        // standing rows read as the quieter half of the same column.
        mSurface = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainer,
            MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanel,
                ContextCompat.getColor(context, R.color.termux_surface_panel)));
        mOutline = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOutlineVariant,
            ContextCompat.getColor(context, R.color.termux_outline_variant));
        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(LayoutTransition.DISAPPEARING, 160L);
        transition.setDuration(LayoutTransition.CHANGE_DISAPPEARING, 180L);
        transition.setDuration(LayoutTransition.APPEARING, 140L);
        setLayoutTransition(transition);
    }

    /**
     * Reconcile the stack against {@code entries} in place.
     *
     * <p>Rows are reused by entry key rather than rebuilt. This view is re-bound on every title
     * change of every background shell — a command that writes its progress to the window title
     * re-binds many times a second — and a rebuild would hand each of those binds to the
     * {@link LayoutTransition}: the outgoing rows animate out from their old slots while identical
     * new rows fade in below them, which is seen as a row blinking under an empty one. Reconciling
     * leaves untouched rows alone, so only real arrivals and departures animate.
     */
    public void bind(@NonNull List<BackgroundProcessModel.Entry> entries) {
        int shown = Math.min(MAX_ROWS, entries.size());
        int hiddenCount = Math.max(0, entries.size() - MAX_ROWS);

        Set<Long> wanted = new HashSet<>();
        for (int i = 0; i < shown; i++) wanted.add(entries.get(i).key);

        for (Iterator<Map.Entry<Long, Row>> it = mRows.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Row> existing = it.next();
            if (wanted.contains(existing.getKey())) continue;
            removeView(existing.getValue().frame);
            it.remove();
        }

        // Entries are sorted by first sighting, so a survivor never moves behind another survivor and
        // an arrival always belongs last: appending keeps the order right without reindexing rows.
        for (int i = 0; i < shown; i++) {
            BackgroundProcessModel.Entry entry = entries.get(i);
            Row row = mRows.get(entry.key);
            if (row == null) {
                row = buildRow();
                mRows.put(entry.key, row);
                addView(row.frame, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(ROW_HEIGHT_DP)));
            }
            bindRow(row, entry.displayText(), i == MAX_ROWS - 1 && hiddenCount > 0, hiddenCount);
        }

        applyRowSpacing();
        setVisibility(mRows.isEmpty() ? GONE : VISIBLE);
    }

    /**
     * Push the stack down by {@code chipHeightPx} of transient notice above it, or back to the top
     * when that is 0. The stack is anchored directly under the status bar rather than below a
     * reserved band, so an expired notice leaves no empty row: the rows slide up into its slot.
     */
    public void setNoticeOccupancyPx(int chipHeightPx) {
        float target = chipHeightPx <= 0 ? 0f : chipHeightPx + dp(ROW_GAP_DP);
        if (target == mTopOffsetPx) return;
        boolean down = target > mTopOffsetPx;
        mTopOffsetPx = target;
        animate().cancel();
        animate()
            .translationY(target)
            .setDuration(down ? SLIDE_DOWN_MS : SLIDE_UP_MS)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    /** Only the first row sits flush; the rest keep a gap. Recomputed because row 0 can be removed. */
    private void applyRowSpacing() {
        int index = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LayoutParams params = (LayoutParams) child.getLayoutParams();
            int margin = index == 0 ? 0 : dp(ROW_GAP_DP);
            if (params.topMargin != margin) {
                params.topMargin = margin;
                child.setLayoutParams(params);
            }
            index++;
        }
    }

    private void bindRow(@NonNull Row row, @NonNull String text, boolean overflow, int hiddenCount) {
        if (!text.equals(row.text)) {
            row.text = text;
            row.title.setText(text);
        }
        if (row.overflow != overflow) {
            row.overflow = overflow;
            row.title.setAlpha(overflow ? 0.28f : 1f);
            row.dots.setVisibility(overflow ? VISIBLE : GONE);
        }
        if (overflow && row.hiddenCount != hiddenCount) {
            row.hiddenCount = hiddenCount;
            row.dots.setContentDescription(getResources().getQuantityString(
                R.plurals.background_process_hidden_count, hiddenCount, hiddenCount));
        }
    }

    @NonNull
    private Row buildRow() {
        FrameLayout frame = new FrameLayout(getContext());
        frame.setClickable(false);
        TextView title = new TextView(getContext());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setMaxWidth(dp(220));
        title.setTextColor(mOnSurface);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        title.setIncludeFontPadding(false);
        title.setPadding(dp(14), 0, dp(14), 0);
        title.setBackground(chipBackground());
        frame.addView(title, new FrameLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.END));
        TextView dots = new TextView(getContext());
        dots.setText("•••");
        dots.setTextColor(ColorUtils.setAlphaComponent(mOnSurface, 220));
        dots.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        dots.setGravity(Gravity.CENTER);
        dots.setVisibility(GONE);
        frame.addView(dots, new FrameLayout.LayoutParams(dp(42),
            LayoutParams.MATCH_PARENT, Gravity.END));
        return new Row(frame, title, dots);
    }

    private GradientDrawable chipBackground() {
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(ColorUtils.setAlphaComponent(mSurface, SURFACE_ALPHA));
        chip.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(mOutline, OUTLINE_ALPHA));
        chip.setCornerRadius(dp(15));
        return chip;
    }

    @NonNull
    public static FrameLayout.LayoutParams buildHostLayoutParams(@NonNull Context context) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        float density = context.getResources().getDisplayMetrics().density;
        // Same anchor as the notice chip. Vertical room for a notice is not reserved here; it is
        // applied as a translation by setNoticeOccupancyPx only while a notice is actually up.
        params.topMargin = Math.round(8f * density);
        params.setMarginEnd(Math.round(10f * density));
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
