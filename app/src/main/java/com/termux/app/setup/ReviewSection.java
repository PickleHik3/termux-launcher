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
import com.termux.app.wall.UseAs;

/**
 * The last section: what Finish will write, grouped. Later waves add Appearance, Power and
 * battery and Setup cards groups here.
 */
final class ReviewSection implements SetupSection {

    @Override
    public int nameRes() {
        return R.string.setup_section_review;
    }

    @Override
    public boolean showsMiniature() {
        return false;
    }

    @NonNull
    @Override
    public View createView(@NonNull Context context, @NonNull SetupState state) {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        TextView title = SetupUi.text(context, 22f, true, SetupUi.onSurface(page));
        title.setText(R.string.setup_review_title);
        page.addView(title, SetupUi.matchWrap(0));
        TextView note = SetupUi.text(context, 14f, false, SetupUi.onSurfaceVariant(page));
        note.setText(R.string.setup_review_note);
        page.addView(note, SetupUi.matchWrap(SetupUi.dp(context, 4)));

        addGroup(context, page, R.string.setup_group_use);
        addRow(context, page, R.string.setup_row_use_as,
            state.useAs() == UseAs.TERMINAL ? R.string.setup_value_terminal
                : R.string.setup_value_home_screen);
        addRow(context, page, R.string.setup_row_start_in,
            state.startInChanges() ? R.string.setup_value_workspace
                : R.string.setup_value_unchanged);
        SetupState.Values v = state.values();
        addRow(context, page, R.string.setup_row_multiplexer, onOff(context, v.multiplexer));
        if (v.multiplexer) {
            addRow(context, page, R.string.setup_row_status_autotiling,
                pair(onOff(context, v.statusShown), onOff(context, v.autotiling)));
            addRow(context, page, R.string.setup_row_focus_grows, onOff(context, v.focusGrows));
        }

        addGroup(context, page, R.string.setup_group_power);
        addRow(context, page, R.string.setup_row_glass_motion,
            pair(onOff(context, v.fancierGlass), onOff(context, v.motionLight)));
        addRow(context, page, R.string.setup_row_parallax_lazy,
            pair(onOff(context, v.parallax), onOff(context, v.lazy)));
        addRow(context, page, R.string.setup_row_memory_animation,
            pair(onOff(context, v.lowMemory), speedLabel(context, v.animationSpeed)));

        addGroup(context, page, R.string.setup_group_cards);
        addRow(context, page, R.string.setup_row_desktop_apps, context.getString(
            v.desktopApps ? R.string.setup_value_card : R.string.setup_value_not_now));
        addRow(context, page, R.string.setup_row_ai, context.getString(
            v.ai ? R.string.setup_value_card : R.string.setup_value_off));
        return page;
    }

    @NonNull
    private static String onOff(@NonNull Context context, boolean on) {
        return context.getString(on ? R.string.setup_value_on : R.string.setup_value_off);
    }

    @NonNull
    private static String pair(@NonNull String first, @NonNull String second) {
        return first + " / " + second;
    }

    @NonNull
    private static String speedLabel(@NonNull Context context, @NonNull String speed) {
        switch (speed) {
            case AnimationSpeed.SLOWER: return context.getString(R.string.setup_speed_slower);
            case AnimationSpeed.FASTER: return context.getString(R.string.setup_speed_faster);
            case AnimationSpeed.OFF: return context.getString(R.string.setup_speed_off);
            default: return context.getString(R.string.setup_speed_normal);
        }
    }

    private void addRow(@NonNull Context context, @NonNull LinearLayout page,
                        @StringRes int label, @StringRes int value) {
        addRow(context, page, label, context.getString(value));
    }

    private void addGroup(@NonNull Context context, @NonNull LinearLayout page,
                          @StringRes int heading) {
        TextView view = SetupUi.text(context, 12f, true, SetupUi.accent(page));
        view.setAllCaps(true);
        view.setLetterSpacing(0.1f);
        view.setText(heading);
        page.addView(view, SetupUi.matchWrap(SetupUi.dp(context, 12)));
    }

    private void addRow(@NonNull Context context, @NonNull LinearLayout page,
                        @StringRes int label, @NonNull String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = SetupUi.text(context, 15f, false, SetupUi.onSurface(page));
        name.setText(label);
        row.addView(name, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView shown = SetupUi.text(context, 15f, true, SetupUi.onSurface(page));
        shown.setText(value);
        row.addView(shown, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        page.addView(row, SetupUi.matchWrap(SetupUi.dp(context, 6)));
    }
}
