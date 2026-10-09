package com.termux.app.fragments.settings.termux;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * The Home and Choose screens' list: the Model centre's shape (a fragment rebuilds the whole
 * item list on every change and hands it to {@link #submit}; DiffUtil keeps every row whose
 * signature is unchanged bound as it is, rebinding in place the ones that changed and moving the
 * ones that re-sorted), with the row views built in code by the screen's {@link Factory} rather
 * than a holder class per row type.
 */
final class TaiBenchListAdapter extends RecyclerView.Adapter<TaiBenchListAdapter.Holder> {
    /** Builds and binds a row of one type. */
    interface Factory {
        @NonNull View create(@NonNull ViewGroup parent, int type);
        void bind(@NonNull View view, @NonNull Item item);
    }

    /** One entry of the list. {@link #signature} is what DiffUtil compares for "same content". */
    static final class Item {
        final int type;
        @NonNull final String key;
        @NonNull final String signature;
        @Nullable final Object data;

        Item(int type, @NonNull String key, @NonNull String signature, @Nullable Object data) {
            this.type = type;
            this.key = key;
            this.signature = signature;
            this.data = data;
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        Holder(@NonNull View view) {
            super(view);
        }
    }

    @NonNull private final Factory factory;
    @NonNull private List<Item> items = new ArrayList<>();

    TaiBenchListAdapter(@NonNull Factory factory) {
        this.factory = factory;
    }

    void submit(@NonNull List<Item> next) {
        List<Item> previous = items;
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return previous.size();
            }

            @Override
            public int getNewListSize() {
                return next.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                Item a = previous.get(oldPosition);
                Item b = next.get(newPosition);
                return a.type == b.type && a.key.equals(b.key);
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return previous.get(oldPosition).signature.equals(next.get(newPosition).signature);
            }
        }, true);
        items = next;
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    Item item(int position) {
        return items.get(position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = factory.create(parent, viewType);
        // Rows are built in code without layout params; the list's default would be wrap-content
        // wide, and every row here spans the list.
        if (view.getLayoutParams() == null) {
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        factory.bind(holder.itemView, items.get(position));
    }
}
