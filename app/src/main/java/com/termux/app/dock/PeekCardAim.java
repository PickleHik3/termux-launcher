package com.termux.app.dock;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.RoundCut;
import com.termux.app.place.RestCards;

/**
 * The Peek glass card as last cut and where the rows stood over the host then, which is where the
 * reflection is aimed from. Owned beside the scene's cards rather than by the activity: apart,
 * the card is Home's rest's whichever page is in front (#305), so a rest that is not Home's keeps
 * the card Home's rest cut. The activity answers what only it knows through {@link Host}.
 */
public final class PeekCardAim {

    /** What only the activity answers. */
    public interface Host {
        /** The cards' look for the host while they stand apart; null otherwise. */
        @Nullable RestCards.HostLook hostLook();
        /** Whether the dock can stand as the Peek glass card now. */
        boolean peekCardWanted();
        /** A Home page stands at rest with the stack dressed as it: no crossing, no travel. */
        boolean homeAtRest();
        /** The apps row, or null while there is none. */
        @Nullable View row();
        /** The row's icon boxes, read into {@code out}; the count. */
        int drawnIconBoxes(@NonNull float[] out);
        @Nullable <T extends View> T findViewById(int id);
        /** The window's decor view, or null with no window. */
        @Nullable View decorView();
        float density();
        /** The translation a view and its ancestors below {@code ancestor} draw with, down the screen. */
        float drawnShiftYIn(@NonNull View view, @NonNull View ancestor);
    }

    @NonNull private final Host mHost;
    /** The card as last cut, in the host's coordinates; null while the dock is a slab. */
    @Nullable private PeekGlassGeometry.Card mCard;
    /**
     * How far the rows stood over the host when the card was cut, where the reflection is aimed
     * from. While the cards stand apart the card and this are Home's rest's (#305).
     */
    private float mRowsPx;
    /** The card's cut, reused: the outline provider reads it on every outline pass. */
    private final float[] mCut = new float[RoundCut.SIZE];
    /** The row's icon boxes, read into this. */
    private final float[] mIconBoxes = new float[64 * 4];

    public PeekCardAim(@NonNull Host host) {
        mHost = host;
    }

    @Nullable public PeekGlassGeometry.Card card() {
        return mCard;
    }

    public float rowsPx() {
        return mRowsPx;
    }

    /** The reused cut of the card ({@link #cut}); read after {@link #cut} filled it. */
    @NonNull public float[] cutBuffer() {
        return mCut;
    }

    /** {@code peekCardWanted} as the glass host reads it: Home's rest while the cards stand apart. */
    public boolean hostWanted() {
        RestCards.HostLook look = mHost.hostLook();
        return look != null ? look.peek : mHost.peekCardWanted();
    }

    /**
     * The card as cut ({@link #card}) and where the row stood over the host then. While the
     * cards stand apart the host is Home's card at every rest (#305): away from Home's rest it
     * keeps the card Home's rest cut, parked with Home and sliding back with it, so neither a
     * crossing nor the Terminal's rest re-cuts it from the rows the Terminal stands. A rest that
     * resolves no card lets it go.
     */
    public void set(@Nullable PeekGlassGeometry.Card card) {
        if (card != null && mCard != null && mHost.hostLook() != null
            && !mHost.homeAtRest()) return;
        mCard = card;
        mRowsPx = layoutRowsOverHost();
    }

    /**
     * {@code card}'s own cut: its top through the icons' centre line, its sides, its radius and
     * its bottom past the screen's edge, as cut.
     */
    public static void cut(@NonNull PeekGlassGeometry.Card card, @NonNull float[] out) {
        PeekGlassGeometry.cut(card, 0f, out);
    }

    /**
     * How far the apps row stands below the dock's glass host as laid out, no translation counted:
     * what a card is cut from ({@link #resolve} reads the row and the host as laid out),
     * so it is the offset the reflection is aimed from.
     */
    private float layoutRowsOverHost() {
        View row = mHost.row();
        View host = mHost.findViewById(R.id.accessory_surface_host);
        View container = mHost.findViewById(R.id.accessory_stack_container);
        if (row == null || host == null || container == null) return 0f;
        float[] rowAt = DockController.layoutOffsetIn(row, container);
        float[] hostAt = DockController.layoutOffsetIn(host, container);
        return rowAt == null || hostAt == null ? 0f : rowAt[1] - hostAt[1];
    }

    /**
     * The card for the row and the host as laid out now, in the host's coordinates, or null
     * where the dock keeps its slab ({@code peekCardWanted}) or nothing is laid out yet.
     */
    @Nullable
    public PeekGlassGeometry.Card resolve() {
        // Apart: Home's card, cut from Home's rest answer at every rest, whichever page is in
        // front and whether or not Home's row is laid out under it (SideCards#hostDressPage).
        RestCards.HostLook look = mHost.hostLook();
        if (look != null) return look.peekCut;
        if (!mHost.peekCardWanted()) return null;
        View row = mHost.row();
        View host = mHost.findViewById(R.id.accessory_surface_host);
        View container = mHost.findViewById(R.id.accessory_stack_container);
        View decor = mHost.decorView();
        if (row == null || host == null || container == null || decor == null
            || !row.isShown() || host.getWidth() <= 0 || host.getHeight() <= 0
            || decor.getWidth() <= 0 || decor.getHeight() <= 0) return null;
        int count = mHost.drawnIconBoxes(mIconBoxes);
        if (count <= 0 || count > mIconBoxes.length / 4) return null;
        float[] rowAt = DockController.layoutOffsetIn(row, container);
        float[] hostAt = DockController.layoutOffsetIn(host, container);
        float[] hostInDecor = DockController.layoutOffsetIn(host, decor);
        if (rowAt == null || hostAt == null || hostInDecor == null) return null;
        float dx = rowAt[0] - hostAt[0];
        float dy = rowAt[1] - hostAt[1];
        float[] boxes = new float[count * 4];
        for (int i = 0; i < count; i++) {
            boxes[i * 4] = mIconBoxes[i * 4] + dx;
            boxes[i * 4 + 1] = mIconBoxes[i * 4 + 1] + dy;
            boxes[i * 4 + 2] = mIconBoxes[i * 4 + 2] + dx;
            boxes[i * 4 + 3] = mIconBoxes[i * 4 + 3] + dy;
        }
        // The strip under the pill continues the card to the screen's bottom edge, under either
        // Style: the host is placed flush while the card stands (dockFloats). Measured where the
        // host is drawn: a crossing's tail keeps the Terminal's keyboard room laid out and slides
        // the stack down past the screen (applyAccessoryStackTranslation), and the reflection's
        // fade measured to the laid-out bottom ran on 650 px below the screen, sharp and bright.
        float screenBottom = PeekGlassGeometry.screenBottomInHost(
            decor.getHeight(), hostInDecor[1], mHost.drawnShiftYIn(host, decor));
        // The A–Z row under the icons rides on the card (DockPlacement#peekCardCarriesAz): the
        // reflections run on behind its letters untouched, to the card's whole height.
        return PeekGlassGeometry.compute(boxes, count, hostInDecor[0],
            host.getWidth(), host.getHeight(), decor.getWidth(), screenBottom,
            mHost.density(), Float.POSITIVE_INFINITY);
    }
}
