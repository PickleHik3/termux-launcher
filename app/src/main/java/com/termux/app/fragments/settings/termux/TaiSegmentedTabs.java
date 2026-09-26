package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.Motion;

/**
 * The Model centre's segmented control (Installed | Chat | Speech, and the 1 | 2 | 3 of the
 * simultaneous-downloads row): a pill track with a thumb that slides under the chosen label. Only
 * the thumb's translation animates, on the app's settle curve, and not at all under reduced
 * motion. Radio semantics for accessibility: each label is a selectable item and the chosen one
 * says so.
 */
public final class TaiSegmentedTabs extends FrameLayout {
    public interface OnSegmentSelectedListener {
        void onSegmentSelected(int index);
    }

    private static final long SLIDE_MS = 320L;

    private final View thumb;
    private final LinearLayout labels;
    private int selected = -1;
    @Nullable private OnSegmentSelectedListener listener;

    public TaiSegmentedTabs(@NonNull Context context) {
        this(context, null);
    }

    public TaiSegmentedTabs(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setBackgroundResource(R.drawable.tai_centre_segment_track);
        int pad = dp(4);
        setPadding(pad, pad, pad, pad);
        thumb = new View(context);
        thumb.setBackgroundResource(R.drawable.tai_centre_segment_thumb);
        addView(thumb, new LayoutParams(0, LayoutParams.MATCH_PARENT));
        labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.HORIZONTAL);
        addView(labels, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setOnSegmentSelectedListener(@Nullable OnSegmentSelectedListener listener) {
        this.listener = listener;
    }

    /** Replaces the labels; keeps the selection when it is still in range. */
    public void setLabels(@NonNull CharSequence... texts) {
        boolean same = labels.getChildCount() == texts.length;
        for (int i = 0; same && i < texts.length; i++) {
            same = texts[i].toString().contentEquals(((TextView) labels.getChildAt(i)).getText());
        }
        if (same) return;
        labels.removeAllViews();
        for (int i = 0; i < texts.length; i++) {
            final int index = i;
            TextView label = new TextView(getContext());
            label.setText(texts[i]);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            label.setClickable(true);
            label.setFocusable(true);
            label.setOnClickListener(view -> {
                if (index == selected) return;
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                select(index, true);
                if (listener != null) listener.onSegmentSelected(index);
            });
            labels.addView(label, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        }
        if (selected >= texts.length) selected = -1;
        applySelection(false);
    }

    public int selectedIndex() {
        return selected;
    }

    public void select(int index, boolean animate) {
        if (index == selected) return;
        selected = index;
        applySelection(animate);
    }

    private void applySelection(boolean animate) {
        int count = labels.getChildCount();
        for (int i = 0; i < count; i++) {
            TextView label = (TextView) labels.getChildAt(i);
            boolean on = i == selected;
            // The selected state is what a screen reader announces as "selected".
            label.setSelected(on);
            label.setTextColor(resolveColor(on ? com.termux.shared.R.attr.termuxColorOnSurface
                : com.termux.shared.R.attr.termuxColorOnSurfaceVariant));
        }
        thumb.setVisibility(selected < 0 ? INVISIBLE : VISIBLE);
        // Before the first layout the width is not known; onLayout places the thumb then.
        if (getWidth() == 0 || count == 0) return;
        float target = thumbOffset();
        if (animate && !TaiMotion.reduced(getContext())) {
            thumb.animate().translationX(target).setDuration(SLIDE_MS).setInterpolator(Motion.settle()).start();
        } else {
            thumb.animate().cancel();
            thumb.setTranslationX(target);
        }
    }

    private float thumbOffset() {
        int count = Math.max(1, labels.getChildCount());
        int inner = getWidth() - getPaddingLeft() - getPaddingRight();
        return Math.max(0, selected) * (inner / (float) count);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // The thumb is one segment wide; sized here, before the children are measured, rather
        // than in onLayout, where changing a child's size would ask for a second layout pass.
        int count = Math.max(1, labels.getChildCount());
        int inner = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        LayoutParams params = (LayoutParams) thumb.getLayoutParams();
        params.width = Math.max(0, inner / count);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            thumb.animate().cancel();
            thumb.setTranslationX(thumbOffset());
        }
    }

    private int resolveColor(int attr) {
        TypedValue value = new TypedValue();
        return getContext().getTheme().resolveAttribute(attr, value, true) ? value.data : 0xFF808080;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
