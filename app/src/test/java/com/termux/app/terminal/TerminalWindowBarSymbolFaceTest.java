package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

/** The window row has to draw its labels with the faces the panes are given, icons included. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class TerminalWindowBarSymbolFaceTest {

    private static final Typeface SYMBOLS = Typeface.SERIF;

    @After
    public void releaseTheSharedFaces() {
        TerminalLabelFaces.resetForTests();
    }

    @Test
    public void aTabLabelsIconIsDrawnByTheMappedSymbolsFace() {
        publishNerdMaps();
        TerminalWindowBar bar = new TerminalWindowBar(
            ApplicationProvider.getApplicationContext(), null);

        bar.setWindows(windows(), 0);

        TextView tab = tabAt(bar, 0);
        assertTrue("an icon label needs spans to reach the symbols face",
            tab.getText() instanceof Spanned);
        Spanned spanned = (Spanned) tab.getText();
        TerminalLabelSymbolSpans.SymbolTypefaceSpan[] spans = spanned.getSpans(0, spanned.length(),
            TerminalLabelSymbolSpans.SymbolTypefaceSpan.class);
        assertEquals(1, spans.length);
        assertSame(SYMBOLS, spans[0].getTypeface());
        assertEquals(0, spanned.getSpanStart(spans[0]));
        assertEquals(1, spanned.getSpanEnd(spans[0]));
        // The plain ASCII pill keeps a plain label: nothing mapped, nothing allocated.
        assertFalse(tabAt(bar, 1).getText() instanceof Spanned);
    }

    @Test
    public void aSelectionChangeKeepsBothTheSpansAndTheBoldStyling() {
        publishNerdMaps();
        TerminalWindowBar bar = new TerminalWindowBar(
            ApplicationProvider.getApplicationContext(), null);
        bar.setWindows(windows(), 0);

        bar.setWindows(windows(), 1);

        TextView selected = tabAt(bar, 1);
        assertEquals(Typeface.BOLD, selected.getTypeface().getStyle());
        assertEquals(Typeface.NORMAL, tabAt(bar, 0).getTypeface().getStyle());
        assertTrue(tabAt(bar, 0).getText() instanceof Spanned);
    }

    @Test
    public void withoutConfiguredSymbolMapsIconsFallBackToTheBundledFace() {
        TerminalLabelFaces.publish(Typeface.MONOSPACE, new TerminalRenderer.SymbolMap[0]);
        TerminalWindowBar bar = new TerminalWindowBar(
            ApplicationProvider.getApplicationContext(), null);

        bar.setWindows(windows(), 0);

        // No symbol_map is configured, but the bundled symbols face still fills the PUA run — an
        // icon in a window name used to be tofu on every device without a patched terminal font.
        assertTrue(tabAt(bar, 0).getText() instanceof Spanned);
        Spanned spanned = (Spanned) tabAt(bar, 0).getText();
        assertEquals(0, spanned.getSpans(0, spanned.length(),
            TerminalLabelSymbolSpans.SymbolTypefaceSpan.class).length);
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
            new TerminalWindowBar.WindowItem("\ue795 herdr", "terminal in herdr"),
            new TerminalWindowBar.WindowItem("plain home", "fish in home"));
    }

    private static TextView tabAt(TerminalWindowBar bar, int index) {
        return (TextView) ((LinearLayout) bar.getChildAt(0)).getChildAt(index);
    }
}
