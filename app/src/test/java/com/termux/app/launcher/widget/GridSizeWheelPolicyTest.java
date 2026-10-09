package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;

/**
 * The digit wheels' arithmetic. A wheel that could leave its range would hand the widget grid a
 * size the store then clamps behind the user's back, and a wheel that counted fractions of a step
 * would change the grid on the slightest touch.
 */
public class GridSizeWheelPolicyTest {

    /** A 3× phone, so a step is 84 pixels. */
    private static final float DENSITY = 3f;

    @Test
    public void theRangesAreTheSettingsSlidersOwn() {
        assertEquals(TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
            GridSizeWheelPolicy.columns().minimum());
        assertEquals(TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
            GridSizeWheelPolicy.columns().maximum());
        assertEquals(TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS,
            GridSizeWheelPolicy.rows().minimum());
        assertEquals(TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS,
            GridSizeWheelPolicy.rows().maximum());
        // The shipped bounds, spelled out: two either way, eight columns, twelve rows.
        assertEquals(2, GridSizeWheelPolicy.columns().minimum());
        assertEquals(8, GridSizeWheelPolicy.columns().maximum());
        assertEquals(2, GridSizeWheelPolicy.rows().minimum());
        assertEquals(12, GridSizeWheelPolicy.rows().maximum());
    }

    @Test
    public void oneNumberPerStep_andNothingForLessThanOne() {
        float step = GridSizeWheelPolicy.STEP_DP * DENSITY;

        assertEquals("a still finger", 0, GridSizeWheelPolicy.stepsFor(0f, DENSITY));
        assertEquals("just short of a step up", 0,
            GridSizeWheelPolicy.stepsFor(-(step - 1f), DENSITY));
        assertEquals("exactly a step up", 1, GridSizeWheelPolicy.stepsFor(-step, DENSITY));
        assertEquals("a step and a half up", 1,
            GridSizeWheelPolicy.stepsFor(-step * 1.5f, DENSITY));
        assertEquals("three steps up", 3, GridSizeWheelPolicy.stepsFor(-step * 3f, DENSITY));
        assertEquals("a step down", -1, GridSizeWheelPolicy.stepsFor(step, DENSITY));
        assertEquals("just short of a step down", 0,
            GridSizeWheelPolicy.stepsFor(step - 1f, DENSITY));
    }

    @Test
    public void draggingUpCountsUpAndDownCountsDown() {
        GridSizeWheelPolicy columns = GridSizeWheelPolicy.columns();
        float step = GridSizeWheelPolicy.STEP_DP * DENSITY;

        assertEquals(5, columns.valueFor(4, -step, DENSITY));
        assertEquals(3, columns.valueFor(4, step, DENSITY));
        assertEquals(4, columns.valueFor(4, -step / 3f, DENSITY));
    }

    @Test
    public void aWheelNeverLeavesItsRange() {
        GridSizeWheelPolicy columns = GridSizeWheelPolicy.columns();
        GridSizeWheelPolicy rows = GridSizeWheelPolicy.rows();
        float step = GridSizeWheelPolicy.STEP_DP * DENSITY;

        assertEquals("dragged off the top", 8, columns.valueFor(4, -step * 100f, DENSITY));
        assertEquals("dragged off the bottom", 2, columns.valueFor(4, step * 100f, DENSITY));
        assertEquals("dragged off the top", 12, rows.valueFor(5, -step * 100f, DENSITY));
        assertEquals("dragged off the bottom", 2, rows.valueFor(5, step * 100f, DENSITY));

        // Whatever the drag, and wherever it started - a stored value from an older build
        // included - the answer is inside the range.
        for (int start = -4; start <= 40; start++) {
            for (float drag = -step * 20f; drag <= step * 20f; drag += step / 2f) {
                int value = rows.valueFor(start, drag, DENSITY);
                assertTrue("never below the minimum", value >= rows.minimum());
                assertTrue("never above the maximum", value <= rows.maximum());
            }
        }
    }

    @Test
    public void theLeftoverIsWhatTheFingerHasCoveredPastTheLastNumber() {
        float step = GridSizeWheelPolicy.STEP_DP * DENSITY;

        assertEquals(0f, GridSizeWheelPolicy.leftoverPx(0f, DENSITY), 0.01f);
        assertEquals("half way to the next number up",
            -step / 2f, GridSizeWheelPolicy.leftoverPx(-step / 2f, DENSITY), 0.01f);
        assertEquals("a whole step lands on the number, nothing left over",
            0f, GridSizeWheelPolicy.leftoverPx(-step, DENSITY), 0.01f);
        assertEquals("a step and a bit", -4f,
            GridSizeWheelPolicy.leftoverPx(-(step + 4f), DENSITY), 0.01f);
        assertEquals("half way down", step / 2f,
            GridSizeWheelPolicy.leftoverPx(step / 2f, DENSITY), 0.01f);
    }

    @Test
    public void aDensityThatIsNotThereStillCountsSteps() {
        // Nothing should divide by zero if a wheel is asked before it has been measured.
        assertEquals(1, GridSizeWheelPolicy.stepsFor(-GridSizeWheelPolicy.STEP_DP, 0f));
        assertEquals(0f, GridSizeWheelPolicy.leftoverPx(-GridSizeWheelPolicy.STEP_DP, 0f), 0.01f);
    }

    @Test
    public void aRangeGivenBackwardsIsStillARange() {
        GridSizeWheelPolicy backwards = new GridSizeWheelPolicy(8, 2);
        assertEquals(2, backwards.minimum());
        assertEquals(8, backwards.maximum());
        assertEquals(2, backwards.clamp(1));
        assertEquals(8, backwards.clamp(9));
    }

    @Test
    public void atTheDefaultTextSizeTheWheelKeepsItsOldPitchAndWidth() {
        // 20sp digits at 1x: about 15dp tall, 11dp wide each. The fixed sizes win.
        float textSize = 20f * DENSITY;
        assertEquals(28f * DENSITY, GridSizeWheelPolicy.pitchPx(DENSITY, 15f * DENSITY, textSize),
            0.01f);
        assertEquals(52f * DENSITY, GridSizeWheelPolicy.widthPx(DENSITY, 22f * DENSITY, textSize),
            0.01f);
    }

    @Test
    public void largeTextSpreadsTheNumbersAndWidensTheWheel() {
        // The same digits at a 2x font scale.
        float textSize = 40f * DENSITY;
        float pitch = GridSizeWheelPolicy.pitchPx(DENSITY, 30f * DENSITY, textSize);
        float width = GridSizeWheelPolicy.widthPx(DENSITY, 44f * DENSITY, textSize);
        assertTrue("numbers clear each other: " + pitch, pitch > 30f * DENSITY);
        assertTrue("and more than the old pitch", pitch > 28f * DENSITY);
        assertTrue("two digits fit with room: " + width, width > 52f * DENSITY);
    }

    @Test
    public void aDragCountsInTheWheelsOwnPitch() {
        GridSizeWheelPolicy policy = GridSizeWheelPolicy.rows();
        assertEquals(3, GridSizeWheelPolicy.stepsForPitch(-100f, 30f));
        assertEquals(0, GridSizeWheelPolicy.stepsForPitch(-29f, 30f));
        assertEquals(-10f, GridSizeWheelPolicy.leftoverForPitch(-100f, 30f), 0.01f);
        assertEquals(7, policy.valueForPitch(4, -100f, 30f));
        assertEquals(policy.maximum(), policy.valueForPitch(policy.maximum(), -900f, 30f));
    }

    @Test
    public void theLongestNumberDecidesTheDigitCount() {
        assertEquals(1, GridSizeWheelPolicy.columns().maximumDigits());
        assertEquals(2, GridSizeWheelPolicy.rows().maximumDigits());
    }
}
