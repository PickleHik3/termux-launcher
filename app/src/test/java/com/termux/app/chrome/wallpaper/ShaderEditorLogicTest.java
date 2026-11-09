package com.termux.app.chrome.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.chrome.scene.ShaderErrors;
import com.termux.app.chrome.scene.ShaderSafety;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ShaderEditorLogicTest {

    private static ShaderErrors.Error error(int line, String message) {
        return new ShaderErrors.Error(line, message);
    }

    @Test
    public void errorText_isEmptyWithNoErrors() {
        assertEquals("", ShaderEditorLogic.errorText(null));
        assertEquals("", ShaderEditorLogic.errorText(Collections.emptyList()));
    }

    @Test
    public void errorText_namesTheLineAndTheMessage() {
        assertEquals("Line 12: 'x' : undeclared identifier",
            ShaderEditorLogic.errorText(Collections.singletonList(error(12, "'x' : undeclared identifier"))));
    }

    @Test
    public void errorText_aMessageWithoutALineIsShownAlone() {
        assertEquals("Out of graphics memory.",
            ShaderEditorLogic.errorText(Collections.singletonList(error(-1, "Out of graphics memory."))));
    }

    @Test
    public void errorText_showsAtMostThreeErrors_oneAline() {
        List<ShaderErrors.Error> five = new ArrayList<>(Arrays.asList(
            error(1, "a"), error(2, "b"), error(3, "c"), error(4, "d"), error(5, "e")));
        assertEquals("Line 1: a\nLine 2: b\nLine 3: c", ShaderEditorLogic.errorText(five));
    }

    @Test
    public void apply_errorsStay_aPassSavesAndApplies() {
        assertEquals(ShaderEditorLogic.Decision.STAY,
            ShaderEditorLogic.applyDecision(Collections.singletonList(error(3, "bad"))));
        assertEquals(ShaderEditorLogic.Decision.STAY, ShaderEditorLogic.applyDecision(null));
        assertEquals(ShaderEditorLogic.Decision.SAVE_AND_APPLY,
            ShaderEditorLogic.applyDecision(Collections.emptyList()));
    }

    @Test
    public void nameOrDefault_trimsCutsAndFallsBack() {
        assertEquals("Plasma", ShaderEditorLogic.nameOrDefault("  Plasma ", "Shader"));
        assertEquals("Shader", ShaderEditorLogic.nameOrDefault("   ", "Shader"));
        assertEquals("Shader", ShaderEditorLogic.nameOrDefault(null, "Shader"));
        StringBuilder long80 = new StringBuilder();
        for (int i = 0; i < 80; i++) long80.append('a');
        assertEquals(ShaderEditorLogic.MAX_NAME, ShaderEditorLogic.nameOrDefault(long80.toString(), "Shader").length());
    }

    @Test
    public void tooHeavyLine_showsOnlyForTheHeavyReason() {
        assertTrue(ShaderEditorLogic.showsTooHeavy(ShaderSafety.REASON_TOO_HEAVY));
        assertFalse(ShaderEditorLogic.showsTooHeavy(null));
        assertFalse(ShaderEditorLogic.showsTooHeavy("battery_saver"));
    }
}
