package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import org.junit.Test;

public class AppDrawerSearchDragExclusionTest {
    @Test public void nonEmptyQueryCannotPickupInSupportedViewTypes() {
        assertFalse(new AppDrawerDragPolicy.FrozenDown(AppDrawerViewType.VERTICAL,
            true, false, true, "app").dragEligible());
        assertFalse(new AppDrawerDragPolicy.FrozenDown(AppDrawerViewType.HORIZONTAL,
            true, false, true, "app").dragEligible());
    }
}
