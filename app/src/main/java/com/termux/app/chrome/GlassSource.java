package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.chrome.scene.LiveSceneFrame;

/**
 * What a glass surface's program reads this draw, picked in one place for every surface that draws
 * a backdrop ({@link SharedFrameDrawable}, {@code PaneGlassBackdropView}): the live picture (what
 * is beneath, recorded into a node that carries the program as its effect), the wallpaper scene's
 * live frame, or the stored frame (the blur cache's shared, pre-blurred wallpaper). The two frames
 * are the same kind of input: a shader over a bitmap, its pixel size and the screen rect it was
 * captured for, aimed from the surface's place on screen and bound to the program the same way.
 * The look, its colour, the rim, the pose and the touch are the surface's, whichever source it
 * reads, so the three draw alike by construction.
 *
 * <p>One per surface, reused on every draw: selecting writes a few fields and allocates nothing.
 * It also remembers which frame the surface's paint or program was last pointed at, so a source
 * that changes (the scene comes on, publishes, or goes off) is bound once, never per draw.</p>
 */
public final class GlassSource {

    /** Nothing to draw: no live picture, no scene frame and no stored frame. */
    public static final int NONE = 0;
    /** The blur cache's stored frame, with its parallax and its crossfade. */
    public static final int STORED = 1;
    /** The wallpaper scene's live frame: no parallax (the scene does not pan), no crossfade. */
    public static final int SCENE = 2;
    /** The live picture, drawn through the surface's own node under the program as an effect. */
    public static final int LIVE = 3;

    private int mKind = NONE;
    @Nullable private Bitmap mBitmap;
    @Nullable private BitmapShader mShader;
    @Nullable private Rect mRect;
    private float mParallaxPx;
    private int mGeneration;
    /** A scene frame was out at the last {@link #select}. */
    private boolean mSceneOn;
    /** A scene frame is on the paint or program, not the stored frame. */
    private boolean mSceneBound;
    /** Which publish of the scene that is, while {@link #mSceneBound}. */
    private int mBoundGeneration;

    /**
     * Which source a draw reads: the live picture whenever it can be drawn, else the scene's live
     * frame while one is out, else the stored frame while one is held. Pure.
     */
    @VisibleForTesting
    static int pick(boolean liveShown, boolean sceneOn, boolean storedHeld) {
        if (liveShown) return LIVE;
        if (sceneOn) return SCENE;
        return storedHeld ? STORED : NONE;
    }

    /**
     * Whether the paint or program must be pointed at another frame before a draw of {@code kind}:
     * a scene frame it does not hold yet (another publish, or the stored frame bound), or the
     * stored frame back once the scene has gone off. A live picture over a scene binds nothing.
     * Pure.
     */
    @VisibleForTesting
    static boolean rebinds(int kind, boolean sceneOn, int generation, boolean sceneBound,
                           int boundGeneration) {
        if (kind == SCENE) return !sceneBound || boundGeneration != generation;
        if (sceneOn) return false;
        return sceneBound;
    }

    /**
     * Picks this draw's source ({@link #pick}) and returns its kind. {@code stored} counts only
     * while it and its shader are live bitmaps.
     *
     * @param liveShown        the live picture is ready and this canvas can draw its node
     * @param scene            {@link LiveSceneFrame#current()}
     * @param stored           the stored frame, or null
     * @param storedShader     the shader over it
     * @param storedRect       the screen rect it was captured for; held by reference, not copied
     * @param storedParallaxPx the wallpaper's live offset under it
     */
    public int select(boolean liveShown, @Nullable LiveSceneFrame.Frame scene,
                      @Nullable Bitmap stored, @Nullable BitmapShader storedShader,
                      @NonNull Rect storedRect, float storedParallaxPx) {
        boolean storedHeld = stored != null && !stored.isRecycled() && storedShader != null;
        int kind = pick(liveShown, scene != null, storedHeld);
        mKind = kind;
        if (scene != null) {
            mGeneration = scene.generation;
        }
        if (kind == SCENE) {
            mBitmap = scene.bitmap;
            mShader = scene.shader;
            mRect = LiveSceneFrame.rect();
            mParallaxPx = 0f;
        } else if (kind == STORED) {
            mBitmap = stored;
            mShader = storedShader;
            mRect = storedRect;
            mParallaxPx = storedParallaxPx;
        } else {
            mBitmap = null;
            mShader = null;
            mRect = null;
            mParallaxPx = 0f;
        }
        mSceneOn = scene != null;
        return kind;
    }

    /**
     * True once per change of the frame the paint or program should hold (see {@link #rebinds}),
     * after which it counts as held: the caller then points it at the scene's frame
     * ({@link #shader()}), or re-syncs its stored frame when the scene has gone off.
     */
    public boolean takeRebind() {
        if (!rebinds(mKind, mSceneOn, mGeneration, mSceneBound, mBoundGeneration)) return false;
        mSceneBound = mKind == SCENE;
        mBoundGeneration = mGeneration;
        return true;
    }

    /** The stored frame is what the paint or program holds now: the surface just bound it. */
    public void storedBound() {
        mSceneBound = false;
    }

    /** The kind {@link #select} last picked. */
    public int kind() {
        return mKind;
    }

    /** The selected frame's bitmap; null for {@link #LIVE} and {@link #NONE}. */
    @Nullable
    public Bitmap bitmap() {
        return mBitmap;
    }

    /** The selected frame's shader; null for {@link #LIVE} and {@link #NONE}. */
    @Nullable
    public BitmapShader shader() {
        return mShader;
    }

    /** The screen rect the selected frame covers; null for {@link #LIVE} and {@link #NONE}. */
    @Nullable
    public Rect rect() {
        return mRect;
    }

    /** The wallpaper's offset under the selected frame: the stored frame's parallax, else 0. */
    public float parallaxPx() {
        return mParallaxPx;
    }

    /**
     * The selected frame's aim from a surface whose own origin is at {@code (originX, originY)} on
     * screen, as {@link SharedFrameDrawable#aim(float[], Rect, int, int, int, int, float)} writes
     * it: the one aim the program and the plain draw take, whichever frame is selected. Writes
     * nothing without a frame.
     */
    public void aim(@NonNull float[] out, int originX, int originY) {
        Bitmap bitmap = mBitmap;
        Rect rect = mRect;
        if (bitmap == null || rect == null) return;
        SharedFrameDrawable.aim(out, rect, bitmap.getWidth(), bitmap.getHeight(), originX, originY,
            mParallaxPx);
    }

    /** Whether a frame replaced under this source fades in ({@link FrameCrossfade}): the stored frame only. */
    public boolean crossfades() {
        return mKind == STORED;
    }
}
