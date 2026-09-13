package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What the OSC terminal title an agent set says it is doing. The cheapest and the most honest of
 * the readings: the agent writes the title itself, once per state change, so unlike
 * {@link AgentScreenRules} it never confuses a stale footer left on screen with a live turn — a
 * Claude sitting at its prompt with a background shell running says {@code ✳ …} while its screen
 * still reads {@code esc to interrupt}.
 *
 * <p>Ported from herdr's {@code osc_title} rules (manifests of 2026.09.11). Order inside an agent
 * is herdr's priority order, which is blocked before working before idle everywhere except where an
 * agent's catch-all has to come last: codex calls any title it has not otherwise explained idle,
 * grok calls it working. First match wins, so the catch-alls sit at the end of their group.
 *
 * <p>Free of Android imports, so every rule here is unit-testable, and cheap enough that the
 * tracker runs it on every refresh without a throttle: it is a compare over one short string.
 */
public final class AgentTitleRules {

    /** One title test and the state it proves. Either a regex or a plain substring, never both. */
    public static final class Rule {
        @NonNull public final String agent;
        @NonNull public final AgentStatus.State state;
        /** The pattern to find, or null when this rule is a substring test. */
        @Nullable public final Pattern pattern;
        /** The substring to look for, already lowercased, or null when this rule is a regex. */
        @Nullable public final String contains;

        private Rule(@NonNull String agent, @NonNull AgentStatus.State state,
                     @Nullable Pattern pattern, @Nullable String contains) {
            this.agent = agent;
            this.state = state;
            this.pattern = pattern;
            this.contains = contains;
        }

        boolean matches(@NonNull String title, @NonNull String lowercased) {
            return pattern != null ? pattern.matcher(title).find()
                : lowercased.contains(contains);
        }
    }

    private static final List<Rule> RULES = buildRules();

    private static List<Rule> buildRules() {
        List<Rule> rules = new ArrayList<>();

        // --- Claude Code: a spinner glyph while it works, the sparkle when it is back at rest.
        // Braille covers <= 2.1.227; the half-circles are the 2.1.228 busy spinner. No blocked
        // title: a permission prompt is only visible on screen.
        rules.add(regex(AgentStatus.AGENT_CLAUDE, AgentStatus.State.WORKING,
            "^[\\u2800-\\u28FF\\u25D0-\\u25D3] "));
        rules.add(regex(AgentStatus.AGENT_CLAUDE, AgentStatus.State.IDLE, "^\\u2733 "));

        // --- Codex: a braille spinner stands as its own word while a turn runs, and any title it
        // has not explained otherwise is a resting one.
        rules.add(contains(AgentStatus.AGENT_CODEX, AgentStatus.State.BLOCKED, "Action Required"));
        rules.add(regex(AgentStatus.AGENT_CODEX, AgentStatus.State.WORKING,
            "(?:^| )[\\u280B\\u2819\\u2839\\u2838\\u283C\\u2834\\u2826\\u2827\\u2807\\u280F](?: |$)"));
        rules.add(regex(AgentStatus.AGENT_CODEX, AgentStatus.State.IDLE, "\\S"));

        // --- Amp: its resting title names the workspace between dashes.
        rules.add(contains(AgentStatus.AGENT_AMP, AgentStatus.State.BLOCKED,
            "Plugin confirmation needed"));
        rules.add(regex(AgentStatus.AGENT_AMP, AgentStatus.State.WORKING, "^[\\u2800-\\u28FF] "));
        rules.add(contains(AgentStatus.AGENT_AMP, AgentStatus.State.IDLE, " - amp - "));

        // --- Qwen Code: status glyphs, each optionally carrying a text-presentation selector.
        rules.add(regex(AgentStatus.AGENT_QWEN, AgentStatus.State.BLOCKED, "^\\u2733\\uFE0E? "));
        rules.add(regex(AgentStatus.AGENT_QWEN, AgentStatus.State.WORKING, "^\\u25D0\\uFE0E? "));

        // --- Grok: it names itself when resting, so anything else it put in the title is a turn.
        // The spinner exclusion keeps a working title that happens to end in the agent's own name
        // ("⠧ Explore - grok") out of the idle rule, as herdr's `not` clause does.
        rules.add(contains(AgentStatus.AGENT_GROK, AgentStatus.State.BLOCKED, "Action Required"));
        rules.add(regex(AgentStatus.AGENT_GROK, AgentStatus.State.IDLE,
            "^(?!.*[\\u2800-\\u28FF])(?:.+ - )?grok$"));
        rules.add(regex(AgentStatus.AGENT_GROK, AgentStatus.State.WORKING, "\\S"));

        // --- Hermes: one leading glyph per state, with or without a variation selector.
        rules.add(regex(AgentStatus.AGENT_HERMES, AgentStatus.State.BLOCKED,
            "^\\u26A0[\\uFE0E\\uFE0F]?(?:\\s|$)"));
        rules.add(regex(AgentStatus.AGENT_HERMES, AgentStatus.State.WORKING,
            "^\\u23F3[\\uFE0E\\uFE0F]?(?:\\s|$)"));
        rules.add(regex(AgentStatus.AGENT_HERMES, AgentStatus.State.IDLE,
            "^\\u2713[\\uFE0E\\uFE0F]?(?:\\s|$)"));

        return Collections.unmodifiableList(rules);
    }

    private AgentTitleRules() {}

    /** The rule table, for tests and for anyone counting what an agent knows. */
    @NonNull
    public static List<Rule> rules() {
        return RULES;
    }

    /** Whether {@code agent} sets a title this table can read. */
    public static boolean hasTitleRules(@Nullable String agent) {
        if (agent == null) return false;
        for (Rule rule : RULES) {
            if (rule.agent.equals(agent)) return true;
        }
        return false;
    }

    /**
     * What {@code title} says {@code agent} is doing, or null when the agent sets no title we read,
     * the title is empty, or no rule claimed it — in which case the caller keeps what it had.
     */
    @Nullable
    public static AgentStatus.State classify(@Nullable String agent, @Nullable String title) {
        if (agent == null || title == null) return null;
        if (title.trim().isEmpty()) return null;
        // herdr lowercases a region before a `contains` test, which is why its manifests spell the
        // same kind of literal both ways; do the fold once rather than per rule.
        String lowercased = title.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (!rule.agent.equals(agent)) continue;
            if (rule.matches(title, lowercased)) return rule.state;
        }
        return null;
    }

    private static Rule regex(@NonNull String agent, @NonNull AgentStatus.State state,
                              @NonNull String regex) {
        return new Rule(agent, state, Pattern.compile(regex), null);
    }

    private static Rule contains(@NonNull String agent, @NonNull AgentStatus.State state,
                                 @NonNull String literal) {
        return new Rule(agent, state, null, literal.toLowerCase(Locale.ROOT));
    }
}
