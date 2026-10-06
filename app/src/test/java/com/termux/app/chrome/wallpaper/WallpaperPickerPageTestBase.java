package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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
 * Apply is enabled only when the pending choice differs, Apply puts the background on Home and
 * points Lock at it while its menu items apply one slot, there is no shortcut row, and Apply sits
 * in one row above the strip. The strip holds Same as Home and the recent photos, evenly spaced,
 * and no pre-made backgrounds. Photos: a cropped photo comes back pending and Apply sets it, the
 * current photo shows in its card, and releasing without Apply discards the pending file. A fake
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
                ? new WallpaperSlots.State(choice, state.lock)
                : new WallpaperSlots.State(state.home, choice);
            if (cb != null) cb.onDone(true, null);
        }

        @Override public void openMoreSettings() { calls.add("more settings"); }
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

        for (int id : new int[] {R.id.wallpaper_picker_apply, R.id.wallpaper_picker_apply_more}) {
            View v = root.findViewById(id);
            assertTrue(v.getResources().getResourceEntryName(id) + " is a 40dp button",
                v.getHeight() >= dp(40) && v.getWidth() >= dp(40));
        }
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

    @Test
    public void applyIsEnabledOnlyWhenThePendingChoiceDiffers() {
        WallpaperPickerPage page = open();
        page.centre(WallpaperSlots.Slot.LOCK);
        View apply = page.root().findViewById(R.id.wallpaper_picker_apply);
        assertFalse("Lock holds what is stored", apply.isEnabled());
        page.choose(picture("one.png"));
        assertTrue(apply.isEnabled());
        page.choose(WallpaperSlots.Choice.sameAsHome());
        assertFalse(apply.isEnabled());

        page.centre(WallpaperSlots.Slot.HOME);
        assertFalse("Home holds what is stored", apply.isEnabled());
        page.choose(WallpaperSlots.Choice.photo());
        assertFalse(apply.isEnabled());
        File two = photoFile("two.png");
        page.choose(WallpaperSlots.Choice.photo(two));
        assertTrue(apply.isEnabled());

        // Swiping keeps each slot's pending choice.
        page.centre(WallpaperSlots.Slot.LOCK);
        assertTrue(page.pending(WallpaperSlots.Slot.LOCK).sameAsHome);
        page.centre(WallpaperSlots.Slot.HOME);
        assertEquals(two, page.pending(WallpaperSlots.Slot.HOME).photoFile);

        apply.performClick();
        assertTrue(mSlots.calls.contains("apply HOME"));
        assertTrue(mListener.calls.contains("applied HOME"));
        assertFalse("stored now matches", apply.isEnabled());
    }

    @Test
    public void applyPutsThePhotoOnHomeAndLockFollows() {
        mSlots.state = new WallpaperSlots.State(picture("home.png"), picture("lock.png"));
        WallpaperPickerPage page = open();
        page.centre(WallpaperSlots.Slot.HOME);
        File chosen = photoFile("chosen.png");
        page.choose(WallpaperSlots.Choice.photo(chosen));
        View apply = page.root().findViewById(R.id.wallpaper_picker_apply);
        assertTrue(apply.isEnabled());

        apply.performClick();
        assertEquals(Arrays.asList("apply HOME", "apply LOCK"), mSlots.calls);
        assertEquals(chosen, mSlots.applied.get(0).photoFile);
        assertTrue("Lock follows Home", mSlots.applied.get(1).sameAsHome);
        assertTrue(mListener.calls.contains("applied HOME"));
        assertTrue(mListener.calls.contains("applied LOCK"));
        assertEquals(chosen, mSlots.state.home.photoFile);
        assertTrue(mSlots.state.lock.sameAsHome);
        assertTrue(page.pending(WallpaperSlots.Slot.LOCK).sameAsHome);
        assertFalse("both slots hold it now", apply.isEnabled());
    }

    @Test
    public void applyFromLockPutsLocksPickOnHomeToo() {
        WallpaperPickerPage page = open();
        File chosen = photoFile("lock-pick.png");
        page.choose(WallpaperSlots.Choice.photo(chosen));
        page.root().findViewById(R.id.wallpaper_picker_apply).performClick();
        assertEquals(Arrays.asList("apply HOME", "apply LOCK"), mSlots.calls);
        assertEquals(chosen, mSlots.applied.get(0).photoFile);
        assertTrue(mSlots.applied.get(1).sameAsHome);
    }

    @Test
    public void theMenuItemsApplyOneSlotOnly() {
        WallpaperPickerPage page = open();
        page.centre(WallpaperSlots.Slot.HOME);
        File first = photoFile("first.png");
        page.choose(WallpaperSlots.Choice.photo(first));
        assertTrue(page.slotOnlyEnabled(WallpaperSlots.Slot.HOME));
        assertTrue(page.slotOnlyEnabled(WallpaperSlots.Slot.LOCK));
        assertTrue(page.root().findViewById(R.id.wallpaper_picker_apply_more).isEnabled());

        page.applySlotOnly(WallpaperSlots.Slot.HOME);
        assertEquals(Arrays.asList("apply HOME"), mSlots.calls);
        assertEquals(first, mSlots.applied.get(0).photoFile);
        assertFalse("Home holds it now", page.slotOnlyEnabled(WallpaperSlots.Slot.HOME));

        File second = photoFile("second.png");
        page.choose(WallpaperSlots.Choice.photo(second));
        page.applySlotOnly(WallpaperSlots.Slot.LOCK);
        assertEquals(Arrays.asList("apply HOME", "apply LOCK"), mSlots.calls);
        assertEquals(second, mSlots.applied.get(1).photoFile);
        assertEquals("Home untouched", first, mSlots.state.home.photoFile);
        assertEquals(second, mSlots.state.lock.photoFile);
    }

    @Test
    public void theShortcutRowIsGoneAndThePendingChoiceSurvivesAHide() {
        WallpaperPickerPage page = open();
        page.centre(WallpaperSlots.Slot.HOME);
        File chosen = photoFile("kept.png");
        page.choose(WallpaperSlots.Choice.photo(chosen));
        assertEquals("the shortcut row is gone", 0, page.root().getResources().getIdentifier(
            "wallpaper_picker_shortcuts", "id", page.root().getContext().getPackageName()));
        // The page is hidden behind the editor, not rebuilt: its pending choices are where they were.
        page.onHidden();
        page.onShown();
        assertEquals(chosen, page.pending(WallpaperSlots.Slot.HOME).photoFile);
        assertTrue("still pending, not applied", mSlots.calls.isEmpty());
        assertTrue(page.root().findViewById(R.id.wallpaper_picker_apply).isEnabled());
    }

    @Test
    public void theCardsSitAboveTheApplyRowAndTheRowAboveTheStrip() {
        WallpaperPickerPage page = open();
        View root = page.root();
        View pager = root.findViewById(R.id.wallpaper_picker_pager);
        View row = root.findViewById(R.id.wallpaper_picker_apply_row);
        View strip = root.findViewById(R.id.wallpaper_picker_strip_card);
        assertTrue("cards end above the row", pager.getBottom() <= row.getTop());
        assertTrue("the row ends above the strip card", row.getBottom() <= strip.getTop());
        assertTrue("the row keeps its 60dp", row.getHeight() >= dp(60));
        View apply = root.findViewById(R.id.wallpaper_picker_apply);
        assertSame("Apply is in the row", row, apply.getParent());
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
    public void aCroppedPhotoComesBackPendingAndApplySetsIt() {
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
        File homePicture = photoFile("stored-home.png");
        mSlots.state = new WallpaperSlots.State(WallpaperSlots.Choice.photo(homePicture),
            WallpaperSlots.Choice.sameAsHome());
        WallpaperPickerPage.ReturnState restore = WallpaperPickerPage.ReturnState.withPhoto(null,
            WallpaperSlots.Slot.LOCK, cropped, mSlots.state);
        WallpaperPickerPage page = open(restore);
        assertEquals(WallpaperSlots.Slot.LOCK, page.centredSlot());
        assertTrue(page.slotOnlyEnabled(WallpaperSlots.Slot.LOCK));
        page.applySlotOnly(WallpaperSlots.Slot.LOCK);
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
    public void backWithoutApplyDiscardsThePendingPhoto() {
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
        View apply = page.root().findViewById(R.id.wallpaper_picker_apply);
        assertTrue(apply.isEnabled());
        apply.performClick();
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
        page.applySlotOnly(WallpaperSlots.Slot.LOCK);
        page.choose(WallpaperSlots.Choice.sameAsHome());
        page.applySlotOnly(WallpaperSlots.Slot.LOCK);
        assertEquals(Arrays.asList("apply LOCK", "apply LOCK"), mSlots.calls);
        assertTrue(mSlots.state.lock.sameAsHome);
    }

    @Test
    public void moreSettingsOpensTheLookPage() {
        WallpaperPickerPage page = open();
        View more = page.root().findViewById(R.id.wallpaper_picker_more_settings);
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
    public void applyAndItsMoreButtonSitInOneRowUnderTheCards() {
        WallpaperPickerPage page = open();
        settle();
        View root = page.root();
        View pager = root.findViewById(R.id.wallpaper_picker_pager);
        View row = root.findViewById(R.id.wallpaper_picker_apply_row);
        View apply = root.findViewById(R.id.wallpaper_picker_apply);
        View more = root.findViewById(R.id.wallpaper_picker_apply_more);
        assertSame("both are in the row", row, apply.getParent());
        assertSame(row, more.getParent());
        assertTrue("under the cards", row.getTop() >= pager.getBottom());
        assertTrue("one row, side by side", more.getLeft() > apply.getLeft());
        assertTrue(page.leavingViews().contains(row));
    }
}
