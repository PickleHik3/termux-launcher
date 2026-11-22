package com.termux.app.chrome;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.glass.GlassEntry;
import com.termux.app.glass.GlassKind;
import com.termux.app.glass.GlassService;
import com.termux.app.window.WindowLayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The slabs of one page kind that wear Live glass, declared to the glass service through that
 * page. A slab asks for its picture with {@link #wear} and gives it back with {@link #drop}; each
 * change asks the page registry to declare again, so the service's set follows the slabs.
 */
public final class DeclaredSlabs {

    /** One slab's entry: its element, its place in the budget and the redress the service calls. */
    private static final class Surface {
        @NonNull final String element;
        final int priority;
        @Nullable final GlassService.Redress redress;

        Surface(@NonNull String element, int priority, @Nullable GlassService.Redress redress) {
            this.element = element;
            this.priority = priority;
            this.redress = redress;
        }
    }

    @NonNull private final GlassService mGlass;
    @NonNull private final Runnable mRedeclare;
    @NonNull private final Map<View, Surface> mSurfaces = new LinkedHashMap<>();

    /** {@code redeclare} makes the page registry declare every page's glass again. */
    public DeclaredSlabs(@NonNull GlassService glass, @NonNull Runnable redeclare) {
        mGlass = glass;
        mRedeclare = redeclare;
    }

    /**
     * {@code slab}'s live picture at {@code blurDp}, its element named {@code kind} and the slab;
     * null while the glass service grants another kind. {@code redress} runs when that kind moves;
     * a slab already worn keeps its first one.
     */
    @Nullable
    public LivePicture wear(@NonNull String kind, int priority, @NonNull View slab, int blurDp,
                            @Nullable Runnable redress) {
        Surface surface = mSurfaces.get(slab);
        if (surface == null) {
            surface = new Surface(kind + "@" + Integer.toHexString(System.identityHashCode(slab)),
                priority, redress == null ? null : k -> redress.run());
            mSurfaces.put(slab, surface);
            // Only a new slab changes what the page declares; a repeat wear only asks for the picture.
            mRedeclare.run();
        }
        return mGlass.livePicture(surface.element,
            blurDp * slab.getResources().getDisplayMetrics().density, LivePicture.class);
    }

    /** The slab gave its glass back: the next declaration leaves it out and the service lets go. */
    public void drop(@NonNull View slab) {
        if (mSurfaces.remove(slab) != null) mRedeclare.run();
    }

    /** Adds one entry per slab that wears glass. */
    public void declare(@NonNull List<GlassEntry> out) {
        for (Map.Entry<View, Surface> held : mSurfaces.entrySet()) {
            Surface surface = held.getValue();
            out.add(new GlassEntry(surface.element, held.getKey(), GlassKind.LIVE,
                surface.priority, WindowLayer.PAGES, null, surface.redress));
        }
    }
}
