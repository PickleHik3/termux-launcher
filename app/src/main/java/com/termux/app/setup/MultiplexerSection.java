package com.termux.app.setup;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.termux.R;

/** Section 3: several terminals in one workspace. Writes nothing until Finish. */
final class MultiplexerSection implements SetupSection {

    @Override
    public int nameRes() {
        return R.string.setup_section_panes;
    }

    @NonNull
    @Override
    public View createView(@NonNull Context context, @NonNull SetupState state) {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        SetupUi.header(page, R.string.setup_panes_title, R.string.setup_panes_description);
        SetupState.Values v = state.values();

        LinearLayout subs = new LinearLayout(context);
        subs.setOrientation(LinearLayout.VERTICAL);
        subs.setVisibility(v.multiplexer ? View.VISIBLE : View.GONE);

        SetupUi.toggleRow(page, R.string.setup_panes_multiplexer, 0, v.multiplexer, 16,
            (button, checked) -> {
                state.touch();
                v.multiplexer = checked;
                subs.setVisibility(checked ? View.VISIBLE : View.GONE);
            });
        page.addView(subs, SetupUi.matchWrap(0));
        SetupUi.toggleRow(subs, R.string.setup_panes_status, R.string.setup_panes_status_help,
            v.statusShown, 16, (button, checked) -> {
                state.touch();
                v.statusShown = checked;
            });
        SetupUi.toggleRow(subs, R.string.setup_panes_autotiling,
            R.string.setup_panes_autotiling_help, v.autotiling, 14, (button, checked) -> {
                state.touch();
                v.autotiling = checked;
            });
        SetupUi.toggleRow(subs, R.string.setup_panes_grows, R.string.setup_panes_grows_help,
            v.focusGrows, 14, (button, checked) -> {
                state.touch();
                v.focusGrows = checked;
            });
        return page;
    }
}
