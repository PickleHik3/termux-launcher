package com.termux.app.terminal.keybind;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.TerminalKeyBindingResolver;
import com.termux.launcherctl.LauncherToolRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the Terl key's hint card lists (ADR 0042, spec "Keys"): the bindings under the leader as
 * the binding table holds them now (Terl's defaults with the user's rebinds; never a list kept by
 * hand), every binding under the leader, grouped by intent (Open & close, Focus, Move, Resize &
 * split, Workspaces, Layout, Tools; round 3), one row per action in plain words with the keys that
 * fire it. The keys a family spreads over (the four focus arrows, the ten Workspaces, minus and
 * equals) share one row.
 *
 * <p>Pure: the table comes in as the resolver's hints and the words for a tool the card does not
 * know come through {@link KeybindHintModel.Labels}.
 */
public final class TerlHintCard {

    private TerlHintCard() {}

    /** The card's sections, by intent, in the order they show (round 3). */
    public enum Group { OPEN_CLOSE, FOCUS, MOVE, RESIZE_SPLIT, WORKSPACES, LAYOUT, TOOLS }

    /** The actions the card names in its own words; a tool outside them wears its registry title. */
    public enum Family {
        TERMINAL(Group.OPEN_CLOSE), NEW_LEFT(Group.OPEN_CLOSE), CLOSE(Group.OPEN_CLOSE), POP_OUT(Group.OPEN_CLOSE),
        FOCUS(Group.FOCUS), FOCUS_ENDS(Group.FOCUS), CENTRE(Group.FOCUS),
        MOVE(Group.MOVE), CONSUME(Group.MOVE),
        SPLIT(Group.RESIZE_SPLIT), SPLIT_OTHER(Group.RESIZE_SPLIT), WIDTH_NEXT(Group.RESIZE_SPLIT),
        WIDTH_PREVIOUS(Group.RESIZE_SPLIT), WIDTH_NUDGE(Group.RESIZE_SPLIT), HEIGHT_NUDGE(Group.RESIZE_SPLIT),
        FULL_WIDTH(Group.RESIZE_SPLIT),
        GO(Group.WORKSPACES), SEND(Group.WORKSPACES), WORKSPACE_STEP(Group.WORKSPACES),
        LAYOUT(Group.LAYOUT), FLOAT(Group.LAYOUT), FULL_SCREEN(Group.LAYOUT), TAB(Group.LAYOUT),
        OVERVIEW(Group.TOOLS), SEARCH(Group.TOOLS), EDIT_MODE(Group.TOOLS);

        @NonNull public final Group group;

        Family(@NonNull Group group) {
            this.group = group;
        }
    }

    /** One key cap: its face, and the chord it fires (the stroke after the leader), or null. */
    public static final class Cap {
        @NonNull public final String text;
        /** The stroke after the leader, e.g. {@code "shift+left"}; null for ⇧ and an elided run. */
        @Nullable public final String chord;

        Cap(@NonNull String text, @Nullable String chord) {
            this.text = text;
            this.chord = chord;
        }

        @NonNull @Override public String toString() {
            return text;
        }
    }

    /** One row: the action and the caps that fire it. */
    public static final class Row {
        @NonNull public final Group group;
        /** The card's own words for it; null for a tool named by {@link #label}. */
        @Nullable public final Family family;
        /** The registry's title (or the binding's label) when {@link #family} is null. */
        @Nullable public final String label;
        @NonNull public final List<Cap> caps;

        Row(@NonNull Group group, @Nullable Family family, @Nullable String label,
            @NonNull List<Cap> caps) {
            this.group = group;
            this.family = family;
            this.label = label;
            this.caps = Collections.unmodifiableList(caps);
        }

        /** The caps' faces joined, for tests and the row's spoken description. */
        @NonNull public String capsText() {
            StringBuilder out = new StringBuilder();
            for (Cap cap : caps) {
                if (out.length() > 0) out.append(' ');
                out.append(cap.text);
            }
            return out.toString();
        }
    }

    /** A group and its rows, never empty. */
    public static final class Section {
        @NonNull public final Group group;
        @NonNull public final List<Row> rows;

        Section(@NonNull Group group, @NonNull List<Row> rows) {
            this.group = group;
            this.rows = Collections.unmodifiableList(rows);
        }
    }

    /** The Shift cap a shifted row starts with. */
    public static final String SHIFT_CAP = "⇧";

    /**
     * The card for the bindings one key past the leader ({@code plain}, the resolver's hints for
     * {@link TerminalKeyBindingResolver#LEADER_HINT_PREFIX}) and one key past leader+Shift
     * ({@code shifted}). Sections in {@link Group} order, empty ones left out; rows in
     * {@link Family} order, then the other tools as the table lists them.
     */
    @NonNull
    public static List<Section> build(@NonNull Map<String, TerminalKeyBindingResolver.Hint> plain,
                                      @NonNull Map<String, TerminalKeyBindingResolver.Hint> shifted,
                                      @NonNull KeybindHintModel.Labels labels) {
        // Row key -> keys in table order. A family is one row; another tool one row per name.
        Map<String, Pending> pending = new LinkedHashMap<>();
        collect(plain, false, labels, pending);
        collect(shifted, true, labels, pending);
        EnumMap<Group, List<Row>> byGroup = new EnumMap<>(Group.class);
        List<Pending> ordered = new ArrayList<>(pending.values());
        // Families first in their own order, the rest after as the table lists them.
        Collections.sort(ordered, (a, b) -> Integer.compare(a.rank(), b.rank()));
        for (Pending row : ordered) {
            List<Row> rows = byGroup.get(row.group);
            if (rows == null) {
                rows = new ArrayList<>();
                byGroup.put(row.group, rows);
            }
            rows.add(new Row(row.group, row.family, row.label, caps(row)));
        }
        List<Section> sections = new ArrayList<>();
        for (Map.Entry<Group, List<Row>> entry : byGroup.entrySet())
            sections.add(new Section(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableList(sections);
    }

    /** The family {@code toolName} belongs to, or null for a tool the card names by its title. */
    @Nullable
    public static Family familyOf(@NonNull String toolName) {
        switch (toolName) {
            case LauncherToolRegistry.TOOL_WINDOW_NEW: return Family.TERMINAL;
            case LauncherToolRegistry.TOOL_WINDOW_NEW_LEFT: return Family.NEW_LEFT;
            case LauncherToolRegistry.TOOL_WINDOW_CLOSE: return Family.CLOSE;
            case LauncherToolRegistry.TOOL_WINDOW_POP_OUT: return Family.POP_OUT;
            case LauncherToolRegistry.TOOL_WINDOW_PREVIOUS:
            case LauncherToolRegistry.TOOL_WINDOW_NEXT:
            case LauncherToolRegistry.TOOL_WINDOW_FOCUS_UP:
            case LauncherToolRegistry.TOOL_WINDOW_FOCUS_DOWN:
            case LauncherToolRegistry.TOOL_SESSION_PREVIOUS:
            case LauncherToolRegistry.TOOL_SESSION_NEXT:
                return Family.FOCUS;
            case LauncherToolRegistry.TOOL_WINDOW_FOCUS_FIRST:
            case LauncherToolRegistry.TOOL_WINDOW_FOCUS_LAST:
                return Family.FOCUS_ENDS;
            case LauncherToolRegistry.TOOL_WINDOW_CENTRE: return Family.CENTRE;
            case LauncherToolRegistry.TOOL_WINDOW_MOVE_LEFT:
            case LauncherToolRegistry.TOOL_WINDOW_MOVE_RIGHT:
            case LauncherToolRegistry.TOOL_WINDOW_MOVE_UP:
            case LauncherToolRegistry.TOOL_WINDOW_MOVE_DOWN:
                return Family.MOVE;
            case LauncherToolRegistry.TOOL_WINDOW_CONSUME_LEFT:
            case LauncherToolRegistry.TOOL_WINDOW_CONSUME_RIGHT:
                return Family.CONSUME;
            case LauncherToolRegistry.TOOL_WINDOW_SPLIT: return Family.SPLIT;
            case LauncherToolRegistry.TOOL_WINDOW_SPLIT_OTHER: return Family.SPLIT_OTHER;
            case LauncherToolRegistry.TOOL_WINDOW_WIDTH_NEXT: return Family.WIDTH_NEXT;
            case LauncherToolRegistry.TOOL_WINDOW_WIDTH_PREVIOUS: return Family.WIDTH_PREVIOUS;
            case LauncherToolRegistry.TOOL_WINDOW_NARROWER:
            case LauncherToolRegistry.TOOL_WINDOW_WIDER:
                return Family.WIDTH_NUDGE;
            case LauncherToolRegistry.TOOL_WINDOW_SHORTER:
            case LauncherToolRegistry.TOOL_WINDOW_TALLER:
                return Family.HEIGHT_NUDGE;
            case LauncherToolRegistry.TOOL_WINDOW_FULL_WIDTH: return Family.FULL_WIDTH;
            case LauncherToolRegistry.TOOL_WORKSPACE_PREVIOUS:
            case LauncherToolRegistry.TOOL_WORKSPACE_NEXT:
                return Family.WORKSPACE_STEP;
            case LauncherToolRegistry.TOOL_WORKSPACE_LAYOUT_TOGGLE: return Family.LAYOUT;
            case LauncherToolRegistry.TOOL_WINDOW_FLOAT: return Family.FLOAT;
            case LauncherToolRegistry.TOOL_WINDOW_FULLSCREEN: return Family.FULL_SCREEN;
            case LauncherToolRegistry.TOOL_WINDOW_TAB: return Family.TAB;
            case LauncherToolRegistry.TOOL_SESSION_BROWSER: return Family.OVERVIEW;
            case LauncherToolRegistry.TOOL_BAR_SEARCH: return Family.SEARCH;
            case LauncherToolRegistry.TOOL_EDIT_MODE_OPEN: return Family.EDIT_MODE;
            default:
                if (toolName.startsWith(LauncherToolRegistry.TOOL_WORKSPACE_GO_PREFIX)) return Family.GO;
                if (toolName.startsWith(LauncherToolRegistry.TOOL_WINDOW_SEND_PREFIX)) return Family.SEND;
                return null;
        }
    }

    /** The section a tool outside the families stands in. */
    @NonNull
    static Group groupOf(@NonNull String toolName) {
        if (toolName.startsWith("window.") || toolName.startsWith("pane.")) return Group.LAYOUT;
        if (toolName.startsWith("workspace.")) return Group.WORKSPACES;
        return Group.TOOLS;
    }

    /** A key's face on the card: arrows as arrows, named keys in words, letters upper-case. */
    @NonNull
    public static String capFace(@NonNull String token) {
        String arrow = KeybindHintModel.arrowGlyph(token);
        if (arrow != null) return arrow;
        switch (token) {
            case "enter": return "↵";
            case "space": return "Space";
            case "tab": return "Tab";
            case "backspace": return "⌫";
            case "escape": return "Esc";
            case "minus": return "-";
            case "equals": return "=";
            case "plus": return "+";
            case "pageup": return "PgUp";
            case "pagedown": return "PgDn";
            case "home": return "Home";
            case "end": return "End";
            default: return token.toUpperCase(Locale.ROOT);
        }
    }

    private static void collect(@NonNull Map<String, TerminalKeyBindingResolver.Hint> hints,
                                boolean shift, @NonNull KeybindHintModel.Labels labels,
                                @NonNull Map<String, Pending> pending) {
        for (Map.Entry<String, TerminalKeyBindingResolver.Hint> entry : hints.entrySet()) {
            String toolName = entry.getValue().toolName;
            Family family = familyOf(toolName);
            String label = family != null ? null : labels.labelFor(toolName, entry.getValue().label);
            // A family's shifted and plain keys are two rows only when both happen (a user bound
            // Focus to a shifted key too): the ⇧ belongs to the whole row.
            String key = family != null ? family.name() + (shift ? "+shift" : "")
                : toolName + '\u0000' + label + (shift ? "+shift" : "");
            Pending row = pending.get(key);
            if (row == null) {
                row = new Pending(family != null ? family.group : groupOf(toolName), family, label,
                    shift, pending.size());
                pending.put(key, row);
            }
            row.tokens.add(entry.getKey());
        }
    }

    @NonNull
    private static List<Cap> caps(@NonNull Pending row) {
        List<Cap> caps = new ArrayList<>();
        String prefix = row.shift ? "shift+" : "";
        if (row.shift) caps.add(new Cap(SHIFT_CAP, null));
        List<String> arrows = new ArrayList<>();
        List<String> digits = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (String token : row.tokens) {
            if (KeybindHintModel.arrowGlyph(token) != null) arrows.add(token);
            else if (token.length() == 1 && Character.isDigit(token.charAt(0))) digits.add(token);
            else others.add(token);
        }
        for (String token : others) caps.add(new Cap(capFace(token), prefix + token));
        // The arrows in the prototype's order, whatever order the table holds them in.
        for (String token : new String[] {"left", "up", "down", "right"})
            if (arrows.contains(token)) caps.add(new Cap(capFace(token), prefix + token));
        // 0 is Workspace 10: it sorts last (K7).
        Collections.sort(digits, (a, b) -> Integer.compare(workspaceOf(a), workspaceOf(b)));
        if (digits.size() > 4 && contiguous(digits)) {
            // An unbroken run of Workspaces is three caps and the last: 1 2 3 …9.
            for (int i = 0; i < 3; i++) caps.add(new Cap(digits.get(i), prefix + digits.get(i)));
            caps.add(new Cap("…" + digits.get(digits.size() - 1), null));
        } else {
            // A run with a gap (a rebind took Workspace 5 elsewhere) shows every key it holds.
            for (String digit : digits) caps.add(new Cap(digit, prefix + digit));
        }
        return caps;
    }

    /** Whether sorted digits run with no gap (1 2 3 4 5, not 1 2 3 4 6; 9 then 0 runs on). */
    private static boolean contiguous(@NonNull List<String> digits) {
        for (int i = 1; i < digits.size(); i++)
            if (workspaceOf(digits.get(i)) - workspaceOf(digits.get(i - 1)) != 1) return false;
        return true;
    }

    /** The Workspace a digit key goes to: 1 to 9 as written, 0 for 10. */
    private static int workspaceOf(@NonNull String digit) {
        int n = digit.charAt(0) - '0';
        return n == 0 ? 10 : n;
    }

    /**
     * The words a screen reader says for a cap's key ({@code "shift+left"}): Shift, the arrows and
     * the named keys by their names from {@code words}, a letter or a digit as itself.
     */
    @NonNull
    public static String spokenKeys(@NonNull String chord, @NonNull KeyWords words) {
        StringBuilder out = new StringBuilder();
        for (String token : chord.split("\\+")) {
            if (token.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            String named = words.nameOf(token);
            out.append(named != null ? named : token.toUpperCase(Locale.ROOT));
        }
        return out.toString();
    }

    /** A named key's spoken name ("Left arrow", "Enter"), or null for one said as it is printed. */
    public interface KeyWords {
        @Nullable String nameOf(@NonNull String token);
    }

    /** A row while its keys are gathered. */
    private static final class Pending {
        @NonNull final Group group;
        @Nullable final Family family;
        @Nullable final String label;
        final boolean shift;
        final int order;
        final List<String> tokens = new ArrayList<>(4);

        Pending(@NonNull Group group, @Nullable Family family, @Nullable String label, boolean shift,
                int order) {
            this.group = group;
            this.family = family;
            this.label = label;
            this.shift = shift;
            this.order = order;
        }

        /** Families by their order (plain before shifted), then the rest by first appearance. */
        int rank() {
            if (family != null) return family.ordinal() * 2 + (shift ? 1 : 0);
            return 1000 + order;
        }
    }
}
