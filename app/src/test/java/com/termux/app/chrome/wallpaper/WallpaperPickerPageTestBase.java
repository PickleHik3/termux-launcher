package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.termux.R;
import com.termux.ai.TaiVisionModels;
import com.termux.app.chrome.wallpaper.living.LivingRecipe;
import com.termux.app.chrome.wallpaper.living.LivingStillBuilder;
import com.termux.app.chrome.wallpaper.living.LivingStillJob;
import com.termux.app.chrome.wallpaper.living.Manifest;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Appearance Overview at the subclass's width: it lays out with nothing cut off, the heading is
 * fixed ("Appearance") while each card carries its own label, Same as Home shows only for Lock,
 * Apply is enabled only when the pending choice differs, Apply puts the background on Home and
 * points Lock at it while its menu items apply one slot, there is no shortcut row,
 * Apply and Motion share one row above the strip, and Motion is hidden below API 34. The strip
 * holds Same as Home and the recent photos, evenly spaced, and no pre-made backgrounds. Photos: a
 * cropped photo comes back pending and Apply sets it, the current photo shows in its card, below
 * API 34 the page holds photos only, and releasing without Apply discards the pending file. A
 * living still's Read again is the AI-star icon button. A fake {@link WallpaperPickerPage.Slots}
 * stands in for {@link WallpaperSlots}, and reads run inline. Native graphics, so text has real
 * metrics.
 */
public abstract class WallpaperPickerPageTestBase {

    /** A slot store in memory: apply and Motion succeed at once and are read back. */
    static final class FakeSlots implements WallpaperPickerPage.Slots {
        WallpaperSlots.State state = new WallpaperSlots.State(
            WallpaperSlots.Choice.animated("aurora"), WallpaperSlots.Choice.sameAsHome(), true, false);
        final List<String> calls = new ArrayList<>();
        /** The choice of each apply, in {@link #calls}' order. */
        final List<WallpaperSlots.Choice> applied = new ArrayList<>();
        List<File> recents = Collections.emptyList();
        final List<File> discarded = new ArrayList<>();
        /** Where an applied photo is "kept": the stored choice names this, not the pending file. */
        @Nullable File keptPhoto;

        @NonNull @Override public List<File> recents() {
            return recents;
        }

        @Override public void discardPhoto(@NonNull File photo) {
            discarded.add(photo);
        }

        @NonNull @Override public WallpaperSlots.State read() {
            return state;
        }

        @Override public void apply(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice,
                                    @Nullable WallpaperSlots.Callback cb) {
            calls.add("apply " + slot);
            applied.add(choice);
            if (choice.photo && choice.photoFile != null && keptPhoto != null) {
                choice = WallpaperSlots.Choice.photo(keptPhoto);
            }
            state = slot == WallpaperSlots.Slot.HOME
                ? new WallpaperSlots.State(choice, state.lock, state.lockMotion, state.lockLiveActive, state.homeMotion)
                : new WallpaperSlots.State(state.home, choice, state.lockMotion, state.lockLiveActive, state.homeMotion);
            if (cb != null) cb.onDone(true, null);
        }

        @Override public void setLockMotion(boolean on, @Nullable WallpaperSlots.Callback cb) {
            calls.add("motion " + on);
            state = new WallpaperSlots.State(state.home, state.lock, on, state.lockLiveActive, state.homeMotion);
            if (cb != null) cb.onDone(true, null);
        }

        @Override public void setHomeMotion(boolean on, @Nullable WallpaperSlots.Callback cb) {
            calls.add("home motion " + on);
            state = new WallpaperSlots.State(state.home, state.lock, state.lockMotion, state.lockLiveActive, on);
            if (cb != null) cb.onDone(true, null);
        }

        // --- living stills ---

        /** The job the page attaches to; null leaves living stills off, as below API 34. */
        @Nullable LivingStillJob job;
        final java.util.Map<File, Manifest> livingByPhoto = new java.util.HashMap<>();
        final java.util.Map<String, Manifest> livingById = new java.util.HashMap<>();
        List<TaiVisionModels.Missing> missing = Collections.emptyList();

        @Nullable @Override public LivingStillJob livingJob() { return job; }
        @Nullable @Override public Manifest livingFor(@NonNull File photo) { return livingByPhoto.get(photo); }
        @Nullable @Override public Manifest livingById(@NonNull String id) { return livingById.get(id); }
        @NonNull @Override public List<TaiVisionModels.Missing> livingMissing() { return missing; }
        @Override public void openModelCentre(@NonNull String modelId) { calls.add("model centre " + modelId); }
        @Override public void openMoreSettings() { calls.add("more settings"); }
    }

    /** The two heavy halves of the job, scripted; the worker only runs when the test says. */
    private static final class ScriptedEngine implements LivingStillJob.Engine {
        Runnable duringAnalysis = () -> {};
        LivingStillJob.Outcome outcome = new LivingStillJob.Outcome(true, false, null, "");
        Manifest manifest;
        File photoSeen;
        int cancels;

        @NonNull @Override
        public LivingStillJob.Outcome analyze(@NonNull File photo, @NonNull File outDir,
                                              @NonNull LivingStillJob.StageSink sink) {
            photoSeen = photo;
            sink.onStage(LivingStillJob.STAGE_DEPTH, 100);
            sink.onStage(LivingStillJob.STAGE_SCENE, 100);
            duringAnalysis.run();
            sink.onStage(LivingStillJob.STAGE_SUBJECT, 100);
            return outcome;
        }

        @NonNull @Override
        public Manifest build(@NonNull File photo, @NonNull File outDir, @NonNull LivingStillBuilder.Progress progress) {
            progress.onProgress(LivingStillJob.STAGE_RECIPE, 50);
            return manifest;
        }

        @Override public void cancelAnalysis() { cancels++; }

        @Override public boolean usesGemma() { return false; }
    }

    static final class RecordingListener implements WallpaperPickerPage.Listener {
        final List<String> calls = new ArrayList<>();

        @Override public void onPickPhoto(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperPickerPage.ReturnState back) {
            this.back = back;
            calls.add("photo " + slot);
        }
        @Override public void onApplied(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice) {
            calls.add("applied " + slot);
        }
        @Nullable WallpaperPickerPage.ReturnState back;

        @Override public void onOpenLook() { calls.add("look"); }
        @Override public void onOpenLayout() { calls.add("layout"); }
        @Override public void onOpenIcons() { calls.add("icons"); }
    }

    private Activity mActivity;
    private ContextThemeWrapper mThemed;
    private FakeSlots mSlots;
    private RecordingListener mListener;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mThemed = new ContextThemeWrapper(mActivity, R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        mSlots = new FakeSlots();
        mListener = new RecordingListener();
    }

    @NonNull
    private WallpaperPickerPage open(int sdk) {
        return open(sdk, null);
    }

    @NonNull
    private WallpaperPickerPage open(int sdk, @Nullable WallpaperPickerPage.ReturnState restore) {
        WallpaperPickerPage page = new WallpaperPickerPage(mThemed, mSlots, mListener, sdk, () -> {}, restore);
        mActivity.setContentView(page.root());
        settle();
        return page;
    }

    /** Photos decode on the calling thread to a plain bitmap; {@link #mDecoded} lists the files. */
    @NonNull
    private WallpaperPickerPage openWithPhotos(int sdk, @Nullable WallpaperPickerPage.ReturnState restore) {
        WallpaperThumbs thumbs = new WallpaperThumbs(Runnable::run, (file, w, h) -> {
            mDecoded.add(file);
            return Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        });
        WallpaperPickerPage page = new WallpaperPickerPage(mThemed, mSlots, mListener, sdk,
            WallpaperSlots.lockLiveSupported(sdk), () -> {}, restore, thumbs);
        mActivity.setContentView(page.root());
        settle();
        return page;
    }

    private final List<File> mDecoded = new ArrayList<>();

    @NonNull
    private File photoFile(@NonNull String name) {
        try {
            File dir = new File(mActivity.getCacheDir(), "picker-test");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no dir");
            File f = new File(dir, name);
            if (!f.isFile() && !f.createNewFile()) throw new IOException("no file");
            return f;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** Runs the posted card sizing and the frames that lay it out. */
    private void settle() {
        for (int i = 0; i < 4; i++) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
    }

    private int dp(int v) {
        return Math.round(v * mThemed.getResources().getDisplayMetrics().density);
    }

    @Test
    public void thePageFitsWithNothingCutOff() {
        WallpaperPickerPage page = open(34);
        View root = page.root();
        assertTrue("laid out", root.getWidth() > 0 && root.getHeight() > 0);
        assertInside(root, root);

        RecyclerView pager = root.findViewById(R.id.wallpaper_picker_pager);
        assertTrue("the previews keep a real height", pager.getHeight() >= dp(160));
        assertTrue("a card is bound", pager.getChildCount() >= 1);
        View cell = pager.getChildAt(0);
        // The cell is a column: the label over the card.
        View card = ((ViewGroup) cell).getChildAt(1);
        assertTrue("the card is sized", card.getHeight() > dp(100) && card.getWidth() > dp(40));
        assertTrue("the card fits the pager", card.getHeight() <= pager.getHeight());

        for (int id : new int[] {R.id.wallpaper_picker_apply, R.id.wallpaper_picker_apply_more}) {
            View v = root.findViewById(id);
            assertTrue(v.getResources().getResourceEntryName(id) + " is a 40dp button",
                v.getHeight() >= dp(40) && v.getWidth() >= dp(40));
        }
        for (int id : new int[] {R.id.wallpaper_picker_photo, R.id.wallpaper_picker_motion}) {
            View v = root.findViewById(id);
            assertTrue(v.getResources().getResourceEntryName(id) + " is a 48dp target",
                v.getHeight() >= dp(48) && v.getWidth() >= dp(48));
        }
        assertEquals("Same as Home alone: no backgrounds, no recents", 1, page.tileCount());

        page.centre(WallpaperSlots.Slot.HOME);
        settle();
        assertInside(root, root);
    }

    @Test
    public void thePageHasNoBarOfItsOwnAndEachCardCarriesItsOwnLabel() {
        WallpaperPickerPage page = open(34);
        assertEquals("the page opens on Home", WallpaperSlots.Slot.HOME, page.centredSlot());
        // The bar (back, the Wallpaper | Look | Layout pill, Done) is the surface's shared frame.
        assertNull(page.root().findViewById(R.id.appearance_page_back));
        assertNull(page.root().findViewById(R.id.appearance_page_done));
        assertEquals("Appearance", mThemed.getString(R.string.wallpaper_picker_title));
        assertEquals("Appearance", page.title().toString());
        page.centre(WallpaperSlots.Slot.LOCK);
        assertEquals("the heading does not follow the centred slot", "Appearance", page.title().toString());
        page.centre(WallpaperSlots.Slot.HOME);
        settle();
        TextView home = page.cardLabel(WallpaperSlots.Slot.HOME);
        TextView lock = page.cardLabel(WallpaperSlots.Slot.LOCK);
        assertTrue("both cards are bound", home != null && lock != null);
        assertEquals(mThemed.getString(R.string.wallpaper_picker_slot_home), home.getText().toString());
        assertEquals(mThemed.getString(R.string.wallpaper_picker_slot_lock), lock.getText().toString());
        WallpaperPreviewView card = page.card(WallpaperSlots.Slot.HOME);
        assertTrue("the label stands above its card", home.getBottom() <= card.getTop());
    }

    @Test
    public void sameAsHomeShowsOnlyForLock() {
        WallpaperPickerPage page = open(34);
        page.centre(WallpaperSlots.Slot.LOCK);
        assertEquals(View.VISIBLE, page.sameAsHomeTile().getVisibility());
        page.centre(WallpaperSlots.Slot.HOME);
        assertEquals(View.GONE, page.sameAsHomeTile().getVisibility());
        // Choosing it on Home is refused.
        page.choose(WallpaperSlots.Choice.sameAsHome());
        assertFalse(page.pending(WallpaperSlots.Slot.HOME).sameAsHome);
    }

    @Test
    public void applyIsEnabledOnlyWhenThePendingChoiceDiffers() {
        WallpaperPickerPage page = open(34);
        page.centre(WallpaperSlots.Slot.LOCK);
        View apply = page.root().findViewById(R.id.wallpaper_picker_apply);
        assertFalse("Lock holds what is stored", apply.isEnabled());
        page.choose(WallpaperSlots.Choice.animated("mesh"));
        assertTrue(apply.isEnabled());
        page.choose(WallpaperSlots.Choice.sameAsHome());
        assertFalse(apply.isEnabled());

        page.centre(WallpaperSlots.Slot.HOME);
        assertFalse("Home holds what is stored", apply.isEnabled());
        page.choose(WallpaperSlots.Choice.animated("aurora"));
        assertFalse(apply.isEnabled());
        page.choose(WallpaperSlots.Choice.animated("tide"));
        assertTrue(apply.isEnabled());

        // Swiping keeps each slot's pending choice.
        page.centre(WallpaperSlots.Slot.LOCK);
        assertTrue(page.pending(WallpaperSlots.Slot.LOCK).sameAsHome);
        page.centre(WallpaperSlots.Slot.HOME);
        assertEquals("tide", page.pending(WallpaperSlots.Slot.HOME).animatedId);

        apply.performClick();
        assertTrue(mSlots.calls.contains("apply HOME"));
        assertTrue(mListener.calls.contains("applied HOME"));
        assertFalse("stored now matches", apply.isEnabled());
    }

    @Test
    public void applyPutsTheBackgroundOnHomeAndLockFollows() {
        mSlots.state = new WallpaperSlots.State(WallpaperSlots.Choice.animated("aurora"),
            WallpaperSlots.Choice.animated("rain"), true, false);
        WallpaperPickerPage page = open(34);
        page.centre(WallpaperSlots.Slot.HOME);
        page.choose(WallpaperSlots.Choice.animated("tide"));
        View apply = page.root().findViewById(R.id.wallpaper_picker_apply);
        assertTrue(apply.isEnabled());

        apply.performClick();
        assertEquals(Arrays.asList("apply HOME", "apply LOCK"), mSlots.calls);
        assertEquals("tide", mSlots.applied.get(0).animatedId);
        assertTrue("Lock follows Home", mSlots.applied.get(1).sameAsHome);
        assertTrue(mListener.calls.contains("applied HOME"));
        assertTrue(mListener.calls.contains("applied LOCK"));
        assertEquals("tide", mSlots.state.home.animatedId);
        assertTrue(mSlots.state.lock.sameAsHome);
        assertTrue(page.pending(WallpaperSlots.Slot.LOCK).sameAsHome);
        assertFalse("both slots hold it now", apply.isEnabled());
    }

    @Test
    public void applyFromLockPutsLocksPickOnHomeToo() {
        WallpaperPickerPage page = open(34);
        page.choose(WallpaperSlots.Choice.animated("mesh"));
        page.root().findViewById(R.id.wallpaper_picker_apply).performClick();
        assertEquals(Arrays.asList("apply HOME", "apply LOCK"), mSlots.calls);
        assertEquals("mesh", mSlots.applied.get(0).animatedId);
        assertTrue(mSlots.applied.get(1).sameAsHome);
    }

    @Test
    public void theMenuItemsApplyOneSlotOnly() {
        WallpaperPickerPage page = open(34);
        page.centre(WallpaperSlots.Slot.HOME);
        page.choose(WallpaperSlots.Choice.animated("tide"));
        assertTrue(page.slotOnlyEnabled(WallpaperSlots.Slot.HOME));
        assertTrue(page.slotOnlyEnabled(WallpaperSlots.Slot.LOCK));
        assertTrue(page.root().findViewById(R.id.wallpaper_picker_apply_more).isEnabled());

        page.applySlotOnly(WallpaperSlots.Slot.HOME);
        assertEquals(Arrays.asList("apply HOME"), mSlots.calls);
        assertEquals("tide", mSlots.applied.get(0).animatedId);
        assertFalse("Home holds it now", page.slotOnlyEnabled(WallpaperSlots.Slot.HOME));

        page.choose(WallpaperSlots.Choice.animated("rain"));
        page.applySlotOnly(WallpaperSlots.Slot.LOCK);
        assertEquals(Arrays.asList("apply HOME", "apply LOCK"), mSlots.calls);
        assertEquals("rain", mSlots.applied.get(1).animatedId);
        assertEquals("Home untouched", "tide", mSlots.state.home.animatedId);
        assertEquals("rain", mSlots.state.lock.animatedId);
    }

    @Test
    public void theShortcutRowIsGoneAndThePendingChoiceSurvivesAHide() {
        WallpaperPickerPage page = open(34);
        page.centre(WallpaperSlots.Slot.HOME);
        page.choose(WallpaperSlots.Choice.animated("tide"));
        assertEquals("the shortcut row is gone", 0, page.root().getResources().getIdentifier(
            "wallpaper_picker_shortcuts", "id", page.root().getContext().getPackageName()));
        // The page is hidden behind the editor, not rebuilt: its pending choices are where they were.
        page.onHidden();
        page.onShown();
        assertEquals("tide", page.pending(WallpaperSlots.Slot.HOME).animatedId);
        assertTrue("still pending, not applied", mSlots.calls.isEmpty());
        assertTrue(page.root().findViewById(R.id.wallpaper_picker_apply).isEnabled());
    }

    @Test
    public void theCardsSitAboveTheApplyRowAndTheRowAboveTheStrip() {
        WallpaperPickerPage page = open(34);
        View root = page.root();
        View pager = root.findViewById(R.id.wallpaper_picker_pager);
        View row = root.findViewById(R.id.wallpaper_picker_apply_row);
        View strip = root.findViewById(R.id.wallpaper_picker_strip_card);
        assertTrue("cards end above the row", pager.getBottom() <= row.getTop());
        assertTrue("the row ends above the strip card", row.getBottom() <= strip.getTop());
        View apply = root.findViewById(R.id.wallpaper_picker_apply);
        View motion = root.findViewById(R.id.wallpaper_picker_motion_row);
        assertTrue("Apply and Motion share the row", apply.getTop() >= row.getTop()
            && apply.getBottom() <= row.getBottom() && motion.getTop() >= row.getTop()
            && motion.getBottom() <= row.getBottom());
        assertTrue("Motion is at the row's end", motion.getLeft() >= apply.getRight());
    }

    @Test
    public void motionShowsOnLockFromApi34() {
        WallpaperPickerPage page = open(34);
        page.centre(WallpaperSlots.Slot.LOCK);
        View motion = page.root().findViewById(R.id.wallpaper_picker_motion);
        assertEquals(View.VISIBLE, motion.getVisibility());
        page.centre(WallpaperSlots.Slot.HOME);
        assertEquals("keeps its place on Home", View.INVISIBLE, motion.getVisibility());
    }

    @Test
    public void motionIsHiddenBelowApi34() {
        WallpaperPickerPage page = open(33);
        View row = page.root().findViewById(R.id.wallpaper_picker_motion_row);
        assertEquals(View.GONE, row.getVisibility());
        assertFalse(page.root().findViewById(R.id.wallpaper_picker_motion).isShown());
    }

    @Test
    public void photoHandsTheSurfaceItsStateToComeBackTo() {
        WallpaperPickerPage page = open(34);
        page.centre(WallpaperSlots.Slot.LOCK);
        page.root().findViewById(R.id.wallpaper_picker_photo).performClick();
        assertEquals("photo LOCK", mListener.calls.get(mListener.calls.size() - 1));
        assertEquals(WallpaperSlots.Slot.LOCK, mListener.back.centred);
        // The surface closes the page after the hand-off: nothing pending is thrown away.
        page.release();
        assertTrue(mSlots.discarded.isEmpty());
    }

    // --- photos ---

    @Test
    public void aCroppedPhotoComesBackPendingAndApplySetsIt() {
        WallpaperPickerPage page = open(34);
        page.root().findViewById(R.id.wallpaper_picker_photo).performClick();
        assertEquals("photo HOME", mListener.calls.get(mListener.calls.size() - 1));
        WallpaperPickerPage.ReturnState back = mListener.back;
        assertTrue(back != null);

        File cropped = photoFile("pending.png");
        WallpaperPickerPage.ReturnState restore =
            WallpaperPickerPage.ReturnState.withPhoto(back, WallpaperSlots.Slot.HOME, cropped, mSlots.state);
        mSlots.keptPhoto = photoFile("exact.png");
        WallpaperPickerPage again = open(34, restore);
        assertEquals(WallpaperSlots.Slot.HOME, again.centredSlot());
        assertEquals(cropped, again.pending(WallpaperSlots.Slot.HOME).photoFile);
        assertTrue("previewed, not applied", mSlots.calls.isEmpty());
        View apply = again.root().findViewById(R.id.wallpaper_picker_apply);
        assertTrue("a pending photo enables Apply", apply.isEnabled());

        apply.performClick();
        assertEquals(Arrays.asList("apply HOME", "apply LOCK"), mSlots.calls);
        assertTrue(mSlots.applied.get(0).photo);
        assertEquals(cropped, mSlots.applied.get(0).photoFile);
        assertTrue("Lock follows", mSlots.applied.get(1).sameAsHome);
        assertEquals("the page now shows what is kept", mSlots.keptPhoto,
            again.pending(WallpaperSlots.Slot.HOME).photoFile);
        assertFalse(apply.isEnabled());
    }

    @Test
    public void aLockOnlyPhotoSetsTheLockScreenAlone() {
        File cropped = photoFile("lock-pending.png");
        WallpaperPickerPage.ReturnState restore = WallpaperPickerPage.ReturnState.withPhoto(null,
            WallpaperSlots.Slot.LOCK, cropped, mSlots.state);
        WallpaperPickerPage page = open(34, restore);
        assertEquals(WallpaperSlots.Slot.LOCK, page.centredSlot());
        assertTrue(page.slotOnlyEnabled(WallpaperSlots.Slot.LOCK));
        page.applySlotOnly(WallpaperSlots.Slot.LOCK);
        assertEquals(Arrays.asList("apply LOCK"), mSlots.calls);
        assertEquals(cropped, mSlots.applied.get(0).photoFile);
        assertEquals("Home untouched", "aurora", mSlots.state.home.animatedId);
    }

    @Test
    public void thePhotoReturnStateRoundTrips() {
        WallpaperPickerPage page = open(34);
        page.centre(WallpaperSlots.Slot.LOCK);
        page.choose(WallpaperSlots.Choice.animated("rain"));
        page.centre(WallpaperSlots.Slot.HOME);
        page.root().findViewById(R.id.wallpaper_picker_photo).performClick();
        WallpaperPickerPage.ReturnState back = mListener.back;
        assertTrue(back != null);
        assertEquals(WallpaperSlots.Slot.HOME, back.centred);
        assertEquals("rain", back.pendingLock.animatedId);
        assertTrue("Photo… is a hand-off: nothing is discarded", mSlots.discarded.isEmpty());

        File cropped = photoFile("round-trip.png");
        WallpaperPickerPage.ReturnState restore =
            WallpaperPickerPage.ReturnState.withPhoto(back, WallpaperSlots.Slot.HOME, cropped, mSlots.state);
        WallpaperPickerPage again = open(34, restore);
        assertEquals("the other slot keeps its pending choice", "rain",
            again.pending(WallpaperSlots.Slot.LOCK).animatedId);
        WallpaperPickerPage.ReturnState out = again.returnState();
        assertEquals(WallpaperSlots.Slot.HOME, out.centred);
        assertEquals(cropped, out.pendingHome.photoFile);
        assertEquals("rain", out.pendingLock.animatedId);

        // A cancelled pick comes back with back itself: nothing changed.
        WallpaperPickerPage cancelled = open(34, back);
        assertEquals("aurora", cancelled.pending(WallpaperSlots.Slot.HOME).animatedId);
    }

    @Test
    public void backWithoutApplyDiscardsThePendingPhoto() {
        File cropped = photoFile("discard.png");
        WallpaperPickerPage page = open(34, WallpaperPickerPage.ReturnState.withPhoto(null,
            WallpaperSlots.Slot.HOME, cropped, mSlots.state));
        page.release();
        assertTrue(mSlots.calls.isEmpty());
        assertEquals(Collections.singletonList(cropped), mSlots.discarded);
    }

    @Test
    public void theCurrentPhotoShowsInItsCard() {
        File exact = photoFile("current.png");
        mSlots.state = new WallpaperSlots.State(WallpaperSlots.Choice.photo(exact),
            WallpaperSlots.Choice.sameAsHome(), true, false);
        WallpaperPickerPage page = openWithPhotos(34, null);
        WallpaperPreviewView card = page.card(WallpaperSlots.Slot.HOME);
        assertTrue("the Home card is bound", card != null);
        assertTrue(card.showsPhoto());
        assertTrue("the photo was decoded", mDecoded.contains(exact));
        assertTrue("the card has the picture", card.still() != null);
        WallpaperPreviewView lock = page.card(WallpaperSlots.Slot.LOCK);
        if (lock != null) {
            assertTrue("Same as Home shows Home's photo", lock.showsPhoto());
        }
    }

    @Test
    public void theStripHoldsSameAsHomeAndTheRecentPhotosAndNothingElse() {
        File a = photoFile("recent-a.png");
        File b = photoFile("recent-b.png");
        mSlots.recents = Arrays.asList(a, b);
        WallpaperPickerPage page = openWithPhotos(34, null);
        assertEquals(2, page.recentTileCount());
        assertEquals("Same as Home and the recents: no pre-made backgrounds",
            WallpaperPickerLogic.stripTileCount(2, RecentWallpapers.MAX), page.tileCount());
        assertEquals(3, page.tileCount());
        assertTrue("thumbnails decoded", mDecoded.containsAll(Arrays.asList(a, b)));

        page.choose(WallpaperSlots.Choice.photo(b));
        View apply = page.root().findViewById(R.id.wallpaper_picker_apply);
        assertTrue(apply.isEnabled());
        apply.performClick();
        assertEquals("no new crop: the recent file itself", b, mSlots.applied.get(0).photoFile);
    }

    @Test
    public void theStripsTilesAreEvenlySpacedAcrossTheRow() {
        mSlots.recents = Arrays.asList(photoFile("e1.png"), photoFile("e2.png"), photoFile("e3.png"));
        WallpaperPickerPage page = openWithPhotos(34, null);
        page.centre(WallpaperSlots.Slot.LOCK);
        settle();
        ViewGroup strip = page.root().findViewById(R.id.wallpaper_picker_strip);
        assertEquals("Same as Home and three photos", 4, strip.getChildCount());
        int cell = strip.getChildAt(0).getWidth();
        assertTrue("the cells share the row", cell > 0);
        for (int i = 0; i < strip.getChildCount(); i++) {
            View c = strip.getChildAt(i);
            assertEquals("every cell is the same width", cell, c.getWidth(), 1f);
            if (i > 0) assertEquals("with no gap between", strip.getChildAt(i - 1).getRight(), c.getLeft());
        }
        assertEquals("the row is used end to end", strip.getWidth() - strip.getPaddingLeft() - strip.getPaddingRight(),
            cell * 4, 4f);
    }

    @Test
    public void below34ThePageHoldsPhotosOnly() {
        mSlots.state = new WallpaperSlots.State(WallpaperSlots.Choice.photo(photoFile("home.png")),
            WallpaperSlots.Choice.sameAsHome(), true, false);
        mSlots.recents = Arrays.asList(photoFile("r1.png"), photoFile("r2.png"), photoFile("r3.png"));
        WallpaperPickerPage page = openWithPhotos(33, null);
        View root = page.root();
        assertEquals(3, page.recentTileCount());
        assertEquals("Same as Home and the recents", 1 + 3, page.tileCount());
        assertEquals("no Motion", View.GONE, root.findViewById(R.id.wallpaper_picker_motion_row).getVisibility());
        View photo = root.findViewById(R.id.wallpaper_picker_photo);
        assertTrue("Photo… is there", photo.isShown());
        assertTrue(photo.getHeight() >= dp(48));
        WallpaperPreviewView card = page.card(WallpaperSlots.Slot.HOME);
        assertTrue(card != null);
        assertFalse("no live preview", card.isLive());
        assertInside(root, root);

        // Same as Home still works for Lock.
        page.centre(WallpaperSlots.Slot.LOCK);
        assertEquals(View.VISIBLE, page.sameAsHomeTile().getVisibility());
        page.choose(WallpaperSlots.Choice.photo(mSlots.recents.get(0)));
        page.applySlotOnly(WallpaperSlots.Slot.LOCK);
        page.choose(WallpaperSlots.Choice.sameAsHome());
        page.applySlotOnly(WallpaperSlots.Slot.LOCK);
        assertEquals(Arrays.asList("apply LOCK", "apply LOCK"), mSlots.calls);
        assertTrue(mSlots.state.lock.sameAsHome);
    }

    // --- living stills (living-stills.md, Part D.6) ---

    private final List<Runnable> mWorkerQueue = new ArrayList<>();
    private ScriptedEngine mScript;
    private Manifest mManifest;

    /** A job on {@link #mWorkerQueue}: nothing runs until {@link #runWorker}, so the page can be seen mid-run. */
    private void withLiving() {
        mScript = new ScriptedEngine();
        File dir = new File(mActivity.getCacheDir(), "living-test/0123456789abcdef");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new AssertionError("no dir");
        mManifest = new Manifest(dir, new LivingRecipe());
        try {
            // Read again hashes the still's own photo, so it must exist.
            java.nio.file.Files.write(mManifest.image().toPath(), new byte[] {1, 2, 3});
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        mScript.manifest = mManifest;
        mSlots.job = new LivingStillJob(mScript, mWorkerQueue::add, Runnable::run, Runnable::run,
            new File(mActivity.getCacheDir(), "living-analysis"));
        mSlots.livingById.put(mManifest.wallpaperId(), mManifest);
    }

    private void runWorker() {
        while (!mWorkerQueue.isEmpty()) mWorkerQueue.remove(0).run();
        settle();
    }

    @NonNull
    private WallpaperPickerPage openOnPhoto(@NonNull WallpaperSlots.Slot slot, @NonNull File photo, int sdk) {
        return openWithPhotos(sdk, WallpaperPickerPage.ReturnState.withPhoto(null, slot, photo, mSlots.state));
    }

    private <T extends View> T find(@NonNull WallpaperPickerPage page, int id) {
        return page.root().findViewById(id);
    }

    @Test
    public void aPhotoOnEitherCardOffersBringToLifeFromApi34() {
        withLiving();
        for (WallpaperSlots.Slot slot : WallpaperSlots.Slot.values()) {
            WallpaperPickerPage page = openOnPhoto(slot, photoFile("offer-" + slot + ".png"), 34);
            assertEquals(slot, page.centredSlot());
            View offer = find(page, R.id.wallpaper_picker_living_offer);
            assertEquals(slot + "", View.VISIBLE, offer.getVisibility());
            assertTrue("a 48dp target", offer.getHeight() >= dp(48) && offer.getWidth() >= dp(48));
            assertTrue("the button reads Bring to life",
                ((MaterialButton) offer).getText().toString().equals(mThemed.getString(R.string.living_bring_to_life)));
            assertNotNull("with the new glyph", ((MaterialButton) offer).getIcon());
            assertFalse("no Motion switch yet", find(page, R.id.wallpaper_picker_motion).isShown());
            assertEquals(View.GONE, find(page, R.id.wallpaper_picker_living_working).getVisibility());
            assertInside(page.root(), page.root());
        }
    }

    @Test
    public void theOtherCardOffersItToo() {
        withLiving();
        WallpaperPickerPage page = openOnPhoto(WallpaperSlots.Slot.HOME, photoFile("both.png"), 34);
        page.centre(WallpaperSlots.Slot.LOCK);
        settle();
        // Lock follows Home, so its card shows the pending photo and offers the same button.
        assertEquals(View.VISIBLE, find(page, R.id.wallpaper_picker_living_offer).getVisibility());
    }

    @Test
    public void nothingShowsBelowApi34() {
        withLiving();
        WallpaperPickerPage page = openOnPhoto(WallpaperSlots.Slot.HOME, photoFile("old.png"), 33);
        assertEquals(View.GONE, find(page, R.id.wallpaper_picker_motion_row).getVisibility());
        assertFalse(find(page, R.id.wallpaper_picker_living_offer).isShown());
    }

    @Test
    public void aBackgroundOrNoJobKeepsTheOldRow() {
        withLiving();
        WallpaperPickerPage page = open(34);
        assertFalse("no photo, no button", find(page, R.id.wallpaper_picker_living_offer).isShown());
        page.centre(WallpaperSlots.Slot.LOCK);
        assertEquals(View.VISIBLE, find(page, R.id.wallpaper_picker_motion).getVisibility());
        mSlots.job = null;
        WallpaperPickerPage noJob = openOnPhoto(WallpaperSlots.Slot.HOME, photoFile("nojob.png"), 34);
        assertFalse(find(noJob, R.id.wallpaper_picker_living_offer).isShown());
    }

    @Test
    public void missingModelsAskForTheModelCentreOnTheFirst() {
        withLiving();
        mSlots.missing = Arrays.asList(
            new TaiVisionModels.Missing("depth-anything-3-small", "Depth Anything 3 Small", 55_035_456L),
            new TaiVisionModels.Missing("u2net", "U-2-Net", 88_230_272L));
        WallpaperPickerPage page = openOnPhoto(WallpaperSlots.Slot.HOME, photoFile("missing.png"), 34);
        find(page, R.id.wallpaper_picker_living_offer).performClick();
        settle();

        androidx.appcompat.app.AlertDialog dialog = page.missingDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        String message = ((TextView) dialog.findViewById(android.R.id.message)).getText().toString();
        assertTrue(message, message.contains("Depth Anything 3 Small") && message.contains("U-2-Net"));
        assertTrue("with sizes", message.contains("MB"));
        assertFalse("nothing started", mSlots.job.isRunning());
        assertEquals(View.VISIBLE, find(page, R.id.wallpaper_picker_living_offer).getVisibility());

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
        settle();
        assertTrue(mSlots.calls.toString(), mSlots.calls.contains("model centre depth-anything-3-small"));
        assertFalse("the first missing model only", mSlots.calls.contains("model centre u2net"));
    }

    @Test
    public void notNowLeavesTheButtonAndStartsNothing() {
        withLiving();
        mSlots.missing = Arrays.asList(new TaiVisionModels.Missing("u2net", "U-2-Net", 88_230_272L));
        WallpaperPickerPage page = openOnPhoto(WallpaperSlots.Slot.HOME, photoFile("notnow.png"), 34);
        find(page, R.id.wallpaper_picker_living_offer).performClick();
        settle();
        androidx.appcompat.app.AlertDialog dialog = page.missingDialog();
        assertNotNull(dialog);
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).performClick();
        settle();
        assertNull(page.missingDialog());
        assertFalse(mSlots.job.isRunning());
        assertTrue(mSlots.calls.isEmpty());
    }

    @Test
    public void theButtonBecomesABarThenTheMotionSwitchAndThePendingStill() {
        withLiving();
        final File photo = photoFile("run.png");
        final WallpaperPickerPage[] holder = new WallpaperPickerPage[1];
        final List<String> midRun = new ArrayList<>();
        mScript.duringAnalysis = () -> {
            WallpaperPickerPage p = holder[0];
            LinearProgressIndicator bar = find(p, R.id.wallpaper_picker_living_progress);
            midRun.add(bar.getProgress() + " " + ((TextView) find(p, R.id.wallpaper_picker_living_stage)).getText());
        };
        WallpaperPickerPage page = openOnPhoto(WallpaperSlots.Slot.HOME, photo, 34);
        holder[0] = page;
        find(page, R.id.wallpaper_picker_living_offer).performClick();
        settle(); // a layout pass for the bar row; the job itself waits on the worker queue

        // Started: the button is gone, a determinate bar with the first stage and a cancel are up.
        assertTrue(mSlots.job.isRunning());
        assertEquals(View.GONE, find(page, R.id.wallpaper_picker_living_offer).getVisibility());
        assertEquals(View.VISIBLE, find(page, R.id.wallpaper_picker_living_working).getVisibility());
        LinearProgressIndicator bar = find(page, R.id.wallpaper_picker_living_progress);
        assertFalse("determinate", bar.isIndeterminate());
        assertEquals(0, bar.getProgress());
        assertEquals(mThemed.getString(R.string.living_stage_depth),
            ((TextView) find(page, R.id.wallpaper_picker_living_stage)).getText().toString());
        View cancel = find(page, R.id.wallpaper_picker_living_cancel);
        assertTrue(cancel.getHeight() >= dp(48) && cancel.getWidth() >= dp(48));
        assertInside(page.root(), page.root());

        runWorker();
        assertEquals("the bar at depth and scene done, and the scene label",
            Arrays.asList("50 " + mThemed.getString(R.string.living_stage_scene)), midRun);

        // Done: the pending choice is the living still, which plays in its card; the switch is on.
        assertFalse(mSlots.job.isRunning());
        assertEquals(View.GONE, find(page, R.id.wallpaper_picker_living_working).getVisibility());
        assertEquals(mManifest.wallpaperId(), page.pending(WallpaperSlots.Slot.HOME).animatedId);
        assertFalse(page.pending(WallpaperSlots.Slot.HOME).photo);
        MaterialSwitch motion = find(page, R.id.wallpaper_picker_motion);
        assertEquals(View.VISIBLE, motion.getVisibility());
        assertTrue("Motion starts on", motion.isChecked());
        assertTrue(motion.isShown());
        assertEquals(View.VISIBLE, find(page, R.id.wallpaper_picker_living_again).getVisibility());
        assertTrue("Apply puts it on the slot, as for every choice", find(page, R.id.wallpaper_picker_apply).isEnabled());
        assertInside(page.root(), page.root());

        find(page, R.id.wallpaper_picker_apply).performClick();
        assertEquals(Arrays.asList("apply HOME", "apply LOCK"), mSlots.calls);
        assertEquals(mManifest.wallpaperId(), mSlots.applied.get(0).animatedId);
    }

    @Test
    public void cancelStopsTheRunAndBringsTheButtonBack() {
        withLiving();
        final WallpaperPickerPage[] holder = new WallpaperPickerPage[1];
        mScript.outcome = new LivingStillJob.Outcome(false, true, "cancelled", "");
        mScript.duringAnalysis = () -> find(holder[0], R.id.wallpaper_picker_living_cancel).performClick();
        WallpaperPickerPage page = openOnPhoto(WallpaperSlots.Slot.HOME, photoFile("cancel.png"), 34);
        holder[0] = page;
        find(page, R.id.wallpaper_picker_living_offer).performClick();
        runWorker();

        assertEquals(1, mScript.cancels);
        assertEquals(View.VISIBLE, find(page, R.id.wallpaper_picker_living_offer).getVisibility());
        assertEquals(View.GONE, find(page, R.id.wallpaper_picker_living_working).getVisibility());
        assertTrue("still the photo", page.pending(WallpaperSlots.Slot.HOME).photo);
        assertNull("a cancel is not an error", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void aFailedRunSaysSoAndKeepsThePhoto() {
        withLiving();
        mScript.outcome = new LivingStillJob.Outcome(false, false, "vision_failed", "boom");
        WallpaperPickerPage page = openOnPhoto(WallpaperSlots.Slot.HOME, photoFile("fail.png"), 34);
        find(page, R.id.wallpaper_picker_living_offer).performClick();
        runWorker();
        assertEquals(mThemed.getString(R.string.living_failed), ShadowToast.getTextOfLatestToast());
        assertEquals(View.VISIBLE, find(page, R.id.wallpaper_picker_living_offer).getVisibility());
        assertTrue(page.pending(WallpaperSlots.Slot.HOME).photo);
    }

    @Test
    public void aPhotoThatAlreadyHasALivingStillIsAdoptedAsOne() {
        withLiving();
        File photo = photoFile("known.png");
        mSlots.livingByPhoto.put(photo, mManifest);
        WallpaperPickerPage page = openOnPhoto(WallpaperSlots.Slot.HOME, photo, 34);
        assertEquals(mManifest.wallpaperId(), page.pending(WallpaperSlots.Slot.HOME).animatedId);
        assertFalse(find(page, R.id.wallpaper_picker_living_offer).isShown());
        assertTrue(find(page, R.id.wallpaper_picker_motion).isShown());

        // Choosing it from the strip later does the same.
        File other = photoFile("known-2.png");
        mSlots.livingByPhoto.put(other, mManifest);
        page.choose(WallpaperSlots.Choice.photo(other));
        assertEquals(mManifest.wallpaperId(), page.pending(WallpaperSlots.Slot.HOME).animatedId);
    }

    @Test
    public void homeMotionSwitchFollowsTheStoredToggleForALivingStill() {
        withLiving();
        mSlots.state = new WallpaperSlots.State(WallpaperSlots.Choice.animated(mManifest.wallpaperId()),
            WallpaperSlots.Choice.sameAsHome(), true, false, false);
        WallpaperPickerPage page = openWithPhotos(34, null);
        MaterialSwitch motion = find(page, R.id.wallpaper_picker_motion);
        assertEquals("Home shows it for a living still", View.VISIBLE, motion.getVisibility());
        assertFalse("off as stored", motion.isChecked());

        motion.performClick();
        assertEquals(Arrays.asList("home motion true"), mSlots.calls);
        assertTrue(mSlots.state.homeMotion);
        assertTrue(motion.isChecked());

        page.centre(WallpaperSlots.Slot.LOCK);
        assertTrue("Lock shows Home's still with Lock's own toggle", motion.isChecked());
        motion.performClick();
        assertEquals(Arrays.asList("home motion true", "motion false"), mSlots.calls);
    }

    @Test
    public void readAgainRunsTheJobOverTheStillsOwnPhoto() {
        withLiving();
        mSlots.state = new WallpaperSlots.State(WallpaperSlots.Choice.animated(mManifest.wallpaperId()),
            WallpaperSlots.Choice.sameAsHome(), true, false);
        WallpaperPickerPage page = openWithPhotos(34, null);
        View again = find(page, R.id.wallpaper_picker_living_again);
        assertEquals(View.VISIBLE, again.getVisibility());
        assertTrue(again.getHeight() >= dp(48));
        assertInside(page.root(), page.root());
        again.performClick();
        assertTrue(mSlots.job.isRunning());
        assertFalse("one run at a time", again.isEnabled());
        runWorker();
        assertEquals(mManifest.image(), mScript.photoSeen);
        assertEquals("the still stays the choice", mManifest.wallpaperId(),
            page.pending(WallpaperSlots.Slot.HOME).animatedId);
        assertFalse(mSlots.job.isRunning());
    }

    @Test
    public void aPageOpenedMidRunShowsTheBarAndClosingItDoesNotStopTheRun() {
        withLiving();
        File photo = photoFile("midrun.png");
        assertTrue(mSlots.job.start(photo));
        WallpaperPickerPage page = openOnPhoto(WallpaperSlots.Slot.HOME, photo, 34);
        assertEquals("re-attached to the run", View.VISIBLE, find(page, R.id.wallpaper_picker_living_working).getVisibility());
        assertEquals(View.GONE, find(page, R.id.wallpaper_picker_living_offer).getVisibility());

        page.release();
        assertTrue("the run goes on without the page", mSlots.job.isRunning());
        runWorker();
        assertEquals(photo, mScript.photoSeen);
        assertFalse(mSlots.job.isRunning());
        assertNull("the closed page took no result", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void readAgainIsTheAiStarIconButton() {
        withLiving();
        mSlots.state = new WallpaperSlots.State(WallpaperSlots.Choice.animated(mManifest.wallpaperId()),
            WallpaperSlots.Choice.sameAsHome(), true, false);
        WallpaperPickerPage page = openWithPhotos(34, null);
        MaterialButton again = find(page, R.id.wallpaper_picker_living_again);
        assertEquals(View.VISIBLE, again.getVisibility());
        assertTrue("an icon, not a text button", again.getIcon() != null);
        assertEquals("no words on it", "", again.getText().toString());
        assertEquals("Read again", again.getContentDescription().toString());
        assertEquals(dp(48), again.getWidth());
    }

    @Test
    public void anotherPhotosRunLeavesTheButtonWaiting() {
        withLiving();
        assertTrue(mSlots.job.start(photoFile("busy-other.png")));
        WallpaperPickerPage page = openOnPhoto(WallpaperSlots.Slot.HOME, photoFile("busy-this.png"), 34);
        View offer = find(page, R.id.wallpaper_picker_living_offer);
        assertEquals(View.VISIBLE, offer.getVisibility());
        assertFalse("one job at a time", offer.isEnabled());
        assertEquals(View.GONE, find(page, R.id.wallpaper_picker_living_working).getVisibility());
        runWorker();
    }

    @Test
    public void moreSettingsOpensTheLookPage() {
        WallpaperPickerPage page = open(34);
        View more = find(page, R.id.wallpaper_picker_more_settings);
        assertTrue("a 48dp target", more.getHeight() >= dp(48) && more.getWidth() >= dp(48));
        assertInside(page.root(), more);
        more.performClick();
        assertEquals(Arrays.asList("more settings"), mSlots.calls);
        assertFalse("the page stays open", page.root().findViewById(R.id.wallpaper_picker_apply) == null);
    }

    /** Every shown view lies inside the root, and no shown text is cut short. */
    private static void assertInside(@NonNull View root, @NonNull View v) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof RecyclerView) return; // the peeking neighbour is meant to run past the edge
        Rect r = new Rect(0, 0, v.getWidth(), v.getHeight());
        if (v != root) ((ViewGroup) root).offsetDescendantRectToMyCoords(v, r);
        String name = v.getId() == View.NO_ID ? v.getClass().getSimpleName()
            : v.getResources().getResourceEntryName(v.getId());
        assertTrue(name + " inside the page: " + r, r.left >= 0 && r.top >= 0
            && r.right <= root.getWidth() && r.bottom <= root.getHeight());
        if (v instanceof TextView) {
            Layout layout = ((TextView) v).getLayout();
            if (layout != null && layout.getLineCount() > 0) {
                assertEquals(name + " is whole", 0, layout.getEllipsisCount(layout.getLineCount() - 1));
            }
        }
        // The strip scrolls sideways: its tiles may run past the edge.
        if (v instanceof ViewGroup && !(v instanceof android.widget.HorizontalScrollView)) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) assertInside(root, g.getChildAt(i));
        }
    }

    @Test
    public void applyAndItsMoreButtonSitInOneRowUnderTheCardsAboveMotion() {
        WallpaperPickerPage page = open(34);
        settle();
        View root = page.root();
        View pager = root.findViewById(R.id.wallpaper_picker_pager);
        View row = root.findViewById(R.id.wallpaper_picker_apply_row);
        View apply = root.findViewById(R.id.wallpaper_picker_apply);
        View more = root.findViewById(R.id.wallpaper_picker_apply_more);
        View motion = root.findViewById(R.id.wallpaper_picker_motion_row);
        assertSame("both are in the row", row, apply.getParent());
        assertSame(row, more.getParent());
        assertTrue("under the cards", row.getTop() >= pager.getBottom());
        assertTrue("above the Motion row", row.getBottom() <= motion.getTop());
        assertTrue("one row, side by side", more.getLeft() > apply.getLeft());
        assertTrue(page.leavingViews().contains(row));
    }
}
