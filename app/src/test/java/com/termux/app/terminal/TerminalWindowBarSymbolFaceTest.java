package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Spanned;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.termux.view.TerminalRenderer;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

/**
 * The window row has to draw its icons with the faces the panes are given. The process glyph is no
 * longer part of the label, so the face it needs now reaches the chip's watermark drawable; an icon
 * the user put inside a window's own name is still text, and still needs the face through a span.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalWindowBarSymbolFaceTest {

    private static final Typeface SYMBOLS = Typeface.SERIF;

    @After
    public void releaseTheSharedFaces() {
        TerminalLabelFaces.resetForTests();
    }

    @Test
    public void theWatermarkGlyphIsDrawnByTheMappedSymbolsFace() {
        publishNerdMaps();
        TerminalWindowBar bar = new TerminalWindowBar(
            ApplicationProvider.getApplicationContext(), null);

        bar.setWindows(windows(), 0);

        // The glyph left the label for the watermark behind it, and took the face with it.
        assertEquals("herdr", tabAt(bar, 0).getText().toString());
        ChipWatermarkDrawable watermark = bar.chipWatermarkAt(0);
        assertNotNull(watermark);
        assertEquals("", watermark.glyph());
        assertSame(SYMBOLS, watermark.glyphFace());
        // The plain ASCII pill has nothing to watermark and nothing to span.
        assertEquals(null, bar.chipWatermarkAt(1).glyph());
        assertFalse(tabAt(bar, 1).getText() instanceof Spanned);
    }

    /** An icon inside the window's own name is still text, and still reaches the mapped face. */
    @Test
    public void anIconInsideTheNameStillReachesTheSymbolsFaceThroughASpan() {
        publishNerdMaps();
        TerminalWindowBar bar = new TerminalWindowBar(
            ApplicationProvider.getApplicationContext(), null);

        bar.setWindows(Arrays.asList(new TerminalWindowBar.WindowItem(
            " ab", "terminal in a b")), 0);

        TextView tab = tabAt(bar, 0);
        assertEquals("ab", tab.getText().toString());
        assertTrue("an icon inside the title needs spans to reach the symbols face",
            tab.getText() instanceof Spanned);
        Spanned spanned = (Spanned) tab.getText();
        TerminalLabelSymbolSpans.SymbolTypefaceSpan[] spans = spanned.getSpans(0, spanned.length(),
            TerminalLabelSymbolSpans.SymbolTypefaceSpan.class);
        assertEquals(1, spans.length);
        assertSame(SYMBOLS, spans[0].getTypeface());
        assertEquals(1, spanned.getSpanStart(spans[0]));
        assertEquals(2, spanned.getSpanEnd(spans[0]));
    }

    @Test
    public void aSelectionChangeKeepsBothTheWatermarkAndTheBoldStyling() {
        publishNerdMaps();
        TerminalWindowBar bar = new TerminalWindowBar(
            ApplicationProvider.getApplicationContext(), null);
        bar.setWindows(windows(), 0);

        bar.setWindows(windows(), 1);

        TextView selected = tabAt(bar, 1);
        assertEquals(Typeface.BOLD, selected.getTypeface().getStyle());
        assertEquals(Typeface.NORMAL, tabAt(bar, 0).getTypeface().getStyle());
        assertSame(SYMBOLS, bar.chipWatermarkAt(0).glyphFace());
        // The selected chip's watermark is the brighter one, and the other is back at rest.
        assertEquals(1f, bar.chipWatermarkAt(1).selection(), .001f);
        assertEquals(0f, bar.chipWatermarkAt(0).selection(), .001f);
    }

    @Test
    public void withoutConfiguredSymbolMapsTheWatermarkFallsBackToTheBundledFace() {
        TerminalLabelFaces.publish(Typeface.MONOSPACE, new TerminalRenderer.SymbolMap[0]);
        TerminalWindowBar bar = new TerminalWindowBar(
            ApplicationProvider.getApplicationContext(), null);

        bar.setWindows(windows(), 0);

        // No symbol_map is configured, so the watermark takes whichever face the bar could load —
        // never null, or a window's icon would be tofu on every device without a patched font.
        assertEquals("", bar.chipWatermarkAt(0).glyph());
        assertNotNull(bar.chipWatermarkAt(0).glyphFace());
        // A label with nothing to map is left alone, spans and all.
        assertFalse(tabAt(bar, 1).getText() instanceof Spanned);
        assertSame(Typeface.MONOSPACE, tabAt(bar, 1).getTypeface());
    }

    private static void publishNerdMaps() {
        TerminalLabelFaces.publish(Typeface.MONOSPACE, new TerminalRenderer.SymbolMap[]{
            new TerminalRenderer.SymbolMap(0xE000, 0xF8FF, SYMBOLS),
            new TerminalRenderer.SymbolMap(0xF0000, 0xFFFFD, SYMBOLS)
        });
    }

    private static List<TerminalWindowBar.WindowItem> windows() {
        return Arrays.asList(
            new TerminalWindowBar.WindowItem(" herdr", "terminal in herdr"),
            new TerminalWindowBar.WindowItem("plain home", "fish in home"));
    }

    private static TextView tabAt(TerminalWindowBar bar, int index) {
        return (TextView) ((LinearLayout) bar.getChildAt(0)).getChildAt(index);
    }
}
