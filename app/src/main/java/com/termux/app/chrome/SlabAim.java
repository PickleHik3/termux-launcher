package com.termux.app.chrome;

import android.graphics.Matrix;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.glass.GlassEntry;
import com.termux.app.glass.GlassKind;
import com.termux.app.glass.GlassService;
import com.termux.app.window.WindowLayer;

/**
 * Keeps a slab that draws the stored wallpaper frame aimed where its page went (ADR 0029 section
 * 10, step 2). The frame is aimed in screen space from the slab's place ({@link GlassAnchor#place}),
 * and a page's slide, sink or tilt, or the wallpaper's own pan, is a transform that never redraws a
 * child; the activity used to push an invalidate to every slab on every frame the wall moved.
 *
 * <p>Now the slab registers with the window's {@link GlassService} while it is attached, and the
 * glass commit (the frame table's last step, on every frame that draws) asks {@link #onFrame}
 * whether its place or the wallpaper's offset moved since it last asked; only then does the slab
 * draw again, in that same frame. At rest nothing draws, so nothing runs. Reading the place walks
 * the slab's parents once and allocates nothing.</p>
 */
public final class SlabAim implements GlassEntry.Frame {

    @NonNull private final View mView;
    @NonNull private final GlassAnchor.Placement mPlacement = new GlassAnchor.Placement();
    /** The place and parallax as of the last frame that asked; the next frame compares to them. */
    @NonNull private final Matrix mLast = new Matrix();
    private int mLastKept = -1;
    private float mLastParallax = Float.NaN;
    @Nullable private WallpaperParallax mParallax;
    @Nullable private GlassService mGlass;
    @Nullable private String mElement;
    /** The element name and place the entry registers under; null names it after the slab. */
    @Nullable private final String mFixedElement;
    @NonNull private final WindowLayer mLayer;
    private final int mPriority;

    /** A pane slab: "slab@hash" on the pages layer, behind widgets and floats. */
    public SlabAim(@NonNull View view) {
        this(view, null, WindowLayer.PAGES, GlassEntry.PRIORITY_WIDGET);
    }

    /**
     * A surface that keeps its own element name, layer and priority: a bar's glass view, which
     * draws its frost inside itself and registers once under its id's name.
     */
    public SlabAim(@NonNull View view, @Nullable String element, @NonNull WindowLayer layer,
                   int priority) {
        mView = view;
        mFixedElement = element;
        mLayer = layer;
        mPriority = priority;
    }

    /** The wallpaper pan the slab's frame follows; null for none. */
    public void setParallax(@Nullable WallpaperParallax parallax) {
        mParallax = parallax;
    }

    /**
     * Registers the slab with its window's glass service as a Wallpaper surface on its layer. Safe to call twice; nothing when the window has no service (a preview, a card).
     */
    public void attach() {
        if (mGlass != null) return;
        GlassService glass = GlassService.of(mView);
        if (glass == null) return;
        String element = mFixedElement != null ? mFixedElement
            : "slab@" + Integer.toHexString(System.identityHashCode(mView));
        // A pane slab's place in its layer is behind widgets and floats: the worst priority. It
        // is Wallpaper, so it never takes a Live slot.
        if (glass.register(new GlassEntry(element, mView, GlassKind.WALLPAPER,
            mPriority, mLayer, this), null) == null) return;
        mGlass = glass;
        mElement = element;
        // The first frame after attaching draws the slab where it stands now.
        mLastKept = -1;
    }

    /** Lets the slab's entry go. */
    public void detach() {
        GlassService glass = mGlass;
        String element = mElement;
        mGlass = null;
        mElement = null;
        if (glass != null && element != null) glass.unregister(element);
    }

    /** Whether the slab is registered with a service, so the activity need not push to it. */
    public boolean attached() {
        return mGlass != null;
    }

    @Override
    public void onFrame() {
        if (mView.getWidth() <= 0 || mView.getHeight() <= 0) return;
        if (moved()) mView.invalidate();
    }

    /** Whether the slab's place over the wallpaper moved since the last call; allocation-free. */
    boolean moved() {
        GlassAnchor.place(mView, mPlacement);
        float parallax = mParallax == null ? 0f : mParallax.offsetPx();
        if (mPlacement.keptBrightness == mLastKept && parallax == mLastParallax
            && mPlacement.toScreen.equals(mLast)) {
            return false;
        }
        mLast.set(mPlacement.toScreen);
        mLastKept = mPlacement.keptBrightness;
        mLastParallax = parallax;
        return true;
    }
}
