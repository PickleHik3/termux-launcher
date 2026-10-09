package com.termux.app.tour;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.graphics.ColorUtils;

/**
 * The two buttons the welcome guide is built from, shared by the setup sheet and the run's cards:
 * a filled pill in the accent for the way on, and a wash of the accent for everything else.
 *
 * <p>Each is laid out 48dp tall for the thumb — the {@code ActionButtonRow} they sit in holds them
 * there — and drawn {@link #VISUAL_DP} tall, the size the card's type wants, by insetting the fill
 * by the difference. A larger font scale grows the drawn button with the label.
 */
public final class CoachButtons {

    /** The touch height the row holds every button to. */
    private static final float TOUCH_DP = 48f;
    /** The height the button is drawn at, inside its touch height. */
    private static final float VISUAL_DP = 36f;
    /** The secondary button's corner. */
    private static final float SECONDARY_RADIUS_DP = 12f;
    /** The secondary button's fill: a wash of the accent. */
    private static final int WASH_ALPHA = 31;
    private static final float LABEL_SP = 13f;

    /** The way on: the accent, filled, as a pill. */
    @NonNull
    public static TextView primary(@NonNull Context context, @StringRes int labelRes,
                                   @ColorInt int accent, @ColorInt int onAccent,
                                   @NonNull View.OnClickListener onClick) {
        float density = context.getResources().getDisplayMetrics().density;
        // Larger than the button is tall, which a drawable clamps to a pill.
        return button(context, labelRes, onAccent, accent, TOUCH_DP * density, 20f, onClick);
    }

    /** Everything else: the accent's ink on a wash of it. */
    @NonNull
    public static TextView secondary(@NonNull Context context, @StringRes int labelRes,
                                     @ColorInt int accent, @NonNull View.OnClickListener onClick) {
        float density = context.getResources().getDisplayMetrics().density;
        return button(context, labelRes, accent, ColorUtils.setAlphaComponent(accent, WASH_ALPHA),
            SECONDARY_RADIUS_DP * density, 14f, onClick);
    }

    @NonNull
    private static TextView button(@NonNull Context context, @StringRes int labelRes,
                                   @ColorInt int ink, @ColorInt int fill, float radiusPx,
                                   float sidePaddingDp, @NonNull View.OnClickListener onClick) {
        float density = context.getResources().getDisplayMetrics().density;
        TextView button = new TextView(context);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, LABEL_SP);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(ink);
        button.setText(labelRes);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        int inset = Math.round(((TOUCH_DP - VISUAL_DP) / 2f) * density);
        int side = Math.round(sidePaddingDp * density);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(radiusPx);
        button.setBackground(new InsetDrawable(shape, 0, inset, 0, inset));
        // After the background, whose insets would otherwise replace the side padding.
        button.setPadding(side, inset, side, inset);
        button.setOnClickListener(onClick);
        return button;
    }

    private CoachButtons() {}
}
