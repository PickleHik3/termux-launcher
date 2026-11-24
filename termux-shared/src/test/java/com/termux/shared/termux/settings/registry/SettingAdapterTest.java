package com.termux.shared.termux.settings.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

/** The registry routes an id its own declarations do not name to the adapter that does. */
public class SettingAdapterTest {

    private static final SettingDeclaration FAKE = SettingDeclaration.integer("fake.size",
        SettingDeclaration.Store.APP_PREFERENCES, "fake.size", 0, 10, 1, 5, SettingRefresh.LISTENER);

    private static final SettingDeclaration FOLLOWER = SettingDeclaration.integer("fake.follower",
        SettingDeclaration.Store.APP_PREFERENCES, "fake.follower", 0, 10, 1, 5,
        SettingRefresh.LISTENER);

    private int held = 5;

    private final SettingAdapter adapter = new SettingAdapter() {
        @Nullable @Override public SettingDeclaration declaration(@NonNull String id) {
            return id.equals(FAKE.id) ? FAKE : id.equals(FOLLOWER.id) ? FOLLOWER : null;
        }

        @NonNull @Override public List<SettingDeclaration> declarations() {
            return java.util.Arrays.asList(FAKE, FOLLOWER);
        }

        @Override public boolean overrides(@NonNull SettingDeclaration declaration) {
            return declaration != FOLLOWER;
        }

        @NonNull @Override public String get(@NonNull SettingDeclaration declaration) {
            return String.valueOf(held);
        }

        @Nullable @Override
        public SettingsRegistry.Refusal check(@NonNull SettingDeclaration declaration,
                                              @NonNull String value) {
            return value.equals("99") ? new SettingsRegistry.Refusal("fake.size is 0–10", "0–10")
                : null;
        }

        @NonNull @Override
        public SettingsRegistry.Outcome set(@NonNull SettingDeclaration declaration,
                                            @NonNull String value) {
            if (value.equals("99")) return new SettingsRegistry.Refusal("fake.size is 0–10", "0–10");
            String old = String.valueOf(held);
            held = Integer.parseInt(value);
            return new SettingsRegistry.Change(declaration, old, value);
        }
    };

    private final SettingsRegistry registry = new SettingsRegistry(
        which -> new InMemorySettingStore(), SettingDeclarations.ALL,
        Collections.singletonList(adapter));

    @Test
    public void anAdaptersIdIsKnownReadAndSaved() {
        assertNotNull(registry.declaration("fake.size"));
        assertEquals("5", registry.get("fake.size"));
        SettingsRegistry.Change change = registry.set("fake.size", "7").change();
        assertEquals("5", change.oldValue);
        assertEquals("7", registry.get("fake.size"));
    }

    @Test
    public void anAdaptersRefusalComesBackAsIs() {
        SettingsRegistry.Refusal refusal = registry.set("fake.size", "99").refusal();
        assertEquals("fake.size is 0–10", refusal.reason);
        assertEquals("5", registry.get("fake.size"));
    }

    @Test
    public void checkAsksTheAdapterAndSavesNothing() {
        assertNull(registry.check("fake.size", "7"));
        assertEquals("fake.size is 0–10", registry.check("fake.size", "99").reason);
        assertEquals("5", registry.get("fake.size"));
    }

    @Test
    public void listHoldsTheAdaptersIdsAfterTheDeclared() {
        List<SettingsRegistry.Entry> entries = registry.list();
        assertEquals("fake.size", entries.get(entries.size() - 2).declaration.id);
        assertEquals("5", entries.get(entries.size() - 2).value);
        assertEquals(SettingDeclarations.ALL.size() + 2, entries.size());
    }

    @Test
    public void aFileCarriesAnAdaptersIdOnlyWhereItHoldsItsOwnValue() {
        String text = SettingsFile.export(registry);
        assertTrue(text, text.contains("\nfake.size = 5"));
        assertTrue(text, !text.contains("fake.follower"));
        assertTrue(SettingsFile.check("fake.size = 7\nfake.follower = 3", registry)
            .errors.isEmpty());
    }

    @Test
    public void anUnknownIdStaysUnknownAndAnAdaptersIdHasNoStep() {
        assertNull(registry.get("fake.other"));
        assertTrue(registry.set("fake.other", "1").refusal() != null);
        assertTrue(registry.step("fake.size", SettingsRegistry.Direction.UP).refusal() != null);
    }
}
