package com.termux.app.fragments.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.chip.Chip;
import com.termux.R;

/**
 * Preference row with a trailing status pill (for example ENABLED / NOT ENABLED),
 * matching the TL handoff permission and backend status rows.
 */
@Keep
public class PillPreference extends Preference {

    public enum Tone { POSITIVE, NEUTRAL, NEGATIVE }

    private CharSequence mPillText = "";
    private Tone mTone = Tone.NEUTRAL;

    public PillPreference(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.preference_widget_pill);
        setIconSpaceReserved(false);
    }

    public PillPreference(@NonNull Context context) {
        this(context, null);
    }

    public void setPill(CharSequence text, Tone tone) {
        mPillText = text == null ? "" : text;
        mTone = tone == null ? Tone.NEUTRAL : tone;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View pillView = holder.findViewById(R.id.settings_pill);
        if (!(pillView instanceof Chip)) return;
        Chip pill = (Chip) pillView;
        if (mPillText.length() == 0) {
            pill.setVisibility(View.GONE);
            return;
        }
        pill.setVisibility(View.VISIBLE);
        pill.setText(mPillText);
        int color = resolveToneColor();
        pill.setTextColor(color);
        pill.setChipBackgroundColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 46)));
    }

    private int resolveToneColor() {
        int attr;
        switch (mTone) {
            case POSITIVE:
                attr = com.termux.shared.R.attr.termuxColorPrimary;
                break;
            case NEGATIVE:
                attr = com.termux.shared.R.attr.termuxColorError;
                break;
            default:
                attr = com.termux.shared.R.attr.termuxColorOnSurfaceVariant;
                break;
        }
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)) {
            return value.data;
        }
        return ContextCompat.getColor(getContext(), R.color.termux_on_surface_variant);
    }
}
