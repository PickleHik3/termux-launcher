package com.termux.app.x11;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The apps open on the display, as the window manager lists them, and the way to bring one to the
 * front. A plain X client on the display's own socket, speaking just enough of the core protocol
 * to read the EWMH properties on the root window and send the activation message back — so it
 * needs nothing installed in the prefix and learns of changes as they happen rather than by
 * polling. Without an EWMH window manager the list is simply empty.
 */
public final class X11WindowList {

    private static final String LOG_TAG = "X11WindowList";

    /** One app window on the display. */
    public static final class Window {
        public final int id;
        @NonNull public final String label;

        Window(int id, @NonNull String label) {
            this.id = id;
            this.label = label;
        }

        @Override public boolean equals(Object o) {
            return o instanceof Window && ((Window) o).id == id && ((Window) o).label.equals(label);
        }

        @Override public int hashCode() {
            return id * 31 + label.hashCode();
        }
    }

    public interface Listener {
        /** Called on the main thread with the windows in stacking-age order and which is in front. */
        void onWindowsChanged(@NonNull List<Window> windows, int activeIndex);
    }

    // Core protocol opcodes and predefined atoms.
    private static final int OP_CHANGE_WINDOW_ATTRIBUTES = 2;
    private static final int OP_INTERN_ATOM = 16;
    private static final int OP_GET_PROPERTY = 20;
    private static final int OP_SEND_EVENT = 25;
    private static final int ATOM_STRING = 31;
    private static final int ATOM_WM_NAME = 39;
    private static final int ATOM_WM_CLASS = 67;
    private static final int EVENT_PROPERTY_NOTIFY = 28;
    private static final int EVENT_CLIENT_MESSAGE = 33;
    private static final int EVENT_GENERIC = 35;
    private static final int MASK_PROPERTY_CHANGE = 0x400000;
    private static final int MASK_SUBSTRUCTURE_NOTIFY = 0x80000;
    private static final int MASK_SUBSTRUCTURE_REDIRECT = 0x100000;
    /** The value EWMH gives a request that comes from a pager or task switcher rather than the app. */
    private static final int SOURCE_PAGER = 2;

    @NonNull private final Handler main = new Handler(Looper.getMainLooper());
    @NonNull private final Listener listener;
    @NonNull private final String displayName;

    private final Object writeLock = new Object();
    @Nullable private LocalSocket socket;
    @Nullable private OutputStream out;
    @Nullable private DataInputStream in;
    private int sequence;
    private volatile boolean stopped;
    @Nullable private Thread thread;

    private int root;
    private int atomClientList, atomActiveWindow, atomNetWmName, atomUtf8String, atomNetWmState,
        atomSkipTaskbar, atomWindowType, atomTypeDock, atomTypeDesktop, atomCloseWindow;
    private boolean dirty;
    @NonNull private List<Window> published = Collections.emptyList();
    private int publishedActive = -1;

    public X11WindowList(@NonNull String displayName, @NonNull Listener listener) {
        this.displayName = displayName;
        this.listener = listener;
    }

    /** Connect and start following the list. Safe to call once per instance. */
    public void start() {
        if (thread != null) return;
        thread = new Thread(this::run, "X11WindowList");
        thread.setDaemon(true);
        thread.start();
    }

    /** Drop the connection; the listener hears nothing more from this instance. */
    public void stop() {
        stopped = true;
        closeQuietly();
    }

    /** Bring {@code window} to the front, the way a task switcher asks the window manager to. */
    public void activate(int window) {
        sendClientMessage(window, atomActiveWindow, SOURCE_PAGER, 0, 0);
    }

    /** Ask the window manager to close {@code window} as its own close button would. */
    public void close(int window) {
        sendClientMessage(window, atomCloseWindow, 0, SOURCE_PAGER, 0);
    }

    // ---- The worker -------------------------------------------------------------------------

    private void run() {
        try {
            connect();
            internAtoms();
            selectPropertyChanges(root);
            refresh();
            while (!stopped) {
                ByteBuffer message = readMessage();
                handleEvent(message);
                if (dirty) refresh();
            }
        } catch (IOException e) {
            if (!stopped) Logger.logDebug(LOG_TAG, "Display connection ended: " + e.getMessage());
        } catch (RuntimeException e) {
            Logger.logWarn(LOG_TAG, "Window list stopped: " + e);
        } finally {
            closeQuietly();
            publish(Collections.emptyList(), -1);
        }
    }

    private void connect() throws IOException {
        String number = displayName.startsWith(":") ? displayName.substring(1) : displayName;
        int dot = number.indexOf('.');
        if (dot >= 0) number = number.substring(0, dot);
        String path = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH + "/.X11-unix/X" + number;
        LocalSocket s = new LocalSocket();
        s.connect(new LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM));
        socket = s;
        out = s.getOutputStream();
        in = new DataInputStream(s.getInputStream());

        // Connection setup: little-endian, protocol 11.0, no authorization — the display accepts
        // local clients of its own user, which is how every prefix app reaches it too.
        ByteBuffer setup = buffer(12);
        setup.put((byte) 'l').put((byte) 0).putShort((short) 11).putShort((short) 0)
            .putShort((short) 0).putShort((short) 0).putShort((short) 0);
        out.write(setup.array());
        out.flush();

        byte[] header = new byte[8];
        in.readFully(header);
        ByteBuffer head = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int status = head.get(0) & 0xff;
        int length = (head.getShort(6) & 0xffff) * 4;
        byte[] body = new byte[length];
        in.readFully(body);
        if (status != 1) {
            int reasonLength = status == 0 ? header[1] & 0xff : length;
            throw new IOException("Display refused the connection: "
                + new String(body, 0, Math.min(reasonLength, body.length), StandardCharsets.ISO_8859_1));
        }
        ByteBuffer data = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        int vendorLength = data.getShort(16) & 0xffff;
        int formats = data.get(21) & 0xff;
        int screens = 32 + pad4(vendorLength) + formats * 8;
        root = data.getInt(screens);
    }

    private void internAtoms() throws IOException {
        atomClientList = internAtom("_NET_CLIENT_LIST");
        atomActiveWindow = internAtom("_NET_ACTIVE_WINDOW");
        atomNetWmName = internAtom("_NET_WM_NAME");
        atomUtf8String = internAtom("UTF8_STRING");
        atomNetWmState = internAtom("_NET_WM_STATE");
        atomSkipTaskbar = internAtom("_NET_WM_STATE_SKIP_TASKBAR");
        atomWindowType = internAtom("_NET_WM_WINDOW_TYPE");
        atomTypeDock = internAtom("_NET_WM_WINDOW_TYPE_DOCK");
        atomTypeDesktop = internAtom("_NET_WM_WINDOW_TYPE_DESKTOP");
        atomCloseWindow = internAtom("_NET_CLOSE_WINDOW");
    }

    /** Re-read the list and everything shown about each window, then tell the listener. */
    private void refresh() throws IOException {
        int guard = 0;
        do {
            dirty = false;
            int[] ids = getWindowsProperty(root, atomClientList);
            int[] active = getWindowsProperty(root, atomActiveWindow);
            int activeId = active.length > 0 ? active[0] : 0;
            List<Window> windows = new ArrayList<>(ids.length);
            int activeIndex = -1;
            for (int id : ids) {
                if (isHiddenFromSwitcher(id)) continue;
                selectPropertyChanges(id);
                if (id == activeId) activeIndex = windows.size();
                windows.add(new Window(id, labelOf(id)));
            }
            publish(windows, activeIndex);
        } while (dirty && ++guard < 8);
    }

    private boolean isHiddenFromSwitcher(int window) throws IOException {
        for (int state : getAtomsProperty(window, atomNetWmState)) {
            if (state == atomSkipTaskbar) return true;
        }
        for (int type : getAtomsProperty(window, atomWindowType)) {
            if (type == atomTypeDock || type == atomTypeDesktop) return true;
        }
        return false;
    }

    /**
     * The app's own name where it gives one, the way a task switcher labels things — the window
     * title is what the app is doing, which is too long for a chip and changes as it works.
     */
    @NonNull
    private String labelOf(int window) throws IOException {
        Property wmClass = getProperty(window, ATOM_WM_CLASS, ATOM_STRING);
        if (wmClass != null && wmClass.format == 8) {
            // Two NUL-terminated strings: the instance, then the class; the class is the app.
            String[] parts = new String(wmClass.data, StandardCharsets.ISO_8859_1).split("\0");
            String name = parts.length > 1 ? parts[1] : parts.length > 0 ? parts[0] : "";
            if (!name.trim().isEmpty()) return capitalise(name.trim());
        }
        Property title = getProperty(window, atomNetWmName, atomUtf8String);
        if (title != null && title.format == 8 && title.data.length > 0) {
            return new String(title.data, StandardCharsets.UTF_8).trim();
        }
        title = getProperty(window, ATOM_WM_NAME, ATOM_STRING);
        if (title != null && title.format == 8 && title.data.length > 0) {
            return new String(title.data, StandardCharsets.ISO_8859_1).trim();
        }
        return "";
    }

    @NonNull
    private static String capitalise(@NonNull String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void publish(@NonNull List<Window> windows, int activeIndex) {
        List<Window> copy = Collections.unmodifiableList(new ArrayList<>(windows));
        synchronized (this) {
            if (copy.equals(published) && activeIndex == publishedActive) return;
            published = copy;
            publishedActive = activeIndex;
        }
        main.post(() -> {
            if (!stopped) listener.onWindowsChanged(copy, activeIndex);
        });
    }

    private void handleEvent(@NonNull ByteBuffer message) {
        int type = message.get(0) & 0x7f;
        if (type == EVENT_PROPERTY_NOTIFY) {
            int atom = message.getInt(8);
            if (atom == atomClientList || atom == atomActiveWindow || atom == atomNetWmName
                    || atom == ATOM_WM_NAME || atom == ATOM_WM_CLASS || atom == atomNetWmState
                    || atom == atomWindowType) {
                dirty = true;
            }
        }
    }

    // ---- Requests ----------------------------------------------------------------------------

    private static final class Property {
        final int format;
        final int type;
        @NonNull final byte[] data;

        Property(int format, int type, @NonNull byte[] data) {
            this.format = format;
            this.type = type;
            this.data = data;
        }
    }

    private int internAtom(@NonNull String name) throws IOException {
        byte[] bytes = name.getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer request = buffer(8 + pad4(bytes.length));
        request.put((byte) OP_INTERN_ATOM).put((byte) 0)
            .putShort((short) (request.capacity() / 4))
            .putShort((short) bytes.length).putShort((short) 0).put(bytes);
        ByteBuffer reply = roundTrip(request);
        if (reply == null) throw new IOException("InternAtom " + name + " failed");
        return reply.getInt(8);
    }

    /** All PropertyNotify events for {@code window}; a window that has gone is not an error here. */
    private void selectPropertyChanges(int window) throws IOException {
        ByteBuffer request = buffer(16);
        request.put((byte) OP_CHANGE_WINDOW_ATTRIBUTES).put((byte) 0).putShort((short) 4)
            .putInt(window).putInt(0x800).putInt(MASK_PROPERTY_CHANGE);
        send(request);
    }

    @Nullable
    private Property getProperty(int window, int property, int type) throws IOException {
        ByteBuffer request = buffer(24);
        request.put((byte) OP_GET_PROPERTY).put((byte) 0).putShort((short) 6)
            .putInt(window).putInt(property).putInt(type).putInt(0).putInt(0x100000);
        ByteBuffer reply = roundTrip(request);
        if (reply == null) return null;
        int format = reply.get(1) & 0xff;
        int actualType = reply.getInt(8);
        int count = reply.getInt(16);
        if (format == 0 || count == 0) return null;
        int bytes = count * (format / 8);
        if (bytes > reply.capacity() - 32) return null;
        byte[] data = new byte[bytes];
        reply.position(32);
        reply.get(data);
        return new Property(format, actualType, data);
    }

    @NonNull
    private int[] getWindowsProperty(int window, int property) throws IOException {
        return asInts(getProperty(window, property, 0));
    }

    @NonNull
    private int[] getAtomsProperty(int window, int property) throws IOException {
        return asInts(getProperty(window, property, 0));
    }

    @NonNull
    private static int[] asInts(@Nullable Property property) {
        if (property == null || property.format != 32) return new int[0];
        ByteBuffer data = ByteBuffer.wrap(property.data).order(ByteOrder.LITTLE_ENDIAN);
        int[] values = new int[property.data.length / 4];
        for (int i = 0; i < values.length; i++) values[i] = data.getInt(i * 4);
        return values;
    }

    /** A ClientMessage to the root window on behalf of {@code window}, as EWMH has switchers do. */
    private void sendClientMessage(int window, int messageType, int d0, int d1, int d2) {
        if (messageType == 0 || out == null) return;
        ByteBuffer request = buffer(44);
        request.put((byte) OP_SEND_EVENT).put((byte) 0).putShort((short) 11)
            .putInt(root).putInt(MASK_SUBSTRUCTURE_NOTIFY | MASK_SUBSTRUCTURE_REDIRECT)
            .put((byte) EVENT_CLIENT_MESSAGE).put((byte) 32).putShort((short) 0)
            .putInt(window).putInt(messageType)
            .putInt(d0).putInt(d1).putInt(d2).putInt(0).putInt(0);
        try {
            send(request);
        } catch (IOException e) {
            Logger.logDebug(LOG_TAG, "Could not reach the display: " + e.getMessage());
        }
    }

    // ---- The wire ----------------------------------------------------------------------------

    private int send(@NonNull ByteBuffer request) throws IOException {
        synchronized (writeLock) {
            OutputStream stream = out;
            if (stream == null) throw new IOException("not connected");
            stream.write(request.array());
            stream.flush();
            sequence = (sequence + 1) & 0xffff;
            return sequence;
        }
    }

    /** Send and wait for the reply, handling the events that arrive meanwhile; null on an error. */
    @Nullable
    private ByteBuffer roundTrip(@NonNull ByteBuffer request) throws IOException {
        int expected = send(request);
        while (true) {
            ByteBuffer message = readMessage();
            int type = message.get(0) & 0xff;
            int seq = message.getShort(2) & 0xffff;
            if (type == 1 && seq == expected) return message;
            if (type == 0) {
                if (seq == expected) return null;
                continue;
            }
            if (type > 1) handleEvent(message);
        }
    }

    @NonNull
    private ByteBuffer readMessage() throws IOException {
        DataInputStream stream = in;
        if (stream == null) throw new IOException("not connected");
        byte[] head = new byte[32];
        stream.readFully(head);
        int type = head[0] & 0x7f;
        int extra = 0;
        if (type == 1 || type == EVENT_GENERIC) {
            extra = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN).getInt(4) * 4;
        }
        if (extra <= 0) return ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        byte[] whole = new byte[32 + extra];
        System.arraycopy(head, 0, whole, 0, 32);
        stream.readFully(whole, 32, extra);
        return ByteBuffer.wrap(whole).order(ByteOrder.LITTLE_ENDIAN);
    }

    private void closeQuietly() {
        LocalSocket s = socket;
        socket = null;
        out = null;
        in = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // Already gone.
            }
        }
    }

    @NonNull
    private static ByteBuffer buffer(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static int pad4(int n) {
        return (n + 3) & ~3;
    }
}
