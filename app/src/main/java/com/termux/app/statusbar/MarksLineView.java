package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.GlassBackdropCache;
import com.termux.app.chrome.OnGlass;
import com.termux.app.terminal.WindowChipInk;
import com.termux.app.wall.pageswitch.MarksLine;
import com.termux.app.wall.pageswitch.MarksLook;
import com.termux.app.wall.pageswitch.PageBarFold;
import com.termux.app.wall.pageswitch.PageBarModel;
import com.termux.app.wall.pageswitch.PageSwitchTargets;

import java.util.Arrays;
import java.util.List;

/**
 * The Marks line: the status bar's pages as a line of marks, one per entry of the wall in the
 * wall's order, where the window chips stand under the Glyphs look. A row ({@link #setVertical}
 * false) draws along x from the start; a column on a side edge draws the same line along y. The
 * current page is a disc with its number or sign cut out, attention a small burst; entries that do
 * not fit the line's length fold as the page bar's do.
 *
 * <p>Later tickets (sliding along the line, the thin in-pane row) build on {@link #line()}: its
 * numbers are {@link MarksLine}'s (cells, centres, the slot under a position) and the shapes are
 * {@link MarksLook}'s.
 *
 * <p>{@link #setPages} is the one input: it compares and rebuilds the line only when the entries,
 * the attention or the length changed. Drawing allocates nothing and requests no layout.
 */
public final class MarksLineView extends View {

    /** What the activity takes a tap on a mark to: the page switcher's own navigation. */
    @Nullable private PageSwitchTargets.Navigator mNavigator;

    private final MarksLook mLook = new MarksLook();
    private final int mTouchSlop;
    @Nullable private PageBarModel mModel;
    @Nullable private boolean[] mAttention;
    @Nullable private MarksLine mLine;
    private boolean mVertical;
    private int mAccent;
    private int mNeutral;
    private int mError;
    @Nullable private ChromeInk mChromeInk;
    @NonNull private MarksLineInk mInk;
    private int mInkGeneration = -1;
    @NonNull private final Rect mBandRect = new Rect();
    @NonNull private final Rect mMeasuredRect = new Rect();
    private boolean mInkMeasured;
    /** The authored inks are the ones held, for this accent and chrome. */
    private boolean mAuthored;
    private float mDownPos;
    private float mDownX;
    private float mDownY;
    private boolean mDown;

    public MarksLineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mAccent = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(context, R.color.termux_primary));
        mNeutral = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(context, R.color.termux_on_surface));
        mError = MaterialColors.getColor(context, com.termux.shared.R.attr.termuxColorError,
            ContextCompat.getColor(context, R.color.termux_error));
        mInk = MarksLineInk.authored(mNeutral, mAccent, mError);
    }

    public void setNavigator(@Nullable PageSwitchTargets.Navigator navigator) {
        mNavigator = navigator;
    }

    /** Along y for a column on a side edge, along x for the row. */
    public void setVertical(boolean vertical) {
        if (mVertical == vertical) return;
        mVertical = vertical;
        rebuild();
    }

    /** The place's accent, the current mark's colour before it is made legible on the band. */
    public void setPlaceAccent(int accent) {
        if (mAccent == accent) return;
        mAccent = accent;
        mInkMeasured = false;
        mAuthored = false;
        syncInk();
        invalidate();
    }

    /**
     * Who can say what the line stands on. Null keeps the authored roles. The band is read, never
     * asked: the status ink pass owns the question ({@code ChromeInk#resolution}), as the window
     * column reads it.
     */
    public void setChromeInk(@Nullable ChromeInk ink) {
        if (mChromeInk == ink) return;
        mChromeInk = ink;
        mInkMeasured = false;
        mAuthored = false;
        mInkGeneration = -1;
        mMeasuredRect.setEmpty();
        syncInk();
        invalidate();
    }

    /**
     * Takes the wall's entries and which of them want the user ({@code attention}, per entry; null
     * for none). Rebuilds the line only when something drawn differs from what is held.
     */
    public void setPages(@Nullable PageBarModel model, @Nullable boolean[] attention) {
        if (sameEntries(mModel, model) && Arrays.equals(mAttention, attention)) {
            mModel = model;
            return;
        }
        mModel = model;
        mAttention = attention == null ? null : attention.clone();
        rebuild();
    }

    /** The layout in use, or null while there are no entries or no length; for later tickets and tests. */
    @Nullable
    public MarksLine line() {
        return mLine;
    }

    /** Re-reads the band after a pass that could have moved it; compares before it repaints. */
    public void refreshInk() {
        if (syncInk()) invalidate();
    }

    // ---- build ------------------------------------------------------------------------------

    private float axisLength() {
        return mVertical ? getHeight() : getWidth();
    }

    private void rebuild() {
        PageBarModel model = mModel;
        float length = axisLength();
        if (model == null || model.entries().isEmpty() || length <= 0f) {
            mLine = null;
        } else {
            mLine = new MarksLine(model.entries(), mAttention,
                getResources().getDisplayMetrics().density, length);
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuild();
    }

    private static boolean sameEntries(@Nullable PageBarModel a, @Nullable PageBarModel b) {
        if (a == b) return true;
        if (a == null || b == null || a.entries().size() != b.entries().size()) return false;
        for (int i = 0; i < a.entries().size(); i++) {
            PageBarModel.Entry x = a.entries().get(i);
            PageBarModel.Entry y = b.entries().get(i);
            if (x.kind != y.kind || x.number != y.number || x.current != y.current
                || x.returnTo != y.returnTo || x.target.action != y.target.action
                || x.target.pageId != y.target.pageId || x.target.index != y.target.index) {
                return false;
            }
        }
        return true;
    }

    // ---- ink --------------------------------------------------------------------------------

    /**
     * Solves the inks on the band as drawn, when the measurement could have moved (a new sample
     * generation, the band moved, or the palette changed). Int and rect comparisons otherwise.
     *
     * @return whether the inks changed
     */
    private boolean syncInk() {
        ChromeInk chrome = mChromeInk;
        OnGlass.Resolution resolved = chrome == null ? null
            : chrome.resolution(GlassBackdropCache.Band.STATUS_BAR);
        if (resolved == null || !chrome.bandRect(GlassBackdropCache.Band.STATUS_BAR, mBandRect)) {
            // Nothing can say what the line stands on yet: the roles themselves, made once.
            if (mAuthored && !mInkMeasured) return false;
            mInkMeasured = false;
            mAuthored = true;
            MarksLineInk authored = MarksLineInk.authored(mNeutral, mAccent, mError);
            boolean changed = !sameInk(authored, mInk);
            mInk = authored;
            return changed;
        }
        if (mInkMeasured && chrome.backdrops().generation() == mInkGeneration
            && mMeasuredRect.equals(mBandRect)) {
            return false;
        }
        boolean pale = chrome.polarity() == ChromeInk.Polarity.PALE_INK;
        int surfaceBase = MaterialColors.getColor(this,
            com.termux.shared.R.attr.termuxColorSurfaceBase,
            ContextCompat.getColor(getContext(), R.color.termux_surface_base));
        MarksLineInk solved = MarksLineInk.onBand(resolved.surface, pale,
            WindowChipInk.neutralSeed(mNeutral, surfaceBase, pale), mAccent, mError);
        boolean changed = !sameInk(solved, mInk);
        mInk = solved;
        mInkMeasured = true;
        mAuthored = false;
        mInkGeneration = chrome.backdrops().generation();
        mMeasuredRect.set(mBandRect);
        return changed;
    }

    private static boolean sameInk(MarksLineInk a, MarksLineInk b) {
        return a.current == b.current && a.other == b.other && a.attention == b.attention;
    }

    // ---- draw -------------------------------------------------------------------------------

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        syncInk();
        MarksLine line = mLine;
        if (line == null) return;
        boolean rtl = !mVertical && getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float cross = (mVertical ? getWidth() : getHeight()) / 2f;
        float size = Math.min(line.markSizePx(), cross * 2f);
        List<PageBarFold.Slot> slots = line.slots();
        for (int i = 0, n = slots.size(); i < n; i++) {
            PageBarFold.Slot slot = slots.get(i);
            float along = line.centreAt(i);
            float cx = mVertical ? cross : rtl ? getWidth() - along : along;
            float cy = mVertical ? along : cross;
            int ink = slot.current ? mInk.current : mInk.other;
            if (slot.folded()) {
                mLook.drawStack(canvas, slot.kind, slot.count, slot.current, 0f, cx, cy, size,
                    ink, mInk.other);
            } else {
                mLook.draw(canvas, slot.kind, slot.number, slot.current, 0f, cx, cy, size, ink);
            }
            if (line.attention(i)) mLook.drawBurst(canvas, cx, cy, size, mInk.attention);
        }
    }

    // ---- touch ------------------------------------------------------------------------------

    private float along(float x, float y) {
        if (mVertical) return y;
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL ? getWidth() - x : x;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        MarksLine line = mLine;
        if (line == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDown = line.slotAt(along(event.getX(), event.getY())) >= 0;
                mDownX = event.getX();
                mDownY = event.getY();
                return mDown;
            case MotionEvent.ACTION_MOVE:
                if (mDown && (Math.abs(event.getX() - mDownX) > mTouchSlop
                    || Math.abs(event.getY() - mDownY) > mTouchSlop)) {
                    mDown = false;
                }
                return mDown;
            case MotionEvent.ACTION_UP:
                boolean tap = mDown;
                mDown = false;
                if (tap) tapAt(along(event.getX(), event.getY()));
                return tap;
            case MotionEvent.ACTION_CANCEL:
                mDown = false;
                return false;
            default:
                return mDown;
        }
    }

    /** A tap at {@code pos} along the line: to the page it lands on; the current page does nothing. */
    void tapAt(float pos) {
        MarksLine line = mLine;
        PageSwitchTargets.Navigator nav = mNavigator;
        if (line == null || nav == null) return;
        PageBarModel.Target target = line.targetAt(pos);
        if (target != null) PageSwitchTargets.carryOut(target, nav);
    }
}
