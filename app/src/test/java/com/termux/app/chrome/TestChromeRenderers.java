package com.termux.app.chrome;

import android.content.Context;

import androidx.annotation.NonNull;

/** A chrome renderer over fake surfaces, for tests outside this package that need a live one. */
public final class TestChromeRenderers {

    private TestChromeRenderers() {}

    @NonNull
    public static ChromeRenderer overFakeSurfaces(@NonNull Context context) {
        return new ChromeRenderer(new FakeChromeSurfaces(context), null);
    }
}
