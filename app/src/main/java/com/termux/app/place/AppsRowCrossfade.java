package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * The apps row's content crossfade (the developer's Q5, 2026-11-25: per-page app lists are
 * planned). Where the apps row STAYs or GLIDEs ({@link CrossingPath}) and the two ends pin
 * different apps, the row draws a second content layer, the arriving end's list laid out at rest
 * in the row's own slots, and the two layers trade alpha on the material curve. The layer is used
 * only while the two lists differ; today every page shows one global list, so it is never active,
 * which {@link #prepare} answers.
 *
 * <p>Memory: the layer holds icon keys ({@code LauncherIconStore#artworkKey}) and slot boxes, no
 * pixels. Its icons are the store's own artwork, drawn from the store, so it adds nothing to the
 * bitmap budget ({@code LauncherIconStore}, performance.md rule 2). Prepared once at crossing
 * begin into arrays made once; a frame reads two alphas and allocates nothing.
 */
public final class AppsRowCrossfade {

    /** The most slots the layer lays out: more pinned apps than this are not drawn by it. */
    public static final int MAX_SLOTS = 16;

    private final String[] mKeys = new String[MAX_SLOTS];
    private final float[] mBoxes = new float[MAX_SLOTS * 4];
    private int mCount;
    private boolean mActive;

    /** Whether the two ends pin different apps (in order): only then is the second layer drawn. */
    public static boolean differs(@Nullable List<String> leaving, @Nullable List<String> arriving) {
        int a = leaving == null ? 0 : leaving.size();
        int b = arriving == null ? 0 : arriving.size();
        if (a != b) return true;
        for (int i = 0; i < a; i++) {
            String x = leaving.get(i);
            String y = arriving.get(i);
            if (x == null ? y != null : !x.equals(y)) return true;
        }
        return false;
    }

    /**
     * Lays the arriving list out at rest in the row's slots ({@code slotBoxes}, left, top, right,
     * bottom per slot, row px) when the two lists differ, else clears the layer. At crossing begin
     * only. Returns whether the layer is drawn on this crossing.
     */
    public boolean prepare(@Nullable List<String> leaving, @Nullable List<String> arriving,
                           @NonNull float[] slotBoxes, int slots) {
        clear();
        if (!differs(leaving, arriving) || arriving == null) return false;
        int n = Math.min(Math.min(arriving.size(), Math.max(0, slots)), MAX_SLOTS);
        n = Math.min(n, slotBoxes.length / 4);
        for (int i = 0; i < n; i++) {
            mKeys[i] = arriving.get(i);
            System.arraycopy(slotBoxes, i * 4, mBoxes, i * 4, 4);
        }
        mCount = n;
        mActive = true;
        return true;
    }

    /** Back to no layer: the landing, a turned-back crossing that came to rest, or a rest. */
    public void clear() {
        for (int i = 0; i < mCount; i++) mKeys[i] = null;
        mCount = 0;
        mActive = false;
    }

    public boolean active() {
        return mActive;
    }

    /** The slots the layer lays out. */
    public int count() {
        return mCount;
    }

    /** Slot {@code i}'s icon key, the store's. */
    @Nullable
    public String key(int i) {
        return i >= 0 && i < mCount ? mKeys[i] : null;
    }

    /** Slot {@code i}'s box at rest ({@code edge}: 0 left, 1 top, 2 right, 3 bottom). */
    public float box(int i, int edge) {
        return i >= 0 && i < mCount && edge >= 0 && edge < 4 ? mBoxes[i * 4 + edge] : 0f;
    }

    /** The row's own (leaving) content's alpha at progress {@code p}: whole while the layer is unused. */
    public float leavingAlpha(float p) {
        return mActive ? 1f - StackScene.materialShare(p) : 1f;
    }

    /** The second layer's alpha at progress {@code p}: 0 while it is unused. */
    public float arrivingAlpha(float p) {
        return mActive ? StackScene.materialShare(p) : 0f;
    }

    /** Bitmap bytes the layer keeps of its own: none, its icons are the store's. */
    public int retainedBitmapBytes() {
        return 0;
    }
}
