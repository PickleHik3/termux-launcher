package com.termux.app.dock;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.google.android.material.color.MaterialColors;
import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Taskbar's run strip (spec "Taskbar"), drawn the way a Waybar workspace list is: a thin row
 * of numbered entries, the window in front lit in the accent, {@code +} at the row's end.
 *
 * <p>Dumb: {@link RunStripModel} builds the entries and the host hands them over with
 * {@link #setEntries}; the strip hides itself while there are none. Its background is clear: the
 * thin glass it floats on is the dock's to build. A tap reports the entry's chip index, which the
 * host routes as a tap on that window chip, or {@code +}. Nothing repaints at rest.</p>
 */
public final class RunStripView extends View {

    /** A tap on an entry. */
    public interface Listener {
        /** The entry for chip {@code chipIndex}, as the window bar counts chips. */
        void onWindowTapped(int chipIndex);

        /** The {@code +} at the strip's end. */
        void onNewWindowTapped();
    }

    /** The strip's height: the row of entries and the touch room around them. */
    public static final float HEIGHT_DP = 44f;
    static final float ENTRY_HEIGHT_DP = 30f;
    static final float ENTRY_RADIUS_DP = 9f;
    static final float BADGE_DP = 20f;
    static final float BADGE_RADIUS_DP = 6f;
    static final float SIDE_PAD_DP = 6f;
    static final float ENTRY_GAP_DP = 4f;
    static final float ENTRY_START_PAD_DP = 5f;
    static final float ENTRY_END_PAD_DP = 10f;
    static final float LABEL_GAP_DP = 6f;
    /** The most room a label takes; past it the label ellipsizes. */
    static final float LABEL_MAX_DP = 104f;
    /** Below this a label says nothing useful, so the entries show their numbers only. */
    static final float LABEL_MIN_DP = 28f;
    static final float LABEL_SP = 13f;
    static final float BADGE_SP = 11f;
    /** The attention dot on a badge's corner. */
    static final float DOT_DP = 7f;
    /** A minimised float's mark after its label. */
    static final String MINIMISED_MARK = "▾";

    /** The lit entry's fill and rim, and a resting entry's ink, as alphas of their colours. */
    static final int LIT_FILL_ALPHA = 46;
    static final int LIT_RIM_ALPHA = 115;
    static final int BADGE_REST_ALPHA = 36;
    static final int REST_INK_ALPHA = 200;
    static final int PLUS_INK_ALPHA = 160;

    private final float mDensity;
    private final int mTouchSlop;
    private final TextPaint mLabelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mBadgePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final StripAccessibility mAccessibility;

    @Nullable private Listener mListener;
    @NonNull private List<RunStripModel.Entry> mEntries = Collections.emptyList();
    /** Each entry's left and right edge and its fitted label, from the last relayout. */
    @NonNull private float[] mLefts = new float[0];
    @NonNull private float[] mRights = new float[0];
    @NonNull private CharSequence[] mLabels = new CharSequence[0];
    private int mLaidOutWidth = -1;

    @ColorInt private int mAccent;
    @ColorInt private int mOnAccent;
    @ColorInt private int mOnSurface;
    @ColorInt private int mDisplay;
    @ColorInt private int mFloat;
    @ColorInt private int mAttention;

    private int mDownEntry = -1;
    private float mDownX;
    private float mDownY;

    public RunStripView(@NonNull Context context) {
        super(context);
        mDensity = getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mLabelPaint.setTypeface(Typeface.MONOSPACE);
        mBadgePaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        mBadgePaint.setTextAlign(Paint.Align.CENTER);
        mRim.setStyle(Paint.Style.STROKE);
        mRim.setStrokeWidth(Math.max(1f, Math.round(mDensity)));
        applyTextSizes();
        readThemeColors();
        mAccessibility = new StripAccessibility(this);
        ViewCompat.setAccessibilityDelegate(this, mAccessibility);
        setVisibility(GONE);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /**
     * The strip's entries ({@link RunStripModel#build}). Shown while there are any; a list equal to
     * the last one changes nothing.
     */
    public void setEntries(@NonNull List<RunStripModel.Entry> entries) {
        int visibility = RunStripModel.isShown(entries) ? VISIBLE : GONE;
        if (getVisibility() != visibility) setVisibility(visibility);
        if (entries.equals(mEntries)) return;
        mEntries = new ArrayList<>(entries);
        relayout(getWidth());
        invalidate();
        mAccessibility.invalidateRoot();
    }

    /** The entries shown now, for tests. */
    @NonNull List<RunStripModel.Entry> entries() {
        return mEntries;
    }

    /** The theme's colours read again after a scheme change made without recreating the activity. */
    public void refreshTheme() {
        readThemeColors();
        invalidate();
    }

    // ---------------------------------------------------------------- layout

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width,
            resolveSize(Math.round(HEIGHT_DP * mDensity), heightMeasureSpec));
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        relayout(w);
    }

    @Override protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyTextSizes();
        readThemeColors();
        relayout(getWidth());
        invalidate();
    }

    /**
     * Places the entries for a strip {@code width} wide: windows from the start edge, {@code +} at
     * the end. Labels share what the numbers leave, each up to {@link #LABEL_MAX_DP}; when that
     * share falls below {@link #LABEL_MIN_DP} the entries show their numbers alone. Text is
     * measured here, never in a draw.
     */
    private void relayout(int width) {
        mLaidOutWidth = width;
        int count = mEntries.size();
        mLefts = new float[count];
        mRights = new float[count];
        mLabels = new CharSequence[count];
        if (count == 0 || width <= 0) return;
        float side = SIDE_PAD_DP * mDensity;
        float gap = ENTRY_GAP_DP * mDensity;
        float bare = (ENTRY_START_PAD_DP + BADGE_DP + ENTRY_START_PAD_DP) * mDensity;
        float withLabel = (ENTRY_START_PAD_DP + BADGE_DP + LABEL_GAP_DP + ENTRY_END_PAD_DP)
            * mDensity;
        int windows = 0;
        for (RunStripModel.Entry entry : mEntries)
            if (entry.kind != RunStripModel.Kind.NEW_WINDOW) windows++;
        float fixed = 2f * side + count * bare + Math.max(0, count - 1) * gap
            + windows * (withLabel - bare);
        float share = windows == 0 ? 0f : (width - fixed) / windows;
        float labelRoom = Math.min(LABEL_MAX_DP * mDensity, share);
        boolean labels = labelRoom >= LABEL_MIN_DP * mDensity;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float x = side;
        for (int i = 0; i < count; i++) {
            RunStripModel.Entry entry = mEntries.get(i);
            CharSequence label = "";
            if (labels && entry.kind != RunStripModel.Kind.NEW_WINDOW) {
                String text = entry.minimised ? entry.label + " " + MINIMISED_MARK : entry.label;
                label = TextUtils.ellipsize(text, mLabelPaint, labelRoom, TextUtils.TruncateAt.END);
            }
            float entryWidth = label.length() == 0 ? bare
                : withLabel + mLabelPaint.measureText(label, 0, label.length());
            float left = entry.kind == RunStripModel.Kind.NEW_WINDOW
                ? Math.max(x, width - side - entryWidth) : x;
            mLabels[i] = label;
            mLefts[i] = rtl ? width - left - entryWidth : left;
            mRights[i] = mLefts[i] + entryWidth;
            x = left + entryWidth + gap;
        }
    }

    /** The entry under {@code x}, or -1 between entries. */
    int entryAt(float x) {
        for (int i = 0; i < mLefts.length; i++) {
            if (x >= mLefts[i] && x < mRights[i]) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- drawing

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mEntries.isEmpty()) return;
        if (mLaidOutWidth != getWidth()) relayout(getWidth());
        float centerY = getHeight() / 2f;
        float entryHalf = ENTRY_HEIGHT_DP * mDensity / 2f;
        float entryRadius = ENTRY_RADIUS_DP * mDensity;
        float badge = BADGE_DP * mDensity;
        float badgeRadius = BADGE_RADIUS_DP * mDensity;
        float startPad = ENTRY_START_PAD_DP * mDensity;
        float labelGap = LABEL_GAP_DP * mDensity;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float half = mRim.getStrokeWidth() / 2f;
        for (int i = 0; i < mEntries.size() && i < mLefts.length; i++) {
            RunStripModel.Entry entry = mEntries.get(i);
            float left = mLefts[i];
            float right = mRights[i];
            if (entry.current) {
                mRect.set(left + half, centerY - entryHalf + half, right - half,
                    centerY + entryHalf - half);
                mFill.setColor(ColorUtils.setAlphaComponent(mAccent, LIT_FILL_ALPHA));
                canvas.drawRoundRect(mRect, entryRadius, entryRadius, mFill);
                mRim.setColor(ColorUtils.setAlphaComponent(mAccent, LIT_RIM_ALPHA));
                canvas.drawRoundRect(mRect, entryRadius, entryRadius, mRim);
            }
            float badgeLeft = rtl ? right - startPad - badge : left + startPad;
            mRect.set(badgeLeft, centerY - badge / 2f, badgeLeft + badge, centerY + badge / 2f);
            if (entry.kind != RunStripModel.Kind.NEW_WINDOW) {
                mFill.setColor(badgeFill(entry));
                canvas.drawRoundRect(mRect, badgeRadius, badgeRadius, mFill);
            }
            mBadgePaint.setColor(entry.kind == RunStripModel.Kind.NEW_WINDOW
                ? ColorUtils.setAlphaComponent(mOnSurface, PLUS_INK_ALPHA)
                : entry.current ? mOnAccent : mOnSurface);
            canvas.drawText(entry.number, mRect.centerX(), baseline(mBadgePaint, centerY),
                mBadgePaint);
            if (entry.attention) {
                float dot = DOT_DP * mDensity;
                float cx = rtl ? mRect.left + dot / 3f : mRect.right - dot / 3f;
                mFill.setColor(mAttention);
                canvas.drawCircle(cx, mRect.top + dot / 3f, dot / 2f, mFill);
            }
            CharSequence label = i < mLabels.length ? mLabels[i] : "";
            if (label.length() > 0) {
                mLabelPaint.setColor(entry.current ? mOnSurface
                    : ColorUtils.setAlphaComponent(mOnSurface, REST_INK_ALPHA));
                float width = mLabelPaint.measureText(label, 0, label.length());
                float labelLeft = rtl ? mRect.left - labelGap - width : mRect.right + labelGap;
                canvas.drawText(label, 0, label.length(), labelLeft,
                    baseline(mLabelPaint, centerY), mLabelPaint);
            }
        }
    }

    /** The badge's fill: the accent when lit, otherwise the window kind's own tint. */
    @ColorInt
    private int badgeFill(@NonNull RunStripModel.Entry entry) {
        if (entry.current) return mAccent;
        switch (entry.kind) {
            case DISPLAY: return ColorUtils.setAlphaComponent(mDisplay, 2 * BADGE_REST_ALPHA);
            case HOME_FLOAT: return ColorUtils.setAlphaComponent(mFloat, 2 * BADGE_REST_ALPHA);
            default: return ColorUtils.setAlphaComponent(mOnSurface, BADGE_REST_ALPHA);
        }
    }

    private void readThemeColors() {
        Context context = getContext();
        mAccent = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        mOnAccent = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnPrimary,
            ContextCompat.getColor(context, R.color.termux_on_primary));
        mOnSurface = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mDisplay = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorSecondary,
            mAccent);
        mFloat = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorTertiary,
            mAccent);
        // The same colour the window chips ring a window asking for the user in.
        mAttention = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorError,
            ContextCompat.getColor(context, R.color.termux_error));
    }

    private void applyTextSizes() {
        mLabelPaint.setTextSize(sp(LABEL_SP));
        // The badge's number shrinks to its box rather than spilling out at a raised font scale.
        mBadgePaint.setTextSize(Math.min(sp(BADGE_SP), BADGE_DP * mDensity * 0.6f));
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
            getResources().getDisplayMetrics());
    }

    private static float baseline(@NonNull Paint paint, float centerY) {
        return centerY - (paint.ascent() + paint.descent()) / 2f;
    }

    // ---------------------------------------------------------------- touch

    /** A tap on an entry reports it; a press between entries is not taken. */
    @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownEntry = entryAt(event.getX());
                mDownX = event.getX();
                mDownY = event.getY();
                return mDownEntry >= 0;
            case MotionEvent.ACTION_MOVE:
                if (mDownEntry >= 0 && Math.hypot(event.getX() - mDownX, event.getY() - mDownY)
                    > mTouchSlop) mDownEntry = -1;
                return true;
            case MotionEvent.ACTION_UP: {
                int entry = entryAt(event.getX());
                int down = mDownEntry;
                mDownEntry = -1;
                if (down >= 0 && entry == down && event.getY() >= 0f
                    && event.getY() < getHeight()) {
                    performClick();
                    report(entry);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                mDownEntry = -1;
                return true;
            default:
                return mDownEntry >= 0;
        }
    }

    @Override public boolean performClick() {
        return super.performClick();
    }

    private void report(int index) {
        if (mListener == null || index < 0 || index >= mEntries.size()) return;
        RunStripModel.Entry entry = mEntries.get(index);
        if (entry.kind == RunStripModel.Kind.NEW_WINDOW) mListener.onNewWindowTapped();
        else mListener.onWindowTapped(entry.chipIndex);
    }

    @Override protected boolean dispatchHoverEvent(@NonNull MotionEvent event) {
        return mAccessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    /** What a screen reader says for an entry: its label, then minimised and attention. */
    @NonNull
    private String spokenFor(@NonNull RunStripModel.Entry entry) {
        String spoken = entry.spokenLabel;
        if (entry.minimised) spoken = getContext().getString(R.string.dock_run_strip_minimised,
            spoken);
        if (entry.attention) spoken = getContext().getString(R.string.dock_run_strip_attention,
            spoken);
        return spoken;
    }

    /** Each entry is a node a screen reader can step to and tap. */
    private final class StripAccessibility extends ExploreByTouchHelper {

        StripAccessibility(@NonNull View host) {
            super(host);
        }

        @Override protected int getVirtualViewAt(float x, float y) {
            int entry = entryAt(x);
            return entry >= 0 ? entry : INVALID_ID;
        }

        @Override protected void getVisibleVirtualViews(List<Integer> ids) {
            for (int i = 0; i < mEntries.size() && i < mLefts.length; i++) ids.add(i);
        }

        @Override protected void onPopulateNodeForVirtualView(int id,
                                                              @NonNull AccessibilityNodeInfoCompat node) {
            Rect bounds = new Rect();
            if (id < mEntries.size() && id < mLefts.length) {
                RunStripModel.Entry entry = mEntries.get(id);
                node.setContentDescription(spokenFor(entry));
                node.setSelected(entry.current);
                bounds.set(Math.round(mLefts[id]), 0, Math.round(mRights[id]), getHeight());
            } else {
                node.setContentDescription("");
            }
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            node.setClickable(true);
            if (bounds.isEmpty()) bounds.set(0, 0, 1, 1);
            node.setBoundsInParent(bounds);
        }

        @Override protected boolean onPerformActionForVirtualView(int id, int action,
                                                                  @Nullable Bundle arguments) {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK || id >= mEntries.size()) {
                return false;
            }
            report(id);
            return true;
        }
    }
}
