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
