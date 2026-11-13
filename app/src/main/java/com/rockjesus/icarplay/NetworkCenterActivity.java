package com.rockjesus.icarplay;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.DhcpInfo;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import io.github.jqssun.airplay.service.AirPlayService;

/**
 * 网络与连接中心：只读展示 Wi‑Fi/热点状态，并提供热点向导与 mDNS 体检。
 * 不修改系统网络配置，不自动替用户连未知热点。
 */
public class NetworkCenterActivity extends Activity {

    private static final int REQ_WIFI = 70;
    private static final int REQ_BT = 71;
    private static WifiManager.LocalOnlyHotspotReservation sHotspot;
    private static String sHotspotSsid = "";
    private static String sHotspotPass = "";

    private WifiManager wifi;
    private NsdManager nsd;
    private WifiManager.MulticastLock multicastLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> mdnsFound = new ArrayList<>();
    private final List<String> mdnsErrors = new ArrayList<>();
    private NsdManager.DiscoveryListener airplayListener;
    private NsdManager.DiscoveryListener raopListener;
    private boolean mdnsRunning;
    private boolean netCheckRunning;
    private boolean portVerified;

    private TextView statusPill;
    private TextView wifiValue;
    private TextView bandValue;
    private TextView ipValue;
    private TextView gatewayValue;
    private TextView dnsValue;
    private TextView hotspotValue;
    private TextView airplayValue;
    private TextView portValue;
    private TextView actionHint;
    private TextView btValue;
    private TextView hicarValue;
    private TextView usbValue;
    private TextView capClient;
    private TextView capAp;
    private TextView capP2p;
    private TextView cap5g;
    private boolean hicarScanned;
    private boolean hicarFramework;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1200);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        nsd = (NsdManager) getSystemService(Context.NSD_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(PageUi.background(this));
        root.setPadding(PageUi.dp(this, 18), PageUi.dp(this, 10),
                PageUi.dp(this, 18), PageUi.dp(this, 8));
        setContentView(root);

        root.addView(PageUi.header(this, "连接中心", IcarLauncherActivity.VERSION_LABEL),
                PageUi.matchWrap(this));

        TextView sub = PageUi.text(this, "Wi‑Fi / 蓝牙 / USB / 热点 · 网络体检与 AirPlay 自检共用",
                10.5f, PageUi.MUTED, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subLp = PageUi.matchWrap(this);
        PageUi.margin(subLp, this, 0, 3, 0, 7);
        root.addView(sub, subLp);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, PageUi.dp(this, 2));
        scroll.addView(content, PageUi.matchWrap(this));

        LinearLayout cardsRow = new LinearLayout(this);
        cardsRow.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(cardsRow, PageUi.matchWrap(this));

        LinearLayout statusCard = PageUi.card(this, PageUi.BLUE, 12, 8);
        cardsRow.addView(statusCard, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f));

        LinearLayout statusHead = new LinearLayout(this);
        statusHead.setOrientation(LinearLayout.HORIZONTAL);
        statusHead.setGravity(Gravity.CENTER_VERTICAL);
        statusHead.addView(PageUi.text(this, "网络状态", 8.5f, PageUi.BLUE, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statusPill = PageUi.statusPill(this, "读取中", PageUi.BLUE);
        statusHead.addView(statusPill, PageUi.wrap(this));
        statusCard.addView(statusHead, PageUi.matchWrap(this));

        wifiValue = valueText();
        bandValue = valueText();
        ipValue = valueText();
        gatewayValue = valueText();
        dnsValue = valueText();
        hotspotValue = valueText();
        airplayValue = valueText();
        portValue = valueText();

        addMetricRow(statusCard, "Wi‑Fi / SSID", wifiValue, "频段 / 速率", bandValue);
        addMetricRow(statusCard, "本机 IP", ipValue, "网关", gatewayValue);
        addMetricRow(statusCard, "DNS", dnsValue, "热点 / Wi‑Fi Direct", hotspotValue);
        addMetricRow(statusCard, "AirPlay 接收", airplayValue, "本机端口", portValue);

        LinearLayout capsRow = new LinearLayout(this);
        capsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams capsLp = PageUi.matchWrap(this);
        PageUi.margin(capsLp, this, 0, 6, 0, 0);
        statusCard.addView(capsRow, capsLp);
        capClient = capPill(capsRow, "客户端", PageUi.BLUE);
        capAp = capPill(capsRow, "自建热点", PageUi.PURPLE);
        capP2p = capPill(capsRow, "P2P", PageUi.CYAN);
        cap5g = capPill(capsRow, "5GHz", PageUi.GOLD);
        View.OnClickListener capsTap = v -> showWifiCapDialog();
        capsRow.setOnClickListener(capsTap);
        capClient.setOnClickListener(capsTap);
        capAp.setOnClickListener(capsTap);
        capP2p.setOnClickListener(capsTap);
        cap5g.setOnClickListener(capsTap);

        LinearLayout peripheral = PageUi.card(this, PageUi.PURPLE, 12, 8);
        LinearLayout.LayoutParams peripheralLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 2f);
        peripheralLp.setMargins(PageUi.dp(this, 8), 0, 0, 0);
        cardsRow.addView(peripheral, peripheralLp);
        peripheral.addView(PageUi.text(this, "外设状态", 8.5f, PageUi.PURPLE, true),
                PageUi.wrap(this));

        btValue = valueText();
        hicarValue = valueText();
        usbValue = valueText();
        btValue.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9.5f * PageUi.scale(this));
        hicarValue.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9.5f * PageUi.scale(this));
        usbValue.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9.5f * PageUi.scale(this));

        peripheralRow(peripheral, "蓝牙", btValue, v -> onBluetoothTap());
        peripheralRow(peripheral, "HUAWEI HiCar", hicarValue, v -> showHiCarDialog());
        peripheralRow(peripheral, "USB", usbValue, v -> showUsbDialog());

        TextView peripheralHint = PageUi.text(this,
                "点任一项看详情 · 连接补强 ›", 8f, PageUi.CYAN, true);
        peripheralHint.setGravity(Gravity.END);
        peripheralHint.setOnClickListener(v -> {
            try {
                startActivity(new Intent(this, LinkBoostActivity.class));
            } catch (Exception e) {
                toast("连接补强打不开");
            }
        });
        LinearLayout.LayoutParams hintLp = PageUi.matchWrap(this);
        PageUi.margin(hintLp, this, 0, 6, 0, 0);
        peripheral.addView(peripheralHint, hintLp);

        LinearLayout actions = PageUi.card(this, PageUi.GREEN, 10, 8);
        LinearLayout.LayoutParams actionsLp = PageUi.matchWrap(this);
        PageUi.margin(actionsLp, this, 0, 8, 0, 0);
        content.addView(actions, actionsLp);

        LinearLayout actionHead = new LinearLayout(this);
        actionHead.setOrientation(LinearLayout.HORIZONTAL);
        actionHead.setGravity(Gravity.CENTER_VERTICAL);
        actionHead.addView(PageUi.text(this, "快捷操作", 8.5f, PageUi.GREEN, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actionHint = PageUi.text(this, "状态自动刷新", 8.5f, PageUi.MUTED, false);
        actionHead.addView(actionHint, PageUi.wrap(this));
        actions.addView(actionHead, PageUi.matchWrap(this));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(action("打开 Wi‑Fi 设置", PageUi.BLUE, v -> openWifiSettings()), actionLp());
        row1.addView(action("打开热点设置", PageUi.GREEN, v -> openHotspotSettings()), actionLp());
        row1.addView(action("mDNS 体检", PageUi.GOLD, v -> startMdnsCheck()), actionLp());
        row1.addView(action("网络体检", PageUi.CYAN, v -> startNetworkCheck()), actionLp());
        actions.addView(row1, PageUi.matchWrap(this));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(action("开启车机热点", PageUi.PURPLE, v -> startCarHotspot()), actionLp());
        row2.addView(action("停止车机热点", PageUi.RED, v -> stopCarHotspot()), actionLp());
        row2.addView(action("启动 AirPlay", PageUi.BLUE, v -> startAirPlay()), actionLp());
        row2.addView(action("停止 AirPlay", PageUi.RED, v -> stopAirPlay()), actionLp());
        actions.addView(row2, PageUi.matchWrap(this));

        LinearLayout resultCard = PageUi.card(this, PageUi.GOLD, 12, 8);
        LinearLayout.LayoutParams resultLp = PageUi.matchWrap(this);
        PageUi.margin(resultLp, this, 0, 8, 0, 0);
        content.addView(resultCard, resultLp);
        resultCard.addView(PageUi.text(this, "体检结果", 8.5f, PageUi.GOLD, true),
                PageUi.wrap(this));
        mdnsView = PageUi.text(this,
                "点“网络体检”检查网关、DNS、外网与 AirPlay 端口；点“mDNS 体检”检查"
                        + " iPhone 的 AirPlay / RAOP 是否能被发现。",
                9.5f, PageUi.MUTED, false);
        mdnsView.setPadding(0, PageUi.dp(this, 3), 0, 0);
        resultCard.addView(mdnsView, PageUi.matchWrap(this));

        Button back = PageUi.back(this);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backLp = PageUi.fixed(this, 38);
        PageUi.margin(backLp, this, 0, 6, 0, 0);
        root.addView(back, backLp);

        refresh();
    }

    private TextView mdnsView;
    private ScrollView scroll;

    private TextView valueText() {
        TextView v = PageUi.text(this, "—", 10f, PageUi.TEXT, false);
        PageUi.mono(v);
        return v;
    }

    private void addMetricRow(LinearLayout card, String leftLabel, TextView leftValue,
                              String rightLabel, TextView rightValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = PageUi.matchWrap(this);
        PageUi.margin(rowLp, this, 0, 4, 0, 0);
        row.addView(metric(leftLabel, leftValue), weight());
        row.addView(metric(rightLabel, rightValue), weight());
        card.addView(row, rowLp);
    }

    private LinearLayout metric(String label, TextView value) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.addView(PageUi.text(this, label, 8f, PageUi.MUTED, false), PageUi.matchWrap(this));
        cell.addView(value, PageUi.matchWrap(this));
        return cell;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(0, 0, PageUi.dp(this, 8), 0);
        return lp;
    }

    private Button action(String text, int accent, android.view.View.OnClickListener listener) {
        Button b = PageUi.button(this, text, 10f, accent);
        b.setOnClickListener(listener);
        return b;
    }

    private LinearLayout.LayoutParams actionLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, PageUi.dp(this, 34), 1f);
        lp.setMargins(PageUi.dp(this, 3), 0, PageUi.dp(this, 3), 0);
        return lp;
    }

    private void refresh() {
        if (wifi == null) return;
        WifiInfo info = wifi.getConnectionInfo();
        boolean connected = info != null && info.getNetworkId() != -1;
        String ssid = info == null ? null : info.getSSID();
        if (ssid != null) ssid = ssid.replace("\"", "");
        if (!connected || ssid == null || ssid.contains("<unknown")) ssid = "未连接";

        int freq = info == null ? 0 : info.getFrequency();
        String band = freq >= 4900 ? "5GHz" : (freq >= 2400 ? "2.4GHz" : "未知");
        int speed = info == null ? 0 : info.getLinkSpeed();
        int rssi = info == null ? 0 : info.getRssi();

        wifiValue.setText(ssid);
        bandValue.setText(String.format(Locale.CHINA, "%s · %d Mbps · %d dBm", band, speed, rssi));

        DhcpInfo dhcp = wifi.getDhcpInfo();
        ipValue.setText(dhcp == null ? "—" : ip(dhcp.ipAddress));
        gatewayValue.setText(dhcp == null ? "—" : ip(dhcp.gateway));
        dnsValue.setText(dhcp == null ? "—" : ip(dhcp.dns1) + " / " + ip(dhcp.dns2));

        boolean ap = isApEnabled();
        String hot = sHotspot != null ? "开启（本应用）" : (ap ? "开启" : "关闭");
        boolean p2p = getPackageManager().hasSystemFeature("android.hardware.wifi.direct");
        hotspotValue.setText(hot + " · P2P " + (p2p ? "支持" : "不支持"));

        boolean airplayRunning = isAirPlayServiceRunning();
        airplayValue.setText(airplayRunning ? "运行中" : "已停止");
        if (!airplayRunning) {
            portVerified = false;
            portValue.setText("—");
        } else if (!portVerified) {
            portValue.setText(airplayPort() + " 待检");
        }

        refreshBluetooth();
        refreshHiCar();
        refreshUsb();
        updateCaps();

        int accent = connected ? PageUi.GREEN : PageUi.RED;
        statusPill.setText(connected ? "已连接" : "未连接");
        statusPill.setTextColor(accent);
        statusPill.setBackground(PageUi.rounded(this,
                PageUi.blend(PageUi.CARD, accent, 0.20f),
                PageUi.blend(accent, PageUi.TEXT, 0.18f), 20));
    }

    private static String ip(int value) {
        if (value == 0) return "—";
        return String.format(Locale.US, "%d.%d.%d.%d",
                value & 0xff, (value >> 8) & 0xff, (value >> 16) & 0xff, (value >> 24) & 0xff);
    }

    /** 无线能力小标签（客户端 / 自建热点 / P2P / 5GHz），点击查看推荐方案。 */
    private TextView capPill(LinearLayout row, String label, int accent) {
        TextView v = PageUi.text(this, label + " —", 8f, accent, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, PageUi.dp(this, 2), 0, PageUi.dp(this, 2));
        v.setBackground(PageUi.rounded(this, PageUi.blend(PageUi.CARD, accent, 0.10f),
                PageUi.blend(PageUi.BORDER, accent, 0.30f), 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(PageUi.dp(this, 2), 0, PageUi.dp(this, 2), 0);
        row.addView(v, lp);
        return v;
    }

    /** 5GHz 频段能力：系统未声明时返回 null，交给界面显示“未声明”。 */
    private Boolean fiveGhzSupport() {
        if (wifi == null) return null;
        try {
            Method m = wifi.getClass().getMethod("is5GHzBandSupported");
            Object result = m.invoke(wifi);
            return result instanceof Boolean ? (Boolean) result : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void updateCaps() {
        if (capClient == null) return;
        boolean on = wifi != null && wifi.isWifiEnabled();
        capClient.setText("客户端 " + (on ? "开" : "关"));
        capClient.setTextColor(on ? PageUi.GREEN : PageUi.MUTED);

        boolean ap = Build.VERSION.SDK_INT >= 26;
        capAp.setText("自建热点 " + (ap ? "可试" : "不支持"));
        capAp.setTextColor(ap ? PageUi.GOLD : PageUi.MUTED);

        boolean p2p = getPackageManager()
                .hasSystemFeature("android.hardware.wifi.direct");
        capP2p.setText("P2P " + (p2p ? "支持" : "无"));
        capP2p.setTextColor(p2p ? PageUi.CYAN : PageUi.MUTED);

        Boolean five = fiveGhzSupport();
        cap5g.setText("5GHz " + (five == null ? "未声明" : (five ? "支持" : "不支持")));
        cap5g.setTextColor(five != null && five ? PageUi.GOLD : PageUi.MUTED);
    }

    /** 车机 Wi‑Fi 能力体检：回答“没有外置路由器时能怎么连”。 */
    private void showWifiCapDialog() {
        boolean on = wifi != null && wifi.isWifiEnabled();
        WifiInfo info = wifi == null ? null : wifi.getConnectionInfo();
        String ssid = info == null ? null : info.getSSID();
        if (ssid != null) ssid = ssid.replace("\"", "");
        if (ssid == null || ssid.contains("<unknown")) ssid = "未连接";
        boolean ap = Build.VERSION.SDK_INT >= 26;
        boolean p2p = getPackageManager()
                .hasSystemFeature("android.hardware.wifi.direct");
        Boolean five = fiveGhzSupport();

        StringBuilder sb = new StringBuilder();
        sb.append("客户端（加入手机热点）：").append(on ? "已开启" : "已关闭");
        if (on) sb.append("，当前 ").append(ssid);
        sb.append("\n自建热点（应用内 AP）：")
                .append(ap ? "接口可试，但要看 Wi‑Fi 驱动是否支持 AP 模式" : "当前版本不支持");
        sb.append("\nWi‑Fi Direct（P2P）：")
                .append(p2p ? "系统声明支持，可做无路由器直连" : "未声明，按不支持处理");
        sb.append("\n5GHz：")
                .append(five == null ? "系统未声明，以实际扫描到的频段为准"
                        : (five ? "支持" : "不支持"));
        sb.append("\n\n没有外置路由器时的推荐顺序：")
                .append("\n① 手机开个人热点 → 车机加入（所有车机都能用）")
                .append("\n② 车机自建热点 → iPhone 连车机（需驱动支持 AP）")
                .append("\n③ 有线兜底：USB 数据线（CarPlay / HiCar 有线）")
                .append("\n\nAirPlay 只要求两台设备在同一网段，外网不是必要条件；")
                .append("热点若开了 AP 隔离，iPhone 会搜不到车机。");
        dialogBuilder()
                .setTitle("无线能力与推荐方案")
                .setMessage(sb.toString())
                .setPositiveButton("打开 Wi‑Fi 设置", (d, w) -> openWifiSettings())
                .setNegativeButton("知道了", null)
                .show();
    }

    private boolean isApEnabled() {
        try {
            Method m = wifi.getClass().getMethod("isWifiApEnabled");
            Object result = m.invoke(wifi);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isAirPlayServiceRunning() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;
            for (ActivityManager.RunningServiceInfo info : am.getRunningServices(Integer.MAX_VALUE)) {
                if ("io.github.jqssun.airplay.service.AirPlayService"
                        .equals(info.service.getClassName())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private int airplayPort() {
        int port = getSharedPreferences("settings", MODE_PRIVATE).getInt("server_port", 7000);
        return port > 0 ? port : 7000;
    }

    private void startAirPlay() {
        portVerified = false;
        try {
            Intent intent = new Intent(this, AirPlayService.class);
            intent.setAction(AirPlayService.ACTION_START_SERVER);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            toast("正在启动 AirPlay 接收…");
            handler.postDelayed(() -> {
                refresh();
                boolean ok = isAirPlayServiceRunning();
                mdnsView.setText(ok
                        ? "AirPlay 已启动。点“网络体检”确认端口是否监听。"
                        : "AirPlay 启动失败。点“网络体检”查看端口状态。");
                if (ok) toast("AirPlay 已启动，等待 iPhone 连接");
                else toast("AirPlay 启动失败，请查看通知或日志");
            }, 1800);
        } catch (Throwable t) {
            toast("AirPlay 启动失败：" + t.getClass().getSimpleName());
        }
    }

    private void stopAirPlay() {
        if (!isAirPlayServiceRunning()) {
            toast("AirPlay 接收当前未运行");
            return;
        }
        toast("正在停止 AirPlay 接收…");
        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                portVerified = false;
                try {
                    AirPlayService service = ((AirPlayService.LocalBinder) binder).getService();
                    service.stopServer();
                } catch (Exception e) {
                    toast("停止失败：" + e.getClass().getSimpleName());
                }
                try {
                    unbindService(this);
                } catch (Exception ignored) {
                }
                handler.postDelayed(() -> {
                    refresh();
                    mdnsView.setText("AirPlay 已停止。点“网络体检”重新检查端口；"
                            + "点“启动 AirPlay”可恢复接收。");
                    toast("AirPlay 已停止；可在本页重新启动");
                }, 700);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };
        try {
            bindService(new Intent(this, AirPlayService.class), conn, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            toast("停止失败：" + e.getClass().getSimpleName());
        }
    }

    private void startNetworkCheck() {
        if (netCheckRunning) return;
        netCheckRunning = true;
        final int port = airplayPort();
        mdnsView.setText("正在体检：网关 ping / DNS / 外网 / AirPlay 端口…\n约 4–8 秒，请稍候。");
        new Thread(() -> {
            StringBuilder report = new StringBuilder("网络体检");
            DhcpInfo dhcp = wifi == null ? null : wifi.getDhcpInfo();
            String gateway = dhcp == null ? null : ip(dhcp.gateway);
            String dns1 = dhcp == null ? null : ip(dhcp.dns1);
            boolean wifiOn = wifi != null && wifi.isWifiEnabled();

            report.append("\n• Wi‑Fi：").append(wifiOn ? "已开启" : "已关闭");
            if (gateway != null && !"—".equals(gateway)) report.append("，网关 ").append(gateway);

            boolean apCap = Build.VERSION.SDK_INT >= 26;
            boolean p2pCap = getPackageManager()
                    .hasSystemFeature("android.hardware.wifi.direct");
            Boolean five = fiveGhzSupport();
            report.append("\n• 无线能力：客户端 ").append(wifiOn ? "可用" : "关闭")
                    .append("，自建热点 ").append(apCap ? "可试" : "不支持")
                    .append("，P2P ").append(p2pCap ? "支持" : "不支持")
                    .append("，5GHz ")
                    .append(five == null ? "未声明" : (five ? "支持" : "不支持"));

            long pingRtt = -1;
            boolean gatewayOk = false;
            if (gateway != null && !"—".equals(gateway)) {
                pingRtt = reachableHost(gateway);
                gatewayOk = pingRtt >= 0
                        || tcpProbe(gateway, 53, 1500) >= 0
                        || tcpProbe(gateway, 80, 1500) >= 0;
                report.append("\n• 网关连通：").append(gatewayOk ? "通" : "不通");
                if (pingRtt >= 0) report.append("，平均 ").append(pingRtt).append(" ms");
                else if (gatewayOk) report.append("（ICMP 被拦，网关 TCP 端口可连）");
            } else {
                report.append("\n• 网关连通：无网关，可能未连接 Wi‑Fi");
            }

            boolean dnsOk = false;
            long dnsMs = -1;
            try {
                long t0 = System.nanoTime();
                InetAddress addr = InetAddress.getByName("www.apple.com");
                dnsMs = (System.nanoTime() - t0) / 1_000_000L;
                dnsOk = addr != null;
            } catch (Exception ignored) {
            }
            report.append("\n• DNS 解析 apple.com：").append(dnsOk ? "正常" : "失败");
            if (dnsOk && dnsMs >= 0) report.append("，").append(dnsMs).append(" ms");
            if (dns1 != null && !"—".equals(dns1)) report.append("（DNS ").append(dns1).append("）");

            long netMs = -1;
            if (dnsOk) netMs = tcpProbe("www.apple.com", 443, 4000);
            boolean netOk = netMs >= 0;
            report.append("\n• 外网 HTTPS：").append(netOk ? "可连" : "不可连");
            if (netOk) report.append("，").append(netMs).append(" ms");

            boolean serviceOn = isAirPlayServiceRunning();
            boolean portOpen = tcpProbe("127.0.0.1", port, 900) >= 0;
            report.append("\n• AirPlay 接收：").append(serviceOn ? "运行中" : "未运行");
            report.append("，端口 ").append(port).append(portOpen ? " 监听" : " 未监听");

            boolean allCore = wifiOn && gatewayOk && serviceOn && portOpen;
            report.append("\n\n结论：");
            if (!gatewayOk && wifiOn) {
                report.append("车机到网关不通，先重连 Wi‑Fi 或检查热点是否还开着。");
            } else if (!wifiOn) {
                report.append("Wi‑Fi 已关闭，先打开 Wi‑Fi 或加入手机热点。");
            } else if (!serviceOn || !portOpen) {
                report.append("网络可用，但 AirPlay 接收没有正常监听，点“启动 AirPlay”重试。");
            } else if (!netOk) {
                report.append("局域网正常、外网不通；AirPlay 不依赖外网，可继续投屏。若 iPhone 搜不到，检查 AP 隔离。");
            } else if (allCore) {
                report.append("链路正常。若 iPhone 仍搜不到车机，重点检查热点/路由器的 AP 隔离或客户端隔离。");
                report.append("\n无线方案：手机开个人热点、车机加入即可投屏，不需要外置路由器；")
                        .append(apCap ? "也可试车机自建热点让 iPhone 连。" : "车机端自建热点不可用，用手机热点。");
            }

            String text = report.toString();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                mdnsView.setText(text);
                airplayValue.setText(serviceOn ? "运行中" : "已停止");
                portValue.setText(serviceOn ? (port + (portOpen ? " 监听" : " 未监听")) : "—");
                portVerified = serviceOn;
                netCheckRunning = false;
                scrollToResult();
            });
        }, "network-check").start();
    }

    /**
     * 探测主机是否可达，返回耗时毫秒；不可达返回 -1。
     * 只用 Java 的 InetAddress.isReachable（ICMP/TCP，由系统实现），不调用任何 shell 命令，
     * 以保证 APK 在封闭车机上不会执行特权命令行。
     */
    private static long reachableHost(String host) {
        Socket socket = null;
        try {
            long t0 = System.nanoTime();
            InetAddress address = InetAddress.getByName(host);
            if (address.isReachable(2000)) return (System.nanoTime() - t0) / 1_000_000L;
            // Android 上 ICMP 常被系统限制；退一步用 TCP 端口探测同一主机。
            for (int port : new int[]{53, 80, 443}) {
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 1200);
                return (System.nanoTime() - t0) / 1_000_000L;
            }
            return -1;
        } catch (Exception ignored) {
            return -1;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** TCP 连接测试，返回耗时毫秒；失败返回 -1。 */
    private static long tcpProbe(String host, int port, int timeoutMs) {
        Socket socket = null;
        try {
            long t0 = System.nanoTime();
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return (System.nanoTime() - t0) / 1_000_000L;
        } catch (Exception ignored) {
            return -1;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** 统一深色弹窗，避免系统默认浅灰对话框和赛博朋克页面割裂。 */
    private AlertDialog.Builder dialogBuilder() {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    private void peripheralRow(LinearLayout card, String label, TextView value,
                               View.OnClickListener listener) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = PageUi.matchWrap(this);
        PageUi.margin(lp, this, 0, 5, 0, 0);
        cell.addView(PageUi.text(this, label, 8f, PageUi.MUTED, false), PageUi.matchWrap(this));
        cell.addView(value, PageUi.matchWrap(this));
        cell.setOnClickListener(listener);
        card.addView(cell, lp);
    }

    private boolean hasBtPermission() {
        return Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private BluetoothAdapter bluetoothAdapter() {
        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        return bm == null ? null : bm.getAdapter();
    }

    private void refreshBluetooth() {
        if (btValue == null) return;
        BluetoothAdapter adapter = bluetoothAdapter();
        if (adapter == null) {
            btValue.setText("不可用");
            btValue.setTextColor(PageUi.MUTED);
            return;
        }
        if (!adapter.isEnabled()) {
            btValue.setText("关闭");
            btValue.setTextColor(PageUi.RED);
            return;
        }
        if (!hasBtPermission()) {
            btValue.setText("开 · 待授权（点此）");
            btValue.setTextColor(PageUi.GOLD);
            return;
        }
        try {
            int bonded = adapter.getBondedDevices() == null ? 0 : adapter.getBondedDevices().size();
            boolean a2dp = adapter.getProfileConnectionState(BluetoothProfile.A2DP)
                    == BluetoothProfile.STATE_CONNECTED;
            boolean hfp = adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
                    == BluetoothProfile.STATE_CONNECTED;
            String audio = (a2dp || hfp) ? " · 音频已连" : " · 无音频连接";
            btValue.setText("开 · 配对 " + bonded + audio);
            btValue.setTextColor(a2dp || hfp ? PageUi.GREEN : PageUi.TEXT);
        } catch (SecurityException e) {
            btValue.setText("开 · 待授权（点此）");
            btValue.setTextColor(PageUi.GOLD);
        } catch (Exception ignored) {
            btValue.setText("开");
            btValue.setTextColor(PageUi.TEXT);
        }
    }

    private void onBluetoothTap() {
        if (!hasBtPermission()) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
            return;
        }
        showBluetoothDialog();
    }

    private void showBluetoothDialog() {
        BluetoothAdapter adapter = bluetoothAdapter();
        if (adapter == null) {
            toast("当前系统没有蓝牙服务");
            return;
        }
        if (!adapter.isEnabled()) {
            dialogBuilder()
                    .setTitle("蓝牙未开启")
                    .setMessage("HiCar 无线连接、免提通话和蓝牙音频都依赖蓝牙。\n"
                            + "请在系统设置里打开蓝牙，并与手机配对。")
                    .setPositiveButton("打开蓝牙设置", (d, w) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                        } catch (Exception ignored) {
                            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                        }
                    })
                    .setNegativeButton("知道了", null)
                    .show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        try {
            for (android.bluetooth.BluetoothDevice device : adapter.getBondedDevices()) {
                if (count >= 6) break;
                String name;
                try {
                    name = device.getName();
                } catch (SecurityException e) {
                    name = null;
                }
                sb.append("\n• ").append(name == null || name.isEmpty() ? "未命名设备" : name)
                        .append("  ").append(device.getAddress());
                count++;
            }
        } catch (SecurityException e) {
            sb.append("\n未授予蓝牙权限，无法读取配对列表。");
        }
        String head = count == 0 ? "还没有配对的手机。" : "已配对 " + count + " 台设备：";
        dialogBuilder()
                .setTitle("蓝牙与 HiCar 无线连接")
                .setMessage(head + sb + "\n\nHiCar 无线 = 蓝牙配对 + 同网 Wi‑Fi；"
                        + "有线 = USB 数据线（需手机支持 HiCar）。\n"
                        + "配对后回到本页，用“网络体检”确认链路。")
                .setPositiveButton("蓝牙设置", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton("知道了", null)
                .show();
    }

    private void refreshHiCar() {
        if (hicarValue == null) return;
        if (!hicarScanned) {
            hicarScanned = true;
            try {
                for (android.content.pm.PackageInfo info : getPackageManager().getInstalledPackages(0)) {
                    String name = info.packageName == null ? "" : info.packageName.toLowerCase(Locale.US);
                    if (name.contains("hicar")) {
                        hicarFramework = true;
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (hicarFramework) {
            hicarValue.setText("框架已装 · 本应用入口");
            hicarValue.setTextColor(PageUi.CYAN);
        } else {
            hicarValue.setText("系统未装 · 本应用入口");
            hicarValue.setTextColor(PageUi.MUTED);
        }
    }

    private void showHiCarDialog() {
        String framework = hicarFramework
                ? "本机已检测到 HiCar 相关框架包。"
                : "本机未检测到 HiCar 框架包，投屏走本应用内置入口。";
        boolean btOn = false;
        try {
            BluetoothAdapter adapter = bluetoothAdapter();
            btOn = adapter != null && adapter.isEnabled();
        } catch (Exception ignored) {
        }
        String btState = btOn ? "蓝牙已开启，可以和手机配对" : "蓝牙未开启，无线 HiCar 第一步就过不去";
        dialogBuilder()
                .setTitle("HUAWEI HiCar 连接方式")
                .setMessage(framework + " " + btState + "\n\n"
                        + "HiCar 官方支持无线和有线两种：\n"
                        + "无线：蓝牙配对 + 同一 Wi‑Fi，由车机侧 HiCar 发起；"
                        + "SYNC+ 没有原厂 HiCar，需要外接 HiCar 适配器（USB 盒子）才成立。\n"
                        + "有线：USB 数据线直连，数据线要能传输，手机端允许 USB 配件。\n\n"
                        + "点首页“HUAWEI HiCar”卡片进入接收页；想看清三条路，点“连接补强”。")
                .setPositiveButton("打开 HiCar 页", (d, w) -> {
                    try {
                        startActivity(new Intent(this, local.hicarlab.HomeActivity.class));
                    } catch (Exception e) {
                        toast("HiCar 页面启动失败");
                    }
                })
                .setNeutralButton("连接补强", (d, w) -> {
                    try {
                        startActivity(new Intent(this, LinkBoostActivity.class));
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton("知道了", null)
                .show();
    }

    private void refreshUsb() {
        if (usbValue == null) return;
        UsbManager um = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (um == null) {
            usbValue.setText("系统无 USB 服务");
            usbValue.setTextColor(PageUi.MUTED);
            return;
        }
        try {
            Map<String, UsbDevice> devices = um.getDeviceList();
            int count = devices == null ? 0 : devices.size();
            if (count == 0) {
                usbValue.setText("未检测到设备");
                usbValue.setTextColor(PageUi.MUTED);
            } else {
                usbValue.setText(count + " 个设备 · 点此查看");
                usbValue.setTextColor(PageUi.GOLD);
            }
        } catch (Exception ignored) {
            usbValue.setText("读取失败");
            usbValue.setTextColor(PageUi.RED);
        }
    }

    private void showUsbDialog() {
        UsbManager um = (UsbManager) getSystemService(Context.USB_SERVICE);
        Map<String, UsbDevice> devices = um == null ? null : um.getDeviceList();
        if (devices == null || devices.isEmpty()) {
            dialogBuilder()
                    .setTitle("USB 检测")
                    .setMessage("当前没有检测到 USB 设备。\n\n"
                            + "CarPlay / HiCar 有线连接需要：数据线支持传输（不是只能充电的线）、"
                            + "插在车机可用的 USB 数据口、手机端允许 USB 配件。\n"
                            + "插上后回到本页会自动刷新。")
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }
        StringBuilder sb = new StringBuilder("检测到 " + devices.size() + " 个 USB 设备：");
        int shown = 0;
        for (UsbDevice device : devices.values()) {
            if (shown >= 6) break;
            String product;
            try {
                product = device.getProductName();
            } catch (Exception e) {
                product = null;
            }
            String label = product == null || product.isEmpty() ? "USB 设备" : product;
            sb.append("\n• ").append(label)
                    .append("  VID ").append(String.format(Locale.US, "%04X", device.getVendorId()))
                    .append(" / PID ").append(String.format(Locale.US, "%04X", device.getProductId()));
            shown++;
        }
        sb.append("\n\n有设备不代表 CarPlay 一定能通：车机侧 USB 通道还要支持对应协议。"
                + "无线投屏不受这里影响。");
        dialogBuilder()
                .setTitle("USB 检测")
                .setMessage(sb.toString())
                .setPositiveButton("知道了", null)
                .show();
    }

    private void openWifiSettings() {
        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
    }

    private void openHotspotSettings() {
        try {
            startActivity(new Intent("android.settings.TETHER_SETTINGS"));
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        }
    }

    private boolean needsWifiPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED;
    }

    private void requestWifiPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.NEARBY_WIFI_DEVICES
                : Manifest.permission.ACCESS_FINE_LOCATION;
        requestPermissions(new String[]{permission}, REQ_WIFI);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_BT) {
            boolean granted = results.length > 0
                    && results[0] == PackageManager.PERMISSION_GRANTED;
            refresh();
            if (granted) showBluetoothDialog();
            else toast("未授权蓝牙，无法读取配对状态");
            return;
        }
        if (requestCode != REQ_WIFI) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            showHotspotWizard();
        } else {
            toast("需要 Wi‑Fi/附近设备权限才能开启热点");
        }
    }

    private void startCarHotspot() {
        if (sHotspot != null) {
            showHotspotDialog();
            return;
        }
        if (needsWifiPermission()) {
            requestWifiPermission();
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean locationOn = lm != null && (Build.VERSION.SDK_INT >= 28
                ? lm.isLocationEnabled()
                : (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)));
        if (Build.VERSION.SDK_INT < 33 && !locationOn) {
            toast("请先打开系统定位开关，Android 8/9 开热点需要定位权限");
            try {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } catch (Exception ignored) {
            }
            return;
        }
        showHotspotWizard();
    }

    /**
     * 热点开启向导：先给三条可执行的路径，再让用户选一条，
     * 不再直接弹失败原因码。
     */
    private void showHotspotWizard() {
        boolean sysOn = isApEnabled();
        String state = "系统热点：" + (sysOn ? "已开启" : "未开启")
                + "\n应用内热点：" + (sHotspot == null ? "未开启" : "已开启");
        String steps = "① 车机「设置 → 网络 → 便携式热点」打开热点（WPA2 加密）\n"
                + "② 让 iPhone 连接该热点；热点不开数据也能投屏\n"
                + "③ 回到本页点「网络体检」，确认网关与 AirPlay 端口\n\n"
                + "若车机允许 AP 模式，也可直接点「试应用内热点」，由本应用开一个本地热点。";
        dialogBuilder()
                .setTitle("热点开启向导")
                .setMessage(state + "\n\n" + steps)
                .setPositiveButton("去系统热点设置", (d, w) -> openHotspotSettings())
                .setNeutralButton("试应用内热点", (d, w) -> startCarHotspotNow())
                .setNegativeButton("取消", null)
                .show();
    }

    private void startCarHotspotNow() {
        if (wifi == null) return;
        if (Build.VERSION.SDK_INT < 26) {
            toast("当前系统版本不支持应用内创建热点，请用系统热点设置");
            openHotspotSettings();
            return;
        }
        toast("正在开启车机热点，Wi‑Fi 可能会短暂断开…");
        try {
            wifi.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() {
                @Override
                public void onStarted(WifiManager.LocalOnlyHotspotReservation reservation) {
                    sHotspot = reservation;
                    WifiConfiguration cfg = reservation.getWifiConfiguration();
                    sHotspotSsid = cfg == null ? "" : String.valueOf(cfg.SSID).replace("\"", "");
                    sHotspotPass = cfg == null ? "" : String.valueOf(cfg.preSharedKey).replace("\"", "");
                    actionHint.setText("车机热点已开启");
                    refresh();
                    showHotspotDialog();
                }

                @Override
                public void onStopped() {
                    sHotspot = null;
                    actionHint.setText("热点优先用手机热点");
                    toast("车机热点已停止");
                    refresh();
                }

                @Override
                public void onFailed(int reason) {
                    sHotspot = null;
                    actionHint.setText("应用内热点不可用，请用系统热点");
                    refresh();
                    showHotspotFailure(reason, null);
                }
            }, handler);
        } catch (Exception e) {
            actionHint.setText("应用内热点不可用，请用系统热点");
            showHotspotFailure(0, e.getClass().getSimpleName());
        }
    }

    private void showHotspotFailure(int reason, String detail) {
        String why = reason == 1 ? "没有可用信道"
                : reason == 2 ? "当前 Wi‑Fi 模式不支持应用内热点"
                : reason == 3 ? "系统禁止热点"
                : reason > 0 ? "原因码 " + reason
                : (detail == null ? "未知错误" : detail);
        dialogBuilder()
                .setTitle("应用内热点没开起来")
                .setMessage(why + "。\n\n部分车机/模拟器的 Wi‑Fi 驱动不支持 AP 模式。"
                        + "可点“打开热点向导”按三步走：系统热点设置 → 手机连热点 → 网络体检，或直接让车机加入 iPhone 个人热点。"
                        + "\nAirPlay 不要求车机有外网，只要两台设备在同一 Wi‑Fi。")
                .setPositiveButton("打开热点向导", (d, w) -> showHotspotWizard())
                .setNegativeButton("用系统热点设置", (d, w) -> openHotspotSettings())
                .show();
    }

    private void stopCarHotspot() {
        if (sHotspot == null) {
            toast("当前没有本应用开启的车机热点");
            return;
        }
        try {
            sHotspot.close();
            toast("车机热点已停止；请重新连接原来的 Wi‑Fi");
        } catch (Exception ignored) {
        }
        sHotspot = null;
        sHotspotSsid = "";
        sHotspotPass = "";
        actionHint.setText("热点优先用手机热点");
        refresh();
    }

    private void showHotspotDialog() {
        String ssid = sHotspotSsid.isEmpty() ? "（系统未返回，请在系统热点设置查看）" : sHotspotSsid;
        String pass = sHotspotPass.isEmpty() ? "（系统未返回，请在系统热点设置查看）" : sHotspotPass;
        String message = "SSID：" + ssid + "\n密码：" + pass
                + "\n\n让 iPhone 连接这个 Wi‑Fi，然后打开 AirPlay 接收。"
                + "\n该热点不提供外网，只用于本机互联。";
        dialogBuilder()
                .setTitle("车机热点已开启")
                .setMessage(message)
                .setPositiveButton("复制凭据", (d, w) -> copyText("SSID: " + ssid + "\n密码: " + pass))
                .setNegativeButton("知道了", null)
                .show();
    }

    private void copyText(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Wi‑Fi 凭据", text));
            toast("已复制到剪贴板");
        }
    }

    private void startMdnsCheck() {
        if (mdnsRunning) return;
        if (nsd == null) {
            mdnsView.setText("当前系统没有 mDNS 服务，无法体检。");
            return;
        }
        mdnsFound.clear();
        mdnsErrors.clear();
        mdnsRunning = true;
        acquireMulticastLock();
        mdnsView.setText("正在搜索 AirPlay 服务…（约 5 秒）");

        airplayListener = listener("_airplay._tcp");
        startDiscovery(airplayListener, "_airplay._tcp");
        handler.postDelayed(this::startRaopPhase, 1900);
        handler.postDelayed(this::finishMdnsCheck, 4700);
    }

    private void startRaopPhase() {
        if (!mdnsRunning) return;
        stopListener(airplayListener);
        mdnsView.setText("正在搜索 RAOP 音频服务…");
        raopListener = listener("_raop._tcp");
        startDiscovery(raopListener, "_raop._tcp");
    }

    private void startDiscovery(NsdManager.DiscoveryListener listener, String type) {
        try {
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener);
        } catch (Exception e) {
            mdnsErrors.add(type + " 启动异常 " + e.getClass().getSimpleName());
        }
    }

    private NsdManager.DiscoveryListener listener(String type) {
        return new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String serviceType) {
            }

            @Override
            public void onServiceFound(NsdServiceInfo info) {
                if (!mdnsRunning) return;
                String line = info.getServiceName() + "  (" + info.getServiceType() + ")";
                if (!mdnsFound.contains(line)) mdnsFound.add(line);
                runOnUiThread(() -> {
                    if (mdnsRunning) renderMdns("发现 " + mdnsFound.size() + " 个服务：");
                });
            }

            @Override
            public void onServiceLost(NsdServiceInfo info) {
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                if (!mdnsRunning) return;
                mdnsErrors.add(serviceType + " 错误码 " + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            }
        };
    }

    private void acquireMulticastLock() {
        if (wifi == null) return;
        try {
            if (multicastLock == null) {
                multicastLock = wifi.createMulticastLock("icarplay-mdns");
                multicastLock.setReferenceCounted(false);
            }
            if (!multicastLock.isHeld()) multicastLock.acquire();
        } catch (Exception ignored) {
        }
    }

    private void releaseMulticastLock() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        } catch (Exception ignored) {
        }
    }

    private void finishMdnsCheck() {
        if (!mdnsRunning) return;
        mdnsRunning = false;
        stopListener(airplayListener);
        stopListener(raopListener);
        releaseMulticastLock();
        if (mdnsFound.isEmpty()) {
            if (mdnsErrors.isEmpty()) {
                mdnsView.setText("未发现 AirPlay / RAOP 服务。请确认手机与车机在同一网段，"
                        + "并检查热点/路由器是否开启了 AP 隔离或客户端隔离。");
                scrollToResult();
            } else {
                mdnsView.setText("mDNS 查询没有结果：" + errorText()
                        + "。可能是系统 Nsd 服务被限制，或当前网络不允许组播。");
                scrollToResult();
            }
        } else {
            String title = "体检完成，发现 " + mdnsFound.size() + " 个服务：";
            if (!mdnsErrors.isEmpty()) title += "（部分查询失败：" + errorText() + "）";
            renderMdns(title);
        }
    }

    private String errorText() {
        StringBuilder sb = new StringBuilder();
        for (String e : mdnsErrors) {
            if (sb.length() > 0) sb.append("；");
            sb.append(e);
        }
        return sb.toString();
    }

    private void renderMdns(String title) {
        StringBuilder sb = new StringBuilder(title);
        for (String s : mdnsFound) sb.append("\n• ").append(s);
        mdnsView.setText(sb.toString());
        scrollToResult();
    }

    private void stopListener(NsdManager.DiscoveryListener listener) {
        if (nsd == null || listener == null) return;
        try {
            nsd.stopServiceDiscovery(listener);
        } catch (Exception ignored) {
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    /** 体检结果较长时自动滚到结论，避免首屏只看到前几行。 */
    private void scrollToResult() {
        if (scroll == null) return;
        handler.postDelayed(() -> {
            if (scroll == null || isFinishing()) return;
            scroll.fullScroll(View.FOCUS_DOWN);
        }, 180);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(tick);
        if (mdnsRunning) {
            stopListener(airplayListener);
            stopListener(raopListener);
            mdnsRunning = false;
        }
        releaseMulticastLock();
    }
}
