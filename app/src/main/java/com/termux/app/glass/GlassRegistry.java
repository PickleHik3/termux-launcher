package com.termux.app.glass;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

/**
 * The glass service's registry (ADR 0029 sections 1 to 4): one entry per element, the kind each is
 * granted, and which grants moved. Pure state: no view is touched here.
 *
 * <p>A kind is granted at registration, from the policy and the slot budget, and granted again
 * only on a policy event: the Live switch (with battery saver and what else puts Live out), a
 * memory trim, the Compositor's availability, or a Live slot freeing. Motion, a landing and what
 * sits above a surface are not inputs, so they can never change a kind.</p>
 *
 * <p>At most {@link #LIVE_SLOTS} surfaces are Live. A surface registered when every slot is taken
 * takes the slot of the newest Live surface of a worse priority, which drops to Wallpaper; with
 * none worse it is Wallpaper until a slot frees.</p>
 */
public final class GlassRegistry {

    /** Live surfaces at most: the recorder's budget (about 8.8 MiB of blur layers). */
    public static final int LIVE_SLOTS = 8;

    private static final class Slot {
        @NonNull final GlassEntry entry;
        final long order;
        @NonNull GlassKind granted = GlassKind.WALLPAPER;
        boolean changed;

        Slot(@NonNull GlassEntry entry, long order) {
            this.entry = entry;
            this.order = order;
        }
    }

    /** Back to front: worse priority first, then the order of registration. */
    @NonNull private final ArrayList<Slot> mSlots = new ArrayList<>();
    private long mNextOrder;
    private boolean mLiveOn;
    private boolean mMemoryShort;
    private boolean mCompositorOn;

    /**
     * Adds {@code entry} and grants its kind. False, with nothing changed, when its element
     * already has an entry: one entry per element, so no twin can come back.
     */
    public boolean register(@NonNull GlassEntry entry) {
        if (indexOf(entry.element) >= 0) return false;
        Slot slot = new Slot(entry, mNextOrder++);
        // Back to front: after every entry of its priority or a worse one.
        int at = 0;
        while (at < mSlots.size() && mSlots.get(at).entry.priority >= entry.priority) at++;
        mSlots.add(at, slot);
        GlassKind kind = grantable(entry.wants);
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
     * The entry at {@code index} in commit order, back to front: widgets on the pages, then the
     * floating windows over them, then the dock in the chrome.
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

    /** How many surfaces are granted Live. */
    public int liveCount() {
        int count = 0;
        for (int i = 0; i < mSlots.size(); i++) {
            if (mSlots.get(i).granted == GlassKind.LIVE) count++;
        }
        return count;
    }

    // ---- Grants ------------------------------------------------------------------------------

    /** {@code wants} under the policy now, before the slot budget. */
    @NonNull
    private GlassKind grantable(@NonNull GlassKind wants) {
        switch (wants) {
            case LIVE:
                return mLiveOn && !mMemoryShort ? GlassKind.LIVE : GlassKind.WALLPAPER;
            case COMPOSITOR:
                return mCompositorOn ? GlassKind.COMPOSITOR : GlassKind.WALLPAPER;
            default:
                return wants;
        }
    }

    /** Every entry granted again in budget order: best priority first, oldest first within it. */
    private void regrant() {
        int live = 0;
        for (int priority = GlassEntry.PRIORITY_DOCK; priority <= GlassEntry.PRIORITY_WIDGET;
             priority++) {
            for (int i = 0; i < mSlots.size(); i++) {
                Slot slot = mSlots.get(i);
                if (slot.entry.priority != priority) continue;
                GlassKind kind = grantable(slot.entry.wants);
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
    }

    @Nullable
    private Slot newestLiveWorseThan(int priority) {
        // Back to front puts the worst priority first and its newest last within its run.
        Slot found = null;
        for (int i = 0; i < mSlots.size(); i++) {
            Slot slot = mSlots.get(i);
            if (slot.entry.priority <= priority) break;
            if (slot.granted != GlassKind.LIVE) continue;
            if (found == null || slot.entry.priority > found.entry.priority
                || (slot.entry.priority == found.entry.priority && slot.order > found.order)) {
                found = slot;
            }
        }
        return found;
    }

    private int indexOf(@NonNull String element) {
        for (int i = 0; i < mSlots.size(); i++) {
            if (mSlots.get(i).entry.element.equals(element)) return i;
        }
        return -1;
    }
}
