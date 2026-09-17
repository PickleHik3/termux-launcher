package com.termux.app.theme.templates;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.MaterialTerminalColorScheme;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The app's one way in: templates, the applier they run through, and the palette on disk.
 *
 * <p>A colour refresh hands its finished palette to {@link #exportPaletteAndRunPassAsync}, which
 * claims the pass on the caller's thread and then, on the one background thread this class owns,
 * writes the palette files and runs that pass. Settings has no palette of its own — a preference
 * screen is a long way from the terminal — so turning a template on or off reads the last exported
 * palette back off disk and applies from there, on that same thread, which is the only reason it can
 * trust what it finds there.
 */
public final class ThemeTemplates {

    private static final String LOG_TAG = "ThemeTemplates";

    /** Templates shipped with the app. */
    private static final String ASSET_ROOT = "theme-templates";

    /** Where a built-in is unpacked so its hooks can be run. */
    private static final String EXTRACT_DIR_PATH =
        TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/libexec/termux-launcher/theme-templates";

    /** Where the user keeps their own templates, and where the applied list lives beside them. */
    private static final String USER_DIR_PATH =
        TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/theme-templates";

    private static final String APPLIED_FILE_PATH = USER_DIR_PATH + "/.applied";

    private static final String PALETTE_FILE_PATH =
        TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/material-colors.properties";

    /** The two mode files beside it, written by the same export. Either may not exist yet. */
    private static final String DARK_PALETTE_FILE_PATH =
        TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/material-colors-dark.properties";

    private static final String LIGHT_PALETTE_FILE_PATH =
        TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/material-colors-light.properties";

    private static final ThemeTemplateLog LOG = message -> Logger.logWarn(LOG_TAG, message);

    /**
     * Every palette write and every template pass, on one thread, in the order they were scheduled.
     *
     * <p>They used to be split — an executor in the activity's session client, another in
     * {@code TermuxApplication}, and a third here for settings — so two refreshes could write the
     * same two files at once and leave the stale one on disk, and a settings toggle could read those
     * files while they were being rewritten. The applier orders the passes themselves, but nothing
     * ordered the writes underneath them. One thread does both.
     */
    private static final ExecutorService PALETTE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "theme-templates");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        return thread;
    });

    private static ThemeTemplateApplier sApplier;

    private ThemeTemplates() {
    }

    private static synchronized ThemeTemplateApplier applier(@NonNull Context context) {
        if (sApplier == null) {
            Context application = context.getApplicationContext();
            ThemeTemplateLoader loader = new ThemeTemplateLoader(
                new AssetThemeTemplateSource(application.getAssets(), ASSET_ROOT, new File(EXTRACT_DIR_PATH)),
                new DirectoryThemeTemplateSource(new File(USER_DIR_PATH)),
                new ThemeTemplatePaths(TermuxConstants.TERMUX_HOME_DIR_PATH, System.getenv()),
                LOG);
            sApplier = new ThemeTemplateApplier(loader, new AppShellThemeTemplateHooks(application),
                new File(APPLIED_FILE_PATH), LOG);
        }
        return sApplier;
    }

    /** Claim the next pass, before the palette is handed to the writer thread. */
    public static long schedulePass(@NonNull Context context) {
        return applier(context).schedule();
    }

    /**
     * Run a pass claimed by {@link #schedulePass(Context)} with the palettes that were just exported.
     *
     * <p>Runs on the calling thread, which must not be the UI thread.
     */
    public static void runPass(@NonNull Context context, @NonNull PaletteSet palettes, long pass) {
        applier(context).apply(palettes, enabledIds(context), pass);
    }

    /**
     * The one way to publish a palette: export {@code material-colors.properties} / {@code .sh} and
     * run a template pass over the same values, off the caller's thread.
     *
     * <p>The pass is claimed here, synchronously, so it is ordered by when the palette was built
     * rather than by when the background thread got to it — a burst of refreshes then leaves the
     * newest one to finish and the rest to stop between templates.
     */
    public static void exportPaletteAndRunPassAsync(@NonNull Context context,
                                                    @NonNull Properties palette) {
        exportPaletteAndRunPassAsync(context, PaletteSet.of(palette));
    }

    /** As {@link #exportPaletteAndRunPassAsync(Context, Properties)}, with both modes derived. */
    public static void exportPaletteAndRunPassAsync(@NonNull Context context,
                                                    @NonNull PaletteSet palettes) {
        exportPaletteAndRunPassAsync(context, () -> palettes, null);
    }

    /**
     * As {@link #exportPaletteAndRunPassAsync(Context, PaletteSet)}, for a caller whose palettes can
     * only be derived off the UI thread. {@code paletteSource} runs on the background thread and may
     * return {@code null} to call the whole thing off; it must not touch theme attributes or
     * resources, which are not safe to resolve there.
     */
    public static void exportPaletteAndRunPassAsync(@NonNull Context context,
                                                    @NonNull Callable<PaletteSet> paletteSource) {
        exportPaletteAndRunPassAsync(context, paletteSource, null);
    }

    /**
     * As above, with something to do once the palette files and the template pass have landed.
     *
     * <p>{@code afterPass} runs on this class's one background thread, after everything the pass
     * writes is on disk — which is the order D2 asks for: a session told the colours moved must not
     * beat the files it is going to re-read to the punch.
     */
    public static void exportPaletteAndRunPassAsync(@NonNull Context context,
                                                    @NonNull Callable<PaletteSet> paletteSource,
                                                    @Nullable AfterPass afterPass) {
        Context application = context.getApplicationContext();
        long pass = schedulePass(application);
        PALETTE_EXECUTOR.execute(() -> {
            try {
                PaletteSet palettes = paletteSource.call();
                if (palettes == null || palettes.active() == null) return;
                MaterialTerminalColorScheme.writeMaterialColorFiles(palettes);
                runPass(application, palettes, pass);
                if (afterPass != null) afterPass.run(palettes);
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error exporting the palette", e);
            }
        });
    }

    /** What {@link #exportPaletteAndRunPassAsync(Context, Callable, AfterPass)} calls at the end. */
    public interface AfterPass {

        /** Called on the export thread, with everything the pass wrote already on disk. */
        void run(@NonNull PaletteSet palettes);
    }

    /**
     * Apply from the palette already on disk, off the caller's thread.
     *
     * <p>This is what a settings toggle uses: it changes which templates should be applied, not what
     * they should say, so the last exported palette is the right one to render.
     */
    public static void applyFromDiskAsync(@NonNull Context context) {
        Context application = context.getApplicationContext();
        ThemeTemplateApplier applier = applier(application);
        long pass = applier.schedule();
        Set<String> enabled = enabledIds(application);
        PALETTE_EXECUTOR.execute(() -> {
            try {
                Properties palette = exportedPalette(PALETTE_FILE_PATH);
                if (palette == null) {
                    Logger.logWarn(LOG_TAG, "No exported palette to render templates from yet");
                    return;
                }
                applier.apply(PaletteSet.of(palette, exportedPalette(DARK_PALETTE_FILE_PATH),
                    exportedPalette(LIGHT_PALETTE_FILE_PATH)), enabled, pass);
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Error applying theme templates", e);
            }
        });
    }

    /**
     * Unpack a built-in template's directory, off the caller's thread.
     *
     * <p>Settings hands the user a command that runs a script out of that directory. The directory
     * exists once the template has been through a pass, but the user may copy the command before
     * the pass has run — or with no palette exported yet, in which case it never will — so the
     * files are put in place here, on the same thread the passes use.
     */
    public static void unpackAsync(@NonNull ThemeTemplate template) {
        PALETTE_EXECUTOR.execute(() -> {
            try {
                template.directory();
            } catch (IOException e) {
                Logger.logWarn(LOG_TAG, "Theme template \"" + template.id + "\" cannot be unpacked: "
                    + e.getMessage());
            }
        });
    }

    /** Every built-in template, for the settings list. */
    @NonNull
    public static List<ThemeTemplate> builtInTemplates(@NonNull Context context) {
        try {
            return applier(context).loader().builtInTemplates();
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error reading built-in theme templates", e);
            return Collections.emptyList();
        }
    }

    @NonNull
    private static Set<String> enabledIds(@NonNull Context context) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        return preferences == null ? Collections.emptySet() : preferences.getThemeTemplatesEnabled();
    }

    /** An exported palette file, or {@code null} if the launcher has never written that one. */
    @Nullable
    private static Properties exportedPalette(@NonNull String path) {
        File file = new File(path);
        if (!file.isFile()) return null;
        Properties palette = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            palette.load(in);
        } catch (IOException e) {
            Logger.logWarn(LOG_TAG, "Cannot read " + path + ": " + e.getMessage());
            return null;
        }
        return palette;
    }
}
