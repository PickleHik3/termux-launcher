package com.termux.app.assistant;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * The strip of the app's own offers between the thread and the composer (#383): up to two rows,
 * each a message, one button and Not now, made of the notice's own look. Gone with no offers. It
 * is told what to say; it never reads a model and nothing here reaches a chat.
 */
final class AssistantOffersView extends LinearLayout {

    static final int MAX_ROWS = 2;

    /** Space between the two rows. */
    private static final int ROW_GAP_DP = 4;

    private final AssistantNoticeView[] rows = new AssistantNoticeView[MAX_ROWS];

    AssistantOffersView(@NonNull Context context, @NonNull AssistantUi.Palette palette) {
        super(context);
        setOrientation(VERTICAL);
        for (int i = 0; i < MAX_ROWS; i++) {
            rows[i] = new AssistantNoticeView(context, palette);
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.topMargin = AssistantUi.dp(context, ROW_GAP_DP);
            addView(rows[i], lp);
        }
        setVisibility(GONE);
    }

    void setPalette(@NonNull AssistantUi.Palette p) {
        for (AssistantNoticeView row : rows) row.setPalette(p);
    }

    /** Shows one row per entry (at most {@link #MAX_ROWS}), or hides the strip for none. */
    void show(@Nullable List<AssistantNoticeView.Content> contents) {
        int n = contents == null ? 0 : Math.min(contents.size(), MAX_ROWS);
        for (int i = 0; i < MAX_ROWS; i++) rows[i].show(i < n ? contents.get(i) : null);
        AssistantUi.setVisible(this, n > 0);
    }
}
