package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.termux.R;

/**
 * The in-app Material 3 segmented pill (the group {@code SegmentedPillPreference} renders from
 * {@code preference_segmented_pill}) as a plain view for a sheet: the layout's own group and
 * segment style, its header row and gutter dropped. A label may be two lines (name, newline,
 * detail); when a line does not fit its segment the segments stack full width, as on the pages.
 */
final class TaiSheetPill {
    interface OnSelected {
        void selected(int index);
    }

    private TaiSheetPill() {}

    @NonNull
    static View create(@NonNull Context context, @NonNull CharSequence[] labels, int selected,
                       @NonNull OnSelected listener) {
        ViewGroup root = (ViewGroup) LayoutInflater.from(context)
            .inflate(R.layout.preference_segmented_pill, null, false);
        root.setPadding(0, 0, 0, 0);
        root.getChildAt(0).setVisibility(View.GONE);
        MaterialButtonToggleGroup group = root.findViewById(R.id.segmented_pill_track);
        ((ViewGroup.MarginLayoutParams) group.getLayoutParams()).topMargin = 0;
        int[] ids = {R.id.segmented_pill_default, R.id.segmented_pill_capsule,
            R.id.segmented_pill_third, R.id.segmented_pill_fourth};
        MaterialButton[] buttons = new MaterialButton[labels.length];
        for (int i = 0; i < ids.length; i++) {
            MaterialButton button = group.findViewById(ids[i]);
            if (i >= labels.length) {
                button.setVisibility(View.GONE);
                continue;
            }
            buttons[i] = button;
            button.setVisibility(View.VISIBLE);
            button.setIcon(null);
            button.setMaxLines(2);
            button.setText(labels[i]);
            button.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        }
        group.check(ids[selected]);
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) return;
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i].getId() == checkedId) {
                    listener.selected(i);
                    return;
                }
            }
        });
        // Fit is judged against the laid-out segment width: stack when a line is cut.
        group.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (group.getOrientation() == LinearLayout.VERTICAL || !anyLineCut(buttons)) return;
            group.post(() -> stack(group, buttons));
        });
        return root;
    }

    /** A name over a smaller, quieter detail line. */
    @NonNull
    static CharSequence twoLine(@NonNull String text) {
        int newline = text.indexOf('\n');
        SpannableString spannable = new SpannableString(text);
        if (newline < 0) return spannable;
        // Size only: the segment's text colour is a checked/unchecked selector, so no fixed colour.
        spannable.setSpan(new RelativeSizeSpan(0.85f), newline + 1, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    private static boolean anyLineCut(@NonNull MaterialButton[] buttons) {
        for (MaterialButton button : buttons) {
            int available = button.getWidth() - button.getPaddingLeft() - button.getPaddingRight();
            if (available <= 0) continue;
            CharSequence label = button.getText();
            int start = 0;
            while (start <= label.length()) {
                int end = start;
                while (end < label.length() && label.charAt(end) != '\n') end++;
                // The detail line is drawn smaller; measuring at full size errs towards stacking.
                if (end > start && button.getPaint().measureText(label, start, end) > available) return true;
                start = end + 1;
            }
        }
        return false;
    }

    private static void stack(@NonNull MaterialButtonToggleGroup group, @NonNull MaterialButton[] buttons) {
        group.setOrientation(LinearLayout.VERTICAL);
        for (MaterialButton button : buttons) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) button.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.weight = 0f;
            button.setLayoutParams(lp);
        }
    }
}
