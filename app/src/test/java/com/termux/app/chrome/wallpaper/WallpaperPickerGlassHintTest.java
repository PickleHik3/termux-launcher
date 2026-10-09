package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

/**
 * The Overview's glass hint on the smallest phone it has to fit, 360 x 640 dp at 1.3x text: it
 * shows while the launcher's own wallpaper is not on screen, goes once it is (on opening, and after
 * Done sets it), and the pager gives up the line's height, so the cards never overlap the strip.
 * Native graphics, so text has real metrics.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WallpaperPickerGlassHintTest {

    private Activity mActivity;
    private ContextThemeWrapper mThemed;
    private WallpaperPickerPageTestBase.FakeSlots mSlots;

    @Before
    public void setUp() {
        RuntimeEnvironment.setFontScale(1.3f);
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mThemed = new ContextThemeWrapper(mActivity, R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        mSlots = new WallpaperPickerPageTestBase.FakeSlots();
    }

    @NonNull
    private WallpaperPickerPage open() {
        WallpaperPickerPage page = new WallpaperPickerPage(mThemed, mSlots,
            new WallpaperPickerPageTestBase.RecordingListener(), () -> {}, null);
        mActivity.setContentView(page.root());
        settle();
        return page;
    }

    private void settle() {
        for (int i = 0; i < 4; i++) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
    }

    @NonNull
    private File photoFile(@NonNull String name) {
        try {
            File dir = new File(mActivity.getCacheDir(), "glass-hint-test");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no dir");
            File f = new File(dir, name);
            if (!f.isFile() && !f.createNewFile()) throw new IOException("no file");
            return f;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @NonNull
    private static Rect rectIn(@NonNull View root, @NonNull View v) {
        Rect r = new Rect(0, 0, v.getWidth(), v.getHeight());
        if (v != root) ((ViewGroup) root).offsetDescendantRectToMyCoords(v, r);
        return r;
    }

    @Test
    public void theHintShowsWhileTheWallpaperIsSetElsewhere() {
        WallpaperPickerPage page = open();
        View hint = page.glassHint();
        assertEquals(View.VISIBLE, hint.getVisibility());
        assertEquals(mThemed.getString(R.string.wallpaper_picker_glass_hint),
            ((TextView) hint).getText().toString());
        assertEquals("one line", 1, ((TextView) hint).getLineCount());

        View root = page.root();
        View strip = root.findViewById(R.id.wallpaper_picker_strip_card);
        Rect hintRect = rectIn(root, hint);
        Rect stripRect = rectIn(root, strip);
        assertTrue("the hint stands inside the strip card: " + hintRect + " in " + stripRect,
            stripRect.contains(hintRect));
        assertTrue("the strip card is on the page", stripRect.bottom <= root.getHeight());
        assertTrue("the hint is under Choose photo", rectIn(root,
            root.findViewById(R.id.wallpaper_picker_photo)).bottom <= hintRect.top);
        assertPagerClear(page);
    }

    @Test
    public void theHintIsGoneWhileTheWallpaperIsTheLaunchersOwn() {
        mSlots.state = new WallpaperSlots.State(WallpaperSlots.Choice.photo(photoFile("home.png")),
            WallpaperSlots.Choice.sameAsHome());
        WallpaperPickerPage page = open();
        assertEquals(View.GONE, page.glassHint().getVisibility());
        assertPagerClear(page);
    }

    @Test
    public void theHintGoesOnceDoneSetsTheWallpaperHere() {
        WallpaperPickerPage page = open();
        assertEquals(View.VISIBLE, page.glassHint().getVisibility());
        int pagerWithHint = page.root().findViewById(R.id.wallpaper_picker_pager).getHeight();
        page.choose(WallpaperSlots.Choice.photo(photoFile("picked.png")));
        boolean[] done = {false};
        page.commitPending(() -> done[0] = true, () -> {});
        settle();
        assertTrue("applied", done[0]);
        assertEquals(View.GONE, page.glassHint().getVisibility());
        int pagerWithout = page.root().findViewById(R.id.wallpaper_picker_pager).getHeight();
        assertTrue("the pager takes the line back: " + pagerWithHint + " -> " + pagerWithout,
            pagerWithout > pagerWithHint);
        assertPagerClear(page);
    }

    /** The pager stands above the strip card and its cards inside the pager's height. */
    private void assertPagerClear(@NonNull WallpaperPickerPage page) {
        View root = page.root();
        RecyclerView pager = root.findViewById(R.id.wallpaper_picker_pager);
        View strip = root.findViewById(R.id.wallpaper_picker_strip_card);
        assertTrue("the pager ends above the strip card",
            rectIn(root, pager).bottom <= rectIn(root, strip).top);
        WallpaperPreviewView card = page.card(WallpaperSlots.Slot.HOME);
        assertTrue("the Home card is bound", card != null && card.getHeight() > 0);
        View cell = (View) card.getParent();
        assertTrue("the label and card fit the pager: " + cell.getHeight() + " in "
            + pager.getHeight(), card.getBottom() <= pager.getHeight()
            && card.getHeight() <= pager.getHeight());
    }
}
