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
import com.termux.app.launcher.notifications.NotificationDots;
import com.termux.app.tiling.WindowKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The control bar on screen (#424, #427): one glass card, drawn by hand so a change of front
 * redraws it without laying anything out. In its {@link ControlBarForm#TWO_ROWS} form the top row
 * holds the place badge, the marks ({@link MarkStrip}) and the reading slot (a placeholder until
 * #428), the bottom row the tools for what is in front (on Home the pinned apps, as many as fit),
 * then navigation mode and search. {@link ControlBarForm#ONE_ROW} holds the badge, the tools,
 * navigation and search in one row; {@link ControlBarForm#RAIL} the same zones top to bottom
 * ({@link ControlBarRows#lines}).
 *
 * <p>The cells are placed when the form, the rows, the pinned apps or the size change
 * ({@link #place}), never in {@link #onDraw}. The controller ({@link ControlBar}) decides what is shown and what a
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

        /** A hold on pinned app {@code index}, whose cell is {@code cell} in the bar: its notifications and actions. */
        default void onPinnedAppHeld(int index, @NonNull Rect cell) {
        }

        /** A tap on the reading slot: the next reading. */
        default void onReading() {
        }

        /** A hold on the reading slot, whose pill is {@code cell} in the bar: the card of every reading. */
        default void onReadingHeld(@NonNull Rect cell) {
        }

        /** A hold on the place badge (a Workspace's number and icon): its menu. */
        default void onPlaceHeld() {
        }
    }

    /** One pinned app: its icon and the name it is spoken by. */
    public static final class PinnedApp {
        @Nullable public final Drawable icon;
        @NonNull public final CharSequence label;
        /** The app's package, which its notification dot is looked up by; null for none. */
        @Nullable public final String packageName;

        public PinnedApp(@Nullable Drawable icon, @NonNull CharSequence label) {
            this(icon, label, null);
        }

        public PinnedApp(@Nullable Drawable icon, @NonNull CharSequence label,
                         @Nullable String packageName) {
            this.icon = icon;
            this.label = label;
            this.packageName = packageName;
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
    /** The reading slot's width in the two rows (an icon and a number with its unit). */
    static final float READING_WIDTH_DP = 72f;
    /** Its width in the one row (an icon and a number), and its length down the rail. */
    static final float READING_COMPACT_DP = 60f;
    static final float READING_RAIL_DP = 52f;
    static final float READING_ICON_DP = 16f;
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
    @NonNull private final Map<ReadingKind, Drawable> mReadingIcons = new EnumMap<>(ReadingKind.class);
    /** Whether the form holds the reading slot ({@code bar.reading}); the controller sets it. */
    private boolean mReadingShown = true;
    @Nullable private ReadingValue mReadingValue;

    @Nullable private ControlBarRows mRows;
    @NonNull private ControlBarForm mForm = ControlBarForm.TWO_ROWS;
    @NonNull private List<PinnedApp> mPinned = Collections.emptyList();
    @NonNull private final Access mAccess;
    private int mInk = 0xFFE6E1E5;
    private int mAccent = 0xFFA8C7FA;
    private float mCornerPx;
    /** Standing flush with the screen's bottom (margin 0): the bottom corners are square. */
    private boolean mFlush;
    /** Edit mode's hand on the bar: while set, a tap on the bar calls it and nothing else answers. */
    @Nullable private Runnable mEditTap;

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
    @NonNull private Set<String> mDotPackages = Collections.emptySet();
    @NonNull private final float[] mDotXY = new float[2];
    @NonNull private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final Runnable mHold = this::fireHold;
    @NonNull private final Rect mIconBounds = new Rect();
    @NonNull private final RectF mScratch = new RectF();

    // The finger.
    private static final int TOUCH_NONE = 0;
    private static final int TOUCH_MARKS = 1;
    private static final int TOUCH_TOOL = 2;
    private static final int TOUCH_PINNED = 3;
    private static final int TOUCH_READING = 4;
    private static final int TOUCH_BADGE = 5;
    private int mTouch = TOUCH_NONE;
    private int mTouchIndex = -1;
    private float mDownX;
    private float mDownY;
    private boolean mEditTapLive;

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
        mReadingIcons.put(ReadingKind.CPU, icon(R.drawable.ic_ms_speed));
        mReadingIcons.put(ReadingKind.GPU, icon(R.drawable.ic_ms_developer_board));
        mReadingIcons.put(ReadingKind.RAM, icon(R.drawable.ic_ms_memory));
        mReadingIcons.put(ReadingKind.BATTERY, icon(R.drawable.ic_ms_battery_full));
        mReadingIcons.put(ReadingKind.WEATHER, icon(R.drawable.ic_ms_cloud));
        mReadingIcons.put(ReadingKind.MEDIA, icon(R.drawable.ic_ms_music_note));
        setContentDescription(context.getString(R.string.control_bar_description));
        mAccess = new Access();
        ViewCompat.setAccessibilityDelegate(this, mAccess);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                if (mFlush) {
                    // Flush with the screen's bottom: the top corners round, the bottom ones are square.
                    android.graphics.Path path = new android.graphics.Path();
                    path.addRoundRect(0, 0, view.getWidth(), view.getHeight(), new float[] {
                        mCornerPx, mCornerPx, mCornerPx, mCornerPx, 0f, 0f, 0f, 0f},
                        android.graphics.Path.Direction.CW);
                    outline.setConvexPath(path);
                } else {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mCornerPx);
                }
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

    /** The bar's shape; nothing happens when it is the one shown. */
    public void setForm(@NonNull ControlBarForm form) {
        if (form == mForm) return;
        mForm = form;
        place();
        invalidate();
    }

    @NonNull
    ControlBarForm form() {
        return mForm;
    }

    /**
     * The reading slot: {@code value} is what it shows, null while it has nothing yet; {@code shown}
     * false takes the slot out of every form ({@code bar.reading} off). Nothing happens when both
     * are the ones set.
     */
    public void setReading(boolean shown, @Nullable ReadingValue value) {
        boolean placeAgain = shown != mReadingShown;
        if (!placeAgain && java.util.Objects.equals(value, mReadingValue)) return;
        mReadingShown = shown;
        ReadingValue before = mReadingValue;
        mReadingValue = value;
        // A new number changes no cell (the widths are fixed); only a kind that has none, or the slot
        // coming or going, places again.
        if (placeAgain || (before == null) != (value == null)) place();
        invalidate();
    }

    boolean readingShown() {
        return mReadingShown;
    }

    @Nullable
    ReadingValue readingValue() {
        return mReadingValue;
    }

    /** Home's pinned apps, in order; the view keeps these icon instances. */
    public void setPinnedApps(@NonNull List<PinnedApp> apps) {
        mPinned = new ArrayList<>(apps);
        place();
        invalidate();
    }

    /** The packages whose pinned icons carry a notification dot; empty while dots are off. */
    public void setDotPackages(@NonNull Set<String> packages) {
        if (mDotPackages.equals(packages)) return;
        mDotPackages = packages.isEmpty() ? Collections.emptySet() : new HashSet<>(packages);
        invalidate();
    }

    /** The bar's inks: {@code ink} for marks and tools, {@code accent} for what is lit. */
    public void setInks(int ink, int accent) {
        if (mInk == ink && mAccent == accent) return;
        mInk = ink;
        mAccent = accent;
        invalidate();
    }

    /** Whether the bar stands flush with the screen's bottom: its bottom corners are square. */
    public void setFlush(boolean flush) {
        if (mFlush == flush) return;
        mFlush = flush;
        invalidateOutline();
    }

    /**
     * Edit mode's tap: while {@code tap} is set, a tap on the bar calls it and no mark, tool,
     * pinned app or reading answers (they would act while the bar is being edited); null stops.
     */
    public void setEditTap(@Nullable Runnable tap) {
        mEditTap = tap;
        mTouch = TOUCH_NONE;
        removeCallbacks(mHold);
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
        mBadge.setEmpty();
        mReading.setEmpty();
        mStrip = new MarkStrip(0, 0f, 0f, 0f);
        mTopRowTop = 0f;
        mTopRowBottom = 0f;
        ControlBarRows rows = mRows;
        if (rows == null || width <= 0 || height <= 0) return;
        List<List<ControlBarRows.Zone>> lines = ControlBarRows.lines(mForm, mReadingShown);
        if (mForm.isRail()) {
            // One line down the rail, from its top to its bottom.
            float pad = dp(SIDE_PAD_DP);
            float tool = Math.min(dp(TOOL_DP), Math.max(0f, width - 2 * dp(ControlBarPlacement.PAD_DP)));
            for (List<ControlBarRows.Zone> line : lines) {
                placeLine(rows, line, true, width / 2f, tool, pad, height - pad);
            }
            return;
        }
        // Rows share the height between the card's padding, each zone on its row's centre.
        float pad = dp(ControlBarPlacement.PAD_DP);
        float side = dp(SIDE_PAD_DP);
        float row = (height - 2 * pad) / lines.size();
        for (int i = 0; i < lines.size(); i++) {
            float top = pad + i * row;
            if (lines.get(i).contains(ControlBarRows.Zone.MARKS)) {
                mTopRowTop = top;
                mTopRowBottom = top + row;
            }
            placeLine(rows, lines.get(i), false, top + row / 2f, dp(TOOL_DP), side, width - side);
        }
    }

    /** Where {@code zone} is drawn (its first cell); empty when the form has none. */
    @NonNull
    RectF zoneBounds(@NonNull ControlBarRows.Zone zone) {
        switch (zone) {
            case PLACE: return new RectF(mBadge);
            case READING: return new RectF(mReading);
            case MARKS:
                return mStrip.count() == 0 ? new RectF() : new RectF(mStripLeft + mStrip.cellStart(0),
                    mTopRowTop, mStripLeft + mStrip.cellEnd(0), mTopRowBottom);
            case TOOLS:
                if (!mPinnedCells.isEmpty()) return new RectF(mPinnedCells.get(0));
                for (int i = 0; i < mToolOrder.size(); i++) {
                    ControlBarTool tool = mToolOrder.get(i);
                    if (tool != ControlBarTool.NAVIGATION && tool != ControlBarTool.SEARCH)
                        return new RectF(mToolCells.get(i));
                }
                return new RectF();
            default:
                ControlBarTool wanted = zone == ControlBarRows.Zone.NAVIGATION
                    ? ControlBarTool.NAVIGATION : ControlBarTool.SEARCH;
                int index = mToolOrder.indexOf(wanted);
                return index < 0 ? new RectF() : new RectF(mToolCells.get(index));
        }
    }

    /** The badge's width in a row: its icon, and on a Workspace its number. */
    private float badgeRowWidth(@NonNull ControlBarRows rows) {
        float badgeWidth = dp(8f) + dp(BADGE_ICON_DP) + dp(10f);
        if (!rows.home) {
            badgeWidth += dp(6f) + mText.measureText(String.valueOf(rows.workspaceNumber));
            if (rows.badgeKind == null) badgeWidth -= dp(BADGE_ICON_DP) + dp(6f);
        }
        return badgeWidth;
    }

    /** The badge's length along the rail: on a Workspace its number above its icon. */
    private float badgeRailLength(@NonNull ControlBarRows rows) {
        return rows.home || rows.badgeKind == null ? dp(BADGE_HEIGHT_DP)
            : dp(6f) + textHeight() + dp(4f) + dp(BADGE_ICON_DP) + dp(8f);
    }

    /** How long a fixed zone runs along its line. */
    private float zoneLength(@NonNull ControlBarRows rows, @NonNull ControlBarRows.Zone zone,
                             boolean vertical, float tool) {
        switch (zone) {
            case PLACE: return vertical ? badgeRailLength(rows) : badgeRowWidth(rows);
            case READING:
                if (vertical) return dp(READING_RAIL_DP);
                return dp(mForm == ControlBarForm.TWO_ROWS ? READING_WIDTH_DP : READING_COMPACT_DP);
            default: return tool;
        }
    }

    /**
     * Places one line's zones in their order ({@link ControlBarRows#lines}) from {@code start} to
     * {@code end} along it, on {@code centre} across it: the zones before the line's filling zone
     * (the marks, or the front's tools) packed from the start, the ones after it from the end, and
     * the filling zone between them.
     */
    private void placeLine(@NonNull ControlBarRows rows, @NonNull List<ControlBarRows.Zone> zones,
                           boolean vertical, float centre, float tool, float start, float end) {
        float gap = dp(INNER_GAP_DP);
        int fill = Math.max(zones.lastIndexOf(ControlBarRows.Zone.MARKS),
            zones.lastIndexOf(ControlBarRows.Zone.TOOLS));
        if (fill < 0) fill = zones.size();
        RectF[] cells = new RectF[zones.size()];
        float limit = end;
        for (int i = zones.size() - 1; i > fill; i--) {
            float length = zoneLength(rows, zones.get(i), vertical, tool);
            cells[i] = cell(vertical, centre, zones.get(i), tool, limit - length, limit);
            limit -= length + gap;
        }
        float cursor = start;
        for (int i = 0; i < fill && i < zones.size(); i++) {
            float length = zoneLength(rows, zones.get(i), vertical, tool);
            cells[i] = cell(vertical, centre, zones.get(i), tool, cursor, cursor + length);
            cursor += length + gap;
        }
        for (int i = 0; i < zones.size(); i++) {
            ControlBarRows.Zone zone = zones.get(i);
            switch (zone) {
                case PLACE:
                    mBadge.set(cells[i]);
                    break;
                case READING:
                    mReading.set(cells[i]);
                    break;
                case NAVIGATION:
                    mToolCells.add(cells[i]);
                    mToolOrder.add(ControlBarTool.NAVIGATION);
                    break;
                case SEARCH:
                    mToolCells.add(cells[i]);
                    mToolOrder.add(ControlBarTool.SEARCH);
                    break;
                case MARKS:
                    // Marks run along a row (the two rows' top); a rail has none.
                    if (vertical) break;
                    mStripLeft = cursor;
                    mStrip = new MarkStrip(rows.marks.size(), dp(rows.home ? DOT_CELL_DP : MARK_WIDTH_DP),
                        dp(MARK_GAP_DP), Math.max(0f, limit - cursor));
                    break;
                case TOOLS:
                    placeTools(rows, vertical, centre, tool, gap, cursor, limit);
                    break;
                default:
                    break;
            }
        }
    }

    /** The front's tools, or the pinned apps, as many as fit from {@code from} to {@code to}. */
    private void placeTools(@NonNull ControlBarRows rows, boolean vertical, float centre, float tool,
                            float gap, float from, float to) {
        float at = from;
        int count = rows.tools.size();
        for (int index = 0; index < count; index++) {
            ControlBarTool each = rows.tools.get(index);
            if (each == ControlBarTool.PINNED_APPS) {
                // The tools after the pinned apps keep their room: apps never push one out.
                int later = 0;
                for (int j = index + 1; j < count; j++) {
                    if (rows.tools.get(j) != ControlBarTool.PINNED_APPS) later++;
                }
                float limit = to - later * (tool + gap);
                for (int i = 0; i < mPinned.size() && at + tool <= limit + gap / 2f; i++) {
                    mPinnedCells.add(cell(vertical, centre, ControlBarRows.Zone.TOOLS, tool, at, at + tool));
                    at += tool + gap;
                }
                continue;
            }
            if (at + tool > to + gap / 2f) break;
            mToolCells.add(cell(vertical, centre, ControlBarRows.Zone.TOOLS, tool, at, at + tool));
            mToolOrder.add(each);
            at += tool + gap;
        }
    }

    /** A zone's cell from {@code a} to {@code b} along its line, centred across it. */
    @NonNull
    private RectF cell(boolean vertical, float centre, @NonNull ControlBarRows.Zone zone, float tool,
                       float a, float b) {
        // The badge and the reading slot are pills; a tool is a square cell.
        float across = !vertical && (zone == ControlBarRows.Zone.PLACE
            || zone == ControlBarRows.Zone.READING) ? dp(BADGE_HEIGHT_DP) : tool;
        return vertical ? new RectF(centre - across / 2f, a, centre + across / 2f, b)
            : new RectF(a, centre - across / 2f, b, centre + across / 2f);
    }

    private float textHeight() {
        Paint.FontMetrics metrics = mText.getFontMetrics();
        return metrics.descent - metrics.ascent;
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        ControlBarRows rows = mRows;
        if (rows == null) return;
        drawBadge(canvas, rows);
        drawMarks(canvas, rows);
        drawReading(canvas);
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
            if (NotificationDots.shows(true, mPinned.get(i).packageName, mDotPackages)) {
                float r = NotificationDots.radiusPx(size, mDensity);
                NotificationDots.centre(l, t, l + size, t + size, mDotXY);
                mDotPaint.setColor(mAccent);
                canvas.drawCircle(mDotXY[0], mDotXY[1], r, mDotPaint);
            }
        }
    }

    /**
     * The reading slot: a pill with the reading's icon and its number. The two rows add the unit; the
     * one row shows the number alone, and the rail stacks the number under the icon.
     */
    private void drawReading(@NonNull Canvas canvas) {
        if (mReading.isEmpty()) return;
        float radius = Math.min(mReading.height(), mReading.width()) / 2f;
        mFill.setColor(ColorUtils.setAlphaComponent(mInk, 20));
        canvas.drawRoundRect(mReading, radius, radius, mFill);
        ReadingValue value = mReadingValue;
        if (value == null) return;
        String text = mForm == ControlBarForm.TWO_ROWS ? value.full() : value.compact();
        float icon = dp(READING_ICON_DP);
        mText.setColor(ColorUtils.setAlphaComponent(mInk, 230));
        if (mForm.isRail()) {
            // The icon over the number, the pair centred in the pill.
            float block = text.isEmpty() ? icon : icon + dp(2f) + textHeight();
            float top = mReading.centerY() - block / 2f;
            mScratch.set(mReading.left, top, mReading.right, top + icon);
            drawIcon(canvas, mReadingIcons.get(value.kind), mScratch, icon, mText.getColor());
            if (!text.isEmpty()) drawReadingText(canvas, text, mReading.centerX(), top + icon + dp(2f));
            return;
        }
        float textWidth = text.isEmpty() ? 0f : mText.measureText(text);
        float gap = text.isEmpty() ? 0f : dp(3f);
        float left = mReading.centerX() - (icon + gap + textWidth) / 2f;
        mScratch.set(left, mReading.top, left + icon, mReading.bottom);
        drawIcon(canvas, mReadingIcons.get(value.kind), mScratch, icon, mText.getColor());
        if (!text.isEmpty()) drawReadingText(canvas, text, left + icon + gap + textWidth / 2f,
            mReading.centerY() - textHeight() / 2f);
    }

    /** {@code text} centred on {@code centreX} with its line's top at {@code top}. */
    private void drawReadingText(@NonNull Canvas canvas, @NonNull String text, float centreX, float top) {
        Paint.Align was = mText.getTextAlign();
        float size = mText.getTextSize();
        mText.setTextAlign(Paint.Align.CENTER);
        // A little under the bar's name size, so "100%" stays inside its pill.
        mText.setTextSize(size * 0.85f);
        Paint.FontMetrics metrics = mText.getFontMetrics();
        canvas.drawText(text, centreX, top - metrics.ascent, mText);
        mText.setTextSize(size);
        mText.setTextAlign(was);
    }

    private void drawBadge(@NonNull Canvas canvas, @NonNull ControlBarRows rows) {
        if (mBadge.isEmpty()) return;
        mFill.setColor(ColorUtils.setAlphaComponent(mAccent, 51));
        float corner = Math.min(mBadge.width(), mBadge.height()) / 2f;
        canvas.drawRoundRect(mBadge, corner, corner, mFill);
        float iconSize = dp(BADGE_ICON_DP);
        float x = mBadge.left + dp(8f);
        Drawable icon = rows.home ? mHomeIcon : rows.badgeKind == null ? null : mKindIcons.get(rows.badgeKind);
        if (mForm.isRail()) {
            drawRailBadge(canvas, rows, icon, iconSize);
            return;
        }
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

    /** The rail's badge: the Workspace's number above its icon, each centred. */
    private void drawRailBadge(@NonNull Canvas canvas, @NonNull ControlBarRows rows,
                               @Nullable Drawable icon, float iconSize) {
        float cx = mBadge.centerX();
        if (rows.home || rows.badgeKind == null) {
            if (!rows.home) {
                String number = String.valueOf(rows.workspaceNumber);
                mText.setColor(mAccent);
                Paint.FontMetrics metrics = mText.getFontMetrics();
                canvas.drawText(number, cx - mText.measureText(number) / 2f,
                    mBadge.centerY() - (metrics.ascent + metrics.descent) / 2f, mText);
            } else if (icon != null) {
                mScratch.set(cx - iconSize / 2f, mBadge.centerY() - iconSize / 2f, cx + iconSize / 2f,
                    mBadge.centerY() + iconSize / 2f);
                drawIcon(canvas, icon, mScratch, iconSize, mAccent);
            }
            return;
        }
        String number = String.valueOf(rows.workspaceNumber);
        mText.setColor(mAccent);
        Paint.FontMetrics metrics = mText.getFontMetrics();
        float top = mBadge.top + dp(6f);
        canvas.drawText(number, cx - mText.measureText(number) / 2f, top - metrics.ascent, mText);
        float iconTop = top + textHeight() + dp(4f);
        mScratch.set(cx - iconSize / 2f, iconTop, cx + iconSize / 2f, iconTop + iconSize);
        if (icon != null) drawIcon(canvas, icon, mScratch, iconSize, mAccent);
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
        Runnable editTap = mEditTap;
        if (editTap != null) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDownX = x;
                    mDownY = y;
                    mEditTapLive = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.hypot(x - mDownX, y - mDownY) > mTouchSlop) mEditTapLive = false;
                    break;
                case MotionEvent.ACTION_UP:
                    if (mEditTapLive) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        editTap.run();
                    }
                    mEditTapLive = false;
                    break;
                default:
                    mEditTapLive = false;
                    break;
            }
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = x;
                mDownY = y;
                mTouch = TOUCH_NONE;
                mTouchIndex = -1;
                if (mStrip.count() > 0 && y >= mTopRowTop && y <= mTopRowBottom
                        && mSlide.down(mStrip, x - mStripLeft)) {
                    mTouch = TOUCH_MARKS;
                } else if ((mTouchIndex = hit(mToolCells, x, y)) >= 0) {
                    mTouch = TOUCH_TOOL;
                } else if ((mTouchIndex = hit(mPinnedCells, x, y)) >= 0) {
                    mTouch = TOUCH_PINNED;
                    postDelayed(mHold, ViewConfiguration.getLongPressTimeout());
                } else if (!mReading.isEmpty() && mReading.contains(x, y)) {
                    mTouch = TOUCH_READING;
                    postDelayed(mHold, ViewConfiguration.getLongPressTimeout());
                } else if (badgeHoldable() && mBadge.contains(x, y)) {
                    mTouch = TOUCH_BADGE;
                    postDelayed(mHold, ViewConfiguration.getLongPressTimeout());
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
                    removeCallbacks(mHold);
                }
                return true;
            case MotionEvent.ACTION_UP: {
                removeCallbacks(mHold);
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
                } else if (touch == TOUCH_READING) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    mListener.onReading();
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(mHold);
                mSlide.cancel();
                mTouch = TOUCH_NONE;
                return true;
            default:
                return true;
        }
    }

    /** The finger stayed on a pinned app, the reading slot or the badge: hold it, and the tap that would follow is spent. */
    private void fireHold() {
        if (mTouch == TOUCH_BADGE) {
            mTouch = TOUCH_NONE;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            mListener.onPlaceHeld();
            return;
        }
        if (mTouch == TOUCH_READING) {
            mTouch = TOUCH_NONE;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            mListener.onReadingHeld(readingCell());
            return;
        }
        if (mTouch != TOUCH_PINNED || mTouchIndex < 0) return;
        int index = mTouchIndex;
        mTouch = TOUCH_NONE;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        RectF cell = mPinnedCells.get(index);
        mListener.onPinnedAppHeld(index, new Rect(Math.round(cell.left), Math.round(cell.top),
            Math.round(cell.right), Math.round(cell.bottom)));
    }

    /** The reading slot's pill in view coordinates, whole pixels. */
    @NonNull
    Rect readingCell() {
        return new Rect(Math.round(mReading.left), Math.round(mReading.top),
            Math.round(mReading.right), Math.round(mReading.bottom));
    }

    /** Whether the badge stands for a Workspace, whose menu a hold opens (Home's has none yet). */
    private boolean badgeHoldable() {
        ControlBarRows rows = mRows;
        return rows != null && !rows.home && !mBadge.isEmpty();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mHold);
        super.onDetachedFromWindow();
    }

    // ---- accessibility ---------------------------------------------------------------------------

    @Override
    protected boolean dispatchHoverEvent(@NonNull MotionEvent event) {
        return mAccess.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    /** Virtual ids: a mark's index, then the tools from {@link #TOOL_BASE}, the apps from {@link #APP_BASE}, the reading slot and the badge. */
    static final int TOOL_BASE = 1000;
    static final int APP_BASE = 2000;
    /** The reading slot's node. */
    static final int READING_NODE = 3000;
    /** The place badge on a Workspace, whose long click opens its menu. */
    static final int BADGE_NODE = 4000;

    /** The virtual node under {@code (x, y)}, or {@link ExploreByTouchHelper#INVALID_ID}. */
    int virtualViewAt(float x, float y) {
        if (badgeHoldable() && mBadge.contains(x, y)) return BADGE_NODE;
        if (mStrip.count() > 0 && y >= mTopRowTop && y <= mTopRowBottom) {
            int mark = mStrip.indexAt(x - mStripLeft);
            if (mark >= 0) return mark;
        }
        if (!mReading.isEmpty() && mReading.contains(x, y)) return READING_NODE;
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
        if (id == BADGE_NODE) return badgeHoldable() ? context.getString(R.string.control_bar_workspace_menu) : null;
        if (id == READING_NODE) {
            if (mReading.isEmpty()) return null;
            ReadingValue value = mReadingValue;
            if (value == null) return context.getString(R.string.control_bar_reading_none);
            String name = context.getString(readingLabel(value.kind));
            return value.number.isEmpty() ? name
                : context.getString(R.string.control_bar_reading, name, value.full());
        }
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
        if (labelOf(id) == null || id == BADGE_NODE) return false;
        // In edit mode a node's click is the bar's tap: it opens the card and launches nothing.
        if (mEditTap != null) {
            mEditTap.run();
            return true;
        }
        if (id == READING_NODE) mListener.onReading();
        else if (id >= APP_BASE) mListener.onPinnedApp(id - APP_BASE);
        else if (id >= TOOL_BASE) mListener.onTool(mToolOrder.get(id - TOOL_BASE));
        else mListener.onMark(id);
        return true;
    }

    @Nullable
    private RectF boundsOf(int id) {
        if (id == BADGE_NODE) return badgeHoldable() ? mBadge : null;
        if (id == READING_NODE) return mReading.isEmpty() ? null : mReading;
        if (id >= APP_BASE) return id - APP_BASE < mPinnedCells.size() ? mPinnedCells.get(id - APP_BASE) : null;
        if (id >= TOOL_BASE) return id - TOOL_BASE < mToolCells.size() ? mToolCells.get(id - TOOL_BASE) : null;
        if (id < 0 || id >= mStrip.count()) return null;
        return new RectF(mStripLeft + mStrip.cellStart(id), mTopRowTop, mStripLeft + mStrip.cellEnd(id),
            mTopRowBottom);
    }

    private static int readingLabel(@NonNull ReadingKind kind) {
        switch (kind) {
            case CPU: return R.string.control_bar_reading_cpu;
            case GPU: return R.string.control_bar_reading_gpu;
            case RAM: return R.string.control_bar_reading_ram;
            case WEATHER: return R.string.control_bar_reading_weather;
            case MEDIA: return R.string.control_bar_reading_media;
            case BATTERY:
            default: return R.string.control_bar_reading_battery;
        }
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
            if (!mReading.isEmpty()) ids.add(READING_NODE);
            if (badgeHoldable()) ids.add(BADGE_NODE);
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
            if (id == BADGE_NODE) {
                node.setLongClickable(true);
                node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK);
                return;
            }
            node.setClickable(true);
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            if (id == READING_NODE) {
                // The tap is the next reading; the card of every reading is the long-press action.
                node.setLongClickable(true);
                node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK);
            }
            ControlBarRows rows = mRows;
            if (id < TOOL_BASE && rows != null && id < rows.marks.size()) {
                node.setSelected(rows.marks.get(id).lit);
            }
        }

        @Override
        protected boolean onPerformActionForVirtualView(int id, int action, @Nullable Bundle arguments) {
            if (mEditTap != null && action == AccessibilityNodeInfoCompat.ACTION_LONG_CLICK
                    && labelOf(id) != null) {
                // A hold is no menu while the bar is edited: it opens the card like a tap.
                mEditTap.run();
                return true;
            }
            if (id == BADGE_NODE && action == AccessibilityNodeInfoCompat.ACTION_LONG_CLICK && badgeHoldable()) {
                mListener.onPlaceHeld();
                sendEventForVirtualView(id, AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
                return true;
            }
            if (action == AccessibilityNodeInfoCompat.ACTION_LONG_CLICK && id == READING_NODE
                    && !mReading.isEmpty()) {
                mListener.onReadingHeld(readingCell());
                sendEventForVirtualView(id, AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
                return true;
            }
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
