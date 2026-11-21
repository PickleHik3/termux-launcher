package com.termux.app.place.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/** The one icon size (ADR 0020 §3, spec §8a): four steps, the fold, the sizes field. */
public class IconSizeTest {

    @Test
    public void theStepsRiseAndNameThemselves() {
        int last = 0;
        for (IconSize size : IconSize.values()) {
            assertSame(size, IconSize.forKey(size.key));
            assertEquals(size.dp > last, true);
            last = size.dp;
        }
        assertNull(IconSize.forKey("huge"));
    }

    @Test
    public void todaysDefaultsLandOnTheirSteps() {
        assertSame("the default dock draws 40 dp", IconSize.S, IconSize.nearest(40));
        assertSame("Home's 48 dp", IconSize.M, IconSize.nearest(48));
        assertSame("Home's 54 dp", IconSize.L, IconSize.nearest(54));
        assertSame("Home's 60 dp", IconSize.XL, IconSize.nearest(60));
        assertSame(IconSize.S, IconSize.nearest(21));
        assertSame(IconSize.M, IconSize.DEFAULT);
    }

    @Test
    public void homesIconWinsOverTheDocksAtTheFold() {
        // The developer's phone: dock_height 2.994 portrait draws 55 dp; Home was never sized.
        assertSame(IconSize.L, IconSize.fold(null, 55));
        assertSame("Home's choice wins", IconSize.M, IconSize.fold(48, 55));
        assertSame(IconSize.DEFAULT, IconSize.fold(null, null));
    }

    @Test
    public void theSizesDocumentHoldsOneValueForBothOrientations() {
        Sizes sizes = IconSize.XL.into(Sizes.EMPTY);
        assertSame(IconSize.XL, IconSize.of(sizes));
        assertSame(IconSize.DEFAULT, IconSize.of(Sizes.EMPTY));
        assertEquals("xl", sizes.document().wide().get(IconSize.FIELD));
    }
}
