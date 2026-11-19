package com.rockjesus.icarplay;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 更新包缓存管理。
 *
 * <p>下载的安装包落在 {@code getExternalFilesDir()/update}，属于应用私有外部目录。
 * 以前只在“签名不一致”时删除，装成功的那份会一直留着（每版约 33 MB）。
 *
 * <p>清理时机只有两个是安全的：
 * <ol>
 *   <li>冷启动（此时版本号已变，说明上一份确实装上了）；</li>
 *   <li>用户在更新页手动点「清理旧包」。</li>
 * </ol>
 * 绝不在 {@code launchInstaller()} 之后立刻删——系统安装器是异步读取 FileProvider 的，
 * 提前删会让安装报 INVALID_APK。
 */
public final class UpdateCache {

    private UpdateCache() {
    }

    public static File dir(Context c) {
        File d = new File(c.getExternalFilesDir(null), "update");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** 目录里的安装包（不含 .part 临时文件）。 */
    public static List<File> apks(Context c) {
        List<File> list = new ArrayList<>();
        File[] files = dir(c).listFiles();
        if (files == null) return list;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".apk")) list.add(f);
        }
        return list;
    }

    public static long totalBytes(Context c) {
        long sum = 0;
        for (File f : apks(c)) sum += f.length();
        return sum;
    }

    public static String human(long bytes) {
        if (bytes <= 0) return "0 MB";
        double mb = bytes / 1048576.0;
        if (mb >= 1024) return String.format("%.1f GB", mb / 1024.0);
        return String.format("%.1f MB", mb);
    }

    public static int installedVersionCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionCode;
        } catch (Throwable e) {
            return 0;
        }
    }

    /**
     * 冷启动清理：删掉「已经装上或更旧」的包，只在还比当前版本新、尚未安装的包之间保留最新一份。
     *
     * @return 删除的文件数量
     */
    public static int cleanup(Context c) {
        int removed = 0;
        try {
            int installed = installedVersionCode(c);
            List<File> list = apks(c);
            if (list.isEmpty()) return 0;
            // 先按“是否已安装/更旧”筛掉
            File newestPending = null;
            for (File f : list) {
                int code = archiveVersionCode(c, f);
                if (code > 0 && code <= installed) {
                    if (f.delete()) removed++;
                    continue;
                }
                if (newestPending == null) newestPending = f; // list 已按修改时间倒序
                else if (f.delete()) removed++;
            }
        } catch (Throwable ignored) {
        }
        return removed;
    }

    /** 清空全部缓存（用户在更新页手动触发）。 */
    public static long clearAll(Context c) {
        long freed = 0;
        try {
            for (File f : apks(c)) {
                long len = f.length();
                if (f.delete()) freed += len;
            }
            File[] rest = dir(c).listFiles();
            if (rest != null) {
                for (File f : rest) {
                    if (!f.isFile()) continue;
                    long len = f.length();
                    if (f.delete()) freed += len;
                }
            }
        } catch (Throwable ignored) {
        }
        return freed;
    }

    /** 读取安装包自己的 versionCode；读不到返回 0。 */
    private static int archiveVersionCode(Context c, File apk) {
        try {
            PackageManager pm = c.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            return info == null ? 0 : info.versionCode;
        } catch (Throwable e) {
            return 0;
        }
    }
}
