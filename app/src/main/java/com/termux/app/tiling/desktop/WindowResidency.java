package com.termux.app.tiling.desktop;

import androidx.annotation.NonNull;

import com.termux.app.chrome.LiveBackdrop;
import com.termux.app.chrome.TerlGlassWear;
import com.termux.app.glass.GlassRegistry;
import com.termux.app.glass.GlassResidency;
import com.termux.app.glass.GlassRoute;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The tiling desktop's side of live window residency (live-window-residency-2026-11-28.md): the
 * rings of its places and windows, and the route and area of every window's glass, worked out
 * once per {@code render()} (an arrangement change, a gesture's door, a rest) and handed to the
 * glass service. Pure: no views; the desktop feeds it distances and sizes.
 *
 * <p>A render runs in two steps. While it lays the places out it asks {@link #holdsLevel} and
 * {@link #holdsWindow} (which change nothing) and {@link #note}s every window of a held place;
 * at its end {@link #commit} moves the rings, applies the warm cap and gives the routes. A window
 * the cap let go after the render already built it is reported ({@link Commit#rerender}), so a
 * second render drops its views; that one converges.
 */
final class WindowResidency {

    /** One window seen by the render. */
    private static final class Seen {
        final float distance;
        final boolean landing;
        @NonNull final GlassResidency.Surface surface;
        final long effectAreaPx;
        final int level;
        /** The render built its views. */
        final boolean built;
        /** Built because the rings held it ({@link #holdsWindow}), so the warm cap can take it. */
        final boolean droppable;

        Seen(float distance, boolean landing, @NonNull GlassResidency.Surface surface, long effectAreaPx,
             int level, boolean built, boolean droppable) {
            this.distance = distance;
            this.landing = landing;
            this.surface = surface;
            this.effectAreaPx = effectAreaPx;
            this.level = level;
            this.built = built;
            this.droppable = droppable;
        }
    }

    /** What a render's end hands the glass service. */
    static final class Commit {
        /** Window glass element to its routing. */
        @NonNull final Map<String, GlassRegistry.Routing> routes;
        /** The hot effect layers' area the place in front may take, in px. */
        final long effectAreaBudgetPx;
        /** The warm cap let go of a window this render built: render again. */
        final boolean rerender;
        /** When a dwell runs out and a rest render could let a window go; MAX_VALUE for none. */
        final long nextChangeMs;

        Commit(@NonNull Map<String, GlassRegistry.Routing> routes, long effectAreaBudgetPx, boolean rerender,
               long nextChangeMs) {
            this.routes = routes;
            this.effectAreaBudgetPx = effectAreaBudgetPx;
            this.rerender = rerender;
            this.nextChangeMs = nextChangeMs;
        }
    }

    @NonNull private final GlassResidency.Tier mTier;
    @NonNull private final GlassResidency.Rings mLevels = new GlassResidency.Rings();
    @NonNull private final GlassResidency.Rings mWindows = new GlassResidency.Rings();
    @NonNull private final HashMap<Long, Seen> mSeen = new HashMap<>();
    @NonNull private Set<Integer> mLandingLevels = Collections.emptySet();
    private long mNow;
    private int mCentre;
    private boolean mFrontOnly;
    private boolean mHalveWarm;
    private long mCarried = Long.MIN_VALUE;

    WindowResidency(@NonNull GlassResidency.Tier tier) {
        mTier = tier;
    }

    /** A render begins at {@code nowMs} around {@code centre}; {@code landing} is a predicted landing level or none. */
    void begin(long nowMs, int centre, @NonNull Set<Integer> landingLevels) {
        mNow = nowMs;
        mCentre = centre;
        mLandingLevels = landingLevels;
        mSeen.clear();
    }

    /**
     * The levels that hold views, out of {@code first} .. {@code last}: the centre and the ones
     * one place away, a predicted landing, and those still inside their leave ring or dwell.
     * Moves the levels' rings (they need no cap). After a trim, the centre alone.
     */
    @NonNull
    Set<Integer> holdLevels(int first, int last) {
        Map<Long, Float> distances = new HashMap<>();
        Set<Long> landings = new HashSet<>();
        for (int level = first; level <= last; level++) {
            if (mFrontOnly && level != mCentre) continue;
            distances.put((long) level, (float) Math.abs(level - mCentre));
            if (mLandingLevels.contains(level)) landings.add((long) level);
        }
        mLevels.update(distances, landings, mNow, Integer.MAX_VALUE);
        Set<Integer> held = new HashSet<>();
        for (long level : mLevels.held()) held.add((int) level);
        return held;
    }

    /** How far {@code level} is from the centre, in places. */
    float levelDistance(int level) {
        return Math.abs(level - mCentre);
    }

    /** Whether {@code level} is a predicted landing. */
    boolean isLanding(int level) {
        return mLandingLevels.contains(level);
    }

    /**
     * Whether window {@code windowId} holds views at {@code distance} screens from the screen
     * (the larger of its place's distance and its column's), without moving anything.
     */
    boolean holdsWindow(long windowId, float distance, boolean landing) {
        return mWindows.peek(windowId, distance, landing, mNow) != GlassResidency.Ring.COLD;
    }

    /**
     * Window {@code windowId} of a held place, at {@code distance}, as {@code surface}, its glass's
     * effect layer {@code effectAreaPx} if it took the effect route, on {@code level} (-1 for a
     * pinned Float, shown over the place in front). {@code built} when the render built its views;
     * {@code droppable} when it built them because the rings held it (a column), so the warm cap
     * may let it go; a window held by its place (a Dwindle tile, a Float) stands while its place
     * does, and its glass is routed whatever its ring.
     */
    void note(long windowId, float distance, boolean landing, @NonNull GlassResidency.Surface surface,
              long effectAreaPx, int level, boolean built, boolean droppable) {
        mSeen.put(windowId, new Seen(Math.max(0f, distance), landing, surface, Math.max(0L, effectAreaPx),
            level, built, droppable));
    }

    /** The window navigation mode carries ({@code Long.MIN_VALUE} or any unknown id for none). */
    void setCarried(long windowId) {
        mCarried = windowId;
    }

    /**
     * The render's end: the windows' rings move (with the tier's warm cap, halved after a trim),
     * each held window's glass gets its route ({@link GlassResidency#route}, a live scene beneath
     * when {@code liveScene}) and area, and the area budget leaves room for the heaviest
     * neighbour place. {@code screenWidthPx} by {@code screenHeightPx} is the device's screen.
     */
    @NonNull
    Commit commit(boolean liveScene, int screenWidthPx, int screenHeightPx) {
        Map<Long, Float> distances = new HashMap<>();
        Set<Long> landings = new HashSet<>();
        for (Map.Entry<Long, Seen> entry : mSeen.entrySet()) {
            distances.put(entry.getKey(), entry.getValue().distance);
            if (entry.getValue().landing) landings.add(entry.getKey());
        }
        int cap = mHalveWarm ? mTier.warmWindows / 2 : mTier.warmWindows;
        mWindows.update(distances, landings, mNow, cap);
        Map<String, GlassRegistry.Routing> routes = new HashMap<>();
        Map<Integer, Long> neighbourArea = new HashMap<>();
        boolean rerender = false;
        for (Map.Entry<Long, Seen> entry : mSeen.entrySet()) {
            long id = entry.getKey();
            Seen seen = entry.getValue();
            GlassResidency.Ring ring = mWindows.ring(id);
            if (ring == GlassResidency.Ring.COLD) {
                if (!seen.built) continue;
                // Built this render: routed as warm until a render drops it (if it is droppable).
                if (seen.droppable) rerender = true;
                ring = GlassResidency.Ring.WARM;
            }
            GlassResidency.Surface surface = id == mCarried ? GlassResidency.Surface.CARRIED : seen.surface;
            GlassRoute route = GlassResidency.route(surface, liveScene, ring, mTier);
            boolean hot = ring == GlassResidency.Ring.HOT;
            long area = route == GlassRoute.EFFECT ? seen.effectAreaPx : 0L;
            routes.put(TerlGlassWear.windowElement(id), new GlassRegistry.Routing(route, area, hot));
            // What a neighbour place would show beside the place in front in a crossing.
            if (!hot && seen.level >= 0 && seen.level != mCentre && route == GlassRoute.EFFECT) {
                Long sum = neighbourArea.get(seen.level);
                neighbourArea.put(seen.level, (sum == null ? 0L : sum) + area);
            }
        }
        long heaviest = 0L;
        for (long area : neighbourArea.values()) heaviest = Math.max(heaviest, area);
        long budget = GlassResidency.effectAreaBudgetPx(mTier, screenWidthPx, screenHeightPx, heaviest);
        return new Commit(routes, budget, rerender, Math.min(mWindows.nextChangeMs(), mLevels.nextChangeMs()));
    }

    /** A memory signal ({@code TRIM_MEMORY_*}): what {@link GlassResidency#trim} lets go. */
    void trim(int level) {
        GlassResidency.Trim trim = GlassResidency.trim(level);
        if (trim.dropBeyondAdjacent) mWindows.letGoBeyond(GlassResidency.ENTER_SCREENS);
        if (trim.halveWarm) mHalveWarm = true;
        if (trim.frontOnly) {
            mFrontOnly = true;
            mLevels.clear();
        }
    }

    /** The window is back in front: the rings are rebuilt around the place in front. Whether a trim was in force. */
    boolean resumed() {
        boolean trimmed = mFrontOnly || mHalveWarm;
        mFrontOnly = false;
        mHalveWarm = false;
        return trimmed;
    }

    /** The effect layer's area of a window {@code widthPx} by {@code heightPx} at {@code marginPx} of blur. */
    static long effectAreaPx(float widthPx, float heightPx, int marginPx) {
        return LiveBackdrop.effectAreaPx(Math.round(widthPx), Math.round(heightPx), marginPx);
    }
}
