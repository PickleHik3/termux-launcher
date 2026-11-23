package com.termux.app.chrome;

import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Which live pictures a reparent turns into a render-node loop. A picture's recording draws the
 * views beneath its surface by reference; when a view moves to a new parent without the pictures
 * recording again (Home's two page grids trade cells mid-swipe by detach and attach), a recording
 * made against the old parents can draw a container that now holds that recording's own holder,
 * or a holder whose recording draws back to it. The renderer's tree walk then never ends (a
 * RenderThread stack overflow, pong 2026-11-23). The recorder asks this once per reparent, never
 * per frame, and drops the recordings of every picture on a loop before the next frame syncs.
 */
final class ReparentCycles {

    /** A parent chain longer than this is a broken tree: the walk stops. */
    static final int MAX_DEPTH = 256;

    private ReparentCycles() {}

    /** Whether {@code view} is {@code ancestor} or inside it. False for either null. */
    static boolean holds(@Nullable View ancestor, @Nullable View view) {
        if (ancestor == null || view == null) return false;
        View current = view;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            if (current == ancestor) return true;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    /**
     * Marks in {@code out} every node of the {@code n}-node graph {@code edge} that lies on a
     * cycle, a self loop included: {@code edge[i][j]} is "picture i's recording draws picture j's
     * holder". Transitive closure over at most a handful of pictures; pure.
     */
    static void onCycle(@NonNull boolean[][] edge, int n, @NonNull boolean[] out) {
        boolean[][] reach = new boolean[n][n];
        for (int i = 0; i < n; i++) System.arraycopy(edge[i], 0, reach[i], 0, n);
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                if (!reach[i][k]) continue;
                for (int j = 0; j < n; j++) {
                    if (reach[k][j]) reach[i][j] = true;
                }
            }
        }
        for (int i = 0; i < n; i++) out[i] = reach[i][i];
    }
}
