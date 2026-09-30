package com.termux.app.terminal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;

/**
 * The window-switch card: the outgoing panes, copied onto a bitmap that the pan carries away.
 *
 * <p>The card is the panes' slabs and nothing else. Each frame is drawn on its own, at the place
 * {@link PaneGlass#slabOutline} puts its slab (one origin for outline, ground and content), under a
 * clip cut at the slab's radius. A software canvas ignores the frame's own clip-to-outline, so
 * drawing the whole surface host instead put the terminal's square cell fill, the glass tint and
 * whatever else the host holds around the panes on the card: a dark plate with square corners
 * behind the rounded rim. The bitmap starts transparent and stays so outside the slabs.
 */
public final class DepartureCard {

    private DepartureCard() {}

    /** What goes under the slabs (the shared blur frame), drawn once in the card's coordinates. */
    public interface Ground {
        /** Paint under {@code slabs}, and only inside them. */
        void paint(@NonNull Canvas canvas, @NonNull Path slabs);
    }

    /**
     * Draws the ground and then every slab-wearing frame onto {@code canvas}, in {@code origin}'s
     * coordinates.
     *
     * @return false, drawing nothing, when no frame wears a slab
     */
    public static boolean draw(@NonNull Canvas canvas, @NonNull View origin,
                               @NonNull Iterable<? extends View> frames, float requestedRadiusPx,
                               @NonNull Ground ground) {
        Path slabs = PaneGlass.slabOutline(frames, origin, requestedRadiusPx, new Path());
        if (slabs.isEmpty()) return false;
        ground.paint(canvas, slabs);
        int[] base = new int[2];
        int[] at = new int[2];
        origin.getLocationOnScreen(base);
        Path cut = new Path();
        for (View frame : frames) {
            if (!PaneGlass.wearsSlab(frame)) continue;
            frame.getLocationOnScreen(at);
            float radius = PaneShape.radiusForBounds(requestedRadiusPx,
                frame.getWidth(), frame.getHeight());
            cut.rewind();
            cut.addRoundRect(new RectF(0f, 0f, frame.getWidth(), frame.getHeight()),
                radius, radius, Path.Direction.CW);
            int save = canvas.save();
            canvas.translate(at[0] - base[0], at[1] - base[1]);
            canvas.clipPath(cut);
            frame.draw(canvas);
            canvas.restoreToCount(save);
        }
        return true;
    }

    /**
     * The view that carries a card bitmap over the surface. A translucent card is bare: no
     * background, no elevation and no outline, so nothing rectangular can draw or cast a shadow
     * around it. Only an opaque-ground card (glass off) takes a plate and a shadow.
     */
    @NonNull
    public static ImageView view(@NonNull Context context, @NonNull Bitmap bitmap,
                                 boolean translucent, int plateColor, float elevationPx) {
        ImageView view = new ImageView(context);
        view.setImageBitmap(bitmap);
        // Captured at reduced resolution; stretched back over the surface. It only ever moves.
        view.setScaleType(ImageView.ScaleType.FIT_XY);
        if (translucent) {
            view.setBackground(null);
            view.setElevation(0f);
            view.setOutlineProvider(null);
        } else {
            view.setBackgroundColor(plateColor);
            view.setElevation(elevationPx);
        }
        return view;
    }
}
