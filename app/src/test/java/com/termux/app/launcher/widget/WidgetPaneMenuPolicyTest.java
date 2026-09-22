package com.termux.app.launcher.widget;

import org.junit.Test;

import java.util.Arrays;

import static com.termux.app.launcher.widget.WidgetPaneMenuPolicy.Item.*;
import static org.junit.Assert.*;

public class WidgetPaneMenuPolicyTest {
    @Test public void unsupportedDeviceGetsNoMenu() {
        assertTrue(WidgetPaneMenuPolicy.itemsFor(false, 1, true).isEmpty());
        assertTrue(WidgetPaneMenuPolicy.itemsFor(false, 3, false).isEmpty());
    }

    @Test public void populatedPageOffersAddAndEditAndNeverAPage() {
        assertEquals(Arrays.asList(ADD_WIDGET, EDIT_WIDGETS),
            WidgetPaneMenuPolicy.itemsFor(true, 1, false));
        assertEquals("populated pages are never removable",
            Arrays.asList(ADD_WIDGET, EDIT_WIDGETS),
            WidgetPaneMenuPolicy.itemsFor(true, 2, false));
    }

    @Test public void emptyPageSkipsEditAndOffersRemoveOnlyPastOnePage() {
        assertEquals(Arrays.asList(ADD_WIDGET),
            WidgetPaneMenuPolicy.itemsFor(true, 1, true));
        assertEquals(Arrays.asList(ADD_WIDGET, REMOVE_PAGE),
            WidgetPaneMenuPolicy.itemsFor(true, 2, true));
    }

    @Test public void nothingAddsAPageByHandAnyMore() {
        for (WidgetPaneMenuPolicy.Item item : WidgetPaneMenuPolicy.Item.values()) {
            assertNotEquals("the spare page is the only way a page appears", "ADD_PAGE",
                item.name());
        }
    }
}
