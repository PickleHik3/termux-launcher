package com.termux.app.wayland;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;

/**
 * Spike: runs the in-process Wayland host and shows each Linux toplevel in its own
 * {@link TextureView}, stacked in equal shares of the height over the system wallpaper.
 * Not exported; start it with {@code am start -n <package>/com.termux.app.wayland.WaylandSpikeActivity}
 * (add {@code --ez xwayland true} to also start rootless Xwayland from the prefix).
 */
public final class WaylandSpikeActivity extends Activity implements WaylandHost.Listener {

    private static final String LOG_TAG = "TLWayland";
    /** The clients' XDG_RUNTIME_DIR, under the prefix's tmp. */
    public static final String SOCKET_DIR = TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH + "/tl-wayland";
    public static final String EXTRA_XWAYLAND = "xwayland";

    private LinearLayout mRoot;
    private TextView mEmpty;
    private boolean mStarted;
    private final LongSparseArray<Pane> mPanes = new LongSparseArray<>();

    /** One toplevel: a title line over its TextureView. */
    private final class Pane implements TextureView.SurfaceTextureListener {
        final long id;
        final LinearLayout row;
        final TextView label;
        final TextureView view;
        Surface surface;
        String title = "";
        String appId = "";
        int bufferWidth;
        int bufferHeight;
        int mouseButton;

        Pane(long id) {
            this.id = id;
            row = new LinearLayout(WaylandSpikeActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            label = new TextView(WaylandSpikeActivity.this);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setTextColor(Color.WHITE);
            label.setShadowLayer(4f, 0f, 1f, Color.BLACK);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            int pad = dp(8);
            label.setPadding(pad, dp(2), pad, dp(2));
            view = new TextureView(WaylandSpikeActivity.this);
            view.setOpaque(false);
            view.setSurfaceTextureListener(this);
            view.setOnTouchListener((v, e) -> onTouch(e));
            view.setOnGenericMotionListener((v, e) -> onGenericMotion(e));
            row.addView(label, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
            row.addView(view, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        void updateLabel() {
            StringBuilder sb = new StringBuilder();
            sb.append(TextUtils.isEmpty(title) ? "(untitled)" : title);
            if (!TextUtils.isEmpty(appId)) sb.append(" · ").append(appId);
            if (bufferWidth > 0) sb.append(" · ").append(bufferWidth).append('×').append(bufferHeight);
            label.setText(sb);
        }

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture texture, int width, int height) {
            surface = new Surface(texture);
            WaylandHost.nativeConfigure(id, width, height);
            WaylandHost.nativeSetSurface(id, surface);
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture texture, int width, int height) {
            WaylandHost.nativeConfigure(id, width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture texture) {
            WaylandHost.nativeSetSurface(id, null);
            if (surface != null) {
                surface.release();
                surface = null;
            }
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture texture) {}

        private boolean onTouch(MotionEvent e) {
            if (e.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) return onMouse(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    int i = e.getActionIndex();
                    WaylandHost.nativeTouch(id, WaylandHost.TOUCH_DOWN, e.getPointerId(i), e.getX(i), e.getY(i));
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP: {
                    int i = e.getActionIndex();
                    WaylandHost.nativeTouch(id, WaylandHost.TOUCH_UP, e.getPointerId(i), e.getX(i), e.getY(i));
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < e.getPointerCount(); i++) {
                        WaylandHost.nativeTouch(id, WaylandHost.TOUCH_MOTION, e.getPointerId(i), e.getX(i), e.getY(i));
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    WaylandHost.nativeTouch(id, WaylandHost.TOUCH_CANCEL, 0, 0f, 0f);
                    return true;
                default:
                    return true;
            }
        }

        private boolean onMouse(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mouseButton = linuxButton(e.getButtonState());
                    WaylandHost.nativePointer(id, WaylandHost.POINTER_MOTION, 0, e.getX(), e.getY());
                    WaylandHost.nativePointer(id, WaylandHost.POINTER_BUTTON_DOWN, mouseButton, e.getX(), e.getY());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    WaylandHost.nativePointer(id, WaylandHost.POINTER_MOTION, 0, e.getX(), e.getY());
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    WaylandHost.nativePointer(id, WaylandHost.POINTER_BUTTON_UP, mouseButton, e.getX(), e.getY());
                    return true;
                default:
                    return true;
            }
        }

        private boolean onGenericMotion(MotionEvent e) {
            if (!e.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_HOVER_ENTER:
                case MotionEvent.ACTION_HOVER_MOVE:
                    WaylandHost.nativePointer(id, WaylandHost.POINTER_MOTION, 0, e.getX(), e.getY());
                    return true;
                case MotionEvent.ACTION_HOVER_EXIT:
                    WaylandHost.nativePointer(id, WaylandHost.POINTER_LEAVE, 0, 0f, 0f);
                    return true;
                case MotionEvent.ACTION_SCROLL:
                    WaylandHost.nativePointer(id, WaylandHost.POINTER_AXIS, 0,
                        e.getAxisValue(MotionEvent.AXIS_HSCROLL) * 15f, -e.getAxisValue(MotionEvent.AXIS_VSCROLL) * 15f);
                    return true;
                default:
                    return false;
            }
        }
    }

    private static int linuxButton(int buttonState) {
        if ((buttonState & MotionEvent.BUTTON_SECONDARY) != 0) return WaylandHost.BTN_RIGHT;
        if ((buttonState & MotionEvent.BUTTON_TERTIARY) != 0) return WaylandHost.BTN_MIDDLE;
        return WaylandHost.BTN_LEFT;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRoot = new LinearLayout(this);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        mRoot.setFitsSystemWindows(true);
        mEmpty = new TextView(this);
        mEmpty.setTextColor(Color.WHITE);
        mEmpty.setShadowLayer(4f, 0f, 1f, Color.BLACK);
        mEmpty.setGravity(Gravity.CENTER);
        int pad = dp(24);
        mEmpty.setPadding(pad, pad, pad, pad);
        mRoot.addView(mEmpty, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT));
        setContentView(mRoot);

        if (!WaylandHost.load()) {
            mEmpty.setText("This build has no Wayland host (libtlwayland.so).");
            return;
        }

        File dir = new File(SOCKET_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            mEmpty.setText("Cannot create " + SOCKET_DIR);
            return;
        }
        try {
            Os.chmod(SOCKET_DIR, 0700);
        } catch (ErrnoException e) {
            Log.w(LOG_TAG, "chmod " + SOCKET_DIR + ": " + e.getMessage());
        }

        File xkb = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/share/X11/xkb");
        String xkbRoot = xkb.isDirectory() ? xkb.getAbsolutePath() : null;
        String xwayland = null;
        if (getIntent().getBooleanExtra(EXTRA_XWAYLAND, false)) {
            File bin = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/Xwayland");
            if (bin.canExecute()) xwayland = bin.getAbsolutePath();
            else Log.w(LOG_TAG, "no executable " + bin);
        }

        Point size = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(size);
        WaylandHost.setListener(this);
        mStarted = WaylandHost.nativeStart(SOCKET_DIR, xkbRoot, xwayland, size.x, size.y);
        mEmpty.setText(mStarted
            ? "Waiting for Wayland clients.\nXDG_RUNTIME_DIR=" + SOCKET_DIR + "\nWAYLAND_DISPLAY=wayland-0"
                + (xwayland != null ? "\nDISPLAY: see logcat TLWayland" : "")
            : "The Wayland host did not start; see logcat TLWayland.");
    }

    @Override
    protected void onDestroy() {
        WaylandHost.setListener(null);
        if (mStarted) {
            WaylandHost.nativeStop();
            mStarted = false;
        }
        mPanes.clear();
        super.onDestroy();
    }

    @Override
    public void onToplevelCreated(long id, String title, String appId) {
        if (mPanes.get(id) != null || isDestroyed()) return;
        Pane pane = new Pane(id);
        pane.title = title;
        pane.appId = appId;
        pane.updateLabel();
        mPanes.put(id, pane);
        mEmpty.setVisibility(View.GONE);
        mRoot.addView(pane.row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    @Override
    public void onToplevelTitle(long id, String title, String appId) {
        Pane pane = mPanes.get(id);
        if (pane == null) return;
        pane.title = title;
        pane.appId = appId;
        pane.updateLabel();
    }

    @Override
    public void onToplevelBufferSize(long id, int width, int height) {
        Pane pane = mPanes.get(id);
        if (pane == null) return;
        pane.bufferWidth = width;
        pane.bufferHeight = height;
        pane.updateLabel();
    }

    @Override
    public void onToplevelDestroyed(long id) {
        Pane pane = mPanes.get(id);
        if (pane == null) return;
        mPanes.remove(id);
        mRoot.removeView(pane.row);
        if (mPanes.size() == 0) mEmpty.setVisibility(View.VISIBLE);
    }
}
