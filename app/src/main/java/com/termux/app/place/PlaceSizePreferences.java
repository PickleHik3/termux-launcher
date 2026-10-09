package com.termux.app.place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.function.Supplier;

/**
 * The three sizes the layout owns, resolved for the orientation on screen.
 *
 * <p>Dock height, keyboard height and the keyboard's chin are laid out per orientation like every
 * bar around them, and shared by every place like the rest of the layout (ADR 0001, ADR 0003). The
 * launcher installs this on the preferences it hands to the chrome, the dock policy, the keyboard
 * and the surface editor, so {@code getAppLauncherBarHeightScale()} and its two neighbours keep
 * their signatures and answer for the orientation being drawn. A grip dragged in the editor writes
 * back through the same route, into the orientation the user is looking at.
 *
 * <p>Settings screens build their own unscoped preferences and never install this, which is why
 * their rows still mean "everywhere".
 */
public final class PlaceSizePreferences implements TermuxAppSharedPreferences.PlaceSizes {

    @NonNull private final Supplier<PlaceLayoutStore> mPlaces;
    @NonNull private final Supplier<PlaceOrientation> mOrientation;

    public PlaceSizePreferences(@NonNull Supplier<PlaceLayoutStore> places,
                                @NonNull Supplier<PlaceOrientation> orientation) {
        mPlaces = places;
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
            : places.dockHeightScale(mOrientation.get());
    }

    @Override
    public void setDockHeightScale(float value) {
        PlaceLayoutStore places = places();
        if (places != null) places.setDockHeightScale(mOrientation.get(), value);
    }

    @Override
    public float keyboardHeightScale() {
        PlaceLayoutStore places = places();
        return places == null ? TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE
            : places.keyboardHeightScale(mOrientation.get());
    }

    @Override
    public void setKeyboardHeightScale(float value) {
        PlaceLayoutStore places = places();
        if (places != null) places.setKeyboardHeightScale(mOrientation.get(), value);
    }

    @Override
    public int keyboardChinDp() {
        PlaceLayoutStore places = places();
        return places == null ? TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BOTTOM_PADDING
            : places.keyboardChinDp(mOrientation.get());
    }

    @Override
    public void setKeyboardChinDp(int value) {
        PlaceLayoutStore places = places();
        if (places != null) places.setKeyboardChinDp(mOrientation.get(), value);
    }
}
