package com.termux.app.editmode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.look.TerlLookStore;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One visit to edit mode: every change applies at once, and this keeps the history of the visit
 * (spec "Edit mode and Settings", History). One slider drag is one Undo step, from where the finger
 * landed to where it lifted; Follow Global, a Tint colour, an icon pack, Revert and a Look pick are
 * one step each, so Undo right after Revert brings the session back and Undo after a Look pick
 * restores the old Look and every own value it cleared. The history ends with the session.
 *
 * <p>The stores stay the truth (ADR 0041): the look store, and the launcher's text size and icon
 * pack through {@link Launcher}. A setting the session wrote that now holds something else was
 * written from outside (launcherctl, terl.conf, Settings, the Assistant) and is the outside's: Undo
 * and Revert skip it, it leaves {@link #changeCount()}, and it is counted in
 * {@link #keptElsewhereCount()} until the session changes it again.
 *
 * <p>Pure: no views, no Android. Used from the main thread.
 */
public final class EditSession {

    /** The launcher's settings the Global card changes beside the look. */
    public interface Launcher {
        /** The shared text size, in percent. */
        int textSizePercent();

        /** Writes the text size, clamped to its range, and has the screen follow at once. */
        void setTextSizePercent(int percent);

        /** The icon pack's package, "" for the system's icons. */
        @NonNull
        String iconPack();

        /** Writes the icon pack and has the launcher's icons follow at once. */
        void setIconPack(@NonNull String pack);
    }

    /** One setting a card row changes. */
    public static final class Setting {
        private enum Kind { OWN, CUSTOM, CORNERS, TINT, LOOK, TEXT_SIZE, ICON_PACK }

        /** Global's corner radius, in dp. */
        public static final Setting CORNERS = new Setting(Kind.CORNERS, null, null);
        /** The launcher's text size, in percent. */
        public static final Setting TEXT_SIZE = new Setting(Kind.TEXT_SIZE, null, null);
        static final Setting TINT = new Setting(Kind.TINT, null, null);
        static final Setting LOOK = new Setting(Kind.LOOK, null, null);
        static final Setting ICON_PACK = new Setting(Kind.ICON_PACK, null, null);

        @NonNull private final Kind kind;
        @Nullable private final TerlSurface surface;
        @Nullable private final TerlProperty property;

        private Setting(@NonNull Kind kind, @Nullable TerlSurface surface,
                        @Nullable TerlProperty property) {
            this.kind = kind;
            this.surface = surface;
            this.property = property;
        }

        /** A surface's own value; following Global when it holds none. */
        @NonNull
        public static Setting own(@NonNull TerlSurface surface, @NonNull TerlProperty property) {
            return new Setting(Kind.OWN, surface, property);
        }

        /** Custom's Opacity or Blur, for all glass. */
        @NonNull
        public static Setting custom(@NonNull TerlProperty property) {
            return new Setting(Kind.CUSTOM, null, property);
        }

        /** Whether a slider writes it, a number at a time. */
        private boolean isNumber() {
            return kind == Kind.OWN || kind == Kind.CUSTOM || kind == Kind.CORNERS
                || kind == Kind.TEXT_SIZE;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (!(other instanceof Setting)) return false;
            Setting that = (Setting) other;
            return kind == that.kind && surface == that.surface && property == that.property;
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, surface, property);
        }
    }

    /** One Undo step: each setting it moved, from (index 0) and to (index 1). */
    private static final class Step {
        final Map<Setting, Object[]> rows = new LinkedHashMap<>();
    }

    @NonNull private final TerlLookStore mStore;
    @Nullable private final Launcher mLauncher;
    /**
     * What each setting the session touched held when the session (or the outside) last owned it.
     * A value is an Integer or a String; null is an own value's absence (it follows Global).
     */
    private final Map<Setting, Object> mBaseline = new HashMap<>();
    /** What the session last wrote to each setting it still owns. */
    private final Map<Setting, Object> mLastWritten = new HashMap<>();
    /** Settings the session changed that were written from outside since. */
    private final Set<Setting> mKeptElsewhere = new HashSet<>();
    private final Deque<Step> mUndo = new ArrayDeque<>();
    /** Drags in progress: where each started. */
    private final Map<Setting, Object> mDragStart = new HashMap<>();
    private boolean mEnded;
    /** A write of the session's own is landing in a store. */
    private boolean mWriting;

    private EditSession(@NonNull TerlLookStore store, @Nullable Launcher launcher) {
        mStore = store;
        mLauncher = launcher;
    }

    /** Opens a session over the look store alone: text size and icon pack are left alone. */
    @NonNull
    public static EditSession begin(@NonNull TerlLookStore store) {
        return new EditSession(store, null);
    }

    /** Opens a session over {@code store} and the launcher's settings: nothing changed yet. */
    @NonNull
    public static EditSession begin(@NonNull TerlLookStore store, @Nullable Launcher launcher) {
        return new EditSession(store, launcher);
    }

    // ---------------------------------------------------------------- sliders

    /** A finger landed on the setting's slider: the start of one Undo step. */
    public void dragStarted(@NonNull Setting setting) {
        if (!editable(setting) || !setting.isNumber()) return;
        reconcile();
        mDragStart.put(setting, read(setting));
    }

    /**
     * Writes {@code value} to the setting, at once. Inside a drag it moves the drag along; on its
     * own it is a step of its own.
     */
    public void set(@NonNull Setting setting, int value) {
        if (!editable(setting) || !setting.isNumber()) return;
        boolean alone = !mDragStart.containsKey(setting);
        if (alone) dragStarted(setting);
        else reconcile();
        write(setting, value);
        if (alone) dragEnded(setting);
    }

    /** The finger lifted: the drag becomes one Undo step, unless it ended where it started. */
    public void dragEnded(@NonNull Setting setting) {
        if (!editable(setting)) return;
        reconcile();
        if (!mDragStart.containsKey(setting)) return;
        Object from = mDragStart.remove(setting);
        if (!mLastWritten.containsKey(setting)) return;
        Object to = read(setting);
        if (Objects.equals(to, from)) return;
        push(setting, from, to);
    }

    public void dragStarted(@NonNull TerlSurface surface, @NonNull TerlProperty property) {
        dragStarted(Setting.own(surface, property));
    }

    /** Gives the surface an own value: {@link #set(Setting, int)} on {@link Setting#own}. */
    public void set(@NonNull TerlSurface surface, @NonNull TerlProperty property, int value) {
        set(Setting.own(surface, property), value);
    }

    public void dragEnded(@NonNull TerlSurface surface, @NonNull TerlProperty property) {
        dragEnded(Setting.own(surface, property));
    }

    // ---------------------------------------------------------------- one-step changes

    /** Clears the surface's own value, as one step. One already following Global is left alone. */
    public void followGlobal(@NonNull TerlSurface surface, @NonNull TerlProperty property) {
        Setting setting = Setting.own(surface, property);
        if (!editable(setting)) return;
        reconcile();
        if (read(setting) == null) return;
        oneStep(setting, null);
    }

    /** Sets Global's Tint colour, as one step. */
    public void setTint(@NonNull String tint) {
        if (!editable(Setting.TINT)) return;
        reconcile();
        oneStep(Setting.TINT, tint);
    }

    /** Sets the icon pack ("" for the system's icons), as one step. */
    public void setIconPack(@NonNull String pack) {
        if (!editable(Setting.ICON_PACK)) return;
        reconcile();
        oneStep(Setting.ICON_PACK, pack);
    }

    /**
     * Picks a Look ({@link TerlLookStore#pick}): unconfirmed while a surface holds an own value it
     * changes nothing and names what it would clear, so the card can ask. Applied, it is one step:
     * the Look and every own value it cleared, which Undo puts back together.
     */
    @NonNull
    public TerlLookStore.Pick pick(@NonNull String lookId, boolean confirmed) {
        if (mEnded) return mStore.pick(lookId, confirmed);
        reconcile();
        Object fromLook = read(Setting.LOOK);
        List<TerlLookStore.OwnValue> own = mStore.ownValues();
        boolean applies = mStore.lookIds().contains(lookId) && (confirmed || own.isEmpty());
        if (!applies) return mStore.pick(lookId, confirmed);

        // The settings the pick moves, with what each will hold, recorded before the store writes
        // and tells its listeners.
        Map<Setting, Object[]> moves = new LinkedHashMap<>();
        if (!lookId.equals(fromLook)) moves.put(Setting.LOOK, new Object[] {fromLook, lookId});
        for (TerlLookStore.OwnValue value : own) {
            moves.put(Setting.own(value.surface, value.property), new Object[] {value.value, null});
        }
        for (Map.Entry<Setting, Object[]> move : moves.entrySet()) {
            claim(move.getKey(), move.getValue()[1]);
        }
        TerlLookStore.Pick pick;
        mWriting = true;
        try {
            pick = mStore.pick(lookId, true);
        } finally {
            mWriting = false;
        }
        Step step = new Step();
        for (Map.Entry<Setting, Object[]> move : moves.entrySet()) {
            Object to = read(move.getKey());
            mLastWritten.put(move.getKey(), to);
            step.rows.put(move.getKey(), new Object[] {move.getValue()[0], to});
        }
        if (!step.rows.isEmpty()) mUndo.push(step);
        return pick;
    }

    // ---------------------------------------------------------------- history

    public boolean canUndo() {
        if (mEnded) return false;
        reconcile();
        return !mUndo.isEmpty();
    }

    /** Reverses the latest step still the session's; settings written from outside since are skipped. */
    public void undo() {
        if (mEnded) return;
        reconcile();
        Step step = mUndo.poll();
        if (step == null) return;
        for (Map.Entry<Setting, Object[]> entry : step.rows.entrySet()) {
            write(entry.getKey(), entry.getValue()[0]);
        }
    }

    /**
     * Puts every setting the session changed back to what it held when the session opened, as one
     * step. Settings written from outside since are skipped.
     */
    public void revert() {
        if (mEnded) return;
        reconcile();
        Step step = new Step();
        for (Map.Entry<Setting, Object> entry : new HashMap<>(mLastWritten).entrySet()) {
            Setting setting = entry.getKey();
            if (!mBaseline.containsKey(setting)) continue;
            Object base = mBaseline.get(setting);
            Object now = entry.getValue();
            if (Objects.equals(base, now)) continue;
            step.rows.put(setting, new Object[] {now, base});
            write(setting, base);
        }
        if (!step.rows.isEmpty()) mUndo.push(step);
    }

    /** How many settings this session changed and still holds changed. */
    public int changeCount() {
        if (mEnded) return 0;
        reconcile();
        int count = 0;
        for (Map.Entry<Setting, Object> entry : mLastWritten.entrySet()) {
            if (!Objects.equals(entry.getValue(), mBaseline.get(entry.getKey()))) count++;
        }
        return count;
    }

    /** How many settings this session changed were then set elsewhere, and kept as set there. */
    public int keptElsewhereCount() {
        if (mEnded) return 0;
        reconcile();
        return mKeptElsewhere.size();
    }

    /** Edit mode closed: the changes stay, the history goes. */
    public void end() {
        mEnded = true;
        mUndo.clear();
        mBaseline.clear();
        mLastWritten.clear();
        mKeptElsewhere.clear();
        mDragStart.clear();
    }

    // ---------------------------------------------------------------- the stores

    private boolean editable(@NonNull Setting setting) {
        if (mEnded) return false;
        switch (setting.kind) {
            case OWN: return setting.surface != null && setting.surface.acceptsOwn;
            case TEXT_SIZE:
            case ICON_PACK: return mLauncher != null;
            default: return true;
        }
    }

    /** What the setting holds now: an Integer, a String, or null for an own value's absence. */
    @Nullable
    private Object read(@NonNull Setting setting) {
        switch (setting.kind) {
            case OWN:
                return mStore.holdsOwn(setting.surface, setting.property)
                    ? (Object) mStore.value(setting.surface, setting.property) : null;
            case CUSTOM: return mStore.customValue(setting.property);
            case CORNERS: return mStore.cornerRadiusDp();
            case TINT: return mStore.tint();
            case LOOK: return mStore.look();
            case TEXT_SIZE: return mLauncher == null ? null : mLauncher.textSizePercent();
            case ICON_PACK: return mLauncher == null ? null : mLauncher.iconPack();
            default: return null;
        }
    }

    private void apply(@NonNull Setting setting, @Nullable Object value) {
        switch (setting.kind) {
            case OWN:
                if (value == null) mStore.followGlobal(setting.surface, setting.property);
                else mStore.setOwn(setting.surface, setting.property, (Integer) value);
                break;
            case CUSTOM: mStore.setCustomValue(setting.property, (Integer) value); break;
            case CORNERS: mStore.setCornerRadiusDp((Integer) value); break;
            case TINT: mStore.setTint((String) value); break;
            case LOOK: mStore.wear((String) value); break;
            case TEXT_SIZE: if (mLauncher != null) mLauncher.setTextSizePercent((Integer) value); break;
            case ICON_PACK: if (mLauncher != null) mLauncher.setIconPack((String) value); break;
            default: break;
        }
    }

    /** What a write of {@code value} will leave, as near as the session can tell before it lands. */
    @Nullable
    private static Object expected(@NonNull Setting setting, @Nullable Object value) {
        if (value instanceof Integer && setting.property != null) {
            return setting.property.clamp((Integer) value);
        }
        return value;
    }

    /** The session takes the setting over: its baseline is kept, and it is no longer the outside's. */
    private void claim(@NonNull Setting setting, @Nullable Object expected) {
        if (!mBaseline.containsKey(setting)) mBaseline.put(setting, read(setting));
        mKeptElsewhere.remove(setting);
        mLastWritten.put(setting, expected(setting, expected));
    }

    private void write(@NonNull Setting setting, @Nullable Object value) {
        // The stores tell their listeners during the write, and the screen asks this session then:
        // what the write will leave is recorded first, and nothing is reconciled until it lands.
        claim(setting, value);
        mWriting = true;
        try {
            apply(setting, value);
        } finally {
            mWriting = false;
        }
        // What the store holds after the write, so it compares equal later.
        mLastWritten.put(setting, read(setting));
    }

    private void oneStep(@NonNull Setting setting, @Nullable Object value) {
        Object from = read(setting);
        write(setting, value);
        Object to = read(setting);
        if (!Objects.equals(from, to)) push(setting, from, to);
    }

    private void push(@NonNull Setting setting, @Nullable Object from, @Nullable Object to) {
        Step step = new Step();
        step.rows.put(setting, new Object[] {from, to});
        mUndo.push(step);
    }

    /**
     * Hands every setting written from outside since the session last wrote it to the outside: its
     * baseline becomes what it holds now, and every step forgets it.
     */
    private void reconcile() {
        if (mWriting) return;
        Iterator<Map.Entry<Setting, Object>> written = mLastWritten.entrySet().iterator();
        List<Setting> handed = new ArrayList<>();
        while (written.hasNext()) {
            Map.Entry<Setting, Object> entry = written.next();
            Setting setting = entry.getKey();
            Object now = read(setting);
            if (Objects.equals(now, entry.getValue())) continue;
            written.remove();
            mBaseline.put(setting, now);
            mKeptElsewhere.add(setting);
            if (mDragStart.containsKey(setting)) mDragStart.put(setting, now);
            handed.add(setting);
        }
        if (handed.isEmpty()) return;
        Iterator<Step> steps = mUndo.iterator();
        while (steps.hasNext()) {
            Step step = steps.next();
            for (Setting setting : handed) step.rows.remove(setting);
            if (step.rows.isEmpty()) steps.remove();
        }
    }
}
