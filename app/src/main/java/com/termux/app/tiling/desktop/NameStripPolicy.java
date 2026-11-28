package com.termux.app.tiling.desktop;

import androidx.annotation.NonNull;

import com.termux.app.tiling.DwindleTree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Which tiled windows show a name strip along their top edge (#422): those on screen that no tab
 * strip names. Full screen shows none; the caller passes no windows then.
 */
final class NameStripPolicy {
    private NameStripPolicy() {}

    @NonNull
    static List<Long> named(@NonNull Collection<Long> live, @NonNull List<DwindleTree.TabStrip> strips) {
        List<Long> out = new ArrayList<>();
        for (long id : live) {
            boolean onStrip = false;
            for (DwindleTree.TabStrip strip : strips) {
                for (long w : strip.windows) if (w == id) onStrip = true;
            }
            if (!onStrip) out.add(id);
        }
        return out;
    }
}
