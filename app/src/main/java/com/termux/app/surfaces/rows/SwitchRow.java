package com.termux.app.surfaces.rows;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * One on/off setting as a 56dp row (appearance-cards overhaul §4, Single on/off): its glyph, its
 * name and a {@link MaterialSwitch} at the end; the whole row toggles. For the cards that draw
 * their own rows (Status bar, Dock); CardBody draws the same row for its cards.
 */
public final class SwitchRow {

    /** Called with the new state after a touch; never on {@link #setOn}. */
    public interface OnToggle {
        void onToggle(boolean on);
    }

    @NonNull public final LinearLayout root;
    @NonNull public final MaterialSwitch toggle;
    private boolean mRestating;

    private SwitchRow(@NonNull LinearLayout root, @NonNull MaterialSwitch toggle) {
        this.root = root;
        this.toggle = toggle;
    }

    @NonNull
    public static SwitchRow build(@NonNull Context context, @DrawableRes int glyph,
                                  @NonNull CharSequence name, boolean on,
                                  @NonNull OnToggle onToggle) {
        LinearLayout line = RowKit.line(context);
        line.setMinimumHeight(RowKit.dp(context, RowKit.ROW_MIN_DP));
        line.addView(RowKit.glyph(context, glyph, name), RowKit.glyphParams(context));
        line.addView(RowKit.title(context, name), new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialSwitch toggle = new MaterialSwitch(context);
        toggle.setContentDescription(name);
        toggle.setChecked(on);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(RowKit.dp(context, 8));
        line.addView(toggle, params);
        SwitchRow row = new SwitchRow(line, toggle);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (!row.mRestating)
                onToggle.onToggle(checked);
        });
        line.setOnClickListener(v -> { if (toggle.isEnabled()) toggle.toggle(); });
        return row;
    }

    /** Shows {@code on} without reporting it. */
    public void setOn(boolean on) {
        if (toggle.isChecked() == on)
            return;
        mRestating = true;
        try {
            toggle.setChecked(on);
        } finally {
            mRestating = false;
        }
    }
}
