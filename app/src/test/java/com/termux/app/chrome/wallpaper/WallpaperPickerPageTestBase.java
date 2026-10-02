package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The wallpaper picker page at the subclass's width: it lays out with nothing cut off, the title
 * follows the centred slot, Same as Home shows only for Lock, Apply is enabled only when the
 * pending choice differs, and Motion is hidden below API 34. A fake {@link WallpaperPickerPage.Slots}
 * stands in for {@link WallpaperSlots}. Native graphics, so text has real metrics.
 */
public abstract class WallpaperPickerPageTestBase {

    /** A slot store in memory: apply and Motion succeed at once and are read back. */
    static final class FakeSlots implements WallpaperPickerPage.Slots {
        WallpaperSlots.State state = new WallpaperSlots.State(
            WallpaperSlots.Choice.animated("aurora"), WallpaperSlots.Choice.sameAsHome(), true, false);
        final List<String> calls = new ArrayList<>();

        @NonNull @Override public WallpaperSlots.State read() {
            return state;
        }

        @Override public void apply(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice,
                                    @Nullable WallpaperSlots.Callback cb) {
            calls.add("apply " + slot);
            state = slot == WallpaperSlots.Slot.HOME
                ? new WallpaperSlots.State(choice, state.lock, state.lockMotion, state.lockLiveActive)
                : new WallpaperSlots.State(state.home, choice, state.lockMotion, state.lockLiveActive);
            if (cb != null) cb.onDone(true, null);
        }

        @Override public void setLockMotion(boolean on, @Nullable WallpaperSlots.Callback cb) {
            calls.add("motion " + on);
            state = new WallpaperSlots.State(state.home, state.lock, on, state.lockLiveActive);
            if (cb != null) cb.onDone(true, null);
        }
    }

    static final class RecordingListener implements WallpaperPickerPage.Listener {
        final List<String> calls = new ArrayList<>();

        @Override public void onPageShown(boolean shown) { calls.add("shown " + shown); }
        @Override public void onPickPhoto(@NonNull WallpaperSlots.Slot slot) { calls.add("photo " + slot); }
        @Override public void onApplied(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice) {
            calls.add("applied " + slot);
        }
        @Override public void onOpenLook() { calls.add("look"); }
        @Override public void onOpenIconPack() { calls.add("icon pack"); }
        @Override public void onOpenLayout() { calls.add("layout"); }
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
        WallpaperPickerPage page = new WallpaperPickerPage(mThemed, mSlots, mListener, sdk, () -> {});
        mActivity.setContentView(page.root());
        settle();
        return page;
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
        View card = ((ViewGroup) cell).getChildAt(0);
        assertTrue("the card is sized", card.getHeight() > dp(100) && card.getWidth() > dp(40));
        assertTrue("the card fits the pager", card.getHeight() <= pager.getHeight());

        for (int id : new int[] {R.id.wallpaper_picker_back, R.id.wallpaper_picker_apply,
            R.id.wallpaper_picker_look, R.id.wallpaper_picker_icon_pack, R.id.wallpaper_picker_layout,
            R.id.wallpaper_picker_photo, R.id.wallpaper_picker_motion}) {
            View v = root.findViewById(id);
            assertTrue(v.getResources().getResourceEntryName(id) + " is a 48dp target",
                v.getHeight() >= dp(48) && v.getWidth() >= dp(48));
        }
        assertEquals("ten backgrounds and Same as Home", AnimatedWallpapers.all().size() + 1, page.tileCount());

        page.centre(WallpaperSlots.Slot.HOME);
        settle();
        assertInside(root, root);
    }

    @Test
    public void theTitleFollowsTheCentredSlot() {
        WallpaperPickerPage page = open(34);
        TextView title = page.root().findViewById(R.id.wallpaper_picker_title);
        assertEquals(mThemed.getString(R.string.wallpaper_picker_slot_lock), title.getText().toString());
        page.centre(WallpaperSlots.Slot.HOME);
        assertEquals(mThemed.getString(R.string.wallpaper_picker_slot_home), title.getText().toString());
        page.centre(WallpaperSlots.Slot.LOCK);
        assertEquals(mThemed.getString(R.string.wallpaper_picker_slot_lock), title.getText().toString());
    }

    @Test
    public void sameAsHomeShowsOnlyForLock() {
        WallpaperPickerPage page = open(34);
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
    public void motionShowsOnLockFromApi34() {
        WallpaperPickerPage page = open(34);
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
    public void shortcutsAndPhotoCloseThePageFirst() {
        WallpaperPickerPage page = open(34);
        page.root().findViewById(R.id.wallpaper_picker_photo).performClick();
        assertEquals("shown false", mListener.calls.get(mListener.calls.size() - 2));
        assertEquals("photo LOCK", mListener.calls.get(mListener.calls.size() - 1));
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
