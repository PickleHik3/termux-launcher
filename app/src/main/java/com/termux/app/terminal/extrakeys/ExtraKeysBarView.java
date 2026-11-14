package com.termux.app.terminal.extrakeys;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.extrakeys.ExtraKeysClient;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The extra-keys bar: the key pages and an optional text input page last, side by side. A sideways
 * drag moves them with {@code translationX}; the page lands in 160 ms and the bar's height follows
 * from the page it lands on, as one geometry step per flip rather than a layout per frame.
 *
 * <p>The bar never reads screen or dock geometry: its own height comes from its layout params and
 * each page is laid out at its rows' height, anchored to one edge.
 */
public final class ExtraKeysBarView extends android.view.ViewGroup {

    /** Page changes the activity answers. */
    public interface Listener {
        /** A page has landed. Called once per landing on a page other than the one before. */
        void onPageSelected(int page);

        /**
         * The committed page changed and with it the rows the bar stands for ({@link
         * #committedRows()}). The activity runs its geometry pass once here.
         */
        void onCommittedHeightChanged();
    }

    /** Dresses a page the bar has just built. */
    public interface PageStyler {
        void style(@NonNull ExtraKeysPageView page, int index);
    }

    private static final float DOT_RADIUS_DP = 1.6f;
    private static final float DOT_GAP_DP = 5f;
    private static final float DOT_FROM_BOTTOM_DP = 2.6f;
    private static final int DOT_ALPHA_CURRENT = 150;
    private static final int DOT_ALPHA_OTHER = 55;

    private final float mDensity;
    private final int mTouchSlop;
    private final List<ExtraKeysPageView> mKeyPages = new ArrayList<>();
    @Nullable private View mTextInput;
    private int[] mKeyPageRows = new int[0];
    @Nullable private PageStyler mPageStyler;
    @Nullable private Listener mListener;
    @Nullable private ExtraKeysClient mClient;
    @NonNull private ExtraKeyModifiers mModifiers = new ExtraKeyModifiers();

    private int mBaseRowPx;
    private float mScale = 1f;
    private int mAnchorEdge = Gravity.BOTTOM;

    /** The page the bar shows, and the page whose rows the bar's height stands for. */
    private int mCurrent;
    private int mCommitted;

    /** Where the pages are, in pixels: page i is at {@code i * width - mOffsetPx}. */
    private float mOffsetPx;

    private float mDownX;
    private float mDownY;
    private boolean mDragging;
    private final ValueAnimator mSlide = new ValueAnimator();
    private int mSlideTarget;

    private final ValueAnimator mConceal = new ValueAnimator();
    private ExtraKeysBarHeight.Step mStep = ExtraKeysBarHeight.Step.NONE;
    private int mStepFromPx;
    private int mStepToPx;
    private int mConcealPx;
    private int mPendingCommit = -1;

    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ExtraKeysBarView(@NonNull Context context) {
        this(context, null);
    }

    public ExtraKeysBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mDensity = context.getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mDotPaint.setStyle(Paint.Style.FILL);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        setClipChildren(true);

        mSlide.setFloatValues(0f, 1f);
        mSlide.setDuration(ExtraKeyGesturePolicy.SLIDE_MS);
        mSlide.setInterpolator(new DecelerateInterpolator());
        mSlide.addUpdateListener(a -> {
            float t = (Float) a.getAnimatedValue();
            mOffsetPx = mSlideFromPx + (mSlideToPx - mSlideFromPx) * t;
            applyOffsets();
        });
        mSlide.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override
            public void onAnimationStart(Animator animation) {
                mCancelled = false;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mCancelled) return;
                mOffsetPx = mSlideToPx;
                applyOffsets();
                land(mSlideTarget);
            }
        });

        mConceal.setFloatValues(0f, 1f);
        mConceal.setDuration(ExtraKeysBarHeight.HEIGHT_MS);
        mConceal.setInterpolator(new DecelerateInterpolator());
        mConceal.addUpdateListener(a -> {
            mConcealPx = ExtraKeysBarHeight.concealedPx(mStep, mStepFromPx, mStepToPx,
                (Float) a.getAnimatedValue());
            invalidate();
        });
        mConceal.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            @Override
            public void onAnimationStart(Animator animation) {
                mCancelled = false;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mCancelled) return;
                finishHeightStep();
            }
        });
    }

    private float mSlideFromPx;
    private float mSlideToPx;

    // ---- Content.

    public void setPageStyler(@Nullable PageStyler styler) {
        mPageStyler = styler;
        if (styler == null) return;
        for (int i = 0; i < mKeyPages.size(); i++) styler.style(mKeyPages.get(i), i);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    public void setClient(@Nullable ExtraKeysClient client) {
        mClient = client;
        for (int i = 0; i < mKeyPages.size(); i++) mKeyPages.get(i).setClient(client);
    }

    public void setModifiers(@NonNull ExtraKeyModifiers modifiers) {
        mModifiers = modifiers;
        for (int i = 0; i < mKeyPages.size(); i++) mKeyPages.get(i).setModifiers(modifiers);
    }

    @NonNull
    public ExtraKeyModifiers getModifiers() {
        return mModifiers;
    }

    /** Builds or rebuilds one page per entry; an empty or null array leaves no key pages. */
    public void setKeySet(@Nullable ExtraKeysInfo[] pages) {
        finishAnimations();
        for (int i = 0; i < mKeyPages.size(); i++) {
            mKeyPages.get(i).dismissPopup();
            removeView(mKeyPages.get(i));
        }
        mKeyPages.clear();
        int count = pages == null ? 0 : pages.length;
        mKeyPageRows = new int[count];
        for (int i = 0; i < count; i++) {
            ExtraKeysPageView page = new ExtraKeysPageView(getContext());
            page.setModifiers(mModifiers);
            page.setClient(mClient);
            page.setKeys(pages[i]);
            if (mPageStyler != null) mPageStyler.style(page, i);
            mKeyPageRows[i] = page.rowCount();
            mKeyPages.add(page);
            addView(page, i);
        }
        mCurrent = clampPage(mCurrent);
        mCommitted = mCurrent;
        mOffsetPx = mCurrent * (float) getWidth();
        requestLayout();
        applyOffsets();
        invalidate();
    }

    /** The view after the last key page, or null for none. */
    public void setTextInputPage(@Nullable View page) {
        if (page == mTextInput) return;
        if (mTextInput != null) removeView(mTextInput);
        mTextInput = page;
        if (page != null) addView(page);
        mCurrent = clampPage(mCurrent);
        mCommitted = clampPage(mCommitted);
        requestLayout();
        applyOffsets();
    }

    public int keyPageCount() {
        return mKeyPages.size();
    }

    public int pageCount() {
        return mKeyPages.size() + (mTextInput == null ? 0 : 1);
    }

    @Nullable
    public ExtraKeysPageView keyPage(int index) {
        return index >= 0 && index < mKeyPages.size() ? mKeyPages.get(index) : null;
    }

    @NonNull
    public List<ExtraKeysPageView> keyPages() {
        return Collections.unmodifiableList(mKeyPages);
    }

    public int getCurrentItem() {
        return mCurrent;
    }

    /** The rows the bar's height stands for: those of the committed page, not of one still sliding. */
    public int committedRows() {
        return ExtraKeysBarHeight.rowsOf(mKeyPageRows, mCommitted);
    }

    public void setAnchorEdge(int gravity) {
        int edge = gravity == Gravity.TOP ? Gravity.TOP : Gravity.BOTTOM;
        if (edge == mAnchorEdge) return;
        mAnchorEdge = edge;
        requestLayout();
    }

    /** One row's height and the scale factor; each page is as tall as its rows. */
    public void setRowMetrics(int baseRowPx, float scale) {
        if (baseRowPx == mBaseRowPx && scale == mScale) return;
        mBaseRowPx = baseRowPx;
        mScale = scale;
        requestLayout();
    }

    private int clampPage(int page) {
        return Math.max(0, Math.min(page, Math.max(0, pageCount() - 1)));
    }

    private int rowsOfPage(int page) {
        return ExtraKeysBarHeight.rowsOf(mKeyPageRows, page);
    }

    private int heightOfRows(int rows, int barHeight) {
        return mBaseRowPx > 0
            ? ExtraKeysBarHeight.pageHeightPx(mBaseRowPx, rows, mScale)
            : barHeight;
    }

    // ---- Layout.

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
            ? 0 : MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
            ? MeasureSpec.getSize(heightMeasureSpec)
            : heightOfRows(rowsOfPage(mCommitted), 0);
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            h = Math.min(h, MeasureSpec.getSize(heightMeasureSpec));
        }
        setMeasuredDimension(w, h);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(pageHeightOf(child, h), MeasureSpec.EXACTLY));
        }
    }

    private int pageHeightOf(@NonNull View child, int barHeight) {
        int rows = child == mTextInput ? 1 : rowsOfPage(mKeyPages.indexOf(child));
        return heightOfRows(rows, barHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int ph = child.getMeasuredHeight();
            int top = mAnchorEdge == Gravity.TOP ? 0 : h - ph;
            child.layout(0, top, w, top + ph);
        }
        applyOffsets();
    }

    /** Moves the pages to the current offset. Draw only: no layout. */
    private void applyOffsets() {
        float w = getWidth();
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setTranslationX(i * w - mOffsetPx);
        }
    }

    // ---- Sideways drag.

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getX();
                mDownY = ev.getY();
                mDragging = false;
                if (mSlide.isRunning()) mSlide.end();
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!mDragging && startsDrag(ev.getX(), ev.getY())) {
                    beginDrag();
                    return true;
                }
                return mDragging;
            default:
                return false;
        }
    }

    private boolean startsDrag(float x, float y) {
        float dx = x - mDownX;
        float dy = y - mDownY;
        return Math.abs(dx) > mTouchSlop && Math.abs(dx) > Math.abs(dy) && pageCount() > 0;
    }

    private void beginDrag() {
        mDragging = true;
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getX();
                mDownY = ev.getY();
                mDragging = false;
                if (mSlide.isRunning()) mSlide.end();
                return pageCount() > 0;
            case MotionEvent.ACTION_MOVE:
                if (!mDragging && startsDrag(ev.getX(), ev.getY())) beginDrag();
                if (mDragging) {
                    float dx = ev.getX() - mDownX;
                    mOffsetPx = mCurrent * (float) getWidth()
                        - ExtraKeyGesturePolicy.dragOffsetPx(mCurrent, pageCount(), dx);
                    applyOffsets();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (mDragging) {
                    int target = ExtraKeyGesturePolicy.landingPage(mCurrent, pageCount(),
                        ev.getX() - mDownX, getWidth());
                    mDragging = false;
                    slideTo(target);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (mDragging) {
                    mDragging = false;
                    slideTo(mCurrent);
                }
                return true;
            default:
                return true;
        }
    }

    private void slideTo(int page) {
        mSlideTarget = clampPage(page);
        mSlideFromPx = mOffsetPx;
        mSlideToPx = mSlideTarget * (float) getWidth();
        mSlide.start();
    }

    // ---- Page and height.

    /** The page the slide landed on: height first or after, as the step says, then the callback. */
    private void land(int page) {
        if (mConceal.isRunning()) mConceal.end();
        int previous = mCurrent;
        mCurrent = page;
        if (page == previous) return;
        int fromPx = heightOfRows(rowsOfPage(mCommitted), getHeight());
        int toPx = heightOfRows(rowsOfPage(page), getHeight());
        ExtraKeysBarHeight.Step step = ExtraKeysBarHeight.step(fromPx, toPx);
        mStep = step;
        mStepFromPx = fromPx;
        mStepToPx = toPx;
        invalidate();
        if (step == ExtraKeysBarHeight.Step.SHRINK) {
            mPendingCommit = page;
            mConcealPx = 0;
            mConceal.start();
        } else {
            mCommitted = page;
            if (step == ExtraKeysBarHeight.Step.GROW) {
                // Commit first: the geometry pass lays the bar out at its new height with the extra
                // part hidden, then the clip reveals it.
                mConcealPx = toPx - fromPx;
                if (mListener != null) mListener.onCommittedHeightChanged();
                mConceal.start();
            }
        }
        if (mListener != null) mListener.onPageSelected(page);
    }

    private void finishHeightStep() {
        mConcealPx = 0;
        if (mPendingCommit >= 0) {
            mCommitted = mPendingCommit;
            mPendingCommit = -1;
            mStep = ExtraKeysBarHeight.Step.NONE;
            if (mListener != null) mListener.onCommittedHeightChanged();
        }
        mStep = ExtraKeysBarHeight.Step.NONE;
        invalidate();
    }

    private void finishAnimations() {
        mDragging = false;
        if (mSlide.isRunning()) mSlide.cancel();
        if (mConceal.isRunning()) mConceal.end();
        mConcealPx = 0;
    }

    /**
     * Shows a page. Without {@code smooth} it commits at once and tells both listeners without any
     * animation.
     */
    public void setCurrentItem(int page, boolean smooth) {
        int target = clampPage(page);
        if (smooth) {
            if (target == mCurrent && !mSlide.isRunning()) return;
            slideTo(target);
            return;
        }
        finishAnimations();
        boolean changed = target != mCurrent || target != mCommitted;
        mCurrent = target;
        mCommitted = target;
        mPendingCommit = -1;
        mStep = ExtraKeysBarHeight.Step.NONE;
        mOffsetPx = target * (float) getWidth();
        applyOffsets();
        invalidate();
        if (changed && mListener != null) {
            mListener.onCommittedHeightChanged();
            mListener.onPageSelected(target);
        }
    }

    // ---- Drawing.

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        int save = canvas.save();
        if (mConcealPx > 0) {
            // The side away from the anchor is the side that grows or goes.
            if (mAnchorEdge == Gravity.TOP) {
                canvas.clipRect(0, 0, getWidth(), Math.max(0, getHeight() - mConcealPx));
            } else {
                canvas.clipRect(0, Math.min(getHeight(), mConcealPx), getWidth(), getHeight());
            }
        }
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
        drawPageDots(canvas);
    }

    private void drawPageDots(@NonNull Canvas canvas) {
        int count = mKeyPages.size();
        if (count < 2 || mCurrent >= count) return;
        float radius = DOT_RADIUS_DP * mDensity;
        float gap = DOT_GAP_DP * mDensity;
        float total = count * radius * 2 + (count - 1) * (gap - radius * 2);
        float cx = (getWidth() - total) / 2f + radius;
        float cy = getHeight() - DOT_FROM_BOTTOM_DP * mDensity;
        int color = mKeyPages.get(0).getButtonTextColor();
        for (int i = 0; i < count; i++) {
            mDotPaint.setColor(color);
            mDotPaint.setAlpha(i == mCurrent ? DOT_ALPHA_CURRENT : DOT_ALPHA_OTHER);
            canvas.drawCircle(cx, cy, radius, mDotPaint);
            cx += gap;
        }
    }
}
