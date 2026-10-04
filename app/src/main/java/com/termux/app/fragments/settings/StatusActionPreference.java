package com.termux.app.fragments.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.termux.R;

/** A status card with one explicit action, used only for services and permissions. */
public final class StatusActionPreference extends Preference {
    public enum Tone { POSITIVE, WARNING, ERROR, NEUTRAL }
    private CharSequence status = "";
    private CharSequence action = "";
    private Tone tone = Tone.NEUTRAL;
    private boolean optional;

    public StatusActionPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_status_action_card);
        setIconSpaceReserved(false);
    }

    public void setState(@NonNull CharSequence status, @NonNull CharSequence action, @NonNull Tone tone) {
        this.status = status;
        this.action = action;
        this.tone = tone;
        notifyChanged();
    }

    /**
     * Marks the service as optional: while it is off, an ERROR or WARNING tone is shown as a
     * neutral "not enabled" state instead of an alarm. Required access (not optional) keeps its
     * warning/error colour and icon. POSITIVE is unaffected.
     */
    public void setOptional(boolean optional) {
        if (this.optional == optional) return;
        this.optional = optional;
        notifyChanged();
    }

    public boolean isOptional() {
        return optional;
    }

    /** The tone actually shown: optional services never present as error or warning. */
    @androidx.annotation.VisibleForTesting
    @NonNull
    Tone effectiveTone() {
        return optional && (tone == Tone.ERROR || tone == Tone.WARNING) ? Tone.NEUTRAL : tone;
    }

    @Override public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView statusView = (TextView) holder.findViewById(R.id.settings_status_text);
        MaterialButton actionView = (MaterialButton) holder.findViewById(R.id.settings_status_action);
        ImageView icon = (ImageView) holder.findViewById(R.id.settings_status_icon);
        if (statusView != null) {
            statusView.setText(status);
            statusView.setTextColor(toneColor());
        }
        if (actionView != null) {
            actionView.setText(action);
            actionView.setOnClickListener(view -> performClick());
        }
        if (icon != null) {
            Tone shown = effectiveTone();
            icon.setImageResource(shown == Tone.POSITIVE ? R.drawable.ic_symbol_check_circle
                : shown == Tone.ERROR ? R.drawable.ic_symbol_error
                : shown == Tone.WARNING ? R.drawable.ic_symbol_warning : R.drawable.ic_symbol_info);
            icon.setImageTintList(ColorStateList.valueOf(toneColor()));
        }
    }

    private int toneColor() {
        Tone shown = effectiveTone();
        int attr = shown == Tone.ERROR ? androidx.appcompat.R.attr.colorError
            : shown == Tone.WARNING ? com.google.android.material.R.attr.colorTertiary
            : shown == Tone.POSITIVE ? androidx.appcompat.R.attr.colorPrimary
            : com.google.android.material.R.attr.colorOnSurfaceVariant;
        return MaterialColors.getColor(getContext(), attr,
            ContextCompat.getColor(getContext(), R.color.termux_on_surface_variant));
    }
}
