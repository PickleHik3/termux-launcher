package com.termux.privileged;

import android.content.Context;
import android.util.Log;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manager class for privileged backend operations
 *
 * This class handles the initialization and management of the appropriate
 * privileged backend (Shizuku or shell fallback) and orchestrates the required
 * state/reason reporting.
 */
public class PrivilegedBackendManager {
    private static final String TAG = "PrivilegedBackendManager";
    private static PrivilegedBackendManager instance;

    public enum BackendState {
        UNINITIALIZED,
        INITIALIZING,
        READY,
        PERMISSION_REQUESTING,
        PERMISSION_DENIED,
        SERVICE_NOT_RUNNING,
        FALLBACK_SHELL,
        UNAVAILABLE
    }

    public enum StatusReason {
        GRANTED,
        PERMISSION_REQUESTING,
        DENIED,
        SERVICE_NOT_RUNNING,
        BINDER_DEAD,
        FALLBACK_SHELL,
        UNAVAILABLE
    }

    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Object stateLock = new Object();
    private BackendState backendState = BackendState.UNINITIALIZED;
    private StatusReason statusReason = StatusReason.UNAVAILABLE;
    private String statusMessage = "Not initialized";

    private Context applicationContext;
    private PrivilegedBackend currentBackend = new NoOpBackend();
    private ShizukuBackend shizukuBackend;
    private ShellBackend shellBackend;
    private boolean shizukuOnlyMode;

    private final ShizukuBackend.Callback shizukuCallback = new ShizukuBackend.Callback() {
        @Override
        public void onPermissionResult(boolean granted) {
            getExecutorService().submit(() -> handleShizukuPermissionResult(granted));
        }

        @Override
        public void onBinderDead() {
            getExecutorService().submit(PrivilegedBackendManager.this::handleShizukuBinderDeath);
        }

        @Override
        public void onBinderReceived() {
            getExecutorService().submit(PrivilegedBackendManager.this::handleShizukuBinderReceived);
        }
    };

    private PrivilegedBackendManager() {
        // Private constructor for singleton
    }

    public static synchronized PrivilegedBackendManager getInstance() {
        if (instance == null) {
            instance = new PrivilegedBackendManager();
        }
        return instance;
    }

    public CompletableFuture<Boolean> initialize(Context context) {
        this.applicationContext = context.getApplicationContext();
        shizukuOnlyMode = false;
        updateState(BackendState.INITIALIZING, StatusReason.UNAVAILABLE, "Choosing backend");

        return CompletableFuture.supplyAsync(this::reselectBackendInternal, getExecutorService());
    }

    public CompletableFuture<Boolean> initializeIfNeeded(Context context) {
        if (applicationContext == null) {
            return initialize(context);
        }
        this.applicationContext = context.getApplicationContext();
        BackendState state = getBackendState();
        if (state == BackendState.UNINITIALIZED || state == BackendState.UNAVAILABLE || state == BackendState.SERVICE_NOT_RUNNING
            || !isCurrentBackendConsistentForState(state)) {
            return reselectBackend();
        }
        return CompletableFuture.completedFuture(isPrivilegedAvailable());
    }

    public CompletableFuture<Boolean> initializeShizukuOnly(Context context) {
        this.applicationContext = context.getApplicationContext();
        shizukuOnlyMode = true;
        // A healthy backend is left alone. This runs on every activity resume (and every
        // Shizuku lock press); tearing down a READY backend to rebuild the same one made
        // isPrivilegedAvailable() read false for the first seconds after each home-return,
        // which silently starved every privileged consumer — most visibly the CPU card's
        // process list. isAvailable() pings the binder, so a dead Shizuku still re-initializes.
        if (currentBackend instanceof ShizukuBackend && isPrivilegedAvailable()) {
            return CompletableFuture.completedFuture(true);
        }
        updateState(BackendState.INITIALIZING, StatusReason.UNAVAILABLE, "Initializing Shizuku");
        return CompletableFuture.supplyAsync(() -> {
            boolean initialized = attemptShizukuInitialization(applicationContext);
            if (!initialized) {
                fallbackToNoOp("Shizuku unavailable");
            }
            return initialized;
        }, getExecutorService());
    }

    public CompletableFuture<Boolean> reselectBackend() {
        if (applicationContext == null) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(this::reselectBackendInternal, getExecutorService());
    }

    private boolean reselectBackendInternal() {
        try {
            if (!isMasterEnabled()) {
                fallbackToNoOp("Privileged access disabled by settings");
                return false;
            }

            if (isPreferShizuku()) {
                boolean hasShizuku = attemptShizukuInitialization(applicationContext);
                if (hasShizuku) {
                    return currentBackend.isAvailable();
                }
                if (isShellFallbackEnabled()) {
                    return fallbackToShell("Shizuku unavailable at startup");
                }
                fallbackToNoOp("Shizuku unavailable and shell fallback disabled");
                return false;
            }

            if (isShellFallbackEnabled()) {
                return fallbackToShell("Shell backend selected by settings");
            }

            fallbackToNoOp("No privileged backend enabled by settings");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize privileged backend", e);
            fallbackToNoOp("Initialization exception: " + e.getMessage());
            return false;
        }
    }

    private boolean attemptShizukuInitialization(Context context) {
        if (shizukuBackend != null && shizukuBackend != currentBackend) {
            shizukuBackend.cleanup();
        }
        shizukuBackend = new ShizukuBackend(shizukuCallback);
        boolean initialized = shizukuBackend.initialize(context).join();
        if (!initialized || !shizukuBackend.isAvailable()) {
            shizukuBackend.cleanup();
            updateState(BackendState.SERVICE_NOT_RUNNING, StatusReason.SERVICE_NOT_RUNNING,
                "Shizuku service not running");
            return false;
        }

        if (shizukuBackend.hasPermission()) {
            applyBackend(shizukuBackend, BackendState.READY, StatusReason.GRANTED,
                "Shizuku backend ready");
        } else {
            applyBackend(shizukuBackend, BackendState.PERMISSION_DENIED, StatusReason.DENIED,
                "Shizuku backend bound but permission missing");
        }

        return true;
    }

    private boolean fallbackToShell(String reason) {
        if (!isShellFallbackEnabled()) {
            fallbackToNoOp("Shell fallback disabled by settings");
            return false;
        }
        if (applicationContext == null) {
            fallbackToNoOp("Missing context for shell fallback");
            return false;
        }

        shellBackend = new ShellBackend();
        boolean shellReady = shellBackend.initialize(applicationContext).join();
        if (shellReady && shellBackend.hasPermission()) {
            applyBackend(shellBackend, BackendState.FALLBACK_SHELL, StatusReason.FALLBACK_SHELL, reason);
            return true;
        }

        fallbackToNoOp("Shell fallback failed: " + reason);
        return false;
    }

    private void fallbackToNoOp(String reason) {
        applyBackend(new NoOpBackend(), BackendState.UNAVAILABLE, StatusReason.UNAVAILABLE, reason);
    }

    private void handleShizukuPermissionResult(boolean granted) {
        if (!(currentBackend instanceof ShizukuBackend)) {
            return;
        }

        if (granted) {
            applyBackend(currentBackend, BackendState.READY, StatusReason.GRANTED,
                "Shizuku permission granted");
        } else {
            applyBackend(currentBackend, BackendState.PERMISSION_DENIED, StatusReason.DENIED,
                "Shizuku permission denied");
            if (!shizukuOnlyMode) {
                fallbackToShell("Shizuku permission denied");
            }
        }
    }

    private void handleShizukuBinderDeath() {
        if (!(currentBackend instanceof ShizukuBackend)) {
            return;
        }

        updateState(BackendState.SERVICE_NOT_RUNNING, StatusReason.BINDER_DEAD,
            "Shizuku binder died");
        if (!shizukuOnlyMode) {
            fallbackToShell("Shizuku binder dead");
        }
    }

    private void handleShizukuBinderReceived() {
        if (!isMasterEnabled() || !isPreferShizuku()) {
            return;
        }
        if (shizukuBackend == null || !shizukuBackend.isAvailable()) {
            return;
        }

        if (shizukuBackend.hasPermission()) {
            applyBackend(shizukuBackend, BackendState.READY, StatusReason.GRANTED,
                "Shizuku binder restored");
        } else {
            applyBackend(shizukuBackend, BackendState.PERMISSION_DENIED, StatusReason.DENIED,
                "Shizuku binder restored; permission missing");
        }
    }

    private void applyBackend(PrivilegedBackend backend, BackendState state, StatusReason reason, String message) {
        if (currentBackend != null && currentBackend != backend) {
            currentBackend.cleanup();
        }
        currentBackend = backend;
        updateState(state, reason, message);
    }

    private void updateState(BackendState state, StatusReason reason, String message) {
        synchronized (stateLock) {
            this.backendState = state;
            this.statusReason = reason;
            this.statusMessage = message;
        }
        Log.i(TAG, "Backend state -> " + state + ", reason -> " + reason + ", message -> " + message);
    }

    private boolean isCurrentBackendConsistentForState(BackendState state) {
        if (currentBackend == null) {
            return false;
        }

        switch (state) {
            case READY:
            case FALLBACK_SHELL:
                return currentBackend.isAvailable() && currentBackend.hasPermission();
            case PERMISSION_DENIED:
            case PERMISSION_REQUESTING:
                return currentBackend.isAvailable();
            default:
                return true;
        }
    }

    private boolean ensureShizukuPermissionBeforeOperation(String operation) {
        if (!isMasterEnabled()) {
            updateState(BackendState.UNAVAILABLE, StatusReason.UNAVAILABLE,
                "Privileged access disabled by settings");
            return false;
        }

        if (currentBackend instanceof ShizukuBackend) {
            ShizukuBackend shizukuBackend = (ShizukuBackend) currentBackend;
            if (!shizukuBackend.isAvailable()) {
                handleShizukuBinderDeath();
                return false;
            }

            if (!shizukuBackend.hasPermission()) {
                if (getBackendState() == BackendState.PERMISSION_REQUESTING) {
                    Log.i(TAG, "Shizuku permission request already in progress before " + operation);
                    return false;
                }
                Log.i(TAG, "Shizuku permission required before " + operation);
                requestPrivilegedPermission(ShizukuBackend.PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    public PrivilegedBackend getBackend() {
        return currentBackend;
    }

    public BackendState getBackendState() {
        synchronized (stateLock) {
            return backendState;
        }
    }

    public StatusReason getStatusReason() {
        synchronized (stateLock) {
            return statusReason;
        }
    }

    public String getStatusMessage() {
        synchronized (stateLock) {
            return statusMessage;
        }
    }

    public boolean isPrivilegedAvailable() {
        if (!isMasterEnabled()) {
            return false;
        }
        BackendState state = getBackendState();
        return (state == BackendState.READY || state == BackendState.FALLBACK_SHELL)
            && currentBackend != null && currentBackend.isAvailable() && currentBackend.hasPermission();
    }

    public PrivilegedBackend.Type getBackendType() {
        return currentBackend != null ? currentBackend.getType() : PrivilegedBackend.Type.NONE;
    }

    public boolean requestPrivilegedPermission(int requestCode) {
        if (!isMasterEnabled()) {
            updateState(BackendState.UNAVAILABLE, StatusReason.UNAVAILABLE,
                "Privileged access disabled by settings");
            return false;
        }

        if (currentBackend instanceof ShizukuBackend) {
            ShizukuBackend shizukuBackend = (ShizukuBackend) currentBackend;
            if (!shizukuBackend.isAvailable()) {
                return false;
            }
            if (shizukuBackend.hasPermission()) {
                updateState(BackendState.READY, StatusReason.GRANTED,
                    "Shizuku permission already granted");
                return true;
            }
            if (getBackendState() == BackendState.PERMISSION_REQUESTING) {
                Log.i(TAG, "Ignoring duplicate Shizuku permission request");
                return true;
            }
            boolean initiated = shizukuBackend.requestPermission(requestCode);
            if (initiated) {
                updateState(BackendState.PERMISSION_REQUESTING, StatusReason.PERMISSION_REQUESTING,
                    "Requested Shizuku permission");
            }
            return initiated;
        }
        return false;
    }

    public CompletableFuture<List<String>> getInstalledPackages() {
        if (!ensureShizukuPermissionBeforeOperation("getInstalledPackages")) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return currentBackend.getInstalledPackages();
    }

    public CompletableFuture<Boolean> installPackage(String apkPath) {
        if (!ensureShizukuPermissionBeforeOperation("installPackage")) {
            return CompletableFuture.completedFuture(false);
        }
        return currentBackend.installPackage(apkPath);
    }

    public CompletableFuture<Boolean> uninstallPackage(String packageName) {
        if (!ensureShizukuPermissionBeforeOperation("uninstallPackage")) {
            return CompletableFuture.completedFuture(false);
        }
        return currentBackend.uninstallPackage(packageName);
    }

    public CompletableFuture<Boolean> setComponentEnabled(String packageName, String componentName, boolean enabled) {
        if (!ensureShizukuPermissionBeforeOperation("setComponentEnabled")) {
            return CompletableFuture.completedFuture(false);
        }
        return currentBackend.setComponentEnabled(packageName, componentName, enabled);
    }

    public CompletableFuture<String> executeCommand(String command) {
        if (!ensureShizukuPermissionBeforeOperation("executeCommand")) {
            if (!isMasterEnabled()) {
                return CompletableFuture.completedFuture("Privileged access disabled by settings");
            }
            return CompletableFuture.completedFuture("Shizuku permission required");
        }
        return currentBackend.executeCommand(command);
    }

    public String getStatusDescription() {
        StringBuilder builder = new StringBuilder();
        builder.append("Backend: ").append(getBackendType());
        builder.append(" | State: ").append(getBackendState());
        builder.append(" | Reason: ").append(getStatusReason());
        builder.append(" | Message: ").append(getStatusMessage());
        builder.append(" | Permission: ").append(currentBackend != null && currentBackend.hasPermission());
        return builder.toString();
    }

    public void cleanup() {
        if (currentBackend != null) {
            currentBackend.cleanup();
        }
        executorService.shutdown();
        applyBackend(new NoOpBackend(), BackendState.UNAVAILABLE, StatusReason.UNAVAILABLE,
            "Resources cleaned up");
    }

    private synchronized ExecutorService getExecutorService() {
        if (executorService.isShutdown() || executorService.isTerminated()) {
            executorService = Executors.newSingleThreadExecutor();
        }
        return executorService;
    }

    private boolean isMasterEnabled() {
        return applicationContext == null || PrivilegedPolicyStore.isMasterEnabled(applicationContext);
    }

    private boolean isPreferShizuku() {
        return applicationContext == null || PrivilegedPolicyStore.isPreferShizuku(applicationContext);
    }

    private boolean isShellFallbackEnabled() {
        return applicationContext == null || PrivilegedPolicyStore.isShellFallbackEnabled(applicationContext);
    }

    private static class NoOpBackend implements PrivilegedBackend {
        @Override
        public CompletableFuture<Boolean> initialize(Context context) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public Type getType() {
            return Type.NONE;
        }

        @Override
        public boolean hasPermission() {
            return false;
        }

        @Override
        public boolean requestPermission(int requestCode) {
            return false;
        }

        @Override
        public CompletableFuture<List<String>> getInstalledPackages() {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        @Override
        public CompletableFuture<Boolean> installPackage(String apkPath) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Boolean> uninstallPackage(String packageName) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Boolean> setComponentEnabled(String packageName, String componentName, boolean enabled) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<String> executeCommand(String command) {
            return CompletableFuture.completedFuture("No privileged backend available");
        }

        @Override
        public boolean isOperationSupported(PrivilegedOperation operation) {
            return false;
        }

        @Override
        public String getStatusDescription() {
            return "No privileged backend available";
        }

        @Override
        public void cleanup() {
            // No-op
        }
    }
}
