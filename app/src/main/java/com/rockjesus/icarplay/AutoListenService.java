package com.rockjesus.icarplay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * 开机静默待命（B 方案）。
 *
 * <p>不拉任何界面，只在开机后起一个前台服务：
 * <ol>
 *   <li>先把内置引擎的会话服务叫起来，让它进入监听；</li>
 *   <li>然后自己扫 /proc/net/tcp 找“已经建立的外部 TCP 连接”作为 iPhone 连上的信号；</li>
 *   <li>只有真的看到连接，才把 CarPlay 接收界面顶到前台。</li>
 * </ol>
 *
 * <p>这样车机开机后仍然是原生界面（最多多个常驻通知），只有 iPhone 真的连上来时
 * 画面才出现，不需要用户每次手动点「开始 CarPlay 接收」。
 * 任何一步失败都静默退出，不重试、不弹窗、不动系统设置。
 */
public final class AutoListenService extends Service {

    private static final String TAG = "iCarPlayAutoListen";
    private static final String CHANNEL = "icarplay_autolisten";
    private static final int NOTIF_ID = 1002;

    /** 引擎的会话服务（同一 UID，可直接启动）。 */
    private static final String ENGINE_SESSION = "com.shilapi.xcertplay.DiPlaySessionService";

    /** 引擎的接收界面，连上后用它把画面顶上来。 */
    private static final String ENGINE_HOST = "com.shilapi.xcertplay.CarPlayHostActivity";

    /** 待命超过这个时长才允许“看到连接就弹界面”，避免开机瞬间误判。 */
    private static final long ARM_DELAY_MS = 8_000L;

    /** 轮询间隔。 */
    private static final long POLL_MS = 3_000L;

    private volatile boolean uiRaised;
    private long startedAt;
    private Thread watcher;

    @Override
    public void onCreate() {
        super.onCreate();
        startedAt = System.currentTimeMillis();
        startForeground(NOTIF_ID, buildNotification());
        tryStartEngine();
        watcher = new Thread(this::watchLoop, "icarplay-autolisten");
        watcher.setDaemon(true);
        watcher.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void tryStartEngine() {
        try {
            Intent i = new Intent();
            i.setClassName(getPackageName(), ENGINE_SESSION);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else {
                startService(i);
            }
            Log.i(TAG, "engine session service requested");
        } catch (Throwable e) {
            Log.w(TAG, "engine session not startable: " + e.getClass().getSimpleName());
        }
    }

    private void watchLoop() {
        while (!uiRaised) {
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                return;
            }
            if (System.currentTimeMillis() - startedAt < ARM_DELAY_MS) continue;
            if (!hasLiveConnection()) continue;
            raiseUi();
            return;
        }
    }

    /**
     * 判断“有没有外部连接进来”。
     *
     * <p>只读 /proc/net/tcp、/proc/net/tcp6 里本进程 UID 的 ESTABLISHED 连接，
     * 排除回环地址。iPhone 连上后引擎会与手机建立 TCP 会话，这里就能看到。
     */
    private boolean hasLiveConnection() {
        int uid = android.os.Process.myUid();
        return scan("/proc/net/tcp", uid) || scan("/proc/net/tcp6", uid);
    }

    private boolean scan(String path, int uid) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String line;
            boolean header = true;
            while ((line = r.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                String[] f = line.trim().split("\\s+");
                if (f.length < 10) continue;
                // f[3] 状态（01=ESTABLISHED），f[7] uid
                if (!"01".equals(f[3])) continue;
                try {
                    if (Integer.parseInt(f[7]) != uid) continue;
                } catch (NumberFormatException ignored) {
                    continue;
                }
                String remote = f[2];
                if (remote == null) continue;
                String hexIp = remote.contains(":") ? remote.substring(0, remote.lastIndexOf(':')) : remote;
                if (isLoopback(hexIp)) continue;
                Log.i(TAG, "live connection spotted: " + remote);
                return true;
            }
        } catch (Throwable e) {
            Log.w(TAG, "scan failed " + path + ": " + e.getClass().getSimpleName());
        }
        return false;
    }

    private boolean isLoopback(String hexIp) {
        if (hexIp == null) return true;
        if (hexIp.length() == 8) {
            // 小端序 127.0.0.1 => "0100007F"
            return "0100007F".equalsIgnoreCase(hexIp);
        }
        if (hexIp.length() == 32) {
            return hexIp.replace("0", "").isEmpty()
                    || hexIp.equalsIgnoreCase("00000000000000000000000001000000")
                    || hexIp.equalsIgnoreCase("0000000000000000FFFF00000100007F");
        }
        return false;
    }

    private void raiseUi() {
        uiRaised = true;
        try {
            Intent i = new Intent();
            i.setClassName(getPackageName(), ENGINE_HOST);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            Log.i(TAG, "session detected, host activity raised");
        } catch (Throwable e) {
            Log.w(TAG, "raise host failed: " + e.getClass().getSimpleName());
        }
        stopSelf();
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    "iCarPlay 静默待命", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, IcarLauncherActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setContentTitle("iCarPlay 开机待命")
                .setContentText("正在等 iPhone 连上来，连上后才显示画面")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setOngoing(true)
                .setContentIntent(pi);
        return b.build();
    }

    @Override
    public void onDestroy() {
        if (watcher != null) watcher.interrupt();
        super.onDestroy();
    }
}
