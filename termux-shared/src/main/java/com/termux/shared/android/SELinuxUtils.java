package com.termux.shared.android;

import android.annotation.SuppressLint;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.termux.shared.logger.Logger;
import com.termux.shared.reflection.ReflectionUtils;
import java.lang.reflect.Method;

public class SELinuxUtils {

    public static final String ANDROID_OS_SELINUX_CLASS = "android.os.SELinux";

    private static final String LOG_TAG = "SELinuxUtils";

    /**
     * Gets the security context of the current process.
     *
     * @return Returns a {@link String} representing the security context of the current process.
     * This will be {@code null} if an exception is raised.
     */
    @Nullable
    public static String getContext() {
        ReflectionUtils.bypassHiddenAPIReflectionRestrictions();
        String methodName = "getContext";
        try {
            @SuppressLint("PrivateApi")
            Class<?> clazz = Class.forName(ANDROID_OS_SELINUX_CLASS);
            Method method = ReflectionUtils.getDeclaredMethod(clazz, methodName);
            if (method == null) {
                Logger.logError(LOG_TAG, "Failed to get " + methodName + "() method of " + ANDROID_OS_SELINUX_CLASS + " class");
                return null;
            }
            return (String) ReflectionUtils.invokeMethod(method, null).value;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to call " + methodName + "() method of " + ANDROID_OS_SELINUX_CLASS + " class", e);
            return null;
        }
    }

    /**
     * Get the security context of a given process id.
     *
     * @param pid The pid of process.
     * @return Returns a {@link String} representing the security context of the given pid.
     * This will be {@code null} if an exception is raised.
     */
    @Nullable
    public static String getPidContext(int pid) {
        ReflectionUtils.bypassHiddenAPIReflectionRestrictions();
        String methodName = "getPidContext";
        try {
            @SuppressLint("PrivateApi")
            Class<?> clazz = Class.forName(ANDROID_OS_SELINUX_CLASS);
            Method method = ReflectionUtils.getDeclaredMethod(clazz, methodName, int.class);
            if (method == null) {
                Logger.logError(LOG_TAG, "Failed to get " + methodName + "() method of " + ANDROID_OS_SELINUX_CLASS + " class");
                return null;
            }
            return (String) ReflectionUtils.invokeMethod(method, null, pid).value;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to call " + methodName + "() method of " + ANDROID_OS_SELINUX_CLASS + " class", e);
            return null;
        }
    }

    /**
     * Get the security context of a file object.
     *
     * @param path The pathname of the file object.
     * @return Returns a {@link String} representing the security context of the file.
     * This will be {@code null} if an exception is raised.
     */
    @Nullable
    public static String getFileContext(@NonNull String path) {
        ReflectionUtils.bypassHiddenAPIReflectionRestrictions();
        String methodName = "getFileContext";
        try {
            @SuppressLint("PrivateApi")
            Class<?> clazz = Class.forName(ANDROID_OS_SELINUX_CLASS);
            Method method = ReflectionUtils.getDeclaredMethod(clazz, methodName, String.class);
            if (method == null) {
                Logger.logError(LOG_TAG, "Failed to get " + methodName + "() method of " + ANDROID_OS_SELINUX_CLASS + " class");
                return null;
            }
            return (String) ReflectionUtils.invokeMethod(method, null, path).value;
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to call " + methodName + "() method of " + ANDROID_OS_SELINUX_CLASS + " class", e);
            return null;
        }
    }

    /**
     * The SELinux domain assigned to apps whose effective target sdk version is {@code >= 29}.
     * Since Android 10 this domain is not allowed {@code execute_no_trans} on {@code app_data_file},
     * so an app running in it cannot execute any binary under its own data directory.
     */
    public static final String DOMAIN_UNTRUSTED_APP = "untrusted_app";

    /**
     * The legacy SELinux domains that are still allowed {@code execute_no_trans} on
     * {@code app_data_file}. Termux targets sdk 28 precisely so that it is assigned
     * {@link #DOMAIN_UNTRUSTED_APP_27} and can execute the bootstrap binaries it installs into
     * {@code $PREFIX}. No newer domain will be added to this set - the permission was removed for
     * good in Android 10.
     */
    public static final String DOMAIN_UNTRUSTED_APP_25 = "untrusted_app_25";

    public static final String DOMAIN_UNTRUSTED_APP_27 = "untrusted_app_27";

    /**
     * Get the domain (type) field of an SELinux context.
     * <p>
     * A context looks like {@code u:r:untrusted_app_27:s0:c123,c256,c512,c768}, of which the third
     * field is the domain.
     *
     * @param seContext The SELinux context, like the one returned by {@link #getContext()}.
     * @return Returns the domain, or {@code null} if {@code seContext} is {@code null} or does not
     * have the expected shape.
     */
    @Nullable
    public static String getDomain(@Nullable String seContext) {
        if (seContext == null) return null;
        String[] fields = seContext.split(":");
        if (fields.length < 3) return null;
        String domain = fields[2].trim();
        return domain.isEmpty() ? null : domain;
    }

    /**
     * Whether the process running in {@code seContext} is forbidden by SELinux from executing files
     * under its own data directory.
     * <p>
     * The app is assigned its domain at install time from the <b>highest</b> target sdk version of
     * any package in its {@code sharedUserId} group, and the assignment is persisted until every
     * member of that group is uninstalled. So an app that targets sdk 28 can still end up in
     * {@link #DOMAIN_UNTRUSTED_APP} - and then nothing under {@code $PREFIX} can be executed,
     * with the kernel denying {@code execute_no_trans}. Detecting that is the only way to tell
     * this apart from a corrupt bootstrap.
     * <p>
     * Only the {@code untrusted_app*} domains are classified. A process in any other domain (a
     * custom policy, a permissive ROM) is reported as unrestricted so that a device that actually
     * works is never blocked by a guess.
     *
     * @param sdkInt The running {@link android.os.Build.VERSION#SDK_INT}.
     * @param seContext The SELinux context of the process, which may be {@code null} if it could
     *                  not be read.
     * @return Returns {@code true} only when the domain is known to forbid the execution.
     */
    public static boolean isAppDataFileExecutionRestricted(int sdkInt, @Nullable String seContext) {
        // The permission was only removed in Android 10.
        if (sdkInt < Build.VERSION_CODES.Q) return false;
        String domain = getDomain(seContext);
        if (domain == null) return false;
        if (!domain.startsWith(DOMAIN_UNTRUSTED_APP)) return false;
        return !DOMAIN_UNTRUSTED_APP_25.equals(domain) && !DOMAIN_UNTRUSTED_APP_27.equals(domain);
    }

    /**
     * Wrapper for {@link #isAppDataFileExecutionRestricted(int, String)} that reads the context of
     * the current process.
     */
    public static boolean isAppDataFileExecutionRestricted() {
        return isAppDataFileExecutionRestricted(Build.VERSION.SDK_INT, getContext());
    }
}
