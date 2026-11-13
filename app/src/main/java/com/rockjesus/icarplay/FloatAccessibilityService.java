package com.rockjesus.icarplay;

import io.github.jqssun.airplay.R;
import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import local.hicarlab.HomeActivity;

/**
 * 车机桌面用的无障碍悬浮球。
 *
 * SYNC+ 1.0 的原车桌面是系统层窗口，普通 TYPE_APPLICATION_OVERLAY 会被桌面盖住。
 * 这里使用 TYPE_ACCESSIBILITY_OVERLAY：只绘制悬浮按钮和快捷面板，不读取窗口内容，
 * 也不监听输入内容。用户在系统无障碍设置里手动开启后生效。
 */
public class FloatAccessibilityService extends AccessibilityService {

    private static volatile FloatAccessibilityService INSTANCE;

    private WindowManager wm;
    private SharedPreferences prefs;

    private FrameLayout ball;
    private LinearLayout panel;
    private WindowManager.LayoutParams ballParams;
    private WindowManager.LayoutParams panelParams;

    private int touchSlop;
    private float downX, downY;
    private int startX, startY;
    private boolean dragging;
    private int screenW, screenH;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        prefs = getSharedPreferences("icarplay", MODE_PRIVATE);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        INSTANCE = this;
        // 无障碍层已经可用，普通悬浮窗服务必须让位，避免出现两个悬浮按钮。
        try { stopService(new Intent(this, FloatService.class)); } catch (Throwable ignored) { }
        if (!prefs.getBoolean("float_auto", true)) return;
        if (ball == null) {
            try {
                showBall();
            } catch (RuntimeException e) {
                toast("悬浮按钮显示失败：" + e.getClass().getSimpleName());
                return;
            }
        }
        prefs.edit().putBoolean("float_running", true).apply();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不读取任何窗口内容。
    }

    @Override
    public void onInterrupt() {
        // 无需中断处理。
    }

    public static boolean isRunning() {
        return INSTANCE != null;
    }

    /** 从首页或通知恢复悬浮按钮。返回是否已由无障碍层接管。 */
    public static boolean showIfRunning() {
        FloatAccessibilityService s = INSTANCE;
        if (s == null) return false;
        s.showFromToggle();
        return true;
    }

    /** 从首页关闭悬浮按钮，但保留无障碍服务本身。 */
    public static void hideIfRunning() {
        FloatAccessibilityService s = INSTANCE;
        if (s != null) s.hideFromToggle();
    }

    private void showFromToggle() {
        prefs.edit().putBoolean("float_auto", true).apply();
        if (ball == null) {
            try {
                showBall();
            } catch (RuntimeException e) {
                toast("悬浮按钮显示失败：" + e.getClass().getSimpleName());
                return;
            }
        }
        prefs.edit().putBoolean("float_running", true).apply();
    }

    private void hideFromToggle() {
        hidePanel();
        if (ball != null) {
            try { wm.removeView(ball); } catch (Exception ignored) { }
            ball = null;
        }
        prefs.edit().putBoolean("float_running", false)
                .putBoolean("float_auto", false).apply();
    }

    private void showBall() {
        ball = new FrameLayout(this);
        ball.setBackground(PageUi.cyberBall(this));

        ImageView icon = new ImageView(this);
        icon.setImageBitmap(PageUi.gradientIcon(this, R.drawable.ic_float_ball,
                PageUi.TITLE_GRADIENT, dp(54)));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        iconParams.setMargins(dp(10), dp(10), dp(10), dp(10));
        ball.addView(icon, iconParams);

        int size = dp(54);
        ballParams = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        ballParams.gravity = Gravity.TOP | Gravity.START;
        updateScreenSize();
        ballParams.x = prefs.getInt("float_x", dp(24));
        ballParams.y = prefs.getInt("float_y", dp(120));
        boundBall();
        prefs.edit().putInt("float_x", ballParams.x)
                .putInt("float_y", ballParams.y).apply();

        ball.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = ballParams.x;
                        startY = ballParams.y;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - downX);
                        int dy = (int) (e.getRawY() - downY);
                        if (!dragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                            dragging = true;
                        }
                        if (dragging) {
                            ballParams.x = startX + dx;
                            ballParams.y = startY + dy;
                            boundBall();
                            wm.updateViewLayout(ball, ballParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        boundBall();
                        prefs.edit().putInt("float_x", ballParams.x)
                                .putInt("float_y", ballParams.y).apply();
                        if (!dragging) togglePanel();
                        return true;
                }
                return false;
            }
        });

        clampBall();
        wm.addView(ball, ballParams);
    }

    /** 把悬浮球坐标限制在屏幕内，避免拖出屏幕后找不回来。 */
    private void boundBall() {
        if (ballParams == null) return;
        if (screenW <= 0 || screenH <= 0) updateScreenSize();
        int size = Math.max(0, ballParams.width);
        int margin = dp(8);
        ballParams.x = Math.max(margin, Math.min(ballParams.x,
                Math.max(margin, screenW - size - margin)));
        ballParams.y = Math.max(margin, Math.min(ballParams.y,
                Math.max(margin, screenH - size - margin)));
    }

    private void updateScreenSize() {
        try {
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            screenW = dm.widthPixels;
            screenH = dm.heightPixels;
        } catch (Throwable ignored) { }
    }

    private void clampBall() {
        if (ball == null || ballParams == null || wm == null) return;
        updateScreenSize();
        boundBall();
        try {
            wm.updateViewLayout(ball, ballParams);
        } catch (Throwable ignored) { }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hidePanel();
        clampBall();
    }

    private void togglePanel() {
        if (panel != null) {
            hidePanel();
            return;
        }
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(PageUi.surface(this, PageUi.PURPLE, 12f));
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));

        panel.addView(panelButton("AirPlay", new View.OnClickListener() {
            public void onClick(View v) { open(AirPlayActivity.class); }
        }));
        panel.addView(panelButton("Android Auto", new View.OnClickListener() {
            public void onClick(View v) { open(AndroidAutoActivity.class); }
        }));
        panel.addView(panelButton("HiCar", new View.OnClickListener() {
            public void onClick(View v) { open(HomeActivity.class); }
        }));
        panel.addView(panelButton("CarPlay", new View.OnClickListener() {
            public void onClick(View v) { open(CarPlayActivity.class); }
        }));
        panel.addView(panelButton("认证设置", new View.OnClickListener() {
            public void onClick(View v) { open(AuthSettingsActivity.class); }
        }));
        panel.addView(panelButton("系统设置", new View.OnClickListener() {
            public void onClick(View v) { openSystemSettings(); }
        }));
        panel.addView(panelButton("iCarPlay主界面", new View.OnClickListener() {
            public void onClick(View v) { open(IcarLauncherActivity.class); }
        }));
        panel.addView(panelButton("车机主屏幕", new View.OnClickListener() {
            public void onClick(View v) { goCarHome(); }
        }));

        panelParams = new WindowManager.LayoutParams(
                dp(170), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;
        int panelW = dp(170);
        panel.measure(
                View.MeasureSpec.makeMeasureSpec(panelW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int panelH = panel.getMeasuredHeight();
        int x = Math.min(ballParams.x - dp(56), screenW - panelW - dp(8));
        x = Math.max(0, Math.min(x, Math.max(0, screenW - panelW - dp(8))));
        int yBelow = ballParams.y + ballParams.height + dp(8);
        int y = yBelow + panelH > screenH ? ballParams.y - panelH - dp(8) : yBelow;
        y = Math.max(0, Math.min(y, Math.max(0, screenH - panelH)));
        panelParams.x = x;
        panelParams.y = y;

        wm.addView(panel, panelParams);
    }

    private Button panelButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(PageUi.TEXT);
        b.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        b.setLetterSpacing(0.03f);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
        return b;
    }

    private void open(Class<?> cls) {
        hidePanel();
        Intent i = new Intent(this, cls);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
    }

    private void openSystemSettings() {
        hidePanel();
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            toast("无法打开系统设置");
        }
    }

    private void goCarHome() {
        hidePanel();
        if (HomeSafety.goToSystemHome(this)) return;
        toast("本应用当前是默认桌面；请先在“桌面设置”切回原车机桌面");
        HomeSafety.openHomeSettings(this);
    }

    private void hidePanel() {
        if (panel != null) {
            try { wm.removeView(panel); } catch (Exception ignored) { }
            panel = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        INSTANCE = null;
        hidePanel();
        if (ball != null) {
            try { wm.removeView(ball); } catch (Exception ignored) { }
            ball = null;
        }
        prefs.edit().putBoolean("float_running", false).apply();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
