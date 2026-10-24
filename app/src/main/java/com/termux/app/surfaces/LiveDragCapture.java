package com.termux.app.surfaces;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.fragments.settings.LayoutCanvasView;
import com.termux.app.fragments.settings.LayoutCanvasView.Block;
import com.termux.shared.logger.Logger;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The live frame's elements as pictures for a drag in the editor (appearance-layout-editor SPEC
 * §3.6a item 1): where each element's view stands in the launcher's container, one copy of the
 * frame off the window's own surface (so glass, frost and refraction come with it), cut into one
 * picture per element, and the real views made transparent under the pictures and shown again
 * after. Nothing is laid out, re-hosted or resized: alpha only, so the terminal never resizes.
 *
 * <p>Where the pictures stand is the canvas's business ({@link LayoutCanvasView#beginLive});
 * this class only knows the frame's map from the container's px to the canvas's: a scale and an
 * origin, the same transform the frame is drawn with.</p>
 */
final class LiveDragCapture {

    private static final String LOG_TAG = "LiveDragCapture";

    /** The views an element can stand in, by where it is placed; the one showing is it. */
    private static final Map<Block, int[]> ELEMENT_VIEWS = new EnumMap<>(Block.class);

    static {
        ELEMENT_VIEWS.put(Block.STATUS_BAR, new int[] {R.id.terminal_window_bar_host});
        ELEMENT_VIEWS.put(Block.APPS_ROW,
            new int[] {R.id.apps_bar_row_host, R.id.place_apps_bar_host});
        ELEMENT_VIEWS.put(Block.ALPHABETS_ROW,
            new int[] {R.id.apps_bar_az_host, R.id.place_az_bar_host});
        ELEMENT_VIEWS.put(Block.EXTRA_KEYS,
            new int[] {R.id.terminal_toolbar_host, R.id.place_extra_keys_host});
        ELEMENT_VIEWS.put(Block.KEYBOARD, new int[] {R.id.inapp_keyboard_view_host});
    }

    /**
     * What stands behind or round the elements and would be left behind when their pictures
     * move: the dock's glass, the status bar's band, the off-dock plank, the selection outline.
     */
    private static final int[] BACKDROP_VIEWS = {R.id.accessory_surface_host,
        R.id.terminal_status_bar_background, R.id.place_off_dock_plank_host,
        R.id.surface_editor_selection_outline};

    /** The container's px in the canvas: {@code x * scale + originX}. */
    static final class FrameMap {
        final float scale;
        final float originX;
        final float originY;

        FrameMap(float scale, float originX, float originY) {
            this.scale = scale;
            this.originX = originX;
            this.originY = originY;
        }

        @NonNull
        RectF toCanvas(@NonNull int[] rootRect) {
            return new RectF(rootRect[0] * scale + originX, rootRect[1] * scale + originY,
                rootRect[2] * scale + originX, rootRect[3] * scale + originY);
        }
    }

    interface Views {
        @Nullable View find(int id);
    }

    @NonNull private final Views mViews;
    @NonNull private final View mRoot;
    /** Each hidden view's alpha before, to give back. */
    @NonNull private final Map<View, Float> mHidden = new HashMap<>();
    private boolean mCancelled;

    LiveDragCapture(@NonNull Views views, @NonNull View root) {
        mViews = views;
        mRoot = root;
    }

    /** The view {@code block} stands in now, or null where it is not on screen. */
    @Nullable
    private View viewOf(@NonNull Block block) {
        int[] ids = ELEMENT_VIEWS.get(block);
        if (ids == null)
            return null;
        for (int id : ids) {
            View view = mViews.find(id);
            if (view != null && view.isShown() && view.getWidth() > 0 && view.getHeight() > 0)
                return view;
        }
        return null;
    }

    /** Each element on screen's rect in the container's own px, {left, top, right, bottom}. */
    @NonNull
    Map<Block, int[]> rootRects() {
        Map<Block, int[]> out = new EnumMap<>(Block.class);
        int[] at = new int[2];
        for (Block block : ELEMENT_VIEWS.keySet()) {
            View view = viewOf(block);
            if (view == null || !AppearanceEditorFrame.offsetIn(view, mRoot, at))
                continue;
            out.put(block, new int[] {at[0], at[1], at[0] + view.getWidth(),
                at[1] + view.getHeight()});
        }
        return out;
    }

    /** Each element on screen's rect in the canvas's px. */
    @NonNull
    Map<Block, RectF> canvasRects(@NonNull FrameMap map) {
        Map<Block, RectF> out = new EnumMap<>(Block.class);
        for (Map.Entry<Block, int[]> entry : rootRects().entrySet())
            out.put(entry.getKey(), map.toCanvas(entry.getValue()));
        return out;
    }

    /**
     * Copies the frame off the window ({@code frameInWindow}, the canvas's origin standing
     * {@code ringPx} up and left of it) and cuts it into the elements' pictures, each with its
     * corner from {@code radius}; {@code onReady} gets them on the main thread, or never once
     * {@link #cancel} has run. A copy that fails gives stand-ins (no picture) at the same rects,
     * logged. Nothing on screen may change between this call and {@code onReady}: the copy is of
     * the window as it is drawn now.
     */
    void capture(@NonNull Context context, @NonNull Rect frameInWindow, int ringPx,
                 @NonNull FrameMap map, @NonNull java.util.function.ToDoubleFunction<Block> radius,
                 @NonNull Consumer<Map<Block, LayoutCanvasView.LiveShot>> onReady) {
        Map<Block, RectF> rects = canvasRects(map);
        Activity activity = activityOf(context);
        if (activity == null || activity.getWindow() == null || frameInWindow.isEmpty()) {
            onReady.accept(shots(null, rects, ringPx, radius));
            return;
        }
        Bitmap frame;
        try {
            frame = Bitmap.createBitmap(frameInWindow.width(), frameInWindow.height(),
                Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError | IllegalArgumentException e) {
            Logger.logWarn(LOG_TAG, "No room for the frame's picture: " + e);
            onReady.accept(shots(null, rects, ringPx, radius));
            return;
        }
        try {
            PixelCopy.request(activity.getWindow(), frameInWindow, frame, result -> {
                if (mCancelled) {
                    frame.recycle();
                    return;
                }
                if (result != PixelCopy.SUCCESS)
                    Logger.logWarn(LOG_TAG, "The frame's copy failed (" + result
                        + "): stand-ins instead");
                onReady.accept(shots(result == PixelCopy.SUCCESS ? frame : null, rects, ringPx,
                    radius));
                frame.recycle();
            }, new Handler(Looper.getMainLooper()));
        } catch (IllegalArgumentException e) {
            Logger.logWarn(LOG_TAG, "The frame could not be copied: " + e);
            frame.recycle();
            onReady.accept(shots(null, rects, ringPx, radius));
        }
    }

    /** The copy is no longer wanted: its answer is dropped. */
    void cancel() {
        mCancelled = true;
    }

    /** One picture per element, cut out of {@code frame} where it has one. */
    @NonNull
    private static Map<Block, LayoutCanvasView.LiveShot> shots(
        @Nullable Bitmap frame, @NonNull Map<Block, RectF> rects, int ringPx,
        @NonNull java.util.function.ToDoubleFunction<Block> radius) {
        Map<Block, LayoutCanvasView.LiveShot> out = new EnumMap<>(Block.class);
        for (Map.Entry<Block, RectF> entry : rects.entrySet()) {
            RectF rect = entry.getValue();
            Bitmap picture = null;
            RectF at = rect;
            if (frame != null) {
                int left = Math.max(0, Math.round(rect.left) - ringPx);
                int top = Math.max(0, Math.round(rect.top) - ringPx);
                int right = Math.min(frame.getWidth(), Math.round(rect.right) - ringPx);
                int bottom = Math.min(frame.getHeight(), Math.round(rect.bottom) - ringPx);
                if (right > left && bottom > top) {
                    picture = Bitmap.createBitmap(frame, left, top, right - left, bottom - top);
                    at = new RectF(left + ringPx, top + ringPx, right + ringPx, bottom + ringPx);
                }
            }
            out.put(entry.getKey(), new LayoutCanvasView.LiveShot(picture, at,
                (float) radius.applyAsDouble(entry.getKey())));
        }
        return out;
    }

    /** The elements' views and what stands behind them made transparent, alpha only. */
    void hideReal() {
        for (Block block : ELEMENT_VIEWS.keySet()) {
            int[] ids = ELEMENT_VIEWS.get(block);
            if (ids == null) continue;
            for (int id : ids) hide(mViews.find(id));
        }
        for (int id : BACKDROP_VIEWS) hide(mViews.find(id));
    }

    private void hide(@Nullable View view) {
        if (view == null || mHidden.containsKey(view))
            return;
        mHidden.put(view, view.getAlpha());
        view.setAlpha(0f);
    }

    /**
     * The real views shown again, each at the alpha it had; one something else has set since is
     * left as it was set.
     */
    void showReal() {
        for (Map.Entry<View, Float> entry : mHidden.entrySet()) {
            if (entry.getKey().getAlpha() == 0f)
                entry.getKey().setAlpha(entry.getValue());
        }
        mHidden.clear();
    }

    @Nullable
    private static Activity activityOf(@NonNull Context context) {
        Context at = context;
        while (at instanceof ContextWrapper) {
            if (at instanceof Activity)
                return (Activity) at;
            at = ((ContextWrapper) at).getBaseContext();
        }
        return null;
    }
}
