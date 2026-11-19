package com.rockjesus.icarplay;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

/**
 * Home Assistant 官方 Android 伴侣应用的轻量桥接。
 *
 * <p>官方有两个发行变体，<b>包名不同</b>，必须都认：
 * <ul>
 *   <li>{@code io.homeassistant.companion.android} —— full 版（含 Google 服务）</li>
 *   <li>{@code io.homeassistant.companion.android.minimal} —— minimal 版（无 Google 依赖，车机首选）</li>
 * </ul>
 * 只做三件事：检测是否安装、用官方启动入口拉起来、给出官方 APK 的下载页。
 * 不内嵌、不打包、不申请任何新权限——官方应用是 Apache-2.0，但重打包既不必要也不安全。
 *
 * <p>注意：车机（Android 8.1、无 GMS）上官方 App 的推送、定位、传感器上报依赖 Google 服务，
 * 因此这里的定位是「入口 + 一键调起」，网页仪表盘仍由 {@link HaPanelActivity} 的内置 WebView 承担。
 */
public final class HaApp {

    /** full 版包名。 */
    public static final String PACKAGE = "io.homeassistant.companion.android";

    /** minimal 版包名（车机推荐，无 Google 依赖）。 */
    public static final String PACKAGE_MINIMAL = "io.homeassistant.companion.android.minimal";

    private static final String[] CANDIDATES = {PACKAGE, PACKAGE_MINIMAL};

    /** 官方启动入口（LauncherAlias → LaunchActivity）；两个变体类名一致。 */
    private static final String LAUNCH_ACTIVITY =
            "io.homeassistant.companion.android.launch.LaunchActivity";

    /** 官方 Release 页：车机浏览器或手机浏览器都能打开。 */
    public static final String DOWNLOAD_PAGE =
            "https://github.com/home-assistant/android/releases/latest";

    /** 官方导航深链：homeassistant://navigate/<path>?server_id=N。 */
    public static final String SCHEME_PREFIX = "homeassistant://navigate/";

    private HaApp() {
    }

    /** 返回已安装的官方包名（优先 full 版），都没装返回 null。 */
    public static String installedPackage(Context ctx) {
        if (ctx == null) return null;
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : CANDIDATES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (Throwable ignored) {
                // 该变体未安装，继续试下一个
            }
        }
        return null;
    }

    /** 是否已安装官方 App（full / minimal 任一）。 */
    public static boolean isInstalled(Context ctx) {
        return installedPackage(ctx) != null;
    }

    /** 已安装返回版本名（2026.11.1 或 2026.11.1-minimal），未装返回空串。 */
    public static String versionLabel(Context ctx) {
        String pkg = installedPackage(ctx);
        if (pkg == null) return "";
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(pkg, 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** 是否是 minimal 变体（车机上更常见）。 */
    public static boolean isMinimal(Context ctx) {
        return PACKAGE_MINIMAL.equals(installedPackage(ctx));
    }

    /** 拉起官方 App；未安装或启动失败返回 false，由调用方决定提示什么。 */
    public static boolean open(Context ctx) {
        String pkg = installedPackage(ctx);
        if (pkg == null) return false;
        try {
            Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) {
                launch = new Intent();
                launch.setClassName(pkg, LAUNCH_ACTIVITY);
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(launch);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 直接跳到指定的 HA 页面（默认首页）。官方 App 若还没连过服务器会先弹自己的一体化引导，
     * 这是正常行为。
     */
    public static boolean openPath(Context ctx, String path) {
        if (!isInstalled(ctx)) return false;
        try {
            String clean = path == null ? "" : path.trim();
            if (clean.startsWith("/")) clean = clean.substring(1);
            Uri uri = Uri.parse(SCHEME_PREFIX + clean);
            Intent deep = new Intent(Intent.ACTION_VIEW, uri);
            deep.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(deep);
            return true;
        } catch (Throwable ignored) {
            return open(ctx);
        }
    }

    /** 打开官方 Release 下载页（走系统浏览器，车机若没浏览器则交给手机）。 */
    public static boolean openDownloadPage(Context ctx) {
        if (ctx == null) return false;
        try {
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_PAGE));
            view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(view);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
