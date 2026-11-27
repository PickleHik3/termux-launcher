package com.termux.app.leader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.termux.app.leader.Leader;
import com.termux.shared.termux.settings.registry.InMemorySettingStore;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.junit.Test;

import java.util.Collections;

/** The leader as a registry setting: every door sets it and is refused the same way (ADR 0042). */
public class LeaderChordSettingAdapterTest {

    private final InMemorySettingStore store = new InMemorySettingStore();
    private final SettingsRegistry registry = new SettingsRegistry(which -> store,
        SettingDeclarations.ALL, Collections.singletonList(new LeaderChordSettingAdapter(store)));

    @Test
    public void theTerlKeyIsTheDefaultLeader() {
        assertEquals("terl", registry.get("keys.leader"));
        assertEquals("", registry.get(LeaderChordSettingAdapter.ID));
        assertSame(Leader.TERL_KEY, LeaderChordSettingAdapter.leaderFrom(store));
    }

    @Test
    public void aCustomLeaderIsTheChoiceAndItsKeys() {
        assertNotNull(registry.set("keys.leader", "custom").change());
        assertNotNull(registry.set(LeaderChordSettingAdapter.ID, "Ctrl+B").change());
        assertEquals("ctrl+b", registry.get(LeaderChordSettingAdapter.ID));
        Leader leader = LeaderChordSettingAdapter.leaderFrom(store);
        assertEquals(Leader.Kind.CUSTOM, leader.kind);
        assertEquals("ctrl+b", leader.chord.spec());

        assertNotNull(registry.set("keys.leader", "ctrl+alt").change());
        assertSame("the chord waits for Custom", Leader.CTRL_ALT,
            LeaderChordSettingAdapter.leaderFrom(store));
    }

    @Test
    public void androidsChordsAreRefusedWithTheReasonAndNothingIsSaved() {
        registry.set(LeaderChordSettingAdapter.ID, "ctrl+b");
        SettingsRegistry.Refusal refusal =
            registry.set(LeaderChordSettingAdapter.ID, "alt+tab").refusal();
        assertNotNull(refusal);
        assertEquals("Android uses Alt+Tab to switch apps.", refusal.reason);
        assertNotNull(registry.check(LeaderChordSettingAdapter.ID, "super+q"));
        assertEquals("ctrl+b", registry.get(LeaderChordSettingAdapter.ID));
        assertNotNull(registry.set("keys.leader", "hyper").refusal());
    }

    @Test
    public void anEmptyChordClearsIt() {
        registry.set(LeaderChordSettingAdapter.ID, "ctrl+b");
        assertNull(registry.check(LeaderChordSettingAdapter.ID, ""));
        assertNotNull(registry.set(LeaderChordSettingAdapter.ID, "").change());
        assertEquals("", registry.get(LeaderChordSettingAdapter.ID));
    }
}
