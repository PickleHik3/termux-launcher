package com.termux.app.theme.templates;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A place template directories come from.
 *
 * <p>Two of these exist in the app — the built-ins packed into the APK and the user's own
 * {@code ~/.termux/theme-templates} — and they differ in one way that matters: an asset is not a
 * file, so a hook cannot be run out of it until it has been put on disk. {@link #directory(String)}
 * is where that happens, which is also what lets a test point the whole machine at a plain
 * directory.
 */
public interface ThemeTemplateSource {

    /** Whether these templates are shipped with the app, and so need a settings toggle. */
    boolean isBuiltIn();

    /** The template ids this source offers, sorted. */
    List<String> ids();

    boolean has(String id);

    /** The parsed {@code template.properties}. */
    java.util.Properties manifest(String id) throws IOException;

    /** A UTF-8 file inside the template directory. */
    String readText(String id, String relativePath) throws IOException;

    /** The template's directory on disk, extracting it first if this source is not one. */
    File directory(String id) throws IOException;
}
