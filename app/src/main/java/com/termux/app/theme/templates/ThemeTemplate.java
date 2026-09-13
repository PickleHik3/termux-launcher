package com.termux.app.theme.templates;

import java.io.File;
import java.io.IOException;

/**
 * One template: a manifest, the file to render, and the hooks that wire the result into a tool.
 *
 * <p>{@code name} and {@code summary} are product copy written by whoever wrote the template — they
 * are what the settings list shows — and the rest is plumbing. {@code output} arrives from the
 * manifest already expanded to an absolute path.
 */
public final class ThemeTemplate {

    public static final String MANIFEST_NAME = "template.properties";

    public static final String KEY_NAME = "name";
    public static final String KEY_SUMMARY = "summary";
    public static final String KEY_INPUT = "input";
    public static final String KEY_OUTPUT = "output";
    public static final String KEY_POST_HOOK = "post_hook";
    public static final String KEY_UNDO_HOOK = "undo_hook";
    public static final String KEY_SETUP_HOOK = "setup_hook";

    public final String id;
    public final String name;
    public final String summary;
    public final String input;
    public final String output;
    public final String postHook;
    public final String undoHook;

    /**
     * The one thing the app will not do for the user: add a line to their shell startup.
     *
     * <p>Some tools are only wired in by an init line in a shell rc, and a hook writing into
     * {@code ~/.bashrc} behind someone's back is not a trade the app makes. A template that needs one
     * names a script here, and the user is offered the command to run themselves.
     */
    public final String setupHook;

    private final ThemeTemplateSource mSource;

    ThemeTemplate(String id, String name, String summary, String input, String output,
                  String postHook, String undoHook, String setupHook, ThemeTemplateSource source) {
        this.id = id;
        this.name = name;
        this.summary = summary;
        this.input = input;
        this.output = output;
        this.postHook = postHook;
        this.undoHook = undoHook;
        this.setupHook = setupHook;
        this.mSource = source;
    }

    /** Whether this one is shipped with the app, and so is off until the user turns it on. */
    public boolean isBuiltIn() {
        return mSource.isBuiltIn();
    }

    /** The template text, straight from the source. */
    public String readInput() throws IOException {
        return mSource.readText(id, input);
    }

    /** The directory the hooks run out of, extracted from the APK first if that is where it lives. */
    public File directory() throws IOException {
        return mSource.directory(id);
    }

    /** Where that directory is, without unpacking anything — for naming a path to the user. */
    public File plannedDirectory() {
        return mSource.plannedDirectory(id);
    }

    /** The command that finishes the setup, for the user to run in their own shell. */
    public String setupCommand() {
        return "bash \"" + new File(plannedDirectory(), setupHook).getAbsolutePath() + "\"";
    }

    public boolean hasPostHook() {
        return postHook != null && !postHook.isEmpty();
    }

    public boolean hasUndoHook() {
        return undoHook != null && !undoHook.isEmpty();
    }

    public boolean hasSetupHook() {
        return setupHook != null && !setupHook.isEmpty();
    }
}
