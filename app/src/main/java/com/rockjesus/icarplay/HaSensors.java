package com.rockjesus.icarplay;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 车机状态采集器（Companion 传感器一侧的本体）。
 *
 * <p>只读、纯本地：所有取值都在 try/catch 里兜底，读不到就少报一条，
 * 绝不影响车机其它功能；不申请任何新权限，已声明的权限才有值。</p>
 */
public final class HaSensors {

    /** 一条可上报的状态。type 只有 sensor / binary_sensor 两种。 */
    public static final class Item {
        final String uniqueId;
        final String type;
        final String name;
        final String icon;
        final String unit;
        final String deviceClass;
        final Object state;

        Item(String uniqueId, String type, String name, String icon,
             String unit, String deviceClass, Object state) {
            this.uniqueId = uniqueId;
            this.type = type;
            this.name = name;
            this.icon = icon;
            this.unit = unit;
            this.deviceClass = deviceClass;
            this.state = state;
        }

        /**
         * 转成 mobile_app 的条目。register 时带 name / icon / 单位 / device_class，
         * update 时同样带上（HA 会 merge，不会丢定义）。
         */
        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("unique_id", uniqueId);
            o.put("type", type);
            o.put("state", state);
            o.put("name", name);
            if (icon != null) o.put("icon", icon);
            JSONObject attrs = new JSONObject();
            if (unit != null) attrs.put("unit_of_measurement", unit);
            if (deviceClass != null) attrs.put("device_class", deviceClass);
            attrs.put("friendly_name", name);
            o.put("attributes", attrs);
            return o;
        }
    }

    public static List<Item> collect(Context ctx) {
        List<Item> out = new ArrayList<>();
        battery(ctx, out);
        network(ctx, out);
        storage(out);
        memory(ctx, out);
        uptime(out);
        screen(ctx, out);
        volume(ctx, out);
        usb(ctx, out);
        foreground(ctx, out);
        channels(ctx, out);
        return out;
    }

    private static void add(List<Item> out, String id, String name, String icon,
                            String unit, String deviceClass, Object state) {
        out.add(new Item(id, "sensor", name, icon, unit, deviceClass, state));
    }

    private static void addBinary(List<Item> out, String id, String name, String icon,
                                  String deviceClass, boolean on) {
        out.add(new Item(id, "binary_sensor", name, icon, null, deviceClass, on));
    }

    // ---------------- 电池 ----------------
    private static void battery(Context ctx, List<Item> out) {
        try {
            Intent sticky = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (sticky == null) return;
            boolean present = sticky.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
            if (!present) return;   // 车机大概率没有电池，直接不报
            int level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) {
                int pct = (int) Math.round(level * 100.0 / scale);
                add(out, "battery_level", "车机电量", "mdi:battery",
                        "%", "battery", pct);
            }
            int status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            addBinary(out, "battery_charging", "车机充电中", "mdi:battery-charging",
                    "battery_charging", charging);
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 网络 ----------------
    @SuppressWarnings("deprecation")
    private static void network(Context ctx, List<Item> out) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            NetworkInfo info = cm.getActiveNetworkInfo();
            boolean online = info != null && info.isConnected();
            addBinary(out, "network_online", "车机联网", "mdi:lan-connect",
                    "connectivity", online);
            String kind = "none";
            if (online) {
                int type = info.getType();
                if (type == ConnectivityManager.TYPE_WIFI) kind = "wifi";
                else if (type == ConnectivityManager.TYPE_MOBILE) kind = "mobile";
                else if (type == 9) kind = "ethernet";   // TYPE_ETHERNET
                else kind = "other";
            }
            add(out, "network_type", "车机网络类型", "mdi:network",
                    null, null, kind);
            if ("wifi".equals(kind)) {
                String ssid = wifiSsid(ctx);
                if (!ssid.isEmpty()) {
                    add(out, "wifi_ssid", "车机 WiFi", "mdi:wifi", null, null, ssid);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private static String wifiSsid(Context ctx) {
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return "";
            String raw = wm.getConnectionInfo().getSSID();
            if (raw == null) return "";
            raw = raw.replace("\"", "").trim();
            if (raw.isEmpty() || "<unknown ssid>".equalsIgnoreCase(raw)) return "";
            return raw;
        } catch (Throwable ignored) {
            return "";
        }
    }

    // ---------------- 存储 / 内存 / 开机时长 ----------------
    private static void storage(List<Item> out) {
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long free = fs.getAvailableBytes();
            long total = fs.getTotalBytes();
            double freeGb = free / 1024.0 / 1024.0 / 1024.0;
            double totalGb = total / 1024.0 / 1024.0 / 1024.0;
            add(out, "storage_free", "车机存储剩余", "mdi:harddisk",
                    "GB", null, String.format(Locale.US, "%.1f", freeGb));
            add(out, "storage_total", "车机存储总量", "mdi:harddisk",
                    "GB", null, String.format(Locale.US, "%.1f", totalGb));
        } catch (Throwable ignored) {
        }
    }

    private static void memory(Context ctx, List<Item> out) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            add(out, "memory_free", "车机内存可用", "mdi:memory",
                    "MB", null, (int) (mi.availMem / 1024 / 1024));
        } catch (Throwable ignored) {
        }
    }

    private static void uptime(List<Item> out) {
        try {
            add(out, "uptime", "车机开机时长", "mdi:timer-outline",
                    "s", "duration", (int) (SystemClock.elapsedRealtime() / 1000));
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 屏幕 / 音量 / USB ----------------
    private static void screen(Context ctx, List<Item> out) {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            addBinary(out, "screen_on", "车机屏幕点亮", "mdi:monitor",
                    "power", pm.isInteractive());
        } catch (Throwable ignored) {
        }
    }

    private static void volume(Context ctx, List<Item> out) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (max <= 0) return;
            add(out, "volume", "车机音量", "mdi:volume-high",
                    "%", null, (int) Math.round(cur * 100.0 / max));
        } catch (Throwable ignored) {
        }
    }

    private static void usb(Context ctx, List<Item> out) {
        try {
            UsbManager um = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
            if (um == null) return;
            int count = um.getDeviceList() == null ? 0 : um.getDeviceList().size();
            add(out, "usb_devices", "车机 USB 设备数", "mdi:usb",
                    null, null, count);
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 前台应用 ----------------
    private static void foreground(Context ctx, List<Item> out) {
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(
                    "usagestats");
            if (usm == null) return;
            long now = System.currentTimeMillis();
            List<UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, now - 10 * 60 * 1000L, now);
            if (stats == null || stats.isEmpty()) return;
            UsageStats latest = null;
            for (UsageStats s : stats) {
                if (s == null) continue;
                if (latest == null || s.getLastTimeUsed() > latest.getLastTimeUsed()) {
                    latest = s;
                }
            }
            if (latest == null || latest.getPackageName() == null) return;
            add(out, "foreground_app", "车机前台应用", "mdi:application",
                    null, null, latest.getPackageName());
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 五条投屏通道 ----------------
    private static void channels(Context ctx, List<Item> out) {
        try {
            addBinary(out, "airplay_running", "AirPlay 引擎运行", "mdi:apple",
                    "running", serviceRunning(ctx,
                            "io.github.jqssun.airplay.service.AirPlayService"));
            addBinary(out, "carlife_running", "CarLife 已连接", "mdi:car",
                    "running", HaApi.prefs(ctx).getBoolean("carlife_active", false));
            addBinary(out, "android_auto_running", "Android Auto 引擎运行", "mdi:android-auto",
                    "running", serviceRunning(ctx,
                            "info.anodsplace.headunit.aap.AapService"));
            addBinary(out, "hicar_running", "HiCar 引擎运行", "mdi:car-connected",
                    "running", serviceRunning(ctx, "local.hicarlab.ReceiverService"));
        } catch (Throwable ignored) {
        }
    }

    private static boolean serviceRunning(Context ctx, String className) {
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;
            for (ActivityManager.RunningServiceInfo info : am.getRunningServices(Integer.MAX_VALUE)) {
                if (info != null && info.service != null
                        && className.equals(info.service.getClassName())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
