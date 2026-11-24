package com.termux.shared.termux.settings.properties;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Theme's night-mode property is clamped by its Setting declaration: an unknown value is System. */
public class NightModeClampTest {

    @Test
    public void aKnownValueIsKeptInAnyCase() {
        assertEquals("true", TermuxSharedProperties.getNightModeInternalPropertyValueFromValue("true"));
        assertEquals("false", TermuxSharedProperties.getNightModeInternalPropertyValueFromValue("FALSE"));
        assertEquals("system", TermuxSharedProperties.getNightModeInternalPropertyValueFromValue("system"));
    }

    @Test
    public void anUnknownOrMissingValueIsTheDefault() {
        assertEquals("system", TermuxSharedProperties.getNightModeInternalPropertyValueFromValue("dark"));
        assertEquals("system", TermuxSharedProperties.getNightModeInternalPropertyValueFromValue(""));
        assertEquals("system", TermuxSharedProperties.getNightModeInternalPropertyValueFromValue(null));
    }
}
