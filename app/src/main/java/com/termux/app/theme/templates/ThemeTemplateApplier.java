package com.termux.app.theme.templates;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
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
 * forget but not the file it had left behind. A template whose hook failed is recorded too — the
 * file is on disk and must still be undone — but marked, so the hook is asked again next pass
 * instead of the failure standing as done.
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

    /** Passes arrive from two threads — the palette writer and the settings screen — one at a time. */
    private final Object mPassLock = new Object();

    /** Ledger column marking a template whose last hook did not finish. */
    private static final String HOOK_PENDING = "hook-pending";

    /** What the ledger says: where each applied template wrote, and whose hook is still owed. */
    private static final class Ledger {
        final Map<String, String> outputs = new LinkedHashMap<>();
        final Set<String> hookPending = new LinkedHashSet<>();
    }

    /** What one template's turn in the pass came to. */
    private static final class Outcome {
        final String output;
        final boolean hookPending;

        Outcome(String output, boolean hookPending) {
            this.output = output;
            this.hookPending = hookPending;
        }
    }

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
        apply(PaletteSet.of(palette), enabledIds, schedule());
    }

    /** As above, for a caller with only the active palette — every mode renders from it. */
    public void apply(Properties palette, Collection<String> enabledIds, long pass) {
        apply(PaletteSet.of(palette), enabledIds, pass);
    }

    /**
     * Run the pass claimed by {@code pass}, stopping early if a newer one has been scheduled.
     *
     * <p>Passes run one at a time: both callers read and rewrite the same ledger and run the same
     * hooks, and a pass that waited its turn finds out it was overtaken before it touches anything.
     */
    public void apply(PaletteSet palettes, Collection<String> enabledIds, long pass) {
        synchronized (mPassLock) {
            applyLocked(palettes, enabledIds, pass);
        }
    }

    private void applyLocked(PaletteSet palettes, Collection<String> enabledIds, long pass) {
        Ledger ledger = readLedger();
        Map<String, String> applied = ledger.outputs;
        Map<String, String> next = new LinkedHashMap<>(applied);
        Set<String> pending = new LinkedHashSet<>(ledger.hookPending);
        // The hooks are told the mode the user is actually in, not the modes the templates carry.
        String mode = ThemeTemplateRenderer.modeOf(palettes.active());
        List<ThemeTemplate> active = mLoader.active(enabledIds);
        Set<String> activeIds = new LinkedHashSet<>();
        boolean superseded = false;
        for (ThemeTemplate template : active) {
            activeIds.add(template.id);
            if (isSuperseded(pass)) {
                superseded = true;
                break;
            }
            boolean hookDue = !applied.containsKey(template.id) || pending.contains(template.id);
            Outcome outcome = applyOne(template, palettes, mode, hookDue);
            if (outcome == null) continue;
            next.put(template.id, outcome.output);
            if (outcome.hookPending) pending.add(template.id);
            else pending.remove(template.id);
        }
        if (!superseded) {
            for (Map.Entry<String, String> entry : new ArrayList<>(applied.entrySet())) {
                if (activeIds.contains(entry.getKey())) continue;
                if (isSuperseded(pass)) break;
                undo(entry.getKey(), entry.getValue(), mode);
                next.remove(entry.getKey());
                pending.remove(entry.getKey());
            }
        }
        pending.retainAll(next.keySet());
        if (!next.equals(applied) || !pending.equals(ledger.hookPending)) writeLedger(next, pending);
    }

    /** @return the path written and whether its hook is still owed, or {@code null} when skipped. */
    private Outcome applyOne(ThemeTemplate template, PaletteSet palettes, String mode, boolean hookDue) {
        // Unpacked before anything else: the hooks run out of this directory, and so does the setup
        // command Settings hands the user, which must point at files that exist by the time they
        // paste it.
        File directory = null;
        try {
            directory = template.directory();
        } catch (IOException e) {
            mLog.warn("Theme template \"" + template.id + "\" cannot be unpacked: " + e.getMessage());
        }
        String source;
        try {
            source = template.readInput();
        } catch (IOException e) {
            mLog.warn("Theme template \"" + template.id + "\" cannot be read: " + e.getMessage());
            return null;
        }
        ThemeTemplateRenderer.Result rendered = ThemeTemplateRenderer.render(source, palettes);
        if (!rendered.isSuccess()) {
            mLog.warn("Theme template \"" + template.id + "\" skipped: " + rendered.failure);
            return null;
        }
        File output = new File(template.output);
        byte[] bytes = rendered.text.getBytes(StandardCharsets.UTF_8);
        boolean changed = !ThemeTemplatePaths.sameOnDisk(output, bytes);
        if (changed && !write(output, bytes)) return null;
        boolean hookPending = false;
        if (template.hasPostHook() && (changed || hookDue))
            hookPending = !runHook(template, directory, template.postHook, mode);
        return new Outcome(template.output, hookPending);
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
            runHook(template, directory, template.undoHook, mode);
            return;
        }
        // Nothing left to ask: the template is gone, or never had an undo of its own. All the app
        // can honestly promise is that the file it wrote goes away.
        if (recordedOutput == null || recordedOutput.isEmpty()) return;
        File output = new File(recordedOutput);
        if (output.isFile() && !output.delete())
            mLog.warn("Theme template \"" + id + "\" left " + recordedOutput + " behind");
    }

    /** @return whether the hook ran and finished; the failure has already been logged when not. */
    private boolean runHook(ThemeTemplate template, File directory, String hook, String mode) {
        if (directory == null) {
            mLog.warn("Theme template \"" + template.id + "\" hook " + hook + " has no directory to run from");
            return false;
        }
        if (mHooks == null) return true;
        boolean ok;
        try {
            ok = mHooks.run(template, directory, hook, mode);
        } catch (RuntimeException e) {
            ok = false;
            mLog.warn("Theme template \"" + template.id + "\" hook " + hook + " failed: " + e.getMessage());
        }
        // A tool that refuses the new colours is not a reason to leave the rest of them stale.
        if (!ok) mLog.warn("Theme template \"" + template.id + "\" hook " + hook + " did not succeed");
        return ok;
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

    /** The ids applied so far and the file each one wrote, in the order they were applied. */
    public Map<String, String> readApplied() {
        return readLedger().outputs;
    }

    /** The applied templates whose hook is still owed, for the next pass to run again. */
    public Set<String> readHookPending() {
        return readLedger().hookPending;
    }

    private Ledger readLedger() {
        Ledger ledger = new Ledger();
        if (mAppliedFile == null || !mAppliedFile.isFile()) return ledger;
        List<String> lines;
        try {
            lines = Files.readAllLines(mAppliedFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            mLog.warn("Cannot read " + mAppliedFile + ": " + e.getMessage());
            return ledger;
        }
        for (String line : lines) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] columns = line.split("\t", -1);
            if (columns.length < 2 || columns[0].isEmpty()) continue;
            ledger.outputs.put(columns[0], columns[1]);
            if (columns.length > 2 && HOOK_PENDING.equals(columns[2])) ledger.hookPending.add(columns[0]);
        }
        return ledger;
    }

    private void writeLedger(Map<String, String> applied, Set<String> hookPending) {
        if (mAppliedFile == null) return;
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> entry : applied.entrySet()) {
            text.append(entry.getKey()).append('\t').append(entry.getValue());
            if (hookPending.contains(entry.getKey())) text.append('\t').append(HOOK_PENDING);
            text.append('\n');
        }
        write(mAppliedFile, text.toString().getBytes(StandardCharsets.UTF_8));
    }
}
