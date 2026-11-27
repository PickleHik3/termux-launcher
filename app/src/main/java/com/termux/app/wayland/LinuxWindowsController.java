package com.termux.app.wayland;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.page.PageHost;
import com.termux.app.page.PageKind;
import com.termux.app.terminal.PaneAttention;
import com.termux.app.terminal.PaneSurfaceStyle;
import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;
import com.termux.app.wall.WallLinux;
import com.termux.app.window.WindowLayer;
import com.termux.app.window.WindowLayers;

import java.util.ArrayList;
import java.util.List;

/**
 * The Linux windows in the Terminal's row (spec #384, "The window contract"), in the launcher
 * process: it listens to the Linux windows host ({@link LinuxWindowsService}), puts each app
 * window in the session's row right after the window in front, among the terminal windows
 * ({@code SessionRow}, #392), and fills the one {@link LinuxWindowPageView} with the window the
 * row binds it to; the wall stands that view in the Terminal page's place while a Linux window is
 * in front. Dialogs with a parent draw centred over the parent's window. Only the bound window,
 * while it is in front of the row or next to it, holds a surface; others give their buffers back
 * and the host repaints their last frame when they return.
 *
 * <p>It binds the host without creating it (the runner's launch does), so a launcher that opens
 * no Linux app never starts the host's process. A host that ends on its own (idle, stopped) kills
 * that binding for good ({@link ServiceConnection#onBindingDied}), so the controller then binds
 * afresh and hears the next host the runner starts. When the host dies its windows leave the row
 * with a notice; shells are untouched. Main thread only.
 */
public final class LinuxWindowsController implements WallLinux, LinuxWindowPageView.Host {

    private static final String LOG_TAG = "TLWayland";
    private static final int MAX_POINTS = ILinuxWindows.MAX_TOUCH_POINTERS;

    /** What the controller asks of the activity: the session's row and its notices. */
    public interface Host {
        /**
         * Puts window {@code id} in the current session's row right after the window in front;
         * {@code goTo}: the wall then pages to it.
         */
        void addWindow(long id, boolean goTo);
        /** Takes window {@code id} out of its row; the window in front settles as a close does. */
        void removeWindow(long id);
        /** Every Linux window leaves every row (a host met again replays its windows; a dead one has none). */
        void removeAllWindows();
        /** Where window {@code id} stands in its row, to put it back there; null when it is in none. */
        @Nullable Object placeOf(long id);
        /** Puts window {@code id} back at {@code place} (else where a new one goes) and pages to it. */
        void restoreWindow(long id, @Nullable Object place);
        /** The wall pages to window {@code id}, already in a row. */
        void goToWindow(long id);
        void notice(@NonNull String text);
        /** The Linux windows' chips changed. */
        void chipsChanged();
        /** A Linux window started or stopped asking for the user: panes of a split re-dress their rims. */
        default void attentionChanged() {}
        /**
         * Window {@code appName}'s app did not close when asked: offers Force close, which runs
         * {@code forceClose} when the user takes it.
         */
        default void offerForceClose(@NonNull String appName, @NonNull Runnable forceClose) {}
        /** The app's name from its desktop entry, for an app id (the X11 class); null when unknown. */
        @Nullable String appName(@NonNull String appId);
        /** Where a window's icon falls back on: the desktop entry's icon for an app id, or by theme name. */
        @NonNull default LinuxWindowIcons.Lookup iconLookup() { return LinuxWindowIcons.Lookup.NONE; }
        /** What a carry of a window asks of the wall and the row; null where none can be carried. */
        @Nullable default LinuxCarry.Host carryHost() { return null; }
        /**
         * The page host that raises and lowers the launcher's keyboard for a text field, as it
         * does for any page's field: a Linux app's field focused in the window in front raises it.
         */
        @NonNull default PageHost pageHost() { return PageHost.NONE; }
        /**
         * The activity window keeps the screen on ({@code on}) while a Linux window in front holds
         * an idle inhibitor (a video playing), and lets it sleep again.
         */
        default void keepScreenOn(boolean on) {}
    }

    /** One Linux window chip, in row order then the minimised ones. */
    public static final class Chip {
        public final long id;
        @NonNull public final String label;
        /** The app id (the X11 class), for its desktop entry's icon; may be empty. */
        @NonNull public final String appId;
        public final boolean minimised;
        public final boolean front;
        /** The app asks for the user, and the user has not gone to the window since. */
        public final boolean attention;

        Chip(long id, @NonNull String label, @NonNull String appId, boolean minimised, boolean front,
             boolean attention) {
            this.id = id;
            this.label = label;
            this.appId = appId;
            this.minimised = minimised;
            this.front = front;
            this.attention = attention;
        }
    }

    /** One app window the host reported. */
    private final class Win implements TextureView.SurfaceTextureListener {
        final long id;
        String title = "";
        /** Started by the launcher as a Wayland desktop (its launch tag); false for everything else. */
        boolean waylandDesktop;
        String appId = "";
        int flags;
        long parentId;
        /** The app's process id, 0 when unknown ({@link LinuxForceClosePolicy}). */
        int clientPid;
        /** {@link LinuxWindowRow#windowClass}: 0 Main, 1 Dialog, 2 Fixed, 3 Transient. */
        int windowClass;
        /**
         * A Dialog without an open parent, or a Transient: no place in the row, drawn over the page
         * in front while {@link #overShown}.
         */
        boolean floating;
        boolean overShown;
        /**
         * A layer surface (overlay or top layer: a launcher, a notification bubble): over the page in
         * front like a Transient, but never a window of the row, with no chip and no page mark.
         */
        boolean layer;
        int layerAnchor;
        int layerKeyboard;
        /** Margins in px: top, right, bottom, left. */
        final int[] layerMargins = new int[4];
        /** The size the host configured it with, in px, until its first frame says its drawn size. */
        int layerWidth;
        int layerHeight;
        /** The page it was shown over; it hides when another comes in front. */
        @Nullable PageKind overKind;
        int overPageId;
        long overLinuxFront;
        int width;
        int height;
        /** It stands in a row (a dialog with a parent and a minimised window do not). */
        boolean inRow;
        boolean minimised;
        /** It is a pane of a terminal window's split (#387), shown in {@link #paneFrame}. */
        boolean inPane;
        /** The pane is a Float on Home (#388) rather than a split's: it draws while the Float is shown. */
        boolean inFloat;
        boolean floatShown;
        @Nullable LinuxWindowPageView paneFrame;
        /** Where it stood when it was minimised ({@link Host#placeOf}); null for none. */
        @Nullable Object placeWhenMinimised;
        @Nullable TextureView view;
        @Nullable Surface surface;
        int sentWidth;
        int sentHeight;
        float sentScale;
        int mouseButton;
        final int[] slots = new int[MAX_POINTS];
        final float[] xs = new float[MAX_POINTS];
        final float[] ys = new float[MAX_POINTS];

        Win(long id) {
            this.id = id;
        }

        boolean isFixed() {
            return windowClass == ILinuxWindows.WINDOW_CLASS_FIXED;
        }

        /**
         * A Main window of the row whose drawn size is over its slot: shown whole as a Fixed one
         * is, and still told the slot's size ({@link #sendConfigure}).
         */
        boolean fitting;

        /** Its view is laid out at its drawn size by {@link #placeFixed}, not filling the slot. */
        boolean placedAtDrawn() {
            return isFixed() || fitting;
        }

        /** Its frame follows its slot: Fixed always, a Main window of the row for when it overflows. */
        boolean followsSlot() {
            return isFixed() || windowClass == ILinuxWindows.WINDOW_CLASS_MAIN && inRow && !inPane && !floating;
        }

        /** What kind of window this is, for its controls ({@link LinuxWindowMenuItems}). */
        @NonNull
        LinuxWindowMenuItems.Kind kind() {
            if (isWaylandDesktop()) return LinuxWindowMenuItems.Kind.WAYLAND_DESKTOP;
            return WaylandDesktopServer.isDesktopWindow(title)
                ? LinuxWindowMenuItems.Kind.X11_DESKTOP : LinuxWindowMenuItems.Kind.APP;
        }

        boolean scales() {
            return (flags & ILinuxWindows.WINDOW_X11) == 0;
        }

        /**
         * A Wayland desktop's window, by its launch tag ({@link WaylandAppRunner#isWaylandDesktopWindow}).
         * Asked late rather than at creation: the runner matches the window to its launch in its
         * own callback, which may come after this controller's. Once known it stays.
         */
        boolean isWaylandDesktop() {
            if (!waylandDesktop) {
                waylandDesktop = WaylandAppRunner.shared(context).isWaylandDesktopWindow(id);
            }
            return waylandDesktop;
        }

        @NonNull
        TextureView view() {
            TextureView v = view;
            if (v == null) {
                v = new TextureView(context);
                v.setOpaque(false);
                v.setSurfaceTextureListener(this);
                v.setOnTouchListener((tv, e) -> onTouch(this, e));
                v.setOnGenericMotionListener((tv, e) -> onGenericMotion(this, e));
                view = v;
            }
            return v;
        }

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture texture, int w, int h) {
            surface = new Surface(texture);
            if (service == null) return;
            try {
                sendConfigure(this, w, h);
                service.setSurface(id, surface);
                service.setVisible(id, floating ? overShown : inPane ? paneDrawing(this) : front);
            } catch (RemoteException ignored) {
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture texture, int w, int h) {
            try {
                sendConfigure(this, w, h);
            } catch (RemoteException ignored) {
            }
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture texture) {
            if (service != null) {
                try {
                    service.setSurface(id, null);
                } catch (RemoteException ignored) {
                }
            }
            if (surface != null) {
                surface.release();
                surface = null;
            }
            // Its buffers go with the texture; the host keeps the last frame.
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture texture) {}
    }

    @NonNull private final Context context;
    @NonNull private final Host host;
    @NonNull private LinuxPaneHost paneHost = new LinuxPaneHost() {};
    @NonNull private final LinuxWindowPageView page;
    private final Handler main = new Handler(Looper.getMainLooper());
    /** The compositor's process id (onAttached), 0 before it said. */
    private int hostPid;
    /** Which windows ask for the user (rim and chip glow); keyed by window id. */
    private final PaneAttention attention = new PaneAttention();
    /** Close asked and not yet answered, per window: the offer of Force close waits on it. */
    private final LongSparseArray<Runnable> forceCloseDue = new LongSparseArray<>();
    private final LongSparseArray<Win> windows = new LongSparseArray<>();
    @Nullable private ILinuxWindows service;
    @Nullable private IBinder binder;
    /** The live binding to the host; null when not listening. A dead one is replaced, never reused. */
    @Nullable private HostConnection connection;
    /** The window the view is bound to; 0 for none. */
    private long boundId;
    private boolean nearFront;
    /** The Terminal page is on screen: its panes' Linux windows draw. */
    private boolean terminalShown;
    @Nullable private PaneSurfaceStyle paneStyle;
    /** The Linux view is the page in front. */
    private boolean front;
    private long focusedId;
    @Nullable private com.termux.app.x11.X11AppSettingsStore appSettings;
    private final LinuxClipboardBridge clipboard;
    /** The screen stays on while a window that holds an idle inhibitor is in front. */
    private final LinuxIdleInhibit idle;
    private final int[] dialogFrame = new int[4];
    private final int[] fixedFrame = new int[4];
    /** The page in front, as the wall last told ({@link #setFrontPage}). */
    @Nullable private PageKind frontKind;
    private int frontPageId = -1;
    private boolean overlayWatched;
    private boolean replacePosted;
    /** Re-places the windows whose frame follows their slot, once per layout pass of it. */
    private final Runnable replaceAll = () -> {
        replacePosted = false;
        for (int i = 0; i < windows.size(); i++) {
            Win w = windows.valueAt(i);
            if (w.floating && w.overShown) placeDialog(w);
            else if (w.followsSlot()) placeFixed(w);
        }
    };
    private final View.OnLayoutChangeListener slotWatcher = (v, l, t, r, b, ol, ot, or, ob) -> {
        if (r - l == or - ol && b - t == ob - ot) return;
        if (v == LinuxWindowsController.this.page.slot()) {
            sendPageSize();
            if (LinuxWindowsController.this.fullscreen.isFullscreen()) postFullscreenSize();
        }
        // Posted: a layout pass must not ask for another.
        if (!replacePosted) {
            replacePosted = true;
            main.post(replaceAll);
        }
    };
    private final List<Chip> chips = new ArrayList<>();
    /** The icons Linux apps set at run time, and the one answer for a window's icon ({@link #windowIcon}). */
    private final LinuxWindowIcons icons = new LinuxWindowIcons();
    /** Which window is full screen, and which asked while it was not in front. */
    private final LinuxFullscreenPolicy fullscreen = new LinuxFullscreenPolicy();
    /** A finger that went down at the bottom edge of the full-screen window: it may show the keys. */
    private boolean edgeSwipe;
    private float edgeDownY;
    /** The extra-keys row shows under the full-screen window, which is lifted for it. */
    private boolean revealed;
    private final int[] revealAt = new int[2];
    private final Runnable hideReveal = this::hideReveal;
    /** The Terminal page's size as the wall last told ({@link #setPageSize}); 0 before. */
    private int pageWidth;
    private int pageHeight;
    /** The page size last sent to the host; reset on every attach. */
    private int sentPageWidth;
    private int sentPageHeight;
    private float sentPageScale;

    /** Which window's text field has focus, and whether a field raised the keyboard. */
    private final LinuxTextFieldPolicy textFields = new LinuxTextFieldPolicy();
    private final Runnable lowerDue = this::lowerKeyboardIfDue;
    private final Runnable fullscreenSize = this::syncFullscreenSize;
    /** The focused field's content hint and purpose, for the phone keyboard's input type. */
    private int fieldHint;
    private int fieldPurpose;
    /** The view serving the phone keyboard's connection to the focused field; null for none. */
    @Nullable private View phoneView;
    /** The full-screen window hears the phone keyboard's insets: its size follows them. */
    private final View.OnApplyWindowInsetsListener fullscreenInsets = (view, insets) -> {
        postFullscreenSize();
        return insets;
    };

    private final LinuxWindowKeys.Sink keySink = (keyCode, down) -> sendKey(keyCode, down);
    private final TerminalKeyEventHandler.KeyValueInterceptor keys =
        new TerminalKeyEventHandler.KeyValueInterceptor() {
            @Override
            public boolean interceptKeyValue(@NonNull juloo.keyboard2.KeyValue value, boolean ctrl,
                                             boolean alt, boolean shift) {
                if (shownWindow() == null && !focusedIsPane() && !focusedIsFloating()) return false;
                // In a text field typed text goes as text, so any character reaches it; keys and
                // chords stay key presses.
                String text = textFields.typesText(focusedId) ? LinuxWindowKeys.typedText(value, ctrl, alt) : null;
                if (text != null) sendText(text);
                else LinuxWindowKeys.send(value, ctrl, alt, shift, false, keySink);
                return true;
            }

            /** Chords go to the app whole, as X11 forwarding does. */
            @Override
            public boolean yieldsToBindings() {
                return false;
            }
        };

    private final IBinder.DeathRecipient death = () -> main.post(this::hostGone);

    /**
     * One binding to the host. Android delivers a host that is created again to a binding only
     * while that binding lives; a host destroyed in the ordinary way (stopped, then unbound by the
     * runner) ends it with {@link #onBindingDied}, so each binding gets its own object and a late
     * call from a dead one is ignored.
     */
    private final class HostConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder b) {
            if (this != connection || b == null) return;
            unlink();
            binder = b;
            service = ILinuxWindows.Stub.asInterface(b);
            Log.i(LOG_TAG, "launcher attached to the Linux windows host");
            try {
                b.linkToDeath(death, 0);
                sentPageWidth = 0;
                sendPageSize();
                service.addCallback(callback);
            } catch (RemoteException e) {
                hostGone();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (this != connection) return;
            hostGone();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            if (this != connection) return;
            Log.i(LOG_TAG, "the Linux windows host ended; listening for the next one");
            if (service != null) hostGone();
            unbind();
            bind();
        }
    }

    /** Host events arrive on binder threads; everything is handled on the main thread. */
    private final ILinuxWindowsCallback.Stub callback = new ILinuxWindowsCallback.Stub() {
        @Override
        public void onAttached(int interfaceVersion, int hostPid) {
            main.post(() -> {
                LinuxWindowsController.this.hostPid = hostPid;
                if (interfaceVersion == ILinuxWindows.VERSION) return;
                Log.w(LOG_TAG, "host speaks version " + interfaceVersion + ", the launcher " + ILinuxWindows.VERSION);
                hostGone();
            });
        }

        @Override
        public void onHostState(int state, String message) {
            main.post(() -> {
                if (state == ILinuxWindows.STATE_RUNNING) clipboard.pushCurrent();
                if (state == ILinuxWindows.STATE_DIED || state == ILinuxWindows.STATE_STOPPED) dropWindows(state == ILinuxWindows.STATE_DIED);
            });
        }

        @Override
        public void onWindowCreated(long id, String title, String appId, int clientPid, int flags, long parentId) {
            main.post(() -> windowCreated(id, title, appId, clientPid, flags, parentId));
        }

        @Override
        public void onWindowChanged(long id, String title, String appId, int width, int height) {
            main.post(() -> windowChanged(id, title, appId, width, height));
        }

        @Override
        public void onWindowDestroyed(long id) {
            main.post(() -> windowDestroyed(id));
        }

        @Override
        public void onXDisplay(String display) {}

        @Override
        public void onFullscreenRequest(long id, boolean fullscreen) {
            main.post(() -> fullscreenRequested(id, fullscreen));
        }

        @Override
        public void onMoveRequest(long id) {
            main.post(() -> moveRequested(id));
        }

        @Override
        public void onWindowMenuRequest(long id) {
            main.post(() -> windowMenuRequested(id));
        }

        @Override
        public void onTextInput(long id, boolean active, int contentHint, int contentPurpose) {
            main.post(() -> textFieldChanged(id, active, contentHint, contentPurpose));
        }

        @Override
        public void onActivationRequest(long id) {
            // An app asked for its window (a second `firefox URL`, a file opened in a running
            // app): the same as a tap on its chip.
            main.post(() -> showWindow(id));
        }

        @Override
        public void onIdleInhibit(long id, boolean inhibited) {
            main.post(() -> {
                if (windows.get(id) == null) return;
                idle.inhibit(id, inhibited);
                syncIdle();
            });
        }

        @Override
        public void onAttention(long id, boolean on) {
            main.post(() -> attentionAsked(id, on));
        }

        @Override
        public void onWindowIcon(long id, String name, int edge, int[] argb) {
            main.post(() -> windowIconSet(id, name, edge, argb));
        }

        @Override
        public void onWindowLayer(long id, int anchor, int keyboard, int marginTop, int marginRight,
                                  int marginBottom, int marginLeft, int width, int height) {
            main.post(() -> windowLayer(id, anchor, keyboard,
                new int[] {marginTop, marginRight, marginBottom, marginLeft}, width, height));
        }

        @Override
        public void onClipboardText(String text) {
            main.post(() -> clipboard.fromLinux(text));
        }

        // Processes and notes are the runner's (WaylandAppRunner), which tells this controller
        // when a window's app comes back (windowAppRestored).
        @Override
        public void onAppStarted(long token, String tag, int pid) {}

        @Override
        public void onAppExited(long token, String tag, int exitCode) {}

        @Override
        public void onWindowNote(long id, String note) {}

        @Override
        public void onReplayEnd() {}
    };

    /**
     * A launcher that restarted while window {@code id}'s app ran has learnt the app, or the scale
     * kept for the window, from the host: the window draws at that scale again.
     */
    private void windowAppRestored(long id) {
        Win w = windows.get(id);
        if (w != null) reconfigure(w);
    }

    private final WaylandAppRunner.WindowAppListener windowAppListener = this::windowAppRestored;

    /** The split's panes follow the windows through {@code panes}; null restores the no-op. */
    public void setPaneHost(@Nullable LinuxPaneHost panes) {
        paneHost = panes != null ? panes : new LinuxPaneHost() {};
    }

    public LinuxWindowsController(@NonNull Context context, @NonNull Host host) {
        this.context = context;
        this.host = host;
        this.page = new LinuxWindowPageView(context, this);
        page.slot().addOnLayoutChangeListener(slotWatcher);
        this.clipboard = new LinuxClipboardBridge(context, this::sendClipboardText);
        this.idle = new LinuxIdleInhibit(on -> host.keepScreenOn(on));
        attention.setListener(id -> {
            rebuildChips();
            host.attentionChanged();
        });
    }

    /** The one view every Linux page shows; also the page registered for them. */
    @NonNull
    public LinuxWindowPageView page() {
        return page;
    }

    /**
     * Listens to the host from now on, without starting it. Places left by an earlier launcher
     * process go first; a running host replays its windows, which get theirs back.
     */
    public void start() {
        host.removeAllWindows();
        WaylandAppRunner.shared(context).setWindowAppListener(windowAppListener);
        if (connection != null) return;
        bind();
    }

    /** Binds the host without creating it (flags 0): the binding waits for the runner to start one. */
    private void bind() {
        HostConnection c = new HostConnection();
        connection = c;
        boolean bound;
        try {
            bound = context.bindService(new Intent(context, LinuxWindowsService.class), c, 0);
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "cannot reach the Linux windows host: " + e);
            bound = false;
        }
        if (!bound) {
            connection = null;
            unbindQuietly(c);
        }
    }

    private void unbind() {
        HostConnection c = connection;
        connection = null;
        if (c != null) unbindQuietly(c);
    }

    private void unbindQuietly(@NonNull ServiceConnection c) {
        try {
            context.unbindService(c);
        } catch (IllegalArgumentException ignored) {
        }
    }

    /** Stops listening; the apps keep running in the host. */
    public void stop() {
        WaylandAppRunner.shared(context).clearWindowAppListener(windowAppListener);
        cancelCarry();
        if (fullscreen.gone(fullscreen.fullscreenId())) hideReveal();
        applyTextField(textFields.dropped());
        idle.clear();
        for (int i = forceCloseDue.size() - 1; i >= 0; i--) main.removeCallbacks(forceCloseDue.valueAt(i));
        forceCloseDue.clear();
        attention.retain(java.util.Collections.emptySet());
        dropPanes();
        for (int i = 0; i < windows.size(); i++) detachViews(windows.valueAt(i));
        if (service != null) {
            try {
                service.removeCallback(callback);
            } catch (RemoteException ignored) {
            }
        }
        unlink();
        unbind();
        service = null;
        clipboard.setInFront(false);
        windows.clear();
        icons.clear();
        boundId = 0;
        rebuildChips();
    }

    /** Opening an app that is already open goes to its window; false when it has none here. */
    public boolean showWindow(long id) {
        Win w = windows.get(id);
        if (w == null) return false;
        if (w.floating) {
            showOver(w);
            return true;
        }
        if (w.parentId != 0 && windows.get(w.parentId) != null) w = windows.get(w.parentId);
        if (w.minimised) restore(w);
        else if (w.inRow) host.goToWindow(w.id);
        return true;
    }

    /** Window {@code id}'s chip; null when it has none. */
    @Nullable
    public Chip chip(long id) {
        for (int i = 0; i < chips.size(); i++) if (chips.get(i).id == id) return chips.get(i);
        // A pane or a Float has no chip of the row's; Home's chip for a Float reads the same name and icon.
        // A layer surface is not a window and has none.
        Win w = windows.get(id);
        if (w == null || w.layer) return null;
        String name = w.appId.isEmpty() ? null : host.appName(w.appId);
        return new Chip(w.id, LinuxWindowRow.chipLabel(name, w.appId,
            context.getString(R.string.linux_window_app)), w.appId, false, false,
            attention.isSet((int) w.id));
    }

    /**
     * A half-size picture of the bound window as it is now, for the departure card of a step away
     * from it; the caller owns it. Null when it shows nothing.
     */
    @Nullable
    public Bitmap departurePicture() {
        return pictureOf(shownWindow());
    }

    @Nullable
    private Bitmap pictureOf(@Nullable Win w) {
        TextureView v = w == null ? null : w.view;
        if (v == null || !v.isAvailable() || v.getWidth() <= 0 || v.getHeight() <= 0) return null;
        try {
            return v.getBitmap(Math.max(1, v.getWidth() / 2), Math.max(1, v.getHeight() / 2));
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    // ---- chips ------------------------------------------------------------------------------

    /** The Linux windows' chips: the windows in a row, then the minimised; the row orders the first. */
    @NonNull
    public List<Chip> chips() {
        return chips;
    }

    /** A tap on a Linux window's chip: a minimised one comes back to its slot, any other is gone to. */
    public void tapChip(long id) {
        showWindow(id);
    }

    /** The × on a Linux window's chip asks its app to close, as the rim tab's Close does. */
    public void closeChip(long id) {
        askClose(id);
    }

    /**
     * Asks window {@code id}'s app to close, and watches for it not to: {@link
     * LinuxForceClosePolicy#OFFER_AFTER_MS} later a window still open with no dialog of its own
     * is offered Force close. A second Close starts the wait again.
     */
    private void askClose(long id) {
        Win w = windows.get(id);
        if (service == null || w == null) return;
        try {
            service.close(id);
        } catch (RemoteException ignored) {
            return;
        }
        cancelForceClose(id);
        Runnable due = () -> {
            forceCloseDue.remove(id);
            Win still = windows.get(id);
            if (!LinuxForceClosePolicy.shouldOffer(still != null, still != null && hasOwnDialog(id),
                    still == null ? 0 : still.clientPid, android.os.Process.myPid(), hostPid)) return;
            String name = still.appId.isEmpty() ? null : host.appName(still.appId);
            host.offerForceClose(LinuxWindowRow.chipLabel(name, still.appId,
                context.getString(R.string.linux_window_app)), () -> forceClose(id));
        };
        forceCloseDue.put(id, due);
        main.postDelayed(due, LinuxForceClosePolicy.OFFER_AFTER_MS);
    }

    private void cancelForceClose(long id) {
        Runnable due = forceCloseDue.get(id);
        if (due == null) return;
        main.removeCallbacks(due);
        forceCloseDue.remove(id);
    }

    /** The app shows a toplevel of its own over this window: it is asking the user, not hanging. */
    private boolean hasOwnDialog(long id) {
        for (int i = 0; i < windows.size(); i++) if (windows.valueAt(i).parentId == id) return true;
        return false;
    }

    /** Ends the process of window {@code id}'s app; the compositor then sees the window go. */
    private void forceClose(long id) {
        Win w = windows.get(id);
        if (w == null || !LinuxForceClosePolicy.canKill(w.clientPid, android.os.Process.myPid(), hostPid)) return;
        try {
            android.os.Process.killProcess(w.clientPid);
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "force close of window " + id + " failed", e);
        }
    }

    /** An app asks for the user without being brought forward; nothing happens for the window the user is on. */
    private void attentionAsked(long id, boolean on) {
        if (windows.get(id) == null) return;
        if (on && focusedId == id) return;
        attention.set((int) id, PaneAttention.Cause.URGENT, on);
    }

    /** Whether Linux window {@code id} asks for the user (its pane's rim glows). */
    public boolean asksForUser(long id) {
        return attention.isSet((int) id);
    }

    /**
     * The screen stays on while a window with an idle inhibitor is shown in front. Asked wherever
     * what is in front may change (every chip rebuild), so a window leaving the front, closing or
     * being minimised lets go; nothing is touched unless the answer changed.
     */
    private void syncIdle() {
        if (!idle.any() && !idle.isOn()) return;
        idle.update(this::inFrontNow);
    }

    /** Window {@code id} is on the page in front: bound or in a pane, or a dialog over one. */
    private boolean inFrontNow(long id) {
        Win w = windows.get(id);
        if (w == null || !front) return false;
        if (w.inPane) return !w.inFloat || w.floatShown;
        if (w.inRow) return w.id == boundId;
        return w.floating ? w.overShown : isDialogOfBound(w);
    }

    private void rebuildChips() {
        syncIdle();
        chips.clear();
        String fallback = context.getString(R.string.linux_window_app);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < windows.size(); i++) {
                Win w = windows.valueAt(i);
                // An open window over the front page waits past the row, as a minimised one does.
                boolean listed = pass == 0 ? w.inRow : w.minimised || w.floating;
                // A layer surface is not a window of the row: it has no chip.
                if (!listed || w.layer) continue;
                String name = w.appId.isEmpty() ? null : host.appName(w.appId);
                chips.add(new Chip(w.id, LinuxWindowRow.chipLabel(name, w.appId, fallback), w.appId,
                    w.minimised || w.floating, w.inRow && w.id == boundId && front,
                    attention.isSet((int) w.id)));
            }
        }
        host.chipsChanged();
    }

    // ---- host events --------------------------------------------------------------------------

    private void windowCreated(long id, String title, String appId, int clientPid, int flags, long parentId) {
        if (windows.get(id) != null) return;
        Win w = new Win(id);
        w.title = title == null ? "" : title;
        w.appId = appId == null ? "" : appId;
        w.flags = flags;
        w.clientPid = clientPid;
        w.parentId = parentId;
        w.windowClass = LinuxWindowRow.windowClass(flags);
        windows.put(id, w);
        if (LinuxWindowRow.isLayer(flags)) {
            // Shown when the host says where it stands (windowLayer); it never joins the row.
            w.layer = true;
            w.floating = true;
            Log.i(LOG_TAG, "Linux layer surface " + id + " (" + w.appId + ") opens");
            return;
        }
        int placement = LinuxWindowRow.placement(w.windowClass, parentId, windows.get(parentId) != null);
        if (placement == LinuxWindowRow.PLACE_OVER_PARENT) {
            // A dialog: drawn over its parent's window, which shows it when bound.
            Win parent = windows.get(parentId);
            if (parent != null && (parent.inPane || parent.id == boundId && nearFront)) attachViews(parent);
            return;
        }
        if (placement == LinuxWindowRow.PLACE_OVER_FRONT) {
            // A parentless dialog or a transient: over the page in front, with a chip while it is open.
            w.parentId = 0;
            w.floating = true;
            Log.i(LOG_TAG, "Linux window " + id + " (" + w.appId + ") opens over the page in front");
            showOver(w);
            return;
        }
        // A window that was open before this launcher registered gets its place back without the
        // wall paging to it; one that opens now is gone to, however soon after the host started.
        boolean replayed = LinuxWindowRow.replayed(flags);
        // This launcher hid no chrome for a window an earlier one showed full screen: it leaves.
        if (replayed) sendFullscreen(id, false);
        w.inRow = true;
        rebuildChips();
        Log.i(LOG_TAG, "Linux window " + id + " (" + w.appId + ") joins the row" + (replayed ? ", replayed" : ""));
        host.addWindow(w.id, !replayed);
    }

    /**
     * Where layer surface {@code id} stands (once, after it was created): it is shown over the page
     * in front, placed by its anchors, size and margins ({@link LinuxWindowRow#layerFrame}), and a
     * surface that takes the keyboard (on demand or exclusive) gets it, as a touch on it would.
     */
    private void windowLayer(long id, int anchor, int keyboard, @NonNull int[] margins, int width, int height) {
        Win w = windows.get(id);
        if (w == null || !w.layer) return;
        w.layerAnchor = anchor;
        w.layerKeyboard = keyboard;
        System.arraycopy(margins, 0, w.layerMargins, 0, 4);
        w.layerWidth = width;
        w.layerHeight = height;
        showOver(w);
        if (keyboard != ILinuxWindows.LAYER_KEYBOARD_NONE) focus(w.id);
    }

    private void windowChanged(long id, String title, String appId, int width, int height) {
        Win w = windows.get(id);
        if (w == null) return;
        boolean wasDesktop = WaylandDesktopServer.isRootfulTitle(w.title);
        w.title = title == null ? "" : title;
        // A title that names the window a desktop late changes the scale it is configured at.
        if (wasDesktop != WaylandDesktopServer.isRootfulTitle(w.title)) reconfigure(w);
        boolean appChanged = !w.appId.equals(appId == null ? "" : appId);
        w.appId = appId == null ? "" : appId;
        boolean sized = w.width != width || w.height != height;
        w.width = width;
        w.height = height;
        if (sized && (isDialogOfBound(w) || w.floating && w.overShown)) placeDialog(w);
        if (sized && w.followsSlot()) placeFixed(w);
        if (appChanged) {
            icons.appChanged(id);
            rebuildChips();
        }
    }

    /** The app set window {@code id}'s icon at run time, or took it away; the bars redress. */
    private void windowIconSet(long id, @Nullable String name, int edge, @Nullable int[] argb) {
        if (windows.get(id) == null) return;
        Bitmap pixels = null;
        if (edge > 0 && edge <= LINUX_ICON_MAX_EDGE_PX && argb != null && argb.length >= edge * edge) {
            try {
                pixels = Bitmap.createBitmap(argb, edge, edge, Bitmap.Config.ARGB_8888);
            } catch (RuntimeException | OutOfMemoryError e) {
                // Decoration: a bad icon is the entry's icon.
                pixels = null;
            }
        }
        // The store keeps it in colour, cut to 96 px, and derives the chip's silhouette.
        icons.set(id, name == null || name.isEmpty() ? null : name, pixels);
        rebuildChips();
    }

    /** The most a run-time icon is read at, as the host hands it over. */
    private static final int LINUX_ICON_MAX_EDGE_PX = 192;

    /**
     * The one answer to "the icon of Linux window {@code id}": the icon its app set at run time,
     * else its desktop entry's by app id, else null (also while one is still resolving; the bars
     * redress when it lands). {@code form}: the chip's silhouette or the page mark's colour icon;
     * the order is the same for both.
     */
    @Nullable
    public Bitmap windowIcon(long id, @NonNull LinuxWindowIcons.Form form) {
        Win w = windows.get(id);
        return w == null ? null : icons.iconOf(id, w.appId, form, host.iconLookup());
    }

    /**
     * The page mark key of Linux window {@code id}: it names the window and changes whenever its
     * icon does ({@link LinuxWindowIcons#key}); null for a window that is gone or has no icon to
     * show. {@code ResolverPageMarkIcons} answers it through {@link #windowIcon}.
     */
    @Nullable
    public String iconKey(long id) {
        Win w = windows.get(id);
        return w == null || w.layer || w.appId.isEmpty() && !icons.has(id) ? null : icons.key(id);
    }

    /** Raised whenever any window's icon changes, for a refresh that compares before it runs. */
    public int iconVersion() {
        return icons.version();
    }

    private void windowDestroyed(long id) {
        Win w = windows.get(id);
        if (w == null) return;
        if (fullscreen.gone(id)) hideReveal();
        detachViews(w);
        windows.remove(id);
        cancelForceClose(id);
        attention.clear((int) id);
        icons.drop(id);
        idle.forget(id);
        applyTextField(textFields.left(id));
        if (focusedId == id) focusedId = 0;
        if (boundId == id) boundId = 0;
        if (w.inRow) host.removeWindow(id);
        if (w.inPane) {
            w.inPane = false;
            w.paneFrame = null;
            paneHost.removePane(id);
        }
        // A parent's dialogs lose their parent: each gets a place of its own.
        for (int i = 0; i < windows.size(); i++) {
            Win d = windows.valueAt(i);
            if (d.parentId != id) continue;
            d.parentId = 0;
            detachViews(d);
            if (LinuxWindowRow.placement(d.windowClass, 0L, false) == LinuxWindowRow.PLACE_OVER_FRONT) {
                d.floating = true;
                showOver(d);
                continue;
            }
            d.inRow = true;
            host.addWindow(d.id, false);
        }
        rebuildChips();
    }

    /** The host stopped or died: its windows leave the row, with a notice when it died. */
    private void dropWindows(boolean died) {
        boolean had = windows.size() > 0;
        if (fullscreen.gone(fullscreen.fullscreenId())) hideReveal();
        applyTextField(textFields.dropped());
        idle.clear();
        dropPanes();
        for (int i = 0; i < windows.size(); i++) detachViews(windows.valueAt(i));
        windows.clear();
        icons.clear();
        focusedId = 0;
        boundId = 0;
        host.removeAllWindows();
        rebuildChips();
        if (died && had) host.notice(context.getString(R.string.linux_windows_stopped));
    }

    /** Every pane's window is going: its pane leaves its split, which collapses. */
    private void dropPanes() {
        for (int i = 0; i < windows.size(); i++) {
            Win w = windows.valueAt(i);
            if (!w.inPane) continue;
            w.inPane = false;
            w.paneFrame = null;
            paneHost.removePane(w.id);
        }
    }

    private void hostGone() {
        boolean had = windows.size() > 0;
        unlink();
        service = null;
        dropWindows(false);
        if (had) host.notice(context.getString(R.string.linux_windows_stopped));
    }

    private void unlink() {
        if (binder == null) return;
        try {
            binder.unlinkToDeath(death, 0);
        } catch (RuntimeException ignored) {
        }
        binder = null;
    }

    // ---- WallLinux ------------------------------------------------------------------------------

    @Override
    public void bind(long id) {
        if (id == boundId) return;
        Win old = shownWindow();
        if (old != null) {
            detachViews(old);
            setVisible(old, false);
        }
        page.dismissRail();
        boundId = id;
        hideOverElsewhere();
        Win now = shownWindow();
        if (now != null && nearFront) attachViews(now);
        if (now != null && front) {
            setVisible(now, true);
            focus(now.id);
        }
        syncFullscreen();
        rebuildChips();
        resolvePendingRail();
    }

    @Override
    public void setNearFront(boolean near) {
        if (near == nearFront) return;
        nearFront = near;
        Win w = shownWindow();
        if (w == null) return;
        if (near) attachViews(w);
        else detachViews(w);
    }

    @Override
    public void setInFront(boolean isFront) {
        if (isFront == front) return;
        if (!isFront) page.dismissRail();
        frontChanged(isFront);
    }

    @Override
    public void setPageSize(int width, int height) {
        pageWidth = width;
        pageHeight = height;
        sendPageSize();
    }

    /**
     * Tells the host the slot a new full-page window gets, with the scale it would draw at: the
     * slot's own size once it is laid out, else the one the Terminal page's size gives it.
     * Compares first.
     */
    private void sendPageSize() {
        ILinuxWindows s = service;
        if (s == null) return;
        FrameLayout slot = page.slot();
        boolean laidOut = slot.getWidth() > 0 && slot.getHeight() > 0;
        int width = laidOut ? slot.getWidth() : pageWidth;
        int height = laidOut ? slot.getHeight() : page.slotHeightFor(pageHeight);
        if (width <= 0 || height <= 0) return;
        float scale = LinuxDisplayScale.windowScale(null, context.getResources().getDisplayMetrics().density, width);
        if (width == sentPageWidth && height == sentPageHeight && scale == sentPageScale) return;
        try {
            s.setPageSize(width, height, scale);
        } catch (RemoteException e) {
            return;
        }
        sentPageWidth = width;
        sentPageHeight = height;
        sentPageScale = scale;
    }

    @Override
    public void applyStyle(@Nullable PaneSurfaceStyle style) {
        page.applyStyle(style);
        paneStyle = style;
        applyPaneStyle(style);
    }

    // ---- views ----------------------------------------------------------------------------------

    /** Puts {@code w}'s view, then its dialogs' centred over it, in the page's slot. */
    private void attachViews(@NonNull Win w) {
        if (w.id == fullscreen.fullscreenId()) {
            showFullscreen(w);
            return;
        }
        FrameLayout slot = slotOf(w);
        TextureView v = w.view();
        if (v.getParent() != slot) {
            if (w.placedAtDrawn()) {
                // Laid out at its drawn size by placeFixed.
                slot.addView(v, 0, new FrameLayout.LayoutParams(0, 0, Gravity.CENTER));
            } else {
                slot.addView(v, 0, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            }
        }
        if (w.followsSlot()) placeFixed(w);
        for (int i = 0; i < windows.size(); i++) {
            Win d = windows.valueAt(i);
            if (d.parentId != w.id || d.inRow || d.floating) continue;
            if (d.view().getParent() != slot) {
                slot.addView(d.view(), new FrameLayout.LayoutParams(0, 0, Gravity.CENTER));
            }
            placeDialog(d);
        }
    }

    /** Takes {@code w}'s views (and its dialogs') out: their textures, and so their buffers, go. */
    private void detachViews(@NonNull Win w) {
        removeView(w.view);
        for (int i = 0; i < windows.size(); i++) {
            Win d = windows.valueAt(i);
            if (d.parentId == w.id && !d.inRow && !d.floating) removeView(d.view);
        }
    }

    private static void removeView(@Nullable View v) {
        if (v != null && v.getParent() instanceof FrameLayout) ((FrameLayout) v.getParent()).removeView(v);
    }

    /** Sizes a dialog's view from {@link LinuxWindowRow#dialogFrame}; compares first. */
    private void placeDialog(@NonNull Win d) {
        if (d.layer) {
            placeLayer(d);
            return;
        }
        TextureView v = d.view;
        Win parent = windows.get(d.parentId);
        FrameLayout slot = d.floating && v != null && v.getParent() instanceof FrameLayout
            ? (FrameLayout) v.getParent() : parent == null ? page.slot() : slotOf(parent);
        if (v == null || v.getParent() != slot) return;
        LinuxWindowRow.dialogFrame(slot.getWidth(), slot.getHeight(), d.width, d.height, dialogFrame);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        if (lp.width == dialogFrame[2] && lp.height == dialogFrame[3]) return;
        lp.width = dialogFrame[2];
        lp.height = dialogFrame[3];
        lp.gravity = Gravity.CENTER;
        v.setLayoutParams(lp);
    }

    /** Places a layer surface from its anchors, margins and size ({@link LinuxWindowRow#layerFrame}); compares first. */
    private void placeLayer(@NonNull Win d) {
        TextureView v = d.view;
        if (v == null || !(v.getParent() instanceof FrameLayout)) return;
        FrameLayout slot = (FrameLayout) v.getParent();
        // Not laid out yet: the slot's layout change places it (replaceAll).
        if (slot.getWidth() <= 0 || slot.getHeight() <= 0) return;
        int w = d.width > 0 ? d.width : d.layerWidth;
        int h = d.height > 0 ? d.height : d.layerHeight;
        LinuxWindowRow.layerFrame(slot.getWidth(), slot.getHeight(), w, h, d.layerAnchor, d.layerMargins, dialogFrame);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int gravity = Gravity.TOP | Gravity.START;
        if (lp.width == dialogFrame[2] && lp.height == dialogFrame[3] && lp.leftMargin == dialogFrame[0]
            && lp.topMargin == dialogFrame[1] && lp.gravity == gravity) return;
        lp.width = dialogFrame[2];
        lp.height = dialogFrame[3];
        lp.leftMargin = dialogFrame[0];
        lp.topMargin = dialogFrame[1];
        lp.gravity = gravity;
        v.setLayoutParams(lp);
    }

    /**
     * Lays a Fixed window out at its drawn size, centred in its slot, scaled down about its centre
     * when it is bigger ({@link LinuxWindowRow#fixedFrame}). The view's own transform maps a touch
     * into the app's coordinates, and a touch outside the frame never reaches the view. Compares
     * first.
     */
    private void placeFixed(@NonNull Win w) {
        TextureView v = w.view;
        if (v == null || !(v.getParent() instanceof FrameLayout) || w.id == fullscreen.fullscreenId()) return;
        FrameLayout slot = (FrameLayout) v.getParent();
        if (slot.getWidth() <= 0 || slot.getHeight() <= 0) return;
        w.fitting = !w.isFixed()
            && LinuxWindowRow.overflowsSlot(slot.getWidth(), slot.getHeight(), w.width, w.height);
        boolean atDrawn = w.placedAtDrawn();
        float scale = atDrawn
            ? LinuxWindowRow.fixedFrame(slot.getWidth(), slot.getHeight(), w.width, w.height, fixedFrame) : 1f;
        boolean drawn = atDrawn && w.width > 0 && w.height > 0;
        int lw = drawn ? w.width : atDrawn ? slot.getWidth() : FrameLayout.LayoutParams.MATCH_PARENT;
        int lh = drawn ? w.height : atDrawn ? slot.getHeight() : FrameLayout.LayoutParams.MATCH_PARENT;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        if (lp.width != lw || lp.height != lh || lp.gravity != Gravity.CENTER) {
            lp.width = lw;
            lp.height = lh;
            lp.gravity = Gravity.CENTER;
            v.setLayoutParams(lp);
        }
        if (v.getScaleX() != scale) v.setScaleX(scale);
        if (v.getScaleY() != scale) v.setScaleY(scale);
        // A whole-shown Main window's view does not follow its slot, so a slot change is told here.
        if (w.fitting) reconfigure(w);
    }

    // ---- windows over the page in front -------------------------------------------------------

    /** The wall told which page is in front (every landing): a window over another page hides. */
    @Override
    public void setFrontPage(@NonNull PageKind kind, int pageId) {
        frontKind = kind;
        frontPageId = pageId;
        hideOverElsewhere();
        syncFullscreen();
    }

    /** The Linux window in front of the row, 0 for none: a window over it is over that page. */
    private long linuxFrontId() {
        return front ? boundId : 0L;
    }

    @Nullable
    private ViewGroup overlayHost() {
        if (!(context instanceof Activity)) return null;
        return WindowLayers.host((Activity) context, WindowLayer.SHEETS);
    }

    /** Shows {@code w} centred over the page in front: its layer's host, else the Linux page's slot. */
    private void showOver(@NonNull Win w) {
        w.overKind = frontKind;
        w.overPageId = frontPageId;
        w.overLinuxFront = linuxFrontId();
        ViewGroup layer = overlayHost();
        FrameLayout hostView = layer instanceof FrameLayout ? (FrameLayout) layer : page.slot();
        if (hostView == layer && !overlayWatched) {
            overlayWatched = true;
            hostView.addOnLayoutChangeListener(slotWatcher);
        }
        TextureView v = w.view();
        if (v.getParent() != hostView) {
            removeView(v);
            hostView.addView(v, new FrameLayout.LayoutParams(0, 0, Gravity.CENTER));
        } else {
            WindowLayers.raiseWithin(v);
        }
        w.overShown = true;
        placeDialog(w);
        setVisible(w, true);
        rebuildChips();
    }

    /** Takes {@code w} off the page it covered: its texture, and so its buffers, go; its chip stays. */
    private void hideOver(@NonNull Win w) {
        if (!w.overShown) return;
        w.overShown = false;
        removeView(w.view);
        setVisible(w, false);
        if (focusedId == w.id) focus(0);
        rebuildChips();
    }

    private void hideOverElsewhere() {
        for (int i = 0; i < windows.size(); i++) {
            Win w = windows.valueAt(i);
            if (!w.floating || !w.overShown) continue;
            if (w.overKind != frontKind || w.overPageId != frontPageId || w.overLinuxFront != linuxFrontId()) {
                hideOver(w);
            }
        }
    }

    private boolean focusedIsFloating() {
        Win w = focusedId == 0 ? null : windows.get(focusedId);
        return w != null && w.floating && w.overShown;
    }

    private boolean isDialogOfBound(@NonNull Win w) {
        Win parent = w.parentId == 0 ? null : windows.get(w.parentId);
        return parent != null && (parent.inRow && parent.id == boundId || parent.inPane);
    }

    /** Where {@code w}'s views go: its pane's slot, else the one page's. */
    @NonNull
    private FrameLayout slotOf(@NonNull Win w) {
        return w.inPane && w.paneFrame != null ? w.paneFrame.slot() : page.slot();
    }

    @Nullable
    private Win shownWindow() {
        if (boundId == 0) return null;
        Win w = windows.get(boundId);
        return w != null && w.inRow ? w : null;
    }

    // ---- LinuxWindowPageView.Host ----------------------------------------------------------------

    @Override
    public boolean hasWindow() {
        return shownWindow() != null;
    }

    /** The one carry under way, null between carries; see {@link #isCarrying}. */
    @Nullable private LinuxCarry carry;

    private boolean beginCarryOf(@Nullable Win w, float x, float y, int pointerId) {
        LinuxCarry.Host carryHost = host.carryHost();
        if (w == null || carryHost == null || isCarrying()) return false;
        carry = new LinuxCarry(context, carryHost, w.id, pictureOf(w));
        carry.begin(x, y, pointerId);
        return true;
    }

    /** Whether a window is lifted now: the activity routes every touch to {@link #carryTouch}. */
    public boolean isCarrying() {
        if (carry != null && !carry.isActive()) carry = null;
        return carry != null;
    }

    /**
     * Every touch of the activity, first: during a carry it is the carry's (true); otherwise the
     * first finger is followed, so an app's own move request can lift its window from where that
     * finger is now (false: the touch goes on as usual).
     */
    public boolean carryTouch(@NonNull MotionEvent ev) {
        if (isCarrying()) return carry.onTouch(ev);
        followFinger(ev);
        return false;
    }

    /** The first finger down, in window coordinates; {@link #fingerDown} while it stays down. */
    private boolean fingerDown;
    private boolean fingerIsMouse;
    private int fingerId;
    private float fingerX;
    private float fingerY;

    private void followFinger(@NonNull MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                fingerDown = true;
                fingerIsMouse = ev.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE;
                fingerId = ev.getPointerId(0);
                fingerX = ev.getX();
                fingerY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE: {
                int i = fingerDown ? ev.findPointerIndex(fingerId) : -1;
                if (i >= 0) {
                    fingerX = ev.getX(i);
                    fingerY = ev.getY(i);
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                if (ev.getPointerId(ev.getActionIndex()) == fingerId) fingerDown = false;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                fingerDown = false;
                break;
            default:
                break;
        }
    }

    /**
     * The app asked to move window {@code id} (a drag on its own title bar): the same carry as
     * holding Move on the rim tab, from the finger as it is now. The app's touch, or button, ends
     * there; the carry takes the rest of the stream. Only a window shown in its row or pane, and
     * not full screen, is lifted; with no finger down nothing happens.
     */
    private void moveRequested(long id) {
        Win w = windows.get(id);
        if (w == null || !fingerDown || id == fullscreen.fullscreenId()) return;
        boolean shown = w.inRow && w.id == boundId && front || w.inPane && terminalShown;
        if (!shown || !beginCarryOf(w, fingerX, fingerY, fingerId)) return;
        long now = SystemClock.uptimeMillis();
        if (fingerIsMouse) {
            sendPointer(w, ILinuxWindows.POINTER_BUTTON_UP, w.mouseButton, 0f, 0f, now);
        } else if (service != null) {
            try {
                service.touch(w.id, ILinuxWindows.TOUCH_CANCEL, 0, now, 0, w.slots, w.xs, w.ys);
            } catch (RemoteException ignored) {
            }
        }
    }

    /** Back or a home press took the carry: the window goes back where it was. */
    public void cancelCarry() {
        if (carry != null) carry.cancel();
        carry = null;
    }

    @Override
    public boolean windowScales() {
        Win w = shownWindow();
        return w != null && w.scales();
    }

    private void closeWin(@Nullable Win w) {
        if (w != null) askClose(w.id);
    }

    private void minimiseWindow() {
        Win w = shownWindow();
        if (w == null) return;
        if (fullscreen.gone(w.id)) {
            sendFullscreen(w.id, false);
            leftFullscreen(w);
        }
        w.placeWhenMinimised = host.placeOf(w.id);
        detachViews(w);
        setVisible(w, false);
        w.minimised = true;
        w.inRow = false;
        boundId = 0;
        host.removeWindow(w.id);
        rebuildChips();
    }

    /**
     * Minimises the row's Linux window {@code id} from its mark's controls: out of the row, waiting
     * as its chip and in Overview, as {@link #minimiseWindow} does for the one shown.
     */
    public void minimiseRowWindow(long id) {
        Win w = windows.get(id);
        if (w == null || !w.inRow) return;
        if (w.id == boundId) {
            minimiseWindow();
            return;
        }
        if (fullscreen.gone(w.id)) {
            sendFullscreen(w.id, false);
            leftFullscreen(w);
        }
        w.placeWhenMinimised = host.placeOf(w.id);
        detachViews(w);
        setVisible(w, false);
        w.minimised = true;
        w.inRow = false;
        host.removeWindow(w.id);
        rebuildChips();
    }

    /**
     * Lifts the row's Linux window {@code id} into the finger at ({@code x}, {@code y}) in window
     * coordinates, pointer {@code pointerId}: the carry the rim tab's Move started, from its mark's
     * Move control. False when it cannot be lifted.
     */
    public boolean beginCarryById(long id, float x, float y, int pointerId) {
        Win w = windows.get(id);
        return w != null && beginCarryOf(w, x, y, pointerId);
    }

    /** The minimised Linux windows' ids, in chip order, into {@code out} (cleared first). */
    public void minimisedIds(@NonNull java.util.List<Long> out) {
        out.clear();
        for (int i = 0; i < windows.size(); i++) {
            Win w = windows.valueAt(i);
            if (w.minimised) out.add(w.id);
        }
    }

    private void restore(@NonNull Win w) {
        w.minimised = false;
        w.inRow = true;
        Object place = w.placeWhenMinimised;
        w.placeWhenMinimised = null;
        rebuildChips();
        host.restoreWindow(w.id, place);
    }

    @Override
    public void frontChanged(boolean isFront) {
        front = isFront;
        hideOverElsewhere();
        clipboard.setInFront(isFront);
        Win w = shownWindow();
        if (w != null) setVisible(w, isFront);
        if (isFront && w != null) focus(w.id);
        if (!isFront) focus(0);
        syncFullscreen();
        rebuildChips();
        resolvePendingRail();
    }

    @NonNull
    @Override
    public TerminalKeyEventHandler.KeyValueInterceptor keys() {
        return keys;
    }

    @Nullable
    @Override
    public InputConnection textInput(@NonNull View view, @NonNull EditorInfo out) {
        return textInputFor(view, out);
    }

    @Override
    public int currentScaleStep() {
        return scaleStepOf(shownWindow());
    }

    private int scaleStepOf(@Nullable Win w) {
        return w == null || w.view == null ? 0 : LinuxDisplayScale.railStep(scale(w, w.view.getWidth()));
    }

    @Override
    public void onScaleStepChosen(int step) {
        chooseScale(shownWindow(), step);
    }

    private void chooseScale(@Nullable Win w, int step) {
        if (w == null || !w.scales() || w.isWaylandDesktop()) return;
        String app = WaylandAppRunner.shared(context).appFor(w.id);
        if (app == null) {
            WaylandAppRunner.shared(context).setWindowScale(w.id, LinuxDisplayScale.stepScale(step));
            reconfigure(w);
            return;
        }
        // A stop is the app's own Scale: the per-app page shows it, and every window of the app takes it.
        appSettings().setScale(app, com.termux.app.x11.DisplayScaleSteps.scaleValue(step));
        for (int i = 0; i < windows.size(); i++) {
            Win other = windows.valueAt(i);
            if (other == w || app.equals(WaylandAppRunner.shared(context).appFor(other.id))) reconfigure(other);
        }
    }

    @Override
    public void onScaleRailUsed() {
        page.dismissRail();
    }

    // ---- panes of a split (#387) ------------------------------------------------------------------

    /** What a pane's page asks, for one window: the page's own Host, bound to {@code w}. */
    private final class PaneHost implements LinuxWindowPageView.Host {
        final Win w;

        PaneHost(@NonNull Win w) {
            this.w = w;
        }

        @Override public boolean hasWindow() { return w.inPane && windows.get(w.id) == w; }
        @Override public boolean windowScales() { return w.scales(); }
        @Override public void frontChanged(boolean isFront) {}
        @Override public void shownChanged(boolean shown) {
            if (!w.inFloat || w.floatShown == shown || windows.get(w.id) != w) return;
            w.floatShown = shown;
            setVisible(w, shown);
        }
        @NonNull @Override public TerminalKeyEventHandler.KeyValueInterceptor keys() { return keys; }
        @Override public int currentScaleStep() { return scaleStepOf(w); }
        @Override public void onScaleStepChosen(int step) { chooseScale(w, step); }
        @Override public void onScaleRailUsed() {
            if (w.paneFrame != null) w.paneFrame.dismissRail();
        }
        @Nullable @Override public InputConnection textInput(@NonNull View view, @NonNull EditorInfo out) {
            return textInputFor(view, out);
        }
    }

    /**
     * Window {@code id}, in a row or already a pane, becomes a pane: it leaves its row, and the
     * frame its split shows is returned (one per window, kept until {@link #releasePane}). Null
     * when the window is gone or minimised.
     */
    @Nullable
    public LinuxWindowPageView takeIntoPane(long id) {
        return takeIntoPane(id, false);
    }

    /** Whether pane window {@code w} draws now: a split's while the Terminal shows, a Float's while the Float does. */
    private boolean paneDrawing(@NonNull Win w) {
        return w.inFloat ? w.floatShown : terminalShown;
    }

    /**
     * {@link #takeIntoPane(long)}, as a Float's picture when {@code floating} (#388): the frame
     * wears no rim tab and the window draws while its Float is shown, not while the Terminal is.
     * A window already a pane changes between the two in place.
     */
    @Nullable
    public LinuxWindowPageView takeIntoPane(long id, boolean floating) {
        Win w = windows.get(id);
        if (w == null || w.minimised) return null;
        if (w.inPane && w.paneFrame != null) {
            if (w.inFloat != floating) {
                w.inFloat = floating;
                w.floatShown = false;
                w.paneFrame.setFloating(floating);
                setVisible(w, paneDrawing(w));
            }
            return w.paneFrame;
        }
        boolean wasRow = w.inRow;
        if (fullscreen.gone(id)) {
            sendFullscreen(id, false);
            leftFullscreen(w);
        }
        detachViews(w);
        if (boundId == id) boundId = 0;
        w.inRow = false;
        w.inPane = true;
        w.inFloat = floating;
        w.floatShown = false;
        LinuxWindowPageView frame = new LinuxWindowPageView(context, new PaneHost(w));
        frame.setEmbedded(true);
        frame.setFloating(floating);
        frame.slot().addOnLayoutChangeListener(slotWatcher);
        frame.applyStyle(paneStyle);
        w.paneFrame = frame;
        attachViews(w);
        setVisible(w, paneDrawing(w));
        if (wasRow) host.removeWindow(id);
        rebuildChips();
        return frame;
    }

    /** Window {@code id}'s pane left its split: it stands in the row again, after the window in front. */
    public void releasePane(long id, boolean toRow) {
        Win w = windows.get(id);
        if (w == null || !w.inPane) return;
        detachViews(w);
        w.inPane = false;
        w.inFloat = false;
        w.floatShown = false;
        w.paneFrame = null;
        if (focusedId == id) focus(0);
        if (toRow) {
            w.inRow = true;
            host.addWindow(id, false);
        }
        rebuildChips();
    }

    /** The Terminal page came on screen or left it: the panes' windows draw only while it is. */
    public void setTerminalShown(boolean shown) {
        if (shown == terminalShown) return;
        terminalShown = shown;
        for (int i = 0; i < windows.size(); i++) {
            Win w = windows.valueAt(i);
            if (w.inPane && !w.inFloat) setVisible(w, shown);
        }
        long old = fullscreen.fullscreenId();
        apply(fullscreen.paneShownChanged(shown), old);
    }

    /**
     * The split's focus is on pane {@code id} (0: on a terminal): the app takes the keys, or gives
     * them up. True when the key owner changed.
     */
    public boolean setPaneFocus(long id) {
        if (id != 0) {
            Win w = windows.get(id);
            if (w == null || !w.inPane || focusedId == id) return false;
        } else if (!focusedIsPane()) {
            return false;
        }
        focus(id);
        return true;
    }

    /** Whether the app that holds the keys is a pane of a split. */
    private boolean focusedIsPane() {
        Win w = focusedId == 0 ? null : windows.get(focusedId);
        return w != null && w.inPane;
    }

    /** A finger went down on {@code w}: its app takes the focus, and the split's moves to its pane. */
    private void tapped(@NonNull Win w) {
        focus(w.id);
        if (w.inPane) paneHost.paneTouched(w.id);
    }

    /** Minimise on a pane: it leaves the split and waits as a chip. */
    private void minimisePane(@NonNull Win w) {
        if (!w.inPane) return;
        detachViews(w);
        setVisible(w, false);
        w.inPane = false;
        w.paneFrame = null;
        w.minimised = true;
        w.placeWhenMinimised = null;
        if (focusedId == w.id) focus(0);
        paneHost.removePane(w.id);
        rebuildChips();
    }

    /** The frame of pane window {@code id}, for the split to hold; null when it is none. */
    @Nullable
    public LinuxWindowPageView paneFrame(long id) {
        Win w = windows.get(id);
        return w != null && w.inPane ? w.paneFrame : null;
    }

    /** Dresses every pane frame as the surface style says; run with the page's own style. */
    public void applyPaneStyle(@Nullable PaneSurfaceStyle style) {
        for (int i = 0; i < windows.size(); i++) {
            Win w = windows.valueAt(i);
            if (w.paneFrame != null) w.paneFrame.applyStyle(style);
        }
    }

    /**
     * Where an app's request for its window menu goes: its controls open as if its mark were
     * held, on the row for a window in front, on the pane's corner tab for one in a split. Set by
     * the activity; null asks nothing.
     */
    @Nullable private java.util.function.LongConsumer windowMenu;

    public void setWindowMenuListener(@Nullable java.util.function.LongConsumer listener) {
        windowMenu = listener;
    }

    /**
     * The app asked for its window menu (a right-click on its own title bar): the window's
     * controls open as if its mark were held, on the row for a row window in front, on the pane's
     * corner tab for a pane of a split. A window with neither on screen (a dialog, a window over
     * the page, a Float, one full screen) gets none.
     */
    private void windowMenuRequested(long id) {
        Win w = windows.get(id);
        if (w == null || id == fullscreen.fullscreenId()) return;
        boolean rowFront = w.inRow && w.id == boundId && front;
        boolean paneShown = w.inPane && !w.inFloat && terminalShown && w.paneFrame != null;
        Log.i(LOG_TAG, "window menu asked id=" + id + " row=" + rowFront + " pane=" + paneShown);
        java.util.function.LongConsumer menu = windowMenu;
        if ((rowFront || paneShown) && menu != null) menu.accept(id);
    }

    /**
     * Whether window {@code id} takes a scale of its own, so its controls offer Scale: not an X
     * window, which shares the server's, and not a Wayland desktop's, which takes its own from its
     * config ({@link LinuxWindowMenuItems}).
     */
    public boolean windowTakesScale(long id) {
        Win w = windows.get(id);
        return w != null && w.scales() && LinuxWindowMenuItems.of(w.kind()).scale;
    }

    /**
     * Whether window {@code id} is a desktop's (spec #394), so its Maximise control takes the
     * whole screen ({@link #fullScreenWindow}) instead of maximising the launcher's page.
     */
    public boolean windowIsDesktop(long id) {
        Win w = windows.get(id);
        return w != null && LinuxWindowMenuItems.of(w.kind()).fullScreen;
    }

    /**
     * Window {@code id}'s Maximise control on a desktop's window: it takes the whole screen, a row
     * window as one that asked would (at once in front, else once it comes in front), a pane's
     * while the Terminal page shows it. Back leaves ({@link #leaveFullscreen}).
     */
    public void fullScreenWindow(long id) {
        takeScreen(windows.get(id));
    }

    /** {@code w} takes the whole screen: a row window as one that asked would, a pane's while on screen. */
    private void takeScreen(@Nullable Win w) {
        if (w == null || windows.get(w.id) != w) return;
        if (w.inRow) {
            fullscreenRequested(w.id, true);
        } else if (w.inPane && terminalShown) {
            long old = fullscreen.fullscreenId();
            apply(fullscreen.requestPane(w.id), old);
        }
    }

    /** The Scale rail asked for a window that is not yet in front, shown once it is; see {@link #showScaleRail}. */
    private long pendingRailId;
    private long pendingRailUntil;
    private static final long PENDING_RAIL_MS = 1500L;

    /**
     * Window {@code id}'s Scale control: the scale rail comes out on its page (a row window) or on
     * its pane's frame (a pane of a split), the rail the rim tab's Scale used to open. A row window
     * that is not in front shows it as soon as it is, within {@link #PENDING_RAIL_MS}. False for a
     * window that is gone or takes no scale of its own.
     */
    public boolean showScaleRail(long id) {
        Win w = windows.get(id);
        if (w == null || !w.scales() || !(w.inRow || w.inPane)) {
            Log.i(LOG_TAG, "scale rail refused id=" + id + " window=" + (w != null));
            return false;
        }
        if (w.inPane) {
            if (w.paneFrame == null) return false;
            w.paneFrame.showRail();
            return true;
        }
        if (w.id == boundId && front) {
            page.showRail();
            return true;
        }
        pendingRailId = id;
        pendingRailUntil = SystemClock.uptimeMillis() + PENDING_RAIL_MS;
        return true;
    }

    /** Shows the rail a Scale control asked for, once its window is the one in front. */
    private void resolvePendingRail() {
        if (pendingRailId == 0L) return;
        if (SystemClock.uptimeMillis() > pendingRailUntil) {
            pendingRailId = 0L;
            return;
        }
        Win w = shownWindow();
        if (w == null || w.id != pendingRailId || !front) return;
        pendingRailId = 0L;
        main.post(page::showRail);
    }

    /** Minimise on pane window {@code id} from its corner tab: it leaves the split and waits as a chip. */
    public void minimisePaneWindow(long id) {
        Win w = windows.get(id);
        if (w != null) minimisePane(w);
    }

    // ---- full screen ----------------------------------------------------------------------------

    /** Window {@code id} asked to be full screen or to leave it; only a row window takes the screen. */
    private void fullscreenRequested(long id, boolean on) {
        Win w = windows.get(id);
        if (w == null || on && !w.inRow) return;
        long old = fullscreen.fullscreenId();
        apply(fullscreen.request(id, on, rowFrontId()), old);
    }

    /** The row window in front: the bound one while the Linux page is in front; 0 for none. */
    private long rowFrontId() {
        Win w = shownWindow();
        return front && w != null ? w.id : 0L;
    }

    /** Re-reads what is in front: a full-screen window paged away from leaves, one that asked enters. */
    private void syncFullscreen() {
        long old = fullscreen.fullscreenId();
        apply(fullscreen.frontChanged(rowFrontId()), old);
    }

    @Override
    public boolean leaveFullscreen() {
        long old = fullscreen.fullscreenId();
        if (!fullscreen.back()) return false;
        sendFullscreen(old, false);
        Win w = windows.get(old);
        if (w != null) leftFullscreen(w);
        return true;
    }

    private void apply(int action, long oldId) {
        if (action == LinuxFullscreenPolicy.NOTHING) return;
        if (action == LinuxFullscreenPolicy.LEAVE || action == LinuxFullscreenPolicy.SWITCH) {
            sendFullscreen(oldId, false);
            Win old = windows.get(oldId);
            if (old != null) leftFullscreen(old);
        }
        if (action == LinuxFullscreenPolicy.ENTER || action == LinuxFullscreenPolicy.SWITCH) {
            Win w = windows.get(fullscreen.fullscreenId());
            if (w == null) return;
            sendFullscreen(w.id, true);
            showFullscreen(w);
        }
    }

    /**
     * Gives {@code w} the whole screen: its view leaves the page's slot for the sheets' layer, over
     * the status bar, the dock, the extra-keys row and the rim, and the size it gets there reaches
     * the app with the full-screen state.
     */
    private void showFullscreen(@NonNull Win w) {
        ViewGroup layer = overlayHost();
        if (!(layer instanceof FrameLayout)) return;
        TextureView v = w.view();
        if (v.getParent() != layer) {
            removeView(v);
            layer.addView(v, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            WindowLayers.raiseWithin(v);
        }
        if (v.getScaleX() != 1f) v.setScaleX(1f);
        if (v.getScaleY() != 1f) v.setScaleY(1f);
        v.setOnApplyWindowInsetsListener(fullscreenInsets);
        page.dismissRail();
        postFullscreenSize();
    }

    /** {@code w} gave the screen back: its view returns to its slot when it still shows there. */
    private void leftFullscreen(@NonNull Win w) {
        hideReveal();
        edgeSwipe = false;
        if (w.view != null) w.view.setOnApplyWindowInsetsListener(null);
        removeView(w.view);
        if (w.inRow && w.id == boundId && nearFront || w.inPane) attachViews(w);
    }

    private void sendFullscreen(long id, boolean on) {
        if (service == null || id == 0) return;
        try {
            service.setFullscreen(id, on);
        } catch (RemoteException ignored) {
        }
    }

    /**
     * A finger that starts at the bottom edge of the full-screen window is the launcher's: a swipe
     * up shows the extra-keys row for a moment. True when the touch is taken.
     */
    private boolean edgeTouch(@NonNull Win w, @NonNull MotionEvent e) {
        TextureView v = w.view;
        if (v == null) return false;
        float density = context.getResources().getDisplayMetrics().density;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                edgeSwipe = LinuxFullscreenPolicy.startsAtBottomEdge(e.getY(), v.getHeight(), density);
                edgeDownY = e.getY();
                return edgeSwipe;
            case MotionEvent.ACTION_MOVE:
                if (!edgeSwipe) return false;
                if (LinuxFullscreenPolicy.swipedUp(edgeDownY, e.getY(), density)) reveal(w);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean was = edgeSwipe;
                edgeSwipe = false;
                return was;
            }
            default:
                return edgeSwipe;
        }
    }

    /**
     * Lifts the full-screen window by the band below the Linux page (the extra-keys row and what
     * stands with it), so that row shows and takes presses; it hides again {@link
     * LinuxFullscreenPolicy#REVEAL_MS} after the last press. A translation: the app keeps its size.
     */
    private void reveal(@NonNull Win w) {
        TextureView v = w.view;
        if (v == null || !(v.getParent() instanceof View)) return;
        View layer = (View) v.getParent();
        page.getLocationInWindow(revealAt);
        int pageBottom = revealAt[1] + page.getHeight();
        layer.getLocationInWindow(revealAt);
        int band = revealAt[1] + layer.getHeight() - pageBottom;
        if (band <= 0) return;
        if (v.getTranslationY() != -band) v.setTranslationY(-band);
        revealed = true;
        main.removeCallbacks(hideReveal);
        main.postDelayed(hideReveal, LinuxFullscreenPolicy.REVEAL_MS);
    }

    private void hideReveal() {
        main.removeCallbacks(hideReveal);
        if (!revealed) return;
        revealed = false;
        for (int i = 0; i < windows.size(); i++) {
            TextureView v = windows.valueAt(i).view;
            if (v != null && v.getTranslationY() != 0f) v.setTranslationY(0f);
        }
    }

    // ---- to the host --------------------------------------------------------------------------

    @NonNull
    private com.termux.app.x11.X11AppSettingsStore appSettings() {
        com.termux.app.x11.X11AppSettingsStore store = appSettings;
        if (store == null) appSettings = store = new com.termux.app.x11.X11AppSettingsStore(context);
        return store;
    }

    /** The scale {@code w} draws at: its app's Scale, the rail's stop kept for it, else the screen's. */
    private float scale(@NonNull Win w, int widthPx) {
        float density = context.getResources().getDisplayMetrics().density;
        if (!w.scales()) return LinuxDisplayScale.current(context);
        String app = WaylandAppRunner.shared(context).appFor(w.id);
        if (app != null) return LinuxDisplayScale.windowScale(appSettings().scale(app), density, widthPx);
        float kept = WaylandAppRunner.shared(context).windowScale(w.id);
        return kept > 0f ? kept : LinuxDisplayScale.windowScale(null, density, widthPx);
    }

    /**
     * Tells the host the size a window draws at: its view's, except a Main window shown whole at
     * its drawn size, which is told its slot's so it can shrink back when it could. The drawn size
     * would otherwise be sent back as its pane and it would never get smaller.
     */
    private void sendConfigure(@NonNull Win w, int width, int height) throws RemoteException {
        if (w.fitting && !w.isFixed() && w.view != null && w.view.getParent() instanceof FrameLayout) {
            FrameLayout slot = (FrameLayout) w.view.getParent();
            if (slot.getWidth() > 0 && slot.getHeight() > 0) {
                width = slot.getWidth();
                height = slot.getHeight();
            }
        }
        if (service == null || width <= 0 || height <= 0) return;
        float scale = scale(w, width);
        // A desktop draws at a whole factor and its view shows it scaled down to the stop.
        if (WaylandDesktopServer.isRootfulTitle(w.title)) scale = WaylandDesktopServer.hostScale(scale);
        // A Wayland desktop ignores the host's scale and sets its own from its config: its window
        // is told its size in real pixels.
        if (w.isWaylandDesktop()) scale = 1f;
        if (width == w.sentWidth && height == w.sentHeight && scale == w.sentScale) return;
        service.configure(w.id, width, height, scale);
        w.sentWidth = width;
        w.sentHeight = height;
        w.sentScale = scale;
    }

    private void reconfigure(@NonNull Win w) {
        if (w.view == null) return;
        try {
            sendConfigure(w, w.view.getWidth(), w.view.getHeight());
        } catch (RemoteException ignored) {
        }
    }

    private void setVisible(@NonNull Win w, boolean visible) {
        if (service == null) return;
        try {
            service.setVisible(w.id, visible);
            for (int i = 0; i < windows.size(); i++) {
                Win d = windows.valueAt(i);
                if (d.parentId == w.id && !d.inRow && !d.floating) service.setVisible(d.id, visible);
            }
        } catch (RemoteException ignored) {
        }
    }

    private void focus(long id) {
        // Going to the window answers what it asked.
        if (id != 0) attention.clear((int) id);
        if (focusedId == id) return;
        focusedId = id;
        if (service == null) return;
        try {
            service.setFocus(id);
        } catch (RemoteException ignored) {
        }
    }

    private void sendKey(int keyCode, boolean down) {
        if (revealed) {
            main.removeCallbacks(hideReveal);
            main.postDelayed(hideReveal, LinuxFullscreenPolicy.REVEAL_MS);
        }
        if (service == null || focusedId == 0 || windows.get(focusedId) == null) return;
        try {
            service.key(focusedId, keyCode, down, SystemClock.uptimeMillis());
        } catch (RemoteException ignored) {
        }
    }

    /** Typed text for the focused window's text field. */
    private void sendText(@NonNull String text) {
        if (revealed) {
            main.removeCallbacks(hideReveal);
            main.postDelayed(hideReveal, LinuxFullscreenPolicy.REVEAL_MS);
        }
        if (service == null || focusedId == 0 || windows.get(focusedId) == null) return;
        try {
            service.commitText(focusedId, text);
        } catch (RemoteException ignored) {
        }
    }

    /** The host told that window {@code id}'s text field took focus or lost it. */
    private void textFieldChanged(long id, boolean active, int hint, int purpose) {
        if (windows.get(id) == null) return;
        if (active) {
            fieldHint = hint;
            fieldPurpose = purpose;
        }
        if (!active) {
            applyTextField(textFields.left(id));
            return;
        }
        PageHost pages = host.pageHost();
        applyTextField(textFields.focused(id, holdsKeys(id), pages.launcherKeyboard(), pages.keyboardUp()));
    }

    /** Whether window {@code id} takes the launcher's keys now: the interceptor's own test. */
    private boolean holdsKeys(long id) {
        if (id == 0 || id != focusedId) return false;
        Win shown = shownWindow();
        return front && shown != null && shown.id == id || focusedIsPane() || focusedIsFloating();
    }

    private void lowerKeyboardIfDue() {
        applyTextField(textFields.lowerDue(host.pageHost().keyboardUp()));
    }

    private void applyTextField(@NonNull LinuxTextFieldPolicy.Action action) {
        switch (action) {
            case RAISE:
                main.removeCallbacks(lowerDue);
                host.pageHost().fieldFocusChanged(page, true);
                break;
            case RAISE_PHONE: {
                main.removeCallbacks(lowerDue);
                View v = takePhoneFocus();
                if (v != null) host.pageHost().showPhoneKeyboard(v);
                break;
            }
            case FOCUS_PHONE:
                main.removeCallbacks(lowerDue);
                takePhoneFocus();
                break;
            case LOWER_SOON:
                main.removeCallbacks(lowerDue);
                main.post(lowerDue);
                break;
            case LOWER:
                host.pageHost().lowerKeyboard();
                break;
            case LOWER_PHONE:
                if (phoneView != null) host.pageHost().hidePhoneKeyboard(phoneView);
                releasePhoneFocus();
                break;
            case RELEASE_PHONE:
                releasePhoneFocus();
                break;
            default:
                break;
        }
        postFullscreenSize();
    }

    /**
     * The view that serves the phone keyboard's connection to the focused window's field takes
     * focus: the window's pane frame, else the Linux page. Already focused, its input restarts so
     * the keyboard reads the field's new content type. Null when that view is not on screen.
     */
    @Nullable
    private View takePhoneFocus() {
        Win w = focusedId == 0 ? null : windows.get(focusedId);
        if (w == null) return null;
        View v = w.inPane && w.paneFrame != null ? w.paneFrame : page;
        if (!v.isAttachedToWindow()) return null;
        if (phoneView != null && phoneView != v) releasePhoneFocus();
        phoneView = v;
        if (!v.isFocusable()) {
            v.setFocusable(true);
            v.setFocusableInTouchMode(true);
        }
        if (v.hasFocus()) {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.restartInput(v);
        } else {
            v.requestFocus();
        }
        return v;
    }

    /** The phone keyboard's connection goes back: the view gives up focus and stops being an editor. */
    private void releasePhoneFocus() {
        View v = phoneView;
        phoneView = null;
        if (v == null) return;
        v.clearFocus();
        v.setFocusableInTouchMode(false);
        v.setFocusable(false);
    }

    /** The phone keyboard's connection to the focused window's field, for {@code view}; null when it serves none. */
    @Nullable
    private InputConnection textInputFor(@NonNull View view, @NonNull EditorInfo out) {
        if (view != phoneView || !textFields.servesPhone(focusedId)) return null;
        out.inputType = LinuxTextFieldPolicy.inputType(fieldHint, fieldPurpose);
        out.imeOptions = LinuxTextFieldPolicy.imeOptions();
        return new LinuxTextInputConnection(view, phoneSink);
    }

    /** Where the phone keyboard's edits go: the focused window's field, its keys as today's keys. */
    private final LinuxTextInputConnection.Sink phoneSink = new LinuxTextInputConnection.Sink() {
        @Override public void commit(@NonNull String text) { sendText(text); }

        @Override public void preedit(@NonNull String text, int cursor) {
            if (service == null || focusedId == 0) return;
            try {
                service.setPreeditText(focusedId, text, cursor);
            } catch (RemoteException ignored) {
            }
        }

        @Override public void delete(int before, int after) {
            if (service == null || focusedId == 0) return;
            try {
                service.deleteSurroundingText(focusedId, before, after);
            } catch (RemoteException ignored) {
            }
        }

        @Override public void key(int keyCode, boolean down) { sendKey(keyCode, down); }
    };

    private void postFullscreenSize() {
        main.removeCallbacks(fullscreenSize);
        main.post(fullscreenSize);
    }

    /**
     * Sizes the full-screen window as adjustResize would: the area above the keyboard while its
     * text field holds one that is up, else the whole layer. Its new size reaches the app as a
     * configure through the TextureView's size change.
     */
    private void syncFullscreenSize() {
        Win w = windows.get(fullscreen.fullscreenId());
        TextureView v = w == null ? null : w.view;
        if (v == null || !(v.getParent() instanceof FrameLayout)) return;
        View layer = (View) v.getParent();
        PageHost pages = host.pageHost();
        boolean holds = w.id == focusedId && textFields.typesText(w.id) && pages.keyboardUp();
        int layerHeight = layer.getHeight();
        int h = LinuxFullscreenPolicy.windowHeight(layerHeight, holds,
            holds ? keyboardTopIn(layer, pages.launcherKeyboard()) : 0);
        int target = h >= layerHeight ? FrameLayout.LayoutParams.MATCH_PARENT : h;
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null || lp.height == target) return;
        lp.height = target;
        if (lp instanceof FrameLayout.LayoutParams) ((FrameLayout.LayoutParams) lp).gravity = Gravity.TOP;
        v.setLayoutParams(lp);
    }

    /**
     * The keyboard's top in {@code layer}'s pixels: for the launcher's keyboard the Linux page's
     * bottom (the page stands above the keyboard and the rows over it), for the phone's the top of
     * its inset.
     */
    private int keyboardTopIn(@NonNull View layer, boolean launcherKeyboard) {
        layer.getLocationInWindow(revealAt);
        int layerTop = revealAt[1];
        if (launcherKeyboard) {
            page.getLocationInWindow(revealAt);
            return revealAt[1] + page.getHeight() - layerTop;
        }
        android.view.WindowInsets raw = layer.getRootWindowInsets();
        if (raw == null) return 0;
        androidx.core.view.WindowInsetsCompat insets =
            androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(raw, layer);
        int type = androidx.core.view.WindowInsetsCompat.Type.ime();
        if (!insets.isVisible(type)) return 0;
        int imeBottom = insets.getInsets(type).bottom;
        if (imeBottom <= 0) return 0;
        return layer.getRootView().getHeight() - imeBottom - layerTop;
    }

    private void sendClipboardText(String text) {
        ILinuxWindows s = service;
        if (s == null) return;
        try {
            s.setClipboardText(text);
        } catch (RemoteException ignored) {
        }
    }

    private void sendPointer(@NonNull Win w, int action, int button, float x, float y, long time) {
        if (service == null) return;
        if (action != ILinuxWindows.POINTER_SCROLL) {
            x = LinuxWindowRow.toConfigured(x, viewWidth(w), w.sentWidth);
            y = LinuxWindowRow.toConfigured(y, viewHeight(w), w.sentHeight);
        }
        try {
            service.pointer(w.id, action, button, x, y, time);
        } catch (RemoteException ignored) {
        }
    }

    private static int viewWidth(@NonNull Win w) {
        return w.view == null ? 0 : w.view.getWidth();
    }

    private static int viewHeight(@NonNull Win w) {
        return w.view == null ? 0 : w.view.getHeight();
    }

    /** The whole MotionEvent in one call: a second finger going down is one wl_touch frame. */
    private boolean onTouch(@NonNull Win w, @NonNull MotionEvent e) {
        if (e.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) return onMouse(w, e);
        if (w.id == fullscreen.fullscreenId() && edgeTouch(w, e)) return true;
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) tapped(w);
        int n = Math.min(e.getPointerCount(), MAX_POINTS);
        for (int i = 0; i < n; i++) {
            w.slots[i] = e.getPointerId(i);
            w.xs[i] = LinuxWindowRow.toConfigured(e.getX(i), viewWidth(w), w.sentWidth);
            w.ys[i] = LinuxWindowRow.toConfigured(e.getY(i), viewHeight(w), w.sentHeight);
        }
        if (service == null) return true;
        try {
            service.touch(w.id, action, e.getActionIndex(), e.getEventTime(), n, w.slots, w.xs, w.ys);
        } catch (RemoteException ignored) {
        }
        return true;
    }

    private boolean onMouse(@NonNull Win w, @NonNull MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tapped(w);
                w.mouseButton = linuxButton(e.getButtonState());
                sendPointer(w, ILinuxWindows.POINTER_MOTION, 0, e.getX(), e.getY(), e.getEventTime());
                sendPointer(w, ILinuxWindows.POINTER_BUTTON_DOWN, w.mouseButton, e.getX(), e.getY(), e.getEventTime());
                return true;
            case MotionEvent.ACTION_MOVE:
                sendPointer(w, ILinuxWindows.POINTER_MOTION, 0, e.getX(), e.getY(), e.getEventTime());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                sendPointer(w, ILinuxWindows.POINTER_BUTTON_UP, w.mouseButton, e.getX(), e.getY(), e.getEventTime());
                return true;
            default:
                return true;
        }
    }

    private boolean onGenericMotion(@NonNull Win w, @NonNull MotionEvent e) {
        if (!e.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
                sendPointer(w, ILinuxWindows.POINTER_MOTION, 0, e.getX(), e.getY(), e.getEventTime());
                return true;
            case MotionEvent.ACTION_HOVER_EXIT:
                sendPointer(w, ILinuxWindows.POINTER_LEAVE, 0, 0f, 0f, e.getEventTime());
                return true;
            case MotionEvent.ACTION_SCROLL:
                sendPointer(w, ILinuxWindows.POINTER_SCROLL, 0,
                    e.getAxisValue(MotionEvent.AXIS_HSCROLL) * 15f, -e.getAxisValue(MotionEvent.AXIS_VSCROLL) * 15f,
                    e.getEventTime());
                return true;
            default:
                return false;
        }
    }

    private static int linuxButton(int buttonState) {
        if ((buttonState & MotionEvent.BUTTON_SECONDARY) != 0) return ILinuxWindows.BTN_RIGHT;
        if ((buttonState & MotionEvent.BUTTON_TERTIARY) != 0) return ILinuxWindows.BTN_MIDDLE;
        return ILinuxWindows.BTN_LEFT;
    }
}
