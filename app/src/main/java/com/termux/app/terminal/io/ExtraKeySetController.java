package com.termux.app.terminal.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.terminal.extrakeys.ExtraKeyModifiers;
import com.termux.app.terminal.extrakeys.ExtraKeysBarView;
import com.termux.app.terminal.extrakeys.ExtraKeysPageView;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.SpecialButton;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import java.util.HashSet;
import java.util.Set;

/**
 * The one owner of which keys the bar and the edge column show: it follows the target the keys go
 * to, resolves that target's set, and hands it to the bar and the edge page.
 */
public final class ExtraKeySetController {

    public interface Host {
        @NonNull PaneWallPage settledPlace();

        boolean homeFloatFocused();

        boolean homeFloatIsDisplay();

        /** The stored value of a property line as written, or null when absent. */
        @Nullable String rawLine(@NonNull String key);

        /** One page of keys from its value; {@code fallback} is the value a bad row falls back to. */
        @Nullable ExtraKeysInfo parsePage(@NonNull String value, @NonNull String fallback, int page);

        @Nullable ExtraKeysBarView bar();

        /** The column or top page when attached and shown, else null. */
        @Nullable ExtraKeysPageView edgePage();

        /** The bar is attached, visible, shown and laid out. */
        boolean barShown();

        @NonNull ExtraKeyModifiers modifiers();

        /** The edge page's rows or scale changed: the activity sizes the column once. */
        void onEdgeKeysChanged();
    }

    @NonNull private final Host mHost;
    @Nullable private ExtraKeysTarget mTarget;
    @Nullable private ExtraKeySetResolver.Resolved mResolved;
    @NonNull private ExtraKeysInfo[] mPages = new ExtraKeysInfo[0];
    private float mScale = 1f;
    private int mEdgeRows = -1;
    private float mEdgeScale = -1f;
    @Nullable private ExtraKeysBarView.Transition mLastTransition;

    public ExtraKeySetController(@NonNull Host host) {
        mHost = host;
    }

    /** The last applied pages: one entry (null if it failed to parse) or two once applied. */
    @NonNull
    public ExtraKeysInfo[] pages() {
        return mPages;
    }

    public float scale() {
        return mScale;
    }

    @Nullable
    public ExtraKeysTarget target() {
        return mTarget;
    }

    /** Where the extra keys go for the place and float focus as they are now, applied or not. */
    @NonNull
    public ExtraKeysTarget targetNow() {
        return ExtraKeySetResolver.targetFor(mHost.settledPlace(), mHost.homeFloatFocused(),
            mHost.homeFloatIsDisplay());
    }

    @Nullable
    public ExtraKeySetResolver.Resolved resolved() {
        return mResolved;
    }

    @VisibleForTesting
    @Nullable
    ExtraKeysBarView.Transition lastTransition() {
        return mLastTransition;
    }

    /** The properties were read again: the same target applies afresh, without motion. */
    public void reload() {
        mTarget = null;
        mResolved = null;
        refresh(false);
    }

    public void refresh(boolean allowMotion) {
        ExtraKeysTarget target = ExtraKeySetResolver.targetFor(mHost.settledPlace(),
            mHost.homeFloatFocused(), mHost.homeFloatIsDisplay());
        ExtraKeySetResolver.Resolved resolved = ExtraKeySetResolver.resolve(target, mHost::rawLine);
        boolean first = mTarget == null;
        if (!first && target == mTarget && resolved.equals(mResolved)) return;
        boolean targetChanged = !first && target != mTarget;
        if (targetChanged) mHost.modifiers().clear();

        String builtIn = ExtraKeySetResolver.builtInPage1(target, mHost::rawLine);
        String fallback1 = builtIn != null ? builtIn
            : TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS;
        ExtraKeysInfo page1 = mHost.parsePage(resolved.page1, fallback1, 0);
        ExtraKeysInfo page2 = null;
        if (resolved.page2 != null) {
            ExtraKeysInfo parsed = mHost.parsePage(resolved.page2,
                TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS2, 1);
            if (parsed != null && parsed.getMatrix() != null && parsed.getMatrix().length > 0) page2 = parsed;
        }
        ExtraKeysInfo[] pages = page2 == null ? new ExtraKeysInfo[] {page1}
            : new ExtraKeysInfo[] {page1, page2};

        ExtraKeysBarView.Transition transition = !targetChanged ? ExtraKeysBarView.Transition.RELOAD
            : allowMotion && mHost.barShown() ? ExtraKeysBarView.Transition.MORPH
            : ExtraKeysBarView.Transition.SWAP;

        if (!targetChanged) mHost.modifiers().clearAbsent(modifierKeysIn(pages));

        mTarget = target;
        mResolved = resolved;
        mPages = pages;
        mScale = resolved.scale;
        mLastTransition = transition;

        ExtraKeysBarView bar = mHost.bar();
        if (bar != null) bar.setKeySet(pages, resolved.scale, transition);
        ExtraKeysPageView edge = mHost.edgePage();
        if (edge != null) {
            if (transition == ExtraKeysBarView.Transition.MORPH) edge.morphTo(page1, null);
            else edge.setKeys(page1);
            int rows = page1 == null || page1.getMatrix() == null ? 0 : page1.getMatrix().length;
            boolean changed = rows != mEdgeRows || Float.compare(resolved.scale, mEdgeScale) != 0;
            mEdgeRows = rows;
            mEdgeScale = resolved.scale;
            if (changed) mHost.onEdgeKeysChanged();
        }
    }

    /** The modifiers that have a key, or a popup key, on any of {@code pages}. */
    @NonNull
    private static Set<SpecialButton> modifierKeysIn(@NonNull ExtraKeysInfo[] pages) {
        Set<SpecialButton> present = new HashSet<>();
        for (ExtraKeysInfo page : pages) {
            if (page == null || page.getMatrix() == null) continue;
            for (ExtraKeyButton[] row : page.getMatrix()) {
                for (ExtraKeyButton key : row) {
                    for (ExtraKeyButton k = key; k != null; k = k.getPopup()) {
                        if (k.isMacro() || k.getKey() == null) continue;
                        SpecialButton button = SpecialButton.valueOf(k.getKey());
                        if (button != null) present.add(button);
                    }
                }
            }
        }
        return present;
    }
}
