package com.termux.app.launcher.paging;

import androidx.annotation.NonNull;

/** Told the apps row's page ticks each time they are published. */
public interface PageTicksListener {
    void onPageTicks(@NonNull PageTicks ticks);
}
