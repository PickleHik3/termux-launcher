package com.termux.app.fragments.settings;

import androidx.annotation.NonNull;

import com.termux.app.place.PlaceLayout.Edge;

import java.util.ArrayList;
import java.util.List;

/**
 * The outlines a lifted element's drop targets are drawn as, one capsule per slot, as pure
 * arithmetic in the layout canvas's px (appearance-layout-editor SPEC §3.5, drag targets). A
 * slot's hit region is what the finger is tested against and stays as it is; its capsule is that
 * region made clean to look at:
 *
 * <ul>
 *   <li>where a side strip shows (the held element may stand on that side), every row's capsule
 *   starts after the strip, so no row runs into a column at a corner;</li>
 *   <li>any two capsules still overlapping are cut apart halfway across their overlap, along
 *   the axis they stack on (rows by height, columns by width);</li>
 *   <li>every capsule is then kept inside the frame and drawn {@code insetPx} inside its
 *   region, one shared inset, so neighbours stand at least twice that apart and none touches the
 *   frame's edge.</li>
 * </ul>
 *
 * <p>Rects are {@code {left, top, right, bottom}}. The tray's slot (no edge) has no capsule here:
 * the hidden items' rail draws its own border.</p>
 */
final class DropCapsulePolicy {

    private DropCapsulePolicy() {}

    /**
     * One capsule per slot of {@code slots}, in the same order: null for the tray's slot and for
     * a slot left with no room once the others and the inset are taken out.
     *
     * @param frame   the phone's rect in the same px
     * @param insetPx the one inset every capsule is drawn inside its region by
     */
    @NonNull
    static List<float[]> capsules(@NonNull List<MiniatureDragPolicy.Slot> slots,
                                  @NonNull float[] frame, float insetPx) {
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
            rects[i] = new float[] {slot.left, slot.top, slot.right, slot.bottom};
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
                rects[i][0] = Math.max(rects[i][0], leftStrip);
            if (!Float.isNaN(rightStrip))
                rects[i][2] = Math.min(rects[i][2], rightStrip);
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
        for (int i = 0; i < n; i++) {
            float[] r = rects[i];
            if (r == null) {
                out.add(null);
                continue;
            }
            float left = Math.max(r[0], frame[0]) + insetPx;
            float top = Math.max(r[1], frame[1]) + insetPx;
            float right = Math.min(r[2], frame[2]) - insetPx;
            float bottom = Math.min(r[3], frame[3]) - insetPx;
            out.add(right > left && bottom > top ? new float[] {left, top, right, bottom} : null);
        }
        return out;
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
