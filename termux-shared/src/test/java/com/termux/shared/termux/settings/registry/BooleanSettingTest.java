package com.termux.shared.termux.settings.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** On/off settings, and the complaint phrases and Assistant flag every declaration carries. */
public class BooleanSettingTest {

    private static final SettingDeclaration LAMP = SettingDeclaration.bool("lamp.lit",
        SettingDeclaration.Store.APP_PREFERENCES, "lamp_lit", true, SettingRefresh.RESTYLE);

    private final InMemorySettingStore store = new InMemorySettingStore();
    private final SettingsRegistry registry = new SettingsRegistry(which -> store,
        Collections.singletonList(LAMP));

    @Test
    public void aBooleanReadsItsDefaultThenWhatTheStoreHolds() {
        assertEquals("true", registry.get("lamp.lit"));
        store.putBoolean("lamp_lit", false);
        assertEquals("false", registry.get("lamp.lit"));
        assertEquals("true", LAMP.defaultValue());
        assertEquals("false, true", LAMP.allowed());
    }

    @Test
    public void aBooleanSavesAsABooleanAndTakesAnyCase() {
        SettingsRegistry.Change change = registry.set("lamp.lit", " False ").change();
        assertNotNull(change);
        assertEquals("true", change.oldValue);
        assertEquals("false", change.newValue);
        assertFalse(store.getBoolean("lamp_lit", true));
    }

    @Test
    public void aBooleanRefusesAnythingElseAndWritesNothing() {
        SettingsRegistry.Refusal refusal = registry.set("lamp.lit", "dim").refusal();
        assertNotNull(refusal);
        assertEquals("false, true", refusal.allowed);
        assertTrue(store.getBoolean("lamp_lit", true));
        assertNotNull(registry.check("lamp.lit", "2"));
        assertNull(registry.check("lamp.lit", "true"));
    }

    @Test
    public void aBooleanHasNoStep() {
        assertNotNull(registry.step("lamp.lit", SettingsRegistry.Direction.UP).refusal());
    }

    @Test
    public void aStoreHoldingAnotherTypeReadsAsTheDefault() {
        store.putInt("lamp_lit", 3);
        assertEquals("true", registry.get("lamp.lit"));
    }

    @Test
    public void aBackupValueMustBeABoolean() {
        assertTrue(LAMP.acceptsStored(Boolean.FALSE));
        assertFalse(LAMP.acceptsStored("true"));
        assertFalse(LAMP.acceptsStored(1));
    }

    @Test
    public void complaintPhrasesAndTheAssistantFlagTravelWithTheDeclaration() {
        assertTrue(LAMP.phrases.isEmpty());
        assertTrue(LAMP.mayOffer);
        SettingDeclaration said = LAMP.saidAs("too bright", "hurts my eyes");
        assertEquals(Arrays.asList("too bright", "hurts my eyes"), said.phrases);
        assertTrue(said.mayOffer);
        SettingDeclaration kept = said.notOffered().notExported();
        assertFalse(kept.mayOffer);
        assertFalse(kept.exported);
        assertEquals(said.phrases, kept.phrases);
        assertEquals("lamp.lit", kept.id);
    }
}
