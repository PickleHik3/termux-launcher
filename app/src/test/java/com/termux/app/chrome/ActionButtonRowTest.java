package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The action row decides one row or a stack from the buttons' measured widths, at a 360dp phone
 * with real text metrics.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ActionButtonRowTest {

    /** A tour card's content width on a 360dp phone: 300dp less its own 14dp padding. */
    private static final int CARD_CONTENT_DP = 272;

    private Context mContext;
    private float mDensity;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mDensity = mContext.getResources().getDisplayMetrics().density;
    }

    @Test
    public void threeLongAnswersStackWithTheLastOnTop() {
        ActionButtonRow row = new ActionButtonRow(mContext);
        TextView terminal = button("Use it as a terminal");
        TextView home = button("Use it as your home screen");
        TextView display = button("Use it as a Linux display");
        row.addView(terminal);
        row.addView(home);
        row.addView(display);

        layout(row, View.MeasureSpec.EXACTLY, CARD_CONTENT_DP);

        assertTrue(row.isStacked());
        // Material's stacked order: the last action of the row's order is on top.
        assertTrue(display.getTop() < home.getTop());
        assertTrue(home.getTop() < terminal.getTop());
        for (TextView button : new TextView[] {terminal, home, display}) {
            assertEquals(row.getWidth(), button.getWidth());
            assertEquals(1, button.getLineCount());
            assertTrue(button.getHeight() >= dp(48));
        }
        assertEquals(terminal.getBottom(), row.getHeight());
    }

    @Test
    public void twoShortActionsShareOneRowAgainstTheEndEdge() {
        ActionButtonRow row = new ActionButtonRow(mContext);
        TextView later = button("Later");
        TextView download = button("Download");
        row.addView(later);
        row.addView(download);

        layout(row, View.MeasureSpec.EXACTLY, CARD_CONTENT_DP);

        assertFalse(row.isStacked());
        assertEquals(later.getTop(), download.getTop());
        assertEquals(row.getWidth(), download.getRight());
        assertEquals(dp(8), download.getLeft() - later.getRight());
        assertTrue(later.getLeft() > 0);
    }

    @Test
    public void aRowInAWrappingCardIsOnlyAsWideAsItsButtons() {
        ActionButtonRow row = new ActionButtonRow(mContext);
        TextView later = button("Later");
        TextView download = button("Download");
        row.addView(later);
        row.addView(download);

        layout(row, View.MeasureSpec.AT_MOST, CARD_CONTENT_DP);

        assertFalse(row.isStacked());
        assertEquals(later.getWidth() + dp(8) + download.getWidth(), row.getWidth());
    }

    @Test
    public void aLeadingLinkThatNoLongerFitsPushesTheRowToAStack() {
        // The closing card's own pair fits a 360dp phone side by side.
        ActionButtonRow fits = new ActionButtonRow(mContext);
        TextView docs = button("Read the docs");
        TextView start = button("Start using");
        fits.setLeading(docs);
        fits.addView(start);
        layout(fits, View.MeasureSpec.EXACTLY, CARD_CONTENT_DP);
        assertFalse(fits.isStacked());
        assertEquals(0, docs.getLeft());
        assertEquals(fits.getWidth(), start.getRight());

        // The same action alone fits; the link beside it is what does not, so the two stack with
        // the action on top and the link under it.
        ActionButtonRow narrow = new ActionButtonRow(mContext);
        TextView longDocs = button("Read the documentation online");
        TextView longStart = button("Start using the launcher");
        narrow.addView(longStart);
        layout(narrow, View.MeasureSpec.EXACTLY, CARD_CONTENT_DP);
        assertFalse(narrow.isStacked());

        narrow.setLeading(longDocs);
        layout(narrow, View.MeasureSpec.EXACTLY, CARD_CONTENT_DP);
        assertTrue(narrow.isStacked());
        assertTrue(longStart.getBottom() < longDocs.getTop());
        assertEquals(narrow.getWidth(), longDocs.getWidth());
    }

    @Test
    public void aLabelWiderThanTheRowOnItsOwnIsEllipsizedOnOneLine() {
        ActionButtonRow row = new ActionButtonRow(mContext);
        TextView wide = button("A label far too long for any phone's card to hold on one line");
        row.addView(wide);

        layout(row, View.MeasureSpec.EXACTLY, CARD_CONTENT_DP);

        assertEquals(1, wide.getLineCount());
        assertTrue(wide.getRight() <= row.getWidth());
    }

    /** A button dressed like the tour card's: 13sp, 12dp sides, 48dp minimum width. */
    private TextView button(String label) {
        TextView button = new TextView(mContext);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(6), dp(12), dp(6));
        button.setMinWidth(dp(48));
        button.setText(label);
        return button;
    }

    private void layout(ActionButtonRow row, int widthMode, int widthDp) {
        row.measure(View.MeasureSpec.makeMeasureSpec(dp(widthDp), widthMode),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
    }

    private int dp(float value) {
        return Math.round(value * mDensity);
    }
}
