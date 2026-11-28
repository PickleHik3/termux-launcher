package com.termux.app.glass;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.window.WindowLayer;

import java.util.ArrayList;

/**
 * The glass service's registry (ADR 0029 sections 1 to 4): one entry per element, the kind each is
 * granted, and which grants moved. Pure state: no view is touched here.
 *
 * <p>A kind is granted at registration, from the policy and the slot budget, and granted again
 * only on a policy event: the Live switch (with battery saver and what else puts Live out), a
 * memory trim, the Compositor's availability, a Live slot freeing, or a sheet coming to rest over
 * the page or leaving it. Motion and a landing are not inputs, so they can never change a kind;
 * what sits above a surface counts only as a sheet at rest that hides it.</p>
 *
 * <p>At most {@link #LIVE_SLOTS} surfaces are Live, whatever they are: the dock, a panel, a float
 * or a widget all take one of the same slots, so nothing else can push a surface off Live. A
 * surface registered when every slot is taken takes the slot of the newest Live surface of a worse
 * priority, which drops to Wallpaper; with none worse it is Wallpaper until a slot frees.</p>
 *
 * <p>A surface a sheet covers ({@link #setCovered}, from {@link GlassCover}) cannot be seen, so
 * it holds no Live slot while the sheet stands: it is granted Wallpaper and its slot goes to the
 * best surface waiting, the sheet's own first. A sheet coming to rest over the page and leaving it
 * are policy events like the others.</p>
 *
 * <p><b>Routes</b> (live window residency, 2026-11-28): a Live surface draws by a route
 * ({@link GlassRoute}) the arrangement sets at rest ({@link #setRoutes}). A surface on the parity
 * route (a tile over the stored ground) is Live without a slot: it holds no recorder and no layer.
 * The slots count effect-route surfaces only, and the hot ones among them share an area budget
 * ({@link #setEffectAreaBudget}): past it, the worst priority, newest first, is granted
 * Wallpaper. A surface never routed takes the effect route with no area, as before.</p>
 *
 * <p>Entries come out back to front in the window's layer order ({@link WindowLayer}); inside a
 * layer, the worse priority first, then the order of registration. That is the order the glass
 * commit walks. The budget is a different order, best priority first, and is kept separately.</p>
 */
public final class GlassRegistry {

    /**
     * Effect-route Live surfaces at most: the recorder's budget (about 8.8 MiB of blur layers).
     * Parity-route surfaces take no slot.
     */
    public static final int LIVE_SLOTS = 8;

    /** How a surface draws its Live picture, its effect layer's area and whether it is on screen. */
    public static final class Routing {
        @NonNull public final GlassRoute route;
        /** The effect layer's area in px (the surface and its blur margin); 0 on the parity route. */
        public final long areaPx;
        /** On screen now: its layer holds pixels, so its area counts. */
        public final boolean hot;

        public Routing(@NonNull GlassRoute route, long areaPx, boolean hot) {
            this.route = route;
            this.areaPx = Math.max(0L, areaPx);
            this.hot = hot;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Routing)) return false;
            Routing r = (Routing) o;
            return route == r.route && areaPx == r.areaPx && hot == r.hot;
        }

        @Override public int hashCode() {
            return (route.hashCode() * 31 + Long.hashCode(areaPx)) * 31 + (hot ? 1 : 0);
        }
    }

    /** A surface never routed: the effect route, no area. */
    private static final Routing UNROUTED = new Routing(GlassRoute.EFFECT, 0L, false);

    private static final class Slot {
        @NonNull final GlassEntry entry;
        final long order;
        @NonNull GlassKind granted = GlassKind.WALLPAPER;
        boolean changed;
        /** A sheet stands over it and it cannot be seen ({@link GlassCover}): no Live slot. */
        boolean covered;
        /** The route its Live grant draws by, as last granted. */
        @NonNull GlassRoute grantedRoute = GlassRoute.EFFECT;

        Slot(@NonNull GlassEntry entry, long order) {
            this.entry = entry;
            this.order = order;
        }
    }

    /** Commit order, back to front: layer, then worse priority first, then registration order. */
    @NonNull private final ArrayList<Slot> mSlots = new ArrayList<>();
    /** Budget order: best priority first, then registration order. */
    @NonNull private final ArrayList<Slot> mBudget = new ArrayList<>();
    private long mNextOrder;
    private boolean mLiveOn;
    private boolean mMemoryShort;
    private boolean mCompositorOn;
    /** Element to its routing, kept for elements not registered yet too (set before they come). */
    @NonNull private final java.util.HashMap<String, Routing> mRoutes = new java.util.HashMap<>();
    private long mEffectAreaBudget = Long.MAX_VALUE;

    /**
     * Adds {@code entry} and grants its kind. False, with nothing changed, when its element
     * already has an entry: one entry per element, so no twin can come back.
     */
    public boolean register(@NonNull GlassEntry entry) {
        return register(entry, false);
    }

    /** {@link #register(GlassEntry)} for a surface a sheet already covers when it comes. */
    public boolean register(@NonNull GlassEntry entry, boolean covered) {
        if (indexOf(entry.element) >= 0) return false;
        Slot slot = new Slot(entry, mNextOrder++);
        slot.covered = covered;
        // Back to front: after every entry of an earlier layer, and of this layer's priority or a worse one.
        int at = 0;
        while (at < mSlots.size() && behind(mSlots.get(at).entry, entry)) at++;
        mSlots.add(at, slot);
        // Budget order: after every entry of its priority or a better one (it is the newest).
        int budgetAt = 0;
        while (budgetAt < mBudget.size() && mBudget.get(budgetAt).entry.priority <= entry.priority) {
            budgetAt++;
        }
        mBudget.add(budgetAt, slot);
        // Granted in budget order with the others: a newcomer takes the slot (or the area) of the
        // newest Live surface of a worse priority, which drops to Wallpaper; with none worse it is
        // Wallpaper until room frees. It learns its kind from the registration, not as a change.
        regrant();
        slot.changed = false;
        return true;
    }

    /** Drops {@code element}'s entry; a Live slot it held goes to the best surface waiting. */
    public boolean unregister(@NonNull String element) {
        int at = indexOf(element);
        if (at < 0) return false;
        Slot gone = mSlots.remove(at);
        mBudget.remove(gone);
        if (gone.granted == GlassKind.LIVE) regrant();
        return true;
    }

    /**
     * The Live policy's answer (the Live blur switch with battery saver, the Low-memory preset,
     * Reduce idle activity and what Live needs to work): a policy event when it moved.
     */
    public void setLiveOn(boolean on) {
        if (on == mLiveOn) return;
        mLiveOn = on;
        regrant();
    }

    /** Memory is short (a trim that lets the blur frames go) or no longer is: a policy event. */
    public void setMemoryShort(boolean memoryShort) {
        if (memoryShort == mMemoryShort) return;
        mMemoryShort = memoryShort;
        regrant();
    }

    /** Whether the system can blur behind the window now (the Compositor kind): a policy event. */
    public void setCompositorOn(boolean on) {
        if (on == mCompositorOn) return;
        mCompositorOn = on;
        regrant();
    }

    /**
     * Makes {@code elements} the surfaces a sheet covers now, every other one uncovered: a policy
     * event when any moved. Empty when no sheet stands over the page.
     */
    public void setCovered(@NonNull java.util.Collection<String> elements) {
        boolean moved = false;
        for (int i = 0; i < mSlots.size(); i++) {
            Slot slot = mSlots.get(i);
            boolean covered = elements.contains(slot.entry.element);
            if (covered == slot.covered) continue;
            slot.covered = covered;
            moved = true;
        }
        if (moved) regrant();
    }

    /**
     * The arrangement's routes at rest: {@code routes} replaces every routing given before, and an
     * element left out takes the effect route with no area. A policy event when a grant or a
     * granted route moved. Elements not registered yet keep theirs for when they come.
     */
    public void setRoutes(@NonNull java.util.Map<String, Routing> routes) {
        if (mRoutes.equals(routes)) return;
        mRoutes.clear();
        mRoutes.putAll(routes);
        regrant();
    }

    /**
     * The area, in px, the hot effect-route surfaces may take together; {@link Long#MAX_VALUE}
     * (the default) for no bound. A policy event when it moved a grant.
     */
    public void setEffectAreaBudget(long areaPx) {
        long budget = Math.max(0L, areaPx);
        if (budget == mEffectAreaBudget) return;
        mEffectAreaBudget = budget;
        regrant();
    }

    /** The route {@code element}'s grant draws by; null when it has no entry. */
    @Nullable
    public GlassRoute route(@NonNull String element) {
        int at = indexOf(element);
        return at < 0 ? null : mSlots.get(at).grantedRoute;
    }

    /** The hot effect-route Live surfaces' area together, in px. */
    public long effectAreaPx() {
        long area = 0L;
        for (int i = 0; i < mSlots.size(); i++) {
            Slot slot = mSlots.get(i);
            if (slot.granted != GlassKind.LIVE || slot.grantedRoute != GlassRoute.EFFECT) continue;
            Routing routing = routing(slot);
            if (routing.hot) area += routing.areaPx;
        }
        return area;
    }

    /** Whether {@code element} is covered by a sheet; false when it has no entry. */
    public boolean covered(@NonNull String element) {
        int at = indexOf(element);
        return at >= 0 && mSlots.get(at).covered;
    }

    /** The kind granted to {@code element}; null when it has no entry. */
    @Nullable
    public GlassKind granted(@NonNull String element) {
        int at = indexOf(element);
        return at < 0 ? null : mSlots.get(at).granted;
    }

    /** The entry of {@code element}; null when it has none. */
    @Nullable
    public GlassEntry entry(@NonNull String element) {
        int at = indexOf(element);
        return at < 0 ? null : mSlots.get(at).entry;
    }

    /** How many entries there are. */
    public int size() {
        return mSlots.size();
    }

    /**
     * The entry at {@code index} in commit order, back to front: the window's layers in their
     * table's order (the ground's frost, the pages' slabs, widgets and floats, the chrome's dock,
     * the sheets' panels).
     */
    @NonNull
    public GlassEntry entryAt(int index) {
        return mSlots.get(index).entry;
    }

    /** The kind granted to the entry at {@code index} in commit order. */
    @NonNull
    public GlassKind grantedAt(int index) {
        return mSlots.get(index).granted;
    }

    /**
     * Whether the grant at {@code index} moved since it was last taken, and takes it: the service
     * re-dresses that surface once.
     */
    public boolean takeChanged(int index) {
        Slot slot = mSlots.get(index);
        boolean changed = slot.changed;
        slot.changed = false;
        return changed;
    }

    /** Whether any grant moved and has not been taken yet. */
    public boolean anyChanged() {
        for (int i = 0; i < mSlots.size(); i++) {
            if (mSlots.get(i).changed) return true;
        }
        return false;
    }

    /** How many entries ask to be visited on every drawn frame ({@link GlassEntry#frame}). */
    public int frameCount() {
        int count = 0;
        for (int i = 0; i < mSlots.size(); i++) {
            if (mSlots.get(i).entry.frame != null) count++;
        }
        return count;
    }

    /** How many surfaces are granted Live, on either route. */
    public int liveCount() {
        int count = 0;
        for (int i = 0; i < mSlots.size(); i++) {
            if (mSlots.get(i).granted == GlassKind.LIVE) count++;
        }
        return count;
    }

    /** How many Live surfaces hold a slot: the effect-route ones. */
    public int effectCount() {
        int count = 0;
        for (int i = 0; i < mSlots.size(); i++) {
            Slot slot = mSlots.get(i);
            if (slot.granted == GlassKind.LIVE && slot.grantedRoute == GlassRoute.EFFECT) count++;
        }
        return count;
    }

    @NonNull
    private Routing routing(@NonNull Slot slot) {
        Routing routing = mRoutes.get(slot.entry.element);
        return routing == null ? UNROUTED : routing;
    }

    // ---- Grants ------------------------------------------------------------------------------

    /** What {@code slot} wants under the policy now, before the slot budget. */
    @NonNull
    private GlassKind grantable(@NonNull Slot slot) {
        switch (slot.entry.wants) {
            case LIVE:
                // A parity surface costs only the stored frame, so short memory does not move it.
                if (routing(slot).route == GlassRoute.PARITY) {
                    return mLiveOn && !slot.covered ? GlassKind.LIVE : GlassKind.WALLPAPER;
                }
                return mLiveOn && !mMemoryShort && !slot.covered
                    ? GlassKind.LIVE : GlassKind.WALLPAPER;
            case COMPOSITOR:
                return mCompositorOn ? GlassKind.COMPOSITOR : GlassKind.WALLPAPER;
            default:
                return slot.entry.wants;
        }
    }

    /**
     * Every entry granted again in budget order: best priority first, oldest first within it. An
     * effect-route surface takes a slot, and a hot one its area; past either, Wallpaper. So the
     * ones that lose are the newest of the worst priority.
     */
    private void regrant() {
        int live = 0;
        long area = 0L;
        for (int i = 0; i < mBudget.size(); i++) {
            Slot slot = mBudget.get(i);
            GlassKind kind = grantable(slot);
            Routing routing = routing(slot);
            GlassRoute route = routing.route;
            if (kind == GlassKind.LIVE && route == GlassRoute.EFFECT) {
                long needs = routing.hot ? routing.areaPx : 0L;
                if (live < LIVE_SLOTS && area + needs <= mEffectAreaBudget) {
                    live++;
                    area += needs;
                } else {
                    kind = GlassKind.WALLPAPER;
                }
            }
            if (kind != GlassKind.LIVE) route = GlassRoute.EFFECT;
            // A Live surface whose route moved is dressed again too: it takes or lets go of its layers.
            if (kind != slot.granted || route != slot.grantedRoute) {
                slot.granted = kind;
                slot.grantedRoute = route;
                slot.changed = true;
            }
        }
    }

    /** Whether {@code held} is drawn behind {@code added} (and so stands before it in commit order). */
    private static boolean behind(@NonNull GlassEntry held, @NonNull GlassEntry added) {
        int layers = held.layer.number() - added.layer.number();
        if (layers != 0) return layers < 0;
        return held.priority >= added.priority;
    }

    private int indexOf(@NonNull String element) {
        for (int i = 0; i < mSlots.size(); i++) {
            if (mSlots.get(i).entry.element.equals(element)) return i;
        }
        return -1;
    }
}
