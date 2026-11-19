package com.rockjesus.icarplay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * 车机状态同步服务：按设定间隔采集并上报到 HA。
 *
 * <p>前台服务形态，避免在 Android 8+ 上被系统随手回收；只在用户打开
 * 「状态上报」开关后才启动，关掉开关立即停止并撤掉通知。</p>
 */
public class HaSyncService extends Service {

    public static final String ACTION_START = "com.rockjesus.icarplay.HA_SYNC_START";
    public static final String ACTION_SYNC_NOW = "com.rockjesus.icarplay.HA_SYNC_NOW";
    public static final String ACTION_STOP = "com.rockjesus.icarplay.HA_SYNC_STOP";

    private static final String CHANNEL = "icarplay_ha_sync";
    private static final int NOTIF_ID = 0x1CA701;

    private Handler handler;
    private Runnable tick;
    private boolean syncing;

    /** 开关开着就拉起服务；冷启动 / 开机时调用。 */
    public static void startIfEnabled(Context ctx) {
        if (!HaCompanion.enabled(ctx)) return;
        try {
            Intent it = new Intent(ctx, HaSyncService.class).setAction(ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(it);
            else ctx.startService(it);
        } catch (Throwable ignored) {
        }
    }

    public static void stop(Context ctx) {
        try {
            ctx.stopService(new Intent(ctx, HaSyncService.class));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(NOTIF_ID, buildNotification());
        handler = new Handler(Looper.getMainLooper());
        tick = () -> {
            runOnce();
            schedule();
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!HaCompanion.enabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_SYNC_NOW.equals(action)) {
            runOnce();
        } else if (handler != null) {
            handler.removeCallbacks(tick);
            schedule();
        }
        return START_STICKY;
    }

    private void schedule() {
        if (handler == null || tick == null) return;
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, HaCompanion.intervalSec(this) * 1000L);
    }

    /** 网络与磁盘都在工作线程里干，主线程只负责排下一轮。 */
    private void runOnce() {
        if (syncing) return;
        syncing = true;
        new Thread(() -> {
            try {
                HaCompanion.push(this);
            } catch (Throwable ignored) {
            }
            syncing = false;
        }, "ha-sync").start();
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "iCarPlay 状态上报", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(
                    Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 1,
                new Intent(this, HaPanelActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setContentTitle("iCarPlay")
                .setContentText("正在向 Home Assistant 同步车机状态")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setContentIntent(pi);
        return b.build();
    }

    @Override
    public void onDestroy() {
        if (handler != null && tick != null) handler.removeCallbacks(tick);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
