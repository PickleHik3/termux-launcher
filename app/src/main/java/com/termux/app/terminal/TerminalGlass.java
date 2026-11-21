package com.termux.app.terminal;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.DeclaredSlabs;
import com.termux.app.chrome.LivePicture;
import com.termux.app.glass.GlassEntry;
import com.termux.app.glass.GlassService;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * The Terminal cell's glass: the slabs of the floats inside its windows that wear glass, declared
 * to the glass service through the Terminal page ({@link TerminalPage#declareGlass}). One object
 * is shared by every window's page, so the set stays declared while any window is the one in
 * front; a float gives its slab back when it closes or is docked away.
 */
public final class TerminalGlass implements TerminalPaneController.FloatGlass {

    @NonNull private final DeclaredSlabs mSlabs;
    @NonNull private final IntSupplier mFloatBlurDp;

    /**
     * {@code redeclare} makes the page registry declare every page's glass again;
     * {@code floatBlurDp} is a float's blur radius, the terminal glass's own.
     */
    public TerminalGlass(@NonNull GlassService glass, @NonNull Runnable redeclare,
                         @NonNull IntSupplier floatBlurDp) {
        mSlabs = new DeclaredSlabs(glass, redeclare);
        mFloatBlurDp = floatBlurDp;
    }

    @Nullable
    @Override
    public LivePicture wear(@NonNull View slab, @Nullable Runnable redress) {
        return mSlabs.wear("float", GlassEntry.PRIORITY_FLOAT, slab, mFloatBlurDp.getAsInt(),
            redress);
    }

    @Override
    public void drop(@NonNull View slab) {
        mSlabs.drop(slab);
    }

    /** Adds one entry per float slab that wears glass. */
    public void declare(@NonNull List<GlassEntry> out) {
        mSlabs.declare(out);
    }
}
