package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Looper;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.Robolectric;

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
 * the page holds pending changes only when a card's choice differs from its slot, committing
 * applies each card's own choice to its own slot (Home first), there is no Apply row, and the
 * cards sit directly above the strip. The strip holds Same as Home and the recent photos, evenly
 * spaced, and no pre-made backgrounds. Photos: a cropped photo comes back pending and the commit
 * sets it, the current photo shows in its card, and releasing uncommitted discards the pending
 * file. A fake
 * {@link WallpaperPickerPage.Slots} stands in for {@link WallpaperSlots}, and reads run inline.
 * Native graphics, so text has real metrics.
 */
public abstract class WallpaperPickerPageTestBase {

    /** A slot store in memory: apply succeeds at once and is read back. */
    static final class FakeSlots implements WallpaperPickerPage.Slots {
        WallpaperSlots.State state = new WallpaperSlots.State(
            WallpaperSlots.Choice.photo(), WallpaperSlots.Choice.sameAsHome());
        final List<String> calls = new ArrayList<>();
        /** The choice of each apply, in {@link #calls}' order. */
        final List<WallpaperSlots.Choice> applied = new ArrayList<>();
        List<File> recents = Collections.emptyList();
        final List<File> discarded = new ArrayList<>();
        /** Where an applied photo is "kept": the stored choice names this, not the pending file. */
        @Nullable File keptPhoto;
        /** Every apply reports failure while set. */
        boolean fail;

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
            if (fail) {
                if (cb != null) cb.onDone(false, "failed");
                return;
            }
            if (choice.photo && choice.photoFile != null && keptPhoto != null) {
                choice = WallpaperSlots.Choice.photo(keptPhoto);
            }
            state = slot == WallpaperSlots.Slot.HOME
                ? new WallpaperSlots.State(choice, state.lock)
                : new WallpaperSlots.State(state.home, choice);
            if (cb != null) cb.onDone(true, null);
        }

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
    private WallpaperPickerPage open() {
        return open(null);
    }

    @NonNull
    private WallpaperPickerPage open(@Nullable WallpaperPickerPage.ReturnState restore) {
        WallpaperPickerPage page = new WallpaperPickerPage(mThemed, mSlots, mListener, () -> {}, restore);
        mActivity.setContentView(page.root());
        settle();
        return page;
    }

    /** Photos decode on the calling thread to a plain bitmap; {@link #mDecoded} lists the files. */
    @NonNull
    private WallpaperPickerPage openWithPhotos(@Nullable WallpaperPickerPage.ReturnState restore) {
        WallpaperThumbs thumbs = new WallpaperThumbs(Runnable::run, (file, w, h) -> {
            mDecoded.add(file);
            return Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        });
        WallpaperPickerPage page = new WallpaperPickerPage(mThemed, mSlots, mListener, () -> {}, restore, thumbs);
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

    @NonNull
    private WallpaperSlots.Choice picture(@NonNull String name) {
        return WallpaperSlots.Choice.photo(photoFile(name));
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
        WallpaperPickerPage page = open();
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

        View photo = root.findViewById(R.id.wallpaper_picker_photo);
        assertTrue("Photo is a 48dp target", photo.getHeight() >= dp(48) && photo.getWidth() >= dp(48));
        assertEquals("Same as Home alone: no backgrounds, no recents", 1, page.tileCount());

        page.centre(WallpaperSlots.Slot.HOME);
        settle();
        assertInside(root, root);
    }

    @Test
    public void thePageHasNoBarOfItsOwnAndEachCardCarriesItsOwnLabel() {
        WallpaperPickerPage page = open();
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
    public void thePageIsReadyOnlyOnceTheCentredCardStandsAtItsSize() {
        // Waydroid: the page faded in before its cards were sized, empty but for "LH" (the two
        // labels' first letters, in 1x1 cards' cells) at its edge.
        WallpaperPickerPage page = new WallpaperPickerPage(mThemed, mSlots, mListener, () -> {}, null);
        mActivity.setContentView(page.root());
        boolean[] ready = {false};
        page.whenReady(() -> ready[0] = true);
        assertFalse("nothing is laid out yet", ready[0]);
        assertNull("no card to grow the editor's frame out of", page.sharedCard());
        settle();
        assertTrue("ready once the cards are sized", ready[0]);
        WallpaperPreviewView card = page.card(WallpaperSlots.Slot.HOME);
        assertTrue("the centred card at its size", card != null && card.getWidth() > dp(40));
        assertEquals("and that card is the shared one", card, page.sharedCard());
        View cell = (View) card.getParent();
        assertEquals(View.VISIBLE, cell.getVisibility());

        boolean[] again = {false};
        page.whenReady(() -> again[0] = true);
        assertTrue("already ready: at once", again[0]);
    }

    @Test
    public void sameAsHomeShowsOnlyForLock() {
        WallpaperPickerPage page = open();
        page.centre(WallpaperSlots.Slot.LOCK);
        assertEquals(View.VISIBLE, page.sameAsHomeTile().getVisibility());
        page.centre(WallpaperSlots.Slot.HOME);
        assertEquals(View.GONE, page.sameAsHomeTile().getVisibility());
        // Choosing it on Home is refused.
        page.choose(WallpaperSlots.Choice.sameAsHome());
        assertFalse(page.pending(WallpaperSlots.Slot.HOME).sameAsHome);
    }

    /** Commits the page's pending choices and returns "done", "failed" or "waiting" by what ran. */
    @NonNull
    private static String commit(@NonNull WallpaperPickerPage page) {
        String[] out = {"waiting"};
        page.commitPending(() -> out[0] = "done", () -> out[0] = "failed");
        return out[0];
    }

    @Test
    public void thePageHoldsPendingChangesOnlyWhenAChoiceDiffers() {
        WallpaperPickerPage page = open();
        assertFalse("nothing chosen", page.hasPendingChanges());
        page.centre(WallpaperSlots.Slot.LOCK);
        page.choose(picture("one.png"));
        assertTrue(page.hasPendingChanges());
        page.choose(WallpaperSlots.Choice.sameAsHome());
        assertFalse("Lock holds what is stored again", page.hasPendingChanges());

        page.centre(WallpaperSlots.Slot.HOME);
        page.choose(WallpaperSlots.Choice.photo());
        assertFalse("a photo with no picture is the stored one", page.hasPendingChanges());
        File two = photoFile("two.png");
        page.choose(WallpaperSlots.Choice.photo(two));
        assertTrue(page.hasPendingChanges());

        // Swiping keeps each slot's pending choice.
        page.centre(WallpaperSlots.Slot.LOCK);
        assertTrue(page.pending(WallpaperSlots.Slot.LOCK).sameAsHome);
        page.centre(WallpaperSlots.Slot.HOME);
        assertEquals(two, page.pending(WallpaperSlots.Slot.HOME).photoFile);
    }

    @Test
    public void nothingPendingCommitsAtOnceAndAppliesNothing() {
        WallpaperPickerPage page = open();
        assertEquals("done", commit(page));
        assertTrue(mSlots.calls.isEmpty());
    }

    @Test
    public void aHomeOnlyPickGoesToHomeAndALockThatFollowsIsCoveredByThePlan() {
        mSlots.state = new WallpaperSlots.State(picture("home.png"), WallpaperSlots.Choice.sameAsHome());
        WallpaperPickerPage page = open();
        File chosen = photoFile("chosen.png");
        page.choose(WallpaperSlots.Choice.photo(chosen));
        assertTrue(page.hasPendingChanges());

        assertEquals("done", commit(page));
        assertEquals("Lock already follows Home: one apply", Arrays.asList("apply HOME"), mSlots.calls);
        assertEquals(chosen, mSlots.applied.get(0).photoFile);
        assertTrue(mListener.calls.contains("applied HOME"));
        assertTrue(mSlots.state.lock.sameAsHome);
        assertFalse("stored now matches", page.hasPendingChanges());
    }

    @Test
    public void aLockOnlyPickGoesToLockAlone() {
        File homePicture = photoFile("stored-home.png");
        mSlots.state = new WallpaperSlots.State(WallpaperSlots.Choice.photo(homePicture),
            WallpaperSlots.Choice.sameAsHome());
        WallpaperPickerPage page = open();
        page.centre(WallpaperSlots.Slot.LOCK);
        File chosen = photoFile("lock-pick.png");
        page.choose(WallpaperSlots.Choice.photo(chosen));

        assertEquals("done", commit(page));
        assertEquals(Arrays.asList("apply LOCK"), mSlots.calls);
        assertEquals(chosen, mSlots.applied.get(0).photoFile);
        assertEquals("Home untouched", homePicture, mSlots.state.home.photoFile);
        assertEquals(chosen, mSlots.state.lock.photoFile);
    }

    @Test
    public void bothPicksGoToTheirOwnSlotsHomeFirst() {
        mSlots.state = new WallpaperSlots.State(picture("home.png"), picture("lock.png"));
        WallpaperPickerPage page = open();
        File home = photoFile("home-pick.png");
        File lock = photoFile("lock-pick.png");
        page.choose(WallpaperSlots.Choice.photo(home));
        page.centre(WallpaperSlots.Slot.LOCK);
        page.choose(WallpaperSlots.Choice.photo(lock));

        assertEquals("done", commit(page));
        assertEquals(Arrays.asList("apply HOME", "apply LOCK"), mSlots.calls);
        assertEquals(home, mSlots.applied.get(0).photoFile);
        assertEquals(lock, mSlots.applied.get(1).photoFile);
        assertTrue(mListener.calls.contains("applied HOME"));
        assertTrue(mListener.calls.contains("applied LOCK"));
        assertFalse(page.hasPendingChanges());
    }

    @Test
    public void lockSameAsHomeAfterAHomePhotoCopiesThePhoto() {
        mSlots.state = new WallpaperSlots.State(picture("home.png"), picture("lock.png"));
        WallpaperPickerPage page = open();
        File chosen = photoFile("chosen-2.png");
        page.choose(WallpaperSlots.Choice.photo(chosen));
        page.centre(WallpaperSlots.Slot.LOCK);
        page.choose(WallpaperSlots.Choice.sameAsHome());

        assertEquals("done", commit(page));
        assertEquals(Arrays.asList("apply HOME", "apply LOCK"), mSlots.calls);
        assertEquals(chosen, mSlots.applied.get(0).photoFile);
        assertTrue("Lock follows Home", mSlots.applied.get(1).sameAsHome);
        assertTrue(mSlots.state.lock.sameAsHome);
        assertTrue(page.pending(WallpaperSlots.Slot.LOCK).sameAsHome);
    }

    @Test
    public void aFailedApplyReportsFailureAndLeavesThePickPending() {
        WallpaperPickerPage page = open();
        page.choose(picture("will-fail.png"));
        mSlots.fail = true;
        assertEquals("failed", commit(page));
        assertTrue("still pending", page.hasPendingChanges());
        mSlots.fail = false;
        assertEquals("a second try works", "done", commit(page));
        assertFalse(page.hasPendingChanges());
    }

    @Test
    public void aReleasedPageDoesNotHangTheCommit() {
        WallpaperPickerPage page = open();
        page.choose(picture("late.png"));
        page.release();
        assertEquals("failed", commit(page));
    }

    @Test
    public void thePendingChoiceSurvivesAHideAndThereIsNoApplyRow() {
        WallpaperPickerPage page = open();
        page.centre(WallpaperSlots.Slot.HOME);
        File chosen = photoFile("kept.png");
        page.choose(WallpaperSlots.Choice.photo(chosen));
        assertEquals("the shortcut row is gone", 0, page.root().getResources().getIdentifier(
            "wallpaper_picker_shortcuts", "id", page.root().getContext().getPackageName()));
        assertEquals("the Apply row is gone", 0, page.root().getResources().getIdentifier(
            "wallpaper_picker_apply_row", "id", page.root().getContext().getPackageName()));
        // The page is hidden behind the editor, not rebuilt: its pending choices are where they were.
        page.onHidden();
        page.onShown();
        assertEquals(chosen, page.pending(WallpaperSlots.Slot.HOME).photoFile);
        assertTrue("still pending, not applied", mSlots.calls.isEmpty());
        assertTrue(page.hasPendingChanges());
    }

    @Test
    public void theCardsFillTheSpaceAboveTheStrip() {
        WallpaperPickerPage page = open();
        View root = page.root();
        View pager = root.findViewById(R.id.wallpaper_picker_pager);
        View strip = root.findViewById(R.id.wallpaper_picker_strip_card);
        assertTrue("cards end above the strip card", pager.getBottom() <= strip.getTop());
        assertTrue("with no row between them", strip.getTop() - pager.getBottom() <= dp(8));
        assertEquals(Collections.singletonList(strip), page.leavingViews());
    }

    @Test
    public void photoHandsTheSurfaceItsStateToComeBackTo() {
        WallpaperPickerPage page = open();
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
    public void aCroppedPhotoComesBackPendingAndCommitSetsIt() {
        WallpaperPickerPage page = open();
        page.root().findViewById(R.id.wallpaper_picker_photo).performClick();
        assertEquals("photo HOME", mListener.calls.get(mListener.calls.size() - 1));
        WallpaperPickerPage.ReturnState back = mListener.back;
        assertTrue(back != null);

        File cropped = photoFile("pending.png");
        WallpaperPickerPage.ReturnState restore =
            WallpaperPickerPage.ReturnState.withPhoto(back, WallpaperSlots.Slot.HOME, cropped, mSlots.state);
        mSlots.keptPhoto = photoFile("exact.png");
        WallpaperPickerPage again = open(restore);
        assertEquals(WallpaperSlots.Slot.HOME, again.centredSlot());
        assertEquals(cropped, again.pending(WallpaperSlots.Slot.HOME).photoFile);
        assertTrue("previewed, not applied", mSlots.calls.isEmpty());
        assertTrue("a pending photo is a pending change", again.hasPendingChanges());

        assertEquals("done", commit(again));
        assertEquals("Lock already follows Home", Arrays.asList("apply HOME"), mSlots.calls);
        assertTrue(mSlots.applied.get(0).photo);
        assertEquals(cropped, mSlots.applied.get(0).photoFile);
        assertEquals("the page now shows what is kept", mSlots.keptPhoto,
            again.pending(WallpaperSlots.Slot.HOME).photoFile);
        assertFalse(again.hasPendingChanges());
    }

    @Test
    public void aLockOnlyPhotoSetsTheLockScreenAlone() {
        File cropped = photoFile("lock-pending.png");
        File homePicture = photoFile("stored-home.png");
        mSlots.state = new WallpaperSlots.State(WallpaperSlots.Choice.photo(homePicture),
            WallpaperSlots.Choice.sameAsHome());
        WallpaperPickerPage.ReturnState restore = WallpaperPickerPage.ReturnState.withPhoto(null,
            WallpaperSlots.Slot.LOCK, cropped, mSlots.state);
        WallpaperPickerPage page = open(restore);
        assertEquals(WallpaperSlots.Slot.LOCK, page.centredSlot());
        assertTrue(page.hasPendingChanges());
        assertEquals("done", commit(page));
        assertEquals(Arrays.asList("apply LOCK"), mSlots.calls);
        assertEquals(cropped, mSlots.applied.get(0).photoFile);
        assertEquals("Home untouched", homePicture, mSlots.state.home.photoFile);
    }

    @Test
    public void thePhotoReturnStateRoundTrips() {
        WallpaperPickerPage page = open();
        page.centre(WallpaperSlots.Slot.LOCK);
        File lockPick = photoFile("lock-pick-2.png");
        page.choose(WallpaperSlots.Choice.photo(lockPick));
        page.centre(WallpaperSlots.Slot.HOME);
        page.root().findViewById(R.id.wallpaper_picker_photo).performClick();
        WallpaperPickerPage.ReturnState back = mListener.back;
        assertTrue(back != null);
        assertEquals(WallpaperSlots.Slot.HOME, back.centred);
        assertEquals(lockPick, back.pendingLock.photoFile);
        assertTrue("Photo… is a hand-off: nothing is discarded", mSlots.discarded.isEmpty());

        File cropped = photoFile("round-trip.png");
        WallpaperPickerPage.ReturnState restore =
            WallpaperPickerPage.ReturnState.withPhoto(back, WallpaperSlots.Slot.HOME, cropped, mSlots.state);
        WallpaperPickerPage again = open(restore);
        assertEquals("the other slot keeps its pending choice", lockPick,
            again.pending(WallpaperSlots.Slot.LOCK).photoFile);
        WallpaperPickerPage.ReturnState out = again.returnState();
        assertEquals(WallpaperSlots.Slot.HOME, out.centred);
        assertEquals(cropped, out.pendingHome.photoFile);
        assertEquals(lockPick, out.pendingLock.photoFile);

        // A cancelled pick comes back with back itself: nothing changed.
        WallpaperPickerPage cancelled = open(back);
        assertNull(cancelled.pending(WallpaperSlots.Slot.HOME).photoFile);
    }

    @Test
    public void releasingUncommittedDiscardsThePendingPhoto() {
        File cropped = photoFile("discard.png");
        WallpaperPickerPage page = open(WallpaperPickerPage.ReturnState.withPhoto(null,
            WallpaperSlots.Slot.HOME, cropped, mSlots.state));
        page.release();
        assertTrue(mSlots.calls.isEmpty());
        assertEquals(Collections.singletonList(cropped), mSlots.discarded);
    }

    @Test
    public void theCurrentPhotoShowsInItsCard() {
        File exact = photoFile("current.png");
        mSlots.state = new WallpaperSlots.State(WallpaperSlots.Choice.photo(exact),
            WallpaperSlots.Choice.sameAsHome());
        WallpaperPickerPage page = openWithPhotos(null);
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
        WallpaperPickerPage page = openWithPhotos(null);
        assertEquals(2, page.recentTileCount());
        assertEquals("Same as Home and the recents: no pre-made backgrounds",
            WallpaperPickerLogic.stripTileCount(2, RecentWallpapers.MAX), page.tileCount());
        assertEquals(3, page.tileCount());
        assertTrue("thumbnails decoded", mDecoded.containsAll(Arrays.asList(a, b)));

        page.choose(WallpaperSlots.Choice.photo(b));
        assertTrue(page.hasPendingChanges());
        assertEquals("done", commit(page));
        assertEquals("no new crop: the recent file itself", b, mSlots.applied.get(0).photoFile);
    }

    @Test
    public void theStripsTilesAreEvenlySpacedAcrossTheRow() {
        mSlots.recents = Arrays.asList(photoFile("e1.png"), photoFile("e2.png"), photoFile("e3.png"));
        WallpaperPickerPage page = openWithPhotos(null);
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
    public void sameAsHomeStillWorksForLockWithRecentPhotos() {
        mSlots.state = new WallpaperSlots.State(WallpaperSlots.Choice.photo(photoFile("home.png")),
            WallpaperSlots.Choice.sameAsHome());
        mSlots.recents = Arrays.asList(photoFile("r1.png"), photoFile("r2.png"), photoFile("r3.png"));
        WallpaperPickerPage page = openWithPhotos(null);
        View root = page.root();
        assertEquals(3, page.recentTileCount());
        assertEquals("Same as Home and the recents", 1 + 3, page.tileCount());
        View photo = root.findViewById(R.id.wallpaper_picker_photo);
        assertTrue("Photo… is there", photo.isShown());
        assertTrue(photo.getHeight() >= dp(48));
        assertTrue(page.card(WallpaperSlots.Slot.HOME) != null);
        assertInside(root, root);

        page.centre(WallpaperSlots.Slot.LOCK);
        assertEquals(View.VISIBLE, page.sameAsHomeTile().getVisibility());
        page.choose(WallpaperSlots.Choice.photo(mSlots.recents.get(0)));
        assertEquals("done", commit(page));
        page.choose(WallpaperSlots.Choice.sameAsHome());
        assertEquals("done", commit(page));
        assertEquals(Arrays.asList("apply LOCK", "apply LOCK"), mSlots.calls);
        assertTrue(mSlots.state.lock.sameAsHome);
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
}
