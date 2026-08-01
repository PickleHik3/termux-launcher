package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Mini-btop styled detail card for the CPU and RAM widgets: two-column core utilisation, visual
 * memory metrics, and a bounded sortable process list. Rebuilt in place from a
 * {@link SystemStatsController.Stats} snapshot on each throttled update.
 */
public final class SystemStatsCardView extends LinearLayout {

    private final int mOnSurface;
    private final int mOnSurfaceVariant;
    private final int mPanel;
    private final int mPrimary;
    private final int mSecondary;
    private final int mTertiary;
    private final int mError;

    private final TextView mCpuHeader;
    private final LinearLayout mCoreColumn;
    private final TextView mMemHeader;
    private final BarView mMemBar;
    private final LinearLayout mMemMetrics;
    private final TextView mTopHeader;
    private final LinearLayout mTopColumn;
    private final ScrollView mProcessScroll;

    private enum SortMetric { CPU, MEMORY }
    private SortMetric mSortMetric = SortMetric.CPU;
    private boolean mSortAscending;
    @NonNull private List<SystemStatsController.Proc> mProcesses = new ArrayList<>();

    public SystemStatsCardView(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        mOnSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mOnSurfaceVariant = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant,
            ContextCompat.getColor(context, R.color.termux_on_surface_variant));
        mPanel = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSurfacePanel,
            ContextCompat.getColor(context, R.color.termux_surface_panel));
        mPrimary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        mSecondary = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSecondary,
            ContextCompat.getColor(context, R.color.termux_secondary));
        mTertiary = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorTertiary, mPrimary);
        mError = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorError,
            ContextCompat.getColor(context, com.termux.shared.R.color.termux_error));

        mCpuHeader = sectionHeader("CPU", mPrimary);
        addView(mCpuHeader);
        mCoreColumn = new LinearLayout(context);
        mCoreColumn.setOrientation(HORIZONTAL);
        addView(mCoreColumn, lp(dp(4)));

        mMemHeader = sectionHeader("Memory", mSecondary);
        addView(mMemHeader, lp(dp(10)));
        mMemBar = new BarView(context);
        LayoutParams barParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(10));
        barParams.topMargin = dp(4);
        addView(mMemBar, barParams);
        mMemMetrics = new LinearLayout(context);
        mMemMetrics.setOrientation(VERTICAL);
        addView(mMemMetrics, lp(dp(5)));

        mTopHeader = sectionHeader("Processes", mTertiary);
        addView(mTopHeader, lp(dp(10)));
        mTopColumn = new LinearLayout(context);
        mTopColumn.setOrientation(VERTICAL);
        mProcessScroll = new ScrollView(context);
        mProcessScroll.setFillViewport(false);
        mProcessScroll.setVerticalScrollBarEnabled(true);
        mProcessScroll.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        mProcessScroll.addView(mTopColumn, new ScrollView.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LayoutParams processParams = lp(dp(4));
        processParams.height = dp(126);
        addView(mProcessScroll, processParams);
    }

    public void bind(@NonNull SystemStatsController.Stats stats) {
        String load = stats.load1 >= 0 ? String.format(Locale.ROOT, "  load %.2f", stats.load1) : "";
        String cpuPct = stats.cpuPercent >= 0 ? stats.cpuPercent + "%" : "--";
        mCpuHeader.setText(String.format(Locale.ROOT, "CPU  %s  ·  %d cores%s", cpuPct, stats.cores, load));

        mCoreColumn.removeAllViews();
        if (stats.corePercent.length > 0) {
            LinearLayout left = coreList();
            LinearLayout right = coreList();
            int split = (stats.corePercent.length + 1) / 2;
            for (int i = 0; i < stats.corePercent.length; i++) {
                (i < split ? left : right).addView(coreRow(i, stats.corePercent[i]));
            }
            LayoutParams columnParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            columnParams.rightMargin = dp(5);
            mCoreColumn.addView(left, columnParams);
            LayoutParams rightParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            rightParams.leftMargin = dp(5);
            mCoreColumn.addView(right, rightParams);
        } else if (stats.cpuPercent >= 0) {
            LinearLayout left = coreList();
            left.addView(coreRow(-1, stats.cpuPercent));
            mCoreColumn.addView(left, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }

        double totalGb = stats.memTotalKb / 1048576.0;
        double usedGb = stats.memUsedKb / 1048576.0;
        mMemHeader.setText(String.format(Locale.ROOT, "Memory  %.1f / %.1f GB", usedGb, totalGb));
        if (stats.memTotalKb > 0) {
            float used = clampFraction(stats.memUsedKb, stats.memTotalKb);
            long cacheKb = stats.cachedKb + stats.buffersKb;
            long swapUsedKb = Math.max(0, stats.swapTotalKb - stats.swapFreeKb);
            mMemBar.setSingle(used, mSecondary);
            mMemMetrics.removeAllViews();
            LinearLayout first = metricRow();
            first.addView(memoryMetric("USED", fmt(stats.memUsedKb), used, mSecondary),
                metricParams(true));
            first.addView(memoryMetric("AVAILABLE", fmt(stats.memAvailKb),
                clampFraction(stats.memAvailKb, stats.memTotalKb), mPrimary), metricParams(false));
            mMemMetrics.addView(first);
            LinearLayout second = metricRow();
            second.addView(memoryMetric("CACHE", fmt(cacheKb),
                clampFraction(cacheKb, stats.memTotalKb), mTertiary), metricParams(true));
            second.addView(memoryMetric("SWAP", stats.swapTotalKb > 0
                    ? fmt(swapUsedKb) + " / " + fmt(stats.swapTotalKb) : "off",
                clampFraction(swapUsedKb, stats.swapTotalKb), mError), metricParams(false));
            LayoutParams secondParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            secondParams.topMargin = dp(5);
            mMemMetrics.addView(second, secondParams);
            mMemBar.setVisibility(VISIBLE);
            mMemMetrics.setVisibility(VISIBLE);
        } else {
            mMemBar.setVisibility(GONE);
            mMemMetrics.removeAllViews();
            mMemMetrics.addView(mono(9, mOnSurfaceVariant));
            ((TextView) mMemMetrics.getChildAt(0)).setText("unavailable");
        }

        if (stats.top.isEmpty()) {
            mTopHeader.setVisibility(GONE);
            mProcessScroll.setVisibility(GONE);
        } else {
            mTopHeader.setVisibility(VISIBLE);
            mProcessScroll.setVisibility(VISIBLE);
            mProcesses = new ArrayList<>(stats.top);
            rebuildProcesses();
        }
    }

    private LinearLayout coreList() {
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(VERTICAL);
        return column;
    }

    private LinearLayout metricRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        return row;
    }

    private LayoutParams metricParams(boolean first) {
        LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        if (first) params.rightMargin = dp(5); else params.leftMargin = dp(5);
        return params;
    }

    private View coreRow(int index, int percent) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = mono(9, mOnSurfaceVariant);
        label.setText(index >= 0 ? String.format(Locale.ROOT, "c%-2d", index) : "cpu");
        label.setWidth(dp(28));
        row.addView(label);
        BarView bar = new BarView(getContext());
        bar.setSingle(percent / 100f, barColor(percent));
        LayoutParams bp = new LayoutParams(0, dp(8), 1f);
        bp.rightMargin = dp(6);
        row.addView(bar, bp);
        TextView pct = mono(9, mOnSurface);
        pct.setText(percent + "%");
        pct.setWidth(dp(34));
        pct.setGravity(Gravity.END);
        row.addView(pct);
        LinearLayout wrap = new LinearLayout(getContext());
        wrap.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        wrap.setPadding(0, dp(1), 0, dp(1));
        return wrap;
    }

    private View memoryMetric(@NonNull String label, @NonNull String value,
                              float fraction, int color) {
        LinearLayout cell = new LinearLayout(getContext());
        cell.setOrientation(VERTICAL);
        LinearLayout labels = new LinearLayout(getContext());
        labels.setOrientation(HORIZONTAL);
        TextView title = mono(7, ColorUtils.setAlphaComponent(color, 220));
        title.setText(label);
        labels.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView amount = mono(8, mOnSurface);
        amount.setText(value);
        amount.setGravity(Gravity.END);
        labels.addView(amount);
        cell.addView(labels, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        BarView bar = new BarView(getContext());
        bar.setSingle(fraction, color);
        LayoutParams barParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(5));
        barParams.topMargin = dp(2);
        cell.addView(bar, barParams);
        return cell;
    }

    private void rebuildProcesses() {
        int scrollY = mProcessScroll.getScrollY();
        List<SystemStatsController.Proc> sorted = new ArrayList<>(mProcesses);
        Comparator<SystemStatsController.Proc> comparator = mSortMetric == SortMetric.CPU
            ? Comparator.comparingDouble((SystemStatsController.Proc p) -> p.cpu)
            : Comparator.comparingLong((SystemStatsController.Proc p) -> p.rssKb);
        if (!mSortAscending) comparator = comparator.reversed();
        comparator = comparator.thenComparing(p -> p.name, String.CASE_INSENSITIVE_ORDER);
        sorted.sort(comparator);
        mTopColumn.removeAllViews();
        mTopColumn.addView(procColumns());
        for (SystemStatsController.Proc process : sorted) mTopColumn.addView(procRow(process));
        mProcessScroll.post(() -> mProcessScroll.scrollTo(0, scrollY));
    }

    private View procRow(@NonNull SystemStatsController.Proc p) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        TextView name = mono(9, p.kernel ? mOnSurfaceVariant : mOnSurface);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setText(resolveProcessName(p));
        row.addView(name, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView mem = mono(9, mSecondary);
        mem.setText(fmt(p.rssKb));
        mem.setWidth(dp(64));
        mem.setGravity(Gravity.END);
        row.addView(mem);
        TextView cpu = mono(9, barColor((int) Math.round(p.cpu)));
        cpu.setText(String.format(Locale.ROOT, "%4.1f%%", p.cpu));
        cpu.setWidth(dp(58));
        cpu.setGravity(Gravity.END);
        row.addView(cpu);
        row.setPadding(0, dp(1), 0, dp(1));
        return row;
    }

    private View procColumns() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        TextView app = mono(8, mOnSurfaceVariant);
        app.setText("APP");
        row.addView(app, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        row.addView(sortColumn("MEM", SortMetric.MEMORY),
            new LayoutParams(dp(64), dp(18)));
        row.addView(sortColumn("CPU", SortMetric.CPU),
            new LayoutParams(dp(58), dp(18)));
        row.setPadding(0, 0, 0, dp(2));
        return row;
    }

    private View sortColumn(@NonNull String label, @NonNull SortMetric metric) {
        LinearLayout control = new LinearLayout(getContext());
        control.setOrientation(HORIZONTAL);
        control.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        control.setClickable(true);
        control.setFocusable(true);
        TextView text = mono(8, mSortMetric == metric ? mTertiary : mOnSurfaceVariant);
        text.setText(label);
        control.addView(text);
        SortGlyphView glyph = new SortGlyphView(getContext(), metric);
        LayoutParams glyphParams = new LayoutParams(dp(13), dp(16));
        glyphParams.leftMargin = dp(2);
        control.addView(glyph, glyphParams);
        control.setContentDescription(label + " sort, "
            + (mSortMetric == metric ? (mSortAscending ? "ascending" : "descending") : "inactive"));
        control.setOnClickListener(v -> {
            if (mSortMetric == metric) mSortAscending = !mSortAscending;
            else {
                mSortMetric = metric;
                mSortAscending = false;
            }
            rebuildProcesses();
        });
        return control;
    }

    @NonNull
    private String resolveProcessName(@NonNull SystemStatsController.Proc process) {
        String processName = process.name;
        if (process.kernel) return friendlyKernelName(processName);
        String packageName = processName;
        int colon = packageName.indexOf(':');
        if (colon > 0) packageName = packageName.substring(0, colon);
        if (!packageName.contains(".")) return processName;
        try {
            android.content.pm.PackageManager pm = getContext().getPackageManager();
            android.content.pm.ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            return TextUtils.isEmpty(label) ? processName : label.toString();
        } catch (Exception ignored) {
            return processName;
        }
    }

    @NonNull
    static String friendlyKernelName(@NonNull String rawName) {
        String name = rawName.replace("[", "").replace("]", "");
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("memlat")) return "Kernel · memory latency";
        if (lower.startsWith("kworker")) {
            int dash = name.lastIndexOf('-');
            String queue = dash >= 0 && dash + 1 < name.length()
                ? name.substring(dash + 1).replace('_', ' ') : "worker";
            return "Kernel · " + queue;
        }
        if (lower.startsWith("irq/")) return "Kernel · IRQ " + name.substring(4);
        if (lower.startsWith("ksoftirqd")) return "Kernel · soft IRQ";
        if (lower.startsWith("migration")) return "Kernel · migration";
        if (lower.startsWith("rcu")) return "Kernel · RCU";
        if (lower.endsWith("events") || lower.endsWith("events unbound")) {
            return "Kernel · events";
        }
        return "Kernel · " + name.replace('_', ' ');
    }

    private TextView sectionHeader(@NonNull String text, int color) {
        TextView tv = mono(10, color);
        tv.setText(text);
        tv.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        return tv;
    }

    private TextView mono(int sp, int color) {
        TextView tv = new TextView(getContext());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextColor(color);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    private LayoutParams lp(int topMargin) {
        LayoutParams p = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        p.topMargin = topMargin;
        return p;
    }

    private int barColor(int percent) {
        if (percent < 60) return mPrimary;
        if (percent < 85) return mTertiary;
        return mError;
    }

    private static float clampFraction(long part, long whole) {
        if (whole <= 0) return 0f;
        return Math.max(0f, Math.min(1f, part / (float) whole));
    }

    private static String fmt(long kb) {
        if (kb >= 1048576) return String.format(Locale.ROOT, "%.1fG", kb / 1048576.0);
        if (kb >= 1024) return String.format(Locale.ROOT, "%.0fM", kb / 1024.0);
        return kb + "K";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    /**
     * Standard table sort marker: a muted stacked ▲▼ pair while the column is inactive, and the
     * active column's single triangle pointing the way the sort runs.
     */
    private final class SortGlyphView extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path mPath = new android.graphics.Path();
        private final SortMetric mMetric;

        SortGlyphView(@NonNull Context context, @NonNull SortMetric metric) {
            super(context);
            mMetric = metric;
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setPathEffect(new android.graphics.CornerPathEffect(dp(0.75f)));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            boolean active = mSortMetric == mMetric;
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            if (active) {
                mPaint.setColor(mTertiary);
                drawTriangle(canvas, cx, cy, dp(7.5f), dp(5), mSortAscending);
            } else {
                mPaint.setColor(ColorUtils.setAlphaComponent(mOnSurfaceVariant, 110));
                drawTriangle(canvas, cx, cy - dp(2.75f), dp(6), dp(3.5f), true);
                drawTriangle(canvas, cx, cy + dp(2.75f), dp(6), dp(3.5f), false);
            }
        }

        private void drawTriangle(@NonNull Canvas canvas, float cx, float cy, float width,
                                  float height, boolean up) {
            float base = up ? cy + height / 2f : cy - height / 2f;
            float apex = up ? cy - height / 2f : cy + height / 2f;
            mPath.rewind();
            mPath.moveTo(cx - width / 2f, base);
            mPath.lineTo(cx + width / 2f, base);
            mPath.lineTo(cx, apex);
            mPath.close();
            canvas.drawPath(mPath, mPaint);
        }
    }

    /** A slim rounded bar filled to a fraction. */
    private final class BarView extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float mUsed;
        private int mColor;

        BarView(@NonNull Context context) {
            super(context);
        }

        void setSingle(float fraction, int color) {
            mUsed = Math.max(0f, Math.min(1f, fraction));
            mColor = color;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float h = getHeight();
            float w = getWidth();
            float r = h / 2f;
            mPaint.setColor(ColorUtils.setAlphaComponent(mPanel, 90));
            canvas.drawRoundRect(0, 0, w, h, r, r, mPaint);
            float fillW = Math.max(w * mUsed, mUsed > 0 ? r : 0);
            mPaint.setColor(mColor);
            canvas.drawRoundRect(0, 0, fillW, h, r, r, mPaint);
        }
    }
}
