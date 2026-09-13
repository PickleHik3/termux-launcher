package com.termux.app.theme.templates;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Reads the manifests of both template sources and hands out finished {@link ThemeTemplate}s.
 *
 * <p>A built-in is used only while the user has it switched on; a template the user dropped into
 * {@code ~/.termux/theme-templates} is used because it is there. An id present in both is the
 * user's — that is how someone takes a shipped template and changes it without losing the name it
 * is known by.
 */
public final class ThemeTemplateLoader {

    private final ThemeTemplateSource mBuiltIns;

    private final ThemeTemplateSource mUserTemplates;

    private final ThemeTemplatePaths mPaths;

    private final ThemeTemplateLog mLog;

    public ThemeTemplateLoader(ThemeTemplateSource builtIns, ThemeTemplateSource userTemplates,
                               ThemeTemplatePaths paths, ThemeTemplateLog log) {
        mBuiltIns = builtIns;
        mUserTemplates = userTemplates;
        mPaths = paths;
        mLog = log == null ? ThemeTemplateLog.NONE : log;
    }

    /** Every built-in, readable or not, for the settings list. */
    public List<ThemeTemplate> builtInTemplates() {
        List<ThemeTemplate> templates = new ArrayList<>();
        for (String id : mBuiltIns.ids()) {
            ThemeTemplate template = read(id, mBuiltIns);
            if (template != null) templates.add(template);
        }
        return templates;
    }

    /**
     * The templates a pass should render: every user template, plus the built-ins in
     * {@code enabledIds} that the user has not replaced with one of their own.
     */
    public List<ThemeTemplate> active(Collection<String> enabledIds) {
        List<ThemeTemplate> templates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String id : mUserTemplates.ids()) {
            ThemeTemplate template = read(id, mUserTemplates);
            if (template != null && seen.add(id)) templates.add(template);
        }
        if (enabledIds != null) {
            for (String id : mBuiltIns.ids()) {
                if (!enabledIds.contains(id) || seen.contains(id)) continue;
                ThemeTemplate template = read(id, mBuiltIns);
                if (template != null && seen.add(id)) templates.add(template);
            }
        }
        return templates;
    }

    /** The template with this id whatever its state, for undoing one that was just switched off. */
    public ThemeTemplate find(String id) {
        if (id == null || id.isEmpty()) return null;
        if (mUserTemplates.has(id)) return read(id, mUserTemplates);
        if (mBuiltIns.has(id)) return read(id, mBuiltIns);
        return null;
    }

    private ThemeTemplate read(String id, ThemeTemplateSource source) {
        Properties manifest;
        try {
            manifest = source.manifest(id);
        } catch (IOException e) {
            mLog.warn("Theme template \"" + id + "\" has an unreadable manifest: " + e.getMessage());
            return null;
        }
        String input = value(manifest, ThemeTemplate.KEY_INPUT);
        String output = value(manifest, ThemeTemplate.KEY_OUTPUT);
        if (input.isEmpty() || output.isEmpty()) {
            mLog.warn("Theme template \"" + id + "\" names no input or output file");
            return null;
        }
        String name = value(manifest, ThemeTemplate.KEY_NAME);
        return new ThemeTemplate(id,
            name.isEmpty() ? id : name,
            value(manifest, ThemeTemplate.KEY_SUMMARY),
            input,
            mPaths.expand(output),
            value(manifest, ThemeTemplate.KEY_POST_HOOK),
            value(manifest, ThemeTemplate.KEY_UNDO_HOOK),
            source);
    }

    private static String value(Properties manifest, String key) {
        String value = manifest.getProperty(key);
        return value == null ? "" : value.trim();
    }
}
