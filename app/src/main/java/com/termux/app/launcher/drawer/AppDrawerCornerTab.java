package com.termux.app.launcher.drawer;

import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.CornerZones;
import com.termux.app.haptics.Haptics;
import com.termux.app.terminal.PaneGlass;
import com.termux.app.wall.PaneControlsView;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.view.HoldTiming;

/**
 * The drawer pane's corner tab: a still hold on a corner of the drawer drops the same
 * {@link PaneControlsView} tab the Widgets page and the display window drop, carrying the drawer's
 * settings. The drawer is the Widgets page's frame in miniature: this watches the touch stream the
 * content already dispatches, decides once at the down through {@link AppDrawerCornerTabPolicy},
 * and when the hold fires the content is owed a cancel and the corner owns the rest of the stream.
 *
 * <p>The tab is created on its first use, so a drawer nobody holds a corner of never builds it.
 */
final class AppDrawerCornerTab {

    /** What the content view does with an event after the tab has seen it. */
    static final int PASS = 0;
    static final int CONSUME = 1;

    private static final int ACTION_SETTINGS = 1;

    interface Host {
        @NonNull ViewGroup view();

        /** The chrome's own rectangles at the down: {@code left, top, right, bottom} each. */
        int chromeRects(@NonNull float[] out);

        /** The hold fired: tell the content its touch is over. */
        void cancelContent(float x, float y);

        void openSettings();
    }

    private final Host mHost;
    private final AppDrawerCornerTabPolicy mPolicy = new AppDrawerCornerTabPolicy();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHoldElapsed = this::onHoldElapsed;
    private final float[] mChromeRects = new float[16];
    @Nullable private PaneControlsView mTab;
    private int mPressedAction = PaneControlsView.ACTION_NONE;
    private boolean mPressMoved;
    private float mDownX, mDownY;

    AppDrawerCornerTab(@NonNull Host host) {
        mHost = host;
    }

    /** True while a down in a corner square waits for its hold. */
    boolean isArmed() {
        return mPolicy.isTracking();
    }

    /** One event of the stream, before the content dispatches it. */
    int filter(@NonNull MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) return onDown(ev);
        if (mPressedAction != PaneControlsView.ACTION_NONE) return onTabPress(ev);
        if (!mPolicy.isTracking()) return PASS;
        boolean claimed = mPolicy.isClaimed();
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                if (mPolicy.move(ev.getX(), ev.getY())) mHandler.removeCallbacks(mHoldElapsed);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mPolicy.secondFinger()) mHandler.removeCallbacks(mHoldElapsed);
                break;
            case MotionEvent.ACTION_UP:
                mHandler.removeCallbacks(mHoldElapsed);
                int corner = mPolicy.corner();
                if (mPolicy.lift() && corner != CornerZones.NONE) showAt(corner);
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelHold();
                break;
            default:
                break;
        }
        return claimed ? CONSUME : PASS;
    }

    private int onDown(@NonNull MotionEvent ev) {
        cancelHold();
        mPressedAction = PaneControlsView.ACTION_NONE;
        mPressMoved = false;
        mDownX = ev.getX();
        mDownY = ev.getY();
        PaneControlsView tab = mTab;
        if (tab != null && tab.isControlsShown()) {
            int action = tab.actionAt(mDownX, mDownY);
            if (action != PaneControlsView.ACTION_NONE) {
                mPressedAction = action;
                return CONSUME;
            }
            // A tap anywhere else, or a scroll started there, puts the tab away.
            tab.dismiss();
        }
        ViewGroup view = mHost.view();
        int count = mHost.chromeRects(mChromeRects) / 4;
        boolean chromeOwns = AppDrawerCornerTabPolicy.chromeOwnsPoint(mDownX, mDownY,
            mChromeRects, count);
        float slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        int corner = mPolicy.down(mDownX, mDownY, view.getWidth(), view.getHeight(),
            view.getResources().getDisplayMetrics().density, chromeOwns, slop);
        android.util.Log.d("DrawerCorner", "down x=" + mDownX + " y=" + mDownY + " w=" + view.getWidth()
            + " h=" + view.getHeight() + " chrome=" + chromeOwns + " corner=" + corner); // DIAG
        if (corner != CornerZones.NONE) {
            mHandler.postDelayed(mHoldElapsed, HoldTiming.holdTimeoutMs());
        }
        return PASS;
    }

    private int onTabPress(@NonNull MotionEvent ev) {
        PaneControlsView tab = mTab;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                float slop = ViewConfiguration.get(mHost.view().getContext()).getScaledTouchSlop();
                if (Math.abs(ev.getX() - mDownX) > slop || Math.abs(ev.getY() - mDownY) > slop) {
                    mPressMoved = true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (tab != null && !mPressMoved
                    && tab.actionAt(ev.getX(), ev.getY()) == mPressedAction) {
                    tab.activate(mPressedAction);
                }
                mPressedAction = PaneControlsView.ACTION_NONE;
                break;
            case MotionEvent.ACTION_CANCEL:
                mPressedAction = PaneControlsView.ACTION_NONE;
                break;
            default:
                break;
        }
        return CONSUME;
    }

    private void onHoldElapsed() {
        boolean held = mPolicy.holdElapsed();
        android.util.Log.d("DrawerCorner", "hold elapsed claimed=" + held); // DIAG
        if (!held) return;
        mHost.cancelContent(mDownX, mDownY);
        Haptics.tick(mHost.view(), HapticFeedbackConstants.LONG_PRESS);
        ViewParent parent = mHost.view().getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
    }

    private void cancelHold() {
        mHandler.removeCallbacks(mHoldElapsed);
        mPolicy.reset();
    }

    private void showAt(int corner) {
        android.util.Log.d("DrawerCorner", "show corner=" + corner); // DIAG
        PaneControlsView tab = ensureTab();
        tab.show(corner);
    }

    @NonNull
    private PaneControlsView ensureTab() {
        PaneControlsView tab = mTab;
        if (tab != null) return tab;
        ViewGroup view = mHost.view();
        tab = new PaneControlsView(view.getContext());
        // Wave 2 adds the Edit button here: one more drawn action with
        // vectorMark(context, R.drawable.ic_drawer_corner_edit), before the settings gear.
        tab.setActions(PaneControlsView.Action.drawn(ACTION_SETTINGS,
            vectorMark(view.getContext(), R.drawable.ic_drawer_corner_settings),
            PaneControlsView.TINT_PRIMARY,
            view.getContext().getString(R.string.pane_controls_open_settings)));
        tab.setListener(id -> {
            if (id != ACTION_SETTINGS) return;
            dismiss();
            mHost.openSettings();
        });
        PaneGlass.dressTab(null, tab, TermuxAppSharedPreferences.PAGE_WALL);
        view.addView(tab, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        mTab = tab;
        return tab;
    }

    /**
     * A tab mark that draws a vector in the tab's own ink (the drawable's baked colour is ignored),
     * in the 16dp square {@code MinimalModeGlyph} fills.
     */
    @NonNull
    static PaneControlsView.Mark vectorMark(@NonNull android.content.Context context, int drawableRes) {
        return new PaneControlsView.Mark() {
            @androidx.annotation.Nullable private android.graphics.drawable.Drawable mIcon;

            @Override
            public void draw(@NonNull android.graphics.Canvas canvas,
                             @NonNull android.graphics.RectF button,
                             @NonNull android.graphics.Paint paint, float density) {
                android.graphics.drawable.Drawable icon = mIcon;
                if (icon == null) {
                    icon = androidx.core.content.ContextCompat.getDrawable(context, drawableRes);
                    if (icon == null) return;
                    icon = icon.mutate();
                    mIcon = icon;
                }
                int half = Math.round(8f * density);
                int cx = Math.round(button.centerX());
                int cy = Math.round(button.centerY());
                icon.setBounds(cx - half, cy - half, cx + half, cy + half);
                icon.setTint(paint.getColor());
                icon.draw(canvas);
            }
        };
    }

    boolean isShown() {
        return mTab != null && mTab.isControlsShown();
    }

    /** Puts the tab away with its own motion; false when it was not out (the press is not spent). */
    boolean dismiss() {
        PaneControlsView tab = mTab;
        if (tab == null || !tab.isControlsShown()) return false;
        tab.dismiss();
        return true;
    }

    /** Gone at once and every press forgotten: the drawer closed, changed type or went away. */
    void reset() {
        cancelHold();
        mPressedAction = PaneControlsView.ACTION_NONE;
        if (mTab != null) mTab.dismissNow();
    }

    @androidx.annotation.VisibleForTesting
    void showForTest(int corner) {
        showAt(corner);
    }
}
