package com.termux.app.setup;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.wall.UseAs;

import java.util.EnumMap;
import java.util.Map;

/** Section 1: whether the launcher is the home screen. Writes nothing until Finish. */
final class HomeScreenSection implements SetupSection {

    @Override
    public int nameRes() {
        return R.string.setup_section_home;
    }

    @NonNull
    @Override
    public View createView(@NonNull Context context, @NonNull SetupState state) {
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        TextView title = SetupUi.text(context, 22f, true, SetupUi.onSurface(page));
        title.setText(R.string.setup_home_title);
        page.addView(title, SetupUi.matchWrap(0));
        TextView description = SetupUi.text(context, 14f, false, SetupUi.onSurfaceVariant(page));
        description.setText(R.string.setup_home_description);
        page.addView(description, SetupUi.matchWrap(SetupUi.dp(context, 4)));

        Map<UseAs, View> cards = new EnumMap<>(UseAs.class);
        addChoice(context, page, cards, state, UseAs.HOME, R.string.setup_home_choice,
            R.string.setup_home_choice_summary);
        addChoice(context, page, cards, state, UseAs.TERMINAL, R.string.setup_terminal_choice,
            R.string.setup_terminal_choice_summary);
        return page;
    }

    private void addChoice(@NonNull Context context, @NonNull LinearLayout page,
                           @NonNull Map<UseAs, View> cards, @NonNull SetupState state,
                           @NonNull UseAs useAs, @StringRes int title, @StringRes int summary) {
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
            state.setUseAs(useAs);
            for (Map.Entry<UseAs, View> entry : cards.entrySet())
                SetupUi.dressChoice(entry.getValue(), entry.getKey() == useAs);
        });
        SetupUi.dressChoice(card, state.useAs() == useAs);
        cards.put(useAs, card);
        page.addView(card, SetupUi.matchWrap(SetupUi.dp(context, useAs == UseAs.HOME ? 16 : 10)));
    }
}
