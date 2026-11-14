package com.termux.app.terminal.io;

import android.os.Trace;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.terminal.extrakeys.ExtraKeysBarView;
import com.termux.shared.view.KeyboardUtils;
import com.termux.terminal.TerminalSession;

/**
 * The toolbar's text input page, and what the activity does when the bar lands on a page. The
 * key pages and their paging belong to {@link ExtraKeysBarView}.
 */
public class TerminalToolbarPages {

    final TermuxActivity mActivity;

    String mSavedTextInput;

    /** The text input page built ahead ({@link #prepareTextInputPage}); null once taken. */
    @Nullable private View mPreparedTextInput;

    public TerminalToolbarPages(TermuxActivity activity, String savedTextInput) {
        this.mActivity = activity;
        this.mSavedTextInput = savedTextInput;
    }

    /**
     * The text input page for {@code bar}: the page built ahead when there is one, else built now,
     * with the saved text put back once.
     */
    @NonNull
    public View takeTextInputPage(@NonNull ViewGroup bar) {
        // Built ahead when the launcher was idle (prepareTextInputPage), so the first page change
        // that shows the toolbar does not inflate and measure it.
        View prepared = mPreparedTextInput;
        mPreparedTextInput = null;
        View layout = prepared != null && prepared.getParent() == null ? prepared
            : buildTextInputPage(bar);
        if (mSavedTextInput != null) {
            final EditText editText = layout.findViewById(R.id.terminal_toolbar_text_input);
            editText.setText(mSavedTextInput);
            mSavedTextInput = null;
        }
        return layout;
    }

    /**
     * Builds the text input page, its paste button and its send action wired, for
     * {@code parent}, the bar; not added to it.
     */
    @NonNull
    private View buildTextInputPage(@NonNull ViewGroup parent) {
        View layout = LayoutInflater.from(mActivity).inflate(
            R.layout.view_terminal_toolbar_text_input, parent, false);

        final Button button = layout.findViewById(R.id.terminal_toolbar_text_input_button);
        button.setText("⎘");
        button.setOnClickListener(v ->
            TermuxTerminalExtraKeys.pasteWhereTheKeyboardPastes(mActivity));
        button.setOnLongClickListener(v -> {
            ExtraKeysBarView bar = mActivity.getTerminalToolbarViewPager();
            bar.setCurrentItem(0, true);
            return true;
        });

        final EditText editText = layout.findViewById(R.id.terminal_toolbar_text_input);
        editText.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mActivity.beginTerminalToolbarExternalTextInput(editText);
            }
            return false;
        });
        editText.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                mActivity.beginTerminalToolbarExternalTextInput(editText);
            } else if (mActivity.hasWindowFocus()) {
                mActivity.endTerminalToolbarExternalTextInput();
            }
            // Focus lost while the window itself is unfocused is lifecycle churn (screen
            // off, keyguard) — ending external input there suppresses an IME that cannot
            // be hidden yet, stranding it on screen. onResume restores the state instead.
        });
        editText.setOnEditorActionListener((v, actionId, event) -> {
            TerminalSession session = mActivity.getCurrentSession();
            if (session != null) {
                if (session.isRunning()) {
                    String textToSend = editText.getText().toString();
                    if (textToSend.length() == 0)
                        textToSend = "\r";
                    session.write(textToSend);
                } else {
                    mActivity.getTermuxTerminalSessionClient().removeFinishedSession(session);
                }
                editText.setText("");
            }
            return true;
        });
        return layout;
    }

    /**
     * Builds the text input page ahead of the first time the bar is laid out, and measures it once
     * at {@code widthPx} x {@code heightPx}: on pong the first page change that showed the toolbar
     * inflated the Material text field inside a 32 ms measure. Does nothing once the page exists
     * or is already prepared; the one small view is kept until the bar takes it.
     */
    public void prepareTextInputPage(@NonNull ViewGroup bar, int widthPx, int heightPx) {
        if (mPreparedTextInput != null
            || bar.findViewById(R.id.terminal_toolbar_text_input) != null) return;
        Trace.beginSection("Toolbar.prepareTextInput");
        try {
            View layout = buildTextInputPage(bar);
            if (widthPx > 0 && heightPx > 0) {
                layout.measure(
                    View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY));
            }
            mPreparedTextInput = layout;
        } finally {
            Trace.endSection();
        }
    }

    /** The bar landed on {@code position}: the key pages hand the terminal focus, the text input takes it. */
    public void onPageSelected(@NonNull ExtraKeysBarView bar, int position) {
        if (position < mActivity.getExtraKeysPageCount()) {
            mActivity.endTerminalToolbarExternalTextInput();
            mActivity.getTerminalView().requestFocus();
        } else {
            final EditText editText = bar.findViewById(R.id.terminal_toolbar_text_input);
            if (editText != null) {
                editText.requestFocus();
                editText.postDelayed(() -> {
                    if (mActivity.isInAppKeyboardEnabled()) {
                        mActivity.beginTerminalToolbarExternalTextInput(editText);
                        return;
                    }
                    mActivity.onSystemImeRequested();
                    KeyboardUtils.showSoftKeyboard(mActivity, editText);
                }, 120);
            }
        }
    }
}
