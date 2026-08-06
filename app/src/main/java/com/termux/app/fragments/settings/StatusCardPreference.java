package com.termux.app.fragments.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

/**
 * Card preference showing a status header (dot + mono label) and a monospace
 * key/value body via the summary, matching the TL handoff status banner design.
 */
@Keep
public class StatusCardPreference extends Preference {

    private CharSequence mStatusLabel = "";
    private boolean mStatusActive;

    public StatusCardPreference(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StatusCardPreference(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        setLayoutResource(R.layout.preference_settings_status_card);
        setPersistent(false);
        setSelectable(false);
        setIconSpaceReserved(false);
    }

    public void setStatus(CharSequence label, boolean active) {
        CharSequence value = label == null ? "" : label;
        // Skip the no-op rebind: callers refresh on every download progress tick.
        if (android.text.TextUtils.equals(mStatusLabel, value) && mStatusActive == active) return;
        mStatusLabel = value;
        mStatusActive = active;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        int color = resolveAttrColor(mStatusActive
            ? com.termux.shared.R.attr.termuxColorPrimary
            : com.termux.shared.R.attr.termuxColorOnSurfaceVariant);
        TextView label = (TextView) holder.findViewById(R.id.settings_status_label);
        if (label != null) {
            label.setText(mStatusLabel);
            label.setTextColor(color);
        }
        ImageView dot = (ImageView) holder.findViewById(R.id.settings_status_dot);
        if (dot != null) {
            dot.setImageTintList(ColorStateList.valueOf(color));
        }
    }

    private int resolveAttrColor(int attr) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)) {
            return value.data;
        }
        return ContextCompat.getColor(getContext(), R.color.termux_on_surface_variant);
    }
}
