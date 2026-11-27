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

    /**
     * How far each edge of the bar is drawn out past where it is laid out while a crossing eases
     * its Margin (BarMarginEase): each child nearer an edge is drawn moved with that edge, at its
     * own size. Drawn only, never laid out; 0 at rest, where drawing is untouched.
     */
    private float mEdgeGrowPx;

    /** Sets {@link #mEdgeGrowPx}; a redraw of this row only, and only when it moves. */
    public void setEdgeGrow(float growPx) {
        if (growPx == mEdgeGrowPx) return;
        mEdgeGrowPx = growPx;
        invalidate();
    }

    @Override
    protected boolean drawChild(@NonNull android.graphics.Canvas canvas, @NonNull View child,
                                long drawingTime) {
        float dx = mEdgeGrowPx == 0f ? 0f : com.termux.app.place.BarMarginEase.contentShiftPx(
            child.getLeft(), child.getRight(), getWidth(), mEdgeGrowPx);
        if (dx == 0f) return super.drawChild(canvas, child, drawingTime);
        int save = canvas.save();
        try {
            canvas.translate(dx, 0f);
            return super.drawChild(canvas, child, drawingTime);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    /** Whether the readings are standing tight right now. */
    private boolean mTight;

    /** What the window says about its own shape; null until the host has said. */
    @Nullable private StatusRowKeepOut.Screen mScreen;
    /** The answer the last measure was made for. */
    @NonNull private StatusRowKeepOut.Answer mKeepOut = StatusRowKeepOut.Answer.NONE;
    /**
     * Whether the last measure parted the row at the camera ({@link StatusRowKeepOut#partAtGap}):
     * the chips on one side, the readings on the other. Then {@link #mPartAnswer} holds where each
     * stands, and the layout places {@link #mPartStrip} and {@link #mPartGroup} there.
     */
    private boolean mParted;
    private final int[] mPartAnswer = new int[StatusRowKeepOut.PART_SIZE];
    @Nullable private View mPartStrip;
    @Nullable private View mPartGroup;
    /** {@link #partAtGap}'s scratch: each reading's width with its margins, and which are dots. */
    @NonNull private int[] mPartItemWidth = new int[16];
    @NonNull private boolean[] mPartItemMark = new boolean[16];
    private final View[] mPartChildren = new View[2];
    private final int[] mPartWidths = new int[2];
    /** Whether the last measure laid the children out by {@link StatusRowKeepOut#flow}. */
    private boolean mFlowing;
    /** The least the chip strip may be given, from the last measure; 0 with no chips to keep. */
    private int mStripMinPx;
    /** Bit {@code i}: child {@code i} is hidden because the row had no room for it. */
    private int mDroppedMask;
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

    /**
     * The window was laid out. The row reads where it stands only in its own layout pass, and a
     * pane that moves under it (Full screen hiding the system bar lifts the status pane from under
     * it to the screen's top) leaves the row's own frame as it was, so that pass never comes: the
     * row would keep the answer it had below the bar and run into the corners and the camera.
     * Asked after every window layout, it lays itself out again only when the answer moved.
     */
    private final android.view.ViewTreeObserver.OnGlobalLayoutListener mWindowLaidOut =
        this::onWindowLaidOut;

    @VisibleForTesting
    void onWindowLaidOut() {
        if (!mHaveSpecs || isLayoutRequested()) return;
        if (!currentKeepOut().equals(mKeepOut)) requestLayout();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(mWindowLaidOut);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnGlobalLayoutListener(mWindowLaidOut);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mWidthSpec = widthMeasureSpec;
        mHeightSpec = heightMeasureSpec;
        mHaveSpecs = true;
        measureRow(widthMeasureSpec, heightMeasureSpec);
        View strip = stripSlot();
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
        // Home's float chips stand in the same slot and stop at the same place.
        TerminalWindowBar homeBar = findViewById(R.id.terminal_home_float_bar);
        if (homeBar != null) changed |= homeBar.setTrailingGapPx(end);
        if (changed) measureRow(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * The linear layout's own measure, made around the keep-out when there is one: the children
     * are measured into the room the corners and the camera leave, and each flexible child is
     * then given exactly the stretch its side of the gap has for it.
     */
    private void measureRow(int widthMeasureSpec, int heightMeasureSpec) {
        // Every reading is measured as it stands before the row decides how many fit.
        mParted = false;
        StatusReadingsCluster cluster = readingsCluster();
        if (cluster != null) cluster.setKept(StatusReadingsCluster.ALL, this);
        boolean keepOut = keepOutApplies(widthMeasureSpec);
        if (!keepOut) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            mStripMinPx = stripMinPx();
            // A plain row is laid out as a linear layout unless that would squeeze the strip
            // below what shows one whole chip: then the flow below takes over, once.
            View strip = stripSlot();
            mFlowing = mStripMinPx > 0 && strip != null && strip.getVisibility() != GONE
                && getOrientation() == HORIZONTAL
                && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
                && strip.getMeasuredWidth() < mStripMinPx;
            if (!mFlowing) return;
        }
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int start = Math.min(width, mKeepOut.leftInsetPx);
        int end = Math.max(start, width - mKeepOut.rightInsetPx);
        int gap = Math.max(0, Math.min(end, mKeepOut.gapEndPx)
            - Math.max(start, mKeepOut.gapStartPx));
        if (keepOut) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(Math.max(0, end - start - gap),
                MeasureSpec.EXACTLY), heightMeasureSpec);
            mStripMinPx = stripMinPx();
            mFlowing = true;
            if (partAtGap(start, end)) {
                setMeasuredDimension(width, getMeasuredHeight());
                return;
            }
        }
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
        if (!mHaveSpecs || !mFlowing) {
            applyDropped(null, null);
            return;
        }
        if (mParted && mPartStrip != null && mPartGroup != null) {
            layOutParted(mPartStrip, mPartGroup);
            return;
        }
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
            boolean dropped = StatusRowKeepOut.isDropped(child.getMeasuredWidth(), weightOf(child),
                widths[i]);
            child.layout(left[i], top, left[i] + (dropped ? 0 : child.getMeasuredWidth()),
                top + child.getMeasuredHeight());
        }
        applyDropped(children, widths);
    }

    /**
     * Hides the children the flow dropped and shows again those it no longer drops. Invisible,
     * not gone: that asks for no layout, and it is undone only for what this row hid.
     */
    private void applyDropped(@Nullable View[] children, @Nullable int[] widths) {
        int mask = 0;
        if (children != null && widths != null) {
            for (int i = 0; i < children.length; i++) {
                if (StatusRowKeepOut.isDropped(children[i].getMeasuredWidth(),
                    weightOf(children[i]), widths[i])) {
                    mask |= 1 << indexOfChild(children[i]);
                }
            }
        }
        if (mask == mDroppedMask) return;
        for (int i = 0; i < getChildCount() && i < 32; i++) {
            View child = getChildAt(i);
            boolean now = (mask & (1 << i)) != 0;
            boolean before = (mDroppedMask & (1 << i)) != 0;
            if (now && child.getVisibility() == VISIBLE) child.setVisibility(INVISIBLE);
            else if (!now && before && child.getVisibility() == INVISIBLE) {
                child.setVisibility(VISIBLE);
            }
        }
        mDroppedMask = mask;
    }

    /**
     * The least the chip strip may be given, from the badge and the front chip at their own
     * width: the badge is measured unconstrained, since the strip it sits in may be squeezed and
     * would hand it a cut-down width. 0 when the strip is not a row of chips.
     */
    private int stripMinPx() {
        View strip = findViewById(R.id.terminal_status_place_content);
        TerminalWindowBar bar = findViewById(R.id.terminal_window_bar);
        if (!(strip instanceof ViewGroup) || bar == null || bar.getVisibility() != VISIBLE
            || getOrientation() != HORIZONTAL
            || ((LinearLayout) strip).getOrientation() != HORIZONTAL) {
            return 0;
        }
        int badgeLead = 0;
        int badgeWidth = 0;
        ViewGroup group = (ViewGroup) strip;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == bar || child.getVisibility() == GONE || weightOf(child) > 0f) continue;
            ViewGroup.LayoutParams params = child.getLayoutParams();
            if (params instanceof MarginLayoutParams) {
                badgeLead += ((MarginLayoutParams) params).leftMargin
                    + ((MarginLayoutParams) params).rightMargin;
            }
            child.measure(getChildMeasureSpec(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0, params.width),
                MeasureSpec.makeMeasureSpec(child.getMeasuredHeight(), MeasureSpec.EXACTLY));
            badgeWidth += child.getMeasuredWidth();
        }
        int barChrome = bar.getPaddingLeft() + bar.getPaddingRight();
        ViewGroup.LayoutParams barParams = bar.getLayoutParams();
        if (barParams instanceof MarginLayoutParams) {
            barChrome += ((MarginLayoutParams) barParams).leftMargin
                + ((MarginLayoutParams) barParams).rightMargin;
        }
        return StatusRowFitPolicy.stripMinPx(strip.getPaddingLeft() + strip.getPaddingRight(),
            badgeLead, badgeWidth, barChrome, bar.frontChipWidthPx());
    }

    /**
     * The row's child that holds the place strip: the strip itself, or the slot it shares with
     * Home's float chips. What the row measures, flows and stands the readings after.
     */
    @Nullable
    private View stripSlot() {
        View view = findViewById(R.id.terminal_status_place_content);
        while (view != null && view.getParent() != this) {
            ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        return view;
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

    /** The readings' own row, when it is the one that can leave readings out for room. */
    @Nullable
    private StatusReadingsCluster readingsCluster() {
        View view = findViewById(R.id.terminal_status_stats_cluster);
        return view instanceof StatusReadingsCluster ? (StatusReadingsCluster) view : null;
    }

    /**
     * Parts the row at the camera ({@link StatusRowKeepOut#partAtGap}) when it is the row the
     * layout builds: the chip strip's slot and one group holding the readings, side by side. The
     * readings that fit their side stand, the rest are left out from the far end; the strip and
     * the group are measured to their stretches. Any other row keeps the flow.
     */
    private boolean partAtGap(int start, int end) {
        StatusReadingsCluster cluster = readingsCluster();
        View strip = stripSlot();
        if (!mKeepOut.hasGap() || cluster == null || strip == null
            || cluster.getVisibility() == GONE || cluster.getOrientation() != HORIZONTAL) {
            return false;
        }
        View group = cluster;
        while (group.getParent() != this) {
            if (!(group.getParent() instanceof View)) return false;
            group = (View) group.getParent();
        }
        View[] children = visualChildren();
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        if (children.length != 2 || group == strip || weightOf(group) > 0f
            || children[rtl ? 1 : 0] != strip || children[rtl ? 0 : 1] != group) {
            return false;
        }
        int count = readingItems(cluster);
        int chrome = readingsChrome(group, cluster);
        // The readings' far end already keeps its own air from the row's edge (the group's end
        // margin and padding, which clear the lens and the bar's corner); the screen's corner
        // takes only what reaches past that, not its whole inset on top of it.
        int farAir = rtl ? leftMargin(group) + group.getPaddingLeft()
            : rightMargin(group) + group.getPaddingRight();
        int readingsStart = rtl ? start - Math.min(mKeepOut.leftInsetPx, farAir) : start;
        int readingsEnd = rtl ? end : end + Math.min(mKeepOut.rightInsetPx, farAir);
        int[] out = mPartAnswer;
        StatusRowKeepOut.partAtGap(readingsStart, readingsEnd, mKeepOut.gapStartPx,
            mKeepOut.gapEndPx, rtl, mStripMinPx, mPartItemWidth, mPartItemMark, count, chrome,
            out);
        // The chips carry no such air: they never reach past the corner's inset.
        out[StatusRowKeepOut.PART_CHIPS_LEFT] = Math.max(start,
            out[StatusRowKeepOut.PART_CHIPS_LEFT]);
        out[StatusRowKeepOut.PART_CHIPS_RIGHT] = Math.max(out[StatusRowKeepOut.PART_CHIPS_LEFT],
            Math.min(end, out[StatusRowKeepOut.PART_CHIPS_RIGHT]));
        int kept = out[StatusRowKeepOut.PART_READINGS_KEPT];
        cluster.setKept(kept, this);
        if (kept > 0) {
            int groupWidth = out[StatusRowKeepOut.PART_READINGS_RIGHT]
                - out[StatusRowKeepOut.PART_READINGS_LEFT] - leftMargin(group) - rightMargin(group);
            group.measure(MeasureSpec.makeMeasureSpec(Math.max(0, groupWidth),
                MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(group.getMeasuredHeight(),
                MeasureSpec.EXACTLY));
        }
        int stripWidth = out[StatusRowKeepOut.PART_CHIPS_RIGHT]
            - out[StatusRowKeepOut.PART_CHIPS_LEFT] - leftMargin(strip) - rightMargin(strip);
        strip.measure(MeasureSpec.makeMeasureSpec(Math.max(0, stripWidth), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(strip.getMeasuredHeight(), MeasureSpec.EXACTLY));
        mPartStrip = strip;
        mPartGroup = group;
        mParted = true;
        return true;
    }

    /**
     * Fills {@link #mPartItemWidth} and {@link #mPartItemMark} with the cluster's shown items,
     * each with its margins, in its own order; returns how many.
     */
    private int readingItems(@NonNull StatusReadingsCluster cluster) {
        int count = 0;
        for (int i = 0; i < cluster.getChildCount(); i++) {
            if (cluster.getChildAt(i).getVisibility() != GONE) count++;
        }
        if (mPartItemWidth.length < count) {
            mPartItemWidth = new int[count];
            mPartItemMark = new boolean[count];
        }
        int item = 0;
        for (int i = 0; i < cluster.getChildCount(); i++) {
            View child = cluster.getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            mPartItemWidth[item] = leftMargin(child) + child.getMeasuredWidth() + rightMargin(child);
            mPartItemMark[item] = child instanceof MaterialDotSeparatorView;
            item++;
        }
        return count;
    }

    /**
     * Everything the readings' group takes besides the items: its margins and padding, whatever
     * else it holds, the cluster's margins and padding. The cluster is measured whole here.
     */
    private static int readingsChrome(@NonNull View group, @NonNull StatusReadingsCluster cluster) {
        return leftMargin(group) + rightMargin(group) + group.getMeasuredWidth()
            - cluster.getMeasuredWidth() + cluster.getPaddingLeft() + cluster.getPaddingRight();
    }

    /** The row's child holding {@code view}, or null when it is not under this row. */
    @Nullable
    private View rowChildOf(@Nullable View view) {
        while (view != null && view.getParent() != this) {
            ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        return view;
    }

    /** Places the parted strip and readings where {@link #partAtGap} answered. */
    private void layOutParted(@NonNull View strip, @NonNull View group) {
        int[] out = mPartAnswer;
        int chipsLeft = out[StatusRowKeepOut.PART_CHIPS_LEFT] + leftMargin(strip);
        strip.layout(chipsLeft, strip.getTop(), chipsLeft + strip.getMeasuredWidth(),
            strip.getTop() + strip.getMeasuredHeight());
        boolean none = out[StatusRowKeepOut.PART_READINGS_KEPT] <= 0;
        int groupLeft = out[StatusRowKeepOut.PART_READINGS_LEFT] + (none ? 0 : leftMargin(group));
        group.layout(groupLeft, group.getTop(), groupLeft + (none ? 0 : group.getMeasuredWidth()),
            group.getTop() + group.getMeasuredHeight());
        // A group with not one reading standing is hidden like any child the flow drops.
        mPartChildren[0] = strip;
        mPartChildren[1] = group;
        mPartWidths[0] = strip.getMeasuredWidth();
        mPartWidths[1] = none ? 0 : group.getMeasuredWidth();
        applyDropped(mPartChildren, mPartWidths);
    }

    private static int leftMargin(@NonNull View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        return params instanceof MarginLayoutParams ? ((MarginLayoutParams) params).leftMargin : 0;
    }

    private static int rightMargin(@NonNull View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        return params instanceof MarginLayoutParams ? ((MarginLayoutParams) params).rightMargin : 0;
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
        int[] minWidths = new int[n];
        View slot = stripSlot();
        for (int i = 0; i < n; i++) {
            if (children[i] == slot) minWidths[i] = mStripMinPx;
        }
        StatusRowKeepOut.flow(marginLeft, widths, marginRight, weights, minWidths, start, end,
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
