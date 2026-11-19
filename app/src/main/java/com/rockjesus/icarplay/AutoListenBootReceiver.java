package com.rockjesus.icarplay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 开机静默待命的入口。
 *
 * <p>只做一件事：把 {@link AutoListenService} 起起来，不拉界面。
 * 该接收器在清单里静态 enabled=false，只有用户在检测页打开「开机静默待命」开关后
 * 才会被动态启用，所以默认不影响车机开机。
 */
public final class AutoListenBootReceiver extends BroadcastReceiver {

    private static final String TAG = "iCarPlayAutoListen";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)
                && !"com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }
        if (!EngineExtra.autoListenEnabled(context)) {
            Log.i(TAG, "auto listen off, skip");
            return;
        }
        try {
            Intent service = new Intent(context, AutoListenService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
            Log.i(TAG, "auto listen service requested");
        } catch (Throwable e) {
            Log.w(TAG, "auto listen not started: " + e.getClass().getSimpleName());
        }
    }
}
