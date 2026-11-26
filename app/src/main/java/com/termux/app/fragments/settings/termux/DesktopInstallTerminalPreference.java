package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

/**
 * The small watch-only terminal under a Desktops row while its install runs or after it failed.
 * It owns its layout (an exemption in {@code SettingsLayoutUtils}); the page hands it the one
 * {@link WatchOnlyTerminal} it keeps, which survives the row being recycled.
 */
public final class DesktopInstallTerminalPreference extends Preference {
    @Nullable private WatchOnlyTerminal terminal;

    public DesktopInstallTerminalPreference(@NonNull Context context) {
        super(context);
        setLayoutResource(R.layout.preference_desktop_install_terminal);
        setIconSpaceReserved(false);
        setSelectable(false);
    }

    public void setTerminal(@NonNull WatchOnlyTerminal terminal) {
        this.terminal = terminal;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        FrameLayout host = (FrameLayout) holder.findViewById(R.id.desktop_install_terminal_host);
        WatchOnlyTerminal view = terminal;
        if (host == null || view == null) return;
        if (view.getParent() == host) return;
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        host.removeAllViews();
        host.addView(view, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
}
