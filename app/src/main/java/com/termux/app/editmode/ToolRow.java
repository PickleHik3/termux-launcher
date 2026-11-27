package com.termux.app.editmode;

import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * One place's tools in the Control bar card: the tools it holds, in order, then the ones it
 * could hold. A tap switches a tool on (at the end) or off; a hold then a drag moves an "on" tool
 * to a new place in the order, and the drop is the one change. Pure widget: it reports the order
 * that results and shows what it is given.
 */
final class ToolRow extends HorizontalScrollView {

    /** How a chip looks and is spoken. */
    interface Style {
        @NonNull TextView chip(@NonNull Context context, @NonNull CharSequence label);

        void paint(@NonNull TextView chip, boolean on);

        @NonNull CharSequence label(@NonNull String tool);

        @NonNull CharSequence spokenState(@NonNull String tool, boolean on);

        @NonNull CharSequence earlier();

        @NonNull CharSequence later();
    }

    /** The order the user's change leaves: the tools that are on, in order. */
    interface Listener {
        void changed(@NonNull List<String> on);
    }

    @NonNull private final Style mStyle;
    @NonNull private final Listener mListener;
    @NonNull private final LinearLayout mChips;
    @NonNull private final List<String> mOn = new ArrayList<>();
    @NonNull private final List<String> mOff = new ArrayList<>();
    private boolean mDragging;
    /** A chip held with its finger still down, or null. */
    private String mHeldTool;
    private final int mTouchSlop;

    ToolRow(@NonNull Context context, @NonNull Style style, @NonNull Listener listener) {
        super(context);
        mStyle = style;
        mListener = listener;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        mChips = new LinearLayout(context);
        mChips.setOrientation(LinearLayout.HORIZONTAL);
        addView(mChips, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** The tools that are on, in order. */
    @NonNull
    List<String> on() {
        return new ArrayList<>(mOn);
    }

    /** Whether a drag is under the finger (the card does not rebuild the row then). */
    boolean dragging() {
        return mDragging;
    }

    /** Shows {@code on} in order, then the rest of {@code options}; nothing happens when it is what is shown. */
    void show(@NonNull List<String> options, @NonNull List<String> on) {
        if (mDragging) return;
        List<String> off = new ArrayList<>();
        for (String tool : options) if (!on.contains(tool)) off.add(tool);
        List<String> shownOn = new ArrayList<>();
        for (String tool : on) if (options.contains(tool) && !shownOn.contains(tool)) shownOn.add(tool);
        if (shownOn.equals(mOn) && off.equals(mOff) && mChips.getChildCount() > 0) return;
        mOn.clear();
        mOn.addAll(shownOn);
        mOff.clear();
        mOff.addAll(off);
        rebuild();
    }

    private void rebuild() {
        mChips.removeAllViews();
        Context context = getContext();
        for (String tool : mOn) addChip(context, tool, true);
        for (String tool : mOff) addChip(context, tool, false);
    }

    private void addChip(@NonNull Context context, @NonNull String tool, boolean on) {
        TextView chip = mStyle.chip(context, mStyle.label(tool));
        chip.setTag(tool);
        mStyle.paint(chip, on);
        chip.setContentDescription(mStyle.spokenState(tool, on));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = Math.round(8 * chip.getResources().getDisplayMetrics().density);
        mChips.addView(chip, params);
        float[] down = new float[1];
        boolean[] live = new boolean[1];
        Runnable hold = () -> {
            if (!live[0] || !on) return;
            mDragging = true;
            mHeldTool = tool;
            chip.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            chip.setAlpha(0.7f);
            requestDisallowInterceptTouchEvent(true);
        };
        chip.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getRawX();
                    live[0] = true;
                    view.postDelayed(hold, ViewConfiguration.getLongPressTimeout());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (mDragging && tool.equals(mHeldTool)) {
                        view.setTranslationX(event.getRawX() - down[0]);
                        return true;
                    }
                    if (Math.abs(event.getRawX() - down[0]) > mTouchSlop) {
                        live[0] = false;
                        view.removeCallbacks(hold);
                    }
                    return false;
                case MotionEvent.ACTION_UP:
                    view.removeCallbacks(hold);
                    if (mDragging && tool.equals(mHeldTool)) {
                        drop(view, tool);
                    } else if (live[0]) {
                        toggle(tool, on);
                    }
                    live[0] = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    view.removeCallbacks(hold);
                    if (mDragging && tool.equals(mHeldTool)) endDrag(view);
                    live[0] = false;
                    return true;
                default:
                    return true;
            }
        });
        if (on) {
            ViewCompat.addAccessibilityAction(chip, mStyle.earlier(), (v, args) -> {
                move(tool, -1);
                return true;
            });
            ViewCompat.addAccessibilityAction(chip, mStyle.later(), (v, args) -> {
                move(tool, 1);
                return true;
            });
        }
    }

    private void toggle(@NonNull String tool, boolean wasOn) {
        List<String> next = new ArrayList<>(mOn);
        if (wasOn) next.remove(tool);
        else next.add(tool);
        mListener.changed(next);
    }

    private void move(@NonNull String tool, int by) {
        int from = mOn.indexOf(tool);
        int to = from + by;
        if (from < 0 || to < 0 || to >= mOn.size()) return;
        List<String> next = new ArrayList<>(mOn);
        next.remove(from);
        next.add(to, tool);
        mListener.changed(next);
    }

    /** The finger lifted over {@code chip}: it lands before the "on" tools whose middle it has not passed. */
    private void drop(@NonNull View chip, @NonNull String tool) {
        float middle = chip.getLeft() + chip.getWidth() / 2f + chip.getTranslationX();
        int index = 0;
        for (int i = 0; i < mOn.size(); i++) {
            View other = mChips.getChildAt(i);
            if (other == chip) continue;
            if (other.getLeft() + other.getWidth() / 2f < middle) index++;
        }
        endDrag(chip);
        List<String> next = new ArrayList<>(mOn);
        next.remove(tool);
        next.add(Math.min(index, next.size()), tool);
        if (!next.equals(mOn)) mListener.changed(next);
    }

    private void endDrag(@NonNull View chip) {
        mDragging = false;
        mHeldTool = null;
        chip.setTranslationX(0f);
        chip.setAlpha(1f);
    }
}
