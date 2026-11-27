package com.termux.app.controlbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.termux.R;
import com.termux.app.tiling.WindowKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The control bar on screen (#424): one glass card of two rows, drawn by hand so a change of front
 * redraws it without laying anything out. Top row: the place badge, the marks ({@link MarkStrip})
 * and the reading slot (a placeholder until #428). Bottom row: the tools for what is in front (on
 * Home the pinned apps, as many as fit), then navigation mode and search.
 *
 * <p>The cells are placed when the rows, the pinned apps or the size change ({@link #place}),
 * never in {@link #onDraw}. The controller ({@link ControlBar}) decides what is shown and what a
 * touch does; this view only draws and reports.
 */
@SuppressLint("ViewConstructor")
public final class ControlBarView extends View {

    /** What a touch on the bar asks for. */
    public interface Listener {
        /** A tap on mark {@code index}, or a slide reaching it. */
        void onMark(int index);

        void onTool(@NonNull ControlBarTool tool);

        /** A tap on pinned app {@code index}. */
        void onPinnedApp(int index);
    }

    /** One pinned app: its icon and the name it is spoken by. */
    public static final class PinnedApp {
        @Nullable public final Drawable icon;
        @NonNull public final CharSequence label;

        public PinnedApp(@Nullable Drawable icon, @NonNull CharSequence label) {
            this.icon = icon;
            this.label = label;
        }
    }

    static final float BADGE_HEIGHT_DP = 32f;
    static final float BADGE_ICON_DP = 17f;
    static final float MARK_WIDTH_DP = 32f;
    static final float MARK_HEIGHT_DP = 36f;
    static final float DOT_CELL_DP = 18f;
    static final float DOT_DP = 7f;
    static final float MARK_GAP_DP = 2f;
    static final float MARK_ICON_DP = 17f;
    static final float MARK_CORNER_DP = 12f;
    static final float TOOL_DP = 40f;
    static final float TOOL_ICON_DP = 21f;
    static final float TOOL_CORNER_DP = 14f;
    static final float PINNED_ICON_DP = 30f;
    static final float READING_WIDTH_DP = 56f;
    static final float INNER_GAP_DP = 6f;
    static final float SIDE_PAD_DP = 6f;

    @NonNull private final Listener mListener;
    private final float mDensity;
    private final int mTouchSlop;
    @NonNull private final MarkSlide mSlide;
    @NonNull private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final Map<ControlBarTool, Drawable> mToolIcons = new EnumMap<>(ControlBarTool.class);
    @NonNull private final Map<WindowKind, Drawable> mKindIcons = new EnumMap<>(WindowKind.class);
    @Nullable private final Drawable mHomeIcon;

    @Nullable private ControlBarRows mRows;
    @NonNull private List<PinnedApp> mPinned = Collections.emptyList();
    @NonNull private final Access mAccess;
    private int mInk = 0xFFE6E1E5;
    private int mAccent = 0xFFA8C7FA;
    private float mCornerPx;

    // Placed by place(): the cells in view coordinates.
    @NonNull private final RectF mBadge = new RectF();
    @NonNull private final RectF mReading = new RectF();
    @NonNull private MarkStrip mStrip = new MarkStrip(0, 0f, 0f, 0f);
    private float mStripLeft;
    private float mTopRowTop;
    private float mTopRowBottom;
    @NonNull private final List<RectF> mToolCells = new ArrayList<>();
    @NonNull private final List<ControlBarTool> mToolOrder = new ArrayList<>();
    @NonNull private final List<RectF> mPinnedCells = new ArrayList<>();
    @NonNull private final Rect mIconBounds = new Rect();
    @NonNull private final RectF mScratch = new RectF();

    // The finger.
    private static final int TOUCH_NONE = 0;
    private static final int TOUCH_MARKS = 1;
    private static final int TOUCH_TOOL = 2;
    private static final int TOUCH_PINNED = 3;
    private int mTouch = TOUCH_NONE;
    private int mTouchIndex = -1;
    private float mDownX;
    private float mDownY;

    public ControlBarView(@NonNull Context context, @NonNull Listener listener) {
        super(context);
        mListener = listener;
        mDensity = getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mSlide = new MarkSlide(mTouchSlop);
        mText.setTextAlign(Paint.Align.LEFT);
        mText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        mText.setTextSize(15f * getResources().getConfiguration().fontScale * mDensity);
        mToolIcons.put(ControlBarTool.DRAWER, icon(R.drawable.ic_ms_apps));
        mToolIcons.put(ControlBarTool.KEYBOARD, icon(R.drawable.ic_ms_keyboard));
        mToolIcons.put(ControlBarTool.SPLIT, icon(R.drawable.ic_ms_vertical_split));
        mToolIcons.put(ControlBarTool.NEW_TERMINAL, icon(R.drawable.ic_assistant_add));
        mToolIcons.put(ControlBarTool.WINDOW_MENU, icon(R.drawable.ic_ms_more_horiz));
        mToolIcons.put(ControlBarTool.MOUSE_MODE, icon(R.drawable.ic_ms_mouse));
        // Placeholder until the four-arrow navigation glyph is drawn.
        mToolIcons.put(ControlBarTool.NAVIGATION, icon(R.drawable.ic_ms_swipe));
        mToolIcons.put(ControlBarTool.SEARCH, icon(R.drawable.ic_ms_search));
        mKindIcons.put(WindowKind.SHELL, icon(R.drawable.ic_ms_terminal));
        mKindIcons.put(WindowKind.LINUX_APP, icon(R.drawable.ic_distro_linux));
        mKindIcons.put(WindowKind.DISPLAY, icon(R.drawable.ic_ms_desktop_windows));
        mKindIcons.put(WindowKind.ASSISTANT, icon(R.drawable.ic_ms_auto_awesome));
        mHomeIcon = icon(R.drawable.ic_ms_home);
        setContentDescription(context.getString(R.string.control_bar_description));
        mAccess = new Access();
        ViewCompat.setAccessibilityDelegate(this, mAccess);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mCornerPx);
            }
        });
    }

    @Nullable
    private Drawable icon(@DrawableRes int id) {
        Drawable drawable = ContextCompat.getDrawable(getContext(), id);
        return drawable == null ? null : drawable.mutate();
    }

    // ---- what the controller sets ----------------------------------------------------------------

    /** The rows to show; nothing happens when they are the ones shown. */
    public void setRows(@NonNull ControlBarRows rows) {
        if (rows.equals(mRows)) return;
        mRows = rows;
        place();
        invalidate();
    }

    @Nullable
    ControlBarRows rows() {
        return mRows;
    }

    /** Home's pinned apps, in order; the view keeps these icon instances. */
    public void setPinnedApps(@NonNull List<PinnedApp> apps) {
        mPinned = new ArrayList<>(apps);
        place();
        invalidate();
    }

    /** The bar's inks: {@code ink} for marks and tools, {@code accent} for what is lit. */
    public void setInks(int ink, int accent) {
        if (mInk == ink && mAccent == accent) return;
        mInk = ink;
        mAccent = accent;
        invalidate();
    }

    /** The card's corner radius; its glass is the background the controller sets. */
    public void setCornerRadiusPx(float radiusPx) {
        if (mCornerPx == radiusPx) return;
        mCornerPx = radiusPx;
        invalidateOutline();
    }

    // ---- placing ---------------------------------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        place();
    }

    private float dp(float value) {
        return value * mDensity;
    }

    /** Places every cell for the rows, the pinned apps and the size. */
    private void place() {
        placeCells();
        mAccess.invalidateRoot();
    }

    private void placeCells() {
        int width = getWidth();
        int height = getHeight();
        mToolCells.clear();
        mToolOrder.clear();
        mPinnedCells.clear();
        ControlBarRows rows = mRows;
        if (rows == null || width <= 0 || height <= 0) return;
        float pad = dp(ControlBarPlacement.PAD_DP);
        float side = dp(SIDE_PAD_DP);
        float row = (height - 2 * pad) / 2f;
        mTopRowTop = pad;
        mTopRowBottom = pad + row;
        float topCentre = pad + row / 2f;
        float bottomCentre = pad + row + row / 2f;

        // Top row: badge, marks, reading slot.
        float badgeHeight = dp(BADGE_HEIGHT_DP);
        float badgeWidth = dp(8f) + dp(BADGE_ICON_DP) + dp(10f);
        if (!rows.home) {
            badgeWidth += dp(6f) + mText.measureText(String.valueOf(rows.workspaceNumber));
            if (rows.badgeKind == null) badgeWidth -= dp(BADGE_ICON_DP) + dp(6f);
        }
        mBadge.set(side, topCentre - badgeHeight / 2f, side + badgeWidth, topCentre + badgeHeight / 2f);
        float readingWidth = dp(READING_WIDTH_DP);
        mReading.set(width - side - readingWidth, topCentre - badgeHeight / 2f, width - side,
            topCentre + badgeHeight / 2f);
        mStripLeft = mBadge.right + dp(INNER_GAP_DP);
        float stripLength = Math.max(0f, mReading.left - dp(INNER_GAP_DP) - mStripLeft);
        float cell = dp(rows.home ? DOT_CELL_DP : MARK_WIDTH_DP);
        mStrip = new MarkStrip(rows.marks.size(), cell, dp(MARK_GAP_DP), stripLength);

        // Bottom row: the trailing tools from the right, then the front's tools from the left.
        float tool = dp(TOOL_DP);
        float gap = dp(INNER_GAP_DP);
        float right = width - side;
        List<RectF> trailing = new ArrayList<>();
        for (int i = rows.trailing.size() - 1; i >= 0; i--) {
            trailing.add(0, new RectF(right - tool, bottomCentre - tool / 2f, right, bottomCentre + tool / 2f));
            right -= tool + gap;
        }
        float left = side;
        for (ControlBarTool each : rows.tools) {
            if (each == ControlBarTool.PINNED_APPS) {
                // As many pinned apps as fit before the trailing tools.
                for (int i = 0; i < mPinned.size() && left + tool <= right + gap / 2f; i++) {
                    mPinnedCells.add(new RectF(left, bottomCentre - tool / 2f, left + tool, bottomCentre + tool / 2f));
                    left += tool + gap;
                }
                continue;
            }
            if (left + tool > right + gap / 2f) break;
            mToolCells.add(new RectF(left, bottomCentre - tool / 2f, left + tool, bottomCentre + tool / 2f));
            mToolOrder.add(each);
            left += tool + gap;
        }
        mToolCells.addAll(trailing);
        mToolOrder.addAll(rows.trailing);
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        ControlBarRows rows = mRows;
        if (rows == null) return;
        drawBadge(canvas, rows);
        drawMarks(canvas, rows);
        // The reading slot, empty until the readings come (#428).
        mFill.setColor(ColorUtils.setAlphaComponent(mInk, 20));
        canvas.drawRoundRect(mReading, mReading.height() / 2f, mReading.height() / 2f, mFill);
        for (int i = 0; i < mToolCells.size(); i++) {
            drawIcon(canvas, mToolIcons.get(mToolOrder.get(i)), mToolCells.get(i), dp(TOOL_ICON_DP),
                ColorUtils.setAlphaComponent(mInk, 220));
        }
        for (int i = 0; i < mPinnedCells.size(); i++) {
            Drawable icon = mPinned.get(i).icon;
            if (icon == null) continue;
            RectF cell = mPinnedCells.get(i);
            int size = Math.round(dp(PINNED_ICON_DP));
            int l = Math.round(cell.centerX() - size / 2f);
            int t = Math.round(cell.centerY() - size / 2f);
            icon.setBounds(l, t, l + size, t + size);
            icon.draw(canvas);
        }
    }

    private void drawBadge(@NonNull Canvas canvas, @NonNull ControlBarRows rows) {
        mFill.setColor(ColorUtils.setAlphaComponent(mAccent, 51));
        canvas.drawRoundRect(mBadge, mBadge.height() / 2f, mBadge.height() / 2f, mFill);
        float iconSize = dp(BADGE_ICON_DP);
        float x = mBadge.left + dp(8f);
        Drawable icon = rows.home ? mHomeIcon : rows.badgeKind == null ? null : mKindIcons.get(rows.badgeKind);
        if (!rows.home) {
            String number = String.valueOf(rows.workspaceNumber);
            mText.setColor(mAccent);
            Paint.FontMetrics metrics = mText.getFontMetrics();
            float baseline = mBadge.centerY() - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(number, x, baseline, mText);
            x += mText.measureText(number) + dp(6f);
        }
        if (icon != null) {
            mScratch.set(x, mBadge.centerY() - iconSize / 2f, x + iconSize, mBadge.centerY() + iconSize / 2f);
            drawIcon(canvas, icon, mScratch, iconSize, mAccent);
        }
    }

    private void drawMarks(@NonNull Canvas canvas, @NonNull ControlBarRows rows) {
        float centre = (mTopRowTop + mTopRowBottom) / 2f;
        float markHeight = Math.min(dp(MARK_HEIGHT_DP), mTopRowBottom - mTopRowTop);
        for (int i = 0; i < mStrip.count(); i++) {
            ControlBarRows.Mark mark = rows.marks.get(i);
            float l = mStripLeft + mStrip.cellStart(i);
            float r = mStripLeft + mStrip.cellEnd(i);
            if (mark.kind == null) {
                // A Home page's dot.
                mFill.setColor(mark.lit ? mAccent : ColorUtils.setAlphaComponent(mInk, 110));
                canvas.drawCircle((l + r) / 2f, centre, dp(DOT_DP) / 2f, mFill);
                continue;
            }
            mScratch.set(l, centre - markHeight / 2f, r, centre + markHeight / 2f);
            if (mark.lit) {
                mFill.setColor(ColorUtils.setAlphaComponent(mAccent, 61));
                canvas.drawRoundRect(mScratch, dp(MARK_CORNER_DP), dp(MARK_CORNER_DP), mFill);
            }
            // The mark's foot: a short bar, the accent when lit.
            float footInset = Math.min(dp(10f), mScratch.width() / 3f);
            mFill.setColor(mark.lit ? mAccent : ColorUtils.setAlphaComponent(mInk, 51));
            canvas.drawRoundRect(mScratch.left + footInset, mScratch.bottom - dp(6f),
                mScratch.right - footInset, mScratch.bottom - dp(3f), dp(2f), dp(2f), mFill);
            drawIcon(canvas, mKindIcons.get(mark.kind), mScratch,
                Math.min(dp(MARK_ICON_DP), mScratch.width() - dp(4f)),
                mark.lit ? mAccent : ColorUtils.setAlphaComponent(mInk, 170));
        }
    }

    private void drawIcon(@NonNull Canvas canvas, @Nullable Drawable icon, @NonNull RectF cell,
                          float size, int color) {
        if (icon == null || size <= 0f) return;
        int s = Math.round(size);
        int l = Math.round(cell.centerX() - s / 2f);
        int t = Math.round(cell.centerY() - s / 2f);
        mIconBounds.set(l, t, l + s, t + s);
        icon.setBounds(mIconBounds);
        icon.setTint(color);
        icon.draw(canvas);
    }

    // ---- touch -----------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = x;
                mDownY = y;
                mTouch = TOUCH_NONE;
                mTouchIndex = -1;
                if (y >= mTopRowTop && y <= mTopRowBottom && mSlide.down(mStrip, x - mStripLeft)) {
                    mTouch = TOUCH_MARKS;
                } else if ((mTouchIndex = hit(mToolCells, x, y)) >= 0) {
                    mTouch = TOUCH_TOOL;
                } else if ((mTouchIndex = hit(mPinnedCells, x, y)) >= 0) {
                    mTouch = TOUCH_PINNED;
                }
                // The bar keeps every touch that lands on it, so nothing under it moves.
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mTouch == TOUCH_MARKS) {
                    int index = mSlide.move(x - mStripLeft);
                    if (index >= 0) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        mListener.onMark(index);
                    }
                } else if (mTouch != TOUCH_NONE
                    && Math.hypot(x - mDownX, y - mDownY) > mTouchSlop) {
                    mTouch = TOUCH_NONE;
                }
                return true;
            case MotionEvent.ACTION_UP: {
                int touch = mTouch;
                int index = mTouchIndex;
                mTouch = TOUCH_NONE;
                if (touch == TOUCH_MARKS) {
                    int tap = mSlide.up();
                    if (tap >= 0) mListener.onMark(tap);
                } else if (touch == TOUCH_TOOL && index < mToolOrder.size()) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    mListener.onTool(mToolOrder.get(index));
                } else if (touch == TOUCH_PINNED) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    mListener.onPinnedApp(index);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                mSlide.cancel();
                mTouch = TOUCH_NONE;
                return true;
            default:
                return true;
        }
    }

    // ---- accessibility ---------------------------------------------------------------------------

    @Override
    protected boolean dispatchHoverEvent(@NonNull MotionEvent event) {
        return mAccess.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    /** Virtual ids: a mark's index, then the tools from {@link #TOOL_BASE}, the apps from {@link #APP_BASE}. */
    static final int TOOL_BASE = 1000;
    static final int APP_BASE = 2000;

    /** The virtual node under {@code (x, y)}, or {@link ExploreByTouchHelper#INVALID_ID}. */
    int virtualViewAt(float x, float y) {
        if (y >= mTopRowTop && y <= mTopRowBottom) {
            int mark = mStrip.indexAt(x - mStripLeft);
            if (mark >= 0) return mark;
        }
        int tool = hit(mToolCells, x, y);
        if (tool >= 0) return TOOL_BASE + tool;
        int app = hit(mPinnedCells, x, y);
        if (app >= 0) return APP_BASE + app;
        return ExploreByTouchHelper.INVALID_ID;
    }

    /** What a node is spoken as; null for an id with no node. */
    @Nullable
    CharSequence labelOf(int id) {
        ControlBarRows rows = mRows;
        if (rows == null) return null;
        Context context = getContext();
        if (id >= APP_BASE) {
            int index = id - APP_BASE;
            return index < mPinnedCells.size() ? mPinned.get(index).label : null;
        }
        if (id >= TOOL_BASE) {
            int index = id - TOOL_BASE;
            return index < mToolOrder.size() ? context.getString(toolLabel(mToolOrder.get(index))) : null;
        }
        if (id < 0 || id >= mStrip.count()) return null;
        ControlBarRows.Mark mark = rows.marks.get(id);
        if (mark.kind == null) {
            return context.getString(R.string.control_bar_home_page, id + 1, rows.marks.size());
        }
        String name = mark.name != null && !mark.name.isEmpty() ? mark.name : context.getString(kindLabel(mark.kind));
        return context.getString(R.string.control_bar_window, name, id + 1, rows.marks.size());
    }

    /** Runs node {@code id}'s tap; false for an id with no node. */
    boolean clickVirtualView(int id) {
        if (labelOf(id) == null) return false;
        if (id >= APP_BASE) mListener.onPinnedApp(id - APP_BASE);
        else if (id >= TOOL_BASE) mListener.onTool(mToolOrder.get(id - TOOL_BASE));
        else mListener.onMark(id);
        return true;
    }

    @Nullable
    private RectF boundsOf(int id) {
        if (id >= APP_BASE) return id - APP_BASE < mPinnedCells.size() ? mPinnedCells.get(id - APP_BASE) : null;
        if (id >= TOOL_BASE) return id - TOOL_BASE < mToolCells.size() ? mToolCells.get(id - TOOL_BASE) : null;
        if (id < 0 || id >= mStrip.count()) return null;
        return new RectF(mStripLeft + mStrip.cellStart(id), mTopRowTop, mStripLeft + mStrip.cellEnd(id),
            mTopRowBottom);
    }

    private static int toolLabel(@NonNull ControlBarTool tool) {
        switch (tool) {
            case DRAWER: return R.string.control_bar_tool_drawer;
            case KEYBOARD: return R.string.control_bar_tool_keyboard;
            case SPLIT: return R.string.control_bar_tool_split;
            case NEW_TERMINAL: return R.string.control_bar_tool_new_terminal;
            case WINDOW_MENU: return R.string.control_bar_tool_window_menu;
            case MOUSE_MODE: return R.string.control_bar_tool_mouse_mode;
            case NAVIGATION: return R.string.control_bar_tool_navigation;
            case SEARCH: return R.string.control_bar_tool_search;
            case PINNED_APPS:
            default: return R.string.control_bar_description;
        }
    }

    private static int kindLabel(@NonNull WindowKind kind) {
        switch (kind) {
            case LINUX_APP: return R.string.control_bar_kind_linux_app;
            case DISPLAY: return R.string.control_bar_kind_display;
            case ASSISTANT: return R.string.control_bar_kind_assistant;
            case SHELL:
            default: return R.string.control_bar_kind_terminal;
        }
    }

    /** The marks, tools and pinned apps as nodes TalkBack can reach and tap. */
    private final class Access extends ExploreByTouchHelper {
        Access() {
            super(ControlBarView.this);
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            return virtualViewAt(x, y);
        }

        @Override
        protected void getVisibleVirtualViews(List<Integer> ids) {
            for (int i = 0; i < mStrip.count(); i++) ids.add(i);
            for (int i = 0; i < mToolCells.size(); i++) ids.add(TOOL_BASE + i);
            for (int i = 0; i < mPinnedCells.size(); i++) ids.add(APP_BASE + i);
        }

        @Override
        @SuppressWarnings("deprecation")
        protected void onPopulateNodeForVirtualView(int id, @NonNull AccessibilityNodeInfoCompat node) {
            CharSequence label = labelOf(id);
            RectF bounds = boundsOf(id);
            node.setContentDescription(label != null ? label : "");
            Rect rect = new Rect();
            if (bounds != null) bounds.round(rect);
            node.setBoundsInParent(rect);
            if (label == null) return;
            node.setClickable(true);
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            ControlBarRows rows = mRows;
            if (id < TOOL_BASE && rows != null && id < rows.marks.size()) {
                node.setSelected(rows.marks.get(id).lit);
            }
        }

        @Override
        protected boolean onPerformActionForVirtualView(int id, int action, @Nullable Bundle arguments) {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false;
            boolean done = clickVirtualView(id);
            if (done) sendEventForVirtualView(id, AccessibilityEvent.TYPE_VIEW_CLICKED);
            return done;
        }
    }

    private static int hit(@NonNull List<RectF> cells, float x, float y) {
        for (int i = 0; i < cells.size(); i++) if (cells.get(i).contains(x, y)) return i;
        return -1;
    }
}
