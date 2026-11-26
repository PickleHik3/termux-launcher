package com.termux.app.wayland;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The icon of a Linux window: the one the app set at run time (xdg_toplevel_icon_v1) when it set
 * one, else the desktop entry's by app id. {@link #iconOf} is the one place that answers; the
 * window chip and the page marks both ask it.
 *
 * <p>Only the run-time icon lives here, as the silhouette a chip draws (one costs
 * {@code SILHOUETTE_PX}^2 x 4 bytes, 16 KB at 64 px), under a budget of {@link #BUDGET_BYTES}
 * counted from the pixels each bitmap holds; the least recently used goes first. An icon is
 * dropped when its window closes ({@link #drop}). Main thread only.
 *
 * <p>Each window has a serial that changes whenever its icon changes (a run-time icon set, taken
 * away or evicted; its app id changed), and the page marks key a Linux window by it
 * ({@link #key}), so a view that asks again only when its model changed sees the new icon.
 */
public final class LinuxWindowIcons {

    /** What the run-time icons may hold in all, in bytes of pixels: 32 silhouettes of 64 px. */
    public static final long BUDGET_BYTES = 512L * 1024L;

    /** The mark key of a Linux window starts with this; a Display page's key is its bound app id. */
    public static final String KEY_PREFIX = "linux-window:";

    /** What an icon lookup falls back on: the desktop entry's icon for an app id, or by name. */
    public interface Lookup {
        /** The desktop entry's icon for {@code appId}; null while unknown. */
        @Nullable Bitmap forAppId(@NonNull String appId);

        /** The icon a theme {@code name} resolves to, like an entry's {@code Icon=}; null while unknown. */
        @Nullable Bitmap named(@NonNull String name);
    }

    private static final class Slot {
        int serial;
        /** The theme name the app set; null for none. */
        @Nullable String name;
        /** The silhouette of the pixels the app set; null for none. */
        @Nullable Bitmap bitmap;
        /** When its pixels were last set or shown ({@link #clock}). */
        long used;
    }

    private final Map<Long, Slot> slots = new HashMap<>();
    /** Counts every set and every icon shown, so the least recently used pixels are known. */
    private long clock;
    private long heldBytes;
    private int version;

    /**
     * Window {@code id}'s app set its run-time icon to a theme {@code name} (null for none) and/or
     * a {@code silhouette} (null for none); both null takes the icon away. The store owns the
     * bitmap from here. A bitmap bigger than the whole budget is not kept.
     */
    public void set(long id, @Nullable String name, @Nullable Bitmap silhouette) {
        Slot slot = slots.get(id);
        if (slot == null) {
            if (name == null && silhouette == null) return;
            slot = new Slot();
            slots.put(id, slot);
        }
        release(slot);
        slot.name = name == null || name.isEmpty() ? null : name;
        slot.bitmap = silhouette != null && bytes(silhouette) <= BUDGET_BYTES ? silhouette : null;
        slot.used = ++clock;
        if (slot.bitmap != null) heldBytes += bytes(slot.bitmap);
        changed(slot);
        trim(id);
    }

    /** The window's app id changed: its entry icon may differ, so its key does. */
    public void appChanged(long id) {
        Slot slot = slots.get(id);
        if (slot == null) {
            slot = new Slot();
            slots.put(id, slot);
        }
        changed(slot);
    }

    /** Window {@code id} closed: its icon goes. */
    public void drop(long id) {
        Slot slot = slots.remove(id);
        if (slot == null) return;
        release(slot);
        version++;
    }

    /** Every window is gone. */
    public void clear() {
        if (slots.isEmpty()) return;
        slots.clear();
        heldBytes = 0;
        version++;
    }

    /**
     * Window {@code id}'s icon: the run-time one when it has one (pixels first, then a name that
     * resolves), else the desktop entry's for {@code appId}, else null. Null too for a window with
     * nothing known, and while a lookup is still resolving.
     */
    @Nullable
    public Bitmap iconOf(long id, @Nullable String appId, @NonNull Lookup lookup) {
        Slot slot = slots.get(id);
        if (slot != null) {
            if (slot.bitmap != null) {
                slot.used = ++clock;
                return slot.bitmap;
            }
            if (slot.name != null) {
                Bitmap named = lookup.named(slot.name);
                if (named != null) return named;
            }
        }
        return appId == null || appId.isEmpty() ? null : lookup.forAppId(appId);
    }

    /** Whether window {@code id} has a run-time icon (pixels or a name). */
    public boolean has(long id) {
        Slot slot = slots.get(id);
        return slot != null && (slot.bitmap != null || slot.name != null);
    }

    /** The window's icon serial; changes whenever {@link #iconOf} could answer differently. */
    public int serial(long id) {
        Slot slot = slots.get(id);
        return slot == null ? 0 : slot.serial;
    }

    /** Raised by every change to any window's icon, for a refresh that compares before it runs. */
    public int version() {
        return version;
    }

    /** What the store holds in pixels, in bytes. */
    public long heldBytes() {
        return heldBytes;
    }

    public int size() {
        return slots.size();
    }

    /** The page mark key of window {@code id}: names the window and changes with its icon. */
    @NonNull
    public String key(long id) {
        return KEY_PREFIX + id + ":" + serial(id);
    }

    /** The window id a {@link #key} names; 0 for a key that is not one (a Display page's app id). */
    public static long windowOfKey(@Nullable String key) {
        if (key == null || !key.startsWith(KEY_PREFIX)) return 0L;
        int end = key.indexOf(':', KEY_PREFIX.length());
        if (end < 0) return 0L;
        try {
            return Long.parseLong(key.substring(KEY_PREFIX.length(), end));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void changed(Slot slot) {
        slot.serial++;
        version++;
    }

    private void release(Slot slot) {
        if (slot.bitmap != null) heldBytes -= bytes(slot.bitmap);
        slot.bitmap = null;
    }

    /** Evicts the least recently used pixels until the budget holds; {@code keep} is never evicted. */
    private void trim(long keep) {
        while (heldBytes > BUDGET_BYTES) {
            Slot oldest = null;
            for (Map.Entry<Long, Slot> e : slots.entrySet()) {
                Slot slot = e.getValue();
                if (e.getKey() == keep || slot.bitmap == null) continue;
                if (oldest == null || slot.used < oldest.used) oldest = slot;
            }
            if (oldest == null) return;
            release(oldest);
            changed(oldest);
        }
    }

    private static long bytes(@NonNull Bitmap b) {
        return (long) b.getWidth() * b.getHeight() * 4L;
    }
}
