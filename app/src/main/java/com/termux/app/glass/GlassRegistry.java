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
 * <p>Entries come out back to front in the window's layer order ({@link WindowLayer}); inside a
 * layer, the worse priority first, then the order of registration. That is the order the glass
 * commit walks. The budget is a different order, best priority first, and is kept separately.</p>
 */
public final class GlassRegistry {

    /** Live surfaces at most: the recorder's budget (about 8.8 MiB of blur layers). */
    public static final int LIVE_SLOTS = 8;

    private static final class Slot {
        @NonNull final GlassEntry entry;
        final long order;
        @NonNull GlassKind granted = GlassKind.WALLPAPER;
        boolean changed;
        /** A sheet stands over it and it cannot be seen ({@link GlassCover}): no Live slot. */
        boolean covered;

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
        GlassKind kind = grantable(slot);
        if (kind == GlassKind.LIVE && liveCount() >= LIVE_SLOTS) {
            Slot evict = newestLiveWorseThan(entry.priority);
            if (evict == null) {
                kind = GlassKind.WALLPAPER;
            } else {
                evict.granted = GlassKind.WALLPAPER;
                evict.changed = true;
            }
        }
        slot.granted = kind;
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

    /** How many surfaces are granted Live. */
    public int liveCount() {
        int count = 0;
        for (int i = 0; i < mSlots.size(); i++) {
            if (mSlots.get(i).granted == GlassKind.LIVE) count++;
        }
        return count;
    }

    // ---- Grants ------------------------------------------------------------------------------

    /** What {@code slot} wants under the policy now, before the slot budget. */
    @NonNull
    private GlassKind grantable(@NonNull Slot slot) {
        switch (slot.entry.wants) {
            case LIVE:
                return mLiveOn && !mMemoryShort && !slot.covered
                    ? GlassKind.LIVE : GlassKind.WALLPAPER;
            case COMPOSITOR:
                return mCompositorOn ? GlassKind.COMPOSITOR : GlassKind.WALLPAPER;
            default:
                return slot.entry.wants;
        }
    }

    /** Every entry granted again in budget order: best priority first, oldest first within it. */
    private void regrant() {
        int live = 0;
        for (int i = 0; i < mBudget.size(); i++) {
            Slot slot = mBudget.get(i);
            GlassKind kind = grantable(slot);
            if (kind == GlassKind.LIVE) {
                if (live < LIVE_SLOTS) live++;
                else kind = GlassKind.WALLPAPER;
            }
            if (kind != slot.granted) {
                slot.granted = kind;
                slot.changed = true;
            }
        }
    }

    @Nullable
    private Slot newestLiveWorseThan(int priority) {
        // The budget's last entries are the worst priority, newest last.
        for (int i = mBudget.size() - 1; i >= 0; i--) {
            Slot slot = mBudget.get(i);
            if (slot.entry.priority <= priority) break;
            if (slot.granted == GlassKind.LIVE) return slot;
        }
        return null;
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
