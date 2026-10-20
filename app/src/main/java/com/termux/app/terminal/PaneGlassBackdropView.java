package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.BitmapShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.FrameCrossfade;
import com.termux.app.chrome.GlassAnchor;
import com.termux.app.chrome.GlassFilters;
import com.termux.app.chrome.GlassMotionGate;
import com.termux.app.chrome.GlassMotionLight;
import com.termux.app.chrome.GlassRefraction;
import com.termux.app.chrome.WallpaperParallax;

/**
 * One pane's glass. Draws the shared pre-blurred wallpaper frame through this pane's own rect, the
 * terminal tint over it, and the film grain on top — the same three layers the whole-terminal glass
 * pane uses, per pane, so a split window reads as several floating slabs rather than one sheet with
 * lines drawn on it.
 *
 * <p>The wallpaper frame is never cropped into a per-pane bitmap: panes resize on every split, drag
 * and keyboard toggle, and cropping would allocate a pane-sized ARGB_8888 bitmap each time. The one
 * cached frame is drawn through a matrix instead, recomputed only when this view's place over the
 * wallpaper actually changes — its layout position, the wall page's slide, sink or tilt, or the
 * wallpaper's own parallax offset. The page's transform is undone in that matrix
 * ({@link GlassAnchor#place}), so the wallpaper in the slab stays fixed to the screen while the
 * slab's own tint, grain and rim move with the page.
 *
 * <p>With Fancier Glass on ({@link #setRefraction}), that same aim is handed to
 * {@link GlassRefraction} as uniforms and the frame is drawn through its program instead — bent
 * under the slab's rim and lit along it — in the one pass the plain draw took. The tint and the
 * grain stay where they are: they are the slab's own surface, not the picture behind it. Under
 * Living glass the program is also told how far the slab is tipped (the plank, a held border, the
 * pane's own press), so a tipping slab displaces the picture through it as a tipped plate of
 * glass does, though the aim keeps that picture fixed to the screen.
 *
 * <p>A terminal pane is also a legibility band ({@link #setVeilSource}): over the tint it draws the
 * veil that keeps the palette's foreground readable on whatever wallpaper is under it, under the
 * grain and the rim, in addition to the user's own terminal opacity — the order
 * {@code GlassSurfaceFactory} gives a chrome band's veil.
 */
public final class PaneGlassBackdropView extends View implements PaneRim.SlabEdge {

    private final Paint mFramePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint mTintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix mFrameMatrix = new Matrix();
    /** Where this slab stands over the wallpaper; read on every draw, see {@link GlassAnchor#place}. */
    private final GlassAnchor.Placement mPlacement = new GlassAnchor.Placement();
    /** The aim; the shader's matrix or the program's uniforms are set from it. */
    private final GlassAnchor.Aim mAim = new GlassAnchor.Aim();
    /** The matrix to the screen the aim was last written for. */
    private final Matrix mLastToScreen = new Matrix();
    private final int[] mRootLocation = new int[2];
    private final Rect mFrameRect = new Rect();
    private final RectF mClipRect = new RectF();
    /**
     * Living glass ({@link GlassMotionLight}): fed this slab's screen rect on every draw it already
     * makes and the presses on it, built on the first draw through the program. Null until then,
     * and while the slab draws plain.
     */
    @Nullable private GlassMotionLight mMotionLight;
    /** Scratch for the slab's corners on screen; a draw never allocates. */
    private final float[] mMotionCorners = new float[4];
    /** Living glass's gate, shared by the motion light and the tilt; built on first use. */
    @Nullable private GlassMotionLight.Gate mMotionGate;
    /**
     * The tilted slab ({@link GlassRefraction#tiltVector}): what the program was last handed,
     * {@code (0, 0)} flat. Read off the rotations on the way up on every draw through the program.
     */
    private final float[] mTilt = new float[2];
    /** The gate's answer for the tilt under way, or {@link #TILT_UNASKED} while the slab lies flat. */
    private int mTiltAllowed = TILT_UNASKED;
    /** Draws in a row the tilt has read the same; see {@link #TILT_STILL_DRAWS}. */
    private int mTiltStillDraws;
    private static final int TILT_UNASKED = -1;
    private static final int TILT_NO = 0;
    private static final int TILT_YES = 1;
    /**
     * How many draws in a row a tilt may read unchanged before the slab stops asking for frames
     * to follow it. A pane's press rig tips its frame on a spring of its own and never redraws
     * the slab, whose program only learns the tilt when it draws; so while tipped the slab asks
     * for the next frame itself, until the tilt has held still this long (a finger resting on a
     * tipped pane costs no frames) or lies flat again (the resting look, written on that draw).
     * A press or a release on the slab starts it asking again; the wall re-aims its pages' slabs
     * on every frame it tips them anyway.
     */
    private static final int TILT_STILL_DRAWS = 6;

    @Nullable private Bitmap mFrame;
    @Nullable private BitmapShader mFrameShader;
    /**
     * The frame {@link #mFrame} is fading in from. Held (not recycled) here through the fade so a
     * scan for what is still on screen keeps finding it as long as this pane is still drawing it.
     */
    @Nullable private Bitmap mPreviousFrame;
    @Nullable private BitmapShader mPreviousFrameShader;
    /** A clearer frame of the same rect for the bent edge to show, or null; see {@link #setClearFrame}. */
    @Nullable private Bitmap mClearFrame;
    @Nullable private BitmapShader mClearShader;
    private final Paint mPreviousFramePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    @NonNull private final FrameCrossfade mCrossfade = new FrameCrossfade();
    /** The preset's rim over the slab, or null (the hairline look draws none on a pane). */
    @Nullable private Drawable mRim;
    private float mRimRadiusPx = PaneGlass.NO_RIM;
    @Nullable private Drawable mGrain;
    /** The grain strength {@link #mGrain} was built from: a fresh drawable is not comparable. */
    private int mGrainStrength;
    @Nullable private ColorFilter mFrostFilter;
    /** The sink dim the frame paints' filter last undid; 255 for the plain frost. */
    private int mFilterKeptBrightness = 0xFF;
    /** False until the first {@link #setGlass} call, so the defaults are never mistaken for a dress. */
    private boolean mDressed;
    private int mTintColor;
    private float mRadiusPx;
    /** Non-zero while this view paints only the corner arcs of an opaque page. */
    private float mCornerMaskRadiusPx;
    /** Painted in the arcs over the frame, or alone when there is none: a corner is never left open. */
    private int mCornerMaskFallbackColor = android.graphics.Color.TRANSPARENT;
    @Nullable private android.graphics.Path mCornerMaskPath;
    private int mCornerMaskWidth;
    private int mCornerMaskHeight;
    private float mCornerMaskPathRadius;
    /** True until the aim has been written for the frame, the program and the place drawn now. */
    private boolean mAimStale = true;
    private int mLastWidth = -1;
    private int mLastHeight = -1;
    /**
     * The wallpaper's live x-offset, or null while nothing pans. Read on every draw rather than
     * copied in, so every slab samples the frame at the same offset the backdrop draws it at.
     */
    @Nullable private WallpaperParallax mParallax;
    /** The parallax the matrix was last aimed for, in px. */
    private float mLastParallax;
    /** The shader the aim was last written for; a rebind of the paint needs its own matrix. */
    @Nullable private BitmapShader mLastAimShader;
    /** Fancier Glass, or null for the plain draw. */
    @Nullable private GlassRefraction.Look mLook;
    /** The program the frame draws through while {@link #mLook} is set and the phone runs one. */
    @Nullable private GlassRefraction.Program mProgram;
    @Nullable private GlassRefraction.Program mPreviousProgram;
    /**
     * The retired frame's program once its fade has ended, kept for the next crossfade so a
     * wallpaper change does not compile a RuntimeShader each time. One program per surface; every
     * uniform it reads is written afresh before it draws again.
     */
    @Nullable private GlassRefraction.Program mSpareProgram;
    /** Told when {@link #drawsEdge} changes; see {@link #setOnEdgeChanged}. */
    @Nullable private Runnable mOnEdgeChanged;
    /** {@link #drawsEdge} as last told. */
    private boolean mEdgeTold;
    /** Answers this slab's veil; null for a slab nobody measures. See {@link #setVeilSource}. */
    @Nullable private PaneSurfaceStyle mVeilSource;
    /**
     * The terminal pane's veil: the terminal's own background at the smallest alpha that lets the
     * palette's worst foreground clear its target here. Drawn over the tint, under the grain and
     * the rim — where it was measured, and in addition to the user's opacity.
     */
    private int mVeilColor = android.graphics.Color.TRANSPARENT;
    /** The laid-out rect {@link #mVeilColor} was asked for. */
    private final Rect mVeilRect = new Rect();
    private final int[] mVeilLocation = new int[2];
    private final int[] mVeilRootLocation = new int[2];

    public PaneGlassBackdropView(@NonNull Context context) {
        this(context, null);
    }

    /** The inflated-from-XML constructor; the pane leaf layout declares this view. */
    public PaneGlassBackdropView(@NonNull Context context,
                                 @Nullable android.util.AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
    }

    /**
     * {@link #setGlass(Bitmap, Rect, int, Drawable, int, float, ColorFilter, boolean)} without a
     * crossfade — the swap lands outright, which is what a corner mask (never tracked for a
     * crossfade) and every caller that predates the wallpaper crossfade both still want.
     */
    public boolean setGlass(@Nullable Bitmap frame, @NonNull Rect frameRect, int tintColor,
                            @Nullable Drawable grain, int grainStrength, float radiusPx,
                            @Nullable ColorFilter frostFilter) {
        return setGlass(frame, frameRect, tintColor, grain, grainStrength, radiusPx, frostFilter,
            false);
    }

    /**
     * Dress this pane, or leave it exactly as it is.
     *
     * <p>Every chrome apply re-dresses every pane, and there are two applies to a frame; a page
     * change cost 78 of these passes, 174 ms, for panes whose glass had not moved (Pong,
     * 2026-09-09). So a re-dress with the frame the pane is already drawing, at the same rect,
     * tint, grain, radius and filter, is not a re-dress: it returns without building a shader or
     * invalidating, and the pane keeps the matrix it had. The frame is compared by identity, never
     * by pixels — a newly blurred frame is a new bitmap, which is what makes that sound.</p>
     *
     * <p>{@code frame} null while a fresh blur is in flight is not a reason to go tint-only: this
     * pane keeps drawing whatever it already had — {@code frame} simply loses the comparison to
     * {@code mFrame} below — until the next pass hands it a real one. {@code crossfade} asks for
     * the swap from that frame to a genuinely new one to fade over
     * {@link FrameCrossfade#DURATION_MS} instead of landing on the next draw; the caller already
     * knows this arrived to replace one a wallpaper change displaced, never a rotation or a radius
     * change.</p>
     *
     * @param frame         shared pre-blurred wallpaper frame, or null for a tint-and-grain-only pane
     * @param frameRect     that frame's rect in screen coordinates
     * @param grain         tiled grain layer, or null when grain is off
     * @param grainStrength what {@code grain} was built from; a fresh drawable compares equal to
     *                      nothing, so this is the grain's identity
     * @return true when this call actually re-dressed the pane
     */
    public boolean setGlass(@Nullable Bitmap frame, @NonNull Rect frameRect, int tintColor,
                            @Nullable Drawable grain, int grainStrength, float radiusPx,
                            @Nullable ColorFilter frostFilter, boolean crossfade) {
        Bitmap requested = frame != null && !frame.isRecycled() ? frame : null;
        // The frame kept while a fresh one is in flight is only kept while it can still be drawn:
        // a recycled one goes tint-only until the next pass hands a real frame, rather than being
        // wrapped in a shader (which throws for a recycled bitmap).
        Bitmap held = mFrame != null && !mFrame.isRecycled() ? mFrame : null;
        Bitmap live = requested != null ? requested : held;
        if (mDressed && live == mFrame && mTintColor == tintColor && mRadiusPx == radiusPx
            && mGrainStrength == grainStrength && (mGrain == null) == (grain == null)
            && mFrostFilter == frostFilter && mFrameRect.equals(frameRect)) {
            return false;
        }
        mDressed = true;
        if (live != mFrame) {
            if (crossfade && mFrame != null && !mFrame.isRecycled() && mFrameRect.equals(frameRect)) {
                mPreviousFrame = mFrame;
                mPreviousFrameShader = mFrameShader;
            } else {
                mPreviousFrame = null;
                mPreviousFrameShader = null;
                mCrossfade.cancel();
            }
            if (mPreviousFrame != null) mCrossfade.start();
        }
        mFrame = live;
        // CLAMP, and drawn as a shader rather than as a bitmap: the cached frame does not always
        // reach the full width of the screen (it is downsampled for the blur, and on ROMs that
        // magnify the wallpaper it is captured against a compensated rect), and a plain drawBitmap
        // simply stopped where the image ran out — leaving a strip of sharp wallpaper down the
        // right edge of the pane. Clamping stretches the edge column over the shortfall, which is
        // how every other glass surface here already renders the same frame.
        mFrameShader = mFrame == null
            ? null : new BitmapShader(mFrame, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        mFramePaint.setShader(mFrameShader);
        syncRefraction();
        mFrameRect.set(frameRect);
        mTintColor = tintColor;
        mGrain = grain;
        mGrainStrength = grainStrength;
        mRadiusPx = radiusPx;
        mFrostFilter = frostFilter;
        mFramePaint.setColorFilter(frostFilter);
        mPreviousFramePaint.setColorFilter(frostFilter);
        mFilterKeptBrightness = 0xFF;
        mAimStale = true;   // force the matrix to be rebuilt against the new frame
        invalidate();
        return true;
    }

    /**
     * The preset's rim on this slab, cut at {@code radiusPx} ({@link PaneGlass#NO_RIM} for none),
     * built by {@code style} only when the radius or the presence changes. Idempotent, so every
     * dress may restate it.
     */
    public void setRim(float radiusPx, @NonNull PaneSurfaceStyle style) {
        if (radiusPx == mRimRadiusPx && (radiusPx < 0f || mRim != null)) return;
        mRimRadiusPx = radiusPx;
        mRim = radiusPx < 0f ? null : style.paneGlassRim(radiusPx);
        invalidate();
    }

    /**
     * Who says how much veil this slab needs, or null for a slab nobody measures (a wall page's).
     * Set by {@link PaneGlass#apply(PaneSurfaceStyle, View, PaneGlassBackdropView, float, boolean)}
     * for a terminal pane, whose ink is the palette's and has to read on this glass whatever the
     * wallpaper does; asked again at once, so a dress after a wallpaper, palette or legibility
     * change lands its new veil in the same pass.
     */
    public void setVeilSource(@Nullable PaneSurfaceStyle source) {
        mVeilSource = source;
        if (refreshVeil()) invalidate();
    }

    /** The veil this slab is drawn with right now, for tests and the departure card. */
    public int veilColor() {
        return mVeilColor;
    }

    /**
     * Re-asks the veil for where this slab is laid out now. The rect is the laid-out one, which
     * ignores every transform — the root's editor scale among them — and the wall page's slide
     * too: a page sliding keeps the veil it had until it settles, rather than re-sampling the
     * wallpaper on every frame of the slide.
     *
     * @return true when the veil changed
     */
    private boolean refreshVeil() {
        int veil = android.graphics.Color.TRANSPARENT;
        if (mVeilSource != null && getWidth() > 0 && getHeight() > 0) {
            GlassAnchor.layoutOriginOnScreen(this, mVeilLocation, mVeilRootLocation);
            mVeilRect.set(mVeilLocation[0], mVeilLocation[1],
                mVeilLocation[0] + getWidth(), mVeilLocation[1] + getHeight());
            veil = mVeilSource.paneGlassVeil(mVeilRect);
        } else {
            mVeilRect.setEmpty();
        }
        if (veil == mVeilColor) return false;
        mVeilColor = veil;
        return true;
    }

    /** True when this slab has moved or resized since its veil was asked for. */
    private boolean veilRectMoved() {
        GlassAnchor.layoutOriginOnScreen(this, mVeilLocation, mVeilRootLocation);
        return mVeilRect.left != mVeilLocation[0] || mVeilRect.top != mVeilLocation[1]
            || mVeilRect.width() != getWidth() || mVeilRect.height() != getHeight();
    }

    /**
     * Paint only the four corner arcs, and skip the tint and grain.
     *
     * <p>For a page whose content is an opaque surface the system composites outside the view
     * hierarchy — the X display — so no parent clip can round it. The arcs are painted over the
     * surface with what sits behind the page instead, which rounds the page's corners for the
     * same cost as any other draw.
     *
     * @param radiusPx the corner radius to cut, or 0 to go back to painting the whole slab
     */
    public void setCornerMaskRadius(float radiusPx) {
        float resolved = Math.max(0f, radiusPx);
        if (mCornerMaskRadiusPx == resolved) return;
        mCornerMaskRadiusPx = resolved;
        syncRefraction();
        invalidate();
    }

    /**
     * Fancier Glass on this slab: the frame is bent under the rim and lit along it, through
     * {@link GlassRefraction}, at this slab's own corner radius. Null puts the plain draw back —
     * the default mode, and what every phone below API 33 draws whatever it is handed. Idempotent,
     * so every dress may restate it. A corner mask never refracts: it stands in for what is
     * behind the page, which has no rim.
     */
    public void setRefraction(@Nullable GlassRefraction.Look look) {
        if (java.util.Objects.equals(look, mLook)) return;
        mLook = look;
        syncRefraction();
        // Whichever way the draw goes now, its aim has to be written afresh: the program's on
        // first use, the shader's matrix after the program cleared it.
        mAimStale = true;
        invalidate();
    }

    /**
     * A clearer frame of the wallpaper, captured for the very rect the frame was (the cache's
     * one rect at a lower blur), which the bent edge shows in place of the frost under the look's
     * clarity — as {@link com.termux.app.chrome.SharedFrameDrawable#setClearFrame} does. Any
     * resolution; the program is told its pixel size against the frame's. Null, the default,
     * keeps the frost at the edge, and a plain draw ignores it. Idempotent, so every dress may
     * restate it.
     */
    public void setClearFrame(@Nullable Bitmap clearFrame) {
        Bitmap live = clearFrame != null && !clearFrame.isRecycled() ? clearFrame : null;
        if (live == mClearFrame) return;
        mClearFrame = live;
        mClearShader = live == null
            ? null : new BitmapShader(live, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        syncClarity();
        invalidate();
    }

    /** The clearer frame this pane draws its edge from, so the blur cache never recycles it under a draw. */
    @Nullable
    public Bitmap heldClearFrame() {
        return mClearFrame;
    }

    /** Hands the program the clear frame at its pixel size against the frame's, or nothing. */
    private void syncClarity() {
        GlassRefraction.Program program = mProgram;
        if (program == null) return;
        Bitmap frame = mFrame;
        Bitmap clear = mClearFrame;
        BitmapShader clearShader = mClearShader;
        if (frame == null || clear == null || clearShader == null || clear.isRecycled()) {
            program.setClarityInput(null, 1f, 1f);
            return;
        }
        program.setClarityInput(clearShader,
            clear.getWidth() / (float) Math.max(1, frame.getWidth()),
            clear.getHeight() / (float) Math.max(1, frame.getHeight()));
    }

    /** True while the frame is drawn through the refraction program rather than plain. */
    public boolean refracts() {
        return mProgram != null;
    }

    /**
     * True while this slab's own program draws its outline, the gathered light: it is shown, it
     * has a frame, and that frame is drawn through the program, not plain (no look, a corner mask,
     * a driver that refused the program, Android 12). The pane's resting border yields to it
     * ({@link PaneRim}); every other case keeps the border.
     */
    @Override
    public boolean drawsEdge() {
        return getVisibility() == View.VISIBLE && mProgram != null && mFrame != null
            && !mFrame.isRecycled();
    }

    @Override
    public void setOnEdgeChanged(@Nullable Runnable onEdgeChanged) {
        mOnEdgeChanged = onEdgeChanged;
        mEdgeTold = drawsEdge();
    }

    /** Tells the border once {@link #drawsEdge} has moved, so it redraws with or without its line. */
    private void tellEdge() {
        boolean edge = drawsEdge();
        if (edge == mEdgeTold) return;
        mEdgeTold = edge;
        Runnable told = mOnEdgeChanged;
        if (told != null) told.run();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        tellEdge();
    }

    /**
     * Points the paints at the program or at the frame's own shader, whichever the look asks for
     * and the phone can run. On every frame change and look change, never per draw: the program
     * captures its input when it is set.
     */
    private void syncRefraction() {
        syncProgram();
        tellEdge();
    }

    private void syncProgram() {
        mLastAimShader = null;
        GlassRefraction.Look look = mLook;
        BitmapShader shader = mFrameShader;
        if (look == null || shader == null || mCornerMaskRadiusPx > 0f || !GlassRefraction.available()) {
            mProgram = null;
            mPreviousProgram = null;
            mSpareProgram = null;
            mFramePaint.setShader(shader);
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        GlassRefraction.Program program = mProgram;
        if (program == null || program.density() != density) {
            program = GlassRefraction.Program.create(density);
        }
        mProgram = program;
        if (program == null) {
            // The driver refused the program: the plain draw is the look this phone gets.
            mPreviousProgram = null;
            mFramePaint.setShader(shader);
            return;
        }
        program.setLook(look);
        program.setInput(shader);
        syncClarity();
        program.applyTo(mFramePaint);
        BitmapShader previousShader = mPreviousFrameShader;
        if (previousShader == null) {
            if (mPreviousProgram != null) mSpareProgram = mPreviousProgram;
            mPreviousProgram = null;
            return;
        }
        GlassRefraction.Program previous = mPreviousProgram;
        if (previous == null || previous.density() != density) {
            GlassRefraction.Program spare = mSpareProgram;
            mSpareProgram = null;
            previous = spare != null && spare.density() == density
                ? spare : GlassRefraction.Program.create(density);
        }
        mPreviousProgram = previous;
        if (previous != null) {
            previous.setLook(look);
            previous.setInput(previousShader);
            previous.applyTo(mPreviousFramePaint);
        }
    }

    /**
     * A finger came down on this slab at {@code (x, y)} in its own px: the glass dents under it
     * ({@link GlassMotionLight#press}) until {@link #releaseGlass}. Nothing on a slab drawn plain,
     * and nothing where Living glass is off or motion is stopped.
     */
    public void pressGlass(float x, float y) {
        if (mProgram == null || getWidth() <= 0 || getHeight() <= 0) return;
        if (motionLight().press(AnimationUtils.currentAnimationTimeMillis(), x, y)) {
            postInvalidateOnAnimation();
        }
        followTilt();
    }

    /** The finger lifted or the gesture went elsewhere: the dent springs back. Safe to repeat. */
    public void releaseGlass() {
        GlassMotionLight light = mMotionLight;
        if (light != null && light.release(AnimationUtils.currentAnimationTimeMillis())) {
            postInvalidateOnAnimation();
        }
        followTilt();
    }

    /**
     * A press or a release sets the press rig's tilt moving: ask for frames to follow it again,
     * even where it had held still long enough for the slab to stop asking.
     */
    private void followTilt() {
        mTiltStillDraws = 0;
        if (mProgram != null && (mTilt[0] != 0f || mTilt[1] != 0f)) postInvalidateOnAnimation();
    }

    @NonNull
    private GlassMotionLight motionLight() {
        GlassMotionLight light = mMotionLight;
        if (light == null) {
            light = new GlassMotionLight(getResources().getDisplayMetrics().density, motionGate());
            mMotionLight = light;
        }
        return light;
    }

    @NonNull
    private GlassMotionLight.Gate motionGate() {
        GlassMotionLight.Gate gate = mMotionGate;
        if (gate == null) {
            gate = GlassMotionGate.of(getContext());
            mMotionGate = gate;
        }
        return gate;
    }

    /**
     * How far this slab is tipped now, into {@link #mTilt}, and whether to ask for the next frame
     * to follow it. The rotations of every view on the way up add: the wall page's plank tip and
     * a held border's press ({@code rotationY}), and a terminal pane's own press rig on its frame
     * ({@code rotationX} and {@code rotationY}), which the aim leaves out on purpose
     * ({@link GlassAnchor#place}) but which tips the glass all the same. The gate is asked once,
     * as the slab leaves flat, never per frame; where Living glass may not move, the slab draws
     * flat whatever tips it. Walks the tree without allocating.
     *
     * @return true when the slab should draw again next frame to follow its tilt
     */
    private boolean readTilt() {
        float rotationX = 0f;
        float rotationY = 0f;
        View current = this;
        while (true) {
            rotationX += current.getRotationX();
            rotationY += current.getRotationY();
            android.view.ViewParent parent = current.getParent();
            if (!(parent instanceof View)) break;
            current = (View) parent;
        }
        float lastX = mTilt[0];
        float lastY = mTilt[1];
        GlassRefraction.tiltVector(rotationX, rotationY, mTilt);
        if (mTilt[0] == 0f && mTilt[1] == 0f) {
            // Flat: the next tip asks the gate afresh.
            mTiltAllowed = TILT_UNASKED;
        } else {
            if (mTiltAllowed == TILT_UNASKED) {
                mTiltAllowed = motionGate().multiplier() > 0f ? TILT_YES : TILT_NO;
            }
            if (mTiltAllowed == TILT_NO) {
                mTilt[0] = 0f;
                mTilt[1] = 0f;
            }
        }
        if (mTilt[0] == 0f && mTilt[1] == 0f) {
            mTiltStillDraws = 0;
            return false;
        }
        if (mTilt[0] != lastX || mTilt[1] != lastY) mTiltStillDraws = 0;
        else mTiltStillDraws++;
        return mTiltStillDraws < TILT_STILL_DRAWS;
    }

    /**
     * Hands the light this slab's rect on screen, read off the placement this draw already took
     * (the page's slide, sink and tilt and any published motion in it). True while its springs
     * still move.
     */
    private boolean driveMotionLight(int width, int height) {
        float[] corners = mMotionCorners;
        corners[0] = 0f;
        corners[1] = 0f;
        corners[2] = width;
        corners[3] = height;
        mPlacement.toScreen.mapPoints(corners);
        return motionLight().onDraw(AnimationUtils.currentAnimationTimeMillis(),
            Math.min(corners[0], corners[2]), Math.min(corners[1], corners[3]),
            Math.max(corners[0], corners[2]), Math.max(corners[1], corners[3]));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // A slab that comes back starts from rest, never from the bend it left with.
        if (mMotionLight != null) mMotionLight.reset();
        mTilt[0] = 0f;
        mTilt[1] = 0f;
        mTiltAllowed = TILT_UNASKED;
        mTiltStillDraws = 0;
    }

    /** The colour laid over the arcs: the wall's dim over its wallpaper, or a flat colour alone. */
    public void setCornerMaskFallbackColor(int color) {
        if (mCornerMaskFallbackColor == color) return;
        mCornerMaskFallbackColor = color;
        invalidate();
    }

    /**
     * Re-aim on the next draw; call after this slab has moved, or after its page did — a slide,
     * the sink, the plank's tilt — since a transform alone never redraws a child.
     */
    public void invalidateGlassPosition() {
        mAimStale = true;
        invalidate();
    }

    /**
     * Follow the wallpaper's parallax: the frame is sampled {@code parallax.offsetPx()} further
     * along on every draw. Null stops following. Whoever moves the offset invalidates this view;
     * the draw itself notices the change and re-aims.
     */
    public void setParallax(@Nullable WallpaperParallax parallax) {
        if (mParallax == parallax) return;
        mParallax = parallax;
        invalidateGlassPosition();
    }

    /** The frame this pane is drawing, so the blur cache never recycles it under a draw. */
    @Nullable
    public Bitmap heldFrame() {
        return mFrame;
    }

    /**
     * The frame a crossfade is still fading out of, so the blur cache never recycles it mid-fade —
     * null once the fade has landed on {@link #heldFrame()} alone, or when the last swap never
     * crossfaded at all.
     */
    @Nullable
    public Bitmap fadingFrame() {
        return mCrossfade.isFinished() ? null : mPreviousFrame;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        int save = canvas.save();
        if (mCornerMaskRadiusPx > 0f) {
            // Everything outside the rounded page, which is exactly the four arcs.
            canvas.clipPath(cornerMaskPath(width, height));
        } else if (mRadiusPx > 0f) {
            mClipRect.set(0f, 0f, width, height);
            canvas.clipRect(mClipRect);   // the round clip is the parent frame's; this bounds ours
        }
        // A software canvas refuses a RuntimeShader (a RealtimeBlurView capturing the window is
        // one), so the refracted frame sits that pass out; it only feeds another view's blur.
        if (mFrame != null && !mFrame.isRecycled() && mFrameShader != null
                && (mProgram == null || canvas.isHardwareAccelerated())) {
            BitmapShader aimShader = mFrameShader;
            Bitmap aimFrame = mFrame;
            // Where this slab is on screen through its page's slide, sink and tilt: the frame is
            // aimed past all of it, so the wallpaper in the slab stays where the wallpaper behind
            // the wall is. The parallax carries the wallpaper to the left under it.
            GlassAnchor.place(this, mPlacement);
            // The sink dims the page's layer, this slab with it; the wallpaper behind does not
            // darken, so the frame is lifted by as much as the layer will take away.
            int kept = mPlacement.keptBrightness;
            if (kept != mFilterKeptBrightness) {
                mFilterKeptBrightness = kept;
                ColorFilter filter = GlassFilters.undimmed(mFrostFilter, kept);
                mFramePaint.setColorFilter(filter);
                mPreviousFramePaint.setColorFilter(filter);
            }
            float parallax = mParallax == null ? 0f : mParallax.offsetPx();
            GlassRefraction.Program program = mProgram;
            if (program != null) {
                // Where the slab stands now against where it stood last draw: its bend follows on
                // a spring and asks for the frames it needs to settle. At rest it asks for none
                // and the program draws the resting look.
                if (driveMotionLight(width, height)) postInvalidateOnAnimation();
                program.setMotionLight(mMotionLight);
                if (mPreviousProgram != null) mPreviousProgram.setMotionLight(mMotionLight);
                // How far the slab is tipped, so the picture through it is displaced as through a
                // tipped plate and its rim leans with the tip. Flat, (0, 0): the resting look.
                if (readTilt()) postInvalidateOnAnimation();
                program.setTilt(mTilt[0], mTilt[1]);
                if (mPreviousProgram != null) mPreviousProgram.setTilt(mTilt[0], mTilt[1]);
            }
            if (mAimStale || aimShader != mLastAimShader || !mPlacement.toScreen.equals(mLastToScreen)
                || width != mLastWidth || height != mLastHeight || parallax != mLastParallax) {
                mAimStale = false;
                mLastToScreen.set(mPlacement.toScreen);
                mLastWidth = width;
                mLastHeight = height;
                mLastParallax = parallax;
                mLastAimShader = aimShader;
                mAim.set(mPlacement.toScreen, mFrameRect, aimFrame.getWidth(), aimFrame.getHeight(),
                    parallax);
                float[] aim = mAim.scaleOffset;
                if (program != null) {
                    // The same aim, as uniforms: the program samples the frame by its own pixels,
                    // and the rim runs along this slab's own rounded rect, so the bend and the
                    // light move with the page while the wallpaper they bend stays put.
                    program.setAim(aim[0], aim[1], aim[2], aim[3]);
                    program.setLocal(mAim.local);
                    program.setRect(0f, 0f, width, height, mRadiusPx);
                    if (mPreviousProgram != null) {
                        mPreviousProgram.setAim(aim[0], aim[1], aim[2], aim[3]);
                        mPreviousProgram.setLocal(mAim.local);
                        mPreviousProgram.setRect(0f, 0f, width, height, mRadiusPx);
                    } else if (mPreviousFrameShader != null) {
                        // No program for the retiring frame: it fades out drawn plain.
                        mAim.shaderMatrix(mFrameMatrix);
                        mPreviousFrameShader.setLocalMatrix(mFrameMatrix);
                    }
                } else {
                    mAim.shaderMatrix(mFrameMatrix);
                    aimShader.setLocalMatrix(mFrameMatrix);
                    // The retired frame shares this pane's rect and, always, the current frame's
                    // own size — both are full captures of the same radius — so the same matrix
                    // aims it.
                    if (mPreviousFrameShader != null) mPreviousFrameShader.setLocalMatrix(mFrameMatrix);
                }
            }
            float progress = mCrossfade.progress();
            boolean fading = progress < 1f && mPreviousFrame != null && !mPreviousFrame.isRecycled()
                && mPreviousFrameShader != null
                && mPreviousFrame.getWidth() == mFrame.getWidth()
                && mPreviousFrame.getHeight() == mFrame.getHeight();
            if (fading) {
                if (program == null || mPreviousProgram == null) {
                    mPreviousFramePaint.setShader(mPreviousFrameShader);
                }
                mPreviousFramePaint.setAlpha(255);
                canvas.drawRect(0f, 0f, width, height, mPreviousFramePaint);
                mFramePaint.setAlpha(Math.round(255f * progress));
                canvas.drawRect(0f, 0f, width, height, mFramePaint);
                postInvalidateOnAnimation();
            } else {
                mPreviousFrame = null;
                mPreviousFrameShader = null;
                if (mPreviousProgram != null) mSpareProgram = mPreviousProgram;
                mPreviousProgram = null;
                mFramePaint.setAlpha(255);
                canvas.drawRect(0f, 0f, width, height, mFramePaint);
            }
        }
        // A corner mask stands in for what is behind the page, so it takes neither the pane
        // tint nor the grain: those belong to a pane's own slab, and the page has none. It takes
        // the wall's own colour over the frame - the dim the root wears - or alone.
        if (mCornerMaskRadiusPx > 0f) {
            if (android.graphics.Color.alpha(mCornerMaskFallbackColor) > 0) {
                mTintPaint.setColor(mCornerMaskFallbackColor);
                canvas.drawRect(0f, 0f, width, height, mTintPaint);
            }
        } else {
            if (android.graphics.Color.alpha(mTintColor) > 0) {
                mTintPaint.setColor(mTintColor);
                canvas.drawRect(0f, 0f, width, height, mTintPaint);
            }
            // A pane that moved or resized (a split, a divider drag, the keyboard) re-asks for its
            // veil here: none of those re-dress it, and the veil is memoised per rect, so a pane
            // that did not move costs one walk up the tree.
            if (mVeilSource != null && veilRectMoved()) refreshVeil();
            if (android.graphics.Color.alpha(mVeilColor) > 0) {
                mTintPaint.setColor(mVeilColor);
                canvas.drawRect(0f, 0f, width, height, mTintPaint);
            }
            if (mGrain != null) {
                mGrain.setBounds(0, 0, width, height);
                mGrain.draw(canvas);
            }
            if (mRim != null) {
                mRim.setBounds(0, 0, width, height);
                mRim.draw(canvas);
            }
        }
        canvas.restoreToCount(save);
    }
    /** Rebuilt only when the size or the radius changes, never per draw. */
    @NonNull
    private android.graphics.Path cornerMaskPath(int width, int height) {
        if (mCornerMaskPath != null && mCornerMaskWidth == width && mCornerMaskHeight == height
            && mCornerMaskPathRadius == mCornerMaskRadiusPx) {
            return mCornerMaskPath;
        }
        float radius = PaneShape.radiusForBounds(mCornerMaskRadiusPx, width, height);
        android.graphics.Path path = mCornerMaskPath == null
            ? new android.graphics.Path() : mCornerMaskPath;
        path.reset();
        path.addRect(0f, 0f, width, height, android.graphics.Path.Direction.CW);
        path.addRoundRect(0f, 0f, width, height, radius, radius,
            android.graphics.Path.Direction.CCW);
        path.setFillType(android.graphics.Path.FillType.WINDING);
        mCornerMaskPath = path;
        mCornerMaskWidth = width;
        mCornerMaskHeight = height;
        mCornerMaskPathRadius = mCornerMaskRadiusPx;
        return path;
    }

    /**
     * This view's position on screen as laid out, ignoring every transform on the way up but one:
     * the wall page's slide, returned separately — the anchor every glass surface shares, see
     * {@link GlassAnchor#layoutOriginOnScreen}.
     *
     * @return the wall page's translation on the way up, in px; 0 off the wall
     */
    // Package-private so the regression test can pin it directly.
    float layoutOriginOnScreen(@NonNull int[] out) {
        return GlassAnchor.layoutOriginOnScreen(this, out, mRootLocation);
    }

}
