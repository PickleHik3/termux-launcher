package com.termux.app.surfaces.rows;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * Two steppers on one row (spec #326: Columns x Rows): each a title over − value +, with a
 * multiplication sign between them. They stand on one line where both hold their natural width
 * ({@link PairFit}) and one under the other where a large font would not let them. Each tap moves
 * one step and reports through {@link StepperRow.OnValue}.
 *
 * <pre>
 * StepperPairRow grid = StepperPairRow.build(context,
 *     "Columns", 2, 8, 1, 4, v -> String.valueOf(v), v -> write(columns, v),
 *     "Rows", 2, 12, 1, 6, v -> String.valueOf(v), v -> write(rows, v));
 * </pre>
 */
public final class StepperPairRow {

    @NonNull public final PairRow root;
    @NonNull public final StepperRow first;
    @NonNull public final StepperRow second;

    private StepperPairRow(@NonNull PairRow root, @NonNull StepperRow first,
                           @NonNull StepperRow second) {
        this.root = root;
        this.first = first;
        this.second = second;
    }

    @NonNull
    public static StepperPairRow build(@NonNull Context context,
                                       @NonNull CharSequence firstTitle, int firstFrom,
                                       int firstTo, int firstStep, int firstValue,
                                       @NonNull SliderRow.Readout firstReadout,
                                       @NonNull StepperRow.OnValue onFirst,
                                       @NonNull CharSequence secondTitle, int secondFrom,
                                       int secondTo, int secondStep, int secondValue,
                                       @NonNull SliderRow.Readout secondReadout,
                                       @NonNull StepperRow.OnValue onSecond) {
        StepperRow first = StepperRow.buildCell(context, firstTitle, firstFrom, firstTo,
            firstStep, firstValue, firstReadout, onFirst);
        StepperRow second = StepperRow.buildCell(context, secondTitle, secondFrom, secondTo,
            secondStep, secondValue, secondReadout, onSecond);
        TextView times = RowKit.title(context, "×");
        times.setGravity(Gravity.CENTER);
        times.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Each cell needs its controls (48dp, the number, 48dp) and no more.
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        first.root.measure(unspecified, unspecified);
        second.root.measure(unspecified, unspecified);
        int need = Math.max(first.root.getMeasuredWidth(), second.root.getMeasuredWidth());
        PairRow row = new PairRow(context, first.root, second.root, times, need);
        return new StepperPairRow(row, first, second);
    }
}
