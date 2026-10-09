package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Result screen's history chart: one entry's decode speed over its kept runs, oldest at
 * the left, as a line with a dot per run; a dashed divider before every run where the app or
 * runtime version changed ({@link TaiBenchLeaderboard#dividers}), so a jump that came with an
 * update is not read as a change in the phone. A run whose check failed draws its dot hollow.
 * Theme colours only; nothing animates.
 */
final class TaiBenchHistoryView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hollow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint divider = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baseline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float dotRadius;
    private final float inset;
    @NonNull private List<TaiBenchLeaderboard.Point> points = Collections.emptyList();
    @NonNull private List<Integer> dividers = Collections.emptyList();

    TaiBenchHistoryView(@NonNull Context context) {
        super(context);
        int accent = TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorPrimary);
        int outline = TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorOutlineVariant);
        int variant = TaiBenchViews.color(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant);
        float density = context.getResources().getDisplayMetrics().density;
        line.setColor(accent);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.5f * density);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        dot.setColor(accent);
        hollow.setColor(accent);
        hollow.setStyle(Paint.Style.STROKE);
        hollow.setStrokeWidth(1.5f * density);
        divider.setColor(variant);
        divider.setStyle(Paint.Style.STROKE);
        divider.setStrokeWidth(density);
        divider.setPathEffect(new DashPathEffect(new float[] {3f * density, 3f * density}, 0f));
        baseline.setColor(outline);
        baseline.setStrokeWidth(density);
        dotRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, context.getResources().getDisplayMetrics());
        inset = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, context.getResources().getDisplayMetrics());
    }

    void setHistory(@NonNull List<TaiBenchLeaderboard.Point> history) {
        points = new ArrayList<>(history);
        dividers = TaiBenchLeaderboard.dividers(points);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), height = getHeight();
        canvas.drawLine(0f, height - 0.5f, width, height - 0.5f, baseline);
        int count = points.size();
        if (count == 0) return;
        double max = 0.0;
        for (TaiBenchLeaderboard.Point point : points) max = Math.max(max, point.decodeTps);
        if (max <= 0.0) max = 1.0;
        float left = inset, right = width - inset;
        float top = inset, bottom = height - inset;
        float stepX = count > 1 ? (right - left) / (count - 1) : 0f;
        path.reset();
        for (int i = 0; i < count; i++) {
            float x = count > 1 ? left + i * stepX : (left + right) / 2f;
            float y = bottom - (float) (points.get(i).decodeTps / max) * (bottom - top);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        if (count > 1) canvas.drawPath(path, line);
        for (int index : dividers) {
            if (index <= 0 || index >= count) continue;
            // Halfway between the run before the change and the first run after it.
            float x = left + (index - 0.5f) * stepX;
            canvas.drawLine(x, top - inset / 2f, x, bottom + inset / 2f, divider);
        }
        for (int i = 0; i < count; i++) {
            TaiBenchLeaderboard.Point point = points.get(i);
            float x = count > 1 ? left + i * stepX : (left + right) / 2f;
            float y = bottom - (float) (point.decodeTps / max) * (bottom - top);
            canvas.drawCircle(x, y, dotRadius, point.checkPassed ? dot : hollow);
        }
    }
}
