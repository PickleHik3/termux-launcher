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
        /**
         * For a clash found only as the leader in force types the stroke, that typed stroke: the
         * other action's own binding, which Replace must take off or the new one never fires.
         */
        @Nullable public final String typedStroke;

        private Decision(@NonNull Kind kind, @Nullable String reason,
                         @Nullable TerminalKeyBindingResolver.Hint holder,
                         @Nullable String typedStroke) {
            this.kind = kind;
            this.reason = reason;
            this.holder = holder;
            this.typedStroke = typedStroke;
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
        if (reason != null) return new Decision(Decision.Kind.REFUSED, reason, null, null);
        TerminalKeyBindingResolver.Hint holder = byStored.get(stored);
        String typedStroke = null;
        if (holder == null) {
            holder = byTyped.get(typed);
            if (holder != null) typedStroke = typed;
        }
        if (holder == null) return new Decision(Decision.Kind.FREE, null, null, null);
        if (tool.equals(holder.toolName) && holder.label == null)
            return new Decision(Decision.Kind.UNCHANGED, null, null, null);
        return new Decision(Decision.Kind.CLASH, null, holder, typedStroke);
    }

    /** Why Android takes the stroke as typed under {@code leader}, or null. */
    @Nullable
    private static String reservation(@NonNull Leader leader, @NonNull String typed) {
        // A prefix leader (Ctrl+B, then the key) types the key bare: nothing to reserve.
        if (leader.isPrefix()) return null;
        return LeaderChord.reservationOfTyped(typed);
    }

    /**
     * The strokes to take off when {@code tool} moves to {@code stored}: its other stored chords
     * (a built-in default is one, though no line of the file says so), and for a clash found only
     * as typed, the other action's typed stroke. The writer turns each into an unmap where a
     * default would otherwise come back.
     */
    @NonNull
    public static List<String> strokesToFree(@NonNull String tool, @NonNull String stored,
            @NonNull Decision decision,
            @NonNull Map<String, TerminalKeyBindingResolver.Hint> byStored) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, TerminalKeyBindingResolver.Hint> holder : byStored.entrySet()) {
            if (!holder.getKey().equals(stored) && tool.equals(holder.getValue().toolName)
                && holder.getValue().label == null) out.add(holder.getKey());
        }
        if (decision.kind == Decision.Kind.CLASH && decision.typedStroke != null)
            out.add(decision.typedStroke);
        return out;
    }

    /** One key of the on-screen picker: what it shows and the stroke token it stands for. */
    public static final class PickerKey {
        @NonNull public final String label;
        @NonNull public final String token;

        PickerKey(@NonNull String label, @NonNull String token) {
            this.label = label;
            this.token = token;
        }
    }

    /** The keys the picker offers: letters, digits, arrows, F-keys, Space and common symbols. */
    @NonNull
    public static List<PickerKey> pickerKeys() {
        List<PickerKey> keys = new ArrayList<>();
        for (char c = 'a'; c <= 'z'; c++) keys.add(new PickerKey(String.valueOf(c).toUpperCase(java.util.Locale.ROOT), String.valueOf(c)));
        for (char c = '0'; c <= '9'; c++) keys.add(new PickerKey(String.valueOf(c), String.valueOf(c)));
        keys.add(new PickerKey("\u2190", "left"));
        keys.add(new PickerKey("\u2191", "up"));
        keys.add(new PickerKey("\u2192", "right"));
        keys.add(new PickerKey("\u2193", "down"));
        for (int i = 1; i <= 12; i++) keys.add(new PickerKey("F" + i, "f" + i));
        keys.add(new PickerKey("Space", "space"));
        for (char c : "-=[];',./\\`".toCharArray()) {
            String token = com.termux.app.leader.KeyTokens.tokenForChar(c);
            if (token != null) keys.add(new PickerKey(String.valueOf(c), token));
        }
        return keys;
    }
}
