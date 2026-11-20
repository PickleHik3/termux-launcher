package com.termux.app.wall.pageswitch;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * The page switcher panel, opened by the extra-keys key (Home's page bar is {@link PageBarController}).
 * An entry (the key) feeds it the finger in raw screen coordinates; it places the panel over or above
 * the entry's anchor, runs the {@link PageSwitchGesture}, and changes the page only on a commit.
 * After a tap the panel stays open and the overlay layer takes the touches itself until a cell is
 * chosen or the panel is dismissed.
 */
public final class PageSwitchController {

    public interface Host extends PageSwitchTargets.Navigator {
        @NonNull PageSwitchModel.Input snapshot();
        boolean hapticsEnabled();
        boolean isReducedMotionEnabled();
        /** Whether something covers the wall (an editor, the drawer, a sheet, first-run setup): the switcher does not open. */
        boolean isSuppressed();
        /** The ink sheets and menus draw their labels in. */
        @ColorInt int panelInk();
        /** The Material accent the focused glyph is drawn in and glows with, through the scheme. */
        @ColorInt int panelAccent();
    }

    private static final float GUTTER_DP = 12f;
    private final java.util.ArrayList<PageSwitchHitArea> mHitAreas = new java.util.ArrayList<>(2);
    /** The current stream is the switcher's: it landed on an entry point or on the open panel. */
    private boolean mStreamClaimed;

    private final Host mHost;
    private final FrameLayout mLayer;
    private final PageSwitchPanelView mPanel;
    private final PageSwitchHaptics mHaptics;
    private final PageSwitchGesture mGesture;
    private final float mDensity;
    private final int[] mLayerAt = new int[2];

    @Nullable private PageSwitchModel mModel;
    private boolean mOpen;
    /** The panel's left and top on the screen, for turning raw coordinates into the panel's. */
    private float mPanelLeftRaw;
    private float mPanelTopRaw;
    private int mPanelWidthPx;
    private int mPanelHeightPx;
    /** An open panel's current stream began outside the panel and was consumed as a dismissal. */
    private boolean mDismissStream;
    private boolean mTapDownInPanel;

    public PageSwitchController(@NonNull Context context, @NonNull Host host,
                                @NonNull FrameLayout layer) {
        mHost = host;
        mLayer = layer;
        mDensity = context.getResources().getDisplayMetrics().density;
        mPanel = new PageSwitchPanelView(context);
        mPanel.setVisibility(View.GONE);
        mHaptics = new PageSwitchHaptics(context, host::hapticsEnabled);
        mGesture = new PageSwitchGesture(new PageSwitchLayout(mDensity, 0, 0, 0f),
            ViewConfiguration.get(context).getScaledTouchSlop());
        // The focused glyph's glow reaches past the panel's bounds; nothing clips it.
        layer.setClipChildren(false);
        layer.addView(mPanel, new FrameLayout.LayoutParams(0, 0));
        layer.setClickable(false);
        layer.setOnTouchListener((v, ev) -> onLayerTouch(ev));
    }

    public boolean isOpen() {
        return mOpen;
    }

    /** Back, a pause or a page change by other means: put the panel away, switching nothing. */
    public void close() {
        if (!mOpen) return;
        mGesture.cancel();
        hide();
    }

    public void addHitArea(@NonNull PageSwitchHitArea area) {
        if (!mHitAreas.contains(area)) mHitAreas.add(area);
    }

    public void removeHitArea(@NonNull PageSwitchHitArea area) {
        mHitAreas.remove(area);
    }

    /**
     * Whether this stream goes to the views ahead of the window's own claims (the dock seam's
     * swipe, the surface long-press): one that lands on the open panel or on an entry point.
     */
    public boolean claimsTouch(@NonNull MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            boolean claimed = mOpen;
            if (!claimed && !mHost.isSuppressed()) {
                for (int i = 0; i < mHitAreas.size() && !claimed; i++)
                    claimed = mHitAreas.get(i).containsRaw(ev.getRawX(), ev.getRawY());
            }
            mStreamClaimed = claimed;
        }
        boolean claimed = mStreamClaimed;
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
            mStreamClaimed = false;
        return claimed;
    }

    // ---- Entry points, in raw screen coordinates ----------------------------------------------

    public boolean entryDown(float rawX, float rawY, int anchorTopRaw, int anchorBottomRaw,
                             boolean overAnchor) {
        if (mHost.isSuppressed()) return false;
        if (mOpen) close();
        int layerWidth = mLayer.getWidth();
        if (layerWidth <= 0) return false;
        PageSwitchModel model = PageSwitchModel.build(mHost.snapshot());
        if (model.cells().isEmpty()) return false;
        mModel = model;

        int gutter = Math.round(GUTTER_DP * mDensity);
        int panelWidth = layerWidth - 2 * gutter;
        mPanel.setReducedMotion(mHost.isReducedMotionEnabled());
        mPanel.setInk(mHost.panelInk());
        mPanel.setAccent(mHost.panelAccent());
        PageSwitchLayout layout = new PageSwitchLayout(mDensity, panelWidth,
            model.cells().size(), mPanel.titleLineHeightPx());
        int panelHeight = Math.round(layout.panelHeightPx());

        mLayer.getLocationOnScreen(mLayerAt);
        float bottomRaw = overAnchor ? anchorBottomRaw : anchorTopRaw;
        float topInLayer = Math.max(0f, bottomRaw - mLayerAt[1] - panelHeight);
        mPanelLeftRaw = mLayerAt[0] + gutter;
        mPanelTopRaw = mLayerAt[1] + topInLayer;
        place(gutter, topInLayer, panelWidth, panelHeight);

        mGesture.setLayout(layout);
        mGesture.setPanelBounds(0f, panelHeight);
        float x = rawX - mPanelLeftRaw;
        mGesture.down(x, rawY - mPanelTopRaw, model.currentIndex());
        mPanel.bind(model, layout, x);
        mPanel.setVisibility(View.VISIBLE);
        mOpen = true;
        for (int i = 0; i < mHitAreas.size(); i++) mHitAreas.get(i).setCovered(true);
        return true;
    }

    public void entryMove(float rawX, float rawY) {
        if (!mOpen) return;
        mGesture.move(rawX - mPanelLeftRaw, rawY - mPanelTopRaw);
        pushState();
    }

    public void entryUp(float rawX, float rawY) {
        if (!mOpen) return;
        float x = rawX - mPanelLeftRaw;
        PageSwitchGesture.Result result = mGesture.up(x, rawY - mPanelTopRaw);
        switch (result) {
            case COMMIT:
                commit(mGesture.committedIndex());
                break;
            case STAY_OPEN:
                mPanel.setState(mGesture.highlightIndex(), x, false);
                mLayer.setClickable(true);
                break;
            default:
                hide();
                break;
        }
    }

    public void entryCancel() {
        if (!mOpen) return;
        mGesture.cancel();
        hide();
    }

    // ---- The open panel -----------------------------------------------------------------------

    private boolean onLayerTouch(@NonNull MotionEvent ev) {
        if (!mOpen) return false;
        float x = ev.getRawX();
        float y = ev.getRawY();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mTapDownInPanel = x >= mPanelLeftRaw && x <= mPanelLeftRaw + mPanelWidthPx
                    && y >= mPanelTopRaw && y <= mPanelTopRaw + mPanelHeightPx;
                mDismissStream = !mTapDownInPanel;
                if (mDismissStream) {
                    mGesture.tapOutside();
                    hide();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (mTapDownInPanel && mOpen) {
                    PageSwitchGesture.Result r =
                        mGesture.tapInPanel(x - mPanelLeftRaw, y - mPanelTopRaw);
                    if (r == PageSwitchGesture.Result.COMMIT) commit(mGesture.committedIndex());
                    else hide();
                }
                mTapDownInPanel = false;
                mDismissStream = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                mTapDownInPanel = false;
                mDismissStream = false;
                return true;
            default:
                return true;
        }
    }

    private void pushState() {
        int highlight = mGesture.highlightIndex();
        if (mGesture.highlightChanged() && highlight >= 0) mHaptics.crossed();
        mPanel.setState(highlight, mGesture.fingerX(), mGesture.isTouching());
    }

    /** The page changes here and only here: the tick, the panel away, then the move. */
    private void commit(int index) {
        PageSwitchModel model = mModel;
        hide();
        if (model == null) return;
        List<PageSwitchModel.Cell> cells = model.cells();
        if (index < 0 || index >= cells.size()) return;
        mHaptics.committed();
        PageSwitchTargets.carryOut(cells.get(index).target, mHost);
    }

    private void hide() {
        mOpen = false;
        mModel = null;
        for (int i = 0; i < mHitAreas.size(); i++) mHitAreas.get(i).setCovered(false);
        mLayer.setClickable(false);
        mPanel.setVisibility(View.GONE);
        mPanel.clear();
    }

    /** Size and position, set once as the panel opens; layout params are compared before they are set. */
    private void place(int leftPx, float topPx, int widthPx, int heightPx) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mPanel.getLayoutParams();
        if (lp.width != widthPx || lp.height != heightPx) {
            lp.width = widthPx;
            lp.height = heightPx;
            mPanel.setLayoutParams(lp);
        }
        mPanel.setTranslationX(leftPx);
        mPanel.setTranslationY(topPx);
        mPanelWidthPx = widthPx;
        mPanelHeightPx = heightPx;
    }
}
