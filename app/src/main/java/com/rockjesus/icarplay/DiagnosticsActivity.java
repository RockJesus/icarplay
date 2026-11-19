package com.rockjesus.icarplay;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.location.LocationManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * 设备诊断：把车机这一侧真正能读到的信息一次性摊开。
 *
 * <p>三层：A 硬件传感器（SensorManager 全表 + 实测值）、B 系统/界面状态、
 * C 厂商车控线索（系统服务名 / 系统属性 / 已装包 / sysfs 节点）。
 * 全只读，每项独立 try/catch，读不到就标"不可用"，绝不因为一项失败影响整页。</p>
 */
public class DiagnosticsActivity extends FitActivity {

    private static final String VENDOR_KEYS =
            "ford,sync,vehicle,canbus,hvac,mcu,cluster,adas,car_";

    private LinearLayout body;
    private TextView summary;
    private TextView tip;
    private Button rescanButton;
    private volatile boolean scanning;

    private volatile String report = "";
    private volatile int reportItems;

    // ---------------------------------------------------------------- 数据模型

    /** 一行：名称 + 值；ok=false 用灰字显示。 */
    private static final class Row {
        final String label;
        final String value;
        final boolean ok;

        Row(String label, String value, boolean ok) {
            this.label = label;
            this.value = value;
            this.ok = ok;
        }
    }

    /** 一张分区卡片。 */
    private static final class Section {
        final String title;
        final int accent;
        final List<Row> rows = new ArrayList<>();

        Section(String title, int accent) {
            this.title = title;
            this.accent = accent;
        }

        void add(String label, String value, boolean ok) {
            rows.add(new Row(label, value, ok));
        }

        void add(String label, String value) {
            rows.add(new Row(label, value, true));
        }
    }

    // ---------------------------------------------------------------- 生命周期

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(PageUi.background(this));
        root.setPadding(PageUi.dp(this, 18), PageUi.dp(this, 10),
                PageUi.dp(this, 18), PageUi.dp(this, 8));

        root.addView(PageUi.header(this, "设备诊断", IcarLauncherActivity.VERSION_LABEL),
                PageUi.matchWrap(this));

        summary = PageUi.text(this, "正在扫描车机…", 10.5f, PageUi.CYAN, false);
        summary.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams summaryLp = PageUi.matchWrap(this);
        PageUi.margin(summaryLp, this, 0, 2, 0, 4);
        root.addView(summary, summaryLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, scrollLp);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tip = PageUi.text(this, "", 9f, PageUi.MUTED, false);
        LinearLayout.LayoutParams tipLp = PageUi.matchWrap(this);
        PageUi.margin(tipLp, this, 2, 4, 0, 4);
        root.addView(tip, tipLp);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams toolsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, PageUi.dp(this, 42));
        root.addView(tools, toolsLp);

        rescanButton = tool("重新扫描", PageUi.BLUE, v -> rescan());
        tools.addView(rescanButton);
        tools.addView(tool("保存报告", PageUi.GREEN, v -> saveReport()));
        tools.addView(tool("复制全文", PageUi.PURPLE, v -> copyReport()));
        tools.addView(tool("上报到 HA", PageUi.GOLD, v -> pushToHa()));

        setFitContentView(root);
        rescan();
    }

    private Button tool(String label, int accent, View.OnClickListener l) {
        Button b = PageUi.button(this, label, 11.5f, accent);
        b.setOnClickListener(l);
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        PageUi.margin(lp, this, 4, 0, 4, 0);
        b.setLayoutParams(lp);
        return b;
    }

    // ---------------------------------------------------------------- 扫描编排

    private void rescan() {
        if (scanning) return;
        scanning = true;
        rescanButton.setEnabled(false);
        summary.setText("正在扫描车机…");
        body.removeAllViews();

        new Thread(() -> {
            List<Section> sections = new ArrayList<>();
            try {
                sections.add(scanHardwareSensors());
            } catch (Throwable t) {
                Section fail = new Section("A · 硬件传感器", PageUi.BLUE);
                fail.add("扫描失败", String.valueOf(t), false);
                sections.add(fail);
            }
            try {
                sections.add(scanSystemState());
            } catch (Throwable t) {
                Section fail = new Section("B · 系统状态", PageUi.GREEN);
                fail.add("扫描失败", String.valueOf(t), false);
                sections.add(fail);
            }
            try {
                sections.add(scanVendor());
            } catch (Throwable t) {
                Section fail = new Section("C · 厂商车控线索", PageUi.GOLD);
                fail.add("扫描失败", String.valueOf(t), false);
                sections.add(fail);
            }
            try {
                sections.add(scanReported());
            } catch (Throwable t) {
                Section fail = new Section("D · 当前已上报 HA 的项", PageUi.PINK);
                fail.add("扫描失败", String.valueOf(t), false);
                sections.add(fail);
            }

            String text = buildReport(sections);
            int items = 0;
            for (Section s : sections) items += s.rows.size();
            report = text;
            reportItems = items;

            runOnUiThread(() -> {
                render(sections);
                scanning = false;
                rescanButton.setEnabled(true);
            });
        }, "icarplay-diagnostics").start();
    }

    private void render(List<Section> sections) {
        body.removeAllViews();
        for (Section s : sections) {
            LinearLayout card = PageUi.card(this, s.accent, 12, 10);
            LinearLayout.LayoutParams lp = PageUi.matchWrap(this);
            PageUi.margin(lp, this, 0, 0, 0, 8);
            card.setLayoutParams(lp);

            card.addView(PageUi.text(this, s.title + "  ·  " + s.rows.size() + " 项",
                    11.5f, s.accent, true), PageUi.matchWrap(this));

            for (Row r : s.rows) {
                TextView line = PageUi.text(this, r.label + "  " + r.value,
                        9.5f, r.ok ? PageUi.TEXT : PageUi.MUTED, false);
                line.setPadding(0, PageUi.dp(this, 2), 0, PageUi.dp(this, 1));
                card.addView(line, PageUi.matchWrap(this));
            }
            body.addView(card);
        }

        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        summary.setText("扫描完成 " + stamp + " · 共 " + reportItems + " 项");
        tip.setText("灰字＝读到但无数据或不可用。C 类若全为 0，说明 SYNC+ 没把车控数据暴露给 Android 层。");
    }

    private String buildReport(List<Section> sections) {
        StringBuilder b = new StringBuilder();
        b.append("iCarPlay 设备诊断报告");
        b.append("\n时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date()));
        b.append("\n机型: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
                .append(" · Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")");
        b.append("\n版本: ").append(IcarLauncherActivity.VERSION_LABEL);
        for (Section s : sections) {
            b.append("\n\n[").append(s.title).append("]");
            for (Row r : s.rows) {
                b.append("\n- ").append(r.label).append(": ").append(r.value);
            }
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- A 硬件传感器

    private Section scanHardwareSensors() {
        Section s = new Section("A · 硬件传感器（SensorManager 全表）", PageUi.BLUE);
        SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) {
            s.add("SensorManager", "不可用", false);
            return s;
        }

        List<Sensor> list = sm.getSensorList(Sensor.TYPE_ALL);
        final Map<Integer, float[]> seen = new HashMap<>();
        final SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                synchronized (seen) {
                    seen.put(event.sensor.getType(), event.values.clone());
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };
        for (Sensor one : list) {
            try {
                sm.registerListener(listener, one, SensorManager.SENSOR_DELAY_NORMAL);
            } catch (Throwable ignored) {
            }
        }
        try {
            Thread.sleep(1600);
        } catch (InterruptedException ignored) {
        }
        try {
            sm.unregisterListener(listener);
        } catch (Throwable ignored) {
        }

        s.add("传感器总数", list.size() + " 个", !list.isEmpty());
        if (list.isEmpty()) {
            s.add("判定", "驱动未上报任何硬件传感器（车机常见）", false);
        }

        for (Sensor one : list) {
            float[] values;
            synchronized (seen) {
                values = seen.get(one.getType());
            }
            String detail = sensorTypeName(one.getType())
                    + " · " + one.getVendor()
                    + " · " + (one.getMinDelay() > 0
                    ? (1000000 / one.getMinDelay()) + "Hz 上限" : "变化触发");
            String value = values == null ? detail + " · 无事件" : detail + " · 值 " + fmt(values);
            s.add(one.getName(), value, values != null);
        }
        return s;
    }

    private String sensorTypeName(int type) {
        switch (type) {
            case Sensor.TYPE_ACCELEROMETER: return "加速度";
            case Sensor.TYPE_MAGNETIC_FIELD: return "磁场";
            case Sensor.TYPE_GYROSCOPE: return "陀螺仪";
            case Sensor.TYPE_LIGHT: return "光感";
            case Sensor.TYPE_PRESSURE: return "气压";
            case Sensor.TYPE_PROXIMITY: return "接近";
            case Sensor.TYPE_GRAVITY: return "重力";
            case Sensor.TYPE_LINEAR_ACCELERATION: return "线性加速度";
            case Sensor.TYPE_ROTATION_VECTOR: return "旋转矢量";
            case Sensor.TYPE_ORIENTATION: return "方向";
            case Sensor.TYPE_GAME_ROTATION_VECTOR: return "游戏旋转矢量";
            case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR: return "地磁旋转矢量";
            case Sensor.TYPE_STEP_DETECTOR: return "计步触发";
            case Sensor.TYPE_RELATIVE_HUMIDITY: return "湿度";
            case Sensor.TYPE_AMBIENT_TEMPERATURE: return "环温";
            case Sensor.TYPE_STEP_COUNTER: return "计步";
            case Sensor.TYPE_SIGNIFICANT_MOTION: return "显著运动";
            default: return "type " + type;
        }
    }

    private String fmt(float[] values) {
        if (values == null) return "无数据";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(values.length, 3); i++) {
            if (i > 0) b.append(", ");
            b.append(String.format(Locale.US, "%.2f", values[i]));
        }
        if (values.length > 3) b.append(" …");
        return b.toString();
    }

    // ---------------------------------------------------------------- B 系统状态

    private Section scanSystemState() {
        Section s = new Section("B · 系统 / 界面状态", PageUi.GREEN);

        // 电池（含温度、电压、健康，现有上报只有电量与充电）
        try {
            Intent sticky = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (sticky == null) {
                s.add("电池", "系统未广播", false);
            } else {
                int level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int health = sticky.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
                int temp = sticky.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                int volt = sticky.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                int plugged = sticky.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                String tech = sticky.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
                s.add("电量", (scale > 0 ? (level * 100 / scale) : level) + "%  ("
                        + batteryStatus(status) + ")");
                s.add("供电", plugged == 1 ? "AC 充电" : plugged == 2 ? "USB 充电"
                        : plugged == 4 ? "无线充电" : "未充电", plugged > 0);
                s.add("健康", batteryHealth(health));
                s.add("温度", temp < 0 ? "不可用" : String.format(Locale.US, "%.1f ℃", temp / 10f), temp >= 0);
                // 电压低于 0.1V 视为没读到（部分模拟/定制系统会给 0 或 1mV 占位）
                s.add("电压", volt < 100 ? "不可用" : String.format(Locale.US, "%.2f V", volt / 1000f), volt >= 100);
                s.add("技术", tech == null ? "不可用" : tech, tech != null);
            }
            BatteryManager bm = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                long counter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                s.add("电荷计数", counter == Long.MIN_VALUE || counter <= 0
                        ? "不可用" : (counter / 1000) + " mAh", counter > 0);
            }
        } catch (Throwable t) {
            s.add("电池", "读取异常", false);
        }

        // 网络
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo active = cm == null ? null : cm.getActiveNetworkInfo();
            s.add("联网", active != null && active.isConnected() ? "已连接" : "未连接",
                    active != null && active.isConnected());
            s.add("网络类型", active == null ? "无" : netTypeName(active.getType()), active != null);
            if (active != null) {
                String subtype = active.getSubtypeName();
                String detail = (subtype == null || subtype.isEmpty() ? "" : subtype + " · ")
                        + (active.isRoaming() ? "漫游" : "非漫游");
                s.add("网络细节", detail, true);
            }
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                WifiInfo wi = wm.getConnectionInfo();
                s.add("Wi-Fi 开关", wm.isWifiEnabled() ? "开" : "关", wm.isWifiEnabled());
                if (wi != null && wi.getNetworkId() >= 0) {
                    String ssid = wi.getSSID();
                    if (ssid != null) ssid = ssid.replace("\"", "");
                    s.add("Wi-Fi 名称", ssid == null ? "未知" : ssid, true);
                    s.add("Wi-Fi 信号", wi.getRssi() + " dBm", wi.getRssi() != 0);
                    s.add("Wi-Fi 链路", wi.getLinkSpeed() + " Mbps · " + wi.getFrequency() + " MHz", wi.getLinkSpeed() > 0);
                    s.add("本机 IPv4", ipv4(wi.getIpAddress()), wi.getIpAddress() != 0);
                    s.add("BSSID", wi.getBSSID() == null ? "未知" : wi.getBSSID(), wi.getBSSID() != null);
                } else {
                    s.add("Wi-Fi", "未连接到任何 AP", false);
                }
            }
        } catch (Throwable t) {
            s.add("网络", "读取异常", false);
        }

        // 移动网络 / 运营商
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String operator = tm.getNetworkOperatorName();
                s.add("运营商", operator == null || operator.isEmpty() ? "不可用" : operator,
                        operator != null && !operator.isEmpty());
                s.add("SIM 状态", simState(tm.getSimState()), tm.getSimState() == TelephonyManager.SIM_STATE_READY);
                s.add("制式", telephonyType(tm.getNetworkType()));
            }
        } catch (Throwable t) {
            s.add("运营商", "读取异常", false);
        }

        // 存储
        try {
            s.add("内部存储", statFsText(Environment.getDataDirectory()), true);
            File ext = Environment.getExternalStorageDirectory();
            if (ext != null && !ext.getPath().equals(Environment.getDataDirectory().getPath())) {
                s.add("外部存储", statFsText(ext), true);
            }
            File[] vols = new File("/storage").listFiles();
            if (vols != null) {
                StringBuilder b = new StringBuilder();
                for (File v : vols) {
                    if (!b.toString().isEmpty()) b.append(" · ");
                    b.append(v.getName());
                }
                s.add("挂载卷 /storage", b.length() == 0 ? "空" : b.toString(), b.length() > 0);
            }
        } catch (Throwable t) {
            s.add("存储", "读取异常", false);
        }

        // 内存
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            s.add("内存", (mi.availMem / 1048576) + " / " + (mi.totalMem / 1048576) + " MB 可用", true);
            s.add("低内存状态", mi.lowMemory ? "是" : "否", true);
        } catch (Throwable t) {
            s.add("内存", "读取异常", false);
        }

        // 屏幕 / 亮度 / 锁屏
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            s.add("屏幕", pm.isInteractive() ? "亮" : "灭", true);
            int brightness = Settings.System.getInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, -1);
            int mode = Settings.System.getInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE, -1);
            s.add("亮度值", brightness < 0 ? "不可读" : brightness + " / 255", brightness >= 0);
            s.add("自动亮度", mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC ? "开" : "关", mode >= 0);
            int timeout = Settings.System.getInt(getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, -1);
            s.add("熄屏超时", timeout < 0 ? "不可读" : (timeout / 1000) + " 秒", timeout >= 0);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) {
                s.add("锁屏", km.isKeyguardLocked() ? "已锁" : "未锁", true);
            }
            DisplayMetrics dm = getResources().getDisplayMetrics();
            s.add("分辨率", dm.widthPixels + " × " + dm.heightPixels + " @ " + dm.densityDpi + "dpi", true);
        } catch (Throwable t) {
            s.add("屏幕", "读取异常", false);
        }

        // 音量与音频输出
        try {
            AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audio != null) {
                s.add("音量·音乐", audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                        + " / " + audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), true);
                s.add("音量·铃声", audio.getStreamVolume(AudioManager.STREAM_RING)
                        + " / " + audio.getStreamMaxVolume(AudioManager.STREAM_RING), true);
                s.add("音量·通知", audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                        + " / " + audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION), true);
                s.add("音量·闹钟", audio.getStreamVolume(AudioManager.STREAM_ALARM)
                        + " / " + audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), true);
                s.add("音频模式", audioMode(audio.getMode()));
                AudioDeviceInfo[] outs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                StringBuilder b = new StringBuilder();
                for (AudioDeviceInfo info : outs) {
                    if (!b.toString().isEmpty()) b.append(" · ");
                    b.append(audioDeviceName(info.getType()));
                }
                s.add("音频输出", b.length() == 0 ? "无" : b.toString(), b.length() > 0);
            }
        } catch (Throwable t) {
            s.add("音频", "读取异常", false);
        }

        // USB
        try {
            UsbManager usb = (UsbManager) getSystemService(Context.USB_SERVICE);
            Map<String, UsbDevice> devices = usb == null ? null : usb.getDeviceList();
            int count = devices == null ? 0 : devices.size();
            s.add("USB 设备数", count + " 个", true);
            if (devices != null) {
                for (UsbDevice d : devices.values()) {
                    String name;
                    try {
                        name = d.getProductName() + " / " + d.getManufacturerName();
                    } catch (Throwable t2) {
                        name = "无名称";
                    }
                    s.add("USB·" + d.getDeviceName(),
                            "VID " + d.getVendorId() + " PID " + d.getProductId() + " · " + name, true);
                }
            }
        } catch (Throwable t) {
            s.add("USB", "读取异常", false);
        }

        // 蓝牙
        try {
            BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = bm == null ? BluetoothAdapter.getDefaultAdapter() : bm.getAdapter();
            if (adapter == null) {
                s.add("蓝牙", "无适配器", false);
            } else {
                s.add("蓝牙开关", adapter.isEnabled() ? "开" : "关", adapter.isEnabled());
                if (adapter.isEnabled()) {
                    s.add("本机蓝牙名", adapter.getName() == null ? "未知" : adapter.getName(), true);
                    Set<BluetoothDevice> bonded = adapter.getBondedDevices();
                    s.add("已配对设备", bonded == null ? "0 个" : bonded.size() + " 个", true);
                    if (bonded != null) {
                        for (BluetoothDevice d : bonded) {
                            s.add("配对·" + d.getName(), d.getAddress(), true);
                        }
                    }
                    if (bm != null) {
                        List<BluetoothDevice> connected = bm.getConnectedDevices(BluetoothProfile.GATT);
                        s.add("已连接(GATT)", connected == null ? "0 个" : connected.size() + " 个", true);
                    }
                }
            }
        } catch (Throwable t) {
            s.add("蓝牙", "读取失败（可能缺权限）", false);
        }

        // 通知
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            int active = nm == null ? 0 : nm.getActiveNotifications().length;
            s.add("本应用通知", active + " 条", true);
            String listeners = Settings.Secure.getString(getContentResolver(),
                    "enabled_notification_listeners");
            boolean granted = listeners != null && listeners.contains(getPackageName());
            s.add("通知监听授权", granted ? "已授权" : "未授权（读不到其它应用通知）", granted);
        } catch (Throwable t) {
            s.add("通知", "读取异常", false);
        }

        // 定位
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            StringBuilder b = new StringBuilder();
            if (lm != null) {
                for (String p : lm.getProviders(true)) {
                    if (b.length() > 0) b.append(" · ");
                    b.append(p);
                }
            }
            s.add("定位 provider", b.length() == 0 ? "无" : b.toString(), b.length() > 0);
            s.add("GPS 开关", lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? "开" : "关",
                    lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER));
            s.add("定位授权", checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED ? "已授权" : "未授权", false);
        } catch (Throwable t) {
            s.add("定位", "读取异常", false);
        }

        // CPU / 温度
        try {
            s.add("CPU 核心", Runtime.getRuntime().availableProcessors() + " 核", true);
            String load = readText(new File("/proc/loadavg"));
            s.add("系统负载", load == null ? "不可读" : shorten(load, 40), load != null);
            String freq = readText(new File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"));
            if (freq != null) {
                try {
                    s.add("cpu0 频率", (Long.parseLong(freq.trim()) / 1000) + " MHz", true);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            s.add("CPU", "读取异常", false);
        }
        try {
            File thermal = new File("/sys/class/thermal");
            File[] zones = thermal.listFiles();
            int shown = 0;
            if (zones != null) {
                for (File zone : zones) {
                    if (!zone.getName().startsWith("thermal_zone") || shown >= 5) continue;
                    String temp = readText(new File(zone, "temp"));
                    if (temp == null) continue;
                    String type = readText(new File(zone, "type"));
                    try {
                        long t = Long.parseLong(temp.trim());
                        double c = t > 1000 ? t / 1000d : t;
                        s.add("温区 " + (type == null ? zone.getName() : type),
                                String.format(Locale.US, "%.1f ℃", c), true);
                        shown++;
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (shown == 0) s.add("温度通道", "无可用 thermal_zone", false);
        } catch (Throwable t) {
            s.add("温度", "读取异常", false);
        }

        // 系统信息
        try {
            s.add("系统版本", "Android " + Build.VERSION.RELEASE + " · API " + Build.VERSION.SDK_INT, true);
            s.add("机型", Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")", true);
            s.add("固件", Build.DISPLAY == null ? "未知" : Build.DISPLAY, true);
            s.add("语言", Locale.getDefault().toString(), true);
            s.add("时区", TimeZone.getDefault().getID(), true);
            s.add("当前时间", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date()), true);
        } catch (Throwable ignored) {
        }
        return s;
    }

    // ---------------------------------------------------------------- C 厂商线索

    private Section scanVendor() {
        Section s = new Section("C · 厂商车控线索", PageUi.GOLD);

        // 1) 反射 ServiceManager.listServices()：Binder 服务全名（API 27 可反射）
        String[] services = null;
        try {
            Class<?> cls = Class.forName("android.os.ServiceManager");
            Object result = cls.getMethod("listServices").invoke(null);
            if (result instanceof String[]) services = (String[]) result;
        } catch (Throwable t) {
            s.add("系统服务", "反射失败（" + t.getClass().getSimpleName() + "）", false);
        }

        if (services != null) {
            s.add("系统服务总数", services.length + " 个", true);
            StringBuilder hits = new StringBuilder();
            int hitCount = 0;
            for (String name : services) {
                if (name == null) continue;
                String lower = name.toLowerCase(Locale.US);
                if (lower.startsWith("car") || lower.contains("vehicle") || lower.contains("canbus")
                        || lower.contains("hvac") || lower.startsWith("mcu") || lower.startsWith("ford")
                        || lower.startsWith("sync") || lower.contains("_sync")
                        || lower.contains("cluster") || lower.contains("adas")) {
                    if (hits.length() > 0) hits.append(" · ");
                    hits.append(name);
                    hitCount++;
                }
            }
            s.add("车控相关服务", hitCount == 0 ? "未发现" : hits.toString(), hitCount > 0);
        }

        // 2) getprop 全量，过滤厂商关键字
        List<String> props = execLines(new String[]{"getprop"});
        if (props.isEmpty()) {
            s.add("系统属性", "读取失败", false);
        } else {
            int hits = 0;
            for (String line : props) {
                String lower = line.toLowerCase(Locale.US);
                if (containsVendorKey(lower)) {
                    s.add("prop", shorten(line, 90), true);
                    hits++;
                    if (hits >= 12) break;
                }
            }
            if (hits == 0) s.add("厂商属性", "未发现 ford/sync/vehicle/canbus/mcu 相关属性", false);
        }

        // 3) 已安装包（Android 8.1 不受包可见性限制）
        try {
            PackageManager pm = getPackageManager();
            List<PackageInfo> packages = pm.getInstalledPackages(0);
            int hits = 0;
            for (PackageInfo info : packages) {
                String name = info.packageName == null ? "" : info.packageName.toLowerCase(Locale.US);
                if (name.contains("ford") || name.contains("sync") || name.contains("vehicle")
                        || name.contains("carlife")) {
                    String label = String.valueOf(pm.getApplicationLabel(info.applicationInfo));
                    s.add("应用·" + info.packageName, label + " · " + version(info.versionName), true);
                    hits++;
                }
            }
            s.add("已装应用总数", packages.size() + " 个", true);
            if (hits == 0) s.add("厂商应用", "未发现 ford/sync/vehicle/carlife 相关包", false);
        } catch (Throwable t) {
            s.add("应用列表", "读取失败", false);
        }

        // 4) sysfs 节点与 CAN / MCU 目录
        try {
            File sys = new File("/sys/class");
            File[] entries = sys.listFiles();
            if (entries != null) {
                StringBuilder b = new StringBuilder();
                for (File f : entries) {
                    String n = f.getName().toLowerCase(Locale.US);
                    if (n.contains("ford") || n.contains("mcu") || n.contains("can")
                            || n.contains("vehicle") || n.contains("hvac")) {
                        if (b.length() > 0) b.append(" · ");
                        b.append(f.getName());
                    }
                }
                s.add("/sys/class 车控节点", b.length() == 0 ? "未发现" : b.toString(), b.length() > 0);
                s.add("/sys/class 总数", entries.length + " 个", true);
            } else {
                s.add("/sys/class", "不可读", false);
            }
        } catch (Throwable t) {
            s.add("/sys/class", "读取异常", false);
        }

        s.add("判定", "若上面全是「未发现」，说明车控只走 SYNC+ 私有层（广播/CAN 网关），"
                + "需要抓 logcat 或反编译车机自带车辆应用才能拿到车门/空调/车速", false);
        return s;
    }

    private boolean containsVendorKey(String lowerLine) {
        String[] keys = {"ford", "sync", "vehicle", "canbus", "hvac", "mcu"};
        for (String key : keys) {
            if (lowerLine.contains(key)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- D 已上报项

    private Section scanReported() {
        Section s = new Section("D · 当前已上报 HA 的项（实时值）", PageUi.PINK);
        List<HaSensors.Item> items = HaSensors.collect(this);
        s.add("条目数", items.size() + " 项", !items.isEmpty());
        for (HaSensors.Item item : items) {
            String raw = String.valueOf(item.state);
            String value = ("true".equals(raw) ? "开" : "false".equals(raw) ? "关" : raw)
                    + (item.unit == null ? "" : " " + item.unit);
            s.add((item.type.equals("binary_sensor") ? "○ " : "● ") + item.name, value, true);
        }
        return s;
    }

    // ---------------------------------------------------------------- 动作

    private void saveReport() {
        String text = report;
        if (text == null || text.isEmpty()) {
            toast("还没有扫描结果");
            return;
        }
        new Thread(() -> {
            String path = null;
            try {
                File dir = getExternalFilesDir(null);
                if (dir == null) dir = getFilesDir();
                File file = new File(dir, "icarplay-diagnostics.txt");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(text.getBytes("UTF-8"));
                }
                path = file.getAbsolutePath();
            } catch (Throwable ignored) {
            }
            final String finalPath = path;
            runOnUiThread(() -> {
                if (finalPath == null) {
                    toast("保存失败");
                } else {
                    toast("已保存：" + finalPath);
                }
            });
        }, "diagnostics-save").start();
    }

    private void copyReport() {
        String text = report;
        if (text == null || text.isEmpty()) {
            toast("还没有扫描结果");
            return;
        }
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("icarplay-diagnostics", text));
            toast("已复制 " + text.length() + " 字符");
        } catch (Throwable t) {
            toast("复制失败");
        }
    }

    private void pushToHa() {
        String text = report;
        if (text == null || text.isEmpty()) {
            toast("还没有扫描结果");
            return;
        }
        if (HaApi.baseUrl(this).isEmpty() || HaApi.token(this).isEmpty()) {
            toast("先在「认证设置」里填 HA 地址与令牌");
            return;
        }
        toast("正在上报到 HA…");
        new Thread(() -> {
            String message;
            try {
                JSONObject payload = new JSONObject();
                payload.put("state", "扫描 " + reportItems + " 项 · "
                        + Build.MANUFACTURER + " " + Build.MODEL);
                JSONObject attrs = new JSONObject();
                attrs.put("friendly_name", "iCarPlay 设备诊断");
                attrs.put("icon", "mdi:stethoscope");
                attrs.put("report", text.length() > 8000 ? text.substring(0, 8000) : text);
                payload.put("attributes", attrs);
                HaApi.Resp resp = HaApi.send(DiagnosticsActivity.this, "POST",
                        "/api/states/sensor.icarplay_diagnostics", payload.toString(), 8000);
                if (resp.code >= 200 && resp.code < 300) {
                    message = "已上报 sensor.icarplay_diagnostics";
                } else if (resp.code == 0) {
                    message = "连不上 HA，检查地址/网络";
                } else {
                    message = "上报失败 HTTP " + resp.code;
                }
            } catch (Throwable t) {
                message = "上报异常：" + t.getClass().getSimpleName();
            }
            final String finalMessage = message;
            runOnUiThread(() -> toast(finalMessage));
        }, "diagnostics-ha").start();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- 工具

    private List<String> execLines(String[] command) {
        List<String> lines = new ArrayList<>();
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(command);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) lines.add(line);
            }
            process.waitFor();
        } catch (Throwable ignored) {
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
        return lines;
    }

    private String readText(File file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line = reader.readLine();
            return line == null ? null : line.trim();
        } catch (Throwable t) {
            return null;
        }
    }

    private String statFsText(File path) {
        try {
            StatFs fs = new StatFs(path.getPath());
            long free = fs.getAvailableBlocksLong() * fs.getBlockSizeLong();
            long total = fs.getBlockCountLong() * fs.getBlockSizeLong();
            return String.format(Locale.US, "%.1f / %.1f GB 可用", free / 1073741824d, total / 1073741824d);
        } catch (Throwable t) {
            return "不可读";
        }
    }

    private String batteryStatus(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "充电中";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "放电中";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "未充电";
            case BatteryManager.BATTERY_STATUS_FULL: return "已充满";
            default: return "未知";
        }
    }

    private String batteryHealth(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "良好";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "过热";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "失效";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "过压";
            case BatteryManager.BATTERY_HEALTH_COLD: return "过冷";
            default: return "未知";
        }
    }

    private String netTypeName(int type) {
        switch (type) {
            case ConnectivityManager.TYPE_WIFI: return "Wi-Fi";
            case ConnectivityManager.TYPE_MOBILE: return "移动数据";
            case ConnectivityManager.TYPE_ETHERNET: return "以太网";
            case ConnectivityManager.TYPE_BLUETOOTH: return "蓝牙共享";
            default: return "type " + type;
        }
    }

    private String telephonyType(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_UNKNOWN: return "未知";
            case TelephonyManager.NETWORK_TYPE_LTE: return "LTE / 4G";
            case TelephonyManager.NETWORK_TYPE_NR: return "5G";
            case TelephonyManager.NETWORK_TYPE_UMTS: return "3G (UMTS)";
            case TelephonyManager.NETWORK_TYPE_CDMA: return "2G/3G (CDMA)";
            case TelephonyManager.NETWORK_TYPE_GSM: return "2G (GSM)";
            case TelephonyManager.NETWORK_TYPE_EDGE: return "2G (EDGE)";
            default: return "type " + type;
        }
    }

    private String simState(int state) {
        switch (state) {
            case TelephonyManager.SIM_STATE_READY: return "就绪";
            case TelephonyManager.SIM_STATE_ABSENT: return "无卡";
            case TelephonyManager.SIM_STATE_PIN_REQUIRED: return "需要 PIN";
            case TelephonyManager.SIM_STATE_PUK_REQUIRED: return "需要 PUK";
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED: return "网络锁定";
            default: return "未知";
        }
    }

    private String audioMode(int mode) {
        switch (mode) {
            case AudioManager.MODE_NORMAL: return "普通";
            case AudioManager.MODE_RINGTONE: return "响铃";
            case AudioManager.MODE_IN_CALL: return "通话中";
            case AudioManager.MODE_IN_COMMUNICATION: return "通信中";
            default: return "type " + mode;
        }
    }

    private String audioDeviceName(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "内置喇叭";
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: return "听筒";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "有线耳机";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "有线耳麦";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "蓝牙 A2DP";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "蓝牙 SCO";
            case AudioDeviceInfo.TYPE_USB_DEVICE: return "USB 声卡";
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB 耳麦";
            case AudioDeviceInfo.TYPE_HDMI: return "HDMI";
            case AudioDeviceInfo.TYPE_LINE_ANALOG: return "模拟线路";
            default: return "type " + type;
        }
    }

    private String ipv4(int ip) {
        if (ip == 0) return "无";
        return ((ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "."
                + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF));
    }

    private String version(String v) {
        return v == null || v.isEmpty() ? "版本未知" : "v" + v;
    }

    private String shorten(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
