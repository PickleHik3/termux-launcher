package com.termux.launcherctl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.junit.Test;

import java.util.Collections;

/** The look grid as registry ids: refused and answered as the {@code look} verb does. */
public class LookSettingsAdapterTest {

    private final LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
    private final LookCommandTest.MemoryBackground background = new LookCommandTest.MemoryBackground();
    private int changed;
    private final SettingsRegistry registry = new SettingsRegistry(which -> {
        throw new AssertionError("a look id reads no registry store");
    }, Collections.emptyList(), Collections.singletonList(
        new LookSettingsAdapter(grid, background, () -> changed++)));

    @Test
    public void anElementsOpacityIsReadAndSavedInTheGrid() {
        SettingsRegistry.Change change = registry.set("look.dock.opacity", "60").change();
        assertNotNull(change);
        assertEquals("60", change.newValue);
        assertEquals(60, grid.value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));
        assertFalse(grid.has(LookGrid.Property.OPACITY, null));
        assertEquals("60", registry.get("look.dock.opacity"));
        assertEquals(1, changed);
    }

    @Test
    public void withoutAnElementItIsGlobals() {
        assertNotNull(registry.set("look.opacity", "30").change());
        assertEquals(30, grid.value(LookGrid.Property.OPACITY, null));
        assertEquals("30", registry.get("look.opacity"));
    }

    @Test
    public void aValueOutsideTheRangeIsClampedAsTheVerbClampsIt() {
        assertEquals("100", registry.set("look.dock.opacity", "400").change().newValue);
        assertEquals(100, grid.value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));
    }

    @Test
    public void aValueThatIsNoNumberIsRefusedAsTheVerbRefusesIt() {
        SettingsRegistry.Refusal refusal = registry.set("look.dock.opacity", "loud").refusal();
        assertEquals("opacity takes a whole number", refusal.reason);
        assertEquals("0–100", refusal.allowed);
        assertFalse(grid.has(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));
        assertEquals(0, changed);
    }

    @Test
    public void aSurfaceOwnsOnlyOpacityAndBlur() {
        SettingsRegistry.Refusal refusal = registry.set("look.dock.grain", "10").refusal();
        assertTrue(refusal.reason, refusal.reason.contains("Global's only"));
        assertNotNull(registry.set("look.dock.blur", "2").change());
    }

    @Test
    public void colourAndCornersTakeTheirOwnWords() {
        assertEquals("tertiary", registry.set("look.colour", "tertiary").change().newValue);
        assertEquals("colour takes primary, secondary or tertiary",
            registry.set("look.colour", "teal").refusal().reason);
        assertEquals("auto", registry.set("look.corners", "auto").change().newValue);
        assertEquals("12", registry.set("look.corners", "12").change().newValue);
    }

    @Test
    public void blurReadsAsItsStopAndTakesDp() {
        assertNotNull(registry.set("look.dock.blur", "12dp").change());
        assertEquals(String.valueOf(grid.value(LookGrid.Property.FROST, LookGrid.Element.DOCK)),
            registry.get("look.dock.blur"));
    }

    @Test
    public void theBackgroundKeepsItsOwnNumbers() {
        assertEquals("40", registry.set("look.background.dim", "40").change().newValue);
        assertEquals(40, background.get(LookCommand.Setting.DIM));
        assertEquals("40", registry.get("look.background.dim"));
        assertNull(registry.declaration("look.background.grain"));
    }

    @Test
    public void anUnknownPropertyIsNoSetting() {
        assertNull(registry.get("look.sparkle"));
        assertNull(registry.declaration("look.dock.sparkle"));
        assertNull(registry.declaration("look"));
        assertNull(registry.declaration("look.a.b.c"));
    }

    @Test
    public void listHoldsTheLookIdsWithTheirRangesAndValues() {
        registry.set("look.dock.opacity", "60");
        SettingsRegistry.Entry opacity = null;
        SettingsRegistry.Entry colour = null;
        for (SettingsRegistry.Entry entry : registry.list()) {
            if (entry.declaration.id.equals("look.dock.opacity")) opacity = entry;
            if (entry.declaration.id.equals("look.colour")) colour = entry;
        }
        assertNotNull(opacity);
        assertEquals("60", opacity.value);
        assertEquals("0–100", opacity.declaration.allowed());
        assertEquals("primary, secondary, tertiary", colour.declaration.allowed());
        assertNotNull(registry.declaration("look.background.blur"));
    }

    @Test
    public void aRegistryWithTheAdapterStillAnswersItsOwnIds() {
        SettingsRegistry both = new SettingsRegistry(which -> new com.termux.shared.termux.settings
            .registry.InMemorySettingStore(), SettingDeclarations.ALL, Collections.singletonList(
            new LookSettingsAdapter(grid, background, () -> { })));
        SettingDeclaration own = both.declaration("text.size");
        assertNotNull(own);
        assertNotNull(both.get("text.size"));
    }
}
