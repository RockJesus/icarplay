package com.rockjesus.icarplay;

import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

/**
 * 内置 DiPlay 引擎里「代码已打包、但入口被我们主动关掉」的几项能力。
 *
 * <p>v1.1.5 为了守住「不接管车机」的底线，用 tools:node="remove" 把引擎的
 * 开机自启（BootReceiver）与蓝牙电话联动（PhoneBluetoothReceiver）整条剥掉了，
 * 同时剥掉了使用情况访问、修改系统设置两项特殊权限。代价是引擎对应逻辑静默失效。
 *
 * <p>v1.1.6 起改为「打包但默认关闭、由用户按需打开」：清单里两个 receiver 静态
 * enabled=false，这里负责读取/切换它们的状态，以及两项特殊权限的查询与跳转。
 * 默认值一律为关闭，不改变车机开机行为。
 */
public final class EngineExtra {

    /** 引擎开机自启接收器。 */
    public static final String BOOT_RECEIVER = "com.shilapi.xcertplay.BootReceiver";

    /** 引擎蓝牙电话联动接收器（ACL_CONNECTED）。 */
    public static final String BT_RECEIVER = "com.shilapi.xcertplay.PhoneBluetoothReceiver";

    private EngineExtra() {
    }

    /** 该接收器是否真的打进包里（被裁剪的 APK 会返回 false）。 */
    public static boolean present(Context c, String cls) {
        try {
            c.getPackageManager().getReceiverInfo(new ComponentName(c, cls), 0);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 当前是否启用。清单默认 enabled=false，所以「未设置」按关闭算。 */
    public static boolean enabled(Context c, String cls) {
        try {
            int state = c.getPackageManager().getComponentEnabledSetting(new ComponentName(c, cls));
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return true;
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) return false;
            return false;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 切换启用状态；返回是否成功。不杀进程，避免正在投屏的会话被打断。 */
    public static boolean setEnabled(Context c, String cls, boolean on) {
        try {
            c.getPackageManager().setComponentEnabledSetting(
                    new ComponentName(c, cls),
                    on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            return enabled(c, cls) == on;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 是否已授予「使用情况访问」（PACKAGE_USAGE_STATS，特殊权限，需用户在系统里手动开）。 */
    public static boolean hasUsageAccess(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return false;
            ApplicationInfo ai = c.getApplicationInfo();
            int mode;
            if (Build.VERSION.SDK_INT >= 29) {
                mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), c.getPackageName());
            } else {
                mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), c.getPackageName());
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 是否已授予「修改系统设置」（WRITE_SETTINGS，只影响亮度/夜间模式这类写入）。 */
    public static boolean canWriteSettings(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 23) return Settings.System.canWrite(c);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 跳转「使用情况访问」授权页。 */
    public static void openUsageAccess(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            i.setData(Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Throwable e) {
            try {
                Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 跳转「修改系统设置」授权页。 */
    public static void openWriteSettings(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            i.setData(Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Throwable e) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 复制报告用的一行摘要。 */
    public static String summaryLine(Context c) {
        return "自启=" + (enabled(c, BOOT_RECEIVER) ? "开" : "关")
                + " 蓝牙电话=" + (enabled(c, BT_RECEIVER) ? "开" : "关")
                + " 使用情况访问=" + (hasUsageAccess(c) ? "已授权" : "未授权")
                + " 修改系统设置=" + (canWriteSettings(c) ? "已授权" : "未授权");
    }
}
