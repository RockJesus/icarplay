package com.rockjesus.icarplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.wifi.WifiManager;

import java.io.File;
import java.util.Map;

/**
 * CarPlay 核心骨架。
 *
 * 当前版本只负责：认证后端选择、Apple USB 设备枚举、认证材料存在性检查，
 * 以及有线/无线启动入口的状态返回。
 *
 * 真正的 CarPlay 协议握手（iAP2 / MFi / AirPlay 隧道 / H.264 / HID）
 * 需要接 DiPlay-Legacy 或等价协议栈，并且必须有 Apple MFi 认证身份。
 */
public final class CarPlayCore {

    public static final int AUTH_NONE = 0;
    public static final int AUTH_LOCAL_CERT = 1;
    public static final int AUTH_MFI_CHIP = 2;
    public static final int AUTH_REMOTE_MFI = 3;

    private static final int APPLE_VENDOR_ID = 0x05AC;

    private CarPlayCore() { }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("icarplay", Context.MODE_PRIVATE);
    }

    public static int auth(Context c) {
        return Math.min(Math.max(prefs(c).getInt("auth_backend", AUTH_NONE), 0), 3);
    }

    public static String authLabel(Context c) {
        switch (auth(c)) {
            case AUTH_LOCAL_CERT: return "本地证书 (.p7b + .pk8)";
            case AUTH_MFI_CHIP: return "MFi 芯片 (I2C / CH341)";
            case AUTH_REMOTE_MFI: return "远程 MFI 服务";
            default: return "未配置";
        }
    }

    public static boolean hasLocalIdentity(Context c) {
        if (assetExists(c, "carplay/identity.pk8")
                && assetExists(c, "carplay/certificate.p7b")) {
            return true;
        }
        File dir = new File(c.getExternalFilesDir(null), "carplay");
        return new File(dir, "identity.pk8").isFile()
                && new File(dir, "certificate.p7b").isFile();
    }

    public static boolean hasAuth(Context c) {
        switch (auth(c)) {
            case AUTH_LOCAL_CERT: return hasLocalIdentity(c);
            case AUTH_MFI_CHIP:
            case AUTH_REMOTE_MFI: return true;
            default: return false;
        }
    }

    public static String identityHint(Context c) {
        File dir = new File(c.getExternalFilesDir(null), "carplay");
        return "查找位置：assets/carplay/ 或 " + dir.getAbsolutePath();
    }

    public static int appleUsbCount(Context c) {
        try {
            UsbManager um = (UsbManager) c.getSystemService(Context.USB_SERVICE);
            if (um == null) return 0;
            int n = 0;
            for (Map.Entry<String, UsbDevice> e : um.getDeviceList().entrySet()) {
                if (e.getValue().getVendorId() == APPLE_VENDOR_ID) n++;
            }
            return n;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String startWired(Context c) {
        if (!hasAuth(c)) {
            return "未配置 MFi 认证：iPhone 会在认证阶段拒绝连接。请先准备证书 / 芯片 / 远程 MFI。";
        }
        return "认证已配置，但 CarPlay 协议引擎尚未接入；当前版本只完成链路骨架。";
    }

    public static String startWireless(Context c) {
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) {
                return "Wi-Fi 未开启：无线 CarPlay 需要车机先连到与 iPhone 同一局域网。";
            }
        } catch (Exception ignored) { }
        if (!hasAuth(c)) {
            return "未配置 MFi 认证：无线 CarPlay 同样过不了认证。";
        }
        return "认证已配置，但 CarPlay 协议引擎尚未接入；当前版本只完成链路骨架。";
    }

    private static boolean assetExists(Context c, String path) {
        try {
            c.getAssets().open(path).close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
