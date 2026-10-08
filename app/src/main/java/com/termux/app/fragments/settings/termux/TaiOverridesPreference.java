package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Two-column list of runtime override key/value cells, matching the TL handoff
 * "Runtime overrides" design. Each cell is tappable; persistence is handled by
 * the hosting fragment. Rendered as rows of two weighted cells (rather than a
 * GridLayout) so the columns reliably stretch to fill the row.
 */
@Keep
public class TaiOverridesPreference extends Preference {

    public static final class Item {
        public final CharSequence label;
        public final CharSequence valueLabel;

        public Item(CharSequence label, CharSequence valueLabel) {
            this.label = label;
            this.valueLabel = valueLabel;
        }
    }

    public interface OnOverrideClickListener {
        void onOverrideClick(int index);
    }

    private final List<Item> mItems = new ArrayList<>();
    private OnOverrideClickListener mListener;

    public TaiOverridesPreference(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TaiOverridesPreference(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        setLayoutResource(R.layout.preference_tai_overrides);
        setPersistent(false);
        setSelectable(false);
        setIconSpaceReserved(false);
    }

    public void setOnOverrideClickListener(OnOverrideClickListener listener) {
        mListener = listener;
    }

    public void setItems(@NonNull List<Item> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyChanged();
    }

    /** How many cells the grid holds; a page that never fed it shows an empty strip. */
    public int itemCount() {
        return mItems.size();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        // The layout's root is the container; found by id for safety, the item view itself otherwise.
        View root = holder.findViewById(R.id.tai_overrides_container);
        if (!(root instanceof LinearLayout)) root = holder.itemView;
        if (!(root instanceof LinearLayout)) {
            android.util.Log.w("TaiOverrides", "no container to fill: " + holder.itemView);
            return;
        }
        LinearLayout container = (LinearLayout) root;
        if (mItems.isEmpty()) android.util.Log.w("TaiOverrides", "bound with no items");
        container.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < mItems.size(); i += 2) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            addCell(inflater, row, i);
            if (i + 1 < mItems.size()) {
                addCell(inflater, row, i + 1);
            } else {
                // Keep the single trailing cell at half width.
                View spacer = new View(getContext());
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                row.addView(spacer);
            }
            container.addView(row);
        }
    }

    private void addCell(@NonNull LayoutInflater inflater, @NonNull LinearLayout row, int index) {
        Item item = mItems.get(index);
        View cell = inflater.inflate(R.layout.preference_tai_override_cell, row, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cell.setLayoutParams(params);
        TextView key = cell.findViewById(R.id.tai_override_key);
        if (key != null) key.setText(item.label);
        TextView value = cell.findViewById(R.id.tai_override_value);
        if (value != null) value.setText(item.valueLabel);
        cell.setOnClickListener(v -> {
            if (mListener != null) mListener.onOverrideClick(index);
        });
        row.addView(cell);
    }
}
