package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.StyleSpan;

import com.termux.view.TerminalRenderer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Window names carry Nerd Font icons, so a label has to reach the symbol_map faces the terminal
 * itself draws those code points with. Span building only — the view is exercised elsewhere.
 */
@RunWith(RobolectricTestRunner.class)
public class TerminalLabelSymbolSpansTest {

    /** Private use area: the classic Nerd Font block, entirely inside the BMP. */
    private static final int PRIVATE_USE_FIRST = 0xE000;
    private static final int PRIVATE_USE_LAST = 0xF8FF;
    /** Material Design Nerd icons, above the BMP, so a label carries them as surrogate pairs. */
    private static final int MATERIAL_FIRST = 0xF0000;
    private static final int MATERIAL_LAST = 0xFFFFD;

    private static final Typeface SYMBOLS = Typeface.SERIF;
    private static final Typeface OTHER_SYMBOLS = Typeface.SANS_SERIF;

    private static final TerminalRenderer.SymbolMap[] NERD_MAPS = {
        new TerminalRenderer.SymbolMap(PRIVATE_USE_FIRST, PRIVATE_USE_LAST, SYMBOLS),
        new TerminalRenderer.SymbolMap(MATERIAL_FIRST, MATERIAL_LAST, SYMBOLS)
    };

    @Test
    public void asciiLabelIsReturnedUntouchedWithoutAllocatingSpans() {
        String label = "herdr";

        assertSame(label, TerminalLabelSymbolSpans.apply(label, NERD_MAPS));
    }

    @Test
    public void noSymbolMapsLeaveEvenAnIconLabelExactlyAsItWas() {
        String label = glyph(0xE795) + " home";

        assertSame(label, TerminalLabelSymbolSpans.apply(label,
            new TerminalRenderer.SymbolMap[0]));
        assertSame(label, TerminalLabelSymbolSpans.apply(label, null));
    }

    @Test
    public void bmpIconIsSpannedOverExactlyItsOwnCodePoint() {
        String label = glyph(0xE795) + " home";

        Spanned spanned = spanned(label);
        TerminalLabelSymbolSpans.SymbolTypefaceSpan[] spans = symbolSpans(spanned);
        assertEquals(1, spans.length);
        assertSame(SYMBOLS, spans[0].getTypeface());
        assertEquals(0, spanned.getSpanStart(spans[0]));
        assertEquals(1, spanned.getSpanEnd(spans[0]));
        assertEquals(label, spanned.toString());
    }

    @Test
    public void astralIconIsSpannedAcrossBothHalvesOfItsSurrogatePair() {
        String icon = glyph(0xF0493);
        assertEquals(2, icon.length());
        String label = icon + " fish";

        Spanned spanned = spanned(label);
        TerminalLabelSymbolSpans.SymbolTypefaceSpan[] spans = symbolSpans(spanned);
        assertEquals(1, spans.length);
        assertEquals(0, spanned.getSpanStart(spans[0]));
        assertEquals(2, spanned.getSpanEnd(spans[0]));
    }

    @Test
    public void consecutiveMappedCodePointsShareOneSpan() {
        String label = glyph(0xE795) + glyph(0xF0493) + glyph(0xE7C5) + " dev";

        Spanned spanned = spanned(label);
        TerminalLabelSymbolSpans.SymbolTypefaceSpan[] spans = symbolSpans(spanned);
        assertEquals(1, spans.length);
        assertEquals(0, spanned.getSpanStart(spans[0]));
        assertEquals(4, spanned.getSpanEnd(spans[0]));
    }

    @Test
    public void adjacentIconsFromDifferentFacesGetOneSpanEach() {
        TerminalRenderer.SymbolMap[] maps = {
            new TerminalRenderer.SymbolMap(PRIVATE_USE_FIRST, PRIVATE_USE_LAST, SYMBOLS),
            new TerminalRenderer.SymbolMap(MATERIAL_FIRST, MATERIAL_LAST, OTHER_SYMBOLS)
        };
        String label = glyph(0xE795) + glyph(0xF0493) + " home";

        CharSequence result = TerminalLabelSymbolSpans.apply(label, maps);
        Spanned spanned = (Spanned) result;
        TerminalLabelSymbolSpans.SymbolTypefaceSpan[] spans = symbolSpans(spanned);
        assertEquals(2, spans.length);
        // Spans come back in the order they were set, which is the order of the runs.
        assertSame(SYMBOLS, spans[0].getTypeface());
        assertEquals(0, spanned.getSpanStart(spans[0]));
        assertEquals(1, spanned.getSpanEnd(spans[0]));
        assertSame(OTHER_SYMBOLS, spans[1].getTypeface());
        assertEquals(1, spanned.getSpanStart(spans[1]));
        assertEquals(3, spanned.getSpanEnd(spans[1]));
    }

    @Test
    public void aLaterRangeWinsTheOverlapExactlyLikeTheRenderer() {
        TerminalRenderer.SymbolMap[] maps = {
            new TerminalRenderer.SymbolMap(PRIVATE_USE_FIRST, PRIVATE_USE_LAST, SYMBOLS),
            new TerminalRenderer.SymbolMap(0xE795, 0xE795, OTHER_SYMBOLS)
        };

        Spanned spanned = (Spanned) TerminalLabelSymbolSpans.apply(glyph(0xE795) + " home", maps);
        TerminalLabelSymbolSpans.SymbolTypefaceSpan[] spans = symbolSpans(spanned);
        assertEquals(1, spans.length);
        assertSame(OTHER_SYMBOLS, spans[0].getTypeface());
    }

    @Test
    public void unmappedIconIsLeftToTheRegularFaceWithoutSpans() {
        // A private-use glyph outside every configured range: nothing to swap it to.
        String label = glyph(0xE795) + " home";
        TerminalRenderer.SymbolMap[] maps = {
            new TerminalRenderer.SymbolMap(MATERIAL_FIRST, MATERIAL_LAST, SYMBOLS)
        };

        assertSame(label, TerminalLabelSymbolSpans.apply(label, maps));
    }

    @Test
    public void anAsciiMapStillReachesAnAsciiLabel() {
        // The ASCII fast path may only be taken when no map claims an ASCII code point.
        TerminalRenderer.SymbolMap[] maps = {
            new TerminalRenderer.SymbolMap('A', 'A', SYMBOLS)
        };

        Spanned spanned = (Spanned) TerminalLabelSymbolSpans.apply("A home", maps);
        assertEquals(1, symbolSpans(spanned).length);
    }

    @Test
    public void spansAlreadyOnTheLabelAreKept() {
        SpannableString label = new SpannableString(glyph(0xE795) + " home");
        label.setSpan(new StyleSpan(Typeface.ITALIC), 2, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        CharSequence result = TerminalLabelSymbolSpans.apply(label, NERD_MAPS);
        assertNotSame(label, result);
        Spanned spanned = (Spanned) result;
        assertEquals(1, symbolSpans(spanned).length);
        assertEquals(1, spanned.getSpans(0, spanned.length(), StyleSpan.class).length);
    }

    @Test
    public void selectedStateBoldSurvivesTheSwapToTheSymbolsFace() {
        TerminalLabelSymbolSpans.SymbolTypefaceSpan span =
            new TerminalLabelSymbolSpans.SymbolTypefaceSpan(SYMBOLS);
        TextPaint paint = new TextPaint();
        // What the tab does for the selected window: setTypeface(terminalFace, Typeface.BOLD).
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        span.updateDrawState(paint);

        assertTrue("bold has to survive, really or synthesized",
            paint.getTypeface().getStyle() == Typeface.BOLD || paint.isFakeBoldText());
        assertNotSame("the run must move off the regular face", Typeface.MONOSPACE,
            paint.getTypeface());
    }

    @Test
    public void unselectedStateGetsThePlainSymbolsFaceWithNoSynthesis() {
        TerminalLabelSymbolSpans.SymbolTypefaceSpan span =
            new TerminalLabelSymbolSpans.SymbolTypefaceSpan(SYMBOLS);
        TextPaint paint = new TextPaint();
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));

        span.updateMeasureState(paint);

        assertSame(SYMBOLS, paint.getTypeface());
        assertFalse(paint.isFakeBoldText());
    }

    @Test
    public void measureAndDrawPaintsAgreeSoTheRunIsNotClipped() {
        TerminalLabelSymbolSpans.SymbolTypefaceSpan span =
            new TerminalLabelSymbolSpans.SymbolTypefaceSpan(SYMBOLS);
        TextPaint draw = new TextPaint();
        TextPaint measure = new TextPaint();
        draw.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        measure.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        span.updateDrawState(draw);
        span.updateMeasureState(measure);

        // Same face and same synthesis on both paints. Compared by style rather than by identity:
        // a derived typeface is not required to be the cached same instance.
        assertEquals(draw.getTypeface().getStyle(), measure.getTypeface().getStyle());
        assertEquals(draw.isFakeBoldText(), measure.isFakeBoldText());
        assertEquals(draw.getTextSkewX(), measure.getTextSkewX(), 0f);
    }

    private static Spanned spanned(String label) {
        return (Spanned) TerminalLabelSymbolSpans.apply(label, NERD_MAPS);
    }

    private static TerminalLabelSymbolSpans.SymbolTypefaceSpan[] symbolSpans(
            Spanned spanned) {
        return spanned.getSpans(0, spanned.length(),
            TerminalLabelSymbolSpans.SymbolTypefaceSpan.class);
    }

    private static String glyph(int codePoint) {
        return new String(Character.toChars(codePoint));
    }
}
