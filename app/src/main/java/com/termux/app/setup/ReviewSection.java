package com.termux.app.setup;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.termux.R;
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
        return page;
    }

    private void addGroup(@NonNull Context context, @NonNull LinearLayout page,
                          @StringRes int heading) {
        TextView view = SetupUi.text(context, 12f, true, SetupUi.accent(page));
        view.setAllCaps(true);
        view.setLetterSpacing(0.1f);
        view.setText(heading);
        page.addView(view, SetupUi.matchWrap(SetupUi.dp(context, 18)));
    }

    private void addRow(@NonNull Context context, @NonNull LinearLayout page,
                        @StringRes int label, @StringRes int value) {
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
        page.addView(row, SetupUi.matchWrap(SetupUi.dp(context, 10)));
    }
}
