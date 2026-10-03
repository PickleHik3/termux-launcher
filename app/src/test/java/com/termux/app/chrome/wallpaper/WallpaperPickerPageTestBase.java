package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
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
import com.termux.R;
import com.termux.app.launcher.data.IconPackChoices;
import com.termux.app.launcher.model.IconPackInfo;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.Robolectric;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The wallpaper picker page at the subclass's width: it lays out with nothing cut off, the title
 * follows the centred slot, Same as Home shows only for Lock, Apply is enabled only when the
 * pending choice differs, Apply puts the background on Home and points Lock at it while its menu
 * items apply one slot, Look and Layout hand back a state the page reopens at, the Icon pack menu
 * lists the packs on the page, the shortcut glyphs sit centred, and Motion is hidden below API 34. A fake {@link WallpaperPickerPage.Slots}
 * stands in for {@link WallpaperSlots}. Native graphics, so text has real metrics.
 */
public abstract class WallpaperPickerPageTestBase {

    /** A slot store in memory: apply and Motion succeed at once and are read back. */
    static final class FakeSlots implements WallpaperPickerPage.Slots {
        WallpaperSlots.State state = new WallpaperSlots.State(
            WallpaperSlots.Choice.animated("aurora"), WallpaperSlots.Choice.sameAsHome(), true, false);
        final List<String> calls = new ArrayList<>();
        /** The choice of each apply, in {@link #calls}' order. */
        final List<WallpaperSlots.Choice> applied = new ArrayList<>();

        @NonNull @Override public WallpaperSlots.State read() {
            return state;
        }

        @Override public void apply(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice,
                                    @Nullable WallpaperSlots.Callback cb) {
            calls.add("apply " + slot);
            applied.add(choice);
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
        @Nullable WallpaperPickerPage.ReturnState back;
        IconPackChoices.Listing packs = new IconPackChoices.Listing(IconPackChoices.entries("System icons",
            Arrays.asList(new IconPackInfo("com.example.arcticons", "Arcticons", 1, false),
                new IconPackInfo("com.example.lines", "Lines", 1, false)), false), 2);

        @Override public void onOpenLook(@NonNull WallpaperPickerPage.ReturnState back) {
            this.back = back;
            calls.add("look");
        }
        @Override public void onOpenLayout(@NonNull WallpaperPickerPage.ReturnState back) {
            this.back = back;
            calls.add("layout");
        }
        @NonNull @Override public IconPackChoices.Listing iconPacks() { return packs; }
        @Override public void onIconPackChosen(@NonNull String packageName) { calls.add("icon pack " + packageName); }
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
            R.id.wallpaper_picker_apply_more, R.id.wallpaper_picker_look, R.id.wallpaper_picker_icon_pack, R.id.wallpaper_picker_layout,
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
    public void lookHandsBackAStateThePageReopensAt() {
        WallpaperPickerPage page = open(34);
        page.centre(WallpaperSlots.Slot.HOME);
        page.choose(WallpaperSlots.Choice.animated("tide"));
        page.centre(WallpaperSlots.Slot.LOCK);
        page.choose(WallpaperSlots.Choice.animated("rain"));
        page.centre(WallpaperSlots.Slot.HOME);
        page.root().findViewById(R.id.wallpaper_picker_look).performClick();
        assertEquals("shown false", mListener.calls.get(mListener.calls.size() - 2));
        assertEquals("look", mListener.calls.get(mListener.calls.size() - 1));
        WallpaperPickerPage.ReturnState back = mListener.back;
        assertTrue(back != null);

        WallpaperPickerPage again = open(34, back);
        assertEquals(WallpaperSlots.Slot.HOME, again.centredSlot());
        assertEquals("tide", again.pending(WallpaperSlots.Slot.HOME).animatedId);
        assertEquals("rain", again.pending(WallpaperSlots.Slot.LOCK).animatedId);
        TextView title = again.root().findViewById(R.id.wallpaper_picker_title);
        assertEquals(mThemed.getString(R.string.wallpaper_picker_slot_home), title.getText().toString());
        assertTrue("still pending, not applied", mSlots.calls.isEmpty());
        assertTrue(again.root().findViewById(R.id.wallpaper_picker_apply).isEnabled());
    }

    @Test
    public void layoutHandsBackAStateToo() {
        WallpaperPickerPage page = open(34);
        page.choose(WallpaperSlots.Choice.animated("mesh"));
        page.root().findViewById(R.id.wallpaper_picker_layout).performClick();
        assertEquals("layout", mListener.calls.get(mListener.calls.size() - 1));
        WallpaperPickerPage again = open(34, mListener.back);
        assertEquals(WallpaperSlots.Slot.LOCK, again.centredSlot());
        assertEquals("mesh", again.pending(WallpaperSlots.Slot.LOCK).animatedId);
        assertEquals("aurora", again.pending(WallpaperSlots.Slot.HOME).animatedId);
    }

    @Test
    public void theIconPackMenuOpensOnThePage() {
        WallpaperPickerPage page = open(34);
        page.root().findViewById(R.id.wallpaper_picker_icon_pack).performClick();
        assertFalse("the page stays open", mListener.calls.contains("shown false"));
        PopupMenu popup = page.iconPackMenu();
        assertTrue("a menu is up", popup != null);
        Menu menu = popup.getMenu();
        assertEquals(3, menu.size());
        assertEquals("System icons", menu.getItem(0).getTitle().toString());
        assertEquals("Arcticons", menu.getItem(1).getTitle().toString());
        assertFalse(menu.getItem(0).isChecked());
        assertTrue("the pack in force is checked", menu.getItem(2).isChecked());

        assertTrue(menu.performIdentifierAction(menu.getItem(1).getItemId(), 0));
        assertEquals("icon pack com.example.arcticons", mListener.calls.get(mListener.calls.size() - 1));
        assertFalse("the page stays open", mListener.calls.contains("shown false"));
    }

    @Test
    public void theShortcutGlyphsSitCentred() {
        WallpaperPickerPage page = open(34);
        for (int id : new int[] {R.id.wallpaper_picker_look, R.id.wallpaper_picker_icon_pack,
            R.id.wallpaper_picker_layout}) {
            MaterialButton button = page.root().findViewById(id);
            String name = button.getResources().getResourceEntryName(id);
            Drawable icon = button.getIcon();
            assertTrue(name + " has a glyph", icon != null);
            Rect b = icon.getBounds();
            assertEquals(name + " glyph is 24dp", dp(24), b.width());
            // Where TextView draws a start (left) compound drawable: across from the left padding,
            // down from the top padding with the leftover height split evenly, then its bounds.
            float cx = button.getPaddingLeft() + b.left + b.width() / 2f;
            int top = button.getCompoundPaddingTop();
            int vspace = button.getHeight() - top - button.getCompoundPaddingBottom();
            float cy = top + (vspace - b.height()) / 2 + b.top + b.height() / 2f;
            assertEquals(name + " centred across", button.getWidth() / 2f, cx, 1f);
            assertEquals(name + " centred down", button.getHeight() / 2f, cy, 1f);
        }
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
    public void photoClosesThePageFirst() {
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
