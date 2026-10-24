package com.termux.app.fragments.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.ChromeShape;
import com.termux.app.place.PlaceLayout.Edge;

import java.util.ArrayList;
import java.util.List;

/**
 * What a lifted element's drop targets are drawn as, as pure arithmetic in the layout canvas's
 * px (appearance-layout-editor SPEC §3.6a items 5–7). A slot's hit region is what the finger is
 * tested against and stays as it is; what is drawn is:
 *
 * <ul>
 *   <li>for every valid slot, a slim <b>marker</b> on its gap line, a few dp thick and running
 *   the slot's length, fully rounded ({@link #markers});</li>
 *   <li>for the slot under the finger, the <b>capsule</b>: the held element's own size and place
 *   at that slot as the shape model gives it ({@link #hoveredCapsule}), solid with a light
 *   tint, at the element's own corners.</li>
 * </ul>
 *
 * <p>Markers are kept clean: where a side strip shows (the held element may stand on that side)
 * every row's marker starts after the strip; any two still overlapping are cut apart halfway;
 * each stands inside the frame, its ends {@code insetPx} in, so no two meet and none crosses the
 * frame's edge. Rects are {@code {left, top, right, bottom}}. The tray's slot (no edge) has no
 * marker here: the hidden items' rail draws its own border.</p>
 */
final class DropCapsulePolicy {

    private DropCapsulePolicy() {}

    /**
     * One marker per slot of {@code slots}, in the same order: null for the tray's slot and for
     * a slot left with no room once the others are taken out.
     *
     * @param frame       the phone's rect in the same px
     * @param thicknessPx how thick a marker is across its gap line
     * @param insetPx     how far a marker's ends and sides stand in from the frame's edge, and
     *                    its ends from the next marker's
     */
    @NonNull
    static List<float[]> markers(@NonNull List<MiniatureDragPolicy.Slot> slots,
                                 @NonNull float[] frame, float thicknessPx, float insetPx) {
        int n = slots.size();
        float[][] rects = new float[n][];
        Edge[] edges = new Edge[n];
        float leftStrip = Float.NaN;
        float rightStrip = Float.NaN;
        for (int i = 0; i < n; i++) {
            MiniatureDragPolicy.Slot slot = slots.get(i);
            if (slot.edge == null)
                continue;
            edges[i] = slot.edge;
            rects[i] = markerOf(slot, frame, thicknessPx, insetPx);
            if (slot.edge == Edge.LEFT)
                leftStrip = Float.isNaN(leftStrip) ? slot.right : Math.max(leftStrip, slot.right);
            else if (slot.edge == Edge.RIGHT)
                rightStrip = Float.isNaN(rightStrip) ? slot.left : Math.min(rightStrip, slot.left);
        }
        // The rows start after the side strips, wherever a strip shows.
        for (int i = 0; i < n; i++) {
            if (rects[i] == null || edges[i].isOnSide())
                continue;
            if (!Float.isNaN(leftStrip))
                rects[i][0] = Math.max(rects[i][0], leftStrip + insetPx);
            if (!Float.isNaN(rightStrip))
                rects[i][2] = Math.min(rects[i][2], rightStrip - insetPx);
        }
        // Whatever still overlaps is cut apart; cuts only shrink, so a pair once apart stays so.
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (rects[i] == null || rects[j] == null || !overlaps(rects[i], rects[j]))
                    continue;
                boolean iRow = !edges[i].isOnSide();
                boolean jRow = !edges[j].isOnSide();
                if (iRow == jRow)
                    split(rects[i], rects[j], iRow);
                else
                    shortenRow(iRow ? rects[i] : rects[j], iRow ? rects[j] : rects[i]);
            }
        }
        List<float[]> out = new ArrayList<>(n);
        for (float[] r : rects)
            out.add(r != null && r[2] > r[0] && r[3] > r[1] ? r : null);
        return out;
    }

    /**
     * One slot's marker before the others are taken into account: {@code thicknessPx} across its
     * gap line, moved in whole where the line stands at the slot's or the frame's edge, never
     * past either; along it, the slot's length less {@code insetPx} at each end, inside the frame.
     */
    @NonNull
    static float[] markerOf(@NonNull MiniatureDragPolicy.Slot slot, @NonNull float[] frame,
                            float thicknessPx, float insetPx) {
        boolean row = slot.edge == null || !slot.edge.isOnSide();
        float depthLo = row ? Math.max(slot.top, frame[1] + insetPx)
            : Math.max(slot.left, frame[0] + insetPx);
        float depthHi = row ? Math.min(slot.bottom, frame[3] - insetPx)
            : Math.min(slot.right, frame[2] - insetPx);
        float lo = slot.line - thicknessPx / 2f;
        float hi = slot.line + thicknessPx / 2f;
        if (lo < depthLo) {
            hi += depthLo - lo;
            lo = depthLo;
        }
        if (hi > depthHi) {
            lo -= hi - depthHi;
            hi = depthHi;
        }
        lo = Math.max(lo, depthLo);
        float alongLo = row ? Math.max(slot.left, frame[0]) : Math.max(slot.top, frame[1]);
        float alongHi = row ? Math.min(slot.right, frame[2]) : Math.min(slot.bottom, frame[3]);
        alongLo += insetPx;
        alongHi -= insetPx;
        return row ? new float[] {alongLo, lo, alongHi, hi}
            : new float[] {lo, alongLo, hi, alongHi};
    }

    /**
     * The capsule the hovered slot opens to: the box the shape model gives the held element
     * dropped there ({@code dropped}, in the model's own px), moved to the canvas by
     * ({@code originX}, {@code originY}) and kept inside the frame. Its size is the element's at
     * that slot wherever that box lies inside the frame, as it always does.
     */
    @Nullable
    static float[] hoveredCapsule(@NonNull ChromeShape.Box dropped, float originX, float originY,
                                  @NonNull float[] frame) {
        float left = Math.max(frame[0], dropped.left + originX);
        float top = Math.max(frame[1], dropped.top + originY);
        float right = Math.min(frame[2], dropped.right + originX);
        float bottom = Math.min(frame[3], dropped.bottom + originY);
        return right > left && bottom > top ? new float[] {left, top, right, bottom} : null;
    }

    /** Whether two rects share any area; rects that only touch do not. */
    static boolean overlaps(@NonNull float[] a, @NonNull float[] b) {
        return a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3];
    }

    /**
     * Two rows (or two columns) cut apart halfway across their overlap: the one whose middle
     * stands first keeps the near half.
     */
    private static void split(@NonNull float[] a, @NonNull float[] b, boolean rows) {
        int lo = rows ? 1 : 0;
        int hi = rows ? 3 : 2;
        boolean aFirst = a[lo] + a[hi] <= b[lo] + b[hi];
        float[] first = aFirst ? a : b;
        float[] second = aFirst ? b : a;
        float mid = (Math.max(a[lo], b[lo]) + Math.min(a[hi], b[hi])) / 2f;
        first[hi] = Math.min(first[hi], mid);
        second[lo] = Math.max(second[lo], mid);
    }

    /** A row still crossing a column: its end on the column's side stops where the column starts. */
    private static void shortenRow(@NonNull float[] row, @NonNull float[] column) {
        boolean columnOnLeft = column[0] + column[2] <= row[0] + row[2];
        if (columnOnLeft)
            row[0] = Math.max(row[0], column[2]);
        else
            row[2] = Math.min(row[2], column[0]);
    }
}
