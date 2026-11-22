package com.termux.app.statusbar;

import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.DeckLook;

/**
 * The deck look's display window (wave 8): while a terminal effect is on, the CPU/RAM/weather
 * cluster sits in one dark inset window, near-black with a 1 dp inner line and rounded 4 dp, the
 * readings lit in the effect's display colour by the widgets themselves. Off the effect the
 * cluster gets back the (absent) background and the padding it had.
 */
public final class DeckDisplayWindow {

    /** The window's glass: near-black, a hair off pure so the inner line reads. */
    public static final int FILL = 0xFF050807;
    /** The 1 dp inner line. */
    public static final int LINE = 0xFF1A1F1E;
    private static final int CORNER_DP = 4;
    /** The room the window keeps around the readings along the bar, and its inset across it. */
    private static final int ALONG_PADDING_DP = 8;
    private static final int ACROSS_INSET_DP = 2;
    /**
     * Tag key for the padding the cluster had before the window, so it is dressed once and
     * undressed to exactly that.
     */
    private static final int TAG_ADDED = R.id.deck_display_window_padding;

    private DeckDisplayWindow() {}

    /**
     * Dresses {@code cluster} for {@code deck}, or undresses it for {@code null}. Idempotent: the
     * padding it adds is remembered on the view, so a second call with a deck adds nothing.
     */
    public static void apply(@Nullable ViewGroup cluster, @Nullable DeckLook deck) {
        if (cluster == null) return;
        Object tag = cluster.getTag(TAG_ADDED);
        int[] before = tag instanceof int[] ? (int[]) tag : null;
        if (deck == null) {
            if (before == null) return;
            cluster.setTag(TAG_ADDED, null);
            // The background first: a background with padding of its own (the inset window)
            // hands that padding to the view as it is set, and would do so again here if it
            // stayed. The padding the cluster had comes back after it, whole.
            cluster.setBackground(null);
            cluster.setPadding(before[0], before[1], before[2], before[3]);
            return;
        }
        if (before != null) return;
        float density = cluster.getResources().getDisplayMetrics().density;
        int along = Math.round(ALONG_PADDING_DP * density);
        int across = Math.round(ACROSS_INSET_DP * density);
        boolean column = cluster instanceof LinearLayout
            && ((LinearLayout) cluster).getOrientation() == LinearLayout.VERTICAL;
        before = new int[]{cluster.getPaddingLeft(), cluster.getPaddingTop(),
            cluster.getPaddingRight(), cluster.getPaddingBottom()};
        cluster.setTag(TAG_ADDED, before);
        // The window, then the padding: View.setBackground replaces the view's padding with the
        // drawable's own (the window's across inset) the moment it is set, which once dropped
        // the room along the bar, left the inset as padding across it, and on undressing left
        // the cluster with less than it started with: the readings' glyphs clipped at the top
        // and the last reading's edge cut off.
        cluster.setBackground(window(density, column ? across : 0, column ? 0 : across));
        int[] added = column ? new int[]{0, along, 0, along} : new int[]{along, 0, along, 0};
        cluster.setPadding(before[0] + added[0], before[1] + added[1], before[2] + added[2],
            before[3] + added[3]);
    }

    @NonNull
    private static InsetDrawable window(float density, int insetX, int insetY) {
        GradientDrawable glass = new GradientDrawable();
        glass.setColor(FILL);
        glass.setCornerRadius(CORNER_DP * density);
        glass.setStroke(Math.max(1, Math.round(density)), LINE);
        return new InsetDrawable(glass, insetX, insetY, insetX, insetY);
    }
}
