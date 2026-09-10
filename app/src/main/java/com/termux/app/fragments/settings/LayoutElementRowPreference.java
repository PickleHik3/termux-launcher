package com.termux.app.fragments.settings;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

/**
 * One compact row on the Layout page: a swatch of the colour the miniature draws this element in,
 * its name, what it is set to in each orientation, and a chevron onto its chooser. The swatch is
 * the only thing the row owns beyond a plain preference — the name is the title and the values are
 * the summary — so a chooser only has to write the summary back for the row to be current.
 */
@Keep
public final class LayoutElementRowPreference extends Preference {

    private static final float SWATCH_RADIUS_DP = 4f;

    private int mSwatchColor;

    public LayoutElementRowPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_layout_element_row);
        setIconSpaceReserved(false);
        setPersistent(false);
    }

    public LayoutElementRowPreference(@NonNull Context context) {
        this(context, null);
    }

    /** The band's colour, so the row and the picture above it name the same thing. */
    public void setSwatchColor(int color) {
        if (mSwatchColor == color) return;
        mSwatchColor = color;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View swatch = holder.findViewById(R.id.layout_element_swatch);
        if (swatch == null) return;
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(SWATCH_RADIUS_DP * getContext().getResources()
            .getDisplayMetrics().density);
        shape.setColor(mSwatchColor);
        swatch.setBackground(shape);
    }
}
