// Developer harness for the Wayland host; it is removed when the Wayland Display page lands.
package com.termux.app.wayland;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.termux.BuildConfig;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;

/**
 * Shows each Linux toplevel of the Wayland host in its own {@link TextureView}, stacked
 * in equal shares of the height over the system wallpaper.
 *
 * The compositor runs in {@link LinuxWindowsService} in the {@code :linuxwindows} process, so it
 * outlives this activity: reopening re-attaches to the live toplevels, and a crash there leaves
 * this activity standing with a Restart button. Not exported; start it with
 * {@code am start -n <package>/com.termux.app.wayland.WaylandTestActivity} from the app's uid
 * (add {@code --ez xwayland true} to also start rootless Xwayland from the prefix).
 */
public final class WaylandTestActivity extends Activity {

    private static final String LOG_TAG = "TLWayland";
    /** The clients' XDG_RUNTIME_DIR, under the prefix's tmp. */
    public static final String SOCKET_DIR = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH + "/tl-wayland";
    public static final String EXTRA_XWAYLAND = "xwayland";

    private static final int MAX_POINTS = ILinuxWindows.MAX_TOUCH_POINTERS;

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final LongSparseArray<Pane> mPanes = new LongSparseArray<>();

    private LinearLayout mPaneHost;
    private TextView mStatus;
    private TextView mEmpty;
    private Button mStartButton;
    private Button mStopButton;
    private Button mCrashButton;
    private Button mProbeButton;
    private Button mCopyLogButton;
    private boolean mCopyLog;

    private ILinuxWindows mService;
    private IBinder mServiceBinder;
    private boolean mBound;
    /** The debug interface, bound beside the host for Crash, Probe and the copy log. */
    private ILinuxWindowsDebug mDebug;
    private boolean mDebugBound;
    /** Start the compositor when the service reports it is not running. */
    private boolean mAutoStart = true;
    /** The activity is visible: panes hand their Surfaces to the host. */
    private boolean mVisible;
    private long mFocusedId;
    private int mHostPid;

    private final IBinder.DeathRecipient mDeath = () -> mMain.post(this::onHostDied);

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mServiceBinder = binder;
            mService = ILinuxWindows.Stub.asInterface(binder);
            try {
                binder.linkToDeath(mDeath, 0);
                mService.addCallback(mCallback);
            } catch (RemoteException e) {
                onHostDied();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            onHostDied();
        }
    };

    private final ServiceConnection mDebugConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mDebug = ILinuxWindowsDebug.Stub.asInterface(binder);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mDebug = null;
        }
    };

    /** Host events arrive on binder threads; everything is handled on the main thread. */
    private final ILinuxWindowsCallback.Stub mCallback = new ILinuxWindowsCallback.Stub() {
        @Override
        public void onAttached(int interfaceVersion, int hostPid) {
            mMain.post(() -> attached(interfaceVersion, hostPid));
        }

        @Override
        public void onHostState(int state, String message) {
            mMain.post(() -> hostState(state, message));
        }

        @Override
        public void onWindowCreated(long id, String title, String appId, int clientPid, int flags) {
            mMain.post(() -> windowCreated(id, title, appId, clientPid, flags));
        }

        @Override
        public void onWindowChanged(long id, String title, String appId, int width, int height) {
            mMain.post(() -> {
                Pane pane = mPanes.get(id);
                if (pane == null) return;
                pane.title = title;
                pane.appId = appId;
                pane.width = width;
                pane.height = height;
                pane.updateLabel();
            });
        }

        @Override
        public void onWindowDestroyed(long id) {
            mMain.post(() -> removePane(id));
        }
    };

    /** One toplevel: a title line with a close button over its TextureView. */
    private final class Pane implements TextureView.SurfaceTextureListener {
        final long id;
        final LinearLayout row;
        final TextView label;
        final TextureView view;
        Surface surface;
        String title = "";
        String appId = "";
        int clientPid;
        int flags;
        int width;
        int height;
        int mouseButton;
        // Reused for every MotionEvent.
        final int[] slots = new int[MAX_POINTS];
        final float[] xs = new float[MAX_POINTS];
        final float[] ys = new float[MAX_POINTS];

        Pane(long id) {
            this.id = id;
            row = new LinearLayout(WaylandTestActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);

            LinearLayout titleLine = new LinearLayout(WaylandTestActivity.this);
            titleLine.setOrientation(LinearLayout.HORIZONTAL);
            titleLine.setGravity(Gravity.CENTER_VERTICAL);
            label = shadowedText();
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setPadding(dp(8), dp(2), dp(8), dp(2));
            TextView close = shadowedText();
            close.setText("✕");
            close.setPadding(dp(12), dp(2), dp(12), dp(2));
            close.setOnClickListener(v -> {
                if (mService != null) {
                    try {
                        mService.close(id);
                    } catch (RemoteException ignored) {
                    }
                }
            });
            titleLine.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            titleLine.addView(close, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

            view = new TextureView(WaylandTestActivity.this);
            view.setOpaque(false);
            view.setSurfaceTextureListener(this);
            view.setOnTouchListener((v, e) -> onTouch(e));
            view.setOnGenericMotionListener((v, e) -> onGenericMotion(e));
            row.addView(titleLine, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(view, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        void updateLabel() {
            StringBuilder sb = new StringBuilder();
            sb.append(TextUtils.isEmpty(title) ? "(untitled)" : title);
            if (!TextUtils.isEmpty(appId)) sb.append(" · ").append(appId);
            if ((flags & ILinuxWindows.WINDOW_X11) != 0) sb.append(" · X11");
            if (clientPid > 0) sb.append(" · pid ").append(clientPid);
            if (width > 0) sb.append(" · ").append(width).append('×').append(height);
            label.setText(sb);
            label.setTypeface(null, id == mFocusedId ? Typeface.BOLD : Typeface.NORMAL);
        }

        /** Hands the Surface to the host. */
        void attach() {
            if (mService == null || surface == null) return;
            try {
                mService.configure(id, view.getWidth(), view.getHeight(), density());
                mService.setSurface(id, surface);
                mService.setVisible(id, mVisible);
            } catch (RemoteException ignored) {
            }
        }

        /** The host draws only visible windows; a hidden one keeps its last frame. */
        void setVisible(boolean visible) {
            if (mService == null) return;
            try {
                mService.setVisible(id, visible);
            } catch (RemoteException ignored) {
            }
        }

        void detach() {
            if (mService == null) return;
            try {
                mService.setSurface(id, null);
            } catch (RemoteException ignored) {
            }
        }

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture texture, int width, int height) {
            surface = new Surface(texture);
            attach();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture texture, int width, int height) {
            if (mService == null) return;
            try {
                mService.configure(id, width, height, density());
            } catch (RemoteException ignored) {
            }
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture texture) {
            detach();
            if (surface != null) {
                surface.release();
                surface = null;
            }
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture texture) {}

        private void sendPointer(int action, int button, float x, float y, long time) {
            if (mService == null) return;
            try {
                mService.pointer(id, action, button, x, y, time);
            } catch (RemoteException ignored) {
            }
        }

        /** The whole MotionEvent in one call: a second finger going down is one wl_touch frame. */
        private boolean onTouch(MotionEvent e) {
            if (e.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) return onMouse(e);
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) focus(id);
            int n = Math.min(e.getPointerCount(), MAX_POINTS);
            for (int i = 0; i < n; i++) {
                slots[i] = e.getPointerId(i);
                xs[i] = e.getX(i);
                ys[i] = e.getY(i);
            }
            if (mService == null) return true;
            try {
                mService.touch(id, action, e.getActionIndex(), e.getEventTime(), n, slots, xs, ys);
            } catch (RemoteException ignored) {
            }
            return true;
        }

        private boolean onMouse(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    focus(id);
                    mouseButton = linuxButton(e.getButtonState());
                    sendPointer(ILinuxWindows.POINTER_MOTION, 0, e.getX(), e.getY(), e.getEventTime());
                    sendPointer(ILinuxWindows.POINTER_BUTTON_DOWN, mouseButton, e.getX(), e.getY(), e.getEventTime());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    sendPointer(ILinuxWindows.POINTER_MOTION, 0, e.getX(), e.getY(), e.getEventTime());
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    sendPointer(ILinuxWindows.POINTER_BUTTON_UP, mouseButton, e.getX(), e.getY(), e.getEventTime());
                    return true;
                default:
                    return true;
            }
        }

        private boolean onGenericMotion(MotionEvent e) {
            if (!e.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_HOVER_ENTER:
                case MotionEvent.ACTION_HOVER_MOVE:
                    sendPointer(ILinuxWindows.POINTER_MOTION, 0, e.getX(), e.getY(), e.getEventTime());
                    return true;
                case MotionEvent.ACTION_HOVER_EXIT:
                    sendPointer(ILinuxWindows.POINTER_LEAVE, 0, 0f, 0f, e.getEventTime());
                    return true;
                case MotionEvent.ACTION_SCROLL:
                    sendPointer(ILinuxWindows.POINTER_SCROLL, 0,
                        e.getAxisValue(MotionEvent.AXIS_HSCROLL) * 15f, -e.getAxisValue(MotionEvent.AXIS_VSCROLL) * 15f,
                        e.getEventTime());
                    return true;
                default:
                    return false;
            }
        }
    }

    private static int linuxButton(int buttonState) {
        if ((buttonState & MotionEvent.BUTTON_SECONDARY) != 0) return ILinuxWindows.BTN_RIGHT;
        if ((buttonState & MotionEvent.BUTTON_TERTIARY) != 0) return ILinuxWindows.BTN_MIDDLE;
        return ILinuxWindows.BTN_LEFT;
    }

    /** Key codes the host maps to evdev (see wayland-host/src/keymap.rs); others stay with Android. */
    private static boolean isForwardedKey(int code) {
        return (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9)
            || (code >= KeyEvent.KEYCODE_DPAD_UP && code <= KeyEvent.KEYCODE_DPAD_RIGHT)
            || (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_SPACE)
            || (code >= KeyEvent.KEYCODE_ENTER && code <= KeyEvent.KEYCODE_SLASH)
            || code == KeyEvent.KEYCODE_PAGE_UP || code == KeyEvent.KEYCODE_PAGE_DOWN
            || (code >= KeyEvent.KEYCODE_ESCAPE && code <= KeyEvent.KEYCODE_CAPS_LOCK)
            || code == KeyEvent.KEYCODE_META_LEFT || code == KeyEvent.KEYCODE_META_RIGHT
            || (code >= KeyEvent.KEYCODE_MOVE_HOME && code <= KeyEvent.KEYCODE_INSERT)
            || (code >= KeyEvent.KEYCODE_F1 && code <= KeyEvent.KEYCODE_F12);
    }

    private TextView shadowedText() {
        TextView t = new TextView(this);
        t.setTextColor(Color.WHITE);
        t.setShadowLayer(4f, 0f, 1f, Color.BLACK);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        return t;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setOnClickListener(listener);
        return b;
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // A build without -Pwayland=true has no host to test.
        if (!BuildConfig.WAYLAND_DISPLAY) {
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(8), 0);
        mStatus = shadowedText();
        mStatus.setSingleLine(true);
        mStatus.setEllipsize(TextUtils.TruncateAt.END);
        mStartButton = button("Start", v -> {
            mAutoStart = true;
            if (mService == null) connect();
            else startHost();
        });
        mStopButton = button("Stop", v -> {
            if (mService == null) return;
            try {
                mService.stop();
            } catch (RemoteException ignored) {
            }
        });
        mCrashButton = button("Crash compositor", v -> {
            if (mDebug == null) return;
            try {
                mDebug.crash();
            } catch (RemoteException ignored) {
            }
        });
        mProbeButton = button("Probe", v -> {
            if (mDebug == null) return;
            try {
                mDebug.probe();
            } catch (RemoteException ignored) {
            }
        });
        mCopyLogButton = button("Log copies", v -> {
            if (mDebug == null) return;
            mCopyLog = !mCopyLog;
            mCopyLogButton.setText(mCopyLog ? "Stop copy log" : "Log copies");
            try {
                mDebug.setCopyLog(mCopyLog);
            } catch (RemoteException ignored) {
            }
        });
        bar.addView(mStatus, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(mStartButton);
        bar.addView(mStopButton);
        root.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout debugBar = new LinearLayout(this);
        debugBar.setOrientation(LinearLayout.HORIZONTAL);
        debugBar.setPadding(dp(8), 0, dp(8), 0);
        debugBar.addView(mCrashButton);
        debugBar.addView(mProbeButton);
        debugBar.addView(mCopyLogButton);
        root.addView(debugBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        mPaneHost = new LinearLayout(this);
        mPaneHost.setOrientation(LinearLayout.VERTICAL);
        mEmpty = shadowedText();
        mEmpty.setGravity(Gravity.CENTER);
        mEmpty.setPadding(dp(24), dp(24), dp(24), dp(24));
        mPaneHost.addView(mEmpty, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT));
        root.addView(mPaneHost, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        showStopped("Connecting…", false);
        connect();
    }

    private void connect() {
        Intent intent = new Intent(this, LinuxWindowsService.class);
        try {
            // Started as well as bound, so the host outlives this activity.
            startService(intent);
            mBound = bindService(intent, mConnection, BIND_AUTO_CREATE);
            if (mBound && !mDebugBound) {
                Intent debug = new Intent(this, LinuxWindowsService.class).setAction(LinuxWindowsService.ACTION_DEBUG);
                mDebugBound = bindService(debug, mDebugConnection, 0);
            }
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "cannot reach the host service: " + e);
            mBound = false;
        }
        if (!mBound) showStopped("The Linux windows host cannot be reached.", true);
    }

    private void startHost() {
        if (mService == null) return;
        File dir = new File(SOCKET_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            showStopped("Cannot create " + SOCKET_DIR, true);
            return;
        }
        try {
            Os.chmod(SOCKET_DIR, 0700);
        } catch (ErrnoException e) {
            Log.w(LOG_TAG, "chmod " + SOCKET_DIR + ": " + e.getMessage());
        }
        File xkb = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/share/X11/xkb");
        String xkbRoot = xkb.isDirectory() ? xkb.getAbsolutePath() : null;
        String xwayland = null;
        if (getIntent().getBooleanExtra(EXTRA_XWAYLAND, false)) {
            File bin = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/Xwayland");
            if (bin.canExecute()) xwayland = bin.getAbsolutePath();
            else Log.w(LOG_TAG, "no executable " + bin);
        }
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getRealSize(size);
        float refresh = display.getRefreshRate();
        long framePeriodNanos = refresh > 1f ? (long) (1_000_000_000L / refresh) : 0L;
        try {
            mService.start(SOCKET_DIR, xkbRoot, xwayland, size.x, size.y, density(), framePeriodNanos);
        } catch (RemoteException ignored) {
        }
    }

    private void attached(int interfaceVersion, int hostPid) {
        if (isDestroyed()) return;
        if (interfaceVersion != ILinuxWindows.VERSION) {
            Log.w(LOG_TAG, "host speaks version " + interfaceVersion + ", this screen " + ILinuxWindows.VERSION);
            onHostDied();
            showStopped("The Linux windows host is a different version.", false);
            return;
        }
        mHostPid = hostPid;
    }

    private void hostState(int state, String message) {
        if (isDestroyed()) return;
        switch (state) {
            case ILinuxWindows.STATE_RUNNING:
                mAutoStart = false;
                mStatus.setText("Host pid " + mHostPid);
                mStartButton.setVisibility(View.GONE);
                mStopButton.setVisibility(View.VISIBLE);
                mCrashButton.setVisibility(View.VISIBLE);
                mEmpty.setText("Waiting for Linux windows.\n" + message);
                break;
            case ILinuxWindows.STATE_STARTING:
                mStatus.setText("Starting…");
                break;
            case ILinuxWindows.STATE_STOPPED:
                if (mAutoStart) {
                    mAutoStart = false;
                    mStatus.setText("Starting…");
                    startHost();
                    break;
                }
                showStopped(TextUtils.isEmpty(message) ? "Stopped." : message, true);
                mStartButton.setText("Start");
                break;
            case ILinuxWindows.STATE_DIED:
                clearPanes();
                showStopped(message, true);
                mStartButton.setText("Restart");
                break;
            default:
                showStopped(message, false);
                break;
        }
    }

    private void showStopped(String line, boolean offerStart) {
        mStatus.setText(line);
        mEmpty.setText(line);
        mEmpty.setVisibility(mPanes.size() == 0 ? View.VISIBLE : View.GONE);
        mStartButton.setVisibility(offerStart ? View.VISIBLE : View.GONE);
        mStopButton.setVisibility(View.GONE);
        mCrashButton.setVisibility(View.GONE);
    }

    /** The host process died: drop its views, say so once, offer Restart. */
    private void onHostDied() {
        if (mService == null && !mBound) return;
        if (mServiceBinder != null) {
            try {
                mServiceBinder.unlinkToDeath(mDeath, 0);
            } catch (RuntimeException ignored) {
            }
        }
        mServiceBinder = null;
        mService = null;
        mDebug = null;
        if (mDebugBound) {
            try {
                unbindService(mDebugConnection);
            } catch (IllegalArgumentException ignored) {
            }
            mDebugBound = false;
        }
        if (mBound) {
            // Unbind so Android does not bring the service back on its own; Restart does.
            try {
                unbindService(mConnection);
            } catch (IllegalArgumentException ignored) {
            }
            mBound = false;
        }
        clearPanes();
        if (isDestroyed()) return;
        showStopped("The Linux windows host stopped unexpectedly.", true);
        mStartButton.setText("Restart");
    }

    private void windowCreated(long id, String title, String appId, int clientPid, int flags) {
        if (mPanes.get(id) != null || isDestroyed()) return;
        Pane pane = new Pane(id);
        pane.title = title;
        pane.appId = appId;
        pane.clientPid = clientPid;
        pane.flags = flags;
        pane.updateLabel();
        mPanes.put(id, pane);
        mEmpty.setVisibility(View.GONE);
        mPaneHost.addView(pane.row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void removePane(long id) {
        Pane pane = mPanes.get(id);
        if (pane == null) return;
        mPanes.remove(id);
        mPaneHost.removeView(pane.row);
        if (mFocusedId == id) mFocusedId = 0;
        if (mPanes.size() == 0) mEmpty.setVisibility(View.VISIBLE);
    }

    private void clearPanes() {
        for (int i = mPanes.size() - 1; i >= 0; i--) mPaneHost.removeView(mPanes.valueAt(i).row);
        mPanes.clear();
        mFocusedId = 0;
        mEmpty.setVisibility(View.VISIBLE);
    }

    private void focus(long id) {
        if (mFocusedId == id) return;
        long old = mFocusedId;
        mFocusedId = id;
        if (mService != null) {
            try {
                mService.setFocus(id);
            } catch (RemoteException ignored) {
            }
        }
        Pane before = mPanes.get(old);
        if (before != null) before.updateLabel();
        Pane now = mPanes.get(id);
        if (now != null) now.updateLabel();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        if (mService != null && mFocusedId != 0 && mPanes.get(mFocusedId) != null
            && isForwardedKey(event.getKeyCode())
            && (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP)) {
            // The client repeats held keys itself (wl_keyboard.repeat_info).
            if (event.getRepeatCount() == 0) {
                try {
                    mService.key(mFocusedId, event.getKeyCode(), action == KeyEvent.ACTION_DOWN, event.getEventTime());
                } catch (RemoteException ignored) {
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mVisible = true;
        for (int i = 0; i < mPanes.size(); i++) mPanes.valueAt(i).setVisible(true);
    }

    @Override
    protected void onStop() {
        // A hidden window copies nothing and its client gets only the slow keep-alive; it keeps
        // its Surface and last frame, so it shows again at once.
        for (int i = 0; i < mPanes.size(); i++) mPanes.valueAt(i).setVisible(false);
        mVisible = false;
        // Keys held as the screen goes must not stay down in the window.
        focus(0);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        // The host keeps running; only this activity's views and listener go.
        if (mService != null) {
            for (int i = 0; i < mPanes.size(); i++) mPanes.valueAt(i).detach();
            try {
                mService.removeCallback(mCallback);
            } catch (RemoteException ignored) {
            }
        }
        if (mServiceBinder != null) {
            try {
                mServiceBinder.unlinkToDeath(mDeath, 0);
            } catch (RuntimeException ignored) {
            }
        }
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        if (mDebugBound) {
            unbindService(mDebugConnection);
            mDebugBound = false;
        }
        mService = null;
        mDebug = null;
        mPanes.clear();
        super.onDestroy();
    }
}
