package com.rockjesus.icarplay;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.github.jqssun.airplay.service.AirPlayService;

/**
 * 网络与连接中心：只读展示 Wi‑Fi/热点状态，并提供热点向导与 mDNS 体检。
 * 不修改系统网络配置，不自动替用户连未知热点。
 */
public class NetworkCenterActivity extends Activity {

    private static final int REQ_WIFI = 70;
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

        TextView sub = PageUi.text(this, "Wi‑Fi / 热点 / 网络体检 · AirPlay 自检与投屏共用",
                10.5f, PageUi.MUTED, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subLp = PageUi.matchWrap(this);
        PageUi.margin(subLp, this, 0, 3, 0, 7);
        root.addView(sub, subLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, PageUi.dp(this, 2));
        scroll.addView(content, PageUi.matchWrap(this));

        LinearLayout statusCard = PageUi.card(this, PageUi.BLUE, 12, 8);
        content.addView(statusCard, PageUi.matchWrap(this));

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

            long pingRtt = -1;
            boolean gatewayOk = false;
            if (gateway != null && !"—".equals(gateway)) {
                pingRtt = pingHost(gateway);
                gatewayOk = pingRtt >= 0 || tcpProbe(gateway, 53, 1500) >= 0;
                report.append("\n• 网关连通：").append(gatewayOk ? "通" : "不通");
                if (pingRtt >= 0) report.append("，平均 ").append(pingRtt).append(" ms");
                else if (gatewayOk) report.append("（ICMP 被拦，TCP 53 可连）");
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
            }

            String text = report.toString();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                mdnsView.setText(text);
                airplayValue.setText(serviceOn ? "运行中" : "已停止");
                portValue.setText(serviceOn ? (port + (portOpen ? " 监听" : " 未监听")) : "—");
                portVerified = serviceOn;
                netCheckRunning = false;
            });
        }, "network-check").start();
    }

    /** ping 主机，返回平均毫秒；失败返回 -1。 */
    private static long pingHost(String host) {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/ping", "-c", "2", "-W", "2", host)
                    .redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
            reader.close();
            if (!process.waitFor(6, TimeUnit.SECONDS)) {
                process.destroy();
                return -1;
            }
            String text = out.toString();
            if (!text.contains("0% packet loss") && !text.contains("0% loss")) return -1;
            int avgAt = text.indexOf("min/avg/max");
            if (avgAt >= 0) {
                int slash = text.indexOf('/', text.indexOf('/') + 1);
                if (slash > 0) {
                    int end = text.indexOf('/', slash + 1);
                    if (end > slash) {
                        String avg = text.substring(slash + 1, end).trim();
                        return Math.round(Double.parseDouble(avg));
                    }
                }
            }
            return 0;
        } catch (Exception ignored) {
            if (process != null) process.destroy();
            return -1;
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
        if (requestCode != REQ_WIFI) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startCarHotspotNow();
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
        startCarHotspotNow();
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
        new AlertDialog.Builder(this)
                .setTitle("应用内热点没开起来")
                .setMessage(why + "。\n\n部分车机/模拟器的 Wi‑Fi 驱动不支持 AP 模式。"
                        + "请点“打开热点设置”用系统热点，或直接让车机加入 iPhone 个人热点。"
                        + "\nAirPlay 不要求车机有外网，只要两台设备在同一 Wi‑Fi。")
                .setPositiveButton("打开热点设置", (d, w) -> openHotspotSettings())
                .setNegativeButton("知道了", null)
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
        new AlertDialog.Builder(this)
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
            } else {
                mdnsView.setText("mDNS 查询没有结果：" + errorText()
                        + "。可能是系统 Nsd 服务被限制，或当前网络不允许组播。");
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
