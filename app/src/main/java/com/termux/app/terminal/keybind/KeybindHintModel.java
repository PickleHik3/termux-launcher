package com.termux.app.terminal.keybind;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.KeybindGroupPalette;
import com.termux.app.terminal.TerminalKeyBindingResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the keybind hint surfaces <em>say</em>, decided without saying it: which legend rows a hint
 * table turns into, which caps they wear, which keys light up and in what colour, and which chips
 * the compact strip carries.
 *
 * <p>Pure: no {@code View}, no {@code Context}, no resources. The two things that genuinely need
 * the host — a tool's display name (a string resource) and a legend group's colour (theme maths
 * that goes through {@code androidx} colour utilities) — come in as the {@link Labels} and
 * {@link GroupColors} seams, so the packing, the caps, the run merging and the row cap can all be
 * asserted with nothing but JUnit. {@link KeybindHintPresenter} owns everything that <em>happens</em>
 * as a result.
 *
 * <p>Two properties are worth more than the individual numbers:
 *
 * <ul>
 *   <li><b>Lighting is never truncated.</b> {@link #MAX_ENTRIES} caps legend <em>rows</em>; every
 *       bound cap of the latched prefix's own table still lights, because a lit cap with no row
 *       tells the truth while a row for a dark cap would not.
 *   <li><b>A run costs one row.</b> Keys one tool claims across a range under the same printed
 *       label — the four arrows, the session digits — merge into a single entry whose cap spells
 *       the range ({@code ←↓↑→}, {@code 1-9}), which is what makes the row cap comfortable.
 * </ul>
 */
public final class KeybindHintModel {

    private KeybindHintModel() {}

    /**
     * Cap on legend rows, not on lit keys. Runs collapse to one row each, which is what makes the
     * cap comfortable rather than tight.
     */
    public static final int MAX_ENTRIES = 24;
    /** Legend rows are two entries wide; an odd trailing cell still gets its half of the row. */
    public static final int COLUMNS = 2;

    /** One legend line: the keycap text shown, the keyboard tokens it lights, and its label. */
    public static final class Entry {
        /** Keycap text; rewritten to the merged range once a run is known to span several keys. */
        @NonNull public String cap;
        public final List<String> tokens = new ArrayList<>(4);
        @NonNull public final String label;
        /** Chord spelled before the keycap, empty for the latched prefix's own keys. */
        @NonNull public final String capPrefix;

        Entry(@NonNull String cap, @NonNull String token, @NonNull String label,
              @NonNull String capPrefix) {
            this.cap = cap;
            this.tokens.add(token);
            this.label = label;
            this.capPrefix = capPrefix;
        }
    }

    /** Names an action for the legend: a registry title, a user {@code --label}, or the raw id. */
    public interface Labels {
        @NonNull String labelFor(@NonNull String toolName, @Nullable String bindingLabel);
    }

    /** A legend group's colour. Called at most once per group; results are memoised here. */
    public interface GroupColors {
        int colorFor(@NonNull KeybindGroupPalette.Group group);
    }

    /** The whole legend: grouped rows in palette order, their colours, and the caps to light. */
    public static final class Legend {
        @NonNull public final EnumMap<KeybindGroupPalette.Group, List<Entry>> groups;
        @NonNull public final EnumMap<KeybindGroupPalette.Group, Integer> groupColors;
        /** Binding token -> group colour, for the in-app keyboard's lit caps. */
        @NonNull public final Map<String, Integer> litTokens;

        Legend(@NonNull EnumMap<KeybindGroupPalette.Group, List<Entry>> groups,
               @NonNull EnumMap<KeybindGroupPalette.Group, Integer> groupColors,
               @NonNull Map<String, Integer> litTokens) {
            this.groups = groups;
            this.groupColors = groupColors;
            this.litTokens = litTokens;
        }

        /** Legend rows across every group, i.e. what {@link #MAX_ENTRIES} caps. */
        public int entryCount() {
            int count = 0;
            for (List<Entry> entries : groups.values()) count += entries.size();
            return count;
        }
    }

    /**
     * Folds the latched prefix's hint table — and, for the full table, the plain {@code Ctrl+}
     * strokes listed alongside it — into legend groups in {@link KeybindGroupPalette.Group} order,
     * so the same action always lands in the same section with the same colour.
     *
     * <p>The {@code Ctrl+} table is a different chord, so its caps are spelled with their own
     * {@code "Ctrl+"} prefix and they never light a key.
     */
    @NonNull
    public static Legend entriesFor(
            @NonNull Map<String, TerminalKeyBindingResolver.Hint> hints,
            @NonNull Map<String, TerminalKeyBindingResolver.Hint> ctrlHints,
            boolean shift,
            @NonNull Labels labels,
            @NonNull GroupColors colors) {
        EnumMap<KeybindGroupPalette.Group, List<Entry>> groups =
            new EnumMap<>(KeybindGroupPalette.Group.class);
        EnumMap<KeybindGroupPalette.Group, Integer> groupColors =
            new EnumMap<>(KeybindGroupPalette.Group.class);
        Map<String, Entry> runEntryByTool = new HashMap<>();
        List<Entry> runEntries = new ArrayList<>();
        Map<String, Integer> litTokens = new LinkedHashMap<>();
        int added = collect(hints, shift, "", true, labels, colors,
            groups, groupColors, runEntryByTool, runEntries, litTokens, 0);
        collect(ctrlHints, false, "Ctrl+", false, labels, colors,
            groups, groupColors, runEntryByTool, runEntries, litTokens, added);
        // A merged entry shows every key it absorbed: arrows as glyphs in ←↓↑→ order, digits as
        // the range they span.
        for (Entry entry : runEntries) {
            if (entry.tokens.size() > 1) entry.cap = entry.capPrefix + runCap(entry.tokens);
        }
        return new Legend(groups, groupColors, litTokens);
    }

    /**
     * @param capPrefix printed before the keycap text, for a table reached by a different chord
     *     than the latched prefix ({@code "Ctrl+"}).
     * @param light whether these keys light up on the in-app keyboard. Only the latched prefix's
     *     own table does: lighting a key for a stroke the prefix does not send would be a lie.
     * @return the running legend-row count, so the row cap spans both tables.
     */
    private static int collect(
            @NonNull Map<String, TerminalKeyBindingResolver.Hint> hints,
            boolean shift,
            @NonNull String capPrefix,
            boolean light,
            @NonNull Labels labels,
            @NonNull GroupColors colors,
            @NonNull EnumMap<KeybindGroupPalette.Group, List<Entry>> groups,
            @NonNull EnumMap<KeybindGroupPalette.Group, Integer> groupColors,
            @NonNull Map<String, Entry> runEntryByTool,
            @NonNull List<Entry> runEntries,
            @NonNull Map<String, Integer> litTokens,
            int added) {
        for (Map.Entry<String, TerminalKeyBindingResolver.Hint> hint : hints.entrySet()) {
            String token = hint.getKey();
            String toolName = hint.getValue().toolName;
            String label = labels.labelFor(toolName, hint.getValue().label);
            // Runs merge on the printed label, not just the tool: nine app.launch digits named
            // after nine different apps are nine bindings, not one "Launch app" row.
            String runKey = toolName + ' ' + label;
            KeybindGroupPalette.Group group = KeybindGroupPalette.groupFor(toolName);
            Integer groupColor = groupColors.get(group);
            if (groupColor == null) {
                groupColor = colors.colorFor(group);
                groupColors.put(group, groupColor);
            }
            // Lighting is never truncated: a bound cap that lights but has no legend row still
            // tells the truth, a legend row for a dark cap would not.
            if (light) litTokens.put(token, groupColor);
            boolean run = isRunToken(token);
            if (run) {
                Entry merged = runEntryByTool.get(runKey);
                if (merged != null) {
                    merged.tokens.add(token);
                    continue;
                }
            }
            if (added >= MAX_ENTRIES) continue;
            added++;
            Entry entry = new Entry(capPrefix + capText(token, shift), token, label, capPrefix);
            if (run) {
                runEntryByTool.put(runKey, entry);
                runEntries.add(entry);
            }
            List<Entry> groupEntries = groups.get(group);
            if (groupEntries == null) {
                groupEntries = new ArrayList<>();
                groups.put(group, groupEntries);
            }
            groupEntries.add(entry);
        }
        return added;
    }

    /** Legend-group colours for every bound cap, without building any legend rows. */
    @NonNull
    public static Map<String, Integer> litTokens(
            @NonNull Map<String, TerminalKeyBindingResolver.Hint> hints,
            @NonNull GroupColors colors) {
        EnumMap<KeybindGroupPalette.Group, Integer> groupColors =
            new EnumMap<>(KeybindGroupPalette.Group.class);
        Map<String, Integer> lit = new LinkedHashMap<>();
        for (Map.Entry<String, TerminalKeyBindingResolver.Hint> hint : hints.entrySet()) {
            KeybindGroupPalette.Group group =
                KeybindGroupPalette.groupFor(hint.getValue().toolName);
            Integer color = groupColors.get(group);
            if (color == null) {
                color = colors.colorFor(group);
                groupColors.put(group, color);
            }
            lit.put(hint.getKey(), color);
        }
        return lit;
    }

    /**
     * The essential binds the strip names, in strip order, with the label each chip wears:
     * {@code {tokens, label}} where tokens are space-separated stroke suffixes and a label may be
     * a nerd-symbol glyph (rendered from the bundled symbol face). Chips whose tokens are not
     * actually bound are dropped rather than shown as a lie.
     */
    private static final String[][] STRIP_BASE = {
        {"enter", "new pane"},
        {"v h", "split"},
        {"w", "close pane"},
        {"c", "new window"},
        {"x", "close window"},
        {"left right", ""},   // nf-fa-window_maximize: previous/next window
        {"up down", ""},      // nf-oct-terminal: previous/next session
    };

    /** The Shift layer's chips, letters shown upper-case by the cap renderer. */
    private static final String[][] STRIP_SHIFT = {
        {"c", "new session"},
        {"x", "close session"},
        {"left down up right", ""},   // nf-fa-arrows: resize pane
        {"p", "palette"},
    };

    /** One strip chip: the caps it prints, its label, and the token whose colour it borrows. */
    public static final class StripChip {
        @NonNull public final String caps;
        @NonNull public final String label;
        /** First bound token of the chip, i.e. the one whose group colour the caps wear. */
        @NonNull public final String colorToken;
        /** Every bound token this chip prints — what the keyboard may light under the strip. */
        @NonNull public final List<String> tokens;

        StripChip(@NonNull String caps, @NonNull String label, @NonNull String colorToken,
                  @NonNull List<String> tokens) {
            this.caps = caps;
            this.label = label;
            this.colorToken = colorToken;
            this.tokens = Collections.unmodifiableList(tokens);
        }
    }

    /**
     * The curated row of binds for the held table — the base list under Ctrl+Alt, the
     * session/resize list once Shift joins — in display order, without the trailing {@code ?}
     * chip the presenter always adds. Empty when nothing curated is actually bound, which is the
     * presenter's cue to show no strip at all.
     */
    @NonNull
    public static List<StripChip> stripChips(
            @NonNull Map<String, TerminalKeyBindingResolver.Hint> hints, boolean shift) {
        List<StripChip> chips = new ArrayList<>();
        for (String[] spec : shift ? STRIP_SHIFT : STRIP_BASE) {
            StringBuilder caps = new StringBuilder();
            String colorToken = null;
            List<String> tokens = new ArrayList<>(4);
            for (String token : spec[0].split(" ")) {
                if (!hints.containsKey(token)) continue;
                if (caps.length() > 0) caps.append(' ');
                caps.append(capText(token, shift));
                if (colorToken == null) colorToken = token;
                tokens.add(token);
            }
            if (caps.length() == 0) continue;
            chips.add(new StripChip(caps.toString(), spec[1], colorToken, tokens));
        }
        return Collections.unmodifiableList(chips);
    }

    /**
     * The held chord, spelled for a human: {@code "ctrl+alt+"} is {@code "Ctrl+Alt"}, and a leader
     * waiting for its second key ({@code "ctrl+space>"}) keeps the {@code ▸} that says so.
     */
    @NonNull
    public static String prefixLabel(@NonNull String prefix) {
        boolean leader = prefix.endsWith(">");
        String body = prefix;
        while (body.endsWith("+") || body.endsWith(">"))
            body = body.substring(0, body.length() - 1);
        StringBuilder label = new StringBuilder(body.length() + 2);
        for (String part : body.split("\\+")) {
            if (part.isEmpty()) continue;
            if (label.length() > 0) label.append('+');
            label.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        if (leader) label.append(" \u25b8");
        return label.toString();
    }

    /**
     * The caps the strip is entitled to light: the tokens its own chips print, and nothing else.
     *
     * <p>Holding the prefix used to light every bound key on the keyboard while the strip named
     * five of them. That is two different answers to one question — the strip says "here is what
     * you can do", the keyboard said "here is everything that exists" — and the lit field was dense
     * enough that the five that matter were the hardest to find in it. The full table is where
     * everything lights, and {@code ?} is how you ask for it.
     */
    @NonNull
    public static Map<String, Integer> stripLitTokens(
            @NonNull Map<String, TerminalKeyBindingResolver.Hint> hints, boolean shift,
            @NonNull GroupColors colors) {
        EnumMap<KeybindGroupPalette.Group, Integer> groupColors =
            new EnumMap<>(KeybindGroupPalette.Group.class);
        Map<String, Integer> lit = new LinkedHashMap<>();
        for (StripChip chip : stripChips(hints, shift)) {
            for (String token : chip.tokens) {
                TerminalKeyBindingResolver.Hint hint = hints.get(token);
                if (hint == null) continue;
                KeybindGroupPalette.Group group = KeybindGroupPalette.groupFor(hint.toolName);
                Integer color = groupColors.get(group);
                if (color == null) {
                    color = colors.colorFor(group);
                    groupColors.put(group, color);
                }
                lit.put(token, color);
            }
        }
        return lit;
    }

    /**
     * Whether this key is part of a run one tool claims across several keys — the four arrows, the
     * nine session digits. Such keys share a legend row instead of each taking one.
     */
    public static boolean isRunToken(@NonNull String token) {
        if (arrowGlyph(token) != null) return true;
        return token.length() == 1 && token.charAt(0) >= '0' && token.charAt(0) <= '9';
    }

    /** Keycap text for a merged run: {@code ←↓↑→} for arrows, {@code 1-9} for a digit span. */
    @NonNull
    public static String runCap(@NonNull List<String> tokens) {
        StringBuilder cap = new StringBuilder();
        for (String token : new String[] {"left", "down", "up", "right"}) {
            if (tokens.contains(token)) cap.append(arrowGlyph(token));
        }
        List<String> digits = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            if (arrowGlyph(token) == null) digits.add(token);
        }
        Collections.sort(digits);
        if (digits.size() >= 3) {
            // Contiguity is not checked: a gap in the middle of nine index binds is not worth
            // spelling out on a cap this small, and the labels name the action either way.
            cap.append(digits.get(0)).append('-').append(digits.get(digits.size() - 1));
        } else {
            for (int i = 0; i < digits.size(); i++) {
                if (i > 0) cap.append(' ');
                cap.append(digits.get(i));
            }
        }
        return cap.toString();
    }

    @Nullable
    public static String arrowGlyph(@NonNull String token) {
        switch (token) {
            case "left": return "←";
            case "down": return "↓";
            case "up": return "↑";
            case "right": return "→";
            default: return null;
        }
    }

    /** Legend keycap text: spelled-out tokens back to their glyph, letters follow the prefix case. */
    @NonNull
    public static String capText(@NonNull String token, boolean shift) {
        String arrow = arrowGlyph(token);
        if (arrow != null) return arrow;
        switch (token) {
            case "minus": return "-";
            case "equals": return "=";
            case "plus": return "+";
            // Named keys as their glyph: a legend cap column is 22dp wide, which "backspace"
            // spelled out overruns before the label it belongs to has started.
            case "space": return "␣";
            case "tab": return "⇥";
            case "enter": return "⏎";
            case "backspace": return "⌫";
            case "delete": return "⌦";
            case "escape": return "esc";
            case "pageup": return "⇞";
            case "pagedown": return "⇟";
            default:
                return shift ? token.toUpperCase(Locale.ROOT) : token.toLowerCase(Locale.ROOT);
        }
    }
}
