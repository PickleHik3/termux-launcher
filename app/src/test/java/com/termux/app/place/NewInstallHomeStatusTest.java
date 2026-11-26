package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;
import com.termux.app.place.layout.LayoutKind;
import com.termux.app.place.layout.Layouts;
import com.termux.app.place.layout.LayoutsJson;

import org.junit.Test;

/**
 * Home's status bar is hidden on a new install and shown on every install that predates it: an
 * absent key still reads shown, and what the appearance reset folds is a new install's.
 */
public class NewInstallHomeStatusTest {

    @Test public void anAbsentKeyStillReadsShownForEveryKind() {
        for (PageKind kind : PageKind.values()) {
            assertTrue(kind.name(), PlaceLayoutStore.kindShowsByDefault(kind, Element.STATUS));
        }
    }

    @Test public void aNewInstallWritesHomesStatusHiddenInBothOrientationsAndNothingElse() {
        String[] keys = PlaceLayoutStore.newInstallHiddenHomeStatusKeys();
        assertEquals(PlaceOrientation.values().length, keys.length);
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            String key = PlaceLayoutStore.kindKey(PageKind.HOME, orientation, Element.STATUS);
            boolean held = false;
            for (String k : keys) held |= k.equals(key);
            assertTrue(key, held);
        }
    }

    @Test public void theAppearanceResetFoldsHomeHiddenAndTheTerminalLeftToItsDefault() {
        Layouts fresh = LayoutReset.newInstall(Layouts.shipped(), null, 360);
        for (PlaceOrientation orientation : PlaceOrientation.values()) {
            assertEquals(Boolean.FALSE, fresh.kind(LayoutKind.HOME).orientation(orientation)
                .get(LayoutsJson.dressField(Element.STATUS)));
            assertNull(fresh.kind(LayoutKind.TERMINAL).orientation(orientation)
                .get(LayoutsJson.dressField(Element.STATUS)));
        }
    }
}
