package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.chrome.scene.LiveWallpaperChoice;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The Photo | Live host on Android 13: Live opens on the stored state, a scene's page shows the
 * knobs the scene has, Back goes from a scene's page to the grid, and terminal-app mode shows the
 * phone-wallpaper switch. Thumbnails stay blank here ({@code RuntimeShader} does not draw under
 * Robolectric). Android 12 and below: {@link WallpaperLiveHostBelow33Test}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.TIRAMISU}, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WallpaperLiveHostTest {

    private Activity mActivity;
    private ContextThemeWrapper mThemed;
    private TermuxAppSharedPreferences mPrefs;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mThemed = new ContextThemeWrapper(mActivity, R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        mPrefs = TermuxAppSharedPreferences.build(mActivity, false);
        assertNotNull(mPrefs);
        LiveWallpaperChoice.none().write(mPrefs);
    }

    @NonNull
    private WallpaperLiveHost open(boolean homeApp) {
        WallpaperPickerPage.Slots slots = new WallpaperPickerPage.Slots() {
            @NonNull @Override public WallpaperSlots.State read() {
                return new WallpaperSlots.State(WallpaperSlots.Choice.photo(), WallpaperSlots.Choice.sameAsHome());
            }

            @Override public void apply(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice,
                                        @androidx.annotation.Nullable WallpaperSlots.Callback cb) {}

            @Override public boolean isHomeApp() {
                return homeApp;
            }
        };
        WallpaperPickerPage picker = new WallpaperPickerPage(mThemed, slots, new WallpaperPickerPage.Listener() {
            @Override public void onPickPhoto(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperPickerPage.ReturnState back) {}
            @Override public void onApplied(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice) {}
            @Override public void onOpenLook() {}
            @Override public void onOpenIcons() {}
        }, () -> {});
        WallpaperLiveHost host = new WallpaperLiveHost(mThemed, picker, () -> {});
        mActivity.setContentView(host.root());
        return host;
    }

    private static void tap(View v) {
        v.performClick();
    }

    @Test
    public void opensOnPhotoWithNothingInUse_andLiveShowsTheFiveScenes() {
        WallpaperLiveHost host = open(true);
        assertEquals(LiveSceneLogic.Kind.PHOTO, host.kind());
        assertNotNull(host.grid());
        assertEquals(View.VISIBLE, host.picker().root().getVisibility());
        assertEquals(5, LiveWallpaperChoice.USER_KINDS.length);
    }

    @Test
    public void opensOnLiveWhileASceneIsInUse_withTheChipOnThatScene() {
        LiveWallpaperChoice.none().withScene("nebula").write(mPrefs);
        WallpaperLiveHost host = open(true);
        assertEquals(LiveSceneLogic.Kind.LIVE, host.kind());
        assertEquals(View.GONE, host.picker().root().getVisibility());
        for (int i = 0; i < LiveWallpaperChoice.USER_KINDS.length; i++) {
            boolean inUse = "nebula".equals(LiveWallpaperChoice.USER_KINDS[i]);
            assertEquals(LiveWallpaperChoice.USER_KINDS[i], inUse ? View.VISIBLE : View.GONE,
                host.grid().badge(i).getVisibility());
        }
    }

    @Test
    public void aSceneWithDensityAndColour_showsBoth_andOthersShowNeither() {
        WallpaperLiveHost host = open(true);
        // Aurora is the first tile.
        tap(host.grid().tile(0));
        assertTrue(host.detailShown());
        LiveSceneDetailPage detail = host.detail();
        assertEquals(View.VISIBLE, detail.densityRow().getVisibility());
        assertEquals(View.VISIBLE, detail.colourRow().getVisibility());
        assertEquals(3, detail.chips().getChildCount());
        // Nebula Drift is the third.
        assertTrue(host.consumeBack());
        tap(host.grid().tile(2));
        assertEquals(View.GONE, detail.densityRow().getVisibility());
        assertEquals(View.GONE, detail.colourRow().getVisibility());
    }

    @Test
    public void backFromASceneGoesToTheGrid_thenIsNotSpent() {
        WallpaperLiveHost host = open(true);
        assertFalse(host.consumeBack());
        tap(host.grid().tile(1));
        assertTrue(host.detailShown());
        assertTrue(host.consumeBack());
        assertFalse(host.detailShown());
        assertEquals(View.VISIBLE, host.grid().root().getVisibility());
        assertEquals(View.GONE, host.detail().root().getVisibility());
        assertFalse(host.consumeBack());
    }

    @Test
    public void backToPhoto_showsOnlyWhileASceneIsInUse() {
        WallpaperLiveHost host = open(true);
        tap(host.grid().tile(0));
        assertEquals(View.GONE, host.detail().backToPhotoButton().getVisibility());
        host.consumeBack();
        LiveWallpaperChoice.none().withScene("aurora").write(mPrefs);
        tap(host.grid().tile(0));
        assertEquals(View.VISIBLE, host.detail().backToPhotoButton().getVisibility());
    }

    @Test
    public void sceneInUseOpensWithItsStoredKnobs() {
        LiveWallpaperChoice.none().withScene("aurora").withSpeed(1.5f).withRateHz(60).withPalette(2).write(mPrefs);
        WallpaperLiveHost host = open(true);
        tap(host.grid().tile(0));
        LiveWallpaperChoice shown = host.detail().current();
        assertEquals("aurora", shown.scene);
        assertEquals(1.5f, shown.speed, 0f);
        assertEquals(60, shown.rateHz);
        assertEquals(2, shown.palette);
    }

    @Test
    public void openingTheScenePageWritesNothing() {
        WallpaperLiveHost host = open(true);
        tap(host.grid().tile(0));
        assertNull("the preview is not the wallpaper", LiveWallpaperChoice.read(mPrefs).scene);
    }

    @Test
    public void phoneWallpaperSwitch_showsInTerminalAppModeOnly() {
        WallpaperLiveHost homeApp = open(true);
        tap(homeApp.grid().tile(0));
        assertEquals(View.GONE, homeApp.detail().alsoRow().getVisibility());

        WallpaperLiveHost terminal = open(false);
        tap(terminal.grid().tile(0));
        assertEquals(View.VISIBLE, terminal.detail().alsoRow().getVisibility());
    }

    @Test
    public void frameRateIsTheStoredOne() {
        LiveWallpaperChoice.none().withScene("bloom").withRateHz(15).write(mPrefs);
        WallpaperLiveHost host = open(true);
        tap(host.grid().tile(3));
        assertEquals(15, host.detail().current().rateHz);
        assertEquals(R.id.segmented_pill_default, ((com.google.android.material.button.MaterialButtonToggleGroup)
            host.detail().rateGroup()).getCheckedButtonId());
    }
}
