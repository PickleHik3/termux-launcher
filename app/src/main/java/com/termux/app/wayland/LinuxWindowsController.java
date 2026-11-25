package com.termux.app.wayland;

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
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.PaneSurfaceStyle;
import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;
import com.termux.app.wall.WallLinux;

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
    /**
     * Windows the host replays this soon after attaching were open before: they get their places
     * back without the wall paging to them.
     */
    private static final long REPLAY_MS = 750;

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
        /** The app's name from its desktop entry, for an app id (the X11 class); null when unknown. */
        @Nullable String appName(@NonNull String appId);
        /** Pane window {@code id} was destroyed or taken out: its pane leaves the split, which collapses. */
        default void removePane(long id) {}
        /** The pane of window {@code id} took the finger: the split's focus moves to it. */
        default void paneTouched(long id) {}
        /** What a carry of a window asks of the wall and the row; null where none can be carried. */
        @Nullable default LinuxCarry.Host carryHost() { return null; }
    }

    /** One Linux window chip, in row order then the minimised ones. */
    public static final class Chip {
        public final long id;
        @NonNull public final String label;
        /** The app id (the X11 class), for its desktop entry's icon; may be empty. */
        @NonNull public final String appId;
        public final boolean minimised;
        public final boolean front;

        Chip(long id, @NonNull String label, @NonNull String appId, boolean minimised, boolean front) {
            this.id = id;
            this.label = label;
            this.appId = appId;
            this.minimised = minimised;
            this.front = front;
        }
    }

    /** One app window the host reported. */
    private final class Win implements TextureView.SurfaceTextureListener {
        final long id;
        String title = "";
        String appId = "";
        int flags;
        long parentId;
        int width;
        int height;
        /** It stands in a row (a dialog with a parent and a minimised window do not). */
        boolean inRow;
        boolean minimised;
        /** It is a pane of a terminal window's split (#387), shown in {@link #paneFrame}. */
        boolean inPane;
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

        boolean scales() {
            return (flags & ILinuxWindows.WINDOW_X11) == 0;
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
                service.setVisible(id, inPane ? terminalShown : front);
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
    @NonNull private final LinuxWindowPageView page;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LongSparseArray<Win> windows = new LongSparseArray<>();
    @Nullable private ILinuxWindows service;
    @Nullable private IBinder binder;
    /** The live binding to the host; null when not listening. A dead one is replaced, never reused. */
    @Nullable private HostConnection connection;
    private long attachedAt;
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
    private final int[] dialogFrame = new int[4];
    private final List<Chip> chips = new ArrayList<>();

    private final LinuxWindowKeys.Sink keySink = (keyCode, down) -> sendKey(keyCode, down);
    private final TerminalKeyEventHandler.KeyValueInterceptor keys =
        new TerminalKeyEventHandler.KeyValueInterceptor() {
            @Override
            public boolean interceptKeyValue(@NonNull juloo.keyboard2.KeyValue value, boolean ctrl,
                                             boolean alt, boolean shift) {
                if (shownWindow() == null && !focusedIsPane()) return false;
                LinuxWindowKeys.send(value, ctrl, alt, shift, false, keySink);
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
            attachedAt = SystemClock.uptimeMillis();
            Log.i(LOG_TAG, "launcher attached to the Linux windows host");
            try {
                b.linkToDeath(death, 0);
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
            main.post(() -> windowCreated(id, title, appId, flags, parentId));
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
        public void onClipboardText(String text) {
            main.post(() -> clipboard.fromLinux(text));
        }
    };

    public LinuxWindowsController(@NonNull Context context, @NonNull Host host) {
        this.context = context;
        this.host = host;
        this.page = new LinuxWindowPageView(context, this);
        this.clipboard = new LinuxClipboardBridge(context, this::sendClipboardText);
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
        cancelCarry();
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
        boundId = 0;
        rebuildChips();
    }

    /** Opening an app that is already open goes to its window; false when it has none here. */
    public boolean showWindow(long id) {
        Win w = windows.get(id);
        if (w == null) return false;
        if (w.parentId != 0 && windows.get(w.parentId) != null) w = windows.get(w.parentId);
        if (w.minimised) restore(w);
        else if (w.inRow) host.goToWindow(w.id);
        return true;
    }

    /** Window {@code id}'s chip; null when it has none. */
    @Nullable
    public Chip chip(long id) {
        for (int i = 0; i < chips.size(); i++) if (chips.get(i).id == id) return chips.get(i);
        return null;
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
        if (service == null || windows.get(id) == null) return;
        try {
            service.close(id);
        } catch (RemoteException ignored) {
        }
    }

    private void rebuildChips() {
        chips.clear();
        String fallback = context.getString(R.string.linux_window_app);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < windows.size(); i++) {
                Win w = windows.valueAt(i);
                boolean listed = pass == 0 ? w.inRow : w.minimised;
                if (!listed) continue;
                String name = w.appId.isEmpty() ? null : host.appName(w.appId);
                chips.add(new Chip(w.id, LinuxWindowRow.chipLabel(name, w.appId, fallback), w.appId,
                    w.minimised, w.inRow && w.id == boundId && front));
            }
        }
        host.chipsChanged();
    }

    // ---- host events --------------------------------------------------------------------------

    private void windowCreated(long id, String title, String appId, int flags, long parentId) {
        if (windows.get(id) != null) return;
        Win w = new Win(id);
        w.title = title == null ? "" : title;
        w.appId = appId == null ? "" : appId;
        w.flags = flags;
        w.parentId = parentId;
        windows.put(id, w);
        if (!LinuxWindowRow.ownsPage(parentId, windows.get(parentId) != null)) {
            // A dialog: drawn over its parent's window, which shows it when bound.
            Win parent = windows.get(parentId);
            if (parent != null && (parent.inPane || parent.id == boundId && nearFront)) attachViews(parent);
            return;
        }
        boolean replayed = SystemClock.uptimeMillis() - attachedAt < REPLAY_MS;
        w.inRow = true;
        rebuildChips();
        Log.i(LOG_TAG, "Linux window " + id + " (" + w.appId + ") joins the row" + (replayed ? ", replayed" : ""));
        host.addWindow(w.id, !replayed);
    }

    private void windowChanged(long id, String title, String appId, int width, int height) {
        Win w = windows.get(id);
        if (w == null) return;
        w.title = title == null ? "" : title;
        boolean appChanged = !w.appId.equals(appId == null ? "" : appId);
        w.appId = appId == null ? "" : appId;
        boolean sized = w.width != width || w.height != height;
        w.width = width;
        w.height = height;
        if (sized && isDialogOfBound(w)) placeDialog(w);
        if (appChanged) rebuildChips();
    }

    private void windowDestroyed(long id) {
        Win w = windows.get(id);
        if (w == null) return;
        detachViews(w);
        windows.remove(id);
        if (focusedId == id) focusedId = 0;
        if (boundId == id) boundId = 0;
        if (w.inRow) host.removeWindow(id);
        if (w.inPane) {
            w.inPane = false;
            w.paneFrame = null;
            host.removePane(id);
        }
        // A parent's dialogs lose their parent: each gets a place of its own.
        for (int i = 0; i < windows.size(); i++) {
            Win d = windows.valueAt(i);
            if (d.parentId != id) continue;
            d.parentId = 0;
            detachViews(d);
            d.inRow = true;
            host.addWindow(d.id, false);
        }
        rebuildChips();
    }

    /** The host stopped or died: its windows leave the row, with a notice when it died. */
    private void dropWindows(boolean died) {
        boolean had = windows.size() > 0;
        dropPanes();
        for (int i = 0; i < windows.size(); i++) detachViews(windows.valueAt(i));
        windows.clear();
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
            host.removePane(w.id);
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
        Win now = shownWindow();
        if (now != null && nearFront) attachViews(now);
        if (now != null && front) {
            setVisible(now, true);
            focus(now.id);
        }
        rebuildChips();
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
    public void applyStyle(@Nullable PaneSurfaceStyle style) {
        page.applyStyle(style);
        paneStyle = style;
        applyPaneStyle(style);
    }

    // ---- views ----------------------------------------------------------------------------------

    /** Puts {@code w}'s view, then its dialogs' centred over it, in the page's slot. */
    private void attachViews(@NonNull Win w) {
        FrameLayout slot = slotOf(w);
        TextureView v = w.view();
        if (v.getParent() != slot) {
            slot.addView(v, 0, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        }
        for (int i = 0; i < windows.size(); i++) {
            Win d = windows.valueAt(i);
            if (d.parentId != w.id || d.inRow) continue;
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
            if (d.parentId == w.id && !d.inRow) removeView(d.view);
        }
    }

    private static void removeView(@Nullable View v) {
        if (v != null && v.getParent() instanceof FrameLayout) ((FrameLayout) v.getParent()).removeView(v);
    }

    /** Sizes a dialog's view from {@link LinuxWindowRow#dialogFrame}; compares first. */
    private void placeDialog(@NonNull Win d) {
        TextureView v = d.view;
        Win parent = windows.get(d.parentId);
        FrameLayout slot = parent == null ? page.slot() : slotOf(parent);
        if (v == null || v.getParent() != slot) return;
        LinuxWindowRow.dialogFrame(slot.getWidth(), slot.getHeight(), d.width, d.height, dialogFrame);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        if (lp.width == dialogFrame[2] && lp.height == dialogFrame[3]) return;
        lp.width = dialogFrame[2];
        lp.height = dialogFrame[3];
        lp.gravity = Gravity.CENTER;
        v.setLayoutParams(lp);
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

    @Override
    public boolean beginCarry(float x, float y, int pointerId) {
        return beginCarryOf(shownWindow(), x, y, pointerId);
    }

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

    /** One touch of the window during a carry; false when none is under way. */
    public boolean carryTouch(@NonNull MotionEvent ev) {
        return isCarrying() && carry.onTouch(ev);
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

    @Override
    public void closeWindow() {
        closeWin(shownWindow());
    }

    private void closeWin(@Nullable Win w) {
        if (w == null || service == null) return;
        try {
            service.close(w.id);
        } catch (RemoteException ignored) {
        }
    }

    @Override
    public void minimiseWindow() {
        Win w = shownWindow();
        if (w == null) return;
        w.placeWhenMinimised = host.placeOf(w.id);
        detachViews(w);
        setVisible(w, false);
        w.minimised = true;
        w.inRow = false;
        boundId = 0;
        host.removeWindow(w.id);
        rebuildChips();
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
        clipboard.setInFront(isFront);
        Win w = shownWindow();
        if (w != null) setVisible(w, isFront);
        if (isFront && w != null) focus(w.id);
        if (!isFront) focus(0);
        rebuildChips();
    }

    @NonNull
    @Override
    public TerminalKeyEventHandler.KeyValueInterceptor keys() {
        return keys;
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
        if (w == null || !w.scales()) return;
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

    /** What a pane's rim tab asks, for one window: the page's own Host, bound to {@code w}. */
    private final class PaneHost implements LinuxWindowPageView.Host {
        final Win w;

        PaneHost(@NonNull Win w) {
            this.w = w;
        }

        @Override public boolean hasWindow() { return w.inPane && windows.get(w.id) == w; }
        @Override public boolean windowScales() { return w.scales(); }
        @Override public void closeWindow() { closeWin(w); }
        @Override public void minimiseWindow() { minimisePane(w); }
        @Override public void frontChanged(boolean isFront) {}
        @NonNull @Override public TerminalKeyEventHandler.KeyValueInterceptor keys() { return keys; }
        @Override public boolean beginCarry(float x, float y, int pointerId) {
            return beginCarryOf(w, x, y, pointerId);
        }
        @Override public int currentScaleStep() { return scaleStepOf(w); }
        @Override public void onScaleStepChosen(int step) { chooseScale(w, step); }
        @Override public void onScaleRailUsed() {
            if (w.paneFrame != null) w.paneFrame.dismissRail();
        }
    }

    /**
     * Window {@code id}, in a row or already a pane, becomes a pane: it leaves its row, and the
     * frame its split shows is returned (one per window, kept until {@link #releasePane}). Null
     * when the window is gone or minimised.
     */
    @Nullable
    public LinuxWindowPageView takeIntoPane(long id) {
        Win w = windows.get(id);
        if (w == null || w.minimised) return null;
        if (w.inPane && w.paneFrame != null) return w.paneFrame;
        boolean wasRow = w.inRow;
        detachViews(w);
        if (boundId == id) boundId = 0;
        w.inRow = false;
        w.inPane = true;
        LinuxWindowPageView frame = new LinuxWindowPageView(context, new PaneHost(w));
        frame.setEmbedded(true);
        frame.applyStyle(paneStyle);
        w.paneFrame = frame;
        attachViews(w);
        setVisible(w, terminalShown);
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
            if (w.inPane) setVisible(w, shown);
        }
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
        if (w.inPane) host.paneTouched(w.id);
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
        host.removePane(w.id);
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

    private void sendConfigure(@NonNull Win w, int width, int height) throws RemoteException {
        if (service == null || width <= 0 || height <= 0) return;
        float scale = scale(w, width);
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
                if (d.parentId == w.id && !d.inRow) service.setVisible(d.id, visible);
            }
        } catch (RemoteException ignored) {
        }
    }

    private void focus(long id) {
        if (focusedId == id) return;
        focusedId = id;
        if (service == null) return;
        try {
            service.setFocus(id);
        } catch (RemoteException ignored) {
        }
    }

    private void sendKey(int keyCode, boolean down) {
        if (service == null || focusedId == 0 || windows.get(focusedId) == null) return;
        try {
            service.key(focusedId, keyCode, down, SystemClock.uptimeMillis());
        } catch (RemoteException ignored) {
        }
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
        try {
            service.pointer(w.id, action, button, x, y, time);
        } catch (RemoteException ignored) {
        }
    }

    /** The whole MotionEvent in one call: a second finger going down is one wl_touch frame. */
    private boolean onTouch(@NonNull Win w, @NonNull MotionEvent e) {
        if (e.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) return onMouse(w, e);
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) tapped(w);
        int n = Math.min(e.getPointerCount(), MAX_POINTS);
        for (int i = 0; i < n; i++) {
            w.slots[i] = e.getPointerId(i);
            w.xs[i] = e.getX(i);
            w.ys[i] = e.getY(i);
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
