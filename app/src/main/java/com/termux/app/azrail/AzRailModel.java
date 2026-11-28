package com.termux.app.azrail;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.az.AzLetterTrack;
import com.termux.app.launcher.drawer.AppDrawerSectionIndex;
import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The A–Z rail's decisions (#437), pure: which letters it holds, where the rail and the apps' fan
 * stand on the thumb's side, which letter a finger is on ({@link AzLetterTrack}, the strip's own
 * slot arithmetic and hysteresis), and which app a finger lets go on.
 *
 * <p>Geometry is in the window layer's pixels. The rail is a column of letters against the screen
 * edge on the thumb's side; the fan is a column of the letter's apps beside it, towards the middle
 * of the screen, centred on the letter's slot. A finger off the fan (on the rail, or further in)
 * holds the letter its height names; a finger inside the fan's column holds that letter still and
 * picks the app at its height. Letting go on an app opens it; anywhere else opens nothing.
 */
public final class AzRailModel {

    /** The thumb's side of the screen. */
    public enum Side { LEFT, RIGHT }

    /** The most apps one fan column shows; a longer letter fans a further column inward. */
    public static final int MAX_FAN = 7;

    @NonNull private final List<Character> mLetters = new ArrayList<>();
    @NonNull private final List<List<LauncherAppEntry>> mApps = new ArrayList<>();
    @NonNull private Side mSide = Side.RIGHT;
    private float mRailLeft;
    private float mRailRight;
    private float mTop;
    private float mBottom;
    private float mFanWidth;
    private float mScreenWidth;
    private float mItemHeight;
    private float mMinColumnWidth;
    private float mEnterSlop;
    private int mLetter = -1;
    /** The finger is inside the held letter's fan, picking an app; the letter holds still. */
    private boolean mLocked;
    /** Where the finger scrubbed from: moving inward past the slop from here, into the fan, locks it. */
    private float mScrubX;

    /** A rail over {@code catalogue}: the letters apps start with, in order, each with its apps. */
    public AzRailModel(@NonNull List<LauncherAppEntry> catalogue) {
        Map<Character, List<LauncherAppEntry>> byLetter = new TreeMap<>((a, b) -> {
            if (a == b) return 0;
            if (a == '#') return -1;
            if (b == '#') return 1;
            return Character.compare(a, b);
        });
        for (LauncherAppEntry entry : AppDrawerSectionIndex.sortByLabel(catalogue)) {
            byLetter.computeIfAbsent(AppDrawerSectionIndex.letterOf(entry), k -> new ArrayList<>()).add(entry);
        }
        for (Map.Entry<Character, List<LauncherAppEntry>> e : byLetter.entrySet()) {
            mLetters.add(e.getKey());
            mApps.add(Collections.unmodifiableList(e.getValue()));
        }
    }

    /** The side the thumb is on: the screen half {@code x} is in. */
    @NonNull
    public static Side sideOf(float x, float screenWidth) {
        return x < screenWidth / 2f ? Side.LEFT : Side.RIGHT;
    }

    public int letterCount() {
        return mLetters.size();
    }

    public char letterAt(int index) {
        return mLetters.get(index);
    }

    /** Every app of letter {@code index}, in the fan's order: down the column nearest the rail, then the next. */
    @NonNull
    public List<LauncherAppEntry> fanApps(int index) {
        if (index < 0 || index >= mApps.size()) return Collections.emptyList();
        return mApps.get(index);
    }

    /** The rows that fit the rail's height. */
    private int fit() {
        return mItemHeight <= 0f ? MAX_FAN : Math.max(1, (int) ((mBottom - mTop) / mItemHeight));
    }

    /** The screen's width on the fan's side of the rail. */
    private float room() {
        return mSide == Side.LEFT ? mScreenWidth - mRailRight : mRailLeft;
    }

    /**
     * The rows one fan column of letter {@code index} holds: {@link #MAX_FAN} at most, fewer where
     * the rail is short, more (up to what fits) where a long letter would otherwise need columns
     * narrower than the minimum.
     */
    public int fanRows(int index) {
        int fit = fit();
        int base = Math.min(MAX_FAN, fit);
        if (mMinColumnWidth <= 0f) return base;
        int maxColumns = Math.max(1, (int) (room() / mMinColumnWidth));
        int need = (fanApps(index).size() + maxColumns - 1) / maxColumns;
        return Math.max(1, Math.max(base, Math.min(fit, need)));
    }

    /** The columns letter {@code index}'s fan takes. */
    public int fanColumns(int index) {
        int n = fanApps(index).size();
        int rows = fanRows(index);
        return Math.max(1, (n + rows - 1) / rows);
    }

    /** The width of one column of letter {@code index}'s fan: narrowed so every column fits on screen. */
    public float fanColumnWidth(int index) {
        return Math.min(mFanWidth, room() / fanColumns(index));
    }

    /**
     * Stands the rail up: the column {@code railWidth} wide against the edge on {@code side} (clear
     * of {@code edgeInset} px a bar rail takes), from {@code top} to {@code bottom}; the fan is
     * {@code fanWidth} wide beside it with items {@code itemHeight} tall. A fan column is not made
     * narrower than {@code minColumnWidth} while rows can grow instead; a finger moving inward
     * {@code enterSlop} into the fan picks apps rather than scrubbing letters.
     */
    public void layout(@NonNull Side side, float screenWidth, float edgeInset, float railWidth, float top,
                       float bottom, float fanWidth, float itemHeight, float minColumnWidth, float enterSlop) {
        mSide = side;
        mScreenWidth = screenWidth;
        if (side == Side.LEFT) {
            mRailLeft = edgeInset;
            mRailRight = edgeInset + railWidth;
        } else {
            mRailRight = screenWidth - edgeInset;
            mRailLeft = mRailRight - railWidth;
        }
        mTop = top;
        mBottom = Math.max(top + 1f, bottom);
        mFanWidth = fanWidth;
        mItemHeight = itemHeight;
        mMinColumnWidth = minColumnWidth;
        mEnterSlop = enterSlop;
        mLetter = -1;
        mLocked = false;
    }

    @NonNull
    public Side side() {
        return mSide;
    }

    public float railLeft() {
        return mRailLeft;
    }

    public float railRight() {
        return mRailRight;
    }

    public float top() {
        return mTop;
    }

    public float bottom() {
        return mBottom;
    }

    /** The centre of letter {@code index}'s slot, y in the layer. */
    public float letterCenterY(int index) {
        return mTop + AzLetterTrack.centerPx(index, mBottom - mTop, Math.max(1, mLetters.size()));
    }

    public float itemHeight() {
        return mItemHeight;
    }

    /** The fan's left edge for letter {@code index}. */
    public float fanLeft(int index) {
        float width = fanColumns(index) * fanColumnWidth(index);
        return mSide == Side.LEFT ? mRailRight : mRailLeft - width;
    }

    public float fanRight(int index) {
        return fanLeft(index) + fanColumns(index) * fanColumnWidth(index);
    }

    /** The top of the fan of letter {@code index}: centred on its slot, kept inside the rail's height. */
    public float fanTop(int index) {
        float height = Math.min(fanApps(index).size(), fanRows(index)) * mItemHeight;
        float top = letterCenterY(index) - height / 2f;
        return Math.max(mTop, Math.min(top, Math.max(mTop, mBottom - height)));
    }

    /** The left edge of fan column {@code column} (0 nearest the rail) of letter {@code index}. */
    public float columnLeft(int index, int column) {
        float w = fanColumnWidth(index);
        return mSide == Side.LEFT ? mRailRight + column * w : mRailLeft - (column + 1) * w;
    }

    /** The app's row in its column and its column, for fan position {@code app} of letter {@code index}. */
    public float appTop(int index, int app) {
        return fanTop(index) + (app % fanRows(index)) * mItemHeight;
    }

    public float appLeft(int index, int app) {
        return columnLeft(index, app / fanRows(index));
    }

    /** The letter held now; -1 before the finger has been over the rail. */
    public int letter() {
        return mLetter;
    }

    /** The height of letter {@code index}'s fan. */
    public float fanHeight(int index) {
        return Math.min(fanApps(index).size(), fanRows(index)) * mItemHeight;
    }

    /** Whether the point is inside the held letter's fan, as it stands for that letter. */
    private boolean inFan(float x, float y) {
        if (mLetter < 0) return false;
        float top = fanTop(mLetter);
        return x >= fanLeft(mLetter) && x <= fanRight(mLetter) && y >= top && y < top + fanHeight(mLetter);
    }

    /**
     * The finger is at ({@code x}, {@code y}): the letter it holds and, inside the fan, the app it
     * is over. Returns the app's index in {@link #fanApps}, or -1 for none.
     *
     * <p>The letter follows the finger's height wherever the finger is, until it moves inward by
     * the slop into the held letter's fan as it actually stands; there it holds and the finger
     * picks. Leaving the fan hands the letter back to the finger's height.
     */
    public int move(float x, float y) {
        if (mLetters.isEmpty()) return -1;
        if (mLocked) {
            if (inFan(x, y)) return appAt(x, y);
            mLocked = false;
            mScrubX = x;
        }
        boolean first = mLetter < 0;
        mLetter = AzLetterTrack.indexWithHysteresis(y - mTop, mLetter, mBottom - mTop,
            mLetters.size(), !first);
        if (first) mScrubX = x;
        float inward = mSide == Side.LEFT ? x - mScrubX : mScrubX - x;
        if (inward < 0f) {
            mScrubX = x;
        } else if (inward >= mEnterSlop && inFan(x, y)) {
            mLocked = true;
            return appAt(x, y);
        }
        return -1;
    }

    /** The app under ({@code x}, {@code y}) in the held letter's fan, or -1. */
    private int appAt(float x, float y) {
        List<LauncherAppEntry> apps = fanApps(mLetter);
        float top = fanTop(mLetter);
        int rows = fanRows(mLetter);
        if (apps.isEmpty() || y < top || y >= top + fanHeight(mLetter)) return -1;
        int row = (int) ((y - top) / mItemHeight);
        float width = fanColumnWidth(mLetter);
        int column = mSide == Side.LEFT ? (int) ((x - mRailRight) / width) : (int) ((mRailLeft - x) / width);
        int index = Math.max(0, column) * rows + row;
        return index < apps.size() ? index : -1;
    }

    /** Whether the finger is picking an app in the held letter's fan. */
    public boolean inFan() {
        return mLocked;
    }

    /**
     * The finger lifted at ({@code x}, {@code y}): the app it let go on, or null for none. Only a
     * finger that went into the fan picks; one still scrubbing letters opens nothing.
     */
    @Nullable
    public LauncherAppEntry release(float x, float y) {
        int index = move(x, y);
        return mLocked && index >= 0 ? fanApps(mLetter).get(index) : null;
    }
}
