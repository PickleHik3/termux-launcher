package com.termux.app.setup;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.AnimationSpeed;

/** Section 6: the phone's detected tier and the power-related defaults. Writes on Finish. */
final class PowerSection implements SetupSection {

    @Override
    public int nameRes() {
        return R.string.setup_section_power;
    }

    @NonNull
    @Override
    public View createView(@NonNull Context context, @NonNull SetupState state) {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        SetupUi.header(page, R.string.setup_power_title, R.string.setup_power_description);
        TextView detected = SetupUi.text(context, 13.5f, true, SetupUi.accent(page));
        page.addView(detected, SetupUi.matchWrap(SetupUi.dp(context, 10)));
        bindDetected(context, detected, state);
        // The GPU name arrives off the main thread and updates this line.
        state.setDetectedListener(() -> {
            if (detected.isAttachedToWindow())
                bindDetected(context, detected, state);
        });

        SetupState.Values v = state.values();
        SetupUi.toggleRow(page, R.string.setup_power_glass, R.string.setup_power_glass_help,
            v.fancierGlass, 12, (button, checked) -> { state.touch(); v.fancierGlass = checked; });
        SetupUi.toggleRow(page, R.string.setup_power_motion_light,
            R.string.setup_power_motion_light_help, v.motionLight, 10,
            (button, checked) -> { state.touch(); v.motionLight = checked; });
        SetupUi.toggleRow(page, R.string.setup_power_parallax, R.string.setup_power_parallax_help,
            v.parallax, 10, (button, checked) -> { state.touch(); v.parallax = checked; });
        SetupUi.toggleRow(page, R.string.setup_power_lazy, R.string.setup_power_lazy_help,
            v.lazy, 10, (button, checked) -> { state.touch(); v.lazy = checked; });
        SetupUi.toggleRow(page, R.string.setup_power_low_memory,
            R.string.setup_power_low_memory_help, v.lowMemory, 10,
            (button, checked) -> { state.touch(); v.lowMemory = checked; });

        TextView speed = SetupUi.text(context, 16f, true, SetupUi.onSurface(page));
        speed.setText(R.string.setup_power_animation);
        page.addView(speed, SetupUi.matchWrap(SetupUi.dp(context, 14)));
        LinearLayout chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        String[] values = {AnimationSpeed.SLOWER, AnimationSpeed.NORMAL, AnimationSpeed.FASTER,
            AnimationSpeed.OFF};
        int[] labels = {R.string.setup_speed_slower, R.string.setup_speed_normal,
            R.string.setup_speed_faster, R.string.setup_speed_off};
        TextView[] views = new TextView[values.length];
        for (int i = 0; i < values.length; i++) {
            views[i] = chip(context, page, labels[i]);
            String value = values[i];
            views[i].setOnClickListener(view -> {
                state.touch();
                v.animationSpeed = value;
                for (int j = 0; j < values.length; j++)
                    SetupUi.dressChoice(views[j], values[j].equals(value));
            });
            SetupUi.dressChoice(views[i], values[i].equals(v.animationSpeed));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0)
                params.setMarginStart(SetupUi.dp(context, 8));
            chips.addView(views[i], params);
        }
        page.addView(chips, SetupUi.matchWrap(SetupUi.dp(context, 8)));
        return page;
    }

    @NonNull
    private TextView chip(@NonNull Context context, @NonNull View page, @StringRes int label) {
        TextView chip = SetupUi.text(context, 14f, true, SetupUi.onSurface(page));
        chip.setText(label);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(SetupUi.dp(context, 4), SetupUi.dp(context, 10), SetupUi.dp(context, 4),
            SetupUi.dp(context, 10));
        chip.setClickable(true);
        chip.setFocusable(true);
        return chip;
    }

    private void bindDetected(@NonNull Context context, @NonNull TextView view,
                              @NonNull SetupState state) {
        DeviceTier.Detected d = state.detected();
        String ram = d.ramBytes > 0
            ? context.getString(R.string.setup_power_ram, d.ramBytes / DeviceTier.GIB) : "?";
        String gpu = d.gpu == null || d.gpu.isEmpty()
            ? context.getString(R.string.setup_power_unknown_gpu) : d.gpu;
        int tier = d.tier == DeviceTier.Tier.LOW ? R.string.setup_tier_low
            : d.tier == DeviceTier.Tier.HIGH ? R.string.setup_tier_high : R.string.setup_tier_mid;
        view.setText(context.getString(R.string.setup_power_detected, d.model, ram, gpu,
            context.getString(tier)));
    }
}
