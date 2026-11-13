package com.rockjesus.icarplay;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 首次启动权限引导：悬浮窗 -> 运行时权限 -> 电池优化 -> 自动开启悬浮按钮。
 *
 * 注意：Android 不允许 APK 安装完成后自动拉起界面，所以“安装后自动弹权限”
 * 实际发生在用户第一次打开 iCarPlay 时。
 */
public final class PermissionOnboarding {

    private static final int REQ_OVERLAY = 4101;
    private static final int REQ_BATTERY = 4102;
    private static final int REQ_RUNTIME = 4103;

    private static final String PREFS = "icarplay";
    private static final String KEY_DONE = "permissions_onboarding_done";
    private static final String KEY_OVERLAY_ASKED = "permissions_overlay_asked";
    private static final String KEY_RUNTIME_ASKED = "permissions_runtime_asked";
    private static final String KEY_BATTERY_ASKED = "permissions_battery_asked";
    private static final String KEY_A11Y_ASKED = "permissions_a11y_asked";
    private static final String KEY_FLOAT_AUTO = "float_auto";

    private PermissionOnboarding() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 每次回到首页都推进一步，兼容部分 ROM 从设置页返回不给 onActivityResult 的情况。 */
    public static void onResume(Activity activity) {
        SharedPreferences p = prefs(activity);
        if (p.getBoolean(KEY_DONE, false)) {
            maybeAskAccessibility(activity);
            startFloatIfPossible(activity);
            return;
        }
        if (p.getBoolean(KEY_BATTERY_ASKED, false)) {
            finish(activity);
            return;
        }
        if (!Settings.canDrawOverlays(activity)) {
            if (!p.getBoolean(KEY_OVERLAY_ASKED, false)) {
                p.edit().putBoolean(KEY_OVERLAY_ASKED, true).apply();
                openOverlaySettings(activity);
                return;
            }
            // 用户刚从设置页返回但没授权：继续把其余权限一次性问完，最后再提示悬浮按钮不可用。
            if (!p.getBoolean(KEY_RUNTIME_ASKED, false)) {
                requestRuntimePermissions(activity);
            }
            return;
        }
        if (!p.getBoolean(KEY_RUNTIME_ASKED, false)) {
            requestRuntimePermissions(activity);
        }
    }

    public static void start(Activity activity) {
        onResume(activity);
    }

    private static void openOverlaySettings(Activity activity) {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + activity.getPackageName()));
        try {
            activity.startActivityForResult(intent, REQ_OVERLAY);
        } catch (Throwable t) {
            try {
                activity.startActivityForResult(
                        new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), REQ_OVERLAY);
            } catch (Throwable ignored) {
                Toast.makeText(activity, "无法打开悬浮窗权限设置", Toast.LENGTH_LONG).show();
                requestRuntimePermissions(activity);
            }
        }
    }

    private static void requestRuntimePermissions(Activity activity) {
        SharedPreferences p = prefs(activity);
        if (p.getBoolean(KEY_RUNTIME_ASKED, false)) {
            // 已经弹过运行时权限，等 onRequestPermissionsResult 再进入电池优化，
            // 避免在权限对话框上再叠一层系统设置页。
            return;
        }

        List<String> pending = new ArrayList<>();
        addIfMissing(activity, pending, Manifest.permission.RECORD_AUDIO);
        addIfMissing(activity, pending, Manifest.permission.ACCESS_FINE_LOCATION);
        addIfMissing(activity, pending, Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Build.VERSION.SDK_INT >= 31) {
            addIfMissing(activity, pending, Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            addIfMissing(activity, pending, Manifest.permission.POST_NOTIFICATIONS);
            addIfMissing(activity, pending, Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        p.edit().putBoolean(KEY_RUNTIME_ASKED, true).apply();
        if (pending.isEmpty()) {
            requestBatteryOptimization(activity);
            return;
        }
        activity.requestPermissions(pending.toArray(new String[0]), REQ_RUNTIME);
    }

    private static void addIfMissing(Activity activity, List<String> out, String permission) {
        if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            out.add(permission);
        }
    }

    private static void requestBatteryOptimization(Activity activity) {
        SharedPreferences p = prefs(activity);
        if (p.getBoolean(KEY_BATTERY_ASKED, false)) {
            finish(activity);
            return;
        }
        p.edit().putBoolean(KEY_BATTERY_ASKED, true).apply();

        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(activity.getPackageName())) {
                Intent intent = new Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + activity.getPackageName()));
                try {
                    activity.startActivityForResult(intent, REQ_BATTERY);
                    return;
                } catch (Throwable ignored) {
                    try {
                        activity.startActivityForResult(
                                new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                                REQ_BATTERY);
                        return;
                    } catch (Throwable ignored2) {
                        // 忽略，直接完成引导。
                    }
                }
            }
        }
        finish(activity);
    }

    public static boolean onActivityResult(Activity activity, int requestCode) {
        if (requestCode == REQ_OVERLAY) {
            // 部分车机从设置返回时 resultCode 仍是 CANCELED，必须以 canDrawOverlays 为准。
            if (Settings.canDrawOverlays(activity)) {
                requestRuntimePermissions(activity);
            } else {
                Toast.makeText(activity, "悬浮窗权限未授予，悬浮按钮暂时无法显示", Toast.LENGTH_LONG).show();
                requestRuntimePermissions(activity);
            }
            return true;
        }
        if (requestCode == REQ_BATTERY) {
            finish(activity);
            return true;
        }
        return false;
    }

    public static boolean onRequestPermissionsResult(Activity activity, int requestCode) {
        if (requestCode == REQ_RUNTIME) {
            requestBatteryOptimization(activity);
            return true;
        }
        return false;
    }

    private static void finish(Activity activity) {
        SharedPreferences p = prefs(activity);
        if (p.getBoolean(KEY_DONE, false)) {
            startFloatIfPossible(activity);
            return;
        }
        p.edit().putBoolean(KEY_DONE, true).apply();
        startFloatIfPossible(activity);
        String message = Settings.canDrawOverlays(activity)
                ? "权限引导完成，悬浮按钮已默认开启"
                : "权限引导完成；悬浮窗未授权，悬浮按钮暂不可用";
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    /** 首次回到首页时，提示开启能盖住原车机桌面的无障碍悬浮层。 */
    private static void maybeAskAccessibility(Activity activity) {
        SharedPreferences p = prefs(activity);
        if (p.getBoolean(KEY_A11Y_ASKED, false)) return;
        if (!p.getBoolean(KEY_FLOAT_AUTO, true)) return;
        if (isAccessibilityEnabled(activity)) return;
        p.edit().putBoolean(KEY_A11Y_ASKED, true).apply();
        new AlertDialog.Builder(activity)
                .setTitle("让悬浮按钮显示在原车机桌面")
                .setMessage("原车机桌面位于系统层，普通悬浮窗会被它盖住。开启 iCarPlay 无障碍服务后，悬浮按钮才能显示在车机桌面上。该服务只绘制悬浮球，不读取屏幕内容。")
                .setPositiveButton("去开启", (d, w) -> {
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Throwable t) {
                        Toast.makeText(activity, "无法打开无障碍设置", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("暂不", null)
                .show();
    }

    /** 是否已在系统无障碍设置中启用 iCarPlay 悬浮按钮服务。 */
    public static boolean isAccessibilityEnabled(Context context) {
        try {
            String enabled = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null || enabled.length() == 0) return false;
            ComponentName cn = new ComponentName(context, FloatAccessibilityService.class);
            return enabled.contains(cn.flattenToString())
                    || enabled.contains(cn.flattenToShortString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 默认自动开启悬浮按钮；用户在首页手动关掉后不再强制拉起。 */
    public static void startFloatIfPossible(Activity activity) {
        SharedPreferences p = prefs(activity);
        if (!p.getBoolean(KEY_DONE, false)) {
            return;
        }
        if (!p.getBoolean(KEY_FLOAT_AUTO, true)) {
            return;
        }
        if (isAccessibilityEnabled(activity) && FloatAccessibilityService.isRunning()) {
            // 无障碍悬浮层已经实际接管，普通悬浮窗让位，避免双球。
            if (isFloatServiceRunning(activity)) {
                try { activity.stopService(new Intent(activity, FloatService.class)); }
                catch (Throwable ignored) { }
            }
            return;
        }
        if (!Settings.canDrawOverlays(activity)) {
            return;
        }
        if (p.getBoolean("float_running", false) && isFloatServiceRunning(activity)) {
            return;
        }
        Intent service = new Intent(activity, FloatService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                activity.startForegroundService(service);
            } else {
                activity.startService(service);
            }
        } catch (Throwable t) {
            Toast.makeText(activity, "悬浮按钮启动失败：" + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 开机恢复悬浮按钮。只在用户完成首次引导且未手动关闭悬浮按钮时启动普通悬浮层。
     * 无障碍层若已真正连接，会将普通层让位；若尚未重新绑定，则先由普通层兜底。
     */
    public static void startFloatOnBoot(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.getBoolean(KEY_DONE, false)) return;
        if (!p.getBoolean(KEY_FLOAT_AUTO, true)) return;
        if (!Settings.canDrawOverlays(context)) return;
        if (isFloatServiceRunning(context)) return;
        Intent service = new Intent(context, FloatService.class).setAction("BOOT_START");
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } catch (Throwable ignored) {
            // 部分 ROM 会限制开机后台启动；失败时保持系统原样，不重试、不崩溃。
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean isFloatServiceRunning(Context context) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager)
                    context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;
            for (android.app.ActivityManager.RunningServiceInfo info
                    : am.getRunningServices(Integer.MAX_VALUE)) {
                if (FloatService.class.getName().equals(info.service.getClassName())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
