package com.termux.app.terminal.io;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDragHandleView;
import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.material.M3;

/**
 * The two small action sheets of the page editor: what can be done to a row, and to a page. Each
 * action closes the sheet and reports; the draft is the caller's, and Done only puts the sheet
 * down. An action that cannot apply (the first row cannot move up) is shown dimmed rather than
 * left out, so the choices do not shift from row to row.
 */
public final class ExtraKeyOptionsSheets {

    /** What the row sheet can ask for. */
    public interface RowActions {
        void moveUp();

        void moveDown();

        void delete();
    }

    /** What the page sheet can ask for. */
    public interface PageActions {
        void moveLeft();

        void moveRight();

        void remove();
    }

    private ExtraKeyOptionsSheets() {}

    public static void showRow(@NonNull Context context, int rowNumber, boolean canMoveUp,
                               boolean canMoveDown, @NonNull RowActions actions) {
        Sheet sheet = new Sheet(context,
            context.getString(R.string.settings_extra_keys_row_options_title, rowNumber),
            context.getString(R.string.settings_extra_keys_row_options_subtitle));
        sheet.action("↑", R.string.settings_extra_keys_row_move_up, canMoveUp, false,
            actions::moveUp);
        sheet.action("↓", R.string.settings_extra_keys_row_move_down, canMoveDown, false,
            actions::moveDown);
        sheet.action("✕", R.string.settings_extra_keys_row_delete, true, true, actions::delete);
        sheet.show();
    }

    public static void showPage(@NonNull Context context, int pageNumber, boolean canMoveLeft,
                                boolean canMoveRight, boolean canRemove,
                                @NonNull PageActions actions) {
        Sheet sheet = new Sheet(context,
            context.getString(R.string.settings_extra_keys_page_options_title),
            context.getString(R.string.settings_extra_keys_page, pageNumber));
        sheet.action("←", R.string.settings_extra_keys_page_move_left, canMoveLeft, false,
            actions::moveLeft);
        sheet.action("→", R.string.settings_extra_keys_page_move_right, canMoveRight, false,
            actions::moveRight);
        sheet.action("✕", R.string.settings_extra_keys_page_remove, canRemove, true,
            actions::remove);
        sheet.show();
    }

    /** A title, a subtitle, a column of actions and Done. */
    private static final class Sheet {
        private final Context context;
        private final float density;
        private final BottomSheetDialog dialog;
        private final LinearLayout root;

        Sheet(@NonNull Context context, @NonNull String title, @NonNull String subtitle) {
            this.context = context;
            this.density = context.getResources().getDisplayMetrics().density;
            this.dialog = new BottomSheetDialog(context);
            root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(16), 0, dp(16), dp(8));
            root.addView(new BottomSheetDragHandleView(context), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout header = new LinearLayout(context);
            header.setGravity(Gravity.CENTER_VERTICAL);
            TextView titleView = text(title,
                com.google.android.material.R.attr.textAppearanceTitleLarge, M3.onSurface(context));
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            header.addView(titleView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            MaterialButton close = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialIconButtonStyle);
            close.setIconResource(R.drawable.ic_symbol_close);
            close.setContentDescription(context.getString(R.string.settings_extra_keys_sheet_done));
            close.setOnClickListener(v -> dialog.dismiss());
            header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
            root.addView(header);

            TextView sub = text(subtitle,
                com.google.android.material.R.attr.textAppearanceBodyMedium,
                M3.onSurfaceVariant(context));
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setPadding(0, 0, 0, dp(8));
            root.addView(sub);
        }

        void action(@NonNull String glyph, @StringRes int label, boolean enabled,
                    boolean destructive, @NonNull Runnable run) {
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setMinimumHeight(dp(52));
            line.setPadding(dp(8), 0, dp(8), 0);
            line.setEnabled(enabled);
            line.setClickable(enabled);
            line.setAlpha(enabled ? 1f : M3.DISABLED_CONTENT + 0.1f);
            int color = destructive ? M3.error(context) : M3.onSurface(context);
            TextView mark = text(glyph, com.google.android.material.R.attr.textAppearanceTitleMedium,
                color);
            mark.setGravity(Gravity.CENTER);
            line.addView(mark, new LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView name = text(context.getString(label),
                com.google.android.material.R.attr.textAppearanceBodyLarge, color);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nameParams.setMarginStart(dp(8));
            line.addView(name, nameParams);
            if (enabled) {
                android.util.TypedValue ripple = new android.util.TypedValue();
                if (context.getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, ripple, true)) {
                    line.setBackgroundResource(ripple.resourceId);
                }
                line.setOnClickListener(v -> {
                    dialog.dismiss();
                    run.run();
                });
            }
            root.addView(line);
        }

        void show() {
            LinearLayout bar = new LinearLayout(context);
            bar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            bar.setPadding(0, dp(8), 0, dp(4));
            MaterialButton done = new MaterialButton(context);
            done.setText(R.string.settings_extra_keys_sheet_done);
            done.setOnClickListener(v -> dialog.dismiss());
            bar.addView(done);
            root.addView(bar);
            dialog.setContentView(root);
            BottomSheetBehavior<?> behavior = dialog.getBehavior();
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            dialog.show();
        }

        private TextView text(@NonNull String value, int appearanceAttr, int color) {
            TextView view = new TextView(context);
            view.setText(value);
            M3.textAppearance(view, appearanceAttr);
            view.setTextColor(color);
            return view;
        }

        private int dp(float dp) {
            return Math.round(dp * density);
        }
    }
}
