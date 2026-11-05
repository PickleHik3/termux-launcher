package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import android.app.Activity;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.chrome.scene.LiveWallpaperChoice;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Below Android 13 Live is not offered at all: no Photo | Live row, and the picker is the Photo page alone. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WallpaperLiveHostBelow33Test {

    @Test
    public void liveIsNotOffered() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity, R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        WallpaperPickerPage picker = new WallpaperPickerPage(themed, new WallpaperPickerPage.Slots() {
                @NonNull @Override public WallpaperSlots.State read() {
                    return new WallpaperSlots.State(WallpaperSlots.Choice.photo(), WallpaperSlots.Choice.sameAsHome());
                }

                @Override public void apply(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice,
                                            @androidx.annotation.Nullable WallpaperSlots.Callback cb) {}
            },
            new WallpaperPickerPage.Listener() {
                @Override public void onPickPhoto(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperPickerPage.ReturnState back) {}
                @Override public void onApplied(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice) {}
                @Override public void onOpenLook() {}
                @Override public void onOpenIcons() {}
            }, () -> {});
        WallpaperLiveHost host = new WallpaperLiveHost(themed, picker, () -> {});
        activity.setContentView(host.root());

        assertFalse(LiveWallpaperChoice.isSupported());
        assertEquals(LiveSceneLogic.Kind.PHOTO, host.kind());
        assertNull(host.grid());
        assertNull(host.detail());
        assertFalse(host.consumeBack());
        // Only the content region: no Photo | Live row above it, and the Photo page stays visible.
        assertEquals(1, ((ViewGroup) host.root()).getChildCount());
        assertEquals(View.VISIBLE, picker.root().getVisibility());
    }
}
