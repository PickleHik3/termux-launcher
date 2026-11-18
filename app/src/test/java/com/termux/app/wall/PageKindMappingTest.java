package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.termux.app.page.PageKind;
import com.termux.app.terminal.io.ExtraKeysTarget;

import org.junit.Test;

public class PageKindMappingTest {

    @Test
    public void everyWallPageMapsToItsKind() {
        assertEquals(PageKind.HOME, PaneWallPage.WIDGETS.pageKind());
        assertEquals(PageKind.TERMINAL, PaneWallPage.TERMINAL.pageKind());
        assertEquals(PageKind.DISPLAY, PaneWallPage.DISPLAY.pageKind());
        assertEquals(PageKind.ASSISTANT, PaneWallPage.ASSISTANT.pageKind());
    }

    @Test
    public void everyKindHasItsOwnExtraKeysTargetThatMarksItsPlace() {
        for (PageKind kind : PageKind.values()) {
            ExtraKeysTarget target = ExtraKeysTarget.forPage(kind);
            assertNotEquals(kind.name(), ExtraKeysTarget.GLOBAL, target);
            assertEquals(kind.name(), kind, target.markingPlace().pageKind());
        }
    }
}
