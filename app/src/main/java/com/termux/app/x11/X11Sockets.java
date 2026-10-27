package com.termux.app.x11;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Which displays are really up. A server that gets SIGTERM leaves its socket file behind, so the
 * file being there says nothing; only a connection that is accepted does. Anything that asks
 * "is a display running?" goes through here, and a dead socket found on the way is removed
 * together with its lock so it cannot be mistaken for a server again.
 */
public final class X11Sockets {

    private static final Pattern DISPLAY_SOCKET = Pattern.compile("X(\\d+)");

    private X11Sockets() {}

    /** Asks whether something listens on a socket file. */
    public interface Probe {
        boolean answers(@NonNull File socket);
    }

    /** The real probe: connect to the file and hang up at once. */
    public static final Probe LOCAL_SOCKET = socket -> {
        try (LocalSocket s = new LocalSocket()) {
            s.connect(new LocalSocketAddress(socket.getAbsolutePath(),
                LocalSocketAddress.Namespace.FILESYSTEM));
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    };

    /** {@code $PREFIX/tmp/.X11-unix}, where the server puts its sockets. */
    @NonNull
    public static File socketDir() {
        return new File(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, ".X11-unix");
    }

    /**
     * The numbers of the displays whose socket answers, sorted. A socket that does not answer is
     * deleted with its {@code .X<n>-lock} (which sits in the directory above). Only {@code X<n>}
     * entries are looked at; every other name in the directory is left alone.
     */
    @NonNull
    public static List<Integer> liveDisplays(@NonNull File socketDir, @NonNull Probe probe) {
        List<Integer> live = new ArrayList<>();
        String[] names = socketDir.list();
        if (names == null) return live;
        for (String name : names) {
            if (!DISPLAY_SOCKET.matcher(name).matches()) continue;
            int n;
            try {
                n = Integer.parseInt(name.substring(1));
            } catch (NumberFormatException tooLong) {
                continue;
            }
            File socket = new File(socketDir, name);
            if (probe.answers(socket)) {
                live.add(n);
            } else {
                //noinspection ResultOfMethodCallIgnored
                socket.delete();
                File parent = socketDir.getParentFile();
                if (parent != null) {
                    //noinspection ResultOfMethodCallIgnored
                    new File(parent, ".X" + name.substring(1) + "-lock").delete();
                }
            }
        }
        Collections.sort(live);
        return live;
    }
}
