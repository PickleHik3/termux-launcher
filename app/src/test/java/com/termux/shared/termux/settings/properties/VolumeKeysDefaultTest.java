package com.termux.shared.termux.settings.properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The volume rocker belongs to the system unless the user asks otherwise.
 *
 * <p>This app can be the home launcher, so a default that turns the volume keys into virtual
 * Ctrl/Fn keys — as upstream Termux does — leaves a user with no way to change the volume from
 * their own home screen. The default is pinned here because it is one line in a constants file
 * that an upstream merge can quietly take back.
 */
public class VolumeKeysDefaultTest {

    @Test
    public void volumeKeysAreLiteralByDefault() {
        assertEquals("volume", TermuxPropertyConstants.DEFAULT_IVALUE_VOLUME_KEYS_BEHAVIOUR);
        assertEquals(TermuxPropertyConstants.IVALUE_VOLUME_KEY_BEHAVIOUR_VOLUME,
            TermuxPropertyConstants.DEFAULT_IVALUE_VOLUME_KEYS_BEHAVIOUR);
    }

    /** The virtual behaviour stays reachable: the default is a default, not a removal. */
    @Test
    public void virtualBehaviourRemainsSelectable() {
        assertTrue(TermuxPropertyConstants.MAP_VOLUME_KEYS_BEHAVIOUR
            .containsKey(TermuxPropertyConstants.IVALUE_VOLUME_KEY_BEHAVIOUR_VIRTUAL));
        assertTrue(TermuxPropertyConstants.MAP_VOLUME_KEYS_BEHAVIOUR
            .containsKey(TermuxPropertyConstants.IVALUE_VOLUME_KEY_BEHAVIOUR_VOLUME));
    }
}
