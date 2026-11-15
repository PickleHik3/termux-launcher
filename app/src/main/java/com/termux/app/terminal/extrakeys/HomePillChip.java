package com.termux.app.terminal.extrakeys;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.notice.TerminalDress;
import com.termux.shared.theme.ThemeUtils;

/**
 * The "Edit keys" chip that hangs off the Home pill after a pick-up: the same static glass as the
 * pill, 34 dp tall, an edit glyph and one line of text in the accent the bar's keys use.
 */
public final class HomePillChip extends androidx.appcompat.widget.AppCompatTextView {

    private static final float PADDING_DP = 14f;
    private static final String EDIT_GLYPH = "✎";

    private final GradientDrawable mGlass = new GradientDrawable();
    private final int mHeightPx;

    public HomePillChip(@NonNull Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        mHeightPx = Math.round(HomePillSpot.CHIP_HEIGHT_DP * density);
        int pad = Math.round(PADDING_DP * density);
        setPadding(pad, 0, pad, 0);
        setGravity(Gravity.CENTER);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setText(EDIT_GLYPH + "  " + context.getString(R.string.home_pill_edit_keys));
        setTextColor(ThemeUtils.getSystemAttrColor(context,
            ExtraKeysPageView.ATTR_BUTTON_ACTIVE_TEXT_COLOR,
            ExtraKeysPageView.DEFAULT_BUTTON_ACTIVE_TEXT_COLOR));
        applyDress(TerminalDress.stored(context));
        setBackground(mGlass);
        setClickable(true);
    }

    /** Re-reads the glass from a dress, as the pill does. */
    public void setDress(@NonNull TerminalDress dress) {
        applyDress(dress);
    }

    private void applyDress(@NonNull TerminalDress dress) {
        mGlass.setColor(dress.fillColor);
        mGlass.setStroke(Math.round(dress.strokeWidthPx), dress.strokeColor);
        mGlass.setCornerRadius(HomePillSpot.CHIP_HEIGHT_DP / 2f
            * getContext().getResources().getDisplayMetrics().density);
    }

    /** One tap on the chip. */
    public void setOnEditKeys(@Nullable Runnable action) {
        setOnClickListener(action == null ? null : (View v) -> action.run());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(mHeightPx, MeasureSpec.EXACTLY));
    }
}
