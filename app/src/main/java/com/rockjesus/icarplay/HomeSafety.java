package com.rockjesus.icarplay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;

/** Read-only default-home awareness. This class never binds or replaces the system launcher. */
public final class HomeSafety {
    private HomeSafety() {}

    public static boolean isDefaultHome(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolved = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return resolved != null
                    && resolved.activityInfo != null
                    && context.getPackageName().equals(resolved.activityInfo.packageName);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * 启动原车机桌面。若本应用就是默认桌面，则返回 false，调用方应提示用户
     * 切回原桌面，绝不能在这里再启动自己形成死循环。
     */
    public static boolean goToSystemHome(Activity activity) {
        if (isDefaultHome(activity)) return false;
        try {
            Intent home = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ResolveInfo resolved = activity.getPackageManager()
                    .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved == null
                    || resolved.activityInfo == null
                    || activity.getPackageName().equals(resolved.activityInfo.packageName)) {
                return false;
            }
            activity.startActivity(home);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Opens the vendor's own home-app picker; the user makes the decision there. */
    public static void openHomeSettings(Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (RuntimeException ignored) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (RuntimeException second) {
                // Some head units have no Settings entry at all; the caller shows a message.
            }
        }
    }
}
