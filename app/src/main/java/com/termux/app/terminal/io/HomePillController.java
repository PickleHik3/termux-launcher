package com.termux.app.terminal.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.extrakeys.HomePillSpot;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;

import java.util.Arrays;

/**
 * Decides whether the Home pill shows, which keys it wears and where it rests, and tells the
 * pill's layer. The activity gives one fact per question through {@link Host}; this combines them
 * and compares before it sets anything, so a refresh that finds nothing new does nothing. It is
 * called at moments, never per frame.
 */
public final class HomePillController {

    /** Slots of the array {@link Host#readGeometry} fills, all in layer pixels. */
    public static final int GEO_WIDTH = 0;
    public static final int GEO_HEIGHT = 1;
    public static final int GEO_INSET_LEFT = 2;
    public static final int GEO_INSET_TOP = 3;
    public static final int GEO_INSET_RIGHT = 4;
    public static final int GEO_INSET_BOTTOM = 5;
    /** Where Home's content starts: under the system bar and whatever stands on the top edge. */
    public static final int GEO_ROOM_TOP = 6;
    /** The top of the dock at rest, or the layer's bottom when no dock rows stand. */
    public static final int GEO_STACK_TOP = 7;
    /** 1 when the A-Z tab is drawn, else 0; its rect follows. */
    public static final int GEO_AZ_SHOWN = 8;
    public static final int GEO_AZ_LEFT = 9;
    public static final int GEO_AZ_TOP = 10;
    public static final int GEO_AZ_RIGHT = 11;
    public static final int GEO_AZ_BOTTOM = 12;
    public static final int GEO_SIZE = 13;

    public interface Host {
        /** Home's bar is on (the Home extra-keys switch and the toolbar switch, this orientation). */
        boolean switchOn();

        /** Where the wall has settled. */
        @NonNull PaneWallPage settledPlace();

        /** Where the extra keys go now; the key set owner's answer. */
        @Nullable ExtraKeysTarget keyTarget();

        boolean keyboardUp();

        boolean appDrawerOpen();

        boolean paletteOpen();

        boolean layoutEditorActive();

        boolean surfaceEditorActive();

        boolean appearanceOpen();

        /** Any registered overlay is open. */
        boolean overlayOpen();

        boolean minimalMode();

        /** Page 1 of the Home set the key set owner resolved, or null. */
        @Nullable ExtraKeysInfo keys();

        /** The Home set's key size. */
        float keysScale();

        void setKeys(@Nullable ExtraKeysInfo keys, float scale);

        float density();

        /** Fills {@code out} (length {@link #GEO_SIZE}); false while the window has no size yet. */
        boolean readGeometry(@NonNull float[] out);

        /** The stored centre fractions {x, y}, or null while never moved. */
        @Nullable float[] storedPosition();

        void storePosition(float x, float y);

        /** The pill's laid-out size, 0 until its first layout. */
        int pillWidth();

        int pillHeight();

        /** A finger holds the pill: nothing may move it from under the finger. */
        boolean pillPickedUp();

        void setArea(@NonNull HomePillSpot.Area area);

        void placePill(float left, float top);

        void setPillShown(boolean shown);

        void openKeysEditor();
    }

    @NonNull private final Host mHost;
    private final float[] mGeo = new float[GEO_SIZE];
    private final float[] mLastGeo = new float[GEO_SIZE];
    private final float[] mSpot = new float[2];
    private final float[] mFractions = new float[2];
    @Nullable private HomePillSpot.Area mArea;
    @Nullable private ExtraKeysInfo mKeys;
    private float mScale = Float.NaN;
    @Nullable private float[] mPosition;
    private boolean mPositionLoaded;
    private boolean mGeoValid;
    private int mPlacedW = -1;
    private int mPlacedH = -1;
    private boolean mPlacedFor;
    private boolean mShown;
    private boolean mFlightHold;

    public HomePillController(@NonNull Host host) {
        mHost = host;
    }

    public boolean isShown() {
        return mShown;
    }

    /** Whether Home's content is open to the pill, by the facts alone (no geometry). */
    public boolean shows() {
        if (!mHost.switchOn()) return false;
        if (mHost.settledPlace() != PaneWallPage.WIDGETS) return false;
        if (mHost.keyTarget() != ExtraKeysTarget.HOME) return false;
        if (mHost.keyboardUp()) return false;
        if (mHost.appDrawerOpen() || mHost.paletteOpen()) return false;
        if (mHost.layoutEditorActive() || mHost.surfaceEditorActive() || mHost.appearanceOpen()) return false;
        if (mHost.overlayOpen() || mHost.minimalMode()) return false;
        return mHost.keys() != null;
    }

    /** The properties were read again: the stored spot is read afresh, then everything is rechecked. */
    public void reload() {
        mPositionLoaded = false;
        mPlacedFor = false;
        refresh();
    }

    /**
     * Keeps a shown pill on screen, with its keys, though Home is no longer the place: a flight to
     * the docked bar is carrying it. Letting go rechecks, which hides it. Ignored while the pill is
     * not shown.
     */
    public void holdForFlight(boolean hold) {
        if (hold && !mShown) return;
        if (hold == mFlightHold) return;
        mFlightHold = hold;
        if (!hold) refresh();
    }

    public void refresh() {
        if (mFlightHold) return;
        boolean show = shows();
        boolean placed = false;
        if (show) {
            ExtraKeysInfo keys = mHost.keys();
            float scale = mHost.keysScale();
            if (keys != mKeys || Float.compare(scale, mScale) != 0) {
                mKeys = keys;
                mScale = scale;
                mHost.setKeys(keys, scale);
            }
            placed = place();
        }
        // Until it has a size and a legal spot it stays hidden, so it never shows at the corner.
        if (placed != mShown) {
            mShown = placed;
            mHost.setPillShown(placed);
        }
    }

    /** Puts the pill at its spot; false while there is no geometry or the pill has no size. */
    private boolean place() {
        int w = mHost.pillWidth();
        int h = mHost.pillHeight();
        if (w <= 0 || h <= 0) return false;
        if (!mHost.readGeometry(mGeo)) return false;
        boolean geoChanged = !mGeoValid || !Arrays.equals(mGeo, mLastGeo);
        if (geoChanged) {
            System.arraycopy(mGeo, 0, mLastGeo, 0, GEO_SIZE);
            mGeoValid = true;
            float density = mHost.density();
            HomePillSpot.Rect az = mGeo[GEO_AZ_SHOWN] > 0f
                ? new HomePillSpot.Rect(mGeo[GEO_AZ_LEFT], mGeo[GEO_AZ_TOP], mGeo[GEO_AZ_RIGHT],
                    mGeo[GEO_AZ_BOTTOM]) : null;
            mArea = HomePillSpot.area(mGeo[GEO_WIDTH], mGeo[GEO_HEIGHT], mGeo[GEO_INSET_LEFT],
                mGeo[GEO_INSET_TOP], mGeo[GEO_INSET_RIGHT], mGeo[GEO_INSET_BOTTOM],
                HomePillSpot.GUTTER_DP * density, mGeo[GEO_ROOM_TOP], mGeo[GEO_STACK_TOP], az,
                HomePillSpot.DEFAULT_INSET_DP * density);
            mHost.setArea(mArea);
            mPlacedFor = false;
        }
        if (mHost.pillPickedUp()) return true;
        if (mPlacedFor && w == mPlacedW && h == mPlacedH) return true;
        if (!mPositionLoaded) {
            mPosition = mHost.storedPosition();
            mPositionLoaded = true;
        }
        HomePillSpot.place(mPosition, w, h, mArea, mSpot);
        mHost.placePill(mSpot[0], mSpot[1]);
        mPlacedFor = true;
        mPlacedW = w;
        mPlacedH = h;
        return true;
    }

    /** The pill was dropped with its top-left at ({@code left}, {@code top}) in layer pixels. */
    public void onPillDropped(float left, float top) {
        HomePillSpot.fractionsOf(left, top, mHost.pillWidth(), mHost.pillHeight(),
            mGeo[GEO_WIDTH], mGeo[GEO_HEIGHT], mFractions);
        mHost.storePosition(mFractions[0], mFractions[1]);
        mPosition = new float[] {mFractions[0], mFractions[1]};
        mPositionLoaded = true;
        // The pill is where it was dropped; the stored spot places it there next time.
        mPlacedFor = true;
    }

    /** The chip's tap: the editor opens on the Home set. */
    public void onEditKeys() {
        mHost.openKeysEditor();
    }
}
