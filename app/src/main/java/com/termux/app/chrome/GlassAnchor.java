package com.termux.app.chrome;

import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;

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
     * and slides a pane frame under a finger, and the FLIP movement animates its translation,
     * and pinning the frost to those baked a press into the matrix.</p>
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
