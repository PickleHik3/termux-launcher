package com.termux.app.terminal.extrakeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.view.Gravity;
import android.view.View;

import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

/** The bar's pages, their heights and the committed height the activity reads. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, application = android.app.Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ExtraKeysBarViewTest {

    private static final int BASE_ROW_PX = 100;
    private static final int WIDTH = 500;

    private ExtraKeysBarView bar;
    private int selected;
    private int lastSelected = -1;
    private int heightChanged;

    @Before
    public void setUp() throws Exception {
        bar = new ExtraKeysBarView(RuntimeEnvironment.getApplication(), null);
        bar.setRowMetrics(BASE_ROW_PX, 1f);
        bar.setKeySet(new ExtraKeysInfo[] {info("[['A','B']]"), info("[['A','B'],['C','D']]")});
        bar.setListener(new ExtraKeysBarView.Listener() {
            @Override
            public void onPageSelected(int page) {
                selected++;
                lastSelected = page;
            }

            @Override
            public void onCommittedHeightChanged() {
                heightChanged++;
            }
        });
        layout(200);
    }

    @Test
    public void everyPageIsAsTallAsItsRows() {
        assertEquals(2, bar.keyPageCount());
        assertEquals(2, bar.pageCount());
        assertEquals(100, bar.keyPage(0).getHeight());
        assertEquals(200, bar.keyPage(1).getHeight());
        assertEquals(WIDTH, bar.keyPage(1).getWidth());
    }

    @Test
    public void aPageIsAnchoredToTheBottomOrTheTopOfTheBar() {
        assertEquals("a one-row page stands on the bar's bottom", 100, bar.keyPage(0).getTop());
        assertEquals(0, bar.keyPage(1).getTop());
        bar.setAnchorEdge(Gravity.TOP);
        layout(200);
        assertEquals(0, bar.keyPage(0).getTop());
    }

    @Test
    public void theTextInputPageIsOneRowAndComesLast() {
        View input = new View(RuntimeEnvironment.getApplication());
        bar.setTextInputPage(input);
        layout(200);
        assertEquals(3, bar.pageCount());
        assertEquals(2, bar.keyPageCount());
        assertEquals(100, input.getHeight());
        assertSame(input, bar.getChildAt(2));
        bar.setCurrentItem(2, false);
        assertEquals("the text input page counts as one row", 1, bar.committedRows());
    }

    @Test
    public void showingASecondPageWithMoreRowsCommitsItsHeightOnceWithoutAnimation() {
        assertEquals(1, bar.committedRows());
        bar.setCurrentItem(1, false);
        assertEquals(1, bar.getCurrentItem());
        assertEquals(2, bar.committedRows());
        assertEquals(1, heightChanged);
        assertEquals(1, selected);
        assertEquals(1, lastSelected);
        assertEquals("the shown page is in place", 0f, bar.keyPage(1).getTranslationX(), 0f);
        assertEquals(-WIDTH, bar.keyPage(0).getTranslationX(), 0f);
    }

    @Test
    public void aSlideToATallerPageCommitsAtOnceThenRevealsTheKeys() {
        bar.setCurrentItem(1, true);
        ShadowLooper.idleMainLooper(ExtraKeyGesturePolicy.SLIDE_MS + 40, TimeUnit.MILLISECONDS);
        assertEquals(2, bar.committedRows());
        assertEquals(1, heightChanged);
        assertEquals(1, selected);
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
        assertEquals("the reveal ends and nothing more is said", 1, heightChanged);
    }

    @Test
    public void aSlideToAShorterPageCommitsItsHeightAfterTheRowsAreHidden() {
        bar.setCurrentItem(1, false);
        heightChanged = 0;
        selected = 0;
        bar.setCurrentItem(0, true);
        ShadowLooper.idleMainLooper(ExtraKeyGesturePolicy.SLIDE_MS + 400, TimeUnit.MILLISECONDS);
        assertEquals(1, selected);
        assertEquals(0, lastSelected);
        assertEquals(1, bar.committedRows());
        assertEquals("the height is said once", 1, heightChanged);
    }

    @Test
    public void anEmptyKeySetLeavesNoKeyPages() {
        bar.setKeySet(new ExtraKeysInfo[0]);
        assertEquals(0, bar.keyPageCount());
        assertEquals(0, bar.committedRows());
        bar.setKeySet(null);
        assertEquals(0, bar.pageCount());
    }

    @Test
    public void everyPageTheBarBuildsIsStyledByTheStyler() throws Exception {
        final int[] styled = new int[1];
        bar.setPageStyler((page, index) -> styled[0] += index + 1);
        assertEquals("existing pages are styled when the styler is set", 3, styled[0]);
        bar.setKeySet(new ExtraKeysInfo[] {info("[['A']]"), info("[['B']]"), info("[['C']]")});
        assertEquals(3 + 6, styled[0]);
        assertNotNull(bar.keyPage(2));
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
