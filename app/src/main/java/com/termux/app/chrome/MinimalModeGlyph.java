package com.termux.app.chrome;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.wall.PaneControlsView;

import java.util.function.BooleanSupplier;

/**
 * The corner tab's minimal-mode button, which the terminal's panes and the Display page both
 * carry. No font has a glyph that reads as "give this place the whole screen" and its way back,
 * so the button is a pair of vector marks drawn the way the tiling button draws its own: four
 * corners pointing out while the place is normal, pointing in once it is minimal. The mark reads
 * the state each time it draws, so a tab only has to be redrawn for a change to show.
 */
public final class MinimalModeGlyph {

    private MinimalModeGlyph() {
    }

    /** A tab mark for a place whose minimal mode {@code minimal} reports. */
    @NonNull
    public static PaneControlsView.Mark mark(@NonNull Context context,
                                             @NonNull BooleanSupplier minimal) {
        return new PaneControlsView.Mark() {
            @Nullable private Drawable mEnter;
            @Nullable private Drawable mExit;

            @Override
            public void draw(@NonNull android.graphics.Canvas canvas,
                             @NonNull android.graphics.RectF button,
                             @NonNull android.graphics.Paint paint, float density) {
                boolean on = minimal.getAsBoolean();
                Drawable icon = on ? mExit : mEnter;
                if (icon == null) {
                    icon = ContextCompat.getDrawable(context,
                        on ? R.drawable.ic_corner_minimal_exit : R.drawable.ic_corner_minimal_enter);
                    if (icon == null) return;
                    icon = icon.mutate();
                    if (on) mExit = icon;
                    else mEnter = icon;
                }
                // The same 16dp square the tiling mark fills, in the tab's own tint for how far
                // out the tab is.
                int half = Math.round(8f * density);
                int cx = Math.round(button.centerX());
                int cy = Math.round(button.centerY());
                icon.setBounds(cx - half, cy - half, cx + half, cy + half);
                icon.setTint(paint.getColor());
                icon.draw(canvas);
            }
        };
    }
}
