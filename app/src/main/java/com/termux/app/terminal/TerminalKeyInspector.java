package com.termux.app.terminal;

import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;
import com.termux.terminal.KittyKeyEncoder;
import com.termux.view.TerminalView;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * An in-app equivalent of kitty's {@code kitten show-key}: an overlay that reports, for each key
 * event, what Android delivered, which app binding claimed it, and the exact bytes that reached the
 * shell.
 * <p>
 * Answering "why did this key do that" needs all three at once. Android's own key logging shows the
 * event but not the encoding; a terminal side probe such as {@code cat -v} shows the bytes but not
 * the event or the binding, and cannot show a stroke the app swallowed before the shell ever saw it.
 * </p>
 * <p>
 * The panel is deliberately <b>not focusable</b>. A dialog would take input focus, the terminal would
 * stop receiving key events, and the inspector would have nothing to inspect.
 * </p>
 */
public final class TerminalKeyInspector implements TerminalView.KeyInputProbe {

    private static final String LOG_TAG = "TerminalKeyInspector";

    /** Rows kept in the panel. Old ones scroll off; this is a live probe, not a log. */
    private static final int MAX_ROWS = 24;

    @Nullable
    private static TerminalKeyInspector sInstance;

    private final TermuxActivity mActivity;

    private final View mPanel;

    private final TextView mOutput;

    private final Deque<String> mRows = new ArrayDeque<>();

    /** The row being assembled for the key event currently being dispatched. */
    @Nullable
    private StringBuilder mPending;

    /** Whether {@link #mPending} has had bytes reported for it. */
    private boolean mPendingWrote;

    private TerminalKeyInspector(@NonNull TermuxActivity activity, @NonNull View panel) {
        mActivity = activity;
        mPanel = panel;
        mOutput = panel.findViewById(R.id.key_inspector_output);
    }

    /** The open inspector, or null when it is closed. */
    @Nullable
    public static TerminalKeyInspector active() {
        return sInstance;
    }

    public static boolean isOpen() {
        return sInstance != null;
    }

    /** Open the inspector, or close it if it is already open. Must run on the main thread. */
    public static boolean toggle(@NonNull TermuxActivity activity) {
        if (sInstance != null) {
            close();
            return false;
        }
        FrameLayout host = activity.findViewById(R.id.terminal_root_container);
        if (host == null) {
            Logger.logWarn(LOG_TAG, "No terminal container to attach the key inspector to");
            return false;
        }
        View panel = activity.getLayoutInflater().inflate(R.layout.key_inspector, host, false);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        host.addView(panel, params);
        // Nothing in the panel may take focus away from the terminal, or there are no key events left
        // to report. The close button is clickable but not focusable for the same reason.
        panel.setFocusable(false);
        panel.setFocusableInTouchMode(false);
        TerminalKeyInspector inspector = new TerminalKeyInspector(activity, panel);
        panel.findViewById(R.id.key_inspector_close).setOnClickListener(v -> close());
        sInstance = inspector;
        inspector.attachProbes();
        inspector.mRows.addFirst(activity.getString(R.string.key_inspector_hint));
        inspector.render();
        return true;
    }

    public static void close() {
        TerminalKeyInspector inspector = sInstance;
        if (inspector == null)
            return;
        sInstance = null;
        inspector.detachProbes();
        View parent = (View) inspector.mPanel.getParent();
        if (parent instanceof FrameLayout)
            ((FrameLayout) parent).removeView(inspector.mPanel);
    }

    /** Attach to every pane, since a pane switch must not silently stop the reporting. */
    private void attachProbes() {
        for (TerminalView view : visibleViews()) view.setKeyInputProbe(this);
    }

    private void detachProbes() {
        for (TerminalView view : visibleViews()) view.setKeyInputProbe(null);
    }

    /** Called for a pane created while the inspector is open. */
    public static void attachTo(@Nullable TerminalView view) {
        if (view != null && sInstance != null)
            view.setKeyInputProbe(sInstance);
    }

    @NonNull
    private Iterable<TerminalView> visibleViews() {
        java.util.List<TerminalView> views = new java.util.ArrayList<>();
        if (mActivity.getPaneController() != null)
            views.addAll(mActivity.getPaneController().getVisiblePaneViews());
        TerminalView current = mActivity.getTerminalView();
        if (current != null && !views.contains(current))
            views.add(current);
        return views;
    }

    /**
     * Start a row for a key event. Called before the event is dispatched, so that the binding and the
     * bytes reported afterwards land on the same row.
     */
    public void recordEvent(@NonNull KeyEvent event, boolean down) {
        flushPending();
        StringBuilder row = new StringBuilder(96);
        row.append(down ? (event.getRepeatCount() > 0 ? "repeat " : "press  ") : "release");
        row.append(' ').append(KeyEvent.keyCodeToString(event.getKeyCode()));
        row.append(" code=").append(event.getKeyCode());
        row.append(" scan=").append(event.getScanCode());
        int unicode = event.getUnicodeChar(event.getMetaState());
        if (unicode > 0)
            row.append(" text=").append(describeCodePoint(unicode));
        String modifiers = describeModifiers(event);
        if (!modifiers.isEmpty())
            row.append(' ').append(modifiers);
        row.append(" dev=").append(event.getDeviceId());
        int flags = keyboardFlags();
        if (flags != 0)
            row.append(" kitty=").append(flags);
        mPending = row;
        mPendingWrote = false;
    }

    /** Report which registry binding claimed the stroke, or that an unbound one was swallowed. */
    public void recordBinding(@Nullable String stroke, @Nullable String toolName) {
        if (mPending == null)
            return;
        if (toolName == null) {
            mPending.append("\n    binding: none, swallowed");
        } else {
            mPending.append("\n    binding: ").append(stroke).append(" -> ").append(toolName);
        }
    }

    @Override
    public void onKeyBytesWritten(String encoder, String bytes) {
        if (mPending == null) {
            // Input with no key event of its own, such as text committed by an IME.
            mPending = new StringBuilder("input  (no key event)");
            mPendingWrote = false;
        }
        mPending.append("\n    ").append(encoder).append(": ").append(caret(bytes));
        mPendingWrote = true;
    }

    /** Finish the row for the previous event and show it. */
    public void flushPending() {
        StringBuilder pending = mPending;
        mPending = null;
        if (pending == null)
            return;
        if (!mPendingWrote)
            pending.append("\n    nothing written to the shell");
        mRows.addFirst(pending.toString());
        while (mRows.size() > MAX_ROWS) mRows.removeLast();
        render();
    }

    private void render() {
        StringBuilder text = new StringBuilder();
        for (String row : mRows) {
            if (text.length() > 0)
                text.append('\n');
            text.append(row);
        }
        mOutput.setText(text.toString());
    }

    private int keyboardFlags() {
        TerminalView view = mActivity.getTerminalView();
        return (view == null || view.mEmulator == null) ? 0 : view.mEmulator.getKeyboardFlags();
    }

    private static String describeModifiers(@NonNull KeyEvent event) {
        StringBuilder out = new StringBuilder();
        if (event.isCtrlPressed())
            out.append("ctrl+");
        if (event.isAltPressed())
            out.append("alt+");
        if (event.isShiftPressed())
            out.append("shift+");
        if (event.isMetaPressed())
            out.append("super+");
        if (event.isCapsLockOn())
            out.append("caps+");
        if (event.isNumLockOn())
            out.append("num+");
        if (out.length() == 0)
            return "";
        // The protocol's own modifier value, so a CSI u code below can be checked against it.
        int protocolModifiers = 0;
        if (event.isShiftPressed())
            protocolModifiers |= KittyKeyEncoder.MOD_SHIFT;
        if (event.isAltPressed())
            protocolModifiers |= KittyKeyEncoder.MOD_ALT;
        if (event.isCtrlPressed())
            protocolModifiers |= KittyKeyEncoder.MOD_CTRL;
        if (event.isMetaPressed())
            protocolModifiers |= KittyKeyEncoder.MOD_SUPER;
        if (event.isCapsLockOn())
            protocolModifiers |= KittyKeyEncoder.MOD_CAPS_LOCK;
        if (event.isNumLockOn())
            protocolModifiers |= KittyKeyEncoder.MOD_NUM_LOCK;
        out.setLength(out.length() - 1);
        return out + "(" + (1 + protocolModifiers) + ")";
    }

    private static String describeCodePoint(int codePoint) {
        if (codePoint < 0x20 || codePoint == 0x7f)
            return caret(new String(Character.toChars(codePoint)));
        return "'" + new String(Character.toChars(codePoint)) + "'"
            + String.format(Locale.US, " U+%04X", codePoint);
    }

    /** Render bytes the way {@code cat -v} does, so they can be compared with a terminal side probe. */
    public static String caret(@Nullable String bytes) {
        if (bytes == null || bytes.isEmpty())
            return "(nothing)";
        StringBuilder out = new StringBuilder(bytes.length() + 8);
        for (int i = 0; i < bytes.length(); i++) {
            char c = bytes.charAt(i);
            if (c == 0x7f) {
                out.append("^?");
            } else if (c < 0x20) {
                out.append('^').append((char) (c + '@'));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
