package com.termux.app.surfaces;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.AzScrubRowView;
import com.termux.app.fragments.settings.MiniatureDragPolicy;
import com.termux.app.haptics.Haptics;
import com.termux.app.launcher.popup.AnchoredMenu;
import com.termux.app.launcher.popup.AnchoredMenuTheme;
import com.termux.app.launcher.popup.AppMenuCard;
import com.termux.app.launcher.popup.MenuRowFactory;
import com.termux.app.layouteditor.LayoutEditorPlan;
import com.termux.app.material.M3;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.statusbar.PinnedNotificationsView;
import com.termux.app.terminal.inappkeyboard.FloatingKeyboardFrame;
import com.termux.app.surfaces.SurfaceLongPressRoute.Answer;
import com.termux.app.surfaces.SurfaceLongPressRoute.Box;
import com.termux.app.surfaces.SurfaceLongPressRoute.Target;
import com.termux.app.surfaces.SurfaceLongPressRoute.Zone;
import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.extrakeys.ExtraKeysView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The long-press popup on the dock, the status bar and the keyboard (editor-redesign §3): watches
 * every touch the window gets, and a hold that stays still for the long-press timeout on one of
 * the three targets — where no child keeps a long-press of its own
 * ({@link SurfaceLongPressRoute}) — opens a small card in the App menu's style beside it, with
 * the target outlined. Edit opens the target's card through its door, Hide puts the target away
 * the way the card's eye does, and the keyboard's card adds Docked and Floating.
 *
 * <pre>
 *   // TermuxActivity.dispatchTouchEvent, before anything else sees the event:
 *   if (mSurfaceLongPress.onWindowTouch(ev)) return true;
 * </pre>
 *
 * <p>Once the card opens the finger's stream is the popup's: the views get a cancel, so the bar
 * under the finger lets go of its press and no tap lands on release, and every later event of
 * the stream is swallowed. A move past the touch slop before the timeout is a swipe, a scrub or
 * a drag, and the hold is forgotten.</p>
 */
public final class SurfaceLongPress {

    /** The activity's half: its views, its doors, its place. */
    public interface Host {
        @NonNull Context context();

        /** The window's root, which the card is shown from and the outline is drawn over. */
        @NonNull View root();

        @Nullable View findView(@IdRes int id);

        /**
         * Whether a press at this screen point is someone else's before any target is asked: the
         * editor is open, the drawer covers the dock, the finger is on the A–Z pull tab.
         */
        boolean pressBlocked(float rawX, float rawY);

        /** The window's views are sent {@code cancel}, as if the stream had been taken from them. */
        void cancelViewStream(@NonNull MotionEvent cancel);

        /**
         * Opens the editor through {@code door} (which logs its line), the Dock card on
         * {@code bar}'s settings when the door is the dock's.
         */
        void openCard(@NonNull EditorDoor door, @Nullable Element bar);

        @Nullable PlaceLayoutStore places();

        /** The page on screen, whose layout a Hide writes. */
        @NonNull PaneWallPage page();

        @NonNull PlaceOrientation orientation();

        /** The chrome is arranged again from the store, as after an editor write. */
        void applyArrangement();

        /** The in-app keyboard goes down, after its switch has been turned off. */
        void hideKeyboard();
    }

    /** The surfaces each target is made of, in the route's priority order. */
    private static final int[] KEYBOARD_IDS = {R.id.inapp_keyboard_container};
    private static final int[] STATUS_IDS = {R.id.terminal_window_bar_host};
    private static final int[] AZ_IDS = {R.id.place_az_bar_host, R.id.apps_bar_az_row};
    private static final int[] EXTRA_KEYS_IDS = {R.id.place_extra_keys_host,
        R.id.terminal_toolbar_host};
    private static final int[] APPS_IDS = {R.id.apps_bar_row_host, R.id.place_apps_bar_host};
    private static final int[] DOCK_FRAME_IDS = {R.id.accessory_stack_container,
        R.id.place_off_dock_plank_host};

    private static final float OUTLINE_STROKE_DP = 2f;
    private static final float OUTLINE_RADIUS_DP = 12f;

    @NonNull private final Host mHost;
    @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());
    @NonNull private final Runnable mFire = this::fire;
    @NonNull private final int[] mLocation = new int[2];

    @Nullable private Zone mArmed;
    private float mDownX;
    private float mDownY;
    private long mDownTime;
    /** From the card's opening to the finger's lift: the stream is the popup's. */
    private boolean mOwnsStream;

    @Nullable private AnchoredMenu mMenu;
    @Nullable private GradientDrawable mOutline;

    public SurfaceLongPress(@NonNull Host host) {
        mHost = host;
    }

    // ------------------------------------------------------------------------ the touch

    /**
     * Sees one window touch event before any view does.
     *
     * @return whether the popup owns this event and the views are to see nothing of it
     */
    public boolean onWindowTouch(@NonNull MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                disarm();
                mOwnsStream = false;
                arm(ev);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (mOwnsStream) return true;
                if (mArmed != null && !SurfaceLongPressRoute.stillHolding(ev.getRawX() - mDownX,
                        ev.getRawY() - mDownY, slopPx()))
                    disarm();
                return false;
            case MotionEvent.ACTION_POINTER_DOWN:
                disarm();
                return mOwnsStream;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                disarm();
                boolean owned = mOwnsStream;
                mOwnsStream = false;
                return owned;
            }
            default:
                return mOwnsStream;
        }
    }

    /** Puts the card away, as Back or a page change would. */
    public void dismiss() {
        disarm();
        if (mMenu != null) mMenu.dismiss();
    }

    /** The App menu's Edit dock: the Dock card, on the apps row the icon stands in. */
    public void editDock() {
        mHost.openCard(EditorDoor.APP_MENU_EDIT_DOCK, Element.APPS);
    }

    private void arm(@NonNull MotionEvent ev) {
        float x = ev.getRawX();
        float y = ev.getRawY();
        if (mHost.pressBlocked(x, y)) return;
        List<Zone> zones = zones();
        if (zones.isEmpty()) return;
        List<Box> owners = new ArrayList<>(2);
        // Only the zone under the finger is walked for children that keep a long-press.
        Answer probe = SurfaceLongPressRoute.route(x, y, zones, Collections.emptyList());
        if (probe.zone == null) return;
        collectOwners(viewOf(probe.zone), x, y, owners);
        Answer answer = SurfaceLongPressRoute.route(x, y, zones, owners);
        if (!answer.opensPopup()) return;
        mArmed = answer.zone;
        mDownX = x;
        mDownY = y;
        mDownTime = ev.getDownTime();
        mHandler.postDelayed(mFire, ViewConfiguration.getLongPressTimeout());
    }

    private void disarm() {
        mArmed = null;
        mHandler.removeCallbacks(mFire);
    }

    private void fire() {
        Zone zone = mArmed;
        mArmed = null;
        if (zone == null) return;
        mOwnsStream = true;
        View root = mHost.root();
        Haptics.tick(root, HapticFeedbackConstants.LONG_PRESS);
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(mDownTime, now, MotionEvent.ACTION_CANCEL,
            mDownX, mDownY, 0);
        try {
            mHost.cancelViewStream(cancel);
        } finally {
            cancel.recycle();
        }
        show(zone, mDownX, mDownY);
    }

    private int slopPx() {
        return ViewConfiguration.get(mHost.context()).getScaledTouchSlop();
    }

    // ------------------------------------------------------------------------ the targets

    /** The view each zone of the last {@link #zones()} was read off, for the owners' walk. */
    private final java.util.Map<Zone, View> mZoneViews = new java.util.IdentityHashMap<>();

    /** Every target on screen now, in the route's priority order. */
    @NonNull
    private List<Zone> zones() {
        mZoneViews.clear();
        List<Zone> zones = new ArrayList<>(8);
        addZones(zones, Target.KEYBOARD, null, KEYBOARD_IDS);
        addZones(zones, Target.STATUS, null, STATUS_IDS);
        addZones(zones, Target.DOCK, Element.AZ, AZ_IDS);
        addZones(zones, Target.DOCK, Element.EXTRA_KEYS, EXTRA_KEYS_IDS);
        addZones(zones, Target.DOCK, Element.APPS, APPS_IDS);
        addZones(zones, Target.DOCK, null, DOCK_FRAME_IDS);
        return zones;
    }

    private void addZones(@NonNull List<Zone> out, @NonNull Target target, @Nullable Element bar,
                          @NonNull int[] ids) {
        for (int id : ids) {
            View view = mHost.findView(id);
            // A Floating keyboard's frame is its card: the handle row above the keys included.
            if (target == Target.KEYBOARD && view != null
                && view.getParent() instanceof FloatingKeyboardFrame)
                view = (View) view.getParent();
            Box box = boxOf(view);
            if (box == null) continue;
            Zone zone = new Zone(target, bar, box);
            mZoneViews.put(zone, view);
            out.add(zone);
        }
    }

    @Nullable
    private View viewOf(@NonNull Zone zone) {
        return mZoneViews.get(zone);
    }

    /** {@code view}'s box on the screen, or null while it is not on screen. */
    @Nullable
    private Box boxOf(@Nullable View view) {
        if (view == null || !view.isShown() || view.getWidth() <= 0 || view.getHeight() <= 0)
            return null;
        view.getLocationOnScreen(mLocation);
        return new Box(mLocation[0], mLocation[1], mLocation[0] + view.getWidth(),
            mLocation[1] + view.getHeight());
    }

    /**
     * The boxes of the children under ({@code x}, {@code y}) that keep a long-press of their own:
     * anything long-clickable (app icons, window chips), the A–Z letters, the keyboard's keys (only
     * where a key's cell is, {@link juloo.keyboard2.Keyboard2View#hasKeyAt}), the pinned
     * notifications, and each extra key.
     */
    private void collectOwners(@Nullable View view, float x, float y, @NonNull List<Box> out) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;
        Box box = boxOf(view);
        if (box != null && box.contains(x, y)) {
            if (view instanceof juloo.keyboard2.Keyboard2View) {
                // Only a key's cell is the key's; the margins round the rows are the frame.
                if (((juloo.keyboard2.Keyboard2View) view).hasKeyAt(x - box.left, y - box.top))
                    out.add(box);
            } else if (ownsLongPress(view)) {
                out.add(box);
            }
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++)
            collectOwners(group.getChildAt(i), x, y, out);
    }

    private static boolean ownsLongPress(@NonNull View view) {
        return view.isLongClickable()
            || view instanceof AzScrubRowView
            || view instanceof PinnedNotificationsView
            || view.getParent() instanceof ExtraKeysView;
    }

    // ------------------------------------------------------------------------ the card

    private void show(@NonNull Zone zone, float x, float y) {
        Context context = mHost.context();
        View root = mHost.root();
        Target target = zone.target;
        Element bar = zone.bar;
        String title = titleOf(context, target, bar);

        List<List<AppMenuCard.Item>> groups = new ArrayList<>(2);
        groups.add(Arrays.asList(AppMenuCard.Item.header(title),
            new AppMenuCard.Item(context.getString(R.string.surface_popup_edit),
                R.drawable.ic_ms_edit, () -> {
                    dismiss();
                    mHost.openCard(target.door, bar);
                })));
        List<AppMenuCard.Item> second = new ArrayList<>(2);
        second.add(new AppMenuCard.Item(context.getString(R.string.surface_popup_hide),
            R.drawable.ic_ms_visibility_off, () -> {
                dismiss();
                hide(target, bar);
            }));
        if (target.offersForm()) second.add(AppMenuCard.Item.custom(formChoice(context)));
        groups.add(second);

        if (mMenu == null) mMenu = new AnchoredMenu(root, menuTheme(context));
        AppMenuCard card = AppMenuCard.build(context, new MenuRowFactory(context,
            menuTheme(context)), groups);
        Box anchor = SurfaceLongPressRoute.anchor(zone.box, x, y);
        Rect anchorRect = new Rect(Math.round(anchor.left), Math.round(anchor.top),
            Math.round(anchor.right), Math.round(anchor.bottom));
        outline(zone.box);
        mMenu.showAppMenu(card, anchorRect, this::removeOutline,
            context.getString(R.string.surface_popup_a11y_pane, title));
    }

    /** The card's caption: what was pressed, by the name its card or its bar goes by. */
    @NonNull
    private static String titleOf(@NonNull Context context, @NonNull Target target,
                                  @Nullable Element bar) {
        if (target == Target.DOCK && bar != null) {
            switch (bar) {
                case APPS: return context.getString(R.string.surface_popup_apps_row);
                case AZ: return context.getString(R.string.editor_dock_bar_az);
                case EXTRA_KEYS: return context.getString(R.string.editor_dock_bar_extra_keys);
                default: break;
            }
        }
        return context.getString(target.card.titleRes);
    }

    /** Docked and Floating, the keyboard's form as it stands marked; a pick applies at once. */
    @NonNull
    private View formChoice(@NonNull Context context) {
        PlaceLayout.KeyboardForm form = keyboardForm();
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(100f * density);
        track.setColor(Color.TRANSPARENT);
        track.setStroke(Math.max(1, Math.round(density)), M3.outlineVariant(context));
        pill.setBackground(track);
        pill.addView(formSegment(context, R.string.appearance_form_docked,
                form == PlaceLayout.KeyboardForm.DOCKED, PlaceLayout.KeyboardForm.DOCKED),
            new LinearLayout.LayoutParams(0, Math.round(36 * density), 1f));
        pill.addView(formSegment(context, R.string.appearance_form_floating,
                form == PlaceLayout.KeyboardForm.FLOATING, PlaceLayout.KeyboardForm.FLOATING),
            new LinearLayout.LayoutParams(0, Math.round(36 * density), 1f));
        return pill;
    }

    @NonNull
    private TextView formSegment(@NonNull Context context, int labelRes, boolean selected,
                                 @NonNull PlaceLayout.KeyboardForm form) {
        float density = context.getResources().getDisplayMetrics().density;
        TextView segment = new TextView(context);
        M3.textAppearance(segment, com.google.android.material.R.attr.textAppearanceLabelLarge);
        segment.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        segment.setText(labelRes);
        segment.setSingleLine(true);
        segment.setGravity(Gravity.CENTER);
        segment.setMinWidth(0);
        segment.setTextColor(selected ? M3.color(context,
            com.google.android.material.R.attr.colorOnSecondaryContainer,
            android.R.color.black) : M3.onSurface(context));
        if (selected) {
            GradientDrawable fill = new GradientDrawable();
            fill.setCornerRadius(100f * density);
            fill.setColor(M3.color(context,
                com.google.android.material.R.attr.colorSecondaryContainer,
                android.R.color.darker_gray));
            segment.setBackground(fill);
        }
        segment.setSelected(selected);
        segment.setClickable(true);
        segment.setOnClickListener(v -> {
            dismiss();
            setKeyboardForm(form);
        });
        return segment;
    }

    /** The pressed target outlined while its card is up, drawn over the window's views. */
    private void outline(@NonNull Box box) {
        removeOutline();
        View root = mHost.root();
        float density = root.getResources().getDisplayMetrics().density;
        int stroke = Math.max(1, Math.round(OUTLINE_STROKE_DP * density));
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.TRANSPARENT);
        outline.setStroke(stroke, M3.primary(root.getContext()));
        outline.setCornerRadius(OUTLINE_RADIUS_DP * density);
        root.getLocationOnScreen(mLocation);
        // Inside the box by half the stroke, so the whole line is on the target.
        int inset = stroke / 2;
        outline.setBounds(Math.round(box.left) - mLocation[0] + inset,
            Math.round(box.top) - mLocation[1] + inset,
            Math.round(box.right) - mLocation[0] - inset,
            Math.round(box.bottom) - mLocation[1] - inset);
        root.getOverlay().add(outline);
        mOutline = outline;
    }

    private void removeOutline() {
        if (mOutline == null) return;
        mHost.root().getOverlay().remove(mOutline);
        mOutline = null;
    }

    // ------------------------------------------------------------------------ the actions

    /**
     * Puts the target away through the editor's own write (the card's eye): the bar or the status
     * bar dropped in the tray, or the keyboard's switch turned off. The editor's tray and the
     * card's eye are the way back.
     */
    private void hide(@NonNull Target target, @Nullable Element bar) {
        LayoutEditorPlan plan = plan();
        if (plan == null) return;
        Element element = SurfaceLongPressRoute.hides(target, bar);
        if (element == null) {
            plan.setKeyboardShown(false);
            mHost.hideKeyboard();
        } else {
            plan.drop(MiniatureDragPolicy.Bar.of(element), null);
        }
        mHost.applyArrangement();
    }

    @NonNull
    private PlaceLayout.KeyboardForm keyboardForm() {
        LayoutEditorPlan plan = plan();
        return plan == null ? PlaceLayout.KeyboardForm.DOCKED : plan.keyboardForm();
    }

    private void setKeyboardForm(@NonNull PlaceLayout.KeyboardForm form) {
        LayoutEditorPlan plan = plan();
        if (plan == null || plan.keyboardForm() == form) return;
        plan.setKeyboardForm(form);
        mHost.applyArrangement();
    }

    /** A one-write plan on the page on screen: the editor's own path into the store. */
    @Nullable
    private LayoutEditorPlan plan() {
        PlaceLayoutStore places = mHost.places();
        return places == null ? null
            : LayoutEditorPlan.enter(places, mHost.page(), mHost.orientation());
    }

    // ------------------------------------------------------------------------ the material

    /** The App menu's card draws its own shades; the theme only colours the rows' ink. */
    @NonNull
    private static AnchoredMenuTheme menuTheme(@NonNull Context context) {
        int ink = M3.onSurface(context);
        return new AnchoredMenuTheme() {
            @Override public int textColor() { return ink; }
            @Override public int selectedTextColor() { return ink; }
            @Override public int opacityPercent() { return 100; }
            @Override public boolean blurEnabled() { return false; }
            @Override public int blurRadiusDp() { return 0; }
        };
    }
}
