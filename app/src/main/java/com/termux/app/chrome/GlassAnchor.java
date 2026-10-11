package com.termux.app.chrome;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.wall.PaneWallLayout;

/**
 * Where a glass surface is on screen, for aiming the shared pre-blurred frame at it.
 *
 * <p>Every surface that samples the frame asks the same question on every draw — <em>where am
 * I over the wallpaper right now?</em> — and the two answers the framework offers are each wrong
 * for some surface. {@code getLocationOnScreen} folds in every transform on the way up, so a
 * surface on a pane the finger is pressing would re-aim to the pressed position on whichever
 * draw happened to land mid-press, then stay there once the spring settled, because nothing
 * re-draws it when a transform alone moves. The laid-out position ignores transforms entirely,
 * so a surface carried by a transform the launcher itself owns — a page sliding along the wall,
 * the dock and keyboard travelling between two places' states — would show the wallpaper of
 * where it was laid out rather than where it is.</p>
 *
 * <p>So there are three anchors, chosen per surface by what moves it:</p>
 * <ul>
 *   <li>{@link #place}: glass on a wall page — the pane slabs, the Widgets and Display pages'
 *       glass and corner arcs, the corner tab. The wallpaper in it is fixed to the screen, lined
 *       up with the wallpaper the launcher draws behind the wall, whatever the page does: slide,
 *       sink under a held border, tip as a plank. The page's whole transform — its translation,
 *       its scale about its centre, its {@code rotationY} under the camera — is composed into the
 *       view's matrix to the screen and the draw aims past it ({@link Aim}), so only the page's
 *       own layers (tint, veil, grain, rim, text) move, sink and tilt with it. A pane's own press
 *       is still left out: it is a finger resting on the slab, and the frost rides it.</li>
 *   <li>{@link #layout}: the laid-out position, plus the one or two transforms the caller
 *       names — the wall page's slide, read off the wall, and a lift the caller supplies. The
 *       accessory stack and the bands around the wall use this.</li>
 *   <li>{@link #screen}: the framework's transform-inclusive position, for a surface nothing
 *       tilts and whose owner re-applies it whenever it moves — the top bars, the palette, the
 *       sheet, the drawer plane, the under-pill strip.</li>
 * </ul>
 *
 * <p>Transforms above the wall are left out of all three: the Appearance editor scales the
 * container the wallpaper backdrop shares with the wall, so glass and backdrop already move
 * together there, and composing that scale would pull the glass off the backdrop beside it.</p>
 */
public final class GlassAnchor {

    private GlassAnchor() {}

    /** Answers a surface's sampling origin on screen, in px. Read on every draw; never allocates. */
    public interface Origin {
        void originOnScreen(@NonNull int[] out);
    }

    /** A vertical lift the anchor adds on every read, in px; see {@link #layout}. */
    public interface Lift {
        float px();
    }

    /**
     * {@code view}'s position on screen as laid out, ignoring every transform on the way up but
     * one: the wall page's slide, returned separately.
     *
     * <p>The laid-out half of the answer: the veil a slab is measured for, and the backdrop's own
     * rect, are asked for where a view is laid out, not where a motion has carried it. Glass
     * that samples the wallpaper on a wall page aims through {@link #place} instead. Everything
     * on the way up is ignored but the page's slide and a motion the owner published with
     * {@link #setMotion}, which is added.</p>
     *
     * @param rootScratch two ints the root's own screen position is read into
     * @return the wall page's translation on the way up, in px; 0 off the wall
     */
    public static float layoutOriginOnScreen(@NonNull View view, @NonNull int[] out,
                                             @NonNull int[] rootScratch) {
        float x = 0f;
        float y = 0f;
        float slideX = 0f;
        View current = view;
        while (true) {
            x += current.getLeft();
            y += current.getTop();
            Object motion = current.getTag(R.id.glass_anchor_motion);
            if (motion instanceof Motion) {
                x += ((Motion) motion).x;
                y += ((Motion) motion).y;
            }
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) break;
            View parentView = (View) parent;
            if (parentView instanceof PaneWallLayout) slideX += current.getTranslationX();
            x -= parentView.getScrollX();
            y -= parentView.getScrollY();
            current = parentView;
        }
        // `current` is now the root of this hierarchy; it carries no transform of its own, so
        // asking the framework for its screen position is safe.
        current.getLocationOnScreen(rootScratch);
        out[0] = Math.round(x) + rootScratch[0];
        out[1] = Math.round(y) + rootScratch[1];
        return slideX;
    }

    /**
     * Where a view on the wall stands over the wallpaper right now; filled by {@link #place},
     * reused across draws so a draw never allocates.
     */
    public static final class Placement {
        /**
         * The view's own px to screen px, through the wall page's translation, scale and tilt
         * and any motion published on the way; floats end to end, so a slide never rounds.
         */
        @NonNull public final Matrix toScreen = new Matrix();
        /**
         * The brightness, of 255 per channel, the wall's sink dim leaves the page this view is
         * on: 255 off the wall and on every page the sink is not dimming. The page's layer dims
         * everything on it, so glass lifts the wallpaper it draws by as much in advance
         * ({@link GlassFilters#undimmed}): the picture comes out of the dim as it is behind it.
         */
        public int keptBrightness = 0xFF;
        @NonNull private final int[] mRoot = new int[2];
    }

    /**
     * Fill {@code out} for {@code view}: its matrix to the screen and the dim its page wears.
     *
     * <p>The matrix composes, from the view up, each view's laid-out position less its parent's
     * scroll, a motion its owner published ({@link #setMotion}), and the full transform of the
     * one view a {@link PaneWallLayout} holds — the page. No other view's transform is in it:
     * a pane frame's press stays out, as it always has, and so does anything above the wall.
     * Walks the tree without allocating.</p>
     */
    public static void place(@NonNull View view, @NonNull Placement out) {
        Matrix matrix = out.toScreen;
        matrix.reset();
        out.keptBrightness = 0xFF;
        View current = view;
        while (true) {
            ViewParent parent = current.getParent();
            if (parent instanceof PaneWallLayout) {
                Matrix page = current.getMatrix();
                if (!page.isIdentity()) matrix.postConcat(page);
                out.keptBrightness = ((PaneWallLayout) parent).keptBrightness(current);
            }
            float x = current.getLeft();
            float y = current.getTop();
            Object motion = current.getTag(R.id.glass_anchor_motion);
            if (motion instanceof Motion) {
                x += ((Motion) motion).x;
                y += ((Motion) motion).y;
            }
            if (!(parent instanceof View)) {
                matrix.postTranslate(x, y);
                break;
            }
            View parentView = (View) parent;
            matrix.postTranslate(x - parentView.getScrollX(), y - parentView.getScrollY());
            current = parentView;
        }
        // `current` is now the root of this hierarchy, as in layoutOriginOnScreen.
        current.getLocationOnScreen(out.mRoot);
        matrix.postTranslate(out.mRoot[0], out.mRoot[1]);
    }

    /**
     * The shared frame aimed at a placed view so it shows the wallpaper that is on the screen
     * under each of its pixels.
     *
     * <p>Split in two so a page at rest draws exactly as it always has: {@link #scaleOffset} is
     * the old aim — frame px scaled to screen px and moved to the view's origin on screen,
     * {@code SharedFrameDrawable#aim}'s four numbers — and {@link #local} carries whatever else
     * the page's transform does, from the view's own px to px from that origin. Identity while
     * the page only slides; a scale, and a perspective under the plank's tilt, otherwise.</p>
     */
    public static final class Aim {
        /** {@code {scaleX, scaleY, offsetX, offsetY}}: frame px to px from the view's origin. */
        @NonNull public final float[] scaleOffset = new float[4];
        /** The view's own px to px from its origin on screen; identity unless scaled or tilted. */
        @NonNull public final Matrix local = new Matrix();
        @NonNull private final Matrix mInverse = new Matrix();
        @NonNull private final float[] mOrigin = new float[2];

        /**
         * Aim for a view {@code toScreen} places, with the frame captured over {@code frameRect}
         * on screen at {@code frameWidth} by {@code frameHeight} px, and the wallpaper panned
         * {@code parallaxPx} to the left under it.
         */
        public void set(@NonNull Matrix toScreen, @NonNull Rect frameRect, int frameWidth,
                        int frameHeight, float parallaxPx) {
            mOrigin[0] = 0f;
            mOrigin[1] = 0f;
            toScreen.mapPoints(mOrigin);
            local.set(toScreen);
            local.postTranslate(-mOrigin[0], -mOrigin[1]);
            scaleOffset[0] = frameRect.width() / (float) Math.max(1, frameWidth);
            scaleOffset[1] = frameRect.height() / (float) Math.max(1, frameHeight);
            scaleOffset[2] = frameRect.left - mOrigin[0] - parallaxPx;
            scaleOffset[3] = frameRect.top - mOrigin[1];
        }

        /**
         * The plain draw's shader matrix, frame px to the view's own px: the old scale and
         * translate, then {@link #local} undone. A page turned edge-on has no inverse; it draws
         * with the old aim for that frame rather than with none.
         */
        public void shaderMatrix(@NonNull Matrix out) {
            out.setScale(scaleOffset[0], scaleOffset[1]);
            out.postTranslate(scaleOffset[2], scaleOffset[3]);
            if (!local.isIdentity() && local.invert(mInverse)) out.postConcat(mInverse);
        }
    }

    /** An offset a view's owner publishes while it animates the view on purpose; see {@link #setMotion}. */
    private static final class Motion {
        float x;
        float y;
    }

    /**
     * Declare that {@code view} is being moved on purpose, {@code (dx, dy)} px away from where it
     * is laid out, so glass inside it samples the wallpaper it is really over. This is the one
     * per-view transform the anchor follows besides the wall page's, and it is explicit rather
     * than read off the view's translation because the plank's press moves the same frame by the
     * same property and that must stay out of the anchor. Whoever publishes it (a FLIP move, an
     * entry) re-aims the glass on every frame and calls {@link #clearMotion} when done.
     */
    public static void setMotion(@NonNull View view, float dx, float dy) {
        Object tag = view.getTag(R.id.glass_anchor_motion);
        Motion motion;
        if (tag instanceof Motion) {
            motion = (Motion) tag;
        } else {
            motion = new Motion();
            view.setTag(R.id.glass_anchor_motion, motion);
        }
        motion.x = dx;
        motion.y = dy;
    }

    /** Withdraw {@link #setMotion}: the anchor is the laid-out position again. */
    public static void clearMotion(@NonNull View view) {
        view.setTag(R.id.glass_anchor_motion, null);
    }

    /**
     * The laid-out anchor: {@link #layoutOriginOnScreen} with the page's slide folded into x and
     * {@code lift} added to y on every read. The accessory stack passes the travel translation it
     * writes itself, so the dock and the keyboard sample the wallpaper they are really over while
     * they slide between two places' states, and nothing else that transforms the stack — the
     * plank's press — moves the frost.
     */
    @NonNull
    public static Origin layout(@NonNull View view, @NonNull Lift lift) {
        final int[] root = new int[2];
        return out -> {
            float slideX = layoutOriginOnScreen(view, out, root);
            out[0] += Math.round(slideX);
            out[1] += Math.round(lift.px());
        };
    }

    /** The transform-inclusive anchor: exactly where the framework says {@code view} is. */
    @NonNull
    public static Origin screen(@NonNull View view) {
        return view::getLocationOnScreen;
    }
}
