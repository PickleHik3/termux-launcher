package com.termux.app.terminal;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.widget.WidgetPaneView;

/**
 * The float layer: where Home floats are drawn, pinned to the Home pages. It lives inside the
 * Home view ({@link WidgetPaneView#setPagePinnedLayer}), over the widget grid, so it slides with
 * Home across the seam and is carried with the page's content while a swipe drags it; the pane
 * tells it which Home page is on screen and the controller shows that page's floats only.
 *
 * <p>The layer itself takes no touch: a press that lands on no float goes on to the grid, and the
 * layer reports it first, since a tap on Home beside the floats gives focus back to Home.
 */
public final class HomeFloatLayer extends FrameLayout implements WidgetPaneView.PagePinnedLayer {

    /** What the layer reports to the controller that fills it. */
    public interface Listener {
        /** Home page {@code page} is on screen: its floats show, every other page's hide. */
        void onHomePageShown(int page);
        /** A press went down on Home beside every float, at this point on screen. */
        void onPressBesideFloats(float rawX, float rawY);
        /**
         * Whether a press on {@code child}, the float under the finger, at ({@code x}, {@code y})
         * in its own coordinates gives a sideways swipe to the page
         * ({@link TerminalBodySwipe#yieldsSideways}).
         */
        default boolean floatYieldsSideways(@NonNull View child, float x, float y) {
            return false;
        }
    }

    @Nullable private Listener mListener;
    private int mPage;

    public HomeFloatLayer(@NonNull Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(false);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /** The Home page last shown here, counting from 0. */
    public int page() {
        return mPage;
    }

    @Override
    public void onHomePageShown(int page) {
        mPage = Math.max(0, page);
        if (mListener != null) mListener.onHomePageShown(mPage);
    }

    /** A float shows on the page in view: the others are gone, and a minimised one is its chip. */
    @Override
    public boolean showsAnything() {
        for (int i = 0; i < getChildCount(); i++)
            if (getChildAt(i).getVisibility() == VISIBLE) return true;
        return false;
    }

    @Override
    public boolean holdsPoint(float x, float y) {
        float localX = x - getLeft() - getTranslationX();
        float localY = y - getTop() - getTranslationY();
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child.getVisibility() != VISIBLE) continue;
            float left = child.getLeft() + child.getTranslationX();
            float top = child.getTop() + child.getTranslationY();
            if (localX >= left && localX < left + child.getWidth()
                && localY >= top && localY < top + child.getHeight())
                return true;
        }
        return false;
    }

    @Override
    public boolean yieldsSidewaysSwipe(float x, float y) {
        if (mListener == null) return false;
        float localX = x - getLeft() - getTranslationX();
        float localY = y - getTop() - getTranslationY();
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child.getVisibility() != VISIBLE) continue;
            float left = child.getLeft() + child.getTranslationX();
            float top = child.getTop() + child.getTranslationY();
            if (localX >= left && localX < left + child.getWidth()
                && localY >= top && localY < top + child.getHeight()) {
                // The topmost float under the finger answers; the ones beneath never see it.
                return mListener.floatYieldsSideways(child, localX - left, localY - top);
            }
        }
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && mListener != null
            && !holdsPoint(event.getX() + getLeft() + getTranslationX(),
                event.getY() + getTop() + getTranslationY())) {
            mListener.onPressBesideFloats(event.getRawX(), event.getRawY());
        }
        return super.dispatchTouchEvent(event);
    }
}
