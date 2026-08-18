package com.termux.privileged;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;
import rikka.sui.Sui;

/**
 * Shizuku-based backend for privileged operations
 * 
 * This implementation uses the Shizuku API to perform privileged operations
 * with proper permission handling and lifecycle management.
 */
public class ShizukuBackend implements PrivilegedBackend {
    private static final String TAG = "ShizukuBackend";
    private static final long COMMAND_TIMEOUT_SECONDS = 30;
    private static final long PUMP_DRAIN_TIMEOUT_MS = 2_000L;
    
    public static final int PERMISSION_REQUEST_CODE = 1001;
    
    private final Callback callback;
    private Context context;
    private boolean isAvailable = false;
    private boolean hasPermission = false;
    private boolean binderReceived = false;
    private boolean suiInitialized = false;
    private boolean listenersRegistered = false;

    public ShizukuBackend() {
        this(null);
    }

    public ShizukuBackend(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onPermissionResult(boolean granted);
        void onBinderDead();
        default void onBinderReceived() {
        }
    }

    // Listeners for Shizuku events
    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        Log.i(TAG, "Shizuku binder received");
        binderReceived = true;
        isAvailable = true;
        checkPermission();
        notifyBinderReceived();
    };
    
    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        Log.i(TAG, "Shizuku binder dead");
        binderReceived = false;
        isAvailable = false;
        hasPermission = false;
        notifyBinderDead();
    };
    
    private final Shizuku.OnRequestPermissionResultListener permissionResultListener = (requestCode, grantResult) -> {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            hasPermission = (grantResult == PackageManager.PERMISSION_GRANTED);
            Log.i(TAG, "Permission request result: " + hasPermission);
            notifyPermissionResult(hasPermission);
        }
    };
    
    @Override
    public CompletableFuture<Boolean> initialize(Context context) {
        this.context = context.getApplicationContext();
        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.i(TAG, "Initializing Shizuku backend...");
                
                suiInitialized = Sui.init(context.getPackageName());
                Log.i(TAG, "Sui initialization result: " + suiInitialized);

                registerListeners();

                if (Shizuku.isPreV11()) {
                    Log.w(TAG, "Shizuku pre-v11 not supported");
                    isAvailable = false;
                    cleanup();
                    return false;
                }

                binderReceived = Shizuku.pingBinder();
                if (binderReceived) {
                    checkPermission();
                    notifyBinderReceived();
                } else {
                    hasPermission = false;
                }

                isAvailable = binderReceived;
                Log.i(TAG, "Shizuku backend initialized, binder: " + binderReceived);
                return isAvailable;

            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize Shizuku backend", e);
                isAvailable = false;
                hasPermission = false;
                cleanup();
                return false;
            }
        }, PrivilegedExecutors.commands());
    }
    
    @Override
    public boolean isAvailable() {
        if (isAvailable && binderReceived) {
            try {
                if (Shizuku.pingBinder()) {
                    return true;
                }
            } catch (Exception e) {
                Log.w(TAG, "Shizuku binder availability check failed", e);
            }
            isAvailable = false;
            binderReceived = false;
            hasPermission = false;
        }
        return isAvailable && binderReceived;
    }
    
    @Override
    public Type getType() {
        return Type.SHIZUKU;
    }
    
    @Override
    public boolean hasPermission() {
        return hasPermission;
    }
    
    @Override
    public boolean requestPermission(int requestCode) {
        if (callback == null) {
            Log.w(TAG, "Cannot request permission: no callback");
            return false;
        }
        if (!isAvailable() || Shizuku.isPreV11()) {
            Log.w(TAG, "Cannot request permission: backend not available");
            return false;
        }
        
        if (hasPermission) {
            Log.i(TAG, "Permission already granted");
            return true;
        }
        
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            Log.i(TAG, "User previously denied permission");
            return false;
        }
        
        if (requestCode != PERMISSION_REQUEST_CODE) {
            Log.w(TAG, "Ignoring non-standard request code: " + requestCode + ", using " + PERMISSION_REQUEST_CODE);
        }

        Log.i(TAG, "Requesting Shizuku permission...");
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE);
        return true;
    }
    
    @Override
    public CompletableFuture<List<String>> getInstalledPackages() {
        return CompletableFuture.supplyAsync(() -> {
            if (!hasPermission()) {
                Log.w(TAG, "No permission to get installed packages");
                return List.of();
            }
            
            try {
                List<String> packages = new ArrayList<>();
                
                // Use PackageManager with Shizuku's elevated privileges
                // This would require a UserService for true privileged access
                // For now, we'll use the standard approach with elevated context
                
                // TODO: Implement proper UserService for full privileged access
                // This is a simplified implementation
                
                Log.i(TAG, "Getting installed packages via Shizuku...");
                
                // For demonstration, we'll use a simple shell command approach
                // In a real implementation, this would use proper Shizuku UserService
                String output = executeShizukuCommand(List.of("pm", "list", "packages"));
                
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
            if (!hasPermission()) {
                Log.w(TAG, "No permission to install packages");
                return false;
            }
            
            try {
                if (apkPath == null || apkPath.trim().isEmpty()) {
                    Log.e(TAG, "Invalid APK path");
                    return false;
                }
                
                Log.i(TAG, "Installing package via Shizuku: " + apkPath);
                
                String output = executeShizukuCommand(List.of("pm", "install", "-r", apkPath));
                
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
            if (!hasPermission()) {
                Log.w(TAG, "No permission to uninstall packages");
                return false;
            }
            
            try {
                if (packageName == null || packageName.trim().isEmpty()) {
                    Log.e(TAG, "Invalid package name");
                    return false;
                }
                
                Log.i(TAG, "Uninstalling package via Shizuku: " + packageName);
                
                String output = executeShizukuCommand(List.of("pm", "uninstall", packageName));
                
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
            if (!hasPermission()) {
                Log.w(TAG, "No permission to modify components");
                return false;
            }
            
            try {
                if (packageName == null || componentName == null) {
                    Log.e(TAG, "Invalid package or component name");
                    return false;
                }
                
                String action = enabled ? "enable" : "disable";
                Log.i(TAG, "Setting component via Shizuku: " + packageName + "/" + componentName + " to " + action);
                
                String output = executeShizukuCommand(List.of("pm", action, packageName + "/" + componentName));
                
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
            if (!hasPermission()) {
                return "No permission to execute commands";
            }
            
            try {
                if (command == null || command.trim().isEmpty()) {
                    return "Invalid command";
                }
                
                Log.i(TAG, "Executing command via Shizuku");
                return executeShizukuCommand(List.of("sh", "-c", command));
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to execute command: " + command, e);
                return "Error: " + e.getMessage();
            }
        }, PrivilegedExecutors.commands());
    }
    
    @Override
    public boolean isOperationSupported(PrivilegedOperation operation) {
        // Shizuku backend supports most operations through UserService
        // For now, we'll return true for most operations
        return operation != null;
    }
    
    @Override
    public String getStatusDescription() {
        int uid = Shizuku.getUid();
        String privilegeLevel = (uid == 0) ? "ROOT" : (uid == 2000 ? "ADB" : "UNKNOWN(" + uid + ")");
        
        return String.format("Shizuku backend - Available: %s, HasPermission: %s, SuiInit: %s, Privilege: %s", 
            isAvailable, hasPermission, suiInitialized, privilegeLevel);
    }
    
    @Override
    public void cleanup() {
        try {
            if (listenersRegistered) {
                Shizuku.removeBinderReceivedListener(binderReceivedListener);
                Shizuku.removeBinderDeadListener(binderDeadListener);
                Shizuku.removeRequestPermissionResultListener(permissionResultListener);
                listenersRegistered = false;
            }
            isAvailable = false;
            binderReceived = false;
            hasPermission = false;
            Log.i(TAG, "Shizuku backend cleaned up");
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }
    }
    
    private void registerListeners() {
        if (listenersRegistered) {
            return;
        }
        Shizuku.addBinderReceivedListener(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
        listenersRegistered = true;
    }

    private void notifyBinderReceived() {
        if (callback != null) {
            callback.onBinderReceived();
        }
    }

    private void notifyBinderDead() {
        if (callback != null) {
            callback.onBinderDead();
        }
    }

    private void notifyPermissionResult(boolean granted) {
        if (callback != null) {
            callback.onPermissionResult(granted);
        }
    }
    
    /**
     * Check permission status
     */
    private void checkPermission() {
        try {
            int permission = Shizuku.checkSelfPermission();
            hasPermission = (permission == PackageManager.PERMISSION_GRANTED);
            Log.i(TAG, "Permission check result: " + hasPermission);
        } catch (Exception e) {
            Log.e(TAG, "Failed to check permission", e);
            hasPermission = false;
        }
    }
    
    /**
     * Execute a command through Shizuku with proper privilege escalation
     */
    private String executeShizukuCommand(List<String> args) {
        try {
            if (!hasPermission()) {
                return "No Shizuku permission";
            }

            if (args == null || args.isEmpty()) {
                return "Invalid command";
            }

            // Use reflective lookup for Shizuku remote process creation.
            Method newProcessMethod = Shizuku.class.getDeclaredMethod(
                "newProcess", String[].class, String[].class, String.class);
            newProcessMethod.setAccessible(true);
            Object processObject = newProcessMethod.invoke(
                null, args.toArray(new String[0]), null, null);
            if (!(processObject instanceof Process)) {
                return "Error: Shizuku process API unavailable";
            }

            Process process = (Process) processObject;

            // A ShizukuRemoteProcess is a binder proxy, and it is registered with the Shizuku
            // client the moment it is created. Letting it fall out of scope after the command exits
            // leaves the proxy, its death recipient and the client's bookkeeping entry behind: the
            // stats sampler runs several commands every few seconds, so the process accumulated
            // about forty of these a minute for as long as the launcher was on screen. Release it
            // on every path, exit code or not.
            try {
                // Both pipes are drained on their own threads BEFORE anything waits on the process.
                // Reading them after the wait deadlocks as soon as the child writes more than the
                // pipe buffer holds (the stats card's ps+top output does): the child blocks on a
                // full pipe while this thread waits for it to exit, until the timeout fires and the
                // sample is thrown away as "stale".
                ProcessOutputPump stdout = ProcessOutputPump.start(
                    "shizuku-stdout", process.getInputStream());
                ProcessOutputPump stderr = ProcessOutputPump.start(
                    "shizuku-stderr", process.getErrorStream());

                boolean finished = waitForProcessExit(process, COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    // Forcibly killing the child closes its pipes, which is what lets the pumps finish.
                    process.destroyForcibly();
                    stdout.await(PUMP_DRAIN_TIMEOUT_MS);
                    stderr.await(PUMP_DRAIN_TIMEOUT_MS);
                    return "Error: command timed out";
                }

                String output = stdout.await(PUMP_DRAIN_TIMEOUT_MS);
                String errorOutput = stderr.await(PUMP_DRAIN_TIMEOUT_MS);
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    String errorMessage = errorOutput.isEmpty() ? ("Exit code: " + exitCode) : errorOutput;
                    return "Error (" + exitCode + "): " + errorMessage;
                }

                return output;
            } finally {
                releaseRemoteProcess(process);
            }

        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Shizuku newProcess API is not available on this runtime", e);
            return "Error: Shizuku command execution API unavailable";
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute Shizuku command", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Drops a finished remote process: closes its three pipes and destroys it, which is what
     * unregisters the binder proxy on the Shizuku client's side.
     */
    private void releaseRemoteProcess(@Nullable Process process) {
        if (process == null) return;
        closeQuietly(process.getInputStream());
        closeQuietly(process.getErrorStream());
        closeQuietly(process.getOutputStream());
        try {
            process.destroy();
        } catch (Throwable ignored) {
            // Already gone, or the remote refuses a second destroy; either way it is released.
        }
    }

    private static void closeQuietly(@Nullable java.io.Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Throwable ignored) {
        }
    }

    private boolean waitForProcessExit(Process process, long timeout, TimeUnit unit) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadlineNanos) {
            try {
                if (!process.isAlive()) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // ShizukuRemoteProcess may throw IllegalArgumentException while still running.
            }
            Thread.sleep(100);
        }
        return false;
    }

}
