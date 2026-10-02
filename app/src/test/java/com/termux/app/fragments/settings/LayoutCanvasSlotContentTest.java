package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class LayoutCanvasSlotContentTest {

    @Test
    public void suppliedItemsDriveTheCountCappedByWhatFits() {
        // 200 long at 20 per slot: floor(184 / 20) = 9 fit.
        assertEquals(5, LayoutCanvasArtwork.slotsForContent(5, 99, 200f, 20f));
        assertEquals(9, LayoutCanvasArtwork.slotsForContent(12, 99, 200f, 20f));
    }

    @Test
    public void noSuppliedItemsFallsBackToThePackRule() {
        assertEquals(LayoutCanvasArtwork.slotsFor(-1, 200f, 20f),
            LayoutCanvasArtwork.slotsForContent(0, -1, 200f, 20f));
        assertEquals(3, LayoutCanvasArtwork.slotsForContent(0, 3, 200f, 20f));
    }

    @Test
    public void aKeySlotCarriesAnIconOrItsTextOrNeither() {
        LayoutCanvasView.KeySlot icon = LayoutCanvasView.KeySlot.ofIcon(new ColorDrawable(0));
        assertTrue(icon.hasIcon());
        assertFalse(icon.hasLabel());
        LayoutCanvasView.KeySlot text = LayoutCanvasView.KeySlot.ofText("ESC");
        assertFalse(text.hasIcon());
        assertTrue(text.hasLabel());
        LayoutCanvasView.KeySlot neither = LayoutCanvasView.KeySlot.ofText("");
        assertFalse(neither.hasIcon());
        assertFalse(neither.hasLabel());
    }

    @Test
    public void suppliedKeySlotsDriveTheKeyBarsSlotCount() {
        // Seven slots are supplied, each an icon or text: the count is theirs, capped by fit.
        assertEquals(7, LayoutCanvasArtwork.slotsForContent(7, -1, 200f, 18f));
        assertEquals(5, LayoutCanvasArtwork.slotsForContent(7, -1, 110f, 18f));
    }

    @Test
    public void keyGlyphSizesToItsBoundsAndTakesATint() {
        LayoutCanvasKeyGlyph glyph = new LayoutCanvasKeyGlyph("\uf015", Typeface.DEFAULT);
        glyph.setBounds(0, 0, 18, 18);
        glyph.setColorFilter(new android.graphics.PorterDuffColorFilter(0xff112233,
            android.graphics.PorterDuff.Mode.SRC_IN));
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(18, 18,
            android.graphics.Bitmap.Config.ARGB_8888);
        glyph.draw(new android.graphics.Canvas(bitmap));
        assertEquals(18, glyph.getBounds().width());
    }

    @Test
    public void keyLabelsAreCutToTheSlot() {
        assertEquals("ESC", LayoutCanvasArtwork.fitLabel("ESC", 3));
        assertEquals("PG", LayoutCanvasArtwork.fitLabel("PGUP", 2));
        assertEquals("", LayoutCanvasArtwork.fitLabel("X", 0));
    }
}
