package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.view.ContextThemeWrapper;

import androidx.annotation.NonNull;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import android.app.Application;

/** The Photo page reads the slots again after Live changed them, keeping a choice the user has not applied yet. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, qualifiers = "w360dp-h780dp-mdpi", application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WallpaperPickerRefreshTest {

    private static WallpaperPickerPage open(WallpaperPickerPageTestBase.FakeSlots slots) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity, R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        WallpaperThumbs thumbs = new WallpaperThumbs(Runnable::run,
            (file, w, h) -> Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888));
        WallpaperPickerPage page = new WallpaperPickerPage(themed, slots, new WallpaperPickerPage.Listener() {
            @Override public void onPickPhoto(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperPickerPage.ReturnState back) {}
            @Override public void onApplied(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice) {}
            @Override public void onOpenLook() {}
            @Override public void onOpenIcons() {}
        }, () -> {}, null, thumbs);
        activity.setContentView(page.root());
        return page;
    }

    @Test
    public void refresh_takesTheNewStoredChoice() {
        WallpaperPickerPageTestBase.FakeSlots slots = new WallpaperPickerPageTestBase.FakeSlots();
        slots.state = new WallpaperSlots.State(WallpaperSlots.Choice.photo(new File("old.png")),
            WallpaperSlots.Choice.sameAsHome());
        WallpaperPickerPage page = open(slots);
        File now = new File("now.png");
        slots.state = new WallpaperSlots.State(WallpaperSlots.Choice.photo(now), WallpaperSlots.Choice.sameAsHome());
        page.refreshStored();
        assertTrue(WallpaperPickerLogic.sameChoice(WallpaperSlots.Choice.photo(now), page.pending(WallpaperSlots.Slot.HOME)));
        assertFalse(page.hasPendingChanges());
    }

    @Test
    public void refresh_keepsAChoiceTheUserHasNotAppliedYet() {
        WallpaperPickerPageTestBase.FakeSlots slots = new WallpaperPickerPageTestBase.FakeSlots();
        slots.state = new WallpaperSlots.State(WallpaperSlots.Choice.photo(new File("old.png")),
            WallpaperSlots.Choice.sameAsHome());
        WallpaperPickerPage page = open(slots);
        File mine = new File("mine.png");
        page.choose(WallpaperSlots.Choice.photo(mine));
        slots.state = new WallpaperSlots.State(WallpaperSlots.Choice.photo(new File("now.png")),
            WallpaperSlots.Choice.sameAsHome());
        page.refreshStored();
        assertTrue(WallpaperPickerLogic.sameChoice(WallpaperSlots.Choice.photo(mine), page.pending(WallpaperSlots.Slot.HOME)));
    }
}
