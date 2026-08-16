package com.termux.app.terminal.io;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.view.KeyboardUtils;
import com.termux.terminal.TerminalSession;

public class TerminalToolbarViewPager {

    public static class PageAdapter extends PagerAdapter {

        final TermuxActivity mActivity;

        String mSavedTextInput;

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
            LayoutInflater inflater = LayoutInflater.from(mActivity);
            View layout;
            int keyPages = mActivity.getExtraKeysPageCount();
            if (position < keyPages) {
                layout = inflater.inflate(R.layout.view_terminal_toolbar_extra_keys, collection, false);
                ExtraKeysView extraKeysView = (ExtraKeysView) layout;
                extraKeysView.setExtraKeysViewClient(mActivity.getTermuxTerminalExtraKeys(position));
                extraKeysView.setButtonTextAllCaps(mActivity.getProperties().shouldExtraKeysTextBeAllCaps());
                // Left swipe from the last key page reaches the text input; from an earlier one it
                // is just the next key page, which the pager already handles.
                final int textInputPage = keyPages;
                extraKeysView.setToolbarTextInputSwipeListener(() ->
                    mActivity.getTerminalToolbarViewPager().setCurrentItem(textInputPage, true));
                extraKeysView.setPageIndicator(position, keyPages);
                mActivity.setExtraKeysView(extraKeysView, position);
                extraKeysView.reload(
                    mActivity.getTermuxTerminalExtraKeys(position).getExtraKeysInfo(),
                    mActivity.getTerminalToolbarDefaultHeight());
            } else {
                layout = inflater.inflate(R.layout.view_terminal_toolbar_text_input, collection, false);

                final Button button = layout.findViewById(R.id.terminal_toolbar_text_input_button);
                button.setText("\u2398");
                button.setOnClickListener(v -> {
                    mActivity.getTermuxTerminalSessionClient().onPasteTextFromClipboard(null);
                });
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
                    } else {
                        mActivity.endTerminalToolbarExternalTextInput();
                    }
                });
                if (mSavedTextInput != null) {
                    editText.setText(mSavedTextInput);
                    mSavedTextInput = null;
                }
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
            }
            collection.addView(layout);
            return layout;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup collection, int position, @NonNull Object view) {
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
