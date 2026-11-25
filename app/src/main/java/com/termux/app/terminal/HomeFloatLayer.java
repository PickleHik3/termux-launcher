package com.termux.app.terminal;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
 * layer reports it when it lifts as a tap ({@link BesideTap}), since a tap on Home beside the
 * floats gives focus back to Home. The rest of such a gesture never reaches the layer, so the pane
 * hands it over ({@link #onPaneTouch}); a swipe that started beside the floats is no tap.
 */
public final class HomeFloatLayer extends FrameLayout implements WidgetPaneView.PagePinnedLayer,
        com.termux.app.chrome.LiveBackdrop.Container {

    /** What the layer reports to the controller that fills it. */
    public interface Listener {
        /** Home page {@code page} is on screen: its floats show, every other page's hide. */
        void onHomePageShown(int page);
        /** A tap on Home beside every float, lifted at this point on screen. */
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
    private final BesideTap mBesideTap;

    public HomeFloatLayer(@NonNull Context context) {
        super(context);
        mBesideTap = new BesideTap(ViewConfiguration.get(context).getScaledTouchSlop());
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
        watch(event);
        return super.dispatchTouchEvent(event);
    }

    /**
     * The rest of a gesture whose press the layer saw go down: a press beside the floats has no
     * target here, so its moves, lift or cancel reach the pane only, which hands them on.
     */
    @Override
    public void onPaneTouch(@NonNull MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) watch(event);
    }

    private void watch(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mBesideTap.down(event.getRawX(), event.getRawY(), mListener != null
                    && !holdsPoint(event.getX() + getLeft() + getTranslationX(),
                        event.getY() + getTop() + getTranslationY()));
                break;
            case MotionEvent.ACTION_MOVE:
                mBesideTap.move(event.getRawX(), event.getRawY());
                break;
            case MotionEvent.ACTION_UP:
                if (mBesideTap.up(event.getRawX(), event.getRawY()) && mListener != null)
                    mListener.onPressBesideFloats(event.getRawX(), event.getRawY());
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                mBesideTap.cancel();
                break;
            default:
                break;
        }
    }

    /** Live blur records what draws beneath a float's glass through this container. */
    @Override
    public void drawChildForBackdrop(@androidx.annotation.NonNull android.graphics.Canvas canvas,
                                     @androidx.annotation.NonNull android.view.View child) {
        drawChild(canvas, child, getDrawingTime());
    }
}
