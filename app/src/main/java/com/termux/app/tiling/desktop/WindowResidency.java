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
 * once per {@code render()} and handed to the glass service at rest. Pure: no views; the desktop
 * feeds it distances and sizes.
 *
 * <p>A render runs in two steps. While it lays the places out it asks {@link #holdLevels} and
 * {@link #holdsWindow} (the latter changes nothing) and {@link #note}s every window of a held
 * place; at its end, at rest, {@link #commit} moves the windows' rings, applies the tier's warm
 * cap to every window beyond the adjacent ring (columns, Dwindle tiles, Floats, full-screen and
 * Linux windows alike) and gives the routes. A window the cap let go after the render built it is
 * reported ({@link Commit#rerender}), so a second render drops its views; that one converges.
 * While a gesture runs nothing is committed: {@link #provisional} routes only the windows the
 * render built anew, and grants already given stay as they were (ADR 0029 section 4).</p>
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

        Seen(float distance, boolean landing, @NonNull GlassResidency.Surface surface, long effectAreaPx,
             int level, boolean built) {
            this.distance = distance;
            this.landing = landing;
            this.surface = surface;
            this.effectAreaPx = effectAreaPx;
            this.level = level;
            this.built = built;
        }
    }

    /** What a render's end hands the glass service. */
    static final class Commit {
        /** Window glass element to its routing. */
        @NonNull final Map<String, GlassRegistry.Routing> routes;
        /** The effect layers' area at most, in px (the tier's, {@link LiveBackdrop#effectAreaBudgetPx}). */
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

    /** The level a pinned Float is noted on: none of its own, it shows over the place in front. */
    static final int PINNED_LEVEL = -1;

    @NonNull private final GlassResidency.Tier mTier;
    @NonNull private final GlassResidency.Rings mLevels = GlassResidency.placeRings();
    @NonNull private final GlassResidency.Rings mWindows = new GlassResidency.Rings();
    @NonNull private final HashMap<Long, Seen> mSeen = new HashMap<>();
    @NonNull private Set<Integer> mLandingLevels = Collections.emptySet();
    private long mNow;
    private int mCentre;
    private boolean mFrontOnly;
    private boolean mHalveWarm;

    WindowResidency(@NonNull GlassResidency.Tier tier) {
        mTier = tier;
    }

    /** A render begins at {@code nowMs} around {@code centre}; {@code landingLevels} are predicted landings. */
    void begin(long nowMs, int centre, @NonNull Set<Integer> landingLevels) {
        mNow = nowMs;
        mCentre = centre;
        mLandingLevels = landingLevels;
        mSeen.clear();
    }

    /**
     * The levels that hold views, out of {@code first} .. {@code last}: the centre and the ones one
     * place away; beyond them a predicted landing and a place waiting out its dwell, at most the
     * tier's {@link GlassResidency.Tier#warmPlaces} of them (farthest let go first, landings last).
     * After a trim, the centre alone. Moves the levels' rings.
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
        mLevels.update(distances, landings, mNow, mTier.warmPlaces);
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
     * effect layer {@code effectAreaPx} if it took the effect route, on {@code level}
     * ({@link #PINNED_LEVEL} for a pinned Float); {@code built} when the render built its views.
     */
    void note(long windowId, float distance, boolean landing, @NonNull GlassResidency.Surface surface,
              long effectAreaPx, int level, boolean built) {
        mSeen.put(windowId, new Seen(Math.max(0f, distance), landing, surface, Math.max(0L, effectAreaPx),
            level, built));
    }

    /**
     * The render's end, at rest: the windows' rings move (with the tier's warm cap, halved after a
     * trim), and each held window's glass gets its route and area, grouped by its place so the
     * registry checks a neighbour against what the place in front leaves.
     * {@code screenWidthPx} by {@code screenHeightPx} is the device's screen.
     */
    @NonNull
    Commit commit(int screenWidthPx, int screenHeightPx) {
        Map<Long, Float> distances = new HashMap<>();
        Set<Long> landings = new HashSet<>();
        for (Map.Entry<Long, Seen> entry : mSeen.entrySet()) {
            distances.put(entry.getKey(), entry.getValue().distance);
            if (entry.getValue().landing) landings.add(entry.getKey());
        }
        int cap = mHalveWarm ? mTier.warmWindows / 2 : mTier.warmWindows;
        mWindows.update(distances, landings, mNow, cap);
        Map<String, GlassRegistry.Routing> routes = new HashMap<>();
        boolean rerender = false;
        for (Map.Entry<Long, Seen> entry : mSeen.entrySet()) {
            long id = entry.getKey();
            Seen seen = entry.getValue();
            GlassResidency.Ring ring = mWindows.ring(id);
            if (ring == GlassResidency.Ring.COLD) {
                if (!seen.built) continue;
                // Built this render and let go by the cap: routed as warm until the next render drops it.
                rerender = true;
                ring = GlassResidency.Ring.WARM;
            }
            routes.put(TerlGlassWear.windowElement(id), routing(seen, ring == GlassResidency.Ring.HOT));
        }
        return new Commit(routes, LiveBackdrop.effectAreaBudgetPx(mTier, screenWidthPx, screenHeightPx), rerender,
            Math.min(mWindows.nextChangeMs(), mLevels.nextChangeMs()));
    }

    /**
     * While a gesture runs (a slide, a navigation pan): {@code held}, the routes given at the last
     * rest, unchanged, with the windows the render built anew added off screen, so they register on
     * their route and no grant moves until rest. Moves no ring.
     */
    @NonNull
    Map<String, GlassRegistry.Routing> provisional(@NonNull Map<String, GlassRegistry.Routing> held) {
        Map<String, GlassRegistry.Routing> routes = new HashMap<>(held);
        for (Map.Entry<Long, Seen> entry : mSeen.entrySet()) {
            String element = TerlGlassWear.windowElement(entry.getKey());
            if (!entry.getValue().built || routes.containsKey(element)) continue;
            routes.put(element, routing(entry.getValue(), false));
        }
        return routes;
    }

    @NonNull
    private GlassRegistry.Routing routing(@NonNull Seen seen, boolean hot) {
        GlassRoute route = GlassResidency.route(seen.surface);
        long area = route == GlassRoute.EFFECT ? seen.effectAreaPx : 0L;
        int group = seen.level == PINNED_LEVEL ? GlassRegistry.Routing.NO_GROUP : seen.level;
        return new GlassRegistry.Routing(route, area, hot, group);
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
