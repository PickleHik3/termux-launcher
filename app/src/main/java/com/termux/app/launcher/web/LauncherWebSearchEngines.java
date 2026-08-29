package com.termux.app.launcher.web;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * The search engines the launcher can hand a query to.
 *
 * <p>A template is a URL with one {@code %s} where the encoded query goes. That is the whole
 * contract, which is what lets the custom entry be any engine at all — a self-hosted SearxNG, a
 * wiki, an issue tracker — without the launcher knowing anything about it.
 *
 * <p>The labels here are brand names and stay untranslated on purpose. They are kept in step by
 * hand with {@code settings_web_search_engine_entries} in {@code strings.xml}, which is what the
 * Settings list shows; this table is what the palette row says and what resolves an id to a URL.
 */
public final class LauncherWebSearchEngines {

    /** The id of the user-supplied template, which reads its URL from its own preference. */
    public static final String CUSTOM = "custom";

    public static final String DEFAULT_ENGINE = "duckduckgo";

    /** One engine: a stable id, a display name, and the URL template it searches with. */
    public static final class Engine {
        public final String id;
        public final String label;
        public final String template;

        Engine(@NonNull String id, @NonNull String label, @NonNull String template) {
            this.id = id;
            this.label = label;
            this.template = template;
        }
    }

    private static final Engine[] ENGINES = {
        new Engine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
        new Engine("google", "Google", "https://www.google.com/search?q=%s"),
        new Engine("bing", "Bing", "https://www.bing.com/search?q=%s"),
        new Engine("brave", "Brave Search", "https://search.brave.com/search?q=%s"),
        new Engine("startpage", "Startpage", "https://www.startpage.com/sp/search?query=%s"),
        new Engine("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s"),
        new Engine("wikipedia", "Wikipedia",
            "https://en.wikipedia.org/w/index.php?search=%s"),
        new Engine("youtube", "YouTube", "https://www.youtube.com/results?search_query=%s"),
    };

    private LauncherWebSearchEngines() {
    }

    /**
     * The engine {@code id} names, with the custom template folded in.
     *
     * <p>A custom entry whose template carries no {@code %s} would silently search for nothing,
     * so it falls back to the default engine rather than opening a URL the user did not ask for.
     */
    @NonNull
    public static Engine resolve(@Nullable String id, @Nullable String customTemplate) {
        if (CUSTOM.equals(id)) {
            String template = customTemplate == null ? "" : customTemplate.trim();
            if (template.contains("%s"))
                return new Engine(CUSTOM, hostLabel(template), template);
            return byId(DEFAULT_ENGINE);
        }
        return byId(id);
    }

    @NonNull
    private static Engine byId(@Nullable String id) {
        for (Engine engine : ENGINES) {
            if (engine.id.equals(id)) return engine;
        }
        for (Engine engine : ENGINES) {
            if (engine.id.equals(DEFAULT_ENGINE)) return engine;
        }
        return ENGINES[0];
    }

    /** The custom engine names itself after its host, so the palette row can still say where. */
    @NonNull
    private static String hostLabel(@NonNull String template) {
        String host = LauncherWebLinks.hostOf(template);
        return host == null ? "Custom search" : host;
    }

    /** The URL {@code engine} searches {@code query} with, or null for an empty query. */
    @Nullable
    public static String searchUrl(@NonNull Engine engine, @NonNull String query) {
        String trimmed = query.trim();
        if (trimmed.isEmpty()) return null;
        return engine.template.replace("%s", encode(trimmed));
    }

    @NonNull
    private static String encode(@NonNull String query) {
        try {
            return URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is guaranteed by the platform; this branch exists only for the checked type.
            return query.replace(" ", "+");
        }
    }
}
