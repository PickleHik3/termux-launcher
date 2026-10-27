package com.termux.x11;

import static android.system.Os.getuid;

import android.annotation.SuppressLint;
import android.app.IActivityManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Keep;

import dalvik.annotation.optimization.CriticalNative;

import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.Arrays;

@Keep @SuppressLint({"StaticFieldLeak", "UnsafeDynamicallyLoadedCode"})
public class CmdEntryPoint extends ICmdEntryInterface.Stub {
    public static final String ACTION_START = "com.termux.x11.CmdEntryPoint.ACTION_START";
    /**
     * {@code TERMUX_X11_HEADLESS=1}: a server only for X clients, never offered to the launcher.
     * The GPU benchmark starts one to measure in; announced, the launcher's display window would
     * adopt it and be torn down when the benchmark stops it. X clients connect through the socket
     * as usual, the screen keeps the server's default size, and nothing ever draws it.
     */
    static final boolean HEADLESS = "1".equals(System.getenv("TERMUX_X11_HEADLESS"));
    static final Handler handler;
    public static Context ctx;
    private final Intent intent = createIntent();
    private boolean broadcastPending;

    /**
     * Command-line entry point.
     *
     * @param args The command-line arguments
     */
    public static void main(String[] args) {
        initEntryPoint();
        android.util.Log.i("CmdEntryPoint", "commit " + BuildConfig.COMMIT);
        handler.post(() -> new CmdEntryPoint(args));
        Looper.loop();
    }

    CmdEntryPoint(String[] args) {
        if (!start(args))
            System.exit(1);
    }

    @SuppressLint({"WrongConstant", "PrivateApi"})
    private Intent createIntent() {
        // We should not care about multiple instances, it should be called only by `Termux:X11` app
        // which is single instance...
        Bundle bundle = new Bundle();
        bundle.putBinder(null, this);

        Intent intent = new Intent(ACTION_START);
        intent.putExtra(null, bundle);
        intent.setPackage(BuildConfig.APPLICATION_ID);

        if (getuid() == 0 || getuid() == 2000)
            intent.setFlags(0x00400000 /* FLAG_RECEIVER_FROM_SHELL */);

        return intent;
    }

    private void sendBroadcast() {
        // Called from native (the X server thread) on every knock on the port; coalesce
        // bursts into a single broadcast fired 250ms later.
        if (HEADLESS)
            return;
        synchronized (this) {
            if (broadcastPending)
                return;
            broadcastPending = true;
        }
        handler.postDelayed(() -> {
            synchronized (this) { broadcastPending = false; }
            sendBroadcast(intent);
        }, 250);
    }

    static void sendBroadcast(Intent intent) {
        if (HEADLESS && ACTION_START.equals(intent.getAction()))
            return;
        try {
            ctx.sendBroadcast(intent);
        } catch (Exception e) {
            if (e instanceof NullPointerException && ctx == null)
                Log.i("Broadcast", "Context is null, falling back to manual broadcasting");
            else
                Log.e("Broadcast", "Falling back to manual broadcasting, failed to broadcast intent through Context:", e);

            String packageName;
            try {
                packageName = android.app.ActivityThread.getPackageManager().getPackagesForUid(getuid())[0];
            } catch (RemoteException ex) {
                throw new RuntimeException(ex);
            }
            IActivityManager am;
            try {
                //noinspection JavaReflectionMemberAccess
                am = (IActivityManager) android.app.ActivityManager.class
                        .getMethod("getService")
                        .invoke(null);
            } catch (Exception e2) {
                try {
                    am = (IActivityManager) Class.forName("android.app.ActivityManagerNative")
                            .getMethod("getDefault")
                            .invoke(null);
                } catch (Exception e3) {
                    throw new RuntimeException(e3);
                }
            }

            assert am != null;
            IIntentSender sender = am.getIntentSender(1, packageName, null, null, 0, new Intent[] { intent },
                    null, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT, null, 0);
            try {
                //noinspection JavaReflectionMemberAccess
                IIntentSender.class
                        .getMethod("send", int.class, Intent.class, String.class, IBinder.class, IIntentReceiver.class, String.class, Bundle.class)
                        .invoke(sender, 0, intent, null, null, new IIntentReceiver.Stub() {
                            @Override public void performReceive(Intent i, int r, String d, Bundle e, boolean o, boolean s, int a) {}
                        }, null, null);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    // In some cases Android Activity part can not connect opened port.
    // In this case opened port works like a lock file.
    private void sendBroadcastDelayed() {
        // Headless: no announcement, and no timer left polling for a connection that never comes.
        if (HEADLESS)
            return;
        if (!connected())
            sendBroadcast(intent);

        handler.postDelayed(this::sendBroadcastDelayed, 1000);
    }

    /** @noinspection DataFlowIssue*/
    @SuppressLint("DiscouragedPrivateApi")
    public static Context createContext() {
        Context context;
        PrintStream err = System.err;
        try {
            java.lang.reflect.Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Object unsafe = f.get(null);
            // Hiding harmless framework errors, like this:
            // java.io.FileNotFoundException: /data/system/theme_config/theme_compatibility.xml: open failed: ENOENT (No such file or directory)
            System.setErr(new PrintStream(new OutputStream() { public void write(int arg0) {} }));
            if (System.getenv("OLD_CONTEXT") != null) {
                context = android.app.ActivityThread.systemMain().getSystemContext();
            } else {
                context = ((android.app.ActivityThread) Class.
                        forName("sun.misc.Unsafe").
                        getMethod("allocateInstance", Class.class).
                        invoke(unsafe, android.app.ActivityThread.class))
                        .getSystemContext();
            }
        } catch (Exception e) {
            Log.e("Context", "Failed to instantiate context:", e);
            context = null;
        } finally {
            System.setErr(err);
        }
        return context;
    }

    public native boolean start(String[] args);
    public native ParcelFileDescriptor getXConnection();
    public native ParcelFileDescriptor getLogcatOutput();
    @CriticalNative private static native boolean connected();

    static {
        try {
            if (Looper.getMainLooper() == null)
                Looper.prepareMainLooper();
        } catch (Exception e) {
            Log.e("CmdEntryPoint", "Something went wrong when preparing MainLooper", e);
        }
        handler = new Handler();
    }

    private static void initEntryPoint() {
        ctx = createContext();

        ClassLoader loader = CmdEntryPoint.class.getClassLoader();
        // The process's own bitness picks the folder: a 32-bit app on a 64-bit device carries
        // only lib/x86 or lib/armeabi-v7a, while SUPPORTED_ABIS[0] names the 64-bit one.
        String abi = LibAbiChoice.choose(android.os.Process.is64Bit(),
            Build.SUPPORTED_64_BIT_ABIS, Build.SUPPORTED_32_BIT_ABIS, Build.SUPPORTED_ABIS,
            a -> loader != null && loader.getResource("lib/" + a + "/libXlorie.so") != null);
        if (abi == null) {
            Log.e("CmdEntryPoint", "No libXlorie.so for " + Arrays.toString(Build.SUPPORTED_ABIS));
            System.err.println("Failed to load native library: no libXlorie.so for ABIs "
                + Arrays.toString(Build.SUPPORTED_ABIS) + ". Did you install the right apk? Try the universal one.");
            System.exit(134);
            return;
        }
        URL res = loader.getResource("lib/" + abi + "/libXlorie.so");
        String libPath = res.getFile().replace("file:", "");
        try {
            System.load(libPath);
        } catch (Throwable e) {
            Log.e("CmdEntryPoint", "Failed to dlopen " + libPath, e);
            System.err.println("Failed to load native library. Did you install the right apk? Try the universal one.");
            System.exit(134);
        }
    }
}
