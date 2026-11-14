package com.termux.app.terminal.extrakeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

/** A page changing key set: which keys carry over, who is drawn while it plays, what is left after. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, application = android.app.Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ExtraKeysPageMorphTest {

    private static final int W = 400;
    private static final int H = 100;

    private ExtraKeysPageView page;
    private int ended;

    @Before
    public void setUp() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        activity.setContentView(root);
        page = new ExtraKeysPageView(activity);
        root.addView(page);
        page.setKeys(info("[['A','B','C']]"));
        layout();
    }

    @Test
    public void aSharedKeyKeepsItsViewAndTheOthersAreNewOrGoing() throws Exception {
        ExtraKeyCapView b = page.capForKey("B");
        ExtraKeyCapView a = page.capForKey("A");
        page.morphTo(info("[['B','D']]"), () -> ended++);
        assertEquals(2, page.keyCount());
        assertSame(b, page.capForKey("B"));
        assertNotNull(page.capForKey("D"));
        assertNull("a leaving key takes no touch", page.capForKey("A"));
        assertSame("it is still drawn", page, a.getParent());
        layout();
        page.getViewTreeObserver().dispatchOnPreDraw();
        // t=0: D has not appeared, B is still where it was, A is at full size.
        assertEquals(0f, page.capForKey("D").getAlpha(), 0.001f);
        assertEquals(0.5f, page.capForKey("D").getScaleX(), 0.001f);
        assertTrue(b.getTranslationX() != 0f);
        assertEquals(1f, a.getAlpha(), 0.001f);
        assertEquals("nothing finished yet", 0, ended);
        ShadowLooper.idleMainLooper(ExtraKeysSetMorph.MORPH_MS + 400, TimeUnit.MILLISECONDS);
        assertEquals(1, ended);
        assertNull("gone after the end", a.getParent());
        assertEquals(2, page.getChildCount());
        for (int i = 0; i < page.keyCount(); i++) {
            ExtraKeyCapView cap = page.capAt(i);
            assertEquals(0f, cap.getTranslationX(), 0f);
            assertEquals(0f, cap.getTranslationY(), 0f);
            assertEquals(1f, cap.getScaleX(), 0f);
            assertEquals(1f, cap.getScaleY(), 0f);
            assertEquals(1f, cap.getAlpha(), 0f);
        }
    }

    @Test
    public void aKeyWithADifferentSecondKeyIsANewView() throws Exception {
        ExtraKeyCapView b = page.capForKey("B");
        page.morphTo(info("[[{key: 'B', popup: {key: 'X', display: 'X'}}]]"), null);
        assertNotSame(b, page.capForKey("B"));
    }

    @Test
    public void aMorphCutShortByNewKeysSnapsAndSaysItEndedOnce() throws Exception {
        page.morphTo(info("[['B','D']]"), () -> ended++);
        layout();
        page.getViewTreeObserver().dispatchOnPreDraw();
        page.setKeys(info("[['Z']]"));
        assertEquals(1, ended);
        assertEquals(1, page.getChildCount());
        ShadowLooper.idleMainLooper(ExtraKeysSetMorph.MORPH_MS + 400, TimeUnit.MILLISECONDS);
        assertEquals("no second call from a dead animator", 1, ended);
    }

    @Test
    public void aPageThatIsNotShownJustChangesKeys() throws Exception {
        ExtraKeysPageView loose = new ExtraKeysPageView(page.getContext());
        loose.setKeys(info("[['A']]"));
        loose.morphTo(info("[['B']]"), () -> ended++);
        assertEquals(1, ended);
        assertEquals(1, loose.keyCount());
        assertNotNull(loose.capForKey("B"));
        assertEquals(1, loose.getChildCount());
    }

    @Test
    public void theOneShotHookIsGoneAfterTheEnd() throws Exception {
        page.morphTo(info("[['B']]"), () -> ended++);
        layout();
        page.getViewTreeObserver().dispatchOnPreDraw();
        ShadowLooper.idleMainLooper(ExtraKeysSetMorph.MORPH_MS + 400, TimeUnit.MILLISECONDS);
        assertEquals(1, ended);
        page.getViewTreeObserver().dispatchOnPreDraw();
        assertEquals(1, ended);
        assertEquals(1, page.getChildCount());
    }

    private void layout() {
        page.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, W, H);
    }

    private static ExtraKeysInfo info(String rows) throws Exception {
        return new ExtraKeysInfo(rows, ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
            ExtraKeysConstants.CONTROL_CHARS_ALIASES);
    }
}
