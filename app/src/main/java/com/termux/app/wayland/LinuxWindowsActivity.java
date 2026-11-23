package com.termux.app.wayland;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
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
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.termux.BuildConfig;
import com.termux.R;

import java.io.File;

/**
 * The Linux windows screen: where Linux apps opened under Wayland Display show, until they have
 * a window page of their own on the wall. One window fills the screen below a row of tabs, one
 * tab per window (its title, and a ✕ that asks the app to close); tapping a tab shows that window.
 * The wallpaper shows through a window's transparent pixels.
 *
 * <p>The compositor runs in {@link LinuxWindowsService} in the {@code :linuxwindows} process, so
 * it outlives this screen: Back leaves the screen and every app keeps running, and opening it
 * again re-attaches to the live windows. When the last window closes the screen finishes.
 * {@link WaylandAppRunner} opens it after starting an app. A long press on the tab row shows the
 * developer controls (Start, Stop, Crash compositor, Probe, the copy log). Not exported.
 */
public final class LinuxWindowsActivity extends Activity {

    private static final String LOG_TAG = "TLWayland";

    /** {@code --es dump all}: write every window to files/wayland-dumps/<id>.png once connected. */
    public static final String EXTRA_DUMP = "dump";
    private static final long DUMP_DELAY_MS = 1000;
    private static final int DUMP_TRIES = 5;

    private static final int MAX_POINTS = ILinuxWindows.MAX_TOUCH_POINTERS;
    /** The widest a tab's title grows before it is ellipsized. */
    private static final int TAB_TITLE_MAX_DP = 200;

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final LongSparseArray<Pane> mPanes = new LongSparseArray<>();
    private final LinuxWindowTabs mTabs = new LinuxWindowTabs();

    private LinearLayout mTabRow;
    private FrameLayout mWindowHost;
    private TextView mEmpty;
    /** The developer controls, shown by a long press on the tab row. */
    private View mDevBar;
    private TextView mStatus;
    private Button mCopyLogButton;
    private boolean mCopyLog;
    private Button mDumpButton;
    /** The intent asked for a dump; done once the host runs and the windows are replayed. */
    private boolean mDumpPending;
    /** The host reported running and has not since stopped. */
    private boolean mHostRunning;

    private ILinuxWindows mService;
    private IBinder mServiceBinder;
    private boolean mBound;
    /** The debug interface, bound beside the host for Crash, Probe and the copy log. */
    private ILinuxWindowsDebug mDebug;
    private boolean mDebugBound;
    /** The activity is visible: the shown window is drawn and has focus. */
    private boolean mVisible;
    /** A window has been here since the screen opened: the last one closing finishes it. */
    private boolean mHadWindow;
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
                pane.updateTab();
            });
        }

        @Override
        public void onWindowDestroyed(long id) {
            mMain.post(() -> removePane(id));
        }

        @Override
        public void onXDisplay(String display) {}
    };

    /** One toplevel: its tab in the row and its TextureView in the window area. */
    private final class Pane implements TextureView.SurfaceTextureListener {
        final long id;
        final LinearLayout tab;
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
            tab = new LinearLayout(LinuxWindowsActivity.this);
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setOnClickListener(v -> showWindow(id));
            tab.setOnLongClickListener(v -> toggleDevBar());
            label = shadowedText();
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setMaxWidth(dp(TAB_TITLE_MAX_DP));
            label.setPadding(dp(12), dp(8), dp(4), dp(8));
            TextView close = shadowedText();
            close.setText("✕");
            close.setContentDescription(getString(R.string.linux_windows_close));
            close.setPadding(dp(8), dp(8), dp(12), dp(8));
            close.setOnClickListener(v -> {
                if (mService != null) {
                    try {
                        mService.close(id);
                    } catch (RemoteException ignored) {
                    }
                }
            });
            tab.addView(label, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
            tab.addView(close, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

            view = new TextureView(LinuxWindowsActivity.this);
            view.setOpaque(false);
            view.setSurfaceTextureListener(this);
            view.setOnTouchListener((v, e) -> onTouch(e));
            view.setOnGenericMotionListener((v, e) -> onGenericMotion(e));
        }

        boolean shown() {
            return mTabs.shown() == id;
        }

        void updateTab() {
            String name = !TextUtils.isEmpty(title) ? title
                : !TextUtils.isEmpty(appId) ? appId : getString(R.string.linux_windows_untitled);
            if (!TextUtils.equals(label.getText(), name)) label.setText(name);
            label.setTypeface(null, shown() ? Typeface.BOLD : Typeface.NORMAL);
            tab.setAlpha(shown() ? 1f : 0.7f);
        }

        /** Hands the Surface to the host. */
        void attach() {
            if (mService == null || surface == null) return;
            try {
                mService.configure(id, view.getWidth(), view.getHeight(), density());
                mService.setSurface(id, surface);
                mService.setVisible(id, mVisible && shown());
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

    /** The output scale: the Display scale setting, as X11 Display reads it. */
    private float density() {
        return LinuxDisplayScale.current(this);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // A build without -Pwayland=true has no host.
        if (!BuildConfig.WAYLAND_DISPLAY) {
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // Below the status bar and above the navigation bar; the keyboard is not handled yet.
        root.setFitsSystemWindows(true);

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setFillViewport(true);
        mTabRow = new LinearLayout(this);
        mTabRow.setOrientation(LinearLayout.HORIZONTAL);
        mTabRow.setGravity(Gravity.CENTER_VERTICAL);
        mTabRow.setMinimumHeight(dp(40));
        mTabRow.setOnLongClickListener(v -> toggleDevBar());
        tabScroll.addView(mTabRow, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(tabScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        mDevBar = devBar();
        mDevBar.setVisibility(View.GONE);
        root.addView(mDevBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        mWindowHost = new FrameLayout(this);
        mEmpty = shadowedText();
        mEmpty.setGravity(Gravity.CENTER);
        mEmpty.setPadding(dp(24), dp(24), dp(24), dp(24));
        mEmpty.setText(R.string.linux_windows_opening);
        mWindowHost.addView(mEmpty, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(mWindowHost, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        handleDumpIntent(getIntent());
        connect();
    }

    /** Developer controls; their words are not product copy. */
    private View devBar() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(8), 0);
        mStatus = shadowedText();
        mStatus.setSingleLine(true);
        mStatus.setPadding(0, 0, dp(8), 0);
        bar.addView(mStatus);
        bar.addView(button("Start", v -> {
            if (mService == null) connect();
            else startHost();
        }));
        bar.addView(button("Stop", v -> {
            if (mService == null) return;
            try {
                mService.stop();
            } catch (RemoteException ignored) {
            }
        }));
        bar.addView(button("Crash compositor", v -> {
            if (mDebug == null) return;
            try {
                mDebug.crash();
            } catch (RemoteException ignored) {
            }
        }));
        bar.addView(button("Probe", v -> {
            if (mDebug == null) return;
            try {
                mDebug.probe();
            } catch (RemoteException ignored) {
            }
        }));
        mCopyLogButton = button("Log copies", v -> {
            if (mDebug == null) return;
            mCopyLog = !mCopyLog;
            mCopyLogButton.setText(mCopyLog ? "Stop copy log" : "Log copies");
            try {
                mDebug.setCopyLog(mCopyLog);
            } catch (RemoteException ignored) {
            }
        });
        bar.addView(mCopyLogButton);
        mDumpButton = button("Dump", v -> dumpAll(1));
        bar.addView(mDumpButton);
        scroll.addView(bar);
        return scroll;
    }

    private boolean toggleDevBar() {
        mDevBar.setVisibility(mDevBar.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        return true;
    }

    private void connect() {
        Intent intent = new Intent(this, LinuxWindowsService.class);
        try {
            mBound = bindService(intent, mConnection, BIND_AUTO_CREATE);
            if (mBound && !mDebugBound) {
                Intent debug = new Intent(this, LinuxWindowsService.class).setAction(LinuxWindowsService.ACTION_DEBUG);
                mDebugBound = bindService(debug, mDebugConnection, 0);
            }
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "cannot reach the host service: " + e);
            mBound = false;
        }
        if (!mBound) showStopped("not reachable");
    }

    /** The developer Start: the same start a launch makes ({@link WaylandAppRunner}). */
    private void startHost() {
        if (mService == null) return;
        File dir = new File(WaylandAppRunner.SOCKET_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            showStopped("cannot create " + WaylandAppRunner.SOCKET_DIR);
            return;
        }
        try {
            Os.chmod(WaylandAppRunner.SOCKET_DIR, 0700);
        } catch (ErrnoException e) {
            Log.w(LOG_TAG, "chmod " + WaylandAppRunner.SOCKET_DIR + ": " + e.getMessage());
        }
        WaylandAppRunner.HostStart start = WaylandAppRunner.hostStart(this);
        try {
            // The service is started as well as bound, so the host outlives this screen.
            startService(new Intent(this, LinuxWindowsService.class));
            mService.start(WaylandAppRunner.SOCKET_DIR, start.xkbConfigRoot, start.xwaylandBinary,
                start.width, start.height, start.scale, start.framePeriodNanos);
        } catch (RemoteException | RuntimeException e) {
            Log.w(LOG_TAG, "start: " + e);
        }
    }

    private void attached(int interfaceVersion, int hostPid) {
        if (isDestroyed()) return;
        if (interfaceVersion != ILinuxWindows.VERSION) {
            Log.w(LOG_TAG, "host speaks version " + interfaceVersion + ", this screen " + ILinuxWindows.VERSION);
            onHostDied();
            return;
        }
        mHostPid = hostPid;
    }

    private void hostState(int state, String message) {
        if (isDestroyed()) return;
        mHostRunning = state == ILinuxWindows.STATE_RUNNING;
        switch (state) {
            case ILinuxWindows.STATE_RUNNING:
                mStatus.setText("Host pid " + mHostPid + ". " + message);
                if (mDumpPending) {
                    mDumpPending = false;
                    // The replayed windows arrive after the state; give them a moment.
                    mMain.postDelayed(() -> dumpAll(DUMP_TRIES), DUMP_DELAY_MS);
                }
                break;
            case ILinuxWindows.STATE_STARTING:
                mStatus.setText("Starting…");
                break;
            case ILinuxWindows.STATE_DIED:
                clearPanes();
                showStopped(message);
                break;
            default:
                // Stopped or unavailable: the developer bar says so; the windows go by their own callbacks.
                mStatus.setText(TextUtils.isEmpty(message) ? "Stopped." : message);
                break;
        }
    }

    /** The host is gone: the empty area says so; the developer bar says why. */
    private void showStopped(String why) {
        if (mStatus != null) mStatus.setText(why);
        if (mEmpty == null) return;
        mEmpty.setText(R.string.linux_windows_stopped);
        mEmpty.setVisibility(mTabs.size() == 0 ? View.VISIBLE : View.GONE);
    }

    /** The host process died: drop its views and say so once. */
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
            // Unbind so Android does not bring the service back on its own; a launch does.
            try {
                unbindService(mConnection);
            } catch (IllegalArgumentException ignored) {
            }
            mBound = false;
        }
        clearPanes();
        if (isDestroyed()) return;
        showStopped("The host process died.");
    }

    private void windowCreated(long id, String title, String appId, int clientPid, int flags) {
        if (mPanes.get(id) != null || isDestroyed()) return;
        Pane pane = new Pane(id);
        pane.title = title;
        pane.appId = appId;
        pane.clientPid = clientPid;
        pane.flags = flags;
        mPanes.put(id, pane);
        mHadWindow = true;
        mEmpty.setVisibility(View.GONE);
        mTabRow.addView(pane.tab, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        mWindowHost.addView(pane.view, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        long before = mTabs.shown();
        mTabs.add(id);
        applyShown(before);
    }

    private void removePane(long id) {
        Pane pane = mPanes.get(id);
        if (pane == null) return;
        mPanes.remove(id);
        mTabRow.removeView(pane.tab);
        mWindowHost.removeView(pane.view);
        if (mFocusedId == id) mFocusedId = 0;
        mTabs.remove(id);
        if (mTabs.size() == 0) {
            // The last window closed: nothing is left to show here.
            if (mHadWindow && !isFinishing()) finish();
            return;
        }
        // The closed window's view is gone with it; only the newly shown one needs telling.
        applyShown(LinuxWindowTabs.NONE);
    }

    private void clearPanes() {
        for (int i = mPanes.size() - 1; i >= 0; i--) {
            Pane pane = mPanes.valueAt(i);
            mTabRow.removeView(pane.tab);
            mWindowHost.removeView(pane.view);
        }
        mPanes.clear();
        mTabs.clear();
        mFocusedId = 0;
        if (mEmpty != null) mEmpty.setVisibility(View.VISIBLE);
    }

    /** A tab was tapped. */
    private void showWindow(long id) {
        long before = mTabs.shown();
        if (mTabs.show(id)) applyShown(before);
    }

    /**
     * Makes the views follow {@link #mTabs}: only the shown window's view is visible, the host is
     * told the window shown before ({@code before}) is hidden, and the shown one takes focus while
     * the screen is visible.
     */
    private void applyShown(long before) {
        long shown = mTabs.shown();
        for (int i = 0; i < mPanes.size(); i++) {
            Pane pane = mPanes.valueAt(i);
            int visibility = pane.id == shown ? View.VISIBLE : View.INVISIBLE;
            if (pane.view.getVisibility() != visibility) pane.view.setVisibility(visibility);
            pane.updateTab();
        }
        if (before != shown) {
            Pane old = mPanes.get(before);
            if (old != null) old.setVisible(false);
        }
        Pane now = mPanes.get(shown);
        if (now != null) {
            now.setVisible(mVisible);
            if (mVisible) focus(shown);
        }
    }

    /** {@code --es dump all}: at once when the host already runs (a launch of the live screen
     *  arrives in onNewIntent), else when it does. */
    private void handleDumpIntent(Intent intent) {
        if (intent == null || !"all".equals(intent.getStringExtra(EXTRA_DUMP))) return;
        if (mHostRunning) mMain.postDelayed(() -> dumpAll(DUMP_TRIES), DUMP_DELAY_MS);
        else mDumpPending = true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDumpIntent(intent);
    }

    /** Asks the host to write every window to files/wayland-dumps/<id>.png (see logcat TLWayland). */
    private void dumpAll(int triesLeft) {
        if (isDestroyed()) return;
        if (mDebug == null) {
            if (triesLeft > 1) mMain.postDelayed(() -> dumpAll(triesLeft - 1), DUMP_DELAY_MS);
            else Log.w(LOG_TAG, "dump: the debug interface is not connected");
            return;
        }
        File dir = new File(getFilesDir(), "wayland-dumps");
        Log.i(LOG_TAG, "dump: " + mPanes.size() + " window(s) to " + dir);
        for (int i = 0; i < mPanes.size(); i++) {
            long id = mPanes.keyAt(i);
            try {
                mDebug.dumpWindow(id, new File(dir, id + ".png").getPath());
            } catch (RemoteException ignored) {
            }
        }
    }

    private void focus(long id) {
        if (mFocusedId == id) return;
        mFocusedId = id;
        if (mService != null) {
            try {
                mService.setFocus(id);
            } catch (RemoteException ignored) {
            }
        }
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
        Pane shown = mPanes.get(mTabs.shown());
        if (shown != null) {
            shown.setVisible(true);
            focus(shown.id);
        }
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
        // The host keeps running; only this screen's views and listener go.
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
