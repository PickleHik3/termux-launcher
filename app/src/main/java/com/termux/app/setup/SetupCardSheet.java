package com.termux.app.setup;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.chrome.ShapeTokens;

import java.util.List;

/**
 * The setup cards' sheet: the wizard card's look (rounded top, handle, no stepper) with a scrolling
 * list of cards, the dismissed ones folded to a row at the bottom, and a footer. A card's page (the
 * stubs) replaces the list until Back.
 */
final class SetupCardSheet extends LinearLayout {

    interface Callbacks {
        void onContinue(@NonNull String id);
        void onDismiss(@NonNull String id);
        void onReopen(@NonNull String id);
        void onOpenLauncher();
        void onSettings();
        void onPageBack();
        void onShizukuGuide();
        void onNotificationAccess();
        boolean isShizukuInstalled();
        boolean isNotificationAccessOn();
    }

    private static final float SHEET_RADIUS_DP = 26f;

    private final Callbacks mCallbacks;
    private final ScrollView mScroll;
    private final LinearLayout mFooter;
    private boolean mOnPage;

    SetupCardSheet(@NonNull Context context, @NonNull Callbacks callbacks) {
        super(context);
        mCallbacks = callbacks;
        setOrientation(VERTICAL);
        setClickable(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(ColorUtils.setAlphaComponent(SetupUi.surface(this), 255));
        float radius = SetupUi.dp(context, SHEET_RADIUS_DP);
        background.setCornerRadii(new float[] {radius, radius, radius, radius, 0f, 0f, 0f, 0f});
        setBackground(background);
        setElevation(ShapeTokens.elevationPx(context, 3));
        int side = SetupUi.dp(context, 20);
        setPadding(side, SetupUi.dp(context, 8), side, SetupUi.dp(context, 8));

        View handle = new View(context);
        GradientDrawable line = new GradientDrawable();
        line.setCornerRadius(SetupUi.dp(context, 2));
        line.setColor(ColorUtils.setAlphaComponent(SetupUi.onSurface(this), 70));
        handle.setBackground(line);
        LayoutParams handleParams = new LayoutParams(SetupUi.dp(context, 36), SetupUi.dp(context, 4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        addView(handle, handleParams);

        mScroll = new ScrollView(context);
        mScroll.setVerticalScrollBarEnabled(false);
        LayoutParams scrollParams = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = SetupUi.dp(context, 10);
        addView(mScroll, scrollParams);

        mFooter = new LinearLayout(context);
        mFooter.setOrientation(HORIZONTAL);
        mFooter.setGravity(Gravity.CENTER_VERTICAL);
        addView(mFooter, SetupUi.matchWrap(SetupUi.dp(context, 8)));
        bindFooter();
    }

    private void bindFooter() {
        Context context = getContext();
        mFooter.removeAllViews();
        View spacer = new View(context);
        mFooter.addView(spacer, new LayoutParams(0, 1, 1f));
        MaterialButton settings = new MaterialButton(context, null,
            androidx.appcompat.R.attr.borderlessButtonStyle);
        settings.setText(R.string.setup_cards_settings);
        settings.setAllCaps(false);
        settings.setOnClickListener(v -> mCallbacks.onSettings());
        mFooter.addView(settings, new LayoutParams(LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT));
        MaterialButton open = new MaterialButton(context);
        open.setText(R.string.setup_cards_open_launcher);
        open.setAllCaps(false);
        open.setOnClickListener(v -> mCallbacks.onOpenLauncher());
        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.leftMargin = SetupUi.dp(context, 8);
        mFooter.addView(open, params);
    }

    boolean isOnPage() {
        return mOnPage;
    }

    /** Pads the card's foot by the navigation bar's height. */
    void setNavInset(int px) {
        setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(),
            SetupUi.dp(getContext(), 8) + px);
    }

    /** Shows the list, keeping the scroll position when {@code keepScroll}. */
    void bindList(@NonNull List<SetupCards.Card> cards, boolean keepScroll) {
        Context context = getContext();
        int scrollY = keepScroll ? mScroll.getScrollY() : 0;
        mOnPage = false;
        mFooter.setVisibility(VISIBLE);
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(VERTICAL);
        SetupUi.header(page, R.string.setup_cards_title, R.string.setup_cards_line);
        boolean anyDismissed = false;
        for (SetupCards.Card card : cards) {
            if (card.state == SetupCards.State.DISMISSED) {
                anyDismissed = true;
                continue;
            }
            page.addView(cardView(card), SetupUi.matchWrap(SetupUi.dp(context, 14)));
        }
        if (anyDismissed) {
            TextView heading = SetupUi.text(context, 13f, true, SetupUi.onSurfaceVariant(page));
            heading.setText(R.string.setup_cards_dismissed_heading);
            page.addView(heading, SetupUi.matchWrap(SetupUi.dp(context, 24)));
            for (SetupCards.Card card : cards)
                if (card.state == SetupCards.State.DISMISSED)
                    page.addView(dismissedRow(card), SetupUi.matchWrap(SetupUi.dp(context, 6)));
        }
        mScroll.removeAllViews();
        mScroll.addView(page, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mScroll.post(() -> mScroll.scrollTo(0, scrollY));
    }

    /** A card's own page ({@link SetupCardPage}) in place of the list, until Back. */
    void bindPage(@NonNull android.view.View pageView) {
        mOnPage = true;
        mFooter.setVisibility(GONE);
        mScroll.removeAllViews();
        mScroll.addView(pageView, new ScrollView.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        mScroll.scrollTo(0, 0);
    }

    /** A card's stub page: the title, the line and Back, for a card with no page yet. */
    void bindPage(@NonNull SetupCards.Card card) {
        bindPage(card, false);
    }

    /** As above; {@code done} shows "Done" in place of the coming-soon line. */
    void bindPage(@NonNull SetupCards.Card card, boolean done) {
        Context context = getContext();
        mOnPage = true;
        mFooter.setVisibility(GONE);
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(VERTICAL);
        TextView title = SetupUi.text(context, 22f, true, SetupUi.onSurface(page));
        title.setText(card.name);
        page.addView(title, SetupUi.matchWrap(0));
        TextView line = SetupUi.text(context, 14f, false, SetupUi.onSurfaceVariant(page));
        line.setText(done ? R.string.setup_cards_done : R.string.setup_cards_coming);
        page.addView(line, SetupUi.matchWrap(SetupUi.dp(context, 8)));
        MaterialButton back = new MaterialButton(context);
        back.setText(R.string.setup_back);
        back.setAllCaps(false);
        back.setOnClickListener(v -> mCallbacks.onPageBack());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = SetupUi.dp(context, 20);
        page.addView(back, params);
        mScroll.removeAllViews();
        mScroll.addView(page, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mScroll.scrollTo(0, 0);
    }

    @DrawableRes
    private static int iconOf(@NonNull String id) {
        switch (id) {
            case SetupCards.SHIZUKU: return R.drawable.ic_ms_memory;
            case SetupCards.ACCESSIBILITY: return R.drawable.ic_symbol_accessibility_new;
            case SetupCards.NOTIFICATIONS: return R.drawable.ic_ms_notifications;
            case SetupCards.NOTIFICATION_ACCESS: return R.drawable.ic_ms_circle_notifications;
            case SetupCards.PINNED: return R.drawable.ic_ms_push_pin;
            case SetupCards.DISPLAY: return R.drawable.ic_ms_desktop_windows;
            case SetupCards.AI: return R.drawable.ic_ms_smart_toy;
            case SetupCards.HOME: return R.drawable.ic_ms_home;
            default: return R.drawable.ic_ms_tour;
        }
    }

    @NonNull
    private View cardView(@NonNull SetupCards.Card card) {
        Context context = getContext();
        boolean done = card.state == SetupCards.State.DONE;
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(VERTICAL);
        int pad = SetupUi.dp(context, 16);
        box.setPadding(pad, pad, pad, SetupUi.dp(context, 8));
        SetupUi.dressChoice(box, false);

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconOf(card.id));
        icon.setColorFilter(SetupUi.accent(this));
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        top.addView(icon, new LayoutParams(SetupUi.dp(context, 24), SetupUi.dp(context, 24)));
        TextView name = SetupUi.text(context, 17f, true, SetupUi.onSurface(this));
        name.setText(card.name);
        LayoutParams nameParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = SetupUi.dp(context, 12);
        top.addView(name, nameParams);
        box.addView(top, SetupUi.matchWrap(0));

        TextView description = SetupUi.text(context, 14f, false, SetupUi.onSurfaceVariant(this));
        description.setText(card.description);
        box.addView(description, SetupUi.matchWrap(SetupUi.dp(context, 8)));

        for (int i = 0; i < card.steps.length; i++) {
            boolean stepDone = i < card.completed;
            TextView step = SetupUi.text(context, 14f, false, SetupUi.onSurface(this));
            step.setText((stepDone ? "✓  " : "○  ") + context.getString(card.steps[i]));
            step.setContentDescription(context.getString(stepDone
                ? R.string.setup_cards_step_done : R.string.setup_cards_step_pending,
                context.getString(card.steps[i])));
            box.addView(step, SetupUi.matchWrap(SetupUi.dp(context, i == 0 ? 10 : 4)));
        }

        if (done) {
            TextView doneLine = SetupUi.text(context, 14f, true, SetupUi.accent(this));
            doneLine.setText(R.string.setup_cards_done);
            box.addView(doneLine, SetupUi.matchWrap(SetupUi.dp(context, 10)));
        }

        if (card.id.equals(SetupCards.SHIZUKU) && !done && !mCallbacks.isShizukuInstalled()) {
            box.addView(textButton(R.string.setup_card_shizuku_guide, mCallbacks::onShizukuGuide),
                buttonParams(8));
        }
        if (card.id.equals(SetupCards.PINNED) && !done && !mCallbacks.isNotificationAccessOn()) {
            box.addView(textButton(R.string.setup_card_pinned_access,
                mCallbacks::onNotificationAccess), buttonParams(8));
        }

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(HORIZONTAL);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (!done) {
            buttons.addView(textButton(R.string.setup_cards_dismiss,
                () -> mCallbacks.onDismiss(card.id)), buttonParams(0));
        }
        MaterialButton primary = new MaterialButton(context);
        primary.setAllCaps(false);
        primary.setText(done ? R.string.setup_cards_review : R.string.setup_cards_continue);
        primary.setOnClickListener(v -> {
            // Review of a done permission or home card has no page class: the stub says Done.
            if (done && SetupCards.isSystemChecked(card.id)
                && SetupCardPages.create(card.id) == null)
                bindPage(card, true);
            else
                mCallbacks.onContinue(card.id);
        });
        LayoutParams primaryParams = buttonParams(0);
        primaryParams.leftMargin = SetupUi.dp(context, 8);
        buttons.addView(primary, primaryParams);
        box.addView(buttons, SetupUi.matchWrap(SetupUi.dp(context, 6)));
        return box;
    }

    @NonNull
    private View dismissedRow(@NonNull SetupCards.Card card) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = SetupUi.dp(context, 12);
        row.setPadding(pad, SetupUi.dp(context, 2), SetupUi.dp(context, 4), SetupUi.dp(context, 2));
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(SetupUi.dp(context, 14));
        shape.setColor(ColorUtils.setAlphaComponent(SetupUi.onSurface(this), 14));
        row.setBackground(shape);
        TextView name = SetupUi.text(context, 15f, false, SetupUi.onSurfaceVariant(this));
        name.setText(card.name);
        row.addView(name, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        row.addView(textButton(R.string.setup_cards_reopen, () -> mCallbacks.onReopen(card.id)),
            buttonParams(0));
        return row;
    }

    @NonNull
    private MaterialButton textButton(int textRes, @NonNull Runnable action) {
        MaterialButton button = new MaterialButton(getContext(), null,
            androidx.appcompat.R.attr.borderlessButtonStyle);
        button.setText(textRes);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    @NonNull
    private LayoutParams buttonParams(int topDp) {
        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = SetupUi.dp(getContext(), topDp);
        return params;
    }
}
