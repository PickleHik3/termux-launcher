package com.termux.app.layouteditor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.fragments.settings.LayoutChooserModel;
import com.termux.app.fragments.settings.MiniatureDragPolicy;
import com.termux.app.place.PlaceArrangeSnapshot;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.wall.PaneWallPage;

/**
 * One Layout editor session, as decisions rather than views: which orientation the miniature is
 * showing, which one a drop writes, whether the live place behind the editor follows it, whether
 * anything has moved since the editor opened, and how to put it all back.
 *
 * <p>The miniature draws either orientation whatever the phone is in, so "shown" and "the phone's"
 * are two different questions. A drop always writes the shown one — that is the orientation the
 * user is looking at a picture of — and the place behind the editor can only follow while the two
 * agree. Editing the other orientation therefore changes the miniature and nothing else until the
 * phone is turned.
 *
 * <p>Pure: a store in, an answer out, no views, so every case above is testable without a window.
 * {@link LayoutEditorController} is the shell that draws it.
 */
public final class LayoutEditorPlan {

    /** How much of the screen's height the portrait miniature's phone frame stands in. */
    public static final float PORTRAIT_FRAME_SCREEN_FRACTION = 0.55f;

    /** What one drop on the miniature did. */
    public enum Drop {
        /** The bar cannot stand there: nothing was written and nothing moved. */
        NONE,
        /** Written for an orientation the phone is not in: the miniature moves, the place does not. */
        MINIATURE,
        /** Written for the orientation on screen: the live place follows it. */
        LIVE
    }

    @NonNull private final PlaceLayoutStore mPlaces;
    @NonNull private PaneWallPage mPlace;
    /** The arrangement of every place as the editor found it; what Discard and ↺ put back. */
    @NonNull private final PlaceArrangeSnapshot mEntry;
    @NonNull private final String mEntrySignature;
    @NonNull private PlaceOrientation mDeviceOrientation;
    @NonNull private PlaceOrientation mShownOrientation;

    private LayoutEditorPlan(@NonNull PlaceLayoutStore places, @NonNull PaneWallPage place,
                             @NonNull PlaceOrientation deviceOrientation) {
        mPlaces = places;
        mPlace = place;
        mDeviceOrientation = deviceOrientation;
        // The editor opens on what the user is looking at; the toggle is how the other one is asked
        // for.
        mShownOrientation = deviceOrientation;
        mEntry = PlaceArrangeSnapshot.capture(places);
        mEntrySignature = mEntry.signature();
    }

    /** Opens a session on one place, showing the orientation the phone is in. */
    @NonNull
    public static LayoutEditorPlan enter(@NonNull PlaceLayoutStore places,
                                         @NonNull PaneWallPage place,
                                         @NonNull PlaceOrientation deviceOrientation) {
        return new LayoutEditorPlan(places, place, deviceOrientation);
    }

    @NonNull
    public PaneWallPage place() {
        return mPlace;
    }

    /** The orientation on the miniature, which is also the one every drop writes. */
    @NonNull
    public PlaceOrientation shownOrientation() {
        return mShownOrientation;
    }

    /** The orientation the phone is in, which is what the place behind the editor is showing. */
    @NonNull
    public PlaceOrientation deviceOrientation() {
        return mDeviceOrientation;
    }

    /** Whether a write lands on the arrangement the live place behind the editor is drawing. */
    public boolean liveFollows() {
        return mShownOrientation == mDeviceOrientation;
    }

    /** The arrangement the miniature draws: the shown orientation's, resolved. */
    @NonNull
    public PlaceLayout shownLayout() {
        return mPlaces.resolve(mPlace, mShownOrientation);
    }

    /**
     * The place on the miniature. A second door opened while the editor is up — the Settings row
     * for another place, say — moves it rather than starting a session over, so what Discard puts
     * back is still the arrangement the user first opened the editor on.
     */
    public void showPlace(@NonNull PaneWallPage place) {
        mPlace = place;
    }

    /** The Portrait / Landscape toggle. Moves the picture and the target of the next drop. */
    public void showOrientation(@NonNull PlaceOrientation orientation) {
        mShownOrientation = orientation;
    }

    /**
     * The phone turned mid-session. The miniature goes with it: the editor is a picture of the
     * place behind it, and leaving it on the orientation the user can no longer see would make the
     * next drop land somewhere they are not looking.
     */
    public void onDeviceOrientationChanged(@NonNull PlaceOrientation orientation) {
        mDeviceOrientation = orientation;
        mShownOrientation = orientation;
    }

    /**
     * A bar dropped on an edge, or in the tray when {@code edge} is null. Writes the shown
     * orientation's key for this place, and says whether the live place behind has to follow.
     */
    @NonNull
    public Drop drop(@NonNull MiniatureDragPolicy.Bar bar, @Nullable PlaceLayout.Edge edge) {
        if (!LayoutChooserModel.applyDrop(mPlaces, mPlace, mShownOrientation, bar, edge))
            return Drop.NONE;
        return liveFollows() ? Drop.LIVE : Drop.MINIATURE;
    }

    /** Whether anything has moved since the editor opened — the unsaved-changes question. */
    public boolean isDirty() {
        return !mEntrySignature.equals(PlaceArrangeSnapshot.capture(mPlaces).signature());
    }

    /** Puts every place's arrangement back the way the editor found it. */
    public void revert() {
        mEntry.restore(mPlaces);
    }

    /**
     * How tall the miniature has to be for its phone frame to stand at the size this orientation
     * asks for: about {@value #PORTRAIT_FRAME_SCREEN_FRACTION} of the screen's height in portrait,
     * and the full width of the screen in landscape, where a frame sized from the height would be
     * a sliver.
     *
     * @param frameAspect the frame's width over its height, for the orientation asked about
     * @param reservedPx  the room the miniature keeps under the frame for the hide tray
     */
    public static int miniatureHeightPx(@NonNull PlaceOrientation orientation, int screenWidthPx,
                                        int screenHeightPx, float frameAspect, int reservedPx) {
        float frameHeight = orientation == PlaceOrientation.LANDSCAPE
            ? screenWidthPx / Math.max(frameAspect, 0.01f)
            : PORTRAIT_FRAME_SCREEN_FRACTION * screenHeightPx;
        return Math.round(frameHeight) + reservedPx;
    }
}
