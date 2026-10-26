package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.FancierGlassPolicy;
import com.termux.app.chrome.WallpaperBackdropPolicy;
import com.termux.app.chrome.WallpaperPicture;
import com.termux.app.chrome.WallpaperPicturePolicy;
import com.termux.app.chrome.WallpaperPictureReader;
import com.termux.app.chrome.WallpaperSourceFacts;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A wallpaper for the launcher alone, while it is not the home app. The glass counts the app-only
 * copy as the launcher's own wallpaper without any wallpaper id agreeing: blur, the self-drawn
 * backdrop (with the phone's wallpaper kept out of the window) and Fancier Glass; a copy that is
 * gone counts for nothing. The slot plan keeps a Same as Home lock following a Home set to the
 * phone, yet copies the picture when Same as Home is applied to Lock. The Overview shows one card,
 * "Termux Launcher", with an unticked round "Also set as phone wallpaper" under it: unticked, Done
 * sets the launcher's picture only; ticked, it goes to the phone as before, and the tick is
 * remembered. As the home app the page keeps its Home and Lock pair. At 360 x 640 dp and 1.3x
 * text, native graphics, so the row's text has real metrics.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, qualifiers = "w360dp-h640dp-mdpi",
    application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WallpaperAppOnlyTest {

    /** Slots in memory that record which door an apply went through. */
    static final class AppOnlySlots implements WallpaperPickerPage.Slots {
        WallpaperSlots.State state = new WallpaperSlots.State(
            WallpaperSlots.Choice.photo(), WallpaperSlots.Choice.sameAsHome());
        boolean homeApp;
        boolean alsoPhone;
        final List<String> calls = new ArrayList<>();
        final List<WallpaperSlots.Choice> applied = new ArrayList<>();
        @Nullable File kept;

        @NonNull @Override public WallpaperSlots.State read() {
            return state;
        }

        @Override public void apply(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice,
                                    @Nullable WallpaperSlots.Callback cb) {
            calls.add("phone " + slot);
            applied.add(choice);
            state = new WallpaperSlots.State(keptChoice(), state.lock);
            if (cb != null) cb.onDone(true, null);
        }

        @Override public void applyAppOnly(@NonNull WallpaperSlots.Choice choice,
                                           @Nullable WallpaperSlots.Callback cb) {
            calls.add("launcher");
            applied.add(choice);
            state = new WallpaperSlots.State(keptChoice(), state.lock, false, true, false);
            if (cb != null) cb.onDone(true, null);
        }

        @NonNull
        private WallpaperSlots.Choice keptChoice() {
            return kept != null ? WallpaperSlots.Choice.photo(kept) : WallpaperSlots.Choice.photo();
        }

        @Override public boolean isHomeApp() {
            return homeApp;
        }

        @Override public boolean alsoSetPhone() {
            return alsoPhone;
        }

        @Override public void setAlsoSetPhone(boolean on) {
            calls.add("remember " + on);
            alsoPhone = on;
        }
    }

    private Activity mActivity;
    private ContextThemeWrapper mThemed;
    private AppOnlySlots mSlots;
    private WallpaperPickerPageTestBase.RecordingListener mListener;

    @Before
    public void setUp() {
        RuntimeEnvironment.setFontScale(1.3f);
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mThemed = new ContextThemeWrapper(mActivity, R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        mSlots = new AppOnlySlots();
        mListener = new WallpaperPickerPageTestBase.RecordingListener();
    }

    // --- the glass ---

    @Test
    public void anAppOnlyCopyIsTheLaunchersWallpaperWithoutAnyIdAgreeing() {
        WallpaperSourceFacts facts = WallpaperSourceFacts.of(-1, 42, true, 10L, 20L, 1620, 2412, true);
        assertTrue("the glass blurs the copy", facts.managedOnScreen());
        assertTrue(facts.appOnlyOnScreen());
        assertTrue("Fancier Glass works over it",
            FancierGlassPolicy.active(FancierGlassPolicy.MIN_SDK, true, facts.managedOnScreen()));

        WallpaperSourceFacts gone = WallpaperSourceFacts.of(-1, 42, false, 0L, 0L, 0, 0, true);
        assertFalse("a copy that is gone counts for nothing", gone.managedOnScreen());
        assertFalse(gone.appOnlyOnScreen());

        WallpaperSourceFacts phone = WallpaperSourceFacts.of(42, 42, true, 10L, 20L, 1620, 2412);
        assertTrue("the phone's managed wallpaper is as before", phone.managedOnScreen());
        assertFalse(phone.appOnlyOnScreen());
    }

    @Test
    public void theFactsAreReadAgainWhenTheAppOnlyMarkMoves() {
        WallpaperSourceFacts facts = WallpaperSourceFacts.of(42, 42, true, 10L, 20L, 1620, 2412);
        assertFalse(facts.isStaleFor(42, false));
        assertTrue("the picker marked the copy app-only", facts.isStaleFor(42, true));
        assertTrue("and cleared the phone's id", facts.isStaleFor(-1, true));
        assertTrue(WallpaperSourceFacts.of(-1, 42, true, 1L, 1L, 1, 1, true).isStaleFor(42, false));
    }

    @Test
    public void anAppOnlyPictureIsTheOneOnScreenWhateverAndroidRuns() {
        assertEquals("over a live wallpaper with no still", WallpaperPicture.MATCHES_SCREEN,
            WallpaperPicturePolicy.resolve(true, false, false, true));
        assertEquals("without it, as before", WallpaperPicture.NO_STILL,
            WallpaperPicturePolicy.resolve(true, false, false, false));
        assertEquals(WallpaperPicture.BEST_EFFORT, WallpaperPicturePolicy.resolve(true, false, true));
    }

    @Test
    public void anAppOnlyPictureIsSelfDrawnAndKeepsThePhonesWallpaperOutOfTheWindow() {
        assertEquals("a refused read of Android's wallpaper does not stand in its way",
            WallpaperBackdropPolicy.Mode.SELF_DRAWN,
            WallpaperBackdropPolicy.mode(true, WallpaperPicture.NO_STILL, false, true));
        assertEquals("wallpaper mode off is still off", WallpaperBackdropPolicy.Mode.PASSTHROUGH,
            WallpaperBackdropPolicy.mode(false, WallpaperPicture.MATCHES_SCREEN, true, true));
        assertEquals("without it, as before", WallpaperBackdropPolicy.Mode.PASSTHROUGH,
            WallpaperBackdropPolicy.mode(true, WallpaperPicture.NO_STILL, true, false));

        assertFalse(WallpaperBackdropPolicy.showsSystemWallpaper(WallpaperBackdropPolicy.Mode.SELF_DRAWN, true));
        assertTrue("the phone's own picture may carry a frame",
            WallpaperBackdropPolicy.showsSystemWallpaper(WallpaperBackdropPolicy.Mode.SELF_DRAWN, false));
        assertTrue(WallpaperBackdropPolicy.showsSystemWallpaper(WallpaperBackdropPolicy.Mode.PASSTHROUGH, false));
    }

    // --- the slots ---

    @Test
    public void aSameAsHomeLockUnderAnAppOnlyHomeFollowsAPhoneSetAndCopiesOnALockApply() {
        File picture = new File("/x/home.png");
        WallpaperSlots.State state = new WallpaperSlots.State(WallpaperSlots.Choice.photo(picture),
            WallpaperSlots.Choice.photo(new File("/x/lock-system.png")), true, true, true);
        WallpaperSlotPlan.Inputs in = WallpaperSlots.inputsFor(state);

        WallpaperSlotPlan home = WallpaperSlotPlan.forApply(WallpaperSlots.Slot.HOME,
            WallpaperSlots.Choice.photo(picture), in);
        assertEquals(WallpaperSlotPlan.Kind.SET_PHOTO, home.kind);
        assertEquals("the lock screen comes along, as Same as Home always did",
            WallpaperSlotPlan.FLAG_SYSTEM | WallpaperSlotPlan.FLAG_LOCK, home.flags);

        WallpaperSlotPlan lock = WallpaperSlotPlan.forApply(WallpaperSlots.Slot.LOCK,
            WallpaperSlots.Choice.sameAsHome(), in);
        assertEquals("the lock screen never got Home's picture: it is copied there",
            WallpaperSlotPlan.Kind.COPY_HOME_PHOTO_TO_LOCK, lock.kind);

        WallpaperSlotPlan plain = WallpaperSlotPlan.forApply(WallpaperSlots.Slot.LOCK,
            WallpaperSlots.Choice.sameAsHome(), new WallpaperSlotPlan.Inputs(WallpaperSlots.Choice.sameAsHome()));
        assertEquals("a phone Home is as before", WallpaperSlotPlan.Kind.RECORD_ONLY, plain.kind);
    }

    @Test
    public void anAppOnlySetKeepsTheCopyMarksItAndLeavesThePhonesIdBehind() throws IOException {
        Context app = RuntimeEnvironment.getApplication();
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, false);
        assertNotNull(prefs);
        prefs.setManagedWallpaperSystemId(42);
        File picture = png(app, "picked.png");

        assertNull(WallpaperSlots.applyAppOnlyNow(app, picture));
        File exact = WallpaperPictureReader.managedWallpaperExactFile(app);
        assertTrue("the exact copy holds the picture", exact.isFile());
        assertTrue(prefs.isWallpaperAppOnly());
        assertTrue("the copy is no longer the phone's picture", prefs.getManagedWallpaperSystemId() <= 0);
        assertTrue(WallpaperPictureReader.managedPictureOnScreen(app, prefs));
        WallpaperSlots.State state = WallpaperSlots.read(app);
        assertTrue(state.appOnly);
        assertEquals("the Home slot is the launcher's picture", exact.getAbsolutePath(),
            state.home.photoFile == null ? null : state.home.photoFile.getAbsolutePath());

        assertTrue(exact.delete());
        assertFalse("a copy that is gone counts for nothing",
            WallpaperPictureReader.managedPictureOnScreen(app, prefs));
        WallpaperSlots.State after = WallpaperSlots.read(app);
        assertFalse(after.appOnly);
        assertNull(after.home.photoFile);
    }

    // --- the Overview ---

    @Test
    public void theLauncherCardAppliesANewPictureOrTheAppOnlyOneOnceTicked() {
        File exact = new File("/x/exact.png");
        WallpaperSlots.State appOnly = new WallpaperSlots.State(WallpaperSlots.Choice.photo(exact),
            WallpaperSlots.Choice.sameAsHome(), false, true, false);
        WallpaperSlots.Choice same = WallpaperSlots.Choice.photo(exact);
        WallpaperSlots.Choice picked = WallpaperSlots.Choice.photo(new File("/x/new.png"));
        assertTrue(WallpaperPickerLogic.launcherCardApplies(picked, appOnly, false));
        assertTrue(WallpaperPickerLogic.launcherCardApplies(picked, appOnly, true));
        assertFalse("nothing new, unticked", WallpaperPickerLogic.launcherCardApplies(same, appOnly, false));
        assertTrue("ticked later: the launcher's picture goes to the phone",
            WallpaperPickerLogic.launcherCardApplies(same, appOnly, true));
        WallpaperSlots.State phone = new WallpaperSlots.State(WallpaperSlots.Choice.photo(exact),
            WallpaperSlots.Choice.sameAsHome());
        assertFalse("already the phone's", WallpaperPickerLogic.launcherCardApplies(same, phone, true));
    }

    @Test
    public void notTheHomeAppTheOverviewShowsTheLaunchersOneCardAndTheRoundCheck() {
        WallpaperPickerPage page = open();
        assertTrue(page.launcherOnly());
        TextView label = page.cardLabel(WallpaperSlots.Slot.HOME);
        assertNotNull("the launcher's card is bound", label);
        assertEquals("Termux Launcher", label.getText().toString());
        assertNull("no Lock card", page.card(WallpaperSlots.Slot.LOCK));
        assertEquals(View.GONE, page.sameAsHomeTile().getVisibility());

        View row = page.root().findViewById(R.id.wallpaper_picker_app_only_row);
        assertEquals(View.VISIBLE, row.getVisibility());
        assertFalse("unticked to start", page.alsoPhone().isChecked());
        assertEquals("Also set as phone wallpaper", page.alsoPhone().getText().toString());

        // The card stands above the row, and the row above the strip, inside the page.
        View card = page.card(WallpaperSlots.Slot.HOME);
        assertNotNull(card);
        Rect cardRect = screenRect(card);
        Rect rowRect = screenRect(row);
        Rect stripRect = screenRect(page.root().findViewById(R.id.wallpaper_picker_strip_card));
        Rect rootRect = screenRect(page.root());
        assertTrue("card " + cardRect + " over row " + rowRect, cardRect.bottom <= rowRect.top);
        assertTrue("row " + rowRect + " over strip " + stripRect, rowRect.bottom <= stripRect.top);
        assertTrue("row inside the page", rootRect.contains(rowRect));
        TextView hint = page.root().findViewById(R.id.wallpaper_picker_also_phone_hint);
        assertTrue("the line under the check stays inside the page",
            rootRect.contains(screenRect(hint)));
        assertTrue("the check is a 48dp target", page.alsoPhone().getHeight() >= 48);
    }

    @Test
    public void asTheHomeAppTheOverviewKeepsItsHomeAndLockPair() {
        mSlots.homeApp = true;
        WallpaperPickerPage page = open();
        assertFalse(page.launcherOnly());
        assertEquals(View.GONE,
            page.root().findViewById(R.id.wallpaper_picker_app_only_row).getVisibility());
        TextView home = page.cardLabel(WallpaperSlots.Slot.HOME);
        assertNotNull(home);
        assertEquals(mThemed.getString(R.string.wallpaper_picker_slot_home), home.getText().toString());
    }

    @Test
    public void uncheckedDoneSetsThePictureForTheLauncherAlone() {
        WallpaperPickerPage page = open();
        WallpaperSlots.Choice picked = WallpaperSlots.Choice.photo(new File("/x/picked.png"));
        page.choose(picked);
        assertTrue(page.hasPendingChanges());
        commit(page);
        assertEquals(Arrays.asList("launcher"), mSlots.calls);
        assertTrue(mListener.calls.contains("applied HOME"));
    }

    @Test
    public void tickedDoneGoesToThePhoneAndTheTickIsRemembered() {
        WallpaperPickerPage page = open();
        page.alsoPhone().setChecked(true);
        page.choose(WallpaperSlots.Choice.photo(new File("/x/picked.png")));
        commit(page);
        assertEquals(Arrays.asList("remember true", "phone HOME"), mSlots.calls);
        assertTrue(mSlots.alsoPhone);
    }

    @Test
    public void tickingLaterSendsTheLaunchersPictureToThePhone() {
        File exact = new File("/x/exact.png");
        mSlots.kept = exact;
        mSlots.state = new WallpaperSlots.State(WallpaperSlots.Choice.photo(exact),
            WallpaperSlots.Choice.sameAsHome(), false, true, false);
        WallpaperPickerPage page = open();
        assertFalse("nothing to do while unticked", page.hasPendingChanges());
        page.alsoPhone().setChecked(true);
        assertTrue(page.hasPendingChanges());
        commit(page);
        assertEquals(Arrays.asList("remember true", "phone HOME"), mSlots.calls);
        assertEquals(exact, mSlots.applied.get(0).photoFile);
    }

    @Test
    public void theTickIsRememberedAcrossPages() {
        mSlots.alsoPhone = true;
        assertTrue(open().alsoPhone().isChecked());
    }

    // --- helpers ---

    @NonNull
    private WallpaperPickerPage open() {
        WallpaperPickerPage page = new WallpaperPickerPage(mThemed, mSlots, mListener, () -> {}, null);
        mActivity.setContentView(page.root());
        for (int i = 0; i < 4; i++) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
        return page;
    }

    private void commit(@NonNull WallpaperPickerPage page) {
        boolean[] done = new boolean[1];
        page.commitPending(() -> done[0] = true, () -> { });
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("Done finished", done[0]);
    }

    @NonNull
    private static Rect screenRect(@NonNull View view) {
        int[] at = new int[2];
        view.getLocationOnScreen(at);
        return new Rect(at[0], at[1], at[0] + view.getWidth(), at[1] + view.getHeight());
    }

    @NonNull
    private static File png(@NonNull Context context, @NonNull String name) throws IOException {
        File file = new File(context.getCacheDir(), name);
        Bitmap bitmap = Bitmap.createBitmap(30, 60, Bitmap.Config.ARGB_8888);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return file;
    }
}
