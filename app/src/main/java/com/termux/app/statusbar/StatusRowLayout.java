package com.termux.app.statusbar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.R;
import com.termux.app.terminal.TerminalWindowBar;

/**
 * The status bar's row: the place's badge and window chips, then the readings. Along a row it
 * runs the chips up to the readings, one {@link StatusRowFitPolicy#CHIPS_READINGS_GAP_DP} short
 * of the first reading's ink, however much of that gap the readings already carry themselves; and
 * when the chips run out of room it stands the readings tight — no dots between them, less room
 * around each — and gives the chips what that frees. A row with room looks exactly as it always
 * did. Standing in a column it is a plain linear layout.
 *
 * <p>Along a row it also keeps clear of what the screen takes from it (#163): its ends step in
 * from the screen's rounded corners, and where it shares a band with the camera it leaves a gap
 * there and lays its children out either side of it. {@link StatusRowKeepOut} works out both from
 * where the row stands at rest; the row only applies the answer, inside its own measure and
 * layout, so nothing here asks for another pass.
 */
public final class StatusRowLayout extends LinearLayout {

    /** Whether the readings are standing tight right now. */
    private boolean mTight;

    /** What the window says about its own shape; null until the host has said. */
    @Nullable private StatusRowKeepOut.Screen mScreen;
    /** The answer the last measure was made for. */
    @NonNull private StatusRowKeepOut.Answer mKeepOut = StatusRowKeepOut.Answer.NONE;
    private int mWidthSpec;
    private int mHeightSpec;
    private boolean mHaveSpecs;

    public StatusRowLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /** For tests and callers that care: whether the readings are standing tight. */
    public boolean isReadingsTight() {
        return mTight;
    }

    /**
     * The window's camera box, corners and the user's cutout choice. Compared first, so the host
     * may hand it over on every insets pass; a change lays the row out again.
     */
    public void setScreenKeepOut(@Nullable StatusRowKeepOut.Screen screen) {
        if (mScreen == null ? screen == null : mScreen.equals(screen)) return;
        mScreen = screen;
        requestLayout();
    }

    /** For tests: the answer the row is laid out with now. */
    @VisibleForTesting
    @NonNull
    StatusRowKeepOut.Answer keepOut() {
        return mKeepOut;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mWidthSpec = widthMeasureSpec;
        mHeightSpec = heightMeasureSpec;
        mHaveSpecs = true;
        measureRow(widthMeasureSpec, heightMeasureSpec);
        View strip = findViewById(R.id.terminal_status_place_content);
        TerminalWindowBar bar = findViewById(R.id.terminal_window_bar);
        boolean horizontal = getOrientation() == HORIZONTAL;
        if (!horizontal || strip == null || bar == null || bar.getVisibility() != VISIBLE) {
            // No chips in a row to make room for: the readings stand as they always have.
            if (setTight(false, strip)) measureRow(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int gap = Math.round(StatusRowFitPolicy.CHIPS_READINGS_GAP_DP
            * getResources().getDisplayMetrics().density);
        int gain = StatusRowFitPolicy.stripShareOf(readingsSavingPx(strip),
            weightOf(strip), totalWeight());
        boolean tight = StatusRowFitPolicy.readingsTight(bar.restingContentWidthPx(),
            bar.getMeasuredWidth(), mTight, gain, bar.getPaddingStart(),
            StatusRowFitPolicy.chipsEndPaddingPx(gap, readingsLeadPx(strip, bar, false)));
        boolean changed = setTight(tight, strip);
        // The end padding is inside the bar, so the row's own split does not move with it, only
        // where the chips stop. The lead is read in the form just chosen, which the measured
        // widths do not show yet.
        int end = StatusRowFitPolicy.chipsEndPaddingPx(gap, readingsLeadPx(strip, bar, mTight));
        changed |= bar.setTrailingGapPx(end);
        if (changed) measureRow(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * The linear layout's own measure, made around the keep-out when there is one: the children
     * are measured into the room the corners and the camera leave, and each flexible child is
     * then given exactly the stretch its side of the gap has for it.
     */
    private void measureRow(int widthMeasureSpec, int heightMeasureSpec) {
        if (!keepOutApplies(widthMeasureSpec)) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int start = Math.min(width, mKeepOut.leftInsetPx);
        int end = Math.max(start, width - mKeepOut.rightInsetPx);
        int gap = Math.max(0, Math.min(end, mKeepOut.gapEndPx)
            - Math.max(start, mKeepOut.gapStartPx));
        super.onMeasure(MeasureSpec.makeMeasureSpec(Math.max(0, end - start - gap),
            MeasureSpec.EXACTLY), heightMeasureSpec);
        View[] children = visualChildren();
        int[] left = new int[children.length];
        int[] widths = new int[children.length];
        flowAroundKeepOut(children, start, end, left, widths);
        for (int i = 0; i < children.length; i++) {
            View child = children[i];
            if (weightOf(child) <= 0f || child.getMeasuredWidth() == widths[i]) continue;
            child.measure(MeasureSpec.makeMeasureSpec(widths[i], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(child.getMeasuredHeight(), MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(width, getMeasuredHeight());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // Where the row stands is known only now. When it asks for a different answer than the
        // measure was made for, the row measures its own children again and lays them out here:
        // its own size does not move, so its parent has nothing to redo.
        StatusRowKeepOut.Answer answer = currentKeepOut();
        if (!answer.equals(mKeepOut)) {
            mKeepOut = answer;
            if (mHaveSpecs) {
                onMeasure(mWidthSpec, mHeightSpec);
                super.onLayout(changed, l, t, r, b);
            }
        }
        if (!mHaveSpecs || !keepOutApplies(mWidthSpec)) return;
        int width = r - l;
        int start = Math.min(width, mKeepOut.leftInsetPx);
        int end = Math.max(start, width - mKeepOut.rightInsetPx);
        View[] children = visualChildren();
        int[] left = new int[children.length];
        int[] widths = new int[children.length];
        flowAroundKeepOut(children, start, end, left, widths);
        for (int i = 0; i < children.length; i++) {
            View child = children[i];
            int top = child.getTop();
            child.layout(left[i], top, left[i] + child.getMeasuredWidth(),
                top + child.getMeasuredHeight());
        }
    }

    /** Whether the row is laid out around a keep-out rather than as a plain linear layout. */
    private boolean keepOutApplies(int widthMeasureSpec) {
        return getOrientation() == HORIZONTAL && !mKeepOut.isNone()
            && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED;
    }

    /** The answer for where the row stands at rest in its window, translations left out. */
    @NonNull
    private StatusRowKeepOut.Answer currentKeepOut() {
        if (mScreen == null || getOrientation() != HORIZONTAL) return StatusRowKeepOut.Answer.NONE;
        int x = 0;
        int y = 0;
        View view = this;
        while (true) {
            x += view.getLeft();
            y += view.getTop();
            ViewParent parent = view.getParent();
            if (!(parent instanceof View)) break;
            View up = (View) parent;
            x -= up.getScrollX();
            y -= up.getScrollY();
            view = up;
        }
        return StatusRowKeepOut.answer(mScreen, view.getWidth(), view.getHeight(), x, y,
            x + getWidth(), y + getHeight());
    }

    /** The children that take room, left to right on screen (reversed in a right-to-left row). */
    @NonNull
    private View[] visualChildren() {
        int count = 0;
        for (int i = 0; i < getChildCount(); i++) if (getChildAt(i).getVisibility() != GONE) count++;
        View[] children = new View[count];
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int at = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(rtl ? getChildCount() - 1 - i : i);
            if (child.getVisibility() != GONE) children[at++] = child;
        }
        return children;
    }

    private void flowAroundKeepOut(@NonNull View[] children, int start, int end,
                                   @NonNull int[] outLeft, @NonNull int[] outWidth) {
        int n = children.length;
        int[] marginLeft = new int[n];
        int[] marginRight = new int[n];
        int[] widths = new int[n];
        float[] weights = new float[n];
        for (int i = 0; i < n; i++) {
            View child = children[i];
            ViewGroup.LayoutParams params = child.getLayoutParams();
            if (params instanceof MarginLayoutParams) {
                marginLeft[i] = ((MarginLayoutParams) params).leftMargin;
                marginRight[i] = ((MarginLayoutParams) params).rightMargin;
            }
            widths[i] = child.getMeasuredWidth();
            weights[i] = weightOf(child);
        }
        StatusRowKeepOut.flow(marginLeft, widths, marginRight, weights, start, end,
            mKeepOut.gapStartPx, mKeepOut.gapEndPx, outLeft, outWidth);
    }

    /** Stands every reading after the strip tight or not; whether anything changed. */
    private boolean setTight(boolean tight, @Nullable View strip) {
        if (mTight == tight) return false;
        mTight = tight;
        int index = strip == null ? -1 : indexOfChild(strip);
        for (int i = index + 1; i < getChildCount(); i++) applyTight(getChildAt(i), tight);
        return true;
    }

    private static void applyTight(@NonNull View view, boolean tight) {
        if (view instanceof StatusBarWidgetView) {
            ((StatusBarWidgetView) view).setTight(tight);
        } else if (view instanceof MaterialDotSeparatorView) {
            ((MaterialDotSeparatorView) view).setTucked(tight);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyTight(group.getChildAt(i), tight);
        }
    }

    /** What standing tight saves across every visible reading after the strip. */
    private int readingsSavingPx(@NonNull View strip) {
        int saving = 0;
        for (int i = indexOfChild(strip) + 1; i < getChildCount(); i++) {
            saving += tightSavingPx(getChildAt(i));
        }
        return saving;
    }

    private static int tightSavingPx(@NonNull View view) {
        if (view.getVisibility() == GONE) return 0;
        if (view instanceof StatusBarWidgetView) return ((StatusBarWidgetView) view).tightSavingPx();
        if (view instanceof MaterialDotSeparatorView) {
            return ((MaterialDotSeparatorView) view).widthPx(false);
        }
        if (!(view instanceof ViewGroup)) return 0;
        ViewGroup group = (ViewGroup) view;
        int saving = 0;
        for (int i = 0; i < group.getChildCount(); i++) saving += tightSavingPx(group.getChildAt(i));
        return saving;
    }

    private static float weightOf(@NonNull View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        return params instanceof LayoutParams ? ((LayoutParams) params).weight : 0f;
    }

    private float totalWeight() {
        float total = 0f;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) total += weightOf(child);
        }
        return total;
    }

    /**
     * The space from the end of the chips' viewport to the first reading's ink, with the readings
     * {@code tight} or not: whatever the strip keeps after the bar, then every inkless thing
     * before the first reading. Negative when the chips are followed by no reading at all — only
     * by the row's end, or by the centring spacer the Widgets place stands the stats on.
     */
    int readingsLeadPx(@NonNull View strip, @NonNull View bar, boolean tight) {
        int lead = strip.getPaddingEnd() + marginEnd(bar);
        int index = indexOfChild(strip);
        if (index < 0) return -1;
        for (int i = index + 1; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int ink = inkLead(child, tight);
            if (ink >= 0) return lead + ink;
            if (!(child instanceof ViewGroup)) return -1;
            lead += footprint(child, tight);
        }
        return -1;
    }

    /** How far into {@code view}, margin included, its first ink starts; negative if it has none. */
    private static int inkLead(@NonNull View view, boolean tight) {
        if (view.getVisibility() == GONE) return -1;
        if (view instanceof StatusBarWidgetView) {
            return marginStart(view) + ((StatusBarWidgetView) view).leadingPaddingPx(tight);
        }
        if (view instanceof MaterialDotSeparatorView) {
            return ((MaterialDotSeparatorView) view).widthPx(tight) > 0 ? marginStart(view) : -1;
        }
        if (!(view instanceof ViewGroup)) return -1;
        ViewGroup group = (ViewGroup) view;
        int lead = marginStart(view) + view.getPaddingStart();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int ink = inkLead(child, tight);
            if (ink >= 0) return lead + ink;
            lead += footprint(child, tight);
        }
        return -1;
    }

    /** The room an inkless {@code view} takes along the row, margins included. */
    private static int footprint(@NonNull View view, boolean tight) {
        int width = view instanceof MaterialDotSeparatorView
            ? ((MaterialDotSeparatorView) view).widthPx(tight) : view.getMeasuredWidth();
        return marginStart(view) + width + marginEnd(view);
    }

    private static int marginStart(@NonNull View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        return params instanceof MarginLayoutParams
            ? ((MarginLayoutParams) params).getMarginStart() : 0;
    }

    private static int marginEnd(@NonNull View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        return params instanceof MarginLayoutParams
            ? ((MarginLayoutParams) params).getMarginEnd() : 0;
    }
}
