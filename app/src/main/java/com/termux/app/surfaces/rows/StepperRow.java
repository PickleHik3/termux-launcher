package com.termux.app.surfaces.rows;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.termux.R;

/**
 * A stepper row (editor-redesign §2: Columns, Rows, App icons, Icon size, Height): an outline
 * glyph, a 16 sp title, and − value + at its end. Each tap moves one step, and a button greys out
 * at its end of the range.
 */
public final class StepperRow {

    /** The value moved by a tap. */
    public interface OnValue {
        void onValue(int value);
    }

    @NonNull public final LinearLayout root;
    @NonNull public final TextView title;
    @NonNull public final MaterialButton minus;
    @NonNull public final MaterialButton plus;
    @NonNull public final TextView value;
    private final int mFrom;
    private final int mTo;
    private final int mStep;
    @NonNull private final StripRow.Readout mReadout;
    private int mValue;

    private StepperRow(@NonNull LinearLayout root, @NonNull TextView title,
                       @NonNull MaterialButton minus, @NonNull MaterialButton plus,
                       @NonNull TextView value, int from, int to, int step,
                       @NonNull StripRow.Readout readout) {
        this.root = root;
        this.title = title;
        this.minus = minus;
        this.plus = plus;
        this.value = value;
        mFrom = from;
        mTo = Math.max(from, to);
        mStep = Math.max(1, step);
        mReadout = readout;
    }

    /** Builds the row at {@code value}, from {@code from} to {@code to} by {@code step}. */
    @NonNull
    public static StepperRow build(@NonNull Context context, @DrawableRes int glyph,
                                   @NonNull CharSequence title, int from, int to, int step,
                                   int value, @NonNull StripRow.Readout readout,
                                   @NonNull OnValue onValue) {
        LinearLayout root = RowKit.line(context);
        root.addView(RowKit.glyph(context, glyph, title), RowKit.glyphParams(context));
        TextView titleView = RowKit.title(context, title);
        root.addView(titleView, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialButton minus = button(context, R.drawable.ic_editor_remove,
            context.getString(R.string.appearance_stepper_less, title));
        MaterialButton plus = button(context, R.drawable.ic_symbol_add,
            context.getString(R.string.appearance_stepper_more, title));
        TextView number = RowKit.title(context, readout.format(value));
        number.setGravity(Gravity.CENTER);
        number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // As wide as the widest value it can read, so the buttons never move under the thumb.
        float widest = 0f;
        for (int v : new int[] {from, to})
            widest = Math.max(widest, number.getPaint().measureText(readout.format(v)));
        number.setMinWidth((int) Math.ceil(widest) + RowKit.dp(context, 8));

        int touch = RowKit.dp(context, RowKit.TOUCH_DP);
        root.addView(minus, new LinearLayout.LayoutParams(touch, touch));
        root.addView(number, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(plus, new LinearLayout.LayoutParams(touch, touch));

        StepperRow row = new StepperRow(root, titleView, minus, plus, number, from, to, step,
            readout);
        row.mValue = Integer.MIN_VALUE;
        row.setValue(value);
        minus.setOnClickListener(v -> row.tap(-1, onValue));
        plus.setOnClickListener(v -> row.tap(1, onValue));
        return row;
    }

    /** Shows {@code value} (clamped) without reporting it. */
    public void setValue(int value) {
        int clamped = Math.max(mFrom, Math.min(mTo, value));
        if (clamped == mValue)
            return;
        mValue = clamped;
        RowKit.setText(this.value, mReadout.format(clamped));
        root.setContentDescription(title.getText() + ", " + mReadout.format(clamped));
        if (minus.isEnabled() != (clamped > mFrom))
            minus.setEnabled(clamped > mFrom);
        if (plus.isEnabled() != (clamped < mTo))
            plus.setEnabled(clamped < mTo);
    }

    public int value() {
        return mValue;
    }

    private void tap(int direction, @NonNull OnValue onValue) {
        int next = stepped(mValue, direction * mStep, mFrom, mTo);
        if (next == mValue)
            return;
        setValue(next);
        onValue.onValue(next);
    }

    /** {@code value} moved by {@code delta}, kept inside {@code from}..{@code to}. */
    static int stepped(int value, int delta, int from, int to) {
        long next = (long) value + delta;
        return (int) Math.max(from, Math.min(Math.max(from, to), next));
    }

    @NonNull
    private static MaterialButton button(@NonNull Context context, @DrawableRes int icon,
                                         @NonNull CharSequence description) {
        MaterialButton button = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialIconButtonOutlinedStyle);
        button.setIconResource(icon);
        button.setContentDescription(description);
        GlyphNames.name(button, description);
        return button;
    }
}
