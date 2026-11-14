package com.termux.app.terminal.extrakeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;

import com.termux.app.terminal.extrakeys.ExtraKeysBarView.Transition;
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

/** The bar changing key set: which page is in front, the height it stands for, who is told when. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, application = android.app.Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ExtraKeysBarMorphTest {

    private static final int BASE_ROW_PX = 100;
    private static final int WIDTH = 500;
    private static final String ONE = "[['A','B']]";
    private static final String TWO = "[['A','B'],['C','D']]";

    private ExtraKeysBarView bar;
    private Activity activity;
    private int heightChanged;

    @Before
    public void setUp() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        activity.setContentView(root);
        bar = new ExtraKeysBarView(activity, null);
        root.addView(bar);
        bar.setRowMetrics(BASE_ROW_PX, 1f);
        bar.setKeySet(new ExtraKeysInfo[] {info(ONE), info(TWO)});
        layout(200);
        bar.setListener(new ExtraKeysBarView.Listener() {
            @Override
            public void onPageSelected(int page) {}

            @Override
            public void onCommittedHeightChanged() {
                heightChanged++;
            }
        });
    }

    private void play() {
        layout(200);
        for (ExtraKeysPageView page : bar.keyPages()) page.getViewTreeObserver().dispatchOnPreDraw();
        ShadowLooper.idleMainLooper(ExtraKeysSetMorph.MORPH_MS + 400, TimeUnit.MILLISECONDS);
    }

    // ---- Which page is in front.

    @Test
    public void reloadKeepsThePageInFront() throws Exception {
        bar.setCurrentItem(1, false);
        bar.setKeySet(new ExtraKeysInfo[] {info(ONE), info(TWO)}, 1f, Transition.RELOAD);
        assertEquals(1, bar.getCurrentItem());
    }

    @Test
    public void swapAndMorphBringTheFirstPageToTheFront() throws Exception {
        bar.setCurrentItem(1, false);
        bar.setKeySet(new ExtraKeysInfo[] {info(ONE), info(TWO)}, 1f, Transition.SWAP);
        assertEquals(0, bar.getCurrentItem());
        bar.setCurrentItem(1, false);
        bar.setKeySet(new ExtraKeysInfo[] {info(ONE), info(TWO)}, 1f, Transition.MORPH);
        assertEquals(0, bar.getCurrentItem());
        play();
    }

    @Test
    public void theTextInputPageStaysInFrontForEveryTransition() throws Exception {
        View input = new View(activity);
        bar.setTextInputPage(input);
        for (Transition transition : Transition.values()) {
            bar.setCurrentItem(bar.keyPageCount(), false);
            bar.setKeySet(new ExtraKeysInfo[] {info(ONE), info(TWO), info(ONE)}, 1f, transition);
            assertEquals(transition.name(), 3, bar.getCurrentItem());
            bar.setKeySet(new ExtraKeysInfo[] {info(ONE), info(TWO)}, 1f, transition);
            assertEquals(transition.name(), 2, bar.getCurrentItem());
        }
    }

    @Test
    public void theOldSignatureStillReloads() throws Exception {
        bar.setCurrentItem(1, false);
        bar.setKeySet(new ExtraKeysInfo[] {info(ONE), info(TWO), info(ONE)});
        assertEquals(1, bar.getCurrentItem());
        assertEquals(3, bar.keyPageCount());
    }

    // ---- Keys.

    @Test
    public void theFrontPageViewBecomesTheFirstKeyPageAndKeepsASharedKey() throws Exception {
        bar.setCurrentItem(1, false);
        ExtraKeysPageView front = bar.keyPage(1);
        ExtraKeyCapView a = front.capForKey("A");
        bar.setKeySet(new ExtraKeysInfo[] {info("[['A','Z']]"), info(ONE)}, 1f, Transition.MORPH);
        assertSame(front, bar.keyPage(0));
        assertSame(a, front.capForKey("A"));
        assertNotNull(front.capForKey("Z"));
        assertNull(front.capForKey("B"));
        assertEquals(2, bar.keyPageCount());
        play();
        assertEquals(1, front.getChildCount() - 1);
    }

    // ---- Height.

    @Test
    public void aTallerSetCommitsAtOnceAndSaysSoOnce() throws Exception {
        bar.setCurrentItem(0, false);
        heightChanged = 0;
        bar.setKeySet(new ExtraKeysInfo[] {info(TWO)}, 1f, Transition.MORPH);
        assertEquals(2, bar.committedRows());
        assertEquals(1, heightChanged);
        play();
        assertEquals(1, heightChanged);
    }

    @Test
    public void anEqualSetSaysNothing() throws Exception {
        bar.setCurrentItem(0, false);
        heightChanged = 0;
        bar.setKeySet(new ExtraKeysInfo[] {info("[['X','Y','Z']]")}, 1f, Transition.MORPH);
        assertEquals(1, bar.committedRows());
        play();
        assertEquals(0, heightChanged);
    }

    @Test
    public void aShorterSetKeepsTheOldHeightUntilTheMorphEnds() throws Exception {
        bar.setCurrentItem(1, false);
        heightChanged = 0;
        bar.setKeySet(new ExtraKeysInfo[] {info(ONE)}, 1f, Transition.MORPH);
        assertEquals("the old rows stand while the keys move", 2, bar.committedRows());
        assertEquals(1f, bar.committedScale(), 0f);
        assertEquals(0, heightChanged);
        play();
        assertEquals(1, bar.committedRows());
        assertEquals(1, heightChanged);
    }

    @Test
    public void aFurtherSetDuringAPendingShorterCommitCommitsFirst() throws Exception {
        bar.setCurrentItem(1, false);
        heightChanged = 0;
        bar.setKeySet(new ExtraKeysInfo[] {info(ONE)}, 1f, Transition.MORPH);
        assertEquals(0, heightChanged);
        bar.setKeySet(new ExtraKeysInfo[] {info(TWO)}, 1f, Transition.RELOAD);
        assertEquals("the short set committed before the next one", 1, heightChanged);
        assertEquals(2, bar.committedRows());
    }

    @Test
    public void aSetsScaleBecomesTheBarsScale() throws Exception {
        bar.setCurrentItem(0, false);
        bar.setKeySet(new ExtraKeysInfo[] {info(ONE)}, 2f, Transition.RELOAD);
        assertEquals(2f, bar.committedScale(), 0f);
        bar.setCurrentItem(0, false);
        // Equal rows at a bigger scale are a taller bar: said at once on a morph.
        heightChanged = 0;
        bar.setKeySet(new ExtraKeysInfo[] {info(ONE)}, 3f, Transition.MORPH);
        assertEquals(3f, bar.committedScale(), 0f);
        assertEquals(1, heightChanged);
        play();
        assertEquals(1, heightChanged);
    }

    private void layout(int barHeight) {
        bar.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(barHeight, View.MeasureSpec.EXACTLY));
        bar.layout(0, 0, WIDTH, barHeight);
    }

    private static ExtraKeysInfo info(String rows) throws Exception {
        return new ExtraKeysInfo(rows, ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
            ExtraKeysConstants.CONTROL_CHARS_ALIASES);
    }
}
