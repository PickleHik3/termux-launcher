package com.termux.app.terminal.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The editor's working copy of every target's extra keys: which targets carry a set of their own,
 * each own set's pages and key size, and the property lines Save writes for all of it. Global is
 * always own. Nothing here touches Android, so the rules are tested on the JVM.
 */
public final class ExtraKeysTargetDrafts {

    /** One property operation of Save: write {@code value}, or remove the line when it is null. */
    public static final class Write {
        @NonNull public final String key;
        @Nullable public final String value;

        Write(@NonNull String key, @Nullable String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Write)) return false;
            Write w = (Write) o;
            return key.equals(w.key) && Objects.equals(value, w.value);
        }

        @Override
        public int hashCode() {
            return key.hashCode() * 31 + (value == null ? 0 : value.hashCode());
        }

        @Override
        public String toString() {
            return key + (value == null ? " removed" : "=" + value);
        }
    }

    private static final class Entry {
        boolean own;
        @Nullable ExtraKeysDraft draft;
        /** Whether the target's key size line is stored. */
        boolean heightStored;
        /** Whether the target had a set of its own when loaded or last saved. */
        boolean ownAtLoad;

        Entry copy() {
            Entry e = new Entry();
            e.own = own;
            e.draft = draft == null ? null : copyOf(draft);
            e.heightStored = heightStored;
            e.ownAtLoad = ownAtLoad;
            return e;
        }
    }

    /** A deep copy of every target's state, taken before an edit so the editor can offer Undo. */
    public static final class Snapshot {
        private final Map<ExtraKeysTarget, Entry> entries;

        private Snapshot(@NonNull Map<ExtraKeysTarget, Entry> entries) {
            this.entries = entries;
        }
    }

    private final Map<ExtraKeysTarget, Entry> entries = new EnumMap<>(ExtraKeysTarget.class);

    private ExtraKeysTargetDrafts() {}

    /** Builds the drafts from the stored property lines, as the bar resolves them. */
    @NonNull
    public static ExtraKeysTargetDrafts load(@NonNull ExtraKeySetResolver.Lines lines) {
        ExtraKeysTargetDrafts out = new ExtraKeysTargetDrafts();
        for (ExtraKeysTarget target : ExtraKeysTarget.values()) {
            ExtraKeySetResolver.Resolved resolved = ExtraKeySetResolver.resolve(target, lines);
            Entry entry = new Entry();
            entry.own = target == ExtraKeysTarget.GLOBAL || resolved.source == ExtraKeySetResolver.Source.OWN;
            entry.heightStored = lines.get(target.heightKey()) != null;
            if (entry.own) {
                List<ExtraKeysLayoutModel> pages = new ArrayList<>(2);
                pages.add(ExtraKeysLayoutModel.parse(resolved.page1));
                pages.add(ExtraKeysLayoutModel.parse(resolved.page2));
                entry.draft = ExtraKeysDraft.of(pages);
                entry.draft.setKeySizeTenths(tenthsOf(resolved.scale));
            }
            entry.ownAtLoad = entry.own;
            out.entries.put(target, entry);
        }
        return out;
    }

    private static int tenthsOf(float scale) {
        return Math.max(ExtraKeysDraft.KEY_SIZE_MIN_TENTHS,
            Math.min(ExtraKeysDraft.KEY_SIZE_MAX_TENTHS, Math.round(scale * 10f)));
    }

    @NonNull
    private static ExtraKeysDraft copyOf(@NonNull ExtraKeysDraft draft) {
        List<ExtraKeysLayoutModel> pages = new ArrayList<>(draft.pageCount());
        for (int i = 0; i < draft.pageCount(); i++) pages.add(draft.page(i));
        ExtraKeysDraft copy = ExtraKeysDraft.of(pages);
        copy.setKeySizeTenths(draft.keySizeTenths());
        return copy;
    }

    @NonNull
    private Entry entry(@NonNull ExtraKeysTarget target) {
        return Objects.requireNonNull(entries.get(target));
    }

    public boolean isOwn(@NonNull ExtraKeysTarget target) {
        return entry(target).own;
    }

    /** The target's draft, or null while it has no set of its own. */
    @Nullable
    public ExtraKeysDraft draftOf(@NonNull ExtraKeysTarget target) {
        return entry(target).draft;
    }

    /** Where the target's keys come from as the draft stands now. */
    @NonNull
    public ExtraKeySetResolver.Source sourceOf(@NonNull ExtraKeysTarget target) {
        if (isOwn(target)) return ExtraKeySetResolver.Source.OWN;
        return target.shippedPage1() != null
            ? ExtraKeySetResolver.Source.SHIPPED : ExtraKeySetResolver.Source.GLOBAL;
    }

    /**
     * Gives the target a set of its own, starting from what it shows now: its built-in keys, else a
     * copy of Global's. The key size starts at Global's.
     */
    public void giveOwnSet(@NonNull ExtraKeysTarget target) {
        Entry entry = entry(target);
        if (entry.own) return;
        ExtraKeysDraft global = Objects.requireNonNull(entry(ExtraKeysTarget.GLOBAL).draft);
        ExtraKeysDraft draft;
        if (sourceOf(target) == ExtraKeySetResolver.Source.SHIPPED) {
            List<ExtraKeysLayoutModel> pages = new ArrayList<>(1);
            pages.add(ExtraKeysLayoutModel.parse(target.shippedPage1()));
            draft = ExtraKeysDraft.of(pages);
        } else {
            draft = copyOf(global);
        }
        draft.setKeySizeTenths(global.keySizeTenths());
        entry.draft = draft;
        entry.own = true;
    }

    /** Takes the target's own set away; it shows its built-in or Global's keys again. */
    public void useDefault(@NonNull ExtraKeysTarget target) {
        if (target == ExtraKeysTarget.GLOBAL) return;
        Entry entry = entry(target);
        entry.own = false;
        entry.draft = null;
    }

    @NonNull
    public Snapshot snapshot() {
        Map<ExtraKeysTarget, Entry> copy = new EnumMap<>(ExtraKeysTarget.class);
        for (Map.Entry<ExtraKeysTarget, Entry> e : entries.entrySet()) {
            copy.put(e.getKey(), e.getValue().copy());
        }
        return new Snapshot(copy);
    }

    public void restore(@NonNull Snapshot snapshot) {
        entries.clear();
        for (Map.Entry<ExtraKeysTarget, Entry> e : snapshot.entries.entrySet()) {
            entries.put(e.getKey(), e.getValue().copy());
        }
    }

    /** Drops rows that ended up empty from every own set, as Save does before writing. */
    public void pruneEmptyRows() {
        for (Entry entry : entries.values()) {
            if (entry.draft == null) continue;
            for (int page = 0; page < entry.draft.pageCount(); page++) {
                entry.draft.page(page).pruneEmptyRows();
            }
        }
    }

    /** Everything Save would write, in one string: switching a target on or off changes it too. */
    @NonNull
    public String fingerprint() {
        StringBuilder out = new StringBuilder();
        for (ExtraKeysTarget target : ExtraKeysTarget.values()) {
            Entry entry = entry(target);
            out.append(target.name()).append(entry.own ? '+' : '-').append('\n');
            if (entry.draft != null) out.append(entry.draft.fingerprint()).append('\n');
        }
        return out.toString();
    }

    /** The property operations Save performs. */
    @NonNull
    public List<Write> writes() {
        List<Write> out = new ArrayList<>();
        ExtraKeysDraft global = Objects.requireNonNull(entry(ExtraKeysTarget.GLOBAL).draft);
        for (ExtraKeysTarget target : ExtraKeysTarget.values()) {
            Entry entry = entry(target);
            ExtraKeysDraft draft = entry.draft;
            if (entry.own && draft != null) {
                out.add(new Write(target.page1Key(), draft.valueFor(0)));
                out.add(new Write(target.page2Key(), draft.valueFor(1)));
                int unlessTenths = target == ExtraKeysTarget.GLOBAL
                    ? ExtraKeysDraft.KEY_SIZE_DEFAULT_TENTHS : global.keySizeTenths();
                if (entry.heightStored || draft.keySizeTenths() != unlessTenths) {
                    out.add(new Write(target.heightKey(), ExtraKeysDraft.keySizeProperty(draft.keySizeTenths())));
                }
            } else if (entry.ownAtLoad) {
                out.add(new Write(target.page1Key(), null));
                out.add(new Write(target.page2Key(), null));
                out.add(new Write(target.heightKey(), null));
            }
        }
        return out;
    }

    /** After Save: what was written is now what is stored. */
    public void markSaved() {
        for (Write w : writes()) {
            for (ExtraKeysTarget target : ExtraKeysTarget.values()) {
                if (target.heightKey().equals(w.key)) entry(target).heightStored = w.value != null;
            }
        }
        for (Entry entry : entries.values()) entry.ownAtLoad = entry.own;
    }
}
