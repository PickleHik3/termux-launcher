package com.termux.shared.termux.settings.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Collections;

/** The font sizes as registry ids, with a faked density and display id. */
public class FontSizeSettingsAdapterTest {

    private final InMemorySettingStore store = new InMemorySettingStore();

    private SettingsRegistry registry(float density, String display) {
        return new SettingsRegistry(which -> {
            throw new AssertionError("a font size id reads no registry store");
        }, Collections.emptyList(), Collections.singletonList(
            new FontSizeSettingsAdapter(store, density, display)));
    }

    @Test
    public void theRangeFollowsTheDensity() {
        FontSizeSettingsAdapter.Range at3 = FontSizeSettingsAdapter.range(3f);
        assertEquals(12, at3.min);
        assertEquals(256, at3.max);
        assertEquals(36, at3.defaultSize);
        FontSizeSettingsAdapter.Range at275 = FontSizeSettingsAdapter.range(2.75f);
        assertEquals(11, at275.min);
        assertEquals(32, at275.defaultSize);
        assertEquals(30, at3.clamp(30));
        assertEquals(12, at3.clamp(2));
        assertEquals(256, at3.clamp(999));
    }

    @Test
    public void theDefaultDisplayUsesTheBareKeys() {
        SettingsRegistry registry = registry(3f, "");
        assertEquals("36", registry.get("terminal.font_size"));
        assertNotNull(registry.set("terminal.font_size", "40").change());
        assertEquals("40", store.getString("fontsize"));
        assertNotNull(registry.set("terminal.compact_font_size", "20").change());
        assertEquals("20", store.getString("compact_fontsize"));
        assertNotNull(registry.set("terminal.scratchpad_font_size", "24").change());
        assertEquals("24", store.getString("fontsize_scratchpad"));
        assertEquals("24", registry.get("terminal.scratchpad_font_size"));
    }

    @Test
    public void anotherDisplayCarriesItsIdInTheKey() {
        SettingsRegistry registry = registry(3f, "2");
        assertNotNull(registry.set("terminal.font_size", "40").change());
        assertEquals("40", store.getString("fontsize2"));
        assertNull(store.getString("fontsize"));
        assertNotNull(registry.set("terminal.scratchpad_font_size", "24").change());
        assertEquals("24", store.getString("fontsize_scratchpad2"));
        assertNotNull(registry.set("terminal.compact_font_size", "20").change());
        assertEquals("20", store.getString("compact_fontsize2"));
    }

    @Test
    public void aValueOutsideTheRangeIsRefusedWithTheRangeAndSavesNothing() {
        SettingsRegistry registry = registry(3f, "");
        SettingsRegistry.Refusal refusal = registry.set("terminal.font_size", "300").refusal();
        assertEquals("terminal.font_size is 12–256", refusal.reason);
        assertEquals("12–256", refusal.allowed);
        assertEquals("terminal.font_size is 12–256", registry.check("terminal.font_size", "5").reason);
        assertEquals("terminal.font_size is 12–256",
            registry.set("terminal.font_size", "big").refusal().reason);
        assertNull(store.getString("fontsize"));
    }

    @Test
    public void aStoredValueOutsideTheRangeReadsClamped() {
        store.putString("fontsize", "9000");
        assertEquals("256", registry(3f, "").get("terminal.font_size"));
        store.putString("fontsize", "x");
        assertEquals("36", registry(3f, "").get("terminal.font_size"));
    }

    @Test
    public void anUnsetCompactOrScratchpadSizeFollowsTheTerminals() {
        SettingsRegistry registry = registry(3f, "");
        store.putString("fontsize", "40");
        assertEquals("40", registry.get("terminal.compact_font_size"));
        assertEquals("40", registry.get("terminal.scratchpad_font_size"));
    }

    @Test
    public void itAnswersOnlyItsOwnIdsAndListsThem() {
        FontSizeSettingsAdapter adapter = new FontSizeSettingsAdapter(store, 3f, "");
        assertNull(adapter.declaration("terminal.corner_radius"));
        assertEquals(3, adapter.declarations().size());
        assertEquals("12–256", adapter.declaration("terminal.font_size").allowed());
        assertEquals(SettingRefresh.TEXT_SIZE, adapter.declaration("terminal.font_size").refresh);
    }
}
