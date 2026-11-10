package com.rockjesus.icarplay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import io.github.jqssun.airplay.MainActivity;

/** 内置小白点：悬浮球 + AirPlay / CarPlay / 设置快捷面板。 */
public class FloatService extends Service {

    private static final String CHANNEL = "icarplay_float";
    private static final int NOTIF_ID = 11;

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

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        prefs = getSharedPreferences("icarplay", MODE_PRIVATE);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        startForeground(NOTIF_ID, buildNotification());

        if (!Settings.canDrawOverlays(this)) {
            toast("没有悬浮窗权限，无法显示小白点");
            stopSelf();
            return;
        }
        showBall();
        prefs.edit().putBoolean("float_running", true).apply();
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "iCarPlay 小白点", NotificationManager.IMPORTANCE_LOW);
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
                .setContentText("内置小白点运行中")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi)
                .setOngoing(true);
        return b.build();
    }

    private void showBall() {
        ball = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#E6EDF3"));
        bg.setStroke(dp(1), Color.parseColor("#8B949E"));
        ball.setBackground(bg);

        TextView label = new TextView(this);
        label.setText("iC");
        label.setTextColor(Color.parseColor("#0E1216"));
        label.setTextSize(15);
        label.setGravity(Gravity.CENTER);
        ball.addView(label, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        int size = dp(54);
        ballParams = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        ballParams.gravity = Gravity.TOP | Gravity.START;
        ballParams.x = prefs.getInt("float_x", dp(24));
        ballParams.y = prefs.getInt("float_y", dp(120));

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
                            wm.updateViewLayout(ball, ballParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        prefs.edit().putInt("float_x", ballParams.x)
                                .putInt("float_y", ballParams.y).apply();
                        if (!dragging) togglePanel();
                        return true;
                }
                return false;
            }
        });

        wm.addView(ball, ballParams);
    }

    private void togglePanel() {
        if (panel != null) {
            hidePanel();
            return;
        }
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F21A2026"));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.parseColor("#30363D"));
        panel.setBackground(bg);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));

        panel.addView(panelButton("AirPlay", new View.OnClickListener() {
            public void onClick(View v) { open(MainActivity.class); }
        }));
        panel.addView(panelButton("CarPlay", new View.OnClickListener() {
            public void onClick(View v) { open(CarPlayActivity.class); }
        }));
        panel.addView(panelButton("认证设置", new View.OnClickListener() {
            public void onClick(View v) { open(AuthSettingsActivity.class); }
        }));
        panel.addView(panelButton("主界面", new View.OnClickListener() {
            public void onClick(View v) { open(IcarLauncherActivity.class); }
        }));
        panel.addView(panelButton("关闭小白点", new View.OnClickListener() {
            public void onClick(View v) {
                prefs.edit().putBoolean("float_running", false).apply();
                stopSelf();
            }
        }));

        panelParams = new WindowManager.LayoutParams(
                dp(170), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        panelParams.x = Math.max(0, ballParams.x - dp(56));
        panelParams.y = Math.min(ballParams.y + dp(64), Math.max(0, screenH - dp(270)));

        wm.addView(panel, panelParams);
    }

    private Button panelButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(Color.parseColor("#E6EDF3"));
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

    private void hidePanel() {
        if (panel != null) {
            try { wm.removeView(panel); } catch (Exception ignored) { }
            panel = null;
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
        prefs.edit().putBoolean("float_running", false).apply();
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
