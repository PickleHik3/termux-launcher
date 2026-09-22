package com.termux.app.terminal;

import android.app.Application;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;

import com.termux.app.statusbar.TopPaneClockForm;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalClockWidgetTest {

    @Test
    public void snapshot_usesPadded24HourTimeAndSpecifiedDateFormat() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        Calendar calendar = Calendar.getInstance(utc);
        calendar.clear();
        calendar.set(2026, Calendar.JULY, 22, 4, 5, 6);

        TerminalClockWidget.ClockSnapshot snapshot =
            TerminalClockWidget.snapshot(calendar.getTimeInMillis(), utc);

        assertEquals("04", snapshot.hh);
        assertEquals("05", snapshot.mm);
        assertEquals("06", snapshot.ss);
        assertEquals("WED 22 JUL", snapshot.date);
        assertEquals("", snapshot.period);
    }

    @Test
    public void snapshot_amPmMode_usesTwelveHourTimeAndPeriod() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        Calendar calendar = Calendar.getInstance(utc);
        calendar.clear();
        calendar.set(2026, Calendar.JULY, 22, 16, 5, 6);

        TerminalClockWidget.ClockSnapshot snapshot =
            TerminalClockWidget.snapshot(calendar.getTimeInMillis(), utc, true);

        assertEquals("04", snapshot.hh);
        assertEquals("PM", snapshot.period);
    }

    @Test
    public void style_acceptsSixVariantsAndFallsBackToFlip() {
        TerminalClockWidget widget = new TerminalClockWidget(
            ApplicationProvider.getApplicationContext(), null);

        widget.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD);
        assertEquals("lcd", widget.getStyle());
        widget.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL);
        assertEquals("minimal", widget.getStyle());
        widget.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LED);
        assertEquals("led", widget.getStyle());
        widget.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE);
        assertEquals("tape", widget.getStyle());
        widget.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB);
        assertEquals("slab", widget.getStyle());
        widget.setStyle("unknown");
        assertEquals("flip", widget.getStyle());
    }

    @Test
    public void alignment_acceptsThreeValuesAndFallsBackToLeft() {
        TerminalClockWidget widget = new TerminalClockWidget(
            ApplicationProvider.getApplicationContext(), null);

        assertEquals("left", widget.getAlignment());
        widget.setAlignment(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER);
        assertEquals("center", widget.getAlignment());
        widget.setAlignment(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT);
        assertEquals("right", widget.getAlignment());
        widget.setAlignment("unknown");
        assertEquals("left", widget.getAlignment());
        widget.setAlignment(null);
        assertEquals("left", widget.getAlignment());
    }

    @Test
    public void lazyMode_reportsEveryDigitSettledOnTheTickThatChangedIt() {
        TerminalClockWidget widget = new TerminalClockWidget(
            ApplicationProvider.getApplicationContext(), null);
        widget.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD);
        long wall = 1_700_000_000_000L;
        widget.updateTime(wall, 1_000L);
        widget.updateTime(wall + 1_000L, 2_000L);

        // Animating: the seconds' ones digit has just changed, so it is at the start of its fold.
        assertEquals(0f, widget.secondsProgress(1, 2_000L, 340L), 0f);

        widget.setLazyMode(true);
        widget.updateTime(wall + 2_000L, 3_000L);

        // Lazy: the same tick must read as settled, or the single frame it paints is the digit
        // dropped 4dp and faded out until the next second.
        assertEquals(1f, widget.secondsProgress(1, 3_000L, 340L), 0f);
        assertEquals(1f, widget.progress(0, 3_000L, 340L), 0f);

        widget.setLazyMode(false);
        widget.updateTime(wall + 3_000L, 4_000L);
        assertEquals(0f, widget.secondsProgress(1, 4_000L, 340L), 0f);
    }

    /**
     * Every FULL-form style must place its time band with the shared alignment offset. Flip drew
     * its own hardcoded centre instead, so the alignment control did nothing while the flip style
     * was on. The offset lands inside a private draw method, which Robolectric cannot rasterize
     * to check, so the guard reads the source the way FullStatusBarGlassTest does.
     */
    @Test
    public void everyFullStyleTranslatesTheTimeBandByTheAlignmentOffset() throws Exception {
        String source = read("app/src/main/java/com/termux/app/terminal/TerminalClockWidget.java");
        for (String method : new String[] {"drawFullLcd", "drawFullMinimal", "drawFullSlab",
            "drawFullTape", "drawFullLed", "drawFullFlip"}) {
            assertTrue(method + " must offset its time band by the alignment",
                body(source, method).contains("canvas.translate(bandDx, 0f)"));
        }
        // The flip date row is its own, so it needs its own guard against re-centering.
        assertTrue("drawFullFlipDateRow must place its date by the alignment",
            body(source, "drawFullFlipDateRow").contains("dateRowTextX("));
    }

    @Test
    public void tapTarget_coversTheTimeDigitsButNotTheDateRowBelowThem() {
        TerminalClockWidget widget = new TerminalClockWidget(
            ApplicationProvider.getApplicationContext(), null);
        widget.setStyle(TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_STYLE_FLIP);
        widget.setForm(TopPaneClockForm.FULL);
        widget.updateTime(1_700_000_000_000L, 1_000L);
        float density = ApplicationProvider.getApplicationContext().getResources()
            .getDisplayMetrics().density;
        int slot = Math.round(68f * density);
        widget.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(Math.round(360f * density),
                android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(slot,
                android.view.View.MeasureSpec.EXACTLY));
        widget.layout(0, 0, widget.getMeasuredWidth(), widget.getMeasuredHeight());

        // Flip: a 35.5dp band over a 13.8dp gap and an 11.7dp date block, centred in the slot.
        float top = (slot - 61f * density) / 2f;
        assertTrue("the digits must be tappable",
            widget.isInsideTapTarget(density, top + 4f * density));
        assertTrue("the date row must not open the clock app",
            !widget.isInsideTapTarget(density, top + 50f * density));
        assertTrue("the slack above the window chips must not open the clock app",
            !widget.isInsideTapTarget(density, slot - density));
    }

    /** The source of {@code method}, from its signature to the first line that closes it. */
    private static String body(String source, String method) {
        int start = source.indexOf("private void " + method + "(");
        assertTrue(method + " not found", start >= 0);
        int end = source.indexOf("\n    }\n", start);
        assertTrue(method + " has no end", end > start);
        return source.substring(start, end);
    }

    private static String read(String relative) throws Exception {
        Path path = Paths.get(relative);
        if (!Files.exists(path)) path = Paths.get("..").resolve(relative);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
