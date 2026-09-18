package com.termux.app.help;

import com.termux.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The words the launcher uses for its own parts, in plain language, each with the one topic that
 * goes further. The glossary list and global search read the same entries.
 *
 * <p>Pure: A–Z order needs resolved titles, so {@link #alphabetical} asks the caller for a
 * {@link HelpTopics.Text} rather than holding a context.
 */
public final class HelpGlossary {
    private HelpGlossary() {}

    /** One term: what it means, and where to read more. */
    public static final class Term {
        /** Stable, and what a topic's {@code termIds} names. */
        public final String id;
        public final int titleRes;
        /** One plain sentence. Mechanism belongs in the full documentation. */
        public final int definitionRes;
        /** The topic this term belongs to. */
        public final String topicId;

        Term(String id, int titleRes, int definitionRes, String topicId) {
            this.id = id;
            this.titleRes = titleRes;
            this.definitionRes = definitionRes;
            this.topicId = topicId;
        }

        @Override public String toString() { return id; }
    }

    private static final List<Term> ALL = build();
    private static final Map<String, Term> BY_ID = index(ALL);

    /** Every term, in the order the guide introduces them. */
    public static List<Term> all() { return ALL; }

    /** The term with this id, or null. */
    public static Term term(String id) { return id == null ? null : BY_ID.get(id); }

    /** The same terms, ordered A–Z by the title the reader actually sees. */
    public static List<Term> alphabetical(final HelpTopics.Text text) {
        List<Term> out = new ArrayList<>(ALL);
        Collections.sort(out, new Comparator<Term>() {
            @Override public int compare(Term a, Term b) {
                int byTitle = text.get(a.titleRes).compareToIgnoreCase(text.get(b.titleRes));
                return byTitle != 0 ? byTitle : a.id.compareTo(b.id);
            }
        });
        return Collections.unmodifiableList(out);
    }

    private static List<Term> build() {
        List<Term> terms = new ArrayList<>();
        terms.add(new Term("place", R.string.help_term_place_title,
            R.string.help_term_place_definition, "places"));
        terms.add(new Term("session", R.string.help_term_session_title,
            R.string.help_term_session_definition, "hierarchy"));
        terms.add(new Term("window", R.string.help_term_window_title,
            R.string.help_term_window_definition, "windows"));
        terms.add(new Term("pane", R.string.help_term_pane_title,
            R.string.help_term_pane_definition, "panes"));
        terms.add(new Term("workspace", R.string.help_term_workspace_title,
            R.string.help_term_workspace_definition, "workspaces"));
        terms.add(new Term("dock", R.string.help_term_dock_title,
            R.string.help_term_dock_definition, "dock"));
        terms.add(new Term("app_drawer", R.string.help_term_drawer_title,
            R.string.help_term_drawer_definition, "az"));
        terms.add(new Term("extra_keys", R.string.help_term_keys_title,
            R.string.help_term_keys_definition, "keys"));
        terms.add(new Term("command_palette", R.string.help_term_palette_title,
            R.string.help_term_palette_definition, "palette"));
        terms.add(new Term("surface", R.string.help_term_surface_title,
            R.string.help_term_surface_definition, "appearance_editor"));
        terms.add(new Term("base", R.string.help_term_base_title,
            R.string.help_term_base_definition, "base_values"));
        terms.add(new Term("independent_value", R.string.help_term_independent_title,
            R.string.help_term_independent_definition, "base_values"));
        terms.add(new Term("docked_floating", R.string.help_term_docked_title,
            R.string.help_term_docked_definition, "keyboard_layouts"));
        terms.add(new Term("mouse_mode", R.string.help_term_mouse_title,
            R.string.help_term_mouse_definition, "mouse_mode"));
        terms.add(new Term("editors", R.string.help_term_editors_title,
            R.string.help_term_editors_definition, "layout_editor"));
        return Collections.unmodifiableList(terms);
    }

    private static Map<String, Term> index(List<Term> terms) {
        Map<String, Term> map = new LinkedHashMap<>();
        for (Term term : terms) map.put(term.id, term);
        return Collections.unmodifiableMap(map);
    }
}
