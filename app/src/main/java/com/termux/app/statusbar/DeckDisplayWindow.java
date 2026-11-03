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
    /** Tag key for the padding the window added, so it is added once and taken back exactly. */
    private static final int TAG_ADDED = R.id.terminal_status_dot_cpu_ram;

    private DeckDisplayWindow() {}

    /**
     * Dresses {@code cluster} for {@code deck}, or undresses it for {@code null}. Idempotent: the
     * padding it adds is remembered on the view, so a second call with a deck adds nothing.
     */
    public static void apply(@Nullable ViewGroup cluster, @Nullable DeckLook deck) {
        if (cluster == null) return;
        Object tag = cluster.getTag(TAG_ADDED);
        int[] added = tag instanceof int[] ? (int[]) tag : null;
        if (deck == null) {
            if (added == null) return;
            cluster.setTag(TAG_ADDED, null);
            cluster.setPadding(cluster.getPaddingLeft() - added[0],
                cluster.getPaddingTop() - added[1], cluster.getPaddingRight() - added[2],
                cluster.getPaddingBottom() - added[3]);
            cluster.setBackground(null);
            return;
        }
        float density = cluster.getResources().getDisplayMetrics().density;
        int along = Math.round(ALONG_PADDING_DP * density);
        int across = Math.round(ACROSS_INSET_DP * density);
        boolean column = cluster instanceof LinearLayout
            && ((LinearLayout) cluster).getOrientation() == LinearLayout.VERTICAL;
        if (added == null) {
            added = column ? new int[]{0, along, 0, along} : new int[]{along, 0, along, 0};
            cluster.setTag(TAG_ADDED, added);
            cluster.setPadding(cluster.getPaddingLeft() + added[0],
                cluster.getPaddingTop() + added[1], cluster.getPaddingRight() + added[2],
                cluster.getPaddingBottom() + added[3]);
            cluster.setBackground(window(density, column ? across : 0, column ? 0 : across));
        }
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
