package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HiddenElementsPopupPlacementTest {

    @Test
    public void centresOnTheAnchorWhenItFits() {
        assertEquals(150, HiddenElementsPopup.leftCentredOn(200, 100, 20, 380));
    }

    @Test
    public void staysInsideTheFrameAtBothEdges() {
        assertEquals(20, HiddenElementsPopup.leftCentredOn(30, 100, 20, 380));
        assertEquals(280, HiddenElementsPopup.leftCentredOn(370, 100, 20, 380));
    }

    @Test
    public void widerThanTheFrameStartsAtItsLeftEdge() {
        assertEquals(20, HiddenElementsPopup.leftCentredOn(200, 500, 20, 380));
    }

    @Test
    public void endEdgeSitsOnTheAnchorsEndEdge() {
        assertEquals(260, HiddenElementsPopup.leftEndAlignedOn(360, 100, 16, 380));
    }

    @Test
    public void endAlignedStaysInsideTheScreenMargins() {
        assertEquals(280, HiddenElementsPopup.leftEndAlignedOn(395, 100, 16, 380));
        assertEquals(16, HiddenElementsPopup.leftEndAlignedOn(60, 100, 16, 380));
        assertEquals(16, HiddenElementsPopup.leftEndAlignedOn(300, 500, 16, 380));
    }

    @Test
    public void popupEndsAboveTheSheetWithAGap() {
        assertEquals(500, HiddenElementsPopup.topAbove(700, 192, 8));
        assertEquals(700, 500 + 192 + 8);
    }
}
