package com.rockjesus.icarplay;

import android.content.Context;
import android.content.Intent;
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

    /** 是否已安装 DiPlay。 */
    public static boolean isInstalled(Context c) {
        try {
            return packageInfo(c) != null;
        } catch (Throwable e) {
            return false;
        }
    }

    public static String version(Context c) {
        PackageInfo pi = packageInfo(c);
        if (pi == null) return "未安装";
        return pi.versionName == null ? "已安装" : pi.versionName;
    }

    /** 启动 DiPlay 首页；返回是否成功拉起。 */
    public static boolean launch(Context c) {
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
