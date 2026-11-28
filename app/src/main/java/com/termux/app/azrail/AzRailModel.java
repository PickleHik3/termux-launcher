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

    /** The most apps one fan shows; the rest of a long letter are reached through the drawer. */
    public static final int MAX_FAN = 7;

    @NonNull private final List<Character> mLetters = new ArrayList<>();
    @NonNull private final List<List<LauncherAppEntry>> mApps = new ArrayList<>();
    @NonNull private Side mSide = Side.RIGHT;
    private float mRailLeft;
    private float mRailRight;
    private float mTop;
    private float mBottom;
    private float mFanWidth;
    private float mItemHeight;
    private int mLetter = -1;

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

    /** The apps of letter {@code index}, as the fan shows them (at most {@link #MAX_FAN}). */
    @NonNull
    public List<LauncherAppEntry> fanApps(int index) {
        if (index < 0 || index >= mApps.size()) return Collections.emptyList();
        List<LauncherAppEntry> all = mApps.get(index);
        return all.size() <= MAX_FAN ? all : all.subList(0, MAX_FAN);
    }

    /**
     * Stands the rail up: the column {@code railWidth} wide against the edge on {@code side} (clear
     * of {@code edgeInset} px a bar rail takes), from {@code top} to {@code bottom}; the fan is
     * {@code fanWidth} wide beside it with items {@code itemHeight} tall.
     */
    public void layout(@NonNull Side side, float screenWidth, float edgeInset, float railWidth, float top,
                       float bottom, float fanWidth, float itemHeight) {
        mSide = side;
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
        mLetter = -1;
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

    public float fanLeft() {
        return mSide == Side.LEFT ? mRailRight : mRailLeft - mFanWidth;
    }

    public float fanRight() {
        return fanLeft() + mFanWidth;
    }

    /** The top of the fan of letter {@code index}: centred on its slot, kept inside the rail's height. */
    public float fanTop(int index) {
        float height = fanApps(index).size() * mItemHeight;
        float top = letterCenterY(index) - height / 2f;
        return Math.max(mTop, Math.min(top, Math.max(mTop, mBottom - height)));
    }

    /** The letter held now; -1 before the finger has been over the rail. */
    public int letter() {
        return mLetter;
    }

    /** Whether ({@code x}) is in the fan's column. */
    private boolean inFanColumn(float x) {
        return x >= fanLeft() && x <= fanRight();
    }

    /**
     * The finger is at ({@code x}, {@code y}): the letter it holds and, inside the fan, the app it
     * is over. Returns the app's index in {@link #fanApps}, or -1 for none.
     */
    public int move(float x, float y) {
        if (mLetters.isEmpty()) return -1;
        if (!inFanColumn(x) || mLetter < 0) {
            mLetter = AzLetterTrack.indexWithHysteresis(y - mTop, mLetter, mBottom - mTop,
                mLetters.size(), mLetter >= 0);
            return -1;
        }
        return appAt(y);
    }

    /** The app at height {@code y} in the held letter's fan, or -1. */
    private int appAt(float y) {
        List<LauncherAppEntry> apps = fanApps(mLetter);
        float top = fanTop(mLetter);
        if (apps.isEmpty() || y < top || y >= top + apps.size() * mItemHeight) return -1;
        return Math.min(apps.size() - 1, (int) ((y - top) / mItemHeight));
    }

    /** The finger lifted at ({@code x}, {@code y}): the app it let go on, or null for none. */
    @Nullable
    public LauncherAppEntry release(float x, float y) {
        if (mLetter < 0 || !inFanColumn(x)) return null;
        int index = appAt(y);
        return index < 0 ? null : fanApps(mLetter).get(index);
    }
}
