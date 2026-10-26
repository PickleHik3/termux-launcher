package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Settings › Home › Icons and labels: the default is the Default preset, the icon size scales the
 * dock's 48 dp, two lines are taken only where the cell has room for them, and at a 1.3 font
 * scale no label is ever cut by its cell.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, qualifiers = "w360dp-h780dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HomeIconStyleTest {

    private static final float DENSITY = 2f;

    @After
    public void tearDown() {
        RuntimeEnvironment.setFontScale(1f);
    }

    @Test
    public void theDefaultIsTheDefaultPreset() {
        HomeIconStyle style = HomeIconStyle.DEFAULT;
        assertEquals(HomeIconSize.DEFAULT, style.iconSize);
        assertEquals(HomeAppIconView.LABEL_SP, style.labelSize.sp, 0f);
        assertFalse(style.twoLines);
        assertTrue(style.shadow);
    }

    @Test
    public void storedLabelSizesAreReadLeniently() {
        assertEquals(HomeIconStyle.LabelSize.DEFAULT, HomeIconStyle.LabelSize.fromValue(null));
        assertEquals(HomeIconStyle.LabelSize.LARGE, HomeIconStyle.LabelSize.fromValue("large"));
    }

    @Test
    public void theIconScalesTheDocksSizeWhereTheCellHasRoom() {
        assertEquals(HomeAppIconMetrics.of(300, 300, 26, DENSITY).iconPx,
            HomeAppIconMetrics.of(300, 300, 26, DENSITY, 1f).iconPx);
        assertEquals(115, HomeAppIconMetrics.of(300, 300, 26, DENSITY, 1.2f).iconPx);
        assertEquals(77, HomeAppIconMetrics.of(300, 300, 26, DENSITY, 0.8f).iconPx);
        // A larger icon is still held to the cell.
        assertEquals(64, HomeAppIconMetrics.of(80, 300, 26, DENSITY, 1.2f).iconPx);
    }

    @Test
    public void twoLinesAreTakenOnlyWhereTheyLeaveALabelledIcon() {
        assertEquals(1, HomeAppIconMetrics.labelLines(300, 300, 26, DENSITY, 1f, false));
        assertEquals(2, HomeAppIconMetrics.labelLines(300, 300, 26, DENSITY, 1f, true));
        // 120 tall: two 26 px lines leave 44 px, under the 48 px floor; one line it is.
        assertEquals(1, HomeAppIconMetrics.labelLines(300, 120, 26, DENSITY, 1f, true));
    }

    @Test
    public void atLargeTextNoLabelIsCutByItsCell() {
        RuntimeEnvironment.setFontScale(1.3f);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        HomeIconStyle[] styles = {
            HomeIconStyle.DEFAULT,
            new HomeIconStyle(HomeIconSize.LARGE, HomeIconStyle.LabelSize.LARGE, true, true),
            new HomeIconStyle(HomeIconSize.SMALL, HomeIconStyle.LabelSize.SMALL, true, false),
        };
        int[][] cells = {{160, 180}, {140, 150}, {120, 120}, {200, 260}};
        for (HomeIconStyle style : styles) {
            for (int[] cell : cells) {
                HomeAppIconView view = new HomeAppIconView(activity);
                view.setStyle(style);
                view.bind(null, "A rather long application name", false);
                view.measure(View.MeasureSpec.makeMeasureSpec(cell[0], View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(cell[1], View.MeasureSpec.EXACTLY));
                view.layout(0, 0, cell[0], cell[1]);
                TextView label = labelOf(view);
                String where = style.iconSize + " " + style.labelSize + " two="
                    + style.twoLines + " in " + cell[0] + "x" + cell[1];
                if (label.getVisibility() != View.VISIBLE) continue;
                assertTrue(where + ": label inside the cell",
                    label.getBottom() <= cell[1] && label.getTop() >= 0);
                assertTrue(where + ": label within the cell's width",
                    label.getLeft() >= 0 && label.getRight() <= cell[0]);
                int lineCount = label.getLayout() == null ? 1 : label.getLayout().getLineCount();
                if (label.getLayout() != null) assertTrue(where + ": the label shows every line it lays out",
                    label.getHeight() >= label.getLayout().getHeight());
                if (!style.twoLines) assertEquals(where, 1, lineCount);
            }
        }
    }

    private static TextView labelOf(HomeAppIconView view) {
        for (int i = 0; i < view.getChildCount(); i++) {
            if (view.getChildAt(i) instanceof TextView) return (TextView) view.getChildAt(i);
        }
        throw new AssertionError("no label");
    }
}
