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
            ExtraKeysTarget target = ExtraKeysTarget.forPage(kind);
            assertNotEquals(kind.name(), ExtraKeysTarget.GLOBAL, target);
            assertEquals(kind.name(), kind, target.markingPlace());
        }
    }
}
