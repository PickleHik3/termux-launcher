package com.termux.app.launcher.drawer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.DockGlassRendering;
import com.termux.app.GlassRimRenderer;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;

/**
 * The app drawer's plane: one full-bleed view that paints a glass slab over an animated rounded
 * rectangle and owns the gesture that puts it away again.
 *
 * <p>Structurally the twin of {@code CommandPaletteView}: the view fills its host and never moves,
 * lays nothing out per frame, and expresses the whole transition as a rectangle it draws into plus
 * a {@link ViewOutlineProvider} that clips to the same rectangle. {@link #setFrame} is therefore
 * the only per-frame call, and it does no allocation.
 *
 * <p><b>Why the slab is drawn rather than laid out.</b> A child view sized to the frame would need
 * a measure/layout pass per frame, on a view tree whose accessory stack is deliberately frozen for
 * exactly that reason. Drawing a {@link Drawable} into the frame costs one {@code setBounds} and
 * one {@code draw}, and the outline clip trims the overdrawn edge.
 *
 * <p><b>Closing drag.</b> A downward drag anywhere on the plane runs the transition backwards. It
 * reuses {@link AppDrawerGestureArbiter} — the same one-way latch the dock uses to open — rather
 * than a second ad-hoc state machine, so "downward, dominated by its vertical component, past
 * 1.15x slop" means one thing in this app and not two. The plane reports raw screen-Y to
 * {@link Callbacks}; the controller owns every decision about what that Y means.
 */
public final class AppDrawerPlaneView extends FrameLayout {

    /** Raw touch reports from the plane's close drag. The controller interprets them. */
    public interface Callbacks {

        /** A downward drag has claimed the stream; {@code downRawY} is the ACTION_DOWN point. */
        void onPlaneDragBegin(float downRawY);

        void onPlaneDrag(float rawY);

        /** @param velocityPxPerSec vertical release velocity, positive downwards */
        void onPlaneDragEnd(float velocityPxPerSec);

        void onPlaneDragCancel();
    }

    /**
     * Who owns an {@code ACTION_DOWN}, asked of the plane's content before the plane claims it.
     *
     * <p>The plane's own arbiter and a {@code RecyclerView} cannot both be allowed to race for a
     * stream: the plane claims at 1.15x slop and the recycler starts scrolling at 1.0x, and the
     * recycler's {@code requestDisallowInterceptTouchEvent(true)} kills the plane's interceptor the
     * moment it does. The winner would then be decided by how fast the finger moved. So the plane
     * asks first and, when the answer is the grid's, does not compete at all — the content resolves
     * every delta through the nested-scroll channel and reports a close back through the
     * {@code *CloseDragFromContent} forwarders, which land in the same {@link Callbacks}.
     */
    public interface CloseDragGate {

        /**
         * @param x the plane's local X
         * @param y the plane's local Y
         * @return true when the content owns the point and the plane must defer
         */
        boolean ownsPoint(float x, float y);
    }

    /**
     * The plane's own eligibility snapshot: every veto cleared.
     *
     * <p>{@link AppDrawerGestureArbiter.Eligibility} exists to stop the <em>dock</em> claiming a
     * drag that belongs to a search field, an A-Z scrub or a pinned-icon pickup. None of those
     * surfaces exist on the plane — it is a full-screen sheet that is, by the time it can be
     * touched at all, already open — so what is left of the arbiter here is precisely the direction
     * and dominance test, which is the part being reused.
     */
    private static final AppDrawerGestureArbiter.Eligibility PLANE_ELIGIBILITY =
        AppDrawerGestureArbiter.Eligibility.allClear();

    /** Content fades in behind the leading edge of the sprout, as the palette's body does. */
    private static final float CONTENT_FADE_START = 0.22f;
    private static final float CONTENT_FADE_END = 0.72f;

    private final AppDrawerGestureArbiter mArbiter = new AppDrawerGestureArbiter();
    private final FrameLayout mContent;
    private final float mTouchSlopPx;
    /** Outward-rounded slab bounds; the outline clip trims the extra edge back inward. */
    private final Rect mSlabBounds = new Rect();

    @NonNull private Frame mFrame = new Frame(0f, 0f, 0f, 0f);
    private float mRadiusPx;
    private float mProgress;
    private final GlassRimRenderer mRim;
    @Nullable private Drawable mGlassSurface;
    @Nullable private Callbacks mCallbacks;
    @Nullable private CloseDragGate mCloseDragGate;
    @Nullable private VelocityTracker mVelocityTracker;
    /** False when the gesture started outside the plane rect, which must fall through untouched. */
    private boolean mTracking;
    /** True for the whole stream when {@code ACTION_DOWN} landed on content that owns it. */
    private boolean mDeferToContent;
    private float mDownRawY;

    public AppDrawerPlaneView(@NonNull Context context) {
        super(context);
        mTouchSlopPx = ViewConfiguration.get(context).getScaledTouchSlop();
        mRim = new GlassRimRenderer(getResources().getDisplayMetrics().density);
        setWillNotDraw(false);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                // Round INWARD, exactly as the palette does: the frame is fractional, and a clip
                // half a pixel outside the painted slab lets the bright frosted wallpaper leak past
                // the glass as a hairline along the edge.
                outline.setRoundRect((int) Math.ceil(mFrame.left), (int) Math.ceil(mFrame.top),
                    (int) Math.floor(mFrame.right), (int) Math.floor(mFrame.bottom), mRadiusPx);
            }
        });
        mContent = new FrameLayout(context);
        // B-1 ships the plane empty. The grid, search pill and A-Z rope are B-2/B-3 and go in here;
        // they inherit the outline clip, so they need no rounding of their own.
        addView(mContent, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        mContent.setAlpha(0f);
    }

    public void setCallbacks(@Nullable Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    /**
     * Installs the content's first-refusal on {@code ACTION_DOWN}. A null gate is the B-1 plane
     * exactly: every point on the plane belongs to its own close drag.
     */
    public void setCloseDragGate(@Nullable CloseDragGate gate) {
        mCloseDragGate = gate;
    }

    /** The (empty in B-1) content frame the app grid will live in. */
    @NonNull
    public FrameLayout getContentHost() {
        return mContent;
    }

    /**
     * Lays the content out for the drawer's <em>open</em> rectangle, once per open.
     *
     * <p>Deliberately not a per-frame call. The content is measured for where the plane is going,
     * not where it currently is, and the growing outline clip is what reveals it — re-laying a grid
     * out on every frame of a 1:1 drag is the measure pass this whole transition is built to avoid.
     *
     * <p>Applied as the content's own layout params rather than as padding on the plane, so it does
     * not depend on the plane having been measured yet: {@code prepareOverlay()} runs on the frame
     * the plane is added in, where {@code getWidth()} is still zero.
     */
    public void setContentInsets(@NonNull Frame openRect) {
        LayoutParams params = (LayoutParams) mContent.getLayoutParams();
        int left = Math.round(openRect.left);
        int top = Math.round(openRect.top);
        int width = Math.max(0, Math.round(openRect.width()));
        int height = Math.max(0, Math.round(openRect.height()));
        if (params.leftMargin == left && params.topMargin == top && params.width == width
            && params.height == height) return;
        params.leftMargin = left;
        params.topMargin = top;
        params.width = width;
        params.height = height;
        mContent.setLayoutParams(params);
    }

    /** The rectangle the plane currently paints, in host coordinates. */
    @NonNull
    public Frame getFrame() {
        return mFrame;
    }

    /**
     * Rebuilds the glass material. Called on every open rather than once, because opacity, grain,
     * theme and the wallpaper-derived accent can all have changed since the last one.
     */
    public void applyGlassMaterial(int baseColor, int accentColor, float opacity, int grainPercent) {
        mGlassSurface = DockGlassRendering.createGlassSurface(getResources(), baseColor,
            accentColor, opacity, grainPercent, true);
        invalidate();
    }

    /**
     * The only per-frame entry point: the plane rectangle and its corner radius, in host
     * coordinates. No allocation, no layout — a bounds update, an outline invalidation and a draw.
     */
    public void setFrame(@NonNull Frame frame, float radiusPx, float progress) {
        mFrame = frame;
        mRadiusPx = Math.max(0f, radiusPx);
        mProgress = Math.max(0f, Math.min(1f, progress));
        // Outward: the slab must reach under the inward-rounded clip, or the two disagree by a
        // subpixel and the seam shows as a lighter line.
        mSlabBounds.set((int) Math.floor(frame.left), (int) Math.floor(frame.top),
            (int) Math.ceil(frame.right), (int) Math.ceil(frame.bottom));
        float contentAlpha = AppDrawerTransitionGeometry.ramp(progress,
            CONTENT_FADE_START, CONTENT_FADE_END);
        mContent.setAlpha(contentAlpha);
        // An alpha-0 view still receives touches. Left VISIBLE, a closed drawer's full-screen grid
        // sits invisible over the terminal and eats every tap — silently, which is what makes it the
        // worst regression on this surface. The flip is the second of the two guards; the first is
        // the controller clearing setInteractive(false) on close.
        mContent.setVisibility(contentAlpha <= 0.01f ? INVISIBLE : VISIBLE);
        invalidateOutline();
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        Drawable surface = mGlassSurface;
        if (surface == null || mSlabBounds.width() <= 0 || mSlabBounds.height() <= 0) return;
        surface.setBounds(mSlabBounds);
        surface.draw(canvas);
        // Rim over the slab: shimmer while the transition is live, settled hairline at rest.
        // Drawn on the outward-rounded slab bounds so the inward outline clip trims it flush.
        float shimmerPhase = mProgress < 1f ? mProgress : -1f;
        mRim.draw(canvas, mSlabBounds.left, mSlabBounds.top, mSlabBounds.right,
            mSlabBounds.bottom, mRadiusPx, shimmerPhase, 0.4f + 0.6f * mProgress);
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN
            && !containsInFrame(ev.getX(), ev.getY())) {
            // clipToOutline clips pixels, not hit testing. The content remains laid out for the
            // full open rectangle while search shortens the painted plane above the keyboard; if
            // this DOWN reached super, that invisible part of the grid would become the touch
            // target before the lower accessory sibling had a chance to dispatch to its keys.
            // Reject only at DOWN so ownership can never change midway through a nested scroll.
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Children (B-2's grid) see the stream until the downward drag actually claims it, at which
        // point the platform sends them the ACTION_CANCEL for us.
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginTracking(ev);
                return false;
            case MotionEvent.ACTION_MOVE:
                return trackMove(ev);
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                endTracking(ev.getActionMasked() == MotionEvent.ACTION_UP);
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Outside the plane rect the touch is not ours: while the plane is still small the
                // rest of the screen is the terminal, and swallowing there would deaden it.
                return beginTracking(ev);
            case MotionEvent.ACTION_MOVE:
                trackMove(ev);
                return mTracking;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean tracking = mTracking;
                endTracking(ev.getActionMasked() == MotionEvent.ACTION_UP);
                return tracking;
            default:
                return mTracking;
        }
    }

    private boolean beginTracking(@NonNull MotionEvent ev) {
        float x = ev.getX();
        float y = ev.getY();
        mTracking = containsInFrame(x, y);
        if (!mTracking) return false;
        CloseDragGate gate = mCloseDragGate;
        // Sampled once, at DOWN, and never re-read: ownership of a stream cannot change halfway
        // through it, or the arbiter would begin a drag the content is already driving.
        mDeferToContent = gate != null && gate.ownsPoint(x, y);
        if (mDeferToContent) return false;
        mDownRawY = ev.getRawY();
        mArbiter.begin(ev.getRawX(), ev.getRawY(), PLANE_ELIGIBILITY);
        if (mVelocityTracker == null) mVelocityTracker = VelocityTracker.obtain();
        mVelocityTracker.clear();
        mVelocityTracker.addMovement(ev);
        return true;
    }

    private boolean containsInFrame(float x, float y) {
        return x >= mFrame.left && x <= mFrame.right && y >= mFrame.top && y <= mFrame.bottom;
    }

    /** @return true once the close drag owns the stream. */
    private boolean trackMove(@NonNull MotionEvent ev) {
        if (mDeferToContent) return false;
        if (!mTracking) return false;
        if (mVelocityTracker != null) mVelocityTracker.addMovement(ev);
        boolean wasDragging = mArbiter.isDrawerDrag();
        AppDrawerGestureArbiter.Claim claim =
            mArbiter.evaluate(ev.getRawX(), ev.getRawY(), mTouchSlopPx);
        if (claim != AppDrawerGestureArbiter.Claim.DRAWER_DRAG) return false;
        if (!wasDragging && mCallbacks != null) mCallbacks.onPlaneDragBegin(mDownRawY);
        if (mCallbacks != null) mCallbacks.onPlaneDrag(ev.getRawY());
        return true;
    }

    private void endTracking(boolean released) {
        boolean dragging = mArbiter.isDrawerDrag();
        float velocity = 0f;
        if (dragging && released && mVelocityTracker != null) {
            mVelocityTracker.computeCurrentVelocity(1000);
            velocity = mVelocityTracker.getYVelocity();
        }
        mArbiter.reset();
        mTracking = false;
        mDeferToContent = false;
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
        if (!dragging || mCallbacks == null) return;
        if (released) {
            mCallbacks.onPlaneDragEnd(velocity);
        } else {
            mCallbacks.onPlaneDragCancel();
        }
    }

    // ------------------------------------------------------ close drag driven by the content

    /**
     * The content's close drag, forwarded into the plane's own {@link Callbacks}.
     *
     * <p>One path from gesture to controller is the point: the grid's nested-scroll claim and the
     * plane's arbiter claim are two ways of deciding, not two ways of reporting. The guard is the
     * seam between them — if the arbiter is already driving this stream then the down point was
     * chrome, the content is not supposed to be claiming anything, and a second begin for one
     * gesture would capture the drawer's geometry twice.
     */
    public void beginCloseDragFromContent(float downRawY) {
        if (mArbiter.isDrawerDrag() || mCallbacks == null) return;
        mCallbacks.onPlaneDragBegin(downRawY);
    }

    public void updateCloseDragFromContent(float rawY) {
        if (mArbiter.isDrawerDrag() || mCallbacks == null) return;
        mCallbacks.onPlaneDrag(rawY);
    }

    /** @param velocityPxPerSec vertical release velocity, positive downwards */
    public void endCloseDragFromContent(float velocityPxPerSec) {
        if (mArbiter.isDrawerDrag() || mCallbacks == null) return;
        mCallbacks.onPlaneDragEnd(velocityPxPerSec);
    }

    public void cancelCloseDragFromContent() {
        if (mArbiter.isDrawerDrag() || mCallbacks == null) return;
        mCallbacks.onPlaneDragCancel();
    }
}
