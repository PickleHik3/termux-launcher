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

    interface Host {
        @NonNull ViewGroup view();

        /** The chrome's own rectangles at the down: {@code left, top, right, bottom} each. */
        int chromeRects(@NonNull float[] out);

        /** The hold fired: tell the content its touch is over. */
        void cancelContent(float x, float y);

        void openSettings();

        /** The Edit button ran: enter edit mode. */
        void enterEdit();

        /** The tick ran: leave edit mode. */
        void leaveEdit();

        /** The grid-size button ran; {@code tab} is the tab's bounds in the content's coordinates. */
        void openGridSize(@NonNull android.graphics.RectF tab);

        /** The grid-size button's text: the size the drawer's grid has now. */
        @NonNull String gridSizeText();

        /** Edit mode as the drawer holds it. */
        @NonNull AppDrawerEditModel editModel();

        @NonNull AppDrawerViewType viewType();

        @NonNull AppDrawerSection section();

        /** The drawer card's corner radius, in px. */
        float cardCornerRadiusPx();

        /** How far the chrome there keeps a top corner's tab below the card's top edge. */
        int cornerClearTopPx();

        /** How far the chrome there keeps a bottom corner's tab above the card's bottom edge. */
        int cornerClearBottomPx();

        /**
         * The card's own glass for the tab, held while the tab is out; null where there is none.
         * Called again with {@code false} once the tab is away.
         */
        @Nullable com.termux.app.chrome.DrawerCardGlassStyle tabGlass(boolean wanted);
    }

    private final Host mHost;
    private final AppDrawerCornerTabPolicy mPolicy = new AppDrawerCornerTabPolicy();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHoldElapsed = this::onHoldElapsed;
    private final float[] mChromeRects = new float[16];
    @Nullable private PaneControlsView mTab;
    private int mPressedAction = PaneControlsView.ACTION_NONE;
    private boolean mPressMoved;
    /** The card's glass is asked for while the tab is out. */
    private boolean mGlassHeld;
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
            // A tap anywhere else, or a scroll started there, puts the tab away; while the drawer
            // is edited the tab is pinned open and stays.
            if (!mHost.editModel().tabPinnedOpen()) tab.dismiss();
        }
        ViewGroup view = mHost.view();
        int count = mHost.chromeRects(mChromeRects) / 4;
        boolean chromeOwns = AppDrawerCornerTabPolicy.chromeOwnsPoint(mDownX, mDownY,
            mChromeRects, count);
        float slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        int corner = mPolicy.down(mDownX, mDownY, view.getWidth(), view.getHeight(),
            view.getResources().getDisplayMetrics().density, chromeOwns, slop);
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
        if (!mPolicy.holdElapsed()) return;
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
        PaneControlsView tab = ensureTab();
        applyButtons(tab);
        applyPlacement(tab, corner);
        dress();
        tab.show(corner);
    }

    /** The buttons the edit model asks for, each id turned into its drawn action. */
    private void applyButtons(@NonNull PaneControlsView tab) {
        int[] ids = AppDrawerCornerTabButtons.ids(mHost.editModel(), mHost.viewType(),
            mHost.section(), AppDrawerCornerTabButtons.EDIT_BUTTON_SHIPPED);
        java.util.ArrayList<PaneControlsView.Action> actions = new java.util.ArrayList<>(ids.length);
        android.content.Context context = tab.getContext();
        for (int id : ids) {
            switch (id) {
                case AppDrawerCornerTabButtons.SETTINGS:
                    actions.add(PaneControlsView.Action.drawn(id,
                        vectorMark(context, R.drawable.ic_drawer_corner_settings),
                        PaneControlsView.TINT_PRIMARY,
                        context.getString(R.string.pane_controls_open_settings)));
                    break;
                case AppDrawerCornerTabButtons.EDIT:
                    actions.add(PaneControlsView.Action.drawn(id,
                        vectorMark(context, R.drawable.ic_drawer_corner_edit),
                        PaneControlsView.TINT_PRIMARY,
                        context.getString(R.string.app_drawer_corner_edit)));
                    break;
                case AppDrawerCornerTabButtons.GRID_SIZE:
                    actions.add(PaneControlsView.Action.label(id, mHost.gridSizeText(),
                        context.getString(R.string.app_drawer_corner_grid_size)));
                    break;
                case AppDrawerCornerTabButtons.TICK:
                    actions.add(PaneControlsView.Action.drawn(id,
                        com.termux.app.wall.WidgetPaneFrame::drawTickMark,
                        PaneControlsView.TINT_PRIMARY,
                        context.getString(R.string.app_drawer_corner_done)));
                    break;
                default:
                    break;
            }
        }
        tab.setActions(actions);
    }

    /**
     * The tab's room is the card less the chrome in its corner's way, so it comes out of the
     * edge of that room; the card's corner arc is only the tab's where the tab is at the card's
     * own edge.
     */
    private void applyPlacement(@NonNull PaneControlsView tab, int corner) {
        boolean top = CornerZones.isTop(corner);
        int clear = Math.max(0, top ? mHost.cornerClearTopPx() : mHost.cornerClearBottomPx());
        android.widget.FrameLayout.LayoutParams params =
            (android.widget.FrameLayout.LayoutParams) tab.getLayoutParams();
        int topMargin = top ? clear : 0;
        int bottomMargin = top ? 0 : clear;
        if (params.topMargin != topMargin || params.bottomMargin != bottomMargin) {
            params.topMargin = topMargin;
            params.bottomMargin = bottomMargin;
            tab.setLayoutParams(params);
        }
        tab.setPaneBorder(clear > 0 ? 0f : mHost.cardCornerRadiusPx(), 0f);
    }

    /** Dresses the tab in the card's own glass, as the section switch is. */
    private void dress() {
        PaneControlsView tab = mTab;
        if (tab == null) return;
        com.termux.app.chrome.DrawerCardGlassStyle style = mHost.tabGlass(true);
        mGlassHeld = true;
        PaneGlass.dressTab(style, tab, TermuxAppSharedPreferences.PAGE_WALL);
    }

    /** The look may have moved while the tab is out: dress it again. */
    void refreshGlass() {
        if (isShown()) dress();
    }

    private void releaseGlass() {
        if (!mGlassHeld) return;
        mGlassHeld = false;
        mHost.tabGlass(false);
    }

    @NonNull
    private PaneControlsView ensureTab() {
        PaneControlsView tab = mTab;
        if (tab != null) return tab;
        ViewGroup view = mHost.view();
        tab = new PaneControlsView(view.getContext());
        tab.setListener(this::onAction);
        view.addView(tab, new android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mTab = tab;
        return tab;
    }

    private void onAction(int id) {
        switch (id) {
            case AppDrawerCornerTabButtons.SETTINGS:
                dismiss();
                mHost.openSettings();
                break;
            case AppDrawerCornerTabButtons.EDIT:
                mHost.enterEdit();
                break;
            case AppDrawerCornerTabButtons.GRID_SIZE: {
                PaneControlsView tab = mTab;
                if (tab == null) break;
                android.graphics.RectF bounds = new android.graphics.RectF();
                tab.tabBounds(bounds);
                mHost.openGridSize(bounds);
                break;
            }
            case AppDrawerCornerTabButtons.TICK:
                mHost.leaveEdit();
                break;
            default:
                break;
        }
    }

    /**
     * Edit mode began or ended: an out tab swaps its buttons to match, and a tab no longer pinned
     * is put away. The grid-size text is re-read here, and again through {@link #refreshButtons}
     * when the size changes.
     */
    void onEditModelChanged() {
        PaneControlsView tab = mTab;
        if (tab == null || !tab.isControlsShown()) return;
        if (mHost.editModel().tabPinnedOpen()) applyButtons(tab);
        else tab.dismiss();
    }

    /** The grid's size changed under an out tab: its grid-size button reads the new size. */
    void refreshButtons() {
        PaneControlsView tab = mTab;
        if (tab != null && tab.isControlsShown()) applyButtons(tab);
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

    /**
     * Gone at once and every press forgotten: the drawer closed, changed type or went away. The
     * card's glass is held until here, not until a dismiss, so a tab still fading never draws a
     * frame the cache has let go.
     */
    void reset() {
        cancelHold();
        mPressedAction = PaneControlsView.ACTION_NONE;
        if (mTab != null) mTab.dismissNow();
        releaseGlass();
    }

    @androidx.annotation.VisibleForTesting
    void showForTest(int corner) {
        showAt(corner);
    }
}
