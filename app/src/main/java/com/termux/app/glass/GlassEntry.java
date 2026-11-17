package com.termux.app.glass;

import android.view.View;

import androidx.annotation.NonNull;

/**
 * One glass surface in the registry (ADR 0029 section 1): the element it stands for, the view it
 * draws in, the kind it asks for and its place in the budget. The service owns everything else:
 * the granted kind, the layers, refresh, commit order and release.
 */
public final class GlassEntry {

    /** Budget order, best first: the dock, then the floating windows, then the widgets. */
    public static final int PRIORITY_DOCK = 0;
    public static final int PRIORITY_FLOAT = 1;
    public static final int PRIORITY_WIDGET = 2;

    /**
     * The element: the look cells the surface reads, named once per surface ("dock", or a
     * float's or a widget's own name). The registry holds one entry per element.
     */
    @NonNull public final String element;
    /** The view the surface draws in; its place is read each frame. */
    @NonNull public final View view;
    /** The kind it asks for. */
    @NonNull public final GlassKind wants;
    /** {@link #PRIORITY_DOCK}, {@link #PRIORITY_FLOAT} or {@link #PRIORITY_WIDGET}. */
    public final int priority;

    public GlassEntry(@NonNull String element, @NonNull View view, @NonNull GlassKind wants,
                      int priority) {
        this.element = element;
        this.view = view;
        this.wants = wants;
        this.priority = Math.max(PRIORITY_DOCK, Math.min(PRIORITY_WIDGET, priority));
    }
}
