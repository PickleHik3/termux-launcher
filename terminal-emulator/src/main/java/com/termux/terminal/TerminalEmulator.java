package com.termux.terminal;

import android.graphics.Bitmap;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Stack;

/**
 * Renders text into a screen. Contains all the terminal-specific knowledge and state. Emulates a subset of the X Window
 * System xterm terminal, which in turn is an emulator for a subset of the Digital Equipment Corporation vt100 terminal.
 * <p>
 * References:
 * <ul>
 * <li>http://invisible-island.net/xterm/ctlseqs/ctlseqs.html</li>
 * <li>http://en.wikipedia.org/wiki/ANSI_escape_code</li>
 * <li>http://man.he.net/man4/console_codes</li>
 * <li>http://bazaar.launchpad.net/~leonerd/libvterm/trunk/view/head:/src/state.c</li>
 * <li>http://www.columbia.edu/~kermit/k95manual/iso2022.html</li>
 * <li>http://www.vt100.net/docs/vt510-rm/chapter4</li>
 * <li>http://en.wikipedia.org/wiki/ISO/IEC_2022 - for 7-bit and 8-bit GL GR explanation</li>
 * <li>http://bjh21.me.uk/all-escapes/all-escapes.txt - extensive!</li>
 * <li>http://woldlab.caltech.edu/~diane/kde4.10/workingdir/kubuntu/konsole/doc/developer/old-documents/VT100/techref.
 * html - document for konsole - accessible!</li>
 * </ul>
 */
public final class TerminalEmulator {

    /**
     * Log unknown or unimplemented escape sequences received from the shell process.
     */
    private static final boolean LOG_ESCAPE_SEQUENCES = false;

    public static final int MOUSE_LEFT_BUTTON = 0;

    /**
     * Mouse moving while having left mouse button pressed.
     */
    public static final int MOUSE_LEFT_BUTTON_MOVED = 32;

    public static final int MOUSE_WHEELUP_BUTTON = 64;

    public static final int MOUSE_WHEELDOWN_BUTTON = 65;
    
    public static final int MOUSE_WHEEL_LEFT = 66;
    public static final int MOUSE_WHEEL_RIGHT = 67;

    /**
     * Used for invalid data - http://en.wikipedia.org/wiki/Replacement_character#Replacement_character
     */
    public static final int UNICODE_REPLACEMENT_CHAR = 0xFFFD;

    /**
     * Escape processing: Not currently in an escape sequence.
     */
    private static final int ESC_NONE = 0;

    /**
     * Escape processing: Have seen an ESC character - proceed to {@link #doEsc(int)}
     */
    private static final int ESC = 1;

    /**
     * Escape processing: Have seen ESC POUND
     */
    private static final int ESC_POUND = 2;

    /**
     * Escape processing: Have seen ESC and a character-set-select ( char
     */
    private static final int ESC_SELECT_LEFT_PAREN = 3;

    /**
     * Escape processing: Have seen ESC and a character-set-select ) char
     */
    private static final int ESC_SELECT_RIGHT_PAREN = 4;

    /**
     * Escape processing: "ESC [" or CSI (Control Sequence Introducer).
     */
    private static final int ESC_CSI = 6;

    /**
     * Escape processing: ESC [ ?
     */
    private static final int ESC_CSI_QUESTIONMARK = 7;

    /**
     * Escape processing: ESC [ $
     */
    private static final int ESC_CSI_DOLLAR = 8;

    /**
     * Escape processing: ESC %
     */
    private static final int ESC_PERCENT = 9;

    /**
     * Escape processing: ESC ] (AKA OSC - Operating System Controls)
     */
    private static final int ESC_OSC = 10;

    /**
     * Escape processing: ESC ] (AKA OSC - Operating System Controls) ESC
     */
    private static final int ESC_OSC_ESC = 11;

    /**
     * Escape processing: ESC [ >
     */
    private static final int ESC_CSI_BIGGERTHAN = 12;

    /**
     * Escape procession: "ESC P" or Device Control String (DCS)
     */
    private static final int ESC_P = 13;

    /**
     * Escape processing: CSI >
     */
    private static final int ESC_CSI_QUESTIONMARK_ARG_DOLLAR = 14;

    /**
     * Escape processing: CSI $ARGS ' '
     */
    private static final int ESC_CSI_ARGS_SPACE = 15;

    /**
     * Escape processing: CSI $ARGS '*'
     */
    private static final int ESC_CSI_ARGS_ASTERIX = 16;

    /**
     * Escape processing: CSI "
     */
    private static final int ESC_CSI_DOUBLE_QUOTE = 17;

    /**
     * Escape processing: CSI '
     */
    private static final int ESC_CSI_SINGLE_QUOTE = 18;

    /**
     * Escape processing: CSI !
     */
    private static final int ESC_CSI_EXCLAMATION = 19;
    /** Escape processing: "ESC _" or Application Program Command (APC). */
    private static final int ESC_APC = 20;
    /** Escape processing: "ESC _" or Application Program Command (APC), followed by Escape. */
    private static final int ESC_APC_ESCAPE = 21;
    /** Escape processing: ESC [ <parameter bytes> */
    private static final int ESC_CSI_UNSUPPORTED_PARAMETER_BYTE = 22;
    /** Escape processing: ESC [ <parameter bytes> <intermediate bytes> */
    private static final int ESC_CSI_UNSUPPORTED_INTERMEDIATE_BYTE = 23;
    /** Escape processing: "ESC [ =", used by the kitty keyboard protocol to set its flags. */
    private static final int ESC_CSI_EQUAL = 24;
    /** Escape processing: "ESC [ <", used by the kitty keyboard protocol to pop its mode stack. */
    private static final int ESC_CSI_LESSTHAN = 25;

    /** Escape processing: CSI &gt; parameters SPACE, used by kitty multiple cursors. */
    private static final int ESC_CSI_BIGGERTHAN_ARGS_SPACE = 26;

    /** The number of parameter arguments including colon separated sub-parameters. */
    static final int MAX_ESCAPE_PARAMETERS = 32;

    /** Maximum CSI payload before abandoning a sequence that never supplies a final byte. */
    static final int MAX_CSI_SEQUENCE_LENGTH = 256;

    /** Default maximum length for OSC, non-sixel DCS, and ignored APC payloads. */
    static final int MAX_STRING_SEQUENCE_LENGTH = 16 * 1024;

    /** OSC 52 carries base64 clipboard data and intentionally has a larger, still finite limit. */
    static final int MAX_CLIPBOARD_SEQUENCE_LENGTH = (100 * 1024) + 10;

    /** Sixel and iTerm image strings may be large, but never larger than the bitmap memory cap. */
    static final int MAX_IMAGE_SEQUENCE_LENGTH = TerminalBitmap.MAX_BITMAP_SIZE + 150;

    /**
     * DECSET 1 - application cursor keys.
     */
    private static final int DECSET_BIT_APPLICATION_CURSOR_KEYS = 1;

    private static final int DECSET_BIT_REVERSE_VIDEO = 1 << 1;

    /**
     * http://www.vt100.net/docs/vt510-rm/DECOM: "When DECOM is set, the home cursor position is at the upper-left
     * corner of the screen, within the margins. The starting point for line numbers depends on the current top margin
     * setting. The cursor cannot move outside of the margins. When DECOM is reset, the home cursor position is at the
     * upper-left corner of the screen. The starting point for line numbers is independent of the margins. The cursor
     * can move outside of the margins."
     */
    private static final int DECSET_BIT_ORIGIN_MODE = 1 << 2;

    /**
     * http://www.vt100.net/docs/vt510-rm/DECAWM: "If the DECAWM function is set, then graphic characters received when
     * the cursor is at the right border of the page appear at the beginning of the next line. Any text on the page
     * scrolls up if the cursor is at the end of the scrolling region. If the DECAWM function is reset, then graphic
     * characters received when the cursor is at the right border of the page replace characters already on the page."
     */
    private static final int DECSET_BIT_AUTOWRAP = 1 << 3;

    /**
     * DECSET 25 - if the cursor should be enabled, {@link #isCursorEnabled()}.
     */
    private static final int DECSET_BIT_CURSOR_ENABLED = 1 << 4;

    private static final int DECSET_BIT_APPLICATION_KEYPAD = 1 << 5;

    /**
     * DECSET 1000 - if to report mouse press&release events.
     */
    private static final int DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE = 1 << 6;

    /**
     * DECSET 1002 - like 1000, but report moving mouse while pressed.
     */
    private static final int DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT = 1 << 7;

    /**
     * DECSET 1004 - NOT implemented.
     */
    private static final int DECSET_BIT_SEND_FOCUS_EVENTS = 1 << 8;

    /**
     * DECSET 1006 - SGR-like mouse protocol (the modern sane choice).
     */
    private static final int DECSET_BIT_MOUSE_PROTOCOL_SGR = 1 << 9;

    /**
     * DECSET 2004 - see {@link #paste(String)}
     */
    private static final int DECSET_BIT_BRACKETED_PASTE_MODE = 1 << 10;

    /**
     * Toggled with DECLRMM - http://www.vt100.net/docs/vt510-rm/DECLRMM
     */
    private static final int DECSET_BIT_LEFTRIGHT_MARGIN_MODE = 1 << 11;

    /**
     * Not really DECSET bit... - http://www.vt100.net/docs/vt510-rm/DECSACE
     */
    private static final int DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE = 1 << 12;

    private String mTitle;

    private final Stack<String> mTitleStack = new Stack<>();

    /**
     * The cursor position. Between (0,0) and (mRows-1, mColumns-1).
     */
    private int mCursorRow, mCursorCol;

    /**
     * The number of character rows and columns in the terminal screen.
     */
    public int mRows, mColumns;

    /** Size of a terminal cell in pixels. */
    private int mCellWidthPixels, mCellHeightPixels;

    /** The number of terminal transcript rows that can be scrolled back to. */
    public static final int TERMINAL_TRANSCRIPT_ROWS_MIN = 100;

    public static final int TERMINAL_TRANSCRIPT_ROWS_MAX = 50000;

    public static final int DEFAULT_TERMINAL_TRANSCRIPT_ROWS = 2000;

    /* The supported terminal cursor styles. */
    public static final int TERMINAL_CURSOR_STYLE_BLOCK = 0;

    public static final int TERMINAL_CURSOR_STYLE_UNDERLINE = 1;

    public static final int TERMINAL_CURSOR_STYLE_BAR = 2;

    public static final int DEFAULT_TERMINAL_CURSOR_STYLE = TERMINAL_CURSOR_STYLE_BLOCK;

    public static final Integer[] TERMINAL_CURSOR_STYLES_LIST = new Integer[] { TERMINAL_CURSOR_STYLE_BLOCK, TERMINAL_CURSOR_STYLE_UNDERLINE, TERMINAL_CURSOR_STYLE_BAR };

    /**
     * The terminal cursor styles.
     */
    private int mCursorStyle = DEFAULT_TERMINAL_CURSOR_STYLE;

    /** One kitty-protocol cursor. Coordinates are fixed screen cells, zero based. */
    public static final class ExtraCursor {
        public final int row;
        public final int col;
        /** 1 block, 2 bar, 3 underline, 29 follow the main cursor. */
        public final int shape;

        ExtraCursor(int row, int col, int shape) {
            this.row = row;
            this.col = col;
            this.shape = shape;
        }
    }

    /** Kitty dynamic color: 0 unset, 1 special/reverse, 2 RGB, 5 palette index. */
    public static final class ExtraCursorColor {
        public int type;
        public int value;
    }

    private final LinkedHashMap<Integer, ExtraCursor> mExtraCursors = new LinkedHashMap<>();
    private ExtraCursor[] mExtraCursorSnapshot = new ExtraCursor[0];
    private final ExtraCursorColor mExtraCursorTextColor = new ExtraCursorColor();
    private final ExtraCursorColor mExtraCursorColor = new ExtraCursorColor();

    /**
     * The normal screen buffer. Stores the characters that appear on the screen of the emulated terminal.
     */
    private final TerminalBuffer mMainBuffer;

    /**
     * The alternate screen buffer, exactly as large as the display and contains no additional saved lines (so that when
     * the alternate screen buffer is active, you cannot scroll back to view saved lines).
     * <p>
     * See http://www.xfree86.org/current/ctlseqs.html#The%20Alternate%20Screen%20Buffer
     */
    final TerminalBuffer mAltBuffer;

    /**
     * The current screen buffer, pointing at either {@link #mMainBuffer} or {@link #mAltBuffer}.
     */
    private TerminalBuffer mScreen;

    /**
     * The terminal session this emulator is bound to.
     */
    private final TerminalOutput mSession;

    private final KittyGraphicsProtocol mKittyGraphics;

    TerminalSessionClient mClient;

    /**
     * Keeps track of the current argument of the current escape sequence. Ranges from 0 to MAX_ESCAPE_PARAMETERS-1.
     */
    private int mArgIndex;

    /**
     * Holds the arguments of the current escape sequence.
     */
    private final int[] mArgs = new int[MAX_ESCAPE_PARAMETERS];
    /** Holds the bit flags which arguments are sub parameters (after a colon) - bit N is set if <code>mArgs[N]</code> is a sub parameter. */
    private int mArgsSubParamsBitSet = 0;

    /**
     * Holds OSC and device control arguments, which can be strings.
     */
    private final StringBuilder mOSCOrDeviceControlArgs = new StringBuilder();

    /**
     * True if the current escape sequence should continue, false if the current escape sequence should be terminated.
     * Used when parsing a single character.
     */
    private boolean mContinueSequence;

    /**
     * The current state of the escape sequence state machine. One of the ESC_* constants.
     */
    private int mEscapeState;

    private boolean ESC_P_escape = false;

    private boolean ESC_P_sixel = false;

    private int mOscStringMaxLength = MAX_STRING_SEQUENCE_LENGTH;
    private int mCsiSequenceLength;
    private int mApcSequenceLength;
    private int mDcsSequenceLength;
    private int mDcsSequenceMaxLength = MAX_STRING_SEQUENCE_LENGTH;
    private boolean mIgnoreCrLfForOsc = false;
    private ITermImage mITermImage;

    private final SavedScreenState mSavedStateMain = new SavedScreenState();

    private final SavedScreenState mSavedStateAlt = new SavedScreenState();

    /**
     * http://www.vt100.net/docs/vt102-ug/table5-15.html
     */
    private boolean mUseLineDrawingG0, mUseLineDrawingG1, mUseLineDrawingUsesG0 = true;

    /**
     * @see TerminalEmulator#mapDecSetBitToInternalBit(int)
     */
    private int mCurrentDecSetFlags, mSavedDecSetFlags;

    /**
     * If insert mode (as opposed to replace mode) is active. In insert mode new characters are inserted, pushing
     * existing text to the right. Characters moved past the right margin are lost.
     */
    private boolean mInsertMode;

    /**
     * An array of tab stops. mTabStop[i] is true if there is a tab stop set for column i.
     */
    private boolean[] mTabStop;

    /**
     * Top margin of screen for scrolling ranges from 0 to mRows-2. Bottom margin ranges from mTopMargin + 2 to mRows
     * (Defines the first row after the scrolling region). Left/right margin in [0, mColumns].
     */
    private int mTopMargin, mBottomMargin, mLeftMargin, mRightMargin;

    /**
     * If the next character to be emitted will be automatically wrapped to the next line. Used to disambiguate the case
     * where the cursor is positioned on the last column (mColumns-1). When standing there, a written character will be
     * output in the last column, the cursor not moving but this flag will be set. When outputting another character
     * this will move to the next line.
     */
    private boolean mAboutToAutoWrap;

    /**
     * If the cursor blinking is enabled. It requires cursor itself to be enabled, which is controlled
     * byt whether {@link #DECSET_BIT_CURSOR_ENABLED} bit is set or not.
     */
    private boolean mCursorBlinkingEnabled;

    /**
     * If currently cursor should be in a visible state or not if {@link #mCursorBlinkingEnabled}
     * is {@code true}.
     */
    private boolean mCursorBlinkState;

    /**
     * Current foreground, background and underline colors. Can either be a color index in [0,259] or a truecolor (24-bit) value.
     * For a 24-bit value the top byte (0xff000000) is set.
     *
     * @see TextStyle
     */
    int mForeColor, mBackColor, mUnderlineColor;

    /** Current {@link TextStyle} effect. */
    int mEffect;

    /**
     * The current underline style, one of the {@code TextStyle.UNDERLINE_STYLE_*} values. Kept beside
     * {@link #mEffect} because it is a field rather than a flag, but always consistent with
     * {@link TextStyle#CHARACTER_ATTRIBUTE_UNDERLINE}: the attribute is set exactly when this is not
     * {@link TextStyle#UNDERLINE_STYLE_NONE}.
     */
    int mUnderlineStyle;

    /** The OSC 8 link that text is currently part of, or {@link TerminalHyperlinks#NO_LINK}. */
    private int mCurrentHyperlinkId = TerminalHyperlinks.NO_LINK;

    /** No command has finished, or the shell reported no usable status. See {@link #getLastCommandExitCode()}. */
    public static final int COMMAND_EXIT_CODE_UNKNOWN = -1;

    private int mLastCommandExitCode = COMMAND_EXIT_CODE_UNKNOWN;

    /** Whether any OSC 133 mark has been seen, which is how the app knows shell integration is set up. */
    private boolean mShellIntegrationSeen;
    /** True between OSC 133;C (command output) and OSC 133;D/prompt return. */
    private boolean mShellIntegrationCommandRunning;

    /**
     * The kitty keyboard protocol state of one screen: its active flags and its mode stack.
     * <p>
     * The main and alternate screens keep separate stacks, as the protocol requires: an editor can then
     * change the mode on the alternate screen without knowing or disturbing what the shell set on the
     * main one.
     * </p>
     */
    private static final class KeyboardModes {

        /** Bounded so that a program cannot push its way through the session's memory. */
        static final int MAX_DEPTH = 16;

        int flags;

        final int[] stack = new int[MAX_DEPTH];

        int depth;

        void push(int newFlags) {
            if (depth == MAX_DEPTH) {
                // Evict the oldest entry, as the specification prescribes for a full stack.
                System.arraycopy(stack, 1, stack, 0, MAX_DEPTH - 1);
                depth--;
            }
            stack[depth++] = flags;
            flags = newFlags;
        }

        void pop(int count) {
            for (int i = 0; i < count; i++) {
                if (depth == 0) {
                    // Popping an empty stack resets the flags rather than being an error.
                    flags = 0;
                    return;
                }
                flags = stack[--depth];
            }
        }

        void reset() {
            flags = 0;
            depth = 0;
        }
    }

    private final KeyboardModes mKeyboardModesMain = new KeyboardModes();

    private final KeyboardModes mKeyboardModesAlt = new KeyboardModes();

    private KeyboardModes keyboardModes() {
        return (mScreen == mMainBuffer) ? mKeyboardModesMain : mKeyboardModesAlt;
    }

    /**
     * The kitty keyboard protocol enhancements the program on the current screen has asked for, as a bit
     * set of the {@code KittyKeyEncoder.FLAG_*} values. Zero means legacy key encoding.
     */
    public int getKeyboardFlags() {
        return keyboardModes().flags;
    }

    /**
     * "CSI = flags ; mode u" - set the keyboard enhancement flags. Mode 1 replaces them, 2 sets the
     * given bits, 3 clears the given bits.
     */
    private void setKeyboardFlags(int flags, int mode) {
        flags &= KittyKeyEncoder.FLAGS_MASK;
        KeyboardModes modes = keyboardModes();
        switch(mode) {
            case 1:
                modes.flags = flags;
                break;
            case 2:
                modes.flags |= flags;
                break;
            case 3:
                modes.flags &= ~flags;
                break;
            default:
                unknownParameter(mode);
                break;
        }
    }

    private final TerminalHyperlinks mHyperlinks = new TerminalHyperlinks();

    /**
     * The number of scrolled lines since last calling {@link #clearScrollCounter()}. Used for moving selection up along
     * with the scrolling text.
     */
    private int mScrollCounter = 0;

    /**
     * Total lines ever scrolled off the top, never reset (unlike {@link #mScrollCounter}, which the
     * view consumes and clears). A kitty graphics placement captures the cursor row when the command
     * arrives but lands after an asynchronous decode; the difference in this count across that gap is
     * how many rows the captured anchor has since moved up.
     */
    private long mScrollEventCount;

    /**
     * If automatic scrolling of terminal is disabled
     */
    private boolean mAutoScrollDisabled;

    private final boolean mBoldWithBright;

    private byte mUtf8ToFollow, mUtf8Index;

    private final byte[] mUtf8InputBuffer = new byte[4];

    private int mLastEmittedCodePoint = -1;

    private final GraphemeClusterer mGraphemeClusterer = new GraphemeClusterer();
    private int mLastGraphemeRow = -1;
    private int mLastGraphemeColumn = -1;
    private int mLastGraphemeCursorRow = -1;
    private int mLastGraphemeCursorCol = -1;
    private boolean mLastGraphemeAboutToAutoWrap;

    public final TerminalColors mColors = new TerminalColors();

    private static final String LOG_TAG = "TerminalEmulator";

    private int cellW = 12, cellH = 12;

    /** Terminal name and version reported for XTVERSION ("CSI > 0 q"); the app supplies its version at startup. */
    private static String sXtVersionName = "termux-launcher";

    public static void setXtVersion(String appVersionName) {
        sXtVersionName = appVersionName == null || appVersionName.isEmpty()
            ? "termux-launcher" : "termux-launcher(" + appVersionName + ")";
    }

    public void setCellSize(int w, int h) {
        cellW = w;
        cellH = h;
    }

    long scrollEventCount() {
        return mScrollEventCount;
    }

    public int getCellWidthPixels() {
        return mCellWidthPixels;
    }

    public int getCellHeightPixels() {
        return mCellHeightPixels;
    }

    public int getRows() {
        return mRows;
    }

    public int getColumns() {
        return mColumns;
    }

    private boolean isDecsetInternalBitSet(int bit) {
        return (mCurrentDecSetFlags & bit) != 0;
    }

    private void setDecsetinternalBit(int internalBit, boolean set) {
        if (set) {
            // The mouse modes are mutually exclusive.
            if (internalBit == DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) {
                setDecsetinternalBit(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT, false);
            } else if (internalBit == DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT) {
                setDecsetinternalBit(DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE, false);
            }
        }
        if (set) {
            mCurrentDecSetFlags |= internalBit;
        } else {
            mCurrentDecSetFlags &= ~internalBit;
        }
    }

    static int mapDecSetBitToInternalBit(int decsetBit) {
        switch(decsetBit) {
            case 1:
                return DECSET_BIT_APPLICATION_CURSOR_KEYS;
            case 5:
                return DECSET_BIT_REVERSE_VIDEO;
            case 6:
                return DECSET_BIT_ORIGIN_MODE;
            case 7:
                return DECSET_BIT_AUTOWRAP;
            case 25:
                return DECSET_BIT_CURSOR_ENABLED;
            case 66:
                return DECSET_BIT_APPLICATION_KEYPAD;
            case 69:
                return DECSET_BIT_LEFTRIGHT_MARGIN_MODE;
            case 1000:
                return DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE;
            case 1002:
            case 1003:
                return DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT;
            case 1004:
                return DECSET_BIT_SEND_FOCUS_EVENTS;
            case 1006:
                return DECSET_BIT_MOUSE_PROTOCOL_SGR;
            case 2004:
                return DECSET_BIT_BRACKETED_PASTE_MODE;
            default:
                return -1;
        }
    }

    public TerminalEmulator(TerminalOutput session, boolean boldWithBright, int columns, int rows, int cellWidthPixels, int cellHeightPixels, Integer transcriptRows, TerminalSessionClient client) {
        mSession = session;
        mScreen = mMainBuffer = new TerminalBuffer(client, columns, getTerminalTranscriptRows(transcriptRows), rows);
        mAltBuffer = new TerminalBuffer(client, columns, rows, rows);
        mClient = client;
        mBoldWithBright = boldWithBright;
        mRows = rows;
        mColumns = columns;
        mCellWidthPixels = cellWidthPixels;
        mCellHeightPixels = cellHeightPixels;
        mTabStop = new boolean[mColumns];
        mKittyGraphics = new KittyGraphicsProtocol(this, session);
        TerminalBuffer.UnreachableImageListener collected = this::onKittyCellsCollected;
        mMainBuffer.setUnreachableImageListener(collected);
        mAltBuffer.setUnreachableImageListener(collected);
        reset();
    }

    public void updateTerminalSessionClient(TerminalSessionClient client) {
        mClient = client;
        setCursorStyle();
        setCursorBlinkState(true);
    }

    public TerminalBuffer getScreen() {
        return mScreen;
    }

    public boolean isAlternateBufferActive() {
        return mScreen == mAltBuffer;
    }

    private int getTerminalTranscriptRows(Integer transcriptRows) {
        if (transcriptRows == null || transcriptRows < TERMINAL_TRANSCRIPT_ROWS_MIN || transcriptRows > TERMINAL_TRANSCRIPT_ROWS_MAX)
            return DEFAULT_TERMINAL_TRANSCRIPT_ROWS;
        else
            return transcriptRows;
    }

    /**
     * @param mouseButton one of the MOUSE_* constants of this class.
     */
    public void sendMouseEvent(int mouseButton, int column, int row, boolean pressed) {
        if (column < 1)
            column = 1;
        if (column > mColumns)
            column = mColumns;
        if (row < 1)
            row = 1;
        if (row > mRows)
            row = mRows;
        if (mouseButton == MOUSE_LEFT_BUTTON_MOVED && !isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT)) {
            // Do not send tracking.
        } else if (isDecsetInternalBitSet(DECSET_BIT_MOUSE_PROTOCOL_SGR)) {
            mSession.write(String.format("\033[<%d;%d;%d" + (pressed ? 'M' : 'm'), mouseButton, column, row));
        } else {
            // 3 for release of all buttons.
            mouseButton = pressed ? mouseButton : 3;
            // Clip to screen, and clip to the limits of 8-bit data.
            boolean out_of_bounds = column > 255 - 32 || row > 255 - 32;
            if (!out_of_bounds) {
                byte[] data = { '\033', '[', 'M', (byte) (32 + mouseButton), (byte) (32 + column), (byte) (32 + row) };
                mSession.write(data, 0, data.length);
            }
        }
    }

    public void resize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        resize(columns, rows, cellWidthPixels, cellHeightPixels, false);
    }

    public void resize(int columns, int rows, int cellWidthPixels, int cellHeightPixels,
                       boolean keepCursorAtBottom) {
        this.mCellWidthPixels = cellWidthPixels;
        this.mCellHeightPixels = cellHeightPixels;

        if (mRows == rows && mColumns == columns) {
            return;
        } else if (columns < 2 || rows < 2) {
            throw new IllegalArgumentException("rows=" + rows + ", columns=" + columns);
        }
        if (mRows != rows) {
            mRows = rows;
            mTopMargin = 0;
            mBottomMargin = mRows;
        }
        if (mColumns != columns) {
            int oldColumns = mColumns;
            mColumns = columns;
            boolean[] oldTabStop = mTabStop;
            mTabStop = new boolean[mColumns];
            setDefaultTabStops();
            int toTransfer = Math.min(oldColumns, columns);
            System.arraycopy(oldTabStop, 0, mTabStop, 0, toTransfer);
            mLeftMargin = 0;
            mRightMargin = mColumns;
        }
        resizeScreen(keepCursorAtBottom);
    }

    private void resizeScreen() {
        resizeScreen(false);
    }

    private void resizeScreen(boolean keepCursorAtBottom) {
        final int[] cursor = { mCursorCol, mCursorRow };
        int newTotalRows = (mScreen == mAltBuffer) ? mRows : mMainBuffer.mTotalRows;
        mScreen.resize(mColumns, mRows, newTotalRows, cursor, getStyle(),
            isAlternateBufferActive(), keepCursorAtBottom);
        mCursorCol = cursor[0];
        mCursorRow = cursor[1];
        resetGraphemeTracking();
    }

    public int getCursorRow() {
        return mCursorRow;
    }

    public int getCursorCol() {
        return mCursorCol;
    }

    /**
     * Get the terminal cursor style. It will be one of {@link #TERMINAL_CURSOR_STYLES_LIST}
     */
    public int getCursorStyle() {
        return mCursorStyle;
    }

    /**
     * Set the terminal cursor style.
     */
    public void setCursorStyle() {
        Integer cursorStyle = null;
        if (mClient != null)
            cursorStyle = mClient.getTerminalCursorStyle();
        if (cursorStyle == null || !Arrays.asList(TERMINAL_CURSOR_STYLES_LIST).contains(cursorStyle))
            mCursorStyle = DEFAULT_TERMINAL_CURSOR_STYLE;
        else
            mCursorStyle = cursorStyle;
    }

    public boolean isReverseVideo() {
        return isDecsetInternalBitSet(DECSET_BIT_REVERSE_VIDEO);
    }

    public boolean isCursorEnabled() {
        return isDecsetInternalBitSet(DECSET_BIT_CURSOR_ENABLED);
    }

    public boolean shouldCursorBeVisible() {
        if (!isCursorEnabled())
            return false;
        else
            return mCursorBlinkingEnabled ? mCursorBlinkState : true;
    }

    /** Extra cursors share the main cursor blink phase, but not its DEC visibility flag. */
    public boolean shouldExtraCursorsBeVisible() {
        return mCursorBlinkingEnabled ? mCursorBlinkState : true;
    }

    public void setCursorBlinkingEnabled(boolean cursorBlinkingEnabled) {
        this.mCursorBlinkingEnabled = cursorBlinkingEnabled;
    }

    public void setCursorBlinkState(boolean cursorBlinkState) {
        this.mCursorBlinkState = cursorBlinkState;
    }

    public boolean isKeypadApplicationMode() {
        return isDecsetInternalBitSet(DECSET_BIT_APPLICATION_KEYPAD);
    }

    public boolean isCursorKeysApplicationMode() {
        return isDecsetInternalBitSet(DECSET_BIT_APPLICATION_CURSOR_KEYS);
    }

    /**
     * If mouse events are being sent as escape codes to the terminal.
     */
    public boolean isMouseTrackingActive() {
        return isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) || isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT);
    }

    /**
     * If motion while a button is held should be reported, i.e. DECSET 1002 or 1003 is set.
     */
    public boolean isMouseTrackingMotionActive() {
        return isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT);
    }

    /**
     * Indicates if bold should be shown with bright colors.
     */
    public boolean isBoldWithBright() {
        return mBoldWithBright;
    }

    private void setDefaultTabStops() {
        for (int i = 0; i < mColumns; i++) mTabStop[i] = (i & 7) == 0 && i != 0;
    }

    /**
     * Accept bytes (typically from the pseudo-teletype) and process them.
     *
     * @param buffer a byte array containing the bytes to be processed
     * @param length the number of bytes in the array to process
     */
    public void append(byte[] buffer, int length) {
        for (int i = 0; i < length; i++) processByte(buffer[i]);
    }

    private void processByte(byte byteToProcess) {
        if (mUtf8ToFollow > 0) {
            if ((byteToProcess & 0b11000000) == 0b10000000) {
                // 10xxxxxx, a continuation byte.
                mUtf8InputBuffer[mUtf8Index++] = byteToProcess;
                if (--mUtf8ToFollow == 0) {
                    byte firstByteMask = (byte) (mUtf8Index == 2 ? 0b00011111 : (mUtf8Index == 3 ? 0b00001111 : 0b00000111));
                    int codePoint = (mUtf8InputBuffer[0] & firstByteMask);
                    for (int i = 1; i < mUtf8Index; i++) codePoint = ((codePoint << 6) | (mUtf8InputBuffer[i] & 0b00111111));
                    if (((codePoint <= 0b1111111) && mUtf8Index > 1) || (codePoint < 0b11111111111 && mUtf8Index > 2) || (codePoint < 0b1111111111111111 && mUtf8Index > 3)) {
                        // Overlong encoding.
                        codePoint = UNICODE_REPLACEMENT_CHAR;
                    }
                    mUtf8Index = mUtf8ToFollow = 0;
                    if (codePoint >= 0x80 && codePoint <= 0x9F) {
                        // Sequence decoded to a C1 control character which we ignore. They are
                        // not used nowadays and increases the risk of messing up the terminal state
                        // on binary input. XTerm does not allow them in utf-8:
                        // "It is not possible to use a C1 control obtained from decoding the
                        // UTF-8 text" - http://invisible-island.net/xterm/ctlseqs/ctlseqs.html
                    } else {
                        switch(Character.getType(codePoint)) {
                            case Character.UNASSIGNED:
                            case Character.SURROGATE:
                                codePoint = UNICODE_REPLACEMENT_CHAR;
                        }
                        processCodePoint(codePoint);
                    }
                }
            } else {
                // Not a UTF-8 continuation byte so replace the entire sequence up to now with the replacement char:
                mUtf8Index = mUtf8ToFollow = 0;
                emitCodePoint(UNICODE_REPLACEMENT_CHAR);
                // The Unicode Standard Version 6.2 – Core Specification
                // (http://www.unicode.org/versions/Unicode6.2.0/ch03.pdf):
                // "If the converter encounters an ill-formed UTF-8 code unit sequence which starts with a valid first
                // byte, but which does not continue with valid successor bytes (see Table 3-7), it must not consume the
                // successor bytes as part of the ill-formed subsequence
                // whenever those successor bytes themselves constitute part of a well-formed UTF-8 code unit
                // subsequence."
                processByte(byteToProcess);
            }
        } else {
            if ((byteToProcess & 0b10000000) == 0) {
                // The leading bit is not set so it is a 7-bit ASCII character.
                processCodePoint(byteToProcess);
                return;
            } else if ((byteToProcess & 0b11100000) == 0b11000000) {
                // 110xxxxx, a two-byte sequence.
                mUtf8ToFollow = 1;
            } else if ((byteToProcess & 0b11110000) == 0b11100000) {
                // 1110xxxx, a three-byte sequence.
                mUtf8ToFollow = 2;
            } else if ((byteToProcess & 0b11111000) == 0b11110000) {
                // 11110xxx, a four-byte sequence.
                mUtf8ToFollow = 3;
            } else {
                // Not a valid UTF-8 sequence start, signal invalid data:
                processCodePoint(UNICODE_REPLACEMENT_CHAR);
                return;
            }
            mUtf8InputBuffer[mUtf8Index++] = byteToProcess;
        }
    }

    public void processCodePoint(int b) {
        // CAN and SUB cancel every escape string, including APC. APC is otherwise handled before
        // the control-character switch because its payload may contain arbitrary control bytes.
        if ((b == 24 || b == 26) && mEscapeState != ESC_NONE) {
            finishSequence();
            emitCodePoint(127);
            return;
        }

        if (isCsiState(mEscapeState) && ++mCsiSequenceLength > MAX_CSI_SEQUENCE_LENGTH) {
            finishSequence();
            return;
        }

        // The Application Program-Control (APC) string might be arbitrary non-printable characters, so handle that early.
        if (mEscapeState == ESC_APC) {
            doApc(b);
            return;
        } else if (mEscapeState == ESC_APC_ESCAPE) {
            doApcEscape(b);
            return;
        }

        switch (b) {
            case 0: // Null character (NUL, ^@). Do nothing.
                break;
            case // Bell (BEL, ^G, \a). If in an OSC sequence, BEL may terminate a string; otherwise signal bell.
            7:
                if (mEscapeState == ESC_OSC)
                    doOsc(b);
                else {
                    if (mEscapeState == ESC_APC) {
                        doApc(b);
                    }
                    mSession.onBell();
                }
                break;
            case // Backspace (BS, ^H).
            8:
                if (mLeftMargin == mCursorCol) {
                    // Jump to previous line if it was auto-wrapped.
                    int previousRow = mCursorRow - 1;
                    if (previousRow >= 0 && mScreen.getLineWrap(previousRow)) {
                        mScreen.clearLineWrap(previousRow);
                        setCursorRowCol(previousRow, mRightMargin - 1);
                    }
                } else {
                    setCursorCol(mCursorCol - 1);
                }
                break;
            case // Horizontal tab (HT, \t) - move to next tab stop, but not past edge of screen
            9:
                // XXX: Should perhaps use color if writing to new cells. Try with
                //       printf "\033[41m\tXX\033[0m\n"
                // The OSX Terminal.app colors the spaces from the tab red, but xterm does not.
                // Note that Terminal.app only colors on new cells, in e.g.
                //       printf "\033[41m\t\r\033[42m\tXX\033[0m\n"
                // the first cells are created with a red background, but when tabbing over
                // them again with a green background they are not overwritten.
                mCursorCol = nextTabStop(1);
                break;
            // Line feed (LF, \n).
            case 10:
            // Vertical tab (VT, \v).
            case 11:
            case // Form feed (FF, \f).
            12:
                if ((mEscapeState != ESC_P || !ESC_P_sixel) && !mIgnoreCrLfForOsc) {
                    // Ignore CR/LF inside sixels or iterm2 data
                    doLinefeed();
                }
                break;
            case // Carriage return (CR, \r).
            13:
                if ((mEscapeState != ESC_P || !ESC_P_sixel) && !mIgnoreCrLfForOsc) {
                    // Ignore CR/LF inside sixels or iterm2 data
                    setCursorCol(mLeftMargin);
                }
                break;
            case // Shift Out (Ctrl-N, SO) → Switch to Alternate Character Set. This invokes the G1 character set.
            14:
                mUseLineDrawingUsesG0 = false;
                break;
            case // Shift In (Ctrl-O, SI) → Switch to Standard Character Set. This invokes the G0 character set.
            15:
                mUseLineDrawingUsesG0 = true;
                break;
            // CAN.
            case 24:
            case // SUB.
            26:
                break;
            case // ESC
            27:
                // Starts an escape sequence unless we're parsing a string
                if (mEscapeState == ESC_P) {
                    // XXX: Ignore escape when reading device control sequence, since it may be part of string terminator.
                    ESC_P_escape = true;
                    return;
                } else if (mEscapeState != ESC_OSC) {
                    if (mEscapeState != ESC_APC) {
                        startEscapeSequence();
                    } else {
                        doApc(b);
                    }
                } else {
                    doOsc(b);
                }
                break;
            default:
                mContinueSequence = false;
                switch(mEscapeState) {
                    case ESC_NONE:
                        if (b >= 32)
                            emitCodePoint(b);
                        break;
                    case ESC:
                        doEsc(b);
                        break;
                    case ESC_POUND:
                        doEscPound(b);
                        break;
                    case // Designate G0 Character Set (ISO 2022, VT100).
                    ESC_SELECT_LEFT_PAREN:
                        mUseLineDrawingG0 = (b == '0');
                        break;
                    case // Designate G1 Character Set (ISO 2022, VT100).
                    ESC_SELECT_RIGHT_PAREN:
                        mUseLineDrawingG1 = (b == '0');
                        break;
                    case ESC_CSI:
                        doCsi(b);
                        break;
                    case ESC_CSI_UNSUPPORTED_PARAMETER_BYTE:
                    case ESC_CSI_UNSUPPORTED_INTERMEDIATE_BYTE:
                        doCsiUnsupportedParameterOrIntermediateByte(b);
                        break;
                    case ESC_CSI_EXCLAMATION:
                        if (b == 'p') {
                            // Soft terminal reset (DECSTR, http://vt100.net/docs/vt510-rm/DECSTR).
                            reset();
                        } else {
                            unknownSequence(b);
                        }
                        break;
                    case ESC_CSI_QUESTIONMARK:
                        doCsiQuestionMark(b);
                        break;
                    case ESC_CSI_BIGGERTHAN:
                        doCsiBiggerThan(b);
                        break;
                    case ESC_CSI_BIGGERTHAN_ARGS_SPACE:
                        if (b == 'q') processExtraCursorSequence();
                        else unknownSequence(b);
                        break;
                    case ESC_CSI_EQUAL:
                        doCsiEqual(b);
                        break;
                    case ESC_CSI_LESSTHAN:
                        doCsiLessThan(b);
                        break;
                    case ESC_CSI_DOLLAR:
                        boolean originMode = isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE);
                        int effectiveTopMargin = originMode ? mTopMargin : 0;
                        int effectiveBottomMargin = originMode ? mBottomMargin : mRows;
                        int effectiveLeftMargin = originMode ? mLeftMargin : 0;
                        int effectiveRightMargin = originMode ? mRightMargin : mColumns;
                        switch(b) {
                            case // ${CSI}${SRC_TOP}${SRC_LEFT}${SRC_BOTTOM}${SRC_RIGHT}${SRC_PAGE}${DST_TOP}${DST_LEFT}${DST_PAGE}$v"
                            'v':
                                // Copy rectangular area (DECCRA - http://vt100.net/docs/vt510-rm/DECCRA):
                                // "If Pbs is greater than Pts, or Pls is greater than Prs, the terminal ignores DECCRA.
                                // The coordinates of the rectangular area are affected by the setting of origin mode (DECOM).
                                // DECCRA is not affected by the page margins.
                                // The copied text takes on the line attributes of the destination area.
                                // If the value of Pt, Pl, Pb, or Pr exceeds the width or height of the active page, then the value
                                // is treated as the width or height of that page.
                                // If the destination area is partially off the page, then DECCRA clips the off-page data.
                                // DECCRA does not change the active cursor position."
                                int topSource = Math.min(getArg(0, 1, true) - 1 + effectiveTopMargin, mRows);
                                int leftSource = Math.min(getArg(1, 1, true) - 1 + effectiveLeftMargin, mColumns);
                                // Inclusive, so do not subtract one:
                                int bottomSource = Math.min(Math.max(getArg(2, mRows, true) + effectiveTopMargin, topSource), mRows);
                                int rightSource = Math.min(Math.max(getArg(3, mColumns, true) + effectiveLeftMargin, leftSource), mColumns);
                                // int sourcePage = getArg(4, 1, true);
                                int destionationTop = Math.min(getArg(5, 1, true) - 1 + effectiveTopMargin, mRows);
                                int destinationLeft = Math.min(getArg(6, 1, true) - 1 + effectiveLeftMargin, mColumns);
                                // int destinationPage = getArg(7, 1, true);
                                int heightToCopy = Math.min(mRows - destionationTop, bottomSource - topSource);
                                int widthToCopy = Math.min(mColumns - destinationLeft, rightSource - leftSource);
                                mScreen.blockCopy(leftSource, topSource, widthToCopy, heightToCopy, destinationLeft, destionationTop);
                                break;
                            // ${CSI}${TOP}${LEFT}${BOTTOM}${RIGHT}${"
                            case '{':
                            // Selective erase rectangular area (DECSERA - http://www.vt100.net/docs/vt510-rm/DECSERA).
                            // ${CSI}${CHAR};${TOP}${LEFT}${BOTTOM}${RIGHT}$x"
                            case 'x':
                            // Fill rectangular area (DECFRA - http://www.vt100.net/docs/vt510-rm/DECFRA).
                            case // ${CSI}$${TOP}${LEFT}${BOTTOM}${RIGHT}$z"
                            'z':
                                // Erase rectangular area (DECERA - http://www.vt100.net/docs/vt510-rm/DECERA).
                                boolean erase = b != 'x';
                                boolean selective = b == '{';
                                // Only DECSERA keeps visual attributes, DECERA does not:
                                boolean keepVisualAttributes = erase && selective;
                                int argIndex = 0;
                                int fillChar = erase ? ' ' : getArg(argIndex++, -1, true);
                                // "Pch can be any value from 32 to 126 or from 160 to 255. If Pch is not in this range, then the
                                // terminal ignores the DECFRA command":
                                if ((fillChar >= 32 && fillChar <= 126) || (fillChar >= 160 && fillChar <= 255)) {
                                    // "If the value of Pt, Pl, Pb, or Pr exceeds the width or height of the active page, the value
                                    // is treated as the width or height of that page."
                                    int top = Math.min(getArg(argIndex++, 1, true) + effectiveTopMargin, effectiveBottomMargin + 1);
                                    int left = Math.min(getArg(argIndex++, 1, true) + effectiveLeftMargin, effectiveRightMargin + 1);
                                    int bottom = Math.min(getArg(argIndex++, mRows, true) + effectiveTopMargin, effectiveBottomMargin);
                                    int right = Math.min(getArg(argIndex, mColumns, true) + effectiveLeftMargin, effectiveRightMargin);
                                    long style = getStyle();
                                    for (int row = top - 1; row < bottom; row++) for (int col = left - 1; col < right; col++) if (!selective || (TextStyle.decodeEffect(mScreen.getStyleAt(row, col)) & TextStyle.CHARACTER_ATTRIBUTE_PROTECTED) == 0)
                                        mScreen.setChar(col, row, fillChar, keepVisualAttributes ? mScreen.getStyleAt(row, col) : style);
                                }
                                break;
                            // "${CSI}${TOP}${LEFT}${BOTTOM}${RIGHT}${ATTRIBUTES}$r"
                            case 'r':
                            // Change attributes in rectangular area (DECCARA - http://vt100.net/docs/vt510-rm/DECCARA).
                            case // "${CSI}${TOP}${LEFT}${BOTTOM}${RIGHT}${ATTRIBUTES}$t"
                            't':
                                // Reverse attributes in rectangular area (DECRARA - http://www.vt100.net/docs/vt510-rm/DECRARA).
                                boolean reverse = b == 't';
                                // FIXME: "coordinates of the rectangular area are affected by the setting of origin mode (DECOM)".
                                int top = Math.min(getArg(0, 1, true) - 1, effectiveBottomMargin) + effectiveTopMargin;
                                int left = Math.min(getArg(1, 1, true) - 1, effectiveRightMargin) + effectiveLeftMargin;
                                int bottom = Math.min(getArg(2, mRows, true) + 1, effectiveBottomMargin - 1) + effectiveTopMargin;
                                int right = Math.min(getArg(3, mColumns, true) + 1, effectiveRightMargin - 1) + effectiveLeftMargin;
                                if (mArgIndex >= 4) {
                                    if (mArgIndex >= mArgs.length)
                                        mArgIndex = mArgs.length - 1;
                                    for (int i = 4; i <= mArgIndex; i++) {
                                        int bits = 0;
                                        // True if setting, false if clearing.
                                        boolean setOrClear = true;
                                        switch(getArg(i, 0, false)) {
                                            case // Attributes off (no bold, no underline, no blink, positive image).
                                            0:
                                                bits = (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE | TextStyle.CHARACTER_ATTRIBUTE_BLINK | TextStyle.CHARACTER_ATTRIBUTE_INVERSE);
                                                if (!reverse)
                                                    setOrClear = false;
                                                break;
                                            case // Bold.
                                            1:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_BOLD;
                                                break;
                                            case // Underline.
                                            4:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
                                                break;
                                            case // Blink.
                                            5:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_BLINK;
                                                break;
                                            case // Negative image.
                                            7:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
                                                break;
                                            case // No bold.
                                            22:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_BOLD;
                                                setOrClear = false;
                                                break;
                                            case // No underline.
                                            24:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
                                                setOrClear = false;
                                                break;
                                            case // No blink.
                                            25:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_BLINK;
                                                setOrClear = false;
                                                break;
                                            case // Positive image.
                                            27:
                                                bits = TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
                                                setOrClear = false;
                                                break;
                                        }
                                        if (reverse && !setOrClear) {
                                            // Reverse attributes in rectangular area ignores non-(1,4,5,7) bits.
                                        } else {
                                            mScreen.setOrClearEffect(bits, setOrClear, reverse, isDecsetInternalBitSet(DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE), effectiveLeftMargin, effectiveRightMargin, top, left, bottom, right);
                                        }
                                    }
                                } else {
                                    // Do nothing.
                                }
                                break;
                            default:
                                unknownSequence(b);
                        }
                        break;
                    case ESC_CSI_DOUBLE_QUOTE:
                        if (b == 'q') {
                            // http://www.vt100.net/docs/vt510-rm/DECSCA
                            int arg = getArg0(0);
                            if (arg == 0 || arg == 2) {
                                // DECSED and DECSEL can erase characters.
                                mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_PROTECTED;
                            } else if (arg == 1) {
                                // DECSED and DECSEL cannot erase characters.
                                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_PROTECTED;
                            } else {
                                unknownSequence(b);
                            }
                        } else {
                            unknownSequence(b);
                        }
                        break;
                    case ESC_CSI_SINGLE_QUOTE:
                        if (b == '}') {
                            // Insert Ps Column(s) (default = 1) (DECIC), VT420 and up.
                            int columnsAfterCursor = mRightMargin - mCursorCol;
                            int columnsToInsert = Math.min(getArg0(1), columnsAfterCursor);
                            int columnsToMove = columnsAfterCursor - columnsToInsert;
                            mScreen.blockCopy(mCursorCol, 0, columnsToMove, mRows, mCursorCol + columnsToInsert, 0);
                            blockClear(mCursorCol, 0, columnsToInsert, mRows);
                        } else if (b == '~') {
                            // Delete Ps Column(s) (default = 1) (DECDC), VT420 and up.
                            int columnsAfterCursor = mRightMargin - mCursorCol;
                            int columnsToDelete = Math.min(getArg0(1), columnsAfterCursor);
                            int columnsToMove = columnsAfterCursor - columnsToDelete;
                            mScreen.blockCopy(mCursorCol + columnsToDelete, 0, columnsToMove, mRows, mCursorCol, 0);
                        } else {
                            unknownSequence(b);
                        }
                        break;
                    case ESC_PERCENT:
                        break;
                    case ESC_APC:
                        doApc(b);
                        break;
                    case ESC_APC_ESCAPE:
                        doApcEscape(b);
                        break;
                    case ESC_OSC:
                        doOsc(b);
                        break;
                    case ESC_OSC_ESC:
                        doOscEsc(b);
                        break;
                    case ESC_P:
                        doDeviceControl(b);
                        break;
                    case ESC_CSI_QUESTIONMARK_ARG_DOLLAR:
                        if (b == 'p') {
                            // Request DEC private mode (DECRQM).
                            int mode = getArg0(0);
                            int value;
                            if (mode == 47 || mode == 1047 || mode == 1049) {
                                // This state is carried by mScreen pointer.
                                value = (mScreen == mAltBuffer) ? 1 : 2;
                            } else {
                                int internalBit = mapDecSetBitToInternalBit(mode);
                                if (internalBit != -1) {
                                    // 1=set, 2=reset.
                                    value = isDecsetInternalBitSet(internalBit) ? 1 : 2;
                                } else {
                                    Logger.logError(mClient, LOG_TAG, "Got DECRQM for unrecognized private DEC mode=" + mode);
                                    // 0=not recognized, 3=permanently set, 4=permanently reset
                                    value = 0;
                                }
                            }
                            mSession.write(String.format(Locale.US, "\033[?%d;%d$y", mode, value));
                        } else {
                            unknownSequence(b);
                        }
                        break;
                    case ESC_CSI_ARGS_SPACE:
                        int arg = getArg0(0);
                        switch(b) {
                            case // "${CSI}${STYLE} q" - set cursor style (http://www.vt100.net/docs/vt510-rm/DECSCUSR).
                            'q':
                                switch(arg) {
                                    // Blinking block.
                                    case 0:
                                    // Blinking block.
                                    case 1:
                                    case // Steady block.
                                    2:
                                        mCursorStyle = TERMINAL_CURSOR_STYLE_BLOCK;
                                        setCursorBlinkingEnabled(arg != 2);
                                        break;
                                    // Blinking underline.
                                    case 3:
                                    case // Steady underline.
                                    4:
                                        mCursorStyle = TERMINAL_CURSOR_STYLE_UNDERLINE;
                                        setCursorBlinkingEnabled(arg != 4);
                                        break;
                                    // Blinking bar (xterm addition).
                                    case 5:
                                    case // Steady bar (xterm addition).
                                    6:
                                        mCursorStyle = TERMINAL_CURSOR_STYLE_BAR;
                                        setCursorBlinkingEnabled(arg != 6);
                                        break;
                                }
                                break;
                            case 't':
                            case 'u':
                                // Set margin-bell volume - ignore.
                                break;
                            default:
                                unknownSequence(b);
                        }
                        break;
                    case ESC_CSI_ARGS_ASTERIX:
                        int attributeChangeExtent = getArg0(0);
                        if (b == 'x' && (attributeChangeExtent >= 0 && attributeChangeExtent <= 2)) {
                            // Select attribute change extent (DECSACE - http://www.vt100.net/docs/vt510-rm/DECSACE).
                            setDecsetinternalBit(DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE, attributeChangeExtent == 2);
                        } else {
                            unknownSequence(b);
                        }
                        break;
                    default:
                        unknownSequence(b);
                        break;
                }
                if (!mContinueSequence)
                    mEscapeState = ESC_NONE;
                break;
        }
    }

    /**
     * When in {@link #ESC_P} ("device control") sequence.
     */
    private void doDeviceControl(int b) {
        boolean stringTerminator = ESC_P_escape && b == '\\';
        if (!stringTerminator && ++mDcsSequenceLength > mDcsSequenceMaxLength) {
            finishSequence();
            return;
        }
        boolean firstSixel = false;
        if (!ESC_P_sixel && (b == '$' || b == '-' || b == '#')) {
            //Check if sixel sequence that needs breaking
            String dcs = mOSCOrDeviceControlArgs.toString();
            if (dcs.matches("[0-9;]*q.*")) {
                firstSixel = true;
            }
        }
        if (firstSixel || (ESC_P_escape && b == '\\') || (ESC_P_sixel && (b == '$' || b == '-' || b == '#'))) // ESC \ terminates OSC
        // Sixel sequences may be very long. '$' and '!' are natural for breaking the sequence.
        {
            String dcs = mOSCOrDeviceControlArgs.toString();
            // DCS $ q P t ST. Request Status String (DECRQSS)
            if (dcs.startsWith("$q")) {
                if (dcs.equals("$q\"p")) {
                    // DECSCL, conformance level, http://www.vt100.net/docs/vt510-rm/DECSCL:
                    String csiString = "64;1\"p";
                    mSession.write("\033P1$r" + csiString + "\033\\");
                } else {
                    finishSequenceAndLogError("Unrecognized DECRQSS string: '" + dcs + "'");
                }
            } else if (dcs.startsWith("+q")) {
                // Request Termcap/Terminfo String. The string following the "q" is a list of names encoded in
                // hexadecimal (2 digits per character) separated by ; which correspond to termcap or terminfo key
                // names.
                // Two special features are also recognized, which are not key names: Co for termcap colors (or colors
                // for terminfo colors), and TN for termcap name (or name for terminfo name).
                // xterm responds with DCS 1 + r P t ST for valid requests, adding to P t an = , and the value of the
                // corresponding string that xterm would send, or DCS 0 + r P t ST for invalid requests. The strings are
                // encoded in hexadecimal (2 digits per character).
                // Example:
                // :kr=\EOC: ks=\E[?1h\E=: ku=\EOA: le=^H:mb=\E[5m:md=\E[1m:\
                // where
                // kd=down-arrow key
                // kl=left-arrow key
                // kr=right-arrow key
                // ku=up-arrow key
                // #2=key_shome, "shifted home"
                // #4=key_sleft, "shift arrow left"
                // %i=key_sright, "shift arrow right"
                // *7=key_send, "shifted end"
                // k1=F1 function key
                // Example: Request for ku is "ESC P + q 6 b 7 5 ESC \", where 6b7d=ku in hexadecimal.
                // Xterm response in normal cursor mode:
                // "<27> P 1 + r 6 b 7 5 = 1 B 5 B 4 1" where 0x1B 0x5B 0x41 = 27 91 65 = ESC [ A
                // Xterm response in application cursor mode:
                // "<27> P 1 + r 6 b 7 5 = 1 B 5 B 4 1" where 0x1B 0x4F 0x41 = 27 91 65 = ESC 0 A
                // #4 is "shift arrow left":
                // *** Device Control (DCS) for '#4'- 'ESC P + q 23 34 ESC \'
                // Response: <27> P 1 + r 2 3 3 4 = 1 B 5 B 3 1 3 B 3 2 4 4 <27> \
                // where 0x1B 0x5B 0x31 0x3B 0x32 0x44 = ESC [ 1 ; 2 D
                // which we find in: TermKeyListener.java: KEY_MAP.put(KEYMOD_SHIFT | KEYCODE_DPAD_LEFT, "\033[1;2D");
                // See http://h30097.www3.hp.com/docs/base_doc/DOCUMENTATION/V40G_HTML/MAN/MAN4/0178____.HTM for what to
                // respond, as well as http://www.freebsd.org/cgi/man.cgi?query=termcap&sektion=5#CAPABILITIES for
                // the meaning of e.g. "ku", "kd", "kr", "kl"
                for (String part : dcs.substring(2).split(";")) {
                    if (part.length() % 2 == 0) {
                        StringBuilder transBuffer = new StringBuilder();
                        char c;
                        for (int i = 0; i < part.length(); i += 2) {
                            try {
                                c = (char) Long.decode("0x" + part.charAt(i) + "" + part.charAt(i + 1)).longValue();
                            } catch (NumberFormatException e) {
                                Logger.logStackTraceWithMessage(mClient, LOG_TAG, "Invalid device termcap/terminfo encoded name \"" + part + "\"", e);
                                continue;
                            }
                            transBuffer.append(c);
                        }
                        String trans = transBuffer.toString();
                        String responseValue;
                        switch(trans) {
                            case "Co":
                            case "colors":
                                // Number of colors.
                                responseValue = "256";
                                break;
                            case "TN":
                            case "name":
                                responseValue = "xterm";
                                break;
                            default:
                                responseValue = KeyHandler.getCodeFromTermcap(trans, isDecsetInternalBitSet(DECSET_BIT_APPLICATION_CURSOR_KEYS), isDecsetInternalBitSet(DECSET_BIT_APPLICATION_KEYPAD));
                                break;
                        }
                        if (responseValue == null) {
                            switch(trans) {
                                // Help key - ignore
                                case "%1":
                                case // Undo key - ignore.
                                "&8":
                                    break;
                                default:
                                    Logger.logWarn(mClient, LOG_TAG, "Unhandled termcap/terminfo name: '" + trans + "'");
                            }
                            // Respond with invalid request:
                            mSession.write("\033P0+r" + part + "\033\\");
                        } else {
                            StringBuilder hexEncoded = new StringBuilder();
                            for (int j = 0; j < responseValue.length(); j++) {
                                hexEncoded.append(String.format("%02X", (int) responseValue.charAt(j)));
                            }
                            mSession.write("\033P1+r" + part + "=" + hexEncoded + "\033\\");
                        }
                    } else {
                        Logger.logError(mClient, LOG_TAG, "Invalid device termcap/terminfo name of odd length: " + part);
                    }
                }
            } else if (ESC_P_sixel || dcs.matches("[0-9;]*q.*")) {
                int pos = 0;
                if (!ESC_P_sixel) {
                    ESC_P_sixel = true;
                    mDcsSequenceMaxLength = MAX_IMAGE_SEQUENCE_LENGTH;
                    mScreen.sixelStart(100, 100);
                    while (dcs.codePointAt(pos) != 'q') {
                        pos++;
                    }
                    pos++;
                }
                if (b == '$' || b == '-') {
                    // Add to string
                    dcs = dcs + (char) b;
                }
                int rep = 1;
                while (pos < dcs.length()) {
                    if (dcs.codePointAt(pos) == '"') {
                        pos++;
                        int[] args = { 0, 0, 0, 0 };
                        int arg = 0;
                        while (pos < dcs.length() && ((dcs.codePointAt(pos) >= '0' && dcs.codePointAt(pos) <= '9') || dcs.codePointAt(pos) == ';')) {
                            if (dcs.codePointAt(pos) >= '0' && dcs.codePointAt(pos) <= '9') {
                                args[arg] = args[arg] * 10 + dcs.codePointAt(pos) - '0';
                            } else {
                                arg++;
                                if (arg > 3) {
                                    break;
                                }
                            }
                            pos++;
                        }
                        if (arg == 3 && args[2] > 0 && args[3] > 0) {
                            mScreen.sixelResize(args[2], args[3]);
                        }
                        if (pos == dcs.length()) {
                            break;
                        }
                    } else if (dcs.codePointAt(pos) == '#') {
                        int col = 0;
                        pos++;
                        while (pos < dcs.length() && dcs.codePointAt(pos) >= '0' && dcs.codePointAt(pos) <= '9') {
                            col = col * 10 + dcs.codePointAt(pos++) - '0';
                        }
                        if (pos == dcs.length() || dcs.codePointAt(pos) != ';') {
                            mScreen.sixelSetColor(col);
                        } else {
                            pos++;
                            int[] args = { 0, 0, 0, 0 };
                            int arg = 0;
                            while (pos < dcs.length() && ((dcs.codePointAt(pos) >= '0' && dcs.codePointAt(pos) <= '9') || dcs.codePointAt(pos) == ';')) {
                                if (dcs.codePointAt(pos) >= '0' && dcs.codePointAt(pos) <= '9') {
                                    args[arg] = args[arg] * 10 + dcs.codePointAt(pos) - '0';
                                } else {
                                    arg++;
                                    if (arg > 3) {
                                        break;
                                    }
                                }
                                pos++;
                            }
                            if (args[0] == 2) {
                                mScreen.sixelSetRGBColor(col, args[1], args[2], args[3]);
                            }
                        }
                    } else if (dcs.codePointAt(pos) == '!') {
                        rep = 0;
                        pos++;
                        while (pos < dcs.length() && dcs.codePointAt(pos) >= '0' && dcs.codePointAt(pos) <= '9') {
                            rep = rep * 10 + dcs.codePointAt(pos++) - '0';
                        }
                        if (rep > TerminalSixel.SIXEL__MAX_REPEAT) {
                            Logger.logError(mClient, LOG_TAG,
                                "The sixel repeat command Pn value " + rep + " is greater than max repeat value " +
                                    TerminalSixel.SIXEL__MAX_REPEAT);
                            mScreen.sixelIgnore();
                            rep = 1;
                            break;
                        }
                    } else if (dcs.codePointAt(pos) == '$' || dcs.codePointAt(pos) == '-' || (dcs.codePointAt(pos) >= '?' && dcs.codePointAt(pos) <= '~')) {
                        mScreen.sixelChar(dcs.codePointAt(pos++), rep);
                        rep = 1;
                    } else {
                        pos++;
                    }
                }
                if (b == '\\') {
                    ESC_P_sixel = false;
                    int n = mScreen.sixelEnd(mCursorRow, mCursorCol, cellW, cellH);
                    for (; n > 0; n--) {
                        doLinefeed();
                    }
                } else {
                    mOSCOrDeviceControlArgs.setLength(0);
                    if (b == '#') {
                        mOSCOrDeviceControlArgs.appendCodePoint(b);
                    }
                    // Do not finish sequence
                    continueSequence(mEscapeState);
                    return;
                }
            } else {
                if (LOG_ESCAPE_SEQUENCES)
                    Logger.logError(mClient, LOG_TAG, "Unrecognized device control string: " + dcs);
            }
            finishSequence();
        } else {
            ESC_P_escape = false;
            if (!appendStringSequenceCodePoint(b)) {
                finishSequence();
            } else {
                continueSequence(mEscapeState);
            }
        }
    }

    /**
     * When in {@link #ESC_APC} (APC, Application Program Command) sequence.
     */
    private void doApc(int b) {
        if (b == 27) {
            continueSequence(ESC_APC_ESCAPE);
        } else if (++mApcSequenceLength > MAX_STRING_SEQUENCE_LENGTH) {
            finishSequence();
        } else {
            mOSCOrDeviceControlArgs.appendCodePoint(b);
        }
    }

    /**
     * When in {@link #ESC_APC} (APC, Application Program Command) sequence.
     */
    private void doApcEscape(int b) {
        if (b == '\\') {
            // A String Terminator (ST), ending the APC escape sequence.
            mKittyGraphics.accept(mOSCOrDeviceControlArgs.toString());
            finishSequence();
        } else if (mApcSequenceLength > MAX_STRING_SEQUENCE_LENGTH - 2) {
            // ESC followed by anything other than '\\' is two payload code points.
            finishSequence();
        } else {
            // The Escape character was not the start of a String Terminator (ST),
            // but instead just data inside of the APC escape sequence.
            mApcSequenceLength += 2;
            mOSCOrDeviceControlArgs.append('\033').appendCodePoint(b);
            continueSequence(ESC_APC);
        }
    }

    private int nextTabStop(int numTabs) {
        for (int i = mCursorCol + 1; i < mColumns; i++) if (mTabStop[i] && --numTabs == 0)
            return Math.min(i, mRightMargin);
        return mRightMargin - 1;
    }

    /**
     * Process byte while in the {@link #ESC_CSI_UNSUPPORTED_PARAMETER_BYTE} or
     * {@link #ESC_CSI_UNSUPPORTED_INTERMEDIATE_BYTE} escape state.
     *
     * Parse unsupported parameter, intermediate and final bytes but ignore them.
     *
     * > For Control Sequence Introducer, ... the ESC [ is followed by
     * > - any number (including none) of "parameter bytes" in the range 0x30–0x3F (ASCII 0–9:;<=>?),
     * > - then by any number of "intermediate bytes" in the range 0x20–0x2F (ASCII space and !"#$%&'()*+,-./),
     * > - then finally by a single "final byte" in the range 0x40–0x7E (ASCII @A–Z[\]^_`a–z{|}~).
     *
     * - https://en.wikipedia.org/wiki/ANSI_escape_code#Control_Sequence_Introducer_commands
     * - https://invisible-island.net/xterm/ecma-48-parameter-format.html#section5.4
     */
    private void doCsiUnsupportedParameterOrIntermediateByte(int b) {
        if (mEscapeState == ESC_CSI_UNSUPPORTED_PARAMETER_BYTE && b >= 0x30 && b <= 0x3F) {
            // Supported `0–9:;>?` or unsupported `<=` parameter byte after an
            // initial unsupported parameter byte in `doCsi()`, or a sequential parameter byte.
            continueSequence(ESC_CSI_UNSUPPORTED_PARAMETER_BYTE);
        } else if (b >= 0x20 && b <= 0x2F) {
            // Optional intermediate byte `!"#$%&'()*+,-./` after parameter or intermediate byte.
            continueSequence(ESC_CSI_UNSUPPORTED_INTERMEDIATE_BYTE);
        } else if (b >= 0x40 && b <= 0x7E) {
            // Final byte `@A–Z[\]^_`a–z{|}~` after parameter or intermediate byte.
            // Calling `unknownSequence()` would log an error with only a final byte, so ignore it for now.
            finishSequence();
        } else {
            unknownSequence(b);
        }
    }

    /** Process byte while in the {@link #ESC_CSI_QUESTIONMARK} escape state. */
    private void doCsiQuestionMark(int b) {
        switch(b) {
            // Selective erase in display (DECSED) - http://www.vt100.net/docs/vt510-rm/DECSED.
            case 'J':
            case // Selective erase in line (DECSEL) - http://vt100.net/docs/vt510-rm/DECSEL.
            'K':
                mAboutToAutoWrap = false;
                int fillChar = ' ';
                int startCol = -1;
                int startRow = -1;
                int endCol = -1;
                int endRow = -1;
                boolean justRow = (b == 'K');
                switch(getArg0(0)) {
                    case // Erase from the active position to the end, inclusive (default).
                    0:
                        startCol = mCursorCol;
                        startRow = mCursorRow;
                        endCol = mColumns;
                        endRow = justRow ? (mCursorRow + 1) : mRows;
                        break;
                    case // Erase from start to the active position, inclusive.
                    1:
                        startCol = 0;
                        startRow = justRow ? mCursorRow : 0;
                        endCol = mCursorCol + 1;
                        endRow = mCursorRow + 1;
                        break;
                    case // Erase all of the display/line.
                    2:
                        startCol = 0;
                        startRow = justRow ? mCursorRow : 0;
                        endCol = mColumns;
                        endRow = justRow ? (mCursorRow + 1) : mRows;
                        break;
                    default:
                        unknownSequence(b);
                        break;
                }
                long style = getStyle();
                for (int row = startRow; row < endRow; row++) {
                    for (int col = startCol; col < endCol; col++) {
                        if ((TextStyle.decodeEffect(mScreen.getStyleAt(row, col)) & TextStyle.CHARACTER_ATTRIBUTE_PROTECTED) == 0)
                            mScreen.setChar(col, row, fillChar, style);
                    }
                }
                break;
            case 'h':
            case 'l':
                if (mArgIndex >= mArgs.length)
                    mArgIndex = mArgs.length - 1;
                for (int i = 0; i <= mArgIndex; i++) doDecSetOrReset(b == 'h', mArgs[i]);
                break;
            case // Device Status Report (DSR, DEC-specific).
            'n':
                switch(getArg0(-1)) {
                    case 6:
                        // Extended Cursor Position (DECXCPR - http://www.vt100.net/docs/vt510-rm/DECXCPR). Page=1.
                        mSession.write(String.format(Locale.US, "\033[?%d;%d;1R", mCursorRow + 1, mCursorCol + 1));
                        break;
                    default:
                        finishSequence();
                        return;
                }
                break;
            case // XTSMGRAPHICS - "${CSI}?${Pi};${Pa};${Pv}S" set or request graphics attributes.
            'S': {
                // Pi: 1=color registers, 2=sixel geometry. Pa: 1=read, 2=reset, 3=set, 4=read maximum.
                // Reply is "CSI ? Pi ; Ps ; Pv S" with Ps 0=success, 1=bad Pi, 2=bad Pa, 3=failure.
                // Sixel clients (notcurses, libsixel) read these before deciding palette size and image
                // geometry, so a terminal advertising sixel in DA1 should answer rather than swallow it.
                int item = getArg0(0);
                int action = getArg1(0);
                if (item == 1) {
                    // The sixel implementation has a fixed 256 color registers, so read, reset,
                    // set and read-maximum all report that value.
                    mSession.write(action >= 1 && action <= 4 ? "\033[?1;0;256S" : "\033[?1;2;0S");
                } else if (item == 2) {
                    if (action == 1 || action == 4) {
                        mSession.write(String.format(Locale.US, "\033[?2;0;%d;%dS", mColumns * mCellWidthPixels, mRows * mCellHeightPixels));
                    } else {
                        // Geometry follows the screen size and cannot be reset or set.
                        mSession.write("\033[?2;2;0S");
                    }
                } else {
                    mSession.write(String.format(Locale.US, "\033[?%d;1;0S", item));
                }
                break;
            }
            case 'r':
            case 's':
                if (mArgIndex >= mArgs.length)
                    mArgIndex = mArgs.length - 1;
                for (int i = 0; i <= mArgIndex; i++) {
                    int externalBit = mArgs[i];
                    int internalBit = mapDecSetBitToInternalBit(externalBit);
                    if (internalBit == -1) {
                        Logger.logWarn(mClient, LOG_TAG, "Ignoring request to save/recall decset bit=" + externalBit);
                    } else {
                        if (b == 's') {
                            mSavedDecSetFlags |= internalBit;
                        } else {
                            doDecSetOrReset((mSavedDecSetFlags & internalBit) != 0, externalBit);
                        }
                    }
                }
                break;
            case 'u':
                // "CSI ? u" - the kitty keyboard protocol query. An application detects support by
                // sending this followed by a primary device attributes request: an answer to the
                // second with none to the first means the protocol is not implemented.
                mSession.write("\033[?" + getKeyboardFlags() + "u");
                break;
            case '$':
                continueSequence(ESC_CSI_QUESTIONMARK_ARG_DOLLAR);
                return;
            default:
                parseArg(b);
        }
    }

    public void doDecSetOrReset(boolean setting, int externalBit) {
        int internalBit = mapDecSetBitToInternalBit(externalBit);
        if (internalBit != -1) {
            setDecsetinternalBit(internalBit, setting);
        }
        switch(externalBit) {
            case // Application Cursor Keys (DECCKM).
            1:
                break;
            case // Set: 132 column mode (. Reset: 80 column mode. ANSI name: DECCOLM.
            3:
                // We don't actually set/reset 132 cols, but we do want the side effects
                // (FIXME: Should only do this if the 95 DECSET bit (DECNCSM) is set, and if changing value?):
                // Sets the left, right, top and bottom scrolling margins to their default positions, which is important for
                // the "reset" utility to really reset the terminal:
                mLeftMargin = mTopMargin = 0;
                mBottomMargin = mRows;
                mRightMargin = mColumns;
                // "DECCOLM resets vertical split screen mode (DECLRMM) to unavailable":
                setDecsetinternalBit(DECSET_BIT_LEFTRIGHT_MARGIN_MODE, false);
                // "Erases all data in page memory":
                blockClear(0, 0, mColumns, mRows);
                setCursorRowCol(0, 0);
                break;
            case // DECSCLM-Scrolling Mode. Ignore.
            4:
                break;
            case // Reverse video. No action.
            5:
                break;
            case // Set: Origin Mode. Reset: Normal Cursor Mode. Ansi name: DECOM.
            6:
                if (setting)
                    setCursorPosition(0, 0);
                break;
            // Wrap-around bit, not specific action.
            case 7:
            // Auto-repeat Keys (DECARM). Do not implement.
            case 8:
            // X10 mouse reporting - outdated. Do not implement.
            case 9:
            // Control cursor blinking - ignore.
            case 12:
            case // Hide/show cursor - no action needed, renderer will check with shouldCursorBeVisible().
            25:
                if (mClient != null)
                    mClient.onTerminalCursorStateChange(setting);
                break;
            // Allow 80 => 132 Mode, ignore.
            case 40:
            // TODO: Reverse wrap-around. Implement???
            case 45:
            case // Application keypad (DECNKM).
            66:
                break;
            case // Left and right margin mode (DECLRMM).
            69:
                if (!setting) {
                    mLeftMargin = 0;
                    mRightMargin = mColumns;
                }
                break;
            case 1000:
            case 1001:
            case 1002:
            case 1003:
            case 1004:
            // UTF-8 mouse mode, ignore.
            case 1005:
            // SGR Mouse Mode
            case 1006:
            case 1015:
            case // Interpret "meta" key, sets eighth bit.
            1034:
                break;
            case // Set: Save cursor as in DECSC. Reset: Restore cursor as in DECRC.
            1048:
                if (setting)
                    saveCursor();
                else
                    restoreCursor();
                break;
            case 47:
            case 1047:
            case 1049:
                {
                    // Set: Save cursor as in DECSC and use Alternate Screen Buffer, clearing it first.
                    // Reset: Use Normal Screen Buffer and restore cursor as in DECRC.
                    TerminalBuffer newScreen = setting ? mAltBuffer : mMainBuffer;
                    if (newScreen != mScreen) {
                        mKittyGraphics.reset();
                        clearExtraCursors();
                        boolean resized = !(newScreen.mColumns == mColumns && newScreen.mScreenRows == mRows);
                        if (setting)
                            saveCursor();
                        mScreen = newScreen;
                        resetGraphemeTracking();
                        if (!setting) {
                            int col = mSavedStateMain.mSavedCursorCol;
                            int row = mSavedStateMain.mSavedCursorRow;
                            restoreCursor();
                            if (resized) {
                                // Restore cursor position _not_ clipped to current screen (let resizeScreen() handle that):
                                mCursorCol = col;
                                mCursorRow = row;
                            }
                        }
                        // Check if buffer size needs to be updated:
                        if (resized)
                            resizeScreen();
                        // Clear new screen if alt buffer:
                        if (newScreen == mAltBuffer)
                            newScreen.blockSet(0, 0, mColumns, mRows, ' ', getStyle());
                    }
                    break;
                }
            case 2004:
                // Bracketed paste mode - setting bit is enough.
                break;
            default:
                unknownParameter(externalBit);
                break;
        }
    }

    /**
     * Process byte while in the {@link #ESC_CSI_EQUAL} escape state, "ESC [ =".
     * <p>
     * Only the kitty keyboard protocol's "CSI = flags ; mode u" is implemented; other sequences with
     * this parameter byte are ignored as before.
     * </p>
     */
    private void doCsiEqual(int b) {
        switch(b) {
            case 'u':
                setKeyboardFlags(getArg0(0), getArg1(1));
                break;
            default:
                parseArg(b);
        }
    }

    /** Process byte while in the {@link #ESC_CSI_LESSTHAN} escape state, "ESC [ &lt;". */
    private void doCsiLessThan(int b) {
        switch(b) {
            case // "CSI < number u" - pop that many keyboard mode stack entries, one by default.
            'u':
                keyboardModes().pop(Math.max(1, getArg0(1)));
                break;
            default:
                parseArg(b);
        }
    }

    private void doCsiBiggerThan(int b) {
        switch(b) {
            case // "CSI > flags u" - push the current keyboard flags and apply new ones.
            'u':
                keyboardModes().push(getArg0(0) & KittyKeyEncoder.FLAGS_MASK);
                break;
            case // "${CSI}>0q" - XTVERSION, report terminal name and version.
            'q':
                if (getArg0(0) == 0) {
                    // Response format is DCS > | text ST, matching xterm/kitty/foot ("name(version)").
                    mSession.write("\033P>|" + sXtVersionName + "\033\\");
                } else {
                    unknownSequence(b);
                }
                break;
            case // "${CSI}>c" or "${CSI}>c". Secondary Device Attributes (DA2).
            'c':
                // Originally this was used for the terminal to respond with "identification code, firmware version level,
                // and hardware options" (http://vt100.net/docs/vt510-rm/DA2), with the first "41" meaning the VT420
                // terminal type. This is not used anymore, but the second version level field has been changed by xterm
                // to mean it's release number ("patch numbers" listed at http://invisible-island.net/xterm/xterm.log.html),
                // and some applications use it as a feature check:
                // * tmux used to have a "xterm won't reach version 500 for a while so set that as the upper limit" check,
                // and then check "xterm_version > 270" if rectangular area operations such as DECCRA could be used.
                // * vim checks xterm version number >140 for "Request termcap/terminfo string" functionality >276 for SGR
                // mouse report.
                // The third number is a keyboard identifier not used nowadays.
                mSession.write("\033[>41;320;0c");
                break;
            case 'm':
                // https://bugs.launchpad.net/gnome-terminal/+bug/96676/comments/25
                // Depending on the first number parameter, this can set one of the xterm resources
                // modifyKeyboard, modifyCursorKeys, modifyFunctionKeys and modifyOtherKeys.
                // http://invisible-island.net/xterm/manpage/xterm.html#RESOURCES
                // * modifyKeyboard (parameter=1):
                // Normally xterm makes a special case regarding modifiers (shift, control, etc.) to handle special keyboard
                // layouts (legacy and vt220). This is done to provide compatible keyboards for DEC VT220 and related
                // terminals that implement user-defined keys (UDK).
                // The bits of the resource value selectively enable modification of the given category when these keyboards
                // are selected. The default is "0":
                // (0) The legacy/vt220 keyboards interpret only the Control-modifier when constructing numbered
                // function-keys. Other special keys are not modified.
                // (1) allows modification of the numeric keypad
                // (2) allows modification of the editing keypad
                // (4) allows modification of function-keys, overrides use of Shift-modifier for UDK.
                // (8) allows modification of other special keys
                // * modifyCursorKeys (parameter=2):
                // Tells how to handle the special case where Control-, Shift-, Alt- or Meta-modifiers are used to add a
                // parameter to the escape sequence returned by a cursor-key. The default is "2".
                // - Set it to -1 to disable it.
                // - Set it to 0 to use the old/obsolete behavior.
                // - Set it to 1 to prefix modified sequences with CSI.
                // - Set it to 2 to force the modifier to be the second parameter if it would otherwise be the first.
                // - Set it to 3 to mark the sequence with a ">" to hint that it is private.
                // * modifyFunctionKeys (parameter=3):
                // Tells how to handle the special case where Control-, Shift-, Alt- or Meta-modifiers are used to add a
                // parameter to the escape sequence returned by a (numbered) function-
                // key. The default is "2". The resource values are similar to modifyCursorKeys:
                // Set it to -1 to permit the user to use shift- and control-modifiers to construct function-key strings
                // using the normal encoding scheme.
                // - Set it to 0 to use the old/obsolete behavior.
                // - Set it to 1 to prefix modified sequences with CSI.
                // - Set it to 2 to force the modifier to be the second parameter if it would otherwise be the first.
                // - Set it to 3 to mark the sequence with a ">" to hint that it is private.
                // If modifyFunctionKeys is zero, xterm uses Control- and Shift-modifiers to allow the user to construct
                // numbered function-keys beyond the set provided by the keyboard:
                // (Control) adds the value given by the ctrlFKeys resource.
                // (Shift) adds twice the value given by the ctrlFKeys resource.
                // (Control/Shift) adds three times the value given by the ctrlFKeys resource.
                //
                // As a special case, legacy (when oldFunctionKeys is true) or vt220 (when sunKeyboard is true)
                // keyboards interpret only the Control-modifier when constructing numbered function-keys.
                // This is done to provide compatible keyboards for DEC VT220 and related terminals that
                // implement user-defined keys (UDK).
                // * modifyOtherKeys (parameter=4):
                // Like modifyCursorKeys, tells xterm to construct an escape sequence for other keys (such as "2") when
                // modified by Control-, Alt- or Meta-modifiers. This feature does not apply to function keys and
                // well-defined keys such as ESC or the control keys. The default is "0".
                // (0) disables this feature.
                // (1) enables this feature for keys except for those with well-known behavior, e.g., Tab, Backarrow and
                // some special control character cases, e.g., Control-Space to make a NUL.
                // (2) enables this feature for keys including the exceptions listed.
                Logger.logError(mClient, LOG_TAG, "(ignored) CSI > MODIFY RESOURCE: " + getArg0(-1) + " to " + getArg1(-1));
                break;
            case ' ':
                continueSequence(ESC_CSI_BIGGERTHAN_ARGS_SPACE);
                break;
            default:
                parseArg(b);
                break;
        }
    }

    /** Implements kitty's CSI &gt; ... SPACE q multiple-cursors protocol. */
    private void processExtraCursorSequence() {
        if (mArgs[0] < 0) {
            mSession.write("\033[>1;2;3;29;30;40;100;101 q");
            return;
        }
        int operation = mArgs[0];
        if (mArgIndex == 0) {
            if (operation == 100) writeExtraCursorQuery();
            else if (operation == 101) writeExtraCursorColorQuery();
            return;
        }
        int groupStart = 1;
        for (int i = 2; i <= mArgIndex + 1; i++) {
            boolean boundary = i > mArgIndex || (mArgsSubParamsBitSet & (1 << i)) == 0;
            if (boundary) {
                applyExtraCursorGroup(operation, groupStart, i);
                groupStart = i;
            }
        }
        rebuildExtraCursorSnapshot();
    }

    private void applyExtraCursorGroup(int operation, int start, int end) {
        int count = end - start;
        if (operation == 30 || operation == 40) {
            if (count <= 0) return;
            ExtraCursorColor color = operation == 40 ? mExtraCursorColor : mExtraCursorTextColor;
            int type = mArgs[start];
            if (type == 0 || type == 1) {
                color.type = type;
                color.value = 0;
            } else if (type == 2 && count >= 4) {
                color.type = 2;
                color.value = 0xff000000 | ((mArgs[start + 1] & 0xff) << 16)
                    | ((mArgs[start + 2] & 0xff) << 8) | (mArgs[start + 3] & 0xff);
            } else if (type == 5 && count >= 2) {
                color.type = 5;
                color.value = mArgs[start + 1] & 0xff;
            }
            return;
        }
        if (operation != 0 && operation != 1 && operation != 2
            && operation != 3 && operation != 29) return;
        int shape = operation;
        int coordinateType = count == 0 ? -1 : mArgs[start];
        if (coordinateType == 0) {
            setExtraCursor(mCursorRow, mCursorCol, shape);
        } else if (coordinateType == 2) {
            for (int i = start + 1; i + 1 < end; i += 2) {
                setExtraCursor(mArgs[i] - 1, mArgs[i + 1] - 1, shape);
            }
        } else if (coordinateType == 4) {
            if (count < 5) {
                setExtraCursorRectangle(0, 0, mRows - 1, mColumns - 1, shape);
            } else {
                for (int i = start + 1; i + 3 < end; i += 4) {
                    setExtraCursorRectangle(mArgs[i] - 1, mArgs[i + 1] - 1,
                        mArgs[i + 2] - 1, mArgs[i + 3] - 1, shape);
                }
            }
        }
    }

    private void setExtraCursor(int row, int col, int shape) {
        if (row < 0 || row >= mRows || col < 0 || col >= mColumns) return;
        int key = (row << 16) | col;
        if (shape == 0) mExtraCursors.remove(key);
        else mExtraCursors.put(key, new ExtraCursor(row, col, shape));
    }

    private void setExtraCursorRectangle(int top, int left, int bottom, int right, int shape) {
        top = Math.max(0, top);
        left = Math.max(0, left);
        bottom = Math.min(mRows - 1, bottom);
        right = Math.min(mColumns - 1, right);
        if (bottom < top || right < left) return;
        for (int row = top; row <= bottom; row++) {
            for (int col = left; col <= right; col++) setExtraCursor(row, col, shape);
        }
    }

    private void rebuildExtraCursorSnapshot() {
        mExtraCursorSnapshot = mExtraCursors.values().toArray(new ExtraCursor[0]);
    }

    private void clearExtraCursors() {
        if (mExtraCursors.isEmpty()) return;
        mExtraCursors.clear();
        mExtraCursorSnapshot = new ExtraCursor[0];
    }

    private void writeExtraCursorQuery() {
        StringBuilder response = new StringBuilder("\033[>100");
        for (ExtraCursor cursor : mExtraCursorSnapshot) {
            response.append(';').append(cursor.shape).append(":2:")
                .append(cursor.row + 1).append(':').append(cursor.col + 1);
        }
        mSession.write(response.append(" q").toString());
    }

    private void writeExtraCursorColorQuery() {
        mSession.write("\033[>101;30:" + serializeExtraCursorColor(mExtraCursorTextColor)
            + ";40:" + serializeExtraCursorColor(mExtraCursorColor) + " q");
    }

    private static String serializeExtraCursorColor(ExtraCursorColor color) {
        if (color.type == 1) return "1";
        if (color.type == 5) return "5:" + color.value;
        if (color.type == 2) return "2:" + ((color.value >> 16) & 0xff) + ':'
            + ((color.value >> 8) & 0xff) + ':' + (color.value & 0xff);
        return "0";
    }

    public ExtraCursor[] getExtraCursors() {
        return mExtraCursorSnapshot;
    }

    public ExtraCursorColor getExtraCursorColor() {
        return mExtraCursorColor;
    }

    public ExtraCursorColor getExtraCursorTextColor() {
        return mExtraCursorTextColor;
    }

    private void startEscapeSequence() {
        mEscapeState = ESC;
        mCsiSequenceLength = 0;
        mApcSequenceLength = 0;
        mDcsSequenceLength = 0;
        mDcsSequenceMaxLength = MAX_STRING_SEQUENCE_LENGTH;
        mArgIndex = 0;
        Arrays.fill(mArgs, -1);
        mArgsSubParamsBitSet = 0;
    }

    private void doLinefeed() {
        boolean belowScrollingRegion = mCursorRow >= mBottomMargin;
        int newCursorRow = mCursorRow + 1;
        if (belowScrollingRegion) {
            // Move down (but not scroll) as long as we are above the last row.
            if (mCursorRow != mRows - 1) {
                setCursorRow(newCursorRow);
            }
        } else {
            if (newCursorRow == mBottomMargin) {
                scrollDownOneLine();
                newCursorRow = mBottomMargin - 1;
            }
            setCursorRow(newCursorRow);
        }
    }

    private void continueSequence(int state) {
        mEscapeState = state;
        mContinueSequence = true;
    }

    private void doEscPound(int b) {
        switch(b) {
            case // Esc # 8 - DEC screen alignment test - fill screen with E's.
            '8':
                mScreen.blockSet(0, 0, mColumns, mRows, 'E', getStyle());
                break;
            default:
                unknownSequence(b);
                break;
        }
    }

    /**
     * Encountering a character in the {@link #ESC} state.
     */
    private void doEsc(int b) {
        switch(b) {
            case '#':
                continueSequence(ESC_POUND);
                break;
            case '(':
                continueSequence(ESC_SELECT_LEFT_PAREN);
                break;
            case ')':
                continueSequence(ESC_SELECT_RIGHT_PAREN);
                break;
            case // Back index (http://www.vt100.net/docs/vt510-rm/DECBI). Move left, insert blank column if start.
            '6':
                if (mCursorCol > mLeftMargin) {
                    mCursorCol--;
                } else {
                    int rows = mBottomMargin - mTopMargin;
                    mScreen.blockCopy(mLeftMargin, mTopMargin, mRightMargin - mLeftMargin - 1, rows, mLeftMargin + 1, mTopMargin);
                    mScreen.blockSet(mLeftMargin, mTopMargin, 1, rows, ' ', TextStyle.encode(mForeColor, mBackColor, 0));
                }
                break;
            case // DECSC save cursor - http://www.vt100.net/docs/vt510-rm/DECSC
            '7':
                saveCursor();
                break;
            case // DECRC restore cursor - http://www.vt100.net/docs/vt510-rm/DECRC
            '8':
                restoreCursor();
                break;
            case // Forward Index (http://www.vt100.net/docs/vt510-rm/DECFI). Move right, insert blank column if end.
            '9':
                if (mCursorCol < mRightMargin - 1) {
                    mCursorCol++;
                } else {
                    int rows = mBottomMargin - mTopMargin;
                    mScreen.blockCopy(mLeftMargin + 1, mTopMargin, mRightMargin - mLeftMargin - 1, rows, mLeftMargin, mTopMargin);
                    mScreen.blockSet(mRightMargin - 1, mTopMargin, 1, rows, ' ', TextStyle.encode(mForeColor, mBackColor, 0));
                }
                break;
            case // RIS - Reset to Initial State (http://vt100.net/docs/vt510-rm/RIS).
            'c':
                reset();
                mMainBuffer.clearTranscript();
                blockClear(0, 0, mColumns, mRows);
                setCursorPosition(0, 0);
                break;
            case // INDEX
            'D':
                doLinefeed();
                break;
            case // Next line (http://www.vt100.net/docs/vt510-rm/NEL).
            'E':
                setCursorCol(isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE) ? mLeftMargin : 0);
                doLinefeed();
                break;
            case // Cursor to lower-left corner of screen
            'F':
                setCursorRowCol(0, mBottomMargin - 1);
                break;
            case // Tab set
            'H':
                mTabStop[mCursorCol] = true;
                break;
            case // "${ESC}M" - reverse index (RI).
            'M':
                // http://www.vt100.net/docs/vt100-ug/chapter3.html: "Move the active position to the same horizontal
                // position on the preceding line. If the active position is at the top margin, a scroll down is performed".
                if (mCursorRow <= mTopMargin) {
                    mScreen.blockCopy(mLeftMargin, mTopMargin, mRightMargin - mLeftMargin, mBottomMargin - (mTopMargin + 1), mLeftMargin, mTopMargin + 1);
                    blockClear(mLeftMargin, mTopMargin, mRightMargin - mLeftMargin);
                } else {
                    mCursorRow--;
                }
                break;
            // SS2, ignore.
            case 'N':
            case // SS3, ignore.
            '0':
                break;
            case // Device control string
            'P':
                clearStringSequenceArgs();
                ESC_P_escape = false;
                mDcsSequenceLength = 0;
                mDcsSequenceMaxLength = MAX_STRING_SEQUENCE_LENGTH;
                continueSequence(ESC_P);
                break;
            case '[':
                mCsiSequenceLength = 0;
                continueSequence(ESC_CSI);
                break;
            case // DECKPAM
            '=':
                setDecsetinternalBit(DECSET_BIT_APPLICATION_KEYPAD, true);
                break;
            case // OSC
            ']':
                clearStringSequenceArgs();
                continueSequence(ESC_OSC);
                break;
            case '_': // APC - Application Program Command.
                clearStringSequenceArgs();
                mApcSequenceLength = 0;
                continueSequence(ESC_APC);
                break;
            default:
                unknownSequence(b);
                break;
        }
    }

    /**
     * DECSC save cursor - http://www.vt100.net/docs/vt510-rm/DECSC . See {@link #restoreCursor()}.
     */
    private void saveCursor() {
        SavedScreenState state = (mScreen == mMainBuffer) ? mSavedStateMain : mSavedStateAlt;
        state.mSavedCursorRow = mCursorRow;
        state.mSavedCursorCol = mCursorCol;
        state.mSavedEffect = mEffect;
        state.mSavedForeColor = mForeColor;
        state.mSavedBackColor = mBackColor;
        state.mSavedUnderlineStyle = mUnderlineStyle;
        state.mSavedUnderlineColor = mUnderlineColor;
        state.mSavedDecFlags = mCurrentDecSetFlags;
        state.mUseLineDrawingG0 = mUseLineDrawingG0;
        state.mUseLineDrawingG1 = mUseLineDrawingG1;
        state.mUseLineDrawingUsesG0 = mUseLineDrawingUsesG0;
    }

    /**
     * DECRS restore cursor - http://www.vt100.net/docs/vt510-rm/DECRC. See {@link #saveCursor()}.
     */
    private void restoreCursor() {
        SavedScreenState state = (mScreen == mMainBuffer) ? mSavedStateMain : mSavedStateAlt;
        setCursorRowCol(state.mSavedCursorRow, state.mSavedCursorCol);
        mEffect = state.mSavedEffect;
        mForeColor = state.mSavedForeColor;
        mBackColor = state.mSavedBackColor;
        mUnderlineStyle = state.mSavedUnderlineStyle;
        mUnderlineColor = state.mSavedUnderlineColor;
        int mask = (DECSET_BIT_AUTOWRAP | DECSET_BIT_ORIGIN_MODE);
        mCurrentDecSetFlags = (mCurrentDecSetFlags & ~mask) | (state.mSavedDecFlags & mask);
        mUseLineDrawingG0 = state.mUseLineDrawingG0;
        mUseLineDrawingG1 = state.mUseLineDrawingG1;
        mUseLineDrawingUsesG0 = state.mUseLineDrawingUsesG0;
    }

    /**
     * Following a CSI - Control Sequence Introducer, "\033[". {@link #ESC_CSI}.
     */
    private void doCsi(int b) {
        switch(b) {
            case '!':
                continueSequence(ESC_CSI_EXCLAMATION);
                break;
            case '"':
                continueSequence(ESC_CSI_DOUBLE_QUOTE);
                break;
            case '\'':
                continueSequence(ESC_CSI_SINGLE_QUOTE);
                break;
            case '$':
                continueSequence(ESC_CSI_DOLLAR);
                break;
            case '*':
                continueSequence(ESC_CSI_ARGS_ASTERIX);
                break;
            case '@':
                {
                    // "CSI{n}@" - Insert ${n} space characters (ICH) - http://www.vt100.net/docs/vt510-rm/ICH.
                    mAboutToAutoWrap = false;
                    int columnsAfterCursor = mColumns - mCursorCol;
                    int spacesToInsert = Math.min(getArg0(1), columnsAfterCursor);
                    int charsToMove = columnsAfterCursor - spacesToInsert;
                    mScreen.blockCopy(mCursorCol, mCursorRow, charsToMove, 1, mCursorCol + spacesToInsert, mCursorRow);
                    blockClear(mCursorCol, mCursorRow, spacesToInsert);
                }
                break;
            case // "CSI${n}A" - Cursor up (CUU) ${n} rows.
            'A':
                setCursorRow(Math.max(0, mCursorRow - getArg0(1)));
                break;
            case // "CSI${n}B" - Cursor down (CUD) ${n} rows.
            'B':
                setCursorRow(Math.min(mRows - 1, mCursorRow + getArg0(1)));
                break;
            // "CSI${n}C" - Cursor forward (CUF).
            case 'C':
            case // "CSI${n}a" - Horizontal position relative (HPR). From ISO-6428/ECMA-48.
            'a':
                setCursorCol(Math.min(mRightMargin - 1, mCursorCol + getArg0(1)));
                break;
            case // "CSI${n}D" - Cursor backward (CUB) ${n} columns.
            'D':
                setCursorCol(Math.max(mLeftMargin, mCursorCol - getArg0(1)));
                break;
            case // "CSI{n}E - Cursor Next Line (CNL). From ISO-6428/ECMA-48.
            'E':
                setCursorPosition(0, mCursorRow + getArg0(1));
                break;
            case // "CSI{n}F - Cursor Previous Line (CPL). From ISO-6428/ECMA-48.
            'F':
                setCursorPosition(0, mCursorRow - getArg0(1));
                break;
            case // "CSI${n}G" - Cursor horizontal absolute (CHA) to column ${n}.
            'G':
                setCursorCol(Math.min(Math.max(1, getArg0(1)), mColumns) - 1);
                break;
            // "${CSI}${ROW};${COLUMN}H" - Cursor position (CUP).
            case 'H':
            case // "${CSI}${ROW};${COLUMN}f" - Horizontal and Vertical Position (HVP).
            'f':
                setCursorPosition(getArg1(1) - 1, getArg0(1) - 1);
                break;
            case // Cursor Horizontal Forward Tabulation (CHT). Move the active position n tabs forward.
            'I':
                setCursorCol(nextTabStop(getArg0(1)));
                break;
            case // "${CSI}${0,1,2,3}J" - Erase in Display (ED)
            'J':
                // ED ignores the scrolling margins.
                switch(getArg0(0)) {
                    case // Erase from the active position to the end of the screen, inclusive (default).
                    0:
                        blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol);
                        blockClear(0, mCursorRow + 1, mColumns, mRows - (mCursorRow + 1));
                        break;
                    case // Erase from start of the screen to the active position, inclusive.
                    1:
                        blockClear(0, 0, mColumns, mCursorRow);
                        blockClear(0, mCursorRow, mCursorCol + 1);
                        break;
                    case // Erase all of the display - all lines are erased, changed to single-width, and the cursor does not
                    2:
                        // move..
                        mKittyGraphics.screenCleared();
                        blockClear(0, 0, mColumns, mRows);
                        clearExtraCursors();
                        break;
                    case // Delete all lines saved in the scrollback buffer (xterm etc)
                    3:
                        mMainBuffer.clearTranscript();
                        clearExtraCursors();
                        break;
                    case // xterm extension: erase the display and saved lines.
                    22:
                        clearExtraCursors();
                        break;
                    default:
                        unknownSequence(b);
                        return;
                }
                mAboutToAutoWrap = false;
                break;
            case // "CSI{n}K" - Erase in line (EL).
            'K':
                switch(getArg0(0)) {
                    case // Erase from the cursor to the end of the line, inclusive (default)
                    0:
                        blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol);
                        break;
                    case // Erase from the start of the screen to the cursor, inclusive.
                    1:
                        blockClear(0, mCursorRow, mCursorCol + 1);
                        break;
                    case // Erase all of the line.
                    2:
                        blockClear(0, mCursorRow, mColumns);
                        break;
                    default:
                        unknownSequence(b);
                        return;
                }
                mAboutToAutoWrap = false;
                break;
            case // "${CSI}{N}L" - insert ${N} lines (IL).
            'L':
                {
                    int linesAfterCursor = mBottomMargin - mCursorRow;
                    int linesToInsert = Math.min(getArg0(1), linesAfterCursor);
                    int linesToMove = linesAfterCursor - linesToInsert;
                    mScreen.blockCopy(0, mCursorRow, mColumns, linesToMove, 0, mCursorRow + linesToInsert);
                    blockClear(0, mCursorRow, mColumns, linesToInsert);
                }
                break;
            case // "${CSI}${N}M" - delete N lines (DL).
            'M':
                {
                    mAboutToAutoWrap = false;
                    int linesAfterCursor = mBottomMargin - mCursorRow;
                    int linesToDelete = Math.min(getArg0(1), linesAfterCursor);
                    int linesToMove = linesAfterCursor - linesToDelete;
                    mScreen.blockCopy(0, mCursorRow + linesToDelete, mColumns, linesToMove, 0, mCursorRow);
                    blockClear(0, mCursorRow + linesToMove, mColumns, linesToDelete);
                }
                break;
            case // "${CSI}{N}P" - delete ${N} characters (DCH).
            'P':
                {
                    // http://www.vt100.net/docs/vt510-rm/DCH: "If ${N} is greater than the number of characters between the
                    // cursor and the right margin, then DCH only deletes the remaining characters.
                    // As characters are deleted, the remaining characters between the cursor and right margin move to the left.
                    // Character attributes move with the characters. The terminal adds blank spaces with no visual character
                    // attributes at the right margin. DCH has no effect outside the scrolling margins."
                    mAboutToAutoWrap = false;
                    int cellsAfterCursor = mColumns - mCursorCol;
                    int cellsToDelete = Math.min(getArg0(1), cellsAfterCursor);
                    int cellsToMove = cellsAfterCursor - cellsToDelete;
                    mScreen.blockCopy(mCursorCol + cellsToDelete, mCursorRow, cellsToMove, 1, mCursorCol, mCursorRow);
                    blockClear(mCursorCol + cellsToMove, mCursorRow, cellsToDelete);
                }
                break;
            case 'S':
                {
                    // "${CSI}${N}S" - scroll up ${N} lines (default = 1) (SU).
                    final int linesToScroll = getArg0(1);
                    for (int i = 0; i < linesToScroll; i++) scrollDownOneLine();
                    break;
                }
            case 'T':
                if (mArgIndex == 0) {
                    // "${CSI}${N}T" - Scroll down N lines (default = 1) (SD).
                    // http://vt100.net/docs/vt510-rm/SD: "N is the number of lines to move the user window up in page
                    // memory. N new lines appear at the top of the display. N old lines disappear at the bottom of the
                    // display. You cannot pan past the top margin of the current page".
                    final int linesToScrollArg = getArg0(1);
                    final int linesBetweenTopAndBottomMargins = mBottomMargin - mTopMargin;
                    final int linesToScroll = Math.min(linesBetweenTopAndBottomMargins, linesToScrollArg);
                    mScreen.blockCopy(mLeftMargin, mTopMargin, mRightMargin - mLeftMargin, linesBetweenTopAndBottomMargins - linesToScroll, mLeftMargin, mTopMargin + linesToScroll);
                    blockClear(mLeftMargin, mTopMargin, mRightMargin - mLeftMargin, linesToScroll);
                } else {
                    // "${CSI}${func};${startx};${starty};${firstrow};${lastrow}T" - initiate highlight mouse tracking.
                    unimplementedSequence(b);
                }
                break;
            case // "${CSI}${N}X" - Erase ${N:=1} character(s) (ECH). FIXME: Clears character attributes?
            'X':
                mAboutToAutoWrap = false;
                mScreen.blockSet(mCursorCol, mCursorRow, Math.min(getArg0(1), mColumns - mCursorCol), 1, ' ', getStyle());
                break;
            case // Cursor Backward Tabulation (CBT). Move the active position n tabs backward.
            'Z':
                int numberOfTabs = getArg0(1);
                int newCol = mLeftMargin;
                for (int i = mCursorCol - 1; i >= 0; i--) if (mTabStop[i]) {
                    if (--numberOfTabs == 0) {
                        newCol = Math.max(i, mLeftMargin);
                        break;
                    }
                }
                mCursorCol = newCol;
                break;
            case '?': // Esc [ ? -- start of a private parameter byte
                continueSequence(ESC_CSI_QUESTIONMARK);
                break;
            case '>': // "Esc [ >" -- start of a private parameter byte
                continueSequence(ESC_CSI_BIGGERTHAN);
                break;
            case '<': // "Esc [ <" -- start of a private parameter byte
                continueSequence(ESC_CSI_LESSTHAN);
                break;
            case '=': // "Esc [ =" -- start of a private parameter byte
                continueSequence(ESC_CSI_EQUAL);
                break;
            case '`': // Horizontal position absolute (HPA - http://www.vt100.net/docs/vt510-rm/HPA).
                setCursorColRespectingOriginMode(getArg0(1) - 1);
                break;
            case // Repeat the preceding graphic character Ps times (REP).
            'b':
                if (mLastEmittedCodePoint == -1)
                    break;
                final int numRepeat = getArg0(1);
                for (int i = 0; i < numRepeat; i++) emitCodePoint(mLastEmittedCodePoint);
                break;
            case // Primary Device Attributes (http://www.vt100.net/docs/vt510-rm/DA1) if argument is missing or zero.
            'c':
                // The important part that may still be used by some (tmux stores this value but does not currently use it)
                // is the first response parameter identifying the terminal service class, where we send 64 for "vt420".
                // This is followed by a list of attributes which is probably unused by applications. Send like xterm.
                if (getArg0(0) == 0)
                    mSession.write("\033[?64;1;2;4;6;9;15;18;21;22c");
                break;
            case // ESC [ Pn d - Vert Position Absolute
            'd':
                setCursorRow(Math.min(Math.max(1, getArg0(1)), mRows) - 1);
                break;
            case // Vertical Position Relative (VPR). From ISO-6429 (ECMA-48).
            'e':
                setCursorPosition(mCursorCol, mCursorRow + getArg0(1));
                break;
            // case 'f': "${CSI}${ROW};${COLUMN}f" - Horizontal and Vertical Position (HVP). Grouped with case 'H'.
            case // Clear tab stop
            'g':
                switch(getArg0(0)) {
                    case 0:
                        mTabStop[mCursorCol] = false;
                        break;
                    case 3:
                        for (int i = 0; i < mColumns; i++) {
                            mTabStop[i] = false;
                        }
                        break;
                    default:
                        // Specified to have no effect.
                        break;
                }
                break;
            case // Set Mode
            'h':
                doSetMode(true);
                break;
            case // Reset Mode
            'l':
                doSetMode(false);
                break;
            case // Esc [ Pn m - character attributes. (can have up to 16 numerical arguments)
            'm':
                selectGraphicRendition();
                break;
            case // Esc [ Pn n - ECMA-48 Status Report Commands
            'n':
                // sendDeviceAttributes()
                switch(getArg0(0)) {
                    case // Device status report (DSR):
                    5:
                        // Answer is ESC [ 0 n (Terminal OK).
                        byte[] dsr = { (byte) 27, (byte) '[', (byte) '0', (byte) 'n' };
                        mSession.write(dsr, 0, dsr.length);
                        break;
                    case // Cursor position report (CPR):
                    6:
                        // Answer is ESC [ y ; x R, where x,y is
                        // the cursor location.
                        mSession.write(String.format(Locale.US, "\033[%d;%dR", mCursorRow + 1, mCursorCol + 1));
                        break;
                    default:
                        break;
                }
                break;
            case // "CSI${top};${bottom}r" - set top and bottom Margins (DECSTBM).
            'r':
                {
                    // https://vt100.net/docs/vt510-rm/DECSTBM.html
                    // The top margin defaults to 1, the bottom margin defaults to mRows.
                    // The escape sequence numbers top 1..23, but we number top 0..22.
                    // The escape sequence numbers bottom 2..24, and so do we (because we use a zero based numbering
                    // scheme, but we store the first line below the bottom-most scrolling line.
                    // As a result, we adjust the top line by -1, but we leave the bottom line alone.
                    // Also require that top + 2 <= bottom.
                    mTopMargin = Math.max(0, Math.min(getArg0(1) - 1, mRows - 2));
                    mBottomMargin = Math.max(mTopMargin + 2, Math.min(getArg1(mRows), mRows));
                    // DECSTBM moves the cursor to column 1, line 1 of the page respecting origin mode.
                    setCursorPosition(0, 0);
                }
                break;
            case 's':
                if (isDecsetInternalBitSet(DECSET_BIT_LEFTRIGHT_MARGIN_MODE)) {
                    // Set left and right margins (DECSLRM - http://www.vt100.net/docs/vt510-rm/DECSLRM).
                    mLeftMargin = Math.min(getArg0(1) - 1, mColumns - 2);
                    mRightMargin = Math.max(mLeftMargin + 1, Math.min(getArg1(mColumns), mColumns));
                    // DECSLRM moves the cursor to column 1, line 1 of the page.
                    setCursorPosition(0, 0);
                } else {
                    // Save cursor (ANSI.SYS), available only when DECLRMM is disabled.
                    saveCursor();
                }
                break;
            case // Window manipulation (from dtterm, as well as extensions)
            't':
                switch(getArg0(0)) {
                    case // Report xterm window state. If the xterm window is open (non-iconified), it returns CSI 1 t .
                    11:
                        mSession.write("\033[1t");
                        break;
                    case // Report xterm window position. Result is CSI 3 ; x ; y t
                    13:
                        mSession.write("\033[3;0;0t");
                        break;
                    case 14: // Report xterm window in pixels. Result is CSI 4 ; height ; width t
                        mSession.write(String.format(Locale.US, "\033[4;%d;%dt", mRows * mCellHeightPixels, mColumns * mCellWidthPixels));
                        break;
                    case 16: // Report xterm character cell size in pixels. Result is CSI 6 ; height ; width t
                        mSession.write(String.format(Locale.US, "\033[6;%d;%dt", mCellHeightPixels, mCellWidthPixels));
                        break;
                    case // Report the size of the text area in characters. Result is CSI 8 ; height ; width t
                    18:
                        mSession.write(String.format(Locale.US, "\033[8;%d;%dt", mRows, mColumns));
                        break;
                    case // Report the size of the screen in characters. Result is CSI 9 ; height ; width t
                    19:
                        // We report the same size as the view, since it's the view really isn't resizable from the shell.
                        mSession.write(String.format(Locale.US, "\033[9;%d;%dt", mRows, mColumns));
                        break;
                    case // Report xterm windows icon label. Result is OSC L label ST. Disabled due to security concerns:
                    20:
                        mSession.write("\033]LIconLabel\033\\");
                        break;
                    case // Report xterm windows title. Result is OSC l label ST. Disabled due to security concerns:
                    21:
                        mSession.write("\033]l\033\\");
                        break;
                    case 22:
                        // 22;0 -> Save xterm icon and window title on stack.
                        // 22;1 -> Save xterm icon title on stack.
                        // 22;2 -> Save xterm window title on stack.
                        mTitleStack.push(mTitle);
                        if (mTitleStack.size() > 20) {
                            // Limit size
                            mTitleStack.remove(0);
                        }
                        break;
                    case // Like 22 above but restore from stack.
                    23:
                        if (!mTitleStack.isEmpty())
                            setTitle(mTitleStack.pop());
                        break;
                    default:
                        // Ignore window manipulation.
                        break;
                }
                break;
            case // Restore cursor (ANSI.SYS).
            'u':
                restoreCursor();
                break;
            case ' ':
                continueSequence(ESC_CSI_ARGS_SPACE);
                break;
            default:
                parseArg(b);
                break;
        }
    }

    /**
     * Select Graphic Rendition (SGR) - see http://en.wikipedia.org/wiki/ANSI_escape_code#graphics.
     */
    private void selectGraphicRendition() {
        if (mArgIndex >= mArgs.length)
            mArgIndex = mArgs.length - 1;
        for (int i = 0; i <= mArgIndex; i++) {
            // Skip leading sub parameters:
            if ((mArgsSubParamsBitSet & (1 << i)) != 0) {
                continue;
            }

            int code = getArg(i, 0, false);
            if (code < 0) {
                if (mArgIndex > 0) {
                    continue;
                } else {
                    code = 0;
                }
            }
            if (code == 0) {
                // reset
                mForeColor = TextStyle.COLOR_INDEX_FOREGROUND;
                mBackColor = TextStyle.COLOR_INDEX_BACKGROUND;
                mUnderlineColor = TextStyle.DECORATION_COLOR_DEFAULT;
                mEffect = 0;
                mUnderlineStyle = TextStyle.UNDERLINE_STYLE_NONE;
            } else if (code == 1) {
                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_BOLD;
            } else if (code == 2) {
                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_DIM;
            } else if (code == 3) {
                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_ITALIC;
            } else if (code == 4) {
                if (i + 1 <= mArgIndex && ((mArgsSubParamsBitSet & (1 << (i + 1))) != 0)) {
                    // Sub parameter, see https://sw.kovidgoyal.net/kitty/underlines/
                    i++;
                    int requestedStyle = mArgs[i];
                    // An unknown style is drawn as a single underline rather than dropped, which is
                    // what the kitty specification asks of terminals that do not know it.
                    if (requestedStyle < 0 || requestedStyle > TextStyle.UNDERLINE_STYLE_MAX)
                        requestedStyle = TextStyle.UNDERLINE_STYLE_SINGLE;
                    setUnderlineStyle(requestedStyle);
                } else {
                    setUnderlineStyle(TextStyle.UNDERLINE_STYLE_SINGLE);
                }
            } else if (code == 5) {
                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_BLINK;
            } else if (code == 7) {
                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
            } else if (code == 8) {
                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE;
            } else if (code == 9) {
                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH;
            } else if (code == 10) {
                // Exit alt charset (TERM=linux) - ignore.
            } else if (code == 11) {
                // Enter alt charset (TERM=linux) - ignore.
            } else if (code == 21) {
                // Doubly underlined (ECMA-48), the same rendition as SGR 4:2.
                setUnderlineStyle(TextStyle.UNDERLINE_STYLE_DOUBLE);
            } else if (code == 22) {
                // Normal color or intensity, neither bright, bold nor faint.
                mEffect &= ~(TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_DIM);
            } else if (code == 23) {
                // not italic, but rarely used as such; clears standout with TERM=screen
                mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_ITALIC;
            } else if (code == 24) {
                // underline: none
                setUnderlineStyle(TextStyle.UNDERLINE_STYLE_NONE);
            } else if (code == 25) {
                // blink: none
                mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_BLINK;
            } else if (code == 27) {
                // image: positive
                mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
            } else if (code == 28) {
                mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE;
            } else if (code == 29) {
                mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH;
            } else if (code >= 30 && code <= 37) {
                mForeColor = code - 30;
            } else if (code == 38 || code == 48 || code == 58) {
                // Extended set foreground(38)/background(48)/underline(58) color.
                // This is followed by either "2;$R;$G;$B" to set a 24-bit color or
                // "5;$INDEX" to set an indexed color.
                if (i + 2 > mArgIndex)
                    continue;
                int firstArg = mArgs[i + 1];
                if (firstArg == 2) {
                    if (i + 4 > mArgIndex) {
                        Logger.logWarn(mClient, LOG_TAG, "Too few CSI" + code + ";2 RGB arguments");
                    } else {
                        int red = getArg(i + 2, 0, false);
                        int green = getArg(i + 3, 0, false);
                        int blue = getArg(i + 4, 0, false);

                        if (red < 0 || green < 0 || blue < 0 || red > 255 || green > 255 || blue > 255) {
                            finishSequenceAndLogError("Invalid RGB: " + red + "," + green + "," + blue);
                        } else {
                            int argbColor = 0xff_00_00_00 | (red << 16) | (green << 8) | blue;
                            switch (code) {
                                case 38: mForeColor = argbColor; break;
                                case 48: mBackColor = argbColor; break;
                                case 58: mUnderlineColor = argbColor; break;
                            }
                        }
                        // "2;P_r;P_g;P_r"
                        i += 4;
                    }
                } else if (firstArg == 5) {
                    int color = getArg(i + 2, 0, false);
                    i += 2; // "5;P_s"
                    if (color >= 0 && color < TextStyle.NUM_INDEXED_COLORS) {
                        switch (code) {
                            case 38: mForeColor = color; break;
                            case 48: mBackColor = color; break;
                            case 58: mUnderlineColor = color; break;
                        }
                    } else {
                        if (LOG_ESCAPE_SEQUENCES)
                            Logger.logWarn(mClient, LOG_TAG, "Invalid color index: " + color);
                    }
                } else {
                    finishSequenceAndLogError("Invalid ISO-8613-3 SGR first argument: " + firstArg);
                }
            } else if (code == 39) {
                // Set default foreground color.
                mForeColor = TextStyle.COLOR_INDEX_FOREGROUND;
            } else if (code >= 40 && code <= 47) {
                // Set background color.
                mBackColor = code - 40;
            } else if (code == 49) {
                // Set default background color.
                mBackColor = TextStyle.COLOR_INDEX_BACKGROUND;
            } else if (code == 59) { // Set default underline color.
                mUnderlineColor = TextStyle.DECORATION_COLOR_DEFAULT;
            } else if (code >= 90 && code <= 97) { // Bright foreground colors (aixterm codes).
                mForeColor = code - 90 + 8;
            } else if (code >= 100 && code <= 107) {
                // Bright background color (aixterm codes).
                mBackColor = code - 100 + 8;
            } else {
                if (LOG_ESCAPE_SEQUENCES)
                    Logger.logWarn(mClient, LOG_TAG, String.format("SGR unknown code %d", code));
            }
        }
    }

    private void doOsc(int b) {
        switch(b) {
            case // Bell.
            7:
                doOscSetTextParameters("\007");
                break;
            case // Escape.
            27:
                continueSequence(ESC_OSC_ESC);
                break;
            default:
                collectOSCArgs(b);
                break;
        }
    }

    private void doOscEsc(int b) {
        switch(b) {
            case '\\':
                doOscSetTextParameters("\033\\");
                break;
            default:
                // The ESC character was not followed by a \, so insert the ESC and
                // the current character in arg buffer.
                collectOSCArgs(27);
                collectOSCArgs(b);
                continueSequence(ESC_OSC);
                break;
        }
    }

    /**
     * Handle the payload of an OSC 8 sequence, "$params;$uri".
     * <p>
     * Text emitted from now on belongs to that link until a sequence with an empty URI closes it. Only
     * the {@code id=} parameter is defined; the rest is ignored, as the specification requires of
     * parameters a terminal does not know.
     * </p>
     */
    private void setCurrentHyperlink(String textParameter) {
        int uriStart = textParameter.indexOf(';');
        if (uriStart < 0) {
            // No parameter/URI separator at all. Malformed, so close any open link rather than guess.
            mCurrentHyperlinkId = TerminalHyperlinks.NO_LINK;
            return;
        }
        String params = textParameter.substring(0, uriStart);
        String uri = textParameter.substring(uriStart + 1);
        if (uri.isEmpty()) {
            mCurrentHyperlinkId = TerminalHyperlinks.NO_LINK;
            return;
        }
        for (int i = 0; i < uri.length(); i++) {
            // Control characters cannot appear in a URI; they must be percent encoded. A URI carrying
            // one is either corrupt or an attempt at smuggling, so drop the whole link.
            if (uri.charAt(i) < ' ' || uri.charAt(i) == 0x7f) {
                mCurrentHyperlinkId = TerminalHyperlinks.NO_LINK;
                return;
            }
        }
        String id = "";
        for (String param : params.split(":")) {
            if (param.startsWith("id=")) {
                id = param.substring(3);
                break;
            }
        }
        mCurrentHyperlinkId = mHyperlinks.intern(id, uri);
        if (mCurrentHyperlinkId == TerminalHyperlinks.NO_LINK && mHyperlinks.isFull()) {
            // Saturation is rare. Pay for one complete live-buffer scan here rather than maintaining
            // fragile reference counts across every erase, copy, scroll, resize, and reflow path.
            boolean[] used = new boolean[TerminalHyperlinks.MAX_LINKS + 1];
            mMainBuffer.markUsedHyperlinkIds(used);
            mAltBuffer.markUsedHyperlinkIds(used);
            if (mHyperlinks.reclaimUnused(used) > 0)
                mCurrentHyperlinkId = mHyperlinks.intern(id, uri);
        }
    }

    /**
     * Handle an OSC 133 shell integration mark, which a shell emits around its prompt and each command
     * so that the terminal can tell prompts, typed input, and output apart.
     * <p>
     * Only the marks that a terminal can act on are kept: the row a prompt starts on, the row typed
     * input starts on, the row output starts on, and the exit status of the last command. Everything
     * else in the sequence - the shell's own bookkeeping parameters - is ignored.
     * </p>
     *
     * @see <a href="https://sw.kovidgoyal.net/kitty/shell-integration/">kitty's shell integration</a>
     */
    private void doShellIntegration(String textParameter) {
        if (textParameter.isEmpty())
            return;
        char kind = textParameter.charAt(0);
        if (kind == 'A' || kind == 'B' || kind == 'C' || kind == 'D')
            mShellIntegrationSeen = true;
        switch(kind) {
            case 'A':
                mShellIntegrationCommandRunning = false;
                mScreen.setShellIntegrationMark(mCursorRow, TerminalRow.MARK_PROMPT_START);
                break;
            case 'B':
                mScreen.setShellIntegrationMark(mCursorRow, TerminalRow.MARK_COMMAND_START);
                break;
            case 'C':
                mShellIntegrationCommandRunning = true;
                mScreen.setShellIntegrationMark(mCursorRow, TerminalRow.MARK_OUTPUT_START);
                break;
            case 'D':
                mShellIntegrationCommandRunning = false;
                mLastCommandExitCode = COMMAND_EXIT_CODE_UNKNOWN;
                int separator = textParameter.indexOf(';');
                if (separator >= 0) {
                    try {
                        // The status may be followed by further "key=value" parameters; take the first field.
                        String status = textParameter.substring(separator + 1);
                        int nextSeparator = status.indexOf(';');
                        if (nextSeparator >= 0)
                            status = status.substring(0, nextSeparator);
                        if (!status.isEmpty())
                            mLastCommandExitCode = Integer.parseInt(status);
                    } catch (NumberFormatException e) {
                        // Leave the exit code unknown rather than failing the sequence.
                    }
                }
                break;
            default:
                if (LOG_ESCAPE_SEQUENCES)
                    Logger.logWarn(mClient, LOG_TAG, "Unknown OSC 133 mark '" + kind + "'");
                break;
        }
    }

    /**
     * An Operating System Controls (OSC) Set Text Parameters. May come here from BEL or ST.
     */
    private void doOscSetTextParameters(String bellOrStringTerminator) {
        int value = -1;
        String textParameter = "";
        // Extract initial $value from initial "$value;..." string.
        for (int mOSCArgTokenizerIndex = 0; mOSCArgTokenizerIndex < mOSCOrDeviceControlArgs.length(); mOSCArgTokenizerIndex++) {
            char b = mOSCOrDeviceControlArgs.charAt(mOSCArgTokenizerIndex);
            if (b == ';') {
                textParameter = mOSCOrDeviceControlArgs.substring(mOSCArgTokenizerIndex + 1);
                break;
            } else if (b >= '0' && b <= '9') {
                value = ((value < 0) ? 0 : value * 10) + (b - '0');
            } else {
                unknownSequence(b);
                return;
            }
        }
        switch(value) {
            // Change icon name and window title to T.
            case 0:
            // Change icon name to T.
            case 1:
            case // Change window title to T.
            2:
                setTitle(textParameter);
                break;
            case 4:
                // P s = 4 ; c ; spec → Change Color Number c to the color specified by spec. This can be a name or RGB
                // specification as per XParseColor. Any number of c name pairs may be given. The color numbers correspond
                // to the ANSI colors 0-7, their bright versions 8-15, and if supported, the remainder of the 88-color or
                // 256-color table.
                // If a "?" is given rather than a name or RGB specification, xterm replies with a control sequence of the
                // same form which can be used to set the corresponding color. Because more than one pair of color number
                // and specification can be given in one control sequence, xterm can make more than one reply.
                int colorIndex = -1;
                int parsingPairStart = -1;
                for (int i = 0; ; i++) {
                    boolean endOfInput = i == textParameter.length();
                    char b = endOfInput ? ';' : textParameter.charAt(i);
                    if (b == ';') {
                        if (parsingPairStart < 0) {
                            parsingPairStart = i + 1;
                        } else {
                            if (colorIndex < 0 || colorIndex > 255) {
                                unknownSequence(b);
                                return;
                            } else {
                                mColors.tryParseColor(colorIndex, textParameter.substring(parsingPairStart, i));
                                mSession.onColorsChanged();
                                colorIndex = -1;
                                parsingPairStart = -1;
                            }
                        }
                    } else if (parsingPairStart >= 0) {
                        // We have passed a color index and are now going through color spec.
                    } else if (parsingPairStart < 0 && (b >= '0' && b <= '9')) {
                        colorIndex = ((colorIndex < 0) ? 0 : colorIndex * 10) + (b - '0');
                    } else {
                        unknownSequence(b);
                        return;
                    }
                    if (endOfInput)
                        break;
                }
                break;
            // Set foreground color.
            case 10:
            // Set background color.
            case 11:
            case // Set cursor color.
            12:
                int specialIndex = TextStyle.COLOR_INDEX_FOREGROUND + (value - 10);
                int lastSemiIndex = 0;
                for (int charIndex = 0; ; charIndex++) {
                    boolean endOfInput = charIndex == textParameter.length();
                    if (endOfInput || textParameter.charAt(charIndex) == ';') {
                        try {
                            String colorSpec = textParameter.substring(lastSemiIndex, charIndex);
                            if ("?".equals(colorSpec)) {
                                // Report current color in the same format xterm and gnome-terminal does.
                                int rgb = mColors.mCurrentColors[specialIndex];
                                int r = (65535 * ((rgb & 0x00FF0000) >> 16)) / 255;
                                int g = (65535 * ((rgb & 0x0000FF00) >> 8)) / 255;
                                int b = (65535 * ((rgb & 0x000000FF))) / 255;
                                mSession.write("\033]" + value + ";rgb:" + String.format(Locale.US, "%04x", r) + "/" + String.format(Locale.US, "%04x", g) + "/" + String.format(Locale.US, "%04x", b) + bellOrStringTerminator);
                            } else {
                                mColors.tryParseColor(specialIndex, colorSpec);
                                mSession.onColorsChanged();
                            }
                            specialIndex++;
                            if (endOfInput || (specialIndex > TextStyle.COLOR_INDEX_CURSOR) || ++charIndex >= textParameter.length())
                                break;
                            lastSemiIndex = charIndex;
                        } catch (NumberFormatException e) {
                            // Ignore.
                        }
                    }
                }
                break;
            case // Semantic hyperlink: "8;$params;$uri". An empty $uri closes the current link.
            8:
                setCurrentHyperlink(textParameter);
                break;
            case // Manipulate Selection Data. Skip the optional first selection parameter(s).
            52:
                int startIndex = textParameter.indexOf(";") + 1;
                try {
                    String clipboardText = new String(Base64.decode(textParameter.substring(startIndex), Base64.DEFAULT), StandardCharsets.UTF_8);
                    mSession.onCopyTextToClipboard(clipboardText);
                } catch (Exception e) {
                    Logger.logError(mClient, LOG_TAG, "OSC Manipulate selection, invalid string '" + textParameter + "'");
                }
                break;
            case // Shell integration marks: "133;A" prompt, "133;B" command, "133;C" output, "133;D[;code]" done.
            133:
                doShellIntegration(textParameter);
                break;
            case 104:
                // "104;$c" → Reset Color Number $c. It is reset to the color specified by the corresponding X
                // resource. Any number of c parameters may be given. These parameters correspond to the ANSI colors 0-7,
                // their bright versions 8-15, and if supported, the remainder of the 88-color or 256-color table. If no
                // parameters are given, the entire table will be reset.
                if (textParameter.isEmpty()) {
                    mColors.reset();
                    mSession.onColorsChanged();
                } else {
                    int lastIndex = 0;
                    for (int charIndex = 0; ; charIndex++) {
                        boolean endOfInput = charIndex == textParameter.length();
                        if (endOfInput || textParameter.charAt(charIndex) == ';') {
                            try {
                                int colorToReset = Integer.parseInt(textParameter.substring(lastIndex, charIndex));
                                mColors.reset(colorToReset);
                                mSession.onColorsChanged();
                                if (endOfInput)
                                    break;
                                charIndex++;
                                lastIndex = charIndex;
                            } catch (NumberFormatException e) {
                                // Ignore.
                            }
                        }
                    }
                }
                break;
            // Reset foreground color.
            case 110:
            // Reset background color.
            case 111:
            case // Reset cursor color.
            112:
                mColors.reset(TextStyle.COLOR_INDEX_FOREGROUND + (value - 110));
                mSession.onColorsChanged();
                break;
            case // Reset highlight color.
            119:
                break;
            case // iTerm extemsions
            1337:
                int argsLength = mOSCOrDeviceControlArgs.length();
                String controlCommandPrefix = mOSCOrDeviceControlArgs.substring(5, Math.min(19, argsLength));

                if (controlCommandPrefix.startsWith("File=") ||
                    controlCommandPrefix.startsWith("MultipartFile=") ||
                    controlCommandPrefix.startsWith("FilePart=") ||
                    controlCommandPrefix.equals("FileEnd")) {
                    ITermImage iTermImage = null;
                    boolean oscArgsCleared = false;
                    int index;

                    if (controlCommandPrefix.startsWith("File=")) {
                        if (mITermImage != null) {
                            Logger.logWarn(mClient, LOG_TAG, "A new iTerm 'File' command received while already processing a 'MultipartFile' command");
                            mITermImage = null;
                        }

                        iTermImage = new ITermImage(mClient, false);
                        if ((index = iTermImage.readArguments(this, mOSCOrDeviceControlArgs, 10)) < 10 ||
                            !iTermImage.readImage(mOSCOrDeviceControlArgs, index)) {
                            iTermImage = null;
                        } else {
                            mOSCOrDeviceControlArgs.setLength(0);
                            oscArgsCleared = true;
                            if (!iTermImage.decodeImage()) {
                                iTermImage = null;
                            }
                        }
                    } else if (controlCommandPrefix.startsWith("MultipartFile=")) {
                        if (mITermImage != null) {
                            Logger.logWarn(mClient, LOG_TAG, "A new iTerm 'MultipartFile' command received while already processing a 'MultipartFile' command");
                            mITermImage = null;
                        }

                        iTermImage = new ITermImage(mClient, true);
                        if (iTermImage.readArguments(this, mOSCOrDeviceControlArgs, 19) < 19) {
                            iTermImage = null;
                        } else {
                            mITermImage = iTermImage;
                        }
                    } else if (controlCommandPrefix.startsWith("FilePart=")) {
                        if (mITermImage == null) {
                            Logger.logError(mClient, LOG_TAG, "An iTerm 'FilePart' command received without a 'MultipartFile' command preceding it");
                            return;
                        }

                        if (!mITermImage.readImage(mOSCOrDeviceControlArgs, 14)) {
                            mITermImage = null;
                        }
                    } else if (controlCommandPrefix.equals("FileEnd")) {
                        if (mITermImage == null) {
                            Logger.logError(mClient, LOG_TAG, "An iTerm 'FileEnd' command received without a 'MultipartFile' command preceding it");
                            return;
                        }

                        iTermImage = mITermImage;
                        mITermImage = null;
                        if (!iTermImage.setMultiPartImageRead() || !iTermImage.decodeImage()) {
                            iTermImage = null;
                        }
                    }

                    if (!oscArgsCleared) {
                        mOSCOrDeviceControlArgs.setLength(0);
                    }

                    if (iTermImage != null && iTermImage.isImageDecoded() && iTermImage.isInline()) {
                        int[] res = mScreen.addImage(iTermImage.getDecodedImage(), mCursorRow, mCursorCol, cellW, cellH,
                            iTermImage.getWidth(), iTermImage.getHeight(), iTermImage.shouldPreserveAspectRatio());
                        int col = res[1] + mCursorCol;
                        if (col < mColumns - 1) {
                            res[0] -= 1;
                        } else {
                            col = 0;
                        }
                        for (; res[0] > 0; res[0]--) {
                            doLinefeed();
                        }
                        mCursorCol = col;
                    }
                } else if (controlCommandPrefix.startsWith("ReportCellSize")) {
                    mSession.write(String.format(Locale.US, "\0331337;ReportCellSize=%d;%d\007", mCellHeightPixels, mCellWidthPixels));
                    if (!controlCommandPrefix.startsWith("MultipartFile=") &&
                        !controlCommandPrefix.startsWith("FilePart=")) {
                        mITermImage = null;
                    }
                }
                break;
            default:
                unknownParameter(value);
                break;
        }
        if (mITermImage != null && value != 1337) {
            mITermImage = null;
        }
        finishSequence();
    }

    private void blockClear(int sx, int sy, int w) {
        blockClear(sx, sy, w, 1);
    }

    private void blockClear(int sx, int sy, int w, int h) {
        mScreen.blockSet(sx, sy, w, h, ' ', getStyle());
    }

    private long getStyle() {
        return TextStyle.encode(mForeColor, mBackColor, mEffect, mUnderlineStyle);
    }

    /**
     * Set the underline style, keeping {@link TextStyle#CHARACTER_ATTRIBUTE_UNDERLINE} in step with it
     * so that code which only knows the attribute bit - DECCARA, the renderer's legacy path, terminfo
     * level styling - keeps working.
     */
    private void setUnderlineStyle(int underlineStyle) {
        mUnderlineStyle = underlineStyle;
        if (underlineStyle == TextStyle.UNDERLINE_STYLE_NONE) {
            mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
        } else {
            mEffect |= TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
        }
    }

    /**
     * The exit status the shell last reported through OSC 133;D, or {@link #COMMAND_EXIT_CODE_UNKNOWN}
     * when no command has finished or shell integration is not in use.
     */
    public int getLastCommandExitCode() {
        return mLastCommandExitCode;
    }

    /** Whether the shell has ever reported an OSC 133 mark, i.e. whether shell integration is active. */
    public boolean hasShellIntegration() {
        return mShellIntegrationSeen;
    }

    public boolean isShellIntegrationCommandRunning() {
        return mShellIntegrationSeen && mShellIntegrationCommandRunning;
    }

    /**
     * The closest row above or below {@code fromRow} where a shell prompt starts, or
     * {@link Integer#MIN_VALUE} if there is none. Rows are in the external coordinate system.
     */
    public int findPromptRow(int fromRow, boolean backwards) {
        return mScreen.findRowWithMark(fromRow, TerminalRow.MARK_PROMPT_START, backwards);
    }

    /** The hyperlink pool of this session. Ids come from {@link TerminalRow#getHyperlinkId(int)}. */
    public TerminalHyperlinks getHyperlinks() {
        return mHyperlinks;
    }

    /**
     * The OSC 8 link target of a cell, or null when the cell is not part of a hyperlink.
     *
     * @param row a row in the external coordinate system, so negative for transcript rows.
     */
    public String getHyperlinkUriAt(int row, int column) {
        if (column < 0 || column >= mColumns || row < -mScreen.getActiveTranscriptRows() || row >= mRows)
            return null;
        return mHyperlinks.getUri(mScreen.getHyperlinkIdAt(row, column));
    }

    /**
     * "CSI P_m h" for set or "CSI P_m l" for reset ANSI mode.
     */
    private void doSetMode(boolean newValue) {
        int modeBit = getArg0(0);
        switch(modeBit) {
            case // Set="Insert Mode". Reset="Replace Mode". (IRM).
            4:
                mInsertMode = newValue;
                break;
            case // Normal Linefeed (LNM).
            20:
                unknownParameter(modeBit);
                // http://www.vt100.net/docs/vt510-rm/LNM
                break;
            case 34:
                // Normal cursor visibility - when using TERM=screen, see
                // http://www.gnu.org/software/screen/manual/html_node/Control-Sequences.html
                break;
            default:
                unknownParameter(modeBit);
                break;
        }
    }

    /**
     * NOTE: The parameters of this function respect the {@link #DECSET_BIT_ORIGIN_MODE}. Use
     * {@link #setCursorRowCol(int, int)} for absolute pos.
     */
    private void setCursorPosition(int x, int y) {
        boolean originMode = isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE);
        int effectiveTopMargin = originMode ? mTopMargin : 0;
        int effectiveBottomMargin = originMode ? mBottomMargin : mRows;
        int effectiveLeftMargin = originMode ? mLeftMargin : 0;
        int effectiveRightMargin = originMode ? mRightMargin : mColumns;
        int newRow = Math.max(effectiveTopMargin, Math.min(effectiveTopMargin + y, effectiveBottomMargin - 1));
        int newCol = Math.max(effectiveLeftMargin, Math.min(effectiveLeftMargin + x, effectiveRightMargin - 1));
        setCursorRowCol(newRow, newCol);
    }

    private void scrollDownOneLine() {
        mScrollCounter++;
        mScrollEventCount++;
        long currentStyle = getStyle();
        if (mLeftMargin != 0 || mRightMargin != mColumns) {
            // Horizontal margin: Do not put anything into scroll history, just non-margin part of screen up.
            mScreen.blockCopy(mLeftMargin, mTopMargin + 1, mRightMargin - mLeftMargin, mBottomMargin - mTopMargin - 1, mLeftMargin, mTopMargin);
            // .. and blank bottom row between margins:
            mScreen.blockSet(mLeftMargin, mBottomMargin - 1, mRightMargin - mLeftMargin, 1, ' ', currentStyle);
        } else {
            mScreen.scrollDownOneLine(mTopMargin, mBottomMargin, currentStyle);
        }
    }

    /**
     * Process the next ASCII character of a parameter.
     *
     * <p>You must use the ; character to separate parameters and : to separate sub-parameters.
     *
     * <p>Parameter characters modify the action or interpretation of the sequence. Originally
     * you can use up to 16 parameters per sequence, but following at least xterm and alacritty
     * we use a common space for parameters and sub-parameters, allowing 32 in total.
     *
     * <p>All parameters are unsigned, positive decimal integers, with the most significant
     * digit sent first. Any parameter greater than 9999 (decimal) is set to 9999
     * (decimal). If you do not specify a value, a 0 value is assumed. A 0 value
     * or omitted parameter indicates a default value for the sequence. For most
     * sequences, the default value is 1.
     *
     * <p>References:
     * <a href="https://vt100.net/docs/vt510-rm/chapter4.html#S4.3.3">VT510 Video Terminal Programmer Information: Control Sequences</a>
     * <a href="https://github.com/alacritty/vte/issues/22">alacritty/vte: Implement colon separated CSI parameters</a>
     * */
    private void parseArg(int b) {
        if (b >= '0' && b <= '9') {
            if (mArgIndex < mArgs.length) {
                int oldValue = mArgs[mArgIndex];
                int thisDigit = b - '0';
                int value;
                if (oldValue >= 0) {
                    value = oldValue * 10 + thisDigit;
                } else {
                    value = thisDigit;
                }
                if (value > 9999)
                    value = 9999;
                mArgs[mArgIndex] = value;
            }
            continueSequence(mEscapeState);
        } else if (b == ';' || b == ':') {
            if (mArgIndex + 1 < mArgs.length) {
                mArgIndex++;
                if (b == ':') {
                    mArgsSubParamsBitSet |= 1 << mArgIndex;
                }
            } else {
                logError("Too many parameters when in state: " + mEscapeState);
            }
            continueSequence(mEscapeState);
        } else {
            unknownSequence(b);
        }
    }

    private int getArg0(int defaultValue) {
        return getArg(0, defaultValue, true);
    }

    private int getArg1(int defaultValue) {
        return getArg(1, defaultValue, true);
    }

    private int getArg(int index, int defaultValue, boolean treatZeroAsDefault) {
        int result = mArgs[index];
        if (result < 0 || (result == 0 && treatZeroAsDefault)) {
            result = defaultValue;
        }
        return result;
    }

    private void collectOSCArgs(int b) {
        if (appendStringSequenceCodePoint(b)) {
            updateOscHandling();
            continueSequence(mEscapeState);
        } else {
            unknownSequence(b);
        }
    }

    private void updateOscHandling() {
        if (mOSCOrDeviceControlArgs.length() >= 5 &&
            mOSCOrDeviceControlArgs.substring(0, 5).equals("1337;")) {
            mIgnoreCrLfForOsc = true;
            mOscStringMaxLength = MAX_IMAGE_SEQUENCE_LENGTH;
        } else if (mOSCOrDeviceControlArgs.length() >= 3 &&
            mOSCOrDeviceControlArgs.substring(0, 3).equals("52;")) {
            mOscStringMaxLength = MAX_CLIPBOARD_SEQUENCE_LENGTH;
        }
    }

    private boolean appendStringSequenceCodePoint(int codePoint) {
        int codeUnits = Character.charCount(codePoint);
        if (mOSCOrDeviceControlArgs.length() > mOscStringMaxLength - codeUnits)
            return false;
        mOSCOrDeviceControlArgs.appendCodePoint(codePoint);
        return true;
    }

    private static boolean isCsiState(int state) {
        switch(state) {
            case ESC_CSI:
            case ESC_CSI_QUESTIONMARK:
            case ESC_CSI_DOLLAR:
            case ESC_CSI_BIGGERTHAN:
            case ESC_CSI_BIGGERTHAN_ARGS_SPACE:
            case ESC_CSI_QUESTIONMARK_ARG_DOLLAR:
            case ESC_CSI_ARGS_SPACE:
            case ESC_CSI_ARGS_ASTERIX:
            case ESC_CSI_DOUBLE_QUOTE:
            case ESC_CSI_SINGLE_QUOTE:
            case ESC_CSI_EXCLAMATION:
            case ESC_CSI_UNSUPPORTED_PARAMETER_BYTE:
            case ESC_CSI_UNSUPPORTED_INTERMEDIATE_BYTE:
            case ESC_CSI_EQUAL:
            case ESC_CSI_LESSTHAN:
                return true;
            default:
                return false;
        }
    }

    private void clearStringSequenceArgs() {
        mOSCOrDeviceControlArgs.setLength(0);
        if (mOSCOrDeviceControlArgs.capacity() > MAX_STRING_SEQUENCE_LENGTH * 2)
            mOSCOrDeviceControlArgs.trimToSize();
    }

    private void unimplementedSequence(int b) {
        logError("Unimplemented sequence char '" + (char) b + "' (U+" + String.format("%04x", b) + ")");
        finishSequence();
    }

    private void unknownSequence(int b) {
        logError("Unknown sequence char '" + (char) b + "' (numeric value=" + b + ")");
        finishSequence();
    }

    private void unknownParameter(int parameter) {
        logError("Unknown parameter: " + parameter);
        finishSequence();
    }

    private void logError(String errorType) {
        if (LOG_ESCAPE_SEQUENCES) {
            StringBuilder buf = new StringBuilder();
            buf.append(errorType);
            buf.append(", escapeState=");
            buf.append(mEscapeState);
            boolean firstArg = true;
            if (mArgIndex >= mArgs.length)
                mArgIndex = mArgs.length - 1;
            for (int i = 0; i <= mArgIndex; i++) {
                int value = mArgs[i];
                if (value >= 0) {
                    if (firstArg) {
                        firstArg = false;
                        buf.append(", args={");
                    } else {
                        buf.append(',');
                    }
                    buf.append(value);
                }
            }
            if (!firstArg)
                buf.append('}');
            finishSequenceAndLogError(buf.toString());
        }
    }

    private void finishSequenceAndLogError(String error) {
        if (LOG_ESCAPE_SEQUENCES)
            Logger.logWarn(mClient, LOG_TAG, error);
        finishSequence();
    }

    private void finishSequence() {
        mEscapeState = ESC_NONE;
        mIgnoreCrLfForOsc = false;
        mOscStringMaxLength = MAX_STRING_SEQUENCE_LENGTH;
        mCsiSequenceLength = 0;
        mApcSequenceLength = 0;
        mDcsSequenceLength = 0;
        mDcsSequenceMaxLength = MAX_STRING_SEQUENCE_LENGTH;
        ESC_P_escape = false;
        if (ESC_P_sixel) {
            mScreen.sixelIgnore();
            ESC_P_sixel = false;
        }
        clearStringSequenceArgs();
    }

    /**
     * Send a Unicode code point to the screen.
     *
     * @param codePoint The code point of the character to display
     */
    private void emitCodePoint(int codePoint) {
        mLastEmittedCodePoint = codePoint;
        if (mUseLineDrawingUsesG0 ? mUseLineDrawingG0 : mUseLineDrawingG1) {
            // http://www.vt100.net/docs/vt102-ug/table5-15.html.
            switch(codePoint) {
                case '_':
                    // Blank.
                    codePoint = ' ';
                    break;
                case '`':
                    // Diamond.
                    codePoint = '◆';
                    break;
                case '0':
                    // Solid block;
                    codePoint = '█';
                    break;
                case 'a':
                    // Checker board.
                    codePoint = '▒';
                    break;
                case 'b':
                    // Horizontal tab.
                    codePoint = '␉';
                    break;
                case 'c':
                    // Form feed.
                    codePoint = '␌';
                    break;
                case 'd':
                    // Carriage return.
                    codePoint = '\r';
                    break;
                case 'e':
                    // Linefeed.
                    codePoint = '␊';
                    break;
                case 'f':
                    // Degree.
                    codePoint = '°';
                    break;
                case 'g':
                    // Plus-minus.
                    codePoint = '±';
                    break;
                case 'h':
                    // Newline.
                    codePoint = '\n';
                    break;
                case 'i':
                    // Vertical tab.
                    codePoint = '␋';
                    break;
                case 'j':
                    // Lower right corner.
                    codePoint = '┘';
                    break;
                case 'k':
                    // Upper right corner.
                    codePoint = '┐';
                    break;
                case 'l':
                    // Upper left corner.
                    codePoint = '┌';
                    break;
                case 'm':
                    // Left left corner.
                    codePoint = '└';
                    break;
                case 'n':
                    // Crossing lines.
                    codePoint = '┼';
                    break;
                case 'o':
                    // Horizontal line - scan 1.
                    codePoint = '⎺';
                    break;
                case 'p':
                    // Horizontal line - scan 3.
                    codePoint = '⎻';
                    break;
                case 'q':
                    // Horizontal line - scan 5.
                    codePoint = '─';
                    break;
                case 'r':
                    // Horizontal line - scan 7.
                    codePoint = '⎼';
                    break;
                case 's':
                    // Horizontal line - scan 9.
                    codePoint = '⎽';
                    break;
                case 't':
                    // T facing rightwards.
                    codePoint = '├';
                    break;
                case 'u':
                    // T facing leftwards.
                    codePoint = '┤';
                    break;
                case 'v':
                    // T facing upwards.
                    codePoint = '┴';
                    break;
                case 'w':
                    // T facing downwards.
                    codePoint = '┬';
                    break;
                case 'x':
                    // Vertical line.
                    codePoint = '│';
                    break;
                case 'y':
                    // Less than or equal to.
                    codePoint = '≤';
                    break;
                case 'z':
                    // Greater than or equal to.
                    codePoint = '≥';
                    break;
                case '{':
                    // Pi.
                    codePoint = 'π';
                    break;
                case '|':
                    // Not equal to.
                    codePoint = '≠';
                    break;
                case '}':
                    // UK pound.
                    codePoint = '£';
                    break;
                case '~':
                    // Centered dot.
                    codePoint = '·';
                    break;
            }
        }
        boolean precedingCellIsCurrent = mLastGraphemeColumn >= 0
            && mCursorRow == mLastGraphemeCursorRow
            && mCursorCol == mLastGraphemeCursorCol
            && mAboutToAutoWrap == mLastGraphemeAboutToAutoWrap;
        if (mGraphemeClusterer.accept(codePoint, precedingCellIsCurrent)) {
            mScreen.appendCodePointToCell(mLastGraphemeColumn, mLastGraphemeRow, codePoint);
            if (mGraphemeClusterer.shouldWidenCell()
                && mScreen.widenCell(mLastGraphemeColumn, mLastGraphemeRow)) {
                mCursorCol = Math.min(mCursorCol + 1, mRightMargin - 1);
                mAboutToAutoWrap = mCursorCol == mRightMargin - 1;
                mLastGraphemeCursorCol = mCursorCol;
                mLastGraphemeAboutToAutoWrap = mAboutToAutoWrap;
            }
            return;
        }
        final boolean autoWrap = isDecsetInternalBitSet(DECSET_BIT_AUTOWRAP);
        final int displayWidth = WcWidth.width(codePoint);
        final boolean cursorInLastColumn = mCursorCol == mRightMargin - 1;
        if (autoWrap) {
            if (cursorInLastColumn && ((mAboutToAutoWrap && displayWidth == 1) || displayWidth == 2)) {
                mScreen.setLineWrap(mCursorRow);
                mCursorCol = mLeftMargin;
                if (mCursorRow + 1 < mBottomMargin) {
                    mCursorRow++;
                } else {
                    scrollDownOneLine();
                }
            }
        } else if (cursorInLastColumn && displayWidth == 2) {
            // The behaviour when a wide character is output with cursor in the last column when
            // autowrap is disabled is not obvious - it's ignored here.
            return;
        }
        if (mInsertMode && displayWidth > 0) {
            // Move character to right one space.
            int destCol = mCursorCol + displayWidth;
            if (destCol < mRightMargin)
                mScreen.blockCopy(mCursorCol, mCursorRow, mRightMargin - destCol, 1, destCol, mCursorRow);
        }
        int offsetDueToCombiningChar = ((displayWidth <= 0 && mCursorCol > 0 && !mAboutToAutoWrap) ? 1 : 0);
        int column = mCursorCol - offsetDueToCombiningChar;
        // Fix TerminalRow.setChar() ArrayIndexOutOfBoundsException index=-1 exception reported
        // The offsetDueToCombiningChar would never be 1 if mCursorCol was 0 to get column/index=-1,
        // so was mCursorCol changed after the offsetDueToCombiningChar conditional by another thread?
        // TODO: Check if there are thread synchronization issues with mCursorCol and mCursorRow, possibly causing others bugs too.
        if (column < 0)
            column = 0;
        mScreen.setChar(column, mCursorRow, codePoint, getStyle(), mUnderlineColor, mCurrentHyperlinkId);
        mLastGraphemeRow = mCursorRow;
        mLastGraphemeColumn = column;
        if (autoWrap && displayWidth > 0)
            mAboutToAutoWrap = (mCursorCol == mRightMargin - displayWidth);
        mCursorCol = Math.min(mCursorCol + displayWidth, mRightMargin - 1);
        mLastGraphemeCursorRow = mCursorRow;
        mLastGraphemeCursorCol = mCursorCol;
        mLastGraphemeAboutToAutoWrap = mAboutToAutoWrap;
    }

    private void setCursorRow(int row) {
        mCursorRow = row;
        mAboutToAutoWrap = false;
        resetGraphemeTracking();
    }

    private void setCursorCol(int col) {
        mCursorCol = col;
        mAboutToAutoWrap = false;
        resetGraphemeTracking();
    }

    /**
     * Set the cursor mode, but limit it to margins if {@link #DECSET_BIT_ORIGIN_MODE} is enabled.
     */
    private void setCursorColRespectingOriginMode(int col) {
        setCursorPosition(col, mCursorRow);
    }

    /**
     * TODO: Better name, distinguished from {@link #setCursorPosition(int, int)} by not regarding origin mode.
     */
    private void setCursorRowCol(int row, int col) {
        mCursorRow = Math.max(0, Math.min(row, mRows - 1));
        mCursorCol = Math.max(0, Math.min(col, mColumns - 1));
        mAboutToAutoWrap = false;
        resetGraphemeTracking();
    }

    public int getScrollCounter() {
        return mScrollCounter;
    }

    public void clearScrollCounter() {
        mScrollCounter = 0;
    }

    public boolean isAutoScrollDisabled() {
        return mAutoScrollDisabled;
    }

    public void toggleAutoScrollDisabled() {
        mAutoScrollDisabled = !mAutoScrollDisabled;
    }

    /**
     * Reset terminal state so user can interact with it regardless of present state.
     */
    public void reset() {
        resetGraphemeTracking();
        setCursorStyle();
        mArgIndex = 0;
        mContinueSequence = false;
        mEscapeState = ESC_NONE;
        mInsertMode = false;
        mTopMargin = mLeftMargin = 0;
        mBottomMargin = mRows;
        mRightMargin = mColumns;
        mAboutToAutoWrap = false;
        mForeColor = mSavedStateMain.mSavedForeColor = mSavedStateAlt.mSavedForeColor = TextStyle.COLOR_INDEX_FOREGROUND;
        mBackColor = mSavedStateMain.mSavedBackColor = mSavedStateAlt.mSavedBackColor = TextStyle.COLOR_INDEX_BACKGROUND;
        mUnderlineColor = mSavedStateMain.mSavedUnderlineColor = mSavedStateAlt.mSavedUnderlineColor = TextStyle.DECORATION_COLOR_DEFAULT;
        mUnderlineStyle = mSavedStateMain.mSavedUnderlineStyle = mSavedStateAlt.mSavedUnderlineStyle = TextStyle.UNDERLINE_STYLE_NONE;
        mCurrentHyperlinkId = TerminalHyperlinks.NO_LINK;
        mHyperlinks.clear();
        mLastCommandExitCode = COMMAND_EXIT_CODE_UNKNOWN;
        mShellIntegrationCommandRunning = false;
        mShellIntegrationSeen = false;
        mKeyboardModesMain.reset();
        mKeyboardModesAlt.reset();
        setDefaultTabStops();
        mUseLineDrawingG0 = mUseLineDrawingG1 = false;
        mUseLineDrawingUsesG0 = true;
        mSavedStateMain.mSavedCursorRow = mSavedStateMain.mSavedCursorCol = mSavedStateMain.mSavedEffect = mSavedStateMain.mSavedDecFlags = 0;
        mSavedStateAlt.mSavedCursorRow = mSavedStateAlt.mSavedCursorCol = mSavedStateAlt.mSavedEffect = mSavedStateAlt.mSavedDecFlags = 0;
        mCurrentDecSetFlags = 0;
        // Initial wrap-around is not accurate but makes terminal more useful, especially on a small screen:
        setDecsetinternalBit(DECSET_BIT_AUTOWRAP, true);
        setDecsetinternalBit(DECSET_BIT_CURSOR_ENABLED, true);
        mSavedDecSetFlags = mSavedStateMain.mSavedDecFlags = mSavedStateAlt.mSavedDecFlags = mCurrentDecSetFlags;
        // XXX: Should we set terminal driver back to IUTF8 with termios?
        mUtf8Index = mUtf8ToFollow = 0;
        mColors.reset();
        mSession.onColorsChanged();
        ESC_P_escape = false;
        ESC_P_sixel = false;
        mOscStringMaxLength = MAX_STRING_SEQUENCE_LENGTH;
        mCsiSequenceLength = 0;
        mApcSequenceLength = 0;
        mDcsSequenceLength = 0;
        mDcsSequenceMaxLength = MAX_STRING_SEQUENCE_LENGTH;
        clearStringSequenceArgs();
        mIgnoreCrLfForOsc = false;
        mITermImage = null;
        mKittyGraphics.reset();
        clearExtraCursors();
        mExtraCursorColor.type = mExtraCursorColor.value = 0;
        mExtraCursorTextColor.type = mExtraCursorTextColor.value = 0;
    }

    private void resetGraphemeTracking() {
        mGraphemeClusterer.reset();
        mLastGraphemeRow = mLastGraphemeColumn = -1;
        mLastGraphemeCursorRow = mLastGraphemeCursorCol = -1;
        mLastGraphemeAboutToAutoWrap = false;
    }

    long getKittyGraphicsBytes() {
        return mMainBuffer.getKittyImageBytes() + mAltBuffer.getKittyImageBytes();
    }

    /** Resolve the stored image and virtual placement referenced by a U+10EEEE cell. */
    public boolean getKittyImagePlaceholder(long imageId, long placementId,
                                            KittyImagePlaceholder out) {
        return mKittyGraphics.getPlaceholder(imageId, placementId, out);
    }

    boolean hasKittyVirtualPlacement(long imageId, long placementId) {
        return mKittyGraphics.hasVirtualPlacement(imageId, placementId);
    }

    boolean placeKittyGraphics(Bitmap bitmap, KittyGraphicsProtocol.Command command, long imageId,
                               int row, int col, int cellWidth, int cellHeight, int[] transform) {
        mPlacingKittyGraphics = true;
        try {
            return placeKittyGraphicsLocked(bitmap, command, imageId, row, col, cellWidth, cellHeight,
                transform);
        } finally {
            mPlacingKittyGraphics = false;
        }
    }

    private boolean placeKittyGraphicsLocked(Bitmap bitmap, KittyGraphicsProtocol.Command command,
                                             long imageId, int row, int col, int cellWidth,
                                             int cellHeight, int[] transform) {
        if (command.action == 'p') {
            // A placement command replaces only its own (image, placement) pair; unidentified
            // placements are additive, which is what makes multiple placements per image work.
            if (command.placementId != 0) {
                mScreen.deleteKittyImages((existing, column, cellRow) ->
                    existing.kittyImageId == imageId && existing.kittyPlacementId == command.placementId, true);
            }
        } else if (imageId != 0) {
            // Retransmitting an image replaces it, so its previous placements go with it.
            mMainBuffer.deleteKittyImages(imageId, true);
            mAltBuffer.deleteKittyImages(imageId, true);
        }
        long availableWidth = Math.max(0L, (long) (mColumns - col) * cellWidth);
        long roundedWidth = ((bitmap.getWidth() + cellWidth - 1L) / cellWidth) * cellWidth;
        long placedWidth = Math.min(availableWidth, roundedWidth);
        long placedHeight = ((bitmap.getHeight() + cellHeight - 1L) / cellHeight) * cellHeight;
        long placedBytes = placedWidth * placedHeight * 4L;
        if (placedBytes <= 0 || getKittyGraphicsBytes() + placedBytes > KittyGraphicsProtocol.MAX_DECODED_BYTES)
            return false;
        int[] delta = mScreen.addKittyImage(bitmap, imageId, command.placementId, command.z, row, col,
            cellWidth, cellHeight, transform);
        return delta[0] != 0 || delta[1] != 0;
    }

    /**
     * Supplies the first row on display, negative into the scrollback. The view owns scrolling, so
     * the position is pulled when it is needed rather than pushed from a dozen scroll sites where
     * one missed call would leave an animation stuck.
     */
    public interface TopRowProvider {
        int topRow();
    }

    private TopRowProvider mTopRowProvider;

    public void setTopRowProvider(TopRowProvider provider) {
        mTopRowProvider = provider;
    }

    /**
     * Whether any cell displaying this kitty image is on screen. With no view attached there is
     * nothing to hide behind, so everything counts as visible.
     */
    boolean isKittyImageOnScreen(long imageId) {
        if (mTopRowProvider == null) return true;
        return mScreen.hasKittyImageInRows(imageId, mTopRowProvider.topRow(), mRows);
    }

    /**
     * Set while a placement is being written. Replacing a placement deletes its old cells before
     * it adds the new ones, and adding them can scroll — so without this a sweep landing in that
     * window would see a live animation with nothing displaying it and throw its frames away.
     */
    private boolean mPlacingKittyGraphics;

    /**
     * Report whether this terminal's output is on screen, so kitty animations can be suspended
     * while it is not. Frames and playback position are kept either way; only the ticking stops.
     */
    public void setKittyAnimationsVisible(boolean visible) {
        mKittyGraphics.setAnimationsVisible(visible);
    }

    /** Release every kitty graphics resource, including the pending animation tick. */
    public void shutdownKittyGraphics() {
        mKittyGraphics.shutdown();
    }

    /**
     * Under memory pressure, give up animation and keep the picture: every stored animation drops
     * its frames and rests on the still image it started from. The placements stay, so no cell goes
     * blank and no session is lost.
     */
    public void dropKittyAnimationFrames() {
        mKittyGraphics.dropAllAnimationFrames();
    }

    private void onKittyCellsCollected() {
        if (mPlacingKittyGraphics) return;
        mKittyGraphics.dropFramesOfUnreachableImages();
    }

    /** Whether a U+10EEEE cell survives on either screen, so virtual placements stay reachable. */
    boolean hasAnyKittyPlaceholderCell() {
        return mMainBuffer.hasAnyKittyPlaceholderCell() || mAltBuffer.hasAnyKittyPlaceholderCell();
    }

    /** Live placements of one stored kitty image on both screens, for animation frame flips. */
    java.util.List<TerminalBitmap> kittyPlacementsFor(long imageId) {
        java.util.List<TerminalBitmap> result = new java.util.ArrayList<>();
        mMainBuffer.collectKittyPlacements(imageId, result);
        mAltBuffer.collectKittyPlacements(imageId, result);
        return result;
    }

    /** Delete kitty placement cells the filter matches on the current screen. */
    int deleteKittyPlacements(TerminalBuffer.KittyPlacementFilter filter, boolean includeScrollback) {
        return mScreen.deleteKittyImages(filter, includeScrollback);
    }

    void advanceKittyGraphicsCursor(KittyGraphicsProtocol.Command command, int imageWidth, int imageHeight,
                                    int row, int col, int cellWidth, int cellHeight) {
        if (command.noCursorMovement) return;
        int columns = Math.min(mColumns - col, (imageWidth + cellWidth - 1) / cellWidth);
        int rows = (imageHeight + cellHeight - 1) / cellHeight;
        int nextColumn = col + columns;
        if (nextColumn < mColumns) {
            rows--;
        } else {
            nextColumn = 0;
        }
        setCursorRowCol(row, col);
        while (rows-- > 0) doLinefeed();
        setCursorCol(nextColumn);
    }

    /** Remove one image's placements from both screens, as a retransmission replacement requires. */
    void deleteKittyImageEverywhere(long imageId) {
        mMainBuffer.deleteKittyImages(imageId, true);
        mAltBuffer.deleteKittyImages(imageId, true);
    }

    void deleteVisibleKittyGraphics() {
        mScreen.deleteKittyImages(-1, false);
    }

    void deleteAllKittyGraphics() {
        mMainBuffer.deleteKittyImages(-1, true);
        mAltBuffer.deleteKittyImages(-1, true);
    }

    public String getSelectedText(int x1, int y1, int x2, int y2) {
        return mScreen.getSelectedText(x1, y1, x2, y2);
    }

    /** used to read aloud the character under the cursor in A11Y */
    public Character getChar(int x, int y) {
        return mScreen.getChar(x, y);
    }

    /** Get the terminal session's title (null if not set). */
    public String getTitle() {
        return mTitle;
    }

    /**
     * Change the terminal session's title.
     */
    private void setTitle(String newTitle) {
        String oldTitle = mTitle;
        mTitle = newTitle;
        if (!Objects.equals(oldTitle, newTitle)) {
            mSession.titleChanged(oldTitle, newTitle);
        }
    }

    /**
     * If DECSET 2004 is set, prefix paste with "\033[200~" and suffix with "\033[201~".
     */
    public void paste(String text) {
        // First: Always remove escape key and C1 control characters [0x80,0x9F]:
        text = text.replaceAll("(\u001B|[\u0080-\u009F])", "");
        // Second: Replace all newlines (\n) or CRLF (\r\n) with carriage returns (\r).
        text = text.replaceAll("\r?\n", "\r");
        // Then: Implement bracketed paste mode if enabled:
        boolean bracketed = isDecsetInternalBitSet(DECSET_BIT_BRACKETED_PASTE_MODE);
        if (bracketed)
            mSession.write("\033[200~");
        mSession.write(text);
        if (bracketed)
            mSession.write("\033[201~");
    }

    /**
     * http://www.vt100.net/docs/vt510-rm/DECSC
     */
    static final class SavedScreenState {

        /**
         * Saved state of the cursor position, Used to implement the save/restore cursor position escape sequences.
         */
        int mSavedCursorRow, mSavedCursorCol;

        int mSavedEffect, mSavedForeColor, mSavedBackColor;

        int mSavedUnderlineStyle;

        int mSavedUnderlineColor = TextStyle.DECORATION_COLOR_DEFAULT;

        int mSavedDecFlags;

        boolean mUseLineDrawingG0, mUseLineDrawingG1, mUseLineDrawingUsesG0 = true;
    }

    @Override
    public String toString() {
        return "TerminalEmulator[size=" + mScreen.mColumns + "x" + mScreen.mScreenRows + ", margins={" + mTopMargin + "," + mRightMargin + "," + mBottomMargin + "," + mLeftMargin + "}]";
    }
}
