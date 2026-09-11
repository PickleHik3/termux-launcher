package com.termux.app.terminal;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Fixture screens for every agent the rules know, plus the "not an agent" case. */
public class AgentScreenRulesTest {

    private static final String CLAUDE_BLOCKED =
        "● Bash(rm -rf build)\n"
            + "  ⎿  Running…\n"
            + "\n"
            + "Do you want to proceed?\n"
            + "❯ 1. Yes\n"
            + "  2. Yes, and don't ask again\n"
            + "  3. No, and tell Claude what to do differently (esc)\n";

    private static final String CLAUDE_WORKING =
        "● Reading src/main/java/Foo.java\n"
            + "\n"
            + "✳ Pondering… (12s · ↑ 1.2k tokens · esc to interrupt)\n";

    private static final String CLAUDE_IDLE =
        "● Done. The build is green.\n"
            + "\n"
            + "╭──────────────────────────────────────────╮\n"
            + "│ >                                        │\n"
            + "╰──────────────────────────────────────────╯\n"
            + "  ? for shortcuts                    bypass permissions on\n";

    private static final String CODEX_BLOCKED =
        "codex wants to run a command\n"
            + "\n"
            + "  $ rm -rf build\n"
            + "\n"
            + "Allow command?\n"
            + "❯ 1. Yes, proceed\n"
            + "  2. No\n";

    private static final String CODEX_WORKING =
        "• Working (18s • esc to interrupt)\n"
            + "  editing app/src/main/java/Foo.java\n";

    private static final String CODEX_IDLE =
        "• Finished in 42s\n"
            + "\n"
            + "  Ask Codex to do anything\n";

    @Test
    public void claude_screensClassifyToEachState() {
        assertEquals(AgentStatus.State.BLOCKED, AgentScreenRules.classify(
            AgentStatus.AGENT_CLAUDE, CLAUDE_BLOCKED, false));
        assertEquals(AgentStatus.State.WORKING, AgentScreenRules.classify(
            AgentStatus.AGENT_CLAUDE, CLAUDE_WORKING, true));
        assertEquals(AgentStatus.State.IDLE, AgentScreenRules.classify(
            AgentStatus.AGENT_CLAUDE, CLAUDE_IDLE, false));
    }

    @Test
    public void codex_screensClassifyToEachState() {
        assertEquals(AgentStatus.State.BLOCKED, AgentScreenRules.classify(
            AgentStatus.AGENT_CODEX, CODEX_BLOCKED, false));
        assertEquals(AgentStatus.State.WORKING, AgentScreenRules.classify(
            AgentStatus.AGENT_CODEX, CODEX_WORKING, true));
        assertEquals(AgentStatus.State.IDLE, AgentScreenRules.classify(
            AgentStatus.AGENT_CODEX, CODEX_IDLE, false));
    }

    @Test
    public void blockedOutranksAStaleWorkingFooterOnTheSameScreen() {
        String both = CLAUDE_WORKING + CLAUDE_BLOCKED;
        assertEquals(AgentStatus.State.BLOCKED, AgentScreenRules.classify(
            AgentStatus.AGENT_CLAUDE, both, true));
    }

    @Test
    public void noAgentAndNoMatchClassifyToNothing() {
        assertNull(AgentScreenRules.classify(null, CLAUDE_BLOCKED, true));
        assertNull(AgentScreenRules.classify(AgentStatus.AGENT_CLAUDE,
            "$ ls\nbuild  src  README.md\n$ ", false));
        assertNull(AgentScreenRules.classify(AgentStatus.AGENT_CLAUDE, "", true));
    }

    @Test
    public void anAgentWithoutRulesOnlyEverWorksOrIdles() {
        assertEquals(AgentStatus.State.WORKING,
            AgentScreenRules.classify("aider", CLAUDE_BLOCKED, true));
        assertEquals(AgentStatus.State.IDLE,
            AgentScreenRules.classify("aider", CLAUDE_BLOCKED, false));
    }

    @Test
    public void tail_keepsOnlyTheLastNonBlankRows() {
        StringBuilder screen = new StringBuilder();
        for (int i = 0; i < 40; i++) screen.append("line ").append(i).append("\n\n");
        String tail = AgentScreenRules.tail(screen.toString(), 3);
        assertEquals("line 37\nline 38\nline 39\n", tail);
        assertEquals("", AgentScreenRules.tail(null, 4));
    }

    @Test
    public void kindFor_namesAgentsIncludingNodeLaunchedOnes() {
        assertEquals(AgentStatus.AGENT_CLAUDE,
            AgentStatus.kindFor("claude", Collections.singletonList("/usr/bin/claude")));
        assertEquals(AgentStatus.AGENT_CODEX,
            AgentStatus.kindFor("codex", Collections.singletonList("codex")));
        assertEquals(AgentStatus.AGENT_CLAUDE, AgentStatus.kindFor("node",
            Arrays.asList("node", "--no-warnings", "/data/data/com.termux/files/usr/bin/claude",
                "--model", "opus")));
        assertEquals("cursor-agent", AgentStatus.kindFor("cursor-agent",
            Collections.singletonList("cursor-agent")));
        assertNull(AgentStatus.kindFor("bash", Collections.singletonList("bash")));
        assertNull(AgentStatus.kindFor("node",
            Arrays.asList("node", "server.js", "--name", "claude")));
        assertTrue(AgentStatus.hasScreenRules(AgentStatus.AGENT_CLAUDE));
        assertTrue(!AgentStatus.hasScreenRules("aider"));
    }

    @Test
    public void rollUp_ordersBlockedOverWorkingOverIdleOverNothing() {
        assertNull(AgentStatus.rollUp((AgentStatus.State) null, null));
        assertEquals(AgentStatus.State.IDLE,
            AgentStatus.rollUp(AgentStatus.State.IDLE, null));
        assertEquals(AgentStatus.State.WORKING,
            AgentStatus.rollUp(AgentStatus.State.IDLE, AgentStatus.State.WORKING));
        assertEquals(AgentStatus.State.BLOCKED,
            AgentStatus.rollUp(AgentStatus.State.WORKING, AgentStatus.State.BLOCKED));
        assertEquals(AgentStatus.State.BLOCKED,
            AgentStatus.rollUp(AgentStatus.State.BLOCKED, AgentStatus.State.WORKING));
        assertEquals(AgentStatus.State.BLOCKED, AgentStatus.rollUp(Arrays.asList(
            new AgentStatus("claude", AgentStatus.State.IDLE, AgentStatus.Source.SCREEN, 0L),
            new AgentStatus("codex", AgentStatus.State.BLOCKED, AgentStatus.Source.HOOK, 0L),
            new AgentStatus("claude", AgentStatus.State.WORKING, AgentStatus.Source.SCREEN, 0L))));
        assertNull(AgentStatus.rollUp(Collections.<AgentStatus>emptyList()));
    }
}
