package com.rockjesus.icarplay;

import io.github.jqssun.airplay.R;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.DisplayMetrics;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import local.hicarlab.HomeActivity;

/** 内置悬浮按钮：悬浮球 + AirPlay / Android Auto / HiCar / CarPlay 快捷面板。 */
public class FloatService extends Service {

    private static final String CHANNEL = "icarplay_float";
    private static final int NOTIF_ID = 11;

    private WindowManager wm;
    private SharedPreferences prefs;

    private FrameLayout ball;
    private LinearLayout panel;
    private View panelScrim;
    private WindowManager.LayoutParams ballParams;
    private WindowManager.LayoutParams panelParams;

    private int touchSlop;
    private float downX, downY;
    private int startX, startY;
    private boolean dragging;
    private int screenW, screenH;
    private int safeL, safeT, safeR, safeB;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        prefs = getSharedPreferences("icarplay", MODE_PRIVATE);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        startForeground(NOTIF_ID, buildNotification());

        if (FloatAccessibilityService.isRunning()) {
            // 无障碍层已实际接管，避免出现双球；仅“已勾选”但尚未连接时允许普通层兜底。
            stopSelf();
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            toast("没有悬浮窗权限，无法显示悬浮按钮");
            stopSelf();
            return;
        }
        showBall();
        prefs.edit().putBoolean("float_running", true).apply();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (FloatAccessibilityService.isRunning()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("没有悬浮窗权限，无法显示悬浮按钮");
            return START_NOT_STICKY;
        }
        if (ball == null) {
            try {
                showBall();
            } catch (RuntimeException e) {
                toast("悬浮按钮显示失败：" + e.getClass().getSimpleName());
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        prefs.edit().putBoolean("float_running", true).apply();
        return START_STICKY;
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "iCarPlay 悬浮按钮", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, IcarLauncherActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setContentTitle("iCarPlay")
                .setContentText("内置悬浮按钮运行中")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi)
                .setOngoing(true);
        return b.build();
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
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
        int margin = dp(10);
        int left = Math.max(0, safeL);
        int top = Math.max(0, safeT);
        int right = safeR > 0 ? safeR : screenW;
        int bottom = safeB > 0 ? safeB : screenH;
        if (right <= left || bottom <= top) return;
        // LayoutParams 的 y 原点在状态栏内侧，实际窗口 y = params.y + top inset。
        // 可用高度必须先扣掉 top，否则球会被状态栏高度顶出屏幕底部。
        int usableW = right - left;
        int usableH = bottom - top;
        int maxX = Math.max(margin, usableW - size - margin);
        int maxY = Math.max(margin, usableH - size - margin);
        ballParams.x = Math.max(margin, Math.min(ballParams.x, maxX));
        ballParams.y = Math.max(margin, Math.min(ballParams.y, maxY));
    }

    private void updateScreenSize() {
        // 不用 getRealMetrics：部分车机/模拟器会返回物理竖屏尺寸（如 900×1600），
        // 与实际 1600×900 的显示区不一致，导致悬浮球能拖到屏幕外。
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                WindowMetrics wmMetrics = wm.getCurrentWindowMetrics();
                Rect bounds = wmMetrics.getBounds();
                WindowInsets insets = wmMetrics.getWindowInsets();
                Insets bars = insets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                screenW = bounds.width();
                screenH = bounds.height();
                safeL = bars.left;
                safeT = bars.top;
                safeR = screenW - bars.right;
                safeB = screenH - bars.bottom;
                return;
            }
        } catch (Throwable ignored) { }
        try {
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(dm);
            screenW = dm.widthPixels;
            screenH = dm.heightPixels;
            safeL = 0;
            safeT = 0;
            safeR = screenW;
            safeB = screenH;
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
        panel.addView(panelButton("无线传文件", new View.OnClickListener() {
            public void onClick(View v) { open(TransferActivity.class); }
        }));
        panel.addView(panelButton("应用管理", new View.OnClickListener() {
            public void onClick(View v) { open(AppManageActivity.class); }
        }));
        panel.addView(panelButton("HA 面板", new View.OnClickListener() {
            public void onClick(View v) { open(HaPanelActivity.class); }
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
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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

        // 菜单打开时铺一层透明全屏触摸层：点菜单以外的任何位置都先收起菜单，
        // 避免事件穿透到下层应用后菜单留在屏幕上。
        addPanelScrim();
        wm.addView(panel, panelParams);
    }

    private void addPanelScrim() {
        if (panelScrim != null) return;
        panelScrim = new View(this);
        panelScrim.setBackgroundColor(Color.TRANSPARENT);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        panelScrim.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    panelScrim.post(new Runnable() {
                        public void run() { hidePanel(); }
                    });
                }
                return true;
            }
        });
        wm.addView(panelScrim, lp);
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
        if (panelScrim != null) {
            try { wm.removeView(panelScrim); } catch (Exception ignored) { }
            panelScrim = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hidePanel();
        if (ball != null) {
            try { wm.removeView(ball); } catch (Exception ignored) { }
            ball = null;
        }
        if (!PermissionOnboarding.isAccessibilityEnabled(this)) {
            prefs.edit().putBoolean("float_running", false).apply();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
