package com.termux.app.surfaces.rows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;

import com.google.android.material.chip.Chip;
import com.termux.R;
import com.termux.app.surfaces.LegendSlider;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The condensed row kinds (spec #326, #345) laid out on pong's width at the default text size and
 * at Large (1.3), and on a narrow card: a slider pair and a stepper pair share a line where they
 * fit and stack where they do not, switch chips wrap, a folded choice opens in place without
 * moving the card's scroll, a touch that starts on a track claims the gesture, and every touch
 * reports the row it was made on and nothing a restatement does is reported.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, qualifiers = "w411dp-h914dp-420dpi",
    application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CondensedRowsTest {

    private static final float[] SCALES = {1f, 1.3f};

    @After
    public void resetFontScale() {
        RuntimeEnvironment.setFontScale(1f);
    }

    // ------------------------------------------------------------------------- slider pair

    private static SliderPairRow pair(Fixture f, List<String> heard) {
        return SliderPairRow.build(f.themed, Arrays.asList(
            new SliderRow.Track(0, "Opacity", 0, 100, 1, v -> v + "%"),
            new SliderRow.Track(0, "Frost", 0, 40, 1, v -> v + " dp")),
            new int[] {50, 10}, new SliderRow.Listener() {
                @Override public void onValue(int index, int value, boolean dragging) {
                    heard.add(index + "=" + value);
                }

                @Override public void onReleased(int index) {
                    heard.add("up" + index);
                }

                @Override public void onReset(int index) {
                    heard.add("reset" + index);
                }
            });
    }

    @Test
    public void twoSlidersShareALineWithTheirNamesAndValuesOverTheTracks() {
        for (float scale : SCALES) {
            Fixture f = new Fixture(scale, 411);
            SliderPairRow row = pair(f, new ArrayList<>());
            f.layOut(row.root);
            String at = "at " + scale;
            assertTrue(at + ": one line", row.root.isSideBySide());
            SliderPairRow.Cell a = row.cells.get(0);
            SliderPairRow.Cell b = row.cells.get(1);
            assertEquals(at + ": same top", f.top(a.slider), f.top(b.slider));
            assertTrue(at + ": second after first", f.left(b.slider) >= f.left(a.slider)
                + a.slider.getWidth());
            assertTrue(at + ": name over its track", f.top(a.name) < f.top(a.slider));
            assertTrue(at + ": value over its track", f.top(a.value) < f.top(a.slider));
            assertEquals(at + ": name and value on one line", f.top(a.name) + a.name.getHeight() / 2,
                f.top(a.value) + a.value.getHeight() / 2, 2);
            assertTrue(at + ": first starts in the gutter", f.left(a.slider) >= f.dp(16) - 1);
            assertTrue(at + ": second ends in the gutter",
                f.left(b.slider) + b.slider.getWidth() <= f.width - f.dp(16) + 1);
            assertEquals(at + ": one-line name", 1, a.name.getMaxLines());
            assertEquals("50%", a.value.getText().toString());
            assertEquals("10 dp", b.value.getText().toString());
        }
    }

    @Test
    public void aNarrowCardStacksTheTwoSliders() {
        Fixture f = new Fixture(1f, 260);
        SliderPairRow row = pair(f, new ArrayList<>());
        f.layOut(row.root);
        assertFalse(row.root.isSideBySide());
        SliderPairRow.Cell a = row.cells.get(0);
        SliderPairRow.Cell b = row.cells.get(1);
        assertTrue("the second stands under the first", f.top(b.name) >= f.top(a.slider));
        assertEquals("both tracks span the row", a.slider.getWidth(), b.slider.getWidth());
    }

    @Test
    public void aFingerThatStartsOnATrackClaimsTheGestureSoTheCardDoesNotScroll() {
        Fixture f = new Fixture(1f, 411);
        List<String> heard = new ArrayList<>();
        SliderPairRow row = pair(f, heard);
        Claims scroll = new Claims(f.themed);
        scroll.addView(row.root, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        f.layOut(scroll);
        LegendSlider slider = row.cells.get(1).slider;
        float x = slider.getWidth() * 0.9f;
        float y = slider.getHeight() / 2f;
        long now = SystemClock.uptimeMillis();
        slider.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0));
        assertTrue("the card is held off the gesture", scroll.disallowed);
        slider.dispatchTouchEvent(MotionEvent.obtain(now, now + 30, MotionEvent.ACTION_UP,
            x, y, 0));
        boolean second = false;
        for (String line : heard) {
            assertFalse("the first slider heard " + heard, line.startsWith("0="));
            second |= line.startsWith("1=");
        }
        assertTrue("a touch reports the slider it was on: " + heard, second);
    }

    @Test
    public void restatingAValueReportsNothing() {
        Fixture f = new Fixture(1f, 411);
        List<String> heard = new ArrayList<>();
        SliderPairRow row = pair(f, heard);
        f.layOut(row.root);
        row.setValue(0, 80);
        row.setValue(1, 5);
        assertEquals("80%", row.cells.get(0).value.getText().toString());
        assertEquals("5 dp", row.cells.get(1).value.getText().toString());
        assertTrue(heard.toString(), heard.isEmpty());
        row.setShown(1, false);
        f.layOut(row.root);
        assertEquals("the one left takes the whole row", f.dp(411 - 32),
            row.cells.get(0).slider.getWidth(), 2);
    }

    // ------------------------------------------------------------------------ stepper pair

    private static StepperPairRow steppers(Fixture f, List<String> heard) {
        return StepperPairRow.build(f.themed,
            "Columns", 1, 8, 1, 4, String::valueOf, v -> heard.add("cols=" + v),
            "Rows", 1, 12, 1, 6, String::valueOf, v -> heard.add("rows=" + v));
    }

    @Test
    public void twoSteppersShareALineInsideTheGutter() {
        for (float scale : SCALES) {
            Fixture f = new Fixture(scale, 411);
            StepperPairRow row = steppers(f, new ArrayList<>());
            f.layOut(row.root);
            String at = "at " + scale;
            assertTrue(at, row.root.isSideBySide());
            assertEquals(at, f.top(row.first.minus), f.top(row.second.minus));
            assertTrue(at, f.left(row.first.minus) >= f.dp(16) - 1);
            assertTrue(at, f.left(row.second.plus) + row.second.plus.getWidth()
                <= f.width - f.dp(16) + 1);
            assertTrue(at + ": 48dp targets", row.first.plus.getWidth() >= f.dp(48) - 1
                && row.first.plus.getHeight() >= f.dp(48) - 1);
        }
    }

    @Test
    public void aStepIsReportedWithItsOwnRowAndClampsAtTheEnd() {
        Fixture f = new Fixture(1f, 411);
        List<String> heard = new ArrayList<>();
        StepperPairRow row = steppers(f, heard);
        f.layOut(row.root);
        row.first.plus.performClick();
        row.second.minus.performClick();
        assertEquals(Arrays.asList("cols=5", "rows=5"), heard);
        row.second.setValue(1);
        assertFalse("minus is out at the bottom", row.second.minus.isEnabled());
        assertEquals("a restatement reports nothing", 2, heard.size());
    }

    @Test
    public void aNarrowCardStacksTheTwoSteppers() {
        Fixture f = new Fixture(1.3f, 240);
        StepperPairRow row = steppers(f, new ArrayList<>());
        f.layOut(row.root);
        assertFalse(row.root.isSideBySide());
        assertTrue(f.top(row.second.minus) > f.top(row.first.minus));
    }

    // --------------------------------------------------------------------------- chips

    private static SwitchChipsRow chips(Fixture f, List<String> heard) {
        return SwitchChipsRow.build(f.themed, Arrays.asList(
            new SwitchChipsRow.Item(R.drawable.ic_editor_text_size, "Two-line labels"),
            new SwitchChipsRow.Item(R.drawable.ic_editor_contrast, "Label shadow"),
            new SwitchChipsRow.Item(R.drawable.ic_symbol_wallpaper, "Wallpaper scrolling"),
            new SwitchChipsRow.Item(R.drawable.ic_symbol_stacks, "Glass widgets")),
            (index, on) -> heard.add(index + (on ? "+" : "-")));
    }

    @Test
    public void switchChipsWrapAndStayInsideTheGutter() {
        for (float scale : SCALES) {
            Fixture f = new Fixture(scale, 411);
            SwitchChipsRow row = chips(f, new ArrayList<>());
            f.layOut(row.root);
            String at = "at " + scale;
            int lowest = 0;
            int highest = Integer.MAX_VALUE;
            for (Chip chip : row.chips) {
                lowest = Math.max(lowest, f.top(chip));
                highest = Math.min(highest, f.top(chip));
                assertTrue(at + ": starts in the gutter", f.left(chip) >= f.dp(16) - 1);
                assertTrue(at + ": ends in the gutter",
                    f.left(chip) + chip.getWidth() <= f.width - f.dp(16) + 1);
            }
            assertNotEquals(at + ": four chips do not fit one line and wrap", highest, lowest);
        }
    }

    @Test
    public void aChipIsASwitchThatReportsItsToggleAndNotItsRestatement() {
        Fixture f = new Fixture(1f, 411);
        List<String> heard = new ArrayList<>();
        SwitchChipsRow row = chips(f, heard);
        f.layOut(row.root);
        row.chips.get(2).performClick();
        row.chips.get(2).performClick();
        row.chips.get(0).performClick();
        assertEquals(Arrays.asList("2+", "2-", "0+"), heard);
        row.setChecked(3, true);
        row.setChecked(0, false);
        assertTrue(row.chips.get(3).isChecked());
        assertEquals("a restatement reports nothing", 3, heard.size());
    }

    // ---------------------------------------------------------------------------- fold

    private static FoldChoiceRow fold(Fixture f, List<Integer> heard) {
        return FoldChoiceRow.build(f.themed, R.drawable.ic_symbol_tune, "Cursor trail",
            Arrays.asList("Off", "Default", "Motion", "Railgun", "Torpedo", "Pixie", "Comet"),
            1, heard::add);
    }

    @Test
    public void aFoldedChoiceOpensInPlaceAndPicksAndFolds() {
        Fixture f = new Fixture(1f, 411);
        List<Integer> heard = new ArrayList<>();
        FoldChoiceRow row = fold(f, heard);
        f.layOut(row.root);
        assertEquals("Default", row.current.getText().toString());
        assertEquals(View.GONE, row.list.getVisibility());
        row.line.performClick();
        f.layOut(row.root);
        assertTrue(row.isOpen());
        assertEquals(View.VISIBLE, row.list.getVisibility());
        assertTrue("the list stands under the line", f.top(row.list) >= f.top(row.line)
            + row.line.getHeight() - 1);
        row.chips.get(3).performClick();
        assertEquals(Arrays.asList(3), heard);
        assertEquals("Railgun", row.current.getText().toString());
        assertFalse("choosing folds it again", row.isOpen());
        assertEquals(View.GONE, row.list.getVisibility());
    }

    @Test
    public void aRestatedChoiceReportsNothingAndTheLineIsOneEllipsizedLine() {
        for (float scale : SCALES) {
            Fixture f = new Fixture(scale, 411);
            List<Integer> heard = new ArrayList<>();
            FoldChoiceRow row = fold(f, heard);
            row.select(5);
            f.layOut(row.root);
            assertTrue(heard.isEmpty());
            assertEquals("Pixie", row.current.getText().toString());
            assertEquals(1, row.title.getMaxLines());
            assertTrue("a 56dp line", row.line.getHeight() >= f.dp(56) - 1);
            assertTrue("the chosen word stays in the gutter", f.left(row.chevron)
                + row.chevron.getWidth() <= f.width - f.dp(16) + 1);
        }
    }

    @Test
    public void openingAFoldKeepsTheCardsScrollPosition() {
        Fixture f = new Fixture(1f, 411);
        LinearLayout column = new LinearLayout(f.themed);
        column.setOrientation(LinearLayout.VERTICAL);
        View filler = new View(f.themed);
        column.addView(filler, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, f.height * 2));
        FoldChoiceRow row = fold(f, new ArrayList<>());
        column.addView(row.root);
        ScrollView scroll = new ScrollView(f.themed);
        scroll.addView(column);
        f.layOut(scroll);
        scroll.scrollTo(0, f.height);
        int before = scroll.getScrollY();
        assertTrue(before > 0);
        row.line.performClick();
        f.layOut(scroll);
        assertEquals("a tap on a control does not move the card", before, scroll.getScrollY());
    }

    // --------------------------------------------------------------------------- drive

    /** A scroll container's stand-in: remembers being held off a gesture. */
    private static final class Claims extends FrameLayout {
        boolean disallowed;

        Claims(@NonNull Context context) {
            super(context);
        }

        @Override public void requestDisallowInterceptTouchEvent(boolean disallow) {
            if (disallow)
                disallowed = true;
            super.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private static final class Fixture {
        final Context themed;
        final FrameLayout parent;
        final int width;
        final int height;

        Fixture(float fontScale, int widthDp) {
            RuntimeEnvironment.setFontScale(fontScale);
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            themed = new ContextThemeWrapper(activity,
                R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            float density = themed.getResources().getDisplayMetrics().density;
            width = Math.round(widthDp * density);
            height = Math.round(914 * density);
            parent = new FrameLayout(themed);
            activity.setContentView(parent, new ViewGroup.LayoutParams(width, height));
        }

        int dp(float value) {
            return Math.round(value * themed.getResources().getDisplayMetrics().density);
        }

        void layOut(@NonNull View row) {
            if (row.getParent() != parent) {
                parent.removeAllViews();
                parent.addView(row, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    row instanceof ScrollView ? ViewGroup.LayoutParams.MATCH_PARENT
                        : ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            parent.layout(0, 0, width, height);
        }

        int top(@NonNull View view) {
            int[] at = new int[2];
            view.getLocationInWindow(at);
            int[] parentAt = new int[2];
            parent.getLocationInWindow(parentAt);
            return at[1] - parentAt[1];
        }

        int left(@NonNull View view) {
            int[] at = new int[2];
            view.getLocationInWindow(at);
            int[] parentAt = new int[2];
            parent.getLocationInWindow(parentAt);
            return at[0] - parentAt[0];
        }
    }
}
