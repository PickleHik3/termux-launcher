package com.termux.app.window;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.util.function.IntFunction;

/**
 * Where a runtime view joins the window: its layer's host, never the window decor or the content
 * view beside the root (ADR 0028 section 6). A host that is missing is not an error: the view goes
 * to the window root instead, and with no root to the content view, so a misplaced host costs an
 * overlay its Z and never the screen. Nothing here gates drawing.
 */
public final class WindowLayers {

    private WindowLayers() {}

    /** The host of {@code layer}, found by id through {@code finder} (a view's or an activity's lookup). */
    @Nullable
    public static ViewGroup host(@NonNull IntFunction<View> finder, @NonNull WindowLayer layer) {
        ViewGroup host = group(finder.apply(layer.hostId()));
        if (host != null) return host;
        host = group(finder.apply(R.id.window_layers_root));
        if (host != null) return host;
        return ContentFrame.of(finder);
    }

    @Nullable
    public static ViewGroup host(@NonNull Activity activity, @NonNull WindowLayer layer) {
        return host(activity::findViewById, layer);
    }

    /** The window root: what the content view holds, the frame every layer is measured in. */
    @Nullable
    public static ViewGroup windowRoot(@NonNull IntFunction<View> finder) {
        ViewGroup root = group(finder.apply(R.id.window_layers_root));
        return root != null ? root : ContentFrame.of(finder);
    }

    @Nullable
    public static ViewGroup windowRoot(@NonNull Activity activity) {
        return windowRoot(activity::findViewById);
    }

    /**
     * The host of {@code layer} inside a window frame already in hand (the window root, or a test's
     * stand-in for it): the frame itself when it holds no such host.
     */
    @NonNull
    public static ViewGroup hostIn(@NonNull ViewGroup frame, @NonNull WindowLayer layer) {
        ViewGroup host = group(frame.findViewById(layer.hostId()));
        return host != null ? host : frame;
    }

    /**
     * Where the editor's card sheet, bar and hints go: the panel host inside the editor layer, whose
     * children cast their shadows with elevation while the picker's host, added after it in the
     * layer, stands over them by child order. The editor host, or the frame, when there is none.
     */
    @NonNull
    public static ViewGroup panelHostIn(@NonNull ViewGroup frame) {
        ViewGroup panel = group(frame.findViewById(R.id.editor_panel_host));
        return panel != null ? panel : hostIn(frame, WindowLayer.EDITOR);
    }

    /** Adds {@code view} to {@code layer}'s host; false when there is no host to add it to. */
    public static boolean add(@NonNull IntFunction<View> finder, @NonNull WindowLayer layer,
                              @NonNull View view, @NonNull ViewGroup.LayoutParams params) {
        ViewGroup host = host(finder, layer);
        if (host == null) return false;
        host.addView(view, params);
        return true;
    }

    public static boolean add(@NonNull Activity activity, @NonNull WindowLayer layer,
                              @NonNull View view, @NonNull ViewGroup.LayoutParams params) {
        return add(activity::findViewById, layer, view, params);
    }

    /**
     * Moves {@code view} last among its siblings: for a view that must stand over the others of its
     * own layer (a float focused over the floats beside it). Never a way past a layer; the layer
     * number decides that.
     */
    public static void raiseWithin(@Nullable View view) {
        if (view == null || !(view.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent.getChildAt(parent.getChildCount() - 1) != view) parent.bringChildToFront(view);
    }

    @Nullable
    private static ViewGroup group(@Nullable View view) {
        return view instanceof ViewGroup ? (ViewGroup) view : null;
    }

    /**
     * The layer {@code view} actually stands in: the layer whose host is its nearest ancestor (or
     * itself); null when it stands in none (not attached, or outside the layer hosts). What the
     * glass registry is told, never a layer inferred from what the surface looks like.
     */
    @androidx.annotation.Nullable
    public static WindowLayer layerOf(@NonNull View view) {
        View at = view;
        while (at != null) {
            int id = at.getId();
            if (id != View.NO_ID) {
                for (WindowLayer layer : LAYERS) {
                    if (layer.hostId() == id) return layer;
                }
            }
            at = at.getParent() instanceof View ? (View) at.getParent() : null;
        }
        return null;
    }

    /** Held once: {@code values()} copies its array on every call. */
    private static final WindowLayer[] LAYERS = WindowLayer.values();
}
