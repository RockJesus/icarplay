package com.rockjesus.icarplay;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CarPlay 连接能力检测。
 *
 * <p>思路参考 CarUp 的「先检测、再连接」：把有线与无线各自的前置条件拆成可观测的条目，
 * 逐条给出就绪 / 待满足 / 不可用，连接卡住时能直接看出停在哪一步。
 *
 * <p>边界说明：这里只检测**车机侧可观测的能力**，不包含 CarUp 闭源的协议引擎内部状态；
 * 真正的 CarPlay 会话需要 MFi 认证材料 + 协议引擎，本应用尚未接入引擎，
 * 因此「协议引擎」一项会一直是不可用，直到补上引擎为止。
 */
public class CarPlayCheck {

    public static final int OK = 0;
    public static final int WARN = 1;
    public static final int FAIL = 2;
    public static final int UNKNOWN = 3;

    public static class Item {
        public final String name;
        public final String value;
        public final int state;
        public final String detail;

        public Item(String name, String value, int state, String detail) {
            this.name = name;
            this.value = value;
            this.state = state;
            this.detail = detail;
        }
    }

    // ---------- 有线 ----------

    public static List<Item> wired(Context c) {
        List<Item> list = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        UsbManager um = (UsbManager) c.getSystemService(Context.USB_SERVICE);

        boolean host = pm != null && pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST);
        list.add(new Item("USB Host 能力", host ? "支持" : "不支持",
                host ? OK : FAIL,
                host ? "系统声明可做 USB 主机" : "车机未声明 USB Host，无法枚举 iPhone"));

        int total = 0;
        int apple = 0;
        boolean appleGranted = false;
        if (um != null) {
            for (Map.Entry<String, UsbDevice> e : um.getDeviceList().entrySet()) {
                total++;
                if (e.getValue().getVendorId() == 0x05AC) {
                    apple++;
                    if (um.hasPermission(e.getValue())) appleGranted = true;
                }
            }
        }
        list.add(new Item("USB 设备枚举", total > 0 ? total + " 个" : "无设备",
                total > 0 ? OK : WARN,
                total > 0 ? "已发现 USB 设备" : "请插上 iPhone 数据线"));

        list.add(new Item("Apple 设备", apple > 0 ? apple + " 个" : "未发现",
                apple > 0 ? OK : WARN,
                apple > 0 ? "已识别 Apple VID 0x05AC" : "iPhone 需解锁并信任本车机"));

        list.add(new Item("USB 访问权限", appleGranted ? "已授权" : "未授权",
                appleGranted ? OK : WARN,
                appleGranted ? "系统已授予该设备访问权限"
                        : "点下方「请求 USB 权限」由系统弹框授权"));

        boolean authOk = CarPlayCore.hasAuth(c);
        list.add(new Item("MFi 认证后端", CarPlayCore.authLabel(c),
                authOk ? OK : FAIL,
                authOk ? "认证材料已就位" : "缺认证材料，iPhone 会在认证阶段断开"));

        list.add(new Item("本地证书材料", CarPlayCore.hasLocalIdentity(c) ? "已找到" : "未找到",
                CarPlayCore.hasLocalIdentity(c) ? OK : WARN,
                "identity.pk8 + certificate.p7b，放在 assets/carplay 或外部目录"));

        boolean engine = DiPlayBridge.isInstalled(c);
        list.add(new Item("CarPlay 接收引擎", engine ? "已就绪（内置）" : "未接入",
                engine ? OK : FAIL,
                engine ? "内置接收引擎，可在 CarPlay 页直接开始接收"
                        : "正常安装包里自带接收引擎，若显示未接入说明 APK 被裁剪"));

        list.add(new Item("数据线要求", "需数据线", UNKNOWN,
                "只能充电的线不枚举数据，换一根支持数据传输的线"));

        list.add(new Item("iPhone 端状态", "需解锁 + 信任", UNKNOWN,
                "首次连接时 iPhone 会弹「信任此电脑」，必须点信任"));

        return list;
    }

    // ---------- 无线 ----------

    public static List<Item> wireless(Context c) {
        List<Item> list = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        WifiManager wm = (WifiManager) c.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);

        boolean wifiOn = false;
        try {
            wifiOn = wm != null && wm.isWifiEnabled();
        } catch (Throwable ignored) {
        }
        list.add(new Item("Wi‑Fi 开关", wifiOn ? "已开启" : "未开启",
                wifiOn ? OK : FAIL, "无线 CarPlay 需要车机在 iPhone 可发现的局域网内"));

        boolean direct = pm != null && pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT);
        list.add(new Item("Wi‑Fi Direct", direct ? "支持" : "不支持",
                direct ? OK : WARN,
                direct ? "可用于点对点发现" : "没有 P2P，只能走同一路由/热点"));

        boolean btOn = false;
        try {
            BluetoothManager bm = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter ba = bm == null ? null : bm.getAdapter();
            btOn = ba != null && ba.isEnabled();
        } catch (Throwable ignored) {
        }
        list.add(new Item("蓝牙开关", btOn ? "已开启" : "未开启",
                btOn ? OK : WARN, "无线 CarPlay 用蓝牙做配对与唤醒"));

        boolean ble = pm != null && pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        list.add(new Item("BLE 支持", ble ? "支持" : "不支持",
                ble ? OK : WARN, "低功耗广播用于发现与重连"));

        String ssid = "未获取";
        String band = "未知";
        try {
            WifiInfo info = wm == null ? null : wm.getConnectionInfo();
            if (info != null) {
                String raw = info.getSSID();
                if (raw != null && !raw.isEmpty() && !"<unknown ssid>".equals(raw)) {
                    ssid = raw.replace("\"", "");
                }
                int freq = info.getFrequency();
                if (freq >= 4900 && freq <= 5900) band = "5GHz";
                else if (freq > 0) band = "2.4GHz";
            }
        } catch (Throwable ignored) {
        }
        list.add(new Item("当前 Wi‑Fi", ssid, ssid.equals("未获取") ? WARN : OK,
                ssid.equals("未获取") ? "读不到 SSID，可能缺定位权限或未联网" : "已连到该网络"));

        list.add(new Item("频段", band, band.equals("5GHz") ? OK : WARN,
                band.equals("5GHz") ? "5GHz 更稳，适合视频" : "2.4GHz 易受干扰，离路由器近些"));

        boolean authOk = CarPlayCore.hasAuth(c);
        list.add(new Item("MFi 认证后端", CarPlayCore.authLabel(c),
                authOk ? OK : FAIL,
                authOk ? "认证材料已就位" : "无线同样过不了认证"));

        boolean engineW = DiPlayBridge.isInstalled(c);
        list.add(new Item("CarPlay 接收引擎", engineW ? "已就绪（内置）" : "未接入",
                engineW ? OK : FAIL,
                engineW ? "内置接收引擎负责无线接收" : "正常安装包里自带接收引擎"));

        list.add(new Item("iPhone 端状态", "需已配对", UNKNOWN,
                "首次无线连接要在 iPhone 里完成配对与授权"));

        return list;
    }

    public static int readyCount(List<Item> items) {
        int n = 0;
        for (Item i : items) if (i.state == OK) n++;
        return n;
    }

    public static String report(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("iCarPlay ").append(IcarLauncherActivity.VERSION_LABEL)
                .append(" CarPlay 能力检测\n");
        sb.append("平台：Android ").append(android.os.Build.VERSION.RELEASE)
                .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n\n");
        sb.append("【有线】").append(readyCount(wired(c))).append("/").append(wired(c).size())
                .append(" 就绪\n");
        for (Item i : wired(c)) {
            sb.append("  - ").append(i.name).append("：").append(i.value).append("\n");
        }
        sb.append("\n【无线】").append(readyCount(wireless(c))).append("/")
                .append(wireless(c).size()).append(" 就绪\n");
        for (Item i : wireless(c)) {
            sb.append("  - ").append(i.name).append("：").append(i.value).append("\n");
        }
        sb.append("\n【引擎增强】").append(EngineExtra.summaryLine(c)).append("\n");
        return sb.toString();
    }
}
