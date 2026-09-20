package com.termux.app.theme.templates;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.runner.app.AppShell;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Runs a template's hook as {@code bash <dir>/<hook>} in the Termux environment.
 *
 * <p>The hook is somebody's shell script against somebody else's tool, so it is held to a clock:
 * {@link AppShell} has no timeout of its own, so the process is started in the background and waited
 * for here, and one that is still running after thirty seconds is killed. Either way the pass carries
 * on — a tool that will not take the new colours is not allowed to stop the rest of them.
 */
public final class AppShellThemeTemplateHooks implements ThemeTemplateApplier.HookRunner {

    private static final String LOG_TAG = "ThemeTemplates";

    private static final long TIMEOUT_MS = 30_000L;

    private final Context mContext;

    public AppShellThemeTemplateHooks(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    @Override
    public boolean run(ThemeTemplate template, File directory, String hook, String mode) {
        File script = new File(directory, hook);
        if (!script.isFile()) {
            Logger.logWarn(LOG_TAG, "Theme template \"" + template.id + "\" has no hook at " + script);
            return false;
        }
        HashMap<String, String> environment = new HashMap<>();
        environment.put("TERMUX_THEME_ID", template.id);
        environment.put("TERMUX_THEME_DIR", directory.getAbsolutePath());
        environment.put("TERMUX_THEME_OUTPUT", template.output);
        environment.put("TERMUX_THEME_MODE", mode);
        ExecutionCommand command = new ExecutionCommand(-1,
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash",
            new String[]{script.getAbsolutePath()}, null, directory.getAbsolutePath(),
            ExecutionCommand.Runner.APP_SHELL.getName(), false);
        command.commandLabel = "Theme template " + template.id + " " + hook;
        command.backgroundCustomLogLevel = Logger.LOG_LEVEL_OFF;
        CountDownLatch finished = new CountDownLatch(1);
        AppShell shell = AppShell.execute(mContext, command, appShell -> finished.countDown(),
            new TermuxShellEnvironment(), environment, false);
        if (shell == null) {
            Logger.logWarn(LOG_TAG, "Theme template \"" + template.id + "\" could not start " + hook);
            return false;
        }
        try {
            if (!finished.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                Logger.logWarn(LOG_TAG, "Theme template \"" + template.id + "\" hook " + hook
                    + " took longer than " + (TIMEOUT_MS / 1000) + "s and was stopped");
                shell.killIfExecuting(mContext, false);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shell.killIfExecuting(mContext, false);
            return false;
        }
        Integer exitCode = command.resultData.exitCode;
        if (exitCode == null || exitCode != 0) {
            Logger.logWarn(LOG_TAG, "Theme template \"" + template.id + "\" hook " + hook
                + " exited with " + exitCode + ": " + command.resultData.stderr);
            return false;
        }
        return true;
    }
}
