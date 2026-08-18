package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import org.junit.Test;

public class AppDrawerCategoryDragExclusionTest {
    @Test public void everyCategorySourceIsFrozenIneligible() {
        for (boolean interactive : new boolean[]{false, true})
            for (boolean empty : new boolean[]{false, true})
                assertFalse(new AppDrawerDragPolicy.FrozenDown(AppDrawerViewType.CATEGORIES,
                    interactive, empty, true, "app").dragEligible());
    }
}
