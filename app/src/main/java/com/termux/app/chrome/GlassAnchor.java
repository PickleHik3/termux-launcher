package com.termux.app.chrome;

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
 * surface on a plank the finger is tilting would re-aim to the tilted position on whichever
 * draw happened to land mid-press, then stay there once the spring settled, because nothing
 * re-draws it when a transform alone moves. The laid-out position ignores transforms entirely,
 * so a surface carried by a transform the launcher itself owns — a page sliding along the wall,
 * the dock and keyboard travelling between two places' states — would show the wallpaper of
 * where it was laid out rather than where it is.</p>
 *
 * <p>So there are two anchors, chosen per surface by what moves it:</p>
 * <ul>
 *   <li>{@link #layout}: the laid-out position, plus the one or two transforms the caller
 *       names — the wall page's slide, read off the wall, and a lift the caller supplies. The
 *       pane slabs and the accessory stack use this; a plank's tilt is deliberately not in it,
 *       so the frost rides the tilt exactly as it always has.</li>
 *   <li>{@link #screen}: the framework's transform-inclusive position, for a surface nothing
 *       tilts and whose owner re-applies it whenever it moves — the top bars, the palette, the
 *       sheet, the drawer plane, the under-pill strip.</li>
 * </ul>
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
     * <p>A place sliding across the wall is a page travelling over a wallpaper that stays where
     * it is (or pans a fraction of the way, with parallax), and glass shows what is behind it, so
     * the frost has to stay glued to the wallpaper rather than ride along with the page. That one
     * transform — the translation the wall puts on its page — is read off the page and handed
     * back, so a draw can aim past it. Everything else on the way up is ignored: the plank tilts
     * and slides a pane frame under a finger, and pinning the frost to those baked a press into the matrix. The exception is a motion the
     * owner published with {@link #setMotion}, which is added.</p>
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

    /** An offset a view's owner publishes while it animates the view on purpose; see {@link #setMotion}. */
    private static final class Motion {
        float x;
        float y;
    }

    /**
     * Declare that {@code view} is being moved on purpose, {@code (dx, dy)} px away from where it
     * is laid out, so glass inside it samples the wallpaper it is really over. This is the one
     * per-view transform the anchor follows besides the wall page's slide, and it is explicit
     * rather than read off the view's translation because the plank's press moves the same frame
     * by the same property and that must stay out of the anchor. Whoever publishes it (a FLIP
     * move, an entry) re-aims the glass on every frame and calls {@link #clearMotion} when done.
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
