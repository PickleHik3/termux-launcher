package com.termux.app.dock;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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
import com.termux.shared.termux.font.NerdFontSpans;

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
    /**
     * A minimised float's mark after its label: a small downward triangle, drawn as a shape so it
     * never depends on a face having the character.
     */
    static final float MINIMISED_MARK_DP = 6f;
    /** Room between the process glyph and the label, and between the label and the mark. */
    static final float INNER_GAP_DP = 4f;

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
    /** The process glyphs, in the bundled Nerd Font symbols face; null when it is missing. */
    @Nullable private final TextPaint mGlyphPaint;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mMark = new Path();
    private final RectF mRect = new RectF();
    private final StripAccessibility mAccessibility;

    @Nullable private Listener mListener;
    @NonNull private List<RunStripModel.Entry> mEntries = Collections.emptyList();
    /**
     * Each entry's left and right edge, its fitted label, whether its glyph and its minimised
     * mark are drawn, from the last relayout.
     */
    @NonNull private float[] mLefts = new float[0];
    @NonNull private float[] mRights = new float[0];
    @NonNull private CharSequence[] mLabels = new CharSequence[0];
    @NonNull private boolean[] mGlyphs = new boolean[0];
    @NonNull private boolean[] mMarks = new boolean[0];
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
        // The chips' process glyphs are Nerd Font symbols no system face has; only the bundled
        // symbols face draws them, and without it the strip leaves them out.
        Typeface symbols = NerdFontSpans.typeface(context);
        if (symbols != null) {
            mGlyphPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            mGlyphPaint.setTypeface(symbols);
        } else {
            mGlyphPaint = null;
        }
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
     * the end. Each label is the entry's distinguishing part ({@link RunStripModel.Entry#shortLabel}),
     * so four shells in four directories read as their directories and not as a shared lead.
     * Labels share what the numbers leave ({@link RunStripModel#labelRooms}), each up to
     * {@link #LABEL_MAX_DP}: a short one keeps its whole text and a long one is cut at its end;
     * when the long ones' share falls below {@link #LABEL_MIN_DP} the entries show their numbers
     * alone. The badge is always drawn. The process glyph goes first when the room runs short,
     * before a character of the label does. Text is measured here, never in a draw.
     */
    private void relayout(int width) {
        mLaidOutWidth = width;
        int count = mEntries.size();
        mLefts = new float[count];
        mRights = new float[count];
        mLabels = new CharSequence[count];
        mGlyphs = new boolean[count];
        mMarks = new boolean[count];
        if (count == 0 || width <= 0) return;
        float side = SIDE_PAD_DP * mDensity;
        float gap = ENTRY_GAP_DP * mDensity;
        float inner = INNER_GAP_DP * mDensity;
        float markWidth = MINIMISED_MARK_DP * mDensity + inner;
        float bare = (ENTRY_START_PAD_DP + BADGE_DP + ENTRY_START_PAD_DP) * mDensity;
        float withLabel = (ENTRY_START_PAD_DP + BADGE_DP + LABEL_GAP_DP + ENTRY_END_PAD_DP)
            * mDensity;
        // What each label wants: its text, its glyph and its minimised mark.
        float[] demands = new float[count];
        float[] glyphWidths = new float[count];
        int labelled = 0;
        for (int i = 0; i < count; i++) {
            RunStripModel.Entry entry = mEntries.get(i);
            if (entry.kind == RunStripModel.Kind.NEW_WINDOW || entry.shortLabel.isEmpty()) continue;
            glyphWidths[i] = glyphWidth(entry);
            demands[i] = glyphWidths[i] + mLabelPaint.measureText(entry.shortLabel)
                + (entry.minimised ? markWidth : 0f);
            labelled++;
        }
        float fixed = 2f * side + count * bare + Math.max(0, count - 1) * gap
            + labelled * (withLabel - bare);
        float[] rooms = RunStripModel.labelRooms(demands, width - fixed,
            LABEL_MAX_DP * mDensity, LABEL_MIN_DP * mDensity);
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float x = side;
        for (int i = 0; i < count; i++) {
            RunStripModel.Entry entry = mEntries.get(i);
            CharSequence label = "";
            float labelWidth = 0f;
            if (rooms[i] > 0f) {
                float room = rooms[i] - (entry.minimised ? markWidth : 0f);
                float text = mLabelPaint.measureText(entry.shortLabel);
                // The glyph goes before a character of the label does.
                mGlyphs[i] = glyphWidths[i] > 0f && glyphWidths[i] + text <= room;
                if (mGlyphs[i]) room -= glyphWidths[i];
                label = TextUtils.ellipsize(entry.shortLabel, mLabelPaint, Math.max(0f, room),
                    TextUtils.TruncateAt.END);
                mMarks[i] = entry.minimised;
                if (label.length() == 0) {
                    mGlyphs[i] = false;
                    mMarks[i] = false;
                } else {
                    labelWidth = (mGlyphs[i] ? glyphWidths[i] : 0f)
                        + mLabelPaint.measureText(label, 0, label.length())
                        + (mMarks[i] ? markWidth : 0f);
                }
            }
            float entryWidth = labelWidth <= 0f ? bare : withLabel + labelWidth;
            float left = entry.kind == RunStripModel.Kind.NEW_WINDOW
                ? Math.max(x, width - side - entryWidth) : x;
            mLabels[i] = label;
            mLefts[i] = rtl ? width - left - entryWidth : left;
            mRights[i] = mLefts[i] + entryWidth;
            x = left + entryWidth + gap;
        }
    }

    /** The process glyph's width with the gap after it, or zero when it is not drawn. */
    private float glyphWidth(@NonNull RunStripModel.Entry entry) {
        if (mGlyphPaint == null || entry.glyph.isEmpty()) return 0f;
        return mGlyphPaint.measureText(entry.glyph) + INNER_GAP_DP * mDensity;
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
        float inner = INNER_GAP_DP * mDensity;
        float mark = MINIMISED_MARK_DP * mDensity;
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
                int ink = entry.current ? mOnSurface
                    : ColorUtils.setAlphaComponent(mOnSurface, REST_INK_ALPHA);
                // From the badge toward the end edge: glyph, label, mark.
                float x = rtl ? mRect.left - labelGap : mRect.right + labelGap;
                if (mGlyphs[i] && mGlyphPaint != null) {
                    mGlyphPaint.setColor(ink);
                    float width = mGlyphPaint.measureText(entry.glyph);
                    canvas.drawText(entry.glyph, rtl ? x - width : x,
                        baseline(mGlyphPaint, centerY), mGlyphPaint);
                    x = rtl ? x - width - inner : x + width + inner;
                }
                mLabelPaint.setColor(ink);
                float width = mLabelPaint.measureText(label, 0, label.length());
                canvas.drawText(label, 0, label.length(), rtl ? x - width : x,
                    baseline(mLabelPaint, centerY), mLabelPaint);
                x = rtl ? x - width - inner : x + width + inner;
                if (mMarks[i]) drawMinimisedMark(canvas, rtl ? x - mark : x, centerY, mark, ink);
            }
        }
    }

    /** A minimised float's mark: a small triangle pointing down, {@code size} wide at {@code left}. */
    private void drawMinimisedMark(@NonNull Canvas canvas, float left, float centerY, float size,
                                   @ColorInt int ink) {
        float half = size / 2f;
        mMark.rewind();
        mMark.moveTo(left, centerY - half * 0.6f);
        mMark.lineTo(left + size, centerY - half * 0.6f);
        mMark.lineTo(left + half, centerY + half * 0.6f);
        mMark.close();
        mFill.setColor(ink);
        canvas.drawPath(mMark, mFill);
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
        if (mGlyphPaint != null) mGlyphPaint.setTextSize(sp(LABEL_SP));
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
