package com.termux.app.terminal.keybind;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.leader.Leader;
import com.termux.app.leader.LeaderChord;
import com.termux.app.terminal.TerminalKeyBindingResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The decisions behind Terl Settings' bindings page, with no views: how a binding reads under a
 * leader, which key press after the leader is a candidate, and what binding it would do to the
 * table (refused, no change, free, or a clash with another action).
 */
public final class ActionBindings {

    private ActionBindings() {}

    /** One action as the page lists it. */
    public static final class Action {
        @NonNull public final String tool;
        @NonNull public final String title;

        public Action(@NonNull String tool, @NonNull String title) {
            this.tool = tool;
            this.title = title;
        }
    }

    /** One row: an action and the chords it is bound to, as stored (empty when unbound). */
    public static final class Row {
        @NonNull public final Action action;
        @NonNull public final List<String> strokes;

        Row(@NonNull Action action, @NonNull List<String> strokes) {
            this.action = action;
            this.strokes = Collections.unmodifiableList(strokes);
        }

        /** The chord the row shows: the first one, or null when unbound. */
        @Nullable
        public String stroke() {
            return strokes.isEmpty() ? null : strokes.get(0);
        }
    }

    /** Every action with the stored strokes that run it, in the order given. */
    @NonNull
    public static List<Row> rows(@NonNull List<Action> actions,
                                 @NonNull Map<String, TerminalKeyBindingResolver.Hint> holders) {
        List<Row> rows = new ArrayList<>();
        for (Action action : actions) {
            List<String> strokes = new ArrayList<>();
            for (Map.Entry<String, TerminalKeyBindingResolver.Hint> holder : holders.entrySet()) {
                // A labelled binding is somebody's own mapping of the tool (an app, a contact).
                if (action.tool.equals(holder.getValue().toolName) && holder.getValue().label == null)
                    strokes.add(holder.getKey());
            }
            rows.add(new Row(action, strokes));
        }
        return rows;
    }

    /** What the leader is called on the page. */
    @NonNull
    public static String leaderName(@NonNull Leader leader) {
        switch (leader.kind) {
            case CTRL_ALT: return "Ctrl+Alt";
            case CUSTOM:
                return TerminalKeyBindingResolver.displaySequence(leader.chord.spec());
            default: return "Terl key";
        }
    }

    /**
     * A stored stroke as the page shows it under {@code leader}: {@code Terl key + P}. A stroke
     * that is not leader-relative reads as itself.
     */
    @NonNull
    public static String display(@NonNull Leader leader, @NonNull String stored) {
        if (!stored.startsWith(Leader.CANONICAL_PREFIX))
            return TerminalKeyBindingResolver.displaySequence(stored);
        String rest = stored.substring(Leader.CANONICAL_PREFIX.length());
        return leaderName(leader) + " + " + TerminalKeyBindingResolver.displaySequence(rest);
    }

    /**
     * The stored stroke for a key pressed after the leader ({@code p}, {@code shift+p}), or null
     * for something that is not a lone key: nothing, or a press that carries Ctrl or Alt.
     */
    @Nullable
    public static String storedFor(@Nullable String pressed) {
        if (pressed == null || pressed.isEmpty()) return null;
        for (String part : pressed.split("\\+")) {
            if (part.equals("ctrl") || part.equals("alt")) return null;
        }
        return Leader.CANONICAL_PREFIX + pressed;
    }

    /** What binding a stroke to an action would do. */
    public static final class Decision {
        public enum Kind { REFUSED, UNCHANGED, FREE, CLASH }

        @NonNull public final Kind kind;
        /** Why a refused key cannot be bound; null otherwise. */
        @Nullable public final String reason;
        /** Who holds the stroke already, for a clash; null otherwise. */
        @Nullable public final TerminalKeyBindingResolver.Hint holder;

        private Decision(@NonNull Kind kind, @Nullable String reason,
                         @Nullable TerminalKeyBindingResolver.Hint holder) {
            this.kind = kind;
            this.reason = reason;
            this.holder = holder;
        }
    }

    /**
     * Judges binding {@code stored} to {@code tool} under {@code leader}: refused when Android
     * takes the keys as the leader types them, unchanged when the action has it already, a clash
     * when another action holds it (as stored, or as the leader in force types it), else free.
     */
    @NonNull
    public static Decision decide(@NonNull Leader leader, @NonNull String stored,
                                  @NonNull String tool,
                                  @NonNull Map<String, TerminalKeyBindingResolver.Hint> byStored,
                                  @NonNull Map<String, TerminalKeyBindingResolver.Hint> byTyped) {
        String typed = leader.physicalSequence(stored);
        String reason = reservation(leader, typed);
        if (reason != null) return new Decision(Decision.Kind.REFUSED, reason, null);
        TerminalKeyBindingResolver.Hint holder = byStored.get(stored);
        if (holder == null) holder = byTyped.get(typed);
        if (holder == null) return new Decision(Decision.Kind.FREE, null, null);
        if (tool.equals(holder.toolName) && holder.label == null)
            return new Decision(Decision.Kind.UNCHANGED, null, null);
        return new Decision(Decision.Kind.CLASH, null, holder);
    }

    /** Why Android takes the stroke as typed under {@code leader}, or null. */
    @Nullable
    private static String reservation(@NonNull Leader leader, @NonNull String typed) {
        // A prefix leader (Ctrl+B, then the key) types the key bare: nothing to reserve.
        if (leader.isPrefix()) return null;
        String first = typed.indexOf('>') < 0 ? typed : typed.substring(0, typed.indexOf('>'));
        boolean ctrl = false, alt = false, shift = false;
        String key = null;
        for (String part : first.split("\\+")) {
            switch (part) {
                case "ctrl": ctrl = true; break;
                case "alt": alt = true; break;
                case "shift": shift = true; break;
                default: key = part;
            }
        }
        return LeaderChord.reservation(ctrl, alt, shift, key);
    }
}
