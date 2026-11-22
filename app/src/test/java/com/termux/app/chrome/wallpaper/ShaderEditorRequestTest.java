package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.scene.ShaderSafety;

import org.junit.Test;

/** Send to AI's door into the shader editor: what the extra carries and where it lands. */
public class ShaderEditorRequestTest {

    @Test
    public void theExtraCarriesTheIdAndThePhotoFlow() {
        ShaderEditorRequest saved = ShaderEditorRequest.parse(ShaderEditorRequest.encode("s1", false));
        assertEquals("s1", saved.id);
        assertFalse(saved.photoFlow);
        ShaderEditorRequest photo = ShaderEditorRequest.parse(ShaderEditorRequest.encode("s1", true));
        assertEquals("s1", photo.id);
        assertTrue(photo.photoFlow);
    }

    @Test
    public void anExtraNamingNoShaderIsIgnored() {
        assertNull(ShaderEditorRequest.parse(null));
        assertNull(ShaderEditorRequest.parse(""));
        assertNull(ShaderEditorRequest.parse("photo:"));
        assertNull(ShaderEditorRequest.parse("../x"));
        assertFalse(ShaderEditorRequest.send(null));
    }

    @Test
    public void aRequestWaitsOnlyForThePickerBeingBuilt() {
        assertTrue(ShaderEditorRequest.stillWaiting(1_000, 1_000));
        assertTrue(ShaderEditorRequest.stillWaiting(1_000, 1_000 + ShaderEditorRequest.WAIT_MS));
        assertFalse(ShaderEditorRequest.stillWaiting(1_000, 1_001 + ShaderEditorRequest.WAIT_MS));
        assertFalse("a clock that went back", ShaderEditorRequest.stillWaiting(1_000, 999));
    }

    @Test
    public void whereARequestLands() {
        assertEquals(ShaderEditorRequest.Landing.STAYS, ShaderEditorRequest.landing(true, true, true, true));
        assertEquals(ShaderEditorRequest.Landing.KEEPS_EDITS, ShaderEditorRequest.landing(true, false, true, true));
        assertEquals(ShaderEditorRequest.Landing.KEEPS_EDITS, ShaderEditorRequest.landing(true, false, true, false));
        assertEquals(ShaderEditorRequest.Landing.EDITOR, ShaderEditorRequest.landing(true, false, false, true));
        assertEquals(ShaderEditorRequest.Landing.EDITOR, ShaderEditorRequest.landing(false, false, false, true));
        assertEquals(ShaderEditorRequest.Landing.LIST, ShaderEditorRequest.landing(false, false, false, false));
        assertEquals(ShaderEditorRequest.Landing.LIST, ShaderEditorRequest.landing(true, false, false, false));
    }

    @Test
    public void anEditIsAnyChangeToTextNameKnobsOrImages() {
        ShaderEditorPage.Draft shown = draft("A", "code", ShaderSafety.Quality.HALF, 30, "p.jpg", null);
        assertFalse(ShaderEditorLogic.edited(shown, draft("A", "code", ShaderSafety.Quality.HALF, 30, "p.jpg", null)));
        assertTrue(ShaderEditorLogic.edited(shown, draft("A", "code2", ShaderSafety.Quality.HALF, 30, "p.jpg", null)));
        assertTrue(ShaderEditorLogic.edited(shown, draft("B", "code", ShaderSafety.Quality.HALF, 30, "p.jpg", null)));
        assertTrue(ShaderEditorLogic.edited(shown, draft("A", "code", ShaderSafety.Quality.FULL, 30, "p.jpg", null)));
        assertTrue(ShaderEditorLogic.edited(shown, draft("A", "code", ShaderSafety.Quality.HALF, 60, "p.jpg", null)));
        assertTrue(ShaderEditorLogic.edited(shown, draft("A", "code", ShaderSafety.Quality.HALF, 30, "q.jpg", null)));
        assertTrue(ShaderEditorLogic.edited(shown, draft("A", "code", ShaderSafety.Quality.HALF, 30, "p.jpg", "d.png")));
    }

    private static ShaderEditorPage.Draft draft(String name, String source, ShaderSafety.Quality q, int rate,
                                                String texture, String depth) {
        return new ShaderEditorPage.Draft("s1", name, source, q, rate, texture, texture, depth, depth, false, false);
    }
}
