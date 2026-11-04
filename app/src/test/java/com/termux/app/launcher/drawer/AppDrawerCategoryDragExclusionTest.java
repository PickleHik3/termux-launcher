package com.termux.app.launcher.drawer;

import static org.junit.Assert.*;
import org.junit.Test;

public class AppDrawerCategoryDragExclusionTest {
    @Test public void categorySourceIsEligibleOnlyWhenInteractiveWithEmptyQuery() {
        for (boolean interactive : new boolean[]{false, true})
            for (boolean empty : new boolean[]{false, true})
                assertEquals(interactive && empty,
                    new AppDrawerDragPolicy.FrozenDown(AppDrawerViewType.CATEGORIES,
                        interactive, empty, true, "app").dragEligible());
    }
}
