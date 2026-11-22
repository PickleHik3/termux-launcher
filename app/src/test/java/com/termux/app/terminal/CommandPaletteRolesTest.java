package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import com.termux.app.theme.LauncherThemeTokens;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class CommandPaletteRolesTest {

    @Test public void theSchemesTokenWinsWhileItOwnsTheChrome() {
        Map<String, Integer> tokens = new HashMap<>();
        tokens.put(LauncherThemeTokens.SECONDARY_CONTAINER, 0xFF334455);
        assertEquals(0xFF334455,
            CommandPaletteRoles.pick(tokens, LauncherThemeTokens.SECONDARY_CONTAINER, 0xFF000000));
    }

    @Test public void theThemesRoleOtherwise() {
        assertEquals(0xFF112233,
            CommandPaletteRoles.pick(null, LauncherThemeTokens.PRIMARY, 0xFF112233));
        assertEquals(0xFF112233, CommandPaletteRoles.pick(new HashMap<>(),
            LauncherThemeTokens.PRIMARY, 0xFF112233));
    }
}
