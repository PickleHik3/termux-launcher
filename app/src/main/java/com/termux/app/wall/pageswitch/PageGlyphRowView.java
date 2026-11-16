package com.termux.app.wall.pageswitch;

import android.content.Context;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import android.graphics.Canvas;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.ChromeShade;
import com.termux.app.chrome.GlassInk;
import com.termux.app.chrome.WallpaperInk;
import com.termux.app.launcher.widget.HomeLabelInk;

import java.util.function.IntSupplier;

/**
 * The row of page glyphs under Home's grid, where the page dots were: the glyph of each kind the
 * wall has a page for, in the order Home, Terminal, Assistant, Display, no capsule. The current
 * kind is drawn strong and the rest weak. The view is as wide and as tall as the band it stands
 * in, so a touch anywhere in the band opens the page switch panel through its {@link Entry}.
 */
public final class PageGlyphRowView extends View implements PageSwitchHitArea {

    /** What a touch on the row calls; the page switch controller implements it. */
    public interface Entry {
        boolean entryDown(float rawX, float rawY, int anchorTopRaw, int anchorBottomRaw,
                          boolean overAnchor);
        void entryMove(float rawX, float rawY);
        void entryUp(float rawX, float rawY);
        void entryCancel();
        /** Claims the touches that land on {@code area} ahead of the window's own gestures. */
        void addHitArea(@NonNull PageSwitchHitArea area);
        void removeHitArea(@NonNull PageSwitchHitArea area);
    }

    static final float GLYPH_DP = 20f;
    static final float GAP_DP = 14f;
    private static final int STRONG_ALPHA = 0xE6000000;
    private static final int WEAK_ALPHA = 0x4D000000;

    private static final PageSwitchModel.Kind[] KINDS = PageSwitchModel.Kind.values();

    private final PageGlyphPainter mPainter;
    private final float mDensity;
    private final int[] mAt = new int[2];
    /** What the model last said: which kinds show, their numbers and the current kind. */
    private final boolean[] mShown = new boolean[KINDS.length];
    private final int[] mNumbers = new int[KINDS.length];
    private int mCurrent = -1;
    private boolean mHasModel;

    @Nullable private IntSupplier mWallpaperInk;
    @Nullable private Entry mEntry;
    private boolean mStreamOpen;

    public PageGlyphRowView(@NonNull Context context) {
        super(context);
        mPainter = new PageGlyphPainter(context);
        mDensity = context.getResources().getDisplayMetrics().density;
    }

    /** The wallpaper-measured ink behind the row: an alpha-0 value means none was measured. */
    public void setWallpaperInk(@Nullable IntSupplier wallpaperInk) {
        mWallpaperInk = wallpaperInk;
    }

    public void setEntry(@Nullable Entry entry) {
        if (mEntry == entry) return;
        if (mEntry != null) mEntry.removeHitArea(this);
        mEntry = entry;
        if (entry != null && isAttachedToWindow()) entry.addHitArea(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mEntry != null) mEntry.addHitArea(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mEntry != null) mEntry.removeHitArea(this);
        setCovered(false);
        super.onDetachedFromWindow();
    }

    /** The row's band on screen, while it is shown and has a glyph to open the switcher from. */
    @Override
    public boolean containsRaw(float rawX, float rawY) {
        if (!mHasModel || mEntry == null || !isShown()) return false;
        getLocationOnScreen(mAt);
        return rawX >= mAt[0] && rawX < mAt[0] + getWidth()
            && rawY >= mAt[1] && rawY < mAt[1] + getHeight();
    }

    @Override
    public void setCovered(boolean covered) {
        float alpha = covered ? 0f : 1f;
        if (getAlpha() != alpha) setAlpha(alpha);
    }

    /** Takes the model's answer; invalidates only when what the row draws changed. */
    public void setModel(@Nullable PageSwitchModel model) {
        boolean changed = mHasModel != (model != null);
        mHasModel = model != null;
        int current = -1;
        for (int i = 0; i < KINDS.length; i++) {
            boolean shown = model != null && model.shows(KINDS[i]);
            int number = shown ? model.numberOf(KINDS[i]) : 0;
            if (mShown[i] != shown || mNumbers[i] != number) changed = true;
            mShown[i] = shown;
            mNumbers[i] = number;
        }
        if (model != null) {
            java.util.List<PageSwitchModel.Cell> cells = model.cells();
            for (int i = 0; i < cells.size(); i++) {
                if (cells.get(i).current) {
                    current = cells.get(i).kind.ordinal();
                    break;
                }
            }
        }
        if (current != mCurrent) changed = true;
        mCurrent = current;
        if (changed) invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (!mHasModel) return;
        int count = 0;
        for (boolean shown : mShown) if (shown) count++;
        if (count == 0) return;
        float glyph = GLYPH_DP * mDensity;
        float gap = GAP_DP * mDensity;
        float total = count * glyph + (count - 1) * gap;
        float x = (getWidth() - total) / 2f;
        float y = (getHeight() - glyph) / 2f;
        int wallpaper = mWallpaperInk == null ? 0 : mWallpaperInk.getAsInt();
        for (int i = 0; i < KINDS.length; i++) {
            if (!mShown[i]) continue;
            boolean strong = i == mCurrent;
            int ink = ink(wallpaper, strong);
            mPainter.drawFloating(canvas, KINDS[i], mNumbers[i], x, y, glyph, ink,
                GlassInk.floatingShadow(ink), 0);
            x += glyph + gap;
        }
    }

    /**
     * The glyphs are chrome over the wall, so they follow the chrome's polarity: white light on
     * the dark band, shadow on the light one. On a measured wallpaper they follow it instead: the
     * ink that reads on the wallpaper behind them, at the same two strengths.
     */
    private static int ink(int wallpaper, boolean strong) {
        if (Color.alpha(wallpaper) != 0) {
            int ink = WallpaperInk.bareInk(wallpaper, HomeLabelInk.DARK_INK, HomeLabelInk.PALE_INK);
            return (ink & 0x00FFFFFF) | (strong ? STRONG_ALPHA : WEAK_ALPHA);
        }
        return strong
            ? ChromeShade.structural(0xE6FFFFFF, ChromeShade.TARGET_RIM)
            : ChromeShade.fill(0x4DFFFFFF);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        Entry entry = mEntry;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (entry == null || !mHasModel) return false;
                getLocationOnScreen(mAt);
                if (!entry.entryDown(event.getRawX(), event.getRawY(), mAt[1],
                        mAt[1] + getHeight(), true)) {
                    return false;
                }
                mStreamOpen = true;
                // The Home pager must not page on this touch.
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mStreamOpen && entry != null) entry.entryMove(event.getRawX(), event.getRawY());
                return mStreamOpen;
            case MotionEvent.ACTION_UP:
                if (mStreamOpen && entry != null) entry.entryUp(event.getRawX(), event.getRawY());
                mStreamOpen = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (mStreamOpen && entry != null) entry.entryCancel();
                mStreamOpen = false;
                return true;
            default:
                return mStreamOpen;
        }
    }
}
