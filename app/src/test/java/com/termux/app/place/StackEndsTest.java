package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.termux.app.chrome.StackMaterial.RegionLook;
import com.termux.app.page.PageKind;
import com.termux.app.wall.PageRef;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** {@link StackEnds#read} copies exactly what the host answers for one page into a StackEnd. */
public class StackEndsTest {

    /** A host with a fixed answer per question; records the pages it was asked about. */
    private static final class FakeHost implements StackEnds.Host {
        boolean floating;
        /** Bit r set: the page has region r. */
        int regionMask;
        /** Bit r set: region r has an even-out matrix. */
        int evenOutMask;
        StackEnd.StripRule strip = StackEnd.StripRule.BARE;
        int ink;
        int keyboardHeight;
        final List<PageRef> asked = new ArrayList<>();

        @Override public void restGeometry(@NonNull PageRef page, @NonNull StackGeometry out) {
            asked.add(page);
            out.keyboardHeightPx = keyboardHeight;
            out.keyboardPresent = keyboardHeight > 0;
        }

        @Override public boolean floating(@NonNull PageRef page) {
            return floating;
        }

        @Override public boolean regionLook(@NonNull PageRef page, int region,
                                            @NonNull RegionLook out) {
            if ((regionMask & (1 << region)) == 0) return false;
            out.blurDp = 10f + region;
            return true;
        }

        @NonNull @Override public StackEnd.StripRule stripRule(@NonNull PageRef page) {
            return strip;
        }

        @Override public int inkArgb(@NonNull PageRef page) {
            return ink;
        }

        @Override public boolean evenOut(@NonNull PageRef page, int region, @NonNull float[] out) {
            if ((evenOutMask & (1 << region)) == 0) return false;
            out[0] = region + 1f;
            return true;
        }
    }

    @Test
    public void everyPageKindComesBackAsItsOwnEnd() {
        for (PageKind kind : PageKind.values()) {
            PageRef page = PageRef.of(kind, kind == PageKind.DISPLAY ? 7 : -1);
            FakeHost host = new FakeHost();
            StackEnd end = StackEnds.read(page, host);
            assertEquals(kind.name(), page, end.page);
            assertEquals(kind.name(), 1, host.asked.size());
            assertSame(kind.name(), page, host.asked.get(0));
        }
    }

    @Test
    public void theDockStyleDecidesTheTopology() {
        FakeHost host = new FakeHost();
        host.floating = false;
        StackEnd docked = StackEnds.read(PageRef.HOME, host);
        assertFalse(docked.floating);
        assertEquals(StackEnd.TOPOLOGY_DOCKED, docked.topology());
        host.floating = true;
        StackEnd floating = StackEnds.read(PageRef.HOME, host);
        assertTrue(floating.floating);
        assertEquals(StackEnd.TOPOLOGY_FLOATING, floating.topology());
    }

    @Test
    public void aRegionHasALookExactlyWhereTheHostGivesOne() {
        for (int mask = 0; mask < (1 << StackEnd.REGION_COUNT); mask++) {
            FakeHost host = new FakeHost();
            host.regionMask = mask;
            StackEnd end = StackEnds.read(PageRef.TERMINAL, host);
            for (int region = 0; region < StackEnd.REGION_COUNT; region++) {
                boolean expected = (mask & (1 << region)) != 0;
                String at = "mask " + mask + " region " + region;
                assertEquals(at, expected, end.has(region));
                if (expected) {
                    assertNotNull(at, end.look(region));
                    assertEquals(at, 10f + region, end.look(region).blurDp, 0f);
                } else {
                    assertNull(at, end.look(region));
                }
            }
        }
    }

    @Test
    public void eachRegionGetsItsOwnLookObject() {
        FakeHost host = new FakeHost();
        host.regionMask = (1 << StackEnd.REGION_COUNT) - 1;
        StackEnd end = StackEnds.read(PageRef.HOME, host);
        for (int a = 0; a < StackEnd.REGION_COUNT; a++) {
            for (int b = a + 1; b < StackEnd.REGION_COUNT; b++) {
                assertNotSame(end.look(a), end.look(b));
            }
        }
    }

    @Test
    public void anEvenOutMatrixIsKeptOnlyForTheRegionsTheHostFills() {
        for (int mask = 0; mask < (1 << StackEnd.REGION_COUNT); mask++) {
            FakeHost host = new FakeHost();
            host.evenOutMask = mask;
            StackEnd end = StackEnds.read(PageRef.HOME, host);
            for (int region = 0; region < StackEnd.REGION_COUNT; region++) {
                String at = "mask " + mask + " region " + region;
                if ((mask & (1 << region)) != 0) {
                    assertNotNull(at, end.evenOut[region]);
                    assertEquals(at, 20, end.evenOut[region].length);
                    assertEquals(at, region + 1f, end.evenOut[region][0], 0f);
                } else {
                    assertNull(at, end.evenOut[region]);
                }
            }
        }
    }

    @Test
    public void theDefaultHostHasNoEvenOutAtAnyRegion() {
        StackEnds.Host bare = new StackEnds.Host() {
            @Override public void restGeometry(@NonNull PageRef page, @NonNull StackGeometry out) {}
            @Override public boolean floating(@NonNull PageRef page) { return false; }
            @Override public boolean regionLook(@NonNull PageRef page, int region,
                                                @NonNull RegionLook out) { return false; }
            @NonNull @Override public StackEnd.StripRule stripRule(@NonNull PageRef page) {
                return StackEnd.StripRule.BARE;
            }
            @Override public int inkArgb(@NonNull PageRef page) { return 0; }
        };
        StackEnd end = StackEnds.read(PageRef.HOME, bare);
        for (int region = 0; region < StackEnd.REGION_COUNT; region++) {
            assertNull(end.evenOut[region]);
        }
    }

    @Test
    public void everyStripRuleAndInkPassesThrough() {
        int[] inks = {0, 0xFFE8EAED, 0xFF000000, 0x80123456};
        for (StackEnd.StripRule rule : StackEnd.StripRule.values()) {
            for (int ink : inks) {
                FakeHost host = new FakeHost();
                host.strip = rule;
                host.ink = ink;
                StackEnd end = StackEnds.read(PageRef.ASSISTANT, host);
                assertEquals(rule, end.strip);
                assertEquals(ink, end.inkArgb);
            }
        }
    }

    @Test
    public void theRestGeometryIsTheHostsAndTheKeyboardOnlyShowsWhereItHasARegion() {
        FakeHost host = new FakeHost();
        host.keyboardHeight = 300;
        host.regionMask = 1 << StackEnd.KEYBOARD;
        StackEnd with = StackEnds.read(PageRef.TERMINAL, host);
        assertEquals(300, with.rest.keyboardHeightPx);
        assertEquals(300, with.keyboardShownPx());
        host.regionMask = 0;
        StackEnd without = StackEnds.read(PageRef.TERMINAL, host);
        assertEquals(300, without.rest.keyboardHeightPx);
        assertEquals(0, without.keyboardShownPx());
    }
}
