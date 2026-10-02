package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.termux.app.haptics.Haptics;

/**
 * The TAI screens' segmented button (Installed | Chat | Speech, the 1 | 2 | 3 of the
 * simultaneous-downloads row, the install sheet's choices): the theme's stock
 * {@link MaterialButtonToggleGroup} in single-selection mode, with one outlined
 * {@link MaterialButton} per label. It only adds the labels-and-index API the screens already
 * speak; the look, the motion and the radio semantics for accessibility are Material's.
 */
public final class TaiSegmentedTabs extends MaterialButtonToggleGroup {
    public interface OnSegmentSelectedListener {
        void onSegmentSelected(int index);
    }

    private int selected = -1;
    private boolean applying;
    @Nullable private OnSegmentSelectedListener listener;

    public TaiSegmentedTabs(@NonNull Context context) {
        this(context, null);
    }

    public TaiSegmentedTabs(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setSingleSelection(true);
        setSelectionRequired(true);
        addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || applying) return;
            int index = indexOfChild(group.findViewById(checkedId));
            if (index < 0 || index == selected) return;
            selected = index;
            Haptics.tick(group, HapticFeedbackConstants.CLOCK_TICK);
            if (listener != null) listener.onSegmentSelected(index);
        });
    }

    public void setOnSegmentSelectedListener(@Nullable OnSegmentSelectedListener listener) {
        this.listener = listener;
    }

    /** Replaces the labels; keeps the selection when it is still in range. */
    public void setLabels(@NonNull CharSequence... texts) {
        boolean same = getChildCount() == texts.length;
        for (int i = 0; same && i < texts.length; i++) {
            same = texts[i].toString().contentEquals(((MaterialButton) getChildAt(i)).getText());
        }
        if (same) return;
        applying = true;
        removeAllViews();
        for (CharSequence text : texts) {
            MaterialButton button = (MaterialButton) android.view.LayoutInflater.from(getContext())
                .inflate(com.termux.R.layout.segment_button, this, false);
            button.setId(View.generateViewId());
            button.setText(text);
            button.setSingleLine(true);
            addView(button, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        if (selected >= texts.length) selected = -1;
        if (selected >= 0) check(getChildAt(selected).getId());
        applying = false;
    }

    public int selectedIndex() {
        return selected;
    }

    /** Marks a segment as chosen; {@code animate} is kept for callers, Material owns the motion. */
    public void select(int index, boolean animate) {
        if (index == selected || index < 0 || index >= getChildCount()) return;
        applying = true;
        selected = index;
        check(getChildAt(index).getId());
        applying = false;
    }
}
