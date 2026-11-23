package com.termux.app.launcher.home;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.DeclaredSlabs;
import com.termux.app.chrome.LivePicture;
import com.termux.app.glass.GlassEntry;
import com.termux.app.glass.GlassService;
import com.termux.app.wall.WidgetPaneFrame;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * Home's glass: the widgets' and the Home floats' slabs that wear glass, declared to the glass
 * service through the Home page ({@link HomePage#declareGlass}). A slab asks for its picture with
 * {@link #wearWidget} or {@link #wearFloat} and gives it back with {@link #drop}; each change asks
 * the page registry to declare again, so the service's set follows the slabs.
 */
public final class HomeGlass implements WidgetPaneFrame.Glass {

    @NonNull private final GlassService mGlass;
    @NonNull private final DeclaredSlabs mSlabs;
    @NonNull private final IntSupplier mFloatBlurDp;

    /**
     * {@code redeclare} makes the page registry declare every page's glass again;
     * {@code floatBlurDp} is a float's blur radius, the terminal glass's own.
     */
    public HomeGlass(@NonNull GlassService glass, @NonNull Runnable redeclare,
                     @NonNull IntSupplier floatBlurDp) {
        mGlass = glass;
        mSlabs = new DeclaredSlabs(glass, redeclare);
        mFloatBlurDp = floatBlurDp;
    }

    /**
     * A widget slab's live picture at {@code blurDp}; null while the glass service grants another
     * kind. {@code redress} runs when that kind moves; a slab already worn keeps its first one.
     */
    @Nullable
    public LivePicture wearWidget(@NonNull View slab, int blurDp, @Nullable Runnable redress) {
        return mSlabs.wear("widget", GlassEntry.PRIORITY_WIDGET, slab, blurDp, redress);
    }

    /** {@link WidgetPaneFrame.Glass}: a widget slab's picture, as {@link #wearWidget}. */
    @Nullable
    @Override
    public LivePicture wear(@NonNull View slab, int blurDp, @Nullable Runnable redress) {
        return wearWidget(slab, blurDp, redress);
    }

    /** A Home float's slab, at the terminal glass's blur; as {@link #wearWidget}. */
    @Nullable
    public LivePicture wearFloat(@NonNull View slab, @Nullable Runnable redress) {
        return mSlabs.wear("float", GlassEntry.PRIORITY_FLOAT, slab, mFloatBlurDp.getAsInt(),
            redress);
    }

    /** The slab gave its glass back: the next declaration leaves it out and the service lets go. */
    @Override
    public void drop(@NonNull View slab) {
        mSlabs.drop(slab);
    }

    /**
     * {@link WidgetPaneFrame.Glass}: a mid-swipe page swap moved widget cells between grids; the
     * service drops any recording that move turned into a loop.
     */
    @Override
    public void reparented(@NonNull List<View> cells, @NonNull View from) {
        mGlass.reparented(cells, from);
    }

    /** Adds one entry per slab that wears glass. */
    public void declare(@NonNull List<GlassEntry> out) {
        mSlabs.declare(out);
    }
}
