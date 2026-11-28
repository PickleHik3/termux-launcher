package com.termux.app.glass;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Live window residency (project-docs/active/live-window-residency-2026-11-28.md): which Live
 * surfaces hold what, streamed the way a game streams level chunks. Pure: no views.
 *
 * <ul>
 *   <li><b>Rings</b> ({@link Rings}): a window is {@link Ring#HOT} while any of it is on screen,
 *   {@link Ring#WARM} while one gesture can bring it there (within {@link #ENTER_SCREENS}, or a
 *   predicted landing), {@link Ring#COLD} otherwise. A column leaves warm only beyond
 *   {@link #LEAVE_SCREENS} <em>and</em> after {@link #DWELL_MS} outside the enter ring, so
 *   back-and-forth never drops and rebuilds it; a place, whose distance is whole, leaves beyond
 *   {@link #LEAVE_PLACES} after the dwell ({@link #placeRings}).</li>
 *   <li><b>Routes</b> ({@link #route}): a tile draws the stored frame ({@link GlassRoute#PARITY}),
 *   whose drawable already follows a live scene's frames; a Float, the bar and a sheet, with
 *   windows beneath them, take the recorder's layers ({@link GlassRoute#EFFECT}).</li>
 *   <li><b>Tiers</b> ({@link #tier}): the effect layers' area and the warm count by RAM.</li>
 *   <li><b>Trim</b> ({@link #trim}): what each memory signal lets go.</li>
 * </ul>
 *
 * <p>Every decision here runs at rest, at a gesture's door or at a policy event, never per frame.
 */
public final class GlassResidency {

    private GlassResidency() {}

    /** What a window holds. */
    public enum Ring {
        /** On screen: views shown, GPU layers held. */
        HOT,
        /** One gesture away: views built and laid out, glass entry granted, no layer pixels. */
        WARM,
        /** Nothing: views dropped, entry let go. */
        COLD
    }

    /** What kind of surface a Live entry is, for its route. */
    public enum Surface {
        /**
         * A Scrolling column, a stacked half, a Dwindle leaf, a full-screen or maximised window, and
         * a tile carried in navigation mode (its faded frame keeps its slot over the ground; the
         * ghost that moves wears no glass).
         */
        TILE,
        /** A Workspace's, a Home page's or a pinned Float: tiles and lower Floats are beneath. */
        FLOAT,
        /** The control bar: the moving stack is beneath it. */
        BAR,
        /** A sheet: everything is beneath it. */
        SHEET
    }

    /** A window enters warm within this many screens of the screen. */
    public static final float ENTER_SCREENS = 1f;
    /** A warm window may leave only beyond this many screens. */
    public static final float LEAVE_SCREENS = 2f;
    /** ... and only after this long outside the enter ring. */
    public static final long DWELL_MS = 2000L;
    /**
     * A place (whole levels apart) leaves beyond this many places after the dwell: the band is for
     * a column's fractional distance; a place two away would otherwise stay for good.
     */
    public static final float LEAVE_PLACES = 1f;
    /**
     * Navigation mode's centre moves only when the view is this far from it (in levels), not half
     * a level as rounding does: a finger hovering between two places changes nothing.
     */
    public static final float NAVIGATION_CENTRE_BAND = 0.75f;

    private static final long GIB = 1024L * 1024L * 1024L;

    /** One RAM tier's budgets (section 4.6). */
    public static final class Tier {
        /** The hot effect layers' area at most, in screens of the device's own size. */
        public final float effectScreens;
        /**
         * Warm windows beyond the adjacent ring at most: columns in the band, and every window of a
         * place waiting out its dwell or predicted as a landing (tiles, Floats, full-screen and
         * Linux windows with their buffers alike).
         */
        public final int warmWindows;
        /** Places beyond the adjacent ring holding views at most (a landing, one waiting out its dwell). */
        public final int warmPlaces;

        Tier(float effectScreens, int warmWindows, int warmPlaces) {
            this.effectScreens = effectScreens;
            this.warmWindows = warmWindows;
            this.warmPlaces = warmPlaces;
        }
    }

    static final Tier ABOVE_8_GIB = new Tier(2.5f, 16, 2);
    static final Tier ABOVE_6_GIB = new Tier(1.75f, 12, 2);
    static final Tier ABOVE_4_GIB = new Tier(1f, 8, 1);
    static final Tier SMALL = new Tier(0.5f, 4, 1);

    /** The tier of a phone with {@code totalMemBytes} of RAM ({@code MemoryInfo.totalMem}). */
    @NonNull
    public static Tier tier(long totalMemBytes, boolean lowRamDevice) {
        if (lowRamDevice || totalMemBytes <= 4L * GIB) return SMALL;
        if (totalMemBytes <= 6L * GIB) return ABOVE_4_GIB;
        if (totalMemBytes <= 8L * GIB) return ABOVE_6_GIB;
        return ABOVE_8_GIB;
    }

    /**
     * The area the hot effect layers of the place in front may take, in px: the tier's screens
     * less {@code heaviestNeighbourPx}, the effect area the heaviest neighbour would show beside it
     * in a crossing, so any one crossing fits. Never below zero.
     */
    public static long effectAreaBudgetPx(@NonNull Tier tier, int screenWidthPx, int screenHeightPx,
                                          long heaviestNeighbourPx) {
        long screen = (long) Math.max(0, screenWidthPx) * (long) Math.max(0, screenHeightPx);
        long budget = Math.round(tier.effectScreens * screen);
        return Math.max(0L, budget - Math.max(0L, heaviestNeighbourPx));
    }

    /**
     * The route of a surface: a tile takes {@link GlassRoute#PARITY}, whatever its ring and ground.
     * Only the ground is beneath a tile, and the stored frame's drawable is a scene surface that
     * draws the live scene's frame while one publishes, so parity already follows the scene, on
     * screen, at no layer cost (review P2-5). A Float, the bar and a sheet, with windows beneath
     * them, take {@link GlassRoute#EFFECT}.
     */
    @NonNull
    public static GlassRoute route(@NonNull Surface surface) {
        return surface == Surface.TILE ? GlassRoute.PARITY : GlassRoute.EFFECT;
    }

    /**
     * Navigation mode's centre (the level the warm ring follows) after the view moved to
     * {@code position}: it stays at {@code centre} until the view is more than
     * {@link #NAVIGATION_CENTRE_BAND} from it, then becomes the level nearest the view, clamped to
     * {@code first} .. {@code last}.
     */
    public static int navigationCentre(int centre, float position, int first, int last) {
        int clamped = Math.max(first, Math.min(last, centre));
        if (Math.abs(position - clamped) <= NAVIGATION_CENTRE_BAND) return clamped;
        return Math.max(first, Math.min(last, Math.round(position)));
    }

    // ---- Trim ------------------------------------------------------------------------------------

    /** What a memory signal lets go (section 4.8). Shells keep running in every case. */
    public static final class Trim {
        /** Warm views beyond the adjacent ring go. */
        public final boolean dropBeyondAdjacent;
        /** The warm count halves until the next resume. */
        public final boolean halveWarm;
        /** Views of every level but the one in front go (and every warm picture). */
        public final boolean frontOnly;
        /** Floats and scene tiles go to the stored frame by a policy event. */
        public final boolean memoryShort;

        Trim(boolean dropBeyondAdjacent, boolean halveWarm, boolean frontOnly, boolean memoryShort) {
            this.dropBeyondAdjacent = dropBeyondAdjacent;
            this.halveWarm = halveWarm;
            this.frontOnly = frontOnly;
            this.memoryShort = memoryShort;
        }

        /** Whether this signal lets anything go. */
        public boolean any() {
            return dropBeyondAdjacent || halveWarm || frontOnly || memoryShort;
        }
    }

    /** {@code ComponentCallbacks2.TRIM_MEMORY_*}, copied so this stays pure. */
    static final int TRIM_RUNNING_MODERATE = 5;
    static final int TRIM_RUNNING_LOW = 10;
    static final int TRIM_RUNNING_CRITICAL = 15;
    static final int TRIM_UI_HIDDEN = 20;

    private static final Trim NOTHING = new Trim(false, false, false, false);

    /** What {@code level} (a {@code TRIM_MEMORY_*}) lets go. */
    @NonNull
    public static Trim trim(int level) {
        if (level >= TRIM_RUNNING_LOW) return new Trim(true, true, true, true);
        if (level >= TRIM_RUNNING_MODERATE) return new Trim(true, true, false, false);
        return NOTHING;
    }

    // ---- Rings -----------------------------------------------------------------------------------

    /** The rings of places: a place leaves beyond {@link #LEAVE_PLACES} after the dwell. */
    @NonNull
    public static Rings placeRings() {
        return new Rings(LEAVE_PLACES);
    }

    /**
     * The rings of a set of keys (window ids, or levels), with hysteresis and dwell. Updated at
     * rest with each key's distance from the screen; keys left out are cold at once (their place
     * went, or holds no views).
     */
    public static final class Rings {

        private static final class State {
            @NonNull Ring ring = Ring.COLD;
            float distance;
            boolean landing;
            /** When it last left the enter ring; -1 while inside it. */
            long outsideSince = -1L;
        }

        @NonNull private final HashMap<Long, State> mStates = new HashMap<>();
        private long mNextChangeMs = Long.MAX_VALUE;
        private final float mLeave;

        /** Columns' rings: leave beyond {@link #LEAVE_SCREENS}. */
        public Rings() {
            this(LEAVE_SCREENS);
        }

        /** Rings whose keys leave beyond {@code leave} after the dwell. */
        public Rings(float leave) {
            mLeave = Math.max(ENTER_SCREENS, leave);
        }

        /**
         * Takes every key's distance now, in screens from the screen (0 while any of it is on
         * screen), the predicted landings, and the time; {@code warmCap} bounds the warm keys
         * beyond the enter ring, farthest let go first, landings last. Whether any ring moved.
         */
        public boolean update(@NonNull Map<Long, Float> distances, @NonNull Set<Long> landings, long nowMs,
                              int warmCap) {
            boolean moved = false;
            mNextChangeMs = Long.MAX_VALUE;
            for (java.util.Iterator<Map.Entry<Long, State>> it = mStates.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Long, State> held = it.next();
                if (!distances.containsKey(held.getKey())) {
                    it.remove();
                    moved = true;
                }
            }
            for (Map.Entry<Long, Float> entry : distances.entrySet()) {
                long key = entry.getKey();
                float distance = Math.max(0f, entry.getValue());
                boolean landing = landings.contains(key);
                State state = mStates.get(key);
                Ring was = state == null ? Ring.COLD : state.ring;
                Ring now = next(was, state, distance, landing, nowMs);
                if (now == Ring.COLD) {
                    if (state != null) {
                        mStates.remove(key);
                        moved = true;
                    }
                    continue;
                }
                if (state == null) {
                    state = new State();
                    mStates.put(key, state);
                }
                boolean inside = landing || distance <= ENTER_SCREENS;
                if (inside) state.outsideSince = -1L;
                else if (state.outsideSince < 0L) state.outsideSince = nowMs;
                state.distance = distance;
                state.landing = landing;
                if (now != was) moved = true;
                state.ring = now;
                // Only a key beyond the leave ring can go when its dwell runs out; one in the band
                // between the rings stays until it moves, so it books nothing.
                if (now == Ring.WARM && !inside && distance > mLeave) {
                    mNextChangeMs = Math.min(mNextChangeMs, state.outsideSince + DWELL_MS);
                }
            }
            if (capWarm(Math.max(0, warmCap))) moved = true;
            return moved;
        }

        /** The ring {@code key} holds now. */
        @NonNull
        public Ring ring(long key) {
            State state = mStates.get(key);
            return state == null ? Ring.COLD : state.ring;
        }

        /**
         * The ring {@code key} would take at {@code distance} now, without moving anything (the
         * warm cap, which needs every key, is left to {@link #update}).
         */
        @NonNull
        public Ring peek(long key, float distance, boolean landing, long nowMs) {
            State state = mStates.get(key);
            return next(state == null ? Ring.COLD : state.ring, state, Math.max(0f, distance), landing, nowMs);
        }

        /** Lets every warm key farther than {@code screens} go at once (a memory trim). */
        public void letGoBeyond(float screens) {
            for (java.util.Iterator<State> it = mStates.values().iterator(); it.hasNext(); ) {
                State state = it.next();
                if (state.ring == Ring.WARM && !state.landing && state.distance > screens) it.remove();
            }
        }

        /** Every key that holds views (hot or warm). */
        @NonNull
        public Set<Long> held() {
            return Collections.unmodifiableSet(new HashSet<>(mStates.keySet()));
        }

        /**
         * When a warm key's dwell runs out and a rest pass could let it go; {@link Long#MAX_VALUE}
         * when none waits.
         */
        public long nextChangeMs() {
            return mNextChangeMs;
        }

        /** Lets every key go (background, a trim). */
        public void clear() {
            mStates.clear();
            mNextChangeMs = Long.MAX_VALUE;
        }

        @NonNull
        private Ring next(@NonNull Ring was, State state, float distance, boolean landing, long nowMs) {
            if (distance <= 0f) return Ring.HOT;
            if (landing || distance <= ENTER_SCREENS) return Ring.WARM;
            if (was == Ring.COLD) return Ring.COLD;
            // Held and outside the enter ring: it leaves only beyond the leave ring and after the dwell.
            long since = state == null || state.outsideSince < 0L ? nowMs : state.outsideSince;
            boolean far = distance > mLeave;
            boolean dwelt = nowMs - since >= DWELL_MS;
            return far && dwelt ? Ring.COLD : Ring.WARM;
        }

        /** Lets the farthest warm keys beyond the enter ring go past {@code cap}; landings go last. */
        private boolean capWarm(int cap) {
            ArrayList<Map.Entry<Long, State>> beyond = new ArrayList<>();
            for (Map.Entry<Long, State> entry : mStates.entrySet()) {
                State state = entry.getValue();
                // Within the enter ring a window is one gesture away and is never capped.
                if (state.ring == Ring.WARM && state.distance > ENTER_SCREENS) {
                    beyond.add(entry);
                }
            }
            if (beyond.size() <= cap) return false;
            // Kept first: landings, then the nearest; the tail goes.
            Collections.sort(beyond, (a, b) -> {
                if (a.getValue().landing != b.getValue().landing) return a.getValue().landing ? -1 : 1;
                int byDistance = Float.compare(a.getValue().distance, b.getValue().distance);
                return byDistance != 0 ? byDistance : Long.compare(a.getKey(), b.getKey());
            });
            for (int i = cap; i < beyond.size(); i++) mStates.remove(beyond.get(i).getKey());
            return true;
        }
    }
}
