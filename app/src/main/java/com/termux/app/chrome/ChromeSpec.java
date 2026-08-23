package com.termux.app.chrome;

/**
 * Immutable snapshot of everything the accessory chrome renders from: which dock rows are live,
 * whether the in-app keyboard is up and how tall, and the tuning values (opacity, blur radius)
 * the surfaces are built with.
 *
 * <p>Promoted from {@code TermuxActivity.AccessoryRenderState}. It is derived, never stored: one
 * is built per render pass and handed to the apply pass, so no two surfaces in a frame can read
 * different states.</p>
 */
public final class ChromeSpec {

    public final boolean toolbarShown;
    public final boolean keyboardShown;
    public final int keyboardHeight;
    public final boolean blurEnabled;
    public final boolean appsRowEnabled;
    public final boolean azRowEnabled;
    public final boolean extraKeysRowEnabled;
    public final float barAlpha;
    public final int blurRadiusDp;

    public ChromeSpec(boolean toolbarShown, boolean keyboardShown, int keyboardHeight,
                      boolean blurEnabled, boolean appsRowEnabled, boolean azRowEnabled,
                      boolean extraKeysRowEnabled, float barAlpha, int blurRadiusDp) {
        this.toolbarShown = toolbarShown;
        this.keyboardShown = keyboardShown;
        this.keyboardHeight = Math.max(0, keyboardHeight);
        this.blurEnabled = blurEnabled;
        this.appsRowEnabled = appsRowEnabled;
        this.azRowEnabled = azRowEnabled;
        this.extraKeysRowEnabled = extraKeysRowEnabled;
        this.barAlpha = barAlpha;
        this.blurRadiusDp = blurRadiusDp;
    }
}
