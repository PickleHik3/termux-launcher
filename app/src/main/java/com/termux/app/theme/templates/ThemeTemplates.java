package com.termux.app.theme.templates;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The app's one way in: templates, the applier they run through, and the palette on disk.
 *
 * <p>A colour refresh already builds the palette and hands it to a writer thread, so that path
 * claims a pass before the hand-off and runs it after the palette files are written. Settings has no
 * palette of its own — a preference screen is a long way from the terminal — so turning a template on
 * or off reads the last exported palette back off disk and applies from there.
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

    private static final ThemeTemplateLog LOG = message -> Logger.logWarn(LOG_TAG, message);

    /** Settings changes get their own thread; colour refreshes stay on the palette writer's. */
    private static final ExecutorService SETTINGS_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "theme-templates");
        thread.setPriority(Thread.MIN_PRIORITY);
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
     * Run a pass claimed by {@link #schedulePass(Context)} with the palette that was just exported.
     *
     * <p>Runs on the calling thread, which must not be the UI thread.
     */
    public static void runPass(@NonNull Context context, @NonNull Properties palette, long pass) {
        applier(context).apply(palette, enabledIds(context), pass);
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
        SETTINGS_EXECUTOR.execute(() -> {
            try {
                Properties palette = exportedPalette();
                if (palette == null) {
                    Logger.logWarn(LOG_TAG, "No exported palette to render templates from yet");
                    return;
                }
                applier.apply(palette, enabled, pass);
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
        SETTINGS_EXECUTOR.execute(() -> {
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

    /** The palette the launcher last exported, or {@code null} if it has never written one. */
    @Nullable
    private static Properties exportedPalette() {
        File file = new File(PALETTE_FILE_PATH);
        if (!file.isFile()) return null;
        Properties palette = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            palette.load(in);
        } catch (IOException e) {
            Logger.logWarn(LOG_TAG, "Cannot read " + PALETTE_FILE_PATH + ": " + e.getMessage());
            return null;
        }
        return palette;
    }
}
