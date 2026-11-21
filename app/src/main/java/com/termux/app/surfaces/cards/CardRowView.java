package com.termux.app.surfaces.cards;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.surfaces.rows.SectionHeader;

/**
 * One row a card draws itself from the row kinds (editor-redesign §2), and how it reads its values
 * again: the card file builds it, the editor restates it whenever the sheet restates its rows.
 */
public final class CardRowView {

    @NonNull public final View view;
    @NonNull private final Runnable mRestate;

    public CardRowView(@NonNull View view, @NonNull Runnable restate) {
        this.view = view;
        mRestate = restate;
    }

    /** Reads the row's values again; touches made since stand as they are. */
    public void restate() {
        mRestate.run();
    }

    /**
     * A section: its header ({@link SectionHeader}, the divider over it unless the sheet's tier
     * divider already stands there) and its content under it.
     */
    @NonNull
    static LinearLayout section(@NonNull Context context, @NonNull CharSequence title,
                                @Nullable CharSequence note, boolean divider,
                                @NonNull View... content) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(SectionHeader.build(context, title, note, divider).root);
        for (View view : content)
            column.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return column;
    }
}
