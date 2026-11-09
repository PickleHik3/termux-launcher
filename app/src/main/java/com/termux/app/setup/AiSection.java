package com.termux.app.setup;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.termux.R;

/** Section 5: whether Finish leaves a Setup card for on-device AI. Nothing downloads. */
final class AiSection implements SetupSection {

    @Override
    public int nameRes() {
        return R.string.setup_section_ai;
    }

    @NonNull
    @Override
    public View createView(@NonNull Context context, @NonNull SetupState state) {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        SetupUi.header(page, R.string.setup_ai_title, R.string.setup_ai_description);
        SetupUi.toggleRow(page, R.string.setup_ai_toggle, R.string.setup_ai_toggle_help,
            state.values().ai, 16, (button, checked) -> {
                state.touch();
                state.values().ai = checked;
            });
        SetupUi.infoRow(page, R.string.setup_ai_stt, R.string.setup_ai_stt_detail, 20);
        SetupUi.infoRow(page, R.string.setup_ai_cleanup, R.string.setup_ai_cleanup_detail, 12);
        SetupUi.infoRow(page, R.string.setup_ai_tts, R.string.setup_ai_tts_detail, 12);
        TextView note = SetupUi.text(context, 13f, false, SetupUi.onSurfaceVariant(page));
        note.setText(R.string.setup_ai_note);
        page.addView(note, SetupUi.matchWrap(SetupUi.dp(context, 14)));
        return page;
    }
}
