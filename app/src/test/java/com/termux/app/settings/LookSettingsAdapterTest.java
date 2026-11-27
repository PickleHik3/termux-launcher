package com.termux.app.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.registry.SettingAdapter;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingsFile;
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
    public void aValueOutsideTheRangeIsRefusedWithTheRangeAndSavesNothing() {
        SettingsRegistry.Refusal refusal = registry.set("look.dock.opacity", "400").refusal();
        assertEquals("look.dock.opacity is 0–100", refusal.reason);
        assertEquals("0–100", refusal.allowed);
        assertEquals("look.dock.opacity is 0–100", registry.check("look.dock.opacity", "-1").reason);
        assertEquals("look.background.dim is 0–100",
            registry.set("look.background.dim", "101").refusal().reason);
        assertEquals("look.opacity is 0–100", registry.set("look.opacity", "101").refusal().reason);
        assertFalse(grid.has(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));
        assertEquals(0, changed);
    }

    @Test
    public void theVerbItselfStillClampsTheSameValue() throws Exception {
        org.json.JSONObject answer = LookCommand.handle(grid, background,
            new org.json.JSONObject().put("op", "set").put("property", "opacity")
                .put("element", "dock").put("value", "400"));
        assertEquals("100", answer.getString("value"));
    }

    @Test
    public void anAppliedFileWithAnOutOfRangeLineIsRefusedWithItsLineNumber() {
        SettingsFile.Result result = SettingsFile.check(
            "look.opacity = 30\n\nlook.dock.opacity = 400\n", registry);
        assertEquals(java.util.Collections.singletonList("line 3: look.dock.opacity is 0–100"),
            result.errors);
        assertEquals(0, changed);
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
    public void checkRefusesAsSetDoesAndSavesNothing() {
        assertEquals("opacity takes a whole number",
            registry.check("look.dock.opacity", "loud").reason);
        assertTrue(registry.check("look.dock.grain", "10").reason.contains("Global's only"));
        assertNull(registry.check("look.dock.opacity", "60"));
        assertFalse(grid.has(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));
        assertEquals(0, changed);
    }

    @Test
    public void aSurfaceValueThatFollowsGlobalIsExportedAsFollowSoItStaysFollowingAfterAnApply() {
        registry.set("look.opacity", "30");
        registry.set("look.dock.opacity", "60");
        String file = SettingsFile.export(registry);
        assertTrue(file, file.contains("\nlook.opacity = 30"));
        assertTrue(file, file.contains("\nlook.dock.opacity = 60"));
        assertTrue(file, file.contains("\nlook.status.opacity = follow"));
        assertTrue(file, file.contains("\nlook.keyboard.blur = follow"));

        LookGrid fresh = new LookGrid(new LookGrid.MemoryStore());
        SettingsRegistry other = new SettingsRegistry(which -> {
            throw new AssertionError("a look id reads no registry store");
        }, Collections.emptyList(), Collections.singletonList(new LookSettingsAdapter(fresh,
            new LookCommandTest.MemoryBackground(), () -> { })));
        SettingsFile.Result result = SettingsFile.check(file, other);
        assertTrue(result.errors.toString(), result.errors.isEmpty());
        for (SettingsFile.Assignment each : result.assignments)
            assertNotNull(each.id, other.set(each.id, each.value).change());
        assertTrue(fresh.has(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));
        assertEquals(60, fresh.value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));
        assertFalse(fresh.has(LookGrid.Property.OPACITY, LookGrid.Element.STATUS));
        assertEquals(30, fresh.value(LookGrid.Property.OPACITY, LookGrid.Element.STATUS));
    }

    @Test
    public void everyLookIdMarkedListenerRefreshesWhenItIsSet() {
        // The refresh these ids name is the adapter's own re-dress, the one the look verb runs;
        // a set that goes through must run it exactly once.
        SettingAdapter adapter = new LookSettingsAdapter(grid, background, () -> changed++);
        int checked = 0;
        for (SettingDeclaration each : adapter.declarations()) {
            if (each.refresh != SettingRefresh.LISTENER) continue;
            int before = changed;
            SettingsRegistry.Outcome outcome = new SettingsRegistry(which -> {
                throw new AssertionError("a look id reads no registry store");
            }, Collections.emptyList(), Collections.singletonList(adapter))
                .set(each.id, adapter.get(each));
            assertNotNull(each.id + " " + outcome, outcome.change());
            assertEquals(each.id, before + 1, changed);
            checked++;
        }
        assertTrue(checked > 10);
    }

    @Test
    public void aConfigSetWritesWhatALauncherctlLookWriteDoesWhetherOrNotTheEditorIsOpen()
            throws Exception {
        // The registry holds no editor state: its look door IS the verb, so both leave the same
        // keys and re-dress once, and the editor's draft sees them as it sees the verb's write.
        LookGrid verbGrid = new LookGrid(new LookGrid.MemoryStore());
        int[] verbChanged = {0};
        LookCommand.handle(verbGrid, new LookCommandTest.MemoryBackground(),
            new org.json.JSONObject().put("op", "set").put("property", "opacity")
                .put("element", "dock").put("value", "60"), () -> verbChanged[0]++);
        registry.set("look.dock.opacity", "60");
        assertEquals(verbGrid.store().keys(), grid.store().keys());
        for (String key : grid.store().keys())
            assertEquals(key, verbGrid.store().getInt(key, -1), grid.store().getInt(key, -2));
        assertEquals(verbChanged[0], changed);
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

    @Test
    public void aSurfaceOnGlobalExportsFollowAndAFileCanPutItBack() {
        String onGlobal = SettingsFile.export(registry);
        assertTrue(onGlobal, onGlobal.contains("look.dock.opacity = follow"));
        assertFalse(onGlobal, onGlobal.contains("look.opacity = follow"));
        assertNotNull(registry.set("look.dock.opacity", "60").change());
        assertTrue(SettingsFile.export(registry).contains("look.dock.opacity = 60"));
        SettingsFile.Applied applied = SettingsFile.apply("look.dock.opacity = follow\n", registry);
        assertTrue(applied.errors.toString(), applied.ok);
        assertFalse(grid.has(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));
        assertTrue(registry.follows("look.dock.opacity"));
        // Global's own value and the Terminal's Background cannot follow
        assertEquals("look.opacity has no Global to follow",
            registry.set("look.opacity", "follow").refusal().reason);
        assertEquals("look.background.blur has no Global to follow",
            registry.set("look.background.blur", "follow").refusal().reason);
    }

    @Test
    public void aRollbackKeepsASurfaceFollowingWhenItsNumberEqualsGlobals() {
        int global = grid.value(LookGrid.Property.OPACITY, null);
        // own value equal to Global's: a number that does not move, an inheritance that does
        SettingsFile.Applied applied = SettingsFile.apply("look.dock.opacity = " + global
            + "\nlook.opacity = 400\n", registry);
        assertFalse(applied.ok);
        assertFalse(grid.has(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));
        assertTrue(registry.follows("look.dock.opacity"));
    }

    @Test
    public void defaultOnASurfaceFollowsGlobal() {
        registry.set("look.dock.opacity", "60");
        assertNotNull(registry.set("look.dock.opacity", "default").change());
        assertFalse(grid.has(LookGrid.Property.OPACITY, LookGrid.Element.DOCK));
    }
}
