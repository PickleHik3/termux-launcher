package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The table of agents that resume by session id (#367). */
public class AgentResumeTest {

    @Test
    public void eachRowBuildsItsResumeCommand() {
        assertEquals(Arrays.asList("claude", "--resume", "abc-1"),
            AgentResume.forAgent("claude").resume("abc-1"));
        assertEquals(Arrays.asList("codex", "resume", "abc-1"),
            AgentResume.forAgent("codex").resume("abc-1"));
        assertEquals(Arrays.asList("opencode", "--session", "ses_1"),
            AgentResume.forAgent("opencode").resume("ses_1"));
    }

    @Test
    public void aCapturedCommandMatchesByTheUnwrappedProgramName() {
        assertEquals("claude", AgentResume.forCommand(Arrays.asList("claude", "--model", "x")).agent);
        assertEquals("codex",
            AgentResume.forCommand(Arrays.asList("/data/usr/bin/codex")).agent);
        // A script run through an interpreter is named by the script, as the window chips do.
        assertEquals("opencode", AgentResume.forCommand(
            Arrays.asList("node", "/usr/lib/node_modules/opencode/bin/opencode.js")).agent);
        assertNull(AgentResume.forCommand(Arrays.asList("nvim", "claude")));
        assertNull(AgentResume.forCommand(Collections.<String>emptyList()));
    }

    @Test
    public void onlyPlainIdsAreAccepted() {
        assertTrue(AgentResume.isValidSessionId("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0"));
        assertTrue(AgentResume.isValidSessionId("ses_2a9F"));
        assertFalse(AgentResume.isValidSessionId(""));
        assertFalse(AgentResume.isValidSessionId(null));
        assertFalse(AgentResume.isValidSessionId("a b"));
        assertFalse(AgentResume.isValidSessionId("a';rm -rf /"));
        assertFalse(AgentResume.isValidSessionId("-p"));
    }

    @Test
    public void anIdIsSavedOnlyWhileTheSavedProgramIsThatAgent() {
        List<String> claude = Arrays.asList("claude");
        assertEquals("id1", AgentResume.sessionToSave(claude, "claude", "id1"));
        assertNull(AgentResume.sessionToSave(claude, "codex", "id1"));
        assertNull(AgentResume.sessionToSave(Arrays.asList("nvim"), "claude", "id1"));
        assertNull(AgentResume.sessionToSave(claude, "claude", null));
        assertNull(AgentResume.sessionToSave(claude, "claude", "bad id"));
    }

    @Test
    public void setupIsOfferedOnlyForRunningAgentsWithoutHooks() {
        List<List<String>> panes = Arrays.asList(
            Arrays.asList("claude"), Arrays.asList("claude", "-c"),
            Arrays.asList("codex"), Arrays.asList("nvim"), Collections.<String>emptyList());
        assertEquals(Arrays.asList("claude", "codex"),
            AgentResume.agentsNeedingSetup(panes, agent -> false));
        assertEquals(Arrays.asList("codex"),
            AgentResume.agentsNeedingSetup(panes, agent -> agent.equals("claude")));
        assertTrue(AgentResume.agentsNeedingSetup(panes, agent -> true).isEmpty());
        assertTrue(AgentResume.agentsNeedingSetup(
            Arrays.asList(Arrays.asList("nvim")), agent -> false).isEmpty());
    }
}
