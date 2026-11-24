package com.termux.app.wayland;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.termux.BuildConfig;
import com.termux.app.theme.TermuxThemeManager;
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
 * developer controls (Start, Stop, Crash compositor, Probe, the copy log). The keyboard glyph at
 * the row's end shows the launcher's own keyboard below the window, which types into the focused
 * window as key presses ({@link LinuxWindowsKeyboard}); Back puts it away first.
 *
 * <p>Each window draws at its app's own Scale, else at the screen's density
 * ({@link LinuxDisplayScale#windowScale}). A hold on a corner of the window area brings out the
 * scale rail ({@link LinuxWindowFrame}); a stop chosen there is stored as the app's Scale, the
 * value its settings page shows, and every open window of that app takes it at once. A window
 * with no app behind it (started from a shell) keeps the stop while it is open. X apps under
 * Xwayland share the X server's one scale (the screen's): their hold says so instead of showing
 * the rail. A Scale changed on the settings page reaches open windows when this screen is next
 * shown. Not exported.
 */
public final class LinuxWindowsActivity extends Activity {

    private static final String LOG_TAG = "TLWayland";

    /** {@code --es dump all}: write every window to files/wayland-dumps/<id>.png once connected. */
    private boolean mStartPending;
    public static final String EXTRA_DUMP = "dump";
    /** Developer extra, {@code --ez start true}: what the developer row's Start does, for a shell. */
    static final String EXTRA_START = "start";
    private static final long DUMP_DELAY_MS = 1000;
    private static final int DUMP_TRIES = 5;

    private static final int MAX_POINTS = ILinuxWindows.MAX_TOUCH_POINTERS;
    /** {@code putExtra(EXTRA_OPENING, true)}: the runner opened the screen with an app just started. */
    static final String EXTRA_OPENING = "opening";
    /** {@code putExtra(EXTRA_SHOW_WINDOW, id)}: bring the window with this id forward, if it is open. */
    static final String EXTRA_SHOW_WINDOW = "show_window";
    /** How long "no Linux apps" shows before the screen closes itself. */
    private static final long NO_APPS_CLOSE_MS = 2500;
    /** The developer status wraps to this many lines. */
    private static final int STATUS_MAX_LINES = 3;
    /** Used only when the theme gives no colour for a role. */
    private static final int FALLBACK_SURFACE = 0xFF202020;
    private static final int FALLBACK_ON_SURFACE = 0xFFFFFFFF;
    /** How much of the accent the shown tab's fill carries over the unshown fill. */
    private static final float SHOWN_TAB_TINT = 0.22f;

    /**
     * The tab row's colours, read from the launcher's own roles (the Material roles the scheme
     * and the wallpaper palette override, see TermuxThemeManager): the row sits on the panel
     * surface, a tab's title is on-surface-variant, and the shown tab is the unshown fill tinted
     * with the primary accent ({@link #SHOWN_TAB_TINT}).
     */
    private int mSurface = FALLBACK_SURFACE;
    private int mOnSurface = FALLBACK_ON_SURFACE;
    private int mOnSurfaceVariant = FALLBACK_ON_SURFACE;
    private int mShownTab = Color.TRANSPARENT;
    private int mUnshownTab = Color.TRANSPARENT;
    private int mTabOutline = Color.TRANSPARENT;

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final LongSparseArray<Pane> mPanes = new LongSparseArray<>();
    private final LinuxWindowTabs mTabs = new LinuxWindowTabs();

    private HorizontalScrollView mTabScroll;
    private LinearLayout mTabRow;
    private int mTabViewportWidth;
    private LinuxWindowFrame mWindowHost;
    /** Per-app settings, where a window's app keeps its Scale; read fresh on each use. */
    @Nullable private com.termux.app.x11.X11AppSettingsStore mAppSettings;
    private TextView mEmpty;
    /** The launcher's keyboard below the window area; the window gives up its height to it. */
    private final LinuxWindowsKeyboard mKeyboard = new LinuxWindowsKeyboard(new LinuxWindowsKeyboard.Host() {
        @Override
        public void typeKey(int keyCode, boolean down) {
            sendKey(keyCode, down, SystemClock.uptimeMillis());
        }

        @Override
        public void hideKeyboard() {
            setKeyboardShown(false);
        }
    });
    private FrameLayout mKeyboardHost;
    private ImageView mKeyboardToggle;
    /** The developer controls, shown by a long press on the tab row. */
    private View mDevBar;
    private TextView mStatus;
    private Button mCopyLogButton;
    private boolean mCopyLog;
    private Button mDumpButton;
    /** The intent asked for a dump; done once the host runs and the windows are replayed. */
    private boolean mDumpPending;
    /** A window to bring forward once the host replays it (the screen was opened by {@link #EXTRA_SHOW_WINDOW}); -1 none. */
    private long mShowWindowPending = -1L;
    /** The host reported running and has not since stopped. */
    private boolean mHostRunning;
    private boolean mHostStarting;
    /** The host has said its state, or could not be reached. */
    private boolean mStateKnown;
    /** The host died or could not be reached. */
    private boolean mStopped;
    /** The runner's launch gave up waiting for the host. */
    private boolean mLaunchFailed;
    /** The runner opened this screen for an app it just started: "Opening" until it has a window. */
    private boolean mOpeningExtra;
    private boolean mOpeningExpired;
    private final Runnable mCloseWhenEmpty = () -> {
        if (mTabs.size() == 0 && !isFinishing()) finish();
    };
    private final Runnable mOpeningExpiry = () -> {
        mOpeningExpired = true;
        settle();
    };
    private final WaylandAppRunner.PendingListener mPendingListener = started -> {
        if (!started) mLaunchFailed = true;
        settle();
    };

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
    /** Android's clipboard and the Linux apps', while this screen is in front. */
    private final LinuxClipboardBridge mClipboard = new LinuxClipboardBridge(this, this::sendClipboardText);
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
                if (mStartPending) {
                    mStartPending = false;
                    startHost();
                }
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

        @Override
        public void onClipboardText(String text) {
            mMain.post(() -> mClipboard.fromLinux(text));
        }
    };

    /** One toplevel: its tab in the row and its TextureView in the window area. */
    private final class Pane implements TextureView.SurfaceTextureListener {
        final long id;
        final LinearLayout tab;
        final TextView label;
        final TextView close;
        /** The tab's rounded fill; recoloured, never replaced. */
        final GradientDrawable shape = new GradientDrawable();
        final TextureView view;
        Surface surface;
        String title = "";
        String appId = "";
        int clientPid;
        int flags;
        int width;
        int height;
        /** What the host was last told: the view's size and the scale; 0 before the first configure. */
        int sentWidth;
        int sentHeight;
        float sentScale;
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
            tab.setMinimumWidth(res(R.dimen.linux_windows_tab_min_width));
            shape.setCornerRadius(res(R.dimen.linux_windows_tab_radius));
            shape.setStroke(res(R.dimen.linux_windows_tab_stroke), mTabOutline);
            tab.setBackground(shape);
            label = chromeText();
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.linux_windows_tab_text));
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER_VERTICAL);
            // The title ends where the close target begins.
            label.setPaddingRelative(res(R.dimen.linux_windows_tab_inner_padding), 0, 0, 0);
            close = chromeText();
            close.setText("✕");
            close.setGravity(Gravity.CENTER);
            close.setContentDescription(getString(R.string.linux_windows_close));
            close.setOnClickListener(v -> {
                if (mService != null) {
                    try {
                        mService.close(id);
                    } catch (RemoteException ignored) {
                    }
                }
            });
            tab.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            tab.addView(close, new LinearLayout.LayoutParams(res(R.dimen.linux_windows_close_target),
                LinearLayout.LayoutParams.MATCH_PARENT));

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
            String name = LinuxWindowTabs.label(WaylandDesktopServer.tabTitle(title), appId,
                getString(R.string.linux_windows_untitled));
            if (!TextUtils.equals(label.getText(), name)) label.setText(name);
            label.setTypeface(null, shown() ? Typeface.BOLD : Typeface.NORMAL);
            int text = shown() ? mOnSurface : mOnSurfaceVariant;
            label.setTextColor(text);
            close.setTextColor(text);
            shape.setColor(shown() ? mShownTab : mUnshownTab);
        }

        /** Hands the Surface to the host. */
        void attach() {
            if (mService == null || surface == null) return;
            try {
                sendConfigure(view.getWidth(), view.getHeight());
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
            try {
                sendConfigure(width, height);
            } catch (RemoteException ignored) {
            }
        }

        /** Whether this window can take a scale of its own: an X window shares the X server's. */
        boolean scales() {
            return (flags & ILinuxWindows.WINDOW_X11) == 0;
        }

        /** The catalogue app this window was opened for, or null (a shell started it, or it is unmatched). */
        @Nullable
        String app() {
            return WaylandAppRunner.shared(LinuxWindowsActivity.this).appFor(id);
        }

        /** The scale this window draws at: its app's Scale, the rail's stop kept for it, else the screen's. */
        float scale() {
            float density = getResources().getDisplayMetrics().density;
            if (!scales()) return LinuxDisplayScale.current(LinuxWindowsActivity.this);
            String app = app();
            if (app != null) return LinuxDisplayScale.windowScale(appSettings().scale(app), density);
            float kept = WaylandAppRunner.shared(LinuxWindowsActivity.this).windowScale(id);
            return kept > 0f ? kept : LinuxDisplayScale.windowScale(null, density);
        }

        /** Tells the host the view's size and the window's scale, when either changed. */
        void sendConfigure(int w, int h) throws RemoteException {
            if (mService == null || w <= 0 || h <= 0) return;
            float scale = scale();
            if (w == sentWidth && h == sentHeight && scale == sentScale) return;
            mService.configure(id, w, h, scale);
            sentWidth = w;
            sentHeight = h;
            sentScale = scale;
        }

        /** Re-reads the scale and tells the host if it changed. */
        void reconfigure() {
            try {
                sendConfigure(view.getWidth(), view.getHeight());
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

    private TextView shadowedText() {
        TextView t = new TextView(this);
        t.setTextColor(Color.WHITE);
        t.setShadowLayer(4f, 0f, 1f, Color.BLACK);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        return t;
    }

    /** Text on the tab row's or developer row's surface: the on-surface colour, no shadow. */
    private TextView chromeText() {
        TextView t = new TextView(this);
        t.setTextColor(mOnSurface);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        return t;
    }

    private int themeColor(int attr, int fallback) {
        return MaterialColors.getColor(this, attr, fallback);
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

    @NonNull
    private com.termux.app.x11.X11AppSettingsStore appSettings() {
        com.termux.app.x11.X11AppSettingsStore store = mAppSettings;
        if (store == null) mAppSettings = store = new com.termux.app.x11.X11AppSettingsStore(this);
        return store;
    }

    /** The scale rail's stops are the shown window's scale. */
    private final LinuxWindowFrame.Host mScaleHost = new LinuxWindowFrame.Host() {
        @Override
        public boolean hasShownWindow() {
            return mPanes.get(mTabs.shown()) != null;
        }

        @Override
        public boolean shownWindowScales() {
            Pane pane = mPanes.get(mTabs.shown());
            return pane != null && pane.scales();
        }

        @Override
        public int currentScaleStep() {
            Pane pane = mPanes.get(mTabs.shown());
            return pane == null ? 0 : LinuxDisplayScale.railStep(pane.scale());
        }

        @Override
        public void onScaleStepChosen(int step) {
            Pane pane = mPanes.get(mTabs.shown());
            if (pane != null && pane.scales()) applyScaleStep(pane, step);
        }

        @Override
        public void onScaleRailUsed() {
            mWindowHost.dismissRail();
        }
    };

    /**
     * {@code step} becomes {@code pane}'s app's Scale, and every open window of that app is told;
     * a window with no app keeps it while it is open.
     */
    private void applyScaleStep(@NonNull Pane pane, int step) {
        String app = pane.app();
        if (app == null) {
            WaylandAppRunner.shared(this).setWindowScale(pane.id, LinuxDisplayScale.stepScale(step));
            pane.reconfigure();
            return;
        }
        appSettings().setScale(app, com.termux.app.x11.DisplayScaleSteps.scaleValue(step));
        for (int i = 0; i < mPanes.size(); i++) {
            Pane other = mPanes.valueAt(i);
            if (other == pane || app.equals(other.app())) other.reconfigure();
        }
    }

    private int res(int dimenId) {
        return getResources().getDimensionPixelSize(dimenId);
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
        // The launcher's scheme or wallpaper palette, as every launcher activity gets it.
        TermuxThemeManager.applyThemeOverlays(this);
        mSurface = themeColor(com.termux.shared.R.attr.termuxColorSurfacePanel, FALLBACK_SURFACE);
        mOnSurface = themeColor(com.termux.shared.R.attr.termuxColorOnSurface, FALLBACK_ON_SURFACE);
        mOnSurfaceVariant = themeColor(com.termux.shared.R.attr.termuxColorOnSurfaceVariant, FALLBACK_ON_SURFACE);
        mUnshownTab = themeColor(com.termux.shared.R.attr.termuxColorSurfacePanelHigh, FALLBACK_SURFACE);
        mShownTab = MaterialColors.layer(mUnshownTab,
            themeColor(com.termux.shared.R.attr.termuxColorPrimary, FALLBACK_ON_SURFACE), SHOWN_TAB_TINT);
        mTabOutline = themeColor(com.termux.shared.R.attr.termuxColorOutlineVariant, FALLBACK_SURFACE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // Below the status bar and above the navigation bar; the launcher's keyboard sits at the
        // bottom of the root, so the system IME never shows here.
        root.setFitsSystemWindows(true);

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        mTabScroll = tabScroll;
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setFillViewport(true);
        tabScroll.setBackgroundColor(mSurface);
        mTabRow = new LinearLayout(this);
        mTabRow.setOrientation(LinearLayout.HORIZONTAL);
        mTabRow.setGravity(Gravity.CENTER_VERTICAL);
        int sidePad = res(R.dimen.linux_windows_start_padding);
        int gap = res(R.dimen.linux_windows_tab_gap);
        int rowPad = res(R.dimen.linux_windows_tab_row_padding);
        mTabRow.setPaddingRelative(sidePad, rowPad, sidePad, rowPad);
        // A transparent divider is the gap between tabs.
        GradientDrawable gapDrawable = new GradientDrawable();
        gapDrawable.setSize(gap, 0);
        mTabRow.setDividerDrawable(gapDrawable);
        mTabRow.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        mTabRow.setMinimumHeight(res(R.dimen.linux_windows_tab_height) + 2 * rowPad);
        mTabRow.setOnLongClickListener(v -> toggleDevBar());
        tabScroll.addView(mTabRow, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        // The tabs share the row's width; a changed width sets theirs after this layout pass.
        tabScroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int width = r - l;
            if (width == mTabViewportWidth) return;
            mTabViewportWidth = width;
            mTabRow.post(this::sizeTabs);
        });
        // The tabs take the row; the keyboard glyph sits at its end.
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setBackgroundColor(mSurface);
        topRow.addView(tabScroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        mKeyboardToggle = keyboardToggle();
        int target = res(R.dimen.linux_windows_close_target);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(target, target);
        toggleParams.setMarginEnd(res(R.dimen.linux_windows_tab_gap));
        topRow.addView(mKeyboardToggle, toggleParams);
        root.addView(topRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        mDevBar = devBar();
        mDevBar.setVisibility(View.GONE);
        root.addView(mDevBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        mWindowHost = new LinuxWindowFrame(this, mScaleHost);
        mEmpty = shadowedText();
        mEmpty.setGravity(Gravity.CENTER);
        mEmpty.setPadding(dp(24), dp(24), dp(24), dp(24));
        mWindowHost.addWindowView(mEmpty);
        root.addView(mWindowHost, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        // Shown, it takes its height from the window area, whose view resizes and so re-configures
        // the window (onSurfaceTextureSizeChanged); hidden, the window has the room back.
        mKeyboardHost = new FrameLayout(this);
        mKeyboardHost.setBackgroundColor(mSurface);
        mKeyboardHost.setVisibility(View.GONE);
        root.addView(mKeyboardHost, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);

        mOpeningExtra = getIntent() != null && getIntent().getBooleanExtra(EXTRA_OPENING, false);
        WaylandAppRunner.shared(this).setPendingListener(mPendingListener);
        settle();
        handleStartIntent(getIntent());
        handleDumpIntent(getIntent());
        handleShowWindowIntent(getIntent());
        connect();
    }

    /**
     * What the empty area says, and whether the screen closes itself. "Opening" only while a launch
     * is in flight; with no host and nothing launching it says so plainly and closes after a moment
     * (never starting the host: its Start is the developer row's).
     */
    private void settle() {
        if (isDestroyed() || mEmpty == null) return;
        mMain.removeCallbacks(mCloseWhenEmpty);
        if (mTabs.size() > 0) return;
        boolean opening = WaylandAppRunner.shared(this).launchPending() || mHostStarting
            || (mOpeningExtra && !mOpeningExpired && (mHostRunning || !mStateKnown));
        boolean close = false;
        int text;
        if (mStopped && mHadWindow) {
            text = R.string.linux_windows_stopped;
        } else if (opening) {
            text = R.string.linux_windows_opening;
        } else if (mLaunchFailed) {
            text = R.string.linux_wayland_start_failed;
            close = true;
        } else if (!mStateKnown) {
            text = 0;
        } else {
            text = R.string.linux_windows_no_apps;
            close = true;
        }
        CharSequence current = mEmpty.getText();
        CharSequence wanted = text == 0 ? "" : getString(text);
        if (!TextUtils.equals(current, wanted)) mEmpty.setText(wanted);
        mEmpty.setVisibility(View.VISIBLE);
        // The developer row open means the developer is driving the host by hand.
        if (close && mDevBar.getVisibility() != View.VISIBLE) mMain.postDelayed(mCloseWhenEmpty, NO_APPS_CLOSE_MS);
    }

    /** The glyph that shows and hides the keyboard; it shows the action it does. */
    private ImageView keyboardToggle() {
        ImageView toggle = new ImageView(this);
        toggle.setScaleType(ImageView.ScaleType.CENTER);
        toggle.setImageTintList(ColorStateList.valueOf(mOnSurfaceVariant));
        toggle.setOnClickListener(v -> setKeyboardShown(!keyboardShown()));
        updateKeyboardToggle(toggle, false);
        return toggle;
    }

    private void updateKeyboardToggle(ImageView toggle, boolean shown) {
        toggle.setImageResource(shown ? R.drawable.ic_ms_keyboard_hide : R.drawable.ic_ms_keyboard);
        toggle.setContentDescription(getString(shown ? R.string.linux_windows_hide_keyboard
            : R.string.linux_windows_show_keyboard));
    }

    private boolean keyboardShown() {
        return mKeyboardHost != null && mKeyboardHost.getVisibility() == View.VISIBLE;
    }

    /** Shows or hides the keyboard; only a change touches the views. */
    private void setKeyboardShown(boolean shown) {
        if (mKeyboardHost == null || shown == keyboardShown()) return;
        if (shown) {
            View keys = mKeyboard.view(this);
            if (keys == null) return;
            if (keys.getParent() == null) {
                mKeyboardHost.addView(keys, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT));
            }
            mKeyboardHost.setVisibility(View.VISIBLE);
        } else {
            mKeyboard.reset();
            mKeyboardHost.setVisibility(View.GONE);
        }
        updateKeyboardToggle(mKeyboardToggle, shown);
    }

    /** Developer controls; their words are not product copy. */
    private View devBar() {
        // The status on its own line, the buttons wrapping below it within the screen's width.
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(mSurface);
        // The same start edge as the tab titles.
        int pad = res(R.dimen.linux_windows_start_padding);
        box.setPaddingRelative(pad, 0, pad, 0);
        mStatus = chromeText();
        mStatus.setMaxLines(STATUS_MAX_LINES);
        mStatus.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(mStatus, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        WrapRow bar = new WrapRow(this);
        box.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
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
        return box;
    }

    /** Children left to right, starting a new line when the next does not fit the width. */
    private static final class WrapRow extends ViewGroup {
        WrapRow(android.content.Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int available = MeasureSpec.getSize(widthSpec);
            int x = 0;
            int lineHeight = 0;
            int height = 0;
            int widest = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                child.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int w = child.getMeasuredWidth();
                if (x > 0 && x + w > available) {
                    height += lineHeight;
                    x = 0;
                    lineHeight = 0;
                }
                x += w;
                widest = Math.max(widest, x);
                lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
            }
            height += lineHeight;
            setMeasuredDimension(resolveSize(widest, widthSpec), resolveSize(height, heightSpec));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int available = r - l;
            int x = 0;
            int y = 0;
            int lineHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                int w = child.getMeasuredWidth();
                if (x > 0 && x + w > available) {
                    y += lineHeight;
                    x = 0;
                    lineHeight = 0;
                }
                child.layout(x, y, x + w, y + child.getMeasuredHeight());
                x += w;
                lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
            }
        }
    }

    private boolean toggleDevBar() {
        mDevBar.setVisibility(mDevBar.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        settle();
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
        mHostStarting = state == ILinuxWindows.STATE_STARTING;
        mStateKnown = true;
        if (mHostRunning || mHostStarting) mStopped = false;
        switch (state) {
            case ILinuxWindows.STATE_RUNNING:
                mClipboard.pushCurrent();
                mStatus.setText("Host pid " + mHostPid + ". " + message);
                if (mOpeningExtra && !mOpeningExpired) {
                    mMain.removeCallbacks(mOpeningExpiry);
                    mMain.postDelayed(mOpeningExpiry, WaylandAppRunner.START_TIMEOUT_MS);
                }
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
        settle();
    }

    /** The host is gone: the empty area says so; the developer bar says why. */
    private void showStopped(String why) {
        if (mStatus != null) mStatus.setText(why);
        mStopped = true;
        mStateKnown = true;
        mHostRunning = false;
        mHostStarting = false;
        settle();
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
        mTabRow.addView(pane.tab, new LinearLayout.LayoutParams(res(R.dimen.linux_windows_tab_min_width),
            res(R.dimen.linux_windows_tab_height)));
        sizeTabs();
        mWindowHost.addWindowView(pane.view);
        // The runner ties the window to its app from its own callback, which may land after this
        // one: look again once both have run.
        mMain.post(pane::reconfigure);
        long before = mTabs.shown();
        mTabs.add(id);
        applyShown(before);
        if (id == mShowWindowPending) {
            mShowWindowPending = -1L;
            showWindow(id);
        }
    }

    private void removePane(long id) {
        Pane pane = mPanes.get(id);
        if (pane == null) return;
        mPanes.remove(id);
        mTabRow.removeView(pane.tab);
        mWindowHost.removeView(pane.view);
        if (mFocusedId == id) mFocusedId = 0;
        mTabs.remove(id);
        sizeTabs();
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

    /** Gives every tab its share of the row ({@link LinuxWindowTabs#tabWidth}); only a changed width is set. */
    private void sizeTabs() {
        int count = mTabRow.getChildCount();
        int width = LinuxWindowTabs.tabWidth(mTabViewportWidth, count,
            res(R.dimen.linux_windows_tab_min_width), res(R.dimen.linux_windows_tab_max_width),
            res(R.dimen.linux_windows_tab_gap),
            res(R.dimen.linux_windows_start_padding));
        for (int i = 0; i < count; i++) {
            View tab = mTabRow.getChildAt(i);
            ViewGroup.LayoutParams lp = tab.getLayoutParams();
            if (lp.width != width) {
                lp.width = width;
                tab.setLayoutParams(lp);
            }
        }
        // Widths moved: the shown tab, end included, stays in view.
        scrollToShown();
    }

    /** Scrolls the row so {@code tab} is in view, once the row has laid it out. */
    private void scrollToTab(View tab) {
        mTabScroll.post(() -> {
            int viewport = mTabScroll.getWidth();
            int left = tab.getLeft();
            int right = left + tab.getWidth();
            int scroll = mTabScroll.getScrollX();
            int target = LinuxWindowTabs.scrollTarget(scroll, viewport, left, right,
                res(R.dimen.linux_windows_start_padding));
            if (target != scroll) mTabScroll.smoothScrollTo(target, 0);
        });
    }

    private void scrollToShown() {
        Pane shown = mPanes.get(mTabs.shown());
        if (shown != null) scrollToTab(shown.tab);
    }

    /** A tab was tapped. */
    private void showWindow(long id) {
        long before = mTabs.shown();
        if (mTabs.show(id)) applyShown(before);
        else scrollToShown();
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
            // The rail was the window's that is going.
            mWindowHost.dismissRail();
        }
        Pane now = mPanes.get(shown);
        if (now != null) scrollToTab(now.tab);
        if (now != null) {
            now.setVisible(mVisible);
            if (mVisible) focus(shown);
        }
    }

    /** {@code --es dump all}: at once when the host already runs (a launch of the live screen
     *  arrives in onNewIntent), else when it does. */
    private void handleStartIntent(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_START, false)) return;
        if (mService == null) {
            mStartPending = true;
            if (!mBound) connect();
        } else {
            startHost();
        }
    }

    private void handleDumpIntent(Intent intent) {
        if (intent == null || !"all".equals(intent.getStringExtra(EXTRA_DUMP))) return;
        if (mHostRunning) mMain.postDelayed(() -> dumpAll(DUMP_TRIES), DUMP_DELAY_MS);
        else mDumpPending = true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_OPENING, false)) {
            mOpeningExtra = true;
            mOpeningExpired = false;
            mLaunchFailed = false;
            settle();
        }
        handleStartIntent(intent);
        handleDumpIntent(intent);
        handleShowWindowIntent(intent);
    }

    /** {@link #EXTRA_SHOW_WINDOW}: selects that window's tab when it exists; nothing otherwise. */
    private void handleShowWindowIntent(@Nullable Intent intent) {
        if (intent == null || !intent.hasExtra(EXTRA_SHOW_WINDOW)) return;
        long id = intent.getLongExtra(EXTRA_SHOW_WINDOW, -1L);
        intent.removeExtra(EXTRA_SHOW_WINDOW);
        if (id < 0) return;
        if (mPanes.get(id) != null) showWindow(id);
        else mShowWindowPending = id;
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
            && LinuxWindowKeys.isForwarded(event.getKeyCode())
            && (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP)) {
            // The client repeats held keys itself (wl_keyboard.repeat_info).
            if (event.getRepeatCount() == 0) sendKey(event.getKeyCode(), action == KeyEvent.ACTION_DOWN, event.getEventTime());
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** One key down or up for the focused window; dropped when there is none. */
    private void sendKey(int keyCode, boolean down, long timeMs) {
        if (mService == null || mFocusedId == 0 || mPanes.get(mFocusedId) == null) return;
        try {
            mService.key(mFocusedId, keyCode, down, timeMs);
        } catch (RemoteException ignored) {
        }
    }

    /** Back puts the keyboard away before it leaves the screen. */
    @Override
    public void onBackPressed() {
        if (keyboardShown()) {
            setKeyboardShown(false);
            return;
        }
        super.onBackPressed();
    }

    private void sendClipboardText(String text) {
        ILinuxWindows service = mService;
        if (service == null) return;
        try {
            service.setClipboardText(text);
        } catch (RemoteException ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        mClipboard.setInFront(hasFocus);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mVisible = true;
        // A Scale changed on an app's settings page while this screen was away.
        for (int i = 0; i < mPanes.size(); i++) mPanes.valueAt(i).reconfigure();
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
        // A modifier latched on the keyboard does not carry over to the next visit.
        mKeyboard.reset();
        mClipboard.setInFront(false);
        // Keys held as the screen goes must not stay down in the window.
        focus(0);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        // The host keeps running; only this screen's views and listener go.
        WaylandAppRunner.shared(this).setPendingListener(null);
        mMain.removeCallbacks(mCloseWhenEmpty);
        mMain.removeCallbacks(mOpeningExpiry);
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
