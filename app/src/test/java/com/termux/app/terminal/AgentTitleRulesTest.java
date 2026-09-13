package com.termux.app.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The OSC titles every agent with title rules is known to set, one case per rule. */
public class AgentTitleRulesTest {

    private static AgentStatus.State classify(String agent, String title) {
        return AgentTitleRules.classify(agent, title);
    }

    @Test
    public void claude_spinsWhileWorkingAndSparklesWhenResting() {
        // Braille spinner: Claude Code up to 2.1.227.
        assertEquals(AgentStatus.State.WORKING, classify(AgentStatus.AGENT_CLAUDE, "⠹ Pondering…"));
        assertEquals(AgentStatus.State.WORKING, classify(AgentStatus.AGENT_CLAUDE, "⣾ Reticulating"));
        // Half-circles: the 2.1.228 busy spinner.
        assertEquals(AgentStatus.State.WORKING, classify(AgentStatus.AGENT_CLAUDE, "◐ Thinking"));
        assertEquals(AgentStatus.State.WORKING, classify(AgentStatus.AGENT_CLAUDE, "◓ Thinking"));
        assertEquals(AgentStatus.State.IDLE, classify(AgentStatus.AGENT_CLAUDE, "✳ ~/projects/tl"));
        // The glyph has to lead and be followed by a space, or it is somebody else's title.
        assertNull(classify(AgentStatus.AGENT_CLAUDE, "~/projects/tl — claude"));
        assertNull(classify(AgentStatus.AGENT_CLAUDE, "✳~/projects/tl"));
    }

    @Test
    public void codex_callsAnyTitleItHasNotExplainedIdle() {
        assertEquals(AgentStatus.State.BLOCKED,
            classify(AgentStatus.AGENT_CODEX, "⚠ Action Required — codex"));
        assertEquals(AgentStatus.State.BLOCKED,
            classify(AgentStatus.AGENT_CODEX, "codex: action required"));
        // The spinner stands as its own word, at the start or after a space.
        assertEquals(AgentStatus.State.WORKING, classify(AgentStatus.AGENT_CODEX, "⠋ ~/projects/tl"));
        assertEquals(AgentStatus.State.WORKING, classify(AgentStatus.AGENT_CODEX, "codex ⠴ working"));
        assertEquals(AgentStatus.State.WORKING, classify(AgentStatus.AGENT_CODEX, "codex ⠏"));
        // Anything else it set is a resting title.
        assertEquals(AgentStatus.State.IDLE, classify(AgentStatus.AGENT_CODEX, "codex — ~/projects/tl"));
        assertNull(classify(AgentStatus.AGENT_CODEX, "   "));
    }

    @Test
    public void amp_namesItsWorkspaceBetweenDashesWhenResting() {
        assertEquals(AgentStatus.State.BLOCKED,
            classify(AgentStatus.AGENT_AMP, "Plugin confirmation needed"));
        assertEquals(AgentStatus.State.WORKING, classify(AgentStatus.AGENT_AMP, "⠙ editing Foo.java"));
        assertEquals(AgentStatus.State.IDLE, classify(AgentStatus.AGENT_AMP, "tl - amp - main"));
        assertNull(classify(AgentStatus.AGENT_AMP, "amp"));
    }

    @Test
    public void qwen_leadsWithAStatusGlyphWithOrWithoutAVariationSelector() {
        assertEquals(AgentStatus.State.BLOCKED, classify(AgentStatus.AGENT_QWEN, "✳ Waiting for you"));
        assertEquals(AgentStatus.State.BLOCKED, classify(AgentStatus.AGENT_QWEN, "✳︎ Waiting for you"));
        assertEquals(AgentStatus.State.WORKING, classify(AgentStatus.AGENT_QWEN, "◐ Running tool"));
        assertEquals(AgentStatus.State.WORKING, classify(AgentStatus.AGENT_QWEN, "◐︎ Running tool"));
        assertNull(classify(AgentStatus.AGENT_QWEN, "qwen — ~/projects/tl"));
    }

    @Test
    public void grok_namesItselfWhenRestingAndWorksOnAnythingElse() {
        assertEquals(AgentStatus.State.BLOCKED,
            classify(AgentStatus.AGENT_GROK, "⚠ Action Required"));
        assertEquals(AgentStatus.State.IDLE, classify(AgentStatus.AGENT_GROK, "grok"));
        assertEquals(AgentStatus.State.IDLE, classify(AgentStatus.AGENT_GROK, "tl build - grok"));
        assertEquals(AgentStatus.State.WORKING, classify(AgentStatus.AGENT_GROK, "Waiting on subagent…"));
        // A spinner keeps a working title out of the idle rule even when it ends in the name.
        assertEquals(AgentStatus.State.WORKING, classify(AgentStatus.AGENT_GROK, "⠧ Explore - grok"));
    }

    @Test
    public void hermes_usesOneGlyphPerStateAndToleratesTheVariationSelector() {
        assertEquals(AgentStatus.State.BLOCKED, classify(AgentStatus.AGENT_HERMES, "⚠ Approve rm?"));
        assertEquals(AgentStatus.State.BLOCKED, classify(AgentStatus.AGENT_HERMES, "⚠︎ Approve rm?"));
        assertEquals(AgentStatus.State.BLOCKED, classify(AgentStatus.AGENT_HERMES, "⚠️"));
        assertEquals(AgentStatus.State.WORKING, classify(AgentStatus.AGENT_HERMES, "⏳ Running tests"));
        assertEquals(AgentStatus.State.WORKING, classify(AgentStatus.AGENT_HERMES, "⏳️ Running tests"));
        assertEquals(AgentStatus.State.IDLE, classify(AgentStatus.AGENT_HERMES, "✓ Done"));
        assertEquals(AgentStatus.State.IDLE, classify(AgentStatus.AGENT_HERMES, "✓︎"));
        assertNull(classify(AgentStatus.AGENT_HERMES, "hermes"));
    }

    @Test
    public void aMissingTitleOrAnAgentThatSetsNoneClassifyToNothing() {
        assertNull(classify(AgentStatus.AGENT_CLAUDE, null));
        assertNull(classify(AgentStatus.AGENT_CLAUDE, ""));
        assertNull(classify(AgentStatus.AGENT_CLAUDE, "   "));
        assertNull(classify(null, "⠹ Pondering…"));
        // pi and opencode are read from the screen; they set no title of their own.
        assertNull(classify(AgentStatus.AGENT_PI, "⠹ Working..."));
        assertNull(classify(AgentStatus.AGENT_OPENCODE, "△ Permission required"));
        assertNull(classify("aider", "⠹ anything"));
    }

    @Test
    public void hasTitleRules_namesExactlyTheAgentsInTheTable() {
        assertTrue(AgentTitleRules.hasTitleRules(AgentStatus.AGENT_CLAUDE));
        assertTrue(AgentTitleRules.hasTitleRules(AgentStatus.AGENT_CODEX));
        assertTrue(AgentTitleRules.hasTitleRules(AgentStatus.AGENT_AMP));
        assertTrue(AgentTitleRules.hasTitleRules(AgentStatus.AGENT_QWEN));
        assertTrue(AgentTitleRules.hasTitleRules(AgentStatus.AGENT_GROK));
        assertTrue(AgentTitleRules.hasTitleRules(AgentStatus.AGENT_HERMES));
        assertFalse(AgentTitleRules.hasTitleRules(AgentStatus.AGENT_PI));
        assertFalse(AgentTitleRules.hasTitleRules(AgentStatus.AGENT_OPENCODE));
        assertFalse(AgentTitleRules.hasTitleRules(null));
    }

    @Test
    public void everyRuleBelongsToAKnownAgentAndABlockedRuleComesFirstForIt() {
        for (AgentTitleRules.Rule rule : AgentTitleRules.rules()) {
            assertTrue(rule.agent, AgentStatus.kindFor(rule.agent, null) != null);
        }
        // Within an agent, a blocked rule may never sit behind a working or idle one.
        String agent = null;
        boolean sawSofter = false;
        for (AgentTitleRules.Rule rule : AgentTitleRules.rules()) {
            if (!rule.agent.equals(agent)) {
                agent = rule.agent;
                sawSofter = false;
            }
            if (rule.state == AgentStatus.State.BLOCKED) assertFalse(agent, sawSofter);
            else sawSofter = true;
        }
    }
}
