package com.termux.app.setup;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.termux.R;

/** Section 4: whether Finish leaves a Setup card for Linux desktop apps. Nothing downloads. */
final class DesktopAppsSection implements SetupSection {

    @Override
    public int nameRes() {
        return R.string.setup_section_desktop;
    }

    @NonNull
    @Override
    public View createView(@NonNull Context context, @NonNull SetupState state) {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        SetupUi.header(page, R.string.setup_desktop_title, R.string.setup_desktop_description);
        View[] cards = new View[2];
        cards[0] = addChoice(context, page, state, true, R.string.setup_desktop_yes,
            R.string.setup_desktop_yes_summary, cards, 16);
        cards[1] = addChoice(context, page, state, false, R.string.setup_desktop_no,
            R.string.setup_desktop_no_summary, cards, 10);
        TextView note = SetupUi.text(context, 13f, false, SetupUi.onSurfaceVariant(page));
        note.setText(R.string.setup_desktop_note);
        page.addView(note, SetupUi.matchWrap(SetupUi.dp(context, 14)));
        return page;
    }

    @NonNull
    private View addChoice(@NonNull Context context, @NonNull LinearLayout page,
                           @NonNull SetupState state, boolean yes, @StringRes int title,
                           @StringRes int summary, @NonNull View[] cards, int topDp) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = SetupUi.dp(context, 16);
        card.setPadding(pad, SetupUi.dp(context, 14), pad, SetupUi.dp(context, 14));
        TextView heading = SetupUi.text(context, 16f, true, SetupUi.onSurface(card));
        heading.setText(title);
        card.addView(heading, SetupUi.matchWrap(0));
        TextView sentence = SetupUi.text(context, 13.5f, false, SetupUi.onSurfaceVariant(card));
        sentence.setText(summary);
        card.addView(sentence, SetupUi.matchWrap(SetupUi.dp(context, 2)));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(view -> {
            state.touch();
            state.values().desktopApps = yes;
            SetupUi.dressChoice(cards[0], yes);
            SetupUi.dressChoice(cards[1], !yes);
        });
        SetupUi.dressChoice(card, state.values().desktopApps == yes);
        page.addView(card, SetupUi.matchWrap(SetupUi.dp(context, topDp)));
        return card;
    }
}
