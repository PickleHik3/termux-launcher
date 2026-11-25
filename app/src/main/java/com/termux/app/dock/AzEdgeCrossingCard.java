package com.termux.app.dock;

import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.place.ChromeShape.Box;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.RestCards;

/**
 * The A–Z bar's second edge card (#307, ADR 0032 2026-11-24 "one card per side"): where Home's
 * rest and the Terminal's draw the bar off the dock at different rects (a side bar runs down to
 * each side's own stack top), the other side's card is laid out once, at the rest, on its own
 * host ({@code place_az_bar_crossing_host}) from the answer's parked A–Z card, and slides in with
 * its side while the edge stack's host ({@code place_az_bar_host}), the front side's card, slides
 * out with the front. Where both rests draw the bar at one rect the answer parks no A–Z card and
 * the edge stack's one host stays put.
 *
 * <p>The edge stack's host is the front side's card at every rest, so a rest looks, insets the
 * content and takes letter touch exactly as before; the crossing host is invisible at a rest (its
 * glass let go) and shows only while a Home ⇄ Terminal crossing carries its card. Laid out at the
 * rest only; a crossing shows it and the stack's writer moves it ({@code StackRenderer}).
 */
public final class AzEdgeCrossingCard {

    /** What the card reads off the activity. */
    public interface Host {
        /** The window's view of {@code id}; null while it has none. */
        @Nullable View view(int id);
        @NonNull DisplayMetrics displayMetrics();
        /**
         * Readies the host's letters and padding for a bar on {@code edge}, at rest: its letters'
         * own view installed, the edge's air, the letters and ink the bar in front wears.
         */
        void prepareBar(@NonNull FrameLayout host, @NonNull PlaceLayout.Edge edge);
        /** Dresses the host's glass for {@code home}'s side as a crossing begins; once per crossing. */
        void dressBar(@NonNull FrameLayout host, @NonNull PlaceLayout.Edge edge, boolean home);
        /** Lets the host's glass go. */
        void releaseBar(@NonNull FrameLayout host);
    }

    @NonNull private final Host mHost;
    private boolean mOn;
    private boolean mHome;
    @NonNull private PlaceLayout.Edge mEdge = PlaceLayout.Edge.LEFT;
    @Nullable private Box mBox;
    private final int[] mAt = new int[2];

    public AzEdgeCrossingCard(@NonNull Host host) {
        mHost = host;
    }

    /** Whether the other side's A–Z card stands on the crossing host at the last rest. */
    public boolean on() {
        return mOn;
    }

    /** The side whose card the crossing host carries (Home's or the Terminal's); meaningful while {@link #on}. */
    public boolean carriesHome() {
        return mHome;
    }

    /** Where the crossing host's card is laid out (screen px); null unless {@link #on}. */
    @Nullable
    public Box box() {
        return mOn ? mBox : null;
    }

    /**
     * The edge a bar laid out at {@code box} stands on, on a screen {@code widthPx} wide: a side
     * when it is taller than wide, the side its centre is on; the top otherwise.
     */
    @NonNull
    public static PlaceLayout.Edge edgeOf(@NonNull Box box, int widthPx) {
        if (box.height() <= box.width()) return PlaceLayout.Edge.TOP;
        return (box.left + box.right) / 2f < widthPx / 2f ? PlaceLayout.Edge.LEFT
            : PlaceLayout.Edge.RIGHT;
    }

    /** The other side's A–Z card the front rest's answer parks; null where none stands apart. */
    @Nullable
    static RestCards.Card parkedAz(@Nullable RestCards answer) {
        if (answer == null || !answer.apart()) return null;
        for (RestCards.Card card : answer.parked())
            if (card.id == RestCards.CardId.AZ) return card;
        return null;
    }

    /**
     * One rest: lays the crossing host out at the other side's A–Z card from the front rest's
     * {@code answer} (compare before write) and keeps it invisible, its glass let go; gone where
     * the answer parks no A–Z card.
     *
     * @return whether its standing or its bounds moved
     */
    public boolean applyAtRest(@Nullable RestCards answer) {
        RestCards.Card card = parkedAz(answer);
        View view = mHost.view(R.id.place_az_bar_crossing_host);
        boolean on = card != null && view instanceof FrameLayout;
        boolean moved = on != mOn || (on && !card.box.equals(mBox));
        mOn = on;
        mBox = on ? card.box : null;
        mHome = on && card.view.side == com.termux.app.page.Side.HOME;
        if (view == null) return moved;
        if (view.getVisibility() == View.VISIBLE && view instanceof FrameLayout)
            mHost.releaseBar((FrameLayout) view);
        if (!on) {
            if (view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
            return moved;
        }
        FrameLayout host = (FrameLayout) view;
        mEdge = edgeOf(card.box, mHost.displayMetrics().widthPixels);
        int left = Math.round(card.box.left);
        int top = Math.round(card.box.top);
        if (host.getParent() instanceof View) {
            ((View) host.getParent()).getLocationOnScreen(mAt);
            left -= mAt[0];
            top -= mAt[1];
        }
        int width = Math.round(card.box.width());
        int height = Math.round(card.box.height());
        ViewGroup.LayoutParams raw = host.getLayoutParams();
        RelativeLayout.LayoutParams params = raw instanceof RelativeLayout.LayoutParams
            ? (RelativeLayout.LayoutParams) raw : new RelativeLayout.LayoutParams(width, height);
        if (raw != params || params.leftMargin != left || params.topMargin != top
            || params.width != width || params.height != height) {
            params.leftMargin = left;
            params.topMargin = top;
            params.rightMargin = 0;
            params.bottomMargin = 0;
            params.width = width;
            params.height = height;
            host.setLayoutParams(params);
            moved = true;
        }
        mHost.prepareBar(host, mEdge);
        if (host.getVisibility() != View.INVISIBLE) host.setVisibility(View.INVISIBLE);
        return moved;
    }

    /**
     * A crossing begins: from Home's side to the Terminal's or back with the other side's card
     * standing apart, the crossing host shows, dressed in that side's glass, at the rect laid out
     * at the rest (no layout). Between two Home pages it stays hidden.
     */
    public void onCrossingBegin(boolean fromHome, boolean toHome) {
        if (!mOn || fromHome == toHome || toHome != mHome) return;
        View view = mHost.view(R.id.place_az_bar_crossing_host);
        if (!(view instanceof FrameLayout)) return;
        mHost.dressBar((FrameLayout) view, mEdge, mHome);
        if (view.getVisibility() != View.VISIBLE) view.setVisibility(View.VISIBLE);
    }
}
