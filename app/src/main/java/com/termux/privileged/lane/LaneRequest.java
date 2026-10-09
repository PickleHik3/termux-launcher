package com.termux.privileged.lane;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One request line of the lane's wire protocol, version 1. This is a contract with a separately
 * written C client, so the shape is exact:
 *
 * <pre>
 * tlpriv1 TAB run TAB &lt;absolute path&gt; TAB &lt;TERM&gt; TAB &lt;rows&gt; TAB &lt;cols&gt; [TAB &lt;arg&gt;]... LF
 * </pre>
 *
 * <p>Fields carry neither TAB nor LF (a TAB would split them; an LF ends the line), and the line
 * is UTF-8. The server answers {@code ok\t<pid>\n} with the pty master attached to that write,
 * {@code err\t<message>\n} when it refuses, and {@code exit\t<code>\n} once the child is gone.
 */
public final class LaneRequest {

    public static final String MAGIC = "tlpriv1";
    public static final String COMMAND_RUN = "run";
    public static final String DEFAULT_TERM = "xterm-256color";

    /** Larger than any terminal, small enough that a nonsense value cannot wrap {@code unsigned short}. */
    static final int MAX_DIMENSION = 10_000;
    /** More than any sensible command line; a client that sends more is not the client this was written for. */
    static final int MAX_LINE_BYTES = 64 * 1024;

    /** A request the server refuses before doing anything; the message is what the client prints. */
    public static final class Refused extends Exception {
        public Refused(@NonNull String message) {
            super(message);
        }
    }

    @NonNull public final String path;
    @NonNull public final String term;
    public final int rows;
    public final int cols;
    /** The child's arguments after argv[0]; never null. */
    @NonNull public final List<String> args;

    private LaneRequest(@NonNull String path, @NonNull String term, int rows, int cols, @NonNull List<String> args) {
        this.path = path;
        this.term = term;
        this.rows = rows;
        this.cols = cols;
        this.args = args;
    }

    /**
     * Parses one line, with or without its trailing LF.
     *
     * @throws Refused when the line is not a v1 {@code run} request the server can act on.
     */
    @NonNull
    public static LaneRequest parse(@NonNull String line) throws Refused {
        if (line.endsWith("\n")) line = line.substring(0, line.length() - 1);
        if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
            throw new Refused("request fields must not contain line breaks");
        }
        // -1 keeps trailing empty fields, so a missing TERM is a real (rejected) field rather
        // than a shift of everything after it.
        String[] fields = line.split("\t", -1);
        if (fields.length < 6) {
            throw new Refused("malformed request: expected "
                + "tlpriv1<TAB>run<TAB>path<TAB>TERM<TAB>rows<TAB>cols[<TAB>arg]...");
        }
        if (!MAGIC.equals(fields[0])) {
            throw new Refused("unsupported protocol '" + fields[0] + "' (this server speaks " + MAGIC + ")");
        }
        if (!COMMAND_RUN.equals(fields[1])) {
            throw new Refused("unsupported command '" + fields[1] + "'");
        }
        String path = fields[2];
        if (path.isEmpty() || !path.startsWith("/")) {
            throw new Refused("the binary path must be absolute");
        }
        if (path.indexOf('\0') >= 0) {
            throw new Refused("the binary path must not contain NUL");
        }
        String term = fields[3].isEmpty() ? DEFAULT_TERM : fields[3];
        if (!isSaneTerm(term)) {
            throw new Refused("TERM must be a plain terminal name");
        }
        int rows = parseDimension(fields[4], "rows");
        int cols = parseDimension(fields[5], "cols");
        List<String> args = fields.length > 6
            ? Collections.unmodifiableList(new ArrayList<>(Arrays.asList(fields).subList(6, fields.length)))
            : Collections.emptyList();
        for (String arg : args) {
            if (arg.indexOf('\0') >= 0) throw new Refused("arguments must not contain NUL");
        }
        return new LaneRequest(path, term, rows, cols, args);
    }

    private static int parseDimension(@NonNull String value, @NonNull String what) throws Refused {
        int number;
        try {
            number = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new Refused(what + " must be a number, not '" + value + "'");
        }
        if (number < 1 || number > MAX_DIMENSION) {
            throw new Refused(what + " must be between 1 and " + MAX_DIMENSION);
        }
        return number;
    }

    /** terminfo names: letters, digits and a few separators, nothing a shell or an env line could trip on. */
    private static boolean isSaneTerm(@NonNull String term) {
        if (term.length() > 64) return false;
        for (int i = 0; i < term.length(); i++) {
            char c = term.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '-' || c == '+' || c == '.' || c == '_';
            if (!ok) return false;
        }
        return true;
    }
}
