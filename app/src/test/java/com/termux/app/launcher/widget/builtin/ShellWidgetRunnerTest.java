package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ShellWidgetRunnerTest {
    @Test public void cleanDropsEscapesCarriageReturnsAndTrailingBlankLines() {
        assertEquals("ok\nred", ShellWidgetRunner.clean("ok\r\n\u001B[31mred\u001B[0m\n\n"));
        assertEquals("100%", ShellWidgetRunner.clean(" 10%\r 50%\r100%"));
        assertEquals("title", ShellWidgetRunner.clean("\u001B]0;window\u0007title"));
    }

    @Test public void decodeMarksTruncationAndDropsASplitCharacter() {
        byte[] whole = "abc".getBytes(StandardCharsets.UTF_8);
        assertEquals("abc", ShellWidgetRunner.decode(whole, false));
        byte[] euro = "ab\u20ac".getBytes(StandardCharsets.UTF_8);
        byte[] cut = Arrays.copyOf(euro, euro.length - 1);
        assertEquals("ab\u2026", ShellWidgetRunner.decode(cut, true));
    }

    @Test public void firstLineSkipsBlankLines() {
        assertEquals("up 3 days", ShellWidgetRunner.firstLine("\n   \n  up 3 days  \nload"));
        assertEquals("", ShellWidgetRunner.firstLine(""));
    }

    @Test public void keysDifferPerCommand() {
        assertEquals(ShellWidgetRunner.key("uptime"), ShellWidgetRunner.key("uptime"));
        assertNotEquals(ShellWidgetRunner.key("uptime"), ShellWidgetRunner.key("df -h"));
    }
}
