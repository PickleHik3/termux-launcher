package com.termux.privileged;

import android.content.Context;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction interface for privileged operations backend.
 * 
 * This interface provides a unified API for performing operations that require
 * elevated privileges (root/ADB), with automatic fallback to shell/rish when
 * Shizuku is not available.
 * 
 * Implementations:
 * - ShizukuBackend: Uses Shizuku for privileged operations
 * - ShellBackend: Falls back to shell/rish commands
 */
public interface PrivilegedBackend {
    
    /**
     * Backend type enumeration
     */
    enum Type {
        SHIZUKU,    // Shizuku with Sui support
        SHELL,      // Shell/rish fallback
        NONE        // No privileged backend available
    }
    
    /**
     * Initialize the backend
     * @param context Application context
     * @return CompletableFuture that completes when backend is ready
     */
    CompletableFuture<Boolean> initialize(Context context);
    
    /**
     * Check if the backend is available and ready for use
     * @return true if backend is operational
     */
    boolean isAvailable();
    
    /**
     * Get the backend type
     * @return The type of this backend
     */
    Type getType();
    
    /**
     * Check if the backend has the required permissions
     * @return true if permissions are granted
     */
    boolean hasPermission();
    
    /**
     * Request permissions from the user
     * @param requestCode Request code for permission result
     * @return true if permission request was initiated
     */
    boolean requestPermission(int requestCode);
    
    /**
     * Get installation apps with privileged access
     * @return List of installed packages
     */
    CompletableFuture<List<String>> getInstalledPackages();
    
    /**
     * Install an APK package
     * @param apkPath Path to the APK file
     * @return CompletableFuture with installation result
     */
    CompletableFuture<Boolean> installPackage(String apkPath);
    
    /**
     * Uninstall a package
     * @param packageName Name of the package to uninstall
     * @return CompletableFuture with uninstall result
     */
    CompletableFuture<Boolean> uninstallPackage(String packageName);
    
    /**
     * Enable or disable a package component
     * @param packageName Package name
     * @param componentName Component name (activity/service/etc.)
     * @param enabled Whether to enable or disable
     * @return CompletableFuture with operation result
     */
    CompletableFuture<Boolean> setComponentEnabled(String packageName, String componentName, boolean enabled);
    
    /**
     * Execute a shell command with elevated privileges
     * @param command Command to execute
     * @return CompletableFuture with command output
     */
    CompletableFuture<String> executeCommand(String command);
    
    /**
     * Check if a specific privileged operation is supported by this backend
     * @param operation The operation to check
     * @return true if the operation is supported
     */
    boolean isOperationSupported(PrivilegedOperation operation);
    
    /**
     * Get the backend status information
     * @return Status description for debugging
     */
    String getStatusDescription();
    
    /**
     * Cleanup resources when backend is no longer needed
     */
    void cleanup();
}