package com.termux.app.wayland;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.x11.X11WindowIconResolver;

import java.util.HashMap;
import java.util.Map;

/**
 * The icon of a Linux window: the one the app set at run time (xdg_toplevel_icon_v1) when it set
 * one, else the desktop entry's by app id. {@link #iconOf} is the one place that answers, in one
 * order, in the form the caller wants ({@link Form}): the single-colour silhouette a window chip
 * wears, or the icon in its own colours, which the page marks draw.
 *
 * <p>A run-time icon is kept in colour, at most {@link #COLOUR_MAX_PX} on a side, and its
 * silhouette is derived from it; both are counted by the pixels they hold, a colour icon at its
 * full size costs 36 KiB and its silhouette 16 KiB, under a budget of {@link #BUDGET_BYTES}; the
 * least recently used goes first. An icon is dropped when its window closes ({@link #drop}). The
 * entry's icons are not kept here: they come from the launcher's own bounded icon store. Main
 * thread only.
 *
 * <p>Each window has a serial that changes whenever its icon changes (a run-time icon set, taken
 * away or evicted; its app id changed), and the page marks key a Linux window by it
 * ({@link #key}), so a view that asks again only when its model changed sees the new icon.
 */
public final class LinuxWindowIcons {

    /** The two forms an icon is asked for. */
    public enum Form {
        /** White shape carrying the artwork's outline and brightness in its alpha: the window chip's. */
        SILHOUETTE,
        /** The artwork in its own colours: the page mark's. */
        COLOUR
    }

    /** The most a run-time icon keeps on a side, in px. */
    public static final int COLOUR_MAX_PX = 96;

    /** What the run-time icons may hold in all, in bytes of pixels: 19 icons of both forms at full size. */
    public static final long BUDGET_BYTES = 1024L * 1024L;

    /** The mark key of a Linux window starts with this; a Display page's key is its bound app id. */
    public static final String KEY_PREFIX = "linux-window:";

    /** What an icon lookup falls back on: the desktop entry's icon for an app id, or by name. */
    public interface Lookup {
        /** The desktop entry's icon for {@code appId} in {@code form}; null while unknown. */
        @Nullable Bitmap forAppId(@NonNull String appId, @NonNull Form form);

        /** The icon a theme {@code name} resolves to, like an entry's {@code Icon=}; null while unknown. */
        @Nullable Bitmap named(@NonNull String name, @NonNull Form form);
    }

    private static final class Slot {
        int serial;
        /** The theme name the app set; null for none. */
        @Nullable String name;
        /** The pixels the app set, in colour; null for none. */
        @Nullable Bitmap colour;
        /** The chip's silhouette of {@link #colour}. */
        @Nullable Bitmap silhouette;
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
     * {@code pixels} (null for none); both null takes the icon away. The pixels are cut to
     * {@link #COLOUR_MAX_PX} and the silhouette is derived; the store owns the bitmap from here.
     */
    public void set(long id, @Nullable String name, @Nullable Bitmap pixels) {
        Slot slot = slots.get(id);
        if (slot == null) {
            if ((name == null || name.isEmpty()) && pixels == null) return;
            slot = new Slot();
            slots.put(id, slot);
        }
        release(slot);
        slot.name = name == null || name.isEmpty() ? null : name;
        if (pixels != null) {
            Bitmap colour = fit(pixels);
            Bitmap silhouette = X11WindowIconResolver.silhouette(colour, X11WindowIconResolver.SILHOUETTE_PX);
            long cost = bytes(colour) + bytes(silhouette);
            if (cost <= BUDGET_BYTES) {
                slot.colour = colour;
                slot.silhouette = silhouette;
                heldBytes += cost;
            }
        }
        slot.used = ++clock;
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
     * Window {@code id}'s icon in {@code form}: the run-time one when it has one (pixels first,
     * then a name that resolves), else the desktop entry's for {@code appId}, else null. Null too
     * while a lookup is still resolving.
     */
    @Nullable
    public Bitmap iconOf(long id, @Nullable String appId, @NonNull Form form, @NonNull Lookup lookup) {
        Slot slot = slots.get(id);
        if (slot != null) {
            if (slot.colour != null) {
                slot.used = ++clock;
                return form == Form.COLOUR ? slot.colour : slot.silhouette;
            }
            if (slot.name != null) {
                Bitmap named = lookup.named(slot.name, form);
                if (named != null) return named;
            }
        }
        return appId == null || appId.isEmpty() ? null : lookup.forAppId(appId, form);
    }

    /** Whether window {@code id} has a run-time icon (pixels or a name). */
    public boolean has(long id) {
        Slot slot = slots.get(id);
        return slot != null && (slot.colour != null || slot.name != null);
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

    /** What the store holds in pixels, in bytes, both forms counted. */
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
        if (slot.colour != null) heldBytes -= bytes(slot.colour);
        if (slot.silhouette != null) heldBytes -= bytes(slot.silhouette);
        slot.colour = null;
        slot.silhouette = null;
    }

    /** {@code source} cut to {@link #COLOUR_MAX_PX} on its longer side; the same bitmap when it fits. */
    @NonNull
    private static Bitmap fit(@NonNull Bitmap source) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        if (longest <= COLOUR_MAX_PX) return source;
        float scale = COLOUR_MAX_PX / (float) longest;
        Bitmap scaled = Bitmap.createScaledBitmap(source,
            Math.max(1, Math.round(source.getWidth() * scale)),
            Math.max(1, Math.round(source.getHeight() * scale)), true);
        if (scaled != source) source.recycle();
        return scaled;
    }

    /** Evicts the least recently used pixels until the budget holds; {@code keep} is never evicted. */
    private void trim(long keep) {
        while (heldBytes > BUDGET_BYTES) {
            Slot oldest = null;
            for (Map.Entry<Long, Slot> e : slots.entrySet()) {
                Slot slot = e.getValue();
                if (e.getKey() == keep || slot.colour == null) continue;
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
