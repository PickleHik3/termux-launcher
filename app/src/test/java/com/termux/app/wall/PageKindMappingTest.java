package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.termux.app.page.PageKind;
import com.termux.app.terminal.io.ExtraKeysTarget;

import org.junit.Test;

public class PageKindMappingTest {

    @Test
    public void everyKindHasItsOwnExtraKeysTargetThatMarksItsPlace() {
        for (PageKind kind : PageKind.values()) {
            // The one exception: a Linux window types through the launcher keyboard like a terminal,
            // so it shares the TERMINAL target rather than adding a key-set of its own.
            if (kind == PageKind.LINUX) {
                assertEquals(ExtraKeysTarget.TERMINAL, ExtraKeysTarget.forPage(kind));
                continue;
            }
            ExtraKeysTarget target = ExtraKeysTarget.forPage(kind);
            assertNotEquals(kind.name(), ExtraKeysTarget.GLOBAL, target);
            assertEquals(kind.name(), kind, target.markingPlace());
        }
    }
}
