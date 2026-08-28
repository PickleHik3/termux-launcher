package com.termux.privileged;

import android.content.Context;
import android.util.Log;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

/**
 * Shell-based backend for privileged operations
 * 
 * This implementation uses shell/rish commands as a fallback when Shizuku
 * is not available. It provides basic privileged operations through shell
 * command execution.
 */
public class ShellBackend implements PrivilegedBackend {
    private static final String TAG = "ShellBackend";
    private static final long COMMAND_TIMEOUT_SECONDS = 30;
    /** The child is already gone or reaped by the time a pump is awaited, so this is a backstop. */
    private static final long PUMP_DRAIN_TIMEOUT_MS = 2_000L;
    
    private Context context;
    private boolean isAvailable = false;
    private boolean hasPermission = false;
    private RootMethod rootMethod = RootMethod.NONE;
    
    @Override
    public CompletableFuture<Boolean> initialize(Context context) {
        this.context = context.getApplicationContext();
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.i(TAG, "Initializing shell backend...");
                
                // Check if su is available
                String suCheck = executeRootCommand(RootMethod.SU, List.of("echo", "test"));
                if (isCommandSuccessful(suCheck)) {
                    hasPermission = true;
                    rootMethod = RootMethod.SU;
                    Log.i(TAG, "Root access available via su");
                } else {
                    // Try rish as alternative
                    String rishCheck = executeRootCommand(RootMethod.RISH, List.of("echo", "test"));
                    if (isCommandSuccessful(rishCheck)) {
                        hasPermission = true;
                        rootMethod = RootMethod.RISH;
                        Log.i(TAG, "Root access available via rish");
                    } else {
                        Log.w(TAG, "Neither su nor rish available");
                        hasPermission = false;
                        rootMethod = RootMethod.NONE;
                    }
                }
                
                isAvailable = true;
                return hasPermission;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize shell backend", e);
                isAvailable = false;
                hasPermission = false;
                return false;
            }
        }, PrivilegedExecutors.commands());
    }
    
    @Override
    public boolean isAvailable() {
        return isAvailable;
    }
    
    @Override
    public Type getType() {
        return Type.SHELL;
    }
    
    @Override
    public boolean hasPermission() {
        return hasPermission;
    }
    
    @Override
    public boolean requestPermission(int requestCode) {
        // Shell backend doesn't need explicit permission requests
        // The user needs to have root access configured
        Log.i(TAG, "Shell backend: Permission check not applicable");
        return hasPermission && rootMethod != RootMethod.NONE;
    }
    
    @Override
    public CompletableFuture<List<String>> getInstalledPackages() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<String> packages = new ArrayList<>();
                
                // Get list of installed packages using pm list packages
                String output = executePrivilegedCommand(List.of("pm", "list", "packages"));
                
                if (output != null) {
                    String[] lines = output.split("\n");
                    for (String line : lines) {
                        if (line.startsWith("package:")) {
                            packages.add(line.substring("package:".length()));
                        }
                    }
                }
                
                return packages;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to get installed packages", e);
                return List.of();
            }
        }, PrivilegedExecutors.commands());
    }
    
    @Override
    public CompletableFuture<Boolean> installPackage(String apkPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (apkPath == null || apkPath.trim().isEmpty()) {
                    Log.e(TAG, "Invalid APK path");
                    return false;
                }
                
                // Install APK using pm install
                String output = executePrivilegedCommand(List.of("pm", "install", "-r", apkPath));
                
                if (output != null) {
                    boolean success = output.contains("Success") || output.contains("success");
                    Log.i(TAG, "Install result: " + output);
                    return success;
                }
                
                return false;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to install package: " + apkPath, e);
                return false;
            }
        }, PrivilegedExecutors.commands());
    }
    
    @Override
    public CompletableFuture<Boolean> uninstallPackage(String packageName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (packageName == null || packageName.trim().isEmpty()) {
                    Log.e(TAG, "Invalid package name");
                    return false;
                }
                
                // Uninstall package using pm uninstall
                String output = executePrivilegedCommand(List.of("pm", "uninstall", packageName));
                
                if (output != null) {
                    boolean success = output.contains("Success") || output.contains("success");
                    Log.i(TAG, "Uninstall result: " + output);
                    return success;
                }
                
                return false;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to uninstall package: " + packageName, e);
                return false;
            }
        }, PrivilegedExecutors.commands());
    }
    
    @Override
    public CompletableFuture<Boolean> setComponentEnabled(String packageName, String componentName, boolean enabled) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (packageName == null || componentName == null) {
                    Log.e(TAG, "Invalid package or component name");
                    return false;
                }
                
                // Enable/disable component using pm enable/disable
                String action = enabled ? "enable" : "disable";
                String output = executePrivilegedCommand(List.of("pm", action, packageName + "/" + componentName));
                
                if (output != null) {
                    boolean success = output.contains("Success") || output.contains("success");
                    Log.i(TAG, "Component " + action + " result: " + output);
                    return success;
                }
                
                return false;
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to set component enabled: " + componentName, e);
                return false;
            }
        }, PrivilegedExecutors.commands());
    }
    
    @Override
    public CompletableFuture<String> executeCommand(String command) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (command == null || command.trim().isEmpty()) {
                    return "Invalid command";
                }
                
                List<String> args = List.of("sh", "-c", command);
                if (hasPermission && rootMethod != RootMethod.NONE) {
                    return executeRootCommand(rootMethod, args);
                }
                return executeDirectCommand(args);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to execute command: " + maskSensitive(command), e);
                return "Error: " + e.getMessage();
            }
        }, PrivilegedExecutors.commands());
    }

    @Override
    public boolean isOperationSupported(PrivilegedOperation operation) {
        // Shell backend supports most operations but with text-based limitations
        return operation != null;
    }
    
    @Override
    public String getStatusDescription() {
        return String.format("Shell backend - Available: %s, HasPermission: %s", 
            isAvailable, hasPermission);
    }
    
    @Override
    public void cleanup() {
        // Shell backend doesn't need specific cleanup
        Log.i(TAG, "Shell backend cleaned up");
    }
    
    /**
     * Execute a shell command and return the output
     */
    private String executePrivilegedCommand(List<String> args) {
        if (hasPermission && rootMethod != RootMethod.NONE) {
            return executeRootCommand(rootMethod, args);
        }
        return executeDirectCommand(args);
    }

    private String executeDirectCommand(List<String> command) {
        String logCommand = String.join(" ", command);
        return runProcess(command, logCommand);
    }

    private String executeRootCommand(RootMethod method, List<String> command) {
        if (method == RootMethod.NONE) {
            return "Error: No root method available";
        }
        String shellCommand = buildShellCommand(command);
        List<String> fullCommand = new ArrayList<>();
        if (method == RootMethod.SU) {
            fullCommand.add("su");
        } else {
            fullCommand.add("rish");
        }
        fullCommand.add("-c");
        fullCommand.add(shellCommand);
        String logCommand = (method == RootMethod.SU ? "su -c " : "rish -c ") + shellCommand;
        return runProcess(fullCommand, logCommand);
    }

    private String runProcess(List<String> command, String logCommand) {
        try {
            Process process = new ProcessBuilder(command).start();

            // Both pipes are drained on their own threads BEFORE anything waits on the process.
            // Reading them afterwards deadlocks as soon as the child writes more than the pipe
            // buffer holds: the child blocks on a full pipe while the parent waits for the child.
            ProcessOutputPump stdout = ProcessOutputPump.start(
                "privileged-stdout", process.getInputStream());
            ProcessOutputPump stderr = ProcessOutputPump.start(
                "privileged-stderr", process.getErrorStream());

            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                // Forcibly killing the child closes its pipes, which is what lets the pumps finish.
                process.destroyForcibly();
                stdout.await(PUMP_DRAIN_TIMEOUT_MS);
                stderr.await(PUMP_DRAIN_TIMEOUT_MS);
                Log.w(TAG, "Command timed out: " + maskSensitive(logCommand));
                return "Error: Command timed out";
            }

            String output = stdout.await(PUMP_DRAIN_TIMEOUT_MS);
            String errorOutput = stderr.await(PUMP_DRAIN_TIMEOUT_MS);

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                Log.w(TAG, "Command failed (" + exitCode + "): " + maskSensitive(logCommand));
                String errorMsg = errorOutput.length() > 0 ? errorOutput : "Exit code: " + exitCode;
                return "Error (" + exitCode + "): " + errorMsg;
            }

            return output;

        } catch (IOException e) {
            if (isExpectedMissingRish(command, e)) {
                Log.w(TAG, "rish not found; skipping rish root path");
            } else {
                Log.e(TAG, "Failed to execute shell command: " + maskSensitive(logCommand), e);
            }
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute shell command: " + maskSensitive(logCommand), e);
            return "Error: " + e.getMessage();
        }
    }

    private String buildShellCommand(List<String> args) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                builder.append(" ");
            }
            builder.append(shellEscape(args.get(i)));
        }
        return builder.toString();
    }

    private String shellEscape(String arg) {
        if (arg == null) {
            return "''";
        }
        return "'" + arg.replace("'", "'\"'\"'") + "'";
    }

    private boolean isCommandSuccessful(String output) {
        return output != null && !output.startsWith("Error");
    }

    private boolean isExpectedMissingRish(List<String> command, IOException exception) {
        if (command == null || command.isEmpty() || !"rish".equals(command.get(0))) {
            return false;
        }
        if (exception instanceof FileNotFoundException) {
            return true;
        }
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("cannot run program \"rish\"")
            || lower.contains("no such file")
            || lower.contains("error=2");
    }

    /**
     * Mask sensitive values in command logging
     */
    private String maskSensitive(String command) {
        return PrivilegedBackend.maskSensitiveCommand(command);
    }

    private enum RootMethod {
        SU,
        RISH,
        NONE
    }
}
