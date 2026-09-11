package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The fallback reading: what the bottom of a pane says the agent in it is doing, for agents that
 * report nothing of their own. Modelled on herdr's screen manifests — a table of per-agent
 * patterns, matched in precedence order against the visible screen, first match wins.
 *
 * <p>Blocked is deliberately strict. A quiet pane is not a blocked pane: only a visible approval or
 * question prompt counts, because a wrong "Needs you" sends the user to a pane that wants nothing,
 * which is worse than a missed one. Agents with no rules of their own fall back to
 * {@link #classifyGeneric(boolean)}, which can never say blocked.
 *
 * <p>Free of Android imports, so every rule here is unit-testable.
 */
public final class AgentScreenRules {

    /** How many of the screen's last rows a rule may look at. A prompt sits at the bottom. */
    public static final int TAIL_ROWS = 12;

    /** One pattern and the state it proves. */
    public static final class Rule {
        @NonNull public final String agent;
        @NonNull public final AgentStatus.State state;
        @NonNull public final Pattern pattern;

        Rule(@NonNull String agent, @NonNull AgentStatus.State state, @NonNull String regex) {
            this.agent = agent;
            this.state = state;
            this.pattern = Pattern.compile(regex);
        }
    }

    /**
     * Ordered blocked, working, idle per agent: a Claude screen that shows both a permission prompt
     * and a stale "esc to interrupt" footer is blocked, not working.
     *
     * <p>To add an agent, append its three groups here in the same order and teach
     * {@link AgentStatus} its binary name. Nothing else knows the rules exist.
     */
    private static final List<Rule> RULES = buildRules();

    private static List<Rule> buildRules() {
        List<Rule> rules = new ArrayList<>();

        // --- Claude Code ---
        rules.add(new Rule(AgentStatus.AGENT_CLAUDE, AgentStatus.State.BLOCKED,
            "(?mi)^\\s*do you want to\\b"));
        rules.add(new Rule(AgentStatus.AGENT_CLAUDE, AgentStatus.State.BLOCKED,
            "(?mi)^\\s*[❯›▸▶]\\s*1[.)]\\s*yes\\b"));
        rules.add(new Rule(AgentStatus.AGENT_CLAUDE, AgentStatus.State.BLOCKED,
            "(?mi)^\\s*\\d+[.)]\\s*(?:yes|no)\\b.*\\R\\s*\\d+[.)]\\s*(?:yes|no)\\b"));
        rules.add(new Rule(AgentStatus.AGENT_CLAUDE, AgentStatus.State.BLOCKED,
            "(?mi)^\\s*allow\\b.*\\?\\s*$|waiting for your input"));
        rules.add(new Rule(AgentStatus.AGENT_CLAUDE, AgentStatus.State.WORKING,
            "(?i)esc to interrupt|ctrl\\+c to interrupt|\\(thinking|(?m)^\\W{0,4}thinking\\b"));
        rules.add(new Rule(AgentStatus.AGENT_CLAUDE, AgentStatus.State.IDLE,
            "(?mi)^\\s*[❯>]\\s*$|\\? for shortcuts|try \"|bypass permissions"));

        // --- Codex ---
        rules.add(new Rule(AgentStatus.AGENT_CODEX, AgentStatus.State.BLOCKED,
            "(?mi)^\\s*allow command\\b|^\\s*do you want to\\b|\\byes, proceed\\b"));
        rules.add(new Rule(AgentStatus.AGENT_CODEX, AgentStatus.State.BLOCKED,
            "(?mi)^\\s*[❯›▸▶]?\\s*\\d*[.)]?\\s*approve\\b|[\\[(]y/n[\\])]"));
        rules.add(new Rule(AgentStatus.AGENT_CODEX, AgentStatus.State.WORKING,
            "(?i)esc to interrupt|working \\("));
        rules.add(new Rule(AgentStatus.AGENT_CODEX, AgentStatus.State.IDLE,
            "(?mi)ask codex to do anything|^\\s*[»›]\\s*$"));

        return Collections.unmodifiableList(rules);
    }

    private AgentScreenRules() {}

    /** The rule table, for tests and for anyone counting what an agent knows. */
    @NonNull
    public static List<Rule> rules() {
        return RULES;
    }

    /**
     * What {@code screen} says {@code agent} is doing, or null when no rule matched and the caller
     * should keep whatever it already had.
     *
     * @param screen the last {@link #TAIL_ROWS} rows of the pane, as plain text.
     * @param cpuWorking the CPU-based working flag, used only for agents with no rules.
     */
    @Nullable
    public static AgentStatus.State classify(@Nullable String agent, @Nullable String screen,
                                             boolean cpuWorking) {
        if (agent == null) return null;
        if (!AgentStatus.hasScreenRules(agent)) return classifyGeneric(cpuWorking);
        if (screen == null || screen.isEmpty()) return null;
        for (Rule rule : RULES) {
            if (!rule.agent.equals(agent)) continue;
            if (rule.pattern.matcher(screen).find()) return rule.state;
        }
        return null;
    }

    /**
     * The reading for a known agent with no rules of its own: the pane's CPU says working or it
     * does not. Never blocked — nothing here has seen a prompt.
     */
    @NonNull
    public static AgentStatus.State classifyGeneric(boolean cpuWorking) {
        return cpuWorking ? AgentStatus.State.WORKING : AgentStatus.State.IDLE;
    }

    /** The last {@code rows} lines of {@code text}, blank lines dropped. */
    @NonNull
    public static String tail(@Nullable String text, int rows) {
        if (text == null || text.isEmpty()) return "";
        String[] lines = text.split("\n");
        StringBuilder out = new StringBuilder();
        int kept = 0;
        for (int i = lines.length - 1; i >= 0 && kept < rows; i--) {
            if (lines[i].trim().isEmpty()) continue;
            out.insert(0, lines[i] + "\n");
            kept++;
        }
        return out.toString();
    }
}
