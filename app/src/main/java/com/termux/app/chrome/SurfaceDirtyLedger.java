package com.termux.app.chrome;

import android.graphics.Rect;

import androidx.annotation.NonNull;

import java.util.EnumMap;

/**
 * The chrome's "what is already painted where" bookkeeping, in one place.
 *
 * <p>Every blurred surface has to know whether the crop it is showing still matches the geometry,
 * the blur radius and the wallpaper source it was cut for — otherwise a re-render either allocates
 * a fresh full-screen bitmap on a frame that did not need one, or keeps showing a crop of the
 * wrong region. That used to be seven copy-pasted flag triples on the Activity
 * ({@code mXBackdropDirty} / {@code mLastXBackdropBlurRadiusDp} / {@code mLastXBackdropManagedSource}
 * plus the frost pair), which drifted apart: only some paths reset all three.</p>
 *
 * <p>The semantics are preserved exactly as they shipped, including the one asymmetry between the
 * two families:</p>
 * <ul>
 *   <li><b>Backdrops</b> ({@link Backdrop}) each own their dirty flag, last radius, last managed-source
 *       flag and last target rect.</li>
 *   <li><b>Frosts</b> ({@link FrostRect}, {@link FrostRadius}) share a <em>single</em> dirty flag —
 *       they are all cut from the same shared pre-blurred wallpaper frame, so whatever invalidates
 *       one invalidates all — while keeping a rect per surface and a radius per tuning group (the
 *       top pane's status band and window bar follow one radius, the palette and the drawer plane
 *       each follow their own; the sheet plane rides the palette's radius with a rect of its own).</li>
 * </ul>
 */
public final class SurfaceDirtyLedger {

    /** Blurred backdrops behind a glass surface, each independently invalidated. */
    public enum Backdrop {
        /** The shared accessory (dock + unified keyboard) crop. */
        ACCESSORY,
        /** The under-pill gesture-nav strip in the decor overlay. */
        DECOR_NAV_BAR,
        /** The keyboard-local crop used when the keyboard is not on the unified dock material. */
        IN_APP_KEYBOARD
    }

    /** Wallpaper-frost crop targets. */
    public enum FrostRect {
        TOP_PANE_STATUS,
        TOP_PANE_WINDOW_BAR,
        COMMAND_PALETTE,
        TERMINAL_SHEET,
        APP_DRAWER
    }

    /** Radius groups for the frost crops: surfaces tuned by the same slider share an entry. */
    public enum FrostRadius {
        TOP_PANE,
        COMMAND_PALETTE,
        APP_DRAWER
    }

    private static final class BackdropEntry {
        boolean dirty = true;
        int lastRadiusDp = -1;
        boolean lastManagedSource;
        @NonNull final Rect lastRect = new Rect();
    }

    @NonNull private final EnumMap<Backdrop, BackdropEntry> mBackdrops = new EnumMap<>(Backdrop.class);
    @NonNull private final EnumMap<FrostRect, Rect> mFrostRects = new EnumMap<>(FrostRect.class);
    @NonNull private final EnumMap<FrostRadius, Integer> mFrostRadii = new EnumMap<>(FrostRadius.class);
    private boolean mFrostDirty = true;

    public SurfaceDirtyLedger() {
        for (Backdrop backdrop : Backdrop.values())
            mBackdrops.put(backdrop, new BackdropEntry());
        for (FrostRect rect : FrostRect.values())
            mFrostRects.put(rect, new Rect());
        for (FrostRadius radius : FrostRadius.values())
            mFrostRadii.put(radius, -1);
    }

    // ---------------------------------------------------------------- backdrops

    public boolean isDirty(@NonNull Backdrop backdrop) {
        return entry(backdrop).dirty;
    }

    public void markDirty(@NonNull Backdrop backdrop) {
        entry(backdrop).dirty = true;
    }

    /** Invalidates every blurred backdrop — a wallpaper, style or blur change moves all of them. */
    public void markAllBackdropsDirty() {
        for (Backdrop backdrop : Backdrop.values())
            entry(backdrop).dirty = true;
    }

    public int lastRadiusDp(@NonNull Backdrop backdrop) {
        return entry(backdrop).lastRadiusDp;
    }

    public boolean lastManagedSource(@NonNull Backdrop backdrop) {
        return entry(backdrop).lastManagedSource;
    }

    /** True when {@code rect} is exactly the rect the surface's current crop was cut for. */
    public boolean matchesLastRect(@NonNull Backdrop backdrop, @NonNull Rect rect) {
        return entry(backdrop).lastRect.equals(rect);
    }

    public void copyLastRect(@NonNull Backdrop backdrop, @NonNull Rect out) {
        out.set(entry(backdrop).lastRect);
    }

    public int lastRectHeight(@NonNull Backdrop backdrop) {
        return entry(backdrop).lastRect.height();
    }

    /** Records a freshly installed crop: clean, and matching this radius/source/geometry. */
    public void recordApplied(@NonNull Backdrop backdrop, int radiusDp, boolean managedSource,
                              @NonNull Rect rect) {
        BackdropEntry entry = entry(backdrop);
        entry.dirty = false;
        entry.lastRadiusDp = radiusDp;
        entry.lastManagedSource = managedSource;
        entry.lastRect.set(rect);
    }

    /** Drops the surface's crop: dirty again, with no radius, source or geometry to match. */
    public void reset(@NonNull Backdrop backdrop) {
        BackdropEntry entry = entry(backdrop);
        entry.dirty = true;
        entry.lastRadiusDp = -1;
        entry.lastManagedSource = false;
        entry.lastRect.setEmpty();
    }

    /** Forgets only the geometry, leaving the radius/source memo — a pure re-crop request. */
    public void invalidateRect(@NonNull Backdrop backdrop) {
        entry(backdrop).lastRect.setEmpty();
    }

    // ------------------------------------------------------------------- frosts

    public boolean isFrostDirty() {
        return mFrostDirty;
    }

    public void markFrostDirty() {
        mFrostDirty = true;
    }

    public void clearFrostDirty() {
        mFrostDirty = false;
    }

    public boolean matchesFrostRect(@NonNull FrostRect key, @NonNull Rect rect) {
        return frostRect(key).equals(rect);
    }

    public void recordFrostRect(@NonNull FrostRect key, @NonNull Rect rect) {
        frostRect(key).set(rect);
    }

    public void clearFrostRect(@NonNull FrostRect key) {
        frostRect(key).setEmpty();
    }

    public int frostRadiusDp(@NonNull FrostRadius key) {
        Integer radius = mFrostRadii.get(key);
        return radius == null ? -1 : radius;
    }

    public void setFrostRadiusDp(@NonNull FrostRadius key, int radiusDp) {
        mFrostRadii.put(key, radiusDp);
    }

    @NonNull
    private BackdropEntry entry(@NonNull Backdrop backdrop) {
        BackdropEntry entry = mBackdrops.get(backdrop);
        if (entry == null) {
            entry = new BackdropEntry();
            mBackdrops.put(backdrop, entry);
        }
        return entry;
    }

    @NonNull
    private Rect frostRect(@NonNull FrostRect key) {
        Rect rect = mFrostRects.get(key);
        if (rect == null) {
            rect = new Rect();
            mFrostRects.put(key, rect);
        }
        return rect;
    }
}
