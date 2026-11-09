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
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.view.KeyboardUtils;
import com.termux.terminal.TerminalSession;

public class TerminalToolbarViewPager {

    public static class PageAdapter extends PagerAdapter {

        final TermuxActivity mActivity;

        String mSavedTextInput;

        /** The text input page built ahead ({@link #prepareTextInputPage}); null once taken. */
        @Nullable private View mPreparedTextInput;

        public PageAdapter(TermuxActivity activity, String savedTextInput) {
            this.mActivity = activity;
            this.mSavedTextInput = savedTextInput;
        }

        @Override
        public int getCount() {
            // Every configured key page, then the text input page last. A second key page only
            // exists when "extra-keys2" is non-empty, so a user who does not want one writes
            // "extra-keys2=[]" (or removes the page in the editor) and gets the old two-page pager.
            return mActivity.getExtraKeysPageCount() + 1;
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            // Pages are rebuilt wholesale after an edit; nothing survives a notifyDataSetChanged.
            return POSITION_NONE;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup collection, int position) {
            View layout;
            int keyPages = mActivity.getExtraKeysPageCount();
            if (position < keyPages) {
                // The page is the activity's own key view, lent here: the same instance the
                // portable host takes when the place stands the keys on another edge, so a latched
                // modifier and the picked colours survive the move either way.
                ExtraKeysView extraKeysView = mActivity.lendExtraKeysPage(position);
                extraKeysView.setVertical(false);
                layout = extraKeysView;
                extraKeysView.setExtraKeysViewClient(mActivity.getTermuxTerminalExtraKeys(position));
                extraKeysView.setButtonTextAllCaps(mActivity.getProperties().shouldExtraKeysTextBeAllCaps());
                // A sideways drag that starts on a key turns one page either way: left to the next
                // key page (the text input after the last), right back to the previous one.
                extraKeysView.setToolbarPageSwipeListener(direction -> {
                    ViewPager pager = mActivity.getTerminalToolbarViewPager();
                    int target = pager.getCurrentItem() + direction;
                    if (target >= 0 && target <= mActivity.getExtraKeysPageCount())
                        pager.setCurrentItem(target, true);
                });
                extraKeysView.setPageIndicator(position, keyPages);
                mActivity.setExtraKeysView(extraKeysView, position);
                extraKeysView.reload(
                    mActivity.getTermuxTerminalExtraKeys(position).getExtraKeysInfo(),
                    mActivity.getTerminalToolbarDefaultHeight());
            } else {
                // Built ahead when the launcher was idle (prepareTextInputPage), so the first
                // page change that shows the toolbar does not inflate and measure it.
                View prepared = mPreparedTextInput;
                mPreparedTextInput = null;
                layout = prepared != null && prepared.getParent() == null ? prepared
                    : buildTextInputPage(collection);
                if (mSavedTextInput != null) {
                    final EditText editText = layout.findViewById(R.id.terminal_toolbar_text_input);
                    editText.setText(mSavedTextInput);
                    mSavedTextInput = null;
                }
            }
            collection.addView(layout);
            return layout;
        }

        /**
         * Builds the text input page, its paste button and its send action wired, for
         * {@code parent}, the pager; not added to it.
         */
        @NonNull
        private View buildTextInputPage(@NonNull ViewGroup parent) {
            View layout = LayoutInflater.from(mActivity).inflate(
                R.layout.view_terminal_toolbar_text_input, parent, false);

            final Button button = layout.findViewById(R.id.terminal_toolbar_text_input_button);
            button.setText("\u2398");
            button.setOnClickListener(v ->
                TermuxTerminalExtraKeys.pasteWhereTheKeyboardPastes(mActivity));
            button.setOnLongClickListener(v -> {
                ViewPager pager = mActivity.getTerminalToolbarViewPager();
                pager.setCurrentItem(0, true);
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
         * Builds the text input page ahead of the first time the pager is laid out, and measures
         * it once at {@code widthPx} x {@code heightPx}: on pong the first page change that showed
         * the toolbar inflated the Material text field inside a 32 ms measure. Does nothing once the
         * page exists or is already prepared; the one small view is kept until the pager takes it.
         */
        public void prepareTextInputPage(@NonNull ViewGroup pager, int widthPx, int heightPx) {
            if (mPreparedTextInput != null
                || pager.findViewById(R.id.terminal_toolbar_text_input) != null) return;
            Trace.beginSection("Toolbar.prepareTextInput");
            try {
                View layout = buildTextInputPage(pager);
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

        @Override
        public void destroyItem(@NonNull ViewGroup collection, int position, @NonNull Object view) {
            // A key page may already have been lent to the portable host, in which case it is no
            // longer a child here and this is a no-op — which is exactly what is wanted: the view
            // belongs to the activity, not to the pager.
            collection.removeView((View) view);
        }
    }

    public static class OnPageChangeListener extends ViewPager.SimpleOnPageChangeListener {

        final TermuxActivity mActivity;

        final ViewPager mTerminalToolbarViewPager;

        public OnPageChangeListener(TermuxActivity activity, ViewPager viewPager) {
            this.mActivity = activity;
            this.mTerminalToolbarViewPager = viewPager;
        }

        @Override
        public void onPageSelected(int position) {
            if (position < mActivity.getExtraKeysPageCount()) {
                mActivity.endTerminalToolbarExternalTextInput();
                mActivity.getTerminalView().requestFocus();
            } else {
                final EditText editText = mTerminalToolbarViewPager.findViewById(R.id.terminal_toolbar_text_input);
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
}
