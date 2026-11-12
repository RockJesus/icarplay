package com.rockjesus.icarplay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机后恢复小白点悬浮层。
 *
 * 只启动悬浮服务：不拉起任何 Activity、不改默认桌面、不做无限重试。
 * 用户在首页手动关掉小白点后会写入 float_auto=false，开机也不再自动拉起。
 */
public final class FloatBootReceiver extends BroadcastReceiver {

    private static final String TAG = "iCarPlayBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)
                && !"com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }
        try {
            PermissionOnboarding.startFloatOnBoot(context);
            Log.i(TAG, "boot received, float restore requested");
        } catch (Throwable t) {
            Log.w(TAG, "boot float restore failed: " + t.getClass().getSimpleName());
        }
    }
}
