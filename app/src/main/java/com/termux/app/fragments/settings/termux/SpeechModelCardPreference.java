package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

/**
 * One installed speech model in the Keyboard's speech model picker (D5): a selectable card with
 * radio semantics, the plain name over "engine · size · window", an "In use" pill on the chosen
 * one, and a "Change window" pill when the model has another window to switch to. Tapping the
 * card is the preference click; the owning screen makes the card it lands on the one in use.
 */
public final class SpeechModelCardPreference extends Preference {
    private boolean chosen;
    @NonNull private CharSequence status = "";
    @NonNull private CharSequence windowAction = "";
    @Nullable private View.OnClickListener windowListener;

    public SpeechModelCardPreference(@NonNull Context context) {
        super(context);
        setLayoutResource(R.layout.preference_speech_model_card);
        setIconSpaceReserved(false);
        setPersistent(false);
    }

    public boolean isChosen() {
        return chosen;
    }

    // Each setter rebinds only on a real change: the screen refreshes every card whenever a
    // download changes status, and a no-op rebind still repaints the row.

    public void setChosen(boolean chosen) {
        if (this.chosen == chosen) return;
        this.chosen = chosen;
        notifyChanged();
    }

    /** A line under the summary (a window switch on its way), or empty for none. */
    public void setStatus(@Nullable CharSequence status) {
        CharSequence value = status == null ? "" : status;
        if (TextUtils.equals(this.status, value)) return;
        this.status = value;
        notifyChanged();
    }

    public void setWindowAction(@Nullable CharSequence text, @Nullable View.OnClickListener listener) {
        CharSequence value = text == null ? "" : text;
        windowListener = listener;
        if (TextUtils.equals(windowAction, value)) return;
        windowAction = value;
        notifyChanged();
    }

    @NonNull
    public CharSequence getWindowAction() {
        return windowAction;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        Context context = getContext();
        View shell = holder.findViewById(R.id.speech_card_shell);
        if (shell != null) shell.setBackgroundResource(chosen ? R.drawable.tai_centre_shell_selected : R.drawable.tai_centre_shell);

        View radioView = holder.findViewById(R.id.speech_card_radio);
        if (radioView instanceof RadioButton) {
            RadioButton radio = (RadioButton) radioView;
            radio.setChecked(chosen);
            radio.setButtonTintList(ColorStateList.valueOf(TaiModelCentreAdapter.color(context, chosen
                ? com.termux.shared.R.attr.termuxColorPrimary : com.termux.shared.R.attr.termuxColorOnSurfaceVariant)));
        }

        View pillView = holder.findViewById(R.id.speech_card_pill);
        if (pillView instanceof TextView) {
            TextView pill = (TextView) pillView;
            pill.setVisibility(chosen ? View.VISIBLE : View.GONE);
            TaiModelCentreAdapter.tonePill(pill, TaiModelCentreRows.Tone.ACCENT);
        }

        View statusView = holder.findViewById(R.id.speech_card_status);
        if (statusView instanceof TextView) {
            ((TextView) statusView).setText(status);
            statusView.setVisibility(status.length() == 0 ? View.GONE : View.VISIBLE);
        }

        View windowView = holder.findViewById(R.id.speech_card_window);
        if (windowView instanceof TextView) {
            TextView window = (TextView) windowView;
            window.setText(windowAction);
            window.setVisibility(windowAction.length() == 0 ? View.GONE : View.VISIBLE);
            TaiModelCentreAdapter.ghostPill(window);
            // A stable trampoline: the listener in the field is read at tap time, so swapping it
            // on a refresh never needs a rebind.
            window.setOnClickListener(view -> {
                View.OnClickListener current = windowListener;
                if (current != null) current.onClick(view);
            });
        }

        // The card is one radio item to a screen reader: "Base · English, radio button, checked".
        holder.itemView.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(RadioButton.class.getName());
                info.setCheckable(true);
                info.setChecked(chosen);
            }
        });
    }
}
