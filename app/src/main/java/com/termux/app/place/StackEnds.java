package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.app.chrome.StackMaterial.RegionLook;
import com.termux.app.wall.PageRef;

/**
 * Reads a {@link StackEnd} for one page through a {@link Host} that knows how that page rests:
 * once as a crossing begins and once per rest change, never mid-crossing. It allocates the end's
 * looks and matrices, which is why it is never called per frame.
 */
public final class StackEnds {

    private StackEnds() {}

    /** What only the activity knows about how a page's stack rests. */
    public interface Host {
        /** {@code page}'s stack as it is laid out at its rest, into {@code out}. */
        void restGeometry(@NonNull PageRef page, @NonNull StackGeometry out);
        /** Whether {@code page}'s dock is Floating (else Docked). */
        boolean floating(@NonNull PageRef page);
        /**
         * {@code region}'s look on {@code page} ({@link StackEnd#ROWS} ..
         * {@link StackEnd#FOOT}), into {@code out}; false where the page has no such region.
         */
        boolean regionLook(@NonNull PageRef page, int region, @NonNull RegionLook out);
        @NonNull StackEnd.StripRule stripRule(@NonNull PageRef page);
        int inkArgb(@NonNull PageRef page);
        /** {@code region}'s even-out matrix on {@code page} into {@code out}; false for identity. */
        default boolean evenOut(@NonNull PageRef page, int region, @NonNull float[] out) {
            return false;
        }
    }

    @NonNull
    public static StackEnd read(@NonNull PageRef page, @NonNull Host host) {
        StackEnd end = new StackEnd(page);
        host.restGeometry(page, end.rest);
        end.floating = host.floating(page);
        for (int region = 0; region < StackEnd.REGION_COUNT; region++) {
            RegionLook look = new RegionLook();
            if (host.regionLook(page, region, look)) end.looks[region] = look;
            float[] matrix = new float[StackScene.COLOR_MATRIX_LENGTH];
            if (host.evenOut(page, region, matrix)) end.evenOut[region] = matrix;
        }
        end.strip = host.stripRule(page);
        end.inkArgb = host.inkArgb(page);
        return end;
    }
}