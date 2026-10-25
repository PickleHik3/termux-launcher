package com.termux.app.launcher.pin;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.launcher.home.HomeSettings;
import com.termux.app.launcher.widget.LauncherWidgetRecord;
import com.termux.app.launcher.widget.WidgetGridDefinition;

/**
 * The confirm card for an app asking to pin something to Home: Chrome's "Add to home screen", an
 * app's "Add widget", an app shortcut's pin. The system sends it
 * {@code ACTION_CONFIRM_PIN_SHORTCUT} or {@code ACTION_CONFIRM_PIN_APPWIDGET} when this launcher is
 * the default Home app; the {@link LauncherApps.PinItemRequest} is read from the intent and only
 * answered ({@code accept}) once the item is on a page.
 *
 * <p>The card shows the item, then "Add to Home" or, with the layout locked, an Unlock that adds
 * afterwards. Placement is the Home pane's ({@link HomePinHandoff}, {@link HomePinPolicy}): this
 * activity holds no Home model. A shortcut is accepted, then placed, and the card names the page
 * it landed on; a widget goes through the widget add path, which may leave for the provider's own
 * screens, so the card hands over, brings Home forward and goes, and Home says the result.</p>
 *
 * <p>Handled in the main process, exported only because the system must reach it, and it answers
 * nothing but a request the platform vouches for ({@code getPinItemRequest} returns null for an
 * intent that does not carry one, and throws unless this is the default Home app).</p>
 */
public final class HomePinRequestActivity extends AppCompatActivity {
    private static final long CONFIRMATION_MS = 900L;
    private static final int CARD_MAX_WIDTH_DP = 420;

    private final Handler main = new Handler(Looper.getMainLooper());
    @Nullable private LauncherApps.PinItemRequest request;
    @Nullable private AppWidgetProviderInfo widgetInfo;
    private int columns = 1;
    private int rows = 1;
    private boolean finishing;

    private TextView heading;
    private TextView detail;
    private MaterialButton confirm;
    private MaterialButton cancel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String action = intent == null ? null : intent.getAction();
        if (!LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT.equals(action)
            && !LauncherApps.ACTION_CONFIRM_PIN_APPWIDGET.equals(action)) {
            finish();
            return;
        }
        LauncherApps.PinItemRequest pin;
        try {
            LauncherApps apps = getSystemService(LauncherApps.class);
            pin = apps == null ? null : apps.getPinItemRequest(intent);
        } catch (RuntimeException notTheHomeApp) {
            pin = null;
        }
        if (pin == null || !pin.isValid()) {
            finish();
            return;
        }
        request = pin;
        setContentView(R.layout.activity_home_pin_request);
        heading = findViewById(R.id.home_pin_heading);
        detail = findViewById(R.id.home_pin_detail);
        confirm = findViewById(R.id.home_pin_confirm);
        cancel = findViewById(R.id.home_pin_cancel);
        View card = findViewById(R.id.home_pin_card);
        int maxWidth = Math.round(CARD_MAX_WIDTH_DP * getResources().getDisplayMetrics().density);
        ViewGroup.LayoutParams params = card.getLayoutParams();
        params.width = Math.min(getResources().getDisplayMetrics().widthPixels, maxWidth);
        card.setLayoutParams(params);
        findViewById(R.id.home_pin_root).setOnClickListener(v -> finish());
        cancel.setOnClickListener(v -> finish());

        if (pin.getRequestType() == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            showShortcut(pin);
        } else if (pin.getRequestType() == LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET) {
            showWidget(pin);
        } else {
            finish();
            return;
        }
        renderState();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void showShortcut(@NonNull LauncherApps.PinItemRequest pin) {
        ShortcutInfo info = pin.getShortcutInfo();
        if (info == null) {
            finish();
            return;
        }
        CharSequence label = info.getShortLabel();
        ((TextView) findViewById(R.id.home_pin_label)).setText(label == null ? "" : label);
        detail.setText(appLabel(info.getPackage()));
        try {
            LauncherApps apps = getSystemService(LauncherApps.class);
            Drawable icon = apps == null ? null : apps.getShortcutIconDrawable(info,
                getResources().getDisplayMetrics().densityDpi);
            ((ImageView) findViewById(R.id.home_pin_preview)).setImageDrawable(icon);
        } catch (RuntimeException ignored) {
            // The card still names it; the icon is only the picture.
        }
        columns = 1;
        rows = 1;
    }

    private void showWidget(@NonNull LauncherApps.PinItemRequest pin) {
        AppWidgetProviderInfo info = pin.getAppWidgetProviderInfo(this);
        if (info == null) {
            finish();
            return;
        }
        widgetInfo = info;
        PackageManager pm = getPackageManager();
        ((TextView) findViewById(R.id.home_pin_label)).setText(info.loadLabel(pm));
        Drawable preview = info.loadPreviewImage(this, 0);
        if (preview == null) preview = info.loadIcon(this, 0);
        ((ImageView) findViewById(R.id.home_pin_preview)).setImageDrawable(preview);
        HomePinTarget home = HomePinHandoff.target();
        if (home != null) {
            int[] span = home.spanFor(info);
            columns = span[0];
            rows = span[1];
        } else {
            float density = getResources().getDisplayMetrics().density;
            columns = Math.min(WidgetGridDefinition.DEFAULT_COLUMNS, HomePinPolicy.classicCells(
                Math.round(info.minWidth / density)));
            rows = Math.min(WidgetGridDefinition.DEFAULT_ROWS, HomePinPolicy.classicCells(
                Math.round(info.minHeight / density)));
        }
    }

    /** The card's one sentence and the button that goes with it, from the lock and the size. */
    private void renderState() {
        HomePinTarget home = HomePinHandoff.target();
        WidgetGridDefinition grid = home == null ? WidgetGridDefinition.DEFAULT : home.grid();
        boolean locked = home != null ? home.layoutLocked() : HomeSettings.isLayoutLocked(this);
        HomePinPolicy.Card state = HomePinPolicy.card(locked, grid, columns, rows);
        String size = widgetInfo == null ? ""
            : getString(R.string.home_pin_size, columns, rows);
        switch (state) {
            case LOCKED:
                detail.setText(joined(size, getString(R.string.home_pin_locked)));
                confirm.setText(R.string.home_pin_unlock);
                confirm.setOnClickListener(v -> {
                    if (home != null) home.unlockLayout();
                    else HomeSettings.setLayoutLocked(this, false);
                    add();
                });
                break;
            case TOO_BIG:
                detail.setText(joined(size, getString(R.string.home_pin_too_big)));
                confirm.setEnabled(false);
                break;
            case READY:
            default:
                if (widgetInfo != null) detail.setText(size);
                confirm.setText(R.string.home_pin_add);
                confirm.setEnabled(true);
                confirm.setOnClickListener(v -> add());
                break;
        }
    }

    @NonNull
    private static String joined(@NonNull String first, @NonNull String second) {
        return first.isEmpty() ? second : first + "\n" + second;
    }

    private void add() {
        LauncherApps.PinItemRequest pin = request;
        if (pin == null || finishing) return;
        confirm.setEnabled(false);
        cancel.setEnabled(false);
        if (pin.getRequestType() == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            addShortcut(pin);
        } else {
            addWidget(pin);
        }
    }

    /**
     * Written before it is accepted, so the system and Home never disagree: Home places the
     * shortcut, then the request is accepted, and a refused or failed accept takes the record
     * back. The card says where it went when Home answered now; when Home was not running the
     * work waits, the card only says it was added, and Home names a failure.
     */
    private void addShortcut(@NonNull LauncherApps.PinItemRequest pin) {
        ShortcutInfo info = pin.getShortcutInfo();
        if (info == null || !pin.isValid()) {
            conclude(getString(R.string.home_pin_failed), false);
            return;
        }
        final String owner = info.getPackage();
        final long serial = serialOf(info);
        final Bundle options = new Bundle();
        CharSequence label = info.getShortLabel();
        options.putString(LauncherWidgetRecord.APP_LABEL, label == null ? "" : label.toString());
        options.putString(LauncherWidgetRecord.SHORTCUT_ID, info.getId());
        final Context app = getApplicationContext();
        final String[] outcome = new String[1];
        boolean ranNow = HomePinHandoff.deliver(target -> {
            int[] placed = target.placeShortcut(owner, serial, options);
            boolean accepted = false;
            if (placed != null) {
                try {
                    accepted = pin.isValid() && pin.accept();
                } catch (RuntimeException e) {
                    accepted = false;
                }
                if (!accepted) target.removeShortcut(placed[0]);
            }
            if (!accepted) {
                outcome[0] = app.getString(R.string.home_pin_failed);
                target.notice(outcome[0]);
            } else {
                outcome[0] = app.getString(placed[2] == 1 ? R.string.home_pin_added_new_page
                    : R.string.home_pin_added);
            }
        });
        boolean failed = ranNow && outcome[0] != null
            && outcome[0].equals(getString(R.string.home_pin_failed));
        conclude(ranNow && outcome[0] != null ? outcome[0] : getString(R.string.home_pin_added),
            !failed);
    }

    /**
     * The widget goes through Home's own add: bind, consent and configuration included, which may
     * leave for other screens. Nothing here may be kept alive for that, so the card goes and Home
     * answers: the request is accepted with the bound id once the widget is on a page.
     */
    private void addWidget(@NonNull LauncherApps.PinItemRequest pin) {
        AppWidgetProviderInfo info = widgetInfo;
        if (info == null || !pin.isValid()) {
            conclude(getString(R.string.home_pin_failed), false);
            return;
        }
        final Context app = getApplicationContext();
        HomePinHandoff.deliver(target -> target.placeWidget(info, new HomePinTarget.WidgetResult() {
            @Override public void onPlaced(int appWidgetId, boolean newPage) {
                Bundle extras = new Bundle();
                extras.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                boolean accepted;
                try {
                    accepted = pin.isValid() && pin.accept(extras);
                } catch (RuntimeException e) {
                    accepted = false;
                }
                target.notice(app.getString(!accepted ? R.string.home_pin_failed
                    : newPage ? R.string.home_pin_added_new_page
                    : R.string.home_pin_widget_added));
            }

            @Override public void onFailed() {
                // Home has said why; the request is left unanswered, which is a decline.
            }
        }));
        goHome();
        finishing = true;
        finish();
    }

    /** Shows the result in the card for a moment, then Home. */
    private void conclude(@NonNull String message, boolean added) {
        finishing = true;
        heading.setText(message);
        detail.setVisibility(View.GONE);
        findViewById(R.id.home_pin_buttons).setVisibility(View.GONE);
        main.postDelayed(() -> {
            if (added) goHome();
            finish();
        }, CONFIRMATION_MS);
    }

    /** Brings the launcher's Home forward, as the Home key does. */
    private void goHome() {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
            // Home is already behind the card; finishing shows it.
        }
    }

    @NonNull
    private String appLabel(@Nullable String packageName) {
        if (packageName == null) return "";
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return packageName;
        }
    }

    private long serialOf(@NonNull ShortcutInfo info) {
        try {
            android.os.UserManager users = getSystemService(android.os.UserManager.class);
            return users == null ? 0L : users.getSerialNumberForUser(info.getUserHandle());
        } catch (RuntimeException e) {
            return 0L;
        }
    }
}
