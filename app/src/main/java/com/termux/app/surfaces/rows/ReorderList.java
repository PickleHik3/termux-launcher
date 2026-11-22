package com.termux.app.surfaces.rows;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of things whose order the user sets: one 56dp line each, its glyph, its name, a switch
 * where the thing can be turned off, and a drag handle at the end. Dragging the handle moves the
 * line; the others slide aside, and the order is reported once, on release. The lines stay
 * aligned: a thing with no switch keeps the switch's room empty.
 */
public final class ReorderList {

    /** A line's thing. {@code toggleable} false: it has no switch (it shows on its own). */
    public static final class Entry {
        @DrawableRes final int glyph;
        @NonNull final CharSequence name;
        final boolean toggleable;

        public Entry(@DrawableRes int glyph, @NonNull CharSequence name, boolean toggleable) {
            this.glyph = glyph;
            this.name = name;
            this.toggleable = toggleable;
        }
    }

    /** The line at place {@code from} was dropped at place {@code to} (top to bottom). */
    public interface OnMove {
        void onMove(int from, int to);
    }

    /** Entry {@code entry}'s switch was touched. */
    public interface OnToggle {
        void onToggle(int entry, boolean on);
    }

    @NonNull public final LinearLayout root;
    @NonNull private final List<View> mLines = new ArrayList<>();
    @NonNull private final List<MaterialSwitch> mSwitches = new ArrayList<>();
    /** Each place's entry, top to bottom. */
    @NonNull private int[] mOrder;
    private boolean mRestating;

    private ReorderList(@NonNull LinearLayout root, @NonNull int[] order) {
        this.root = root;
        mOrder = order.clone();
    }

    /**
     * The list of {@code entries}, standing in {@code order} (each place's entry index),
     * {@code on} saying each entry's switch.
     */
    @NonNull
    public static ReorderList build(@NonNull Context context, @NonNull List<Entry> entries,
                                    @NonNull int[] order, @NonNull boolean[] on,
                                    @NonNull OnMove onMove, @NonNull OnToggle onToggle) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        ReorderList list = new ReorderList(column, order);
        for (int i = 0; i < entries.size(); i++)
            list.addLine(context, entries.get(i), i, on[i], onMove, onToggle);
        list.standLines();
        return list;
    }

    private void addLine(@NonNull Context context, @NonNull Entry entry, int index, boolean on,
                         @NonNull OnMove onMove, @NonNull OnToggle onToggle) {
        LinearLayout line = RowKit.line(context);
        line.addView(RowKit.glyph(context, entry.glyph, entry.name), RowKit.glyphParams(context));
        line.addView(RowKit.title(context, entry.name), new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialSwitch toggle = new MaterialSwitch(context);
        toggle.setContentDescription(entry.name);
        toggle.setChecked(on);
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        switchParams.setMarginStart(RowKit.dp(context, 8));
        line.addView(toggle, switchParams);
        if (entry.toggleable) {
            toggle.setOnCheckedChangeListener((button, checked) -> {
                if (!mRestating)
                    onToggle.onToggle(index, checked);
            });
        } else {
            // Holds the switch's room so every line's handle and title line up.
            toggle.setVisibility(View.INVISIBLE);
            toggle.setEnabled(false);
            toggle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        ImageView handle = new ImageView(context);
        handle.setImageResource(R.drawable.ic_drag_indicator_24);
        handle.setImageTintList(android.content.res.ColorStateList.valueOf(RowKit.color(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant)));
        handle.setScaleType(ImageView.ScaleType.CENTER);
        handle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int touch = RowKit.dp(context, RowKit.TOUCH_DP);
        line.addView(handle, new LinearLayout.LayoutParams(touch, touch));
        handle.setOnTouchListener(new Drag(line, onMove));
        describeMoves(context, line, onMove);
        mLines.add(line);
        mSwitches.add(toggle);
    }

    /** Move up and Move down for a screen reader, where a drag is not at hand. */
    private void describeMoves(@NonNull Context context, @NonNull View line,
                               @NonNull OnMove onMove) {
        ViewCompat.addAccessibilityAction(line, context.getString(R.string.editor_reorder_up),
            (view, args) -> moveBy(line, -1, onMove));
        ViewCompat.addAccessibilityAction(line, context.getString(R.string.editor_reorder_down),
            (view, args) -> moveBy(line, 1, onMove));
    }

    private boolean moveBy(@NonNull View line, int step, @NonNull OnMove onMove) {
        int from = root.indexOfChild(line);
        int to = from + step;
        if (from < 0 || to < 0 || to >= root.getChildCount()) return false;
        onMove.onMove(from, to);
        return true;
    }

    /** Shows {@code order} (each place's entry index); moves nothing when it already stands. */
    public void setOrder(@NonNull int[] order) {
        if (java.util.Arrays.equals(order, mOrder)) return;
        mOrder = order.clone();
        standLines();
    }

    /** Shows entry {@code entry}'s switch {@code on} without reporting it. */
    public void setOn(int entry, boolean on) {
        MaterialSwitch toggle = mSwitches.get(entry);
        if (toggle.isChecked() == on) return;
        mRestating = true;
        try {
            toggle.setChecked(on);
        } finally {
            mRestating = false;
        }
    }

    /** Puts the lines in {@link #mOrder}, touching the column only where it differs. */
    private void standLines() {
        for (int place = 0; place < mOrder.length; place++) {
            View line = mLines.get(mOrder[place]);
            if (root.getChildAt(place) == line) continue;
            if (line.getParent() == root) root.removeView(line);
            root.addView(line, place);
        }
    }

    /**
     * Where a line dragged from place {@code from} by {@code dy} lands among {@code count} lines
     * of {@code height}: the place whose middle its middle has passed.
     */
    static int dropPlace(int from, float dy, int height, int count) {
        if (height <= 0) return from;
        int to = from + Math.round(dy / height);
        return Math.max(0, Math.min(count - 1, to));
    }

    /** The handle's drag: the line follows the finger, the lines it passes slide aside. */
    private final class Drag implements View.OnTouchListener {
        @NonNull private final View mLine;
        @NonNull private final OnMove mOnMove;
        private float mDownY;
        private int mFrom = -1;

        Drag(@NonNull View line, @NonNull OnMove onMove) {
            mLine = line;
            mOnMove = onMove;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View handle, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mFrom = root.indexOfChild(mLine);
                    if (mFrom < 0) return false;
                    mDownY = event.getRawY();
                    // The sheet and the scroller above it leave the finger to the drag.
                    if (root.getParent() != null)
                        root.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (mFrom < 0) return false;
                    follow(event.getRawY() - mDownY);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (mFrom < 0) return false;
                    int to = event.getActionMasked() == MotionEvent.ACTION_UP
                        ? dropPlace(mFrom, event.getRawY() - mDownY, mLine.getHeight(),
                            root.getChildCount())
                        : mFrom;
                    settle();
                    int from = mFrom;
                    mFrom = -1;
                    if (to != from) mOnMove.onMove(from, to);
                    return true;
                default:
                    return false;
            }
        }

        /** The line at {@code dy} from where it was picked up; the passed lines shift a place. */
        private void follow(float dy) {
            int height = mLine.getHeight();
            int count = root.getChildCount();
            float low = -mFrom * (float) height;
            float high = (count - 1 - mFrom) * (float) height;
            mLine.setTranslationY(Math.max(low, Math.min(high, dy)));
            int to = dropPlace(mFrom, dy, height, count);
            for (int place = 0; place < count; place++) {
                View other = root.getChildAt(place);
                if (other == mLine) continue;
                float shift = 0f;
                if (to > mFrom && place > mFrom && place <= to) shift = -height;
                else if (to < mFrom && place < mFrom && place >= to) shift = height;
                if (other.getTranslationY() != shift) other.setTranslationY(shift);
            }
        }

        private void settle() {
            for (int place = 0; place < root.getChildCount(); place++)
                root.getChildAt(place).setTranslationY(0f);
        }
    }

    /** For tests: the entry each place shows now, top to bottom. */
    @NonNull
    int[] shownOrder() {
        int[] out = new int[root.getChildCount()];
        for (int place = 0; place < out.length; place++)
            out[place] = mLines.indexOf(root.getChildAt(place));
        return out;
    }

    /** For tests: whether entry {@code entry} offers a switch. */
    boolean hasSwitch(int entry) {
        return mSwitches.get(entry).getVisibility() == View.VISIBLE;
    }

    /** For tests: the switch of entry {@code entry}. */
    @NonNull
    MaterialSwitch switchOf(int entry) {
        return mSwitches.get(entry);
    }
}
