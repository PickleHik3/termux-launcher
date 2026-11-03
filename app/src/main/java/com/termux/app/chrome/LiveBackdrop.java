package com.termux.app.chrome;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;

/**
 * Live blur's recorder: for each glass surface it is given, everything the window draws
 * <em>beneath</em> that surface, in Android's draw order, and never the surface itself or anything
 * above it. So no glass needs hiding while it is recorded, and glass on glass falls out: what is
 * beneath one glass may include another glass.
 *
 * <p>{@link #recordBelow} walks from the surface up to the root it was made with
 * ({@code terminal_root_container}). At each ancestor it draws the children that draw before the
 * one on the path, with their transforms, as that ancestor's own draw would; the launcher-drawn
 * wallpaper ({@link WallpaperBackdropView}) is simply the root's bottom child. Drawing a child into
 * a {@link RecordingCanvas} draws its cached display list: nothing is repainted. An ancestor can
 * only do that for its children from inside (it is {@code ViewGroup.drawChild}, protected), so the
 * containers on a live surface's path implement {@link Container}; an ancestor that does not
 * contributes nothing beneath.</p>
 *
 * <p><b>When.</b> Surfaces are re-recorded in a pre-draw of a frame that is already being drawn,
 * and only when what is beneath them moved: the views drawn beneath (a sibling shown, hidden or
 * raised) or where the surface stands among them. Anything that merely redraws beneath (a page
 * sliding inside its parent, a terminal printing) reaches the picture through the node it
 * references, without a new recording. Recording never invalidates a view or schedules a frame:
 * at rest nothing draws, so nothing records and nothing blurs.</p>
 *
 * <p><b>Memory.</b> At most {@link #MAX_LIVE_SURFACES} pictures, each one blur layer of at most
 * {@link #MAX_LAYER_BYTES}: {@link #BUDGET_BYTES} in all. Pictures are released when Live goes
 * off, when their surface hides ({@link #detach}) and under memory pressure ({@link #releaseAll}).</p>
 */
public final class LiveBackdrop implements ViewTreeObserver.OnPreDrawListener {

    /** The dock plus the widgets on the visible page. */
    public static final int MAX_LIVE_SURFACES = 8;

    /** The panel the budget is sized for: 1440 x 3200, the largest phone screen in common use. */
    static final int BUDGET_SCREEN_WIDTH = 1440;
    static final int BUDGET_SCREEN_HEIGHT = 3200;

    /** One picture's blur layer at most: a whole budget screen at a quarter scale, 4 bytes a pixel. */
    public static final long MAX_LAYER_BYTES = layerBytes(BUDGET_SCREEN_WIDTH, BUDGET_SCREEN_HEIGHT);

    /** Every live layer together: about 8.8 MiB. */
    public static final long BUDGET_BYTES = MAX_LIVE_SURFACES * MAX_LAYER_BYTES;

    /**
     * A container on a live surface's path: draws one of its own children, now, into another
     * canvas, exactly as its own draw does (its cached display list, at its transform).
     * Implement it as {@code drawChild(canvas, child, getDrawingTime())}.
     */
    public interface Container {
        void drawChildForBackdrop(@NonNull Canvas canvas, @NonNull View child);
    }

    @NonNull private final ViewGroup mRoot;
    @NonNull private final ArrayList<LivePicture> mPictures = new ArrayList<>(MAX_LIVE_SURFACES);
    @Nullable private ViewTreeObserver mObserver;

    // Scratch for one walk; the walk runs on the UI thread only.
    @NonNull private final ArrayList<View> mPath = new ArrayList<>();
    @NonNull private final ArrayList<View> mBeneath = new ArrayList<>();
    @NonNull private final Matrix mMatrix = new Matrix();
    @NonNull private final Matrix mInverse = new Matrix();
    @NonNull private int[] mPreorder = new int[16];
    @NonNull private float[] mZ = new float[16];
    @NonNull private boolean[] mDrawn = new boolean[16];
    @NonNull private int[] mOut = new int[16];
    @NonNull private int[] mOrder = new int[16];

    public LiveBackdrop(@NonNull ViewGroup root) {
        mRoot = root;
    }

    /** Bytes of a quarter-scale RGBA layer over a {@code width} x {@code height} recording. */
    public static long layerBytes(int width, int height) {
        long w = Math.max(1, (width + LivePicture.SCALE - 1) / LivePicture.SCALE);
        long h = Math.max(1, (height + LivePicture.SCALE - 1) / LivePicture.SCALE);
        return w * h * 4L;
    }

    /**
     * The live picture for {@code surface}, made if it has none; null when {@link #MAX_LIVE_SURFACES}
     * are already live (the surface keeps its stored frame).
     */
    @Nullable
    public LivePicture attach(@NonNull View surface, float blurRadiusPx) {
        return attach(surface, blurRadiusPx, false);
    }

    /**
     * {@link #attach(View, float)} for a panel ({@link PanelBackdrop}): {@code blurRadiusPx} is the
     * panel's {@code RealtimeBlurView} radius when {@code panelRadius}, calibrated by
     * {@link LivePicture#panelQuarterRadius}, so the panel keeps the strength it had.
     */
    @Nullable
    public LivePicture attach(@NonNull View surface, float blurRadiusPx, boolean panelRadius) {
        LivePicture picture = find(surface);
        if (picture == null) {
            if (mPictures.size() >= MAX_LIVE_SURFACES) return null;
            picture = new LivePicture(surface, blurRadiusPx, panelRadius);
            mPictures.add(picture);
            listen(true);
        } else {
            picture.setBlurRadiusPx(blurRadiusPx);
        }
        return picture;
    }

    /** Drops {@code surface}'s picture and its layer. */
    public void detach(@NonNull View surface) {
        LivePicture picture = find(surface);
        if (picture == null) return;
        picture.release();
        mPictures.remove(picture);
        if (mPictures.isEmpty()) listen(false);
    }

    /** Drops every picture and every layer: Live went off, or memory is short. */
    public void releaseAll() {
        for (int i = 0; i < mPictures.size(); i++) mPictures.get(i).release();
        mPictures.clear();
        listen(false);
    }

    /** How many surfaces are live now. */
    public int size() {
        return mPictures.size();
    }

    /** Whether {@code drawable} is a live picture this recorder hands out. */
    public boolean shows(@Nullable Drawable drawable) {
        if (drawable == null) return false;
        for (int i = 0; i < mPictures.size(); i++) {
            if (mPictures.get(i).owns(drawable)) return true;
        }
        return false;
    }

    @Nullable
    private LivePicture find(@NonNull View surface) {
        for (int i = 0; i < mPictures.size(); i++) {
            if (mPictures.get(i).mSurface == surface) return mPictures.get(i);
        }
        return null;
    }

    private void listen(boolean on) {
        if (on) {
            if (mObserver != null && mObserver.isAlive()) return;
            mObserver = mRoot.getViewTreeObserver();
            mObserver.addOnPreDrawListener(this);
        } else {
            if (mObserver != null && mObserver.isAlive()) mObserver.removeOnPreDrawListener(this);
            // A dead observer was merged into the live one when the root attached.
            ViewTreeObserver current = mRoot.getViewTreeObserver();
            if (current != mObserver && current.isAlive()) current.removeOnPreDrawListener(this);
            mObserver = null;
        }
    }

    /**
     * Runs inside a frame already being drawn: re-records each shown surface whose beneath moved.
     * Calls nothing that invalidates or schedules a frame.
     */
    @Override
    public boolean onPreDraw() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        for (int i = 0; i < mPictures.size(); i++) {
            LivePicture picture = mPictures.get(i);
            View surface = picture.mSurface;
            if (!picture.held()) {
                // Its view put up another picture: nothing draws this one any more.
                picture.release();
                mPictures.remove(i--);
                continue;
            }
            if (!surface.isShown() || surface.getWidth() <= 0 || surface.getHeight() <= 0) {
                // Hidden: no layer while it is; the next shown frame records it again.
                picture.trim();
                continue;
            }
            if (!collect(surface)) continue;
            int margin = picture.marginPx();
            if (picture.mRecordedWidth == surface.getWidth()
                && picture.mRecordedHeight == surface.getHeight()
                && picture.mRecordedMargin == margin
                && picture.mRecordedMatrix.equals(mMatrix)
                && picture.mRecordedBeneath.equals(mBeneath)) {
                continue;
            }
            int width = surface.getWidth() + 2 * margin;
            int height = surface.getHeight() + 2 * margin;
            if (!record(picture.recordNode(width, height), width, height, margin)) continue;
            picture.mRecordedBeneath.clear();
            picture.mRecordedBeneath.addAll(mBeneath);
            picture.mRecordedMatrix.set(mMatrix);
            picture.mRecordedWidth = surface.getWidth();
            picture.mRecordedHeight = surface.getHeight();
            picture.mRecordedMargin = margin;
        }
        if (mPictures.isEmpty()) listen(false);
        return true;
    }

    /**
     * Records everything drawn beneath {@code surface} into {@code out}, in the surface's own
     * coordinates offset by {@code marginPx} on each side. False, with {@code out} untouched, when
     * the surface is not inside the root or its transform cannot be undone.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public boolean recordBelow(@NonNull View surface, @NonNull RenderNode out, int marginPx) {
        if (!collect(surface)) return false;
        int width = Math.max(1, surface.getWidth() + 2 * marginPx);
        int height = Math.max(1, surface.getHeight() + 2 * marginPx);
        out.setPosition(0, 0, width, height);
        return record(out, width, height, marginPx);
    }

    /** The root this recorder records within. */
    @NonNull
    public ViewGroup root() {
        return mRoot;
    }

    /**
     * Records the whole root, as the window draws it, beneath {@code surface} in another window
     * (a popup menu): everything in the root is beneath a window above it. In {@code surface}'s
     * coordinates offset by {@code marginPx}, placed by the two views' screen positions. False,
     * with {@code out} untouched, when the root cannot draw its children ({@link Container}) or
     * either view is not on screen.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public boolean recordWindowBeneath(@NonNull View surface, @NonNull RenderNode out, int marginPx) {
        if (!(mRoot instanceof Container) || !mRoot.isAttachedToWindow()
            || !surface.isAttachedToWindow()) return false;
        mRoot.getLocationOnScreen(mLocation);
        int rootX = mLocation[0];
        int rootY = mLocation[1];
        surface.getLocationOnScreen(mLocation);
        mBeneath.clear();
        collectLevel(mRoot, null, mBeneath);
        int width = Math.max(1, surface.getWidth() + 2 * marginPx);
        int height = Math.max(1, surface.getHeight() + 2 * marginPx);
        out.setPosition(0, 0, width, height);
        RecordingCanvas canvas = out.beginRecording(width, height);
        try {
            canvas.translate(marginPx + rootX - mLocation[0] - mRoot.getScrollX(),
                marginPx + rootY - mLocation[1] - mRoot.getScrollY());
            canvas.enableZ();
            for (int i = 0; i < mBeneath.size(); i++) {
                ((Container) mRoot).drawChildForBackdrop(canvas, mBeneath.get(i));
            }
            canvas.disableZ();
        } finally {
            out.endRecording();
        }
        mBeneath.clear();
        return true;
    }

    @NonNull private final int[] mLocation = new int[2];

    /**
     * Fills {@link #mPath} (surface first, root last), {@link #mBeneath} (per level from the root
     * down, each level's views bottom first, then a null) and {@link #mMatrix} (surface to root).
     */
    private boolean collect(@NonNull View surface) {
        mPath.clear();
        mBeneath.clear();
        View view = surface;
        while (view != mRoot) {
            mPath.add(view);
            ViewParent parent = view.getParent();
            if (!(parent instanceof ViewGroup)) return false;
            view = (View) parent;
        }
        mPath.add(mRoot);
        mMatrix.reset();
        for (int i = mPath.size() - 1; i >= 1; i--) {
            ViewGroup parent = (ViewGroup) mPath.get(i);
            View child = mPath.get(i - 1);
            if (parent instanceof Container) collectLevel(parent, child, mBeneath);
            mBeneath.add(null);
            mMatrix.preTranslate(child.getLeft() - parent.getScrollX(),
                child.getTop() - parent.getScrollY());
            Matrix own = child.getMatrix();
            if (!own.isIdentity()) mMatrix.preConcat(own);
        }
        return true;
    }

    /**
     * {@code parent}'s children drawn before {@code pathChild}, bottom first, into {@code out}; every
     * child it draws when {@code pathChild} is null.
     */
    @VisibleForTesting
    void collectLevel(@NonNull ViewGroup parent, @Nullable View pathChild, @NonNull ArrayList<View> out) {
        int count = parent.getChildCount();
        if (mPreorder.length < count) {
            mPreorder = new int[count];
            mZ = new float[count];
            mDrawn = new boolean[count];
            mOut = new int[count];
            mOrder = new int[count];
        }
        boolean customOrder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        for (int i = 0; i < count; i++) {
            mPreorder[i] = customOrder ? parent.getChildDrawingOrder(i) : i;
            View child = parent.getChildAt(i);
            mZ[i] = child.getZ();
            // ViewGroup.dispatchDraw's own test: shown, or animating out.
            mDrawn[i] = child.getVisibility() == View.VISIBLE || child.getAnimation() != null;
        }
        int n = beneath(count, mPreorder, mZ, mDrawn,
            pathChild == null ? EVERY_CHILD : parent.indexOfChild(pathChild), mOrder, mOut);
        for (int i = 0; i < n; i++) out.add(parent.getChildAt(mOut[i]));
    }

    /**
     * Which children draw beneath the one at {@code pathIndex}, bottom first, as
     * {@code ViewGroup}'s draw orders them: the drawing order ({@code preorder}, container indices
     * by drawing position), then a stable sort by Z, so a child raised by elevation draws after
     * every lower one whatever its place. Only children that draw at all ({@code drawn}) are
     * written; the path child and everything after it never are. With {@link #EVERY_CHILD} for
     * {@code pathIndex}, every child that draws is written. Pure.
     *
     * @param order scratch of at least {@code count}, for the sorted drawing order
     * @return how many container indices were written to {@code out}; 0 when the path child is
     *         not among them
     */
    @VisibleForTesting
    static int beneath(int count, @NonNull int[] preorder, @NonNull float[] z,
                       @NonNull boolean[] drawn, int pathIndex, @NonNull int[] order,
                       @NonNull int[] out) {
        int sorted = 0;
        for (int i = 0; i < count; i++) {
            int index = preorder[i];
            int at = sorted;
            while (at > 0 && z[order[at - 1]] > z[index]) at--;
            System.arraycopy(order, at, order, at + 1, sorted - at);
            order[at] = index;
            sorted++;
        }
        int written = 0;
        for (int i = 0; i < sorted; i++) {
            int index = order[i];
            if (index == pathIndex) return written;
            if (drawn[index]) out[written++] = index;
        }
        return pathIndex == EVERY_CHILD ? written : 0;
    }

    /** {@link #beneath}'s path index for "no path child": every child that draws. */
    static final int EVERY_CHILD = Integer.MIN_VALUE;

    /** Draws {@link #mBeneath} into {@code out} along {@link #mPath}, as {@link #collect} found them. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private boolean record(@NonNull RenderNode out, int width, int height, int marginPx) {
        if (!mMatrix.invert(mInverse)) return false;
        RecordingCanvas canvas = out.beginRecording(width, height);
        try {
            canvas.translate(marginPx, marginPx);
            canvas.concat(mInverse);
            int next = 0;
            for (int i = mPath.size() - 1; i >= 1; i--) {
                ViewGroup parent = (ViewGroup) mPath.get(i);
                View child = mPath.get(i - 1);
                int save = canvas.save();
                canvas.translate(-parent.getScrollX(), -parent.getScrollY());
                // Shadows and Z order among the views drawn here, as the parent's own draw has.
                canvas.enableZ();
                for (View below = mBeneath.get(next++); below != null; below = mBeneath.get(next++)) {
                    ((Container) parent).drawChildForBackdrop(canvas, below);
                }
                canvas.disableZ();
                canvas.restoreToCount(save);
                canvas.translate(child.getLeft() - parent.getScrollX(),
                    child.getTop() - parent.getScrollY());
                Matrix own = child.getMatrix();
                if (!own.isIdentity()) canvas.concat(own);
            }
        } finally {
            out.endRecording();
        }
        return true;
    }
}
