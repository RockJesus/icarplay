package com.rockjesus.icarplay;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/**
 * DiPlay 桥接：检测第三方 CarPlay 接收端是否已装，并提供启动入口。
 *
 * <p>DiPlay（`com.shihab.diplay`）是开源的独立 CarPlay 接收端（GPLv3），
 * 它自带 `assets/offline-mfi/` 的 `certificate.p7b` + `identity.pk8`，
 * 也就是本应用目前缺失的 MFi 身份材料。我们**不复制**它的任何资产或代码，
 * 只做「装上没装、版本多少、帮我启动」这三件事，避免许可证与合规问题。
 */
public class DiPlayBridge {

    public static final String PACKAGE = "com.shihab.diplay";

    /** 内置引擎的入口 Activity（同一个 APK 内）。 */
    public static final String BUILTIN_ACTIVITY = "com.shilapi.xcertplay.DiPlayActivity";

    /** 内置引擎版本，与打进本包的 DiPlay 库版本保持一致。 */
    public static final String BUILTIN_VERSION = "0.2.22";

    /** Whether a CarPlay engine is available: built in, or a standalone DiPlay install. */
    public static boolean isInstalled(Context c) {
        try {
            return hasBuiltin() || packageInfo(c) != null;
        } catch (Throwable e) {
            return false;
        }
    }

    public static String version(Context c) {
        // 启动时优先走内置引擎，所以状态也以内置为准；外部包只作为兜底。
        if (hasBuiltin()) return BUILTIN_VERSION + "（内置）";
        PackageInfo pi = packageInfo(c);
        if (pi != null && pi.versionName != null) return pi.versionName + "（独立包）";
        return "未安装";
    }

    /** 启动 CarPlay 引擎；返回是否成功拉起。 */
    public static boolean launch(Context c) {
        if (launchBuiltin(c, null)) return true;
        try {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(PACKAGE);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 直接进 CarPlay 画面（跳过引擎首页）。 */
    public static boolean launchCarPlayScreen(Context c) {
        return launchBuiltin(c, "com.shilapi.xcertplay.CarPlayHostActivity");
    }

    private static boolean launchBuiltin(Context c, String cls) {
        if (!hasBuiltin()) return false;
        try {
            Intent i = new Intent();
            i.setClassName(c.getPackageName(), cls == null ? BUILTIN_ACTIVITY : cls);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }


    // ---- 引擎（内置 DiPlay）自身的无线配置 ----
    // 引擎默认 transport=WIRELESS、mode=MANUAL：SSID 为空时 CarPlayRuntimeConfig
    // 会 require 失败并在启动线程里抛 IllegalArgumentException，直接闪退。
    // 所以直达 CarPlay 画面前必须先确认无线参数齐，否则切成有线。

    public static final String ENGINE_PREFS = "xcertplay_airplay";
    private static final String K_WIRELESS = "wireless_enabled";
    private static final String K_MODE = "wireless_hotspot_mode";
    private static final String K_MANUAL_SSID = "manual_hotspot_ssid";
    private static final String K_MANUAL_PASS = "manual_hotspot_passphrase";
    private static final String K_EXIST_SSID = "existing_wifi_ssid";
    private static final String K_EXIST_PASS = "existing_wifi_passphrase";

    public static SharedPreferences enginePrefs(Context c) {
        return c.getSharedPreferences(ENGINE_PREFS, Context.MODE_PRIVATE);
    }

    public static boolean engineWireless(Context c) {
        return enginePrefs(c).getBoolean(K_WIRELESS, true);
    }

    public static String engineMode(Context c) {
        String m = enginePrefs(c).getString(K_MODE, "MANUAL");
        return m == null ? "MANUAL" : m;
    }

    /** 无线参数够不够引擎启动（不够就会闪退）。 */
    public static boolean engineWirelessReady(Context c) {
        if (!engineWireless(c)) return true;
        String mode = engineMode(c);
        if ("MANUAL".equals(mode)) {
            String ssid = enginePrefs(c).getString(K_MANUAL_SSID, "");
            return ssid != null && !ssid.trim().isEmpty();
        }
        if ("EXISTING_WIFI".equals(mode)) {
            String ssid = enginePrefs(c).getString(K_EXIST_SSID, "");
            return ssid != null && !ssid.trim().isEmpty();
        }
        return true;
    }

    /** 把车机热点名/密码写进引擎，并切到无线。 */
    public static void useCarHotspot(Context c, String ssid, String pass) {
        enginePrefs(c).edit()
                .putBoolean(K_WIRELESS, true)
                .putString(K_MODE, "MANUAL")
                .putString(K_MANUAL_SSID, ssid == null ? "" : ssid.trim())
                .putString(K_MANUAL_PASS, pass == null ? "" : pass)
                .apply();
    }

    /** 同局域网模式：车机和 iPhone 都连同一个路由。 */
    public static void useExistingWifi(Context c, String ssid, String pass) {
        enginePrefs(c).edit()
                .putBoolean(K_WIRELESS, true)
                .putString(K_MODE, "EXISTING_WIFI")
                .putString(K_EXIST_SSID, ssid == null ? "" : ssid.trim())
                .putString(K_EXIST_PASS, pass == null ? "" : pass)
                .apply();
    }

    /** 切到有线（USB）。引擎不再走无线，也就不会做 SSID 校验。 */
    public static void useWired(Context c) {
        enginePrefs(c).edit().putBoolean(K_WIRELESS, false).apply();
    }

    /**
     * 安全启动：无线没配好就先降到有线，避免引擎启动线程抛异常闪退。
     * 返回给用户看的提示。
     */
    public static String launchSafely(Context c) {
        String prefix = "";
        if (!engineWirelessReady(c)) {
            useWired(c);
            prefix = "无线还没配好，先按有线（USB）启动；要无线请到「无线向导」保存热点名。\n";
        }
        if (launchCarPlayScreen(c) || launch(c)) return prefix + "已进入 CarPlay 接收画面";
        return prefix + "启动失败：车机可能限制后台拉起";
    }

    private static boolean hasBuiltin() {
        try {
            Class.forName(BUILTIN_ACTIVITY);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public static String installUrl() {
        return "https://github.com/shihabal3amri/DiPlay/releases/latest";
    }

    private static PackageInfo packageInfo(Context c) {
        try {
            PackageManager pm = c.getPackageManager();
            if (pm == null) return null;
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                return pm.getPackageInfo(PACKAGE,
                        PackageManager.PackageInfoFlags.of(0));
            }
            return pm.getPackageInfo(PACKAGE, 0);
        } catch (Throwable e) {
            return null;
        }
    }
}
