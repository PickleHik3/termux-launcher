package com.termux.app.theme.templates;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One pass: render every active template, write what changed, and undo what the user turned off.
 *
 * <p>The pass is deliberately quiet when nothing moved. A rendered file is written only when its
 * bytes differ, and a hook runs only when its file changed or the template had never been applied —
 * so a wallpaper refresh that lands on the same palette costs a few reads and nothing else, and
 * tools are not restarted under the user for no reason.
 *
 * <p>Which templates were applied, and where each one wrote, is kept in a small tsv next to them.
 * Without it a template switched off could not be undone: the app would know the id it had to
 * forget but not the file it had left behind.
 *
 * <p>Passes are ordered by {@link #schedule()}. Colour refreshes arrive in bursts — a wallpaper
 * change, then the contrast that follows it — and a pass that has been overtaken stops between
 * templates rather than finishing work the next one is about to redo.
 */
public final class ThemeTemplateApplier {

    /** Runs a template's hook. Production goes through {@code bash}; tests record the call. */
    public interface HookRunner {

        /**
         * @param template  the template the hook belongs to.
         * @param directory the template's directory on disk, which the hook runs out of.
         * @param hook      the hook's path relative to that directory.
         * @param mode      {@code dark} or {@code light}, the palette's own.
         * @return whether the hook finished successfully.
         */
        boolean run(ThemeTemplate template, File directory, String hook, String mode);
    }

    private final ThemeTemplateLoader mLoader;

    private final HookRunner mHooks;

    private final File mAppliedFile;

    private final ThemeTemplateLog mLog;

    private final AtomicLong mLatestPass = new AtomicLong();

    public ThemeTemplateApplier(ThemeTemplateLoader loader, HookRunner hooks, File appliedFile,
                                ThemeTemplateLog log) {
        mLoader = loader;
        mHooks = hooks;
        mAppliedFile = appliedFile;
        mLog = log == null ? ThemeTemplateLog.NONE : log;
    }

    /** The templates this applier works from, for the settings list. */
    public ThemeTemplateLoader loader() {
        return mLoader;
    }

    /**
     * Claim the next pass, superseding every pass already in flight.
     *
     * <p>Called on whatever thread schedules the work — the point is that a pass queued behind a
     * running one is known about before the running one reaches its next template.
     */
    public long schedule() {
        return mLatestPass.incrementAndGet();
    }

    /** Schedule a pass and run it here. Never on the UI thread: hooks are other people's shells. */
    public void apply(Properties palette, Collection<String> enabledIds) {
        apply(palette, enabledIds, schedule());
    }

    /** Run the pass claimed by {@code pass}, stopping early if a newer one has been scheduled. */
    public void apply(Properties palette, Collection<String> enabledIds, long pass) {
        Map<String, String> applied = readApplied();
        Map<String, String> next = new LinkedHashMap<>(applied);
        String mode = ThemeTemplateRenderer.modeOf(palette);
        List<ThemeTemplate> active = mLoader.active(enabledIds);
        Set<String> activeIds = new LinkedHashSet<>();
        boolean superseded = false;
        for (ThemeTemplate template : active) {
            activeIds.add(template.id);
            if (isSuperseded(pass)) {
                superseded = true;
                break;
            }
            String output = applyOne(template, palette, mode, !applied.containsKey(template.id));
            if (output != null) next.put(template.id, output);
        }
        if (!superseded) {
            for (Map.Entry<String, String> entry : new ArrayList<>(applied.entrySet())) {
                if (activeIds.contains(entry.getKey())) continue;
                if (isSuperseded(pass)) break;
                undo(entry.getKey(), entry.getValue(), mode);
                next.remove(entry.getKey());
            }
        }
        if (!next.equals(applied)) writeApplied(next);
    }

    /** @return the path written, or {@code null} when the template was skipped. */
    private String applyOne(ThemeTemplate template, Properties palette, String mode, boolean isNew) {
        String source;
        try {
            source = template.readInput();
        } catch (IOException e) {
            mLog.warn("Theme template \"" + template.id + "\" cannot be read: " + e.getMessage());
            return null;
        }
        ThemeTemplateRenderer.Result rendered = ThemeTemplateRenderer.render(source, palette);
        if (!rendered.isSuccess()) {
            mLog.warn("Theme template \"" + template.id + "\" skipped: " + rendered.failure);
            return null;
        }
        File output = new File(template.output);
        byte[] bytes = rendered.text.getBytes(StandardCharsets.UTF_8);
        boolean changed = !sameOnDisk(output, bytes);
        if (changed && !write(output, bytes)) return null;
        if (template.hasPostHook() && (changed || isNew)) runHook(template, template.postHook, mode);
        return template.output;
    }

    private void undo(String id, String recordedOutput, String mode) {
        ThemeTemplate template = mLoader.find(id);
        File directory = null;
        if (template != null) {
            try {
                directory = template.directory();
            } catch (IOException e) {
                mLog.warn("Theme template \"" + id + "\" cannot be unpacked to undo: " + e.getMessage());
            }
        }
        if (template != null && template.hasUndoHook() && directory != null && directory.isDirectory()) {
            runHook(template, template.undoHook, mode);
            return;
        }
        // Nothing left to ask: the template is gone, or never had an undo of its own. All the app
        // can honestly promise is that the file it wrote goes away.
        if (recordedOutput == null || recordedOutput.isEmpty()) return;
        File output = new File(recordedOutput);
        if (output.isFile() && !output.delete())
            mLog.warn("Theme template \"" + id + "\" left " + recordedOutput + " behind");
    }

    private void runHook(ThemeTemplate template, String hook, String mode) {
        File directory;
        try {
            directory = template.directory();
        } catch (IOException e) {
            mLog.warn("Theme template \"" + template.id + "\" cannot be unpacked: " + e.getMessage());
            return;
        }
        if (mHooks == null) return;
        boolean ok;
        try {
            ok = mHooks.run(template, directory, hook, mode);
        } catch (RuntimeException e) {
            ok = false;
            mLog.warn("Theme template \"" + template.id + "\" hook " + hook + " failed: " + e.getMessage());
        }
        // A tool that refuses the new colours is not a reason to leave the rest of them stale.
        if (!ok) mLog.warn("Theme template \"" + template.id + "\" hook " + hook + " did not succeed");
    }

    private boolean isSuperseded(long pass) {
        return mLatestPass.get() != pass;
    }

    private boolean write(File output, byte[] bytes) {
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            mLog.warn("Cannot create " + parent);
            return false;
        }
        // Opened rather than replaced, so an output the user has symlinked somewhere keeps working.
        try (FileOutputStream out = new FileOutputStream(output)) {
            out.write(bytes);
        } catch (IOException e) {
            mLog.warn("Cannot write " + output + ": " + e.getMessage());
            return false;
        }
        return true;
    }

    private static boolean sameOnDisk(File file, byte[] wanted) {
        if (!file.isFile() || file.length() != wanted.length) return false;
        try {
            return Arrays.equals(Files.readAllBytes(file.toPath()), wanted);
        } catch (IOException e) {
            return false;
        }
    }

    /** The ids applied so far and the file each one wrote, in the order they were applied. */
    public Map<String, String> readApplied() {
        Map<String, String> applied = new LinkedHashMap<>();
        if (mAppliedFile == null || !mAppliedFile.isFile()) return applied;
        List<String> lines;
        try {
            lines = Files.readAllLines(mAppliedFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            mLog.warn("Cannot read " + mAppliedFile + ": " + e.getMessage());
            return applied;
        }
        for (String line : lines) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            applied.put(line.substring(0, tab), line.substring(tab + 1));
        }
        return applied;
    }

    private void writeApplied(Map<String, String> applied) {
        if (mAppliedFile == null) return;
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> entry : applied.entrySet())
            text.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
        write(mAppliedFile, text.toString().getBytes(StandardCharsets.UTF_8));
    }
}
