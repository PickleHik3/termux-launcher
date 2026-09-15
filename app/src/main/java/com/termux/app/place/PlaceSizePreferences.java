package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.function.Supplier;

/**
 * The three sizes a place owns, resolved for the place and orientation on screen.
 *
 * <p>Dock height, keyboard height and the keyboard's chin are laid out per place and per
 * orientation like every bar around them (ADR 0001). The launcher installs this on the preferences
 * it hands to the chrome, the dock policy, the keyboard and the surface editor, so
 * {@code getAppLauncherBarHeightScale()} and its two neighbours keep their signatures and start
 * answering for the place being drawn — the same seam that makes the look layer resolve without a
 * per-place branch above it. A grip dragged in the editor writes back through the same route, into
 * the place and orientation the user is looking at.
 *
 * <p>Settings screens build their own unscoped preferences and never install this, which is why
 * their rows still mean "everywhere".
 */
public final class PlaceSizePreferences implements TermuxAppSharedPreferences.PlaceSizes {

    @NonNull private final Supplier<PlaceLayoutStore> mPlaces;
    @NonNull private final Supplier<PaneWallPage> mPlace;
    @NonNull private final Supplier<PlaceOrientation> mOrientation;

    public PlaceSizePreferences(@NonNull Supplier<PlaceLayoutStore> places,
                                @NonNull Supplier<PaneWallPage> place,
                                @NonNull Supplier<PlaceOrientation> orientation) {
        mPlaces = places;
        mPlace = place;
        mOrientation = orientation;
    }

    @Nullable
    private PlaceLayoutStore places() {
        return mPlaces.get();
    }

    @Override
    public float dockHeightScale() {
        PlaceLayoutStore places = places();
        return places == null ? TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT
            : places.dockHeightScale(mPlace.get(), mOrientation.get());
    }

    @Override
    public void setDockHeightScale(float value) {
        PlaceLayoutStore places = places();
        if (places != null) places.setDockHeightScale(mPlace.get(), mOrientation.get(), value);
    }

    @Override
    public float keyboardHeightScale() {
        PlaceLayoutStore places = places();
        return places == null ? TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE
            : places.keyboardHeightScale(mPlace.get(), mOrientation.get());
    }

    @Override
    public void setKeyboardHeightScale(float value) {
        PlaceLayoutStore places = places();
        if (places != null) places.setKeyboardHeightScale(mPlace.get(), mOrientation.get(), value);
    }

    @Override
    public int keyboardChinDp() {
        PlaceLayoutStore places = places();
        return places == null ? TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING
            : places.keyboardChinDp(mPlace.get(), mOrientation.get());
    }

    @Override
    public void setKeyboardChinDp(int value) {
        PlaceLayoutStore places = places();
        if (places != null) places.setKeyboardChinDp(mPlace.get(), mOrientation.get(), value);
    }
}
